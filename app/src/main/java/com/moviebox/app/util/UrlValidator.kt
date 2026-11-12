package com.moviebox.app.util

import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Season
import com.moviebox.app.player.PlayHeaders
import java.net.URI
import java.util.Locale

enum class StreamFormat(val label: String) {
    HLS("HLS (.m3u8)"),
    DASH("DASH (.mpd)"),
    PROGRESSIVE("MP4 / WebM / MKV"),
    DIRECT_HTTP("Authorized HTTP/HTTPS Stream"),
    UNSUPPORTED("Unsupported")
}

data class StreamUrlValidation(
    val isValid: Boolean,
    val format: StreamFormat = StreamFormat.UNSUPPORTED,
    val errorMessage: String? = null
)

object UrlValidator {

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtu.be"
    )

    /**
     * Validates whether a URL can be played by ExoPlayer as an authorized direct video stream.
     * Normalizes spaces and special characters so Gofile / CDN links with spaces or parentheses
     * in the filename validate and play directly.
     * Explicitly rejects YouTube watch links so trailer URLs are never mistaken for full streams.
     */
    fun validateStreamUrl(rawUrl: String): StreamUrlValidation {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return StreamUrlValidation(false, StreamFormat.UNSUPPORTED, "No video stream URL configured.")
        }
        if (trimmed.startsWith("moviebox-offline://") ||
            trimmed.startsWith("file://") ||
            trimmed.startsWith("content://")
        ) {
            return StreamUrlValidation(true, StreamFormat.PROGRESSIVE, null)
        }
        val normalized = if (trimmed.contains("://")) {
            PlayHeaders.normalizeUrl(trimmed)
        } else {
            trimmed
        }
        val uri = try {
            URI(normalized)
        } catch (e: Exception) {
            return StreamUrlValidation(false, StreamFormat.UNSUPPORTED, "Malformed URL syntax.")
        }
        val scheme = uri.scheme?.lowercase(Locale.US) ?: ""
        if (scheme != "https" && scheme != "http") {
            return StreamUrlValidation(
                false,
                StreamFormat.UNSUPPORTED,
                "Only valid HTTP/HTTPS authorized stream URLs are supported."
            )
        }
        val host = uri.host?.lowercase(Locale.US) ?: ""
        if (host.isBlank()) {
            return StreamUrlValidation(false, StreamFormat.UNSUPPORTED, "URL is missing a valid host.")
        }
        if (host in YOUTUBE_HOSTS) {
            return StreamUrlValidation(
                false,
                StreamFormat.UNSUPPORTED,
                "YouTube links are web pages/trailers, not direct ExoPlayer streams. Use the Trailer field for YouTube links and provide a direct MP4/HLS/DASH URL for playback."
            )
        }
        val path = (uri.path ?: "").lowercase(Locale.US)
        val format = when {
            path.endsWith(".m3u8") -> StreamFormat.HLS
            path.endsWith(".mpd") -> StreamFormat.DASH
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mkv") ||
                path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".3gp") ||
                path.endsWith(".flv") -> StreamFormat.PROGRESSIVE
            else -> StreamFormat.DIRECT_HTTP
        }
        return StreamUrlValidation(true, format, null)
    }

    /** Validates trailer URLs (supports YouTube links or direct MP4/HLS trailer streams). */
    fun validateTrailerUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val normalized = if (trimmed.contains("://")) PlayHeaders.normalizeUrl(trimmed) else trimmed
            val uri = URI(normalized)
            val scheme = uri.scheme?.lowercase(Locale.US)
            (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    /** Validates HTTP/HTTPS image URLs (posters, backdrops, avatars). */
    fun validateImageUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return true // optional
        return try {
            val normalized = if (trimmed.contains("://")) PlayHeaders.normalizeUrl(trimmed) else trimmed
            val uri = URI(normalized)
            val scheme = uri.scheme?.lowercase(Locale.US)
            (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun isHttpOrHttpsUrl(raw: String): Boolean {
        val s = raw.trim().lowercase(Locale.US)
        return s.startsWith("https://") || (s.startsWith("http") && s.length > 7 && s[4] == ':' && s[5] == '/')
    }

    fun buildYouTubeUrl(videoKey: String): String {
        val clean = videoKey.trim()
        return if (isHttpOrHttpsUrl(clean)) clean
        else "https://www.youtube.com/watch?v=$clean"
    }

    fun normalizeTitle(title: String): String =
        title.trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /**
     * Ensures seasons and episodes have positive, strictly unique numbers within each series.
     * Returns null if valid, or a descriptive error message if invalid.
     */
    fun validateSeasonEpisodeOrdering(seasons: List<Season>): String? {
        val seenSeasons = HashSet<Int>()
        for (season in seasons) {
            if (season.seasonNumber <= 0) {
                return "Season number must be >= 1 (found ${season.seasonNumber})."
            }
            if (!seenSeasons.add(season.seasonNumber)) {
                return "Duplicate Season ${season.seasonNumber} found. Season numbers must be unique."
            }
            val seenEpisodes = HashSet<Int>()
            for (ep in season.episodes) {
                if (ep.episodeNumber <= 0) {
                    return "Episode number in Season ${season.seasonNumber} must be >= 1."
                }
                if (!seenEpisodes.add(ep.episodeNumber)) {
                    return "Duplicate Episode ${ep.episodeNumber} in Season ${season.seasonNumber}."
                }
                if (ep.title.isBlank()) {
                    return "Episode S${season.seasonNumber}E${ep.episodeNumber} is missing a title."
                }
                if (ep.videoUrl.isNotBlank()) {
                    val v = validateStreamUrl(ep.videoUrl)
                    if (!v.isValid) {
                        return "S${season.seasonNumber}E${ep.episodeNumber}: ${v.errorMessage}"
                    }
                }
            }
        }
        return null
    }

    /** Filters and sorts catalog items for user browsing and search. */
    fun filterCatalog(
        items: List<CatalogItem>,
        query: String = "",
        categoryId: String? = null,
        genre: String? = null,
        minRating: Double = 0.0,
        releaseYear: Int? = null,
        sortBy: String = "NEWEST"
    ): List<CatalogItem> {
        val q = normalizeTitle(query)
        val rawQ = query.trim().lowercase(Locale.US)

        val filtered = items.filter { item ->
            if (!item.published) return@filter false
            if (categoryId != null && categoryId != "ALL" && categoryId !in item.categoryIds) {
                return@filter false
            }
            if (!genre.isNullOrBlank() && genre != "All" &&
                item.genres.none { it.equals(genre, ignoreCase = true) }
            ) {
                return@filter false
            }
            if (item.rating < minRating) return@filter false
            if (releaseYear != null && item.releaseYear != releaseYear) return@filter false

            if (q.isNotEmpty()) {
                val matchTitle = item.normalizedTitle.contains(q) || item.title.lowercase(Locale.US).contains(rawQ)
                val matchGenre = item.genres.any { it.lowercase(Locale.US).contains(rawQ) }
                val matchCast = item.cast.any {
                    it.name.lowercase(Locale.US).contains(rawQ) ||
                        it.character.lowercase(Locale.US).contains(rawQ)
                }
                matchTitle || matchGenre || matchCast
            } else {
                true
            }
        }

        return when (sortBy) {
            "RATING" -> filtered.sortedByDescending { it.rating }
            "TITLE" -> filtered.sortedBy { it.title.lowercase(Locale.US) }
            "OLDEST" -> filtered.sortedBy { it.releaseYear }
            else -> filtered.sortedWith(compareByDescending<CatalogItem> { it.releaseYear }.thenByDescending { it.updatedAt })
        }
    }
}
