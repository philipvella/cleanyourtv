package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * glass_tiles: a tile's glass, drawn over its banner as the card's foreground (the card's outline clips it to the
 * rounded corners). No blur, which this launcher never uses: a rim that catches the light at the top and again,
 * fainter, at the bottom, as a lens bends it; a soft sheen over the upper part; and a faint inner shade along the
 * bottom edge, which gives the pane its thickness. The tile's own translucency (AppAdapter) lets the wallpaper
 * through.
 *
 * <p>liquid_glass adds what Apple's Liquid Glass asks of a control, still without blur: the glass answers focus
 * (a tile at rest is a quiet pane, so the band it sits on is the one glass surface; the focused tile lights up with a
 * brighter lensing rim and a specular glint where the light, from above and to the left, strikes it), and a second,
 * fainter rim just inside the first, where a thick lens bends the light again.
 */
final class LiquidGlass extends Drawable {
    private final float density;
    private final boolean liquid;
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int builtFor = -1;
    private boolean focused;
    private int alpha = 255;

    LiquidGlass(float density) {
        this(density, false);
    }

    LiquidGlass(float density, boolean liquid) {
        this.density = density;
        this.liquid = liquid;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        shade.setStyle(Paint.Style.STROKE);
        shade.setStrokeWidth(3f * density);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(density);
        inner.setColor(0xFFFFFFFF);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        int w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        if (builtFor != (w << 16 | h)) {
            builtFor = w << 16 | h;
            sheen.setShader(new LinearGradient(0, b.top, 0, b.top + h * 0.55f,
                    0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            rim.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                    new int[]{0xB3FFFFFF, 0x26FFFFFF, 0x14FFFFFF, 0x4DFFFFFF}, new float[]{0f, 0.35f, 0.75f, 1f},
                    Shader.TileMode.CLAMP));
            shade.setShader(new LinearGradient(0, b.bottom - 8 * density, 0, b.bottom,
                    0x00000000, 0x33000000, Shader.TileMode.CLAMP));
            // The specular glint: light from the top left, a soft spot that fades out across the pane.
            glint.setShader(new RadialGradient(b.left + w * 0.2f, b.top, Math.max(w, h) * 0.6f,
                    new int[]{0x59FFFFFF, 0x1AFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        }
        float r = AppAdapter.cornerRadius(w, density);
        // At rest a liquid tile is a quieter pane, so the glass under it (the band) stays the one surface.
        int lit = !liquid ? 255 : focused ? 255 : 150;
        sheen.setAlpha(alpha * lit / 255);
        rim.setAlpha(alpha * lit / 255);
        box.set(b);
        canvas.drawRoundRect(box, r, r, sheen);
        if (liquid && focused) {
            glint.setAlpha(alpha);
            canvas.drawRoundRect(box, r, r, glint);
        }
        float in = shade.getStrokeWidth() / 2;
        box.set(b.left + in, b.top + in, b.right - in, b.bottom - in);
        canvas.drawRoundRect(box, r - in, r - in, shade);
        in = rim.getStrokeWidth() / 2;
        box.set(b.left + in, b.top + in, b.right - in, b.bottom - in);
        canvas.drawRoundRect(box, r - in, r - in, rim);
        if (liquid) {
            // The lens's second edge, a hair inside the rim: brighter on the focused pane.
            float i2 = rim.getStrokeWidth() + inner.getStrokeWidth() / 2 + density;
            inner.setAlpha(alpha * (focused ? 0x30 : 0x14) / 255);
            box.set(b.left + i2, b.top + i2, b.right - i2, b.bottom - i2);
            canvas.drawRoundRect(box, Math.max(0, r - i2), Math.max(0, r - i2), inner);
        }
    }

    @Override
    public boolean isStateful() {
        return liquid;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        if (!liquid) return false;
        boolean f = false;
        for (int s : state) if (s == android.R.attr.state_focused) f = true;
        if (f == focused) return false;
        focused = f;
        invalidateSelf();
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        sheen.setAlpha(alpha);
        rim.setAlpha(alpha);
        shade.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        rim.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
