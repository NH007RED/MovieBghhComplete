package com.moviebox.app.ui.user

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.repository.CatalogState
import com.moviebox.app.ui.components.CategoryFilterRow
import com.moviebox.app.ui.components.HeroFeaturedSlider
import com.moviebox.app.ui.components.MovieBoxLogo
import com.moviebox.app.ui.components.PosterCard
import com.moviebox.app.ui.components.SectionHeaderRow
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import com.moviebox.app.util.UrlValidator

@Composable
fun HomeScreen(
    catalogState: CatalogState,
    watchlistIds: Set<String>,
    showAdminIcon: Boolean = false,
    onOpenDetails: (CatalogItem) -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMoviesTab: () -> Unit,
    onOpenSeriesTab: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenNotifications: () -> Unit = {}
) {
    var selectedCategoryId by rememberSaveable { mutableStateOf("ALL") }

    val publishedMovies = UrlValidator.filterCatalog(
        items = catalogState.movies,
        categoryId = selectedCategoryId
    )
    val publishedSeries = UrlValidator.filterCatalog(
        items = catalogState.series,
        categoryId = selectedCategoryId
    )
    val allPublished = (publishedMovies + publishedSeries).sortedByDescending { it.updatedAt }

    val heroSliderItems = (
        allPublished.filter { it.featured } +
            allPublished.filter { it.trending } +
            allPublished
        ).distinctBy { it.id }.take(6)

    val trendingList = allPublished.filter { it.trending }.ifEmpty { allPublished.take(8) }
    val popularMovies = publishedMovies.filter { it.popular }.ifEmpty { publishedMovies.take(8) }
    val popularSeries = publishedSeries.filter { it.popular }.ifEmpty { publishedSeries.take(8) }
    val recentlyAdded = allPublished.sortedByDescending { it.createdAt }.take(8)

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Header Bar (Admin icon ONLY shown on owner's device ID: e029f5624b252c8d)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MovieBoxLogo()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showAdminIcon) {
                    IconButton(onClick = onOpenAdmin) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = "Admin Panel",
                            tint = TextSecondary
                        )
                    }
                }
                val notifCount = (catalogState.notificationsHistory + listOfNotNull(catalogState.latestNotification))
                    .distinctBy { it.id }
                    .size
                Box {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = "Notifications",
                            tint = TextWhite
                        )
                    }
                    if (notifCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .size(17.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (notifCount > 9) "9+" else "$notifCount",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Offline / Cached Indicator Banner
        if (catalogState.isUsingCachedData) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StatusAmber.copy(alpha = 0.15f))
                    .border(1.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                Text(
                    text = "Offline mode: showing locally cached catalog.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusAmber
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar Trigger
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenSearch)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Search movies, web series...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }

            // Category Filter Chips
            if (catalogState.categories.isNotEmpty()) {
                item {
                    CategoryFilterRow(
                        categories = catalogState.categories,
                        selectedCategoryId = selectedCategoryId,
                        onSelect = { selectedCategoryId = it },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (catalogState.isLoading && allPublished.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CrimsonRed)
                    }
                }
            } else if (allPublished.isEmpty()) {
                item {
                    EmptyCatalogCard(
                        showAdminButton = showAdminIcon,
                        onOpenAdmin = onOpenAdmin,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                    )
                }
            } else {
                // Hero Featured Image Slider (YouTube Thumbnail 1280x720 / 16:9 Rounded Corner)
                if (heroSliderItems.isNotEmpty()) {
                    item {
                        HeroFeaturedSlider(
                            items = heroSliderItems,
                            watchlistIds = watchlistIds,
                            onWatchClick = onOpenDetails,
                            onToggleWatchlist = onToggleWatchlist
                        )
                    }
                }

                // Trending Now Row
                if (trendingList.isNotEmpty()) {
                    item {
                        CatalogRowSection(
                            title = "Trending Now",
                            items = trendingList,
                            onItemClick = onOpenDetails,
                            onSeeAll = onOpenMoviesTab
                        )
                    }
                }

                // Popular Movies Row
                if (popularMovies.isNotEmpty()) {
                    item {
                        CatalogRowSection(
                            title = "Popular Movies",
                            items = popularMovies,
                            onItemClick = onOpenDetails,
                            onSeeAll = onOpenMoviesTab
                        )
                    }
                }

                // Popular Web Series Row
                if (popularSeries.isNotEmpty()) {
                    item {
                        CatalogRowSection(
                            title = "Popular Web Series",
                            items = popularSeries,
                            onItemClick = onOpenDetails,
                            onSeeAll = onOpenSeriesTab
                        )
                    }
                }

                // Recently Added Row
                if (recentlyAdded.isNotEmpty()) {
                    item {
                        CatalogRowSection(
                            title = "Recently Added",
                            items = recentlyAdded,
                            onItemClick = onOpenDetails,
                            onSeeAll = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRowSection(
    title: String,
    items: List<CatalogItem>,
    onItemClick: (CatalogItem) -> Unit,
    onSeeAll: (() -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeaderRow(
            title = title,
            onSeeAll = onSeeAll,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                PosterCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    width = 128.dp
                )
            }
        }
    }
}

@Composable
private fun EmptyCatalogCard(
    showAdminButton: Boolean,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.MovieFilter,
            contentDescription = null,
            tint = CrimsonRed,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = "No Published Titles Yet",
            style = MaterialTheme.typography.titleLarge,
            color = TextWhite,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (showAdminButton) {
                "The catalog reads live from Firebase Realtime Database. Sign in to the Admin Panel to add movies/series, import metadata from TMDB, or seed the open-source demo catalog."
            } else {
                "New Movies & Web Series are being updated. Please check back shortly!"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        if (showAdminButton) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onOpenAdmin,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Admin Panel", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
