package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * clock_calendar's own month calendar, for a TV with no calendar app: the month with today ringed, the household's
 * dates (seasonal_dates) and the holidays marked and listed beside it. Offline, no permissions and no events: it
 * reads nothing but the clock. Left and Right change the month, OK comes back to today's, Back returns to Home.
 */
public final class CalendarActivity extends Activity {
    static final String EXTRA_DATES = "com.gathin.tvhome.extra.DATES";
    /** public_holidays: the region Holidays.region chose, or "" for none. */
    static final String EXTRA_REGION = "com.gathin.tvhome.extra.REGION";
    private String region = "";
    private String[] sceneNames;
    private final java.util.Map<Integer, java.util.Map<LocalDate, String>> bankByYear = new java.util.HashMap<>();

    private final Locale here = Locale.getDefault();
    private final DayOfWeek firstDay = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
    private Map<MonthDay, Season.Scene> own;
    private YearMonth shown = YearMonth.now();
    private float density;
    private Typeface light, regular;
    private TextView title;
    private LinearLayout grid, marks;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        String[] dates = getIntent().getStringArrayExtra(EXTRA_DATES);
        own = Season.parseDates(dates == null ? new ArrayList<>() : Arrays.asList(dates));
        String r = getIntent().getStringExtra(EXTRA_REGION);
        region = r == null ? "" : r;
        sceneNames = getResources().getStringArray(R.array.calendar_day_names);
        try {
            light = getResources().getFont(R.font.montserrat_light);
        } catch (RuntimeException e) {
            light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        }
        try {
            regular = UiFont.get(this, R.string.font_regular);
        } catch (RuntimeException e) {
            regular = Typeface.DEFAULT;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF15181D, 0xFF07080A}));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(72), dp(44), dp(72), dp(32));
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.BOTTOM);
        title = text(40, 0xFFFFFFFF, light, 0f);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView hint = caps(getString(R.string.calendar_hint), 0x99FFFFFF);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(8);
        header.addView(hint, hlp);
        page.addView(header);

        LinearLayout body = new LinearLayout(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        blp.topMargin = dp(28);
        page.addView(body, blp);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        body.addView(grid, new LinearLayout.LayoutParams(dp(CELL_W * 7), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        slp.setMarginStart(dp(56));
        body.addView(side, slp);
        side.addView(caps(getString(R.string.calendar_today), 0x99FFFFFF));
        TextView today = text(20, 0xFFFFFFFF, regular, 0f);
        today.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", here)));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(8);
        tlp.bottomMargin = dp(32);
        side.addView(today, tlp);
        side.addView(caps(getString(R.string.calendar_this_month), 0x99FFFFFF));
        marks = new LinearLayout(this);
        marks.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(4);
        side.addView(marks, mlp);

        setContentView(root);
        show();
    }

    private static final int CELL_W = 68, CELL_H = 50;

    /** Draws the month in {@link #shown}: the title, the weekdays, the days and the list of marked days. */
    private void show() {
        title.setText(shown.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, here) + " " + shown.getYear());
        grid.removeAllViews();
        LinearLayout names = new LinearLayout(this);
        for (int c = 0; c < 7; c++) {
            TextView n = caps(firstDay.plus(c).getDisplayName(TextStyle.SHORT, here), 0x99FFFFFF);
            n.setGravity(Gravity.CENTER);
            names.addView(n, new LinearLayout.LayoutParams(dp(CELL_W), dp(28)));
        }
        grid.addView(names);
        java.util.Map<LocalDate, String> bank = bankByYear.get(shown.getYear());
        if (bank == null) {
            bank = Holidays.of(region, shown.getYear());
            bankByYear.put(shown.getYear(), bank);
        }
        Map<Integer, java.util.List<CalendarMonth.Mark>> marked = CalendarMonth.marks(shown, own, sceneNames, bank);
        LocalDate now = LocalDate.now();
        int first = CalendarMonth.firstColumn(shown, firstDay), length = shown.lengthOfMonth();
        int rows = CalendarMonth.rows(shown, firstDay);
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            for (int c = 0; c < 7; c++) {
                int day = r * 7 + c - first + 1;
                java.util.List<CalendarMonth.Mark> on = marked.get(day);
                boolean off = false, other = false;
                if (on != null) for (CalendarMonth.Mark m : on) {
                    if (m.bank) off = true;
                    else other = true;
                }
                row.addView(day >= 1 && day <= length
                                ? cell(day, shown.atDay(day).equals(now), off, other)
                                : new View(this),
                        new LinearLayout.LayoutParams(dp(CELL_W), dp(CELL_H)));
            }
            grid.addView(row);
        }
        marks.removeAllViews();
        if (marked.isEmpty()) {
            TextView none = text(16, 0x80FFFFFF, regular, 0f);
            none.setText(R.string.calendar_nothing);
            marks.addView(none, line());
        }
        for (Map.Entry<Integer, java.util.List<CalendarMonth.Mark>> m : marked.entrySet()) {
            boolean firstOfDay = true;
            for (CalendarMonth.Mark mark : m.getValue()) {
                // The day, then the name with "BANK HOLIDAY" under it, so a long name keeps the full width.
                LinearLayout one = new LinearLayout(this);
                TextView d = text(16, 0xFFFFFFFF, regular, 0f);
                d.setText(firstOfDay ? String.valueOf(m.getKey()) : "");
                one.addView(d, new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout words = new LinearLayout(this);
                words.setOrientation(LinearLayout.VERTICAL);
                TextView what = text(16, 0xCCFFFFFF, regular, 0f);
                what.setSingleLine(false);
                what.setMaxLines(2);
                what.setEllipsize(android.text.TextUtils.TruncateAt.END);
                what.setText(mark.name);
                words.addView(what);
                if (mark.bank) {
                    TextView tag = caps(getString(R.string.calendar_bank_holiday), 0x80FFFFFF);
                    tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    tlp.topMargin = dp(5);
                    words.addView(tag, tlp);
                }
                one.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                marks.addView(one, line());
                firstOfDay = false;
            }
        }
    }

    /**
     * One day: its number on a white disc when it is today, in a thin white ring on a bank holiday, and with a small
     * dot under it for any other date worth a mark.
     */
    private View cell(int day, boolean today, boolean bankHoliday, boolean marked) {
        FrameLayout cell = new FrameLayout(this);
        TextView n = text(19, today ? 0xFF0B0C0E : 0xE6FFFFFF, regular, 0f);
        n.setText(String.valueOf(day));
        n.setGravity(Gravity.CENTER);
        if (today) {
            GradientDrawable disc = new GradientDrawable();
            disc.setShape(GradientDrawable.OVAL);
            disc.setColor(0xFFFFFFFF);
            n.setBackground(disc);
        } else if (bankHoliday) {
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(Math.max(1, dp(1.5f)), 0xB3FFFFFF);
            n.setBackground(ring);
        }
        cell.addView(n, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        if (marked) {
            View dot = new View(this);
            GradientDrawable round = new GradientDrawable();
            round.setShape(GradientDrawable.OVAL);
            round.setColor(today ? 0xFF0B0C0E : 0xCCFFFFFF);
            dot.setBackground(round);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(5), dp(5),
                    Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            dlp.bottomMargin = dp(today ? 9 : 4);
            cell.addView(dot, dlp);
        }
        return cell;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                shown = shown.minusMonths(1);
                show();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                shown = shown.plusMonths(1);
                show();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                shown = YearMonth.now();
                show();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private LinearLayout.LayoutParams line() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        return lp;
    }

    /** Small spaced capitals, as the date on Home. */
    private TextView caps(String s, int colour) {
        TextView t = text(12, colour, regular, 0.14f);
        t.setText(s.toUpperCase(here));
        return t;
    }

    private TextView text(float sp, int colour, Typeface face, float spacing) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colour);
        t.setTypeface(face);
        t.setLetterSpacing(spacing);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
