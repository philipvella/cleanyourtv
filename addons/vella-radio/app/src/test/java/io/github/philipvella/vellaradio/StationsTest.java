package io.github.philipvella.vellaradio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class StationsTest {
    @Test
    public void idsAreUniqueAndEveryStreamIsHttps() {
        Set<String> ids = new HashSet<>();
        for (Stations.Station s : Stations.ALL) {
            assertTrue(s.id, ids.add(s.id));
            assertTrue(s.id, s.url.startsWith("https://"));
            assertFalse(s.id, s.name.isEmpty());
        }
    }

    @Test
    public void stAlbansHearsThreeCountiesFirstThenLondon() {
        List<Stations.Station> near = Stations.near(51.755, -0.336);
        assertEquals("bbc_three_counties_radio", near.get(0).id);
        assertTrue(near.stream().anyMatch(s -> s.id.equals("bbc_london")));
        assertFalse(near.stream().anyMatch(s -> s.id.equals("bbc_radio_cornwall")));
    }

    @Test
    public void glasgowHearsScotland() {
        assertTrue(Stations.near(55.86, -4.25).stream().anyMatch(s -> s.id.equals("bbc_radio_scotland_fm")));
    }

    @Test
    public void theDefaultIsTheChoiceThenTheBuildsThenTheNearest() {
        assertEquals("classic_fm", Stations.defaultFor("classic_fm", "bbc_radio_two", 51.755, -0.336).id);
        assertEquals("bbc_radio_two", Stations.defaultFor("", "bbc_radio_two", 51.755, -0.336).id);
        assertEquals("bbc_three_counties_radio", Stations.defaultFor("gone", "", 51.755, -0.336).id);
        // Mid-Atlantic: nothing local, so Radio 2.
        assertEquals("bbc_radio_two", Stations.defaultFor("", "", 40, -40).id);
    }

    @Test
    public void homeGetsShortNames() {
        String[][] cases = {
                {"bbc_three_counties_radio", "Three Counties"}, {"bbc_london", "London"}, {"bbc_radio_essex", "Essex"},
                {"bbc_wm", "WM"}, {"bbc_radio_scotland_fm", "Radio Scotland"}, {"bbc_radio_two", "Radio 2"},
                {"bbc_radio_four_extra", "Radio 4 Extra"}, {"bbc_6music", "6 Music"}, {"bbc_1xtra", "Radio 1Xtra"},
                {"bbc_world_service", "World Service"}, {"bbc_asian_network", "Asian Network"},
                {"heart_london", "Heart London"}, {"classic_fm", "Classic FM"}};
        for (String[] c : cases) assertEquals(c[0], c[1], Stations.shortName(Stations.byId(c[0])));
    }

    @Test
    public void nationalStationsAreNotLocal() {
        assertNotNull(Stations.byId("bbc_radio_fourfm"));
        assertTrue(Stations.national().stream().noneMatch(Stations.Station::local));
    }
}
