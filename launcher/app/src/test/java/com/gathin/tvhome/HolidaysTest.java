package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.util.Map;

import org.junit.Test;

/** Checked against gov.uk/bank-holidays. */
public class HolidaysTest {
    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    @Test
    public void englandAndWales2026And2027() {
        Map<LocalDate, String> y26 = Holidays.of(Holidays.ENGLAND, 2026);
        assertEquals(8, y26.size());
        assertEquals("New Year's Day", y26.get(d(2026, 1, 1)));
        assertEquals("Good Friday", y26.get(d(2026, 4, 3)));
        assertEquals("Easter Monday", y26.get(d(2026, 4, 6)));
        assertEquals("Early May bank holiday", y26.get(d(2026, 5, 4)));
        assertEquals("Spring bank holiday", y26.get(d(2026, 5, 25)));
        assertEquals("Summer bank holiday", y26.get(d(2026, 8, 31)));
        assertEquals("Christmas Day", y26.get(d(2026, 12, 25)));
        assertEquals("Boxing Day (substitute day)", y26.get(d(2026, 12, 28)));
        Map<LocalDate, String> y27 = Holidays.of(Holidays.ENGLAND, 2027);
        assertEquals("Good Friday", y27.get(d(2027, 3, 26)));
        assertEquals("Easter Monday", y27.get(d(2027, 3, 29)));
        assertEquals("Christmas Day (substitute day)", y27.get(d(2027, 12, 27)));
        assertEquals("Boxing Day (substitute day)", y27.get(d(2027, 12, 28)));
    }

    @Test
    public void scotlandAndNorthernIrelandHaveTheirOwn() {
        Map<LocalDate, String> sc = Holidays.of(Holidays.SCOTLAND, 2027);
        assertEquals("2nd January (substitute day)", sc.get(d(2027, 1, 4)));
        assertEquals("Summer bank holiday", sc.get(d(2027, 8, 2)));
        assertEquals("St Andrew's Day", sc.get(d(2027, 11, 30)));
        assertEquals(null, sc.get(d(2027, 3, 29)));          // no Easter Monday
        Map<LocalDate, String> ni = Holidays.of(Holidays.NORTHERN_IRELAND, 2026);
        assertEquals("St Patrick's Day", ni.get(d(2026, 3, 17)));
        assertEquals("Battle of the Boyne (Orangemen's Day) (substitute day)", ni.get(d(2026, 7, 13)));
    }

    @Test
    public void theOneOffDaysAreThere() {
        Map<LocalDate, String> y22 = Holidays.of(Holidays.ENGLAND, 2022);
        assertEquals("Spring bank holiday", y22.get(d(2022, 6, 2)));
        assertEquals("Platinum Jubilee bank holiday", y22.get(d(2022, 6, 3)));
        assertEquals("State Funeral of Queen Elizabeth II", y22.get(d(2022, 9, 19)));
        assertEquals("Early May bank holiday", Holidays.of(Holidays.ENGLAND, 2020).get(d(2020, 5, 8)));
    }

    @Test
    public void theRegionComesFromThePlaceOnUkTime() {
        assertEquals(Holidays.ENGLAND, Holidays.region("auto", "Europe/London", "51.755", "-0.336"));   // St Albans
        assertEquals(Holidays.SCOTLAND, Holidays.region("auto", "Europe/London", "55.95", "-3.19"));    // Edinburgh
        assertEquals(Holidays.NORTHERN_IRELAND, Holidays.region("auto", "Europe/London", "54.6", "-5.93"));  // Belfast
        assertEquals(Holidays.ENGLAND, Holidays.region("auto", "Europe/London", "55.77", "-2.0"));     // Berwick
        assertEquals("", Holidays.region("auto", "Europe/Malta", "35.9", "14.5"));
        assertEquals(Holidays.SCOTLAND, Holidays.region("scotland", "Europe/Malta", "", ""));
        assertEquals("", Holidays.region("off", "Europe/London", "51.7", "-0.3"));
    }
}
