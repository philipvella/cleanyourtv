package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import com.gathin.tvhome.Season.Scene;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Launcher settings' animation schedule: seasonal_scenes "auto" as a Gantt chart of the year, one row per scene in
 * the order that settles an overlap (the top row wins). OK on a row moves it up or down the list, changes its days or
 * turns it off; Left and Right on the chart show another year (Easter moves). Each change is saved at once as one
 * on-TV choice (SeasonSchedule.KEY), and Home picks it up when it next comes back.
 */
public final class SeasonScheduleActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    private static final int BROWSE = 0, MOVE = 1, DATES = 2;
    private static final int BG = 0xFF101419, INK = 0xFFFFFFFF, SOFT = 0xFFACB8C1, FAINT = 0x66FFFFFF;
    private static final int FOCUS_FILL = 0x5574A987, FOCUS_RING = 0xFFB9E7C7, MOVE_FILL = 0x884DAE6D;

    private SharedPreferences choices;
    private SeasonSchedule schedule;
    private Map<MonthDay, Scene> own;
    private String[] sceneNames;
    private boolean auto;
    private int year = LocalDate.now().getYear();
    private int row;
    private int mode = BROWSE;
    private int edge = SeasonSchedule.START;
    private Chart chart;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config cfg = Config.load(this);
        auto = Season.AUTO.equals(cfg.seasonalScenes.trim().toLowerCase(Locale.ROOT));
        own = Season.parseDates(cfg.seasonalDates);
        sceneNames = getResources().getStringArray(R.array.season_names);
        schedule = SeasonSchedule.parse(choices.getString(SeasonSchedule.KEY, null));
        // Start on the row that plays today.
        row = Math.max(0, schedule.winner(LocalDate.now(), own));
        chart = new Chart(this);
        chart.setFocusable(true);
        chart.setFocusableInTouchMode(true);
        setContentView(chart);
        chart.requestFocus();
    }

    private void save(SeasonSchedule next) {
        if (next == schedule) return;
        schedule = next;
        SharedPreferences.Editor e = choices.edit();
        if (next.key().equals(SeasonSchedule.DEFAULT.key())) e.remove(SeasonSchedule.KEY);
        else e.putString(SeasonSchedule.KEY, next.key());
        // Home rebuilds when it next resumes because the change count moved (LauncherSettingsActivity.changed).
        e.putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
        setResult(RESULT_OK);
        chart.recompute();
    }

    /** OK on a row: what can be done with it. */
    private void showActions() {
        SeasonSchedule.Slot s = schedule.slots.get(row);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.schedule_move));
        actions.add(() -> setMode(MOVE));
        if (!s.own()) {
            labels.add(getString(R.string.schedule_dates));
            actions.add(() -> {
                edge = SeasonSchedule.START;
                setMode(DATES);
            });
        }
        labels.add(getString(s.on ? R.string.schedule_turn_off : R.string.schedule_turn_on));
        actions.add(() -> save(schedule.toggled(row)));
        if (!s.own() && !schedule.isDefault(row) && !sameDays(s)) {
            labels.add(getString(R.string.schedule_reset_dates));
            actions.add(() -> save(schedule.reset(row)));
        }
        new AlertDialog.Builder(this)
                .setTitle(name(s))
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Whether the slot already has its default days (it may only be off). */
    private static boolean sameDays(SeasonSchedule.Slot s) {
        for (SeasonSchedule.Slot d : SeasonSchedule.DEFAULT.slots) {
            if (d.scene == s.scene) return d.start.equals(s.start) && d.end.equals(s.end);
        }
        return true;
    }

    private void resetAll() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.schedule_title)
                .setMessage(R.string.schedule_reset_all_message)
                .setPositiveButton(R.string.schedule_reset_all, (d, w) -> {
                    save(SeasonSchedule.DEFAULT);
                    row = Math.max(0, schedule.winner(LocalDate.now(), own));
                    chart.invalidate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setMode(int m) {
        mode = m;
        chart.invalidate();
    }

    private String name(SeasonSchedule.Slot s) {
        if (s.own()) return getString(R.string.schedule_own);
        for (int i = 0; i < Season.PICKABLE.length; i++) if (Season.PICKABLE[i] == s.scene) return sceneNames[i];
        return Season.keyOf(s.scene);
    }

    /** Each scene's colour on the chart; the household's dates take their scene's. */
    private static int colour(Scene s) {
        switch (s) {
            case AUTUMN: return 0xFFD98A3D;
            case HALLOWEEN: return 0xFFFF7A1A;
            case FIREWORKS: return 0xFFE8C35A;
            case CHRISTMAS: return 0xFFD9534F;
            case WINTER: return 0xFF8DB9E3;
            case VALENTINE: return 0xFFE8749A;
            case SPRING: return 0xFF9ACD72;
            case SUMMER: return 0xFFF2D46B;
            case NEW_YEAR: return 0xFFD8DEE6;
            case PANCAKE_DAY: return 0xFFD9B27C;
            case ST_PATRICKS: return 0xFF3FB06A;
            case EASTER: return 0xFFB79CF0;
            case MOTHERS_DAY: return 0xFFF0A8C8;
            case FATHERS_DAY: return 0xFF6FA8DC;
            case BIRTHDAY: return 0xFFFFC857;
            case ANNIVERSARY: return 0xFFE57373;
            default: return SOFT;
        }
    }

    private static int alpha(int colour, int a) {
        return (colour & 0x00FFFFFF) | (a << 24);
    }

    /** The chart: drawn in one view, with the remote's keys handled here. */
    private final class Chart extends View {
        private final float d = getResources().getDisplayMetrics().density;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        private final RectF r = new RectF();
        private final DateTimeFormatter dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault());
        /** For the year shown: the slot that wins each day (-1 for none), and which days each slot has. */
        private int[] winners = new int[0];
        private boolean[][] has = new boolean[0][];
        private LocalDate first;
        /** The OK or Menu key pressed on the chart, acted on when it is released; 0 for none. */
        private int pressed;

        Chart(Context ctx) {
            super(ctx);
            setBackgroundColor(BG);
            line.setStyle(Paint.Style.STROKE);
            recompute();
        }

        void recompute() {
            first = LocalDate.of(year, 1, 1);
            int days = first.lengthOfYear(), n = schedule.slots.size();
            winners = new int[days];
            has = new boolean[n][days];
            for (int k = 0; k < days; k++) {
                LocalDate day = first.plusDays(k);
                winners[k] = schedule.winner(day, own);
                for (int i = 0; i < n; i++) has[i][k] = schedule.has(i, day, own);
            }
            invalidate();
        }

        private float dp(float v) {
            return v * d;
        }

        @Override
        protected void onDraw(Canvas c) {
            float pad = dp(56), w = getWidth(), h = getHeight();
            float x0 = pad + dp(210), x1 = w - pad;
            int days = winners.length, n = schedule.slots.size();
            float dayW = (x1 - x0) / days;

            // Title and what the chart means.
            text.setTypeface(light);
            text.setTextSize(dp(30));
            text.setColor(INK);
            text.setTextAlign(Paint.Align.LEFT);
            float y = dp(32) - text.ascent();
            c.drawText(getString(R.string.schedule_title), pad, y, text);
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTypeface(medium);
            text.setTextSize(dp(22));
            c.drawText((mode == BROWSE ? "‹  " : "") + year + (mode == BROWSE ? "  ›" : ""), x1, y, text);
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(regular);
            text.setTextSize(dp(13));
            text.setColor(SOFT);
            y += dp(22);
            c.drawText(getString(auto ? R.string.schedule_subtitle : R.string.schedule_subtitle_not_auto), pad, y, text);

            // The month header and the grid.
            float top = y + dp(30), bottom = h - dp(88);
            float rh = Math.min(dp(30), (bottom - top) / n);
            bottom = top + rh * n;
            text.setTypeface(medium);
            text.setTextSize(dp(11));
            text.setLetterSpacing(0.12f);
            text.setTextAlign(Paint.Align.CENTER);
            line.setStrokeWidth(dp(1));
            for (Month m : Month.values()) {
                LocalDate start = LocalDate.of(year, m, 1);
                float mx = x0 + (start.getDayOfYear() - 1) * dayW, mw = start.lengthOfMonth() * dayW;
                text.setColor(SOFT);
                c.drawText(m.getDisplayName(TextStyle.SHORT, Locale.getDefault()).toUpperCase(Locale.getDefault()),
                        mx + mw / 2, top - dp(10), text);
                line.setColor(0x1AFFFFFF);
                c.drawLine(mx, top - dp(4), mx, bottom, line);
            }
            c.drawLine(x1, top - dp(4), x1, bottom, line);
            text.setLetterSpacing(0f);

            // Rows.
            for (int i = 0; i < n; i++) {
                SeasonSchedule.Slot s = schedule.slots.get(i);
                float ry = top + i * rh, cy = ry + rh / 2;
                if (i == row) {
                    r.set(pad - dp(10), ry + dp(1), x1 + dp(10), ry + rh - dp(1));
                    fill.setColor(mode == MOVE ? MOVE_FILL : FOCUS_FILL);
                    c.drawRoundRect(r, dp(8), dp(8), fill);
                    line.setColor(FOCUS_RING);
                    line.setStrokeWidth(dp(mode == BROWSE ? 1.5f : 2f));
                    c.drawRoundRect(r, dp(8), dp(8), line);
                    line.setStrokeWidth(dp(1));
                }
                // The place in the list, then the name.
                text.setTextSize(Math.min(dp(14), rh * 0.5f));
                text.setTypeface(medium);
                text.setTextAlign(Paint.Align.RIGHT);
                text.setColor(FAINT);
                float base = cy - (text.ascent() + text.descent()) / 2;
                c.drawText(i == row && mode == MOVE ? "⇅" : String.valueOf(i + 1), pad + dp(18), base, text);
                text.setTextAlign(Paint.Align.LEFT);
                text.setTypeface(regular);
                text.setColor(s.on ? INK : FAINT);
                c.drawText(name(s), pad + dp(30), base, text);
                if (!s.on) {
                    text.setTextAlign(Paint.Align.RIGHT);
                    text.setTextSize(Math.min(dp(11), rh * 0.4f));
                    text.setTypeface(medium);
                    c.drawText(getString(R.string.schedule_off_tag), x0 - dp(14), base, text);
                    text.setTextAlign(Paint.Align.LEFT);
                }
                drawBars(c, i, s, x0, dayW, cy, rh);
            }

            // Today.
            LocalDate today = LocalDate.now();
            if (today.getYear() == year) {
                float tx = x0 + (today.getDayOfYear() - 0.5f) * dayW;
                line.setColor(0xCCFFFFFF);
                line.setStrokeWidth(dp(1.5f));
                c.drawLine(tx, top - dp(4), tx, bottom, line);
                fill.setColor(INK);
                c.drawCircle(tx, top - dp(4), dp(3), fill);
                line.setStrokeWidth(dp(1));
            }

            // The row's details, then the keys.
            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(regular);
            text.setTextSize(dp(15));
            text.setColor(INK);
            c.drawText(details(), pad, bottom + dp(34), text);
            text.setTypeface(medium);
            text.setTextSize(dp(12));
            text.setLetterSpacing(0.12f);
            text.setColor(SOFT);
            c.drawText(hints().toUpperCase(Locale.getDefault()), pad, h - dp(28), text);
            text.setLetterSpacing(0f);
        }

        /** A slot's days: solid where it plays, faint where a row above wins, an outline when it is off. */
        private void drawBars(Canvas c, int i, SeasonSchedule.Slot s, float x0, float dayW, float cy, float rh) {
            float bh = Math.max(dp(6), rh * 0.42f), minW = dp(4);
            int days = winners.length;
            int k = 0;
            while (k < days) {
                if (!has[i][k]) {
                    k++;
                    continue;
                }
                boolean wins = winners[k] == i;
                Scene sc = s.own() ? own.get(MonthDay.from(first.plusDays(k))) : s.scene;
                int end = k + 1;
                // The household's days are drawn one by one, each in its own scene's colour.
                if (!s.own()) while (end < days && has[i][end] && (winners[end] == i) == wins) end++;
                float a = x0 + k * dayW, b = x0 + end * dayW;
                if (b - a < minW) {
                    float mid = (a + b) / 2;
                    a = mid - minW / 2;
                    b = mid + minW / 2;
                }
                r.set(a, cy - bh / 2, b, cy + bh / 2);
                int col = colour(sc == null ? Scene.NONE : sc);
                if (!s.on) {
                    line.setColor(0x55FFFFFF);
                    c.drawRoundRect(r, dp(3), dp(3), line);
                } else if (wins) {
                    fill.setColor(col);
                    c.drawRoundRect(r, dp(3), dp(3), fill);
                } else {
                    fill.setColor(alpha(col, 0x38));
                    c.drawRoundRect(r, dp(3), dp(3), fill);
                    line.setColor(alpha(col, 0x80));
                    c.drawRoundRect(r, dp(3), dp(3), line);
                }
                k = end;
            }
            // Changing the days: a handle on the edge (or both) the arrows move.
            if (i == row && mode == DATES && !s.own()) {
                // The start of the run that begins this year, and the end of the run that ends in it (for a run
                // over New Year, last winter's).
                LocalDate start = s.start.on(year), last = s.end(year);
                if (last.getYear() != year) last = s.end(year - 1);
                handle(c, x0 + (start.getDayOfYear() - 1) * dayW, cy, rh, edge != SeasonSchedule.END,
                        start.getYear() == year);
                handle(c, x0 + last.getDayOfYear() * dayW, cy, rh, edge != SeasonSchedule.START,
                        last.getYear() == year);
            }
        }

        private void handle(Canvas c, float x, float cy, float rh, boolean active, boolean shown) {
            if (!shown) return;
            fill.setColor(active ? INK : 0x80FFFFFF);
            float hw = dp(active ? 2.5f : 1.5f), hh = rh * 0.4f;
            r.set(x - hw, cy - hh, x + hw, cy + hh);
            c.drawRoundRect(r, hw, hw, fill);
        }

        /** "Christmas · 1 Dec – 26 Dec · on Home 26 of 26 days": what the row does this year. */
        private String details() {
            SeasonSchedule.Slot s = schedule.slots.get(row);
            int mine = 0, total = 0;
            for (int k = 0; k < winners.length; k++) {
                if (has[row][k]) total++;
                if (winners[k] == row) mine++;
            }
            if (s.own()) {
                return getString(R.string.schedule_details_own, total, mine);
            }
            String when = s.start.on(year).format(dayMonth) + " – " + s.end(year).format(dayMonth);
            if (s.start.anchor == SeasonSchedule.EASTER) when += " · " + getString(R.string.schedule_moves_easter);
            if (s.start.anchor == SeasonSchedule.FATHERS) when += " · " + getString(R.string.schedule_moves_fathers);
            if (!s.on) return getString(R.string.schedule_details_off, name(s), when);
            if (mode == DATES) {
                int which = edge == SeasonSchedule.START ? R.string.schedule_edge_start
                        : edge == SeasonSchedule.END ? R.string.schedule_edge_end : R.string.schedule_edge_both;
                return getString(R.string.schedule_details_editing, name(s), when, getString(which));
            }
            return getString(R.string.schedule_details, name(s), when, mine, total);
        }

        private String hints() {
            if (mode == MOVE) return getString(R.string.schedule_hint_move);
            if (mode == DATES) return getString(R.string.schedule_hint_dates);
            return getString(R.string.schedule_hint_browse);
        }

        @Override
        public boolean onKeyDown(int code, KeyEvent event) {
            int n = schedule.slots.size();
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    int by = code == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                    if (mode == MOVE) {
                        SeasonSchedule next = schedule.moved(row, by);
                        if (next != schedule) {
                            row += by;
                            save(next);
                        }
                    } else if (mode == DATES) {
                        edge = (edge + (by > 0 ? 1 : 2)) % 3;
                        invalidate();
                    } else {
                        row = Math.max(0, Math.min(n - 1, row + by));
                        invalidate();
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    int by = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                    if (mode == DATES) {
                        // Held, it speeds up to a week a step.
                        int step = event.getRepeatCount() > 12 ? 7 : 1;
                        save(schedule.shifted(row, edge, by * step, year));
                    } else if (mode == BROWSE && event.getRepeatCount() == 0) {
                        year = Math.max(2000, Math.min(2099, year + by));
                        recompute();
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_MENU:
                    // Acted on when let go, so the dialog it opens does not get this press's release.
                    if (event.getRepeatCount() == 0) pressed = code;
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (mode != BROWSE) {
                        setMode(BROWSE);
                        return true;
                    }
                    return super.onKeyDown(code, event);
                default:
                    return super.onKeyDown(code, event);
            }
        }

        @Override
        public boolean onKeyUp(int code, KeyEvent event) {
            if (code != pressed) return super.onKeyUp(code, event);
            pressed = 0;
            if (code == KeyEvent.KEYCODE_MENU) {
                if (mode == BROWSE) resetAll();
            } else if (mode == BROWSE) {
                showActions();
            } else {
                setMode(BROWSE);
            }
            return true;
        }
    }
}
