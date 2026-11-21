package io.github.philipvella.vellaradio;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Finds more stations in Radio Browser (Online), from the station list's "Find more stations". Type a name with the
 * TV's keyboard; with nothing typed it lists the most played. OK on a result adds it to My stations, and OK again
 * takes it off. Only MP3 streams on https are shown: the ones the TV's player can play.
 */
public final class SearchActivity extends Activity {
    /** Typing stops this long before the search runs by itself. */
    private static final long PAUSE_MS = 900;

    private Look look;
    private EditText box;
    private TextView where, status;
    private LinearLayout results;
    private boolean ukOnly = true;
    /** Each search gets a number; a slow answer for an earlier one is dropped. */
    private int generation;
    private String lastKey;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable typed = this::search;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        look = new Look(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(look.background());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(dp(64), dp(48), dp(64), dp(40));
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // The side: the title, where to look, what this is, and the keys.
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        page.addView(side, new LinearLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.MATCH_PARENT));
        side.addView(look.text("Find stations", 40, Look.INK, look.light));
        side.addView(look.caps("Radio Browser · MP3 streams", Look.DIM), look.margins(0, 4, 0, 0));
        side.addView(look.caps("Where", Look.DIM), look.margins(0, 40, 0, 6));
        LinearLayout toggle = look.row("", "OK to change", "");
        Look.tag(toggle).setVisibility(View.GONE);
        Look.mark(toggle).setVisibility(View.GONE);
        where = (TextView) ((LinearLayout) toggle.getChildAt(1)).getChildAt(0);
        toggle.setOnClickListener(v -> {
            ukOnly = !ukOnly;
            showWhere();
            search();
        });
        LinearLayout.LayoutParams tlp = (LinearLayout.LayoutParams) toggle.getLayoutParams();
        tlp.setMarginStart(-dp(16));
        side.addView(toggle);
        TextView about = look.text("A free community directory of the stations' own public streams. Only MP3 streams "
                + "are shown: the ones this TV can play.", 13, Look.FAINT, look.regular);
        about.setSingleLine(false);
        about.setMaxLines(5);
        about.setLineSpacing(0, 1.2f);
        side.addView(about, look.margins(0, 24, 0, 0));
        View fill = new View(this);
        side.addView(fill, new LinearLayout.LayoutParams(1, 0, 1));
        side.addView(look.caps("OK  add or remove", Look.DIM));
        side.addView(look.caps("Back  your stations", Look.FAINT), look.margins(0, 6, 0, 0));

        // The search box, what is happening, and the results.
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        rlp.setMarginStart(dp(40));
        page.addView(right, rlp);

        box = new EditText(this);
        box.setSingleLine(true);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.setHint("Search by name, e.g. jazz");
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        box.setTextColor(Look.INK);
        box.setHintTextColor(Look.FAINT);
        box.setTypeface(look.regular);
        box.setPadding(dp(20), dp(14), dp(20), dp(14));
        GradientDrawable idle = new GradientDrawable();
        idle.setColor(0x14FFFFFF);
        idle.setStroke(Math.round(1.5f * look.density), 0x33FFFFFF);
        idle.setCornerRadius(14 * look.density);
        StateListDrawable boxBg = new StateListDrawable();
        boxBg.addState(new int[] {android.R.attr.state_focused}, look.ring());
        boxBg.addState(new int[] {}, idle);
        box.setBackground(boxBg);
        box.setOnEditorActionListener((v, action, event) -> {
            boolean go = action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (!go) return false;
            hideKeyboard();
            search();
            return true;
        });
        // An EditText keeps Down for itself on this TV (BRAVIA, Android 9): send it to the first result.
        box.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_DOWN || event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (results.getChildCount() == 0) return false;
            hideKeyboard();
            results.getChildAt(0).requestFocus();
            return true;
        });
        box.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                main.removeCallbacks(typed);
                main.postDelayed(typed, PAUSE_MS);
            }
        });
        right.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        status = look.caps("", Look.DIM);
        right.addView(status, look.margins(0, 20, 0, 8));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(24));
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        right.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        showWhere();
        box.requestFocus();
        search();
    }

    @Override
    protected void onDestroy() {
        generation++;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void showWhere() {
        where.setText(ukOnly ? "In the UK" : "Anywhere");
    }

    /** Searches for what is in the box (the most played when it is empty), off the main thread. */
    private void search() {
        main.removeCallbacks(typed);
        String query = box.getText().toString().trim();
        String key = ukOnly + "|" + query;
        if (key.equals(lastKey)) return;
        lastKey = key;
        int run = ++generation;
        results.removeAllViews();
        status.setText(Look.upper("Searching…"));
        boolean uk = ukOnly;
        new Thread(() -> {
            List<Online.Found> found;
            try {
                found = Online.search(query, uk, Arrays.asList(Stations.ALL));
            } catch (IOException offline) {
                main.post(() -> {
                    if (run != generation) return;
                    lastKey = null;
                    status.setText(Look.upper("Couldn't reach the directory"));
                });
                return;
            }
            main.post(() -> {
                if (run == generation) show(query, found);
            });
        }, "radio-search").start();
    }

    private void show(String query, List<Online.Found> found) {
        results.removeAllViews();
        if (found.isEmpty()) {
            status.setText(Look.upper("No stations found"));
            return;
        }
        String scope = ukOnly ? "in the UK" : "anywhere";
        status.setText(Look.upper(query.isEmpty() ? "Most played " + scope : found.size() + " found " + scope));
        for (Online.Found f : found) {
            LinearLayout row = look.row(f.name, f.detail(), "Added");
            Look.mark(row).setVisibility(View.GONE);
            TextView tag = Look.tag(row);
            tag.setVisibility(MyStations.has(this, f.id) ? View.VISIBLE : View.GONE);
            row.setOnClickListener(v -> {
                if (MyStations.has(this, f.id)) {
                    MyStations.remove(this, f.id);
                    tag.setVisibility(View.GONE);
                } else {
                    MyStations.add(this, f.station());
                    tag.setVisibility(View.VISIBLE);
                }
            });
            results.addView(row);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
    }

    private int dp(float v) {
        return look.dp(v);
    }
}
