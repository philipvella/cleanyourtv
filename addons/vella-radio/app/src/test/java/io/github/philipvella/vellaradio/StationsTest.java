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
    public void nationalStationsAreNotLocal() {
        assertNotNull(Stations.byId("bbc_radio_fourfm"));
        assertTrue(Stations.national().stream().noneMatch(Stations.Station::local));
    }
}
