package com.gathin.tvhome;

import com.gathin.tvhome.Season.Scene;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * seasonal_scenes "auto": when each scene plays, as an ordered list of slots. Where two slots cover the same day the
 * one higher in the list wins. The default is the calendar Season always had; Launcher settings' animation schedule
 * (SeasonScheduleActivity) moves, re-dates or turns off slots and saves the result as one line in the on-TV
 * choices ({@link #KEY}). Pure, so it is unit-tested (SeasonScheduleTest).
 */
final class SeasonSchedule {
    /** The on-TV choice: absent means the default schedule. */
    static final String KEY = "season_schedule";
    /** What an edge is counted from. */
    static final int FIXED = 0, EASTER = 1, FATHERS = 2;
    /** Which edge an edit moves. */
    static final int START = 0, END = 1, BOTH = 2;

    /** A slot's first or last day: a date, or Easter Sunday or Father's Day, plus a number of days. */
    static final class Edge {
        final int anchor, month, day, offset;

        Edge(int anchor, int month, int day, int offset) {
            this.anchor = anchor;
            this.month = month;
            this.day = day;
            this.offset = offset;
        }

        LocalDate on(int year) {
            LocalDate base;
            if (anchor == EASTER) base = Season.easter(year);
            else if (anchor == FATHERS) base = Season.fathersDay(year);
            else base = LocalDate.of(year, month, Math.min(day, YearMonth.of(year, month).lengthOfMonth()));
            return base.plusDays(offset);
        }

        /**
         * Moved by some days. A date stays a plain month and day (counted in a year without 29 February), except
         * that the last day of February is kept as "the day before 1 March" so a leap year's 29th is not lost.
         */
        Edge shift(int days) {
            if (anchor != FIXED) return new Edge(anchor, month, day, offset + days);
            LocalDate d = LocalDate.of(2001, month, Math.min(day, YearMonth.of(2001, month).lengthOfMonth()))
                    .plusDays(offset + days);
            if (d.getMonthValue() == 2 && d.getDayOfMonth() == 28) return new Edge(FIXED, 3, 1, -1);
            return new Edge(FIXED, d.getMonthValue(), d.getDayOfMonth(), 0);
        }

        String key() {
            String base = anchor == EASTER ? "easter" : anchor == FATHERS ? "fathers"
                    : String.format(Locale.ROOT, "%02d-%02d", month, day);
            return offset == 0 ? base : base + (offset > 0 ? "+" : "") + offset;
        }

        /** "12-25", "03-01-1", "easter-2", "fathers"; null when it does not parse. */
        static Edge parse(String s) {
            String t = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
            try {
                if (t.startsWith("easter")) return new Edge(EASTER, 0, 0, offsetOf(t.substring(6)));
                if (t.startsWith("fathers")) return new Edge(FATHERS, 0, 0, offsetOf(t.substring(7)));
                if (t.length() < 5 || t.charAt(2) != '-') return null;
                int m = Integer.parseInt(t.substring(0, 2)), d = Integer.parseInt(t.substring(3, 5));
                if (m < 1 || m > 12 || d < 1 || d > YearMonth.of(2000, m).lengthOfMonth()) return null;
                return new Edge(FIXED, m, d, offsetOf(t.substring(5)));
            } catch (NumberFormatException bad) {
                return null;
            }
        }

        private static int offsetOf(String s) {
            if (s.isEmpty()) return 0;
            int n = Integer.parseInt(s.startsWith("+") ? s.substring(1) : s);
            if (Math.abs(n) > 366) throw new NumberFormatException(s);
            return n;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Edge)) return false;
            Edge e = (Edge) o;
            return key().equals(e.key());
        }

        @Override
        public int hashCode() {
            return key().hashCode();
        }
    }

    /** One row: a scene and its days, or (scene null) the household's own dates from seasonal_dates. */
    static final class Slot {
        final Scene scene;
        final Edge start, end;
        final boolean on;

        Slot(Scene scene, Edge start, Edge end, boolean on) {
            this.scene = scene;
            this.start = start;
            this.end = end;
            this.on = on;
        }

        boolean own() {
            return scene == null;
        }

        /** Whether the slot's days include {@code day}; a slot may run over New Year (31 December to 1 January). */
        boolean covers(LocalDate day) {
            if (own()) return false;
            for (int y = day.getYear() - 1; y <= day.getYear(); y++) {
                LocalDate a = start.on(y), b = end(y);
                if (!day.isBefore(a) && !day.isAfter(b)) return true;
            }
            return false;
        }

        /** The last day of the run that starts in {@code year}. */
        LocalDate end(int year) {
            LocalDate a = start.on(year), b = end.on(year);
            return b.isBefore(a) ? end.on(year + 1) : b;
        }

        /** Whether these days make sense: under a year long, and only a run of dates may go over New Year. */
        boolean valid(int year) {
            if (own()) return true;
            LocalDate a = start.on(year), b = end.on(year);
            if (b.isBefore(a) && (start.anchor != FIXED || end.anchor != FIXED)) return false;
            return ChronoUnit.DAYS.between(a, end(year)) < 365;
        }

        String key() {
            String name = own() ? "own" : Season.keyOf(scene);
            return (on ? "" : "!") + name + (own() ? "" : ":" + start.key() + "~" + end.key());
        }
    }

    static final SeasonSchedule DEFAULT = new SeasonSchedule(defaults());

    final List<Slot> slots;

    SeasonSchedule(List<Slot> slots) {
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    /** The calendar Season has always used, top to bottom. */
    private static List<Slot> defaults() {
        List<Slot> s = new ArrayList<>();
        s.add(new Slot(null, null, null, true));
        s.add(dates(Scene.NEW_YEAR, 12, 31, 1, 1));
        s.add(dates(Scene.FIREWORKS, 11, 5, 11, 5));                                    // Bonfire Night
        s.add(new Slot(Scene.EASTER, new Edge(EASTER, 0, 0, -2), new Edge(EASTER, 0, 0, 1), true));  // Fri-Mon
        s.add(new Slot(Scene.PANCAKE_DAY, new Edge(EASTER, 0, 0, -47), new Edge(EASTER, 0, 0, -47), true));
        s.add(new Slot(Scene.MOTHERS_DAY, new Edge(EASTER, 0, 0, -21), new Edge(EASTER, 0, 0, -21), true));
        s.add(dates(Scene.ST_PATRICKS, 3, 17, 3, 17));
        s.add(new Slot(Scene.FATHERS_DAY, new Edge(FATHERS, 0, 0, 0), new Edge(FATHERS, 0, 0, 0), true));
        s.add(dates(Scene.HALLOWEEN, 10, 25, 10, 31));
        s.add(dates(Scene.CHRISTMAS, 12, 1, 12, 26));
        s.add(dates(Scene.VALENTINE, 2, 7, 2, 14));
        s.add(new Slot(Scene.WINTER, new Edge(FIXED, 12, 1, 0), new Edge(FIXED, 3, 1, -1), true));
        s.add(dates(Scene.SPRING, 3, 1, 5, 31));
        s.add(dates(Scene.SUMMER, 6, 1, 8, 31));
        s.add(dates(Scene.AUTUMN, 9, 1, 11, 30));
        return s;
    }

    private static Slot dates(Scene scene, int m1, int d1, int m2, int d2) {
        return new Slot(scene, new Edge(FIXED, m1, d1, 0), new Edge(FIXED, m2, d2, 0), true);
    }

    /** The scene for the day: the first slot that is on and covers it, or NONE. */
    Scene at(LocalDate day, Map<MonthDay, Scene> ownDates) {
        int i = winner(day, ownDates);
        if (i < 0) return Scene.NONE;
        Slot s = slots.get(i);
        return s.own() ? ownDates.get(MonthDay.from(day)) : s.scene;
    }

    /** The index of the slot that plays on the day, or -1. */
    int winner(LocalDate day, Map<MonthDay, Scene> ownDates) {
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (!s.on) continue;
            if (s.own() ? ownDates.containsKey(MonthDay.from(day)) : s.covers(day)) return i;
        }
        return -1;
    }

    /** Whether slot i has the day at all, whether or not it wins it. */
    boolean has(int i, LocalDate day, Map<MonthDay, Scene> ownDates) {
        Slot s = slots.get(i);
        return s.own() ? ownDates.containsKey(MonthDay.from(day)) : s.covers(day);
    }

    /** Slot i swapped with its neighbour above (-1) or below (+1); the same schedule at either end. */
    SeasonSchedule moved(int i, int by) {
        int j = i + by;
        if (i < 0 || i >= slots.size() || j < 0 || j >= slots.size()) return this;
        List<Slot> out = new ArrayList<>(slots);
        Collections.swap(out, i, j);
        return new SeasonSchedule(out);
    }

    /** Slot i's start, end or both moved by some days; the same schedule when that would not make sense. */
    SeasonSchedule shifted(int i, int edge, int days, int year) {
        Slot s = slots.get(i);
        if (s.own()) return this;
        Edge a = edge == END ? s.start : s.start.shift(days);
        Edge b = edge == START ? s.end : s.end.shift(days);
        Slot t = new Slot(s.scene, a, b, s.on);
        if (!t.valid(year)) return this;
        return with(i, t);
    }

    SeasonSchedule toggled(int i) {
        Slot s = slots.get(i);
        return with(i, new Slot(s.scene, s.start, s.end, !s.on));
    }

    /** Slot i's days back to the default's; its place in the list stays. */
    SeasonSchedule reset(int i) {
        Slot s = slots.get(i);
        for (Slot d : DEFAULT.slots) {
            if (d.scene == s.scene) return with(i, new Slot(s.scene, d.start, d.end, true));
        }
        return this;
    }

    /** Whether slot i has the default's days and is on. */
    boolean isDefault(int i) {
        Slot s = slots.get(i);
        for (Slot d : DEFAULT.slots) if (d.scene == s.scene) return s.key().equals(d.key());
        return false;
    }

    private SeasonSchedule with(int i, Slot s) {
        List<Slot> out = new ArrayList<>(slots);
        out.set(i, s);
        return new SeasonSchedule(out);
    }

    /** "own,new_year:12-31~01-01,!summer:06-01~08-31,…": the order, the days and "!" for a slot that is off. */
    String key() {
        StringBuilder b = new StringBuilder();
        for (Slot s : slots) {
            if (b.length() > 0) b.append(',');
            b.append(s.key());
        }
        return b.toString();
    }

    /**
     * The saved line back to a schedule. Pieces that do not parse are skipped, and any default slot the line does not
     * name is added at the end, so a damaged line never loses a scene. Empty or null is the default.
     */
    static SeasonSchedule parse(String line) {
        if (line == null || line.trim().isEmpty()) return DEFAULT;
        List<Slot> out = new ArrayList<>();
        Set<Scene> seen = EnumSet.noneOf(Scene.class);
        boolean ownSeen = false;
        for (String raw : line.split(",")) {
            String p = raw.trim().toLowerCase(Locale.ROOT);
            boolean on = !p.startsWith("!");
            if (!on) p = p.substring(1).trim();
            int colon = p.indexOf(':');
            String name = colon < 0 ? p : p.substring(0, colon).trim();
            if ("own".equals(name)) {
                if (!ownSeen) out.add(new Slot(null, null, null, on));
                ownSeen = true;
                continue;
            }
            Scene scene = Season.named(name);
            if (scene == Scene.NONE || seen.contains(scene) || colon < 0) continue;
            String[] range = p.substring(colon + 1).split("~");
            if (range.length != 2) continue;
            Edge a = Edge.parse(range[0]), b = Edge.parse(range[1]);
            if (a == null || b == null) continue;
            Slot s = new Slot(scene, a, b, on);
            if (!s.valid(2001) || !s.valid(2024)) continue;
            out.add(s);
            seen.add(scene);
        }
        for (Slot d : DEFAULT.slots) {
            if (d.own() ? !ownSeen : !seen.contains(d.scene)) out.add(d);
        }
        return new SeasonSchedule(out);
    }
}
