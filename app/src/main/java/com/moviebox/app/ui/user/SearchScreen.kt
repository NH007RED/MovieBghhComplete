package com.moviebox.app.ui.user

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.MediaType
import com.moviebox.app.ui.components.PosterCard
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.StatusBlue
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import com.moviebox.app.util.UrlValidator
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    allPublishedItems: List<CatalogItem>,
    tmdbSearchResults: List<CatalogItem> = emptyList(),
    isSearchingTmdb: Boolean = false,
    notifiedRequestKeys: Set<String> = emptySet(),
    onSearchQueryChanged: (String) -> Unit = {},
    onNotifyAdmin: (CatalogItem) -> Unit = {},
    onBack: () -> Unit,
    onOpenDetails: (CatalogItem) -> Unit
) {
    BackHandler {
        onBack()
    }

    var searchInputText by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(searchInputText) {
        delay(280)
        debouncedQuery = searchInputText.trim()
        onSearchQueryChanged(debouncedQuery)
    }

    val catalogResults = remember(allPublishedItems, debouncedQuery) {
        UrlValidator.filterCatalog(
            items = allPublishedItems,
            query = debouncedQuery
        )
    }

    // Filter TMDB search results to show Movies & Web Series NOT yet available in the app catalog
    val unavailableTmdbResults = remember(tmdbSearchResults, allPublishedItems) {
        tmdbSearchResults.filter { tmdbItem ->
            allPublishedItems.none { pub ->
                (pub.tmdbId != null && pub.tmdbId == tmdbItem.tmdbId && pub.mediaType == tmdbItem.mediaType) ||
                    (pub.title.equals(tmdbItem.title, ignoreCase = true) && pub.mediaType == tmdbItem.mediaType)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Slim Single-Line Top Search Bar Row (Fixed so placeholder never wraps onto 2 lines)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextWhite
                )
            }
            OutlinedTextField(
                value = searchInputText,
                onValueChange = { searchInputText = it },
                placeholder = {
                    Text(
                        text = "Search movies, web series...",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                textStyle = TextStyle(
                    color = TextWhite,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                },
                trailingIcon = {
                    if (searchInputText.isNotEmpty()) {
                        IconButton(
                            onClick = { searchInputText = "" },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                maxLines = 1,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonRed,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            )
        }

        if (debouncedQuery.isBlank() && catalogResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Search any Movie or Web Series. If a title is not available in the app, tap 'Notify to Admin' to request it!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. AVAILABLE IN APP SECTION
                if (catalogResults.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayCircle,
                                    contentDescription = null,
                                    tint = StatusGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (debouncedQuery.isBlank()) "Available in App (${catalogResults.size})"
                                    else "Available to Play & Download (${catalogResults.size})",
                                    color = TextWhite,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }

                    items(
                        items = catalogResults,
                        key = { "cat_${it.id}" }
                    ) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onOpenDetails(item) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 2. UNAVAILABLE IN APP -> NOTIFY TO ADMIN SECTION (When user searches)
                if (debouncedQuery.isNotBlank()) {
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (catalogResults.isNotEmpty()) 8.dp else 0.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                    Icon(
                                        imageVector = Icons.Filled.NotificationsActive,
                                        contentDescription = null,
                                        tint = StatusAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Not Available in App • Notify to Admin",
                                        color = StatusAmber,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSearchingTmdb) {
                                    CircularProgressIndicator(
                                        color = StatusAmber,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Custom direct request bar for exact typed search query
                            val customReqItem = remember(debouncedQuery) {
                                CatalogItem(
                                    id = "custom_req_${debouncedQuery.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                                    title = debouncedQuery,
                                    mediaType = MediaType.MOVIE.name,
                                    releaseYear = 2026,
                                    status = "Requested"
                                )
                            }
                            val customReqKey = "${MediaType.MOVIE.name}_${debouncedQuery.lowercase()}"
                            val isCustomSent = customReqKey in notifiedRequestKeys

                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Request \"$debouncedQuery\"",
                                            color = TextWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Not in app? Send request directly to Admin",
                                            color = TextSecondary,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = { onNotifyAdmin(customReqItem) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCustomSent) StatusGreen else CrimsonRed
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCustomSent) Icons.Filled.CheckCircle else Icons.Filled.NotificationsActive,
                                            contentDescription = null,
                                            tint = if (isCustomSent) Color.Black else Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isCustomSent) "Notified" else "Notify to Admin",
                                            color = if (isCustomSent) Color.Black else Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = unavailableTmdbResults,
                        key = { "tmdb_unavail_${it.mediaType}_${it.tmdbId ?: it.id}" }
                    ) { item ->
                        val reqKey = "${item.mediaType}_${item.tmdbId ?: item.title.lowercase()}"
                        val isNotified = reqKey in notifiedRequestKeys

                        UnavailableSearchPosterCard(
                            item = item,
                            isNotified = isNotified,
                            onNotifyAdmin = { onNotifyAdmin(item) },
                            onOpenPreviewDetails = { onOpenDetails(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnavailableSearchPosterCard(
    item: CatalogItem,
    isNotified: Boolean,
    onNotifyAdmin: () -> Unit,
    onOpenPreviewDetails: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isNotified) StatusGreen.copy(alpha = 0.6f) else StatusAmber.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clickable(onClick = onOpenPreviewDetails)
        ) {
            PosterImageWithFallback(
                imageUrl = item.posterUrl.ifBlank { item.backdropUrl },
                title = item.title,
                modifier = Modifier.fillMaxSize()
            )

            // Top-Left Type Badge
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = if (item.isSeries) "WEB SERIES" else "MOVIE",
                    color = if (item.isSeries) StatusBlue else CrimsonLight,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Top-Right Rating Badge
            if (item.rating > 0.0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = String.format("%.1f", item.rating),
                            color = TextWhite,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Banner: NOT AVAILABLE IN APP
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "NOT AVAILABLE IN APP",
                    color = StatusAmber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }

        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.title,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${item.releaseYear} • ${if (item.isSeries) "Web Series" else "Movie"}",
                color = TextSecondary,
                fontSize = 10.5.sp,
                maxLines = 1
            )

            Button(
                onClick = onNotifyAdmin,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isNotified) StatusGreen else CrimsonRed
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                Icon(
                    imageVector = if (isNotified) Icons.Filled.CheckCircle else Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    tint = if (isNotified) Color.Black else Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (isNotified) "✓ Sent to Admin" else "Notify to Admin",
                    color = if (isNotified) Color.Black else Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
