package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gathin.tvhome.Season.Scene;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class SeasonScheduleTest {
    private static final Map<MonthDay, Scene> NONE = Collections.emptyMap();

    private static int index(SeasonSchedule s, Scene scene) {
        for (int i = 0; i < s.slots.size(); i++) if (s.slots.get(i).scene == scene) return i;
        throw new AssertionError(scene);
    }

    @Test
    public void theDefaultKeepsEveryDayOfTheOldCalendar() {
        // A leap year and an ordinary one, each with the household's own day on top.
        Map<MonthDay, Scene> own = new HashMap<>();
        own.put(MonthDay.of(4, 21), Scene.BIRTHDAY);
        for (int year : new int[] {2024, 2026}) {
            for (LocalDate d = LocalDate.of(year, 1, 1); d.getYear() == year; d = d.plusDays(1)) {
                Scene s = SeasonSchedule.DEFAULT.at(d, own);
                assertTrue(d.toString(), s != Scene.NONE);
            }
        }
        assertEquals(Scene.WINTER, SeasonSchedule.DEFAULT.at(LocalDate.of(2024, 2, 29), NONE));
        assertEquals(Scene.SPRING, SeasonSchedule.DEFAULT.at(LocalDate.of(2024, 3, 1), NONE));
        assertEquals(Scene.NEW_YEAR, SeasonSchedule.DEFAULT.at(LocalDate.of(2026, 1, 1), NONE));
        assertEquals(Scene.BIRTHDAY, SeasonSchedule.DEFAULT.at(LocalDate.of(2026, 4, 21), own));
    }

    @Test
    public void theSavedLineGivesTheSameScheduleBack() {
        SeasonSchedule s = SeasonSchedule.DEFAULT.moved(index(SeasonSchedule.DEFAULT, Scene.CHRISTMAS), -1)
                .toggled(index(SeasonSchedule.DEFAULT, Scene.SUMMER))
                .shifted(index(SeasonSchedule.DEFAULT, Scene.HALLOWEEN), SeasonSchedule.START, -3, 2026);
        assertEquals(s.key(), SeasonSchedule.parse(s.key()).key());
        assertSame(SeasonSchedule.DEFAULT, SeasonSchedule.parse(""));
        assertSame(SeasonSchedule.DEFAULT, SeasonSchedule.parse(null));
    }

    @Test
    public void theHigherRowWinsAnOverlap() {
        // Christmas sits above winter: 20 December is Christmas until winter is moved above it.
        LocalDate day = LocalDate.of(2026, 12, 20);
        SeasonSchedule s = SeasonSchedule.DEFAULT;
        assertEquals(Scene.CHRISTMAS, s.at(day, NONE));
        while (index(s, Scene.WINTER) > index(s, Scene.CHRISTMAS)) s = s.moved(index(s, Scene.WINTER), -1);
        assertEquals(Scene.WINTER, s.at(day, NONE));
    }

    @Test
    public void aSceneThatIsOffLetsTheNextRowPlay() {
        SeasonSchedule s = SeasonSchedule.DEFAULT.toggled(index(SeasonSchedule.DEFAULT, Scene.HALLOWEEN));
        assertEquals(Scene.AUTUMN, s.at(LocalDate.of(2026, 10, 31), NONE));
    }

    @Test
    public void datesMoveAndCanRunOverNewYear() {
        SeasonSchedule s = SeasonSchedule.DEFAULT;
        int christmas = index(s, Scene.CHRISTMAS);
        s = s.shifted(christmas, SeasonSchedule.END, 10, 2026);           // to 5 January
        assertEquals("12-01~01-05", s.slots.get(christmas).key().substring("christmas:".length()));
        // New Year is still above it on the 1st; the 3rd is Christmas's now.
        assertEquals(Scene.NEW_YEAR, s.at(LocalDate.of(2027, 1, 1), NONE));
        assertEquals(Scene.CHRISTMAS, s.at(LocalDate.of(2027, 1, 3), NONE));
        assertEquals(Scene.CHRISTMAS, s.at(LocalDate.of(2026, 1, 3), NONE));
    }

    @Test
    public void theEndOfFebruaryStaysTheEndOfFebruary() {
        SeasonSchedule s = SeasonSchedule.DEFAULT;
        int winter = index(s, Scene.WINTER);
        s = s.shifted(winter, SeasonSchedule.END, 1, 2026).shifted(winter, SeasonSchedule.END, -1, 2026);
        assertTrue(s.slots.get(winter).key().endsWith("~03-01-1"));
        assertTrue(s.slots.get(winter).covers(LocalDate.of(2028, 2, 29)));
        assertFalse(s.slots.get(winter).covers(LocalDate.of(2028, 3, 1)));
    }

    @Test
    public void easterDaysMoveWithEaster() {
        SeasonSchedule s = SeasonSchedule.DEFAULT;
        int easter = index(s, Scene.EASTER);
        s = s.shifted(easter, SeasonSchedule.START, -5, 2026);             // from Palm Sunday
        assertEquals(Scene.EASTER, s.at(Season.easter(2026).minusDays(7), NONE));
        assertEquals(Scene.EASTER, s.at(Season.easter(2027).minusDays(7), NONE));
    }

    @Test
    public void nonsenseDatesAreRefused() {
        SeasonSchedule s = SeasonSchedule.DEFAULT;
        int easter = index(s, Scene.EASTER);
        // An Easter run cannot end before it starts.
        assertSame(s, s.shifted(easter, SeasonSchedule.END, -10, 2026));
        // Nothing runs a whole year.
        int summer = index(s, Scene.SUMMER);
        SeasonSchedule t = s;
        for (int i = 0; i < 400; i++) t = t.shifted(summer, SeasonSchedule.END, 1, 2026);
        assertTrue(t.slots.get(summer).valid(2026));
        // The ends do not move past a row's first or last place.
        assertSame(s, s.moved(0, -1));
        assertSame(s, s.moved(s.slots.size() - 1, 1));
    }

    @Test
    public void aDamagedLineKeepsEveryScene() {
        SeasonSchedule s = SeasonSchedule.parse("summer:06-01~08-31,bogus,christmas:13-40~12-26,!own,winter:x");
        assertEquals(SeasonSchedule.DEFAULT.slots.size(), s.slots.size());
        assertEquals(Scene.SUMMER, s.slots.get(0).scene);
        assertFalse(s.slots.get(1).on);
        assertTrue(s.slots.get(1).own());
        // The household's own day is off, so the calendar plays.
        Map<MonthDay, Scene> own = Collections.singletonMap(MonthDay.of(4, 21), Scene.BIRTHDAY);
        assertEquals(Scene.SPRING, s.at(LocalDate.of(2026, 4, 21), own));
    }
}
