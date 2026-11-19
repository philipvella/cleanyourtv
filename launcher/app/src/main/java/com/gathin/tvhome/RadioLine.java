package com.gathin.tvhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * radio_source (config.xml or a theme): what a radio add-on is playing, in the middle of Home's bottom strip, and OK
 * to play or stop it. The add-on does the streaming; the launcher only reads its provider and starts its service, and
 * stays offline. Contract (docs/modules.md): a {@value #META} meta-data naming a provider whose {@code now} row has
 * "station", "owner" and "state" (playing, loading, stopped or error); a service for {@value #ACTION} that takes
 * "command" toggle; and an activity for {@value #STATIONS}, the station list.
 */
final class RadioLine {
    static final String META = "com.gathin.tvhome.radio";
    static final String ACTION = "com.gathin.tvhome.action.RADIO";
    static final String STATIONS = "com.gathin.tvhome.action.RADIO_STATIONS";
    private static final String[] COLUMNS = {"station", "owner", "state"};

    /** Called on the main thread; an empty station hides the block. */
    interface Listener {
        void onRadio(String station, String owner, String state);
    }

    private final Context ctx;
    private final String pkg;
    private final Uri uri;
    private final Handler main;
    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ContentObserver observer;
    private boolean watching;

    private RadioLine(Context ctx, String pkg, Uri uri, Handler main, Listener listener) {
        this.ctx = ctx;
        this.pkg = pkg;
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

    /** Null when the key is empty, the package is not installed, or it names no provider of its own. */
    static RadioLine create(Context ctx, String pkg, Handler main, Listener listener) {
        if (pkg == null || pkg.trim().isEmpty()) return null;
        String p = pkg.trim();
        try {
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(p, PackageManager.GET_META_DATA);
            String authority = info.metaData == null ? null : info.metaData.getString(META);
            if (authority == null || authority.trim().isEmpty()) return null;
            ProviderInfo provider = pm.resolveContentProvider(authority.trim(), 0);
            if (provider == null || !p.equals(provider.packageName)) return null;
            Uri uri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority.trim()).appendPath("now").build();
            return new RadioLine(ctx, p, uri, main, listener);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /** Play the station shown, or stop it. Started from Home while it is on screen, so Android lets it run in front. */
    boolean toggle() {
        Intent i = new Intent(ACTION).setPackage(pkg).putExtra("command", "toggle");
        List<ResolveInfo> found = ctx.getPackageManager().queryIntentServices(i, 0);
        if (found.isEmpty() || found.get(0).serviceInfo == null) return false;
        i.setClassName(pkg, found.get(0).serviceInfo.name);
        try {
            ctx.startForegroundService(i);
            return true;
        } catch (RuntimeException refused) {
            // Removed since Home started, or the system refused: nothing plays.
            return false;
        }
    }

    /** The add-on's station list, or null when it has none. */
    Intent stations() {
        Intent i = new Intent(STATIONS).setPackage(pkg);
        return ctx.getPackageManager().resolveActivity(i, 0) == null ? null : i;
    }

    void start() {
        if (!watching) {
            try {
                ctx.getContentResolver().registerContentObserver(uri, false, observer);
                watching = true;
            } catch (SecurityException e) {
                // The provider went away; the block keeps what it had.
            }
        }
        read();
    }

    void stop() {
        if (watching) ctx.getContentResolver().unregisterContentObserver(observer);
        watching = false;
    }

    void shutdown() {
        stop();
        io.shutdownNow();
    }

    void read() {
        try {
            io.execute(() -> {
                String station = "", owner = "", state = "";
                try (Cursor c = ctx.getContentResolver().query(uri, COLUMNS, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        station = column(c, "station");
                        owner = column(c, "owner");
                        state = column(c, "state");
                    }
                } catch (RuntimeException gone) {
                    return;
                }
                String s = station, o = owner, st = state;
                main.post(() -> listener.onRadio(s, o, st));
            });
        } catch (RejectedExecutionException shutDown) {
            // Home is being destroyed.
        }
    }

    private static String column(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        return i < 0 ? "" : WeatherLine.clean(c.getString(i));
    }
}
