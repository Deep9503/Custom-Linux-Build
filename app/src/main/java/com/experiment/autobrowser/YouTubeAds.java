package com.experiment.autobrowser;

/**
 * YouTube ad blocking. YouTube serves ads from the same servers as videos, so domain
 * lists can't stop them. Instead (like uBlock Origin / Brave scriptlets) this script:
 *  1. strips ad data (adPlacements, playerAds, adSlots...) from the player config,
 *     whether it arrives in the page, via JSON.parse or via fetch;
 *  2. hides ad cards, banners and overlays;
 *  3. as a fallback, auto-clicks "Skip" and fast-forwards any ad that still plays.
 */
final class YouTubeAds {
    private YouTubeAds() {}

    static boolean isYouTube(String host) {
        return host != null && (host.equals("youtube.com") || host.endsWith(".youtube.com"));
    }

    static final String JS = "(function(){if(window.__abYT)return;window.__abYT=1;"
            // 1. prune ad data
            + "var K=['adPlacements','adSlots','playerAds','adBreakHeartbeatParams','adBreakParams'];"
            + "function prune(o){try{if(!o||typeof o!=='object')return o;"
            + "if(Array.isArray(o)){o.forEach(prune);return o;}"
            + "K.forEach(function(k){if(k in o)delete o[k];});"
            + "if(o.playerResponse)prune(o.playerResponse);"
            + "if(o.response&&typeof o.response==='object')prune(o.response);}catch(e){}return o;}"
            + "function hasAds(o){return o&&typeof o==='object'&&(o.adPlacements||o.playerAds||o.adSlots"
            + "||(o.playerResponse&&(o.playerResponse.adPlacements||o.playerResponse.playerAds||o.playerResponse.adSlots)));}"
            + "var ipr;try{Object.defineProperty(window,'ytInitialPlayerResponse',{configurable:true,"
            + "get:function(){return ipr;},set:function(v){ipr=prune(v);}});}catch(e){}"
            + "var jp=JSON.parse;JSON.parse=function(){var r=jp.apply(this,arguments);"
            + "try{if(hasAds(r))prune(r);}catch(e){}return r;};"
            + "try{var rj=Response.prototype.json;Response.prototype.json=function(){"
            + "return rj.apply(this,arguments).then(function(r){if(hasAds(r))prune(r);return r;});};}catch(e){}"
            // 2. hide ad elements
            + "var css='ytd-ad-slot-renderer,ytd-in-feed-ad-layout-renderer,ytd-promoted-sparkles-web-renderer,"
            + "ytd-display-ad-renderer,ytd-banner-promo-renderer,ytd-statement-banner-renderer,"
            + "ytd-player-legacy-desktop-watch-ads-renderer,ytd-promoted-video-renderer,ytd-search-pyv-renderer,"
            + "ytd-companion-slot-renderer,ytd-action-companion-ad-renderer,ytd-engagement-panel-section-list-renderer[target-id=engagement-panel-ads],"
            + "#player-ads,#masthead-ad,.ytp-ad-overlay-container,.ytp-ad-image-overlay,.ytp-ad-text-overlay,"
            + "ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytm-ad-slot-renderer,ad-slot-renderer,"
            + "ytm-promoted-video-renderer,ytm-in-feed-ad-layout-renderer,ytm-banner-promo-renderer,"
            + "ytd-rich-item-renderer:has(ytd-ad-slot-renderer),ytm-rich-item-renderer:has(ad-slot-renderer)"
            + "{display:none!important}';"
            + "(function add(){var r=document.documentElement;if(!r){setTimeout(add,50);return;}"
            + "var s=document.createElement('style');s.textContent=css;(document.head||r).appendChild(s);})();"
            // 3. skip anything that still plays
            + "var on=0,wasMuted=false;"
            + "setInterval(function(){var v=document.querySelector('video');"
            + "var b=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,"
            + ".ytp-ad-skip-button-container button,.ytp-ad-overlay-close-button');if(b)b.click();"
            + "var ad=document.querySelector('.ad-showing,.ad-interrupting');"
            + "if(ad&&v){if(!on){on=1;wasMuted=v.muted;}v.muted=true;v.playbackRate=16;"
            + "if(isFinite(v.duration)&&v.duration>0&&v.currentTime<v.duration-0.1)v.currentTime=v.duration-0.05;}"
            + "else if(on&&v){on=0;v.muted=wasMuted;v.playbackRate=1;}},250);"
            + "})();";
}
