package io.github.philipvella.vellaradio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The station list, opened by holding OK on the radio on Home ({@code com.gathin.tvhome.action.RADIO_STATIONS}).
 * "Find more stations" first (SearchActivity), then the stations added from it, the local stations for the place
 * and the national ones. OK plays a station (or stops the one playing); holding OK makes it the default that OK on
 * Home plays; Menu offers that and removing it from the list. In the launcher's calendar's style (Look).
 */
public final class StationsActivity extends Activity {
    private static final String FIND = "find", RESTORE = "restore";

    private Look look;
    private TextView nowName, nowState, defaultName;
    private LinearLayout list;
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

        Row(Stations.Station station, LinearLayout view) {
            this.station = station;
            this.view = view;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        look = new Look(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(look.background());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(dp(64), dp(48), dp(64), dp(40));
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The side: the title, what is on, the default, and the keys.
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        page.addView(side, new LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT));
        side.addView(look.text(getString(R.string.stations_title), 40, Look.INK, look.light));
        side.addView(look.caps("Free radio · " + getString(R.string.place), Look.DIM), look.margins(0, 4, 0, 0));
        side.addView(look.caps("Now", Look.DIM), look.margins(0, 40, 0, 6));
        nowName = look.text("", 20, Look.INK, look.regular);
        side.addView(nowName);
        nowState = look.caps("", Look.FAINT);
        side.addView(nowState, look.margins(0, 6, 0, 0));
        side.addView(look.caps("Default on Home", Look.DIM), look.margins(0, 32, 0, 6));
        defaultName = look.text("", 18, Look.SOFT, look.regular);
        side.addView(defaultName);
        // Find more stations, one press of Left from anywhere in the list (it is also the list's first row).
        TextView search = look.caps("Find more stations", Look.INK);
        search.setFocusable(true);
        search.setClickable(true);
        search.setPadding(dp(14), dp(12), dp(14), dp(12));
        android.graphics.drawable.GradientDrawable idle = new android.graphics.drawable.GradientDrawable();
        idle.setColor(0x14FFFFFF);
        idle.setCornerRadius(14 * getResources().getDisplayMetrics().density);
        android.graphics.drawable.GradientDrawable ring = look.ring();
        search.setBackground(idle);
        search.setOnFocusChangeListener((view, focused) -> view.setBackground(focused ? ring : idle));
        search.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        side.addView(search, look.margins(0, 32, 0, 0));
        View fill = new View(this);
        side.addView(fill, new LinearLayout.LayoutParams(1, 0, 1));
        side.addView(look.caps("OK  play or stop", Look.DIM));
        side.addView(look.caps("Hold OK  make it the default", Look.DIM), look.margins(0, 6, 0, 0));
        side.addView(look.caps("Menu  more options", Look.DIM), look.margins(0, 6, 0, 0));
        side.addView(look.caps("Back  Home", Look.FAINT), look.margins(0, 6, 0, 0));

        // The list.
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(8), 0, dp(24));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        slp.setMarginStart(dp(40));
        page.addView(scroll, slp);

        setContentView(root);
        // Start on the station Home shows.
        Stations.Station shown = Radio.shown(this);
        build(shown == null ? null : shown.id, 0);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Back from the search, which may have added stations: build the list again, on the row that had focus.
        View f = getCurrentFocus();
        build(f != null && f.getTag() instanceof String ? (String) f.getTag() : null, 0);
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

    /** Builds the list and focuses the row with that key (a station id, FIND or RESTORE), else the focusable row at that index. */
    private void build(String focus, int index) {
        list.removeAllViews();
        rows.clear();
        List<View> focusable = new ArrayList<>();

        LinearLayout find = look.row("Find more stations", "Radio Browser · free online directory", "");
        Look.tag(find).setVisibility(View.GONE);
        find.setTag(FIND);
        find.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        list.addView(find);
        focusable.add(find);

        Set<String> hidden = MyStations.hidden(this);
        List<Stations.Station> mine = MyStations.list(this);
        if (!mine.isEmpty()) {
            list.addView(look.caps("My stations", Look.DIM), look.margins(0, 24, 0, 8));
            for (Stations.Station s : mine) focusable.add(row(s));
        }
        List<Stations.Station> near = new ArrayList<>();
        for (Stations.Station s : Stations.near(Radio.latitude(this), Radio.longitude(this))) {
            if (!hidden.contains(s.id)) near.add(s);
        }
        if (!near.isEmpty()) {
            list.addView(look.caps("Near " + getString(R.string.place), Look.DIM), look.margins(0, 24, 0, 8));
            for (Stations.Station s : near) focusable.add(row(s));
        }
        List<Stations.Station> national = new ArrayList<>();
        for (Stations.Station s : Stations.national()) if (!hidden.contains(s.id)) national.add(s);
        if (!national.isEmpty()) {
            list.addView(look.caps("National", Look.DIM), look.margins(0, 24, 0, 8));
            for (Stations.Station s : national) focusable.add(row(s));
        }
        if (!hidden.isEmpty()) {
            int n = hidden.size();
            LinearLayout restore = look.row("Restore removed stations", n + (n == 1 ? " station removed" : " stations removed"), "");
            Look.tag(restore).setVisibility(View.GONE);
            restore.setTag(RESTORE);
            restore.setOnClickListener(v -> {
                MyStations.restore(this);
                build(RESTORE, Integer.MAX_VALUE);
            });
            LinearLayout.LayoutParams rlp = (LinearLayout.LayoutParams) restore.getLayoutParams();
            rlp.topMargin = dp(24);
            list.addView(restore);
            focusable.add(restore);
        }

        refresh();
        View target = null;
        for (View v : focusable) if (focus != null && focus.equals(v.getTag())) target = v;
        if (target == null) target = focusable.get(Math.max(0, Math.min(index, focusable.size() - 1)));
        target.requestFocus();
    }

    private LinearLayout row(Stations.Station s) {
        LinearLayout v = look.row(s.name, s.owner, "Default");
        v.setTag(s.id);
        v.setLongClickable(true);
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
                if (event.getAction() == KeyEvent.ACTION_UP) options(s);
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
        rows.add(new Row(s, v));
        list.addView(v);
        return v;
    }

    /** Menu on a station: make it the default, or take it off the list. */
    private void options(Stations.Station s) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(s.name)
                .setItems(new CharSequence[] {"Make default", "Remove from list"}, (d, which) -> {
                    if (which == 0) makeDefault(s);
                    else remove(s);
                })
                .show();
    }

    private void remove(Stations.Station s) {
        // Focus goes to the row that takes its place.
        int index = 0;
        for (int i = 0, n = 0; i < list.getChildCount(); i++) {
            View c = list.getChildAt(i);
            if (!c.isFocusable()) continue;
            if (s.id.equals(c.getTag())) index = n;
            n++;
        }
        if (s.id.equals(playingId())) startForegroundService(RadioService.command(this, "stop", null));
        MyStations.remove(this, s.id);
        build(null, index);
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
            Look.mark(r.view).setVisibility(r.station.id.equals(on) ? View.VISIBLE : View.INVISIBLE);
            Look.tag(r.view).setVisibility(def != null && r.station.id.equals(def.id) ? View.VISIBLE : View.GONE);
        }
        String st = Radio.state();
        nowName.setText(on.isEmpty() || shown == null ? "Nothing playing" : shown.name);
        nowName.setTextColor(on.isEmpty() ? Look.FAINT : Look.INK);
        nowState.setText(Look.upper(Radio.PLAYING.equals(st) ? "Live" : Radio.LOADING.equals(st) ? "Tuning in…"
                : Radio.ERROR.equals(st) ? "Couldn't reach the station" : ""));
        defaultName.setText(def == null ? "" : def.name);
    }

    private int dp(float v) {
        return look.dp(v);
    }
}
