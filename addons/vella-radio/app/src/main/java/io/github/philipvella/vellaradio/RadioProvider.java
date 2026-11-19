package io.github.philipvella.vellaradio;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

/**
 * What is playing, for the Clean Your TV launcher's radio_source: the station's name, who runs it, and its state
 * (playing, loading, stopped or error). Read-only and exported without a permission: it holds only what Home shows.
 */
public final class RadioProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.philipvella.vellaradio.now";
    static final Uri NOW = Uri.parse("content://" + AUTHORITY + "/now");
    static final String[] COLUMNS = {"station", "owner", "state", "id"};

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!"now".equals(uri.getLastPathSegment())) return null;
        Context ctx = getContext();
        if (ctx == null) return null;
        Stations.Station s = Radio.shown(ctx);
        MatrixCursor c = new MatrixCursor(COLUMNS);
        if (s != null) c.addRow(new Object[] {s.name, s.owner, Radio.state(), s.id});
        c.setNotificationUri(ctx.getContentResolver(), NOW);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".now";
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
