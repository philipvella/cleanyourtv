package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import com.gathin.tvhome.Season.Scene;

import java.time.LocalDate;

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
    public void fireworksOnBonfireNightAndNewYear() {
        assertEquals(Scene.FIREWORKS, on(11, 5));
        assertEquals(Scene.FIREWORKS, on(12, 31));
        assertEquals(Scene.FIREWORKS, on(1, 1));
        assertEquals(Scene.AUTUMN, on(11, 6));
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
        assertEquals(Scene.WINTER, Season.at(LocalDate.of(2028, 2, 29)));
    }

    @Test
    public void springAndSummer() {
        assertEquals(Scene.SPRING, on(3, 1));
        assertEquals(Scene.SPRING, on(5, 31));
        assertEquals(Scene.SUMMER, on(6, 1));
        assertEquals(Scene.SUMMER, on(8, 31));
    }

    @Test
    public void theKeyIsOffWhenEmptyFollowsTheDateOnAutoAndForcesANamedScene() {
        LocalDate day = LocalDate.of(2026, 10, 31);
        assertEquals(Scene.NONE, Season.pick("", day));
        assertEquals(Scene.NONE, Season.pick(null, day));
        assertEquals(Scene.NONE, Season.pick("easter", day));
        assertEquals(Scene.HALLOWEEN, Season.pick("auto", day));
        assertEquals(Scene.CHRISTMAS, Season.pick(" Christmas ", day));
        assertEquals(Scene.FIREWORKS, Season.pick("bonfire", day));
        assertEquals(Scene.FIREWORKS, Season.pick("new_year", day));
    }
}
