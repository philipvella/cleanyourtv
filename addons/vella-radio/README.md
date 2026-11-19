# Vella radio

Free UK radio for the Clean Your TV launcher's `radio_source` (docs/modules.md): the station plays from the middle of
Home's bottom strip. OK plays or stops it; holding OK opens the station list.

- **Stations:** the BBC's local and nations' stations whose area reaches the place in `options.xml`, then the
  national ones: BBC Radio 1 to 6 Music, Asian Network, World Service, Classic FM, Heart, Capital, Smooth, LBC,
  Radio X, Virgin Radio and Times Radio. Every stream is the broadcaster's own public one.
- **Default station:** the one OK on Home plays. Hold OK (or press Menu) on a station in the list to make it the
  default. Before that, it is `default_station` in `options.xml`, or the nearest local station when that is empty.
- **When it stops:** when you press OK again, when another app plays sound (a video starts), and when the TV goes to
  standby. It goes quiet while the assistant listens.

Permissions: `INTERNET` for the streams, and `FOREGROUND_SERVICE` (with `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) so it
keeps playing while you browse Home. There's no background work while it is stopped, and nothing is sent anywhere:
the place's latitude and longitude only pick the local stations, on the TV.

Build and install:

```bash
./gradlew --no-daemon assembleRelease
../../tools/venv/bin/python -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/radio.apk
../../tools/venv/bin/python -I ../../tools/tv.py "pm install -r /data/local/tmp/radio.apk; rm -f /data/local/tmp/radio.apk"
```

Then set `"radio_source": "io.github.philipvella.vellaradio"` in the theme.
