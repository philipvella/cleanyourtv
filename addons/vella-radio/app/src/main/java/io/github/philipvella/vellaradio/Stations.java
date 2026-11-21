package io.github.philipvella.vellaradio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The free stations: the BBC's local and nations' stations, each with the rough middle and reach of its area, and the
 * national ones anyone in the UK can hear. Every stream is the broadcaster's own public one. Pure, so it runs in JVM
 * tests.
 */
final class Stations {
    private Stations() {
    }

    /** The BBC's public HLS playlists, by station id; Hls picks the stream from it. */
    private static final String BBC = "https://a.files.bbci.co.uk/ms6/live/3441A116-B12E-4D2F-ACA8-C1984642FA4B/"
            + "audio/simulcast/hls/uk/pc_hd_abr_v2/ak/";

    static final class Station {
        final String id, name, owner, url;
        /** Local stations: the middle of the area and how far it reaches, km. National: NaN. */
        final double lat, lon, reachKm;

        private Station(String id, String name, String owner, String url, double lat, double lon, double reachKm) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.url = url;
            this.lat = lat;
            this.lon = lon;
            this.reachKm = reachKm;
        }

        boolean local() {
            return !Double.isNaN(lat);
        }

        /** Whether the stream is an HLS playlist to resolve first (Hls), not a plain audio stream. */
        boolean hls() {
            return url.endsWith(".m3u8");
        }
    }

    private static Station bbcLocal(String id, String name, double lat, double lon, double reachKm) {
        return new Station(id, name, "BBC", BBC + id + ".m3u8", lat, lon, reachKm);
    }

    private static Station bbc(String id, String name) {
        return new Station(id, name, "BBC", BBC + id + ".m3u8", Double.NaN, Double.NaN, Double.NaN);
    }

    private static Station other(String id, String name, String owner, String url) {
        return new Station(id, name, owner, url, Double.NaN, Double.NaN, Double.NaN);
    }

    private static Station global(String id, String name, String stream) {
        // The MP3 stream: the TV's own player finds no track in Global's AAC one (MediaTek boards, Android 9).
        return other(id, name, "Global", "https://media-ssl.musicradio.com/" + stream + "MP3");
    }

    private static Station newsUk(String id, String name, String host, String stream) {
        return other(id, name, "News UK", "https://" + host + ".live.stream.broadcasting.news/" + stream);
    }

    private static Station otherLocal(String id, String name, String owner, String url, double lat, double lon, double reachKm) {
        return new Station(id, name, owner, url, lat, lon, reachKm);
    }

    static final Station[] ALL = {
            // England's BBC local radio.
            bbcLocal("bbc_three_counties_radio", "BBC Three Counties Radio", 51.90, -0.45, 50),
            bbcLocal("bbc_london", "BBC Radio London", 51.51, -0.13, 40),
            bbcLocal("bbc_radio_berkshire", "BBC Radio Berkshire", 51.45, -0.97, 45),
            bbcLocal("bbc_radio_bristol", "BBC Radio Bristol", 51.45, -2.59, 45),
            bbcLocal("bbc_radio_cambridge", "BBC Radio Cambridgeshire", 52.35, 0.00, 50),
            bbcLocal("bbc_radio_cornwall", "BBC Radio Cornwall", 50.40, -4.90, 70),
            bbcLocal("bbc_radio_coventry_warwickshire", "BBC CWR", 52.35, -1.55, 35),
            bbcLocal("bbc_radio_cumbria", "BBC Radio Cumbria", 54.60, -3.00, 70),
            bbcLocal("bbc_radio_derby", "BBC Radio Derby", 52.95, -1.50, 40),
            bbcLocal("bbc_radio_devon", "BBC Radio Devon", 50.70, -3.80, 65),
            bbcLocal("bbc_radio_essex", "BBC Essex", 51.80, 0.60, 50),
            bbcLocal("bbc_radio_gloucestershire", "BBC Radio Gloucestershire", 51.85, -2.20, 40),
            bbcLocal("bbc_radio_hereford_worcester", "BBC Hereford & Worcester", 52.15, -2.50, 45),
            bbcLocal("bbc_radio_humberside", "BBC Radio Humberside", 53.75, -0.40, 50),
            bbcLocal("bbc_radio_kent", "BBC Radio Kent", 51.20, 0.80, 50),
            bbcLocal("bbc_radio_lancashire", "BBC Radio Lancashire", 53.85, -2.60, 45),
            bbcLocal("bbc_radio_leeds", "BBC Radio Leeds", 53.80, -1.55, 35),
            bbcLocal("bbc_radio_leicester", "BBC Radio Leicester", 52.63, -1.13, 40),
            bbcLocal("bbc_radio_lincolnshire", "BBC Radio Lincolnshire", 53.10, -0.20, 60),
            bbcLocal("bbc_radio_manchester", "BBC Radio Manchester", 53.48, -2.24, 30),
            bbcLocal("bbc_radio_merseyside", "BBC Radio Merseyside", 53.41, -2.98, 30),
            bbcLocal("bbc_radio_newcastle", "BBC Newcastle", 54.97, -1.61, 55),
            bbcLocal("bbc_radio_norfolk", "BBC Radio Norfolk", 52.65, 1.00, 50),
            bbcLocal("bbc_radio_northampton", "BBC Radio Northampton", 52.24, -0.90, 40),
            bbcLocal("bbc_radio_nottingham", "BBC Radio Nottingham", 52.95, -1.15, 40),
            bbcLocal("bbc_radio_oxford", "BBC Radio Oxford", 51.75, -1.26, 45),
            bbcLocal("bbc_radio_sheffield", "BBC Radio Sheffield", 53.38, -1.47, 35),
            bbcLocal("bbc_radio_shropshire", "BBC Radio Shropshire", 52.70, -2.75, 40),
            bbcLocal("bbc_radio_solent", "BBC Radio Solent", 50.90, -1.40, 45),
            bbcLocal("bbc_radio_somerset_sound", "BBC Radio Somerset", 51.10, -3.00, 40),
            bbcLocal("bbc_radio_stoke", "BBC Radio Stoke", 53.00, -2.18, 35),
            bbcLocal("bbc_radio_suffolk", "BBC Radio Suffolk", 52.20, 1.00, 45),
            bbcLocal("bbc_radio_surrey", "BBC Radio Surrey", 51.25, -0.45, 35),
            bbcLocal("bbc_radio_sussex", "BBC Radio Sussex", 50.90, -0.20, 45),
            bbcLocal("bbc_tees", "BBC Tees", 54.57, -1.23, 35),
            bbcLocal("bbc_wm", "BBC Radio WM", 52.48, -1.90, 35),
            bbcLocal("bbc_radio_wiltshire", "BBC Wiltshire", 51.35, -1.95, 40),
            bbcLocal("bbc_radio_york", "BBC Radio York", 54.00, -1.10, 40),
            bbcLocal("bbc_radio_guernsey", "BBC Radio Guernsey", 49.45, -2.58, 20),
            bbcLocal("bbc_radio_jersey", "BBC Radio Jersey", 49.21, -2.13, 20),
            // The nations' own stations.
            bbcLocal("bbc_radio_scotland_fm", "BBC Radio Scotland", 56.80, -4.20, 230),
            bbcLocal("bbc_radio_nan_gaidheal", "BBC Radio nan Gàidheal", 57.50, -5.50, 170),
            bbcLocal("bbc_radio_wales_fm", "BBC Radio Wales", 52.30, -3.70, 130),
            bbcLocal("bbc_radio_cymru", "BBC Radio Cymru", 52.30, -3.70, 130),
            bbcLocal("bbc_radio_ulster", "BBC Radio Ulster", 54.60, -6.70, 95),
            bbcLocal("bbc_radio_foyle", "BBC Radio Foyle", 55.00, -7.30, 40),
            // Regional commercial stations.
            otherLocal("heart_london", "Heart London", "Global", "https://media-ssl.musicradio.com/HeartLondonMP3", 51.51, -0.13, 45),
            otherLocal("heart_essex", "Heart Essex", "Global", "https://media-ssl.musicradio.com/HeartEssexMP3", 51.80, 0.60, 45),
            otherLocal("capital_london", "Capital London", "Global", "https://media-ssl.musicradio.com/CapitalMP3", 51.51, -0.13, 45),
            otherLocal("smooth_london", "Smooth London", "Global", "https://media-ssl.musicradio.com/SmoothLondonMP3", 51.51, -0.13, 45),
            otherLocal("lbc_london", "LBC London", "Global", "https://media-ssl.musicradio.com/LBCLondonMP3", 51.51, -0.13, 45),
            // National.
            bbc("bbc_radio_one", "BBC Radio 1"),
            bbc("bbc_1xtra", "BBC Radio 1Xtra"),
            bbc("bbc_radio_one_dance", "BBC Radio 1 Dance"),
            bbc("bbc_radio_one_anthems", "BBC Radio 1 Anthems"),
            bbc("bbc_radio_two", "BBC Radio 2"),
            bbc("bbc_radio_three", "BBC Radio 3"),
            bbc("bbc_radio_three_unwind", "BBC Radio 3 Unwind"),
            bbc("bbc_radio_fourfm", "BBC Radio 4"),
            bbc("bbc_radio_four_extra", "BBC Radio 4 Extra"),
            bbc("bbc_radio_five_live", "BBC Radio 5 Live"),
            bbc("bbc_6music", "BBC Radio 6 Music"),
            bbc("bbc_asian_network", "BBC Asian Network"),
            bbc("bbc_world_service", "BBC World Service"),
            // Global's own free streams, as Global Player plays them.
            global("classic_fm", "Classic FM", "ClassicFM"),
            global("classic_fm_calm", "Classic FM Calm", "ClassicFMCalm"),
            global("gold", "Gold", "Gold"),
            global("heart", "Heart", "HeartUK"),
            global("heart_70s", "Heart 70s", "Heart70s"),
            global("heart_80s", "Heart 80s", "Heart80s"),
            global("heart_90s", "Heart 90s", "Heart90s"),
            global("heart_00s", "Heart 00s", "Heart00s"),
            global("heart_dance", "Heart Dance", "HeartDance"),
            global("heart_xmas", "Heart Xmas", "HeartXmas"),
            global("capital", "Capital", "CapitalUK"),
            global("capital_xtra", "Capital XTRA", "CapitalXTRANational"),
            global("capital_xtra_reloaded", "Capital XTRA Reloaded", "CapitalXTRAReloaded"),
            global("capital_dance", "Capital Dance", "CapitalDance"),
            global("capital_chill", "Capital Chill", "CapitalChill"),
            global("smooth", "Smooth", "SmoothUK"),
            global("smooth_chill", "Smooth Chill", "SmoothChill"),
            global("smooth_country", "Smooth Country", "SmoothCountry"),
            global("smooth_70s", "Smooth 70s", "Smooth70s"),
            global("lbc", "LBC", "LBCUK"),
            global("lbc_news", "LBC News", "LBCNewsUK"),
            global("radio_x", "Radio X", "RadioXUK"),
            global("radio_x_classic_rock", "Radio X Classic Rock", "RadioXClassicRock"),
            global("radio_x_chilled", "Radio X Chilled", "RadioXChilled"),
            // News UK's own free MP3 streams. Virgin Radio is left out: it streams only AAC, which this TV's player
            // cannot read.
            newsUk("talksport", "talkSPORT", "talksport", "stream-mp3"),
            newsUk("times_radio", "Times Radio", "times", "stream-mp3"),
    };

    /**
     * The name Home shows, with the owner on the line under it: "BBC Three Counties Radio" is "Three Counties",
     * "BBC Radio 2" is "Radio 2", "BBC Radio 6 Music" is "6 Music". The nations keep "Radio" ("Radio Scotland");
     * the others' names are short already.
     */
    static String shortName(Station s) {
        String n = s.name;
        if (!n.startsWith("BBC ")) return n;
        n = n.substring(4);
        if (n.startsWith("Radio 6 ")) return n.substring(6);
        if (!s.local()) return n.startsWith("Radio ") && !Character.isDigit(n.charAt(6)) ? n.substring(6) : n;
        if (s.reachKm >= 90) return n;
        if (n.startsWith("Radio ")) n = n.substring(6);
        if (n.endsWith(" Radio")) n = n.substring(0, n.length() - 6);
        return n;
    }

    static Station byId(String id) {
        if (id == null) return null;
        for (Station s : ALL) if (s.id.equals(id.trim())) return s;
        return null;
    }

    /** The local stations whose area reaches the place, nearest (for its reach) first. */
    static List<Station> near(double lat, double lon) {
        List<Station> out = new ArrayList<>();
        for (Station s : ALL) if (s.local() && km(lat, lon, s.lat, s.lon) <= s.reachKm) out.add(s);
        out.sort(Comparator.comparingDouble(s -> km(lat, lon, s.lat, s.lon) / s.reachKm));
        return out;
    }

    static List<Station> national() {
        List<Station> out = new ArrayList<>();
        for (Station s : ALL) if (!s.local()) out.add(s);
        return out;
    }

    /** The station OK on Home plays: the one chosen on the TV, else the build's default, else the nearest local, else Radio 2. */
    static Station defaultFor(String chosen, String built, double lat, double lon) {
        Station s = byId(chosen);
        if (s == null) s = byId(built);
        if (s == null) {
            List<Station> near = near(lat, lon);
            s = near.isEmpty() ? byId("bbc_radio_two") : near.get(0);
        }
        return s;
    }

    /** Great-circle distance, km. */
    static double km(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
