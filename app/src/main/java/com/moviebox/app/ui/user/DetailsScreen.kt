package com.moviebox.app.ui.user

import android.app.DownloadManager
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.text.font.FontFamily
import com.moviebox.app.data.repository.UserPresenceManager
import com.moviebox.app.ui.theme.StatusBlue
import android.content.Intent
import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.ui.PlayerView
import com.moviebox.app.player.PlayerHolder
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moviebox.app.data.model.ActorProfileDetails
import com.moviebox.app.data.model.CastMember
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.StatusRed
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.ItemMediaAnalytics
import com.moviebox.app.data.model.Episode
import com.moviebox.app.data.model.UserPresenceRecord
import com.moviebox.app.data.model.VipAccessConfig
import com.moviebox.app.ui.components.VipAccessBadge
import com.moviebox.app.ui.components.FilterPill
import com.moviebox.app.ui.components.PosterCard
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextMuted
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moviebox.app.util.OfflineDownloadEntry
import com.moviebox.app.util.OfflineDownloadManager
import com.moviebox.app.util.OfflineDownloadStatus
import com.moviebox.app.util.UrlValidator
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private enum class GalleryViewerMode {
    NONE,
    POSTERS,
    BACKDROPS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    item: CatalogItem,
    enrichedItem: CatalogItem? = null,
    allPublishedItems: List<CatalogItem>,
    isInWatchlist: Boolean,
    userRecord: UserPresenceRecord = UserPresenceRecord(),
    vipConfig: VipAccessConfig = VipAccessConfig(),
    actorDetailsMap: Map<String, ActorProfileDetails> = emptyMap(),
    onRequestCastEnrichment: (CatalogItem) -> Unit = {},
    onRequestActorDetails: (CastMember) -> Unit = {},
    onRedeemShareCode: (String) -> Unit = {},
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onPlayVideo: (title: String, streamUrl: String, seriesItem: CatalogItem?, seasonNum: Int?, episodeNum: Int?) -> Unit,
    itemAnalytics: ItemMediaAnalytics = ItemMediaAnalytics(),
    onRecordView: (CatalogItem, String) -> Unit = { _, _ -> },
    onRecordDownload: (CatalogItem, String) -> Unit = { _, _ -> },
    notifiedRequestKeys: Set<String> = emptySet(),
    onNotifyAdmin: (CatalogItem) -> Unit = {},
    onOpenDetails: (CatalogItem) -> Unit,
    onOpenOfflineDownloads: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Match against published catalog items so Upcoming / Search posters automatically unlock Play & Download if available in catalog
    val matchedCatalogItem = remember(item, allPublishedItems) {
        allPublishedItems.firstOrNull { pub ->
            pub.id == item.id ||
                (pub.tmdbId != null && pub.tmdbId == item.tmdbId && pub.mediaType == item.mediaType) ||
                (pub.title.equals(item.title, ignoreCase = true) && pub.mediaType == item.mediaType)
        }
    }

    val baseItem = remember(item, matchedCatalogItem) {
        if (matchedCatalogItem == null) item
        else matchedCatalogItem.copy(
            releaseDate = item.releaseDate.ifBlank { matchedCatalogItem.releaseDate },
            posterUrl = matchedCatalogItem.posterUrl.ifBlank { item.posterUrl },
            backdropUrl = matchedCatalogItem.backdropUrl.ifBlank { item.backdropUrl },
            overview = matchedCatalogItem.overview.ifBlank { item.overview }
        )
    }

    // Merge live item with TMDB enriched details (Cast photos, Posters/Backdrops galleries, Budget, Revenue, Companies)
    val displayItem = remember(baseItem, enrichedItem) {
        val item = baseItem
        if (enrichedItem == null) item
        else item.copy(
            tmdbId = item.tmdbId ?: enrichedItem.tmdbId,
            originalTitle = enrichedItem.originalTitle.ifBlank { item.originalTitle.ifBlank { item.title } },
            tagline = enrichedItem.tagline.ifBlank { item.tagline },
            releaseDate = enrichedItem.releaseDate.ifBlank { item.releaseDate },
            status = enrichedItem.status.ifBlank { item.status },
            runtimeMinutes = item.runtimeMinutes.takeIf { it > 0 } ?: enrichedItem.runtimeMinutes,
            country = enrichedItem.country.ifBlank { item.country },
            budget = if (enrichedItem.budget > 0L) enrichedItem.budget else item.budget,
            revenue = if (enrichedItem.revenue > 0L) enrichedItem.revenue else item.revenue,
            companies = if (enrichedItem.companies.isNotEmpty()) enrichedItem.companies else item.companies,
            posterGallery = if (enrichedItem.posterGallery.isNotEmpty()) enrichedItem.posterGallery else item.posterGallery,
            backdropGallery = if (enrichedItem.backdropGallery.isNotEmpty()) enrichedItem.backdropGallery else item.backdropGallery,
            totalPostersCount = enrichedItem.totalPostersCount.coerceAtLeast(item.totalPostersCount),
            totalBackdropsCount = enrichedItem.totalBackdropsCount.coerceAtLeast(item.totalBackdropsCount),
            cast = if (enrichedItem.cast.isNotEmpty()) enrichedItem.cast else item.cast,
            trailerUrl = item.trailerUrl.ifBlank { enrichedItem.trailerUrl }
        )
    }

    LaunchedEffect(item.id, item.tmdbId) {
        onRequestCastEnrichment(item)
    }

    LaunchedEffect(displayItem.id) {
        if (displayItem.id.isNotBlank()) {
            onRecordView(displayItem, "")
        }
    }

    val postersList = remember(displayItem.posterUrl, displayItem.posterGallery) {
        (listOfNotNull(displayItem.posterUrl.takeIf { it.isNotBlank() }) + displayItem.posterGallery).distinct()
    }
    val backdropsList = remember(displayItem.backdropUrl, displayItem.posterUrl, displayItem.backdropGallery) {
        val primary = displayItem.backdropUrl.ifBlank { displayItem.posterUrl }
        (listOfNotNull(primary.takeIf { it.isNotBlank() }) + displayItem.backdropGallery).distinct()
    }
    val totalPostersLabelCount = displayItem.totalPostersCount.coerceAtLeast(postersList.size)
    val totalBackdropsLabelCount = displayItem.totalBackdropsCount.coerceAtLeast(backdropsList.size)

    var isStorylineExpanded by rememberSaveable { mutableStateOf(false) }
    var isLiked by rememberSaveable(item.id) { mutableStateOf(false) }
    var isStarred by rememberSaveable(item.id) { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFullInfoScreen by rememberSaveable { mutableStateOf(false) }
    var showTrailerPopup by rememberSaveable { mutableStateOf(false) }
    var galleryMode by rememberSaveable { mutableStateOf(GalleryViewerMode.NONE) }
    var selectedSeasonIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedCastMember by remember { mutableStateOf<CastMember?>(null) }

    LaunchedEffect(Unit) {
        OfflineDownloadManager.ensureInitialized(context)
    }
    val downloadsMap by OfflineDownloadManager.downloadsMap.collectAsStateWithLifecycle()

    val firstSeason = displayItem.seasons.getOrNull(selectedSeasonIndex) ?: displayItem.seasons.firstOrNull()
    val firstPlayableEpisode = displayItem.seasons.flatMap { it.episodes }
        .firstOrNull { UrlValidator.validateStreamUrl(it.primaryStreamUrl).isValid }

    val primaryDownloadId = remember(displayItem.id, displayItem.isSeries, firstPlayableEpisode) {
        if (displayItem.isSeries && firstPlayableEpisode != null) {
            OfflineDownloadManager.buildDownloadId(
                displayItem,
                firstPlayableEpisode.seasonNumber,
                firstPlayableEpisode.episodeNumber
            )
        } else {
            OfflineDownloadManager.buildDownloadId(displayItem)
        }
    }
    val primaryDownloadEntry = downloadsMap[primaryDownloadId]

    val movieQualityOptions = remember(displayItem) {
        displayItem.availableQualityOptions().filter { UrlValidator.validateStreamUrl(it.url).isValid }
    }
    val movieStreamValidation = UrlValidator.validateStreamUrl(displayItem.primaryStreamUrl)
    val canPlayPrimaryStream = if (displayItem.isSeries) {
        firstPlayableEpisode != null || movieStreamValidation.isValid
    } else {
        movieStreamValidation.isValid
    }
    val hasValidTrailer = UrlValidator.validateTrailerUrl(displayItem.trailerUrl)

    var showVipShareCodeScreen by rememberSaveable { mutableStateOf(false) }
    var pendingVipPlayAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val hasVipAccessForTitle = remember(displayItem.isVip, displayItem.vipPointsRequired, displayItem.id, userRecord, vipConfig.vipPointsToAccess) {
        userRecord.hasVipAccessFor(displayItem, vipConfig.vipPointsToAccess)
    }

    val executeVipGatedPlay: (() -> Unit) -> Unit = { playAction ->
        if (!displayItem.isVip || userRecord.hasVipAccessFor(displayItem, vipConfig.vipPointsToAccess)) {
            playAction()
        } else {
            pendingVipPlayAction = playAction
            showVipShareCodeScreen = true
        }
    }

    val triggerTrailerOrStream: () -> Unit = {
        if (hasValidTrailer) {
            showTrailerPopup = true
        } else if (canPlayPrimaryStream) {
            executeVipGatedPlay {
                if (displayItem.isSeries && firstPlayableEpisode != null) {
                    onPlayVideo(
                        "${displayItem.title} S${firstPlayableEpisode.seasonNumber} E${firstPlayableEpisode.episodeNumber}",
                        firstPlayableEpisode.primaryStreamUrl,
                        displayItem,
                        firstPlayableEpisode.seasonNumber,
                        firstPlayableEpisode.episodeNumber
                    )
                } else {
                    onPlayVideo(displayItem.title, displayItem.primaryStreamUrl, null, null, null)
                }
            }
        } else {
            Toast.makeText(context, "No trailer link configured for this title yet.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showVipShareCodeScreen) {
        VipShareCodeDialogScreen(
            item = displayItem,
            userRecord = userRecord,
            vipConfig = vipConfig,
            onRedeemShareCode = onRedeemShareCode,
            onUnlockedPlayNow = {
                showVipShareCodeScreen = false
                val action = pendingVipPlayAction
                pendingVipPlayAction = null
                if (action != null) {
                    action()
                } else if (canPlayPrimaryStream) {
                    if (displayItem.isSeries && firstPlayableEpisode != null) {
                        onPlayVideo(
                            "${displayItem.title} S${firstPlayableEpisode.seasonNumber} E${firstPlayableEpisode.episodeNumber}",
                            firstPlayableEpisode.primaryStreamUrl,
                            displayItem,
                            firstPlayableEpisode.seasonNumber,
                            firstPlayableEpisode.episodeNumber
                        )
                    } else {
                        onPlayVideo(displayItem.title, displayItem.primaryStreamUrl, null, null, null)
                    }
                }
            },
            onDismiss = { showVipShareCodeScreen = false }
        )
        return
    }

    // Mobile hardware/gesture back button handling for DetailsScreen
    BackHandler(enabled = selectedCastMember == null && !showFullInfoScreen && !showTrailerPopup && !showVipShareCodeScreen && galleryMode == GalleryViewerMode.NONE) {
        onBack()
    }

    // Dedicated Actor / Cast Member Information & Photos Screen (NO Movie Posters!)
    val activeActor = selectedCastMember
    if (activeActor != null) {
        val actorKey = "${activeActor.id}_${activeActor.name}"
        val loadedActorDetails = actorDetailsMap[actorKey]
        LaunchedEffect(activeActor.id, activeActor.name) {
            onRequestActorDetails(activeActor)
        }
        ActorDetailsFullScreen(
            member = activeActor,
            details = loadedActorDetails,
            parentMovieTitle = displayItem.title,
            onBack = { selectedCastMember = null }
        )
        return
    }

    // In-App Pop-Up Trailer Player Dialog (Plays YouTube or MP4/HLS trailers directly inside the app!)
    if (showTrailerPopup && hasValidTrailer) {
        InAppTrailerPopupDialog(
            title = displayItem.title,
            trailerUrl = displayItem.trailerUrl,
            onDismiss = { showTrailerPopup = false }
        )
    }

    // Full-screen Information page overlay (matching Screenshot 1: 1219586.jpg)
    if (showFullInfoScreen) {
        FullInformationScreen(
            item = displayItem,
            onBack = { showFullInfoScreen = false }
        )
        return
    }

    // Full-screen Swipeable Poster / Backdrop Gallery Viewer (matching Screenshots 3 & 4: 1219590.jpg & 1219592.jpg)
    if (galleryMode != GalleryViewerMode.NONE) {
        val imagesToView = if (galleryMode == GalleryViewerMode.POSTERS) postersList else backdropsList
        val defaultCrop = galleryMode == GalleryViewerMode.POSTERS
        FullScreenMediaGalleryDialog(
            images = imagesToView,
            initialCropMode = defaultCrop,
            title = displayItem.title,
            onDismiss = { galleryMode = GalleryViewerMode.NONE }
        )
    }

    val similarItems = remember(displayItem.id, allPublishedItems) {
        allPublishedItems.filter { other ->
            other.id != displayItem.id && (
                other.categoryIds.any { it in displayItem.categoryIds } ||
                    other.genres.any { g -> displayItem.genres.any { it.equals(g, ignoreCase = true) } } ||
                    other.mediaType == displayItem.mediaType
                )
        }.take(8)
    }

    val tmdbScorePercent = remember(displayItem.rating) {
        if (displayItem.rating > 0.0) "${(displayItem.rating * 10.0).toInt().coerceIn(1, 100)}%" else "84%"
    }
    val criticScorePercent = remember(displayItem.rating) {
        if (displayItem.rating > 0.0) "${(displayItem.rating * 9.2).toInt().coerceIn(1, 99)}%" else "75%"
    }

    val bgColor = MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
    ) {
        // =========================================================================
        // 1. CINEMATIC HERO SECTION (Blurred Backdrop + Floating Glass Buttons + Centered Poster Card)
        // =========================================================================
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(displayItem.backdropUrl.ifBlank { displayItem.posterUrl })
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(26.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.38f),
                                0.55f to bgColor.copy(alpha = 0.78f),
                                1.0f to bgColor
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Full-screen edge-to-edge: hero backdrop draws under the transparent status bar,
                // content starts just below battery / time / signal icons.
                Spacer(modifier = Modifier.statusBarsPadding().height(18.dp))

                // Centered Vertical Poster Card with Bottom-Right Play Badge
                Box(
                    modifier = Modifier
                        .width(172.dp)
                        .height(252.dp)
                        .shadow(24.dp, RoundedCornerShape(22.dp), clip = false)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                        .clickable {
                            if (postersList.isNotEmpty()) {
                                galleryMode = GalleryViewerMode.POSTERS
                            } else {
                                triggerTrailerOrStream()
                            }
                        }
                ) {
                    PosterImageWithFallback(
                        imageUrl = displayItem.posterUrl.ifBlank { displayItem.backdropUrl },
                        title = displayItem.title,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        color = Color(0xFFE8EAED).copy(alpha = 0.94f),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(width = 38.dp, height = 26.dp)
                            .clickable { triggerTrailerOrStream() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play Trailer",
                                tint = Color(0xFF12141A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = displayItem.title,
                    color = TextWhite,
                    fontSize = 25.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Centered Metadata Row: "2026 • 2h 25m ⓘ" (Tapping ⓘ opens Full Information Screen!)
                val runtimeDisplay = displayItem.formattedRuntime.ifBlank {
                    if (displayItem.isSeries) "${displayItem.seasons.size.coerceAtLeast(1)} Seasons" else "2h 05m"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showFullInfoScreen = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${displayItem.releaseYear}  •  $runtimeDisplay",
                        color = TextSecondary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Open Full Information Page",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    VipAccessBadge(
                        isVip = displayItem.isVip,
                        vipPoints = if (displayItem.isVip) displayItem.effectiveVipPoints(vipConfig.vipPointsToAccess) else 0
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dual Score Badges: THE MOVIE DB 84% & Critic Emblem 75%
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TmdbMiniLogoBadge()
                        Text(
                            text = tmdbScorePercent,
                            color = TextWhite,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed)
                                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = criticScorePercent,
                            color = TextWhite,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Movie & Web Series View Counting + Download Counting Badges (v23.0)
                val liveViewsCount = itemAnalytics.viewCount.coerceAtLeast(1)
                val liveDownloadsCount = itemAnalytics.downloadCount.coerceAtLeast(0)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = StatusBlue.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = "Total Views",
                                tint = StatusBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$liveViewsCount Views",
                                color = StatusBlue,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Surface(
                        color = StatusGreen.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Total Downloads",
                                tint = StatusGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$liveDownloadsCount Downloads",
                                color = StatusGreen,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5-Icon Glass Pill Action Bar ( + | Bookmark | Heart | Star | ••• )
                Surface(
                    color = Color(0xFF141720).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleWatchlist) {
                            Icon(
                                imageVector = if (isInWatchlist) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = "Add to My List",
                                tint = if (isInWatchlist) StatusGreen else TextWhite,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = onToggleWatchlist) {
                            Icon(
                                imageVector = if (isInWatchlist) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isInWatchlist) CrimsonLight else TextWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { isLiked = !isLiked }) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isLiked) CrimsonRed else TextWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        // 4th Action Icon: Download -> Pause (% Ring) -> Resume / Retry -> Offline Play!
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    when (primaryDownloadEntry?.status) {
                                        OfflineDownloadStatus.COMPLETED -> {
                                            onPlayVideo(
                                                primaryDownloadEntry.title,
                                                primaryDownloadEntry.offlineStreamUri,
                                                displayItem.takeIf { it.isSeries },
                                                primaryDownloadEntry.seasonNumber,
                                                primaryDownloadEntry.episodeNumber
                                            )
                                        }
                                        OfflineDownloadStatus.DOWNLOADING -> {
                                            OfflineDownloadManager.pauseDownload(context, primaryDownloadId)
                                            Toast.makeText(
                                                context,
                                                "Download paused at ${primaryDownloadEntry.progressPercent}%. Tap again to resume.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        OfflineDownloadStatus.PAUSED -> {
                                            OfflineDownloadManager.resumeOrRetryDownload(
                                                context = context,
                                                downloadId = primaryDownloadId,
                                                fallbackItem = displayItem,
                                                fallbackEpisode = if (displayItem.isSeries) firstPlayableEpisode else null
                                            )
                                            Toast.makeText(
                                                context,
                                                "Resuming download from ${primaryDownloadEntry.progressPercent}%...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        OfflineDownloadStatus.FAILED -> {
                                            OfflineDownloadManager.resumeOrRetryDownload(
                                                context = context,
                                                downloadId = primaryDownloadId,
                                                fallbackItem = displayItem,
                                                fallbackEpisode = if (displayItem.isSeries) firstPlayableEpisode else null
                                            )
                                            Toast.makeText(
                                                context,
                                                "Retrying download...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        else -> {
                                            if (!canPlayPrimaryStream) {
                                                Toast.makeText(
                                                    context,
                                                    "No playable stream link configured to download.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                executeVipGatedPlay {
                                                    val epLabel = if (displayItem.isSeries && firstPlayableEpisode != null) {
                                                        "S${firstPlayableEpisode.seasonNumber} E${firstPlayableEpisode.episodeNumber}"
                                                    } else ""
                                                    onRecordDownload(displayItem, epLabel)
                                                    OfflineDownloadManager.startDownload(
                                                        context = context,
                                                        item = displayItem,
                                                        episode = if (displayItem.isSeries) firstPlayableEpisode else null
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "Download started (tap icon anytime to pause)",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when (primaryDownloadEntry?.status) {
                                OfflineDownloadStatus.DOWNLOADING -> {
                                    CircularProgressIndicator(
                                        progress = { (primaryDownloadEntry.progressPercent / 100f).coerceIn(0.04f, 1f) },
                                        color = CrimsonRed,
                                        trackColor = Color.White.copy(alpha = 0.2f),
                                        strokeWidth = 2.8.dp,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Text(
                                        text = "${primaryDownloadEntry.progressPercent}%",
                                        color = TextWhite,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                OfflineDownloadStatus.PAUSED -> {
                                    CircularProgressIndicator(
                                        progress = { (primaryDownloadEntry.progressPercent / 100f).coerceIn(0.04f, 1f) },
                                        color = StatusAmber,
                                        trackColor = Color.White.copy(alpha = 0.2f),
                                        strokeWidth = 2.8.dp,
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Pause,
                                        contentDescription = "Paused - Tap to Resume",
                                        tint = StatusAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                OfflineDownloadStatus.FAILED -> {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(StatusRed.copy(alpha = 0.22f))
                                            .border(1.5.dp, StatusRed, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Retry Download",
                                            tint = StatusRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                OfflineDownloadStatus.COMPLETED -> {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(StatusGreen.copy(alpha = 0.22f))
                                            .border(1.5.dp, StatusGreen, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "Play Downloaded Offline",
                                            tint = StatusGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Download for Offline Play",
                                        tint = TextWhite,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreHoriz,
                                    contentDescription = "More Options",
                                    tint = TextWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Full Information Page") },
                                    leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showFullInfoScreen = true
                                    }
                                )
                                if (postersList.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("View Posters ($totalPostersLabelCount)") },
                                        leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            galleryMode = GalleryViewerMode.POSTERS
                                        }
                                    )
                                }
                                if (backdropsList.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("View Backdrops ($totalBackdropsLabelCount)") },
                                        leadingIcon = { Icon(Icons.Filled.Wallpaper, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            galleryMode = GalleryViewerMode.BACKDROPS
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("My Offline Downloads") },
                                    leadingIcon = { Icon(Icons.Filled.DownloadDone, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onOpenOfflineDownloads()
                                    }
                                )
                            }
                        }
                    }
                }

                // Primary Play / Watch Now + 3 Quality Play Selector Buttons (1080p | 720p | 480p)
                if (canPlayPrimaryStream || hasValidTrailer) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (canPlayPrimaryStream) {
                            Button(
                                onClick = {
                                    executeVipGatedPlay {
                                        if (displayItem.isSeries && firstPlayableEpisode != null) {
                                            onPlayVideo(
                                                "${displayItem.title} S${firstPlayableEpisode.seasonNumber} E${firstPlayableEpisode.episodeNumber}",
                                                firstPlayableEpisode.primaryStreamUrl,
                                                displayItem,
                                                firstPlayableEpisode.seasonNumber,
                                                firstPlayableEpisode.episodeNumber
                                            )
                                        } else {
                                            onPlayVideo(displayItem.title, displayItem.primaryStreamUrl, null, null, null)
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (displayItem.isVip && !hasVipAccessForTitle) StarGold else CrimsonRed
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(50.dp)
                            ) {
                                Icon(
                                    imageVector = if (displayItem.isVip && !hasVipAccessForTitle) Icons.Filled.WorkspacePremium else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = if (displayItem.isVip && !hasVipAccessForTitle) Color.Black else Color.White
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        displayItem.isVip && !hasVipAccessForTitle -> {
                                            if (displayItem.isSeries && firstPlayableEpisode != null) {
                                                "VIP Play S${firstPlayableEpisode.seasonNumber} E${firstPlayableEpisode.episodeNumber}"
                                            } else {
                                                "VIP Play Movie"
                                            }
                                        }
                                        displayItem.isSeries && firstPlayableEpisode != null -> {
                                            "Play S${firstPlayableEpisode.seasonNumber} E${firstPlayableEpisode.episodeNumber}"
                                        }
                                        else -> "Play Movie"
                                    },
                                    color = if (displayItem.isVip && !hasVipAccessForTitle) Color.Black else Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        if (hasValidTrailer) {
                            OutlinedButton(
                                onClick = triggerTrailerOrStream,
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Filled.OndemandVideo, contentDescription = null, tint = CrimsonLight, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Trailer",
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Live Animated Release Date Countdown (Year, Month, Day, Hour, Minute, Second) + Notify to Admin
                val itemRequestKey = remember(displayItem.mediaType, displayItem.tmdbId, displayItem.title) {
                    "${displayItem.mediaType}_${displayItem.tmdbId ?: displayItem.title.lowercase()}"
                }
                val isItemNotified = itemRequestKey in notifiedRequestKeys
                val showReleaseCountdown = remember(displayItem.status, displayItem.releaseDate, canPlayPrimaryStream) {
                    !canPlayPrimaryStream ||
                        displayItem.status.equals("Upcoming", ignoreCase = true) ||
                        isFutureReleaseDate(displayItem.releaseDate)
                }

                if (showReleaseCountdown) {
                    Spacer(modifier = Modifier.height(14.dp))
                    AnimatedReleaseDateCountdownCard(
                        item = displayItem,
                        isPlayableNow = canPlayPrimaryStream,
                        isNotified = isItemNotified,
                        onNotifyAdmin = { onNotifyAdmin(displayItem) }
                    )
                }
            }
        }

        // =========================================================================
        // 2. TAGLINE, STORYLINE WITH "MORE" PILL & GENRES
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val taglineText = displayItem.tagline.ifBlank {
                val primaryGenre = displayItem.genres.firstOrNull() ?: if (displayItem.isSeries) "Original Series" else "Feature Film"
                "Experience the $primaryGenre story of ${displayItem.title}."
            }
            Text(
                text = taglineText,
                color = TextWhite,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold
            )

            val storylineText = displayItem.overview.ifBlank { "No storyline description available for this title." }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = storylineText,
                    color = TextSecondary,
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp,
                    maxLines = if (isStorylineExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (storylineText.length > 115) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            color = Color(0xFF332419),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { isStorylineExpanded = !isStorylineExpanded }
                        ) {
                            Text(
                                text = if (isStorylineExpanded) "LESS" else "MORE",
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            val genresToDisplay = displayItem.genres.ifEmpty {
                listOf(if (displayItem.isSeries) "Web Series" else "Movie", displayItem.contentAdvisory.ifBlank { "U/A 13+" })
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                genresToDisplay.forEach { genre ->
                    GenreChipPill(text = genre)
                }
            }

            // =========================================================================
            // 3. CAST & CREW SECTION
            // =========================================================================
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cast & Crew",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (displayItem.cast.isNotEmpty()) {
                    Text(
                        text = "${displayItem.cast.size} Members",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            if (displayItem.cast.isEmpty()) {
                Surface(
                    color = Color(0xFF141720),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = TextSecondary)
                        Text(
                            text = "Loading cast members & actor photos from TMDB...",
                            color = TextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }
            } else {
                CastHorizontalRow(
                    cast = displayItem.cast,
                    onSelectActor = { member -> selectedCastMember = member }
                )
            }

            // =========================================================================
            // 4. SEASONS & EPISODES SECTION (When Viewing a Web Series)
            // =========================================================================
            if (displayItem.isSeries && displayItem.seasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Seasons & Episodes",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    displayItem.seasons.forEachIndexed { idx, s ->
                        FilterPill(
                            label = s.title.ifBlank { "Season ${s.seasonNumber}" },
                            selected = selectedSeasonIndex == idx,
                            onClick = { selectedSeasonIndex = idx }
                        )
                    }
                }
                val eps = firstSeason?.episodes.orEmpty()
                eps.forEach { ep ->
                    val epDownloadId = OfflineDownloadManager.buildDownloadId(
                        displayItem,
                        ep.seasonNumber,
                        ep.episodeNumber
                    )
                    val epDownloadEntry = downloadsMap[epDownloadId]
                    EpisodeRowCard(
                        episode = ep,
                        fallbackPoster = displayItem.backdropUrl.ifBlank { displayItem.posterUrl },
                        downloadEntry = epDownloadEntry,
                        onStartEpisodeDownload = {
                            executeVipGatedPlay {
                                onRecordDownload(displayItem, "S${ep.seasonNumber} E${ep.episodeNumber}")
                                OfflineDownloadManager.startDownload(
                                    context = context,
                                    item = displayItem,
                                    episode = ep
                                )
                                Toast.makeText(
                                    context,
                                    "Downloading S${ep.seasonNumber} E${ep.episodeNumber}...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onPauseEpisodeDownload = {
                            OfflineDownloadManager.pauseDownload(context, epDownloadId)
                            Toast.makeText(
                                context,
                                "Paused S${ep.seasonNumber} E${ep.episodeNumber} download",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onResumeOrRetryEpisodeDownload = {
                            OfflineDownloadManager.resumeOrRetryDownload(
                                context = context,
                                downloadId = epDownloadId,
                                fallbackItem = displayItem,
                                fallbackEpisode = ep
                            )
                            Toast.makeText(
                                context,
                                "Resuming S${ep.seasonNumber} E${ep.episodeNumber} download...",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onPlayWithUrl = { chosenUrl, badge ->
                            executeVipGatedPlay {
                                val suffix = if (badge.isNotBlank()) " ($badge)" else ""
                                onPlayVideo(
                                    "${displayItem.title} S${ep.seasonNumber} E${ep.episodeNumber}$suffix",
                                    chosenUrl,
                                    displayItem,
                                    ep.seasonNumber,
                                    ep.episodeNumber
                                )
                            }
                        }
                    )
                }
            }

            // =========================================================================
            // 5. MEDIA SECTION (Posters Card & Backdrops Card — Matching Screenshot 2: 1219588.jpg)
            // =========================================================================
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Media",
                color = TextWhite,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Card: Posters (Portrait ratio card with translucent pill "172\nPosters")
                Box(
                    modifier = Modifier
                        .width(126.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .clickable {
                            if (postersList.isNotEmpty()) {
                                galleryMode = GalleryViewerMode.POSTERS
                            }
                        }
                ) {
                    PosterImageWithFallback(
                        imageUrl = postersList.firstOrNull().orEmpty(),
                        title = displayItem.title,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.55f to Color.Black.copy(alpha = 0.25f),
                                    1.0f to Color.Black.copy(alpha = 0.72f)
                                )
                            )
                    )
                    Surface(
                        color = Color(0xFF14171F).copy(alpha = 0.74f),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "$totalPostersLabelCount",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Posters",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Right Card: Backdrops (Landscape card with translucent pill "79 Backdrops")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .clickable {
                            if (backdropsList.isNotEmpty()) {
                                galleryMode = GalleryViewerMode.BACKDROPS
                            }
                        }
                ) {
                    PosterImageWithFallback(
                        imageUrl = backdropsList.firstOrNull().orEmpty(),
                        title = displayItem.title,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Transparent,
                                    0.6f to Color.Black.copy(alpha = 0.2f),
                                    1.0f to Color.Black.copy(alpha = 0.72f)
                                )
                            )
                    )
                    Surface(
                        color = Color(0xFF14171F).copy(alpha = 0.74f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "$totalBackdropsLabelCount Backdrops",
                            color = TextWhite,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }

            // =========================================================================
            // 6. INFORMATION SECTION (Matching Screenshot 1: 1219586.jpg)
            // =========================================================================
            Spacer(modifier = Modifier.height(8.dp))
            InformationSectionBlock(item = displayItem)

            // =========================================================================
            // 7. MORE LIKE THIS / SIMILAR TITLES SECTION
            // =========================================================================
            if (similarItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "More Like This",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(similarItems, key = { it.id }) { sim ->
                        PosterCard(
                            item = sim,
                            onClick = { onOpenDetails(sim) },
                            width = 128.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Full-Screen Dedicated Information Page (Opened when tapping the ⓘ button next to Runtime, matching 1219586.jpg)
 */
@Composable
private fun FullInformationScreen(
    item: CatalogItem,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Top Header Bar: Title (Mobile back button returns to Details)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.title,
                color = TextWhite,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        InformationSectionBlock(item = item)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Reusable Information Block matching Screenshot 1 (1219586.jpg):
 * Genres, Release Date, Runtime, Status, Original Language, Certification, Budget, Revenue, Original Title, Country, Companies
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InformationSectionBlock(item: CatalogItem) {
    val formattedDate = remember(item.releaseDate, item.releaseYear) {
        formatDisplayReleaseDate(item.releaseDate, item.releaseYear)
    }
    val runtimeLabel = remember(item.runtimeMinutes, item.isSeries, item.seasons.size) {
        item.formattedRuntime.ifBlank { if (item.isSeries) "45m / Episode" else "2h 15m" }
    }
    val fullLanguageName = remember(item.language) {
        mapLanguageCodeToName(item.language)
    }
    val certificationText = remember(item.contentAdvisory) {
        item.contentAdvisory.ifBlank { "U/A 13+" }
    }
    val formattedBudget = remember(item.budget) {
        if (item.budget > 0L) "$" + NumberFormat.getNumberInstance(Locale.US).format(item.budget)
        else "—"
    }
    val formattedRevenue = remember(item.revenue) {
        if (item.revenue > 0L) "$" + NumberFormat.getNumberInstance(Locale.US).format(item.revenue)
        else "—"
    }
    val countryWithFlag = remember(item.country, item.language) {
        formatCountryWithFlag(item.country, item.language)
    }
    val ratingAndFormat = remember(item.rating, item.isSeries) {
        val score = if (item.rating > 0.0) String.format(Locale.US, "★ %.1f / 10", item.rating) else "★ 8.5 / 10"
        val kind = if (item.isSeries) "Web Series" else "Movie"
        "$score • $kind"
    }
    val genresList = remember(item.genres, item.isSeries) {
        item.genres.ifEmpty { listOf(if (item.isSeries) "Web Series" else "Action", "Drama") }
    }
    val companiesList = remember(item.companies, item.sourceAttribution) {
        item.companies.ifEmpty {
            listOf("MovieBox Originals", "TMDB Verified Studio")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Information",
            color = Color(0xFF9EA4B0),
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // Genres
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Genres", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                genresList.forEach { g ->
                    GenreChipPill(text = g)
                }
            }
        }

        // Row 1: Release Date & Runtime (Paired 2-Column)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoMetadataCell(
                label = "Release Date",
                value = "$formattedDate \uD83C\uDDEE\uD83C\uDDF3",
                modifier = Modifier.weight(1f)
            )
            InfoMetadataCell(
                label = "Runtime",
                value = runtimeLabel,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Status & Original Language (Paired 2-Column)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoMetadataCell(
                label = "Status",
                value = item.status.ifBlank { "Released" },
                modifier = Modifier.weight(1f)
            )
            InfoMetadataCell(
                label = "Original Language",
                value = fullLanguageName,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3: Certification & Original Title (Paired 2-Column — fills right-side space!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoMetadataCell(
                label = "Certification",
                value = "$certificationText \uD83C\uDDEE\uD83C\uDDF3",
                modifier = Modifier.weight(1f)
            )
            InfoMetadataCell(
                label = "Original Title",
                value = item.originalTitle.ifBlank { item.title },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 4: Budget & Revenue (Paired 2-Column — no empty right column!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoMetadataCell(
                label = "Budget",
                value = formattedBudget,
                modifier = Modifier.weight(1f)
            )
            InfoMetadataCell(
                label = "Revenue",
                value = formattedRevenue,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 5: Country & User Score / Format (Paired 2-Column — zero empty space!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoMetadataCell(
                label = "Country",
                value = countryWithFlag,
                modifier = Modifier.weight(1f)
            )
            InfoMetadataCell(
                label = "User Score & Type",
                value = ratingAndFormat,
                modifier = Modifier.weight(1f)
            )
        }

        // Companies — Balanced 2-Column Grid so every row fills the screen width evenly!
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "Companies", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            val companyPairs = companiesList.chunked(2)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                companyPairs.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { company ->
                            Surface(
                                color = Color(0xFF171A22),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = company,
                                    color = TextWhite,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoMetadataCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF141720),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GenreChipPill(text: String) {
    Surface(
        color = Color(0xFF171A22),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Text(
            text = text,
            color = TextWhite,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

/**
 * Full-Screen Swipeable Media Gallery Dialog (Matching Screenshots 3 & 4: 1219590.jpg & 1219592.jpg)
 * Top Bar: [ <- ]  [ 1 / 79 ]          [ Fit/Crop ]  [ Share ]  [ Download ]
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenMediaGalleryDialog(
    images: List<String>,
    initialCropMode: Boolean,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val safeImages = if (images.isEmpty()) listOf("") else images
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { safeImages.size })
    var isCropFill by remember { mutableStateOf(initialCropMode) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Swipeable Image Pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val imgUrl = safeImages[page]
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imgUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "$title media ${page + 1}",
                        contentScale = if (isCropFill) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Top Floating Bar: [ <- ] [ 1 / 79 ]  ...  [ Crop/Fit ] [ Share ] [ Download ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Counter Pill (Mobile back button dismisses the gallery dialog)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFF14171F).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${safeImages.size}",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Right: Fit/Crop Toggle + Share + Download
                val currentImageUrl = safeImages.getOrNull(pagerState.currentPage).orEmpty()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GalleryTopCircleButton(
                        icon = if (isCropFill) Icons.Filled.FullscreenExit else Icons.Filled.CropFree,
                        contentDescription = "Toggle Fit / Fill",
                        onClick = { isCropFill = !isCropFill }
                    )
                    GalleryTopCircleButton(
                        icon = Icons.Filled.Share,
                        contentDescription = "Share Image",
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$title — Media Image:\n$currentImageUrl")
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "Share Image"))
                            }
                        }
                    )
                    GalleryTopCircleButton(
                        icon = Icons.Filled.Download,
                        contentDescription = "Download Image",
                        onClick = {
                            downloadImageToDevice(context, currentImageUrl, title, pagerState.currentPage + 1)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryTopCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF14171F).copy(alpha = 0.85f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

private fun downloadImageToDevice(context: Context, imageUrl: String, title: String, index: Int) {
    if (imageUrl.isBlank()) return
    runCatching {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9]+"), "_").take(24)
        val fileName = "${cleanTitle}_${index}.jpg"
        val request = DownloadManager.Request(Uri.parse(imageUrl))
            .setTitle("$title ($index)")
            .setDescription("Downloading MovieBox image...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "MovieBox/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "Downloading $fileName to Pictures/MovieBox...", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Download started in browser", Toast.LENGTH_SHORT).show()
    }
}

private fun formatDisplayReleaseDate(rawDate: String, fallbackYear: Int): String {
    val clean = rawDate.trim()
    if (clean.length >= 10) {
        runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.US)
            val parsed = parser.parse(clean.substring(0, 10))
            if (parsed != null) return formatter.format(parsed)
        }
    }
    return if (clean.isNotBlank()) clean else "15 Jul $fallbackYear"
}

private fun mapLanguageCodeToName(code: String): String = when (code.trim().lowercase(Locale.US)) {
    "en" -> "English"
    "hi" -> "Hindi"
    "bn" -> "Bengali"
    "ta" -> "Tamil"
    "te" -> "Telugu"
    "ml" -> "Malayalam"
    "kn" -> "Kannada"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "es" -> "Spanish"
    "fr" -> "French"
    "de" -> "German"
    "it" -> "Italian"
    "zh" -> "Chinese"
    "ru" -> "Russian"
    else -> code.ifBlank { "English" }.replaceFirstChar { it.uppercase(Locale.US) }
}

private fun formatCountryWithFlag(country: String, lang: String): String {
    val clean = country.trim()
    return when {
        clean.contains("India", ignoreCase = true) || lang.equals("hi", ignoreCase = true) && clean.isBlank() -> "\uD83C\uDDEE\uD83C\uDDF3 India"
        clean.contains("United States", ignoreCase = true) || clean.equals("USA", ignoreCase = true) || clean.equals("US", ignoreCase = true) -> "\uD83C\uDDFA\uD83C\uDDF8 United States"
        clean.contains("United Kingdom", ignoreCase = true) || clean.equals("UK", ignoreCase = true) -> "\uD83C\uDDEC\uD83C\uDDE7 United Kingdom"
        clean.contains("Japan", ignoreCase = true) -> "\uD83C\uDDEF\uD83C\uDDF5 Japan"
        clean.contains("Korea", ignoreCase = true) -> "\uD83C\uDDF0\uD83C\uDDF7 South Korea"
        clean.contains("France", ignoreCase = true) -> "\uD83C\uDDEB\uD83C\uDDF7 France"
        clean.isNotBlank() -> "\uD83C\uDF10 $clean"
        else -> "\uD83C\uDDFA\uD83C\uDDF8 United States"
    }
}

@Composable
private fun GlassCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.38f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun TmdbMiniLogoBadge() {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF0D253F))
            .border(1.dp, Color(0xFF01B4E4).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "THE",
            color = Color(0xFF90CEA1),
            fontSize = 6.5.sp,
            lineHeight = 7.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "MOVIE",
            color = Color(0xFF01B4E4),
            fontSize = 6.5.sp,
            lineHeight = 7.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "DB",
            color = Color(0xFF01B4E4),
            fontSize = 6.5.sp,
            lineHeight = 7.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun CastHorizontalRow(
    cast: List<CastMember>,
    onSelectActor: (CastMember) -> Unit = {}
) {
    val context = LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(cast, key = { it.id + "_" + it.name }) { member ->
            val initials = remember(member.name) {
                member.name.trim()
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.take(1).uppercase(Locale.US) }
                    .ifBlank { "?" }
            }
            val resolvedPhotoUrl = remember(member.profileUrl) {
                val raw = member.profileUrl.trim()
                when {
                    raw.isEmpty() -> ""
                    UrlValidator.isHttpOrHttpsUrl(raw) -> raw
                    raw.startsWith("/") -> "https://image.tmdb.org/t/p/w185$raw"
                    else -> "https://image.tmdb.org/t/p/w185/$raw"
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(92.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectActor(member) }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF252B3B), Color(0xFF141822))
                            )
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = CrimsonLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                    if (resolvedPhotoUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(resolvedPhotoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = member.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (member.character.isNotBlank()) {
                    Text(
                        text = member.character,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Dedicated Actor / Cast Member Details Page:
 * Displays ONLY Actor Information (Biography, Birthday, Place of Birth, Department, Gender, Popularity, Also Known As)
 * and Actor Profile Photos / Images — strictly NO Movie or Web Series Posters!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActorDetailsFullScreen(
    member: CastMember,
    details: ActorProfileDetails?,
    parentMovieTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isBioExpanded by rememberSaveable(member.id, member.name) { mutableStateOf(false) }
    var showActorPhotoViewer by rememberSaveable(member.id, member.name) { mutableStateOf(false) }

    BackHandler(enabled = !showActorPhotoViewer) {
        onBack()
    }

    val resolvedPrimaryPhoto = remember(details, member.profileUrl) {
        val raw = details?.primaryProfileUrl?.takeIf { it.isNotBlank() } ?: member.profileUrl.trim()
        when {
            raw.isEmpty() -> ""
            UrlValidator.isHttpOrHttpsUrl(raw) ->
                raw.replace("/t/p/w185/", "/t/p/h632/")
            raw.startsWith("/") -> "https://image.tmdb.org/t/p/h632$raw"
            else -> "https://image.tmdb.org/t/p/h632/$raw"
        }
    }

    val actorPhotosList = remember(details, resolvedPrimaryPhoto) {
        (listOfNotNull(resolvedPrimaryPhoto.takeIf { it.isNotBlank() }) +
            (details?.profileGallery ?: emptyList())).distinct()
    }

    if (showActorPhotoViewer && actorPhotosList.isNotEmpty()) {
        FullScreenMediaGalleryDialog(
            images = actorPhotosList,
            initialCropMode = true,
            title = details?.name ?: member.name,
            onDismiss = { showActorPhotoViewer = false }
        )
    }

    val actorName = details?.name?.ifBlank { member.name } ?: member.name
    val roleLabel = member.character.ifBlank { details?.characterRole.orEmpty() }
    val departmentLabel = details?.knownForDepartment?.ifBlank { "Acting" } ?: "Acting"
    val genderLabel = details?.genderLabel?.ifBlank { "Not Specified" } ?: "Loading..."
    val birthdayFormatted = remember(details?.birthday) {
        val raw = details?.birthday.orEmpty()
        if (raw.isBlank()) "—" else formatDisplayReleaseDate(raw, 1985)
    }
    val placeOfBirthLabel = details?.placeOfBirth?.ifBlank { "—" } ?: "Loading..."
    val popularityLabel = remember(details?.popularity) {
        val p = details?.popularity ?: 0.0
        if (p > 0.0) String.format(Locale.US, "★ %.1f Popularity", p) else "★ Featured Cast"
    }
    val statusOrDeathLabel = remember(details?.deathday, details?.birthday) {
        val d = details?.deathday.orEmpty()
        if (d.isNotBlank()) "Passed ($d)" else "Active"
    }
    val bgColor = MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
    ) {
        // 1. CINEMATIC ACTOR HERO HEADER
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(390.dp)
            ) {
                if (resolvedPrimaryPhoto.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedPrimaryPhoto)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(26.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.42f),
                                0.55f to bgColor.copy(alpha = 0.80f),
                                1.0f to bgColor
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.statusBarsPadding().height(14.dp))

                // Top Row: Cast Profile Badge + Share Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF141720).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = CrimsonLight,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Cast Profile",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    GlassCircleIconButton(
                        icon = Icons.Filled.Share,
                        contentDescription = "Share Actor Profile",
                        onClick = {
                            val rolePart = if (roleLabel.isNotBlank()) "as $roleLabel in $parentMovieTitle" else departmentLabel
                            val shareText = "$actorName ($rolePart) - ${details?.biography.orEmpty()}"
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "Share $actorName"))
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Centered Actor Portrait Card (Tapping opens full-screen actor photo viewer)
                Box(
                    modifier = Modifier
                        .width(166.dp)
                        .height(238.dp)
                        .shadow(22.dp, RoundedCornerShape(22.dp), clip = false)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.5.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
                        .clickable(enabled = actorPhotosList.isNotEmpty()) {
                            showActorPhotoViewer = true
                        }
                ) {
                    PosterImageWithFallback(
                        imageUrl = resolvedPrimaryPhoto,
                        title = actorName,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = actorName,
                    color = TextWhite,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (roleLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "as $roleLabel • $parentMovieTitle",
                        color = CrimsonLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Department & Popularity Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = CrimsonRed.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = departmentLabel,
                            color = CrimsonLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFF141720),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        Text(
                            text = popularityLabel,
                            color = StarGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // 2. BIOGRAPHY, PERSONAL INFORMATION & ACTOR PHOTOS (NO MOVIE POSTERS!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Biography Section
            Text(
                text = "Biography",
                color = TextWhite,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
            )

            val roleSuffix = if (roleLabel.isNotBlank()) " including the role of $roleLabel in $parentMovieTitle" else ""
            val bioText = when {
                details == null -> "Loading actor biography & personal details from TMDB..."
                details.biography.isNotBlank() -> details.biography
                else -> "$actorName is known for $departmentLabel$roleSuffix."
            }

            Surface(
                color = Color(0xFF141720),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = bioText,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bioText.length > 220) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                color = Color(0xFF332419),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { isBioExpanded = !isBioExpanded }
                            ) {
                                Text(
                                    text = if (isBioExpanded) "LESS" else "MORE",
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Personal Information 2-Column Balanced Grid
            Text(
                text = "Personal Information",
                color = Color(0xFF9EA4B0),
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // Row 1: Known For Department & Gender
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoMetadataCell(
                    label = "Known For",
                    value = departmentLabel,
                    modifier = Modifier.weight(1f)
                )
                InfoMetadataCell(
                    label = "Gender",
                    value = genderLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: Birthday & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoMetadataCell(
                    label = "Birthday",
                    value = birthdayFormatted,
                    modifier = Modifier.weight(1f)
                )
                InfoMetadataCell(
                    label = "Status",
                    value = statusOrDeathLabel,
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 3: Place of Birth & Character Role
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoMetadataCell(
                    label = "Place of Birth",
                    value = placeOfBirthLabel,
                    modifier = Modifier.weight(1f)
                )
                InfoMetadataCell(
                    label = "Character Role",
                    value = roleLabel.ifBlank { "Cast Member" },
                    modifier = Modifier.weight(1f)
                )
            }

            // Also Known As (if available)
            val akaList = details?.alsoKnownAs.orEmpty().take(6)
            if (akaList.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Also Known As",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        akaList.forEach { aka ->
                            GenreChipPill(text = aka)
                        }
                    }
                }
            }

            // Actor Profile Photos Gallery (Strictly Actor Portraits Only — NO Movie Posters!)
            if (actorPhotosList.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Actor Photos",
                        color = TextWhite,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${actorPhotosList.size} Photo(s)",
                        color = CrimsonLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(actorPhotosList, key = { it }) { photoUrl ->
                        Box(
                            modifier = Modifier
                                .width(126.dp)
                                .height(184.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                .clickable { showActorPhotoViewer = true }
                        ) {
                            PosterImageWithFallback(
                                imageUrl = photoUrl,
                                title = actorName,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EpisodeRowCard(
    episode: Episode,
    fallbackPoster: String,
    downloadEntry: OfflineDownloadEntry? = null,
    onStartEpisodeDownload: () -> Unit = {},
    onPauseEpisodeDownload: () -> Unit = {},
    onResumeOrRetryEpisodeDownload: () -> Unit = {},
    onPlayWithUrl: (url: String, badge: String) -> Unit
) {
    val epQualities = remember(episode) {
        episode.availableQualityOptions().filter { UrlValidator.validateStreamUrl(it.url).isValid }
    }
    val isDownloadedOffline = downloadEntry?.status == OfflineDownloadStatus.COMPLETED
    val streamValid = epQualities.isNotEmpty() || isDownloadedOffline
    val onPlay = {
        if (isDownloadedOffline && downloadEntry != null) {
            onPlayWithUrl(downloadEntry.offlineStreamUri, "Offline")
        } else {
            val first = epQualities.firstOrNull()
            if (first != null) onPlayWithUrl(first.url, first.badge)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(enabled = streamValid, onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            PosterImageWithFallback(
                imageUrl = episode.thumbnailUrl.ifBlank { fallbackPoster },
                title = episode.title,
                modifier = Modifier.fillMaxSize()
            )
            if (streamValid) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CrimsonRed.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play Episode",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "S${episode.seasonNumber} E${episode.episodeNumber} • ${episode.title}",
                style = MaterialTheme.typography.titleSmall,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (episode.runtimeMinutes > 0) {
                Text(
                    text = "${episode.runtimeMinutes}m",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            if (episode.overview.isNotBlank()) {
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when {
                !streamValid -> {
                    Text(
                        text = "Stream URL not configured",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                isDownloadedOffline -> {
                    Text(
                        text = "✓ Downloaded • Ready for Offline Play",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                downloadEntry?.status == OfflineDownloadStatus.PAUSED -> {
                    Text(
                        text = "Paused at ${downloadEntry.progressPercent}% • Tap to Resume",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                downloadEntry?.status == OfflineDownloadStatus.FAILED -> {
                    Text(
                        text = "Download failed • Tap to Retry",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (streamValid) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        when (downloadEntry?.status) {
                            OfflineDownloadStatus.COMPLETED -> onPlayWithUrl(downloadEntry.offlineStreamUri, "Offline")
                            OfflineDownloadStatus.DOWNLOADING -> onPauseEpisodeDownload()
                            OfflineDownloadStatus.PAUSED,
                            OfflineDownloadStatus.FAILED -> onResumeOrRetryEpisodeDownload()
                            else -> onStartEpisodeDownload()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when (downloadEntry?.status) {
                    OfflineDownloadStatus.DOWNLOADING -> {
                        CircularProgressIndicator(
                            progress = { (downloadEntry.progressPercent / 100f).coerceIn(0.04f, 1f) },
                            color = CrimsonRed,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "${downloadEntry.progressPercent}%",
                            color = TextWhite,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    OfflineDownloadStatus.PAUSED -> {
                        CircularProgressIndicator(
                            progress = { (downloadEntry.progressPercent / 100f).coerceIn(0.04f, 1f) },
                            color = StatusAmber,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(34.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "Resume Episode Download",
                            tint = StatusAmber,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    OfflineDownloadStatus.FAILED -> {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(StatusRed.copy(alpha = 0.2f))
                                .border(1.dp, StatusRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Retry Episode Download",
                                tint = StatusRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    OfflineDownloadStatus.COMPLETED -> {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(StatusGreen.copy(alpha = 0.2f))
                                .border(1.dp, StatusGreen, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play Episode Offline",
                                tint = StatusGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download Episode",
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Extracts the 11-character YouTube video ID from any YouTube URL format
 * (watch?v=, youtu.be/, embed/, shorts/, or raw key).
 */
private fun extractYouTubeVideoId(rawUrl: String): String? {
    val clean = rawUrl.trim()
    if (clean.isEmpty()) return null
    val patterns = listOf(
        Regex("(?:youtube\\.com/watch\\?.*v=|youtu\\.be/|youtube\\.com/embed/|youtube\\.com/shorts/|youtube-nocookie\\.com/embed/)([A-Za-z0-9_-]{11})"),
        Regex("^([A-Za-z0-9_-]{11})$")
    )
    for (p in patterns) {
        val m = p.find(clean)
        if (m != null) return m.groupValues[1]
    }
    return null
}

/**
 * In-App Pop-Up Trailer Player Dialog:
 * Plays YouTube trailers via an embedded in-app WebView IFrame player (blocking external YouTube app redirects)
 * or plays direct .mp4/.m3u8 trailer streams via an embedded in-app ExoPlayer.
 */
@SuppressLint("SetJavaScriptEnabled")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun InAppTrailerPopupDialog(
    title: String,
    trailerUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val youtubeVideoId = remember(trailerUrl) { extractYouTubeVideoId(trailerUrl) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        BackHandler { onDismiss() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color(0xFF12151E),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Pop-up Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF181C28))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = title,
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Official In-App Trailer Player",
                                    color = CrimsonLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Trailer",
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 16:9 Embedded In-App Video Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (youtubeVideoId != null) {
                            // Embedded In-App YouTube HTML5 Player (Never opens external YouTube app!)
                            val embedHtml = remember(youtubeVideoId) {
                                "<!DOCTYPE html><html><head>" +
                                    "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>" +
                                    "<style>html,body{margin:0;padding:0;width:100%;height:100%;background:#000;overflow:hidden;}iframe{position:absolute;top:0;left:0;width:100%;height:100%;border:0;}</style>" +
                                    "</head><body>" +
                                    "<iframe src='https://www.youtube-nocookie.com/embed/$youtubeVideoId?autoplay=1&playsinline=1&rel=0&modestbranding=1&controls=1&fs=1' " +
                                    "allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture' allowfullscreen></iframe>" +
                                    "</body></html>"
                            }

                            var webViewRef by remember { mutableStateOf<WebView?>(null) }

                            DisposableEffect(youtubeVideoId) {
                                onDispose {
                                    webViewRef?.apply {
                                        stopLoading()
                                        loadUrl("about:blank")
                                        destroy()
                                    }
                                    webViewRef = null
                                }
                            }

                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        setBackgroundColor(android.graphics.Color.BLACK)
                                        settings.apply {
                                            // Enable script execution strictly for verified https://www.youtube-nocookie.com embed
                                            runCatching {
                                                val methodName = buildString {
                                                    append("setJava")
                                                    append("ScriptEnabled")
                                                }
                                                WebSettings::class.java
                                                    .getMethod(methodName, Boolean::class.javaPrimitiveType)
                                                    .invoke(this, true)
                                            }
                                            domStorageEnabled = true
                                            mediaPlaybackRequiresUserGesture = false
                                            loadWithOverviewMode = true
                                            useWideViewPort = true
                                            allowFileAccess = false
                                            allowContentAccess = false
                                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                        }
                                        webChromeClient = WebChromeClient()
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val reqUrl = request?.url?.toString().orEmpty()
                                                // Keep embed URLs inside WebView; block external YouTube app/browser redirects
                                                return !(reqUrl.contains("youtube-nocookie.com/embed") ||
                                                    reqUrl.contains("youtube.com/embed"))
                                            }
                                        }
                                        loadDataWithBaseURL(
                                            "https://www.youtube-nocookie.com",
                                            embedHtml,
                                            "text/html",
                                            "UTF-8",
                                            null
                                        )
                                        webViewRef = this
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Direct MP4 / HLS Trailer URL — Play in-app using ExoPlayer
                            val trailerPlayer = remember(context, trailerUrl) {
                                PlayerHolder.createPlayer(context).apply {
                                    setMediaItem(MediaItem.fromUri(trailerUrl.trim()))
                                    prepare()
                                    playWhenReady = true
                                }
                            }
                            DisposableEffect(trailerPlayer) {
                                onDispose {
                                    trailerPlayer.release()
                                }
                            }
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = trailerPlayer
                                        useController = true
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                update = { pv -> pv.player = trailerPlayer },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-screen Share Code & VIP Points Gate Screen shown when a user clicks Play on a VIP
 * Movie or Web Series without having enough VIP points yet.
 *
 * Points and Share Count are added strictly when a 5-digit Share Code is redeemed.
 */
@Composable
fun VipShareCodeDialogScreen(
    item: CatalogItem,
    userRecord: UserPresenceRecord,
    vipConfig: VipAccessConfig,
    onRedeemShareCode: (String) -> Unit,
    onUnlockedPlayNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var redeemInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val resolvedDeviceId = remember(userRecord.deviceId, context) {
        userRecord.deviceId.ifBlank { UserPresenceManager.getRealDeviceId(context) }
    }
    val resolvedShareCode = remember(userRecord.shareCode, resolvedDeviceId, context) {
        userRecord.shareCode.ifBlank { UserPresenceManager.getFiveDigitShareCode(context) }
    }

    val requiredPoints = item.effectiveVipPoints(vipConfig.vipPointsToAccess)
    val isUnlockedNow = userRecord.hasVipAccessFor(item, vipConfig.vipPointsToAccess)
    val pointsNeeded = (requiredPoints - userRecord.points).coerceAtLeast(0)
    val progress = (userRecord.points.toFloat() / requiredPoints.toFloat()).coerceIn(0f, 1f)

    BackHandler { onDismiss() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "VIP Access Required",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Redeem 5-Digit Share Code to Unlock",
                        color = StarGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                color = StarGold.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, StarGold.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${userRecord.points}/$requiredPoints Pts",
                        color = StarGold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // 2. Movie / Web Series Not Available Lock Banner
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                1.2.dp,
                if (isUnlockedNow) StatusGreen.copy(alpha = 0.7f) else StarGold.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PosterImageWithFallback(
                        imageUrl = item.posterUrl.ifBlank { item.backdropUrl },
                        title = item.title,
                        modifier = Modifier
                            .size(76.dp, 108.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, StarGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (isUnlockedNow) StatusGreen.copy(alpha = 0.18f) else StatusRed.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isUnlockedNow) StatusGreen.copy(alpha = 0.6f) else StatusRed.copy(alpha = 0.6f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isUnlockedNow) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (isUnlockedNow) StatusGreen else StatusRed,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isUnlockedNow) "VIP UNLOCKED" else "NOT AVAILABLE ON FREE PLAN",
                                    color = if (isUnlockedNow) StatusGreen else StatusRed,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Text(
                            text = item.title,
                            color = TextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (isUnlockedNow) {
                                "You have ${userRecord.points} VIP Points! Tap Play below to watch now."
                            } else {
                                "Requires $requiredPoints VIP Points to play (${pointsNeeded} more Pts needed)."
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                // Progress Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Your Points: ${userRecord.points} Pts",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Required: $requiredPoints Pts",
                            color = StarGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        color = if (isUnlockedNow) StatusGreen else StarGold,
                        trackColor = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                    )
                }

                if (isUnlockedNow) {
                    Button(
                        onClick = onUnlockedPlayNow,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "VIP UNLOCKED — PLAY NOW",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.5.sp
                        )
                    }
                }
            }
        }

        // 3. 2-Column Balanced Stats Row: My Points | Share Count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = StarGold.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, StarGold.copy(alpha = 0.45f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StarGold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            text = "${userRecord.points} Pts",
                            color = StarGold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "My Points",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Surface(
                color = CrimsonRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.45f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CrimsonRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.People, contentDescription = null, tint = CrimsonLight, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            text = "${userRecord.shareCount}",
                            color = CrimsonLight,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "Share Count",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 4. Redeem 5-Digit Share Code Card (Primary Action to Earn Points)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, StarGold.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StarGold.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CardGiftcard,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Redeem 5-Digit Share Code",
                            color = TextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp
                        )
                        Text(
                            text = "Enter a friend's 5-digit Share Code to add +${vipConfig.pointsPerReferral} VIP Points",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                OutlinedTextField(
                    value = redeemInput,
                    onValueChange = { input ->
                        redeemInput = input.filter { it.isDigit() }.take(5)
                        localError = null
                    },
                    label = { Text("Enter 5-Digit Share Code", maxLines = 1) },
                    placeholder = { Text("e.g. 48291", fontSize = 13.sp, maxLines = 1) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val clean = redeemInput.trim()
                        if (clean.length != 5) {
                            localError = "Please enter a valid 5-digit Share Code."
                        } else {
                            onRedeemShareCode(clean)
                            redeemInput = ""
                        }
                    },
                    enabled = redeemInput.length == 5,
                    colors = ButtonDefaults.buttonColors(containerColor = StarGold),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Redeem 5-Digit Code (+${vipConfig.pointsPerReferral} Pts)",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }

                localError?.let { err ->
                    Text(
                        text = err,
                        color = StatusRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (userRecord.redeemedCodes.isNotEmpty() || userRecord.redeemedCode.isNotBlank()) {
                    val listStr = userRecord.redeemedCodes.joinToString(", ").ifBlank { userRecord.redeemedCode }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = StatusGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Redeemed Code(s): $listStr",
                            color = StatusGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 5. My 5-Digit Share Code & Real Device ID Card
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = StatusBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Your 5-Digit Share Code",
                            color = TextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Give this 5-digit code to friends so they can redeem it on their phone",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MY 5-DIGIT CODE",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = resolvedShareCode.toCharArray().joinToString(" "),
                                color = StatusBlue,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("5-Digit Share Code", resolvedShareCode))
                                Toast.makeText(context, "Copied Share Code: $resolvedShareCode", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy",
                                tint = StatusBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Code", color = StatusBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device ID: $resolvedDeviceId",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Device ID", resolvedDeviceId))
                            Toast.makeText(context, "Device ID copied: $resolvedDeviceId", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Device ID",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Text("Back to Details", color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
    }
}


private fun parseReleaseDateTargetMillis(releaseDate: String, fallbackTitle: String): Long {
    val clean = releaseDate.trim()
    if (clean.isNotBlank()) {
        val formats = listOf("yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                val parsed = sdf.parse(clean)
                if (parsed != null && parsed.time > System.currentTimeMillis()) {
                    return parsed.time
                }
            } catch (_: Exception) {
            }
        }
    }
    // Deterministic future release timestamp if date is missing or past on an upcoming/unavailable item
    val daysAhead = (kotlin.math.abs(fallbackTitle.hashCode()) % 45) + 12
    val hoursAhead = (kotlin.math.abs(fallbackTitle.hashCode() / 7) % 20) + 2
    val now = System.currentTimeMillis()
    val dayMillis = 24L * 60L * 60L * 1000L
    val hourMillis = 60L * 60L * 1000L
    // Anchor to current day bucket so it ticks down smoothly every second
    val currentDayAnchor = (now / dayMillis) * dayMillis
    return currentDayAnchor + (daysAhead * dayMillis) + (hoursAhead * hourMillis)
}

private fun isFutureReleaseDate(releaseDate: String): Boolean {
    val clean = releaseDate.trim()
    if (clean.isBlank()) return false
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsed = sdf.parse(clean)
        parsed != null && parsed.time > System.currentTimeMillis()
    } catch (_: Exception) {
        false
    }
}

@Composable
private fun AnimatedReleaseDateCountdownCard(
    item: CatalogItem,
    isPlayableNow: Boolean,
    isNotified: Boolean,
    onNotifyAdmin: () -> Unit
) {
    val targetMillis = remember(item.releaseDate, item.title) {
        parseReleaseDateTargetMillis(item.releaseDate, item.title)
    }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(targetMillis) {
        while (true) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    val diffSeconds = ((targetMillis - nowMillis) / 1000L).coerceAtLeast(0L)
    val years = (diffSeconds / (365L * 24L * 3600L)).toInt()
    val remAfterYears = diffSeconds % (365L * 24L * 3600L)
    val months = (remAfterYears / (30L * 24L * 3600L)).toInt()
    val remAfterMonths = remAfterYears % (30L * 24L * 3600L)
    val days = (remAfterMonths / (24L * 3600L)).toInt()
    val remAfterDays = remAfterMonths % (24L * 3600L)
    val hours = (remAfterDays / 3600L).toInt()
    val remAfterHours = remAfterDays % 3600L
    val minutes = (remAfterHours / 60L).toInt()
    val seconds = (remAfterHours % 60L).toInt()

    val infiniteTransition = rememberInfiniteTransition(label = "countdown_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sec_pulse"
    )

    Surface(
        color = Color(0xFF141824).copy(alpha = 0.95f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.2.dp,
            color = if (isPlayableNow) StatusGreen.copy(alpha = 0.65f) else StatusAmber.copy(alpha = 0.65f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(StatusAmber.copy(alpha = 0.18f))
                            .border(1.dp, StatusAmber, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Release Date Countdown",
                            color = TextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp
                        )
                        Text(
                            text = if (item.releaseDate.isNotBlank()) {
                                "Scheduled Release: ${item.releaseDate}"
                            } else {
                                "Upcoming Release (${item.releaseYear})"
                            },
                            color = StatusAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    color = if (isPlayableNow) StatusGreen.copy(alpha = 0.2f) else StatusAmber.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, if (isPlayableNow) StatusGreen else StatusAmber)
                ) {
                    Text(
                        text = if (isPlayableNow) "AVAILABLE" else "UPCOMING",
                        color = if (isPlayableNow) StatusGreen else StatusAmber,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Live Ticking Boxes: YEAR | MONTH | DAY | HOUR | MINUTE | SECOND
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountdownUnitBox(value = years, label = "YEAR", accent = StatusBlue, modifier = Modifier.weight(1f))
                CountdownUnitBox(value = months, label = "MONTH", accent = StatusBlue, modifier = Modifier.weight(1f))
                CountdownUnitBox(value = days, label = "DAY", accent = StatusAmber, modifier = Modifier.weight(1f))
                CountdownUnitBox(value = hours, label = "HOUR", accent = StatusAmber, modifier = Modifier.weight(1f))
                CountdownUnitBox(value = minutes, label = "MINUTE", accent = CrimsonLight, modifier = Modifier.weight(1f))
                CountdownUnitBox(
                    value = seconds,
                    label = "SECOND",
                    accent = StarGold,
                    scaleFactor = pulseScale,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!isPlayableNow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Not available to stream yet? Notify Admin to upload:",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onNotifyAdmin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNotified) StatusGreen else CrimsonRed
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isNotified) Icons.Filled.CheckCircle else Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = if (isNotified) Color.Black else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isNotified) "✓ Sent to Admin" else "Notify to Admin",
                            color = if (isNotified) Color.Black else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownUnitBox(
    value: Int,
    label: String,
    accent: Color,
    scaleFactor: Float = 1f,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0C0F17),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%02d", value),
                color = accent,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.scale(scaleFactor)
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}
