package com.gathin.tvhome;

import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * ui_font "roboto" (config.xml or a theme): Home's text in the TV's own system sans-serif (Roboto on Android TV),
 * the standard UI face of Android, instead of the bundled Outfit. Nothing is bundled; each text keeps the weight its
 * layout gave it. Off by default: {@link #create} returns null.
 */
final class HomeFont {
    static final String NAME = "roboto";
    private static final int[] WEIGHTS = {100, 300, 400, 500, 700, 900};
    /** The family names Android 8 knows for those weights (before Typeface.create with a weight, API 28). */
    private static final String[] FAMILIES = {"sans-serif-thin", "sans-serif-light", "sans-serif",
            "sans-serif-medium", "sans-serif", "sans-serif-black"};
    private final Typeface[] cache = new Typeface[WEIGHTS.length];

    private HomeFont() {
    }

    static HomeFont create(String uiFont) {
        return NAME.equalsIgnoreCase(uiFont == null ? "" : uiFont.trim()) ? new HomeFont() : null;
    }

    /** The system sans-serif at the weight nearest to {@code weight}. */
    Typeface at(int weight) {
        int best = 0;
        for (int i = 1; i < WEIGHTS.length; i++) {
            if (Math.abs(WEIGHTS[i] - weight) < Math.abs(WEIGHTS[best] - weight)) best = i;
        }
        if (cache[best] == null) {
            cache[best] = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? Typeface.create(Typeface.SANS_SERIF, WEIGHTS[best], false)
                    : Typeface.create(FAMILIES[best], WEIGHTS[best] >= 700 ? Typeface.BOLD : Typeface.NORMAL);
        }
        return cache[best];
    }

    /**
     * Swaps a text's typeface for the system sans-serif at the same weight. Outfit's own file reports 100 and is read
     * as regular; its extra-light (200) becomes Roboto Light, as Roboto Thin is too faint on a TV.
     */
    void apply(View v) {
        if (!(v instanceof TextView)) return;
        TextView t = (TextView) v;
        Typeface was = t.getTypeface();
        int weight = 400;
        if (was != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) weight = was.getWeight();
        if (weight <= 150) weight = 400;
        else if (weight < 300) weight = 300;
        if (was != null && was.isBold()) weight = Math.max(weight, 700);
        t.setTypeface(at(weight));
    }

    /** Every text under {@code root}, for a layout inflated before the config was read. */
    void applyAll(View root) {
        apply(root);
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) applyAll(g.getChildAt(i));
        }
    }
}
