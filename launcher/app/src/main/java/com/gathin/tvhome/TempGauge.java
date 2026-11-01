package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;

/**
 * Today's temperature range as a round gauge, like a phone's lock-screen weather: an open ring from the low (left
 * end) to the high (right end), coloured from the low's colour to the high's, a dot where the temperature now sits,
 * the temperature in the middle and the low and high under the ring's two ends. Drawn with plain drawText, which
 * PlainTextFrame turns into paths.
 */
final class TempGauge extends View {
    /** The ring opens at the bottom: it starts at 135° (bottom left) and sweeps 270° clockwise. */
    private static final float START = 135f, SWEEP = 270f;
    /** Temperature, °C, to colour: icy blue, sky blue, green, warm yellow, orange. */
    private static final float[] STOPS = {-5, 5, 14, 22, 30};
    private static final int[] COLOURS = {0xFF7AA7FF, 0xFF5AC8FA, 0xFF8EDB6A, 0xFFFFD24D, 0xFFFF8A3D};

    private final float density;
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int now, low, high;
    private boolean shaderStale = true;

    TempGauge(Context context, Typeface face) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        ring.setStrokeWidth(5 * density);
        dot.setStyle(Paint.Style.FILL);
        float sp = context.getResources().getDisplayMetrics().scaledDensity;
        big.setColor(Color.WHITE);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTextSize(24 * sp);
        small.setColor(0xE6FFFFFF);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTextSize(12 * sp);
        if (face != null) {
            big.setTypeface(Typeface.create(face, Typeface.NORMAL));
            small.setTypeface(face);
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** The clock's typeface, so the numbers match the time on the right. */
    void face(Typeface face) {
        if (face == null || face == big.getTypeface()) return;
        big.setTypeface(face);
        small.setTypeface(face);
        invalidate();
    }

    void set(int now, int low, int high) {
        if (now == this.now && low == this.low && high == this.high) return;
        this.now = now;
        this.low = Math.min(low, high);
        this.high = Math.max(low, high);
        shaderStale = true;
        invalidate();
    }

    /** Where the temperature now sits between the low (0) and the high (1); the middle when they are equal. */
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
            int lo = colour(low), hi = colour(high);
            SweepGradient sweep = new SweepGradient(cx, cy, new int[]{lo, hi, hi, lo},
                    new float[]{0f, SWEEP / 360f, (SWEEP + 45f) / 360f, 1f});
            Matrix turn = new Matrix();
            turn.setRotate(START, cx, cy);
            sweep.setLocalMatrix(turn);
            ring.setShader(sweep);
            shaderStale = false;
        }
        canvas.drawArc(oval, START, SWEEP, false, ring);

        // The dot: a dark cut-out, then a white centre, on the ring where the temperature now sits.
        double a = Math.toRadians(START + SWEEP * position(now, low, high));
        float r = oval.width() / 2;
        float dx = cx + (float) (r * Math.cos(a)), dy = cy + (float) (r * Math.sin(a));
        dot.setColor(0xCC000000);
        canvas.drawCircle(dx, dy, 5.5f * density, dot);
        dot.setColor(Color.WHITE);
        canvas.drawCircle(dx, dy, 3.5f * density, dot);

        // The temperature now in the middle, optically centred on its digits rather than on the degree sign.
        String t = now + "°";
        float degree = big.measureText("°");
        Paint.FontMetrics m = big.getFontMetrics();
        canvas.drawText(t, cx + degree / 2, cy - (m.ascent + m.descent) / 2, big);

        // The low and the high under the ring's two ends.
        float endY = cy + (float) (r * Math.sin(Math.toRadians(START))) + small.getTextSize() * 0.9f;
        float endX = (float) (r * Math.cos(Math.toRadians(START)));
        canvas.drawText(String.valueOf(low), cx + endX, endY, small);
        canvas.drawText(String.valueOf(high), cx - endX, endY, small);
    }
}
