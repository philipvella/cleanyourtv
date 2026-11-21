package io.github.philipvella.vellaradio;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * The radio's screens' style, in the launcher's calendar's: a dark gradient, a light title, small spaced capitals and
 * rows with a soft focus ring.
 */
final class Look {
    static final int INK = 0xFFFFFFFF, SOFT = 0xCCFFFFFF, DIM = 0x99FFFFFF, FAINT = 0x80FFFFFF;

    private final Context ctx;
    final float density;
    final Typeface light, regular, medium;

    Look(Context ctx) {
        this.ctx = ctx;
        density = ctx.getResources().getDisplayMetrics().density;
        light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    Drawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF15181D, 0xFF07080A});
    }

    /** The focus ring a row wears while it has focus. */
    GradientDrawable ring() {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0x26FFFFFF);
        ring.setStroke(Math.round(1.5f * density), 0x80FFFFFF);
        ring.setCornerRadius(14 * density);
        return ring;
    }

    TextView text(String s, float sp, int colour, Typeface face) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Small spaced capitals, as the launcher's date and calendar headings are set. */
    TextView caps(String s, int colour) {
        TextView t = text(upper(s), 12, colour, medium);
        t.setLetterSpacing(0.14f);
        return t;
    }

    /** A small caps line for under a row's name. */
    TextView small(String s, int colour) {
        TextView t = caps(s, colour);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        return t;
    }

    /**
     * A focusable row: a dot (hidden; shown while the station plays), the name over a small caps line, and a tag on
     * the right. Its children: 0 the dot, 1 the name and small line, 2 the tag.
     */
    LinearLayout row(String name, String detail, String tagText) {
        LinearLayout v = new LinearLayout(ctx);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(16), dp(10), dp(16), dp(10));
        v.setFocusable(true);
        v.setClickable(true);

        View mark = new View(ctx);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(INK);
        mark.setBackground(dot);
        mark.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(8), dp(8));
        mlp.setMarginEnd(dp(14));
        v.addView(mark, mlp);

        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(text(name, 18, INK, regular));
        words.addView(small(detail, FAINT), margins(0, 2, 0, 0));
        v.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView tag = small(tagText, DIM);
        LinearLayout.LayoutParams tlp = margins(0, 0, 0, 0);
        tlp.setMarginStart(dp(12));
        v.addView(tag, tlp);

        GradientDrawable ring = ring();
        v.setOnFocusChangeListener((view, focused) -> view.setBackground(focused ? ring : null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        v.setLayoutParams(lp);
        return v;
    }

    static View mark(LinearLayout row) {
        return row.getChildAt(0);
    }

    static TextView tag(LinearLayout row) {
        return (TextView) row.getChildAt(2);
    }

    static String upper(String s) {
        return s.toUpperCase(Locale.getDefault());
    }

    LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    int dp(float v) {
        return Math.round(v * density);
    }
}
