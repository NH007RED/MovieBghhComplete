package com.moviebox.app.finder

import com.moviebox.app.net.Net
import com.moviebox.app.player.PlayHeaders
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException

data class FetchResult(
    val finalUrl: String,
    val code: Int,
    val body: String,
    val bytes: Int,
    val truncated: Boolean
)

/**
 * Page source fetcher ported from VideoLinkFinder v2.2.
 */
object Fetcher {
    private const val MAX_BYTES = 5 * 1024 * 1024
    const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Throws(IOException::class)
    fun fetch(start: String, referer: String? = null): FetchResult {
        val safeUrl = PlayHeaders.normalizeUrl(start)
        val rb = Request.Builder()
            .url(safeUrl)
            .header("User-Agent", PlayHeaders.userAgent.ifBlank { UA })
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")

        val effectiveRef = referer ?: PlayHeaders.refererFor(safeUrl)
        if (effectiveRef.isNotEmpty()) rb.header("Referer", effectiveRef)

        val cookie = PlayHeaders.cookieHeaderFor(safeUrl)
        if (cookie.isNotEmpty()) rb.header("Cookie", cookie)

        Net.client.newCall(rb.build()).execute().use { resp ->
            val body = resp.body
            val buf = ByteArrayOutputStream()
            var truncated = false
            if (body != null) {
                body.byteStream().use { s ->
                    val tmp = ByteArray(16384)
                    while (true) {
                        val n = s.read(tmp)
                        if (n < 0) break
                        buf.write(tmp, 0, n)
                        if (buf.size() >= MAX_BYTES) {
                            truncated = true
                            break
                        }
                    }
                }
            }
            val cs = body?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            return FetchResult(
                resp.request.url.toString(),
                resp.code,
                String(buf.toByteArray(), cs),
                buf.size(),
                truncated
            )
        }
    }
}
