package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

public class CalendarMonthTest {
    @Test
    public void theFirstDaySitsInItsWeekdaysColumn() {
        YearMonth nov = YearMonth.of(2026, 11);   // 1 November 2026 is a Sunday
        assertEquals(6, CalendarMonth.firstColumn(nov, DayOfWeek.MONDAY));
        assertEquals(0, CalendarMonth.firstColumn(nov, DayOfWeek.SUNDAY));
        assertEquals(6, CalendarMonth.rows(nov, DayOfWeek.MONDAY));
        assertEquals(5, CalendarMonth.rows(nov, DayOfWeek.SUNDAY));
        assertEquals(4, CalendarMonth.rows(YearMonth.of(2027, 2), DayOfWeek.MONDAY));   // Feb 2027 starts on a Monday
    }

    @Test
    public void holidaysAreMarkedOnTheirOneDay() {
        assertEquals(Season.Scene.FIREWORKS, CalendarMonth.holiday(LocalDate.of(2026, 11, 5)));
        assertEquals(Season.Scene.HALLOWEEN, CalendarMonth.holiday(LocalDate.of(2026, 10, 31)));
        assertNull(CalendarMonth.holiday(LocalDate.of(2026, 10, 30)));
        assertEquals(Season.Scene.CHRISTMAS, CalendarMonth.holiday(LocalDate.of(2026, 12, 25)));
        assertNull(CalendarMonth.holiday(LocalDate.of(2026, 12, 24)));
        assertEquals(Season.Scene.NEW_YEAR, CalendarMonth.holiday(LocalDate.of(2027, 1, 1)));
        assertNull(CalendarMonth.holiday(LocalDate.of(2026, 12, 31)));
        assertEquals(Season.Scene.EASTER, CalendarMonth.holiday(Season.easter(2027)));
        assertNull(CalendarMonth.holiday(Season.easter(2027).plusDays(1)));
    }

    private static final String[] NAMES = {"", "", "Halloween", "Bonfire Night", "Christmas Day", "", "", "", "",
            "New Year's Day", "", "", "Easter Sunday", "Mother's Day", "Father's Day", "Birthday", "Anniversary"};

    @Test
    public void theHouseholdsDatesComeFirstAndANameIsGivenOnce() {
        Map<MonthDay, Season.Scene> own = Season.parseDates(Arrays.asList("04-21=birthday", "11-05=anniversary"));
        Map<Integer, java.util.List<CalendarMonth.Mark>> nov = CalendarMonth.marks(YearMonth.of(2026, 11), own, NAMES,
                java.util.Collections.emptyMap());
        assertEquals("Anniversary", nov.get(5).get(0).name);
        assertEquals("Bonfire Night", nov.get(5).get(1).name);
        Map<Integer, java.util.List<CalendarMonth.Mark>> dec = CalendarMonth.marks(YearMonth.of(2026, 12), own, NAMES,
                Holidays.of(Holidays.ENGLAND, 2026));
        assertEquals(1, dec.get(25).size());                 // the bank holiday and Season's Christmas are one name
        assertEquals(true, dec.get(25).get(0).bank);
        assertEquals("Boxing Day (substitute day)", dec.get(28).get(0).name);
    }
}
