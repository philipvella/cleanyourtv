package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * A welcome screen, every part of it off unless a theme or config.xml turns it on:
 * <ul>
 * <li>hero_text: a large headline centred in the upper third, in capitals; hero_caption: a smaller line just
 * above the dock band. Both in the bundled Cormorant Garamond, with a soft shadow, and both ride up and fade out
 * as the grid scrolls under them.</li>
 * <li>header_bottom: the clock and date at the bottom right, the greeting hidden; the pills stay at the top.</li>
 * <li>pill_labels_only: the top pills as text-only chips.</li>
 * <li>weather_source: one weather line and its place at the bottom left, from an add-on (WeatherLine). When the
 * add-on has a forecast screen ({@value #FORECAST}), Down from the app row focuses the weather and OK opens it.</li>
 * </ul>
 * With none of them set, {@link #attach} returns null and Home is exactly as before: no view is added and no font is
 * loaded.
 */
final class Welcome {
    /** Room kept free under the dock for the bottom clock and the weather, in dp. */
    static final float BOTTOM_STRIP_DP = 92;
    static final float BOTTOM_STRIP_STACKED_DP = 118;
    /** The bottom clock and weather sit this far above the screen's edge, in dp. */
    static final float BOTTOM_MARGIN_DP = 30;
    /** Side margin of the bottom blocks, in dp: the header's own 56dp padding plus the clock's 4dp inset. */
    static final float SIDE_MARGIN_DP = 60;
    static final float HERO_MAX_SP = 60, HERO_MIN_SP = 40, CAPTION_SP = 34;
    /** The headline never starts above this, in dp: clear of the top-right pills (26dp padding and 42dp pills). */
    static final float HERO_MIN_TOP_DP = 88;
    /** The highest content_lift can take the hero: clear of the top bar's 34 to 42dp buttons. */
    static final float HERO_LIFTED_MIN_TOP_DP = 64;
    /** content_lift in px. */
    private int lift;
    /** hero_line_height, percent. */
    private int lineHeight = 95;
    /** The headline's centre, as a share of the screen's height (the middle of the upper third is 1/6). */
    static final float HERO_CENTRE = 0.21f;
    /** Gap between the caption's baseline area and the dock band's top edge, in dp. */
    static final float CAPTION_GAP_DP = 22;
    static final String CAPTION_BOTTOM = "bottom", CAPTION_UNDER_HERO = "under_hero";
    /** under_hero: the gap between the headline and the caption, in dp. */
    static final float CAPTION_UNDER_GAP_DP = 14;
    /** caption_position "bottom" and "under_hero": the caption's size, level with the clock's 24sp. */
    static final float CAPTION_BOTTOM_SP = 24;
    /** The headline's first line, as a share of the others' size. */
    static final float HERO_FIRST_LINE = 0.7f;

    private final Activity activity;
    private final float density;
    /** ui_font "roboto", or null for the launcher's Outfit. */
    private HomeFont inter;
    private TextView headline, caption;
    /** caption_position "under_hero": the headline and caption as one centred block; null otherwise. */
    private LinearLayout heroGroup;
    /** caption_position "bottom": the caption in the bottom status bar; null otherwise. */
    private TextView bottomCaption;
    /** The PlainTextFrames round the bottom caption, the bottom clock and the weather; null when not shown. */
    private View captionFrame, clockFrame, weatherFrame;
    /** Room round each framed block for its blurred shadow, in dp. */
    static final float GLOW_ROOM_DP = 16;
    /** hero_font: the headline's and caption's face; null for the launcher's font. */
    private Typeface heroFace;
    private LinearLayout weatherBlock;
    private TextView weatherText, weatherSummary, weatherPlace;
    /** Today's range as a round gauge, when the add-on gives "high", "low", "sky" and "details". */
    private RingGauge weatherGauge;
    /** clock_gauge: the time in a ring of the day or night that is left, and the row holding it beside the date. */
    private RingGauge clockGauge;
    private LinearLayout clockGaugeRow;
    /** weather_place and weather_mono (Config). */
    private boolean showPlace = true, mono;
    /** The clock's own line (the time and AM/PM), hidden while the ring shows the time. */
    private View clockLine;
    private static final float GAUGE_DP = 84, GAUGE_CENTRE_SP = 22;
    /** "15:57" is wider than "10°": smaller, so it stays inside the ring. */
    private static final float CLOCK_CENTRE_SP = 18;
    /** The clock's ring is wider than tall, so the end labels ("06:54") fit beside the same size of ring. */
    private static final float CLOCK_GAUGE_WIDTH_DP = 100;
    /** The icon now beside the line, by name; empty for none. */
    private String weatherIcon = "";
    private WeatherLine weather;
    /** weather_source: the add-on's forecast screen; null when it has none, and the weather is then not focusable. */
    static final String FORECAST = "com.gathin.tvhome.action.WEATHER_FORECAST";
    private Intent forecast;
    /** Up from the focused weather: back to the app row (MainActivity). */
    private Runnable weatherUp;
    /** next_sun_time: the line under the date, the place, and the minute tick that keeps it right; null when off. */
    private TextView sunLine;
    private double sunLat, sunLon;
    private Handler main;
    private final Runnable sunTick = this::sunTick;

    private Welcome(Activity activity) {
        this.activity = activity;
        this.density = activity.getResources().getDisplayMetrics().density;
    }

    static boolean wanted(Config cfg) {
        return !cfg.heroText.isEmpty() || !cfg.heroCaption.isEmpty() || cfg.headerBottom || cfg.pillLabelsOnly
                || !cfg.weatherSource.isEmpty() || cfg.smallPills
                || ((cfg.nextSunTime || cfg.clockGauge) && !cfg.latitude.isEmpty());
    }

    /**
     * Sets up what the config asks for. Call once from onCreate, after the header and pills are set up (so the
     * text-only pills and the moved clock win over the earlier layout).
     */
    static Welcome attach(Activity activity, Config cfg, FrameLayout root, RecyclerView grid, Handler main,
                          HomeFont inter) {
        if (!wanted(cfg)) return null;
        Welcome w = new Welcome(activity);
        w.inter = inter;
        View content = (View) grid.getParent().getParent();   // the header-and-grid column, a child of root
        int at = root.indexOfChild(content);
        boolean bottomStrip = cfg.headerBottom || !cfg.weatherSource.isEmpty();
        if (bottomStrip) {
            // The grid ends above the strip, so the dock (pushed to the grid's bottom) clears the clock and weather.
            View wrapper = (View) grid.getParent();
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) wrapper.getLayoutParams();
            // The clock stacked over the date (date_below_clock) is taller than the clock and date side by side.
            float strip = cfg.headerBottom && cfg.dateBelowClock ? BOTTOM_STRIP_STACKED_DP : BOTTOM_STRIP_DP;
            // content_lift raises the dock with the rest; the hero follows in addHero.
            lp.bottomMargin = Math.round((strip + cfg.contentLift) * w.density);
            wrapper.setLayoutParams(lp);
        }
        w.lift = Math.round(cfg.contentLift * w.density);
        w.lineHeight = cfg.heroLineHeight;
        if (!cfg.heroText.isEmpty() || !cfg.heroCaption.isEmpty()) {
            w.heroFace = "montserrat".equalsIgnoreCase(cfg.heroFont) ? w.font(R.font.montserrat_light) : null;
            w.addHero(root, at, grid, cfg.heroText, cfg.heroCaption, cfg.captionPosition.trim().toLowerCase(Locale.ROOT));
            at = root.indexOfChild(content);
        }
        if (cfg.headerBottom) w.moveClockToBottom(root, at, cfg.dateBelowClock);
        if (labelsOnly(cfg)) {
            w.labelsOnly(R.id.btnInputs, R.id.btnWifi, R.id.btnVpn, R.id.btnSettings);
        }
        if (cfg.smallPills) w.smallPills();
        if (cfg.nextSunTime) w.addSunLine(cfg, main);
        if (cfg.clockGauge && cfg.headerBottom) w.addClockGauge(cfg, root, main);
        if (!cfg.weatherSource.isEmpty()) {
            w.showPlace = cfg.weatherPlace;
            w.mono = cfg.weatherMono;
            w.weather = WeatherLine.create(activity, cfg.weatherSource, main, w::showWeather);
            if (w.weather != null) w.addWeather(root, root.indexOfChild(content));
        }
        // Each block drawn through a plain canvas, so High contrast text does not stroke it (PlainTextFrame).
        if (w.heroGroup != null) w.plain(w.heroGroup);
        else if (w.headline != null) w.plain(w.headline);
        if (w.bottomCaption != null) w.captionFrame = w.plain(w.bottomCaption);
        if (cfg.headerBottom) w.clockFrame = w.plain(w.clockGaugeRow != null ? w.clockGaugeRow : activity.findViewById(R.id.leftBlock));
        if (w.weatherBlock != null) w.weatherFrame = w.plain(w.weatherBlock);
        if (w.weatherBlock != null) w.forecast = w.forecastIntent(cfg.weatherSource.trim());
        if (w.forecast != null) w.makeWeatherOpen();
        return w;
    }

    /**
     * With the bottom strip, the room MainActivity leaves under the dock: none, so the dock sits on the strip and the
     * row under it is clipped at the grid's edge instead of peeking out above the clock. The tiles' own name space
     * under the dock is the gap.
     */
    static float bottomRoomDp(Config cfg, float fallback) {
        return cfg.headerBottom || !cfg.weatherSource.isEmpty() ? 0 : fallback;
    }

    /** pill_labels_only is on: Wi-Fi updates must not put the network icon back. */
    static boolean labelsOnly(Config cfg) {
        return cfg.pillLabelsOnly && !cfg.topMenu && !cfg.iconOnlyButtons && !cfg.iconOnlyInputs && !cfg.festival.active(java.time.LocalDate.now());
    }

    // ---- hero headline and caption -------------------------------------------------------------------

    private void addHero(FrameLayout root, int at, RecyclerView grid, String text, String captionText,
                         String captionPosition) {
        boolean captionAtBottom = CAPTION_BOTTOM.equals(captionPosition);
        if (!text.isEmpty()) {
            headline = shadowed(new TextView(activity));
            headline.setTypeface(heroFace != null ? heroFace : inter != null ? inter.at(300) : font(R.font.outfit_light));
            // Display type sits tight, as on Apple's screens; the theme's own capitals and line breaks are kept.
            headline.setLetterSpacing(-0.02f);
            headline.setMaxLines(3);
            headline.setLineSpacing(0, lineHeight / 100f);
            headline.setIncludeFontPadding(false);
            headline.setGravity(Gravity.CENTER_HORIZONTAL);
            headline.setText(firstLineSmaller(text.replace("\\n", "\n")));
            int screenW = activity.getResources().getDisplayMetrics().widthPixels;
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            fit(headline, screenW - Math.round(160 * density));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            // Centre the block of lines on HERO_CENTRE of the screen's height.
            int lines = text.replace("\\n", "\n").split("\n", -1).length;
            float block = headline.getLineHeight() * Math.min(3, lines);
            lp.topMargin = Math.max(Math.round(HERO_MIN_TOP_DP * density), Math.round(screenH * HERO_CENTRE - block * 0.5f));
            // content_lift: up with the dock, but never under the top bar's buttons.
            lp.topMargin = Math.max(Math.round(HERO_LIFTED_MIN_TOP_DP * density), lp.topMargin - lift);
            root.addView(headline, at++, lp);
        }
        if (!captionText.isEmpty()) {
            caption = shadowed(new TextView(activity));
            caption.setTypeface(heroFace != null ? heroFace : inter != null ? inter.at(400) : font(R.font.outfit_light));
            caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, CAPTION_SP);
            caption.setLetterSpacing(-0.01f);
            caption.setSingleLine(true);
            caption.setIncludeFontPadding(false);
            caption.setText(captionText);
            if (CAPTION_UNDER_HERO.equals(captionPosition) && headline != null) {
                // under_hero: right under the headline, one centred block with it, so the two stay together.
                smallCaption(caption);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) headline.getLayoutParams();
                int index = root.indexOfChild(headline);
                root.removeView(headline);
                heroGroup = new LinearLayout(activity);
                heroGroup.setOrientation(LinearLayout.VERTICAL);
                heroGroup.setGravity(Gravity.CENTER_HORIZONTAL);
                heroGroup.addView(headline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.topMargin = Math.round(CAPTION_UNDER_GAP_DP * density);
                heroGroup.addView(caption, clp);
                root.addView(heroGroup, index, lp);
                caption = null;   // fades with the group, not moved on its own
            } else if (captionAtBottom) {
                // bottom: the middle column of the bottom status bar (weather, caption, clock), at the scale and
                // weight of those blocks rather than the headline's.
                smallCaption(caption);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                lp.bottomMargin = Math.round(BOTTOM_MARGIN_DP * density);
                root.addView(caption, at, lp);
                bottomCaption = caption;
                caption = null;   // not moved or faded with the grid
                // Level with the midpoint of the weather and clock blocks, whichever are shown, as they change.
                root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> centreBottomCaption());
            } else {
                caption.setVisibility(View.INVISIBLE);   // until the dock's place is known
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                root.addView(caption, at, lp);
                // Just above the dock band, wherever the grid's padding parks the dock.
                grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> placeCaption(root, grid));
            }
        }
        // Both ride up with the grid as rows come up under them, and fade out by the time they would meet the rows.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                int shift = rv.canScrollVertically(-1) ? rv.computeVerticalScrollOffset() : 0;
                float room = Math.max(1f, 140 * density);
                float alpha = Math.max(0f, 1f - shift / room);
                if (caption != null) {
                    caption.setTranslationY(-shift);
                    caption.setAlpha(alpha);
                }
                if (heroGroup != null) heroGroup.setAlpha(alpha);
                else if (headline != null) headline.setAlpha(alpha);
            }
        });
    }

    /** The smaller caption, for "bottom" and "under_hero": light, 24sp, in a soft white with the soft shadow. */
    private void smallCaption(TextView t) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, CAPTION_BOTTOM_SP);
        t.setTypeface(heroFace != null ? heroFace : inter != null ? inter.at(300) : font(R.font.outfit_light));
        t.setTextColor(0xE0FFFFFF);
        t.setLetterSpacing(0.02f);
        softShadow(t);
    }

    /** caption_position "bottom": the caption's centre on the midpoint of the side blocks' centres, so the three read as one bar. */
    private void centreBottomCaption() {
        View caption = captionFrame != null ? captionFrame : bottomCaption;
        if (caption == null || caption.getHeight() == 0) return;
        float sum = 0;
        int n = 0;
        View weatherSide = weatherBlock != null && weatherBlock.getVisibility() == View.VISIBLE
                ? (weatherFrame != null ? weatherFrame : weatherBlock) : null;
        for (View side : new View[] {weatherSide, clockFrame}) {
            if (side == null || side.getHeight() == 0 || side.getParent() != caption.getParent()) continue;
            sum += side.getTop() + side.getHeight() / 2f;
            n++;
        }
        if (n == 0) return;
        float target = sum / n - (caption.getTop() + caption.getHeight() / 2f);
        if (Math.abs(caption.getTranslationY() - target) >= 1) caption.setTranslationY(target);
    }

    /**
     * Moves {@code v} into a PlainTextFrame in its place, with room round it for the shadow; the frame takes v's
     * place, gravity and margins (less that room), so v lands where it was.
     */
    private View plain(View v) {
        if (!(v.getParent() instanceof FrameLayout) || !(v.getLayoutParams() instanceof FrameLayout.LayoutParams)) return v;
        FrameLayout parent = (FrameLayout) v.getParent();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
        int at = parent.indexOfChild(v);
        int room = Math.round(GLOW_ROOM_DP * density);
        parent.removeView(v);
        PlainTextFrame frame = new PlainTextFrame(activity);
        frame.setPadding(room, room, room, room);
        frame.setFocusable(false);
        frame.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, lp.gravity);
        flp.setMargins(lp.leftMargin - room, lp.topMargin - room, lp.rightMargin - room, lp.bottomMargin - room);
        parent.addView(frame, at, flp);
        return frame;
    }

    private void placeCaption(FrameLayout root, RecyclerView grid) {
        if (caption == null) return;
        int[] g = new int[2], r = new int[2];
        grid.getLocationInWindow(g);
        root.getLocationInWindow(r);
        int bandTop = g[1] - r[1] + grid.getPaddingTop() - Math.round(AppAdapter.DOCK_EXTRA_DP * density);
        int h = caption.getHeight() > 0 ? caption.getHeight() : Math.round(caption.getTextSize() * 1.2f);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) caption.getLayoutParams();
        int top = Math.max(0, bandTop - h - Math.round(CAPTION_GAP_DP * density));
        if (lp.topMargin != top) {
            lp.topMargin = top;
            caption.post(() -> caption.setLayoutParams(lp));
        }
        caption.setVisibility(View.VISIBLE);
    }

    /** Shrinks the headline from HERO_MAX_SP until its widest line fits {@code maxPx}. */
    private void fit(TextView t, int maxPx) {
        float sp = HERO_MAX_SP;
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        String[] lines = t.getText().toString().split("\n");
        while (sp > HERO_MIN_SP && widest(t, lines) > maxPx) {
            sp -= 2;
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        }
    }

    /** With two or more lines, the first ("Welcome to") at HERO_FIRST_LINE of the size, leading into the rest. */
    private static CharSequence firstLineSmaller(String text) {
        int nl = text.indexOf('\n');
        if (nl <= 0) return text;
        android.text.SpannableString s = new android.text.SpannableString(text);
        s.setSpan(new android.text.style.RelativeSizeSpan(HERO_FIRST_LINE), 0, nl, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static float widest(TextView t, String[] lines) {
        float w = 0;
        for (String l : lines) w = Math.max(w, t.getPaint().measureText(l));
        return w;
    }

    /**
     * The one shadow every welcome text uses: a soft dark glow all round the letters, blurred wide so it lifts them
     * off the photo without reading as a rim. The blocks are drawn through PlainTextFrame, so the TV's "High contrast
     * text" setting does not add its hard black stroke to them; everything else on Home, and every other app, keeps
     * that setting's stroke.
     */
    private void softShadow(TextView t) {
        // Drawn by PlainTextFrame round the glyphs; the TextView's own is only for a block that is not framed.
        t.setShadowLayer(PlainTextFrame.SHADOW_BLUR_DP * density, 0, PlainTextFrame.SHADOW_DROP_DP * density,
                PlainTextFrame.SHADOW_COLOUR);
    }

    private TextView shadowed(TextView t) {
        t.setTextColor(0xFFFFFFFF);
        softShadow(t);
        t.setFocusable(false);
        t.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return t;
    }

    private Typeface font(int id) {
        try {
            return activity.getResources().getFont(id);
        } catch (RuntimeException e) {
            return Typeface.SERIF;
        }
    }

    // ---- clock at the bottom right ------------------------------------------------------------------

    private void moveClockToBottom(FrameLayout root, int at, boolean dateBelow) {
        View block = activity.findViewById(R.id.leftBlock);
        View greeting = activity.findViewById(R.id.greeting);
        greeting.setVisibility(View.GONE);
        ((ViewGroup) block.getParent()).removeView(block);
        // The pills no longer line up with the clock: they keep the top of the header.
        View buttons = activity.findViewById(R.id.topButtons);
        RelativeLayout.LayoutParams blp = (RelativeLayout.LayoutParams) buttons.getLayoutParams();
        blp.removeRule(RelativeLayout.ALIGN_BOTTOM);
        blp.removeRule(RelativeLayout.ALIGN_TOP);
        blp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        blp.bottomMargin = 0;
        buttons.setLayoutParams(blp);
        View stats = activity.findViewById(R.id.stats);
        RelativeLayout.LayoutParams slp = (RelativeLayout.LayoutParams) stats.getLayoutParams();
        slp.removeRule(RelativeLayout.BELOW);
        slp.removeRule(RelativeLayout.ALIGN_START);
        slp.addRule(RelativeLayout.ALIGN_PARENT_START);
        stats.setLayoutParams(slp);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        lp.rightMargin = Math.round(SIDE_MARGIN_DP * density);
        lp.bottomMargin = Math.round(BOTTOM_MARGIN_DP * density);
        root.addView(block, at, lp);
        // Quieter than the header's 52sp clock: down here it sits beside the weather, not over the screen. The date
        // stays at 12sp, the smallest size that reads from a sofa.
        ((TextView) activity.findViewById(R.id.clock)).setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        ((TextView) activity.findViewById(R.id.date)).setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        if (dateBelow) alignRight((LinearLayout) block);
        // Readable over a photo, like the hero lines.
        for (int id : new int[] {R.id.clock, R.id.ampm, R.id.date, R.id.shamsiDate, R.id.lunarDate}) {
            View v = activity.findViewById(id);
            if (v instanceof TextView) softShadow((TextView) v);
        }
    }

    /**
     * date_below_clock with the clock at the bottom right: the clock and the date stacked, both flush right, so the
     * pair lines up with the screen's right margin.
     */
    private void alignRight(LinearLayout block) {
        block.setHorizontalGravity(Gravity.RIGHT);
        View clock = activity.findViewById(R.id.clock);
        ((LinearLayout) clock.getParent()).setHorizontalGravity(Gravity.RIGHT);
        View column = (View) activity.findViewById(R.id.date).getParent();
        if (column.getParent() != block) return;   // the date did not move under the clock
        ((LinearLayout.LayoutParams) column.getLayoutParams()).gravity = Gravity.RIGHT;
        ViewGroup texts = (ViewGroup) column;
        for (int i = 0; i < texts.getChildCount(); i++) {
            View text = texts.getChildAt(i);
            ((LinearLayout.LayoutParams) text.getLayoutParams()).gravity = Gravity.RIGHT;
            if (text instanceof TextView) ((TextView) text).setGravity(Gravity.RIGHT);
        }
        column.requestLayout();
    }

    // ---- text-only pills ----------------------------------------------------------------------------

    private void labelsOnly(int... ids) {
        int pad = Math.round(20 * density);
        for (int id : ids) {
            View v = activity.findViewById(id);
            if (!(v instanceof TextView)) continue;
            TextView pill = (TextView) v;
            pill.setCompoundDrawablesRelative(null, null, null, null);
            pill.setCompoundDrawablePadding(0);
            pill.setPaddingRelative(pad, 0, pad, 0);
            pill.setLetterSpacing(0.04f);
        }
    }

    // ---- smaller pills ------------------------------------------------------------------------------

    /** small_pills: every top-bar button 34dp tall instead of 42dp, with 13sp text and tighter padding. */
    private void smallPills() {
        ViewGroup bar = activity.findViewById(R.id.topButtons);
        int h = Math.round(34 * density), round = Math.round(42 * density), pad = Math.round(14 * density);
        for (int i = 0; i < bar.getChildCount(); i++) {
            View v = bar.getChildAt(i);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.height = h;
            if (lp.width == round) lp.width = h;   // a round icon button stays round
            lp.setMarginStart(Math.round(8 * density));
            v.setLayoutParams(lp);
            if (v instanceof TextView) {
                TextView t = (TextView) v;
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                if (t.getText().length() > 0) t.setPaddingRelative(pad, 0, pad, 0);
                else if (lp.width == h) centreIcon(t, h);
            }
        }
    }

    /**
     * A round icon button made smaller: its side padding was set for the 42dp button, which left the 34dp one too
     * narrow for the icon, so it was drawn from the left padding and sat off-centre. Pads it to centre the icon.
     */
    private static void centreIcon(TextView t, int side) {
        android.graphics.drawable.Drawable icon = null;
        for (android.graphics.drawable.Drawable d : t.getCompoundDrawablesRelative()) if (d != null) { icon = d; break; }
        if (icon == null) return;
        int w = icon.getBounds().width() > 0 ? icon.getBounds().width() : icon.getIntrinsicWidth();
        int pad = Math.max(0, (side - w) / 2);
        t.setPaddingRelative(pad, 0, side - w - pad > 0 ? side - w - pad : 0, 0);
    }

    // ---- the next sunrise or sunset under the date ---------------------------------------------------

    /** next_sun_time: one line under the date, in the date's type, for the sun's next rise or set. */
    private void addSunLine(Config cfg, Handler main) {
        if (!place(cfg)) return;   // no usable place: nothing to show
        TextView date = activity.findViewById(R.id.date);
        if (date == null || !(date.getParent() instanceof LinearLayout)) return;
        LinearLayout column = (LinearLayout) date.getParent();
        sunLine = new TextView(activity);
        sunLine.setTextSize(TypedValue.COMPLEX_UNIT_PX, date.getTextSize());
        sunLine.setTypeface(date.getTypeface());
        sunLine.setLetterSpacing(date.getLetterSpacing());
        sunLine.setTextColor(date.getCurrentTextColor());
        sunLine.setIncludeFontPadding(date.getIncludeFontPadding());
        sunLine.setSingleLine(true);
        sunLine.setGravity(date.getGravity());
        softShadow(sunLine);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = ((LinearLayout.LayoutParams) date.getLayoutParams()).gravity;
        lp.topMargin = Math.round(6 * density);
        column.addView(sunLine, column.indexOfChild(date) + 1, lp);
        this.main = main;
    }

    /** The theme's latitude and longitude as numbers; false when either is missing or not a number. */
    private boolean place(Config cfg) {
        try {
            sunLat = Double.parseDouble(cfg.latitude);
            sunLon = Double.parseDouble(cfg.longitude);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * clock_gauge: the clock block (date and sun line) and a ring to its right, in one row centred on the ring, the
     * mirror of the weather on the left. The ring holds the time, so the clock's own line goes.
     */
    private void addClockGauge(Config cfg, FrameLayout root, Handler main) {
        if (!place(cfg)) return;
        View block = activity.findViewById(R.id.leftBlock);
        // Found before the block leaves the window: findViewById on the activity can't see a detached view.
        TextView clock = block == null ? null : block.findViewById(R.id.clock);
        if (clock == null || block.getParent() != root || !(block.getLayoutParams() instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) block.getLayoutParams();
        int at = root.indexOfChild(block);
        root.removeView(block);
        clockGaugeRow = new LinearLayout(activity);
        clockGaugeRow.setOrientation(LinearLayout.HORIZONTAL);
        clockGaugeRow.setGravity(Gravity.CENTER_VERTICAL);
        clockGaugeRow.addView(block, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        clockLine = (View) clock.getParent();
        clockGauge = new RingGauge(activity, clock.getTypeface(), CLOCK_CENTRE_SP);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Math.round(CLOCK_GAUGE_WIDTH_DP * density),
                Math.round(GAUGE_DP * density));
        // The ring's own width leaves room either side of it for the labels; less margin, so the gap matches the weather's.
        glp.setMarginStart(Math.round((14 - (CLOCK_GAUGE_WIDTH_DP - GAUGE_DP) / 2) * density));
        clockGaugeRow.addView(clockGauge, glp);
        // The ring's labels reach past it; the row ends where the ring would, level with the weather's margin.
        lp.rightMargin -= Math.round((CLOCK_GAUGE_WIDTH_DP - GAUGE_DP) / 2 * density);
        root.addView(clockGaugeRow, at, lp);
        clockLine.setVisibility(View.GONE);
        this.main = main;
    }

    /** Day: muted sunshine to evening orange. Night: dusk indigo to the blue before dawn. */
    private static final int DAY_FROM = 0xFFFFD36B, DAY_TO = 0xFFFF9A5C, NIGHT_FROM = 0xFF8C9EFF, NIGHT_TO = 0xFF7FC8FF;

    /** The sun line (Sun.next) and the clock's ring (Sun.period), then again at the start of the next minute. */
    private void sunTick() {
        main.removeCallbacks(sunTick);
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime now = java.time.LocalDateTime.now(zone);
        java.time.LocalDate today = now.toLocalDate();
        int minute = now.getHour() * 60 + now.getMinute();
        int[] sunToday = Sun.riseAndSet(today, sunLat, sunLon, zone);
        int[] sunTomorrow = Sun.riseAndSet(today.plusDays(1), sunLat, sunLon, zone);
        if (sunLine != null) {
            int[] next = Sun.next(minute, sunToday, sunTomorrow);
            if (next == null) {
                sunLine.setVisibility(View.GONE);
            } else {
                sunLine.setText(activity.getString(next[0] == 0 ? R.string.next_sunrise : R.string.next_sunset,
                        clockText(next[1], false)).toUpperCase(Locale.getDefault()));
                sunLine.setVisibility(View.VISIBLE);
            }
        }
        if (clockGauge != null) {
            int[] period = Sun.period(minute, Sun.riseAndSet(today.minusDays(1), sunLat, sunLon, zone), sunToday, sunTomorrow);
            boolean valid = System.currentTimeMillis() >= MainActivity.TIME_VALID_AFTER_MS;
            // Before network time the clock block is hidden (MainActivity); so is the ring. Polar days get the plain clock.
            clockGauge.setVisibility(valid && period != null ? View.VISIBLE : valid ? View.GONE : View.INVISIBLE);
            clockLine.setVisibility(period == null ? View.VISIBLE : View.GONE);
            if (period != null) {
                boolean day = period[0] == 1;
                float done = (minute - period[1]) / (float) Math.max(1, period[2] - period[1]);
                clockGauge.show(clockText(minute, true), clockText(period[1], true), clockText(period[2], true), done,
                        day ? DAY_FROM : NIGHT_FROM, day ? DAY_TO : NIGHT_TO, true);
                clockGauge.setContentDescription(clockText(minute, true));
            }
            if (!valid) {
                main.postDelayed(sunTick, 1_000L);
                return;
            }
        }
        main.postDelayed(sunTick, 60_000L - (System.currentTimeMillis() % 60_000L) + 50);
    }

    /** Minutes from midnight (any day) as the TV's 24 or 12-hour time; the ring's middle leaves out AM and PM. */
    private String clockText(int minutes, boolean bare) {
        int m = Math.floorMod(minutes, 1440);
        boolean h24 = android.text.format.DateFormat.is24HourFormat(activity);
        if (h24) return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
        int h = m / 60 % 12 == 0 ? 12 : m / 60 % 12;
        String t = String.format(Locale.ROOT, "%d:%02d", h, m % 60);
        return bare ? t : t + (m < 720 ? " AM" : " PM");
    }

    // ---- weather at the bottom left -----------------------------------------------------------------

    private void addWeather(FrameLayout root, int at) {
        // A row: today's range gauge (shown when the add-on gives the range), then the lines.
        weatherBlock = new LinearLayout(activity);
        weatherBlock.setOrientation(LinearLayout.HORIZONTAL);
        weatherBlock.setGravity(Gravity.CENTER_VERTICAL);
        weatherBlock.setFocusable(false);
        weatherGauge = new RingGauge(activity, inter != null ? inter.at(400) : font(R.font.outfit_regular), GAUGE_CENTRE_SP);
        weatherGauge.setVisibility(View.GONE);
        int g = Math.round(GAUGE_DP * density);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(g, g);
        glp.setMarginEnd(Math.round(14 * density));
        weatherBlock.addView(weatherGauge, glp);
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        weatherBlock.addView(texts, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        weatherBlock.setVisibility(View.GONE);
        weatherText = new TextView(activity);
        weatherText.setTextColor(0xFFFFFFFF);
        weatherText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        weatherText.setTypeface(font(R.font.outfit_regular));
        weatherText.setSingleLine(true);
        weatherText.setIncludeFontPadding(false);
        softShadow(weatherText);
        weatherSummary = new TextView(activity);
        weatherSummary.setTextColor(0xE6FFFFFF);
        weatherSummary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        weatherSummary.setTypeface(font(R.font.outfit_regular));
        weatherSummary.setSingleLine(true);
        weatherSummary.setIncludeFontPadding(false);
        softShadow(weatherSummary);
        weatherSummary.setVisibility(View.GONE);
        weatherPlace = new TextView(activity);
        weatherPlace.setTextColor(0xB3FFFFFF);
        weatherPlace.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        weatherPlace.setTypeface(font(R.font.outfit_medium));
        weatherPlace.setLetterSpacing(0.12f);
        weatherPlace.setSingleLine(true);
        softShadow(weatherPlace);
        // Each line its own width: a vertical LinearLayout's default is match_parent, which held the line to the place's width.
        texts.addView(weatherText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Math.round(4 * density);
        texts.addView(weatherSummary, slp);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = Math.round(5 * density);
        texts.addView(weatherPlace, plp);
        if (inter != null) {
            inter.apply(weatherText);
            inter.apply(weatherSummary);
            weatherPlace.setTypeface(inter.at(500));
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT);
        lp.leftMargin = Math.round(SIDE_MARGIN_DP * density);
        lp.bottomMargin = Math.round(BOTTOM_MARGIN_DP * density);
        root.addView(weatherBlock, at, lp);
    }

    private void showWeather(String line, String place, String icon, String temp, String summary, String[] range) {
        if (weatherBlock == null) return;
        // With today's range: the gauge carries the temperature, so the first line is the sky's words.
        Integer current = wholeDegrees(temp), high = wholeDegrees(range[0]), low = wholeDegrees(range[1]);
        boolean gauge = current != null && high != null && low != null && !range[2].isEmpty() && !range[3].isEmpty();
        weatherGauge.setVisibility(gauge ? View.VISIBLE : View.GONE);
        if (gauge) {
            weatherGauge.showTemperature(current, low, high, mono);
            weatherGauge.setContentDescription(null);
            temp = range[2].substring(0, 1).toUpperCase(Locale.getDefault()) + range[2].substring(1);
            summary = range[3];
        }
        // With temp and summary: two lines that mirror the clock and date on the right, the temperature in the clock's
        // type and "MOSTLY SUNNY · WIND 2 M/S" in the date's with the place under it, so the blocks line up row for row.
        // Without them: the add-on's one line, and the place under it.
        boolean split = !temp.isEmpty() && !summary.isEmpty();
        Locale here = Locale.getDefault();
        if (split) {
            matchType(weatherText, R.id.clock);
            matchType(weatherSummary, R.id.date);
            if (gauge) {
                weatherGauge.face(weatherText.getTypeface());
                // The gauge is this side's one large element, as the clock is on the right; the sky's words join the
                // small capitals under it in the date's type, in full white so they lead the lines.
                matchType(weatherText, R.id.date);
                weatherText.setTextColor(0xFFFFFFFF);
            }
            weatherText.setText(gauge ? temp.toUpperCase(here) : temp);
            // Small capitals stacked as the date and the sunset are on the right: 6dp apart.
            int gap = Math.round((gauge ? 6 : 4) * density);
            ((LinearLayout.LayoutParams) weatherSummary.getLayoutParams()).topMargin = gap;
            ((LinearLayout.LayoutParams) weatherPlace.getLayoutParams()).topMargin = gauge ? gap : Math.round(5 * density);
            weatherSummary.setText(windAsGlyph(capitalsKeepingUnits(summary, here), weatherSummary));
            // The place on a line of its own under the details, in the date's type, a little dimmer.
            matchType(weatherPlace, R.id.date);
            weatherPlace.setTextColor(0xB3FFFFFF);
            weatherPlace.setText(place.toUpperCase(here));
        } else {
            weatherText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            weatherText.setText(line);
            weatherPlace.setText(place.toUpperCase(here));
        }
        weatherSummary.setVisibility(split ? View.VISIBLE : View.GONE);
        weatherPlace.setVisibility(place.isEmpty() || !showPlace ? View.GONE : View.VISIBLE);
        // The add-on's icon before the first line, no taller than its capitals.
        int size = Math.round((weatherGauge.getVisibility() == View.VISIBLE ? 15 : split ? 20 : 16) * density);
        if (WeatherIcon.known(icon)) {
            android.graphics.drawable.Drawable now = weatherText.getCompoundDrawables()[0];
            if (!icon.equals(weatherIcon) || now == null || now.getBounds().width() != size) {
                WeatherIcon d = new WeatherIcon(icon, mono);
                d.setBounds(0, 0, size, size);
                weatherText.setCompoundDrawables(d, null, null, null);
                weatherText.setCompoundDrawablePadding(Math.round(8 * density));
            }
        } else if (!weatherIcon.isEmpty()) {
            weatherText.setCompoundDrawables(null, null, null, null);
        }
        weatherIcon = WeatherIcon.known(icon) ? icon : "";
        weatherBlock.setVisibility(line.isEmpty() ? View.GONE : View.VISIBLE);
        weatherBlock.setContentDescription(place.isEmpty() ? line : line + ", " + place);
    }

    /** A unit after a number: m/s, km/h, mph, kn, mm, cm, hPa, mb, °C, °F and %; their case is part of their meaning. */
    private static final java.util.regex.Pattern UNIT = java.util.regex.Pattern.compile(
            "(?<=\\d)(\\s?)(m/s|km/h|mph|kn|kt|mm|cm|hPa|mb|°C|°F|%)(?![\\p{L}/])");

    /**
     * The line in capitals, like the date, except its units: "wind 3 m/s" reads "WIND 3 m/s", not "WIND 3 M/S"
     * (megasiemens).
     */
    static String capitalsKeepingUnits(String s, Locale here) {
        StringBuilder out = new StringBuilder();
        java.util.regex.Matcher m = UNIT.matcher(s);
        int from = 0;
        while (m.find()) {
            out.append(s.substring(from, m.start()).toUpperCase(here)).append(m.group());
            from = m.end();
        }
        return out.append(s.substring(from).toUpperCase(here)).toString();
    }

    /** "WIND" just before a speed: its word becomes the wind glyph ("WIND 3 m/s" reads "≋ 3 m/s"). */
    private static final java.util.regex.Pattern WIND_WORD = java.util.regex.Pattern.compile("\\bWIND (?=[-\\d])");

    /**
     * The details line with the word "WIND" before a speed swapped for the wind glyph, the way the cloud stands
     * before the conditions: the unit already says what the number is. Anything else is left as it is.
     */
    private static CharSequence windAsGlyph(String s, TextView on) {
        java.util.regex.Matcher m = WIND_WORD.matcher(s);
        if (!m.find()) return s;
        android.text.SpannableStringBuilder out = new android.text.SpannableStringBuilder();
        out.append(s, 0, m.start());
        int at = out.length();
        out.append("\uFFFC ");
        out.setSpan(new GlyphSpan(new WeatherIcon("wind")), at, at + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.append(s, m.end(), s.length());
        on.setContentDescription(s);
        return out;
    }

    /** A drawable the size of the text's capitals, centred on them, as one character of a line. */
    private static final class GlyphSpan extends android.text.style.ReplacementSpan {
        private final android.graphics.drawable.Drawable glyph;
        private final android.graphics.Rect cap = new android.graphics.Rect();

        GlyphSpan(android.graphics.drawable.Drawable glyph) {
            this.glyph = glyph;
        }

        private int side(android.graphics.Paint paint) {
            paint.getTextBounds("H", 0, 1, cap);
            return Math.round(cap.height() * 1.35f);
        }

        @Override
        public int getSize(@NonNull android.graphics.Paint paint, CharSequence text, int start, int end,
                           android.graphics.Paint.FontMetricsInt fm) {
            return side(paint);
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas canvas, CharSequence text, int start, int end, float x,
                         int top, int y, int bottom, @NonNull android.graphics.Paint paint) {
            int side = side(paint);
            int middle = y - cap.height() / 2;
            glyph.setBounds(0, 0, side, side);
            glyph.setAlpha(paint.getAlpha());
            canvas.save();
            canvas.translate(x, middle - side / 2f);
            glyph.draw(canvas);
            canvas.restore();
        }
    }

    /** "-3", "14" or "10°C" to whole degrees; null when there is no number. */
    static Integer wholeDegrees(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("-?\\d+").matcher(s == null ? "" : s.replace('\u2212', '-'));
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    /** Gives a weather line the size, face, spacing, colour and font padding of the clock's or the date's text. */
    private void matchType(TextView t, int id) {
        View v = activity.findViewById(id);
        if (!(v instanceof TextView)) return;
        TextView model = (TextView) v;
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, model.getTextSize());
        t.setTypeface(model.getTypeface());
        t.setLetterSpacing(model.getLetterSpacing());
        t.setTextColor(model.getCurrentTextColor());
        t.setIncludeFontPadding(model.getIncludeFontPadding());
    }

    /** The add-on's forecast screen, when the weather_source package has one; else null. */
    private Intent forecastIntent(String pkg) {
        Intent i = new Intent(FORECAST).setPackage(pkg);
        return activity.getPackageManager().resolveActivity(i, 0) == null ? null : i;
    }

    /** The weather takes focus (Down from the app row), shows a soft frame while focused, and OK opens the forecast. */
    private void makeWeatherOpen() {
        weatherBlock.setFocusable(true);
        weatherBlock.setClickable(true);
        weatherBlock.setOnClickListener(v -> {
            try {
                activity.startActivity(forecast);
            } catch (ActivityNotFoundException | SecurityException gone) {
                // The add-on was removed or changed since Home started: nothing to open.
            }
        });
        weatherBlock.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (weatherUp != null) weatherUp.run();
                return true;
            }
            // Nothing else down here takes focus: the other arrows stay on the weather.
            return keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        });
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0x26FFFFFF);
        ring.setStroke(Math.round(1.5f * density), 0x80FFFFFF);
        ring.setCornerRadius(14 * density);
        int in = Math.round(4 * density);
        View frame = weatherFrame != null ? weatherFrame : weatherBlock;
        InsetDrawable focusRing = new InsetDrawable(ring, in);
        weatherBlock.setOnFocusChangeListener((v, focused) -> {
            // An inset background replaces the frame's padding with its own; keep the frame's, so nothing moves.
            int l = frame.getPaddingLeft(), t = frame.getPaddingTop(), r = frame.getPaddingRight(), b = frame.getPaddingBottom();
            frame.setBackground(focused ? focusRing : null);
            frame.setPadding(l, t, r, b);
        });
    }

    /** Down from the app row: focuses the weather when it can open the forecast and is on screen. */
    boolean focusWeather(Runnable up) {
        if (forecast == null || weatherBlock == null || weatherBlock.getVisibility() != View.VISIBLE) return false;
        weatherUp = up;
        return weatherBlock.requestFocus();
    }

    // ---- lifecycle ----------------------------------------------------------------------------------

    void start() {
        if (weather != null) weather.start();
        if (sunLine != null || clockGauge != null) sunTick();
    }

    void stop() {
        if (weather != null) weather.stop();
        if (main != null) main.removeCallbacks(sunTick);
    }

    void homeAgain() {
        if (weather != null) weather.read();
    }

    void shutdown() {
        if (weather != null) weather.shutdown();
        if (main != null) main.removeCallbacks(sunTick);
    }
}
