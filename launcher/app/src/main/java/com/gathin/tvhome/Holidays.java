package com.gathin.tvhome;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.TreeMap;

/**
 * public_holidays: the bank holidays of the TV's part of the UK, worked out offline from the rules (the Banking and
 * Financial Dealings Act's days, the proclaimed Monday holidays, and the substitute weekday when one falls on a
 * weekend), with the one-off days since 2020. The region comes from the time zone and the theme's latitude and
 * longitude, or is named. Pure, so it runs in JVM tests.
 */
final class Holidays {
    static final String AUTO = "auto", OFF = "off", ENGLAND = "england-wales", SCOTLAND = "scotland",
            NORTHERN_IRELAND = "northern-ireland";

    private Holidays() {
    }

    /**
     * The region for a public_holidays value: a named one as it is, "auto" from the place (Scotland north of the
     * border, Northern Ireland west of the Irish Sea, else England and Wales) while the clock is on UK time; "" when
     * off, unknown, or outside the UK.
     */
    static String region(String key, String zone, String latitude, String longitude) {
        String k = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
        if (k.equals(ENGLAND) || k.equals(SCOTLAND) || k.equals(NORTHERN_IRELAND)) return k;
        if (!k.equals(AUTO)) return "";
        if (!"Europe/London".equals(zone) && !"Europe/Belfast".equals(zone) && !"GB".equals(zone)) return "";
        try {
            double lat = Double.parseDouble(latitude.trim()), lon = Double.parseDouble(longitude.trim());
            if (lat >= 54.0 && lat <= 55.4 && lon <= -5.4 && lon >= -8.3) return NORTHERN_IRELAND;
            if (lat >= 55.8 || (lat >= 55.0 && lon <= -2.1)) return SCOTLAND;
        } catch (RuntimeException noPlace) {
            // UK time without a place: England and Wales, where most of the UK lives.
        }
        return ENGLAND;
    }

    /** The region's bank holidays in a year, by date, with their names. */
    static Map<LocalDate, String> of(String region, int year) {
        Map<LocalDate, String> out = new TreeMap<>();
        if (region.isEmpty()) return out;
        boolean scotland = SCOTLAND.equals(region), ni = NORTHERN_IRELAND.equals(region);
        LocalDate easter = Season.easter(year);

        LocalDate newYear = LocalDate.of(year, 1, 1);
        if (scotland) {
            // 1 and 2 January: a weekend day moves to the next weekday that isn't the other.
            LocalDate first = weekday(newYear), second = LocalDate.of(year, 1, 2);
            second = weekday(second);
            if (!second.isAfter(first)) second = weekday(first.plusDays(1));
            out.put(first, named("New Year's Day", first, newYear));
            out.put(second, named("2nd January", second, LocalDate.of(year, 1, 2)));
        } else {
            moved(out, newYear, "New Year's Day");
        }
        if (ni) moved(out, LocalDate.of(year, 3, 17), "St Patrick's Day");
        out.put(easter.minusDays(2), "Good Friday");
        if (!scotland) out.put(easter.plusDays(1), "Easter Monday");

        LocalDate earlyMay = LocalDate.of(year, 5, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        if (year == 2020) earlyMay = LocalDate.of(2020, 5, 8);          // VE Day, moved from the Monday
        out.put(earlyMay, "Early May bank holiday");
        LocalDate spring = LocalDate.of(year, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
        if (year == 2022) spring = LocalDate.of(2022, 6, 2);            // moved for the Platinum Jubilee
        out.put(spring, "Spring bank holiday");
        if (year == 2022) out.put(LocalDate.of(2022, 6, 3), "Platinum Jubilee bank holiday");
        if (year == 2022) out.put(LocalDate.of(2022, 9, 19), "State Funeral of Queen Elizabeth II");
        if (year == 2023) out.put(LocalDate.of(2023, 5, 8), "Coronation bank holiday");

        if (ni) moved(out, LocalDate.of(year, 7, 12), "Battle of the Boyne (Orangemen's Day)");
        LocalDate summer = scotland
                ? LocalDate.of(year, 8, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
                : LocalDate.of(year, 8, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY));
        out.put(summer, "Summer bank holiday");
        if (scotland) moved(out, LocalDate.of(year, 11, 30), "St Andrew's Day");

        // Christmas and Boxing Day: on a weekend they move to the next weekdays, Christmas first.
        LocalDate christmas = LocalDate.of(year, Month.DECEMBER, 25), boxing = christmas.plusDays(1);
        LocalDate c = weekday(christmas), b = weekday(boxing);
        if (!b.isAfter(c)) b = weekday(c.plusDays(1));
        out.put(c, named("Christmas Day", c, christmas));
        out.put(b, named("Boxing Day", b, boxing));
        return out;
    }

    /** A holiday on its weekday, or the Monday after a weekend, named as gov.uk names a moved one. */
    private static void moved(Map<LocalDate, String> out, LocalDate day, String name) {
        LocalDate on = weekday(day);
        out.put(on, named(name, on, day));
    }

    private static String named(String name, LocalDate on, LocalDate day) {
        return on.equals(day) ? name : name + " (substitute day)";
    }

    /** The day itself, or the Monday after when it is a Saturday or Sunday. */
    private static LocalDate weekday(LocalDate d) {
        DayOfWeek w = d.getDayOfWeek();
        return w == DayOfWeek.SATURDAY ? d.plusDays(2) : w == DayOfWeek.SUNDAY ? d.plusDays(1) : d;
    }
}
