package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
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
 */
final class LiquidGlass extends Drawable {
    private final float density;
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private int builtFor = -1;

    LiquidGlass(float density) {
        this.density = density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        shade.setStyle(Paint.Style.STROKE);
        shade.setStrokeWidth(3f * density);
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
        }
        float r = AppAdapter.cornerRadius(w, density);
        box.set(b);
        canvas.drawRoundRect(box, r, r, sheen);
        float in = shade.getStrokeWidth() / 2;
        box.set(b.left + in, b.top + in, b.right - in, b.bottom - in);
        canvas.drawRoundRect(box, r - in, r - in, shade);
        in = rim.getStrokeWidth() / 2;
        box.set(b.left + in, b.top + in, b.right - in, b.bottom - in);
        canvas.drawRoundRect(box, r - in, r - in, rim);
    }

    @Override
    public void setAlpha(int alpha) {
        sheen.setAlpha(alpha);
        rim.setAlpha(alpha);
        shade.setAlpha(alpha);
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
