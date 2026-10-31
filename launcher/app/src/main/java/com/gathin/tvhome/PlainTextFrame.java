package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.FrameLayout;

/**
 * Draws its children into a bitmap of its own, with every text drawn as its glyph outlines (a path), then puts that
 * on screen. With the TV's "High contrast text" setting on, Android strokes every text it draws in solid black, even
 * on a bitmap canvas (BRAVIA, Android 9); it never strokes a path, so the welcome texts keep their soft blurred
 * shadow instead of a hard outline. Used only for the few welcome blocks (Welcome); a frame costs one ARGB bitmap of
 * its size, redrawn when a child changes.
 */
final class PlainTextFrame extends FrameLayout {
    private Bitmap bitmap;
    private PathCanvas offscreen;

    PlainTextFrame(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
            if (bitmap != null) bitmap.recycle();
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            offscreen = new PathCanvas(bitmap, getResources().getDisplayMetrics().density);
        }
        bitmap.eraseColor(0);
        super.dispatchDraw(offscreen);
        canvas.drawBitmap(bitmap, 0, 0, null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
            offscreen = null;
        }
    }

    /** The soft shadow under every text, in dp: blur radius and drop; and its colour. */
    static final float SHADOW_BLUR_DP = 6, SHADOW_DROP_DP = 1.5f;
    /** The shadow's copy of the letters is thickened by this much before the blur, so thin type casts enough of it. */
    static final float SHADOW_SPREAD_DP = 3;
    static final int SHADOW_COLOUR = 0x99000000;

    /**
     * A bitmap canvas that draws text as its glyph outlines: first a thickened, blurred, translucent copy a little
     * lower (the shadow, a soft halo rather than a rim, drawn here so it never depends on the paint's own shadow),
     * then the letters in the paint's colour.
     */
    private static final class PathCanvas extends Canvas {
        private final Path path = new Path();
        private final Paint fill = new Paint();
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float drop;

        PathCanvas(Bitmap b, float density) {
            super(b);
            drop = SHADOW_DROP_DP * density;
            shade.setStyle(Paint.Style.FILL_AND_STROKE);
            shade.setStrokeWidth(SHADOW_SPREAD_DP * density);
            shade.setStrokeJoin(Paint.Join.ROUND);
            shade.setMaskFilter(new BlurMaskFilter(SHADOW_BLUR_DP * density, BlurMaskFilter.Blur.NORMAL));
        }

        private void glyphs(String text, float x, float y, Paint paint) {
            path.reset();
            paint.getTextPath(text, 0, text.length(), x, y, path);
            // The shadow follows the text's own opacity, so a fading headline takes its shadow with it.
            int alpha = Math.round(((SHADOW_COLOUR >>> 24) & 0xFF) * paint.getAlpha() / 255f);
            shade.setColor(SHADOW_COLOUR & 0x00FFFFFF | (alpha << 24));
            save();
            translate(0, drop);
            drawPath(path, shade);
            restore();
            fill.set(paint);
            fill.clearShadowLayer();
            fill.setStyle(Paint.Style.FILL);
            drawPath(path, fill);
        }

        @Override
        public void drawText(char[] text, int index, int count, float x, float y, Paint paint) {
            glyphs(new String(text, index, count), x, y, paint);
        }

        @Override
        public void drawText(String text, float x, float y, Paint paint) {
            glyphs(text, x, y, paint);
        }

        @Override
        public void drawText(String text, int start, int end, float x, float y, Paint paint) {
            glyphs(text.substring(start, end), x, y, paint);
        }

        @Override
        public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) {
            glyphs(text.subSequence(start, end).toString(), x, y, paint);
        }

        @Override
        public void drawTextRun(char[] text, int index, int count, int contextIndex, int contextCount, float x,
                                float y, boolean isRtl, Paint paint) {
            glyphs(new String(text, index, count), x, y, paint);
        }

        @Override
        public void drawTextRun(CharSequence text, int start, int end, int contextStart, int contextEnd, float x,
                                float y, boolean isRtl, Paint paint) {
            glyphs(text.subSequence(start, end).toString(), x, y, paint);
        }
    }
}
