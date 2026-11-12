package com.moviebox.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.moviebox.app.data.model.CastMember
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Category
import com.moviebox.app.data.model.CategoryType
import com.moviebox.app.data.model.Episode
import com.moviebox.app.data.model.MediaType
import com.moviebox.app.data.model.Season
import com.moviebox.app.data.model.AppUpdateConfig
import com.moviebox.app.data.model.PushNotificationPayload
import com.moviebox.app.data.model.UserPresenceRecord
import com.moviebox.app.data.model.MovieRequestItem
import com.moviebox.app.data.model.MediaActivityLog
import com.moviebox.app.data.model.ItemMediaAnalytics
import com.moviebox.app.data.model.VipAccessConfig
import com.moviebox.app.security.NhNativeSecurityVault
import com.moviebox.app.util.UrlValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

data class CatalogState(
    val isLoading: Boolean = true,
    val isConnectedToFirebase: Boolean = false,
    val isUsingCachedData: Boolean = false,
    val movies: List<CatalogItem> = emptyList(),
    val series: List<CatalogItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val users: List<UserPresenceRecord> = emptyList(),
    val movieRequests: List<MovieRequestItem> = emptyList(),
    val mediaAnalyticsMap: Map<String, ItemMediaAnalytics> = emptyMap(),
    val vipAccessConfig: VipAccessConfig = VipAccessConfig(),
    val appUpdateConfig: AppUpdateConfig? = null,
    val latestNotification: PushNotificationPayload? = null,
    val notificationsHistory: List<PushNotificationPayload> = emptyList(),
    val lastUpdated: Long = 0L,
    val errorMessage: String? = null
) {
    fun analyticsFor(itemId: String): ItemMediaAnalytics =
        mediaAnalyticsMap[itemId] ?: ItemMediaAnalytics(itemId = itemId)
}

class CatalogRepository(private val context: Context) {

    private val gson = Gson()
    private val cacheFile = File(context.filesDir, "moviebox_catalog_cache.json")
    private val db: FirebaseDatabase? = runCatching {
        NhNativeSecurityVault.ensureFirebaseInitialized(context)
        val dbUrl = NhNativeSecurityVault.getDeveloperInfo(context).firebaseDatabaseUrl
        if (dbUrl.isNotBlank()) FirebaseDatabase.getInstance(dbUrl) else FirebaseDatabase.getInstance()
    }.getOrNull()

    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var started = false
    private var usersRef: DatabaseReference? = null
    private var usersListener: ValueEventListener? = null

    /**
     * Starts the live /users listener for Admin -> User Management. Must be called AFTER the admin
     * is signed in and verified. Also mirrors all registered 5-digit Share Codes into
     * /config/registeredCodes/{code} so every device can strictly verify whether a 5-digit Share Code
     * exists in Firebase before redeeming it, and credits code owners when their code is redeemed.
     */
    fun attachUsersListener() {
        val database = db ?: return
        if (usersListener != null) return
        val ref = database.getReference("users")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val raw = snapshot.children.mapNotNull { UserPresenceRecord.fromSnapshot(it) }
                val codeRedemptionCounts = HashMap<String, Int>()
                raw.GroupByDeviceAndCountRedemptions(codeRedemptionCounts)
                val ptsPerRedeem = _state.value.vipAccessConfig.pointsPerReferral.coerceAtLeast(1)

                val list = raw
                    .groupBy { it.deviceId.ifBlank { it.uid } }
                    .map { (_, group) ->
                        val latest = group.maxByOrNull { it.lastSeen } ?: group.first()
                        val maxPts = group.maxOfOrNull { it.points } ?: latest.points
                        val maxShares = group.maxOfOrNull { it.shareCount } ?: latest.shareCount
                        val allRedeemed = group.flatMap { it.redeemedCodes }.distinct()
                        val allUnlocked = group.flatMap { it.unlockedItemIds }.distinct()
                        val redeemedCountForMyCode = codeRedemptionCounts[latest.shareCode] ?: 0
                        val totalVerifiedInteractions = redeemedCountForMyCode + allRedeemed.size
                        val combinedShares = maxOf(maxShares, totalVerifiedInteractions)
                        val minReferralPoints = totalVerifiedInteractions * ptsPerRedeem
                        val combinedPoints = maxOf(maxPts, minReferralPoints)
                        val anyVip = group.any { it.isVip }
                        latest.copy(
                            points = combinedPoints,
                            shareCount = combinedShares,
                            isVip = anyVip,
                            redeemedCode = latest.redeemedCode.ifBlank { allRedeemed.firstOrNull().orEmpty() },
                            redeemedCodes = allRedeemed,
                            unlockedItemIds = allUnlocked
                        )
                    }
                    .sortedByDescending { it.lastSeen }
                val fromUserNodes = snapshot.children.flatMap { userSnap ->
                    userSnap.child("movieRequests").children.mapNotNull { MovieRequestItem.fromSnapshot(it) }
                }
                val mergedRequests = (fromUserNodes + _state.value.movieRequests)
                    .groupBy { it.id }
                    .map { (_, reqGroup) ->
                        val newest = reqGroup.maxByOrNull { it.requestedAt } ?: reqGroup.first()
                        val distinctDevices = reqGroup.map { it.requestedByDeviceId.ifBlank { it.requestedByUid } }.distinct().size
                        val statusResolved = if (reqGroup.any { it.status == "UPLOADED" }) "UPLOADED" else newest.status
                        newest.copy(
                            requestCount = maxOf(newest.requestCount, distinctDevices),
                            status = statusResolved
                        )
                    }
                    .sortedByDescending { it.requestedAt }
                val userActivityLogs = snapshot.children.flatMap { userSnap ->
                    userSnap.child("mediaActivityLogs").children.mapNotNull { MediaActivityLog.fromSnapshot(it) }
                }
                val mergedAnalytics = mergeActivityLogsIntoAnalyticsMap(_state.value.mediaAnalyticsMap, userActivityLogs)
                _state.value = _state.value.copy(
                    users = list,
                    movieRequests = mergedRequests,
                    mediaAnalyticsMap = mergedAnalytics
                )

                // Mirror registered 5-digit codes to /config/registeredCodes and sync earned referral points
                runCatching {
                    val regMap = HashMap<String, Any?>()
                    val now = System.currentTimeMillis()
                    list.forEach { u ->
                        val code = u.shareCode.ifBlank {
                            UserPresenceRecord.generateFiveDigitShareCode(u.deviceId.ifBlank { u.uid })
                        }
                        if (code.length == 5) {
                            regMap["/config/registeredCodes/$code"] = mapOf(
                                "shareCode" to code,
                                "uid" to u.uid,
                                "deviceId" to u.deviceId
                            )
                        }
                        val rawUser = raw.filter { (it.deviceId.ifBlank { it.uid }) == (u.deviceId.ifBlank { u.uid }) }
                            .maxByOrNull { it.lastSeen }
                        if (rawUser != null && (u.points > rawUser.points || u.shareCount > rawUser.shareCount)) {
                            val devKey = u.deviceId.ifBlank { u.uid }
                            regMap["/config/userOverrides/$devKey"] = mapOf(
                                "uid" to u.uid,
                                "deviceId" to u.deviceId,
                                "shareCode" to code,
                                "points" to u.points,
                                "shareCount" to u.shareCount,
                                "isVip" to u.isVip,
                                "updatedAt" to now
                            )
                        }
                    }
                    if (regMap.isNotEmpty()) {
                        database.reference.updateChildren(regMap)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                usersListener = null
                usersRef = null
            }
        }
        usersRef = ref
        usersListener = listener
        runCatching { ref.addValueEventListener(listener) }.onFailure {
            usersListener = null
            usersRef = null
        }
    }

    private fun List<UserPresenceRecord>.GroupByDeviceAndCountRedemptions(outCounts: MutableMap<String, Int>) {
        val byDevice = this.groupBy { it.deviceId.ifBlank { it.uid } }
        byDevice.values.forEach { group ->
            val codes = group.flatMap { it.redeemedCodes + listOfNotNull(it.redeemedCode.takeIf(String::isNotBlank)) }.distinct()
            codes.forEach { code ->
                outCounts[code] = (outCounts[code] ?: 0) + 1
            }
        }
    }

    fun detachUsersListener() {
        runCatching { usersListener?.let { usersRef?.removeEventListener(it) } }
        usersListener = null
        usersRef = null
        _state.value = _state.value.copy(users = emptyList())
    }

    fun startListening() {
        if (started) return
        started = true
        loadLocalCacheFallback()

        val database = db ?: run {
            _state.value = _state.value.copy(
                isLoading = false,
                isConnectedToFirebase = false,
                errorMessage = "Firebase Realtime Database is not configured."
            )
            return
        }

        // 1. Monitor Firebase connection state
        runCatching {
            database.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) == true
                    _state.value = _state.value.copy(
                        isConnectedToFirebase = connected,
                        isUsingCachedData = !connected && (_state.value.movies.isNotEmpty() || _state.value.series.isNotEmpty())
                    )
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // 2. Listen to /categories
        runCatching {
            database.getReference("categories").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { Category.fromSnapshot(it) }
                        .sortedWith(compareBy<Category> { it.order }.thenBy { it.name })
                    _state.value = _state.value.copy(categories = list, isLoading = false, errorMessage = null)
                }
                override fun onCancelled(error: DatabaseError) {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = error.message)
                }
            })
        }

        // 3. Listen to /movies
        runCatching {
            database.getReference("movies").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { CatalogItem.fromSnapshot(it) }
                        .sortedByDescending { it.updatedAt }
                    _state.value = _state.value.copy(
                        movies = list,
                        isLoading = false,
                        isUsingCachedData = false,
                        errorMessage = null
                    )
                    saveLocalCache()
                }
                override fun onCancelled(error: DatabaseError) {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = error.message)
                }
            })
        }

        // 4. Listen to /webseries
        runCatching {
            database.getReference("webseries").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull { CatalogItem.fromSnapshot(it) }
                        .sortedByDescending { it.updatedAt }
                    _state.value = _state.value.copy(
                        series = list,
                        isLoading = false,
                        isUsingCachedData = false,
                        errorMessage = null
                    )
                    saveLocalCache()
                }
                override fun onCancelled(error: DatabaseError) {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = error.message)
                }
            })
        }

        // 5. Listen to /metadata/lastUpdated
        runCatching {
            database.getReference("metadata/lastUpdated").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val ts = snapshot.getValue(Long::class.java) ?: 0L
                    _state.value = _state.value.copy(lastUpdated = ts)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // 6. Listen to /config/vipSettings for global VIP Points configuration
        runCatching {
            database.getReference("config/vipSettings").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val cfg = VipAccessConfig.fromSnapshot(snapshot)
                    _state.value = _state.value.copy(vipAccessConfig = cfg)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // 7. Listen to /appUpdate for In-App Update configuration
        runCatching {
            database.getReference("appUpdate").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val cfg = AppUpdateConfig.fromSnapshot(snapshot)
                    _state.value = _state.value.copy(appUpdateConfig = cfg)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // 8. Listen to /notifications/latest for live push notification delivery
        runCatching {
            database.getReference("notifications/latest").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val notif = PushNotificationPayload.fromSnapshot(snapshot)
                    val mergedHistory = if (notif != null) {
                        (listOf(notif) + _state.value.notificationsHistory)
                            .distinctBy { it.id }
                            .sortedByDescending { it.sentAt }
                    } else {
                        _state.value.notificationsHistory
                    }
                    _state.value = _state.value.copy(
                        latestNotification = notif,
                        notificationsHistory = mergedHistory
                    )
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // 9. Listen to /notifications/history for the full Notifications Inbox screen
        runCatching {
            database.getReference("notifications/history").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val historyList = snapshot.children.mapNotNull { PushNotificationPayload.fromSnapshot(it) }
                    val latest = _state.value.latestNotification
                    val combined = (historyList + listOfNotNull(latest))
                        .distinctBy { it.id }
                        .sortedByDescending { it.sentAt }
                    _state.value = _state.value.copy(notificationsHistory = combined)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // 10. Listen to /movieRequests and /config/movieRequests for user-submitted Movie/Series requests
        runCatching {
            database.getReference("movieRequests").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val directReqs = snapshot.children.mapNotNull { MovieRequestItem.fromSnapshot(it) }
                    val merged = (directReqs + _state.value.movieRequests)
                        .groupBy { it.id }
                        .map { (_, grp) ->
                            val latest = grp.maxByOrNull { it.requestedAt } ?: grp.first()
                            val statusResolved = if (grp.any { it.status == "UPLOADED" }) "UPLOADED" else latest.status
                            latest.copy(
                                requestCount = grp.maxOf { it.requestCount },
                                status = statusResolved
                            )
                        }
                        .sortedByDescending { it.requestedAt }
                    _state.value = _state.value.copy(movieRequests = merged)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // 11. Listen to /mediaAnalytics for live Movie & Web Series Views and Downloads
        runCatching {
            database.getReference("mediaAnalytics").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val fromNode = HashMap<String, ItemMediaAnalytics>()
                    snapshot.children.forEach { itemSnap ->
                        val itemId = itemSnap.key ?: return@forEach
                        fromNode[itemId] = ItemMediaAnalytics.fromSnapshot(itemId, itemSnap)
                    }
                    // Preserve any logs already merged from /users/{uid}/mediaActivityLogs
                    val existingLogs = _state.value.mediaAnalyticsMap.values.flatMap { it.viewLogs + it.downloadLogs }
                    val combinedMap = mergeActivityLogsIntoAnalyticsMap(fromNode, existingLogs)
                    _state.value = _state.value.copy(mediaAnalyticsMap = combinedMap)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun mergeActivityLogsIntoAnalyticsMap(
        baseMap: Map<String, ItemMediaAnalytics>,
        extraLogs: List<MediaActivityLog>
    ): Map<String, ItemMediaAnalytics> {
        if (extraLogs.isEmpty()) return baseMap
        val result = HashMap(baseMap)
        val groupedByItem = extraLogs.groupBy { it.itemId }
        groupedByItem.forEach { (itemId, logs) ->
            if (itemId.isBlank()) return@forEach
            val cur = result[itemId] ?: ItemMediaAnalytics(itemId = itemId)
            val mergedViews = (cur.viewLogs + logs.filter { !it.isDownload })
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }
            val mergedDownloads = (cur.downloadLogs + logs.filter { it.isDownload })
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }
            result[itemId] = cur.copy(
                viewCount = maxOf(cur.viewCount, mergedViews.size),
                downloadCount = maxOf(cur.downloadCount, mergedDownloads.size),
                viewLogs = mergedViews,
                downloadLogs = mergedDownloads
            )
        }
        return result
    }

    // ---- Local Offline Cache ----
    private data class CachedSnapshot(
        val movies: List<CatalogItem> = emptyList(),
        val series: List<CatalogItem> = emptyList(),
        val categories: List<Category> = emptyList()
    )

    private fun loadLocalCacheFallback() {
        runCatching {
            if (!cacheFile.exists()) return
            val json = cacheFile.readText()
            val type = object : TypeToken<CachedSnapshot>() {}.type
            val cached: CachedSnapshot? = gson.fromJson(json, type)
            if (cached != null && (cached.movies.isNotEmpty() || cached.series.isNotEmpty())) {
                _state.value = _state.value.copy(
                    movies = cached.movies,
                    series = cached.series,
                    categories = cached.categories,
                    isUsingCachedData = true,
                    isLoading = false
                )
            }
        }
    }

    private fun saveLocalCache() {
        runCatching {
            val cur = _state.value
            val snap = CachedSnapshot(cur.movies, cur.series, cur.categories)
            cacheFile.writeText(gson.toJson(snap))
        }
    }

    // ---- Duplicate Check for TMDB Import ----
    fun findDuplicateByTmdbId(tmdbId: Int, mediaType: MediaType): CatalogItem? {
        val pool = if (mediaType == MediaType.MOVIE) _state.value.movies else _state.value.series
        return pool.firstOrNull { it.tmdbId == tmdbId }
    }

    // ---- Category Usage Check ----
    fun countItemsUsingCategory(categoryId: String): Int {
        val cur = _state.value
        return (cur.movies + cur.series).count { categoryId in it.categoryIds }
    }

    // ---- Admin CRUD Operations ----
    suspend fun saveVipAccessConfig(config: VipAccessConfig): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val finalConfig = config.copy(updatedAt = System.currentTimeMillis())
        database.reference.child("config").child("vipSettings").setValue(finalConfig.toFirebaseMap()).await()
        _state.value = _state.value.copy(vipAccessConfig = finalConfig)
    }

    suspend fun adminUpdateUserRecord(
        user: UserPresenceRecord,
        newPoints: Int,
        newShareCount: Int,
        isVip: Boolean
    ): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val cleanPoints = newPoints.coerceAtLeast(0)
        val cleanShares = newShareCount.coerceAtLeast(0)
        val now = System.currentTimeMillis()
        val devKey = user.deviceId.ifBlank { user.uid }

        val overrideData = mapOf(
            "uid" to user.uid,
            "deviceId" to user.deviceId,
            "shareCode" to user.shareCode,
            "points" to cleanPoints,
            "shareCount" to cleanShares,
            "isVip" to isVip,
            "updatedAt" to now
        )
        database.reference.child("config").child("userOverrides").child(devKey).setValue(overrideData).await()

        if (user.uid.isNotBlank()) {
            runCatching {
                database.reference.child("users").child(user.uid).updateChildren(
                    mapOf(
                        "points" to cleanPoints,
                        "shareCount" to cleanShares,
                        "isVip" to isVip
                    )
                ).await()
            }
        }

        val updatedUsers = _state.value.users.map { u ->
            if (u.uid == user.uid || (user.deviceId.isNotBlank() && u.deviceId == user.deviceId)) {
                u.copy(points = cleanPoints, shareCount = cleanShares, isVip = isVip)
            } else {
                u
            }
        }
        _state.value = _state.value.copy(users = updatedUsers)
    }

    suspend fun saveCategory(category: Category): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val now = System.currentTimeMillis()
        val id = category.id.ifBlank { "cat_${UUID.randomUUID().toString().take(8)}" }
        val finalCat = category.copy(
            id = id,
            name = category.name.trim(),
            createdAt = if (category.createdAt > 0L) category.createdAt else now,
            updatedAt = now
        )
        val updates = mapOf(
            "/categories/$id" to finalCat.toFirebaseMap(),
            "/metadata/lastUpdated" to ServerValue.TIMESTAMP
        )
        database.reference.updateChildren(updates).await()
    }

    suspend fun deleteCategory(categoryId: String): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val cur = _state.value
        val updates = HashMap<String, Any?>()
        updates["/categories/$categoryId"] = null
        cur.movies.filter { categoryId in it.categoryIds }.forEach { movie ->
            val updatedIds = movie.categoryIds.filterNot { it == categoryId }
            updates["/movies/${movie.id}/categoryIds"] = updatedIds
        }
        cur.series.filter { categoryId in it.categoryIds }.forEach { s ->
            val updatedIds = s.categoryIds.filterNot { it == categoryId }
            updates["/webseries/${s.id}/categoryIds"] = updatedIds
        }
        updates["/metadata/lastUpdated"] = ServerValue.TIMESTAMP
        database.reference.updateChildren(updates).await()
    }

    suspend fun saveCatalogItem(item: CatalogItem): Result<CatalogItem> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        require(item.title.isNotBlank()) { "Title is required." }

        if (item.videoUrl.isNotBlank()) {
            val streamCheck = UrlValidator.validateStreamUrl(item.videoUrl)
            require(streamCheck.isValid) { streamCheck.errorMessage ?: "Invalid stream URL." }
        }
        if (item.trailerUrl.isNotBlank()) {
            require(UrlValidator.validateTrailerUrl(item.trailerUrl)) { "Invalid trailer URL." }
        }
        if (item.isSeries) {
            val orderErr = UrlValidator.validateSeasonEpisodeOrdering(item.seasons)
            require(orderErr == null) { orderErr ?: "Invalid season/episode ordering." }
        }

        val now = System.currentTimeMillis()
        val prefix = if (item.isSeries) "series" else "movie"
        val id = item.id.ifBlank { "${prefix}_${UUID.randomUUID().toString().take(8)}" }
        val finalItem = item.copy(
            id = id,
            title = item.title.trim(),
            normalizedTitle = UrlValidator.normalizeTitle(item.title),
            createdAt = if (item.createdAt > 0L) item.createdAt else now,
            updatedAt = now
        )
        val node = if (finalItem.isSeries) "webseries" else "movies"
        val updates = mapOf(
            "/$node/$id" to finalItem.toFirebaseMap(),
            "/metadata/lastUpdated" to ServerValue.TIMESTAMP
        )
        database.reference.updateChildren(updates).await()
        finalItem
    }

    suspend fun deleteCatalogItem(item: CatalogItem): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val node = if (item.isSeries) "webseries" else "movies"
        val updates = mapOf(
            "/$node/${item.id}" to null,
            "/metadata/lastUpdated" to ServerValue.TIMESTAMP
        )
        database.reference.updateChildren(updates).await()
    }

    suspend fun seedOpenSourceDemoCatalog(): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val now = System.currentTimeMillis()
        val categories = listOf(
            Category("cat_action", "Action", CategoryType.BOTH.name, true, 1, now, now),
            Category("cat_scifi", "Sci-Fi", CategoryType.BOTH.name, true, 2, now, now),
            Category("cat_animation", "Animation", CategoryType.BOTH.name, true, 3, now, now),
            Category("cat_drama", "Drama", CategoryType.BOTH.name, true, 4, now, now)
        )
        val demoMovies = listOf(
            CatalogItem(
                id = "open_movie_tears_of_steel",
                title = "Tears of Steel",
                normalizedTitle = "tears of steel",
                overview = "In a dystopian future Amsterdam, a group of warriors and scientists gather at the Oude Kerk to stage a crucial event from the past in a desperate attempt to save the world from destructive robots. (Blender Foundation CC-BY Open Movie)",
                mediaType = MediaType.MOVIE.name,
                releaseYear = 2012,
                releaseDate = "2012-09-26",
                rating = 8.4,
                runtimeMinutes = 12,
                contentAdvisory = "PG-13",
                language = "en",
                posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
                backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
                genres = listOf("Sci-Fi", "Action"),
                categoryIds = listOf("cat_scifi", "cat_action"),
                cast = listOf(
                    CastMember("c1", "Derek de Lint", "Old Thom", ""),
                    CastMember("c2", "Sergio Hasselbaink", "Barley", ""),
                    CastMember("c3", "Vanja Rukavina", "Young Thom", "")
                ),
                trailerUrl = "https://www.youtube.com/watch?v=R6MlUcmOul8",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                isVip = true,
                vipPointsRequired = 100,
                featured = true,
                trending = true,
                popular = true,
                published = true,
                sourceAttribution = "BLENDER_OPEN_MOVIE",
                createdAt = now - 86_400_000L * 3,
                updatedAt = now
            ),
            CatalogItem(
                id = "open_movie_sintel",
                title = "Sintel",
                normalizedTitle = "sintel",
                overview = "A lonely young woman, Sintel, helps and befriends a dragon, whom she calls Scales. But when he is kidnapped by an adult dragon, Sintel decides to embark on a dangerous quest to find her lost friend. (Blender Foundation CC-BY Open Movie)",
                mediaType = MediaType.MOVIE.name,
                releaseYear = 2010,
                releaseDate = "2010-09-27",
                rating = 8.6,
                runtimeMinutes = 15,
                contentAdvisory = "PG",
                language = "en",
                posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
                backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
                genres = listOf("Animation", "Action", "Drama"),
                categoryIds = listOf("cat_animation", "cat_drama"),
                cast = listOf(
                    CastMember("c4", "Halina Reijn", "Sintel", ""),
                    CastMember("c5", "Thom Hoffman", "Shaman", "")
                ),
                trailerUrl = "https://www.youtube.com/watch?v=eRsGyueVLvQ",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                isVip = false,
                vipPointsRequired = 0,
                featured = false,
                trending = true,
                popular = true,
                published = true,
                sourceAttribution = "BLENDER_OPEN_MOVIE",
                createdAt = now - 86_400_000L * 2,
                updatedAt = now
            ),
            CatalogItem(
                id = "open_movie_big_buck_bunny",
                title = "Big Buck Bunny",
                normalizedTitle = "big buck bunny",
                overview = "A large andlovable rabbit deals with three tiny bullies, led by a flying squirrel, who are determined to squuelch his happiness in the forest. (Blender Foundation CC-BY Open Movie)",
                mediaType = MediaType.MOVIE.name,
                releaseYear = 2008,
                releaseDate = "2008-05-30",
                rating = 8.1,
                runtimeMinutes = 10,
                contentAdvisory = "G",
                language = "en",
                posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
                backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
                genres = listOf("Animation", "Comedy"),
                categoryIds = listOf("cat_animation"),
                cast = listOf(
                    CastMember("c6", "Jan Morgenstern", "Composer", ""),
                    CastMember("c7", "Sacha Goedegebure", "Director", "")
                ),
                trailerUrl = "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                isVip = false,
                vipPointsRequired = 0,
                featured = false,
                trending = false,
                popular = true,
                published = true,
                sourceAttribution = "BLENDER_OPEN_MOVIE",
                createdAt = now - 86_400_000L,
                updatedAt = now
            )
        )

        val demoSeries = listOf(
            CatalogItem(
                id = "open_series_blender_chronicles",
                title = "Open Cinema Chronicles",
                normalizedTitle = "open cinema chronicles",
                overview = "An anthology web series showcasing open-source short films and visual effects milestones created with Blender.",
                mediaType = MediaType.SERIES.name,
                releaseYear = 2024,
                releaseDate = "2024-01-15",
                rating = 8.9,
                runtimeMinutes = 12,
                contentAdvisory = "PG-13",
                language = "en",
                posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
                backdropUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
                genres = listOf("Sci-Fi", "Animation"),
                categoryIds = listOf("cat_scifi", "cat_animation"),
                cast = listOf(
                    CastMember("c8", "Tygo Gernandt", "Proog", ""),
                    CastMember("c9", "Cas Jansen", "Emo", "")
                ),
                trailerUrl = "https://www.youtube.com/watch?v=TLkA0RELQ1g",
                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                seasons = listOf(
                    Season(
                        id = "s_1",
                        seasonNumber = 1,
                        title = "Season 1",
                        overview = "Foundational open-source animated stories.",
                        posterUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
                        episodes = listOf(
                            Episode(
                                id = "s1_e1",
                                seasonNumber = 1,
                                episodeNumber = 1,
                                title = "Elephants Dream",
                                overview = "Two strange characters explore a capricious and seemingly infinite machine.",
                                thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
                                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                                runtimeMinutes = 11,
                                airDate = "2006-03-24"
                            ),
                            Episode(
                                id = "s1_e2",
                                seasonNumber = 1,
                                episodeNumber = 2,
                                title = "Sintel's Journey",
                                overview = "Sintel braves the jagged mountains in search of Scales.",
                                thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
                                videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                                runtimeMinutes = 15,
                                airDate = "2010-09-27"
                            )
                        )
                    )
                ),
                isVip = true,
                vipPointsRequired = 100,
                featured = true,
                trending = true,
                popular = true,
                published = true,
                sourceAttribution = "BLENDER_OPEN_MOVIE",
                createdAt = now,
                updatedAt = now
            )
        )

        val updates = HashMap<String, Any?>()
        categories.forEach { updates["/categories/${it.id}"] = it.toFirebaseMap() }
        demoMovies.forEach { updates["/movies/${it.id}"] = it.toFirebaseMap() }
        demoSeries.forEach { updates["/webseries/${it.id}"] = it.toFirebaseMap() }
        updates["/metadata/lastUpdated"] = ServerValue.TIMESTAMP
        database.reference.updateChildren(updates).await()
    }

    suspend fun saveAppUpdateConfig(config: AppUpdateConfig): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val finalConfig = config.copy(updatedAt = System.currentTimeMillis())
        database.reference.child("appUpdate").setValue(finalConfig.toFirebaseMap()).await()
    }

    suspend fun broadcastNotificationToDatabase(payload: PushNotificationPayload): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val now = System.currentTimeMillis()
        val id = payload.id.ifBlank { "notif_$now" }
        val finalPayload = payload.copy(id = id, sentAt = now)
        val updates = mapOf(
            "/notifications/latest" to finalPayload.toFirebaseMap(),
            "/notifications/history/$id" to finalPayload.toFirebaseMap()
        )
        database.reference.updateChildren(updates).await()
    }

suspend fun submitMovieRequestToAdmin(
        item: CatalogItem,
        userRecord: UserPresenceRecord
    ): Result<MovieRequestItem> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val now = System.currentTimeMillis()
        val cleanTitle = item.title.trim().ifBlank { "Requested Title" }
        val reqId = if (item.tmdbId != null && item.tmdbId > 0) {
            "req_${item.mediaType.lowercase()}_${item.tmdbId}"
        } else {
            "req_${UrlValidator.normalizeTitle(cleanTitle).replace(Regex("[^a-z0-9]+"), "_").take(28)}"
        }
        val existingCount = _state.value.movieRequests.find { it.id == reqId }?.requestCount ?: 0
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: userRecord.uid
        val devId = userRecord.deviceId.ifBlank { UserPresenceManager.getRealDeviceId(context) }
        val shareCode = userRecord.shareCode.ifBlank { UserPresenceManager.getFiveDigitShareCode(context) }

        val reqItem = MovieRequestItem(
            id = reqId,
            tmdbId = item.tmdbId,
            title = cleanTitle,
            mediaType = item.mediaType,
            releaseYear = item.releaseYear,
            releaseDate = item.releaseDate,
            rating = item.rating,
            posterUrl = item.posterUrl,
            backdropUrl = item.backdropUrl,
            overview = item.overview,
            requestedByDeviceId = devId,
            requestedByShareCode = shareCode,
            requestedByUid = uid,
            requestCount = existingCount + 1,
            status = "PENDING",
            requestedAt = now
        )

        // 1. Write inside /users/{uid}/movieRequests/{reqId} (always permitted for self uid)
        if (uid.isNotBlank()) {
            runCatching {
                database.reference.child("users").child(uid).child("movieRequests").child(reqId)
                    .setValue(reqItem.toFirebaseMap()).await()
            }
        }
        // 2. Also write to top-level /movieRequests/{reqId}
        runCatching {
            database.reference.child("movieRequests").child(reqId)
                .setValue(reqItem.toFirebaseMap()).await()
        }

        val updatedList = (listOf(reqItem) + _state.value.movieRequests.filterNot { it.id == reqId })
            .sortedByDescending { it.requestedAt }
        _state.value = _state.value.copy(movieRequests = updatedList)
        reqItem
    }

    suspend fun updateMovieRequestStatus(
        req: MovieRequestItem,
        newStatus: String
    ): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val updated = req.copy(status = newStatus)
        runCatching {
            database.reference.child("movieRequests").child(req.id)
                .setValue(updated.toFirebaseMap()).await()
        }
        if (req.requestedByUid.isNotBlank()) {
            runCatching {
                database.reference.child("users").child(req.requestedByUid)
                    .child("movieRequests").child(req.id)
                    .setValue(updated.toFirebaseMap()).await()
            }
        }
        _state.value = _state.value.copy(
            movieRequests = _state.value.movieRequests.map {
                if (it.id == req.id) updated else it
            }
        )
    }

    suspend fun deleteMovieRequest(req: MovieRequestItem): Result<Unit> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        runCatching {
            database.reference.child("movieRequests").child(req.id).removeValue().await()
        }
        if (req.requestedByUid.isNotBlank()) {
            runCatching {
                database.reference.child("users").child(req.requestedByUid)
                    .child("movieRequests").child(req.id).removeValue().await()
            }
        }
        _state.value = _state.value.copy(
            movieRequests = _state.value.movieRequests.filterNot { it.id == req.id }
        )
    }

    suspend fun fetchAllUsersSnapshot(): Result<List<UserPresenceRecord>> = runCatching {
        val database = db ?: error("Firebase Database unavailable.")
        val snap = database.reference.child("users").get().await()
        snap.children.mapNotNull { UserPresenceRecord.fromSnapshot(it) }
    }


    suspend fun recordMediaActivity(
        item: CatalogItem,
        userRecord: UserPresenceRecord,
        actionType: String,
        episodeLabel: String = ""
    ): Result<MediaActivityLog> = runCatching {
        val itemId = item.id.trim()
        require(itemId.isNotBlank()) { "Item ID is blank" }
        val now = System.currentTimeMillis()
        val uid = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: userRecord.uid
        val devId = userRecord.deviceId.ifBlank { UserPresenceManager.getRealDeviceId(context) }
        val shareCode = userRecord.shareCode.ifBlank { UserPresenceManager.getFiveDigitShareCode(context) }
        val deviceModel = userRecord.deviceModel.ifBlank {
            "${android.os.Build.MANUFACTURER.orEmpty()} ${android.os.Build.MODEL.orEmpty()}".trim()
        }
        val userName = userRecord.displayName.ifBlank { "User • $shareCode ($deviceModel)" }
        val cleanAction = if (actionType.equals("DOWNLOAD", ignoreCase = true)) "DOWNLOAD" else "VIEW"
        val logId = "${cleanAction.lowercase()}_${now}_${devId.takeLast(6)}"

        val logEntry = MediaActivityLog(
            id = logId,
            itemId = itemId,
            itemTitle = item.title,
            mediaType = item.mediaType,
            actionType = cleanAction,
            uid = uid,
            deviceId = devId,
            shareCode = shareCode,
            userName = userName,
            deviceModel = deviceModel,
            episodeLabel = episodeLabel,
            timestamp = now
        )

        // Optimistically update in-memory state immediately so UI reflects the new count right away
        val curAnalytics = _state.value.analyticsFor(itemId)
        val updatedAnalytics = if (logEntry.isDownload) {
            val newDownloads = (listOf(logEntry) + curAnalytics.downloadLogs).distinctBy { it.id }
            curAnalytics.copy(
                downloadCount = maxOf(curAnalytics.downloadCount + 1, newDownloads.size),
                downloadLogs = newDownloads
            )
        } else {
            val newViews = (listOf(logEntry) + curAnalytics.viewLogs).distinctBy { it.id }
            curAnalytics.copy(
                viewCount = maxOf(curAnalytics.viewCount + 1, newViews.size),
                viewLogs = newViews
            )
        }
        _state.value = _state.value.copy(
            mediaAnalyticsMap = _state.value.mediaAnalyticsMap + (itemId to updatedAnalytics)
        )

        val database = db ?: return@runCatching logEntry
        // 1. Write inside /users/{uid}/mediaActivityLogs/{logId} (always permitted for self uid)
        if (uid.isNotBlank()) {
            runCatching {
                database.reference.child("users").child(uid).child("mediaActivityLogs").child(logId)
                    .setValue(logEntry.toFirebaseMap()).await()
            }
        }
        // 2. Write to /mediaAnalytics/{itemId}
        val subNode = if (logEntry.isDownload) "downloads" else "views"
        val countField = if (logEntry.isDownload) "downloadCount" else "viewCount"
        val nextCount = if (logEntry.isDownload) updatedAnalytics.downloadCount else updatedAnalytics.viewCount
        runCatching {
            val itemAnalyticsRef = database.reference.child("mediaAnalytics").child(itemId)
            itemAnalyticsRef.child(subNode).child(logId).setValue(logEntry.toFirebaseMap())
            itemAnalyticsRef.child(countField).setValue(nextCount)
            itemAnalyticsRef.child("itemTitle").setValue(item.title)
            itemAnalyticsRef.child("mediaType").setValue(item.mediaType)
            itemAnalyticsRef.child("updatedAt").setValue(now)
        }
        logEntry
    }
}
