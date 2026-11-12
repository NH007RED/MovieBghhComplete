package com.moviebox.app.ui.user

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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.moviebox.app.ui.components.CategoryFilterRow
import com.moviebox.app.ui.components.FilterPill
import com.moviebox.app.ui.components.PosterCard
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import com.moviebox.app.util.UrlValidator

@Composable
fun MoviesScreen(
    movies: List<CatalogItem>,
    categories: List<Category>,
    onOpenDetails: (CatalogItem) -> Unit,
    onOpenSearch: () -> Unit
) {
    var selectedCategoryId by rememberSaveable { mutableStateOf("ALL") }
    var selectedGenre by rememberSaveable { mutableStateOf("All") }
    var sortBy by rememberSaveable { mutableStateOf("NEWEST") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var visibleCount by rememberSaveable { mutableIntStateOf(12) }

    val movieCategories = categories.filter {
        it.active && (it.type == CategoryType.MOVIE.name || it.type == CategoryType.BOTH.name)
    }
    val defaultGenres = listOf("All", "Action", "Comedy", "Drama", "Sci-Fi", "Thriller", "Horror")

    val filtered = UrlValidator.filterCatalog(
        items = movies,
        categoryId = selectedCategoryId,
        genre = if (selectedCategoryId == "ALL") selectedGenre else null,
        sortBy = sortBy
    )
    val paged = filtered.take(visibleCount)

    Column(modifier = Modifier.fillMaxSize()) {
        // Header (Screen 2 in 1219435.png)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Movies",
                style = MaterialTheme.typography.headlineSmall,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = "Search Movies", tint = TextWhite)
                }
                Box {
                    IconButton(onClick = { sortMenuExpanded = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = TextWhite)
                    }
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Newest First") },
                            onClick = { sortBy = "NEWEST"; sortMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Top Rated") },
                            onClick = { sortBy = "RATING"; sortMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Title A–Z") },
                            onClick = { sortBy = "TITLE"; sortMenuExpanded = false }
                        )
                    }
                }
            }
        }

        // Category or Genre Pills
        if (movieCategories.isNotEmpty()) {
            CategoryFilterRow(
                categories = movieCategories,
                selectedCategoryId = selectedCategoryId,
                onSelect = { selectedCategoryId = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                defaultGenres.take(4).forEach { g ->
                    FilterPill(
                        label = g,
                        selected = selectedGenre == g,
                        onClick = { selectedGenre = g }
                    )
                }
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
                    text = "No movies available in this category yet.",
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
                items(paged, key = { it.id }) { movie ->
                    PosterCard(
                        item = movie,
                        onClick = { onOpenDetails(movie) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (filtered.size > visibleCount) {
                    item(span = { GridItemSpan(2) }) {
                        OutlinedButton(
                            onClick = { visibleCount += 12 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Load More (${filtered.size - visibleCount} remaining)")
                        }
                    }
                }
            }
        }
    }
}
