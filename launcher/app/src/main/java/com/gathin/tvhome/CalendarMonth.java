package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The month CalendarActivity shows: where its first day sits in a week that starts on the locale's first day, and
 * which days are worth a mark (the household's own dates from seasonal_dates, and the holidays Season knows, each on
 * its one day). Pure, so it runs in JVM tests.
 */
final class CalendarMonth {
    private CalendarMonth() {
    }

    /** The column (0 to 6) of the month's first day, in a week that starts on {@code firstDay}. */
    static int firstColumn(YearMonth month, DayOfWeek firstDay) {
        return Math.floorMod(month.atDay(1).getDayOfWeek().getValue() - firstDay.getValue(), 7);
    }

    /** The rows the month needs: 4 to 6. */
    static int rows(YearMonth month, DayOfWeek firstDay) {
        return (firstColumn(month, firstDay) + month.lengthOfMonth() + 6) / 7;
    }

    /** One thing on a day: its name, and whether it is a bank holiday (a day off). */
    static final class Mark {
        final String name;
        final boolean bank;

        Mark(String name, boolean bank) {
            this.name = name;
            this.bank = bank;
        }
    }

    /**
     * The days of the month with something on, in order: the household's own date, the bank holiday, then the day's
     * other holiday (Halloween, Mother's Day), a name already given once left out. {@code sceneNames} are the names
     * of Season's scenes in their order; {@code bank} is Holidays.of for the month's year.
     */
    static Map<Integer, List<Mark>> marks(YearMonth month, Map<MonthDay, Season.Scene> own, String[] sceneNames,
                                          Map<LocalDate, String> bank) {
        Map<Integer, List<Mark>> out = new TreeMap<>();
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate day = month.atDay(d);
            List<Mark> on = new ArrayList<>();
            Season.Scene mine = own.get(MonthDay.from(day));
            if (mine != null) add(on, name(mine, sceneNames), false);
            String off = bank.get(day);
            if (off != null) add(on, off, true);
            Season.Scene h = holiday(day);
            if (h != null && h != mine) add(on, name(h, sceneNames), false);
            if (!on.isEmpty()) out.put(d, on);
        }
        return out;
    }

    private static String name(Season.Scene s, String[] names) {
        return s.ordinal() < names.length ? names[s.ordinal()] : "";
    }

    private static void add(List<Mark> on, String name, boolean bank) {
        if (name.isEmpty()) return;
        for (Mark m : on) if (m.name.equalsIgnoreCase(name)) return;
        on.add(new Mark(name, bank));
    }

    /** The holiday on this very day, or null: Season's scenes that last several days are marked on their main day. */
    static Season.Scene holiday(LocalDate day) {
        int m = day.getMonthValue(), dom = day.getDayOfMonth();
        Season.Scene s = Season.at(day);
        switch (s) {
            case NEW_YEAR: return m == 1 && dom == 1 ? s : null;
            case HALLOWEEN: return m == 10 && dom == 31 ? s : null;
            case CHRISTMAS: return m == 12 && dom == 25 ? s : null;
            case VALENTINE: return m == 2 && dom == 14 ? s : null;
            case EASTER: return day.equals(Season.easter(day.getYear())) ? s : null;
            case FIREWORKS: case PANCAKE_DAY: case MOTHERS_DAY: case ST_PATRICKS: case FATHERS_DAY:
                return s;
            default:
                return null;
        }
    }
}
