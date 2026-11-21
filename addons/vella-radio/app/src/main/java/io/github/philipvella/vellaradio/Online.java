package io.github.philipvella.vellaradio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * More stations, from Radio Browser (radio-browser.info): a free community directory of the stations' own public
 * streams. Only what was typed (and "GB" for the UK) is sent, with a plain app name as the User-Agent. Only MP3
 * streams on https are kept: the TV's player finds no track in AAC streams, and Android refuses plain http. Also how
 * the added stations are kept on the TV, as JSON. Plain Java, so it runs in JVM tests.
 */
final class Online {
    private Online() {
    }

    static final String AGENT = "VellaRadio/1.0";
    static final String PREFIX = "rb_";
    /** Radio Browser's servers, tried in turn. */
    static final String[] SERVERS = {"https://de1.api.radio-browser.info", "https://de2.api.radio-browser.info",
            "https://all.api.radio-browser.info"};
    /** Results shown at most; more are asked for, as some are dropped (http, duplicates, built in). */
    static final int SHOWN = 60;
    private static final int ASKED = 150;

    /** One station the directory found, ready to add. */
    static final class Found {
        final String id, name, url, country, tags;
        final int bitrate;

        Found(String id, String name, String url, String country, String tags, int bitrate) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.country = country;
            this.tags = tags;
            this.bitrate = bitrate;
        }

        Stations.Station station() {
            return Stations.online(id, name, url);
        }

        /** The small line under the name: "GB · 128 KBPS · JAZZ, SOUL" (upper-cased by the screen). */
        String detail() {
            StringBuilder b = new StringBuilder();
            if (!country.isEmpty()) b.append(country);
            if (bitrate > 0) b.append(b.length() > 0 ? " · " : "").append(bitrate).append(" kbps");
            String t = tags.replace(",", ", ");
            if (t.length() > 40) {
                int cut = t.lastIndexOf(", ", 40);
                t = t.substring(0, cut > 0 ? cut : 40);
            }
            if (!t.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(t);
            return b.toString();
        }
    }

    /** The search's path and query on any server: by name (or the most played when empty), MP3, working ones only. */
    static String searchPath(String query, boolean ukOnly) {
        StringBuilder b = new StringBuilder("/json/stations/search?");
        String q = query == null ? "" : query.trim();
        if (!q.isEmpty()) b.append("name=").append(encode(q)).append('&');
        if (ukOnly) b.append("countrycode=GB&");
        b.append("codec=MP3&hidebroken=true&order=clickcount&reverse=true&limit=").append(ASKED);
        return b.toString();
    }

    /**
     * The stations worth showing from the directory's answer, most played first: MP3 on https, checked working, one
     * per name and per stream, none the app has built in, at most {@link #SHOWN}.
     */
    static List<Found> results(String body, Collection<Stations.Station> builtIn) {
        Set<String> seen = new HashSet<>();
        for (Stations.Station s : builtIn) {
            seen.add(nameKey(s.name));
            seen.add(urlKey(s.url));
        }
        List<Found> out = new ArrayList<>();
        Object parsed = Json.parse(body);
        if (!(parsed instanceof List)) return out;
        for (Object o : (List<?>) parsed) {
            if (out.size() >= SHOWN) break;
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            String uuid = str(m.get("stationuuid"));
            String name = clean(str(m.get("name")));
            String url = str(m.get("url_resolved")).trim();
            if (url.isEmpty()) url = str(m.get("url")).trim();
            if (uuid.isEmpty() || name.isEmpty() || !playable(url, str(m.get("codec")))) continue;
            if (m.get("lastcheckok") instanceof Number && ((Number) m.get("lastcheckok")).intValue() == 0) continue;
            String nk = nameKey(name), uk = urlKey(url);
            if (nk.isEmpty() || seen.contains(nk) || seen.contains(uk)) continue;
            seen.add(nk);
            seen.add(uk);
            int bitrate = m.get("bitrate") instanceof Number ? ((Number) m.get("bitrate")).intValue() : 0;
            out.add(new Found(PREFIX + uuid, name, url, str(m.get("countrycode")).trim(),
                    clean(str(m.get("tags"))), bitrate));
        }
        return out;
    }

    /** Whether the TV can play it: an https MP3 stream. */
    static boolean playable(String url, String codec) {
        return url.toLowerCase(Locale.ROOT).startsWith("https://") && "MP3".equalsIgnoreCase(codec.trim());
    }

    /** Asks each server in turn; the last failure when none answers. Off the main thread. */
    static List<Found> search(String query, boolean ukOnly, Collection<Stations.Station> builtIn) throws IOException {
        IOException last = null;
        for (String server : SERVERS) {
            try {
                return results(get(server + searchPath(query, ukOnly)), builtIn);
            } catch (IOException e) {
                last = e;
            } catch (IllegalArgumentException garbled) {
                last = new IOException(garbled);
            }
        }
        throw last;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", AGENT);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0 && body.size() < 2 * 1024 * 1024; ) body.write(buf, 0, n);
                return body.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    /** The added stations as JSON, for the TV's preferences. */
    static String encode(List<Stations.Station> stations) {
        StringBuilder b = new StringBuilder("[");
        for (Stations.Station s : stations) {
            if (b.length() > 1) b.append(',');
            b.append("{\"id\":").append(Json.quote(s.id)).append(",\"name\":").append(Json.quote(s.name))
                    .append(",\"url\":").append(Json.quote(s.url)).append('}');
        }
        return b.append(']').toString();
    }

    /** The added stations back from {@link #encode}; nothing for anything unreadable, and never a non-https one. */
    static List<Stations.Station> decode(String json) {
        List<Stations.Station> out = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return out;
        Object parsed;
        try {
            parsed = Json.parse(json);
        } catch (IllegalArgumentException garbled) {
            return out;
        }
        if (!(parsed instanceof List)) return out;
        Set<String> ids = new HashSet<>();
        for (Object o : (List<?>) parsed) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            String id = str(m.get("id")), name = str(m.get("name")), url = str(m.get("url"));
            if (!id.startsWith(PREFIX) || name.isEmpty() || !url.startsWith("https://") || !ids.add(id)) continue;
            out.add(Stations.online(id, name, url));
        }
        return out;
    }

    /** Names as the directory has them, tidied: no stray or doubled spaces. */
    static String clean(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    /** "Jazz FM", "JAZZ-FM " and "Jazz FM!" are one station. */
    static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** The same stream with or without https, or a trailing slash. */
    static String urlKey(String url) {
        String u = url.trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "");
        return "url:" + (u.endsWith("/") ? u.substring(0, u.length() - 1) : u);
    }

    private static String str(Object o) {
        return o instanceof String ? (String) o : "";
    }

    private static String encode(String q) {
        try {
            return URLEncoder.encode(q, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
