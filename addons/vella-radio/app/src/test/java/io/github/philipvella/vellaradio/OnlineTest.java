package io.github.philipvella.vellaradio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class OnlineTest {
    private static String station(String uuid, String name, String url, String codec, int ok) {
        return "{\"stationuuid\":\"" + uuid + "\",\"name\":\"" + name + "\",\"url\":\"http://x\",\"url_resolved\":\""
                + url + "\",\"codec\":\"" + codec + "\",\"bitrate\":128,\"countrycode\":\"GB\",\"tags\":\"jazz,soul\","
                + "\"lastcheckok\":" + ok + ",\"geo_lat\":null,\"votes\":12}";
    }

    @Test
    public void keepsHttpsMp3OnlyOncePerNameAndStream() {
        String body = "[" + String.join(",",
                station("a", " Jazz  London ", "https://a.example/live.mp3", "MP3", 1),
                station("b", "Plain http", "http://b.example/live.mp3", "MP3", 1),
                station("c", "AAC only", "https://c.example/live", "AAC", 1),
                station("d", "jazz london!", "https://d.example/other.mp3", "MP3", 1),
                station("e", "Same stream", "https://A.example/live.mp3/", "MP3", 1),
                station("f", "Broken", "https://f.example/live.mp3", "MP3", 0),
                station("g", "Classic FM", "https://g.example/cfm.mp3", "MP3", 1),
                station("h", "Gold", "https://media-ssl.musicradio.com/GoldMP3", "MP3", 1),
                station("i", "Good one", "https://i.example/s", "mp3", 1)) + "]";
        List<Online.Found> found = Online.results(body, Arrays.asList(Stations.ALL));
        assertEquals(2, found.size());
        Online.Found first = found.get(0);
        assertEquals("rb_a", first.id);
        assertEquals("Jazz London", first.name);
        assertEquals("https://a.example/live.mp3", first.url);
        assertEquals("GB · 128 kbps · jazz, soul", first.detail());
        assertEquals("rb_i", found.get(1).id);
    }

    @Test
    public void showsAtMostSixty() {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) b.append(',');
            b.append(station("u" + i, "Station " + i, "https://s.example/" + i, "MP3", 1));
        }
        assertEquals(Online.SHOWN, Online.results(b.append(']').toString(), Collections.emptyList()).size());
    }

    @Test
    public void nothingFromAnAnswerThatIsNotAList() {
        assertTrue(Online.results("{\"error\":1}", Collections.emptyList()).isEmpty());
        assertTrue(Online.results("[]", Collections.emptyList()).isEmpty());
    }

    @Test
    public void theSearchSendsOnlyTheNameAndCountry() {
        assertEquals("/json/stations/search?name=jazz+fm&countrycode=GB&codec=MP3&hidebroken=true&order=clickcount"
                + "&reverse=true&limit=150", Online.searchPath(" jazz fm ", true));
        assertEquals("/json/stations/search?codec=MP3&hidebroken=true&order=clickcount&reverse=true&limit=150",
                Online.searchPath("", false));
        assertTrue(Online.searchPath("r&b", true).contains("name=r%26b&"));
    }

    @Test
    public void addedStationsRoundTrip() {
        List<Stations.Station> mine = Arrays.asList(
                Stations.online("rb_1", "Jazz \"London\" \\ Radio", "https://a.example/live.mp3"),
                Stations.online("rb_2", "Ünïcode FM", "https://b.example/s?x=1&y=2"));
        List<Stations.Station> back = Online.decode(Online.encode(mine));
        assertEquals(2, back.size());
        for (int i = 0; i < 2; i++) {
            assertEquals(mine.get(i).id, back.get(i).id);
            assertEquals(mine.get(i).name, back.get(i).name);
            assertEquals(mine.get(i).url, back.get(i).url);
            assertTrue(back.get(i).online);
            assertEquals(Stations.ONLINE, back.get(i).owner);
            assertFalse(back.get(i).local());
        }
    }

    @Test
    public void decodingSkipsAnythingOdd() {
        assertTrue(Online.decode("").isEmpty());
        assertTrue(Online.decode(null).isEmpty());
        assertTrue(Online.decode("not json").isEmpty());
        assertTrue(Online.decode("{}").isEmpty());
        List<Stations.Station> s = Online.decode("[{\"id\":\"rb_1\",\"name\":\"A\",\"url\":\"http://plain\"},"
                + "{\"id\":\"classic_fm\",\"name\":\"B\",\"url\":\"https://b\"},"
                + "{\"id\":\"rb_2\",\"name\":\"C\",\"url\":\"https://c\"},"
                + "{\"id\":\"rb_2\",\"name\":\"C again\",\"url\":\"https://c\"}, 3]");
        assertEquals(1, s.size());
        assertEquals("rb_2", s.get(0).id);
    }

    @Test
    public void addedNamesAreKeptWholeOnHome() {
        assertEquals("BBC Radio 2 Relay", Stations.shortName(Stations.online("rb_x", "BBC Radio 2 Relay", "https://x")));
    }

    @Test
    public void theDefaultSkipsRemovedAndFindsAdded() {
        Stations.Station mine = Stations.online("rb_9", "Mine", "https://m");
        java.util.function.Function<String, Stations.Station> find =
                id -> id.equals("rb_9") ? mine : Stations.byId(id);
        assertEquals("rb_9", Stations.defaultFor("rb_9", "gold", 51.755, -0.336, find, Collections.emptySet()).id);
        // The chosen one was removed: the build's.
        assertEquals("gold", Stations.defaultFor("rb_gone", "gold", 51.755, -0.336, find, Collections.emptySet()).id);
        // The build's was hidden too: the nearest one still on the list.
        String second = Stations.near(51.755, -0.336).get(1).id;
        assertEquals(second, Stations.defaultFor("", "gold", 51.755, -0.336, find,
                new HashSet<>(Arrays.asList("gold", "bbc_three_counties_radio"))).id);
        assertEquals("bbc_radio_two", Stations.defaultFor("", "", 40, -40, find, Collections.emptySet()).id);
    }

    @Test
    public void jsonReadsTheDirectorysShapes() {
        Object v = Json.parse(" [ {\"a\": \"x\\u00e9\\n\", \"b\": -1.5e2, \"c\": [true, false, null], \"d\": {}} ] ");
        Map<?, ?> m = (Map<?, ?>) ((List<?>) v).get(0);
        assertEquals("xé\n", m.get("a"));
        assertEquals(-150.0, (Double) m.get("b"), 0);
        assertEquals(Arrays.asList(true, false, null), m.get("c"));
        assertTrue(((Map<?, ?>) m.get("d")).isEmpty());
        assertNull(Json.parse("null"));
        assertEquals("\"a\\\"b\\\\c\\u0001\"", Json.quote("a\"b\\c\u0001"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void jsonRefusesGarbage() {
        Json.parse("[1, 2");
    }
}
