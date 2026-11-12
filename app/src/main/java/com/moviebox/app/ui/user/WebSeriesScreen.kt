package com.moviebox.app.ui.user

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Category
import com.moviebox.app.data.model.CategoryType
import com.moviebox.app.ui.components.FilterPill
import com.moviebox.app.ui.components.PosterCard
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite

@Composable
fun WebSeriesScreen(
    seriesList: List<CatalogItem>,
    categories: List<Category>,
    onOpenDetails: (CatalogItem) -> Unit,
    onOpenSearch: () -> Unit
) {
    var selectedFilter by rememberSaveable { mutableStateOf("All") }

    val seriesCategories = categories.filter {
        it.active && (it.type == CategoryType.SERIES.name || it.type == CategoryType.BOTH.name)
    }

    val published = seriesList.filter { it.published }
    val filtered = when (selectedFilter) {
        "All" -> published.sortedByDescending { it.updatedAt }
        "Popular" -> published.filter { it.popular }.ifEmpty { published.sortedByDescending { it.rating } }
        "New" -> published.sortedByDescending { it.releaseYear }
        "Top Rated" -> published.sortedByDescending { it.rating }
        else -> {
            val matchCat = seriesCategories.firstOrNull { it.name == selectedFilter }
            if (matchCat != null) published.filter { matchCat.id in it.categoryIds }
            else published
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header (Screen 3 in 1219435.png)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Web Series",
                style = MaterialTheme.typography.headlineSmall,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search Web Series", tint = TextWhite)
            }
        }

        // Filter Pills (All | Popular | New | Top Rated + custom series categories)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Popular", "New", "Top Rated").forEach { tab ->
                FilterPill(
                    label = tab,
                    selected = selectedFilter == tab,
                    onClick = { selectedFilter = tab }
                )
            }
            seriesCategories.forEach { cat ->
                FilterPill(
                    label = cat.name,
                    selected = selectedFilter == cat.name,
                    onClick = { selectedFilter = cat.name }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No web series published in this filter yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filtered, key = { it.id }) { series ->
                    PosterCard(
                        item = series,
                        onClick = { onOpenDetails(series) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
