package io.github.philipvella.vellaradio;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The BBC's playlists list one stream, by a plain-http address that Android refuses (cleartext); the same address
 * answers on https. Fetches the playlist and gives the stream to play, on https.
 */
final class Hls {
    private Hls() {
    }

    static String resolve(String playlist) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(playlist).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0 && body.size() < 256 * 1024; ) body.write(buf, 0, n);
            String stream = stream(playlist, body.toString(StandardCharsets.UTF_8.name()));
            if (stream == null) throw new IOException("no stream in the playlist");
            return stream;
        } finally {
            c.disconnect();
        }
    }

    /**
     * The first stream a master playlist lists, resolved against the playlist's address and on https; the playlist
     * itself when it lists segments, not streams; null when it lists neither.
     */
    static String stream(String playlist, String body) {
        boolean master = body.contains("#EXT-X-STREAM-INF");
        if (!master) return body.contains("#EXTINF") ? playlist : null;
        boolean next = false;
        for (String raw : body.split("\r?\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                next = true;
            } else if (next && !line.isEmpty() && !line.startsWith("#")) {
                String url = URI.create(playlist).resolve(line).toString();
                return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
            }
        }
        return null;
    }
}
