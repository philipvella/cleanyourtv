package io.github.philipvella.vellaradio;

import android.app.Activity;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The station list, opened by holding OK on the radio on Home ({@code com.gathin.tvhome.action.RADIO_STATIONS}).
 * The local stations for the place first, then the national ones. OK plays a station (or stops the one playing);
 * holding OK, or Menu, makes it the default that OK on Home plays. In the launcher's calendar's style: a dark
 * gradient, a light title and small spaced capitals.
 */
public final class StationsActivity extends Activity {
    private static final int INK = 0xFFFFFFFF, SOFT = 0xCCFFFFFF, DIM = 0x99FFFFFF, FAINT = 0x80FFFFFF;

    private float density;
    private Typeface light, regular, medium;
    private TextView nowName, nowState, defaultName;
    private final List<Row> rows = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentObserver watch = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange) {
            refresh();
        }
    };

    private static final class Row {
        final Stations.Station station;
        final LinearLayout view;
        final View mark;
        final TextView tag;

        Row(Stations.Station station, LinearLayout view, View mark, TextView tag) {
            this.station = station;
            this.view = view;
            this.mark = mark;
            this.tag = tag;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF15181D, 0xFF07080A}));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(dp(64), dp(48), dp(64), dp(40));
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The side: the title, what is on, the default, and the keys.
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        page.addView(side, new LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT));
        side.addView(text(getString(R.string.stations_title), 40, INK, light));
        side.addView(caps("Free radio · " + getString(R.string.place), DIM), margins(0, 4, 0, 0));
        side.addView(caps("Now", DIM), margins(0, 40, 0, 6));
        nowName = text("", 20, INK, regular);
        side.addView(nowName);
        nowState = caps("", FAINT);
        side.addView(nowState, margins(0, 6, 0, 0));
        side.addView(caps("Default on Home", DIM), margins(0, 32, 0, 6));
        defaultName = text("", 18, SOFT, regular);
        side.addView(defaultName);
        View fill = new View(this);
        side.addView(fill, new LinearLayout.LayoutParams(1, 0, 1));
        side.addView(caps("OK  play or stop", DIM));
        side.addView(caps("Hold OK  make it the default", DIM), margins(0, 6, 0, 0));
        side.addView(caps("Back  Home", FAINT), margins(0, 6, 0, 0));

        // The list.
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(8), 0, dp(24));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        slp.setMarginStart(dp(40));
        page.addView(scroll, slp);

        List<Stations.Station> near = Stations.near(Radio.latitude(this), Radio.longitude(this));
        if (!near.isEmpty()) {
            list.addView(caps("Near " + getString(R.string.place), DIM), margins(0, 0, 0, 8));
            for (Stations.Station s : near) list.addView(row(s));
        }
        list.addView(caps("National", DIM), margins(0, near.isEmpty() ? 0 : 24, 0, 8));
        for (Stations.Station s : Stations.national()) list.addView(row(s));

        setContentView(root);
        refresh();
        // Start on the station Home shows.
        Stations.Station shown = Radio.shown(this);
        for (Row r : rows) {
            if (shown != null && r.station.id.equals(shown.id)) {
                r.view.requestFocus();
                break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        getContentResolver().registerContentObserver(RadioProvider.NOW, false, watch);
        refresh();
    }

    @Override
    protected void onStop() {
        getContentResolver().unregisterContentObserver(watch);
        super.onStop();
    }

    private View row(Stations.Station s) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(16), dp(10), dp(16), dp(10));
        v.setFocusable(true);
        v.setClickable(true);
        v.setLongClickable(true);

        View mark = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(INK);
        mark.setBackground(dot);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        mlp.setMarginEnd(dp(14));
        v.addView(mark, mlp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(s.name, 18, INK, regular));
        TextView owner = caps(s.owner, FAINT);
        owner.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        words.addView(owner, margins(0, 2, 0, 0));
        v.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView tag = caps("Default", DIM);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        v.addView(tag);

        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0x26FFFFFF);
        ring.setStroke(Math.round(1.5f * density), 0x80FFFFFF);
        ring.setCornerRadius(14 * density);
        v.setOnFocusChangeListener((view, focused) -> view.setBackground(focused ? ring : null));
        // Held OK: the View's long click or the key's long-press flag, whichever comes first; once per press.
        boolean[] held = {false};
        v.setOnClickListener(view -> {
            if (held[0]) return;
            boolean on = s.id.equals(playingId());
            startForegroundService(RadioService.command(this, on ? "stop" : "play", s.id));
        });
        v.setOnLongClickListener(view -> {
            if (!held[0]) {
                held[0] = true;
                makeDefault(s);
            }
            return true;
        });
        v.setOnKeyListener((view, code, event) -> {
            if (code == KeyEvent.KEYCODE_MENU) {
                if (event.getAction() == KeyEvent.ACTION_UP) makeDefault(s);
                return true;
            }
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER
                    && code != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                held[0] = false;
            } else if (event.getAction() == KeyEvent.ACTION_DOWN && event.isLongPress() && !held[0]) {
                held[0] = true;
                view.cancelLongPress();
                makeDefault(s);
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP && held[0]) {
                view.setPressed(false);
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        v.setLayoutParams(lp);
        rows.add(new Row(s, v, mark, tag));
        return v;
    }

    private void makeDefault(Stations.Station s) {
        Radio.setDefault(this, s.id);
        refresh();
    }

    /** The station playing or tuning in, or "". */
    private String playingId() {
        String st = Radio.state();
        if (!Radio.PLAYING.equals(st) && !Radio.LOADING.equals(st)) return "";
        Stations.Station s = Radio.shown(this);
        return s == null ? "" : s.id;
    }

    private void refresh() {
        String on = playingId();
        Stations.Station def = Radio.defaultStation(this);
        Stations.Station shown = Radio.shown(this);
        for (Row r : rows) {
            r.mark.setVisibility(r.station.id.equals(on) ? View.VISIBLE : View.INVISIBLE);
            r.tag.setVisibility(def != null && r.station.id.equals(def.id) ? View.VISIBLE : View.GONE);
        }
        String st = Radio.state();
        nowName.setText(on.isEmpty() ? "Nothing playing" : shown.name);
        nowName.setTextColor(on.isEmpty() ? FAINT : INK);
        nowState.setText(upper(Radio.PLAYING.equals(st) ? "Live" : Radio.LOADING.equals(st) ? "Tuning in…"
                : Radio.ERROR.equals(st) ? "Couldn't reach the station" : ""));
        defaultName.setText(def == null ? "" : def.name);
    }

    private TextView text(String s, float sp, int colour, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Small spaced capitals, as the launcher's date and calendar headings are set. */
    private TextView caps(String s, int colour) {
        TextView t = text(upper(s), 12, colour, medium);
        t.setLetterSpacing(0.14f);
        return t;
    }

    private static String upper(String s) {
        return s.toUpperCase(Locale.getDefault());
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
