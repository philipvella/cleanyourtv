package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import com.gathin.tvhome.Season.Scene;

import java.util.Random;

/**
 * seasonal_scenes: a full-screen layer between the wallpaper and Home's text and tiles that plays the season's scene
 * (Season): falling autumn leaves, Halloween ghosts and glowing pumpkins, fireworks, Christmas snow with Father
 * Christmas's sleigh crossing now and then, winter snow, Valentine's hearts, spring blossom or summer fireflies.
 * Everything is drawn in code (Halloween reuses the launcher's own ghost and pumpkin pictures), at about 30 frames a
 * second and only between {@link #start} and {@link #stop}; nothing is allocated per frame.
 */
final class SeasonView extends View {
    private static final long FRAME_MS = 33;
    private static final double TAU = Math.PI * 2;
    private static final int MAX = 64;

    private final float dp;
    private final Random rnd = new Random();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path leaf = new Path(), heart = new Path(), petal = new Path();
    private final RectF oval = new RectF();

    private Scene scene = Scene.NONE;
    private boolean running;
    /** setUp has run for the current scene. */
    private boolean ready;
    private long startMs, lastMs;
    private Bitmap ghost, pumpkin;

    // Particles: position, speed, size, phases, colour; their meaning depends on the scene.
    private int count;
    private final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX],
            size = new float[MAX], phase = new float[MAX], spin = new float[MAX], rot = new float[MAX];
    private final int[] colour = new int[MAX];

    // Fireworks: bursts of sparks.
    private static final int BURSTS = 5, SPARKS = 36;
    private final float[] bx = new float[BURSTS], by = new float[BURSTS], bt = new float[BURSTS];
    private final int[] bColour = new int[BURSTS];
    private float nextBurst;

    // Christmas: the sleigh's next crossing, in seconds since start.
    private float sleighStart = 6;
    private static final float SLEIGH_SECONDS = 16, SLEIGH_EVERY = 75;

    private static final int[] AUTUMN = {0xFFC0392B, 0xFFE67E22, 0xFFF1C40F, 0xFFA04000, 0xFFD35400, 0xFF8E5A2B};
    private static final int[] BLOSSOM = {0xFFF8BBD0, 0xFFF48FB1, 0xFFFCE4EC, 0xFFFFFFFF};
    private static final int[] HEARTS = {0xFFE53950, 0xFFFF6F91, 0xFFFF8FA3, 0xFFD81B60};
    private static final int[] FIREWORK = {0xFFFFD54F, 0xFFFF5252, 0xFF69F0AE, 0xFF40C4FF, 0xFFE040FB, 0xFFFFFFFF};

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    SeasonView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Unit shapes, scaled per particle.
        leaf.moveTo(0, -0.5f);
        leaf.cubicTo(0.38f, -0.3f, 0.32f, 0.3f, 0, 0.5f);
        leaf.cubicTo(-0.32f, 0.3f, -0.38f, -0.3f, 0, -0.5f);
        leaf.close();
        heart.moveTo(0, 0.35f);
        heart.cubicTo(-0.6f, -0.05f, -0.3f, -0.6f, 0, -0.25f);
        heart.cubicTo(0.3f, -0.6f, 0.6f, -0.05f, 0, 0.35f);
        heart.close();
        petal.addOval(new RectF(-0.5f, -0.3f, 0.5f, 0.3f), Path.Direction.CW);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    /** Plays {@code s} from now while Home is on screen; NONE clears the layer. */
    void start(Scene s) {
        if (s != scene || !ready) setUp(s);
        if (scene == Scene.NONE) {
            stop();
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        if (!running) {
            running = true;
            lastMs = SystemClock.uptimeMillis();
            post(frame);
        }
    }

    /** Home is off screen: no frames until it is back. */
    void stop() {
        running = false;
        removeCallbacks(frame);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // A screensaver or another window over Home: pause, and carry on where it was when Home shows again.
        if (visibility != VISIBLE) removeCallbacks(frame);
        else if (running) {
            lastMs = SystemClock.uptimeMillis();
            removeCallbacks(frame);
            post(frame);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (scene != Scene.NONE) setUp(scene);
    }

    private void setUp(Scene s) {
        scene = s;
        ready = true;
        startMs = SystemClock.uptimeMillis();
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        switch (s) {
            case AUTUMN: count = 14; break;
            case SPRING: count = 22; break;
            case WINTER: count = 60; break;
            case CHRISTMAS: count = 48; break;
            case VALENTINE: count = 14; break;
            case SUMMER: count = 18; break;
            case HALLOWEEN: count = 5; break;
            default: count = 0;
        }
        for (int i = 0; i < count; i++) spawn(i, w, h, true);
        for (int b = 0; b < BURSTS; b++) bt[b] = -1;
        nextBurst = 0.5f;
        sleighStart = 6;
        if (s == Scene.HALLOWEEN && ghost == null) {
            ghost = BitmapFactory.decodeResource(getResources(), R.drawable.halloween_ghost);
            pumpkin = BitmapFactory.decodeResource(getResources(), R.drawable.halloween_pumpkin_lit);
        }
    }

    /** A new particle; {@code anywhere} scatters it over the screen (the first frame), else it enters at the edge. */
    private void spawn(int i, int w, int h, boolean anywhere) {
        x[i] = rnd.nextFloat() * w;
        phase[i] = (float) (rnd.nextFloat() * TAU);
        rot[i] = rnd.nextFloat() * 360;
        switch (scene) {
            case AUTUMN:
                y[i] = anywhere ? rnd.nextFloat() * h : -30 * dp;
                vy[i] = (28 + rnd.nextFloat() * 30) * dp;
                vx[i] = (18 + rnd.nextFloat() * 22) * dp;            // sway amplitude
                size[i] = (18 + rnd.nextFloat() * 14) * dp;
                spin[i] = (rnd.nextBoolean() ? 1 : -1) * (30 + rnd.nextFloat() * 60);
                colour[i] = AUTUMN[rnd.nextInt(AUTUMN.length)];
                break;
            case SPRING:
                y[i] = anywhere ? rnd.nextFloat() * h : -20 * dp;
                vy[i] = (20 + rnd.nextFloat() * 22) * dp;
                vx[i] = (16 + rnd.nextFloat() * 20) * dp;
                size[i] = (8 + rnd.nextFloat() * 7) * dp;
                spin[i] = (rnd.nextBoolean() ? 1 : -1) * (40 + rnd.nextFloat() * 80);
                colour[i] = BLOSSOM[rnd.nextInt(BLOSSOM.length)];
                break;
            case WINTER: case CHRISTMAS:
                y[i] = anywhere ? rnd.nextFloat() * h : -8 * dp;
                size[i] = (1.2f + rnd.nextFloat() * 2.8f) * dp;
                vy[i] = (14 + size[i] / dp * 9 + rnd.nextFloat() * 10) * dp;   // bigger flakes, nearer, faster
                vx[i] = (6 + rnd.nextFloat() * 14) * dp;
                colour[i] = (int) (120 + rnd.nextFloat() * 120) << 24 | 0xFFFFFF;
                break;
            case VALENTINE:
                y[i] = anywhere ? rnd.nextFloat() * h : h + 30 * dp;
                vy[i] = -(18 + rnd.nextFloat() * 20) * dp;          // rising
                vx[i] = (10 + rnd.nextFloat() * 18) * dp;
                size[i] = (16 + rnd.nextFloat() * 16) * dp;
                spin[i] = 0;
                colour[i] = HEARTS[rnd.nextInt(HEARTS.length)];
                break;
            case SUMMER:
                y[i] = rnd.nextFloat() * h;                          // home position; fireflies wander round it
                vx[i] = (20 + rnd.nextFloat() * 50) * dp;            // wander reach
                vy[i] = 0.15f + rnd.nextFloat() * 0.35f;             // wander speed
                spin[i] = 0.6f + rnd.nextFloat() * 1.4f;             // pulse speed
                size[i] = (1.8f + rnd.nextFloat() * 1.4f) * dp;
                break;
            case HALLOWEEN:
                // Ghosts rise slowly through the screen, swaying; they enter from below.
                y[i] = anywhere ? h * (0.3f + rnd.nextFloat() * 0.7f) : h + 60 * dp;
                vy[i] = -(16 + rnd.nextFloat() * 14) * dp;
                vx[i] = (20 + rnd.nextFloat() * 30) * dp;
                size[i] = (40 + rnd.nextFloat() * 22) * dp;
                break;
            default:
                break;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (scene == Scene.NONE) return;
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.1f, Math.max(0, (now - lastMs) / 1000f));
        lastMs = now;
        float t = (now - startMs) / 1000f;
        int w = getWidth(), h = getHeight();
        switch (scene) {
            case AUTUMN: drawFalling(c, t, dt, w, h, leaf); break;
            case SPRING: drawFalling(c, t, dt, w, h, petal); break;
            case WINTER: drawSnow(c, t, dt, w, h); break;
            case CHRISTMAS:
                drawSnow(c, t, dt, w, h);
                drawSleigh(c, t, w, h);
                break;
            case VALENTINE: drawHearts(c, t, dt, w, h); break;
            case SUMMER: drawFireflies(c, t, w, h); break;
            case HALLOWEEN: drawHalloween(c, t, dt, w, h); break;
            case FIREWORKS: drawFireworks(c, t, w, h); break;
            default: break;
        }
    }

    // ---- leaves and blossom ---------------------------------------------------------------------------

    private void drawFalling(Canvas c, float t, float dt, int w, int h, Path shape) {
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            y[i] += vy[i] * dt;
            rot[i] += spin[i] * dt;
            if (y[i] > h + 40 * dp) spawn(i, w, h, false);
            float sway = (float) Math.sin(t * 0.9f + phase[i]) * vx[i];
            // A leaf turning over: its width follows a slow cosine, so it flutters as it falls.
            float flip = 0.35f + 0.65f * Math.abs((float) Math.cos(t * 1.3f + phase[i]));
            c.save();
            c.translate(x[i] + sway, y[i]);
            c.rotate(rot[i]);
            c.scale(size[i] * flip, size[i]);
            fill.setColor(colour[i]);
            fill.setAlpha(215);
            c.drawPath(shape, fill);
            if (shape == leaf) {
                line.setColor(0x66000000);
                line.setStrokeWidth(0.04f);
                c.drawLine(0, -0.45f, 0, 0.62f, line);              // the midrib and stem
            }
            c.restore();
        }
    }

    // ---- snow and Father Christmas -------------------------------------------------------------------

    private void drawSnow(Canvas c, float t, float dt, int w, int h) {
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            y[i] += vy[i] * dt;
            if (y[i] > h + 10 * dp) spawn(i, w, h, false);
            float drift = (float) Math.sin(t * 0.7f + phase[i]) * vx[i];
            fill.setColor(colour[i]);
            c.drawCircle(x[i] + drift, y[i], size[i], fill);
        }
    }

    /** Every SLEIGH_EVERY seconds, the sleigh and three reindeer cross the top of the screen, left to right. */
    private void drawSleigh(Canvas c, float t, int w, int h) {
        if (t > sleighStart + SLEIGH_SECONDS) sleighStart += SLEIGH_EVERY;
        float p = (t - sleighStart) / SLEIGH_SECONDS;
        if (p < 0 || p > 1) return;
        float u = 1.15f * dp;                                       // drawing unit
        float span = 250 * u;
        float sx = -span + p * (w + span * 1.2f);
        float sy = h * 0.115f + (float) Math.sin(p * TAU * 1.5f) * 10 * dp - p * 20 * dp;
        int ink = 0xF2FFF6E6;
        float gallop = (float) Math.sin(t * 9);
        // A trail of twinkles behind the sleigh.
        for (int k = 1; k <= 10; k++) {
            float tx = sx - k * 14 * u, ty = sy + 28 * u + (float) Math.sin(k * 1.7f + t * 4) * 5 * u;
            fill.setColor(ink);
            fill.setAlpha((int) (200 * (1 - k / 11f) * (0.5f + 0.5f * Math.abs(Math.sin(t * 6 + k)))));
            c.drawCircle(tx, ty, (2.2f - k * 0.15f) * u, fill);
        }
        c.save();
        c.translate(sx, sy);
        fill.setColor(ink);
        line.setColor(ink);
        line.setStrokeWidth(2.2f * u);
        // Sleigh: body, runner with its curl, Santa and the sack.
        oval.set(4 * u, 12 * u, 64 * u, 28 * u);
        c.drawRoundRect(oval, 7 * u, 7 * u, fill);
        c.drawLine(0, 32 * u, 70 * u, 32 * u, line);
        c.drawLine(70 * u, 32 * u, 78 * u, 24 * u, line);
        c.drawLine(18 * u, 28 * u, 18 * u, 32 * u, line);
        c.drawLine(52 * u, 28 * u, 52 * u, 32 * u, line);
        c.drawCircle(14 * u, 8 * u, 10 * u, fill);                 // the sack
        oval.set(28 * u, 0, 48 * u, 18 * u);
        c.drawOval(oval, fill);                                     // Santa
        c.drawCircle(40 * u, -5 * u, 6 * u, fill);                  // his head
        line.setStrokeWidth(1.2f * u);
        c.drawLine(48 * u, 8 * u, 98 * u, 6 * u, line);             // the reins
        line.setStrokeWidth(2.2f * u);
        // Three reindeer, galloping.
        for (int r = 0; r < 3; r++) {
            float rx = 96 * u + r * 50 * u;
            float lift = (float) Math.sin(t * 9 + r) * 2 * u;
            oval.set(rx, 2 * u + lift, rx + 30 * u, 14 * u + lift);
            c.drawOval(oval, fill);
            c.drawLine(rx + 27 * u, 5 * u + lift, rx + 34 * u, -4 * u + lift, line);   // neck
            c.drawCircle(rx + 36 * u, -6 * u + lift, 4.5f * u, fill);                  // head
            c.drawLine(rx + 34 * u, -9 * u + lift, rx + 30 * u, -18 * u + lift, line); // antlers
            c.drawLine(rx + 38 * u, -9 * u + lift, rx + 42 * u, -18 * u + lift, line);
            float g = gallop * (r % 2 == 0 ? 1 : -1);
            c.drawLine(rx + 5 * u, 12 * u + lift, rx + (1 + 5 * g) * u, 22 * u, line);  // legs
            c.drawLine(rx + 10 * u, 12 * u + lift, rx + (12 - 5 * g) * u, 22 * u, line);
            c.drawLine(rx + 21 * u, 12 * u + lift, rx + (19 + 5 * g) * u, 22 * u, line);
            c.drawLine(rx + 26 * u, 12 * u + lift, rx + (29 - 5 * g) * u, 22 * u, line);
            if (r == 2) {                                           // Rudolph's nose
                fill.setColor(0xFFFF3B30);
                c.drawCircle(rx + 41 * u, -5 * u + lift, 2.2f * u, fill);
                fill.setColor(ink);
            }
        }
        c.restore();
    }

    // ---- hearts --------------------------------------------------------------------------------------

    private void drawHearts(Canvas c, float t, float dt, int w, int h) {
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            y[i] += vy[i] * dt;
            if (y[i] < -40 * dp) spawn(i, w, h, false);
            float sway = (float) Math.sin(t * 0.8f + phase[i]) * vx[i];
            float fade = Math.min(1f, Math.max(0f, y[i] / (h * 0.25f)));   // fade out near the top
            c.save();
            c.translate(x[i] + sway, y[i]);
            c.rotate((float) Math.sin(t + phase[i]) * 12);
            c.scale(size[i], size[i]);
            fill.setColor(colour[i]);
            fill.setAlpha((int) (190 * fade));
            c.drawPath(heart, fill);
            c.restore();
        }
    }

    // ---- fireflies -----------------------------------------------------------------------------------

    private void drawFireflies(Canvas c, float t, int w, int h) {
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            float fx = x[i] + (float) Math.sin(t * vy[i] + phase[i]) * vx[i];
            float fy = y[i] + (float) Math.cos(t * vy[i] * 0.8f + phase[i] * 1.3f) * vx[i] * 0.6f;
            float pulse = Math.max(0f, (float) Math.sin(t * spin[i] + phase[i]));
            int a = (int) (40 + 215 * pulse);
            fill.setColor(0xFFE8FF8A);
            fill.setAlpha(a / 5);
            c.drawCircle(fx, fy, size[i] * 4, fill);
            fill.setAlpha(a / 3);
            c.drawCircle(fx, fy, size[i] * 2, fill);
            fill.setColor(0xFFFFFFD0);
            fill.setAlpha(a);
            c.drawCircle(fx, fy, size[i], fill);
        }
    }

    // ---- Halloween -----------------------------------------------------------------------------------

    /** Fixed spots at the screen's sides, as shares of its width and height, for the glowing pumpkins. */
    private static final float[][] PUMPKINS = {{0.055f, 0.30f}, {0.945f, 0.30f}, {0.075f, 0.52f}, {0.925f, 0.52f}};

    private void drawHalloween(Canvas c, float t, float dt, int w, int h) {
        if (pumpkin != null) {
            float ps = 54 * dp;
            for (int k = 0; k < PUMPKINS.length; k++) {
                float px = PUMPKINS[k][0] * w, py = PUMPKINS[k][1] * h + (float) Math.sin(t * 1.1f + k) * 5 * dp;
                // A candle glow that flickers behind each pumpkin.
                float flicker = 0.7f + 0.3f * (float) Math.abs(Math.sin(t * 7.3f + k * 2.1f) * Math.sin(t * 3.1f + k));
                if (glow.getShader() == null) {
                    glow.setShader(new RadialGradient(0, 0, ps * 1.3f, 0xFFFF8A1E, 0x00FF8A1E, Shader.TileMode.CLAMP));
                }
                glow.setAlpha((int) (110 * flicker));
                c.save();
                c.translate(px, py);
                c.drawCircle(0, 0, ps * 1.3f, glow);
                c.restore();
                oval.set(px - ps / 2, py - ps / 2, px + ps / 2, py + ps / 2);
                c.drawBitmap(pumpkin, null, oval, bitmapPaint);
            }
        }
        if (ghost != null) {
            for (int i = 0; i < count; i++) {
                y[i] += vy[i] * dt;
                if (y[i] < -size[i] * 1.5f) spawn(i, w, h, false);
                float gx = x[i] + (float) Math.sin(t * 0.6f + phase[i]) * vx[i];
                float fade = Math.min(1f, Math.min(y[i] / (h * 0.2f), (h - y[i]) / (h * 0.2f)));
                bitmapPaint.setAlpha((int) (170 * Math.max(0f, fade)));
                float gs = size[i];
                oval.set(gx - gs / 2, y[i] - gs / 2, gx + gs / 2, y[i] + gs / 2);
                c.drawBitmap(ghost, null, oval, bitmapPaint);
            }
            bitmapPaint.setAlpha(255);
        }
    }

    // ---- fireworks -----------------------------------------------------------------------------------

    private void drawFireworks(Canvas c, float t, int w, int h) {
        if (t >= nextBurst) {
            for (int b = 0; b < BURSTS; b++) {
                if (bt[b] >= 0 && t - bt[b] < 2.2f) continue;
                bx[b] = w * (0.1f + rnd.nextFloat() * 0.8f);
                by[b] = h * (0.1f + rnd.nextFloat() * 0.35f);
                bt[b] = t;
                bColour[b] = FIREWORK[rnd.nextInt(FIREWORK.length)];
                break;
            }
            nextBurst = t + 0.7f + rnd.nextFloat() * 1.3f;
        }
        fill.setStyle(Paint.Style.FILL);
        float g = 60 * dp;                                          // gravity, px/s²
        for (int b = 0; b < BURSTS; b++) {
            if (bt[b] < 0) continue;
            float age = t - bt[b];
            if (age > 2.2f) {
                bt[b] = -1;
                continue;
            }
            float alpha = 1f - age / 2.2f;
            float speed = 110 * dp * (1f - age / 4f);
            for (int s = 0; s < SPARKS; s++) {
                double a = TAU * s / SPARKS + b;
                float r = speed * age * (0.75f + 0.25f * (float) Math.sin(s * 3.7f + b));
                float sx = bx[b] + (float) Math.cos(a) * r;
                float sy = by[b] + (float) Math.sin(a) * r + 0.5f * g * age * age;
                fill.setColor(bColour[b]);
                fill.setAlpha((int) (255 * alpha * alpha));
                c.drawCircle(sx, sy, 2.2f * dp, fill);
                fill.setAlpha((int) (90 * alpha));
                c.drawCircle(sx, sy, 5 * dp, fill);
            }
        }
    }
}
