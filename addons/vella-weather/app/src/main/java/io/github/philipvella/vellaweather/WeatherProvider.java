package io.github.philipvella.vellaweather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The weather line for the Clean Your TV launcher's weather_source (docs/modules.md). Read-only and exported
 * without a permission: it holds only what Home shows anyway. Nothing is scheduled: a query answers with the saved
 * line and, when that is 30 minutes old (5 after a failure), fetches a new one and tells the launcher. The launcher
 * asks while Home is on screen, so nothing runs while it is not.
 */
public final class WeatherProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.philipvella.vellaweather.now";
    static final Uri NOW = Uri.parse("content://" + AUTHORITY + "/now");
    static final String[] COLUMNS = {"line", "place", "icon", "temp", "summary", "high", "low", "sky", "details"};
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean FETCHING = new AtomicBoolean();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"now".equals(uri.getLastPathSegment())) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        SharedPreferences saved = ctx.getSharedPreferences("now", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        String line = saved.getString("line", "");
        String icon = saved.getString("icon", "");
        String temp = saved.getString("temp", "");
        String summary = saved.getString("summary", "");
        long at = saved.getLong("at", 0);
        MatrixCursor c = new MatrixCursor(COLUMNS);
        if (!line.isEmpty() && now - at < Now.STALE_MS) c.addRow(new Object[] {line, ctx.getString(R.string.place), icon, temp, summary,
                saved.getString("high", ""), saved.getString("low", ""), saved.getString("sky", ""),
                saved.getString("details", "")});
        c.setNotificationUri(ctx.getContentResolver(), NOW);
        long due = saved.getBoolean("failed", false) ? Now.RETRY_MS : Now.REFRESH_MS;
        if (now - Math.max(at, saved.getLong("tried", 0)) >= due || now < at) refresh(ctx.getApplicationContext());
        return c;
    }

    private static void refresh(Context app) {
        if (!FETCHING.compareAndSet(false, true)) return;
        ONE.execute(() -> {
            SharedPreferences saved = app.getSharedPreferences("now", Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            try {
                Now.Reading reading = Now.fetch(Double.parseDouble(app.getString(R.string.latitude)),
                        Double.parseDouble(app.getString(R.string.longitude)));
                saved.edit().putString("line", reading.line).putString("icon", reading.icon).putString("temp", reading.temp)
                        .putString("summary", reading.summary)
                        .putString("high", reading.high).putString("low", reading.low).putString("sky", reading.sky)
                        .putString("details", reading.details).putLong("at", now).putLong("tried", now)
                        .putBoolean("failed", false).apply();
                app.getContentResolver().notifyChange(NOW, null);
            } catch (Exception e) {
                // Offline or a bad reply: keep the last line, try again in 5 minutes.
                saved.edit().putLong("tried", now).putBoolean("failed", true).apply();
            } finally {
                FETCHING.set(false);
            }
        });
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".line";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
