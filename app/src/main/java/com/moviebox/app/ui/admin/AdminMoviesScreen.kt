package com.moviebox.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.CastMember
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Category
import com.moviebox.app.data.model.CategoryType
import com.moviebox.app.data.model.MediaType
import com.moviebox.app.data.model.ItemMediaAnalytics
import com.moviebox.app.ui.components.MediaAnalyticsBadgeRow
import com.moviebox.app.ui.components.MediaAnalyticsLogsDialog
import com.moviebox.app.ui.theme.StatusBlue
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.components.StatusPillBadge
import com.moviebox.app.ui.components.VipAccessBadge
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.StatusRed
import com.moviebox.app.ui.theme.TextMuted
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import com.moviebox.app.util.UrlValidator
import java.util.Locale
import java.util.UUID

@Composable
fun AdminMoviesScreen(
    movies: List<CatalogItem>,
    categories: List<Category>,
    mediaAnalyticsMap: Map<String, ItemMediaAnalytics> = emptyMap(),
    defaultVipPoints: Int = 100,
    initialEditingMovie: CatalogItem? = null,
    openEditorInitially: Boolean = false,
    onQuickFetchTmdb: (idOrTitle: String, onResult: (CatalogItem) -> Unit) -> Unit,
    onSaveMovie: (CatalogItem) -> Unit,
    onDeleteMovie: (CatalogItem) -> Unit,
    onSendPushForMovie: (CatalogItem) -> Unit = {},
    onNavigateToTmdbImport: () -> Unit
) {
    var editingMovie by remember(initialEditingMovie) { mutableStateOf(initialEditingMovie) }
    var isEditorOpen by remember(openEditorInitially, initialEditingMovie) {
        mutableStateOf(openEditorInitially || initialEditingMovie != null)
    }
    var deleteConfirmTarget by remember { mutableStateOf<CatalogItem?>(null) }
    var analyticsTargetMovie by remember { mutableStateOf<CatalogItem?>(null) }
    var searchFilter by remember { mutableStateOf("") }

    analyticsTargetMovie?.let { target ->
        MediaAnalyticsLogsDialog(
            item = target,
            analytics = mediaAnalyticsMap[target.id] ?: ItemMediaAnalytics(itemId = target.id),
            onDismiss = { analyticsTargetMovie = null }
        )
    }

    if (isEditorOpen) {
        AdminMovieEditorView(
            initialMovie = editingMovie,
            categories = categories.filter {
                it.type == CategoryType.MOVIE.name || it.type == CategoryType.BOTH.name
            },
            defaultVipPoints = defaultVipPoints,
            onQuickFetchTmdb = onQuickFetchTmdb,
            onSave = { item ->
                onSaveMovie(item)
                isEditorOpen = false
                editingMovie = null
            },
            onCancel = {
                isEditorOpen = false
                editingMovie = null
            }
        )
        return
    }

    val filtered = remember(movies, searchFilter) {
        if (searchFilter.isBlank()) movies
        else movies.filter {
            it.title.contains(searchFilter, ignoreCase = true) ||
                it.genres.any { g -> g.contains(searchFilter, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row — responsive for mobile portrait
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Movie, contentDescription = null, tint = CrimsonRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Movies (${movies.size})",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onNavigateToTmdbImport,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("TMDB", fontSize = 12.sp, maxLines = 1)
                }
                Button(
                    onClick = {
                        editingMovie = null
                        isEditorOpen = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Movie", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        OutlinedTextField(
            value = searchFilter,
            onValueChange = { searchFilter = it },
            placeholder = { Text("Search movies by title or genre...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        )

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No movies found. Tap '+ Add Movie' to create one.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(filtered, key = { _, m -> m.id }) { index, movie ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = TextMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.width(22.dp)
                            )
                            PosterImageWithFallback(
                                imageUrl = movie.posterUrl,
                                title = movie.title,
                                modifier = Modifier
                                    .size(44.dp, 62.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = movie.title,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${movie.releaseYear} • ★ ${String.format(Locale.US, "%.1f", movie.rating)} • ${movie.genres.firstOrNull() ?: "Movie"}",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPillBadge(
                                        active = movie.published,
                                        activeLabel = "Active",
                                        inactiveLabel = "Draft"
                                    )
                                    VipAccessBadge(
                                        isVip = movie.isVip,
                                        vipPoints = if (movie.isVip) movie.effectiveVipPoints(defaultVipPoints) else 0
                                    )
                                }
                                MediaAnalyticsBadgeRow(
                                    analytics = mediaAnalyticsMap[movie.id] ?: ItemMediaAnalytics(itemId = movie.id),
                                    onClick = { analyticsTargetMovie = movie }
                                )
                            }
                            var rowMenuOpen by remember { mutableStateOf(false) }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        editingMovie = movie
                                        isEditorOpen = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TextWhite, modifier = Modifier.size(17.dp))
                                }
                                Box {
                                    IconButton(
                                        onClick = { rowMenuOpen = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = TextWhite, modifier = Modifier.size(19.dp))
                                    }
                                    DropdownMenu(
                                        expanded = rowMenuOpen,
                                        onDismissRequest = { rowMenuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("View & Download Logs") },
                                            leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, tint = StatusBlue) },
                                            onClick = {
                                                rowMenuOpen = false
                                                analyticsTargetMovie = movie
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (movie.isVip) "Switch to FREE" else "Set as VIP (${defaultVipPoints} Pts)") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.WorkspacePremium,
                                                    contentDescription = null,
                                                    tint = StarGold
                                                )
                                            },
                                            onClick = {
                                                rowMenuOpen = false
                                                val nextVip = !movie.isVip
                                                onSaveMovie(
                                                    movie.copy(
                                                        isVip = nextVip,
                                                        vipPointsRequired = if (nextVip && movie.vipPointsRequired <= 0) defaultVipPoints else movie.vipPointsRequired
                                                    )
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Send Push Notification") },
                                            leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = CrimsonRed) },
                                            onClick = {
                                                rowMenuOpen = false
                                                onSendPushForMovie(movie)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Edit Movie") },
                                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                            onClick = {
                                                rowMenuOpen = false
                                                editingMovie = movie
                                                isEditorOpen = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Movie") },
                                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = StatusRed) },
                                            onClick = {
                                                rowMenuOpen = false
                                                deleteConfirmTarget = movie
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    deleteConfirmTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Delete Movie", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete '${target.title}' from the catalog?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMovie(target)
                        deleteConfirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminMovieEditorView(
    initialMovie: CatalogItem?,
    categories: List<Category>,
    defaultVipPoints: Int,
    onQuickFetchTmdb: (idOrTitle: String, onResult: (CatalogItem) -> Unit) -> Unit,
    onSave: (CatalogItem) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }
    var title by remember { mutableStateOf(initialMovie?.title ?: "") }
    var selectedCategoryName by remember {
        mutableStateOf(
            initialMovie?.genres?.firstOrNull()
                ?: categories.firstOrNull()?.name
                ?: "Drama"
        )
    }
    var releaseYear by remember { mutableStateOf((initialMovie?.releaseYear ?: 2024).toString()) }
    var rating by remember { mutableStateOf((initialMovie?.rating ?: 8.0).toString()) }
    var runtimeMinutes by remember { mutableStateOf((initialMovie?.runtimeMinutes ?: 120).toString()) }
    var contentAdvisory by remember { mutableStateOf(initialMovie?.contentAdvisory ?: "PG-13") }
    var language by remember { mutableStateOf(initialMovie?.language ?: "en") }
    var overview by remember { mutableStateOf(initialMovie?.overview ?: "") }
    var posterUrl by remember { mutableStateOf(initialMovie?.posterUrl ?: "") }
    var backdropUrl by remember { mutableStateOf(initialMovie?.backdropUrl ?: "") }
    var trailerUrl by remember { mutableStateOf(initialMovie?.trailerUrl ?: "") }
    var videoUrl1080p by remember {
        mutableStateOf(initialMovie?.videoUrl1080p?.ifBlank { initialMovie.videoUrl } ?: "")
    }
    var videoUrl720p by remember { mutableStateOf(initialMovie?.videoUrl720p ?: "") }
    var videoUrl480p by remember { mutableStateOf(initialMovie?.videoUrl480p ?: "") }
    var preservedCastList by remember {
        mutableStateOf<List<CastMember>>(initialMovie?.cast ?: emptyList())
    }
    var castText by remember {
        mutableStateOf(
            initialMovie?.cast?.joinToString(", ") { "${it.name}:${it.character}" } ?: ""
        )
    }
    var isVip by remember { mutableStateOf(initialMovie?.isVip ?: false) }
    var vipPointsInput by remember {
        mutableStateOf((initialMovie?.vipPointsRequired?.takeIf { it > 0 } ?: defaultVipPoints).toString())
    }
    var published by remember { mutableStateOf(initialMovie?.published ?: true) }
    var featured by remember { mutableStateOf(initialMovie?.featured ?: false) }
    var trending by remember { mutableStateOf(initialMovie?.trending ?: true) }
    var popular by remember { mutableStateOf(initialMovie?.popular ?: true) }
    var tmdbId by remember { mutableStateOf(initialMovie?.tmdbId) }

    var quickTmdbIdInput by remember { mutableStateOf(initialMovie?.tmdbId?.toString() ?: "") }
    var quickTmdbTitleInput by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var showOverwriteConfirm by remember { mutableStateOf<CatalogItem?>(null) }

    val applyImportedMovie: (CatalogItem) -> Unit = { imported ->
        title = imported.title
        releaseYear = imported.releaseYear.toString()
        rating = String.format(Locale.US, "%.1f", imported.rating)
        runtimeMinutes = imported.runtimeMinutes.toString()
        overview = imported.overview
        posterUrl = imported.posterUrl
        backdropUrl = imported.backdropUrl
        trailerUrl = imported.trailerUrl
        if (imported.genres.isNotEmpty()) {
            selectedCategoryName = imported.genres.first()
        }
        preservedCastList = imported.cast
        castText = imported.cast.joinToString(", ") { "${it.name}:${it.character}" }
        tmdbId = imported.tmdbId
    }

    val streamValidation1080 = remember(videoUrl1080p) { UrlValidator.validateStreamUrl(videoUrl1080p) }
    val streamValidation720 = remember(videoUrl720p) { UrlValidator.validateStreamUrl(videoUrl720p) }
    val streamValidation480 = remember(videoUrl480p) { UrlValidator.validateStreamUrl(videoUrl480p) }
    val trailerValid = remember(trailerUrl) { trailerUrl.isBlank() || UrlValidator.validateTrailerUrl(trailerUrl) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isTabletWide = screenWidthDp >= 720

    val quickImportCard: @Composable (Modifier) -> Unit = { cardModifier ->
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = cardModifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quick Import from TMDB",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                // Search by Movie ID row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickTmdbIdInput,
                        onValueChange = { quickTmdbIdInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Search by Movie ID", maxLines = 1) },
                        placeholder = { Text("e.g. 872585") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onQuickFetchTmdb(quickTmdbIdInput) { fetched ->
                                if (title.isBlank() && overview.isBlank()) {
                                    applyImportedMovie(fetched)
                                } else {
                                    showOverwriteConfirm = fetched
                                }
                            }
                        },
                        enabled = quickTmdbIdInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Import", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                // Search by Title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickTmdbTitleInput,
                        onValueChange = { quickTmdbTitleInput = it },
                        label = { Text("Or Search by Title", maxLines = 1) },
                        placeholder = { Text("e.g. Oppenheimer") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onQuickFetchTmdb(quickTmdbTitleInput) { fetched ->
                                if (title.isBlank() && overview.isBlank()) {
                                    applyImportedMovie(fetched)
                                } else {
                                    showOverwriteConfirm = fetched
                                }
                            }
                        },
                        enabled = quickTmdbTitleInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Import", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Text(
                    text = "TMDB auto-fills metadata, posters, cast, and trailer link. Enter your full video stream URL below.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }

    val movieFormCard: @Composable (Modifier) -> Unit = { cardModifier ->
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = cardModifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // =========================================================================
                // VIP / FREE ACCESS TYPE & POINTS SELECTOR CARD
                // =========================================================================
                Surface(
                    color = if (isVip) StarGold.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isVip) StarGold.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = if (isVip) StarGold else StatusGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Movie Access Mode (VIP / Free)",
                                        color = TextWhite,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = if (isVip) "VIP Movie — Requires Points or Share Code to play"
                                        else "Free Movie — Playable by all users immediately",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // FREE Option
                            Surface(
                                color = if (!isVip) StatusGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    width = if (!isVip) 1.5.dp else 1.dp,
                                    color = if (!isVip) StatusGreen else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { isVip = false }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (!isVip) Icons.Filled.CheckCircle else Icons.Filled.LockOpen,
                                        contentDescription = null,
                                        tint = if (!isVip) StatusGreen else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "FREE",
                                        color = if (!isVip) StatusGreen else TextWhite,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // VIP Option
                            Surface(
                                color = if (isVip) StarGold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    width = if (isVip) 1.5.dp else 1.dp,
                                    color = if (isVip) StarGold else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { isVip = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WorkspacePremium,
                                        contentDescription = null,
                                        tint = if (isVip) StarGold else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "VIP ONLY",
                                        color = if (isVip) StarGold else TextWhite,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        if (isVip) {
                            OutlinedTextField(
                                value = vipPointsInput,
                                onValueChange = { vipPointsInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("VIP Points Required to Access Movie", maxLines = 1) },
                                placeholder = { Text("e.g. $defaultVipPoints") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(25, 50, 100, 200).forEach { preset ->
                                    OutlinedButton(
                                        onClick = { vipPointsInput = preset.toString() },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("$preset Pts", fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Movie Title", maxLines = 1) },
                    placeholder = { Text("e.g. Oppenheimer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 2-column row 1: Category/Genre + Release Year
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = selectedCategoryName,
                        onValueChange = { selectedCategoryName = it },
                        label = { Text("Category / Genre", maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = releaseYear,
                        onValueChange = { releaseYear = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Release Year", maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                // 2-column row 2: Rating + Runtime (min)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = rating,
                        onValueChange = { rating = it },
                        label = { Text("Rating (0–10)", maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = runtimeMinutes,
                        onValueChange = { runtimeMinutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Runtime (min)", maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 2-column row 3: Advisory + Language
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = contentAdvisory,
                        onValueChange = { contentAdvisory = it },
                        label = { Text("Advisory (PG-13)", maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text("Language", maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = overview,
                    onValueChange = { overview = it },
                    label = { Text("Description / Storyline", maxLines = 1) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = posterUrl,
                    onValueChange = { posterUrl = it },
                    label = { Text("Poster Image URL (HTTPS)", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = backdropUrl,
                    onValueChange = { backdropUrl = it },
                    label = { Text("Backdrop Image URL (HTTPS)", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Poster + Backdrop Thumbnails Preview Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PosterImageWithFallback(
                            imageUrl = posterUrl,
                            title = title.ifBlank { "Poster" },
                            modifier = Modifier
                                .size(68.dp, 94.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Poster Preview", color = TextSecondary, fontSize = 11.sp)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        PosterImageWithFallback(
                            imageUrl = backdropUrl.ifBlank { posterUrl },
                            title = title.ifBlank { "Backdrop" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(94.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Backdrop Preview", color = TextSecondary, fontSize = 11.sp)
                    }
                }

                // Full-width Trailer URL
                OutlinedTextField(
                    value = trailerUrl,
                    onValueChange = { trailerUrl = it },
                    label = { Text("Trailer Link (YouTube or HTTPS)", maxLines = 1) },
                    placeholder = { Text("https://www.youtube.com/watch?v=...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!trailerValid) {
                    Text("Invalid HTTP/HTTPS trailer URL", color = StatusRed, fontSize = 11.sp)
                }

                // 3 Video Stream Quality Links (1080p Full HD, 720p HD, 480p SD)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Video Stream Quality Links (3 Qualities)",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        OutlinedTextField(
                            value = videoUrl1080p,
                            onValueChange = { videoUrl1080p = it },
                            label = { Text("1. 1080p Full HD Stream Link (.mp4 / .m3u8)", maxLines = 1) },
                            placeholder = { Text("https://.../movie_1080p.mp4") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (videoUrl1080p.isNotBlank()) {
                            Text(
                                text = if (streamValidation1080.isValid) "✓ 1080p Verified (${streamValidation1080.format.label})"
                                else streamValidation1080.errorMessage ?: "Invalid 1080p URL",
                                color = if (streamValidation1080.isValid) StatusGreen else StatusRed,
                                fontSize = 11.sp
                            )
                        }

                        OutlinedTextField(
                            value = videoUrl720p,
                            onValueChange = { videoUrl720p = it },
                            label = { Text("2. 720p HD Stream Link (.mp4 / .m3u8)", maxLines = 1) },
                            placeholder = { Text("https://.../movie_720p.mp4") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (videoUrl720p.isNotBlank()) {
                            Text(
                                text = if (streamValidation720.isValid) "✓ 720p Verified (${streamValidation720.format.label})"
                                else streamValidation720.errorMessage ?: "Invalid 720p URL",
                                color = if (streamValidation720.isValid) StatusGreen else StatusRed,
                                fontSize = 11.sp
                            )
                        }

                        OutlinedTextField(
                            value = videoUrl480p,
                            onValueChange = { videoUrl480p = it },
                            label = { Text("3. 480p SD Stream Link (.mp4 / .m3u8)", maxLines = 1) },
                            placeholder = { Text("https://.../movie_480p.mp4") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (videoUrl480p.isNotBlank()) {
                            Text(
                                text = if (streamValidation480.isValid) "✓ 480p Verified (${streamValidation480.format.label})"
                                else streamValidation480.errorMessage ?: "Invalid 480p URL",
                                color = if (streamValidation480.isValid) StatusGreen else StatusRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = castText,
                    onValueChange = { castText = it },
                    label = { Text("Cast Members (Name:Character, Name:Character)", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Clean 2x2 Grid for Checkboxes
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(checked = published, onCheckedChange = { published = it })
                            Text("Published", color = TextWhite, fontSize = 13.sp, maxLines = 1)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(checked = featured, onCheckedChange = { featured = it })
                            Text("Hero Featured", color = TextWhite, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(checked = trending, onCheckedChange = { trending = it })
                            Text("Trending", color = TextWhite, fontSize = 13.sp, maxLines = 1)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(checked = popular, onCheckedChange = { popular = it })
                            Text("Popular", color = TextWhite, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }

                validationMessage?.let { msg ->
                    Text(text = msg, color = StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                // Bottom Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Cancel", color = TextSecondary, maxLines = 1)
                    }
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                validationMessage = "Movie title is required."
                                return@Button
                            }
                            if (videoUrl1080p.isNotBlank() && !streamValidation1080.isValid) {
                                validationMessage = "1080p Link: ${streamValidation1080.errorMessage}"
                                return@Button
                            }
                            if (videoUrl720p.isNotBlank() && !streamValidation720.isValid) {
                                validationMessage = "720p Link: ${streamValidation720.errorMessage}"
                                return@Button
                            }
                            if (videoUrl480p.isNotBlank() && !streamValidation480.isValid) {
                                validationMessage = "480p Link: ${streamValidation480.errorMessage}"
                                return@Button
                            }
                            val primaryVideo = videoUrl1080p.trim().ifBlank {
                                videoUrl720p.trim().ifBlank { videoUrl480p.trim() }
                            }
                            if (!trailerValid) {
                                validationMessage = "Trailer URL must be a valid HTTP/HTTPS link."
                                return@Button
                            }
                            val matchedCatId = categories.firstOrNull {
                                it.name.equals(selectedCategoryName.trim(), ignoreCase = true)
                            }?.id ?: "cat_drama"

                            val parsedCast = castText.split(",").mapNotNull { entry ->
                                val parts = entry.trim().split(":")
                                val actorName = parts.getOrNull(0)?.trim().orEmpty()
                                if (actorName.isBlank()) null
                                else {
                                    val existingMember = preservedCastList.firstOrNull {
                                        it.name.equals(actorName, ignoreCase = true)
                                    } ?: initialMovie?.cast?.firstOrNull {
                                        it.name.equals(actorName, ignoreCase = true)
                                    }
                                    CastMember(
                                        id = existingMember?.id ?: actorName.lowercase(Locale.US).replace(" ", "_"),
                                        name = actorName,
                                        character = parts.getOrNull(1)?.trim().orEmpty().ifBlank { existingMember?.character.orEmpty() },
                                        profileUrl = existingMember?.profileUrl.orEmpty()
                                    )
                                }
                            }

                            val resolvedVipPoints = if (isVip) {
                                (vipPointsInput.toIntOrNull() ?: defaultVipPoints).coerceAtLeast(1)
                            } else {
                                0
                            }

                            val baseMovie = initialMovie ?: CatalogItem()
                            val movieToSave = baseMovie.copy(
                                id = initialMovie?.id ?: "movie_${UUID.randomUUID().toString().take(8)}",
                                tmdbId = tmdbId,
                                title = title.trim(),
                                normalizedTitle = UrlValidator.normalizeTitle(title),
                                overview = overview.trim(),
                                mediaType = MediaType.MOVIE.name,
                                releaseYear = releaseYear.toIntOrNull() ?: 2024,
                                rating = (rating.toDoubleOrNull() ?: 8.0).coerceIn(0.0, 10.0),
                                runtimeMinutes = runtimeMinutes.toIntOrNull() ?: 120,
                                contentAdvisory = contentAdvisory.trim().ifBlank { "PG-13" },
                                language = language.trim().ifBlank { "en" },
                                posterUrl = posterUrl.trim(),
                                backdropUrl = backdropUrl.trim().ifBlank { posterUrl.trim() },
                                genres = listOf(selectedCategoryName.trim().ifBlank { "Drama" }),
                                categoryIds = listOf(matchedCatId),
                                cast = parsedCast.ifEmpty { initialMovie?.cast ?: emptyList() },
                                trailerUrl = trailerUrl.trim(),
                                videoUrl = primaryVideo,
                                videoUrl1080p = videoUrl1080p.trim(),
                                videoUrl720p = videoUrl720p.trim(),
                                videoUrl480p = videoUrl480p.trim(),
                                isVip = isVip,
                                vipPointsRequired = resolvedVipPoints,
                                featured = featured,
                                trending = trending,
                                popular = popular,
                                published = published,
                                sourceAttribution = if (tmdbId != null) "TMDB" else (initialMovie?.sourceAttribution ?: "MANUAL"),
                                createdAt = initialMovie?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(movieToSave)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Movie", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (initialMovie == null) "Add / Edit Movie" else "Edit Movie — ${initialMovie.title}",
                color = TextWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isTabletWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                movieFormCard(Modifier.weight(1.5f))
                quickImportCard(Modifier.weight(1f))
            }
        } else {
            quickImportCard(Modifier.fillMaxWidth())
            movieFormCard(Modifier.fillMaxWidth())
        }
    }

    showOverwriteConfirm?.let { imported ->
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = null },
            title = { Text("Overwrite Current Fields?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Apply imported TMDB metadata for '${imported.title}' (${imported.releaseYear}) to the editor form? Your manual stream URL will be preserved.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        applyImportedMovie(imported)
                        showOverwriteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("Apply Metadata", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = null }) {
                    Text("Keep Manual Edits")
                }
            }
        )
    }
}
