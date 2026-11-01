package io.github.philipvella.vellaweather;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NowTest {
    @Test
    public void theLineReadsAsHomeShowsIt() {
        assertEquals("22°C · light rain · wind 10 m/s", Now.line(21.6, 61, true, 9.7));
    }

    @Test
    public void belowFreezingRoundsTowardsTheNearestDegree() {
        assertEquals("-3°C · light snow · wind 2 m/s", Now.line(-2.6, 71, false, 1.6));
    }

    @Test
    public void clearIsSunnyByDayOnly() {
        assertEquals("sunny", Now.sky(0, true));
        assertEquals("clear", Now.sky(0, false));
    }

    @Test
    public void rainNearFreezingInDryAirIsSnow() {
        assertEquals(71, Now.snowIfCold(61, 1.0, 60));
        assertEquals(61, Now.snowIfCold(61, 8.0, 90));
    }

    @Test
    public void theLineSplitsIntoTheTemperatureAndTheWordsUnderIt() {
        assertEquals("22°C", Now.temp(21.6));
        assertEquals("light rain · wind 10 m/s", Now.summary(61, true, 9.7));
    }

    @Test
    public void eachKindOfWeatherHasItsIcon() {
        assertEquals("clear-night", Now.icon(1, false));
        assertEquals("partly-cloudy-day", Now.icon(2, true));
        assertEquals("cloudy", Now.icon(3, true));
        assertEquals("fog", Now.icon(48, true));
        assertEquals("drizzle", Now.icon(53, true));
        assertEquals("rain", Now.icon(81, true));
        assertEquals("snow", Now.icon(71, true));
        assertEquals("thunder", Now.icon(95, false));
    }

    @Test
    public void detailsLeadWithTheFeelsLikeTemperature() {
        org.junit.Assert.assertEquals("feels like 8° · wind 2 m/s", Now.details(7.6, 2.4));
        org.junit.Assert.assertEquals("feels like -3° · wind 0 m/s", Now.details(-2.6, 0));
    }
}
