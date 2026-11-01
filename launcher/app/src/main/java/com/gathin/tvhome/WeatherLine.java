package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * weather_source (config.xml or a theme): one weather line and its place at the bottom left of Home, from an add-on
 * that publishes them, the way game_day reads its games. The launcher only reads and stays offline; the add-on does
 * the fetching. Contract: docs/modules.md.
 */
final class WeatherLine {
    static final String META = "com.gathin.tvhome.weather";
    /** Asked again this often while Home is on screen, so the add-on can fetch when its copy is due. */
    static final long REREAD_MS = 30 * 60_000L;
    /**
     * "icon", "temp" and "summary" are optional: without the icon the line shows alone, and with temp and summary the
     * temperature gets its own large line with the summary ("light rain · wind 10 m/s") under it. With "high" and
     * "low" (today's, as whole °C: "14", "6"), "sky" ("light rain") and "details" ("feels like 8° · wind 2 m/s") as
     * well, the temperature is a round gauge of today's range beside the sky's words, with the details under them.
     */
    private static final String[] COLUMNS = {"line", "place", "icon", "temp", "summary", "high", "low", "sky", "details"};

    /** Called on the main thread; an empty line hides the block. */
    interface Listener {
        void onWeather(String line, String place, String icon, String temp, String summary, String[] range);
    }

    private final Context ctx;
    private final Uri uri;
    private final Handler main;
    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private final Runnable reread = this::tick;
    private boolean watching;

    private WeatherLine(Context ctx, Uri uri, Handler main, Listener listener) {
        this.ctx = ctx;
        this.uri = uri;
        this.main = main;
        this.listener = listener;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                read();
            }
        };
    }

    /**
     * Null when the key is empty, the package is not installed, or it names no provider of its own in a
     * {@value #META} meta-data. Naming the package is the owner's opt-in.
     */
    static WeatherLine create(Context ctx, String pkg, Handler main, Listener listener) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg.trim(), PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            // The provider must be the named package's own, not another app's that claims the authority.
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !pkg.trim().equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim()).appendPath("now").build();
            return new WeatherLine(ctx, uri, main, listener);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Home is on screen: read now, watch for changes and ask again every {@link #REREAD_MS}. */
    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The add-on's provider went away; the line keeps what it had.
            }
        }
        tick();
    }

    /** Home is off screen: nothing runs until it is back. */
    void stop() {
        main.removeCallbacks(reread);
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    private void tick() {
        main.removeCallbacks(reread);
        read();
        main.postDelayed(reread, REREAD_MS);
    }

    /** One query off the main thread (the provider lives in the add-on's process), then the listener on the main thread. */
    void read() {
        try {
            io.execute(() -> {
                String line = "", place = "", icon = "", temp = "", summary = "";
                String[] range = {"", "", "", ""};   // high, low, sky, details
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        line = column(c, "line");
                        place = column(c, "place");
                        icon = column(c, "icon");
                        temp = column(c, "temp");
                        summary = column(c, "summary");
                        for (int k = 0; k < range.length; k++) range[k] = column(c, COLUMNS[5 + k]);
                    }
                } catch (RuntimeException gone) {
                    // Uninstalled or crashed mid-query: keep what is on screen.
                    return;
                }
                String l = line, p = place, i = icon, t = temp, s = summary;
                main.post(() -> listener.onWeather(l, p, i, t, s, range));
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    /** A text column by name, cleaned; empty when the add-on does not publish it. */
    private static String column(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        return i < 0 ? "" : clean(c.getString(i));
    }

    /** One short line: trimmed, no line breaks, at most 80 characters. */
    static String clean(String s) {
        if (s == null) return "";
        String one = s.replace('\n', ' ').replace('\r', ' ').trim();
        return one.length() > 80 ? one.substring(0, 80) : one;
    }
}
