package com.moviebox.app.finder

import android.webkit.CookieManager
import androidx.media3.common.MimeTypes
import com.moviebox.app.net.Net
import com.moviebox.app.player.PlayHeaders
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream

sealed interface ProbeResult {
    // A real video or audio stream. [mime] is set for HLS / DASH so ExoPlayer does not guess from the file name.
    data class Playable(val url: String, val mime: String?) : ProbeResult

    // The server answered with a web page (expired link, ad page, Gofile redirect, hot-link protection).
    data class WebPage(val code: Int) : ProbeResult

    // The server refused the request (401, 403, 404, etc.).
    data class HttpError(val code: Int) : ProbeResult

    // Could not check (network error).
    data object Unknown : ProbeResult
}

// Inspects the first bytes of a candidate URL before playback starts so ExoPlayer is only
// fed genuine binary media streams (preventing ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED).
object LinkProbe {

    fun cookieFor(url: String): String {
        val combined = PlayHeaders.cookieHeaderFor(url)
        if (combined.isNotEmpty()) return combined
        return try {
            CookieManager.getInstance().getCookie(url).orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    // Blocking call: run from Dispatchers.IO.
    fun probe(
        url: String,
        referer: String = "",
        ua: String = Fetcher.UA,
        depth: Int = 0
    ): ProbeResult {
        return try {
            val safeUrl = PlayHeaders.normalizeUrl(url)
            val effectiveUa = ua.ifBlank { PlayHeaders.userAgent.ifBlank { Fetcher.UA } }
            val effectiveReferer = referer.ifBlank { PlayHeaders.refererFor(safeUrl) }

            val rb = Request.Builder()
                .url(safeUrl)
                .header("User-Agent", effectiveUa)
                .header("Accept", "*/*")
                .header("Range", "bytes=0-4095")
            if (effectiveReferer.isNotEmpty()) {
                rb.header("Referer", effectiveReferer)
            }
            val cookie = cookieFor(safeUrl)
            if (cookie.isNotEmpty()) {
                rb.header("Cookie", cookie)
            }

            Net.client.newCall(rb.build()).execute().use { resp ->
                val code = resp.code
                val finalUrl = resp.request.url.toString()
                if (code >= 400) return ProbeResult.HttpError(code)

                // If Gofile redirected a /download/web/... link to https://gofile.io/d/... or similar HTML page
                val finalLower = finalUrl.lowercase()
                if (finalLower.contains("gofile.io/d/") || finalLower.endsWith("gofile.io/")) {
                    return ProbeResult.WebPage(code)
                }

                val ctype = resp.header("Content-Type").orEmpty().lowercase()
                val head = readBytes(resp, 4096)
                val text = String(head, Charsets.ISO_8859_1).trimStart('\uFEFF', ' ', '\n', '\r', '\t')

                when {
                    text.startsWith("#EXTM3U") || ctype.contains("mpegurl") ->
                        ProbeResult.Playable(finalUrl, MimeTypes.APPLICATION_M3U8)
                    ctype.contains("dash+xml") || text.contains("<MPD", ignoreCase = true) ->
                        ProbeResult.Playable(finalUrl, MimeTypes.APPLICATION_MPD)
                    ctype.contains("html") || text.startsWith("<") || ctype.startsWith("text/") || ctype.contains("json") || text.startsWith("{") ->
                        resolveFromPage(safeUrl, effectiveReferer, effectiveUa, depth, code)
                    isMedia(head, ctype, finalUrl) ->
                        ProbeResult.Playable(finalUrl, null)
                    else ->
                        ProbeResult.WebPage(code)
                }
            }
        } catch (e: Exception) {
            ProbeResult.Unknown
        }
    }

    // The link returned an HTML page: inspect it one level deep for an embedded direct stream URL.
    private fun resolveFromPage(
        url: String,
        referer: String,
        ua: String,
        depth: Int,
        code: Int
    ): ProbeResult {
        if (depth >= 1) return ProbeResult.WebPage(code)
        return try {
            val rb = Request.Builder().url(url).header("User-Agent", ua)
            if (referer.isNotEmpty()) rb.header("Referer", referer)
            val cookie = cookieFor(url)
            if (cookie.isNotEmpty()) rb.header("Cookie", cookie)
            Net.client.newCall(rb.build()).execute().use { resp ->
                val html = String(readBytes(resp, 512 * 1024), Charsets.UTF_8)
                val base = resp.request.url.toString()
                val candidate = VideoExtractor.extract(html, base)
                    .firstOrNull { it.type != "IFRAME" && it.url != url }
                if (candidate != null) {
                    val inner = probe(candidate.url, url, ua, depth + 1)
                    if (inner is ProbeResult.Playable) return inner
                }
                ProbeResult.WebPage(code)
            }
        } catch (e: Exception) {
            ProbeResult.WebPage(code)
        }
    }

    private fun readBytes(resp: Response, max: Int): ByteArray {
        val s = resp.body?.byteStream() ?: return ByteArray(0)
        val out = ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        while (out.size() < max) {
            val n = s.read(tmp, 0, minOf(tmp.size, max - out.size()))
            if (n < 0) break
            out.write(tmp, 0, n)
        }
        return out.toByteArray()
    }

    private fun isMedia(h: ByteArray, ctype: String, finalUrl: String): Boolean {
        if (h.size < 4) return false

        val t = String(h, 0, minOf(h.size, 128), Charsets.ISO_8859_1).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (t.startsWith("<") || t.startsWith("{") ||
            t.startsWith("Error", ignoreCase = true) ||
            t.startsWith("Access Denied", ignoreCase = true) ||
            t.startsWith("404", ignoreCase = true) ||
            t.startsWith("403", ignoreCase = true)
        ) {
            return false
        }

        // 1. Positive binary container magic-byte signatures:
        // - MKV / WebM EBML header: 0x1A 0x45 0xDF 0xA3
        if (h[0] == 0x1A.toByte() && h[1] == 0x45.toByte() &&
            h[2] == 0xDF.toByte() && h[3] == 0xA3.toByte()
        ) {
            return true
        }

        // - MP4 / MOV / M4V ISO BMFF box signature at bytes 4..7
        if (h.size >= 8) {
            val box = String(h, 4, 4, Charsets.ISO_8859_1)
            if (box == "ftyp" || box == "moov" || box == "mdat" || box == "wide" || box == "free" || box == "styp") {
                return true
            }
        }

        // - Ogg, RIFF (AVI/WAV), FLV, ID3
        val s4 = String(h, 0, 4, Charsets.ISO_8859_1)
        if (s4 == "OggS" || s4 == "RIFF" || s4.startsWith("FLV") || s4.startsWith("ID3")) {
            return true
        }

        // - MPEG-TS sync byte 0x47 at 0 and 188
        if (h[0] == 0x47.toByte() && h.size > 188 && h[188] == 0x47.toByte()) {
            return true
        }

        // If the URL path explicitly ends with .mkv or .webm, it MUST start with the EBML header (0x1A 0x45 0xDF 0xA3)
        // Checked above; if it did not match EBML magic bytes, reject it so ExoPlayer's MatroskaExtractor never crashes!
        val pathLower = finalUrl.substringBefore('?').substringBefore('#').lowercase()
        if (pathLower.endsWith(".mkv") || pathLower.endsWith(".webm")) {
            return false
        }

        // 2. Explicit video/audio MIME types for other formats
        if (ctype.startsWith("video/") || ctype.startsWith("audio/") ||
            ctype.contains("matroska") || ctype.contains("mp4") || ctype.contains("webm") ||
            ctype.contains("octet-stream")
        ) {
            return h.size >= 64
        }

        return false
    }
}
