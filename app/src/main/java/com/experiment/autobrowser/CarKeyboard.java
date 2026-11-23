package com.experiment.autobrowser;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * On-screen keyboard drawn by the app itself on the car display. Android Auto's own
 * keyboard is often disabled by the car, but taps on our surface still arrive, so
 * this keyboard works wherever the browser does.
 */
class CarKeyboard extends LinearLayout {

    static final int MODE_ADDRESS = 0; // search the web or open a URL
    static final int MODE_PAGE = 1;    // type into a text field on the page

    interface Listener {
        void onSubmit(String text, int mode, boolean submit);
        void onClose();
    }

    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"z", "x", "c", "v", "b", "n", "m"},
    };
    private static final String[][] SYMBOLS = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"-", "/", ":", ";", "(", ")", "&", "@", "\"", "_"},
            {".", ",", "?", "!", "'", "#", "%", "+", "="},
    };

    private final Listener listener;
    private final TextView preview;
    private final LinearLayout rows;
    private final StringBuilder buf = new StringBuilder();
    private boolean shift, symbols;
    private int mode = MODE_ADDRESS;
    private String hint = "";

    CarKeyboard(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        setOrientation(VERTICAL);
        GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xF2181A1C);
        float r = dp(16);
        panel.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0}); // rounded top only
        setBackground(panel);
        setPadding(dp(8), dp(8), dp(8), dp(6));
        setClickable(true); // swallow taps on gaps so they don't reach the page

        LinearLayout header = new LinearLayout(c);
        header.setGravity(Gravity.CENTER_VERTICAL);
        preview = new TextView(c);
        preview.setTextColor(Color.WHITE);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        preview.setSingleLine(true);
        preview.setEllipsize(TextUtils.TruncateAt.START);
        preview.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2F3134);
        bg.setCornerRadius(dp(20));
        preview.setBackground(bg);
        header.addView(preview, new LayoutParams(0, dp(40), 1f));
        header.addView(key("✕", 0, 0xFF44474A, this::close), new LayoutParams(dp(56), dp(40)));
        addView(header);

        rows = new LinearLayout(c);
        rows.setOrientation(VERTICAL);
        addView(rows);
        build();
    }

    void open(int mode, String initial, String hint) {
        if (isOpen() && this.mode == mode && mode == MODE_PAGE) return; // keep what's typed
        this.mode = mode;
        this.hint = hint == null ? "" : hint;
        buf.setLength(0);
        if (initial != null) buf.append(initial);
        shift = false;
        symbols = false;
        build();
        setVisibility(VISIBLE);
    }

    boolean isOpen() { return getVisibility() == VISIBLE; }

    boolean isPageMode() { return mode == MODE_PAGE; }

    /** True if (x, y) in the parent's coordinates falls on the keyboard. */
    boolean contains(float x, float y) {
        return isOpen() && x >= getLeft() && x < getRight() && y >= getTop() && y < getBottom();
    }

    private void close() {
        setVisibility(GONE);
        listener.onClose();
    }

    private void build() {
        rows.removeAllViews();
        String[][] layout = symbols ? SYMBOLS : LETTERS;
        for (int r = 0; r < layout.length; r++) {
            LinearLayout row = newRow();
            if (r == 2 && !symbols) row.addView(key(shift ? "⇧●" : "⇧", 0, 0xFF44474A, () -> {
                shift = !shift; build();
            }), weight(1.5f));
            for (String k : layout[r]) {
                String label = shift && !symbols ? k.toUpperCase() : k;
                row.addView(key(label, 0, 0, () -> type(label)), weight(1f));
            }
            if (r == 2) row.addView(key("⌫", 0, 0xFF44474A, this::backspace), weight(1.5f));
            rows.addView(row);
        }
        LinearLayout last = newRow();
        last.addView(key(symbols ? "ABC" : "123", 0, 0xFF44474A, () -> {
            symbols = !symbols; build();
        }), weight(1.4f));
        if (mode == MODE_ADDRESS) {
            last.addView(key("/", 0, 0, () -> type("/")), weight(0.9f));
            last.addView(key("space", 0, 0, () -> type(" ")), weight(3.5f));
            last.addView(key(".", 0, 0, () -> type(".")), weight(0.9f));
            last.addView(key(".com", 0, 0, () -> type(".com")), weight(1.3f));
            last.addView(key("Go", 0, 0xFF1A73E8, () -> submit(true)), weight(1.8f));
        } else {
            last.addView(key("@", 0, 0, () -> type("@")), weight(0.9f));
            last.addView(key("space", 0, 0, () -> type(" ")), weight(3.5f));
            last.addView(key(".", 0, 0, () -> type(".")), weight(0.9f));
            last.addView(key("Done", 0, 0xFF44474A, () -> submit(false)), weight(1.5f));
            last.addView(key("Enter", 0, 0xFF1A73E8, () -> submit(true)), weight(1.6f));
        }
        rows.addView(last);
        updatePreview();
    }

    private void type(String s) {
        buf.append(s);
        if (shift) { shift = false; build(); } else updatePreview();
    }

    private void backspace() {
        if (buf.length() > 0) buf.setLength(buf.length() - 1);
        updatePreview();
    }

    private void submit(boolean enter) {
        String text = buf.toString();
        setVisibility(GONE);
        listener.onSubmit(text, mode, enter);
    }

    private void updatePreview() {
        if (buf.length() == 0) {
            preview.setText(hint);
            preview.setTextColor(0xFF9AA0A6);
        } else {
            preview.setText(buf + "▏");
            preview.setTextColor(Color.WHITE);
        }
    }

    // ------------------------------------------------------------------ view helpers

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setPadding(0, dp(3), 0, 0);
        return row;
    }

    private LayoutParams weight(float w) {
        LayoutParams lp = new LayoutParams(0, dp(42), w);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private TextView key(String label, int unused, int color, Runnable action) {
        TextView t = new TextView(getContext());
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.length() > 2 ? 15 : 20);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color == 0 ? 0xFF2F3134 : color);
        bg.setCornerRadius(dp(6));
        t.setBackground(bg);
        t.setOnClickListener(v -> {
            // Flash the key so the driver can see which one registered.
            bg.setColor(0xFF8AB4F8);
            v.postDelayed(() -> bg.setColor(color == 0 ? 0xFF2F3134 : color), 150);
            action.run();
        });
        return t;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
