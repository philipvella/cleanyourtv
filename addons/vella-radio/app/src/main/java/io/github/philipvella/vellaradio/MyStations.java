package io.github.philipvella.vellaradio;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The TV's own changes to the list, kept in the radio's preferences: the stations added from the directory (Online),
 * and the built-in ones removed from the list. Everything that finds a station by its id goes through {@link #find},
 * so it sees the added ones too.
 */
final class MyStations {
    private static final String MINE = "mine", HIDDEN = "hidden";

    private MyStations() {
    }

    /** A built-in or added station by its id, removed from the list or not; null when there is none. */
    static Stations.Station find(Context ctx, String id) {
        if (id == null || id.trim().isEmpty()) return null;
        Stations.Station s = Stations.byId(id);
        if (s != null) return s;
        for (Stations.Station m : list(ctx)) if (m.id.equals(id.trim())) return m;
        return null;
    }

    /** The added stations, in the order they were added. */
    static List<Stations.Station> list(Context ctx) {
        return Online.decode(prefs(ctx).getString(MINE, ""));
    }

    static boolean has(Context ctx, String id) {
        for (Stations.Station m : list(ctx)) if (m.id.equals(id)) return true;
        return false;
    }

    static void add(Context ctx, Stations.Station s) {
        List<Stations.Station> mine = list(ctx);
        for (Stations.Station m : mine) if (m.id.equals(s.id)) return;
        mine.add(s);
        prefs(ctx).edit().putString(MINE, Online.encode(mine)).apply();
    }

    /** Takes a station off the list: an added one is forgotten, a built-in one hidden until restored. */
    static void remove(Context ctx, String id) {
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (Stations.byId(id) != null) {
            Set<String> hidden = new HashSet<>(hidden(ctx));
            hidden.add(id);
            e.putStringSet(HIDDEN, hidden);
        } else {
            List<Stations.Station> mine = list(ctx);
            List<Stations.Station> kept = new ArrayList<>();
            for (Stations.Station m : mine) if (!m.id.equals(id)) kept.add(m);
            e.putString(MINE, Online.encode(kept));
        }
        // A removed default gives way to the next in line (Stations.defaultFor).
        if (id.equals(prefs(ctx).getString("default", ""))) e.remove("default");
        e.apply();
        changed(ctx);
    }

    /** The built-in stations removed from the list. */
    static Set<String> hidden(Context ctx) {
        Set<String> s = prefs(ctx).getStringSet(HIDDEN, null);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(s));
    }

    static void restore(Context ctx) {
        prefs(ctx).edit().remove(HIDDEN).apply();
        changed(ctx);
    }

    /** Home shows the default, which a removal or restore can change. */
    private static void changed(Context ctx) {
        ctx.getContentResolver().notifyChange(RadioProvider.NOW, null);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("radio", Context.MODE_PRIVATE);
    }
}
