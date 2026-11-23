package com.experiment.autobrowser;

import android.app.Presentation;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.ViewGroup;
import android.view.Gravity;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;

import java.util.ArrayList;
import java.util.List;

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

    public static final int MAX_TABS = 10;

    /** One browser tab. Its WebView exists only while the car screen is connected. */
    public static final class Tab {
        String url;
        String title = "";
        WebView view;

        Tab(String url) { this.url = url; }

        public String url() { return url == null ? "" : url; }
        public String title() { return title == null || title.isEmpty() ? url() : title; }
    }

    private final CarContext ctx;
    private final Handler main = new Handler(Looper.getMainLooper());

    private VirtualDisplay display;
    private Presentation presentation;
    private FrameLayout container;
    private WebView web; // the current tab's WebView
    private final List<Tab> tabs = new ArrayList<>();
    private int current;
    private CarKeyboard keyboard;
    private int surfaceW, surfaceH;
    private Rect visibleArea;
    private Rect stableArea;
    private float lastTapX = -1, lastTapY = -1;

    private volatile long lastTap;
    private Listener listener;

    /** The browser currently shown in the car, if any (lets the phone app send pages to it). */
    public static volatile CarBrowser active;

    public CarBrowser(CarContext ctx) {
        this.ctx = ctx;
        for (String u : Prefs.tabs(ctx)) tabs.add(new Tab(u.isEmpty() ? Prefs.home(ctx) : u));
        if (tabs.isEmpty()) tabs.add(new Tab(Prefs.home(ctx)));
        current = Math.min(Math.max(0, Prefs.currentTab(ctx)), tabs.size() - 1);
        AdBlocker.init(ctx);
        active = this;
    }

    public void setListener(Listener l) { listener = l; }
    public String url() { return tabs.get(current).url(); }
    public String title() { String t = tabs.get(current).title; return t == null ? "" : t; }
    public List<Tab> tabs() { return tabs; }
    public int currentIndex() { return current; }

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

            keyboard = new CarKeyboard(presentation.getContext(), new CarKeyboard.Listener() {
                @Override
                public void onSubmit(String text, int mode, boolean enter) {
                    if (mode == CarKeyboard.MODE_PAGE) {
                        typeIntoPage(text, enter);
                    } else if (!text.trim().isEmpty()) {
                        Prefs.addRecent(ctx, text.trim());
                        load(Prefs.resolve(ctx, text));
                    }
                }

                @Override
                public void onClose() { }
            });
            keyboard.setVisibility(android.view.View.GONE);
            container.addView(keyboard, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM));
            presentation.setContentView(container);
            presentation.show();
            applyVisibleArea();
            switchTo(current);
        } catch (Exception e) {
            Log.e(TAG, "Could not attach browser to car surface", e);
        }
    }

    /**
     * Android Auto changes the "visible" area every time its buttons show or hide (on each
     * touch). Following it made the page and keyboard jump under the user's finger, so we
     * lay out against the "stable" area, which never changes while the app is open.
     */
    @Override
    public void onVisibleAreaChanged(@NonNull Rect area) {
        visibleArea = new Rect(area);
        if (stableArea == null) applyVisibleArea();
    }

    @Override
    public void onStableAreaChanged(@NonNull Rect area) {
        stableArea = new Rect(area);
        applyVisibleArea();
    }

    @Override
    public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
        releaseDisplay();
    }

    /** Keep page content out from under the host's buttons. */
    private void applyVisibleArea() {
        Rect a = stableArea != null && !stableArea.isEmpty() ? stableArea : visibleArea;
        if (container == null || a == null || a.isEmpty()) return;
        int l = a.left, t = a.top, r = Math.max(0, surfaceW - a.right), b = Math.max(0, surfaceH - a.bottom);
        if (container.getPaddingLeft() == l && container.getPaddingTop() == t
                && container.getPaddingRight() == r && container.getPaddingBottom() == b) return;
        container.setPadding(l, t, r, b);
    }

    public void release() {
        releaseDisplay();
        if (active == this) active = null;
    }

    private void releaseDisplay() {
        saveTabs();
        for (Tab t : tabs) {
            if (t.view != null) {
                t.view.stopLoading();
                t.view.destroy();
                t.view = null;
            }
        }
        web = null;
        if (presentation != null) {
            presentation.dismiss();
            presentation = null;
        }
        if (display != null) {
            display.release();
            display = null;
        }
        container = null;
        keyboard = null;
    }

    // ---------------------------------------------------------------- input from the car

    @Override
    public void onClick(float x, float y) {
        if (container == null) return;
        // Surface coordinates == container coordinates, so the tap reaches the
        // on-screen keyboard if it's open, otherwise the web page underneath.
        long t = SystemClock.uptimeMillis();
        // Some head units report one press twice; drop an identical tap within 90 ms.
        if (t - lastTap < 90 && Math.abs(x - lastTapX) < 8 && Math.abs(y - lastTapY) < 8) return;
        lastTap = t;
        lastTapX = x;
        lastTapY = y;
        if (keyboard != null && keyboard.isOpen() && !keyboard.contains(x, y)) {
            return; // while typing, stray taps on the page are ignored (close with ✕)
        }
        tap(container, t, MotionEvent.ACTION_DOWN, x, y);
        tap(container, t, MotionEvent.ACTION_UP, x, y);
    }

    @Override
    public void onScroll(float distanceX, float distanceY) {
        // Scroll the page directly (like a mouse wheel) instead of faking finger drags,
        // which sites like YouTube mistook for taps or pull-to-refresh.
        if (web == null || (keyboard != null && keyboard.isOpen())) return;
        web.evaluateJavascript(WebSetup.scrollJs(distanceX, distanceY, WebSetup.SCROLL_PIXELS), null);
    }

    @Override
    public void onFling(float velocityX, float velocityY) {
        if (web == null || (keyboard != null && keyboard.isOpen())) return;
        web.evaluateJavascript(WebSetup.scrollJs(-velocityX * 0.3f, -velocityY * 0.3f,
                WebSetup.SCROLL_SMOOTH), null);
    }

    @Override
    public void onScale(float focusX, float focusY, float scaleFactor) {
        if (web != null && scaleFactor > 0) {
            web.zoomBy(Math.max(0.02f, Math.min(50f, scaleFactor)));
        }
    }

    private static void tap(android.view.View target, long down, int action, float x, float y) {
        MotionEvent e = MotionEvent.obtain(down, down + (action == MotionEvent.ACTION_UP ? 60 : 0),
                action, x, y, 0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(e);
        e.recycle();
    }

    // ---------------------------------------------------------------- browser commands

    public void load(String url) {
        Tab t = tabs.get(current);
        t.url = url;
        t.title = "";
        saveTabs();
        main.post(() -> { if (t.view != null) t.view.loadUrl(url); });
    }

    // ---------------------------------------------------------------- tabs

    /** Shows tab {@code i} on the car screen, creating its WebView if needed. */
    public void switchTo(int i) {
        if (i < 0 || i >= tabs.size()) return;
        current = i;
        Tab t = tabs.get(i);
        if (container != null) {
            if (web != null && web != t.view) container.removeView(web);
            boolean fresh = t.view == null;
            if (fresh) t.view = createWebView(t);
            if (t.view.getParent() == null) {
                container.addView(t.view, 0, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            web = t.view;
            if (fresh && t.url != null && !t.url.isEmpty()) web.loadUrl(t.url);
        }
        saveTabs();
        if (listener != null) listener.onPageChanged(t.url(), t.title);
    }

    /** Opens a new tab (at most {@link #MAX_TABS}) and shows it. */
    public boolean newTab(String url) {
        if (tabs.size() >= MAX_TABS) return false;
        tabs.add(new Tab(url));
        switchTo(tabs.size() - 1);
        return true;
    }

    public void closeTab(int i) {
        if (i < 0 || i >= tabs.size()) return;
        Tab t = tabs.remove(i);
        if (t.view != null) {
            if (container != null) container.removeView(t.view);
            if (web == t.view) web = null;
            t.view.destroy();
        }
        if (tabs.isEmpty()) tabs.add(new Tab(Prefs.home(ctx)));
        if (current > i || current >= tabs.size()) current = Math.max(0, current - 1);
        switchTo(current);
    }

    private void saveTabs() {
        List<String> urls = new ArrayList<>();
        for (Tab t : tabs) urls.add(t.url());
        Prefs.setTabs(ctx, urls, current);
    }

    private WebView createWebView(Tab tab) {
        WebView v = new WebView(presentation.getContext());
        WebSetup.configure(ctx, v);
        v.getSettings().setSupportMultipleWindows(true); // target=_blank links open new tabs
        v.setWebViewClient(new BrowserClient(ctx, new BrowserClient.Callbacks() {
            @Override
            public void onStarted(String url) {
                tab.url = url;
                if (v == web && keyboard != null && keyboard.isOpen() && keyboard.isPageMode()) {
                    keyboard.setVisibility(android.view.View.GONE);
                }
            }

            @Override
            public void onFinished(WebView view, String url) {
                tab.url = url;
                tab.title = view.getTitle() == null ? "" : view.getTitle();
                saveTabs();
                if (view == web && listener != null) listener.onPageChanged(tab.url, tab.title);
            }
        }, WebSetup.FOCUS_WATCH_JS));
        v.addJavascriptInterface(new KeysBridge(), WebSetup.KEYS_BRIDGE);
        v.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          Message resultMsg) {
                // Only for links the user tapped; scripted pop-ups are blocked.
                if (!isUserGesture || tabs.size() >= MAX_TABS || presentation == null) return false;
                Tab t = new Tab("");
                t.view = createWebView(t);
                tabs.add(t);
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(t.view);
                resultMsg.sendToTarget();
                switchTo(tabs.size() - 1);
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                for (int i = 0; i < tabs.size(); i++) {
                    if (tabs.get(i).view == window) { closeTab(i); break; }
                }
            }
        });
        return v;
    }

    public void back() { if (web != null && web.canGoBack()) web.goBack(); }
    public void forward() { if (web != null && web.canGoForward()) web.goForward(); }
    public void reload() { if (web != null) web.reload(); }
    public void pageUp() {
        if (web != null) web.evaluateJavascript(WebSetup.scrollJs(0, -1, WebSetup.SCROLL_PAGES), null);
    }

    public void pageDown() {
        if (web != null) web.evaluateJavascript(WebSetup.scrollJs(0, 1, WebSetup.SCROLL_PAGES), null);
    }
    public void zoomIn() { if (web != null) web.zoomIn(); }
    public void zoomOut() { if (web != null) web.zoomOut(); }

    public void applyDesktopMode() {
        if (web != null) {
            WebSetup.applyDesktopMode(ctx, web);
            web.reload();
        }
    }

    public void typeIntoPage(String text, boolean enter) {
        if (web != null) web.evaluateJavascript(WebSetup.typeIntoFocusedFieldJs(text, enter), null);
    }

    /** Opens the on-screen keyboard for a web search / address. */
    public void showAddressKeyboard() {
        if (keyboard != null) {
            keyboard.open(CarKeyboard.MODE_ADDRESS, "",
                    "Search " + Prefs.engineName(ctx) + " or type a web address");
        }
    }

    /** Called from page JavaScript when a text field gets focus. */
    private class KeysBridge {
        @JavascriptInterface
        public void onFocus(String value, String type, String label) {
            main.post(() -> {
                // Only react to fields the user tapped, not ones a page focuses by itself.
                if (keyboard == null || SystemClock.uptimeMillis() - lastTap > 2000) return;
                String hint = !label.isEmpty() ? label
                        : "password".equals(type) ? "Password" : "Type here";
                keyboard.open(CarKeyboard.MODE_PAGE, value, hint);
            });
        }
    }
}
