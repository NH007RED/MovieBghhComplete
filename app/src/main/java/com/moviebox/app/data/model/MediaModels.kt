package com.moviebox.app.data.model

import com.google.firebase.database.DataSnapshot
import kotlin.math.abs

enum class MediaType {
    MOVIE,
    SERIES
}

enum class CategoryType(val label: String) {
    MOVIE("Movie"),
    SERIES("Web Series"),
    BOTH("Both")
}

enum class ThemeVariant(val label: String) {
    DEEP_RED("DeepRed"),
    DEEP_BLACK("DeepBlack")
}

data class Category(
    val id: String = "",
    val name: String = "",
    val type: String = CategoryType.BOTH.name,
    val active: Boolean = true,
    val order: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "type" to type,
        "active" to active,
        "order" to order,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): Category? {
            val id = snap.child("id").getValue(String::class.java) ?: snap.key ?: return null
            val name = snap.child("name").getValue(String::class.java) ?: return null
            val type = snap.child("type").getValue(String::class.java) ?: CategoryType.BOTH.name
            val active = snap.child("active").getValue(Boolean::class.java) ?: true
            val order = snap.child("order").getValue(Int::class.java) ?: 0
            val createdAt = snap.child("createdAt").getValue(Long::class.java) ?: 0L
            val updatedAt = snap.child("updatedAt").getValue(Long::class.java) ?: 0L
            return Category(id, name, type, active, order, createdAt, updatedAt)
        }
    }
}

data class CastMember(
    val id: String = "",
    val name: String = "",
    val character: String = "",
    val profileUrl: String = ""
) {
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "character" to character,
        "profileUrl" to profileUrl
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): CastMember? {
            val name = snap.child("name").getValue(String::class.java) ?: return null
            return CastMember(
                id = snap.child("id").getValue(String::class.java) ?: name,
                name = name,
                character = snap.child("character").getValue(String::class.java) ?: "",
                profileUrl = snap.child("profileUrl").getValue(String::class.java) ?: ""
            )
        }
    }
}

data class QualityStreamOption(
    val label: String,
    val badge: String,
    val url: String
)

data class Episode(
    val id: String = "",
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val title: String = "",
    val overview: String = "",
    val thumbnailUrl: String = "",
    val videoUrl: String = "",
    val videoUrl1080p: String = "",
    val videoUrl720p: String = "",
    val videoUrl480p: String = "",
    val runtimeMinutes: Int = 0,
    val airDate: String = ""
) {
    val primaryStreamUrl: String
        get() = videoUrl1080p.ifBlank { videoUrl720p.ifBlank { videoUrl480p.ifBlank { videoUrl } } }

    fun availableQualityOptions(): List<QualityStreamOption> {
        val list = mutableListOf<QualityStreamOption>()
        val u1080 = videoUrl1080p.trim().ifBlank { videoUrl.trim() }
        val u720 = videoUrl720p.trim()
        val u480 = videoUrl480p.trim()
        if (u1080.isNotBlank()) list.add(QualityStreamOption("1080p Full HD", "1080p", u1080))
        if (u720.isNotBlank()) list.add(QualityStreamOption("720p HD", "720p", u720))
        if (u480.isNotBlank()) list.add(QualityStreamOption("480p SD", "480p", u480))
        return list
    }

    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "seasonNumber" to seasonNumber,
        "episodeNumber" to episodeNumber,
        "title" to title,
        "overview" to overview,
        "thumbnailUrl" to thumbnailUrl,
        "videoUrl" to primaryStreamUrl,
        "videoUrl1080p" to videoUrl1080p.ifBlank { videoUrl },
        "videoUrl720p" to videoUrl720p,
        "videoUrl480p" to videoUrl480p,
        "runtimeMinutes" to runtimeMinutes,
        "airDate" to airDate
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): Episode? {
            val title = snap.child("title").getValue(String::class.java) ?: return null
            val sNum = snap.child("seasonNumber").getValue(Int::class.java) ?: 1
            val eNum = snap.child("episodeNumber").getValue(Int::class.java) ?: 1
            val legacyUrl = snap.child("videoUrl").getValue(String::class.java) ?: ""
            val url1080 = snap.child("videoUrl1080p").getValue(String::class.java) ?: legacyUrl
            val url720 = snap.child("videoUrl720p").getValue(String::class.java) ?: ""
            val url480 = snap.child("videoUrl480p").getValue(String::class.java) ?: ""
            val primary = url1080.ifBlank { url720.ifBlank { url480.ifBlank { legacyUrl } } }
            return Episode(
                id = snap.child("id").getValue(String::class.java) ?: "s${sNum}_e${eNum}",
                seasonNumber = sNum,
                episodeNumber = eNum,
                title = title,
                overview = snap.child("overview").getValue(String::class.java) ?: "",
                thumbnailUrl = snap.child("thumbnailUrl").getValue(String::class.java) ?: "",
                videoUrl = primary,
                videoUrl1080p = url1080,
                videoUrl720p = url720,
                videoUrl480p = url480,
                runtimeMinutes = snap.child("runtimeMinutes").getValue(Int::class.java) ?: 0,
                airDate = snap.child("airDate").getValue(String::class.java) ?: ""
            )
        }
    }
}

data class Season(
    val id: String = "",
    val seasonNumber: Int = 1,
    val title: String = "",
    val overview: String = "",
    val posterUrl: String = "",
    val episodes: List<Episode> = emptyList()
) {
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "seasonNumber" to seasonNumber,
        "title" to title,
        "overview" to overview,
        "posterUrl" to posterUrl,
        "episodes" to episodes.sortedBy { it.episodeNumber }.map { it.toFirebaseMap() }
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): Season? {
            val sNum = snap.child("seasonNumber").getValue(Int::class.java) ?: 1
            val eps = snap.child("episodes").children.mapNotNull { Episode.fromSnapshot(it) }
                .sortedBy { it.episodeNumber }
            return Season(
                id = snap.child("id").getValue(String::class.java) ?: "season_$sNum",
                seasonNumber = sNum,
                title = snap.child("title").getValue(String::class.java) ?: "Season $sNum",
                overview = snap.child("overview").getValue(String::class.java) ?: "",
                posterUrl = snap.child("posterUrl").getValue(String::class.java) ?: "",
                episodes = eps
            )
        }
    }
}

data class CatalogItem(
    val id: String = "",
    val tmdbId: Int? = null,
    val title: String = "",
    val originalTitle: String = "",
    val tagline: String = "",
    val normalizedTitle: String = "",
    val overview: String = "",
    val mediaType: String = MediaType.MOVIE.name,
    val releaseYear: Int = 2024,
    val releaseDate: String = "",
    val status: String = "Released",
    val rating: Double = 0.0,
    val runtimeMinutes: Int = 0,
    val contentAdvisory: String = "U/A 13+",
    val language: String = "en",
    val country: String = "United States",
    val budget: Long = 0L,
    val revenue: Long = 0L,
    val companies: List<String> = emptyList(),
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val logoUrl: String = "",
    val posterGallery: List<String> = emptyList(),
    val backdropGallery: List<String> = emptyList(),
    val totalPostersCount: Int = 0,
    val totalBackdropsCount: Int = 0,
    val genres: List<String> = emptyList(),
    val categoryIds: List<String> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val trailerUrl: String = "",
    val videoUrl: String = "",
    val videoUrl1080p: String = "",
    val videoUrl720p: String = "",
    val videoUrl480p: String = "",
    val seasons: List<Season> = emptyList(),
    val isVip: Boolean = false,
    val vipPointsRequired: Int = 0,
    val featured: Boolean = false,
    val trending: Boolean = false,
    val popular: Boolean = false,
    val published: Boolean = true,
    val sourceAttribution: String = "MANUAL",
    val importedAt: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val isSeries: Boolean
        get() = mediaType.equals(MediaType.SERIES.name, ignoreCase = true)

    val primaryStreamUrl: String
        get() = videoUrl1080p.ifBlank { videoUrl720p.ifBlank { videoUrl480p.ifBlank { videoUrl } } }

    fun effectiveVipPoints(globalDefault: Int = 100): Int =
        if (vipPointsRequired > 0) vipPointsRequired else globalDefault.coerceAtLeast(1)

    fun availableQualityOptions(): List<QualityStreamOption> {
        val list = mutableListOf<QualityStreamOption>()
        val u1080 = videoUrl1080p.trim().ifBlank { videoUrl.trim() }
        val u720 = videoUrl720p.trim()
        val u480 = videoUrl480p.trim()
        if (u1080.isNotBlank()) list.add(QualityStreamOption("1080p Full HD", "1080p", u1080))
        if (u720.isNotBlank()) list.add(QualityStreamOption("720p HD", "720p", u720))
        if (u480.isNotBlank()) list.add(QualityStreamOption("480p SD", "480p", u480))
        return list
    }

    val formattedRuntime: String
        get() {
            if (isSeries) {
                val count = seasons.size
                return if (count == 1) "1 Season" else if (count > 1) "$count Seasons" else "1 Season"
            }
            if (runtimeMinutes <= 0) return ""
            val h = runtimeMinutes / 60
            val m = runtimeMinutes % 60
            return if (h > 0) "${h}h ${m}m" else "${m}m"
        }

    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "tmdbId" to tmdbId,
        "title" to title,
        "originalTitle" to originalTitle.ifBlank { title },
        "tagline" to tagline,
        "normalizedTitle" to normalizedTitle,
        "overview" to overview,
        "mediaType" to mediaType,
        "releaseYear" to releaseYear,
        "releaseDate" to releaseDate,
        "status" to status,
        "rating" to rating,
        "runtimeMinutes" to runtimeMinutes,
        "contentAdvisory" to contentAdvisory,
        "language" to language,
        "country" to country,
        "budget" to budget,
        "revenue" to revenue,
        "companies" to companies,
        "posterUrl" to posterUrl,
        "backdropUrl" to backdropUrl,
        "logoUrl" to logoUrl,
        "posterGallery" to posterGallery.take(40),
        "backdropGallery" to backdropGallery.take(40),
        "totalPostersCount" to totalPostersCount,
        "totalBackdropsCount" to totalBackdropsCount,
        "genres" to genres,
        "categoryIds" to categoryIds,
        "cast" to cast.map { it.toFirebaseMap() },
        "trailerUrl" to trailerUrl,
        "videoUrl" to primaryStreamUrl,
        "videoUrl1080p" to videoUrl1080p.ifBlank { videoUrl },
        "videoUrl720p" to videoUrl720p,
        "videoUrl480p" to videoUrl480p,
        "seasons" to seasons.sortedBy { it.seasonNumber }.map { it.toFirebaseMap() },
        "isVip" to isVip,
        "vipPointsRequired" to vipPointsRequired,
        "featured" to featured,
        "trending" to trending,
        "popular" to popular,
        "published" to published,
        "sourceAttribution" to sourceAttribution,
        "importedAt" to importedAt,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): CatalogItem? {
            val id = snap.child("id").getValue(String::class.java) ?: snap.key ?: return null
            val title = snap.child("title").getValue(String::class.java) ?: return null
            val ratingNum = snap.child("rating").value
            val rating = when (ratingNum) {
                is Double -> ratingNum
                is Long -> ratingNum.toDouble()
                is Float -> ratingNum.toDouble()
                is Int -> ratingNum.toDouble()
                else -> 0.0
            }
            val vipPtsRaw = snap.child("vipPointsRequired").value
            val vipPts = when (vipPtsRaw) {
                is Long -> vipPtsRaw.toInt()
                is Int -> vipPtsRaw
                is Double -> vipPtsRaw.toInt()
                is String -> vipPtsRaw.toIntOrNull() ?: 0
                else -> 0
            }
            val genres = snap.child("genres").children.mapNotNull { it.getValue(String::class.java) }
            val categoryIds = snap.child("categoryIds").children.mapNotNull { it.getValue(String::class.java) }
            val companies = snap.child("companies").children.mapNotNull { it.getValue(String::class.java) }
            val posterGallery = snap.child("posterGallery").children.mapNotNull { it.getValue(String::class.java) }
            val backdropGallery = snap.child("backdropGallery").children.mapNotNull { it.getValue(String::class.java) }
            val cast = snap.child("cast").children.mapNotNull { CastMember.fromSnapshot(it) }
            val seasons = snap.child("seasons").children.mapNotNull { Season.fromSnapshot(it) }
                .sortedBy { it.seasonNumber }

            return CatalogItem(
                id = id,
                tmdbId = snap.child("tmdbId").getValue(Int::class.java),
                title = title,
                originalTitle = snap.child("originalTitle").getValue(String::class.java) ?: title,
                tagline = snap.child("tagline").getValue(String::class.java) ?: "",
                normalizedTitle = snap.child("normalizedTitle").getValue(String::class.java) ?: title.lowercase(),
                overview = snap.child("overview").getValue(String::class.java) ?: "",
                mediaType = snap.child("mediaType").getValue(String::class.java) ?: MediaType.MOVIE.name,
                releaseYear = snap.child("releaseYear").getValue(Int::class.java) ?: 2024,
                releaseDate = snap.child("releaseDate").getValue(String::class.java) ?: "",
                status = snap.child("status").getValue(String::class.java) ?: "Released",
                rating = rating,
                runtimeMinutes = snap.child("runtimeMinutes").getValue(Int::class.java) ?: 0,
                contentAdvisory = snap.child("contentAdvisory").getValue(String::class.java) ?: "U/A 13+",
                language = snap.child("language").getValue(String::class.java) ?: "en",
                country = snap.child("country").getValue(String::class.java) ?: "United States",
                budget = snap.child("budget").getValue(Long::class.java) ?: 0L,
                revenue = snap.child("revenue").getValue(Long::class.java) ?: 0L,
                companies = companies,
                posterUrl = snap.child("posterUrl").getValue(String::class.java) ?: "",
                backdropUrl = snap.child("backdropUrl").getValue(String::class.java) ?: "",
                logoUrl = snap.child("logoUrl").getValue(String::class.java) ?: "",
                posterGallery = posterGallery,
                backdropGallery = backdropGallery,
                totalPostersCount = snap.child("totalPostersCount").getValue(Int::class.java) ?: posterGallery.size,
                totalBackdropsCount = snap.child("totalBackdropsCount").getValue(Int::class.java) ?: backdropGallery.size,
                genres = genres,
                categoryIds = categoryIds,
                cast = cast,
                trailerUrl = snap.child("trailerUrl").getValue(String::class.java) ?: "",
                videoUrl = (snap.child("videoUrl1080p").getValue(String::class.java) ?: "").ifBlank {
                    (snap.child("videoUrl720p").getValue(String::class.java) ?: "").ifBlank {
                        (snap.child("videoUrl480p").getValue(String::class.java) ?: "").ifBlank {
                            snap.child("videoUrl").getValue(String::class.java) ?: ""
                        }
                    }
                },
                videoUrl1080p = (snap.child("videoUrl1080p").getValue(String::class.java) ?: "").ifBlank {
                    snap.child("videoUrl").getValue(String::class.java) ?: ""
                },
                videoUrl720p = snap.child("videoUrl720p").getValue(String::class.java) ?: "",
                videoUrl480p = snap.child("videoUrl480p").getValue(String::class.java) ?: "",
                seasons = seasons,
                isVip = snap.child("isVip").getValue(Boolean::class.java) ?: false,
                vipPointsRequired = vipPts,
                featured = snap.child("featured").getValue(Boolean::class.java) ?: false,
                trending = snap.child("trending").getValue(Boolean::class.java) ?: false,
                popular = snap.child("popular").getValue(Boolean::class.java) ?: false,
                published = snap.child("published").getValue(Boolean::class.java) ?: true,
                sourceAttribution = snap.child("sourceAttribution").getValue(String::class.java) ?: "MANUAL",
                importedAt = snap.child("importedAt").getValue(Long::class.java) ?: 0L,
                createdAt = snap.child("createdAt").getValue(Long::class.java) ?: 0L,
                updatedAt = snap.child("updatedAt").getValue(Long::class.java) ?: 0L
            )
        }
    }
}

data class VipAccessConfig(
    val vipPointsToAccess: Int = 100,
    val pointsPerReferral: Int = 50,
    val pointsPerShareApp: Int = 25,
    val shareAppUrl: String = "",
    val updatedAt: Long = 0L
) {
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "vipPointsToAccess" to vipPointsToAccess.coerceAtLeast(1),
        "pointsPerReferral" to pointsPerReferral.coerceAtLeast(1),
        "pointsPerShareApp" to pointsPerShareApp.coerceAtLeast(1),
        "shareAppUrl" to shareAppUrl.trim(),
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): VipAccessConfig {
            if (!snap.exists()) return VipAccessConfig()
            val vipPts = (snap.child("vipPointsToAccess").getValue(Long::class.java)?.toInt()
                ?: snap.child("vipPointsToAccess").getValue(Int::class.java)
                ?: 100).coerceAtLeast(1)
            val refPts = (snap.child("pointsPerReferral").getValue(Long::class.java)?.toInt()
                ?: snap.child("pointsPerReferral").getValue(Int::class.java)
                ?: 50).coerceAtLeast(1)
            val sharePts = (snap.child("pointsPerShareApp").getValue(Long::class.java)?.toInt()
                ?: snap.child("pointsPerShareApp").getValue(Int::class.java)
                ?: 25).coerceAtLeast(1)
            val url = snap.child("shareAppUrl").getValue(String::class.java) ?: ""
            val updatedAt = snap.child("updatedAt").getValue(Long::class.java) ?: 0L
            return VipAccessConfig(
                vipPointsToAccess = vipPts,
                pointsPerReferral = refPts,
                pointsPerShareApp = sharePts,
                shareAppUrl = url,
                updatedAt = updatedAt
            )
        }
    }
}

data class UserPresenceRecord(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val fcmToken: String = "",
    val deviceId: String = "",
    val deviceModel: String = "",
    val appVersion: String = "",
    val shareCode: String = "",
    val points: Int = 0,
    val shareCount: Int = 0,
    val redeemedCode: String = "",
    val redeemedCodes: List<String> = emptyList(),
    val isVip: Boolean = false,
    val unlockedItemIds: List<String> = emptyList()
) {
    fun hasVipAccessFor(item: CatalogItem, globalVipPoints: Int = 100): Boolean {
        if (!item.isVip) return true
        if (isVip) return true
        if (item.id in unlockedItemIds) return true
        val required = item.effectiveVipPoints(globalVipPoints)
        return points >= required
    }

    fun hasGlobalVip(globalVipPoints: Int = 100): Boolean =
        isVip || points >= globalVipPoints.coerceAtLeast(1)

    companion object {
        fun generateFiveDigitShareCode(seed: String): String {
            val clean = seed.trim().ifBlank { "moviebox_default" }
            var hash = 5381L
            for (ch in clean) {
                hash = ((hash shl 5) + hash) + ch.code.toLong()
            }
            val fiveDigit = 10000 + (abs(hash) % 90000L).toInt()
            return fiveDigit.toString()
        }

        fun fromSnapshot(snap: DataSnapshot): UserPresenceRecord? {
            val uid = snap.child("uid").getValue(String::class.java) ?: snap.key ?: return null
            val deviceId = snap.child("deviceId").getValue(String::class.java) ?: ""
            val rawCode = snap.child("shareCode").getValue(String::class.java).orEmpty().trim()
            val resolvedCode = if (rawCode.length == 5 && rawCode.all { it.isDigit() }) {
                rawCode
            } else {
                generateFiveDigitShareCode(deviceId.ifBlank { uid })
            }
            val ptsRaw = snap.child("points").value
            val pts = when (ptsRaw) {
                is Long -> ptsRaw.toInt()
                is Int -> ptsRaw
                is Double -> ptsRaw.toInt()
                is String -> ptsRaw.toIntOrNull() ?: 0
                else -> 0
            }
            val sharesRaw = snap.child("shareCount").value
            val shares = when (sharesRaw) {
                is Long -> sharesRaw.toInt()
                is Int -> sharesRaw
                is Double -> sharesRaw.toInt()
                is String -> sharesRaw.toIntOrNull() ?: 0
                else -> 0
            }
            val redeemedList = snap.child("redeemedCodes").children.mapNotNull {
                it.getValue(String::class.java)
            }.distinct()
            val singleRedeemed = snap.child("redeemedCode").getValue(String::class.java)
                ?: redeemedList.firstOrNull().orEmpty()
            val unlockedList = snap.child("unlockedItemIds").children.mapNotNull {
                it.getValue(String::class.java)
            }.distinct()

            return UserPresenceRecord(
                uid = uid,
                displayName = snap.child("displayName").getValue(String::class.java) ?: "User",
                email = snap.child("email").getValue(String::class.java) ?: "",
                online = snap.child("online").getValue(Boolean::class.java) ?: false,
                lastSeen = snap.child("lastSeen").getValue(Long::class.java) ?: 0L,
                fcmToken = snap.child("fcmToken").getValue(String::class.java) ?: "",
                deviceId = deviceId,
                deviceModel = snap.child("deviceModel").getValue(String::class.java) ?: "",
                appVersion = snap.child("appVersion").getValue(String::class.java) ?: "",
                shareCode = resolvedCode,
                points = pts.coerceAtLeast(0),
                shareCount = shares.coerceAtLeast(0),
                redeemedCode = singleRedeemed,
                redeemedCodes = if (redeemedList.isNotEmpty()) redeemedList else listOfNotNull(singleRedeemed.takeIf { it.isNotBlank() }),
                isVip = snap.child("isVip").getValue(Boolean::class.java) ?: false,
                unlockedItemIds = unlockedList
            )
        }
    }
}

data class AppUpdateConfig(
    val versionName: String = "1.0.0",
    val versionCode: Int = 1,
    val updateFeatures: String = "",
    val apkUrl: String = "",
    val forceUpdate: Boolean = false,
    val updatedAt: Long = 0L
) {
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "versionName" to versionName,
        "versionCode" to versionCode,
        "updateFeatures" to updateFeatures,
        "apkUrl" to apkUrl,
        "forceUpdate" to forceUpdate,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): AppUpdateConfig? {
            if (!snap.exists()) return null
            val versionName = snap.child("versionName").getValue(String::class.java) ?: return null
            val versionCode = snap.child("versionCode").getValue(Int::class.java) ?: 1
            val updateFeatures = snap.child("updateFeatures").getValue(String::class.java) ?: ""
            val apkUrl = snap.child("apkUrl").getValue(String::class.java) ?: ""
            val forceUpdate = snap.child("forceUpdate").getValue(Boolean::class.java) ?: false
            val updatedAt = snap.child("updatedAt").getValue(Long::class.java) ?: 0L
            return AppUpdateConfig(
                versionName = versionName,
                versionCode = versionCode,
                updateFeatures = updateFeatures,
                apkUrl = apkUrl,
                forceUpdate = forceUpdate,
                updatedAt = updatedAt
            )
        }
    }
}

data class PushNotificationPayload(
    val id: String = "",
    val itemId: String = "",
    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val sentAt: Long = 0L
) {
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "itemId" to itemId,
        "title" to title,
        "body" to body,
        "imageUrl" to imageUrl,
        "sentAt" to sentAt
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): PushNotificationPayload? {
            if (!snap.exists()) return null
            val id = snap.child("id").getValue(String::class.java) ?: return null
            val title = snap.child("title").getValue(String::class.java) ?: return null
            val body = snap.child("body").getValue(String::class.java) ?: ""
            val imageUrl = snap.child("imageUrl").getValue(String::class.java) ?: ""
            val itemId = snap.child("itemId").getValue(String::class.java) ?: ""
            val sentAt = snap.child("sentAt").getValue(Long::class.java) ?: 0L
            return PushNotificationPayload(id, itemId, title, body, imageUrl, sentAt)
        }
    }
}

data class AppPreferences(
    val themeVariant: ThemeVariant = ThemeVariant.DEEP_RED,
    val defaultQuality: String = "Auto",
    val autoPlayNextEpisode: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val watchlistIds: Set<String> = emptySet(),
    val tmdbApiKeyOverride: String = ""
)

data class TmdbImportSelection(
    val importTitle: Boolean = true,
    val importOverview: Boolean = true,
    val importPoster: Boolean = true,
    val importBackdrop: Boolean = true,
    val importTrailer: Boolean = true,
    val importYearAndRating: Boolean = true,
    val importCast: Boolean = true,
    val importSeasons: Boolean = true
)

data class ActorProfileDetails(
    val personId: Int = 0,
    val name: String = "",
    val characterRole: String = "",
    val biography: String = "",
    val birthday: String = "",
    val deathday: String = "",
    val placeOfBirth: String = "",
    val knownForDepartment: String = "Acting",
    val genderLabel: String = "Not Specified",
    val popularity: Double = 0.0,
    val primaryProfileUrl: String = "",
    val profileGallery: List<String> = emptyList(),
    val alsoKnownAs: List<String> = emptyList()
)

data class MovieRequestItem(
    val id: String = "",
    val tmdbId: Int? = null,
    val title: String = "",
    val mediaType: String = MediaType.MOVIE.name,
    val releaseYear: Int = 2025,
    val releaseDate: String = "",
    val rating: Double = 0.0,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val overview: String = "",
    val requestedByDeviceId: String = "",
    val requestedByShareCode: String = "",
    val requestedByUid: String = "",
    val requestCount: Int = 1,
    val status: String = "PENDING",
    val requestedAt: Long = 0L
) {
    val isSeries: Boolean
        get() = mediaType.equals(MediaType.SERIES.name, ignoreCase = true)

    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "tmdbId" to tmdbId,
        "title" to title,
        "mediaType" to mediaType,
        "releaseYear" to releaseYear,
        "releaseDate" to releaseDate,
        "rating" to rating,
        "posterUrl" to posterUrl,
        "backdropUrl" to backdropUrl,
        "overview" to overview,
        "requestedByDeviceId" to requestedByDeviceId,
        "requestedByShareCode" to requestedByShareCode,
        "requestedByUid" to requestedByUid,
        "requestCount" to requestCount,
        "status" to status,
        "requestedAt" to requestedAt
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): MovieRequestItem? {
            val id = snap.child("id").getValue(String::class.java) ?: snap.key ?: return null
            val title = snap.child("title").getValue(String::class.java) ?: return null
            val ratingRaw = snap.child("rating").value
            val rating = when (ratingRaw) {
                is Double -> ratingRaw
                is Long -> ratingRaw.toDouble()
                is Float -> ratingRaw.toDouble()
                is Int -> ratingRaw.toDouble()
                else -> 0.0
            }
            val countRaw = snap.child("requestCount").value
            val count = when (countRaw) {
                is Long -> countRaw.toInt()
                is Int -> countRaw
                is Double -> countRaw.toInt()
                else -> 1
            }
            return MovieRequestItem(
                id = id,
                tmdbId = snap.child("tmdbId").getValue(Int::class.java),
                title = title,
                mediaType = snap.child("mediaType").getValue(String::class.java) ?: MediaType.MOVIE.name,
                releaseYear = snap.child("releaseYear").getValue(Int::class.java) ?: 2025,
                releaseDate = snap.child("releaseDate").getValue(String::class.java) ?: "",
                rating = rating,
                posterUrl = snap.child("posterUrl").getValue(String::class.java) ?: "",
                backdropUrl = snap.child("backdropUrl").getValue(String::class.java) ?: "",
                overview = snap.child("overview").getValue(String::class.java) ?: "",
                requestedByDeviceId = snap.child("requestedByDeviceId").getValue(String::class.java) ?: "",
                requestedByShareCode = snap.child("requestedByShareCode").getValue(String::class.java) ?: "",
                requestedByUid = snap.child("requestedByUid").getValue(String::class.java) ?: "",
                requestCount = count.coerceAtLeast(1),
                status = snap.child("status").getValue(String::class.java) ?: "PENDING",
                requestedAt = snap.child("requestedAt").getValue(Long::class.java) ?: 0L
            )
        }
    }
}


data class MediaActivityLog(
    val id: String = "",
    val itemId: String = "",
    val itemTitle: String = "",
    val mediaType: String = MediaType.MOVIE.name,
    val actionType: String = "VIEW",
    val uid: String = "",
    val deviceId: String = "",
    val shareCode: String = "",
    val userName: String = "",
    val deviceModel: String = "",
    val episodeLabel: String = "",
    val timestamp: Long = 0L
) {
    val isDownload: Boolean
        get() = actionType.equals("DOWNLOAD", ignoreCase = true)

    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "itemId" to itemId,
        "itemTitle" to itemTitle,
        "mediaType" to mediaType,
        "actionType" to actionType,
        "uid" to uid,
        "deviceId" to deviceId,
        "shareCode" to shareCode,
        "userName" to userName,
        "deviceModel" to deviceModel,
        "episodeLabel" to episodeLabel,
        "timestamp" to timestamp
    )

    companion object {
        fun fromSnapshot(snap: DataSnapshot): MediaActivityLog? {
            if (!snap.exists()) return null
            val id = snap.child("id").getValue(String::class.java) ?: snap.key ?: return null
            val itemId = snap.child("itemId").getValue(String::class.java) ?: return null
            val tsRaw = snap.child("timestamp").value
            val ts = when (tsRaw) {
                is Long -> tsRaw
                is Int -> tsRaw.toLong()
                is Double -> tsRaw.toLong()
                else -> 0L
            }
            return MediaActivityLog(
                id = id,
                itemId = itemId,
                itemTitle = snap.child("itemTitle").getValue(String::class.java) ?: "",
                mediaType = snap.child("mediaType").getValue(String::class.java) ?: MediaType.MOVIE.name,
                actionType = snap.child("actionType").getValue(String::class.java) ?: "VIEW",
                uid = snap.child("uid").getValue(String::class.java) ?: "",
                deviceId = snap.child("deviceId").getValue(String::class.java) ?: "",
                shareCode = snap.child("shareCode").getValue(String::class.java) ?: "",
                userName = snap.child("userName").getValue(String::class.java) ?: "",
                deviceModel = snap.child("deviceModel").getValue(String::class.java) ?: "",
                episodeLabel = snap.child("episodeLabel").getValue(String::class.java) ?: "",
                timestamp = ts
            )
        }
    }
}

data class ItemMediaAnalytics(
    val itemId: String = "",
    val viewCount: Int = 0,
    val downloadCount: Int = 0,
    val viewLogs: List<MediaActivityLog> = emptyList(),
    val downloadLogs: List<MediaActivityLog> = emptyList()
) {
    companion object {
        fun fromSnapshot(itemId: String, snap: DataSnapshot): ItemMediaAnalytics {
            val viewsList = snap.child("views").children
                .mapNotNull { MediaActivityLog.fromSnapshot(it) }
                .sortedByDescending { it.timestamp }
            val downloadsList = snap.child("downloads").children
                .mapNotNull { MediaActivityLog.fromSnapshot(it) }
                .sortedByDescending { it.timestamp }

            val vcRaw = snap.child("viewCount").value
            val explicitViews = when (vcRaw) {
                is Long -> vcRaw.toInt()
                is Int -> vcRaw
                is Double -> vcRaw.toInt()
                else -> 0
            }
            val dcRaw = snap.child("downloadCount").value
            val explicitDownloads = when (dcRaw) {
                is Long -> dcRaw.toInt()
                is Int -> dcRaw
                is Double -> dcRaw.toInt()
                else -> 0
            }
            return ItemMediaAnalytics(
                itemId = itemId,
                viewCount = maxOf(explicitViews, viewsList.size),
                downloadCount = maxOf(explicitDownloads, downloadsList.size),
                viewLogs = viewsList,
                downloadLogs = downloadsList
            )
        }
    }
}
