package io.github.philipvella.vellaweather;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ForecastTest {
    private static final String[] TIMES = {"2026-11-01T09:00", "2026-11-01T10:00", "2026-11-01T11:00"};

    @Test
    public void theHourlyRowStartsAtTheHourNowIsIn() {
        assertEquals(1, Forecast.firstHour(TIMES, "2026-11-01T10:30"));
        assertEquals(1, Forecast.firstHour(TIMES, "2026-11-01T10:00"));
        assertEquals(0, Forecast.firstHour(TIMES, "2026-11-01T08:45"));
        assertEquals(3, Forecast.firstHour(TIMES, "2026-11-01T12:15"));
    }

    @Test
    public void timesAndWordsReadAsTheScreenShowsThem() {
        assertEquals("06:55", Forecast.clock("2026-11-01T06:55"));
        assertEquals("2026-11-01", Forecast.clock("2026-11-01"));
        assertEquals("Light rain", Forecast.capital("light rain"));
        assertEquals("", Forecast.capital(""));
    }
}
