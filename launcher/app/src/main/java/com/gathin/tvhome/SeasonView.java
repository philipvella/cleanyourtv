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
 * seasonal_scenes: a full-screen layer between the wallpaper and Home's text and tiles that plays the day's scene
 * (Season): autumn leaves, Halloween ghosts and glowing pumpkins, fireworks, Christmas snow with Father Christmas's
 * sleigh, winter snow, Valentine's hearts, spring blossom, summer fireflies, New Year fireworks and gold confetti,
 * Pancake Day pancakes, St Patrick's shamrocks, Easter eggs, Mothering Sunday flowers, Father's Day ties, birthday
 * balloons and confetti, anniversary rose petals and gold sparkles. Everything is drawn in code (Halloween reuses the
 * launcher's own ghost and pumpkin pictures), at about 30 frames a second and only between {@link #start} and
 * {@link #stop}; nothing is allocated per frame.
 */
final class SeasonView extends View {
    /** 20 frames a second: the calm pace below needs no more, and the TV does less. */
    private static final long FRAME_MS = 50;
    /**
     * Kept in the background, so the scene never pulls the eye from Home: the share of each scene's particles drawn,
     * the pace of every motion (falling, spinning, swaying, bursts, the sleigh), and the whole layer's opacity.
     */
    static final float CALM_SHARE = 0.45f, CALM_PACE = 0.55f, CALM_OPACITY = 0.75f;
    private static final double TAU = Math.PI * 2;
    private static final int MAX = 64;

    // Particle kinds; a scene uses one or two.
    private static final int LEAF = 0, PETAL = 1, FLAKE = 2, HEART = 3, FIREFLY = 4, GHOST = 5, CONFETTI = 6,
            PANCAKE = 7, SHAMROCK = 8, EGG = 9, FLOWER = 10, TIE = 11, BALLOON = 12, ROSE = 13, SPARKLE = 14;

    private final float dp;
    private final Random rnd = new Random();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path leaf = new Path(), heart = new Path(), petal = new Path(), egg = new Path(), tie = new Path(),
            zigzag = new Path(), rose = new Path();
    private final RectF oval = new RectF();

    private Scene scene = Scene.NONE;
    private boolean running;
    /** setUp has run for the current scene. */
    private boolean ready;
    private long startMs, lastMs;
    private Bitmap ghost, pumpkin;

    private int count;
    private final int[] kind = new int[MAX], colour = new int[MAX];
    private final float[] x = new float[MAX], y = new float[MAX], vx = new float[MAX], vy = new float[MAX],
            size = new float[MAX], phase = new float[MAX], spin = new float[MAX], rot = new float[MAX];
    /** Which kinds a scene is made of: kindA for indices below {@code split}, kindB for the rest. */
    private int split, kindA, kindB;

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
    private static final int[] GOLD = {0xFFFFD54F, 0xFFFFE082, 0xFFFFC107, 0xFFFFF8E1, 0xFFE6B800};
    private static final int[] PARTY = {0xFFFF5252, 0xFFFFD54F, 0xFF69F0AE, 0xFF40C4FF, 0xFFE040FB, 0xFFFF9100};
    private static final int[] GREENS = {0xFF2E7D32, 0xFF43A047, 0xFF66BB6A, 0xFF1B5E20};
    private static final int[] PASTEL = {0xFFF8BBD0, 0xFFB3E5FC, 0xFFFFF59D, 0xFFC8E6C9, 0xFFD1C4E9, 0xFFFFCCBC};
    private static final int[] MUM = {0xFFF48FB1, 0xFFCE93D8, 0xFFFFAB91, 0xFFF06292, 0xFFFFF59D};
    private static final int[] TIES = {0xFF1E3A8A, 0xFF7F1D1D, 0xFF14532D, 0xFF0E7490, 0xFF374151};
    private static final int[] ROSES = {0xFFB71C1C, 0xFFD32F2F, 0xFFC2185B, 0xFFE57373};

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
        setAlpha(CALM_OPACITY);
        // Unit shapes, about one unit across, scaled per particle.
        leaf.moveTo(0, -0.5f);
        leaf.cubicTo(0.38f, -0.3f, 0.32f, 0.3f, 0, 0.5f);
        leaf.cubicTo(-0.32f, 0.3f, -0.38f, -0.3f, 0, -0.5f);
        leaf.close();
        heart.moveTo(0, 0.35f);
        heart.cubicTo(-0.6f, -0.05f, -0.3f, -0.6f, 0, -0.25f);
        heart.cubicTo(0.3f, -0.6f, 0.6f, -0.05f, 0, 0.35f);
        heart.close();
        petal.addOval(new RectF(-0.5f, -0.3f, 0.5f, 0.3f), Path.Direction.CW);
        // An egg: rounder at the bottom.
        egg.moveTo(0, -0.5f);
        egg.cubicTo(0.3f, -0.5f, 0.38f, 0.05f, 0.36f, 0.18f);
        egg.cubicTo(0.34f, 0.42f, 0.18f, 0.5f, 0, 0.5f);
        egg.cubicTo(-0.18f, 0.5f, -0.34f, 0.42f, -0.36f, 0.18f);
        egg.cubicTo(-0.38f, 0.05f, -0.3f, -0.5f, 0, -0.5f);
        egg.close();
        zigzag.moveTo(-0.37f, 0.05f);
        for (int i = 1; i <= 8; i++) zigzag.lineTo(-0.37f + i * 0.0925f, i % 2 == 1 ? -0.07f : 0.05f);
        // A necktie: the knot, then the blade widening to a point.
        tie.moveTo(-0.1f, -0.5f);
        tie.lineTo(0.1f, -0.5f);
        tie.lineTo(0.07f, -0.36f);
        tie.lineTo(0.2f, 0.32f);
        tie.lineTo(0, 0.5f);
        tie.lineTo(-0.2f, 0.32f);
        tie.lineTo(-0.07f, -0.36f);
        tie.close();
        // A rose petal: a rounded cup with a notch.
        rose.moveTo(0, 0.5f);
        rose.cubicTo(-0.55f, 0.2f, -0.5f, -0.45f, -0.12f, -0.42f);
        rose.quadTo(0, -0.3f, 0.12f, -0.42f);
        rose.cubicTo(0.5f, -0.45f, 0.55f, 0.2f, 0, 0.5f);
        rose.close();
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
        kindB = -1;
        switch (s) {
            case AUTUMN: kindA = LEAF; count = 14; break;
            case SPRING: kindA = PETAL; count = 22; break;
            case WINTER: kindA = FLAKE; count = 60; break;
            case CHRISTMAS: kindA = FLAKE; count = 48; break;
            case VALENTINE: kindA = HEART; count = 14; break;
            case SUMMER: kindA = FIREFLY; count = 18; break;
            case HALLOWEEN: kindA = GHOST; count = 5; break;
            case NEW_YEAR: kindA = CONFETTI; count = 36; break;
            case PANCAKE_DAY: kindA = PANCAKE; count = 9; break;
            case ST_PATRICKS: kindA = SHAMROCK; count = 16; break;
            case EASTER: kindA = EGG; count = 12; break;
            case MOTHERS_DAY: kindA = FLOWER; count = 16; break;
            case FATHERS_DAY: kindA = TIE; count = 12; break;
            case BIRTHDAY: kindA = BALLOON; kindB = CONFETTI; split = 9; count = 45; break;
            case ANNIVERSARY: kindA = ROSE; kindB = SPARKLE; split = 16; count = 36; break;
            default: count = 0;
        }
        // Fewer of everything, the two kinds of a mixed scene in the same proportion.
        if (count > 0) {
            if (kindB >= 0) split = Math.max(2, Math.round(split * CALM_SHARE));
            count = Math.max(3, Math.round(count * CALM_SHARE));
        }
        if (kindB < 0) split = count;
        for (int i = 0; i < count; i++) spawn(i, w, h, true);
        for (int b = 0; b < BURSTS; b++) bt[b] = -1;
        nextBurst = 0.5f;
        sleighStart = 6;
        if (s == Scene.HALLOWEEN && ghost == null) {
            ghost = BitmapFactory.decodeResource(getResources(), R.drawable.halloween_ghost);
            pumpkin = BitmapFactory.decodeResource(getResources(), R.drawable.halloween_pumpkin_lit);
        }
    }

    /** A falling particle: starts above the top edge, or anywhere on the first frame. */
    private void falling(int i, int h, boolean anywhere, float speedDp, float spreadDp, float swayDp) {
        y[i] = anywhere ? rnd.nextFloat() * h : -40 * dp;
        vy[i] = (speedDp + rnd.nextFloat() * spreadDp) * dp;
        vx[i] = (swayDp + rnd.nextFloat() * swayDp) * dp;
    }

    /** A rising particle: starts below the bottom edge, or anywhere on the first frame. */
    private void rising(int i, int h, boolean anywhere, float speedDp, float spreadDp, float swayDp) {
        y[i] = anywhere ? rnd.nextFloat() * h : h + 60 * dp;
        vy[i] = -(speedDp + rnd.nextFloat() * spreadDp) * dp;
        vx[i] = (swayDp + rnd.nextFloat() * swayDp) * dp;
    }

    private int any(int[] palette) {
        return palette[rnd.nextInt(palette.length)];
    }

    /** A new particle; {@code anywhere} scatters it over the screen (the first frame), else it enters at the edge. */
    private void spawn(int i, int w, int h, boolean anywhere) {
        int k = i < split ? kindA : kindB;
        kind[i] = k;
        x[i] = rnd.nextFloat() * w;
        phase[i] = (float) (rnd.nextFloat() * TAU);
        rot[i] = rnd.nextFloat() * 360;
        spin[i] = (rnd.nextBoolean() ? 1 : -1) * (30 + rnd.nextFloat() * 60);
        switch (k) {
            case LEAF:
                falling(i, h, anywhere, 28, 30, 18);
                size[i] = (18 + rnd.nextFloat() * 14) * dp;
                colour[i] = any(AUTUMN);
                break;
            case PETAL:
                falling(i, h, anywhere, 20, 22, 16);
                size[i] = (8 + rnd.nextFloat() * 7) * dp;
                spin[i] *= 1.5f;
                colour[i] = any(BLOSSOM);
                break;
            case FLAKE:
                size[i] = (1.2f + rnd.nextFloat() * 2.8f) * dp;
                falling(i, h, anywhere, 14 + size[i] / dp * 9, 10, 6);   // bigger flakes, nearer, faster
                colour[i] = (int) (120 + rnd.nextFloat() * 120) << 24 | 0xFFFFFF;
                break;
            case HEART:
                rising(i, h, anywhere, 18, 20, 10);
                size[i] = (16 + rnd.nextFloat() * 16) * dp;
                colour[i] = any(HEARTS);
                break;
            case FIREFLY:
                y[i] = rnd.nextFloat() * h;                          // home position; fireflies wander round it
                vx[i] = (20 + rnd.nextFloat() * 50) * dp;            // wander reach
                vy[i] = 0.15f + rnd.nextFloat() * 0.35f;             // wander speed
                spin[i] = 0.6f + rnd.nextFloat() * 1.4f;             // pulse speed
                size[i] = (1.8f + rnd.nextFloat() * 1.4f) * dp;
                break;
            case GHOST:
                rising(i, h, anywhere, 16, 14, 20);
                if (anywhere) y[i] = h * (0.3f + rnd.nextFloat() * 0.7f);
                size[i] = (40 + rnd.nextFloat() * 22) * dp;
                break;
            case CONFETTI:
                falling(i, h, anywhere, 40, 40, 14);
                size[i] = (5 + rnd.nextFloat() * 4) * dp;
                spin[i] *= 4;
                colour[i] = scene == Scene.NEW_YEAR ? any(GOLD) : any(PARTY);
                break;
            case PANCAKE:
                falling(i, h, anywhere, 22, 18, 24);
                size[i] = (34 + rnd.nextFloat() * 18) * dp;
                spin[i] = 0.5f + rnd.nextFloat() * 0.7f;             // flips per second
                break;
            case SHAMROCK:
                falling(i, h, anywhere, 24, 22, 18);
                size[i] = (16 + rnd.nextFloat() * 12) * dp;
                colour[i] = any(GREENS);
                break;
            case EGG:
                falling(i, h, anywhere, 20, 16, 16);
                size[i] = (26 + rnd.nextFloat() * 14) * dp;
                colour[i] = any(PASTEL);
                break;
            case FLOWER:
                falling(i, h, anywhere, 18, 18, 18);
                size[i] = (16 + rnd.nextFloat() * 12) * dp;
                colour[i] = any(MUM);
                break;
            case TIE:
                falling(i, h, anywhere, 24, 20, 20);
                size[i] = (30 + rnd.nextFloat() * 16) * dp;
                colour[i] = any(TIES);
                break;
            case BALLOON:
                rising(i, h, anywhere, 24, 22, 10);
                size[i] = (34 + rnd.nextFloat() * 16) * dp;
                colour[i] = any(PARTY);
                break;
            case ROSE:
                falling(i, h, anywhere, 18, 18, 18);
                size[i] = (12 + rnd.nextFloat() * 9) * dp;
                colour[i] = any(ROSES);
                break;
            case SPARKLE:
                y[i] = rnd.nextFloat() * h;
                vx[i] = (10 + rnd.nextFloat() * 30) * dp;
                vy[i] = 0.1f + rnd.nextFloat() * 0.25f;
                spin[i] = 0.8f + rnd.nextFloat() * 1.6f;
                size[i] = (2 + rnd.nextFloat() * 2.5f) * dp;
                colour[i] = any(GOLD);
                break;
            default:
                break;
        }
    }

    /** The layer's opacity applies to each shape on its own: no full-screen offscreen buffer every frame. */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (scene == Scene.NONE) return;
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.1f, Math.max(0, (now - lastMs) / 1000f)) * CALM_PACE;
        lastMs = now;
        float t = (now - startMs) / 1000f * CALM_PACE;
        int w = getWidth(), h = getHeight();
        if (scene == Scene.HALLOWEEN) drawPumpkins(c, t, w, h);
        if (scene == Scene.FIREWORKS || scene == Scene.NEW_YEAR) drawFireworks(c, t, w, h);
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            if (kind[i] != FIREFLY && kind[i] != SPARKLE) {
                y[i] += vy[i] * dt;
                rot[i] += spin[i] * dt;
                boolean gone = vy[i] > 0 ? y[i] > h + 60 * dp : y[i] < -size[i] * 2.5f;
                if (gone) spawn(i, w, h, false);
            }
            drawParticle(c, i, t, h);
        }
        if (scene == Scene.CHRISTMAS) drawSleigh(c, t, w, h);
    }

    private void drawParticle(Canvas c, int i, float t, int h) {
        float sway = (float) Math.sin(t * 0.9f + phase[i]) * vx[i];
        float px = x[i] + sway, py = y[i];
        int k = kind[i];
        switch (k) {
            case LEAF: case PETAL: case SHAMROCK: case FLOWER: case ROSE: case EGG: case TIE: {
                // Leaves and petals flutter (the width follows a slow cosine); eggs and ties rock gently instead.
                boolean rocks = k == EGG || k == TIE;
                float flip = rocks ? 1f : 0.35f + 0.65f * Math.abs((float) Math.cos(t * 1.3f + phase[i]));
                c.save();
                c.translate(px, py);
                c.rotate(rocks ? (float) Math.sin(t * 0.8f + phase[i]) * 25 : rot[i]);
                c.scale(size[i] * flip, size[i]);
                drawShape(c, k, colour[i]);
                c.restore();
                break;
            }
            case FLAKE:
                fill.setColor(colour[i]);
                c.drawCircle(x[i] + (float) Math.sin(t * 0.7f + phase[i]) * vx[i], py, size[i], fill);
                break;
            case HEART: {
                float fade = Math.min(1f, Math.max(0f, y[i] / (h * 0.25f)));    // fade out near the top
                c.save();
                c.translate(px, py);
                c.rotate((float) Math.sin(t + phase[i]) * 12);
                c.scale(size[i], size[i]);
                fill.setColor(colour[i]);
                fill.setAlpha((int) (190 * fade));
                c.drawPath(heart, fill);
                c.restore();
                break;
            }
            case FIREFLY: case SPARKLE: {
                float fx = x[i] + (float) Math.sin(t * vy[i] + phase[i]) * vx[i];
                float fy = y[i] + (float) Math.cos(t * vy[i] * 0.8f + phase[i] * 1.3f) * vx[i] * 0.6f;
                float pulse = Math.max(0f, (float) Math.sin(t * spin[i] + phase[i]));
                int a = (int) (40 + 215 * pulse);
                fill.setColor(k == FIREFLY ? 0xFFE8FF8A : colour[i]);
                fill.setAlpha(a / 5);
                c.drawCircle(fx, fy, size[i] * 4, fill);
                fill.setAlpha(a / 3);
                c.drawCircle(fx, fy, size[i] * 2, fill);
                fill.setColor(k == FIREFLY ? 0xFFFFFFD0 : 0xFFFFFFFF);
                fill.setAlpha(a);
                c.drawCircle(fx, fy, size[i], fill);
                break;
            }
            case GHOST: {
                if (ghost == null) break;
                float fade = Math.min(1f, Math.min(y[i] / (h * 0.2f), (h - y[i]) / (h * 0.2f)));
                bitmapPaint.setAlpha((int) (170 * Math.max(0f, fade)));
                oval.set(px - size[i] / 2, py - size[i] / 2, px + size[i] / 2, py + size[i] / 2);
                c.drawBitmap(ghost, null, oval, bitmapPaint);
                bitmapPaint.setAlpha(255);
                break;
            }
            case CONFETTI: {
                // A paper square spinning in 3D: one side narrows and widens.
                float turn = Math.abs((float) Math.cos(t * 4 + phase[i]));
                float s = size[i];
                c.save();
                c.translate(px, py);
                c.rotate(rot[i]);
                fill.setColor(colour[i]);
                fill.setAlpha(230);
                c.drawRect(-s / 2 * turn, -s / 3, s / 2 * turn, s / 3, fill);
                c.restore();
                break;
            }
            case PANCAKE:
                drawPancake(c, i, px, py, t);
                break;
            case BALLOON:
                drawBalloon(c, i, px, py, t);
                break;
            default:
                break;
        }
    }

    /** One of the unit shapes, in the current transform. */
    private void drawShape(Canvas c, int k, int col) {
        fill.setColor(col);
        fill.setAlpha(225);
        switch (k) {
            case LEAF:
                c.drawPath(leaf, fill);
                line.setColor(0x66000000);
                line.setStrokeWidth(0.04f);
                c.drawLine(0, -0.45f, 0, 0.62f, line);              // the midrib and stem
                break;
            case PETAL:
                c.drawPath(petal, fill);
                break;
            case ROSE:
                c.drawPath(rose, fill);
                break;
            case SHAMROCK:
                for (int l = 0; l < 3; l++) {                       // three heart-shaped leaves round the centre
                    c.save();
                    c.rotate(l * 120);
                    c.translate(0, -0.22f);
                    c.scale(0.6f, 0.6f);
                    c.rotate(180);
                    c.drawPath(heart, fill);
                    c.restore();
                }
                line.setColor(col);
                line.setStrokeWidth(0.07f);
                c.drawLine(0, 0.05f, 0.12f, 0.55f, line);           // the stem
                break;
            case FLOWER:
                for (int p = 0; p < 5; p++) {                       // five round petals and a yellow heart
                    double a = TAU * p / 5;
                    c.drawCircle((float) Math.cos(a) * 0.26f, (float) Math.sin(a) * 0.26f, 0.22f, fill);
                }
                fill.setColor(0xFFFFD54F);
                c.drawCircle(0, 0, 0.15f, fill);
                break;
            case EGG:
                c.drawPath(egg, fill);
                line.setStrokeWidth(0.045f);
                line.setColor(0xCCFFFFFF);
                c.drawPath(zigzag, line);
                line.setColor(darker(col));
                c.drawLine(-0.24f, -0.2f, 0.24f, -0.2f, line);
                c.drawLine(-0.32f, 0.25f, 0.32f, 0.25f, line);
                break;
            case TIE:
                c.drawPath(tie, fill);
                line.setColor(0x55FFFFFF);                           // stripes across the blade
                line.setStrokeWidth(0.035f);
                for (float s = -0.25f; s < 0.3f; s += 0.14f) c.drawLine(-0.15f, s, 0.15f, s + 0.08f, line);
                break;
            default:
                break;
        }
    }

    private static int darker(int argb) {
        int r = ((argb >> 16) & 0xFF) * 3 / 5, g = ((argb >> 8) & 0xFF) * 3 / 5, b = (argb & 0xFF) * 3 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A pancake tossed and flipping: a golden disc whose height follows the flip, with browned spots. */
    private void drawPancake(Canvas c, int i, float px, float py, float t) {
        float flip = (float) Math.cos(t * spin[i] * TAU + phase[i]);
        float s = size[i];
        c.save();
        c.translate(px, py);
        c.rotate((float) Math.sin(t * 0.7f + phase[i]) * 15);
        c.scale(1, Math.max(0.12f, Math.abs(flip)));
        fill.setColor(0xFFE0A84E);
        oval.set(-s / 2, -s / 2, s / 2, s / 2);
        c.drawOval(oval, fill);
        fill.setColor(0xFFF3CC7A);
        oval.set(-s * 0.42f, -s * 0.42f, s * 0.42f, s * 0.42f);
        c.drawOval(oval, fill);
        fill.setColor(0xFFB9772F);                                  // browned spots, on the side facing us
        if (flip > 0) {
            c.drawCircle(-s * 0.15f, -s * 0.1f, s * 0.06f, fill);
            c.drawCircle(s * 0.18f, s * 0.05f, s * 0.05f, fill);
            c.drawCircle(-s * 0.02f, s * 0.2f, s * 0.045f, fill);
        } else {
            c.drawCircle(s * 0.1f, -s * 0.18f, s * 0.05f, fill);
            c.drawCircle(-s * 0.2f, s * 0.12f, s * 0.06f, fill);
        }
        c.restore();
    }

    /** A party balloon on a wavy string, with a highlight. */
    private void drawBalloon(Canvas c, int i, float px, float py, float t) {
        float s = size[i];
        fill.setColor(colour[i]);
        fill.setAlpha(225);
        oval.set(px - s * 0.4f, py - s * 0.5f, px + s * 0.4f, py + s * 0.5f);
        c.drawOval(oval, fill);
        c.drawCircle(px, py + s * 0.52f, s * 0.06f, fill);         // the knot
        fill.setColor(0x66FFFFFF);
        oval.set(px - s * 0.25f, py - s * 0.36f, px - s * 0.08f, py - s * 0.1f);
        c.drawOval(oval, fill);
        line.setColor(0x99FFFFFF);
        line.setStrokeWidth(1.2f * dp);
        float wave = (float) Math.sin(t * 2 + phase[i]) * s * 0.08f;
        c.drawLine(px, py + s * 0.56f, px + wave, py + s * 0.95f, line);
        c.drawLine(px + wave, py + s * 0.95f, px - wave, py + s * 1.35f, line);
    }

    // ---- Father Christmas ----------------------------------------------------------------------------

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

    // ---- Halloween pumpkins --------------------------------------------------------------------------

    /** Fixed spots at the screen's sides, as shares of its width and height, for the glowing pumpkins. */
    private static final float[][] PUMPKINS = {{0.055f, 0.30f}, {0.945f, 0.30f}, {0.075f, 0.52f}, {0.925f, 0.52f}};

    private void drawPumpkins(Canvas c, float t, int w, int h) {
        if (pumpkin == null) return;
        float ps = 54 * dp;
        if (glow.getShader() == null) {
            glow.setShader(new RadialGradient(0, 0, ps * 1.3f, 0xFFFF8A1E, 0x00FF8A1E, Shader.TileMode.CLAMP));
        }
        for (int k = 0; k < PUMPKINS.length; k++) {
            float px = PUMPKINS[k][0] * w, py = PUMPKINS[k][1] * h + (float) Math.sin(t * 1.1f + k) * 5 * dp;
            // A candle glow that flickers behind each pumpkin.
            float flicker = 0.7f + 0.3f * (float) Math.abs(Math.sin(t * 7.3f + k * 2.1f) * Math.sin(t * 3.1f + k));
            glow.setAlpha((int) (110 * flicker));
            c.save();
            c.translate(px, py);
            c.drawCircle(0, 0, ps * 1.3f, glow);
            c.restore();
            oval.set(px - ps / 2, py - ps / 2, px + ps / 2, py + ps / 2);
            c.drawBitmap(pumpkin, null, oval, bitmapPaint);
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
                bColour[b] = scene == Scene.NEW_YEAR && rnd.nextBoolean() ? any(GOLD) : any(FIREWORK);
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
