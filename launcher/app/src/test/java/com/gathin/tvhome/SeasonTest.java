package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.Season.Scene;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Arrays;
import java.util.Map;

import org.junit.Test;

public class SeasonTest {
    private static Scene on(int month, int day) {
        return Season.at(LocalDate.of(2026, month, day));
    }

    @Test
    public void autumnRunsFromSeptemberToHalloweenWeekAndThroughNovember() {
        assertEquals(Scene.AUTUMN, on(9, 1));
        assertEquals(Scene.AUTUMN, on(10, 24));
        assertEquals(Scene.AUTUMN, on(11, 1));
        assertEquals(Scene.AUTUMN, on(11, 30));
    }

    @Test
    public void halloweenIsTheLastWeekOfOctober() {
        assertEquals(Scene.HALLOWEEN, on(10, 25));
        assertEquals(Scene.HALLOWEEN, on(10, 31));
    }

    @Test
    public void fireworksOnBonfireNightAndItsOwnSceneAtNewYear() {
        assertEquals(Scene.FIREWORKS, on(11, 5));
        assertEquals(Scene.AUTUMN, on(11, 6));
        assertEquals(Scene.NEW_YEAR, on(12, 31));
        assertEquals(Scene.NEW_YEAR, on(1, 1));
    }

    @Test
    public void christmasUntilBoxingDayThenWinterSnow() {
        assertEquals(Scene.CHRISTMAS, on(12, 1));
        assertEquals(Scene.CHRISTMAS, on(12, 26));
        assertEquals(Scene.WINTER, on(12, 27));
        assertEquals(Scene.WINTER, on(1, 2));
        assertEquals(Scene.WINTER, on(2, 6));
    }

    @Test
    public void valentinesWeekThenWinterAgain() {
        assertEquals(Scene.VALENTINE, on(2, 7));
        assertEquals(Scene.VALENTINE, on(2, 14));
        assertEquals(Scene.WINTER, on(2, 15));
        assertEquals(Scene.WINTER, Season.at(LocalDate.of(2024, 2, 29)));
        // In 2028 the leap day is Shrove Tuesday (Easter is 16 April).
        assertEquals(Scene.PANCAKE_DAY, Season.at(LocalDate.of(2028, 2, 29)));
    }

    @Test
    public void springAndSummer() {
        assertEquals(Scene.SPRING, on(3, 1));
        assertEquals(Scene.SPRING, on(5, 31));
        assertEquals(Scene.SUMMER, on(6, 1));
        assertEquals(Scene.SUMMER, on(8, 31));
    }

    @Test
    public void easterSundayByTheGregorianComputus() {
        assertEquals(LocalDate.of(2024, 3, 31), Season.easter(2024));
        assertEquals(LocalDate.of(2025, 4, 20), Season.easter(2025));
        assertEquals(LocalDate.of(2026, 4, 5), Season.easter(2026));
        assertEquals(LocalDate.of(2027, 3, 28), Season.easter(2027));
        assertEquals(LocalDate.of(2038, 4, 25), Season.easter(2038));
    }

    @Test
    public void easterRunsFromGoodFridayToEasterMonday() {
        assertEquals(Scene.SPRING, on(4, 2));
        assertEquals(Scene.EASTER, on(4, 3));
        assertEquals(Scene.EASTER, on(4, 5));
        assertEquals(Scene.EASTER, on(4, 6));
        assertEquals(Scene.SPRING, on(4, 7));
    }

    @Test
    public void pancakeDayAndMotheringSundayFollowEaster() {
        assertEquals(Scene.PANCAKE_DAY, on(2, 17));                 // 2026: 47 days before 5 April
        assertEquals(Scene.MOTHERS_DAY, on(3, 15));                 // 2026: three weeks before Easter
        assertEquals(Scene.PANCAKE_DAY, Season.at(LocalDate.of(2027, 2, 9)));
        assertEquals(Scene.MOTHERS_DAY, Season.at(LocalDate.of(2027, 3, 7)));
    }

    @Test
    public void stPatricksAndFathersDay() {
        assertEquals(Scene.ST_PATRICKS, on(3, 17));
        assertEquals(LocalDate.of(2026, 6, 21), Season.fathersDay(2026));
        assertEquals(LocalDate.of(2027, 6, 20), Season.fathersDay(2027));
        assertEquals(Scene.FATHERS_DAY, on(6, 21));
        assertEquals(Scene.SUMMER, on(6, 20));
    }

    @Test
    public void theHouseholdsOwnDatesWinOverEverything() {
        Map<MonthDay, Scene> own = Season.parseDates(Arrays.asList(
                "04-21=birthday", "04-28=birthday", "04-16=anniversary", "02-04=anniversary",
                "13-40=birthday", "05-01=nonsense", "no equals"));
        assertEquals(4, own.size());
        assertEquals(Scene.BIRTHDAY, Season.at(LocalDate.of(2026, 4, 21), own));
        assertEquals(Scene.BIRTHDAY, Season.at(LocalDate.of(2026, 4, 28), own));
        assertEquals(Scene.ANNIVERSARY, Season.at(LocalDate.of(2026, 4, 16), own));
        assertEquals(Scene.ANNIVERSARY, Season.at(LocalDate.of(2027, 2, 4), own));
        // 21 April 2025 was Easter Monday: the birthday wins.
        assertEquals(Scene.BIRTHDAY, Season.at(LocalDate.of(2025, 4, 21), own));
        assertEquals(Scene.EASTER, Season.at(LocalDate.of(2025, 4, 21)));
        assertEquals(Scene.BIRTHDAY, Season.pick("auto", LocalDate.of(2026, 4, 21), own));
    }

    @Test
    public void theKeyIsOffWhenEmptyFollowsTheDateOnAutoAndForcesANamedScene() {
        LocalDate day = LocalDate.of(2026, 10, 31);
        assertEquals(Scene.NONE, Season.pick("", day));
        assertEquals(Scene.NONE, Season.pick(null, day));
        assertEquals(Scene.NONE, Season.pick("off", day));
        assertEquals(Scene.NONE, Season.pick("easterish", day));
        assertEquals(Scene.HALLOWEEN, Season.pick("auto", day));
        assertEquals(Scene.CHRISTMAS, Season.pick(" Christmas ", day));
        assertEquals(Scene.FIREWORKS, Season.pick("bonfire", day));
        assertEquals(Scene.NEW_YEAR, Season.pick("new_year", day));
    }

    @Test
    public void everySceneTheSettingsRowOffersRoundTripsThroughItsStoredValue() {
        LocalDate day = LocalDate.of(2026, 7, 1);
        for (Scene s : Season.PICKABLE) assertEquals(s, Season.pick(Season.keyOf(s), day));
        assertEquals(Scene.SUMMER, Season.pick(Season.AUTO, day));
        assertTrue(Season.PICKABLE.length == Scene.values().length - 1);
    }
}
