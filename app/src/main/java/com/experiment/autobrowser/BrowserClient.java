package com.experiment.autobrowser;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;

/** WebViewClient shared by the phone and car browsers: ad blocking + page callbacks. */
class BrowserClient extends WebViewClient {

    interface Callbacks {
        void onStarted(String url);
        void onFinished(WebView view, String url);
    }

    private final Context app;
    private final Callbacks callbacks;
    private final String extraJs;
    private volatile String pageHost = "";
    private String cssJsHost, cssJs;

    /** @param extraJs optional script injected into every page (may be null). */
    BrowserClient(Context ctx, Callbacks callbacks, String extraJs) {
        this.app = ctx.getApplicationContext();
        this.callbacks = callbacks;
        this.extraJs = extraJs;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        String s = r.getUrl().getScheme();
        // Keep http(s) inside the browser; ignore app deep links (intent:, market:, ...).
        return s == null || !(s.equals("http") || s.equals("https"));
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
        String host = r.getUrl().getHost();
        if (r.isForMainFrame()) {
            pageHost = host == null ? "" : host;
            return null; // never block a page the user opened
        }
        if (host != null && Prefs.adBlock(app) && AdBlocker.shouldBlock(host, pageHost)) {
            return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        }
        return null;
    }

    @Override
    public void onPageStarted(WebView v, String url, Bitmap favicon) {
        String h = Uri.parse(url).getHost();
        pageHost = h == null ? "" : h;
        inject(v);
        if (callbacks != null) callbacks.onStarted(url);
    }

    @Override
    public void onPageCommitVisible(WebView v, String url) {
        inject(v);
    }

    @Override
    public void onPageFinished(WebView v, String url) {
        inject(v);
        if (callbacks != null) callbacks.onFinished(v, url);
    }

    private void inject(WebView v) {
        if (Prefs.adBlock(app)) {
            String host = pageHost;
            if (!host.equals(cssJsHost)) {
                String css = AdBlocker.css(host);
                cssJs = css.isEmpty() ? null : WebSetup.injectCssJs(css);
                cssJsHost = host;
            }
            if (cssJs != null) v.evaluateJavascript(cssJs, null);
            // Fallback when document-start scripts aren't supported (script is idempotent).
            if (YouTubeAds.isYouTube(host)) v.evaluateJavascript(YouTubeAds.JS, null);
        }
        if (extraJs != null) v.evaluateJavascript(extraJs, null);
    }
}
