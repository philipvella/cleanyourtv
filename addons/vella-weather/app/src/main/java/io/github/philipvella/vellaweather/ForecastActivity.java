package io.github.philipvella.vellaweather;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The full forecast, opened from the weather on the Clean Your TV launcher's Home (OK on the weather; the launcher
 * starts {@code com.gathin.tvhome.action.WEATHER_FORECAST} in this package). The weather now, the next 24 hours and
 * the next seven days. Shows the last forecast it saved at once, fetches a new one, and Back returns to Home.
 */
public final class ForecastActivity extends Activity {
    /** A saved forecast younger than this is shown while the new one loads; older, it waits for the fetch. */
    private static final long SHOW_SAVED_MS = 6 * 60 * 60_000L;
    private static final int INK = 0xFFFFFFFF, SOFT = 0xB3FFFFFF, FAINT = 0x80FFFFFF, RAIN = 0xFF8FD3FF;

    private float density;
    private FrameLayout root;
    private TextView status;
    private Thread fetch;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        root = new FrameLayout(this);
        GradientDrawable sky = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF0E1A2B, 0xFF0B0F17, 0xFF07080B});
        root.setBackground(sky);
        status = text("Loading the forecast…", 16, SOFT, Typeface.NORMAL);
        root.addView(status, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);

        SharedPreferences saved = getSharedPreferences("forecast", Context.MODE_PRIVATE);
        String json = saved.getString("json", "");
        long at = saved.getLong("at", 0);
        if (!json.isEmpty() && System.currentTimeMillis() - at < SHOW_SAVED_MS) show(json, at);
        load();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fetch != null) fetch.interrupt();
    }

    private void load() {
        double lat = Double.parseDouble(getString(R.string.latitude)), lon = Double.parseDouble(getString(R.string.longitude));
        fetch = new Thread(() -> {
            String json;
            try {
                json = Forecast.fetchJson(lat, lon);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isDestroyed() && root.getChildCount() == 1) {
                        status.setText("Couldn't reach the weather service.\nCheck the TV's internet connection and try again.");
                    }
                });
                return;
            }
            long now = System.currentTimeMillis();
            getSharedPreferences("forecast", Context.MODE_PRIVATE).edit().putString("json", json).putLong("at", now).apply();
            runOnUiThread(() -> {
                if (!isDestroyed()) show(json, now);
            });
        }, "forecast");
        fetch.start();
    }

    private void show(String json, long at) {
        Forecast f;
        try {
            f = Forecast.parse(json);
        } catch (Exception bad) {
            return;
        }
        root.removeAllViews();
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56), dp(28), dp(56), dp(24));
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // The place, and when the forecast was fetched.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView place = text(getString(R.string.place).toUpperCase(Locale.getDefault()), 13, SOFT, Typeface.BOLD);
        place.setLetterSpacing(0.16f);
        top.addView(place, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        java.text.DateFormat clock = android.text.format.DateFormat.getTimeFormat(this);
        top.addView(text("Updated " + clock.format(new java.util.Date(at)), 12, FAINT, Typeface.NORMAL));
        page.addView(top);

        page.addView(now(f), full(0, 8, 0, 0));
        page.addView(label("Next 24 hours"), margins(0, 16, 0, 8));
        page.addView(hours(f), full(0, 0, 0, 0));
        page.addView(label("Next 7 days"), margins(0, 16, 0, 8));
        page.addView(days(f), full(0, 0, 0, 0));
    }

    /** The big icon and temperature, the words, and today's high, low, sun and wind. */
    private View now(Forecast f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(icon(f.icon), box(64, 0, 0, 0, 0));
        TextView temp = text(f.temp + "°", 56, INK, Typeface.NORMAL);
        temp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        temp.setIncludeFontPadding(false);
        row.addView(temp, margins(18, 0, 22, 0));

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(Forecast.capital(f.sky), 24, INK, Typeface.NORMAL));
        words.addView(text("Feels like " + f.feels + "°  ·  Humidity " + f.humidity + "%  ·  Wind " + f.wind + " m/s",
                14, SOFT, Typeface.NORMAL), margins(0, 6, 0, 0));
        row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (f.days.length > 0) {
            Forecast.Day today = f.days[0];
            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            TextView hl = text("High " + today.max + "°   Low " + today.min + "°", 18, INK, Typeface.NORMAL);
            hl.setGravity(Gravity.END);
            right.addView(hl);
            TextView sun = text("Sunrise " + today.sunrise + "  ·  Sunset " + today.sunset, 13, SOFT, Typeface.NORMAL);
            sun.setGravity(Gravity.END);
            right.addView(sun, margins(0, 6, 0, 0));
            TextView rain = text(today.rain + "% chance of rain", 13, RAIN, Typeface.NORMAL);
            rain.setGravity(Gravity.END);
            right.addView(rain, margins(0, 4, 0, 0));
            row.addView(right);
        }
        return row;
    }

    /** One column per slot: the time, the icon, the temperature and the chance of rain. */
    private View hours(Forecast f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(card(0x14FFFFFF));
        row.setPadding(dp(6), dp(10), dp(6), dp(10));
        for (int i = 0; i < f.hours.length; i++) {
            Forecast.Hour h = f.hours[i];
            LinearLayout cell = column();
            cell.addView(centred(text(i == 0 ? "Now" : h.time, 12, SOFT, Typeface.NORMAL)));
            cell.addView(icon(h.icon), box(26, 0, 6, 0, 6));
            cell.addView(centred(text(h.temp + "°", 17, INK, Typeface.NORMAL)));
            cell.addView(centred(text(h.rain + "%", 11, h.rain >= 30 ? RAIN : FAINT, Typeface.NORMAL)), margins(0, 3, 0, 0));
            row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        return row;
    }

    /** One card per day: its name and date, the icon, the high and low, and the chance of rain. */
    private View days(Forecast f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Locale here = Locale.getDefault();
        for (int i = 0; i < f.days.length; i++) {
            Forecast.Day d = f.days[i];
            LocalDate date = LocalDate.parse(d.date);
            LinearLayout cell = column();
            cell.setBackground(card(i == 0 ? 0x26FFFFFF : 0x14FFFFFF));
            cell.setPadding(dp(4), dp(10), dp(4), dp(10));
            String name = i == 0 ? "Today" : date.getDayOfWeek().getDisplayName(TextStyle.SHORT, here);
            cell.addView(centred(text(name, 15, INK, Typeface.BOLD)));
            cell.addView(centred(text(date.getDayOfMonth() + " " + date.getMonth().getDisplayName(TextStyle.SHORT, here),
                    11, FAINT, Typeface.NORMAL)), margins(0, 2, 0, 0));
            cell.addView(icon(d.icon), box(30, 0, 6, 0, 4));
            cell.addView(centred(text(Forecast.capital(d.sky), 11, SOFT, Typeface.NORMAL)));
            // Each day's high and low, named, so the two numbers can't be mistaken for each other.
            cell.addView(centred(text("High " + d.max + "°", 17, INK, Typeface.NORMAL)), margins(0, 8, 0, 0));
            cell.addView(centred(text("Low " + d.min + "°", 15, SOFT, Typeface.NORMAL)), margins(0, 2, 0, 0));
            cell.addView(centred(text(d.rain + "% rain", 11, d.rain >= 30 ? RAIN : FAINT, Typeface.NORMAL)),
                    margins(0, 3, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(i == 0 ? 0 : dp(5), 0, i == f.days.length - 1 ? 0 : dp(5), 0);
            row.addView(cell, lp);
        }
        return row;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        return c;
    }

    private TextView label(String s) {
        TextView t = text(s.toUpperCase(Locale.getDefault()), 12, SOFT, Typeface.BOLD);
        t.setLetterSpacing(0.14f);
        return t;
    }

    private TextView text(String s, float sp, int colour, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(Typeface.create("sans-serif", style));
        t.setSingleLine(!s.contains("\n"));
        if (s.contains("\n")) t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView centred(TextView t) {
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private View icon(String kind) {
        View v = new View(this);
        if (WeatherIcon.known(kind)) v.setBackground(new WeatherIcon(kind));
        return v;
    }

    private GradientDrawable card(int colour) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colour);
        g.setCornerRadius(dp(14));
        return g;
    }

    /** A square of {@code size} dp with margins, for an icon. */
    private LinearLayout.LayoutParams box(int size, int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    /** The page's full width, with margins. */
    private LinearLayout.LayoutParams full(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = margins(l, t, r, b);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        return lp;
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
