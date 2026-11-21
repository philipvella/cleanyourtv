package io.github.philipvella.vellaradio;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * What the radio is doing, shared by the service that plays it, the provider Home reads and the station list. Held in
 * memory: when the app's process is gone, nothing plays, and the state starts as stopped.
 */
final class Radio {
    static final String PLAYING = "playing", LOADING = "loading", STOPPED = "stopped", ERROR = "error";
    /** After a pause, Home keeps showing the paused station this long, so OK picks it up again; then the default. */
    static final long RESUME_MS = 15 * 60_000L;

    private static volatile String state = STOPPED;
    private static volatile String stationId = "";
    private static volatile long changedAt;

    private Radio() {
    }

    static String state() {
        return state;
    }

    static void set(Context ctx, String newState, String id) {
        state = newState;
        if (id != null && !id.isEmpty()) stationId = id;
        changedAt = System.currentTimeMillis();
        ctx.getContentResolver().notifyChange(RadioProvider.NOW, null);
    }

    /** The station Home shows: the one playing, or just paused, else the default. */
    static Stations.Station shown(Context ctx) {
        Stations.Station now = MyStations.find(ctx, stationId);
        boolean recent = System.currentTimeMillis() - changedAt < RESUME_MS;
        if (now != null && (!STOPPED.equals(state) || recent)) return now;
        return defaultStation(ctx);
    }

    static Stations.Station defaultStation(Context ctx) {
        return Stations.defaultFor(prefs(ctx).getString("default", ""), ctx.getString(R.string.default_station),
                latitude(ctx), longitude(ctx), id -> MyStations.find(ctx, id), MyStations.hidden(ctx));
    }

    static void setDefault(Context ctx, String id) {
        prefs(ctx).edit().putString("default", id).apply();
        ctx.getContentResolver().notifyChange(RadioProvider.NOW, null);
    }

    static double latitude(Context ctx) {
        return number(ctx.getString(R.string.latitude), 51.5);
    }

    static double longitude(Context ctx) {
        return number(ctx.getString(R.string.longitude), -0.12);
    }

    private static double number(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (RuntimeException bad) {
            return fallback;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("radio", Context.MODE_PRIVATE);
    }
}
