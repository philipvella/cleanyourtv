package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * weather_source: the icon beside the weather line, drawn in code (no bitmaps) from the name the add-on publishes
 * in its "icon" column: clear-day, clear-night, partly-cloudy-day, partly-cloudy-night, cloudy, fog, drizzle, rain,
 * snow or thunder. {@link #known} says whether a name is one of them; an unknown name shows no icon.
 */
final class WeatherIcon extends Drawable {
    private static final int SUN = 0xFFFFD36B, MOON = 0xFFF2E6C8, CLOUD = 0xFFFFFFFF, DROP = 0xFF8FD3FF,
            BOLT = 0xFFFFC94A, MIST = 0xCCFFFFFF, SHADE = 0x59000000;
    private final String kind;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path other = new Path();

    WeatherIcon(String kind) {
        this.kind = kind;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    static boolean known(String kind) {
        switch (kind) {
            case "clear-day": case "clear-night": case "partly-cloudy-day": case "partly-cloudy-night":
            case "cloudy": case "fog": case "drizzle": case "rain": case "snow": case "thunder":
                return true;
            default:
                return false;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        canvas.save();
        canvas.translate(b.left, b.top);
        // A faint shade under the glyph, one pixel down, so a white cloud reads over a light photo.
        canvas.translate(0, s * 0.02f);
        drawGlyph(canvas, s, true);
        canvas.translate(0, -s * 0.02f);
        drawGlyph(canvas, s, false);
        canvas.restore();
    }

    private void drawGlyph(Canvas c, float s, boolean shade) {
        switch (kind) {
            case "clear-day":
                sun(c, s, 0.5f, 0.5f, 0.2f, shade);
                break;
            case "clear-night":
                moon(c, s, 0.5f, 0.5f, 0.3f, shade);
                break;
            case "partly-cloudy-day":
                sun(c, s, 0.36f, 0.36f, 0.15f, shade);
                cloud(c, s, 0.06f, 0.08f, shade);
                break;
            case "partly-cloudy-night":
                moon(c, s, 0.36f, 0.34f, 0.2f, shade);
                cloud(c, s, 0.06f, 0.08f, shade);
                break;
            case "cloudy":
                cloud(c, s, 0, 0, shade);
                break;
            case "fog":
                cloud(c, s, 0, -0.12f, shade);
                strokes(c, s, shade ? SHADE : MIST, new float[] {0.18f, 0.8f, 0.82f, 0.8f, 0.28f, 0.92f, 0.72f, 0.92f});
                break;
            case "drizzle":
                cloud(c, s, 0, -0.12f, shade);
                dots(c, s, shade ? SHADE : DROP, 0.035f, new float[] {0.35f, 0.82f, 0.55f, 0.88f, 0.72f, 0.8f});
                break;
            case "rain":
                cloud(c, s, 0, -0.12f, shade);
                strokes(c, s, shade ? SHADE : DROP,
                        new float[] {0.38f, 0.74f, 0.32f, 0.92f, 0.56f, 0.74f, 0.5f, 0.92f, 0.74f, 0.74f, 0.68f, 0.92f});
                break;
            case "snow":
                cloud(c, s, 0, -0.12f, shade);
                dots(c, s, shade ? SHADE : CLOUD, 0.045f,
                        new float[] {0.34f, 0.8f, 0.54f, 0.8f, 0.74f, 0.8f, 0.44f, 0.93f, 0.64f, 0.93f});
                break;
            case "thunder":
                cloud(c, s, 0, -0.12f, shade);
                path.reset();
                path.moveTo(0.56f * s, 0.62f * s);
                path.lineTo(0.42f * s, 0.82f * s);
                path.lineTo(0.52f * s, 0.82f * s);
                path.lineTo(0.46f * s, 0.98f * s);
                path.lineTo(0.64f * s, 0.74f * s);
                path.lineTo(0.54f * s, 0.74f * s);
                path.lineTo(0.6f * s, 0.62f * s);
                path.close();
                fill.setColor(shade ? SHADE : BOLT);
                c.drawPath(path, fill);
                break;
            default:
                break;
        }
    }

    private void sun(Canvas c, float s, float cx, float cy, float r, boolean shade) {
        int colour = shade ? SHADE : SUN;
        fill.setColor(colour);
        c.drawCircle(cx * s, cy * s, r * s, fill);
        line.setColor(colour);
        line.setStrokeWidth(0.055f * s);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float in = (r + 0.08f) * s, out = (r + 0.17f) * s;
            c.drawLine(cx * s + (float) Math.cos(a) * in, cy * s + (float) Math.sin(a) * in,
                    cx * s + (float) Math.cos(a) * out, cy * s + (float) Math.sin(a) * out, line);
        }
    }

    private void moon(Canvas c, float s, float cx, float cy, float r, boolean shade) {
        path.reset();
        path.addCircle(cx * s, cy * s, r * s, Path.Direction.CW);
        other.reset();
        other.addCircle((cx + r * 0.55f) * s, (cy - r * 0.35f) * s, r * 0.85f * s, Path.Direction.CW);
        path.op(other, Path.Op.DIFFERENCE);
        fill.setColor(shade ? SHADE : MOON);
        c.drawPath(path, fill);
    }

    /** A cloud filling the lower middle of the square, moved by (dx, dy) in units of the side. */
    private void cloud(Canvas c, float s, float dx, float dy, boolean shade) {
        path.reset();
        path.addCircle((0.36f + dx) * s, (0.58f + dy) * s, 0.16f * s, Path.Direction.CW);
        path.addCircle((0.56f + dx) * s, (0.48f + dy) * s, 0.22f * s, Path.Direction.CW);
        path.addCircle((0.74f + dx) * s, (0.62f + dy) * s, 0.13f * s, Path.Direction.CW);
        path.addRoundRect((0.2f + dx) * s, (0.56f + dy) * s, (0.87f + dx) * s, (0.75f + dy) * s,
                0.095f * s, 0.095f * s, Path.Direction.CW);
        path.setFillType(Path.FillType.WINDING);
        fill.setColor(shade ? SHADE : CLOUD);
        c.drawPath(path, fill);
    }

    /** Short lines, four coordinates (x1, y1, x2, y2) each, in units of the side. */
    private void strokes(Canvas c, float s, int colour, float[] xy) {
        line.setColor(colour);
        line.setStrokeWidth(0.06f * s);
        for (int i = 0; i + 3 < xy.length; i += 4) c.drawLine(xy[i] * s, xy[i + 1] * s, xy[i + 2] * s, xy[i + 3] * s, line);
    }

    private void dots(Canvas c, float s, int colour, float r, float[] xy) {
        fill.setColor(colour);
        for (int i = 0; i + 1 < xy.length; i += 2) c.drawCircle(xy[i] * s, xy[i + 1] * s, r * s, fill);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        line.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        fill.setColorFilter(filter);
        line.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
