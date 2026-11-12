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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.ui.components.FilterPill
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.components.RatingStarBadge
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite

@Composable
fun WatchlistScreen(
    allItems: List<CatalogItem>,
    watchlistIds: Set<String>,
    onOpenDetails: (CatalogItem) -> Unit,
    onRemoveFromWatchlist: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var filterType by rememberSaveable { mutableStateOf("ALL") }
    var showHeaderMenu by remember { mutableStateOf(false) }

    val savedItems = allItems.filter { it.id in watchlistIds && it.published }.filter {
        when (filterType) {
            "MOVIE" -> !it.isSeries
            "SERIES" -> it.isSeries
            else -> true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header (Screen 5 in 1219435.png)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "My List",
                style = MaterialTheme.typography.headlineSmall,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Box {
                IconButton(onClick = { showHeaderMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = TextWhite)
                }
                DropdownMenu(
                    expanded = showHeaderMenu,
                    onDismissRequest = { showHeaderMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Clear My List") },
                        enabled = watchlistIds.isNotEmpty(),
                        onClick = {
                            showHeaderMenu = false
                            onClearAll()
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(label = "All", selected = filterType == "ALL", onClick = { filterType = "ALL" })
            FilterPill(label = "Movies", selected = filterType == "MOVIE", onClick = { filterType = "MOVIE" })
            FilterPill(label = "Web Series", selected = filterType == "SERIES", onClick = { filterType = "SERIES" })
        }

        if (savedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Your Watchlist is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextWhite
                    )
                    Text(
                        text = "Tap '+ My List' on any movie or web series to save it on this device without needing an account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedItems, key = { it.id }) { item ->
                    WatchlistRow(
                        item = item,
                        onClick = { onOpenDetails(item) },
                        onRemove = { onRemoveFromWatchlist(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchlistRow(
    item: CatalogItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var rowMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterImageWithFallback(
            imageUrl = item.posterUrl.ifBlank { item.backdropUrl },
            title = item.title,
            modifier = Modifier
                .width(68.dp)
                .height(94.dp)
                .clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.releaseYear.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                RatingStarBadge(rating = item.rating)
            }
            Text(
                text = if (item.isSeries) "Web Series • ${item.formattedRuntime}" else "Movie • ${item.formattedRuntime}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Box {
            IconButton(onClick = { rowMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = TextSecondary)
            }
            DropdownMenu(
                expanded = rowMenu,
                onDismissRequest = { rowMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("View Details") },
                    onClick = { rowMenu = false; onClick() }
                )
                DropdownMenuItem(
                    text = { Text("Remove from My List") },
                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                    onClick = { rowMenu = false; onRemove() }
                )
            }
        }
    }
}
