# My TV — rebuild runbook

Everything needed to put the Sony back to the clean setup from scratch (factory reset, new Mac, or
a Sony firmware update that undid things). Follow it top to bottom; each step says how to check it worked.

## The TV

| | |
|---|---|
| Set | Sony BRAVIA, 2018 XF-series, 4K, 2 GB RAM |
| Build | `BRAVIA_ATV3_4K_EU-user 9 PTT1.190515.001.S54 672201` (Android TV 9, API 28) |
| Matching upstream list | `examples/sony-kd-55xf8096/debloat.txt` — my edited copy is `examples/sony-vella/debloat.txt` |
| IP on home Wi‑Fi | `192.168.68.100` (check in Settings → Network → Status if it changed) |
| ADB | Network ADB on port 5555, `ro.adb.secure=1` (TV asks to approve each new computer) |
| My repo | `https://github.com/philipvella/cleanyourtv`, branch `vella-launcher` |
| Theme | `themes/vella/` (also kept at `~/work/vella-theme/`) |

## 0. Prerequisites on the Mac (one-time)

```bash
# Homebrew must exist: https://brew.sh
brew install git gh python openjdk@17 android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
gh auth login                      # GitHub.com → HTTPS → browser
```

Check:

```bash
/opt/homebrew/opt/openjdk@17/bin/java -version        # prints 17.x
ls /opt/homebrew/share/android-commandlinetools/platforms/   # android-34
```

## 1. Get my repo

```bash
mkdir -p ~/work && cd ~/work
git clone https://github.com/philipvella/cleanyourtv
cd cleanyourtv
git checkout vella-launcher
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # optional, for updates
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
```

## 2. Open ADB on the TV

On the TV remote:

```
Settings → Device Preferences → About → press OK on "Build" 7 times
← back → Developer options
   USB debugging      ON
   Network debugging  ON        (separate toggle, further down)
Settings → Device Preferences → Power → sleep/idle standby OFF (for the session)
Settings → Network → Status → note the IP
```

On the Mac:

```bash
cd ~/work/cleanyourtv
export TV_HOST=192.168.68.100            # use the IP from the TV
tools/venv/bin/python -I tools/tv.py --probe
```

Accept "Allow USB debugging?" on the TV (tick Always allow). Probe must print `BRAVIA 4K GB ATV3` and
`PTT1.190515.001.S54`. If it says **refused**: Network debugging is off, or the TV is asleep; fix and rerun.
If the TV moved IP, update `TV_HOST`.

## 3. Debloat

```bash
tools/disable.sh examples/sony-vella/debloat.txt
tools/venv/bin/python -I tools/tv.py reboot
```

After the reboot:

```bash
tools/venv/bin/python -I tools/tv.py 'pm list packages -d' | wc -l      # ~25–30
```

And on the TV: live TV, each HDMI input, Netflix/iPlayer/Prime, volume via CEC all work.

Lines I deliberately kept in that list (Alexa setup, YouView) are commented with `#`; keep it that way.

## 4. Build and install the launcher

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export TV_HOST=192.168.68.100
launcher/build-and-install.sh
```

The script builds, installs, makes it Home, and rolls back if Home does not land on it.
Check: press Home on the remote → black screen, clock top-left, dock of apps.

Stock launcher back instead: `tools/restore.sh examples/sony-vella/debloat.txt` (see §7).

## 5. Apply my theme

```bash
tools/theme.sh themes/vella
```

Home restarts with the house wallpaper. Tweak `themes/vella/config.json` and rerun to adjust.
`tools/theme.sh --reset` removes the theme.

## 6. Close ADB when done (optional but tidy)

```
TV: Settings → Device Preferences → Developer options → Network debugging OFF
```

```bash
tools/venv/bin/python -I tools/adb-closed.py 192.168.68.100     # exit 0 = closed
```

Turn it back on the same way whenever a step above is needed again.

## 7. Undo / recovery

| Problem | Do |
|---|---|
| One app or feature broke after debloat | `tools/venv/bin/python -I tools/tv.py 'pm enable <package>'` |
| Want the stock TV back | `tools/restore.sh examples/sony-vella/debloat.txt` (uses the record in `~/.cleanyourtv/`; a backup copy lives at `~/work/cleanyourtv-restore-record/`) |
| No Mac available | TV Settings → Apps → show system apps → Enable; Settings → Apps → Default home → Google launcher |
| Sony firmware update undid things | `tools/venv/bin/python -I tools/check.py examples/sony-vella/debloat.txt --fix`, then §4 and §5 again |
| Home is stuck on a weird app | `tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.google.android.tvlauncher'` |

## 8. Keeping the repo safe

After any change on the Mac:

```bash
cd ~/work/cleanyourtv
git add -A && git commit -m "what changed" && git push
```

Pull fixes from the original project:

```bash
git fetch upstream && git merge upstream/main
```

Theme files also live outside the repo at `~/work/vella-theme/`; copy changes into `themes/vella/` before committing.
