package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Locale;

/**
 * seasonal_scenes (config.xml or a theme): which animated scene SeasonView plays on Home, picked from the date
 * (northern hemisphere, UK dates), or forced by naming one. Pure, so the calendar is unit-tested (SeasonTest).
 */
final class Season {
    enum Scene { NONE, AUTUMN, HALLOWEEN, FIREWORKS, CHRISTMAS, WINTER, VALENTINE, SPRING, SUMMER }

    static final String AUTO = "auto";

    private Season() {
    }

    /**
     * The scene for {@code key} on {@code day}: "auto" follows the calendar; a scene's name ("christmas", "autumn",
     * also "bonfire" and "new_year" for the fireworks) forces it; empty or unknown is NONE (off).
     */
    static Scene pick(String key, LocalDate day) {
        String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) return Scene.NONE;
        if (AUTO.equals(k)) return at(day);
        switch (k) {
            case "bonfire": case "new_year": case "fireworks": return Scene.FIREWORKS;
            default:
                for (Scene s : Scene.values()) if (s.name().toLowerCase(Locale.ROOT).equals(k)) return s;
                return Scene.NONE;
        }
    }

    /** The calendar. */
    static Scene at(LocalDate day) {
        MonthDay d = MonthDay.from(day);
        int m = day.getMonthValue(), dom = day.getDayOfMonth();
        if (m == 12 && dom == 31 || m == 1 && dom == 1) return Scene.FIREWORKS;          // New Year
        if (m == 11 && dom == 5) return Scene.FIREWORKS;                                    // Bonfire Night
        if (m == 10 && dom >= 25) return Scene.HALLOWEEN;
        if (m == 12 && dom <= 26) return Scene.CHRISTMAS;
        if (m == 2 && dom >= 7 && dom <= 14) return Scene.VALENTINE;
        if (m == 12 || m == 1 || m == 2) return Scene.WINTER;
        if (m >= 3 && m <= 5) return Scene.SPRING;
        if (m >= 6 && m <= 8) return Scene.SUMMER;
        return d.isBefore(MonthDay.of(12, 1)) ? Scene.AUTUMN : Scene.WINTER;               // September to November
    }
}
