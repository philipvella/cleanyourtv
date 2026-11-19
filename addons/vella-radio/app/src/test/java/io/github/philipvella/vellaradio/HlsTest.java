package io.github.philipvella.vellaradio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HlsTest {
    private static final String MASTER = "https://a.files.bbci.co.uk/ms6/live/x/audio/simulcast/hls/uk/pc_hd_abr_v2/ak/bbc_radio_two.m3u8";

    @Test
    public void theMastersStreamMovesToHttps() {
        String body = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=339200,CODECS=\"mp4a.40.2\"\n"
                + "http://as-hls-uk-live.akamaized.net/pool_1/live/uk/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d320000.norewind.m3u8\n";
        assertEquals("https://as-hls-uk-live.akamaized.net/pool_1/live/uk/bbc_radio_two/bbc_radio_two.isml/bbc_radio_two-audio%3d320000.norewind.m3u8",
                Hls.stream(MASTER, body));
    }

    @Test
    public void aRelativeStreamResolvesAgainstThePlaylist() {
        assertEquals("https://a.files.bbci.co.uk/ms6/live/x/audio/simulcast/hls/uk/pc_hd_abr_v2/ak/low/two.m3u8",
                Hls.stream(MASTER, "#EXTM3U\r\n#EXT-X-STREAM-INF:BANDWIDTH=1\r\nlow/two.m3u8\r\n"));
    }

    @Test
    public void aMediaPlaylistIsPlayedAsItIs() {
        assertEquals(MASTER, Hls.stream(MASTER, "#EXTM3U\n#EXTINF:6.4,\nseg-1.ts\n"));
        assertNull(Hls.stream(MASTER, "<html>gone</html>"));
    }
}
