package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RingGaugeTest {
    @Test
    public void theDotSitsBetweenTheLowAndTheHigh() {
        assertEquals(0f, RingGauge.position(6, 6, 10), 0.001f);
        assertEquals(1f, RingGauge.position(10, 6, 10), 0.001f);
        assertEquals(0.5f, RingGauge.position(8, 6, 10), 0.001f);
        assertEquals(0.5f, RingGauge.position(7, 7, 7), 0.001f);
        assertEquals(1f, RingGauge.position(12, 6, 10), 0.001f);
    }

    @Test
    public void degreesAreReadFromTheAddOnsText() {
        assertEquals(Integer.valueOf(10), Welcome.wholeDegrees("10°C"));
        assertEquals(Integer.valueOf(-3), Welcome.wholeDegrees("-3"));
        assertEquals(Integer.valueOf(-3), Welcome.wholeDegrees("−3°C"));
        assertNull(Welcome.wholeDegrees(""));
    }

    @Test
    public void capitalsLeaveTheUnitsAlone() {
        java.util.Locale uk = java.util.Locale.UK;
        assertEquals("FEELS LIKE 8° · WIND 3 m/s", Welcome.capitalsKeepingUnits("feels like 8° · wind 3 m/s", uk));
        assertEquals("WIND 12 km/h · 1013 hPa · 4 mm · 10°C · 80%",
                Welcome.capitalsKeepingUnits("wind 12 km/h · 1013 hPa · 4 mm · 10°C · 80%", uk));
        assertEquals("LIGHT RAIN · 5 MILES", Welcome.capitalsKeepingUnits("light rain · 5 miles", uk));
        assertEquals("WIND 3MPH", Welcome.capitalsKeepingUnits("wind 3MPH", uk));
    }
}
