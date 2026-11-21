package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.RadialGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;
    /** Light glass like the top bar's pills (config.xml dock_glass): a white tint that fades downwards. */
    private final boolean lightGlass;
    /** Padding all round the tiles equal to the gap between them (config.xml dock_even_padding). */
    private final boolean evenPadding;
    /** The band's top and bottom the glass gradient was made for; it is remade when they move. */
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;
    /** Draw the band and its edge (config.xml dock_band); false leaves the tiles floating on the wallpaper. */
    private final boolean bandVisible;
    /** single_row: the tiles after the dock share its row, so they take the same offsets to stay level with it. */
    private final boolean singleRow;
    /** tile_scale on a single row: the row is clipped to the grid's padding, so the band stops there too. */
    private boolean clampToPadding;
    /** dock_radius_match: the band's corners take the tiles' radius. */
    private boolean matchTileRadius;
    /** liquid_glass: the band drawn as Liquid Glass (drawLiquid). */
    private boolean liquid;
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix lightAt = new Matrix();
    private final RectF ring = new RectF();
    private float lensTop = Float.NaN, lensBottom = Float.NaN, lightRadius = -1;
    /** dock_band_all: in a single row, the band runs behind every app instead of the dock apps only. */
    private final boolean bandAll;

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     * @param opacity     the plain band's opacity in percent (dock_opacity), so a wallpaper can show through it.
     * @param lightGlass  a light frosted band like the pills, for a sky or wallpaper behind it.
     * @param evenPadding the band's padding is the gap between the tiles on all four sides (dock_even_padding).
     * @param bandVisible false skips the band and its edge, so the tiles float on the wallpaper (dock_band).
     * @param singleRow   every app sits in the dock's row (single_row); the band covers the dock apps only, unless bandAll.
     * @param bandAll     with singleRow, the band runs behind every app in the row (dock_band_all).
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity, boolean lightGlass,
                   boolean evenPadding, boolean bandVisible, boolean singleRow, boolean bandAll) {
        this.dockSize = dockSize;
        this.singleRow = singleRow;
        this.bandAll = singleRow && bandAll;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        this.lightGlass = lightGlass;
        this.evenPadding = evenPadding;
        this.bandVisible = bandVisible;
        band.setColor(translucent ? 0x8C121014 : (Math.round(opacity * 2.55f) << 24) | 0x141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(lightGlass ? 0x38FFFFFF : translucent ? 0x26FFFFFF : 0x1FFFFFFF);
    }

    void setClampToPadding(boolean clamp) {
        clampToPadding = clamp;
    }

    void setMatchTileRadius(boolean match) {
        matchTileRadius = match;
    }

    void setLiquid(boolean on) {
        liquid = on;
        tint.setColor(0x1F000000);
        lensRim.setStyle(Paint.Style.STROKE);
        lensRim.setStrokeWidth(1.5f * density);
        lensShade.setStyle(Paint.Style.STROKE);
        lensShade.setStrokeWidth(3f * density);
        shadow.setStyle(Paint.Style.STROKE);
    }

    /**
     * liquid_glass: the band as Apple's Liquid Glass, without blur. A faint dark tint under the frost keeps the tiles
     * legible on any wallpaper; the edge is a lens (a rim bright where the light from above meets it, fading down the
     * sides, catching it again at the bottom), with an inner shade that gives the pane its thickness; a soft light
     * gathers on the band above the focused tile and follows focus along it; and a faint shadow lifts the whole
     * band off the wallpaper. Corners concentric with the tiles' (the caller passes the radius). Nothing is made
     * per frame: the gradients are rebuilt only when the band's height changes, and the light is moved with a matrix.
     */
    private void drawLiquid(Canvas c, RecyclerView parent, float r, float cardWidth) {
        if (rect.top != lensTop || rect.bottom != lensBottom) {
            lensTop = rect.top;
            lensBottom = rect.bottom;
            band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x2EFFFFFF, 0x0AFFFFFF,
                    Shader.TileMode.CLAMP));
            lensRim.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                    new int[]{0xA6FFFFFF, 0x26FFFFFF, 0x12FFFFFF, 0x47FFFFFF}, new float[]{0f, 0.3f, 0.72f, 1f},
                    Shader.TileMode.CLAMP));
            lensShade.setShader(new LinearGradient(0, rect.bottom - 10 * density, 0, rect.bottom,
                    0x00000000, 0x29000000, Shader.TileMode.CLAMP));
        }
        // The shadow first: one soft ring just outside the band, a little lower, lifting it off the wallpaper.
        float grow = 4 * density;
        shadow.setStrokeWidth(grow);
        shadow.setColor(0x14000000);
        ring.set(rect.left, rect.top + density * 2, rect.right, rect.bottom + density * 2);
        ring.inset(-grow / 2, -grow / 2);
        c.drawRoundRect(ring, r + grow / 2, r + grow / 2, shadow);
        c.drawRoundRect(rect, r, r, tint);
        c.drawRoundRect(rect, r, r, band);
        // The light over the focused tile, gathered along the band's top edge.
        View focused = parent.getFocusedChild();
        if (focused != null) {
            float want = Math.max(cardWidth, 40 * density) * 1.3f;
            if (want != lightRadius) {
                lightRadius = want;
                light.setShader(new RadialGradient(0, 0, want, new int[]{0x38FFFFFF, 0x0FFFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            }
            float fx = focused.getLeft() + focused.getWidth() / 2f + focused.getTranslationX();
            lightAt.setTranslate(fx, rect.top);
            light.getShader().setLocalMatrix(lightAt);
            // Only the light's own patch is filled, not the whole band: cheaper on the TV's GPU.
            c.save();
            c.clipRect(fx - want, rect.top, fx + want, Math.min(rect.bottom, rect.top + want));
            c.drawRoundRect(rect, r, r, light);
            c.restore();
        }
        float in = lensShade.getStrokeWidth() / 2;
        ring.set(rect.left + in, rect.top + in, rect.right - in, rect.bottom - in);
        c.drawRoundRect(ring, r - in, r - in, lensShade);
        in = lensRim.getStrokeWidth() / 2;
        ring.set(rect.left + in, rect.top + in, rect.right - in, rect.bottom - in);
        c.drawRoundRect(ring, r - in, r - in, lensRim);
    }

    /** Concentric corners: the band's radius is the tiles' radius plus the band's padding around them. */
    static float radiusPx(float tileRadiusPx, float paddingPx) {
        return tileRadiusPx + paddingPx;
    }

    /**
     * The band's padding on every side for dock_even_padding: the gap between two dock tiles (a cell minus its tile, as
     * AppAdapter.tileWidthPx lays them out), capped by the room the grid leaves at its edges.
     */
    static float evenPaddingPx(int innerWidthPx, int columns, int tileWidthPx, float roomPx) {
        float gap = columns > 0 ? innerWidthPx / (float) columns - tileWidthPx : 0f;
        return Math.max(0f, Math.min(gap, roomPx));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && (singleRow || pos < dockSize)) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (!bandVisible) return;
        boolean any = false;
        int cardWidth = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || (!bandAll && pos >= dockSize)) continue;
            // tile_scale: only the tiles inside the centred row count; the rest are clipped away (MainActivity).
            if (clampToPadding) {
                float mid = child.getLeft() + child.getWidth() / 2f;
                if (mid < parent.getPaddingLeft() || mid > parent.getWidth() - parent.getPaddingRight()) continue;
            }
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            cardWidth = card.getWidth();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        if (clampToPadding) {
            // The same 14dp all round the centred row, with corners concentric with the tiles'.
            rect.inset(-pad, -pad);
            rect.bottom += namesRoom;
            float r = matchTileRadius ? AppAdapter.cornerRadius(cardWidth, density)
                    : radiusPx(AppAdapter.cornerRadius(cardWidth, density), pad);
            if (liquid) {
                drawLiquid(c, parent, r, cardWidth);
                return;
            }
            if (lightGlass && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
                band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x24FFFFFF, 0x0DFFFFFF,
                        Shader.TileMode.CLAMP));
                shaderTop = rect.top;
                shaderBottom = rect.bottom;
            }
            c.drawRoundRect(rect, r, r, band);
            c.drawRoundRect(rect, r, r, edge);
            return;
        }
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        // 22dp at the sides (the grid's 10dp padding makes room): a focused first or last tile grows about 7dp
        // outwards and still keeps about the band's top margin from its rounded end.
        float sidePad = 22 * density;
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        float radius = AppAdapter.cornerRadius(cardWidth, density);
        if (evenPadding) {
            // The same padding on all four sides, so the band's corners are concentric with the tiles' everywhere.
            int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            // A single row's tiles can run off the screen, so only its top limits the padding; its ends are clamped below.
            float room = (singleRow ? rect.top : Math.min(rect.top, Math.min(rect.left, parent.getWidth() - rect.right))) - density;
            float even = evenPaddingPx(inner, dockSize, cardWidth, room);
            rect.inset(-even, -even);
            radius = radiusPx(radius, even);
        } else {
            // dock_even_padding off: the band as before, with its fixed 26 dp corners.
            rect.inset(singleRow ? -sidePad : -side, -pad);
            radius = 26 * density;
        }
        if (singleRow) {
            // Tiles past the screen's edge: the band stops at the grid's edge with its rounded end instead of being
            // sliced square there.
            rect.left = Math.max(rect.left, density);
            rect.right = Math.min(rect.right, parent.getWidth() - density);
        }
        rect.bottom += namesRoom;
        if (matchTileRadius) radius = AppAdapter.cornerRadius(cardWidth, density);
        if (liquid) {
            drawLiquid(c, parent, radius, cardWidth);
            return;
        }
        if (lightGlass && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
            // The pills' glass (pill_bg: white, 18% fading to 8%), a little fainter over this much larger area.
            band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x24FFFFFF, 0x0DFFFFFF,
                    Shader.TileMode.CLAMP));
            shaderTop = rect.top;
            shaderBottom = rect.bottom;
        }
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
