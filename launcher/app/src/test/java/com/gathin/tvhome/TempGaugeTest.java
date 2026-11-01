package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TempGaugeTest {
    @Test
    public void theDotSitsBetweenTheLowAndTheHigh() {
        assertEquals(0f, TempGauge.position(6, 6, 10), 0.001f);
        assertEquals(1f, TempGauge.position(10, 6, 10), 0.001f);
        assertEquals(0.5f, TempGauge.position(8, 6, 10), 0.001f);
        assertEquals(0.5f, TempGauge.position(7, 7, 7), 0.001f);
        assertEquals(1f, TempGauge.position(12, 6, 10), 0.001f);
    }

    @Test
    public void degreesAreReadFromTheAddOnsText() {
        assertEquals(Integer.valueOf(10), Welcome.wholeDegrees("10°C"));
        assertEquals(Integer.valueOf(-3), Welcome.wholeDegrees("-3"));
        assertEquals(Integer.valueOf(-3), Welcome.wholeDegrees("−3°C"));
        assertNull(Welcome.wholeDegrees(""));
    }
}
