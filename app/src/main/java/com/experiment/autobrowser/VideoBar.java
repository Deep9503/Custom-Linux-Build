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
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    private final ImageView playPause;
    private final TextView time;
    private final Timeline timeline;
    private boolean hiddenByUser;
    private boolean paused = true;

    VideoBar(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        setGravity(Gravity.CENTER_VERTICAL);
        // Soft fade from transparent to dark, like a streaming app's controls.
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xB3000000, 0xE6000000}));
        setPadding(dp(12), dp(28), dp(12), dp(10));
        setClickable(true); // taps on gaps don't fall through to the page
        setVisibility(GONE);

        addView(skipButton(R.drawable.ic_replay, () -> listener.onSeekBy(-10)), size(52));
        playPause = icon(R.drawable.ic_play, dp(34));
        playPause.setOnClickListener(v -> {
            flash(v);
            paused = !paused;
            playPause.setImageResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
            listener.onToggle();
        });
        addView(playPause, size(60));
        addView(skipButton(R.drawable.ic_forward10, () -> listener.onSeekBy(10)), size(52));

        time = new TextView(c);
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        time.setPadding(dp(12), 0, dp(6), 0);
        addView(time);

        timeline = new Timeline(c);
        addView(timeline, new LayoutParams(0, dp(44), 1f));
        ImageView close = icon(R.drawable.ic_close, dp(22));
        close.setAlpha(0.8f);
        close.setOnClickListener(v -> {
            hiddenByUser = true;
            setVisibility(GONE);
        });
        addView(close, size(48));
    }

    /** Called about once a second with the page's video state; t < 0 means no video. */
    void update(double t, double duration, boolean isPaused) {
        if (t < 0) {
            setVisibility(GONE);
            return;
        }
        paused = isPaused;
        playPause.setImageResource(paused ? R.drawable.ic_play : R.drawable.ic_pause);
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

    private ImageView icon(int res, int sizePx) {
        ImageView iv = new ImageView(getContext());
        iv.setImageResource(res);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = Math.max(0, (dp(44) - sizePx) / 2);
        iv.setPadding(pad, pad, pad, pad);
        return iv;
    }

    /** Circular-arrow icon with a small "10" in the middle. */
    private View skipButton(int res, Runnable action) {
        FrameLayout f = new FrameLayout(getContext());
        f.addView(icon(res, dp(32)), new FrameLayout.LayoutParams(-1, -1));
        TextView ten = new TextView(getContext());
        ten.setText("10");
        ten.setTextColor(Color.WHITE);
        ten.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        ten.setTypeface(Typeface.DEFAULT_BOLD);
        ten.setGravity(Gravity.CENTER);
        ten.setPadding(0, dp(3), 0, 0);
        f.addView(ten, new FrameLayout.LayoutParams(-1, -1));
        f.setOnClickListener(v -> { flash(v); action.run(); });
        return f;
    }

    /** Brief round highlight so the driver sees the tap registered. */
    private void flash(View v) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x40FFFFFF);
        v.setBackground(g);
        v.postDelayed(() -> v.setBackground(null), 180);
    }

    private LayoutParams size(int widthDp) {
        LayoutParams lp = new LayoutParams(dp(widthDp), dp(44));
        lp.setMargins(dp(2), 0, dp(2), 0);
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
            track.setColor(0x4DFFFFFF);
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
                c.drawCircle(x, cy, dp(7), knob);
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
