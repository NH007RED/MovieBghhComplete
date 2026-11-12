package com.moviebox.app.player

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import com.moviebox.app.finder.Fetcher
import com.moviebox.app.net.Net
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// Shared HTTP Playback Headers, Cookies & Gofile Token Manager
// Ported from VideoLinkFinder v2.2 (PlayHeaders) so ExoPlayer sends the exact same
// User-Agent, Referer, and Cookie (accountToken=...) headers as VideoLinkFinder.
object PlayHeaders {
    private const val PREFS_NAME = "moviebox_stream_headers"
    private const val KEY_GOFILE_TOKEN = "gofile_account_token"
    private const val KEY_GOFILE_SALT = "gofile_wt_salt"

    @Volatile
    var referer: String = ""

    @Volatile
    var userAgent: String = Fetcher.UA

    @Volatile
    var gofileToken: String = ""

    @Volatile
    var cachedGofileSalt: String = ""

    // Stream MIME types detected by LinkProbe (for HLS / DASH streams without extensions), keyed by URL.
    val mimeTypes = ConcurrentHashMap<String, String>()

    // Per-URL verified Referer header (records the exact Referer that passed LinkProbe.probe).
    val customRefererByUrl = ConcurrentHashMap<String, String>()

    // Custom per-host or per-URL cookies resolved in the background.
    val extraCookies = ConcurrentHashMap<String, String>()

    fun initUserAgentIfNeeded(context: Context) {
        val appCtx = context.applicationContext
        if (userAgent == Fetcher.UA) {
            runCatching {
                val defaultUa = WebSettings.getDefaultUserAgent(appCtx)
                if (!defaultUa.isNullOrBlank()) {
                    userAgent = defaultUa
                }
            }
        }
        if (gofileToken.isBlank()) {
            runCatching {
                val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedToken = prefs.getString(KEY_GOFILE_TOKEN, "").orEmpty()
                val savedSalt = prefs.getString(KEY_GOFILE_SALT, "").orEmpty()
                if (savedSalt.isNotBlank()) {
                    cachedGofileSalt = savedSalt
                }
                if (savedToken.isNotBlank()) {
                    saveGofileTokenCookie(savedToken, "", appCtx)
                }
            }
        }
    }

    fun saveCachedGofileSalt(context: Context, salt: String) {
        if (salt.isBlank()) return
        cachedGofileSalt = salt
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_GOFILE_SALT, salt)
                .apply()
        }
    }

    fun isGofileHost(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("gofile.io")
    }

    fun isGofileApiHost(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("api.gofile.io")
    }

    // Safely encodes raw spaces and brackets in URLs without altering already-encoded %20 or valid characters.
    fun normalizeUrl(rawUrl: String): String {
        var s = rawUrl.trim()
        if (s.isEmpty()) return s
        if (s.startsWith("view-source:")) s = s.removePrefix("view-source:").trim()
        if (!s.contains("://") && !s.startsWith("moviebox-offline://") && !s.startsWith("file://") && !s.startsWith("content://")) {
            s = "https://$s"
        }
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when (ch) {
                ' ' -> sb.append("%20")
                '[' -> sb.append("%5B")
                ']' -> sb.append("%5D")
                '{' -> sb.append("%7B")
                '}' -> sb.append("%7D")
                '|' -> sb.append("%7C")
                '^' -> sb.append("%5E")
                '`' -> sb.append("%60")
                '"' -> sb.append("%22")
                '<' -> sb.append("%3C")
                '>' -> sb.append("%3E")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun refererFor(url: String): String {
        customRefererByUrl[url]?.let { return it }
        if (referer.isNotBlank()) return referer
        if (isGofileHost(url)) return "https://gofile.io/"
        return runCatching {
            val u = URI(normalizeUrl(url))
            if (!u.scheme.isNullOrBlank() && !u.host.isNullOrBlank()) {
                "${u.scheme}://${u.host}/"
            } else ""
        }.getOrDefault("")
    }

    fun cookieHeaderFor(url: String): String {
        val parts = LinkedHashMap<String, String>()

        fun parseAndPut(rawCookie: String?) {
            if (rawCookie.isNullOrBlank()) return
            rawCookie.split(";").forEach { pair ->
                val trimmed = pair.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val k = trimmed.substring(0, eq).trim()
                    val v = trimmed.substring(eq + 1).trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) {
                        parts[k] = v
                    }
                }
            }
        }

        // 1. WebView CookieManager cookies for exact URL (exact match to VideoLinkFinder v2.2)
        runCatching {
            parseAndPut(CookieManager.getInstance().getCookie(url))
        }

        // 2. If Gofile host, also include cookies from https://gofile.io and our active gofileToken
        if (isGofileHost(url)) {
            runCatching {
                parseAndPut(CookieManager.getInstance().getCookie("https://gofile.io"))
                parseAndPut(CookieManager.getInstance().getCookie("https://gofile.io/"))
            }
            if (gofileToken.isNotBlank()) {
                parts["accountToken"] = gofileToken
            }
        }

        // 3. Any extra cookies stored for this URL or host
        extraCookies[url]?.let { parseAndPut(it) }
        runCatching {
            val host = Uri.parse(url).host?.lowercase(Locale.US)
            if (!host.isNullOrBlank()) {
                extraCookies[host]?.let { parseAndPut(it) }
            }
        }

        return parts.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    // Builds the HTTP headers for ExoPlayer & OfflineDownloadManager stream requests.
    // Matches VideoLinkFinder v2.2's ResolvingDataSource: sends User-Agent, Referer, and Cookie.
    fun buildHeadersForUrl(url: String): Map<String, String> {
        val extra = HashMap<String, String>()
        extra["User-Agent"] = userAgent.ifBlank { Fetcher.UA }
        val ref = refererFor(url)
        if (ref.isNotEmpty()) extra["Referer"] = ref
        val cookie = cookieHeaderFor(url)
        if (cookie.isNotEmpty()) extra["Cookie"] = cookie
        if (isGofileApiHost(url) && gofileToken.isNotBlank()) {
            extra["Authorization"] = "Bearer $gofileToken"
            extra["Origin"] = "https://gofile.io"
        }
        return extra
    }

    fun clearGofileToken(context: Context? = null) {
        gofileToken = ""
        extraCookies.remove("gofile.io")
        context?.applicationContext?.let { appCtx ->
            runCatching {
                appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_GOFILE_TOKEN)
                    .apply()
            }
        }
    }

    fun saveGofileTokenCookie(token: String, targetUrl: String = "", context: Context? = null) {
        if (token.isBlank()) return
        gofileToken = token
        extraCookies["gofile.io"] = "accountToken=$token"
        if (targetUrl.isNotBlank()) {
            val norm = normalizeUrl(targetUrl)
            extraCookies[targetUrl] = "accountToken=$token"
            extraCookies[norm] = "accountToken=$token"
            runCatching {
                val host = Uri.parse(norm).host?.lowercase(Locale.US)
                if (!host.isNullOrBlank()) {
                    extraCookies[host] = "accountToken=$token"
                }
            }
        }
        context?.applicationContext?.let { appCtx ->
            runCatching {
                appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_GOFILE_TOKEN, token)
                    .apply()
            }
        }
        runCatching {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setCookie("https://gofile.io", "accountToken=$token; Domain=.gofile.io; Path=/")
            cm.setCookie("https://gofile.io/", "accountToken=$token; Domain=.gofile.io; Path=/")
            if (targetUrl.isNotBlank()) {
                val norm = normalizeUrl(targetUrl)
                cm.setCookie(norm, "accountToken=$token; Domain=.gofile.io; Path=/")
                cm.setCookie(norm, "accountToken=$token; Path=/")
            }
            cm.flush()
        }
    }
}

// Native ExoPlayer builder ported from VideoLinkFinder v2.2:
// Uses OkHttpDataSource.Factory(Net.client) + ResolvingDataSource.Factory injecting
// User-Agent, Referer, and CookieManager cookies, plus Jellyfin FFmpeg audio decoder fallback.
@UnstableApi
object PlayerHolder {
    val audioDelay = AudioDelayProcessor()

    val SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val SPEED_LABELS = listOf("0.25x", "0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
    val ASPECT_LABELS = listOf("Fit", "Stretch", "Crop (Zoom)", "16:9", "4:3")
    val SUBTITLE_COLORS = intArrayOf(
        android.graphics.Color.WHITE,
        android.graphics.Color.YELLOW,
        android.graphics.Color.CYAN,
        android.graphics.Color.rgb(255, 165, 0)
    )
    val SUBTITLE_COLOR_NAMES = listOf("White", "Yellow", "Cyan", "Orange")

    fun createPlayer(context: Context): ExoPlayer {
        val app = context.applicationContext
        PlayHeaders.initUserAgentIfNeeded(app)

        val factory = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(audioDelay))
                .build()
        }
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val http = OkHttpDataSource.Factory(Net.client)
            .setUserAgent(PlayHeaders.userAgent.ifBlank { Fetcher.UA })

        val upstream = ResolvingDataSource.Factory(DefaultDataSource.Factory(app, http)) { spec ->
            val web = spec.uri.scheme == "http" || spec.uri.scheme == "https"
            if (!web) {
                spec
            } else {
                val urlStr = spec.uri.toString()
                val extra = PlayHeaders.buildHeadersForUrl(urlStr)
                if (extra.isEmpty()) spec else spec.withAdditionalHeaders(extra)
            }
        }

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)

        val player = ExoPlayer.Builder(app, factory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(upstream, extractorsFactory))
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        (player.trackSelector as? DefaultTrackSelector)?.let { ts ->
            ts.setParameters(
                ts.buildUponParameters()
                    .setConstrainAudioChannelCountToDeviceCapabilities(false)
            )
        }
        return player
    }

    fun trackLabel(f: Format, n: Int, kind: String): String {
        val lang = f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage }
        val base = f.label ?: lang ?: "$kind $n"
        val extra = if (f.channelCount > 0) {
            when (f.channelCount) {
                1 -> " • Mono"
                2 -> " • Stereo"
                6 -> " • 5.1"
                8 -> " • 7.1"
                else -> " • ${f.channelCount}ch"
            }
        } else ""
        return base + extra
    }

    fun codecName(mime: String?): String = when (mime) {
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "AC-3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        null -> ""
        else -> mime.substringAfter('/').uppercase(Locale.US)
    }
}
