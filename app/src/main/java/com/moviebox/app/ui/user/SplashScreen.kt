package com.moviebox.app.ui.user

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.ui.components.MovieBoxLogo
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.theme.CrimsonDark
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusBlue
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import java.util.Locale

private val defaultSeriesPosters = listOf(
    "https://image.tmdb.org/t/p/w500/7vjaCdMw15FEbXyLQTVa04URsPm.jpg",
    "https://image.tmdb.org/t/p/w500/reEMJA1uzscCbkpeRJeTT2bjqUp.jpg",
    "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
    "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
    "https://image.tmdb.org/t/p/w500/u3bZgnGQ9T01sWNhyveQz0wH0Hl.jpg",
    "https://image.tmdb.org/t/p/w500/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg"
)

/**
 * 3-Step Animated Welcome / Onboarding Screen (replaces the old loading progress screen):
 * - Step 0 (Step 1): All Movies with live TMDB-fetched poster wall + Recently Updated Movie spotlight in the middle
 * - Step 1 (Step 2): Web Series poster showcase + seasons & episodes highlights
 * - Step 2 (Step 3): Instant Full HD Play, Smart Offline Download & 100% No Ads showcase
 */
@Composable
fun SplashScreen(
    indianPosterUrls: List<String> = emptyList(),
    catalogMovies: List<CatalogItem> = emptyList(),
    catalogSeries: List<CatalogItem> = emptyList(),
    onFinished: () -> Unit
) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var enterVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enterVisible = true
    }

    BackHandler(enabled = currentStep > 0) {
        currentStep = (currentStep - 1).coerceAtLeast(0)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "onboardingInfinite")
    val driftOffset by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftY"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    // Combine catalog movie posters + live TMDB-fetched Indian/Global movie posters
    val moviePostersPool = remember(catalogMovies, indianPosterUrls) {
        val fromCatalog = catalogMovies.mapNotNull { it.posterUrl.takeIf(String::isNotBlank) }
        val fallback = listOf(
            "https://image.tmdb.org/t/p/w500/nEufeZlyAOLqO2brrs0yeF1lgXO.jpg",
            "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
            "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg",
            "https://image.tmdb.org/t/p/w500/rCzpDGLbOoPwLjy3OAm5NUPOTrC.jpg",
            "https://image.tmdb.org/t/p/w500/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
            "https://image.tmdb.org/t/p/w500/j73Pq3K3I9bJc0kH3xZ9wM8P2sL.jpg"
        )
        val combined = (fromCatalog + indianPosterUrls + fallback).distinct()
        List(12) { idx -> combined[idx % combined.size] }
    }

    // Most recently updated movie for the Step 1 Center Spotlight
    val recentUpdatedMovie = remember(catalogMovies) {
        catalogMovies
            .filter { it.posterUrl.isNotBlank() || it.backdropUrl.isNotBlank() }
            .maxByOrNull { if (it.updatedAt > 0L) it.updatedAt else it.createdAt }
    }

    // Web Series posters for Step 2
    val seriesPostersPool = remember(catalogSeries, indianPosterUrls) {
        val fromSeries = catalogSeries.mapNotNull { it.posterUrl.takeIf(String::isNotBlank) }
        val combined = (fromSeries + defaultSeriesPosters + indianPosterUrls).distinct()
        List(12) { idx -> combined[idx % combined.size] }
    }
    val featuredSeriesItem = remember(catalogSeries) {
        catalogSeries
            .filter { it.posterUrl.isNotBlank() || it.backdropUrl.isNotBlank() }
            .maxByOrNull { if (it.updatedAt > 0L) it.updatedAt else it.createdAt }
    }

    val bgColor = MaterialTheme.colorScheme.background
    val bgWallPosters = if (currentStep == 1) seriesPostersPool else moviePostersPool

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // 1. Animated TMDB Poster Wall Collage in Background
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .alpha(if (currentStep == 2) 0.22f else 0.36f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (rowIdx in 0 until 4) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (colIdx in 0 until 3) {
                        val posterIdx = (rowIdx * 3 + colIdx) % bgWallPosters.size
                        val url = bgWallPosters[posterIdx]
                        val direction = if (colIdx % 2 == 0) 1f else -1f
                        SplashPosterTile(
                            url = url,
                            visible = enterVisible,
                            order = rowIdx * 3 + colIdx,
                            driftDp = driftOffset * direction,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Deep Vignette & Radial Glow Overlays
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to bgColor.copy(alpha = 0.78f),
                        0.42f to bgColor.copy(alpha = 0.55f),
                        0.76f to bgColor.copy(alpha = 0.94f),
                        1.0f to bgColor
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            when (currentStep) {
                                0 -> CrimsonRed.copy(alpha = 0.26f * glowPulse + 0.05f)
                                1 -> StatusBlue.copy(alpha = 0.24f * glowPulse + 0.05f)
                                else -> StarGold.copy(alpha = 0.22f * glowPulse + 0.05f)
                            },
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )

        // 3. Foreground 3-Step Animated Content Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Brand Logo + Step Counter / Skip Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MovieBoxLogo(iconSize = 30.dp, fontSizeSp = 21)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color(0xFF141824).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                        Text(
                            text = "Step ${currentStep + 1} / 3",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                    if (currentStep < 2) {
                        TextButton(
                            onClick = onFinished,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Skip",
                                color = TextWhite.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Center Animated Step Showcase + Bottom Description
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(
                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> fullWidth / 2 }
                        ) + fadeIn(tween(380))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(380, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> -fullWidth / 2 }
                            ) + fadeOut(tween(300)))
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(420, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> -fullWidth / 2 }
                        ) + fadeIn(tween(380))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(380, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> fullWidth / 2 }
                            ) + fadeOut(tween(300)))
                    }.using(SizeTransform(clip = false))
                },
                label = "onboardingStepTransition",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { step ->
                when (step) {
                    0 -> StepOneMoviesShowcase(
                        recentMovie = recentUpdatedMovie,
                        moviePosters = moviePostersPool,
                        floatY = floatY,
                        glowPulse = glowPulse
                    )
                    1 -> StepTwoWebSeriesShowcase(
                        featuredSeries = featuredSeriesItem,
                        seriesPosters = seriesPostersPool,
                        floatY = floatY,
                        glowPulse = glowPulse
                    )
                    else -> StepThreePlayDownloadNoAdsShowcase(
                        floatY = floatY,
                        glowPulse = glowPulse
                    )
                }
            }

            // Bottom Controls: Animated 3-Step Indicator Dots + Next / Start Watching Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 3-Step Pill Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (idx in 0 until 3) {
                        val isSelected = currentStep == idx
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 32.dp else 9.dp,
                            animationSpec = tween(320, easing = FastOutSlowInEasing),
                            label = "stepDotWidth$idx"
                        )
                        val activeColor = when (currentStep) {
                            0 -> CrimsonRed
                            1 -> StatusBlue
                            else -> StarGold
                        }
                        Box(
                            modifier = Modifier
                                .height(9.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) activeColor
                                    else Color.White.copy(alpha = 0.22f)
                                )
                                .clickable { currentStep = idx }
                        )
                    }
                }

                // Primary Next / Start Watching Action Button
                val buttonColor = when (currentStep) {
                    0 -> CrimsonRed
                    1 -> StatusBlue
                    else -> CrimsonRed
                }
                Button(
                    onClick = {
                        if (currentStep < 2) {
                            currentStep += 1
                        } else {
                            onFinished()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .shadow(16.dp, RoundedCornerShape(16.dp), clip = false)
                ) {
                    Text(
                        text = when (currentStep) {
                            0 -> "Next — Explore Web Series"
                            1 -> "Next — Play, Offline & No Ads"
                            else -> "Start Watching Now"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.5.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (currentStep < 2) Icons.AutoMirrored.Filled.ArrowForward else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * STEP 1: All Movies with TMDB Fetched Posters + Recently Updated Movie Spotlight in the Middle
 */
@Composable
private fun StepOneMoviesShowcase(
    recentMovie: CatalogItem?,
    moviePosters: List<String>,
    floatY: Float,
    glowPulse: Float
) {
    val centerPosterUrl = recentMovie?.posterUrl?.ifBlank { recentMovie.backdropUrl }
        ?: moviePosters.firstOrNull().orEmpty()
    val leftPosterUrl = moviePosters.getOrElse(1) { centerPosterUrl }
    val rightPosterUrl = moviePosters.getOrElse(2) { centerPosterUrl }

    val centerTitle = recentMovie?.title?.ifBlank { "Latest Blockbuster Movie" }
        ?: "Latest TMDB Blockbuster"
    val centerYear = recentMovie?.releaseYear ?: 2026
    val centerRating = recentMovie?.rating?.takeIf { it > 0.0 } ?: 8.7
    val centerGenre = recentMovie?.genres?.firstOrNull() ?: "Action • Sci-Fi"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(4.dp))

        // Middle Hero Visual: 3-Poster Fan with Recently Updated Movie Spotlight in the Center
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left TMDB Movie Poster
            Box(
                modifier = Modifier
                    .offset(x = (-82).dp, y = (14 - floatY * 0.6f).dp)
                    .rotate(-10f)
                    .width(132.dp)
                    .height(194.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                    .alpha(0.82f)
            ) {
                PosterImageWithFallback(
                    imageUrl = leftPosterUrl,
                    title = "TMDB Movie",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Right TMDB Movie Poster
            Box(
                modifier = Modifier
                    .offset(x = 82.dp, y = (14 + floatY * 0.6f).dp)
                    .rotate(10f)
                    .width(132.dp)
                    .height(194.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
                    .alpha(0.82f)
            ) {
                PosterImageWithFallback(
                    imageUrl = rightPosterUrl,
                    title = "TMDB Movie",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Center Spotlight: Recently Updated Movie Card
            Box(
                modifier = Modifier
                    .offset(y = floatY.dp)
                    .width(184.dp)
                    .height(268.dp)
                    .shadow(28.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        width = 1.8.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                CrimsonLight.copy(alpha = 0.65f + 0.3f * glowPulse),
                                StarGold.copy(alpha = 0.55f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                PosterImageWithFallback(
                    imageUrl = centerPosterUrl,
                    title = centerTitle,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.25f),
                                0.52f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.92f)
                            )
                        )
                )

                // Top "RECENT UPDATE" Badge
                Surface(
                    color = CrimsonRed,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MovieFilter,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "RECENT UPDATE",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Bottom Movie Title & Rating inside Center Poster
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = centerTitle,
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", centerRating)} • $centerYear • $centerGenre",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Bottom Description Card about New & Recently Updated Movies
        Surface(
            color = Color(0xFF121622).copy(alpha = 0.90f),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.40f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = CrimsonRed.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "NEW MOVIES • LIVE TMDB POSTERS",
                        color = CrimsonLight,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Latest Blockbuster Movies & Recent Updates",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Watch newly released and recently updated Indian & Hollywood movies fetched live with official TMDB posters, cast details, and 1080p Full HD streams.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * STEP 2: Web Series Poster Showcase & Description
 */
@Composable
private fun StepTwoWebSeriesShowcase(
    featuredSeries: CatalogItem?,
    seriesPosters: List<String>,
    floatY: Float,
    glowPulse: Float
) {
    val centerSeriesPoster = featuredSeries?.posterUrl?.ifBlank { featuredSeries.backdropUrl }
        ?: seriesPosters.firstOrNull().orEmpty()
    val leftSeriesPoster = seriesPosters.getOrElse(1) { centerSeriesPoster }
    val rightSeriesPoster = seriesPosters.getOrElse(2) { centerSeriesPoster }

    val seriesTitle = featuredSeries?.title?.ifBlank { "Trending Original Series" }
        ?: "Original Web Series"
    val seasonsCount = featuredSeries?.seasons?.size?.takeIf { it > 0 } ?: 3
    val ratingVal = featuredSeries?.rating?.takeIf { it > 0.0 } ?: 8.9

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(4.dp))

        // Middle Hero Visual: Web Series Poster Showcase
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left Web Series Poster
            Box(
                modifier = Modifier
                    .offset(x = (-84).dp, y = (16 + floatY * 0.5f).dp)
                    .rotate(-9f)
                    .width(132.dp)
                    .height(194.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, StatusBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .alpha(0.84f)
            ) {
                PosterImageWithFallback(
                    imageUrl = leftSeriesPoster,
                    title = "Web Series",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Right Web Series Poster
            Box(
                modifier = Modifier
                    .offset(x = 84.dp, y = (16 - floatY * 0.5f).dp)
                    .rotate(9f)
                    .width(132.dp)
                    .height(194.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, StatusBlue.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .alpha(0.84f)
            ) {
                PosterImageWithFallback(
                    imageUrl = rightSeriesPoster,
                    title = "Web Series",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Center Web Series Hero Poster Card
            Box(
                modifier = Modifier
                    .offset(y = (-floatY).dp)
                    .width(184.dp)
                    .height(268.dp)
                    .shadow(28.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        width = 1.8.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                StatusBlue.copy(alpha = 0.70f + 0.25f * glowPulse),
                                CrimsonRed.copy(alpha = 0.55f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                PosterImageWithFallback(
                    imageUrl = centerSeriesPoster,
                    title = seriesTitle,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.28f),
                                0.52f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.92f)
                            )
                        )
                )

                // Top "WEB SERIES" Badge
                Surface(
                    color = StatusBlue,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "WEB SERIES • HD",
                            color = Color.Black,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Bottom Series Info inside Center Poster
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = seriesTitle,
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", ratingVal)} • $seasonsCount Seasons • All Episodes",
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Bottom Description Card about Web Series
        Surface(
            color = Color(0xFF121622).copy(alpha = 0.90f),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = StatusBlue.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "WEB SERIES • SEASONS & EPISODES",
                        color = StatusBlue,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Trending Web Series & Complete Seasons",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Binge-watch your favorite original Web Series with full season & episode guides, auto-play next episode, and multi-quality HD streaming.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * STEP 3: Instant Play, Offline Download & 100% No Ads Showcase
 */
@Composable
private fun StepThreePlayDownloadNoAdsShowcase(
    floatY: Float,
    glowPulse: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(4.dp))

        // Middle Visual: 3 Animated Feature Cards (Play + Offline Download + 100% No Ads)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (floatY * 0.4f).dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OnboardingFeatureHighlightCard(
                icon = Icons.Filled.PlayCircleFilled,
                badge = "ULTRA-FAST PLAYER",
                title = "Instant 1080p Full HD Play",
                subtitle = "Switch between 1080p, 720p & 480p qualities with smooth ExoPlayer streaming and custom audio/subtitle controls.",
                accentColor = CrimsonRed,
                glowPulse = glowPulse
            )

            OnboardingFeatureHighlightCard(
                icon = Icons.Filled.DownloadForOffline,
                badge = "WATCH WITHOUT INTERNET",
                title = "Smart Offline Downloads",
                subtitle = "Download movies & web series episodes directly to your phone with pause/resume support and watch offline anytime.",
                accentColor = StatusGreen,
                glowPulse = glowPulse
            )

            OnboardingFeatureHighlightCard(
                icon = Icons.Filled.Shield,
                badge = "ZERO INTERRUPTIONS",
                title = "100% Ad-Free Cinema Experience",
                subtitle = "No pop-ups, no mid-roll video ads, and no distractions — enjoy non-stop cinema entertainment.",
                accentColor = StarGold,
                glowPulse = glowPulse
            )
        }

        // Bottom Summary Card
        Surface(
            color = Color(0xFF121622).copy(alpha = 0.90f),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, StarGold.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "PLAY • OFFLINE DOWNLOAD • 100% NO ADS",
                        color = StarGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = "Play, Download Offline & Enjoy Zero Ads",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Everything is ready — stream online or download for offline playback with zero ads.",
                    color = TextSecondary,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun OnboardingFeatureHighlightCard(
    icon: ImageVector,
    badge: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    glowPulse: Float
) {
    Surface(
        color = Color(0xFF141824).copy(alpha = 0.92f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f + 0.25f * glowPulse)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = badge,
                    color = accentColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = title,
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SplashPosterTile(
    url: String,
    visible: Boolean,
    order: Int,
    driftDp: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 520, delayMillis = order * 60),
        label = "tileAlpha$order"
    )
    val tileScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = tween(durationMillis = 520, delayMillis = order * 60, easing = FastOutSlowInEasing),
        label = "tileScale$order"
    )
    Box(
        modifier = modifier
            .offset(y = driftDp.dp)
            .scale(tileScale)
            .alpha(tileAlpha)
            .aspectRatio(0.68f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF151924))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
