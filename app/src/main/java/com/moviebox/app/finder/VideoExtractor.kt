package com.moviebox.app.finder

import java.net.URL

data class FoundLink(val url: String, val type: String)

/**
 * Video link extractor ported from VideoLinkFinder v2.2.
 * Extracts direct .mp4, .m3u8, .mpd, .webm, .mov, .mkv, .flv, .m4v, .3gp links and iframe embeds.
 */
object VideoExtractor {
    private const val EXT = "mp4|m3u8|mpd|webm|mov|mkv|flv|m4v|3gp"

    private val absRegex = Regex(
        """https?://[^\s"'<>\\]+?\.(?:$EXT)(?![A-Za-z0-9_])(?:\?[^\s"'<>\\]*)?""",
        RegexOption.IGNORE_CASE
    )

    private val gofileDownloadRegex = Regex(
        """https?://[a-zA-Z0-9.-]*gofile\.io/download/[^\s"'<>\\]+""",
        RegexOption.IGNORE_CASE
    )

    private val relRegex = Regex(
        """["']?(?:src|data-src|file|source|url|video_url|downloadLink|link|contentUrl|hlsUrl|mp4)["']?\s*[=:]\s*["']([^"'\s<>]+\.(?:$EXT)(?:[?#][^"'\s<>]*)?)["']""",
        RegexOption.IGNORE_CASE
    )

    private val iframeRegex = Regex(
        """<iframe[^>]+?src\s*=\s*["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    private val mediaPathRegex = Regex(
        """\.($EXT)(?:/|$)""",
        RegexOption.IGNORE_CASE
    )

    fun looksLikeMedia(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("gofile.io/download/")) return true
        val path = url.substringBefore('?').substringBefore('#')
        return mediaPathRegex.containsMatchIn(path)
    }

    fun typeOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.contains(".m3u8") -> "HLS (m3u8)"
            path.contains(".mpd") -> "DASH (mpd)"
            path.contains(".mkv") -> "MKV"
            path.contains(".webm") -> "WEBM"
            path.contains(".mov") -> "MOV"
            else -> mediaPathRegex.find(path)?.groupValues?.get(1)?.uppercase() ?: "VIDEO"
        }
    }

    private fun resolve(base: String, s: String): String? {
        return try {
            val r = URL(URL(base), s.trim()).toString()
            if (r.startsWith("http://") || r.startsWith("https://")) r else null
        } catch (e: Exception) {
            null
        }
    }

    fun extract(raw: String, base: String): List<FoundLink> {
        val text = raw
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("&amp;", "&")

        val out = LinkedHashMap<String, FoundLink>()

        fun add(u: String, type: String? = null) {
            val clean = u.trim().trimEnd(',', ';', ')', '\\')
            if (clean.isEmpty() || clean.startsWith("data:") || clean.startsWith("blob:")) return
            if (!out.containsKey(clean)) out[clean] = FoundLink(clean, type ?: typeOf(clean))
        }

        gofileDownloadRegex.findAll(text).forEach { add(it.value) }
        absRegex.findAll(text).forEach { add(it.value) }

        relRegex.findAll(text).forEach { m ->
            val resolved = resolve(base, m.groupValues[1])
            if (resolved != null) add(resolved)
        }

        iframeRegex.findAll(text).forEach { m ->
            val resolved = resolve(base, m.groupValues[1])
            if (resolved != null && !looksLikeMedia(resolved)) add(resolved, "IFRAME")
        }

        return out.values.toList()
    }
}
