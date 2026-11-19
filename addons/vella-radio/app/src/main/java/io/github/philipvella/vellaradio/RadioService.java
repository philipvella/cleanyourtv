package io.github.philipvella.vellaradio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Plays one station with the platform's MediaPlayer, as a media-playback foreground service while it plays. Started
 * by Home (OK on the radio) or the station list with {@link #ACTION} and a {@link #COMMAND}: "toggle" (play the
 * station Home shows, or stop), "play" (with {@link #STATION}) or "stop". It stops for good when another app takes the
 * sound (a video starts), and when the TV goes to standby; it goes quiet while the assistant listens.
 */
public final class RadioService extends Service implements AudioManager.OnAudioFocusChangeListener {
    static final String ACTION = "com.gathin.tvhome.action.RADIO";
    static final String COMMAND = "command", STATION = "station";
    private static final String CHANNEL = "radio";
    private static final int NOTE = 1;
    /** A live stream that drops is tried again this many times, this far apart. */
    private static final int RETRIES = 3;
    private static final long RETRY_MS = 3000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private Stations.Station station;
    private int retries;
    /** Each play gets a number; a slow resolve for an earlier one is dropped. */
    private int generation;
    private boolean screenWatch;

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopRadio();
        }
    };

    static Intent command(Context ctx, String command, String stationId) {
        Intent i = new Intent(ctx, RadioService.class).setAction(ACTION).putExtra(COMMAND, command);
        if (stationId != null) i.putExtra(STATION, stationId);
        return i;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A foreground start must reach startForeground, even when the answer is to stop.
        foreground(station != null ? station : Radio.shown(this));
        String command = intent == null ? "stop" : String.valueOf(intent.getStringExtra(COMMAND));
        switch (command) {
            case "toggle":
                if (player != null || Radio.LOADING.equals(Radio.state())) stopRadio();
                else play(Radio.shown(this));
                break;
            case "play":
                Stations.Station s = Stations.byId(intent.getStringExtra(STATION));
                play(s != null ? s : Radio.shown(this));
                break;
            default:
                stopRadio();
        }
        return START_NOT_STICKY;
    }

    private void foreground(Stations.Station s) {
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_radio)
                .setContentTitle(s != null ? s.name : getString(R.string.app_name))
                .setContentText("Radio")
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTE, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTE, b.build());
        }
    }

    private void play(Stations.Station s) {
        if (s == null) {
            stopRadio();
            return;
        }
        release();
        station = s;
        retries = 0;
        foreground(s);
        if (!takeFocus()) {
            Radio.set(this, Radio.STOPPED, s.id);
            stopRadio();
            return;
        }
        watchScreen();
        start(s);
    }

    /** Resolves the stream off the main thread, then prepares it; the state goes loading, then playing. */
    private void start(Stations.Station s) {
        int run = ++generation;
        Radio.set(this, Radio.LOADING, s.id);
        new Thread(() -> {
            String url;
            try {
                url = s.hls() ? Hls.resolve(s.url) : s.url;
            } catch (Exception offline) {
                main.post(() -> {
                    if (run == generation) failed();
                });
                return;
            }
            main.post(() -> {
                if (run == generation) prepare(url);
            });
        }, "radio-resolve").start();
    }

    private void prepare(String url) {
        release();
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        mp.setOnPreparedListener(p -> {
            if (p != player) return;
            p.start();
            retries = 0;
            Radio.set(this, Radio.PLAYING, station.id);
        });
        mp.setOnErrorListener((p, what, extra) -> {
            if (p == player) failed();
            return true;
        });
        // A live stream has no end: when it does end, the connection dropped.
        mp.setOnCompletionListener(p -> {
            if (p == player) failed();
        });
        try {
            mp.setDataSource(url);
            mp.prepareAsync();
        } catch (Exception bad) {
            failed();
        }
    }

    /** The stream failed or dropped: try again a few times, then show the error and stop. */
    private void failed() {
        release();
        if (station != null && retries++ < RETRIES) {
            Radio.set(this, Radio.LOADING, station.id);
            int run = generation;
            main.postDelayed(() -> {
                if (run == generation && station != null) start(station);
            }, RETRY_MS);
            return;
        }
        String id = station != null ? station.id : null;
        stopRadio();
        Radio.set(this, Radio.ERROR, id);
    }

    private void stopRadio() {
        generation++;
        main.removeCallbacksAndMessages(null);
        release();
        dropFocus();
        if (screenWatch) {
            unregisterReceiver(screenOff);
            screenWatch = false;
        }
        Radio.set(this, Radio.STOPPED, station != null ? station.id : null);
        station = null;
        stopForeground(true);
        stopSelf();
    }

    private void release() {
        if (player == null) return;
        MediaPlayer p = player;
        player = null;
        try {
            p.reset();
        } catch (IllegalStateException ignored) {
            // Already torn down.
        }
        p.release();
    }

    private boolean takeFocus() {
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(this, main)
                    .build();
        }
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void dropFocus() {
        if (focus != null) audio.abandonAudioFocusRequest(focus);
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                stopRadio();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                volume(0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                volume(0.2f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                volume(1f);
                break;
            default:
                break;
        }
    }

    private void volume(float v) {
        if (player != null) player.setVolume(v, v);
    }

    private void watchScreen() {
        if (screenWatch) return;
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        screenWatch = true;
    }

    @Override
    public void onDestroy() {
        generation++;
        main.removeCallbacksAndMessages(null);
        release();
        dropFocus();
        if (screenWatch) unregisterReceiver(screenOff);
        screenWatch = false;
        if (!Radio.STOPPED.equals(Radio.state()) && !Radio.ERROR.equals(Radio.state())) {
            Radio.set(this, Radio.STOPPED, null);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
