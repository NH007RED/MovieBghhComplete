package com.moviebox.app.finder

import android.net.Uri

/**
 * Ad and pop-up blocker ported from VideoLinkFinder v2.2:
 * Blocks ad domains and hides ad overlays during background link extraction.
 */
object AdBlock {
    private val hosts: Set<String> = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "google-analytics.com", "adnxs.com", "adsrvr.org", "advertising.com", "amazon-adsystem.com",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "revcontent.com", "mgid.com",
        "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com", "smartadserver.com",
        "adform.net", "bidswitch.net", "moatads.com", "scorecardresearch.com", "quantserve.com",
        "exoclick.com", "exosrv.com", "juicyads.com", "trafficjunky.net", "popads.net", "popcash.net",
        "propellerads.com", "onclickads.net", "clickadu.com", "adcash.com", "hilltopads.net",
        "adsterra.com", "monetag.com", "a-ads.com", "ad-maven.com", "admaven.com", "yllix.com",
        "zedo.com", "yieldmo.com", "33across.com", "sharethrough.com", "teads.tv", "adsco.re",
        "adskeeper.com", "realsrv.com", "tsyndicate.com", "clickaine.com", "adspyglass.com",
        "ad.gt", "adtng.com", "pushwhy.com", "syndication.realsrv.com"
    )

    fun isBlocked(url: String): Boolean {
        val host = try {
            Uri.parse(url).host
        } catch (e: Exception) {
            null
        } ?: return false
        var h = host.lowercase()
        while (h.contains('.')) {
            if (h in hosts) return true
            h = h.substringAfter('.')
        }
        return false
    }

    const val PAGE_JS = "(function(){try{" +
        "var s=document.createElement('style');" +
        "s.textContent=\"ins.adsbygoogle,.adsbygoogle,[id^='google_ads'],[id^='div-gpt-ad']," +
        "iframe[src*='doubleclick.net'],iframe[src*='googlesyndication'],iframe[src*='adsterra']," +
        "iframe[src*='popads']{display:none!important}\";" +
        "(document.head||document.documentElement).appendChild(s);" +
        "if(!window.__nhPop){window.__nhPop=1;var o=window.open;" +
        "window.open=function(){if(navigator.userActivation&&navigator.userActivation.isActive){" +
        "return o.apply(window,arguments);}return null;};}" +
        "}catch(e){}})();"
}
