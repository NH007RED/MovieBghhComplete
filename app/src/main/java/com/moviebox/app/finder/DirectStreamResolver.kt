package com.moviebox.app.finder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.JsonParser
import com.moviebox.app.net.Net
import com.moviebox.app.player.PlayHeaders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

data class ResolvedStream(
    val url: String,
    val mimeType: String? = null,
    val verifiedPlayable: Boolean = false
)

// Automatic Direct Video Stream Resolver for MovieBox v24.0.
//
// Key fixes for Gofile .mkv direct playback:
// 1. DoH-First DNS (Net.client): Bypasses ISP DNS sinkholing on mobile networks (Jio / Airtel)
//    that resolves *.gofile.io to a sinkhole IP serving "<html><head></head><body></body></html>".
// 2. Direct /download/web/<uuid>/<filename>.mkv handling:
//    - Probes immediately over DoH with existing cookies/tokens.
//    - Mints a guest accountToken via POST https://api.gofile.io/accounts and probes the
//      direct .mkv URL immediately BEFORE calling /contents/.
//    - If /contents/<uuid> is needed, resolves both single-file payloads (including
//      parentFolderCode / downloadPage) and folder children payloads.
// 3. Invisible WebView Navigation on https://gofile.io/d/<contentId>:
//    - Loading a raw /download/web/ URL in WebView without a token returns an empty
//      "<html><head></head><body></body></html>" document with zero scripts.
//    - Instead, the invisible WebView loads https://gofile.io/d/<contentId> (proxied over
//      Net.client DoH, supporting GET, POST /accounts, and OPTIONS CORS preflights) so
//      Gofile's official web app JS (config.js + wt.obf.js) runs, creates and authorizes
//      accountToken, and unlocks the direct .mkv stream.
object DirectStreamResolver {

    private val gofileShareRegex = Regex(
        """https?://(?:www\.)?gofile\.io/d/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE
    )

    private val gofileDownloadContentIdRegex = Regex(
        """https?://[a-zA-Z0-9.-]*gofile\.io/download/(?:web|direct)/([A-Za-z0-9-]+)""",
        RegexOption.IGNORE_CASE
    )

    private val KNOWN_GOFILE_SALTS = listOf(
        "5d4f7g8sd45fsd",
        "12af056dacea0b",
        "9844d94d963d30",
        "gf2026x",
        "4fd6sg89d7s6"
    )

    @Volatile
    private var lastWorkingSalt: String? = null

    @Volatile
    private var scrapedWtObfSalt: String? = null

    suspend fun resolveForDirectPlay(
        context: Context,
        rawUrl: String,
        forceRefreshToken: Boolean = false
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        PlayHeaders.initUserAgentIfNeeded(appContext)

        val normalized = PlayHeaders.normalizeUrl(rawUrl)
        if (normalized.isBlank() ||
            normalized.startsWith("moviebox-offline://") ||
            normalized.startsWith("file://") ||
            normalized.startsWith("content://")
        ) {
            return@withContext ResolvedStream(normalized, null, true)
        }

        var workingUrl = normalized

        // =========================================================================
        // PATH A: GOFILE LINKS (file-*.gofile.io/download/web/.../*.mkv OR gofile.io/d/...)
        // =========================================================================
        if (PlayHeaders.isGofileHost(workingUrl)) {
            val shareId = gofileShareRegex.find(workingUrl)?.groupValues?.getOrNull(1)
            val downloadFileId = gofileDownloadContentIdRegex.find(workingUrl)?.groupValues?.getOrNull(1)
            val contentId = shareId ?: downloadFileId

            if (forceRefreshToken) {
                PlayHeaders.clearGofileToken(appContext)
            }

            // A1. Fast Direct Probe over DoH (matches VideoLinkFinder v2.2 PLAY path):
            //     If workingUrl is already a direct /download/ link, test it immediately over DoH
            //     with any existing token/cookie (and without token if none is set).
            if (!forceRefreshToken && shareId == null) {
                probeGofileWithReferers(workingUrl)?.let { playable ->
                    return@withContext ResolvedStream(playable.url, playable.mime, true)
                }
            }

            // A2. Mint Guest accountToken via POST https://api.gofile.io/accounts over DoH
            //     and IMMEDIATELY probe workingUrl BEFORE making any /contents/ calls!
            val apiToken = acquireGofileAccountTokenViaApi(appContext, workingUrl)
            if (apiToken.isNotBlank() && shareId == null) {
                probeGofileWithReferers(workingUrl)?.let { playable ->
                    return@withContext ResolvedStream(playable.url, playable.mime, true)
                }
            }

            // A3. If workingUrl is a gofile.io/d/<shareId> folder link OR if the direct link
            //     needed /contents/<id> unlock, call /contents/<id> over DoH (max 3 attempts).
            if (apiToken.isNotBlank() && contentId != null) {
                fetchGofileContentLinkViaApi(appContext, contentId, apiToken)?.let { freshLink ->
                    workingUrl = PlayHeaders.normalizeUrl(freshLink)
                    PlayHeaders.saveGofileTokenCookie(apiToken, workingUrl, appContext)
                }
                probeGofileWithReferers(workingUrl)?.let { playable ->
                    return@withContext ResolvedStream(playable.url, playable.mime, true)
                }
            }

            // A4. Stage 1 DoH-Backed WebView: Execute Gofile's live wt.obf.js over DoH
            val wtResolvedUrl = resolveGofileViaWtObfWebView(appContext, workingUrl, contentId)
            if (!wtResolvedUrl.isNullOrBlank()) {
                workingUrl = PlayHeaders.normalizeUrl(wtResolvedUrl)
            }
            probeGofileWithReferers(workingUrl)?.let { playable ->
                return@withContext ResolvedStream(playable.url, playable.mime, true)
            }

            // A5. Stage 2 DoH-Backed Invisible Browser Navigation on https://gofile.io/d/<contentId>
            val browserResolved = resolveViaInvisibleBrowserNavigation(
                appContext = appContext,
                targetUrl = workingUrl,
                gofileContentId = contentId
            )
            if (browserResolved != null) {
                return@withContext browserResolved
            }

            return@withContext ResolvedStream(workingUrl, null, false)
        }

        // =========================================================================
        // PATH B: GENERAL DIRECT STREAM OR WEB PAGE LINKS
        // =========================================================================
        val probeResult = LinkProbe.probe(
            url = workingUrl,
            referer = PlayHeaders.refererFor(workingUrl),
            ua = PlayHeaders.userAgent
        )

        if (probeResult is ProbeResult.Playable) {
            probeResult.mime?.let { PlayHeaders.mimeTypes[probeResult.url] = it }
            return@withContext ResolvedStream(probeResult.url, probeResult.mime, true)
        }

        // If LinkProbe detected a web page or HTTP error, run the DoH-backed invisible Browser extractor
        if (probeResult is ProbeResult.WebPage || probeResult is ProbeResult.HttpError) {
            val browserResolved = resolveViaInvisibleBrowserNavigation(
                appContext = appContext,
                targetUrl = workingUrl,
                gofileContentId = null
            )
            if (browserResolved != null) {
                return@withContext browserResolved
            }
        }

        ResolvedStream(workingUrl, null, false)
    }

    // Probes a Gofile direct download URL across the 3 Referer modes used by Gofile servers:
    // 1. "https://gofile.io/" (standard Gofile web referer)
    // 2. url itself (exact match to VideoLinkFinder v2.2 BrowserActivity where current = start)
    // 3. "" (exact match to rkwyu/gofile-dl where no Referer header is sent)
    // Records the exact Referer that succeeded into PlayHeaders so ExoPlayer sends identical headers.
    private fun probeGofileWithReferers(url: String): ProbeResult.Playable? {
        val ua = PlayHeaders.userAgent
        val referersToTry = listOf("https://gofile.io/", url, "")
        for (ref in referersToTry) {
            val res = LinkProbe.probe(url = url, referer = ref, ua = ua)
            if (res is ProbeResult.Playable) {
                PlayHeaders.referer = ref
                PlayHeaders.customRefererByUrl[url] = ref
                PlayHeaders.customRefererByUrl[res.url] = ref
                res.mime?.let { PlayHeaders.mimeTypes[res.url] = it }
                return res
            }
        }
        return null
    }

    // Computes Gofile's 2026 dynamic X-Website-Token:
    // sha256("{userAgent}::{language}::{accountToken}::{timeWindow}::{salt}")
    // where timeWindow = unixSeconds / 14400.
    fun generateGofileWebsiteToken(
        userAgent: String,
        accountToken: String,
        salt: String = "5d4f7g8sd45fsd",
        language: String = "en-US",
        windowOffset: Long = 0L
    ): String {
        val timeSlot = ((System.currentTimeMillis() / 1000L) / 14400L) + windowOffset
        val raw = "$userAgent::$language::$accountToken::$timeSlot::$salt"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    private fun candidateSalts(context: Context): List<String> {
        if (scrapedWtObfSalt == null) {
            scrapedWtObfSalt = fetchLiveSaltFromWtObfJs(context).orEmpty()
        }
        val list = mutableListOf<String>()
        lastWorkingSalt?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        PlayHeaders.cachedGofileSalt.takeIf { it.isNotBlank() }?.let { list.add(it) }
        scrapedWtObfSalt?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        list.addAll(KNOWN_GOFILE_SALTS)
        return list.distinct()
    }

    // Downloads https://gofile.io/dist/js/wt.obf.js over Net.client (DoH) and looks for
    // candidate salt strings so salt rotation is handled automatically.
    private fun fetchLiveSaltFromWtObfJs(context: Context): String? {
        return try {
            val req = Request.Builder()
                .url("https://gofile.io/dist/js/wt.obf.js")
                .header("User-Agent", PlayHeaders.userAgent)
                .header("Referer", "https://gofile.io/")
                .build()
            Net.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val js = resp.body?.string().orEmpty()
                if (js.isBlank()) return null

                val colonSalt = Regex("""::([A-Za-z0-9]{6,20})["']""").find(js)?.groupValues?.getOrNull(1)
                if (!colonSalt.isNullOrBlank()) {
                    PlayHeaders.saveCachedGofileSalt(context, colonSalt)
                    return colonSalt
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // Creates a Gofile guest account token via POST https://api.gofile.io/accounts over DoH.
    // Follows official Gofile API docs (POST with JSON "{}" or empty body) first, then X-Website-Token fallback.
    private fun acquireGofileAccountTokenViaApi(context: Context, targetUrl: String): String {
        val ua = PlayHeaders.userAgent
        val jsonBody = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
        val emptyPostBody = ByteArray(0).toRequestBody(null)

        // 1. Official Gofile API / gallery-dl / rkwyu/gofile-dl method:
        //    POST https://api.gofile.io/accounts with JSON "{}" or empty body (no X-Website-Token required)
        for (body in listOf(jsonBody, emptyPostBody)) {
            try {
                val req = Request.Builder()
                    .url("https://api.gofile.io/accounts")
                    .header("User-Agent", ua)
                    .header("Accept", "*/*")
                    .header("Origin", "https://gofile.io")
                    .header("Referer", "https://gofile.io/")
                    .post(body)
                    .build()
                Net.client.newCall(req).execute().use { resp ->
                    resp.headers("Set-Cookie").forEach { sc ->
                        runCatching { CookieManager.getInstance().setCookie("https://gofile.io", sc) }
                    }
                    val bodyStr = resp.body?.string().orEmpty()
                    if (bodyStr.isNotBlank()) {
                        val json = JsonParser.parseString(bodyStr).asJsonObject
                        if (json.get("status")?.asString.equals("ok", ignoreCase = true)) {
                            val token = json.getAsJsonObject("data")?.get("token")?.asString.orEmpty()
                            if (token.isNotBlank()) {
                                PlayHeaders.saveGofileTokenCookie(token, targetUrl, context)
                                return token
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Fallback: POST https://api.gofile.io/accounts with X-Website-Token & X-BL
        for (salt in candidateSalts(context).take(3)) {
            try {
                val wt = generateGofileWebsiteToken(
                    userAgent = ua,
                    accountToken = "",
                    salt = salt,
                    language = "en-US"
                )
                val req = Request.Builder()
                    .url("https://api.gofile.io/accounts")
                    .header("User-Agent", ua)
                    .header("Accept", "*/*")
                    .header("Origin", "https://gofile.io")
                    .header("Referer", "https://gofile.io/")
                    .header("X-Website-Token", wt)
                    .header("X-BL", "en-US")
                    .post(emptyPostBody)
                    .build()

                Net.client.newCall(req).execute().use { resp ->
                    resp.headers("Set-Cookie").forEach { sc ->
                        runCatching { CookieManager.getInstance().setCookie("https://gofile.io", sc) }
                    }
                    val bodyStr = resp.body?.string().orEmpty()
                    if (bodyStr.isNotBlank()) {
                        val json = JsonParser.parseString(bodyStr).asJsonObject
                        val status = json.get("status")?.asString.orEmpty()
                        if (status.equals("ok", ignoreCase = true)) {
                            val token = json.getAsJsonObject("data")?.get("token")?.asString.orEmpty()
                            if (token.isNotBlank()) {
                                lastWorkingSalt = salt
                                PlayHeaders.saveCachedGofileSalt(context, salt)
                                PlayHeaders.saveGofileTokenCookie(token, targetUrl, context)
                                return token
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return PlayHeaders.gofileToken
    }

    // Calls GET https://api.gofile.io/contents/{contentId} with the 2026 X-Website-Token
    // and X-BL headers (bounded to at most 3 attempts to never trigger 429 rate limits).
    private fun fetchGofileContentLinkViaApi(
        context: Context,
        contentId: String,
        token: String,
        depth: Int = 0
    ): String? {
        if (depth > 1) return null
        val ua = PlayHeaders.userAgent
        val salts = candidateSalts(context).take(3)

        for (salt in salts) {
            try {
                val wt = generateGofileWebsiteToken(
                    userAgent = ua,
                    accountToken = token,
                    salt = salt,
                    language = "en-US",
                    windowOffset = 0L
                )
                val url = "https://api.gofile.io/contents/$contentId?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", ua)
                    .header("Accept", "*/*")
                    .header("Origin", "https://gofile.io")
                    .header("Referer", "https://gofile.io/")
                    .header("Authorization", "Bearer $token")
                    .header("X-Website-Token", wt)
                    .header("X-BL", "en-US")
                    .build()

                Net.client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (bodyStr.isNotBlank()) {
                        val root = JsonParser.parseString(bodyStr).asJsonObject
                        val status = root.get("status")?.asString.orEmpty()
                        if (status.equals("ok", ignoreCase = true)) {
                            lastWorkingSalt = salt
                            PlayHeaders.saveCachedGofileSalt(context, salt)
                            val data = root.getAsJsonObject("data") ?: return null
                            data.get("link")?.asString?.takeIf { it.isNotBlank() }?.let { return it }

                            // If contentId was a single file UUID that returned parentFolderCode or downloadPage
                            val dlPageCode = data.get("downloadPage")?.asString
                                ?.let { gofileShareRegex.find(it)?.groupValues?.getOrNull(1) }
                            val parentCode = data.get("parentFolderCode")?.asString
                                ?: dlPageCode
                                ?: data.get("parentFolder")?.asString
                                ?: data.get("code")?.asString
                            if (!parentCode.isNullOrBlank() && parentCode != contentId && !data.has("children")) {
                                fetchGofileContentLinkViaApi(context, parentCode, token, depth + 1)?.let {
                                    return it
                                }
                            }

                            val children = data.getAsJsonObject("children") ?: return null
                            if (children.has(contentId)) {
                                val exact = children.getAsJsonObject(contentId)
                                exact?.get("link")?.asString?.takeIf { it.isNotBlank() }?.let { return it }
                            }
                            for (entry in children.entrySet()) {
                                val child = entry.value.asJsonObject
                                val link = child.get("link")?.asString.orEmpty()
                                val name = child.get("name")?.asString.orEmpty()
                                val mimetype = child.get("mimetype")?.asString.orEmpty()
                                if (link.isNotBlank() && (VideoExtractor.looksLikeMedia(link) ||
                                        VideoExtractor.looksLikeMedia(name) ||
                                        mimetype.startsWith("video/", ignoreCase = true) ||
                                        mimetype.contains("matroska", ignoreCase = true))
                                ) {
                                    return link
                                }
                            }
                            for (entry in children.entrySet()) {
                                val link = entry.value.asJsonObject.get("link")?.asString.orEmpty()
                                if (link.isNotBlank()) return link
                            }
                            return null
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    // Proxies WebView requests for gofile.io and api.gofile.io (including OPTIONS CORS preflights,
    // POST /accounts, and GET pages/scripts/JSON) through Net.client (DoH) so WebView is 100%
    // immune to ISP DNS sinkholing ("<html><head></head><body></body></html>").
    private fun proxyThroughDohIfNeeded(request: WebResourceRequest?): WebResourceResponse? {
        if (request == null) return null
        val urlStr = request.url?.toString() ?: return null
        val scheme = request.url?.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (VideoExtractor.looksLikeMedia(urlStr)) return null

        val host = request.url?.host?.lowercase(Locale.US).orEmpty()
        if (!host.contains("gofile.io")) return null

        val method = request.method?.uppercase(Locale.US) ?: "GET"
        val corsHeaders = HashMap<String, String>().apply {
            put("Access-Control-Allow-Origin", request.requestHeaders["Origin"] ?: "https://gofile.io")
            put("Access-Control-Allow-Credentials", "true")
            put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            put(
                "Access-Control-Allow-Headers",
                request.requestHeaders["Access-Control-Request-Headers"]
                    ?: "Authorization, X-Website-Token, X-BL, Content-Type, Accept, Origin, Referer, User-Agent"
            )
        }

        if (method == "OPTIONS") {
            return WebResourceResponse(
                "text/plain",
                "utf-8",
                200,
                "OK",
                corsHeaders,
                ByteArrayInputStream(ByteArray(0))
            )
        }

        if (method != "GET" && !(method == "POST" && urlStr.contains("/accounts"))) {
            return null
        }

        return try {
            val rb = Request.Builder()
                .url(urlStr)
                .header("User-Agent", PlayHeaders.userAgent)
            request.requestHeaders.forEach { (k, v) ->
                if (!k.equals("Accept-Encoding", ignoreCase = true)) {
                    rb.header(k, v)
                }
            }
            val cookie = PlayHeaders.cookieHeaderFor(urlStr)
            if (cookie.isNotEmpty() && request.requestHeaders["Cookie"].isNullOrBlank()) {
                rb.header("Cookie", cookie)
            }
            if (method == "POST") {
                rb.post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            } else {
                rb.get()
            }

            val resp = Net.client.newCall(rb.build()).execute()
            resp.headers("Set-Cookie").forEach { sc ->
                runCatching { CookieManager.getInstance().setCookie("https://gofile.io", sc) }
            }
            val rawCt = resp.header("Content-Type") ?: "text/html; charset=utf-8"
            val mime = rawCt.substringBefore(';').trim().ifBlank { "text/html" }
            val charset = rawCt.substringAfter("charset=", "utf-8").substringBefore(';').trim().ifBlank { "utf-8" }
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            resp.close()

            WebResourceResponse(
                mime,
                charset,
                resp.code.coerceIn(200, 599),
                resp.message.ifBlank { "OK" },
                corsHeaders,
                ByteArrayInputStream(bytes)
            )
        } catch (_: Exception) {
            null
        }
    }

    // Stage 1 WebView: Executes Gofile's official https://gofile.io/dist/js/wt.obf.js inside an
    // offscreen WebView with DoH request proxying and TextEncoder.prototype.encode hooking so
    // any rotated salt and live X-Website-Token are captured automatically.
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveGofileViaWtObfWebView(
        appContext: Context,
        targetUrl: String,
        contentId: String?
    ): String? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String?>()
        var webView: WebView? = null
        val isDirectDownloadUrl = targetUrl.contains("/download/", ignoreCase = true)

        try {
            val wv = WebView(appContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }
            webView = wv

            val ua = wv.settings.userAgentString
            if (!ua.isNullOrBlank()) {
                PlayHeaders.userAgent = ua
            }

            runCatching {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            }

            wv.webChromeClient = WebChromeClient()
            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return proxyThroughDohIfNeeded(request)
                }
            }

            val safeCid = (contentId ?: "").replace("'", "")
            val presetTok = PlayHeaders.gofileToken.replace("'", "")
            val bootstrapHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <script>
                    window.__mbState = { done: false, token: '$presetTok', link: '', salt: '', wt: '', error: '' };
                    try {
                        if ('$presetTok') {
                            localStorage.setItem('accountToken', '$presetTok');
                        }
                        if (typeof TextEncoder !== 'undefined') {
                            var origEncode = TextEncoder.prototype.encode;
                            TextEncoder.prototype.encode = function(str) {
                                if (typeof str === 'string' && str.indexOf('::') !== -1) {
                                    var parts = str.split('::');
                                    if (parts.length >= 5) {
                                        window.__mbState.salt = parts[parts.length - 1];
                                    }
                                }
                                return origEncode.apply(this, arguments);
                            };
                        }
                    } catch (e) {}
                    window.appdata = window.appdata || {};
                    window.appdata.accountsManager = {
                        getAccountToken: function() { return localStorage.getItem('accountToken') || window.__mbState.token || ''; }
                    };
                    </script>
                    <script src="https://gofile.io/dist/js/config.js"></script>
                    <script src="https://gofile.io/dist/js/wt.obf.js"></script>
                </head>
                <body>
                <script>
                (async function() {
                    try {
                        var bl = navigator.language || 'en-US';
                        var tok = localStorage.getItem('accountToken') || window.__mbState.token || '';
                        if (!tok) {
                            var wt1 = (typeof generateWT === 'function') ? generateWT('') : '';
                            var h1 = { 'Content-Type': 'application/json', 'X-BL': bl };
                            if (wt1) h1['X-Website-Token'] = wt1;
                            var r1 = await fetch('https://api.gofile.io/accounts', { method: 'POST', headers: h1, body: '{}' });
                            var j1 = await r1.json();
                            if (j1 && j1.status === 'ok' && j1.data && j1.data.token) {
                                tok = j1.data.token;
                                localStorage.setItem('accountToken', tok);
                            }
                        }
                        if (tok) {
                            document.cookie = 'accountToken=' + tok + '; domain=.gofile.io; path=/; max-age=31536000; SameSite=Lax';
                            window.__mbState.token = tok;
                            var cid = '$safeCid';
                            if (cid) {
                                var wt2 = (typeof generateWT === 'function') ? generateWT(tok) : '';
                                window.__mbState.wt = wt2 || '';
                                var h2 = { 'Authorization': 'Bearer ' + tok, 'X-BL': bl };
                                if (wt2) h2['X-Website-Token'] = wt2;
                                var r2 = await fetch('https://api.gofile.io/contents/' + cid + '?contentFilter=&page=1&pageSize=1000&sortField=name&sortDirection=1', { headers: h2 });
                                var j2 = await r2.json();
                                if (j2 && j2.status === 'ok' && j2.data) {
                                    if (j2.data.link) {
                                        window.__mbState.link = j2.data.link;
                                    } else if (j2.data.children) {
                                        var keys = Object.keys(j2.data.children);
                                        for (var i = 0; i < keys.length; i++) {
                                            var ch = j2.data.children[keys[i]];
                                            if (ch && ch.link) {
                                                window.__mbState.link = ch.link;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e) {
                        window.__mbState.error = String(e);
                    } finally {
                        window.__mbState.done = true;
                    }
                })();
                </script>
                </body>
                </html>
            """.trimIndent()

            wv.loadDataWithBaseURL(
                "https://gofile.io/",
                bootstrapHtml,
                "text/html",
                "UTF-8",
                null
            )

            launch {
                for (step in 0 until 15) {
                    if (deferred.isCompleted) break
                    delay(220L)
                    wv.evaluateJavascript("(function(){return JSON.stringify(window.__mbState||{});})()") { rawJson ->
                        if (deferred.isCompleted) return@evaluateJavascript
                        runCatching {
                            val unwrapped = JSONTokener(rawJson).nextValue() as? String ?: return@runCatching
                            val obj = JSONObject(unwrapped)
                            val tok = obj.optString("token", "")
                            val link = obj.optString("link", "")
                            val salt = obj.optString("salt", "")
                            val done = obj.optBoolean("done", false)
                            if (salt.isNotBlank()) {
                                lastWorkingSalt = salt
                                scrapedWtObfSalt = salt
                                PlayHeaders.saveCachedGofileSalt(appContext, salt)
                            }
                            if (tok.isNotBlank()) {
                                PlayHeaders.saveGofileTokenCookie(tok, targetUrl, appContext)
                            }
                            if (link.isNotBlank()) {
                                PlayHeaders.saveGofileTokenCookie(tok, link, appContext)
                                deferred.complete(link)
                            } else if (done) {
                                deferred.complete(null)
                            }
                        }
                    }
                }
                if (!deferred.isCompleted) {
                    deferred.complete(null)
                }
            }

            val jsLink = withTimeoutOrNull(3500L) {
                deferred.await()
            }
            if (!jsLink.isNullOrBlank()) return@withContext jsLink

            // If we captured a new salt from wt.obf.js and have a contentId, run fetchGofileContentLinkViaApi on IO
            val resolvedLink = if (!contentId.isNullOrBlank() && PlayHeaders.gofileToken.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    fetchGofileContentLinkViaApi(appContext, contentId, PlayHeaders.gofileToken)
                }
            } else null

            resolvedLink ?: if (isDirectDownloadUrl) targetUrl else null
        } catch (_: Exception) {
            if (isDirectDownloadUrl) targetUrl else null
        } finally {
            runCatching {
                CookieManager.getInstance().flush()
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }

    // Stage 2 WebView: Full Invisible Browser Navigation with DoH Request Proxying.
    // For Gofile direct /download/web/<uuid>/... links, loads https://gofile.io/d/<uuid>
    // so Gofile's real web app HTML & JS load (instead of the empty "<html><head></head><body></body></html>"
    // returned by /download/web/ when unauthenticated), mints and authorizes accountToken,
    // and unlocks the direct .mkv stream.
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveViaInvisibleBrowserNavigation(
        appContext: Context,
        targetUrl: String,
        gofileContentId: String? = null
    ): ResolvedStream? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<ResolvedStream?>()
        var webView: WebView? = null
        val candidates = Collections.synchronizedSet(LinkedHashSet<String>())
        if (VideoExtractor.looksLikeMedia(targetUrl)) {
            candidates.add(targetUrl)
        }

        try {
            val wv = WebView(appContext).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                }
            }
            webView = wv

            val ua = wv.settings.userAgentString
            if (!ua.isNullOrBlank()) {
                PlayHeaders.userAgent = ua
            }

            runCatching {
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            }

            wv.setDownloadListener(DownloadListener { downloadUrl, userAgent, _, _, _ ->
                if (!downloadUrl.isNullOrBlank()) {
                    if (!userAgent.isNullOrBlank()) PlayHeaders.userAgent = userAgent
                    candidates.add(PlayHeaders.normalizeUrl(downloadUrl))
                }
            })

            val fetchHookJs = """
                (function() {
                    if (window.__mbFetchHooked) return;
                    window.__mbFetchHooked = true;
                    window.__mbCapturedLinks = window.__mbCapturedLinks || [];
                    window.__mbCapturedToken = window.__mbCapturedToken || '';
                    try {
                        var origFetch = window.fetch;
                        if (origFetch) {
                            window.fetch = async function() {
                                var resp = await origFetch.apply(this, arguments);
                                try {
                                    var u = String(arguments[0] || '');
                                    if (u.indexOf('api.gofile.io') !== -1) {
                                        var clone = resp.clone();
                                        clone.json().then(function(j) {
                                            if (j && j.status === 'ok' && j.data) {
                                                if (j.data.token) {
                                                    window.__mbCapturedToken = j.data.token;
                                                }
                                                if (j.data.link) {
                                                    window.__mbCapturedLinks.push(j.data.link);
                                                }
                                                if (j.data.children) {
                                                    Object.keys(j.data.children).forEach(function(k) {
                                                        var ch = j.data.children[k];
                                                        if (ch && ch.link) window.__mbCapturedLinks.push(ch.link);
                                                    });
                                                }
                                            }
                                        }).catch(function(){});
                                    }
                                } catch (e) {}
                                return resp;
                            };
                        }
                    } catch (e) {}
                })();
            """.trimIndent()

            wv.webChromeClient = WebChromeClient()
            wv.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val u = request?.url?.toString() ?: return null
                    if (AdBlock.isBlocked(u)) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    if (VideoExtractor.looksLikeMedia(u)) {
                        request.requestHeaders["Referer"]?.let { ref ->
                            if (ref.isNotBlank() && !VideoExtractor.looksLikeMedia(ref)) {
                                PlayHeaders.referer = ref
                            }
                        }
                        candidates.add(PlayHeaders.normalizeUrl(u))
                        return null
                    }
                    return proxyThroughDohIfNeeded(request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
                    if (scheme != "http" && scheme != "https" && scheme != "about" &&
                        scheme != "blob" && scheme != "data" && scheme != "javascript"
                    ) return true
                    if (AdBlock.isBlocked(uri.toString())) return true
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (url != null && !VideoExtractor.looksLikeMedia(url)) {
                        PlayHeaders.referer = url
                    }
                    view?.evaluateJavascript(fetchHookJs, null)
                    view?.evaluateJavascript(AdBlock.PAGE_JS, null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (view == null || deferred.isCompleted) return
                    view.evaluateJavascript(fetchHookJs, null)
                    view.evaluateJavascript(AdBlock.PAGE_JS, null)
                }
            }

            // CRITICAL: Loading a Gofile /download/web/<uuid>/... URL directly in WebView returns
            // "<html><head></head><body></body></html>" with zero scripts.
            // Loading "https://gofile.io/d/<gofileContentId>" loads Gofile's real web app!
            val initialLoadUrl = if (PlayHeaders.isGofileHost(targetUrl) && !gofileContentId.isNullOrBlank()) {
                "https://gofile.io/d/$gofileContentId"
            } else if (PlayHeaders.isGofileHost(targetUrl) && targetUrl.contains("/download/", ignoreCase = true)) {
                "https://gofile.io/"
            } else {
                targetUrl
            }
            wv.loadUrl(initialLoadUrl)

            val harvestJs = """
                (function() {
                    var out = { token: '', links: [], html: '' };
                    try {
                        var t = window.__mbCapturedToken || localStorage.getItem('accountToken') || '';
                        if (!t && window.appdata && window.appdata.accountsManager && typeof window.appdata.accountsManager.getAccountToken === 'function') {
                            t = window.appdata.accountsManager.getAccountToken() || '';
                        }
                        out.token = t;
                        if (window.__mbCapturedLinks && window.__mbCapturedLinks.length) {
                            for (var c = 0; c < window.__mbCapturedLinks.length; c++) {
                                out.links.push(window.__mbCapturedLinks[c]);
                            }
                        }
                        if (window.appdata && window.appdata.fileManager && window.appdata.fileManager.mainContent && window.appdata.fileManager.mainContent.data) {
                            var d = window.appdata.fileManager.mainContent.data;
                            if (d.link) out.links.push(d.link);
                            if (d.children) {
                                Object.keys(d.children).forEach(function(k) {
                                    var ch = d.children[k];
                                    if (ch) {
                                        ch.overloaded = false;
                                        if (ch.link) out.links.push(ch.link);
                                    }
                                });
                            }
                        }
                        var vids = document.querySelectorAll('video, source');
                        for (var i = 0; i < vids.length; i++) {
                            if (vids[i].src) out.links.push(vids[i].src);
                            if (vids[i].currentSrc) out.links.push(vids[i].currentSrc);
                            if (vids[i].tagName === 'VIDEO') {
                                vids[i].muted = true;
                                var p = vids[i].play();
                                if (p && p.catch) p.catch(function(){});
                            }
                        }
                        out.html = document.documentElement ? document.documentElement.outerHTML : '';
                    } catch (e) {}
                    return JSON.stringify(out);
                })();
            """.trimIndent()

            val pollerJob = launch {
                for (step in 0 until 14) {
                    if (deferred.isCompleted || !isActive) break
                    delay(500L)
                    runCatching { CookieManager.getInstance().flush() }

                    val pageUrl = wv.url.orEmpty()
                    if (pageUrl.isNotBlank() && !VideoExtractor.looksLikeMedia(pageUrl)) {
                        PlayHeaders.referer = pageUrl
                    }

                    val cookieStr = runCatching {
                        CookieManager.getInstance().getCookie("https://gofile.io").orEmpty()
                    }.getOrDefault("")
                    Regex("""accountToken=([^;\s]+)""").find(cookieStr)?.groupValues?.getOrNull(1)?.let { tok ->
                        if (tok.isNotBlank()) PlayHeaders.saveGofileTokenCookie(tok, targetUrl, appContext)
                    }

                    val jsSnapshot = CompletableDeferred<String>()
                    wv.evaluateJavascript(harvestJs) { raw ->
                        jsSnapshot.complete(raw.orEmpty())
                    }
                    val rawResult = withTimeoutOrNull(900L) { jsSnapshot.await() }.orEmpty()
                    runCatching {
                        val unwrapped = JSONTokener(rawResult).nextValue() as? String
                        if (!unwrapped.isNullOrBlank()) {
                            val obj = JSONObject(unwrapped)
                            val tok = obj.optString("token", "")
                            if (tok.isNotBlank()) {
                                PlayHeaders.saveGofileTokenCookie(tok, targetUrl, appContext)
                            }
                            val arr = obj.optJSONArray("links")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val l = arr.optString(i, "")
                                    if (l.isNotBlank()) {
                                        val norm = PlayHeaders.normalizeUrl(l)
                                        if (tok.isNotBlank()) PlayHeaders.saveGofileTokenCookie(tok, norm, appContext)
                                        candidates.add(norm)
                                    }
                                }
                            }
                            val html = obj.optString("html", "")
                            if (html.isNotBlank()) {
                                val base = pageUrl.ifBlank { targetUrl }
                                VideoExtractor.extract(html, base).forEach { found ->
                                    if (found.type != "IFRAME") {
                                        val norm = PlayHeaders.normalizeUrl(found.url)
                                        if (tok.isNotBlank()) PlayHeaders.saveGofileTokenCookie(tok, norm, appContext)
                                        candidates.add(norm)
                                    }
                                }
                            }
                        }
                    }

                    val snapshotList = synchronized(candidates) { candidates.toList().reversed() }
                    val verified = withContext(Dispatchers.IO) {
                        for (cand in snapshotList) {
                            if (PlayHeaders.isGofileHost(cand)) {
                                probeGofileWithReferers(cand)?.let { playable ->
                                    return@withContext ResolvedStream(playable.url, playable.mime, true)
                                }
                            } else {
                                val p = LinkProbe.probe(
                                    url = cand,
                                    referer = PlayHeaders.refererFor(cand),
                                    ua = PlayHeaders.userAgent
                                )
                                if (p is ProbeResult.Playable) {
                                    p.mime?.let { PlayHeaders.mimeTypes[p.url] = it }
                                    return@withContext ResolvedStream(p.url, p.mime, true)
                                }
                            }
                        }
                        null
                    }

                    if (verified != null && !deferred.isCompleted) {
                        deferred.complete(verified)
                        break
                    }
                }
                if (!deferred.isCompleted) {
                    deferred.complete(null)
                }
            }

            val result = withTimeoutOrNull(7500L) {
                deferred.await()
            }
            pollerJob.cancel()
            result
        } catch (_: Exception) {
            null
        } finally {
            runCatching {
                CookieManager.getInstance().flush()
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }
}
