package com.experiment.autobrowser;

import android.app.Presentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;

/**
 * Draws a WebView onto the Android Auto map surface.
 *
 * Navigation-category apps receive a raw Surface from the car host. We wrap it in a
 * private VirtualDisplay, show a Presentation on that display, and put a WebView in it.
 * Taps, pans, flings and pinches from the car screen are forwarded to the WebView.
 */
public class CarBrowser implements SurfaceCallback {

    private static final String TAG = "AutoBrowser";

    public interface Listener {
        void onPageChanged(String url, String title);
    }

    private final CarContext ctx;
    private final Handler main = new Handler(Looper.getMainLooper());

    private VirtualDisplay display;
    private Presentation presentation;
    private FrameLayout container;
    private WebView web;
    private int surfaceW, surfaceH;
    private Rect visibleArea;

    private String currentUrl;
    private String currentTitle = "";
    private Listener listener;

    public CarBrowser(CarContext ctx) {
        this.ctx = ctx;
        this.currentUrl = Prefs.home(ctx);
    }

    public void setListener(Listener l) { listener = l; }
    public String url() { return currentUrl; }
    public String title() { return currentTitle; }

    // ---------------------------------------------------------------- surface lifecycle

    @Override
    public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
        Surface surface = sc.getSurface();
        if (surface == null) return;
        releaseDisplay();
        surfaceW = sc.getWidth();
        surfaceH = sc.getHeight();
        try {
            DisplayManager dm = ctx.getSystemService(DisplayManager.class);
            display = dm.createVirtualDisplay("AutoBrowser", surfaceW, surfaceH,
                    sc.getDpi(), surface, 0 /* private display owned by this app */);

            presentation = new Presentation(ctx, display.getDisplay());
            container = new FrameLayout(presentation.getContext());
            container.setBackgroundColor(Color.BLACK);

            web = new WebView(presentation.getContext());
            WebSetup.configure(ctx, web);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    String scheme = r.getUrl().getScheme();
                    // Keep http(s) inside the browser; ignore app deep links (intent:, market:, ...).
                    return scheme == null || !(scheme.equals("http") || scheme.equals("https"));
                }

                @Override
                public void onPageStarted(WebView v, String url, Bitmap favicon) {
                    currentUrl = url;
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    currentUrl = url;
                    currentTitle = v.getTitle() == null ? "" : v.getTitle();
                    if (listener != null) listener.onPageChanged(currentUrl, currentTitle);
                }
            });
            web.setWebChromeClient(new WebChromeClient());

            container.addView(web, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            presentation.setContentView(container);
            presentation.show();
            applyVisibleArea();
            web.loadUrl(currentUrl);
        } catch (Exception e) {
            Log.e(TAG, "Could not attach browser to car surface", e);
        }
    }

    @Override
    public void onVisibleAreaChanged(@NonNull Rect area) {
        visibleArea = new Rect(area);
        applyVisibleArea();
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect area) {
        if (visibleArea == null) onVisibleAreaChanged(area);
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
        releaseDisplay();
    }

    /** Keep page content out from under the host's buttons. */
    private void applyVisibleArea() {
        if (container == null || visibleArea == null || visibleArea.isEmpty()) return;
        container.setPadding(visibleArea.left, visibleArea.top,
                Math.max(0, surfaceW - visibleArea.right),
                Math.max(0, surfaceH - visibleArea.bottom));
    }

    public void release() {
        releaseDisplay();
    }

    private void releaseDisplay() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        if (presentation != null) {
            presentation.dismiss();
            presentation = null;
        }
        if (display != null) {
            display.release();
            display = null;
        }
        container = null;
    }

    // ---------------------------------------------------------------- input from the car

    @Override
    public void onClick(float x, float y) {
        if (web == null) return;
        float wx = x - container.getPaddingLeft();
        float wy = y - container.getPaddingTop();
        if (wx < 0 || wy < 0 || wx > web.getWidth() || wy > web.getHeight()) return;
        long t = SystemClock.uptimeMillis();
        dispatch(t, t, MotionEvent.ACTION_DOWN, wx, wy);
        dispatch(t, t + 60, MotionEvent.ACTION_UP, wx, wy);
    }

    @Override
    public void onScroll(float distanceX, float distanceY) {
        if (web == null) return;
        // Simulate a finger drag so inner scrolling containers work too.
        float cx = web.getWidth() / 2f, cy = web.getHeight() / 2f;
        long t = SystemClock.uptimeMillis();
        dispatch(t, t, MotionEvent.ACTION_DOWN, cx, cy);
        int steps = 4;
        for (int i = 1; i <= steps; i++) {
            float f = i / (float) steps;
            dispatch(t, t + i * 8L, MotionEvent.ACTION_MOVE, cx - distanceX * f, cy - distanceY * f);
        }
        dispatch(t, t + 40, MotionEvent.ACTION_UP, cx - distanceX, cy - distanceY);
    }

    @Override
    public void onFling(float velocityX, float velocityY) {
        if (web != null) web.flingScroll((int) -velocityX, (int) -velocityY);
    }

    @Override
    public void onScale(float focusX, float focusY, float scaleFactor) {
        if (web != null && scaleFactor > 0) {
            web.zoomBy(Math.max(0.02f, Math.min(50f, scaleFactor)));
        }
    }

    private void dispatch(long down, long at, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(down, at, action, x, y, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(e);
        e.recycle();
    }

    // ---------------------------------------------------------------- browser commands

    public void load(String url) {
        currentUrl = url;
        main.post(() -> { if (web != null) web.loadUrl(url); });
    }

    public void back() { if (web != null && web.canGoBack()) web.goBack(); }
    public void forward() { if (web != null && web.canGoForward()) web.goForward(); }
    public void reload() { if (web != null) web.reload(); }
    public void pageUp() { if (web != null) web.pageUp(false); }
    public void pageDown() { if (web != null) web.pageDown(false); }
    public void zoomIn() { if (web != null) web.zoomIn(); }
    public void zoomOut() { if (web != null) web.zoomOut(); }

    public void applyDesktopMode() {
        if (web != null) {
            WebSetup.applyDesktopMode(ctx, web);
            web.reload();
        }
    }

    public void typeIntoPage(String text) {
        if (web != null) web.evaluateJavascript(WebSetup.typeIntoFocusedFieldJs(text), null);
    }
}
