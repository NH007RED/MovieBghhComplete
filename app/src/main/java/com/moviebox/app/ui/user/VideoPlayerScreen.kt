package com.moviebox.app.ui.user

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Episode
import com.moviebox.app.data.model.QualityStreamOption
import com.moviebox.app.finder.DirectStreamResolver
import com.moviebox.app.player.PlayHeaders
import com.moviebox.app.player.PlayerHolder
import com.moviebox.app.player.SubtitleCue
import com.moviebox.app.player.SubtitleTools
import com.moviebox.app.ui.components.FilterPill
import com.moviebox.app.ui.components.PosterCard
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.TextMuted
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import com.moviebox.app.util.OfflineDownloadManager
import com.moviebox.app.util.UrlValidator
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PlayerGestureMode {
    NONE,
    SEEK,
    BRIGHTNESS,
    VOLUME
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    title: String,
    streamUrl: String,
    currentMovieItem: CatalogItem? = null,
    similarMovies: List<CatalogItem> = emptyList(),
    seriesItem: CatalogItem? = null,
    currentSeasonNumber: Int? = null,
    currentEpisodeNumber: Int? = null,
    autoPlayNext: Boolean = true,
    onBack: () -> Unit,
    onSwitchEpisode: (Episode) -> Unit = {},
    onSelectSimilarMovie: (CatalogItem) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // Active Episode object when watching a Web Series
    val currentEpisodeObj: Episode? = remember(seriesItem, currentSeasonNumber, currentEpisodeNumber) {
        seriesItem?.seasons
            ?.firstOrNull { it.seasonNumber == currentSeasonNumber }
            ?.episodes
            ?.firstOrNull { it.episodeNumber == currentEpisodeNumber }
    }

    // 3 Admin-Configured Quality Links (1080p, 720p, 480p) for current Movie or Episode
    val adminQualityOptions: List<QualityStreamOption> = remember(currentMovieItem, currentEpisodeObj, streamUrl) {
        val rawOptions = when {
            currentEpisodeObj != null -> currentEpisodeObj.availableQualityOptions()
            currentMovieItem != null -> currentMovieItem.availableQualityOptions()
            else -> emptyList()
        }.filter { UrlValidator.validateStreamUrl(it.url).isValid }
        if (rawOptions.isNotEmpty()) rawOptions
        else if (UrlValidator.validateStreamUrl(streamUrl).isValid) {
            listOf(QualityStreamOption("1080p / Auto", "Auto", streamUrl))
        } else emptyList()
    }

    // Active stream URL (can be switched live between 1080p, 720p, 480p while keeping playback position!)
    var activeStreamUrl by rememberSaveable(streamUrl) { mutableStateOf(streamUrl) }
    var resumePositionOnQualitySwitch by remember { mutableLongStateOf(0L) }
    var resolveRetryAttempt by remember(activeStreamUrl) { mutableIntStateOf(0) }
    var hasAutoRetriedOnError by remember(activeStreamUrl) { mutableStateOf(false) }

    val validation = remember(activeStreamUrl) { UrlValidator.validateStreamUrl(activeStreamUrl) }

    // Detect fullscreen either via toggle button OR device landscape orientation!
    var isFullscreenToggle by rememberSaveable { mutableStateOf(false) }
    val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isFullscreen = isFullscreenToggle || isDeviceLandscape

    // Core Player States
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(validation.errorMessage) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }

    // Aspect ratio & video size (0=Fit, 1=Stretch, 2=Crop/Zoom, 3=16:9, 4=4:3)
    var aspectMode by rememberSaveable { mutableIntStateOf(0) }
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var playbackSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }

    // Center gesture flash badge
    var gestureFlashText by remember { mutableStateOf<String?>(null) }

    // External SRT/VTT Subtitle & Subtitle Style/Delay state
    var loadedCues by remember { mutableStateOf<List<SubtitleCue>?>(null) }
    var loadedSubName by remember { mutableStateOf("External Subtitle") }
    var subtitleDelayMs by rememberSaveable { mutableLongStateOf(0L) }
    var subtitleCounter by remember { mutableIntStateOf(0) }
    var subtitleScale by rememberSaveable { mutableFloatStateOf(1.0f) }
    var subtitleColorIndex by rememberSaveable { mutableIntStateOf(0) }

    // Audio Delay state (0..2000 ms via AudioDelayProcessor)
    var audioDelayMs by rememberSaveable { mutableIntStateOf(PlayerHolder.audioDelay.delayMs) }

    // Track Selection state
    var currentTracks by remember { mutableStateOf(Tracks.EMPTY) }
    var availableHlsHeights by remember { mutableStateOf(emptyList<String>()) }
    var selectedQualityLabel by remember(activeStreamUrl, adminQualityOptions) {
        mutableStateOf(
            adminQualityOptions.firstOrNull { it.url == activeStreamUrl }?.badge ?: "Auto"
        )
    }
    var selectedSubtitleLabel by remember { mutableStateOf("Off") }
    var selectedAudioLabel by remember { mutableStateOf("Default") }

    var activeDialog by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // True Immersive Edge-to-Edge Fullscreen System UI Management (ported from VideoPlayerOffline v1.4)
    DisposableEffect(activity, isFullscreen) {
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= 28) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val win = activity?.window
            if (win != null) {
                win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowInsetsControllerCompat(win, win.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Mobile hardware / gesture Back button handler
    BackHandler {
        when {
            isLocked -> {
                showUnlockButton = true
                Toast.makeText(context, "Screen is locked", Toast.LENGTH_SHORT).show()
            }
            isFullscreenToggle -> {
                isFullscreenToggle = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            else -> {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                onBack()
            }
        }
    }

    val initialSeasonIdx = remember(seriesItem, currentSeasonNumber) {
        seriesItem?.seasons?.indexOfFirst { it.seasonNumber == currentSeasonNumber }?.takeIf { it >= 0 } ?: 0
    }
    var selectedSeasonIndex by rememberSaveable(seriesItem?.id, currentSeasonNumber) {
        mutableIntStateOf(initialSeasonIdx)
    }

    val nextEpisode: Episode? = remember(seriesItem, currentSeasonNumber, currentEpisodeNumber) {
        if (seriesItem == null || currentSeasonNumber == null || currentEpisodeNumber == null) null
        else {
            val allEps = seriesItem.seasons.sortedBy { it.seasonNumber }
                .flatMap { it.episodes.sortedBy { e -> e.episodeNumber } }
            val idx = allEps.indexOfFirst {
                it.seasonNumber == currentSeasonNumber && it.episodeNumber == currentEpisodeNumber
            }
            if (idx >= 0 && idx + 1 < allEps.size) allEps[idx + 1] else null
        }
    }

    val prevEpisode: Episode? = remember(seriesItem, currentSeasonNumber, currentEpisodeNumber) {
        if (seriesItem == null || currentSeasonNumber == null || currentEpisodeNumber == null) null
        else {
            val allEps = seriesItem.seasons.sortedBy { it.seasonNumber }
                .flatMap { it.episodes.sortedBy { e -> e.episodeNumber } }
            val idx = allEps.indexOfFirst {
                it.seasonNumber == currentSeasonNumber && it.episodeNumber == currentEpisodeNumber
            }
            if (idx > 0) allEps[idx - 1] else null
        }
    }

    val exoPlayer = remember(context) {
        PlayerHolder.createPlayer(context)
    }

    val applyLoadedSubtitleToPlayer: () -> Unit = {
        val cues = loadedCues
        val current = exoPlayer.currentMediaItem
        if (cues != null && current != null) {
            val file = SubtitleTools.writeShiftedVtt(
                File(context.cacheDir, "subs"),
                cues,
                subtitleDelayMs,
                ++subtitleCounter
            )
            val subConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLabel(loadedSubName)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val updatedItem = current.buildUpon()
                .setSubtitleConfigurations(listOf(subConfig))
                .build()
            val pos = exoPlayer.currentPosition
            val wasPlaying = exoPlayer.playWhenReady
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
            exoPlayer.setMediaItem(updatedItem, pos)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = wasPlaying
            selectedSubtitleLabel = loadedSubName
        }
    }

    val subtitleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@launch
                    val cues = SubtitleTools.parse(SubtitleTools.decode(bytes))
                    if (cues.isEmpty()) {
                        Toast.makeText(context, "Not a valid SRT/VTT subtitle file", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    loadedCues = cues
                    loadedSubName = uri.lastPathSegment?.substringAfterLast('/') ?: "External SRT/VTT"
                    applyLoadedSubtitleToPlayer()
                    Toast.makeText(context, "Loaded subtitle: $loadedSubName", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Couldn't read subtitle file", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Load activeStreamUrl into ExoPlayer:
    // Automatically runs DirectStreamResolver (Gofile token/cookie acquisition + LinkProbe + headless WebView
    // extractor) so links play DIRECTLY inside MovieBox's native ExoPlayer without opening any browser!
    LaunchedEffect(activeStreamUrl, resolveRetryAttempt) {
        if (validation.isValid) {
            playerError = null
            isBuffering = true
            val trimmedUrl = activeStreamUrl.trim()
            if (trimmedUrl.startsWith(OfflineDownloadManager.OFFLINE_URI_PREFIX)) {
                val resolvedOfflineUri = OfflineDownloadManager.prepareOfflinePlaybackUri(context, trimmedUrl)
                if (resolvedOfflineUri.isNullOrBlank()) {
                    playerError = "Offline download chunks could not be loaded."
                    isBuffering = false
                } else {
                    val mediaItem = MediaItem.Builder()
                        .setUri(resolvedOfflineUri)
                        .setMediaId(trimmedUrl)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                        .build()
                    val startPos = resumePositionOnQualitySwitch.coerceAtLeast(0L)
                    resumePositionOnQualitySwitch = 0L
                    exoPlayer.setMediaItem(mediaItem, startPos)
                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                }
            } else {
                val resolvedStream = DirectStreamResolver.resolveForDirectPlay(
                    context = context,
                    rawUrl = trimmedUrl,
                    forceRefreshToken = resolveRetryAttempt > 0
                )
                val finalUrl = resolvedStream.url
                val builder = MediaItem.Builder()
                    .setUri(finalUrl)
                    .setMediaId(trimmedUrl)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())

                val mime = resolvedStream.mimeType ?: PlayHeaders.mimeTypes[finalUrl]
                if (mime == MimeTypes.APPLICATION_M3U8 || mime == MimeTypes.APPLICATION_MPD) {
                    builder.setMimeType(mime)
                }

                val startPos = resumePositionOnQualitySwitch.coerceAtLeast(0L)
                resumePositionOnQualitySwitch = 0L
                exoPlayer.setMediaItem(builder.build(), startPos)
                exoPlayer.setPlaybackSpeed(playbackSpeed)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        } else {
            playerError = validation.errorMessage
            isBuffering = false
        }
    }

    LaunchedEffect(isControlsVisible, isPlaying, isSeeking, isLocked) {
        if (isControlsVisible && isPlaying && !isSeeking && !isLocked) {
            delay(4000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(showUnlockButton, isLocked) {
        if (showUnlockButton && isLocked) {
            delay(3000)
            showUnlockButton = false
        }
    }

    LaunchedEffect(gestureFlashText) {
        if (gestureFlashText != null && !isSeeking) {
            delay(750)
            gestureFlashText = null
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    playerError = null
                }
                durationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                if (playbackState == Player.STATE_ENDED) {
                    if (!isLocked) isControlsVisible = true
                    if (autoPlayNext && nextEpisode != null &&
                        UrlValidator.validateStreamUrl(nextEpisode.primaryStreamUrl).isValid
                    ) {
                        onSwitchEpisode(nextEpisode)
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }

            override fun onPlayerError(error: PlaybackException) {
                // Automatic one-time silent token/cookie refresh & headless stream resolution on HTTP/container error
                if (!hasAutoRetriedOnError && !activeStreamUrl.startsWith(OfflineDownloadManager.OFFLINE_URI_PREFIX)) {
                    hasAutoRetriedOnError = true
                    isBuffering = true
                    resumePositionOnQualitySwitch = exoPlayer.currentPosition.coerceAtLeast(0L)
                    resolveRetryAttempt += 1
                    return
                }
                isBuffering = false
                val httpCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                playerError = if (httpCode != null) {
                    "The server refused this link (HTTP $httpCode).\nTap Retry to refresh stream token & play."
                } else {
                    "Can't play this video (${error.errorCodeName}).\n" +
                        (error.localizedMessage ?: "The stream may be unreachable or use an unsupported format.")
                }
                isControlsVisible = true
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
                val heights = mutableListOf<String>()
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val fmt = group.getTrackFormat(i)
                            if (fmt.height > 0) heights.add("${fmt.height}p")
                        }
                    }
                }
                availableHlsHeights = heights.distinct()

                val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
                val activeAudioIdx = audioGroups.indexOfFirst { it.isSelected }
                selectedAudioLabel = if (activeAudioIdx >= 0) {
                    val f = audioGroups[activeAudioIdx].getTrackFormat(0)
                    val codec = PlayerHolder.codecName(f.sampleMimeType)
                    PlayerHolder.trackLabel(f, activeAudioIdx + 1, "Audio") +
                        (if (codec.isNotEmpty()) " • $codec" else "")
                } else {
                    "Default"
                }

                val textDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                val activeSubIdx = textGroups.indexOfFirst { it.isSelected }
                selectedSubtitleLabel = when {
                    textDisabled -> "Off"
                    activeSubIdx >= 0 -> PlayerHolder.trackLabel(
                        textGroups[activeSubIdx].getTrackFormat(0),
                        activeSubIdx + 1,
                        "Subtitle"
                    )
                    loadedCues != null -> loadedSubName
                    else -> "Off"
                }
            }
        }
        exoPlayer.addListener(listener)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_DESTROY -> {
                    PlayerHolder.audioDelay.delayMs = 0
                    exoPlayer.release()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.removeListener(listener)
            PlayerHolder.audioDelay.delayMs = 0
            exoPlayer.release()
            OfflineDownloadManager.cleanupPlaybackSessions(context)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isSeeking) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d != C.TIME_UNSET && d > 0L) durationMs = d
            }
            delay(500)
        }
    }

    val seekByDelta: (Long) -> Unit = { deltaMs ->
        val maxDur = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, maxDur)
        exoPlayer.seekTo(target)
        positionMs = target
    }

    val toggleFullscreenMode: () -> Unit = {
        val nextFull = !isFullscreen
        isFullscreenToggle = nextFull
        activity?.requestedOrientation = if (nextFull) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val enterPipMode: () -> Unit = {
        if (activity == null || !context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            Toast.makeText(context, "Picture-in-Picture isn't supported on this device", Toast.LENGTH_SHORT).show()
        } else {
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    val w = if (videoSize.width > 0) videoSize.width else 16
                    val h = if (videoSize.height > 0) videoSize.height else 9
                    val r = (w.toFloat() / h).coerceIn(0.42f, 2.38f)
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational((r * 1000).toInt(), 1000))
                        .build()
                    activity.enterPictureInPictureMode(params)
                } else {
                    @Suppress("DEPRECATION")
                    activity.enterPictureInPictureMode()
                }
            }.onFailure {
                Toast.makeText(context, "Couldn't enter Picture-in-Picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Reusable Video Surface + Overlay Composable (used in both Fullscreen Landscape and Portrait Split mode!)
    val videoPlayerSurfaceBox: @Composable (Modifier) -> Unit = { boxModifier ->
        Box(
            modifier = boxModifier.background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (validation.isValid && playerError == null) {
                val subtitleColor = PlayerHolder.SUBTITLE_COLORS.getOrElse(subtitleColorIndex) {
                    android.graphics.Color.WHITE
                }

                AndroidView(
                    factory = { ctx ->
                        val container = FrameLayout(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                        val playerView = PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.Gravity.CENTER
                            )
                        }
                        container.addView(playerView)

                        var scaling = false
                        var scaleAcc = 1f
                        var scaleDone = false
                        var gestureMode = PlayerGestureMode.NONE
                        var gStartPos = 0L
                        var gTargetPos = 0L
                        var gStartBright = 0.5f
                        var gStartVol = 0.5f

                        val scaleDetector = ScaleGestureDetector(
                            ctx,
                            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                                override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                                    scaling = true
                                    scaleAcc = 1f
                                    scaleDone = false
                                    return true
                                }

                                override fun onScale(d: ScaleGestureDetector): Boolean {
                                    if (isLocked || scaleDone) return true
                                    scaleAcc *= d.scaleFactor
                                    if (scaleAcc > 1.15f) {
                                        aspectMode = 2
                                        gestureFlashText = "Full screen (Crop)"
                                        scaleDone = true
                                    } else if (scaleAcc < 0.87f) {
                                        aspectMode = 0
                                        gestureFlashText = "Fit"
                                        scaleDone = true
                                    }
                                    return true
                                }

                                override fun onScaleEnd(d: ScaleGestureDetector) {
                                    scaling = false
                                }
                            }
                        )

                        val gestureDetector = GestureDetector(
                            ctx,
                            object : GestureDetector.SimpleOnGestureListener() {
                                override fun onDown(e: MotionEvent): Boolean {
                                    gestureMode = PlayerGestureMode.NONE
                                    return true
                                }

                                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                    if (isLocked) {
                                        showUnlockButton = !showUnlockButton
                                    } else {
                                        isControlsVisible = !isControlsVisible
                                    }
                                    return true
                                }

                                override fun onDoubleTap(e: MotionEvent): Boolean {
                                    if (isLocked) return true
                                    val isLeft = e.x < container.width / 2f
                                    seekByDelta(if (isLeft) -10_000L else 10_000L)
                                    gestureFlashText = if (isLeft) "⏪ 10s" else "10s ⏩"
                                    return true
                                }

                                override fun onScroll(
                                    e1: MotionEvent?,
                                    e2: MotionEvent,
                                    dx: Float,
                                    dy: Float
                                ): Boolean {
                                    if (isLocked || e1 == null || container.width == 0 || container.height == 0) return false
                                    val totalX = e2.x - e1.x
                                    val totalY = e2.y - e1.y
                                    if (gestureMode == PlayerGestureMode.NONE) {
                                        if (abs(totalX) < 36 && abs(totalY) < 36) return true
                                        gestureMode = when {
                                            abs(totalX) > abs(totalY) -> PlayerGestureMode.SEEK
                                            e1.x < container.width / 2f -> PlayerGestureMode.BRIGHTNESS
                                            else -> PlayerGestureMode.VOLUME
                                        }
                                        gStartPos = exoPlayer.currentPosition
                                        gStartBright = readCurrentBrightness(activity, ctx)
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        gStartVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol
                                    }

                                    when (gestureMode) {
                                        PlayerGestureMode.SEEK -> {
                                            val dur = durationMs.takeIf { it > 0L } ?: return true
                                            isSeeking = true
                                            gTargetPos = (gStartPos + (totalX / container.width * 120_000f).toLong()).coerceIn(0L, dur)
                                            positionMs = gTargetPos
                                            val diffSec = (gTargetPos - gStartPos) / 1000L
                                            val sign = if (diffSec >= 0) "+" else "−"
                                            gestureFlashText = "${formatDurationMs(gTargetPos)}  ($sign${abs(diffSec)}s)"
                                        }

                                        PlayerGestureMode.BRIGHTNESS -> {
                                            val newBright = (gStartBright - totalY / container.height * 1.2f).coerceIn(0.02f, 1f)
                                            activity?.window?.let { win ->
                                                val lp = win.attributes
                                                lp.screenBrightness = newBright
                                                win.attributes = lp
                                            }
                                            gestureFlashText = "Brightness ${(newBright * 100).roundToInt()}%"
                                        }

                                        PlayerGestureMode.VOLUME -> {
                                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                            val newVol = (gStartVol - totalY / container.height * 1.2f).coerceIn(0f, 1f)
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                (newVol * maxVol).roundToInt(),
                                                0
                                            )
                                            gestureFlashText = "Volume ${(newVol * 100).roundToInt()}%"
                                        }

                                        PlayerGestureMode.NONE -> {}
                                    }
                                    return true
                                }
                            }
                        )

                        container.setOnTouchListener { v, ev ->
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                            scaleDetector.onTouchEvent(ev)
                            if (scaling || ev.pointerCount > 1) {
                                gestureMode = PlayerGestureMode.NONE
                                return@setOnTouchListener true
                            }
                            val handled = gestureDetector.onTouchEvent(ev)
                            if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
                                if (gestureMode == PlayerGestureMode.SEEK) {
                                    exoPlayer.seekTo(gTargetPos)
                                    isSeeking = false
                                }
                                gestureMode = PlayerGestureMode.NONE
                            }
                            handled
                        }

                        container
                    },
                    update = { container ->
                        val pv = container.getChildAt(0) as? PlayerView ?: return@AndroidView
                        pv.player = exoPlayer

                        pv.subtitleView?.apply {
                            setApplyEmbeddedStyles(false)
                            setApplyEmbeddedFontSizes(false)
                            setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleScale)
                            setStyle(
                                CaptionStyleCompat(
                                    subtitleColor,
                                    android.graphics.Color.TRANSPARENT,
                                    android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    null
                                )
                            )
                        }

                        val lp = pv.layoutParams as FrameLayout.LayoutParams
                        lp.gravity = android.view.Gravity.CENTER
                        when (aspectMode) {
                            0, 1, 2 -> {
                                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                                pv.resizeMode = when (aspectMode) {
                                    0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            }
                            else -> {
                                val cw = container.width
                                val ch = container.height
                                if (cw > 0 && ch > 0) {
                                    val ratio = if (aspectMode == 3) 16f / 9f else 4f / 3f
                                    var w = cw
                                    var h = (w / ratio).toInt()
                                    if (h > ch) {
                                        h = ch
                                        w = (h * ratio).toInt()
                                    }
                                    lp.width = w
                                    lp.height = h
                                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                                }
                            }
                        }
                        pv.layoutParams = lp
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isBuffering && playerError == null) {
                CircularProgressIndicator(color = CrimsonRed)
            }

            gestureFlashText?.let { flash ->
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = flash,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }

            if (isLocked && showUnlockButton) {
                IconButton(
                    onClick = {
                        isLocked = false
                        showUnlockButton = false
                        isControlsVisible = true
                        Toast.makeText(context, "Controls unlocked", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(CrimsonRed)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockOpen,
                        contentDescription = "Unlock Controls",
                        tint = Color.White
                    )
                }
            }

            if (playerError != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF10131C))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = playerError ?: "Playback error",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (validation.isValid) {
                            Button(
                                onClick = {
                                    playerError = null
                                    isBuffering = true
                                    resumePositionOnQualitySwitch = exoPlayer.currentPosition.coerceAtLeast(0L)
                                    resolveRetryAttempt += 1
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry", color = Color.White)
                            }
                        }
                        if (nextEpisode != null) {
                            OutlinedButton(onClick = { onSwitchEpisode(nextEpisode) }) {
                                Text("Next Episode", color = TextWhite)
                            }
                        }
                    }
                }
            }

            // Interactive Overlay Controls Layer
            if (isControlsVisible && !isLocked && playerError == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f))
                ) {
                    // Top Gradient Bar (Title + Quality Pill + Subtitles + Fullscreen Toggle + More Menu)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Quick Quality Badge inside Top Bar
                            Surface(
                                color = CrimsonRed.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { activeDialog = "QUALITY" }
                            ) {
                                Text(
                                    text = selectedQualityLabel,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            IconButton(onClick = { activeDialog = "SUBTITLES" }) {
                                Icon(Icons.Filled.Subtitles, contentDescription = "Subtitles", tint = Color.White)
                            }
                            IconButton(onClick = toggleFullscreenMode) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color.White
                                )
                            }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Select Quality ($selectedQualityLabel)") },
                                        leadingIcon = { Icon(Icons.Filled.HighQuality, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            activeDialog = "QUALITY"
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isLooping) "Loop: On" else "Loop: Off") },
                                        leadingIcon = { Icon(Icons.Filled.Loop, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            isLooping = !isLooping
                                            exoPlayer.repeatMode =
                                                if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Load External Subtitle (.srt/.vtt)") },
                                        leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            subtitleFilePicker.launch(arrayOf("*/*"))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Subtitle Style & Delay") },
                                        leadingIcon = { Icon(Icons.Filled.FormatSize, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            activeDialog = "SUBTITLE_STYLE"
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Audio Sync Delay ($audioDelayMs ms)") },
                                        leadingIcon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            activeDialog = "AUDIO_DELAY"
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Video & Stream Info") },
                                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            activeDialog = "INFO"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Center Transport Row: [Prev] [−30] [-10] [Play/Pause] [+10] [+30] [Next]
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (prevEpisode != null) {
                            IconButton(onClick = { onSwitchEpisode(prevEpisode) }) {
                                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                        TextButton(onClick = { seekByDelta(-30_000L) }) {
                            Text("−30", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                        IconButton(onClick = { seekByDelta(-10_000L) }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Back 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        IconButton(
                            onClick = {
                                if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        IconButton(onClick = { seekByDelta(10_000L) }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        TextButton(onClick = { seekByDelta(30_000L) }) {
                            Text("+30", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                        if (nextEpisode != null) {
                            IconButton(onClick = { onSwitchEpisode(nextEpisode) }) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(30.dp))
                            }
                        }
                    }

                    // Bottom Overlay Bar:
                    // In Fullscreen Landscape, renders the full SeekBar + 6 Tool Buttons inside the bottom overlay (just like VideoPlayerOffline activity_player.xml!)
                    if (isFullscreen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                                    )
                                )
                                .padding(horizontal = 22.dp, vertical = 10.dp)
                        ) {
                            val safeDuration = durationMs.coerceAtLeast(1L)
                            val progressFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = formatDurationMs(positionMs),
                                    color = CrimsonLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = progressFraction,
                                    onValueChange = { frac ->
                                        if (durationMs > 0L) {
                                            isSeeking = true
                                            val seekTarget = (frac * durationMs).toLong()
                                            positionMs = seekTarget
                                            exoPlayer.seekTo(seekTarget)
                                            isSeeking = false
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = CrimsonRed,
                                        activeTrackColor = CrimsonRed,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatDurationMs(durationMs),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlayerToolButton(
                                    icon = Icons.Filled.Lock,
                                    label = "Lock",
                                    onClick = {
                                        isLocked = true
                                        isControlsVisible = false
                                        showUnlockButton = true
                                        Toast.makeText(context, "Controls locked", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                PlayerToolButton(
                                    icon = Icons.Filled.HighQuality,
                                    label = selectedQualityLabel,
                                    onClick = { activeDialog = "QUALITY" }
                                )
                                PlayerToolButton(
                                    icon = Icons.Filled.Subtitles,
                                    label = "Subtitle",
                                    onClick = { activeDialog = "SUBTITLES" }
                                )
                                PlayerToolButton(
                                    icon = Icons.Filled.Audiotrack,
                                    label = "Audio",
                                    onClick = { activeDialog = "AUDIO" }
                                )
                                PlayerToolButton(
                                    icon = Icons.Filled.Speed,
                                    label = "${playbackSpeed}x",
                                    onClick = { activeDialog = "SPEED" }
                                )
                                PlayerToolButton(
                                    icon = Icons.Filled.AspectRatio,
                                    label = PlayerHolder.ASPECT_LABELS[aspectMode].substringBefore(" "),
                                    onClick = { activeDialog = "ASPECT" }
                                )
                                PlayerToolButton(
                                    icon = Icons.Filled.PictureInPictureAlt,
                                    label = "PiP",
                                    onClick = enterPipMode
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                                    )
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatDurationMs(positionMs)} / ${formatDurationMs(durationMs)}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${PlayerHolder.ASPECT_LABELS[aspectMode]} • ${playbackSpeed}x",
                                color = CrimsonLight,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // FULLSCREEN LANDSCAPE vs PORTRAIT SPLIT LAYOUT
    // =========================================================================
    if (isFullscreen) {
        // True Fullscreen: 100% of screen width & height, zero scroll overflow, zero status bar gap!
        videoPlayerSurfaceBox(Modifier.fillMaxSize())
    } else {
        // Portrait Mode: 16:9 Player at top + Scrubber + 6 Tool Bar + Quality/Subtitles/Audio Cards + Seasons/Similar Movies below
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            videoPlayerSurfaceBox(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val safeDuration = durationMs.coerceAtLeast(1L)
                val progressFraction = (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

                Slider(
                    value = progressFraction,
                    onValueChange = { frac ->
                        if (durationMs > 0L) {
                            isSeeking = true
                            val seekTarget = (frac * durationMs).toLong()
                            positionMs = seekTarget
                            exoPlayer.seekTo(seekTarget)
                            isSeeking = false
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = CrimsonRed,
                        activeTrackColor = CrimsonRed,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 6 Native VideoPlayerOffline v1.4 Tool Buttons: Lock | Subtitle | Audio | Speed | Aspect | PiP
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerToolButton(
                            icon = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            label = "Lock",
                            onClick = {
                                isLocked = true
                                isControlsVisible = false
                                showUnlockButton = true
                                Toast.makeText(context, "Controls locked", Toast.LENGTH_SHORT).show()
                            }
                        )
                        PlayerToolButton(
                            icon = Icons.Filled.Subtitles,
                            label = "Subtitle",
                            onClick = { activeDialog = "SUBTITLES" }
                        )
                        PlayerToolButton(
                            icon = Icons.Filled.Audiotrack,
                            label = "Audio",
                            onClick = { activeDialog = "AUDIO" }
                        )
                        PlayerToolButton(
                            icon = Icons.Filled.Speed,
                            label = "${playbackSpeed}x",
                            onClick = { activeDialog = "SPEED" }
                        )
                        PlayerToolButton(
                            icon = Icons.Filled.AspectRatio,
                            label = PlayerHolder.ASPECT_LABELS[aspectMode].substringBefore(" "),
                            onClick = { activeDialog = "ASPECT" }
                        )
                        PlayerToolButton(
                            icon = Icons.Filled.PictureInPictureAlt,
                            label = "PiP",
                            onClick = enterPipMode
                        )
                    }
                }

                // Quality | Subtitles | Audio Status Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TrackSelectorCard(
                        title = "Quality",
                        value = selectedQualityLabel,
                        modifier = Modifier.weight(1f),
                        onClick = { activeDialog = "QUALITY" }
                    )
                    TrackSelectorCard(
                        title = "Subtitles",
                        value = selectedSubtitleLabel,
                        modifier = Modifier.weight(1f),
                        onClick = { activeDialog = "SUBTITLES" }
                    )
                    TrackSelectorCard(
                        title = "Audio",
                        value = selectedAudioLabel,
                        modifier = Modifier.weight(1f),
                        onClick = { activeDialog = "AUDIO" }
                    )
                }

                // Direct 3-Quality Switcher Bar (1080p Full HD | 720p HD | 480p SD) — Single-line uniform height!
                if (adminQualityOptions.size > 1 && !activeStreamUrl.startsWith(OfflineDownloadManager.OFFLINE_URI_PREFIX)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        adminQualityOptions.forEach { qOpt ->
                            val isSelected = selectedQualityLabel.equals(qOpt.badge, ignoreCase = true)
                            val pillBg = if (isSelected) CrimsonRed else MaterialTheme.colorScheme.surfaceVariant
                            val pillBorder = if (isSelected) CrimsonRed else MaterialTheme.colorScheme.outline
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(pillBg)
                                    .border(1.dp, pillBorder, RoundedCornerShape(50))
                                    .clickable {
                                        if (!isSelected) {
                                            resumePositionOnQualitySwitch = exoPlayer.currentPosition
                                            selectedQualityLabel = qOpt.badge
                                            activeStreamUrl = qOpt.url
                                            Toast.makeText(context, "Switched to ${qOpt.label}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = qOpt.label,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // CASE A: WEB SERIES PLAYER — Next Episode + Seasons & Episodes Selector
                if (seriesItem != null && seriesItem.seasons.isNotEmpty()) {
                    if (nextEpisode != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Next Episode",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (UrlValidator.validateStreamUrl(nextEpisode.primaryStreamUrl).isValid) {
                                        onSwitchEpisode(nextEpisode)
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PosterImageWithFallback(
                                imageUrl = nextEpisode.thumbnailUrl.ifBlank { seriesItem.backdropUrl },
                                title = nextEpisode.title,
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "S${nextEpisode.seasonNumber} E${nextEpisode.episodeNumber} - ${nextEpisode.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextWhite
                                )
                                if (nextEpisode.overview.isNotBlank()) {
                                    Text(
                                        text = nextEpisode.overview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Seasons & Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        seriesItem.seasons.forEachIndexed { idx, s ->
                            FilterPill(
                                label = s.title.ifBlank { "Season ${s.seasonNumber}" },
                                selected = selectedSeasonIndex == idx,
                                onClick = { selectedSeasonIndex = idx }
                            )
                        }
                    }

                    val currentSeason = seriesItem.seasons.getOrNull(selectedSeasonIndex) ?: seriesItem.seasons.firstOrNull()
                    currentSeason?.episodes.orEmpty().forEach { ep ->
                        val isCurrentEp = ep.seasonNumber == currentSeasonNumber && ep.episodeNumber == currentEpisodeNumber
                        val streamValid = UrlValidator.validateStreamUrl(ep.primaryStreamUrl).isValid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrentEp) CrimsonRed.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (isCurrentEp) CrimsonRed else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = streamValid) { onSwitchEpisode(ep) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PosterImageWithFallback(
                                imageUrl = ep.thumbnailUrl.ifBlank { seriesItem.backdropUrl.ifBlank { seriesItem.posterUrl } },
                                title = ep.title,
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "S${ep.seasonNumber} E${ep.episodeNumber} • ${ep.title}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrentEp) CrimsonRed else TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (ep.overview.isNotBlank()) {
                                    Text(
                                        text = ep.overview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (!streamValid) {
                                    Text(
                                        text = "Stream URL not configured",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // CASE B: MOVIE PLAYER — Overview + Similar Movies Poster Grid Below Player
                if (seriesItem == null) {
                    currentMovieItem?.let { movie ->
                        if (movie.overview.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${movie.title} (${movie.releaseYear})",
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = movie.overview,
                                        color = TextSecondary,
                                        fontSize = 12.5.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (similarMovies.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Similar Movies",
                            color = TextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        val chunkedMovies = remember(similarMovies) { similarMovies.chunked(2) }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            chunkedMovies.forEach { rowPair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    rowPair.forEach { simMovie ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            PosterCard(
                                                item = simMovie,
                                                onClick = { onSelectSimilarMovie(simMovie) },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    if (rowPair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // =========================================================================
    // NATIVE PLAYER DIALOGS
    // =========================================================================

    if (activeDialog == "SUBTITLES") {
        val textGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        val textDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val activeIdx = if (textDisabled) 0 else (textGroups.indexOfFirst { it.isSelected } + 1)

        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text("Subtitles", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val options = listOf("Off") + textGroups.mapIndexed { idx, g ->
                        PlayerHolder.trackLabel(g.getTrackFormat(0), idx + 1, "Subtitle")
                    }
                    options.forEachIndexed { idx, label ->
                        val isSelected = idx == activeIdx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CrimsonRed.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                                    if (idx == 0) {
                                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        selectedSubtitleLabel = "Off"
                                    } else {
                                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setOverrideForType(
                                                TrackSelectionOverride(textGroups[idx - 1].mediaTrackGroup, 0)
                                            )
                                        selectedSubtitleLabel = label
                                    }
                                    exoPlayer.trackSelectionParameters = builder.build()
                                    activeDialog = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, color = TextWhite, fontSize = 14.sp)
                            if (isSelected) {
                                Text(text = "Active", color = CrimsonRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = "SUBTITLE_STYLE" }) {
                    Text("Style & Delay")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            activeDialog = null
                            subtitleFilePicker.launch(arrayOf("*/*"))
                        }
                    ) {
                        Text("Load File…")
                    }
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    if (activeDialog == "SUBTITLE_STYLE") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text("Subtitle Style & Delay", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Size: ${(subtitleScale * 100).roundToInt()}%",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = subtitleScale,
                        onValueChange = { subtitleScale = it.coerceIn(0.5f, 2.0f) },
                        valueRange = 0.5f..2.0f
                    )

                    Text(
                        text = "Subtitle Color",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerHolder.SUBTITLE_COLOR_NAMES.forEachIndexed { idx, name ->
                            FilterPill(
                                label = name,
                                selected = subtitleColorIndex == idx,
                                onClick = { subtitleColorIndex = idx }
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Text(
                        text = "External Subtitle Delay: ${if (subtitleDelayMs > 0) "+" else ""}$subtitleDelayMs ms",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                subtitleDelayMs = (subtitleDelayMs - 100L).coerceAtLeast(-10_000L)
                                if (loadedCues != null) applyLoadedSubtitleToPlayer()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("-100 ms")
                        }
                        OutlinedButton(
                            onClick = {
                                subtitleDelayMs = 0L
                                if (loadedCues != null) applyLoadedSubtitleToPlayer()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset")
                        }
                        OutlinedButton(
                            onClick = {
                                subtitleDelayMs = (subtitleDelayMs + 100L).coerceAtMost(10_000L)
                                if (loadedCues != null) applyLoadedSubtitleToPlayer()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+100 ms")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Done")
                }
            }
        )
    }

    if (activeDialog == "AUDIO") {
        val audioGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.length > 0 }
        val selectedIdx = audioGroups.indexOfFirst { it.isSelected }

        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text("Audio Track", fontWeight = FontWeight.Bold) },
            text = {
                if (audioGroups.isEmpty()) {
                    Text(
                        text = "No audio tracks found yet. The video may have no sound, or it is still loading.",
                        color = TextSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        audioGroups.forEachIndexed { idx, g ->
                            val f = g.getTrackFormat(0)
                            val codec = PlayerHolder.codecName(f.sampleMimeType)
                            val baseLabel = PlayerHolder.trackLabel(f, idx + 1, "Audio")
                            val fullLabel = baseLabel +
                                (if (codec.isNotEmpty()) " • $codec" else "") +
                                (if (!g.isSupported) " ⚠" else "")
                            val isSelected = idx == selectedIdx

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CrimsonRed.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                                            .build()
                                        selectedAudioLabel = fullLabel
                                        activeDialog = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = fullLabel, color = TextWhite, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                                if (isSelected) {
                                    Text(text = "Active", color = CrimsonRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = "AUDIO_DELAY" }) {
                    Text("Audio Delay…")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (activeDialog == "AUDIO_DELAY") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text("Audio Sync Delay", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "$audioDelayMs ms",
                        color = TextWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Delays sound so it plays later than the picture (0 to 2000 ms). Use when audio is ahead of video.",
                        color = TextSecondary,
                        fontSize = 12.5.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                audioDelayMs = (audioDelayMs - 50).coerceAtLeast(0)
                                PlayerHolder.audioDelay.delayMs = audioDelayMs
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("−50 ms")
                        }
                        OutlinedButton(
                            onClick = {
                                audioDelayMs = 0
                                PlayerHolder.audioDelay.delayMs = 0
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset")
                        }
                        OutlinedButton(
                            onClick = {
                                audioDelayMs = (audioDelayMs + 50).coerceAtMost(2000)
                                PlayerHolder.audioDelay.delayMs = audioDelayMs
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+50 ms")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Done")
                }
            }
        )
    }

    if (activeDialog == "SPEED") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlayerHolder.SPEED_LABELS.forEachIndexed { idx, label ->
                        val speedVal = PlayerHolder.SPEEDS[idx]
                        val isSelected = abs(playbackSpeed - speedVal) < 0.01f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CrimsonRed.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    playbackSpeed = speedVal
                                    exoPlayer.setPlaybackSpeed(speedVal)
                                    activeDialog = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, color = TextWhite)
                            if (isSelected) {
                                Text(text = "Active", color = CrimsonRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (activeDialog == "ASPECT") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text("Aspect Ratio", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlayerHolder.ASPECT_LABELS.forEachIndexed { idx, label ->
                        val isSelected = aspectMode == idx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CrimsonRed.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    aspectMode = idx
                                    gestureFlashText = label
                                    activeDialog = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = label, color = TextWhite)
                            if (isSelected) {
                                Text(text = "Active", color = CrimsonRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Quality Dialog — Supports BOTH the 3 Admin Stream Links (1080p, 720p, 480p) AND HLS Adaptive Tracks!
    if (activeDialog == "QUALITY") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text("Select Video Quality", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (adminQualityOptions.isNotEmpty()) {
                        Text(
                            text = "Direct Stream Sources",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        adminQualityOptions.forEach { qOpt ->
                            val isSelected = qOpt.url == activeStreamUrl
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CrimsonRed.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        if (!isSelected) {
                                            resumePositionOnQualitySwitch = exoPlayer.currentPosition
                                            activeStreamUrl = qOpt.url
                                        }
                                        selectedQualityLabel = qOpt.badge
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .clearVideoSizeConstraints()
                                            .build()
                                        activeDialog = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = qOpt.label, color = TextWhite, fontWeight = FontWeight.Medium)
                                if (isSelected) {
                                    Text(text = "Playing", color = CrimsonRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (availableHlsHeights.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text(
                            text = "Adaptive HLS/DASH Bitrate Tracks",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        (listOf("Auto") + availableHlsHeights).forEach { opt ->
                            val isSelected = opt == selectedQualityLabel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CrimsonRed.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable {
                                        selectedQualityLabel = opt
                                        val heightInt = opt.removeSuffix("p").toIntOrNull()
                                        val params = exoPlayer.trackSelectionParameters.buildUpon()
                                        if (heightInt != null) {
                                            params.setMaxVideoSize(Int.MAX_VALUE, heightInt)
                                        } else {
                                            params.clearVideoSizeConstraints()
                                        }
                                        exoPlayer.trackSelectionParameters = params.build()
                                        activeDialog = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = opt, color = TextWhite)
                                if (isSelected) {
                                    Text(text = "Active", color = CrimsonRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (activeDialog == "INFO") {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = { Text("Video & Codec Info", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Title: $title", color = TextWhite, fontSize = 13.sp)
                    Text("Active Quality: $selectedQualityLabel", color = CrimsonLight, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text("Stream Format: ${validation.format.label}", color = TextSecondary, fontSize = 12.5.sp)
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        Text("Resolution: ${videoSize.width} × ${videoSize.height}", color = TextSecondary, fontSize = 12.5.sp)
                    }
                    Text("Audio Track: $selectedAudioLabel", color = TextSecondary, fontSize = 12.5.sp)
                    Text("Subtitle Track: $selectedSubtitleLabel", color = TextSecondary, fontSize = 12.5.sp)
                    Text("Decoder: Hardware + Jellyfin FFmpeg Fallback", color = TextSecondary, fontSize = 12.5.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { activeDialog = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun PlayerToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextWhite,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun TrackSelectorCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = TextWhite,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun readCurrentBrightness(activity: Activity?, context: Context): Float {
    val w = activity?.window?.attributes?.screenBrightness ?: -1f
    if (w >= 0f) return w
    return try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (_: Exception) {
        0.5f
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
