package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;

/**
 * A round gauge like a phone's lock-screen widgets: an open ring from one end (left) to the other (right), shaded
 * from one colour to another, a dot at a position along it, a reading in the middle and a label under each end.
 * The weather uses it for today's temperature range, the clock for how much of the day or night is left (the part
 * already passed is dimmed). Drawn with plain drawText, which PlainTextFrame turns into paths.
 */
final class RingGauge extends View {
    /** The ring opens at the bottom: it starts at 150° (bottom left) and sweeps 240° clockwise, leaving room for the labels. */
    private static final float START = 150f, SWEEP = 240f;
    /** How far each colour is washed toward white, and the ring's opacity: a hint of colour, not a rainbow. */
    private static final float WASH = 0.6f;
    private static final int RING_ALPHA = 0xC0;
    /** The opacity of the ring's passed part, when it is dimmed. */
    private static final int PASSED_ALPHA = 0x48;
    /** Temperature, °C, to colour: icy blue, sky blue, green, warm yellow, orange. */
    private static final float[] STOPS = {-5, 5, 14, 22, 30};
    private static final int[] COLOURS = {0xFF7AA7FF, 0xFF5AC8FA, 0xFF8EDB6A, 0xFFFFD24D, 0xFFFF8A3D};

    private final float density;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Rect digit = new Rect();
    private String centre = "", left = "", right = "";
    private float position = 0.5f;
    private int from = Color.WHITE, to = Color.WHITE;
    private boolean dimPassed;
    private boolean shaderStale = true;

    RingGauge(Context context, Typeface face, float centreSp) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setStrokeWidth(5 * density);
        dot.setStyle(Paint.Style.FILL);
        float sp = context.getResources().getDisplayMetrics().scaledDensity;
        big.setColor(Color.WHITE);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTextSize(centreSp * sp);
        small.setColor(0xE6FFFFFF);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(12 * sp);
        face(face);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The clock's typeface, so the numbers match the rest of the strip. */
    void face(Typeface face) {
        if (face == null || face == big.getTypeface()) return;
        big.setTypeface(face);
        small.setTypeface(face);
        invalidate();
    }

    /**
     * What the gauge shows: the reading in the middle, the labels under the left and right ends, the dot's position
     * (0 at the left end, 1 at the right), the two end colours (washed here), and whether the part before the dot is dimmed.
     */
    void show(String centre, String left, String right, float position, int from, int to, boolean dimPassed) {
        if (centre.equals(this.centre) && left.equals(this.left) && right.equals(this.right) && position == this.position
                && from == this.from && to == this.to && dimPassed == this.dimPassed) return;
        this.centre = centre;
        this.left = left;
        this.right = right;
        this.position = Math.max(0f, Math.min(1f, position));
        shaderStale |= from != this.from || to != this.to;
        this.from = from;
        this.to = to;
        this.dimPassed = dimPassed;
        invalidate();
    }

    /** Today's temperature range, the temperature now in the middle. */
    void showTemperature(int now, int low, int high) {
        int lo = Math.min(low, high), hi = Math.max(low, high);
        show(now + "°", String.valueOf(lo), String.valueOf(hi), position(now, lo, hi), colour(lo), colour(hi), false);
    }

    /** Where a value sits between the low (0) and the high (1); the middle when they are equal. */
    static float position(int now, int low, int high) {
        if (high <= low) return 0.5f;
        return Math.max(0f, Math.min(1f, (now - low) / (float) (high - low)));
    }

    /** The colour of a temperature in °C, blended between the nearest stops. */
    static int colour(float t) {
        if (t <= STOPS[0]) return COLOURS[0];
        for (int i = 1; i < STOPS.length; i++) {
            if (t <= STOPS[i]) {
                float f = (t - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1]);
                return blend(COLOURS[i - 1], COLOURS[i], f);
            }
        }
        return COLOURS[COLOURS.length - 1];
    }

    private static int muted(int c) {
        int soft = blend(c, Color.WHITE, WASH);
        return (soft & 0x00FFFFFF) | (RING_ALPHA << 24);
    }

    private static int blend(int a, int b, float f) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        shaderStale = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float inset = ring.getStrokeWidth() / 2 + 3 * density;   // room for the dot's outline
        float side = Math.min(w, h);
        float cx = w / 2, cy = side / 2;
        oval.set(cx - side / 2 + inset, cy - side / 2 + inset, cx + side / 2 - inset, cy + side / 2 - inset);
        if (shaderStale) {
            int lo = muted(from), hi = muted(to);
            SweepGradient sweep = new SweepGradient(cx, cy, new int[]{lo, hi, hi, lo},
                    new float[]{0f, SWEEP / 360f, (SWEEP + 45f) / 360f, 1f});
            Matrix turn = new Matrix();
            turn.setRotate(START, cx, cy);
            sweep.setLocalMatrix(turn);
            ring.setShader(sweep);
            shaderStale = false;
        }
        float at = SWEEP * position;
        if (dimPassed && at > 0) {
            // What is left at full strength, what has passed faint: the bright stretch is the time remaining.
            ring.setAlpha(PASSED_ALPHA);
            canvas.drawArc(oval, START, at, false, ring);
            ring.setAlpha(0xFF);
            canvas.drawArc(oval, START + at, SWEEP - at, false, ring);
        } else {
            canvas.drawArc(oval, START, SWEEP, false, ring);
        }

        // The dot: a dark cut-out, then a white centre.
        double a = Math.toRadians(START + at);
        float r = oval.width() / 2;
        float dx = cx + (float) (r * Math.cos(a)), dy = cy + (float) (r * Math.sin(a));
        dot.setColor(0x99000000);
        canvas.drawCircle(dx, dy, 5.5f * density, dot);
        dot.setColor(Color.WHITE);
        canvas.drawCircle(dx, dy, 3.5f * density, dot);

        // The reading in the middle; a trailing degree sign is left out of the centring, so the digits sit centred.
        float shift = centre.endsWith("°") ? big.measureText("°") / 2 : 0;
        Paint.FontMetrics m = big.getFontMetrics();
        canvas.drawText(centre, cx + shift, cy - (m.ascent + m.descent) / 2, big);

        // The labels under the ring's two ends, clear of the round cap and of the dot when it sits there.
        small.getTextBounds("0", 0, 1, digit);
        float endY = cy + (float) (r * Math.sin(Math.toRadians(START))) + 5.5f * density + 3 * density + digit.height();
        float endX = (float) (r * Math.cos(Math.toRadians(START)));
        canvas.drawText(left, cx + endX, endY, small);
        canvas.drawText(right, cx - endX, endY, small);
    }
}
