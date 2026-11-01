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

    @Test
    public void theHouseholdsDatesComeFirst() {
        Map<MonthDay, Season.Scene> own = Season.parseDates(Arrays.asList("04-21=birthday", "11-05=anniversary"));
        Map<Integer, Season.Scene> nov = CalendarMonth.marks(YearMonth.of(2026, 11), own);
        assertEquals(Season.Scene.ANNIVERSARY, nov.get(5));
        assertEquals(1, nov.size());
        assertEquals(Season.Scene.BIRTHDAY, CalendarMonth.marks(YearMonth.of(2026, 4), own).get(21));
    }
}
