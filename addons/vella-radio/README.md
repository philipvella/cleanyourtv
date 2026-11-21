# Vella radio

Free UK radio for the Clean Your TV launcher's `radio_source` (docs/modules.md): the station plays from the middle of
Home's bottom strip. OK plays or stops it; holding OK opens the station list.

- **Stations:** the local stations whose area reaches the place in `options.xml` (the BBC's local and nations'
  stations, and Heart, Capital, Smooth and LBC in London and Essex), then the national ones: the BBC's (Radio 1 to 6
  Music, Radio 1 Dance and Anthems, Radio 3 Unwind, Asian Network, World Service), Global's (Classic FM, Classic FM
  Calm and Christmas, Gold, Heart and its decades, Dance and Xmas, Capital, Capital XTRA, Dance and Chill, Smooth and
  its spin-offs, LBC, LBC News and Radio X), and News UK's (talkSPORT and Times Radio). Every stream is the
  broadcaster's own free public one, as its own app plays it, in MP3 where the broadcaster has both: the TV's player
  finds no track in Global's AAC streams. Virgin Radio streams only AAC, so it is not here. Bauer's stations
  (Absolute, Kiss, Magic, Greatest Hits, Planet Rock and others) are not here: Bauer streams them only through its own
  app.
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
