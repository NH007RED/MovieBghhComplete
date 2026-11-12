package com.moviebox.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moviebox.app.BuildConfig
import com.moviebox.app.data.local.PreferencesRepository
import com.moviebox.app.data.model.AppPreferences
import com.moviebox.app.data.model.ActorProfileDetails
import com.moviebox.app.data.model.CastMember
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Category
import com.moviebox.app.data.model.MediaType
import com.moviebox.app.data.model.ThemeVariant
import com.moviebox.app.data.model.AppUpdateConfig
import com.moviebox.app.data.model.PushNotificationPayload
import com.moviebox.app.data.model.TmdbImportSelection
import com.moviebox.app.data.model.UserPresenceRecord
import com.moviebox.app.data.model.MovieRequestItem
import com.moviebox.app.data.model.VipAccessConfig
import com.moviebox.app.util.FcmPushSender
import com.moviebox.app.util.ServiceAccountCredentials
import com.moviebox.app.data.remote.TmdbRepository
import com.moviebox.app.data.repository.AdminAuthState
import com.moviebox.app.data.repository.AuthRepository
import com.moviebox.app.data.repository.CatalogRepository
import com.moviebox.app.data.repository.CatalogState
import com.moviebox.app.data.repository.UserPresenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TmdbSearchUiState(
    val isLoading: Boolean = false,
    val results: List<CatalogItem> = emptyList(),
    val selectedPreview: CatalogItem? = null,
    val errorMessage: String? = null
)

class MovieBoxViewModel(app: Application) : AndroidViewModel(app) {

    val prefsRepo = PreferencesRepository(app)
    val catalogRepo = CatalogRepository(app)
    val authRepo = AuthRepository(app)
    val tmdbRepo = TmdbRepository()

    val catalogState: StateFlow<CatalogState> = catalogRepo.state
    val authState: StateFlow<AdminAuthState> = authRepo.authState
    val currentUserRecord: StateFlow<UserPresenceRecord> = UserPresenceManager.currentUserRecord
    val preferences: StateFlow<AppPreferences> = prefsRepo.preferencesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppPreferences()
    )

    private val _tmdbState = MutableStateFlow(TmdbSearchUiState())
    val tmdbState: StateFlow<TmdbSearchUiState> = _tmdbState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    private val _enrichedItemMap = MutableStateFlow<Map<String, CatalogItem>>(emptyMap())
    val enrichedItemMap: StateFlow<Map<String, CatalogItem>> = _enrichedItemMap.asStateFlow()

    private val _actorDetailsMap = MutableStateFlow<Map<String, ActorProfileDetails>>(emptyMap())
    val actorDetailsMap: StateFlow<Map<String, ActorProfileDetails>> = _actorDetailsMap.asStateFlow()

    fun loadActorProfileDetails(member: CastMember) {
        val cacheKey = "${member.id}_${member.name}"
        if (_actorDetailsMap.value.containsKey(cacheKey)) return
        viewModelScope.launch {
            val keyOverride = preferences.value.tmdbApiKeyOverride
            tmdbRepo.fetchActorProfileDetails(member, keyOverride).onSuccess { details ->
                _actorDetailsMap.value = _actorDetailsMap.value + (cacheKey to details)
            }
        }
    }

    private val _indianPosterUrls = MutableStateFlow<List<String>>(
        listOf(
            "https://image.tmdb.org/t/p/w500/nEufeZlyAOLqO2brrs0yeF1lgXO.jpg",
            "https://image.tmdb.org/t/p/w500/j73Pq3K3I9bJc0kH3xZ9wM8P2sL.jpg",
            "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
            "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
            "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg",
            "https://image.tmdb.org/t/p/w500/rCzpDGLbOoPwLjy3OAm5NUPOTrC.jpg"
        )
    )
    val indianPosterUrls: StateFlow<List<String>> = _indianPosterUrls.asStateFlow()

    // Curated + Live TMDB Upcoming Movies & Web Series with future release dates for countdown timer
    private val _upcomingItems = MutableStateFlow<List<CatalogItem>>(
        listOf(
            CatalogItem(
                id = "upcoming_movie_pushpa_rule",
                tmdbId = 896117,
                title = "Toxic: A Fairy Tale for Grown-Ups",
                overview = "An upcoming high-octane action drama thriller set in a coastal underworld empire.",
                mediaType = MediaType.MOVIE.name,
                releaseYear = 2027,
                releaseDate = "2027-03-19",
                status = "Upcoming",
                rating = 8.9,
                runtimeMinutes = 165,
                genres = listOf("Action", "Crime", "Thriller"),
                posterUrl = "https://image.tmdb.org/t/p/w500/nEufeZlyAOLqO2brrs0yeF1lgXO.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/nEufeZlyAOLqO2brrs0yeF1lgXO.jpg"
            ),
            CatalogItem(
                id = "upcoming_movie_war_2",
                tmdbId = 1108427,
                title = "Avatar: Fire and Ash",
                overview = "Jake Sully and Neytiri encounter the Ash People, a new Na'vi clan on Pandora.",
                mediaType = MediaType.MOVIE.name,
                releaseYear = 2027,
                releaseDate = "2027-05-14",
                status = "Upcoming",
                rating = 9.1,
                runtimeMinutes = 182,
                genres = listOf("Sci-Fi", "Adventure", "Action"),
                posterUrl = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"
            ),
            CatalogItem(
                id = "upcoming_series_stranger_finale",
                tmdbId = 66732,
                title = "Stranger Things: The Final Chapter",
                overview = "The final epic showdown in Hawkins as the boundary between the Upside Down and the real world collapses.",
                mediaType = MediaType.SERIES.name,
                releaseYear = 2027,
                releaseDate = "2027-01-15",
                status = "Upcoming",
                rating = 9.2,
                runtimeMinutes = 60,
                genres = listOf("Sci-Fi", "Mystery", "Drama"),
                posterUrl = "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/49WJfeN0moxb9IPfGn8AIqMGskD.jpg"
            ),
            CatalogItem(
                id = "upcoming_series_squid_world",
                tmdbId = 93405,
                title = "Squid Game: Global Arena",
                overview = "New high-stakes survival games unfold on an international stage with an all-new cast of players.",
                mediaType = MediaType.SERIES.name,
                releaseYear = 2027,
                releaseDate = "2027-08-22",
                status = "Upcoming",
                rating = 8.8,
                runtimeMinutes = 55,
                genres = listOf("Thriller", "Action", "Drama"),
                posterUrl = "https://image.tmdb.org/t/p/w500/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w1280/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg"
            )
        )
    )
    val upcomingItems: StateFlow<List<CatalogItem>> = _upcomingItems.asStateFlow()

    private val _userSearchTmdbResults = MutableStateFlow<List<CatalogItem>>(emptyList())
    val userSearchTmdbResults: StateFlow<List<CatalogItem>> = _userSearchTmdbResults.asStateFlow()

    private val _isUserSearchingTmdb = MutableStateFlow(false)
    val isUserSearchingTmdb: StateFlow<Boolean> = _isUserSearchingTmdb.asStateFlow()

    private val _notifiedRequestKeys = MutableStateFlow<Set<String>>(emptySet())
    val notifiedRequestKeys: StateFlow<Set<String>> = _notifiedRequestKeys.asStateFlow()

    fun refreshUpcomingCatalog() {
        viewModelScope.launch {
            val keyOverride = preferences.value.tmdbApiKeyOverride
            tmdbRepo.fetchUpcomingMoviesAndSeries(keyOverride).onSuccess { fetched ->
                if (fetched.isNotEmpty()) {
                    val combined = (fetched + _upcomingItems.value)
                        .distinctBy { "${it.mediaType}_${it.tmdbId ?: it.normalizedTitle}" }
                    _upcomingItems.value = combined
                }
            }
        }
    }

    fun searchAllMoviesAndSeriesForUser(query: String) {
        val clean = query.trim()
        if (clean.length < 2) {
            _userSearchTmdbResults.value = emptyList()
            _isUserSearchingTmdb.value = false
            return
        }
        _isUserSearchingTmdb.value = true
        viewModelScope.launch {
            val keyOverride = preferences.value.tmdbApiKeyOverride
            tmdbRepo.searchCombinedMoviesAndSeries(clean, keyOverride)
                .onSuccess { list ->
                    _userSearchTmdbResults.value = list
                    _isUserSearchingTmdb.value = false
                }
                .onFailure {
                    _isUserSearchingTmdb.value = false
                }
        }
    }

    fun notifyAdminToRequestMovie(item: CatalogItem) {
        val key = "${item.mediaType}_${item.tmdbId ?: item.title.lowercase()}"
        _notifiedRequestKeys.value = _notifiedRequestKeys.value + key
        viewModelScope.launch {
            catalogRepo.submitMovieRequestToAdmin(item, currentUserRecord.value)
                .onSuccess {
                    showToast("Sent '${item.title}' request to Admin!")
                }
                .onFailure { e ->
                    showToast("Request saved locally: ${e.message ?: "Sent to Admin"}")
                }
        }
    }

    fun markMovieRequestStatus(req: MovieRequestItem, status: String) {
        viewModelScope.launch {
            catalogRepo.updateMovieRequestStatus(req, status)
                .onSuccess { showToast("Marked '${req.title}' as $status.") }
        }
    }

    fun deleteMovieRequest(req: MovieRequestItem) {
        viewModelScope.launch {
            catalogRepo.deleteMovieRequest(req)
                .onSuccess { showToast("Removed request for '${req.title}'.") }
        }
    }

    init {
        runCatching { UserPresenceManager.start(app) }
        FcmPushSender.subscribeAppToAllUsersTopic()
        catalogRepo.startListening()
        refreshUpcomingCatalog()
        refreshCacheSize()
        viewModelScope.launch {
            authRepo.refreshSession()
        }
        viewModelScope.launch {
            authRepo.authState.collect { st ->
                if (st is AdminAuthState.VerifiedAdmin) {
                    catalogRepo.attachUsersListener()
                } else {
                    catalogRepo.detachUsersListener()
                }
            }
        }
        viewModelScope.launch {
            val keyOverride = preferences.value.tmdbApiKeyOverride
            tmdbRepo.fetchIndianMoviePosters(keyOverride).onSuccess { urls ->
                if (urls.isNotEmpty()) {
                    _indianPosterUrls.value = urls
                }
            }
        }
    }

    fun showToast(msg: String) {
        _toast.value = msg
    }

    fun clearToast() {
        _toast.value = null
    }

    fun refreshCacheSize() {
        _cacheSizeBytes.value = prefsRepo.getCacheSizeBytes()
    }

    fun clearCache() {
        val ok = prefsRepo.clearAppCache()
        refreshCacheSize()
        showToast(if (ok) "Cache cleared." else "Could not clear cache.")
    }

    // ---- 5-Digit Share Code Redeem & VIP Points System ----
    fun redeemShareCode(inputCode: String, onSuccess: () -> Unit = {}) {
        val vipCfg = catalogState.value.vipAccessConfig
        UserPresenceManager.redeemShareCode(
            rawCode = inputCode,
            pointsPerReferral = vipCfg.pointsPerReferral
        ) { success, message ->
            showToast(message)
            if (success) {
                onSuccess()
            }
        }
    }

    fun unlockVipItemIfEligible(item: CatalogItem): Boolean {
        val user = currentUserRecord.value
        val globalPts = catalogState.value.vipAccessConfig.vipPointsToAccess
        if (user.hasVipAccessFor(item, globalPts)) {
            if (item.isVip) {
                UserPresenceManager.markItemUnlocked(item.id)
            }
            return true
        }
        return false
    }

    fun saveVipAccessConfig(config: VipAccessConfig) {
        viewModelScope.launch {
            catalogRepo.saveVipAccessConfig(config)
                .onSuccess {
                    showToast("VIP Points settings saved (${config.vipPointsToAccess} pts for VIP access)!")
                }
                .onFailure { e ->
                    showToast("Failed to save VIP config: ${e.message}")
                }
        }
    }

    fun adminUpdateUserRecord(
        user: UserPresenceRecord,
        newPoints: Int,
        newShareCount: Int,
        isVip: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            catalogRepo.adminUpdateUserRecord(user, newPoints, newShareCount, isVip)
                .onSuccess {
                    showToast("Updated user (${user.shareCode}) — $newPoints pts, $newShareCount shares.")
                    onSuccess()
                }
                .onFailure { e ->
                    showToast("Update failed: ${e.message}")
                }
        }
    }

    // ---- User Preferences & Watchlist ----
    fun setThemeVariant(variant: ThemeVariant) {
        viewModelScope.launch { prefsRepo.setThemeVariant(variant) }
    }

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch { prefsRepo.setDefaultQuality(quality) }
    }

    fun setAutoPlayNextEpisode(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoPlayNextEpisode(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setNotificationsEnabled(enabled) }
    }

    fun toggleWatchlist(itemId: String) {
        viewModelScope.launch {
            val wasInList = itemId in preferences.value.watchlistIds
            prefsRepo.toggleWatchlist(itemId)
            showToast(if (wasInList) "Removed from My List" else "Added to My List")
        }
    }

    fun removeFromWatchlist(itemId: String) {
        viewModelScope.launch {
            prefsRepo.removeFromWatchlist(itemId)
            showToast("Removed from My List")
        }
    }

    fun clearWatchlist() {
        viewModelScope.launch {
            prefsRepo.clearWatchlist()
            showToast("Watchlist cleared")
        }
    }

    fun saveTmdbApiKeyOverride(apiKey: String) {
        viewModelScope.launch {
            prefsRepo.setTmdbApiKeyOverride(apiKey)
            showToast("TMDB API key updated")
        }
    }

    val hasTmdbKeyConfigured: Boolean
        get() = tmdbRepo.resolveApiKey(preferences.value.tmdbApiKeyOverride).isNotBlank() ||
            BuildConfig.TMDB_API_KEY.isNotBlank()

    fun ensureCastImagesLoaded(item: CatalogItem) {
        val needsEnrichment = item.cast.isEmpty() ||
            item.cast.all { it.profileUrl.isBlank() } ||
            item.posterGallery.size <= 1 ||
            item.backdropGallery.size <= 1 ||
            item.companies.isEmpty()
        if (!needsEnrichment) return
        if (_enrichedItemMap.value.containsKey(item.id)) return

        viewModelScope.launch {
            val keyOverride = preferences.value.tmdbApiKeyOverride
            tmdbRepo.fetchCastAndDetailsForItem(item, keyOverride).onSuccess { detailed ->
                val merged = item.copy(
                    tmdbId = item.tmdbId ?: detailed.tmdbId,
                    originalTitle = item.originalTitle.ifBlank { detailed.originalTitle },
                    tagline = item.tagline.ifBlank { detailed.tagline },
                    releaseDate = item.releaseDate.ifBlank { detailed.releaseDate },
                    status = detailed.status.ifBlank { item.status },
                    runtimeMinutes = item.runtimeMinutes.takeIf { it > 0 } ?: detailed.runtimeMinutes,
                    country = detailed.country.ifBlank { item.country },
                    budget = if (item.budget > 0L) item.budget else detailed.budget,
                    revenue = if (item.revenue > 0L) item.revenue else detailed.revenue,
                    companies = if (item.companies.isNotEmpty()) item.companies else detailed.companies,
                    posterGallery = detailed.posterGallery.ifEmpty { listOfNotNull(item.posterUrl.takeIf { it.isNotBlank() }) },
                    backdropGallery = detailed.backdropGallery.ifEmpty { listOfNotNull(item.backdropUrl.takeIf { it.isNotBlank() }) },
                    totalPostersCount = detailed.totalPostersCount.coerceAtLeast(detailed.posterGallery.size),
                    totalBackdropsCount = detailed.totalBackdropsCount.coerceAtLeast(detailed.backdropGallery.size),
                    cast = if (detailed.cast.isNotEmpty()) detailed.cast else item.cast,
                    trailerUrl = item.trailerUrl.ifBlank { detailed.trailerUrl }
                )
                _enrichedItemMap.value = _enrichedItemMap.value + (item.id to merged)
                if (authState.value is AdminAuthState.VerifiedAdmin) {
                    catalogRepo.saveCatalogItem(merged)
                }
            }
        }
    }

    // ---- Admin Authentication ----
    fun signInAdmin(email: String, password: String) {
        viewModelScope.launch {
            authRepo.signInAdmin(email, password)
        }
    }

    fun signOutAdmin() {
        authRepo.signOut()
        showToast("Signed out of Admin Panel")
    }

    // ---- Admin Category CRUD ----
    fun saveCategory(category: Category, onSuccess: () -> Unit = {}) {
        if (category.name.isBlank()) {
            showToast("Category name is required.")
            return
        }
        viewModelScope.launch {
            val res = catalogRepo.saveCategory(category)
            res.onSuccess {
                showToast("Category '${category.name}' saved.")
                onSuccess()
            }.onFailure { e ->
                showToast("Failed to save category: ${e.message}")
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            catalogRepo.deleteCategory(categoryId)
                .onSuccess { showToast("Category deleted.") }
                .onFailure { showToast("Delete failed: ${it.message}") }
        }
    }

    fun countItemsUsingCategory(categoryId: String): Int =
        catalogRepo.countItemsUsingCategory(categoryId)

    // ---- Admin Movie & Series CRUD ----
    fun saveCatalogItem(item: CatalogItem, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            catalogRepo.saveCatalogItem(item)
                .onSuccess {
                    showToast("'${it.title}' saved to Firebase Realtime Database.")
                    onSuccess()
                }
                .onFailure { e ->
                    showToast(e.message ?: "Failed to save item.")
                }
        }
    }

    fun deleteCatalogItem(item: CatalogItem, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            catalogRepo.deleteCatalogItem(item)
                .onSuccess {
                    showToast("'${item.title}' deleted.")
                    onSuccess()
                }
                .onFailure { e ->
                    showToast("Delete failed: ${e.message}")
                }
        }
    }

    fun togglePublish(item: CatalogItem) {
        viewModelScope.launch {
            val updated = item.copy(published = !item.published)
            catalogRepo.saveCatalogItem(updated)
                .onSuccess {
                    showToast("'${item.title}' is now ${if (updated.published) "Published" else "Draft"}.")
                }
                .onFailure { e ->
                    showToast("Update failed: ${e.message}")
                }
        }
    }

    fun seedOpenDemoCatalog() {
        viewModelScope.launch {
            catalogRepo.seedOpenSourceDemoCatalog()
                .onSuccess { showToast("Open-source demo catalog seeded to Firebase RTDB.") }
                .onFailure { showToast("Could not seed catalog: ${it.message}") }
        }
    }

    // ---- TMDB Search, Lookup & Import ----
    fun searchTmdb(query: String, isSeries: Boolean) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        val keyOverride = preferences.value.tmdbApiKeyOverride
        _tmdbState.value = _tmdbState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val asId = clean.toIntOrNull()
            if (asId != null) {
                val res = if (isSeries) tmdbRepo.fetchSeriesById(asId, keyOverride)
                else tmdbRepo.fetchMovieById(asId, keyOverride)
                res.onSuccess { item ->
                    _tmdbState.value = TmdbSearchUiState(
                        isLoading = false,
                        results = listOf(item),
                        selectedPreview = item
                    )
                }.onFailure { e ->
                    _tmdbState.value = TmdbSearchUiState(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            } else {
                val res = if (isSeries) tmdbRepo.searchSeries(clean, keyOverride)
                else tmdbRepo.searchMovies(clean, keyOverride)
                res.onSuccess { list ->
                    val firstSummary = list.firstOrNull()
                    val firstDetailed = if (firstSummary?.tmdbId != null) {
                        val det = if (isSeries) tmdbRepo.fetchSeriesById(firstSummary.tmdbId, keyOverride)
                        else tmdbRepo.fetchMovieById(firstSummary.tmdbId, keyOverride)
                        det.getOrDefault(firstSummary)
                    } else {
                        firstSummary
                    }
                    _tmdbState.value = TmdbSearchUiState(
                        isLoading = false,
                        results = list,
                        selectedPreview = firstDetailed,
                        errorMessage = if (list.isEmpty()) "No TMDB results found for '$clean'." else null
                    )
                }.onFailure { e ->
                    _tmdbState.value = TmdbSearchUiState(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    fun selectTmdbPreview(item: CatalogItem) {
        val keyOverride = preferences.value.tmdbApiKeyOverride
        _tmdbState.value = _tmdbState.value.copy(selectedPreview = item, isLoading = true)
        val tmdbId = item.tmdbId ?: run {
            _tmdbState.value = _tmdbState.value.copy(isLoading = false)
            return
        }
        viewModelScope.launch {
            val fullRes = if (item.isSeries) tmdbRepo.fetchSeriesById(tmdbId, keyOverride)
            else tmdbRepo.fetchMovieById(tmdbId, keyOverride)
            fullRes.onSuccess { detailed ->
                _tmdbState.value = _tmdbState.value.copy(isLoading = false, selectedPreview = detailed)
            }.onFailure {
                _tmdbState.value = _tmdbState.value.copy(isLoading = false)
            }
        }
    }

    fun fetchTmdbQuickFill(
        idOrTitle: String,
        isSeries: Boolean,
        onResult: (CatalogItem) -> Unit
    ) {
        val clean = idOrTitle.trim()
        if (clean.isEmpty()) {
            showToast("Enter a TMDB ID or Title first.")
            return
        }
        val keyOverride = preferences.value.tmdbApiKeyOverride
        viewModelScope.launch {
            val id = clean.toIntOrNull()
            if (id != null) {
                val res = if (isSeries) tmdbRepo.fetchSeriesById(id, keyOverride)
                else tmdbRepo.fetchMovieById(id, keyOverride)
                res.onSuccess {
                    showToast("Fetched '${it.title}' (${it.cast.size} cast members) from TMDB.")
                    onResult(it)
                }.onFailure {
                    showToast(it.message ?: "TMDB lookup failed.")
                }
            } else {
                val searchRes = if (isSeries) tmdbRepo.searchSeries(clean, keyOverride)
                else tmdbRepo.searchMovies(clean, keyOverride)
                searchRes.onSuccess { list ->
                    val first = list.firstOrNull()
                    if (first == null) {
                        showToast("No TMDB match found for '$clean'.")
                    } else if (first.tmdbId != null) {
                        val detailRes = if (isSeries) tmdbRepo.fetchSeriesById(first.tmdbId, keyOverride)
                        else tmdbRepo.fetchMovieById(first.tmdbId, keyOverride)
                        val finalItem = detailRes.getOrDefault(first)
                        showToast("Fetched '${finalItem.title}' (${finalItem.cast.size} cast members) from TMDB.")
                        onResult(finalItem)
                    } else {
                        onResult(first)
                    }
                }.onFailure {
                    showToast(it.message ?: "TMDB search failed.")
                }
            }
        }
    }

    fun findExistingByTmdbId(tmdbId: Int?, isSeries: Boolean): CatalogItem? {
        if (tmdbId == null) return null
        return catalogRepo.findDuplicateByTmdbId(
            tmdbId,
            if (isSeries) MediaType.SERIES else MediaType.MOVIE
        )
    }

    fun applySelectionAndSaveTmdb(
        preview: CatalogItem,
        selection: TmdbImportSelection,
        manualVideoUrl: String,
        selectedCategoryIds: List<String>,
        existingToOverwrite: CatalogItem? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val keyOverride = preferences.value.tmdbApiKeyOverride
            val resolvedPreview = if (preview.cast.isEmpty() && preview.tmdbId != null) {
                val det = if (preview.isSeries) tmdbRepo.fetchSeriesById(preview.tmdbId, keyOverride)
                else tmdbRepo.fetchMovieById(preview.tmdbId, keyOverride)
                det.getOrDefault(preview)
            } else {
                preview
            }

            val now = System.currentTimeMillis()
            val base = existingToOverwrite ?: CatalogItem(
                id = resolvedPreview.id,
                mediaType = resolvedPreview.mediaType,
                createdAt = now
            )
            val merged = base.copy(
                tmdbId = resolvedPreview.tmdbId,
                title = if (selection.importTitle || base.title.isBlank()) resolvedPreview.title else base.title,
                normalizedTitle = if (selection.importTitle || base.title.isBlank()) resolvedPreview.normalizedTitle else base.normalizedTitle,
                overview = if (selection.importOverview) resolvedPreview.overview else base.overview,
                releaseYear = if (selection.importYearAndRating) resolvedPreview.releaseYear else base.releaseYear,
                releaseDate = if (selection.importYearAndRating) resolvedPreview.releaseDate else base.releaseDate,
                rating = if (selection.importYearAndRating) resolvedPreview.rating else base.rating,
                runtimeMinutes = resolvedPreview.runtimeMinutes.takeIf { it > 0 } ?: base.runtimeMinutes,
                contentAdvisory = resolvedPreview.contentAdvisory.ifBlank { base.contentAdvisory },
                language = resolvedPreview.language.ifBlank { base.language },
                posterUrl = if (selection.importPoster) resolvedPreview.posterUrl else base.posterUrl,
                backdropUrl = if (selection.importBackdrop) resolvedPreview.backdropUrl else base.backdropUrl,
                genres = resolvedPreview.genres.ifEmpty { base.genres },
                categoryIds = selectedCategoryIds.ifEmpty { base.categoryIds },
                cast = if (selection.importCast) resolvedPreview.cast else base.cast,
                trailerUrl = if (selection.importTrailer) resolvedPreview.trailerUrl else base.trailerUrl,
                videoUrl = manualVideoUrl.trim().ifBlank { preview.primaryStreamUrl.ifBlank { base.primaryStreamUrl } },
                videoUrl1080p = preview.videoUrl1080p.ifBlank { manualVideoUrl.trim().ifBlank { base.videoUrl1080p } },
                videoUrl720p = preview.videoUrl720p.ifBlank { base.videoUrl720p },
                videoUrl480p = preview.videoUrl480p.ifBlank { base.videoUrl480p },
                seasons = if (selection.importSeasons) resolvedPreview.seasons else base.seasons,
                isVip = preview.isVip,
                vipPointsRequired = preview.vipPointsRequired,
                sourceAttribution = "TMDB",
                importedAt = now,
                updatedAt = now
            )
            saveCatalogItem(merged, onSuccess)
        }
    }

    private val _serviceAccountCreds = MutableStateFlow<ServiceAccountCredentials?>(null)
    val serviceAccountCreds: StateFlow<ServiceAccountCredentials?> = _serviceAccountCreds.asStateFlow()

    private val _serviceAccountFileName = MutableStateFlow("")
    val serviceAccountFileName: StateFlow<String> = _serviceAccountFileName.asStateFlow()

    private val _isSendingPush = MutableStateFlow(false)
    val isSendingPush: StateFlow<Boolean> = _isSendingPush.asStateFlow()

    fun setServiceAccountCredentials(creds: ServiceAccountCredentials, fileName: String) {
        _serviceAccountCreds.value = creds
        _serviceAccountFileName.value = fileName
        showToast("Valid Service Account JSON loaded (${creds.projectId})")
    }

    fun refreshUsersForPush() {
        viewModelScope.launch {
            catalogRepo.fetchAllUsersSnapshot().onSuccess { list ->
                showToast("Fetched ${list.size} registered user record(s) + topic 'moviebox_all'")
            }
        }
    }

    fun sendPushNotificationToAllUsers(
        creds: ServiceAccountCredentials,
        item: CatalogItem?,
        title: String,
        body: String,
        imageUrl: String,
        onComplete: () -> Unit = {}
    ) {
        if (title.isBlank() || body.isBlank()) {
            showToast("Title and body message are required.")
            return
        }
        _isSendingPush.value = true
        viewModelScope.launch {
            val userTokens = catalogState.value.users.mapNotNull { it.fcmToken.takeIf(String::isNotBlank) }
            val fcmRes = FcmPushSender.sendPushNotificationHttpV1(
                creds = creds,
                title = title,
                body = body,
                imageUrl = imageUrl,
                itemId = item?.id.orEmpty(),
                userTokens = userTokens
            )
            catalogRepo.broadcastNotificationToDatabase(
                PushNotificationPayload(
                    id = "notif_${System.currentTimeMillis()}",
                    itemId = item?.id.orEmpty(),
                    title = title,
                    body = body,
                    imageUrl = imageUrl,
                    sentAt = System.currentTimeMillis()
                )
            )
            FcmPushSender.showSystemNotification(
                context = getApplication(),
                title = title,
                body = body,
                imageUrl = imageUrl
            )
            _isSendingPush.value = false
            fcmRes.onSuccess {
                showToast("Push notification sent to all users!")
                onComplete()
            }.onFailure { e ->
                showToast("Broadcasted to DB; FCM v1 status: ${e.message}")
                onComplete()
            }
        }
    }

    fun saveAndPublishAppUpdate(config: AppUpdateConfig) {
        viewModelScope.launch {
            catalogRepo.saveAppUpdateConfig(config)
                .onSuccess {
                    showToast("In-App Update v${config.versionName} published to all users!")
                }
                .onFailure { e ->
                    showToast("Failed to save update config: ${e.message}")
                }
        }
    }


    private val recordedViewSessionKeys = mutableSetOf<String>()

    fun recordItemView(item: CatalogItem, episodeLabel: String = "") {
        val cleanId = item.id.trim()
        if (cleanId.isBlank()) return
        val sessionKey = "${cleanId}_${episodeLabel}"
        if (!recordedViewSessionKeys.add(sessionKey)) return
        viewModelScope.launch {
            catalogRepo.recordMediaActivity(
                item = item,
                userRecord = currentUserRecord.value,
                actionType = "VIEW",
                episodeLabel = episodeLabel
            )
        }
    }

    fun recordItemDownload(item: CatalogItem, episodeLabel: String = "") {
        val cleanId = item.id.trim()
        if (cleanId.isBlank()) return
        viewModelScope.launch {
            catalogRepo.recordMediaActivity(
                item = item,
                userRecord = currentUserRecord.value,
                actionType = "DOWNLOAD",
                episodeLabel = episodeLabel
            )
        }
    }
}
