package com.experiment.autobrowser;

import android.annotation.SuppressLint;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;

/** Common WebView configuration and page scripts for phone and car. */
final class WebSetup {
    private WebSetup() {}

    /** Name of the JS bridge the car browser exposes to pages. */
    static final String KEYS_BRIDGE = "AutoBrowserKeys";

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
        if (Prefs.adBlock(c) && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // Runs before YouTube's own scripts, so ad data can be stripped before the player sees it.
            WebViewCompat.addDocumentStartJavaScript(web, YouTubeAds.JS, new HashSet<>(Arrays.asList(
                    "https://youtube.com", "https://*.youtube.com")));
        }
    }

    static void applyDesktopMode(Context c, WebView web) {
        web.getSettings().setUserAgentString(Prefs.desktopMode(c) ? Prefs.DESKTOP_UA : null);
    }

    static final int SCROLL_PIXELS = 0;  // dx/dy in screen pixels
    static final int SCROLL_SMOOTH = 1;  // screen pixels, animated
    static final int SCROLL_PAGES = 2;   // dx/dy in screen-heights (0.8 of the view)

    /**
     * Scrolls whatever is scrollable under the middle of the view (a feed, a list, or the
     * page itself), without touch events, so nothing gets clicked and no pull-to-refresh.
     */
    static String scrollJs(float dx, float dy, int mode) {
        return "(function(dx,dy,m){var d=window.devicePixelRatio||1;"
                + "if(m===2){dx=dx*innerWidth*0.8;dy=dy*innerHeight*0.8;}else{dx/=d;dy/=d;}"
                + "var el=document.elementFromPoint(innerWidth/2,innerHeight/2),t=null;"
                + "while(el&&el!==document.body&&el!==document.documentElement){var s=getComputedStyle(el);"
                + "if(dy&&/(auto|scroll|overlay)/.test(s.overflowY)&&el.scrollHeight>el.clientHeight+1){t=el;break;}"
                + "if(!dy&&dx&&/(auto|scroll|overlay)/.test(s.overflowX)&&el.scrollWidth>el.clientWidth+1){t=el;break;}"
                + "el=el.parentElement;}"
                + "var o={left:dx,top:dy,behavior:m===0?'instant':'smooth'};"
                + "if(t){t.scrollBy(o);}else{(document.scrollingElement||document.documentElement).scrollBy(o);}"
                + "})(" + dx + "," + dy + "," + mode + ")";
    }

    /** Adds (or refreshes) a <style> element that hides ad elements. */
    static String injectCssJs(String css) {
        return "(function(c){var r=document.documentElement;if(!r)return;"
                + "var s=document.getElementById('__ab_css');"
                + "if(!s){s=document.createElement('style');s.id='__ab_css';(document.head||r).appendChild(s);}"
                + "if(s.textContent.length!==c.length)s.textContent=c;})(" + JSONObject.quote(css) + ")";
    }

    /** Tells the car browser when a text field on the page gets focus, so it can show its keyboard. */
    static final String FOCUS_WATCH_JS =
            "(function(){if(window.__abFocus)return;window.__abFocus=1;"
                    + "document.addEventListener('focusin',function(e){var t=e.target;if(!t)return;"
                    + "var tag=(t.tagName||'').toLowerCase();"
                    + "var ok=t.isContentEditable||tag==='textarea'||(tag==='input'&&"
                    + "/^(text|search|email|url|tel|password|number|)$/i.test(t.getAttribute('type')||''));"
                    + "if(!ok)return;window.__abLast=t;"
                    + "if(window." + KEYS_BRIDGE + ")" + KEYS_BRIDGE + ".onFocus("
                    + "t.isContentEditable?t.textContent:(t.value||''),"
                    + "(t.getAttribute('type')||tag),t.getAttribute('placeholder')||t.getAttribute('aria-label')||'');"
                    + "},true);})();";

    /** JS that puts text into the focused (or last focused) field and optionally submits it. */
    static String typeIntoFocusedFieldJs(String text, boolean submit) {
        return "(function(t,sub){var e=document.activeElement;"
                + "if((!e||e===document.body)&&window.__abLast&&document.contains(window.__abLast))e=window.__abLast;"
                + "if(!e||e===document.body){e=document.querySelector("
                + "'input[type=search],input[type=text],input:not([type]),textarea');}"
                + "if(!e)return 'no-field';e.focus();"
                + "if(e.isContentEditable){e.textContent=t;}else{"
                + "var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');"
                + "if(d&&d.set){d.set.call(e,t);}else{e.value=t;}}"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "if(!sub)return 'ok';"
                + "var k={key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true};"
                + "var ok=e.dispatchEvent(new KeyboardEvent('keydown',k));"
                + "e.dispatchEvent(new KeyboardEvent('keypress',k));"
                + "e.dispatchEvent(new KeyboardEvent('keyup',k));"
                + "if(ok&&e.form){if(e.form.requestSubmit){e.form.requestSubmit();}else{e.form.submit();}}"
                + "return 'ok';})(" + JSONObject.quote(text) + "," + submit + ")";
    }
}
