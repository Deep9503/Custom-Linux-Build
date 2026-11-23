package com.experiment.autobrowser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Video controls drawn by the app on the car screen. Web players rely on double-taps and
 * dragging, which Android Auto can't deliver, so this bar offers single-tap controls:
 * back 10 s, play/pause, forward 10 s, and a timeline you tap to jump to that point.
 */
class VideoBar extends LinearLayout {

    interface Listener {
        void onToggle();
        void onSeekBy(int seconds);
        void onSeekTo(float fraction);
    }

    private final Listener listener;
    private final TextView playPause;
    private final TextView time;
    private final Timeline timeline;
    private boolean hiddenByUser;
    private boolean paused = true;

    VideoBar(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(0xD9000000);
        int p = dp(4);
        setPadding(p, p, p, p);
        setClickable(true); // taps on gaps don't fall through to the page
        setVisibility(GONE);

        addView(button("⏪ 10", () -> listener.onSeekBy(-10)), size(76));
        playPause = button("▶", () -> {
            paused = !paused;
            playPause.setText(paused ? "▶" : "⏸");
            listener.onToggle();
        });
        addView(playPause, size(64));
        addView(button("10 ⏩", () -> listener.onSeekBy(10)), size(76));

        time = new TextView(c);
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        time.setPadding(dp(8), 0, dp(8), 0);
        addView(time);

        timeline = new Timeline(c);
        addView(timeline, new LayoutParams(0, dp(44), 1f));
        addView(button("✕", () -> {
            hiddenByUser = true;
            setVisibility(GONE);
        }), size(52));
    }

    /** Called about once a second with the page's video state; t < 0 means no video. */
    void update(double t, double duration, boolean isPaused) {
        if (t < 0) {
            setVisibility(GONE);
            return;
        }
        paused = isPaused;
        playPause.setText(paused ? "▶" : "⏸");
        time.setText(duration > 0 ? fmt(t) + " / " + fmt(duration) : fmt(t) + " · LIVE");
        timeline.set(duration > 0 ? (float) (t / duration) : -1f);
        if (!hiddenByUser) setVisibility(VISIBLE);
    }

    /** New page or tab: forget a previous "hide" and wait for the next video. */
    void reset() {
        hiddenByUser = false;
        setVisibility(GONE);
    }

    /** Show again even if the user hid it (e.g. from the menu). */
    void forceShow() {
        hiddenByUser = false;
    }

    boolean contains(float x, float y) {
        return getVisibility() == VISIBLE && x >= getLeft() && x < getRight()
                && y >= getTop() && y < getBottom();
    }

    private static String fmt(double s) {
        int t = (int) Math.max(0, s);
        int h = t / 3600, m = (t % 3600) / 60, sec = t % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }

    private TextView button(String label, Runnable action) {
        TextView t = new TextView(getContext());
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF3C4043);
        bg.setCornerRadius(dp(6));
        t.setBackground(bg);
        t.setOnClickListener(v -> {
            bg.setColor(0xFF8AB4F8);
            v.postDelayed(() -> bg.setColor(0xFF3C4043), 150);
            action.run();
        });
        return t;
    }

    private LayoutParams size(int widthDp) {
        LayoutParams lp = new LayoutParams(dp(widthDp), dp(44));
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    /** Progress track; a tap jumps the video to that point. */
    private class Timeline extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint done = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress = -1f;

        Timeline(Context c) {
            super(c);
            track.setColor(0xFF5F6368);
            done.setColor(0xFFFF0033);
            knob.setColor(Color.WHITE);
        }

        void set(float p) {
            progress = p;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float pad = dp(10), cy = getHeight() / 2f, h = dp(3);
            float w = getWidth() - 2 * pad;
            c.drawRoundRect(pad, cy - h, pad + w, cy + h, h, h, track);
            if (progress >= 0) {
                float x = pad + w * Math.min(1f, progress);
                c.drawRoundRect(pad, cy - h, x, cy + h, h, h, done);
                c.drawCircle(x, cy, dp(8), knob);
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (progress < 0) return true; // live stream: no seeking
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float pad = dp(10);
                float f = (e.getX() - pad) / (getWidth() - 2 * pad);
                f = Math.max(0f, Math.min(0.999f, f));
                set(f);
                listener.onSeekTo(f);
            }
            return true;
        }
    }
}
