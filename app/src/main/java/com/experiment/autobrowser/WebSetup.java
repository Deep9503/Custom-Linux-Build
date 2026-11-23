package com.experiment.autobrowser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** Common WebView configuration for phone and car. */
final class WebSetup {
    private WebSetup() {}

    @SuppressLint("SetJavaScriptEnabled")
    static void configure(Context c, WebView web) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        applyDesktopMode(c, web);
    }

    static void applyDesktopMode(Context c, WebView web) {
        web.getSettings().setUserAgentString(Prefs.desktopMode(c) ? Prefs.DESKTOP_UA : null);
    }

    /** JS that types text into the focused field of the page and submits it. */
    static String typeIntoFocusedFieldJs(String text) {
        return "(function(t){var e=document.activeElement;"
                + "if(!e||e===document.body){e=document.querySelector("
                + "'input[type=search],input[type=text],input:not([type]),textarea');if(e)e.focus();}"
                + "if(!e)return 'no-field';"
                + "if(e.isContentEditable){e.textContent=t;}else{"
                + "var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');"
                + "if(d&&d.set){d.set.call(e,t);}else{e.value=t;}}"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "var k={key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true};"
                + "e.dispatchEvent(new KeyboardEvent('keydown',k));"
                + "e.dispatchEvent(new KeyboardEvent('keyup',k));"
                + "if(e.form){if(e.form.requestSubmit){e.form.requestSubmit();}else{e.form.submit();}}"
                + "return 'ok';})(" + org.json.JSONObject.quote(text) + ")";
    }
}
