package com.moviebox.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moviebox.app.data.model.ItemMediaAnalytics
import com.moviebox.app.data.model.MediaActivityLog
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.StatusBlue
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Category
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextMuted
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import java.util.Locale

@Composable
fun MovieBoxLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    fontSizeSp: Int = 20,
    subtitle: String? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(CrimsonRed, Color(0xFFB0060F))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(iconSize * 0.5f)) {
                val path = Path().apply {
                    moveTo(size.width * 0.2f, size.height * 0.12f)
                    lineTo(size.width * 0.88f, size.height * 0.5f)
                    lineTo(size.width * 0.2f, size.height * 0.88f)
                    close()
                }
                drawPath(path, Color.White)
            }
        }
        Column {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextWhite, fontWeight = FontWeight.ExtraBold)) {
                        append("Movie")
                    }
                    withStyle(SpanStyle(color = CrimsonRed, fontWeight = FontWeight.ExtraBold)) {
                        append("Box")
                    }
                },
                fontSize = fontSizeSp.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun RatingStarBadge(
    rating: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = StarGold,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = if (rating > 0.0) String.format(Locale.US, "%.1f", rating) else "NR",
            style = MaterialTheme.typography.labelMedium,
            color = TextWhite,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun VipAccessBadge(
    isVip: Boolean,
    vipPoints: Int = 0,
    modifier: Modifier = Modifier
) {
    if (isVip) {
        Surface(
            color = StarGold.copy(alpha = 0.18f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, StarGold.copy(alpha = 0.65f)),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = "VIP",
                    tint = StarGold,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = if (vipPoints > 0) "VIP • ${vipPoints}P" else "VIP",
                    color = StarGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    } else {
        Surface(
            color = StatusGreen.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.45f)),
            modifier = modifier
        ) {
            Text(
                text = "FREE",
                color = StatusGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
fun PosterImageWithFallback(
    imageUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF1F2637), Color(0xFF0E121B))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = CrimsonRed.copy(alpha = 0.65f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun PosterCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null
) {
    val baseMod = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Column(
        modifier = baseMod
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    color = if (item.isVip) StarGold.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            PosterImageWithFallback(
                imageUrl = item.posterUrl.ifBlank { item.backdropUrl },
                title = item.title,
                modifier = Modifier.fillMaxSize()
            )
            // Top-left media badge
            if (item.isSeries) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CrimsonRed.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SERIES",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            // Top-right VIP or HD badge
            if (item.isVip) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFB300), Color(0xFFFF8F00))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = "VIP",
                            tint = Color.Black,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "VIP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Black
                        )
                    }
                }
            } else if (item.videoUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "HD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusGreen
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (item.isSeries && item.seasons.isNotEmpty()) {
                    "${item.seasons.size} Seasons"
                } else {
                    item.releaseYear.toString()
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            RatingStarBadge(rating = item.rating)
        }
    }
}

/**
 * Resolves a backdrop/poster URL to 1280x720 (w1280) HD YouTube-thumbnail resolution when from TMDB.
 */
private fun toYoutubeThumbnail1280Url(rawUrl: String): String {
    val clean = rawUrl.trim()
    if (clean.isEmpty()) return ""
    return clean
        .replace("/t/p/w500/", "/t/p/w1280/")
        .replace("/t/p/w780/", "/t/p/w1280/")
        .replace("/t/p/original/", "/t/p/w1280/")
}

/**
 * YouTube Thumbnail (1280x720 / 16:9 aspect ratio) Rounded-Corner Hero Image Slider
 * with auto-sliding HorizontalPager and animated pill indicators.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroFeaturedSlider(
    items: List<CatalogItem>,
    watchlistIds: Set<String>,
    onWatchClick: (CatalogItem) -> Unit,
    onToggleWatchlist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { items.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(items.size, isDragged) {
        if (items.size > 1 && !isDragged) {
            if (!pagerState.isScrollInProgress && pagerState.currentPageOffsetFraction.absoluteValue > 0.001f) {
                runCatching {
                    pagerState.animateScrollToPage(
                        page = pagerState.currentPage,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )
                }
            }
            while (true) {
                delay(4200L)
                if (!pagerState.isScrollInProgress && !isDragged) {
                    val nextPage = (pagerState.settledPage + 1) % items.size
                    runCatching {
                        pagerState.animateScrollToPage(
                            page = nextPage,
                            animationSpec = tween(
                                durationMillis = 600,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            ),
            key = { index -> items.getOrNull(index)?.id ?: index },
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val item = items[page]
            val pageOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).absoluteValue.coerceIn(0f, 1f)
            val scale = lerp(0.95f, 1f, 1f - pageOffset)
            val alpha = lerp(0.78f, 1f, 1f - pageOffset)

            HeroFeaturedBanner(
                item = item,
                isInWatchlist = item.id in watchlistIds,
                onWatchClick = { onWatchClick(item) },
                onToggleWatchlist = { onToggleWatchlist(item.id) },
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
            )
        }

        if (items.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.indices.forEach { idx ->
                    val active = pagerState.settledPage == idx
                    val dotWidth by animateDpAsState(
                        targetValue = if (active) 22.dp else 7.dp,
                        animationSpec = tween(durationMillis = 280),
                        label = "hero_dot_width"
                    )
                    Box(
                        modifier = Modifier
                            .height(7.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(if (active) CrimsonRed else TextSecondary.copy(alpha = 0.35f))
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = idx,
                                        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun HeroFeaturedBanner(
    item: CatalogItem,
    isInWatchlist: Boolean,
    onWatchClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumb1280Url = toYoutubeThumbnail1280Url(item.backdropUrl.ifBlank { item.posterUrl })

    // Exact YouTube Thumbnail (1280x720 = 16:9) aspect ratio with rounded corners
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1280f / 720f)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = if (item.isVip) StarGold.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onWatchClick)
    ) {
        PosterImageWithFallback(
            imageUrl = thumb1280Url,
            title = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.38f to Color.Black.copy(alpha = 0.28f),
                        1f to Color(0xFF0B0D12).copy(alpha = 0.92f)
                    )
                )
        )
        // Top-right HD / VIP badge
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isVip) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFFB300), Color(0xFFFF8F00))
                            )
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "👑 VIP",
                        color = Color.Black,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (item.isSeries) "SERIES • HD" else "MOVIE • HD",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title.uppercase(Locale.US),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val genreLabel = item.genres.take(2).joinToString(" • ").ifBlank {
                if (item.isSeries) "Web Series" else "Movie"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${item.releaseYear}  •  ${item.contentAdvisory}  •  $genreLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RatingStarBadge(rating = item.rating)
            }
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onWatchClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (item.videoUrl.isNotBlank()) "Watch Now" else "View Details",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = onToggleWatchlist,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Filled.Check else Icons.Filled.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isInWatchlist) "In My List" else "My List",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryFilterRow(
    categories: List<Category>,
    selectedCategoryId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterPill(
            label = "All",
            selected = selectedCategoryId == "ALL",
            onClick = { onSelect("ALL") }
        )
        categories.filter { it.active }.forEach { cat ->
            FilterPill(
                label = cat.name,
                selected = selectedCategoryId == cat.id,
                onClick = { onSelect(cat.id) }
            )
        }
    }
}

@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) CrimsonRed else MaterialTheme.colorScheme.surfaceVariant
    val border = if (selected) CrimsonRed else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SectionHeaderRow(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )
        if (onSeeAll != null) {
            TextButton(
                onClick = onSeeAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun StatusPillBadge(
    active: Boolean,
    activeLabel: String = "Active",
    inactiveLabel: String = "Draft"
) {
    val color = if (active) StatusGreen else TextMuted
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = if (active) activeLabel else inactiveLabel,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}


// =============================================================================
// ADMIN & USER MEDIA VIEW / DOWNLOAD ANALYTICS BADGES & MODAL DIALOG (v23.0)
// =============================================================================
@Composable
fun MediaAnalyticsBadgeRow(
    analytics: ItemMediaAnalytics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = StatusBlue.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = "Views",
                    tint = StatusBlue,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "${analytics.viewCount} Views",
                    color = StatusBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }

        Surface(
            color = StatusGreen.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Downloads",
                    tint = StatusGreen,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "${analytics.downloadCount} Downloads",
                    color = StatusGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun MediaAnalyticsLogsDialog(
    item: CatalogItem,
    analytics: ItemMediaAnalytics,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("VIEWS") }
    val activeLogs = if (selectedTab == "VIEWS") analytics.viewLogs else analytics.downloadLogs
    val uniqueViewUsers = remember(analytics.viewLogs) {
        analytics.viewLogs.map { it.deviceId.ifBlank { it.uid } }.filter { it.isNotBlank() }.distinct().size
    }
    val uniqueDownloadUsers = remember(analytics.downloadLogs) {
        analytics.downloadLogs.map { it.deviceId.ifBlank { it.uid } }.filter { it.isNotBlank() }.distinct().size
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: Poster + Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        PosterImageWithFallback(
                            imageUrl = item.posterUrl.ifBlank { item.backdropUrl },
                            title = item.title,
                            modifier = Modifier
                                .size(46.dp, 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Surface(
                                color = if (item.isSeries) StatusBlue.copy(alpha = 0.2f) else CrimsonRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(1.dp, if (item.isSeries) StatusBlue else CrimsonLight)
                            ) {
                                Text(
                                    text = if (item.isSeries) "WEB SERIES ANALYTICS" else "MOVIE ANALYTICS",
                                    color = if (item.isSeries) StatusBlue else CrimsonLight,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = item.title,
                                color = TextWhite,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.releaseYear} • View & Download User History",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // 2 KPI Summary Cards: Total Views | Total Downloads
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = StatusBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (selectedTab == "VIEWS") 1.5.dp else 1.dp,
                            color = if (selectedTab == "VIEWS") StatusBlue else StatusBlue.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTab = "VIEWS" }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, tint = StatusBlue, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "${analytics.viewCount}",
                                    color = StatusBlue,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Total Views",
                                color = TextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$uniqueViewUsers unique user(s)",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Surface(
                        color = StatusGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (selectedTab == "DOWNLOADS") 1.5.dp else 1.dp,
                            color = if (selectedTab == "DOWNLOADS") StatusGreen else StatusGreen.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTab = "DOWNLOADS" }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "${analytics.downloadCount}",
                                    color = StatusGreen,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Total Downloads",
                                color = TextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$uniqueDownloadUsers unique user(s)",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Tab Selector Pills: Who Viewed vs Who Downloaded
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterPill(
                        label = "👁 Viewed Users (${analytics.viewLogs.size})",
                        selected = selectedTab == "VIEWS",
                        onClick = { selectedTab = "VIEWS" },
                        modifier = Modifier.weight(1f)
                    )
                    FilterPill(
                        label = "⬇ Downloaded Users (${analytics.downloadLogs.size})",
                        selected = selectedTab == "DOWNLOADS",
                        onClick = { selectedTab = "DOWNLOADS" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // User Activity List (User ID, 5-Digit Share Code, Device Name, Exact Date & Time)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp)
                ) {
                    if (activeLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedTab == "VIEWS") {
                                    "No view logs recorded for '${item.title}' yet."
                                } else {
                                    "No download logs recorded for '${item.title}' yet."
                                },
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(activeLogs, key = { it.id }) { log ->
                                MediaActivityUserLogRow(log = log)
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Analytics", color = TextWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MediaActivityUserLogRow(log: MediaActivityLog) {
    val formattedTime = remember(log.timestamp) {
        if (log.timestamp <= 0L) "Unknown Time"
        else runCatching {
            SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.US).format(Date(log.timestamp))
        }.getOrDefault("Unknown Time")
    }
    val accent = if (log.isDownload) StatusGreen else StatusBlue

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(5.dp),
                        border = BorderStroke(1.dp, accent)
                    ) {
                        Text(
                            text = if (log.isDownload) "DOWNLOADED" else "VIEWED",
                            color = accent,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                    if (log.episodeLabel.isNotBlank()) {
                        Surface(
                            color = StatusAmber.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(5.dp),
                            border = BorderStroke(1.dp, StatusAmber)
                        ) {
                            Text(
                                text = log.episodeLabel,
                                color = StatusAmber,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                    Text(
                        text = log.userName.ifBlank { "User • ${log.shareCode.ifBlank { "Guest" }}" },
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Smartphone, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                    Text(
                        text = "ID: ${log.deviceId.ifBlank { log.uid.ifBlank { "N/A" } }} • Code: ${log.shareCode.ifBlank { "N/A" }}",
                        color = StatusBlue,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = StarGold, modifier = Modifier.size(12.dp))
                Text(
                    text = "Time: $formattedTime",
                    color = StarGold,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
