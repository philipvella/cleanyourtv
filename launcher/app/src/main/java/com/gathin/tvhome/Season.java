package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * seasonal_scenes (config.xml or a theme): which animated scene SeasonView plays on Home, picked from the date (UK
 * calendar, northern hemisphere) and the household's own dates (seasonal_dates), or forced by naming one. Pure, so
 * the calendar is unit-tested (SeasonTest).
 */
final class Season {
    enum Scene {
        NONE, AUTUMN, HALLOWEEN, FIREWORKS, CHRISTMAS, WINTER, VALENTINE, SPRING, SUMMER,
        NEW_YEAR, PANCAKE_DAY, ST_PATRICKS, EASTER, MOTHERS_DAY, FATHERS_DAY, BIRTHDAY, ANNIVERSARY
    }

    static final String AUTO = "auto", OFF = "off";
    /** The scenes Launcher settings offers by name, in the order of R.array.season_names. */
    static final Scene[] PICKABLE = {Scene.AUTUMN, Scene.HALLOWEEN, Scene.FIREWORKS, Scene.CHRISTMAS, Scene.WINTER,
            Scene.VALENTINE, Scene.SPRING, Scene.SUMMER, Scene.NEW_YEAR, Scene.PANCAKE_DAY, Scene.ST_PATRICKS,
            Scene.EASTER, Scene.MOTHERS_DAY, Scene.FATHERS_DAY, Scene.BIRTHDAY, Scene.ANNIVERSARY};

    private Season() {
    }

    /** The stored value for a pickable scene: its name in lower case, as the key takes it. */
    static String keyOf(Scene s) {
        return s.name().toLowerCase(Locale.ROOT);
    }

    /** A scene by its key name ("christmas", "new_year"; "bonfire" for the fireworks), or NONE. */
    static Scene named(String name) {
        String k = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if ("bonfire".equals(k)) return Scene.FIREWORKS;
        for (Scene s : Scene.values()) if (s != Scene.NONE && keyOf(s).equals(k)) return s;
        return Scene.NONE;
    }

    static Scene pick(String key, LocalDate day) {
        return pick(key, day, Collections.emptyMap());
    }

    /**
     * The scene for {@code key} on {@code day}: "auto" follows the calendar and the household's dates; a scene's
     * name forces it; empty, "off" or unknown is NONE.
     */
    static Scene pick(String key, LocalDate day, Map<MonthDay, Scene> ownDates) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) return Scene.NONE;
        if (AUTO.equals(k)) return at(day, ownDates);
        return named(k);
    }

    /**
     * seasonal_dates: {@code "MM-DD=scene"} lines ("04-21=birthday", "04-16=anniversary"); a line that does not parse
     * or names no scene is skipped.
     */
    static Map<MonthDay, Scene> parseDates(List<String> lines) {
        Map<MonthDay, Scene> out = new HashMap<>();
        if (lines == null) return out;
        for (String line : lines) {
            int eq = line == null ? -1 : line.indexOf('=');
            if (eq < 0) continue;
            String[] md = line.substring(0, eq).trim().split("-");
            Scene s = named(line.substring(eq + 1));
            if (md.length != 2 || s == Scene.NONE) continue;
            try {
                out.put(MonthDay.of(Integer.parseInt(md[0].trim()), Integer.parseInt(md[1].trim())), s);
            } catch (RuntimeException bad) {
                // "13-40" or "ab-cd": skipped
            }
        }
        return out;
    }

    static Scene at(LocalDate day) {
        return at(day, Collections.emptyMap());
    }

    /** The calendar: the household's own dates first, then the one-day holidays, then the seasons. */
    static Scene at(LocalDate day, Map<MonthDay, Scene> ownDates) {
        Scene own = ownDates.get(MonthDay.from(day));
        if (own != null) return own;
        int m = day.getMonthValue(), dom = day.getDayOfMonth();
        if (m == 12 && dom == 31 || m == 1 && dom == 1) return Scene.NEW_YEAR;
        if (m == 11 && dom == 5) return Scene.FIREWORKS;                                    // Bonfire Night
        LocalDate easter = easter(day.getYear());
        if (!day.isBefore(easter.minusDays(2)) && !day.isAfter(easter.plusDays(1))) return Scene.EASTER;  // Fri-Mon
        if (day.equals(easter.minusDays(47))) return Scene.PANCAKE_DAY;                    // Shrove Tuesday
        if (day.equals(easter.minusDays(21))) return Scene.MOTHERS_DAY;                    // Mothering Sunday
        if (m == 3 && dom == 17) return Scene.ST_PATRICKS;
        if (day.equals(fathersDay(day.getYear()))) return Scene.FATHERS_DAY;
        if (m == 10 && dom >= 25) return Scene.HALLOWEEN;
        if (m == 12 && dom <= 26) return Scene.CHRISTMAS;
        if (m == 2 && dom >= 7 && dom <= 14) return Scene.VALENTINE;
        if (m == 12 || m == 1 || m == 2) return Scene.WINTER;
        if (m >= 3 && m <= 5) return Scene.SPRING;
        if (m >= 6 && m <= 8) return Scene.SUMMER;
        return Scene.AUTUMN;                                                                 // September to November
    }

    /** Western Easter Sunday (the Anonymous Gregorian algorithm). */
    static LocalDate easter(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, dayOfMonth = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, dayOfMonth);
    }

    /** The UK's Father's Day: the third Sunday of June. */
    static LocalDate fathersDay(int year) {
        return LocalDate.of(year, 6, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY));
    }
}
