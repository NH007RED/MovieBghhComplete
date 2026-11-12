package com.moviebox.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.repository.UserPresenceManager
import com.moviebox.app.security.NhNativeSecurityVault
import com.moviebox.app.ui.MovieBoxViewModel
import com.moviebox.app.ui.admin.AdminShellScreen
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.MovieBoxTheme
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusBlue
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import com.moviebox.app.ui.user.DetailsScreen
import com.moviebox.app.ui.user.HomeScreen
import com.moviebox.app.ui.user.InAppUpdateDialog
import com.moviebox.app.ui.user.MoviesScreen
import com.moviebox.app.ui.user.NotificationsScreen
import com.moviebox.app.ui.user.OfflineDownloadsScreen
import com.moviebox.app.ui.user.SearchScreen
import com.moviebox.app.ui.user.SplashScreen
import com.moviebox.app.ui.user.UserSettingsScreen
import com.moviebox.app.ui.user.VideoPlayerScreen
import com.moviebox.app.ui.user.WatchlistScreen
import com.moviebox.app.ui.user.WebSeriesScreen
import com.moviebox.app.util.FcmPushSender
import com.moviebox.app.util.OfflineDownloadManager
import kotlin.math.roundToInt

private enum class UserRootTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    MOVIES("Movies", Icons.Filled.Movie),
    WEB_SERIES("Web Series", Icons.Filled.LiveTv),
    MY_LIST("My List", Icons.Filled.Bookmark),
    PROFILE("Settings", Icons.Filled.Settings)
}

private sealed class ActiveOverlay {
    data object None : ActiveOverlay()
    data object Search : ActiveOverlay()
    data object Notifications : ActiveOverlay()
    data object OfflineDownloads : ActiveOverlay()
    data class Details(
        val item: CatalogItem,
        val previousItem: CatalogItem? = null
    ) : ActiveOverlay()
    data class Player(
        val title: String,
        val streamUrl: String,
        val movieItem: CatalogItem? = null,
        val seriesItem: CatalogItem? = null,
        val seasonNum: Int? = null,
        val episodeNum: Int? = null
    ) : ActiveOverlay()
    data object AdminPanel : ActiveOverlay()
}

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        UserPresenceManager.setForeground(true)
    }

    override fun onStop() {
        UserPresenceManager.setForeground(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        setContent {
            val vm: MovieBoxViewModel = viewModel()
            val prefs by vm.preferences.collectAsStateWithLifecycle()

            MovieBoxTheme(variant = prefs.themeVariant) {
                MovieBoxRootApp(vm = vm)
            }
        }
    }
}

@Composable
fun MovieBoxRootApp(vm: MovieBoxViewModel) {
    var showSplash by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(UserRootTab.HOME) }
    var activeOverlay by remember { mutableStateOf<ActiveOverlay>(ActiveOverlay.None) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }

    val catalogState by vm.catalogState.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()
    val currentUserRecord by vm.currentUserRecord.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val cacheSizeBytes by vm.cacheSizeBytes.collectAsStateWithLifecycle()
    val toastMsg by vm.toast.collectAsStateWithLifecycle()
    val enrichedItemMap by vm.enrichedItemMap.collectAsStateWithLifecycle()
    val actorDetailsMap by vm.actorDetailsMap.collectAsStateWithLifecycle()
    val indianPosterUrls by vm.indianPosterUrls.collectAsStateWithLifecycle()
    val upcomingItems by vm.upcomingItems.collectAsStateWithLifecycle()
    val userSearchTmdbResults by vm.userSearchTmdbResults.collectAsStateWithLifecycle()
    val isUserSearchingTmdb by vm.isUserSearchingTmdb.collectAsStateWithLifecycle()
    val notifiedRequestKeys by vm.notifiedRequestKeys.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var dismissedUpdateTimestamp by rememberSaveable { mutableStateOf(0L) }
    var lastShownNotificationId by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        OfflineDownloadManager.ensureInitialized(context)
    }
    val offlineDownloadsMap by OfflineDownloadManager.downloadsMap.collectAsStateWithLifecycle()
    val offlineDownloadsList = remember(offlineDownloadsMap) {
        offlineDownloadsMap.values.toList()
    }
    val isDeviceOnline by OfflineDownloadManager.observeNetworkOnline(context)
        .collectAsStateWithLifecycle(initialValue = OfflineDownloadManager.isNetworkAvailable(context))

    LaunchedEffect(isDeviceOnline) {
        if (!isDeviceOnline) {
            showSplash = false
            if (activeOverlay !is ActiveOverlay.Player) {
                activeOverlay = ActiveOverlay.OfflineDownloads
            }
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    LaunchedEffect(catalogState.latestNotification?.id) {
        val notif = catalogState.latestNotification
        if (notif != null && notif.id.isNotBlank()) {
            if (lastShownNotificationId.isEmpty()) {
                lastShownNotificationId = notif.id
            } else if (notif.id != lastShownNotificationId) {
                lastShownNotificationId = notif.id
                FcmPushSender.showSystemNotification(
                    context = context,
                    title = notif.title,
                    body = notif.body,
                    imageUrl = notif.imageUrl
                )
            }
        }
    }

    LaunchedEffect(toastMsg) {
        val msg = toastMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearToast()
        }
    }

    val nativeSecurityInfo = remember(context) {
        NhNativeSecurityVault.getDeveloperInfo(context)
    }

    if (nativeSecurityInfo.isTampered) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0C10))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "SECURITY TAMPER DETECTED",
                    color = CrimsonRed,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = nativeSecurityInfo.tamperReason
                        ?: "Native library libnh_security.so was modified or removed. Unauthorized APK modification is blocked.",
                    color = TextSecondary,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    if (showSplash) {
        SplashScreen(
            indianPosterUrls = indianPosterUrls,
            catalogMovies = catalogState.movies.filter { it.published },
            catalogSeries = catalogState.series.filter { it.published },
            onFinished = { showSplash = false }
        )
        return
    }

    val updateCfg = catalogState.appUpdateConfig
    val isUpdateAvailable = updateCfg != null &&
        updateCfg.apkUrl.isNotBlank() &&
        (updateCfg.versionCode > BuildConfig.VERSION_CODE ||
            !updateCfg.versionName.equals(BuildConfig.VERSION_NAME, ignoreCase = true)) &&
        updateCfg.updatedAt != dismissedUpdateTimestamp

    if (isUpdateAvailable && updateCfg != null && activeOverlay !is ActiveOverlay.AdminPanel) {
        InAppUpdateDialog(
            updateConfig = updateCfg,
            onDismiss = { dismissedUpdateTimestamp = updateCfg.updatedAt }
        )
    }

    // Non-Home tabs return to Home first; pressing Back on Home opens the Cartoon Animated Exit Dialog
    BackHandler(enabled = activeOverlay == ActiveOverlay.None && currentTab != UserRootTab.HOME) {
        currentTab = UserRootTab.HOME
    }

    BackHandler(enabled = activeOverlay == ActiveOverlay.None && currentTab == UserRootTab.HOME && !showExitDialog) {
        showExitDialog = true
    }

    if (showExitDialog) {
        AnimatedCartoonExitDialog(
            onStay = { showExitDialog = false },
            onExitApp = {
                showExitDialog = false
                (context as? ComponentActivity)?.finish()
            }
        )
    }

    val allPublished = remember(catalogState.movies, catalogState.series) {
        (catalogState.movies + catalogState.series)
            .filter { it.published }
            .sortedByDescending { it.updatedAt }
    }

    val publishedMoviesOnly = remember(catalogState.movies) {
        catalogState.movies.filter { it.published }.sortedByDescending { it.updatedAt }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = TextWhite,
                    actionColor = CrimsonLight
                )
            }
        },
        bottomBar = {
            if (activeOverlay == ActiveOverlay.None) {
                PremiumGlassNavigationBar(
                    currentTab = currentTab,
                    onSelectTab = { selectedTab -> currentTab = selectedTab }
                )
            }
        }
    ) { innerPadding ->
        val boxModifier = if (activeOverlay is ActiveOverlay.Player) {
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        } else if (activeOverlay is ActiveOverlay.Details) {
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = innerPadding.calculateBottomPadding())
        } else {
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        }
        Box(modifier = boxModifier) {
            when (val overlay = activeOverlay) {
                is ActiveOverlay.AdminPanel -> {
                    AdminShellScreen(
                        vm = vm,
                        catalogState = catalogState,
                        authState = authState,
                        preferences = prefs,
                        cacheSizeBytes = cacheSizeBytes,
                        onExitAdminToUserApp = { activeOverlay = ActiveOverlay.None }
                    )
                }

                is ActiveOverlay.Player -> {
                    val similarForMovie = remember(overlay.movieItem, publishedMoviesOnly) {
                        val cur = overlay.movieItem
                        if (cur == null) publishedMoviesOnly.take(10)
                        else publishedMoviesOnly.filter { it.id != cur.id }.sortedByDescending { other ->
                            other.genres.count { g -> cur.genres.any { it.equals(g, ignoreCase = true) } }
                        }.take(10)
                    }

                    VideoPlayerScreen(
                        title = overlay.title,
                        streamUrl = overlay.streamUrl,
                        currentMovieItem = overlay.movieItem,
                        similarMovies = similarForMovie,
                        seriesItem = overlay.seriesItem,
                        currentSeasonNumber = overlay.seasonNum,
                        currentEpisodeNumber = overlay.episodeNum,
                        autoPlayNext = prefs.autoPlayNextEpisode,
                        onBack = {
                            val returnTarget = overlay.seriesItem ?: overlay.movieItem
                            activeOverlay = when {
                                !isDeviceOnline || overlay.streamUrl.startsWith(OfflineDownloadManager.OFFLINE_URI_PREFIX) -> {
                                    if (!isDeviceOnline) ActiveOverlay.OfflineDownloads
                                    else if (returnTarget != null) ActiveOverlay.Details(returnTarget)
                                    else ActiveOverlay.OfflineDownloads
                                }
                                returnTarget != null -> ActiveOverlay.Details(returnTarget)
                                else -> ActiveOverlay.None
                            }
                        },
                        onSwitchEpisode = { nextEp ->
                            val sTitle = overlay.seriesItem?.title ?: overlay.title
                            activeOverlay = ActiveOverlay.Player(
                                title = "$sTitle S${nextEp.seasonNumber} E${nextEp.episodeNumber}",
                                streamUrl = nextEp.primaryStreamUrl,
                                seriesItem = overlay.seriesItem,
                                seasonNum = nextEp.seasonNumber,
                                episodeNum = nextEp.episodeNumber
                            )
                        },
                        onSelectSimilarMovie = { simMovie ->
                            val canPlaySim = vm.unlockVipItemIfEligible(simMovie)
                            if (simMovie.primaryStreamUrl.isNotBlank() && canPlaySim) {
                                activeOverlay = ActiveOverlay.Player(
                                    title = simMovie.title,
                                    streamUrl = simMovie.primaryStreamUrl,
                                    movieItem = simMovie
                                )
                            } else {
                                activeOverlay = ActiveOverlay.Details(
                                    item = simMovie,
                                    previousItem = overlay.movieItem
                                )
                            }
                        }
                    )
                }

                is ActiveOverlay.Details -> {
                    val liveItem = allPublished.find {
                        it.id == overlay.item.id ||
                            (it.tmdbId != null && it.tmdbId == overlay.item.tmdbId && it.mediaType == overlay.item.mediaType)
                    } ?: overlay.item
                    DetailsScreen(
                        item = liveItem,
                        enrichedItem = enrichedItemMap[liveItem.id] ?: enrichedItemMap[overlay.item.id],
                        allPublishedItems = allPublished,
                        isInWatchlist = liveItem.id in prefs.watchlistIds,
                        userRecord = currentUserRecord,
                        vipConfig = catalogState.vipAccessConfig,
                        actorDetailsMap = actorDetailsMap,
                        onRequestCastEnrichment = vm::ensureCastImagesLoaded,
                        onRequestActorDetails = vm::loadActorProfileDetails,
                        onRedeemShareCode = { code -> vm.redeemShareCode(code) },
                        itemAnalytics = catalogState.analyticsFor(liveItem.id),
                        onRecordView = vm::recordItemView,
                        onRecordDownload = vm::recordItemDownload,
                        notifiedRequestKeys = notifiedRequestKeys,
                        onNotifyAdmin = vm::notifyAdminToRequestMovie,
                        onBack = {
                            val prev = overlay.previousItem
                            activeOverlay = if (prev != null) {
                                ActiveOverlay.Details(prev)
                            } else {
                                ActiveOverlay.None
                            }
                        },
                        onToggleWatchlist = { vm.toggleWatchlist(liveItem.id) },
                        onPlayVideo = { pTitle, pUrl, sItem, sNum, eNum ->
                            val epLabel = if (sNum != null && eNum != null) "S${sNum} E${eNum}" else ""
                            vm.recordItemView(liveItem, epLabel)
                            vm.unlockVipItemIfEligible(liveItem)
                            activeOverlay = ActiveOverlay.Player(
                                title = pTitle,
                                streamUrl = pUrl,
                                movieItem = liveItem.takeIf { !it.isSeries },
                                seriesItem = sItem ?: liveItem.takeIf { it.isSeries },
                                seasonNum = sNum,
                                episodeNum = eNum
                            )
                        },
                        onOpenDetails = { sim ->
                            activeOverlay = ActiveOverlay.Details(
                                item = sim,
                                previousItem = liveItem
                            )
                        },
                        onOpenOfflineDownloads = {
                            activeOverlay = ActiveOverlay.OfflineDownloads
                        }
                    )
                }

                is ActiveOverlay.OfflineDownloads -> {
                    OfflineDownloadsScreen(
                        downloads = offlineDownloadsList,
                        isDeviceOnline = isDeviceOnline,
                        onPlayOfflineItem = { entry ->
                            val matchedItem = allPublished.find { it.id == entry.itemId }
                            activeOverlay = ActiveOverlay.Player(
                                title = entry.title,
                                streamUrl = entry.offlineStreamUri,
                                movieItem = matchedItem?.takeIf { !it.isSeries },
                                seriesItem = matchedItem?.takeIf { it.isSeries },
                                seasonNum = entry.seasonNumber,
                                episodeNum = entry.episodeNumber
                            )
                        },
                        onDeleteOfflineItem = { dlId ->
                            OfflineDownloadManager.cancelOrDeleteDownload(context, dlId)
                        },
                        onBrowseOnlineCatalog = {
                            activeOverlay = ActiveOverlay.None
                        }
                    )
                }

                is ActiveOverlay.Search -> {
                    SearchScreen(
                        allPublishedItems = allPublished,
                        tmdbSearchResults = userSearchTmdbResults,
                        isSearchingTmdb = isUserSearchingTmdb,
                        notifiedRequestKeys = notifiedRequestKeys,
                        onSearchQueryChanged = vm::searchAllMoviesAndSeriesForUser,
                        onNotifyAdmin = vm::notifyAdminToRequestMovie,
                        onBack = { activeOverlay = ActiveOverlay.None },
                        onOpenDetails = { item -> activeOverlay = ActiveOverlay.Details(item) }
                    )
                }

                is ActiveOverlay.Notifications -> {
                    val allNotifs = remember(catalogState.notificationsHistory, catalogState.latestNotification) {
                        (catalogState.notificationsHistory + listOfNotNull(catalogState.latestNotification))
                            .distinctBy { it.id }
                            .sortedByDescending { it.sentAt }
                    }
                    NotificationsScreen(
                        notifications = allNotifs,
                        allPublishedItems = allPublished,
                        onOpenItemDetails = { item -> activeOverlay = ActiveOverlay.Details(item) },
                        onBack = { activeOverlay = ActiveOverlay.None }
                    )
                }

                ActiveOverlay.None -> {
                    when (currentTab) {
                        UserRootTab.HOME -> {
                            val isOwnerAdminDevice = remember(currentUserRecord.deviceId, context) {
                                val resolvedId = currentUserRecord.deviceId.ifBlank { UserPresenceManager.getRealDeviceId(context) }
                                resolvedId.equals(UserPresenceManager.OWNER_ADMIN_DEVICE_ID, ignoreCase = true) ||
                                    UserPresenceManager.isOwnerAdminDevice(context)
                            }
                            HomeScreen(
                                catalogState = catalogState,
                                watchlistIds = prefs.watchlistIds,
                                showAdminIcon = isOwnerAdminDevice,
                                onOpenDetails = { item -> activeOverlay = ActiveOverlay.Details(item) },
                                onToggleWatchlist = vm::toggleWatchlist,
                                onOpenSearch = { activeOverlay = ActiveOverlay.Search },
                                onOpenMoviesTab = { currentTab = UserRootTab.MOVIES },
                                onOpenSeriesTab = { currentTab = UserRootTab.WEB_SERIES },
                                onOpenAdmin = {
                                    if (isOwnerAdminDevice) {
                                        activeOverlay = ActiveOverlay.AdminPanel
                                    }
                                },
                                onOpenNotifications = { activeOverlay = ActiveOverlay.Notifications }
                            )
                        }

                        UserRootTab.MOVIES -> MoviesScreen(
                            movies = catalogState.movies,
                            categories = catalogState.categories,
                            onOpenDetails = { item -> activeOverlay = ActiveOverlay.Details(item) },
                            onOpenSearch = { activeOverlay = ActiveOverlay.Search }
                        )

                        UserRootTab.WEB_SERIES -> WebSeriesScreen(
                            seriesList = catalogState.series,
                            categories = catalogState.categories,
                            onOpenDetails = { item -> activeOverlay = ActiveOverlay.Details(item) },
                            onOpenSearch = { activeOverlay = ActiveOverlay.Search }
                        )

                        UserRootTab.MY_LIST -> WatchlistScreen(
                            allItems = allPublished,
                            watchlistIds = prefs.watchlistIds,
                            onOpenDetails = { item -> activeOverlay = ActiveOverlay.Details(item) },
                            onRemoveFromWatchlist = vm::removeFromWatchlist,
                            onClearAll = vm::clearWatchlist
                        )

                        UserRootTab.PROFILE -> UserSettingsScreen(
                            preferences = prefs,
                            userRecord = currentUserRecord,
                            vipConfig = catalogState.vipAccessConfig,
                            upcomingItems = upcomingItems,
                            allPublishedItems = allPublished,
                            onSelectTheme = vm::setThemeVariant,
                            onSelectQuality = vm::setDefaultQuality,
                            onToggleAutoPlay = vm::setAutoPlayNextEpisode,
                            onToggleNotifications = vm::setNotificationsEnabled,
                            onRedeemShareCode = { code -> vm.redeemShareCode(code) },
                            onOpenOfflineDownloads = { activeOverlay = ActiveOverlay.OfflineDownloads },
                            onOpenItemDetails = { item -> activeOverlay = ActiveOverlay.Details(item) },
                            onRefreshUpcoming = vm::refreshUpcomingCatalog
                        )
                    }
                }
            }
        }
    }
}

// =============================================================================
// 1. NEXT-LEVEL PREMIUM TRANSPARENT GLASSMORPHIC & ANIMATED NAVIGATION BAR (v22.0)
// =============================================================================
@Composable
private fun PremiumGlassNavigationBar(
    currentTab: UserRootTab,
    onSelectTab: (UserRootTab) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nav_bar_glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFF06080D).copy(alpha = 0.65f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(
                    elevation = 22.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = CrimsonRed.copy(alpha = 0.45f),
                    spotColor = CrimsonRed.copy(alpha = 0.55f)
                )
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1A1F2E).copy(alpha = 0.72f),
                            Color(0xFF0B0E17).copy(alpha = 0.82f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            CrimsonLight.copy(alpha = 0.55f * glowPulse),
                            Color.White.copy(alpha = 0.22f),
                            StarGold.copy(alpha = 0.48f * glowPulse),
                            Color.White.copy(alpha = 0.22f),
                            CrimsonRed.copy(alpha = 0.55f * glowPulse)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 6.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserRootTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    PremiumNavTabItem(
                        tab = tab,
                        selected = selected,
                        glowPulse = glowPulse,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumNavTabItem(
    tab: UserRootTab,
    selected: Boolean,
    glowPulse: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.20f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "icon_scale"
    )

    val iconLiftY by animateFloatAsState(
        targetValue = if (selected) -4f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "icon_lift"
    )

    val capsuleAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "capsule_alpha"
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 20.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "indicator_width"
    )

    val iconTint by animateColorAsState(
        targetValue = if (selected) Color.White else TextSecondary.copy(alpha = 0.78f),
        animationSpec = tween(durationMillis = 240),
        label = "icon_tint"
    )

    val labelColor by animateColorAsState(
        targetValue = if (selected) StarGold else TextSecondary.copy(alpha = 0.72f),
        animationSpec = tween(durationMillis = 240),
        label = "label_color"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Animated Translucent Glowing Pill Behind Active Tab
        if (capsuleAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 3.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                CrimsonRed.copy(alpha = 0.34f * capsuleAlpha * glowPulse),
                                StarGold.copy(alpha = 0.14f * capsuleAlpha)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(
                                CrimsonLight.copy(alpha = 0.65f * capsuleAlpha),
                                StarGold.copy(alpha = 0.55f * capsuleAlpha)
                            )
                        ),
                        shape = RoundedCornerShape(18.dp)
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(0, iconLiftY.roundToInt()) }
                    .scale(iconScale)
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        CrimsonRed.copy(alpha = 0.52f * glowPulse),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = tab.label,
                color = labelColor,
                fontSize = if (selected) 10.5.sp else 9.8.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Animated Neon Indicator Bar
            Box(
                modifier = Modifier
                    .width(indicatorWidth)
                    .height(2.8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(CrimsonLight, StarGold)
                        )
                    )
            )
        }
    }
}

// =============================================================================
// 2. EXIT DIALOG BOX WITH EXPRESSIVE CARTOON ANIMATION (v22.0)
// =============================================================================
@Composable
private fun AnimatedCartoonExitDialog(
    onStay: () -> Unit,
    onExitApp: () -> Unit
) {
    Dialog(
        onDismissRequest = onStay,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "exit_cartoon_anim")

        // Bouncing cartoon body animation
        val bounceY by infiniteTransition.animateFloat(
            initialValue = -7f,
            targetValue = 7f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cartoon_bounce"
        )

        // Waving hand animation
        val waveAngle by infiniteTransition.animateFloat(
            initialValue = -24f,
            targetValue = 26f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hand_wave"
        )

        // Blinking eyes animation
        val eyeBlinkPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "eye_blink"
        )

        // Cute cartoon tear drop animation
        val tearDropY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "tear_drop"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(28.dp, RoundedCornerShape(28.dp), ambientColor = CrimsonRed, spotColor = CrimsonRed)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF1F172B).copy(alpha = 0.96f),
                                    Color(0xFF0E121C).copy(alpha = 0.98f)
                                )
                            )
                        )
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    CrimsonLight.copy(alpha = 0.75f),
                                    StarGold.copy(alpha = 0.65f),
                                    StatusBlue.copy(alpha = 0.55f)
                                )
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Speech Bubble Badge above Cartoon
                    Surface(
                        color = StarGold.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, StarGold.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = "🍿 Wait! Don't Leave Me Yet! 🎬",
                            color = StarGold,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Animated Cinema Mascot Cartoon Canvas ("Poppy the Popcorn Mascot")
                    AnimatedCinemaMascotCanvas(
                        bounceY = bounceY,
                        waveAngle = waveAngle,
                        isBlinking = eyeBlinkPhase > 0.90f,
                        tearDropY = tearDropY,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Leaving MovieBox Already?",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Your favorite Movies & Web Series are ready with hot popcorn! Are you sure you want to exit the app?",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons Row: Stay & Watch | Exit App
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onExitApp,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, CrimsonLight.copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                tint = CrimsonLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Exit App",
                                color = CrimsonLight,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                        }

                        Button(
                            onClick = onStay,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.25f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Stay & Watch",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedCinemaMascotCanvas(
    bounceY: Float,
    waveAngle: Float,
    isBlinking: Boolean,
    tearDropY: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f + 10f

        // Soft glowing spotlight behind the cartoon character
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CrimsonRed.copy(alpha = 0.30f),
                    StarGold.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = size.minDimension * 0.56f
            ),
            radius = size.minDimension * 0.56f,
            center = Offset(cx, cy)
        )

        // Floor shadow that scales inversely with bounceY
        val shadowScale = (1f - (bounceY / 45f)).coerceIn(0.75f, 1.2f)
        drawOval(
            color = Color.Black.copy(alpha = 0.45f),
            topLeft = Offset(cx - 56f * shadowScale, size.height - 20f),
            size = Size(112f * shadowScale, 16f)
        )

        // Floating Golden Stars & Popcorn Sparkles around the cartoon
        val starOffsets = listOf(
            Offset(cx - 105f, cy - 55f - bounceY * 0.6f),
            Offset(cx + 108f, cy - 48f + bounceY * 0.6f),
            Offset(cx - 88f, cy + 20f + bounceY * 0.4f),
            Offset(cx + 95f, cy + 26f - bounceY * 0.5f)
        )
        starOffsets.forEachIndexed { idx, pos ->
            drawCircle(
                color = if (idx % 2 == 0) StarGold else CrimsonLight,
                radius = if (idx % 2 == 0) 5.5f else 4.2f,
                center = pos
            )
        }

        translate(left = 0f, top = bounceY) {
            // Cartoon Little Feet
            drawRoundRect(
                color = Color(0xFFFFB300),
                topLeft = Offset(cx - 38f, cy + 48f),
                size = Size(24f, 15f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFFFFB300),
                topLeft = Offset(cx + 14f, cy + 48f),
                size = Size(24f, 15f),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // Left Cartoon Arm (Holding Mini Cinema Clapperboard / Heart)
            drawLine(
                color = Color(0xFFFFE082),
                start = Offset(cx - 48f, cy + 4f),
                end = Offset(cx - 76f, cy + 20f),
                strokeWidth = 10f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color.White,
                radius = 9f,
                center = Offset(cx - 78f, cy + 22f)
            )

            // Right Waving Cartoon Arm & White Cartoon Glove
            rotate(degrees = waveAngle, pivot = Offset(cx + 48f, cy - 2f)) {
                drawLine(
                    color = Color(0xFFFFE082),
                    start = Offset(cx + 48f, cy - 2f),
                    end = Offset(cx + 84f, cy - 34f),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
                // Cute white cartoon glove palm + fingers
                drawCircle(
                    color = Color.White,
                    radius = 11f,
                    center = Offset(cx + 87f, cy - 37f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = Offset(cx + 79f, cy - 46f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = Offset(cx + 88f, cy - 49f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5f,
                    center = Offset(cx + 96f, cy - 45f)
                )
            }

            // Fluffy Buttery Golden Popcorn Puffs on Top of the Mascot's Head
            val puffCenters = listOf(
                Offset(cx - 34f, cy - 46f),
                Offset(cx - 14f, cy - 56f),
                Offset(cx + 12f, cy - 56f),
                Offset(cx + 34f, cy - 46f),
                Offset(cx, cy - 62f)
            )
            puffCenters.forEach { puff ->
                drawCircle(
                    color = Color(0xFFFFF59D),
                    radius = 20f,
                    center = puff
                )
                drawCircle(
                    color = Color(0xFFFFD54F),
                    radius = 13f,
                    center = Offset(puff.x + 2f, puff.y + 3f)
                )
            }

            // Red & White Striped Cinema Popcorn Bucket Body
            val bucketPath = Path().apply {
                moveTo(cx - 52f, cy - 42f)
                lineTo(cx + 52f, cy - 42f)
                lineTo(cx + 42f, cy + 52f)
                lineTo(cx - 42f, cy + 52f)
                close()
            }
            drawPath(
                path = bucketPath,
                color = Color(0xFFFFF8E7)
            )

            // 3 Vertical Red Stripes on the Popcorn Bucket
            for (stripeOffset in listOf(-28f, 0f, 28f)) {
                drawRoundRect(
                    color = Color(0xFFE50914),
                    topLeft = Offset(cx + stripeOffset - 8f, cy - 40f),
                    size = Size(16f, 90f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }

            // Bucket Rim & Border
            drawRoundRect(
                color = Color(0xFFFFC107),
                topLeft = Offset(cx - 55f, cy - 45f),
                size = Size(110f, 10f),
                cornerRadius = CornerRadius(5f, 5f)
            )
            drawPath(
                path = bucketPath,
                color = Color(0xFF2B1810),
                style = Stroke(width = 3.5f)
            )

            // Cute 3D Cinema Glasses Frame (Red & Cyan 3D Lenses)
            // Left Lens Frame (Red 3D Lens)
            drawRoundRect(
                color = Color(0xFF1A1D26),
                topLeft = Offset(cx - 42f, cy - 24f),
                size = Size(38f, 28f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFFFF1744).copy(alpha = 0.85f),
                topLeft = Offset(cx - 39f, cy - 21f),
                size = Size(32f, 22f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Right Lens Frame (Cyan 3D Lens)
            drawRoundRect(
                color = Color(0xFF1A1D26),
                topLeft = Offset(cx + 4f, cy - 24f),
                size = Size(38f, 28f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawRoundRect(
                color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                topLeft = Offset(cx + 7f, cy - 21f),
                size = Size(32f, 22f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Bridge between 3D Glasses
            drawLine(
                color = Color(0xFF1A1D26),
                start = Offset(cx - 4f, cy - 10f),
                end = Offset(cx + 4f, cy - 10f),
                strokeWidth = 4.5f
            )

            // Expressive Cartoon Eyes inside the 3D Glasses
            if (isBlinking) {
                drawLine(
                    color = Color.White,
                    start = Offset(cx - 31f, cy - 10f),
                    end = Offset(cx - 15f, cy - 10f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(cx + 15f, cy - 10f),
                    end = Offset(cx + 31f, cy - 10f),
                    strokeWidth = 3.5f,
                    cap = StrokeCap.Round
                )
            } else {
                // Big Puppy-Dog Eyes with Shiny Highlights
                drawCircle(color = Color.White, radius = 7.5f, center = Offset(cx - 23f, cy - 10f))
                drawCircle(color = Color(0xFF11131A), radius = 4.5f, center = Offset(cx - 22f, cy - 10f))
                drawCircle(color = Color.White, radius = 2.2f, center = Offset(cx - 24f, cy - 12f))

                drawCircle(color = Color.White, radius = 7.5f, center = Offset(cx + 23f, cy - 10f))
                drawCircle(color = Color(0xFF11131A), radius = 4.5f, center = Offset(cx + 22f, cy - 10f))
                drawCircle(color = Color.White, radius = 2.2f, center = Offset(cx + 20f, cy - 12f))
            }

            // Animated Cute Tear Drop falling from the right eye
            drawCircle(
                color = Color(0xFF40C4FF),
                radius = 4.2f,
                center = Offset(cx + 32f, cy + 4f + tearDropY)
            )

            // Rosy Cartoon Cheeks
            drawCircle(
                color = Color(0xFFFF8A80).copy(alpha = 0.85f),
                radius = 6.5f,
                center = Offset(cx - 30f, cy + 12f)
            )
            drawCircle(
                color = Color(0xFFFF8A80).copy(alpha = 0.85f),
                radius = 6.5f,
                center = Offset(cx + 30f, cy + 12f)
            )

            // Cute Pouting / "Aww Don't Go!" Cartoon Mouth Badge
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - 18f, cy + 10f),
                size = Size(36f, 20f),
                cornerRadius = CornerRadius(10f, 10f)
            )
            val sadMouthPath = Path().apply {
                moveTo(cx - 10f, cy + 24f)
                quadraticTo(cx, cy + 15f, cx + 10f, cy + 24f)
            }
            drawPath(
                path = sadMouthPath,
                color = Color(0xFF2B1810),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }
    }
}
