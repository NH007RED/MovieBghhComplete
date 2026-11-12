package com.moviebox.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Category
import com.moviebox.app.data.model.TmdbImportSelection
import com.moviebox.app.ui.TmdbSearchUiState
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.components.RatingStarBadge
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.StatusRed
import com.moviebox.app.ui.theme.TextMuted
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import java.util.Locale

@Composable
fun AdminTmdbImportScreen(
    isTmdbConfigured: Boolean,
    tmdbState: TmdbSearchUiState,
    categories: List<Category>,
    defaultVipPoints: Int = 100,
    onSearch: (query: String, isSeries: Boolean) -> Unit,
    onSelectPreview: (CatalogItem) -> Unit,
    findExistingDuplicate: (tmdbId: Int?, isSeries: Boolean) -> CatalogItem?,
    onCommitImport: (
        preview: CatalogItem,
        selection: TmdbImportSelection,
        manualVideoUrl: String,
        selectedCategoryIds: List<String>,
        existingToOverwrite: CatalogItem?
    ) -> Unit,
    onConfigureApiKey: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("Oppenheimer") }
    var isSeriesMode by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(TmdbImportSelection()) }
    var manualVideoUrl1080p by remember { mutableStateOf("") }
    var manualVideoUrl720p by remember { mutableStateOf("") }
    var manualVideoUrl480p by remember { mutableStateOf("") }
    var isVipImport by remember { mutableStateOf(false) }
    var vipPointsInput by remember(defaultVipPoints) { mutableStateOf(defaultVipPoints.toString()) }
    var apiKeyDialogVisible by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var duplicateConfirmTarget by remember { mutableStateOf<CatalogItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Import from TMDB",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Search TMDB v3 by title or ID, preview metadata, set VIP/Free, and import to Firebase",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = { apiKeyDialogVisible = true },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = if (isTmdbConfigured) StatusGreen else StarGold,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (isTmdbConfigured) "API Key Active" else "Set API Key",
                    fontSize = 11.5.sp,
                    maxLines = 1
                )
            }
        }

        // MediaType Selector (Movie vs Web Series)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = !isSeriesMode,
                onClick = { isSeriesMode = false },
                label = { Text("Movies (TMDB)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = isSeriesMode,
                onClick = { isSeriesMode = true },
                label = { Text("Web Series (TV)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Filled.LiveTv, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }

        // Search Bar + Red "Search" Button
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
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Title (Oppenheimer) or ID (872585)", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                )
                Button(
                    onClick = { onSearch(searchQuery, isSeriesMode) },
                    enabled = !tmdbState.isLoading && searchQuery.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text(
                        text = if (tmdbState.isLoading) "..." else "Search",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        tmdbState.errorMessage?.let { err ->
            Surface(
                color = StatusRed.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StatusRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = StatusRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = err, color = TextWhite, fontSize = 12.sp)
                }
            }
        }

        if (tmdbState.results.size > 1) {
            Text(
                text = "Search Results (Tap to load full preview):",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tmdbState.results.take(5).forEach { candidate ->
                    val isSelected = tmdbState.selectedPreview?.tmdbId == candidate.tmdbId
                    Surface(
                        color = if (isSelected) CrimsonRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) CrimsonRed else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectPreview(candidate) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PosterImageWithFallback(
                                imageUrl = candidate.posterUrl,
                                title = candidate.title,
                                modifier = Modifier
                                    .size(46.dp, 66.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = candidate.title,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "TMDB ID: ${candidate.tmdbId ?: "—"} • ${candidate.releaseYear} • ★ ${String.format(Locale.US, "%.1f", candidate.rating)}",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (candidate.overview.isNotBlank()) {
                                    Text(
                                        text = candidate.overview,
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        val preview = tmdbState.selectedPreview
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (preview != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PosterImageWithFallback(
                            imageUrl = preview.posterUrl,
                            title = preview.title,
                            modifier = Modifier
                                .size(70.dp, 98.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preview.title,
                                color = TextWhite,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "${preview.releaseYear}", color = TextSecondary, fontSize = 12.5.sp)
                                RatingStarBadge(rating = preview.rating)
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = preview.genres.firstOrNull() ?: "Drama",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = preview.overview,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Search and select a TMDB title above to preview its metadata before importing.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                // Access Mode: FREE vs VIP Selector
                Surface(
                    color = if (isVipImport) StarGold.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isVipImport) StarGold.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Set Access Type (VIP / Free)",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = !isVipImport,
                                onClick = { isVipImport = false },
                                label = { Text("FREE Access") },
                                leadingIcon = {
                                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = isVipImport,
                                onClick = { isVipImport = true },
                                label = { Text("VIP Only") },
                                leadingIcon = {
                                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = StarGold, modifier = Modifier.size(15.dp))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (isVipImport) {
                            OutlinedTextField(
                                value = vipPointsInput,
                                onValueChange = { vipPointsInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("VIP Points Required to Access", maxLines = 1) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Text(
                    text = "Select Data to Import",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TmdbCheckboxRow(
                            label = "Title",
                            checked = selection.importTitle,
                            onCheckedChange = { selection = selection.copy(importTitle = it) }
                        )
                        TmdbCheckboxRow(
                            label = "Poster",
                            checked = selection.importPoster,
                            onCheckedChange = { selection = selection.copy(importPoster = it) }
                        )
                        TmdbCheckboxRow(
                            label = "Backdrop",
                            checked = selection.importBackdrop,
                            onCheckedChange = { selection = selection.copy(importBackdrop = it) }
                        )
                        TmdbCheckboxRow(
                            label = "Cast & Crew",
                            checked = selection.importCast,
                            onCheckedChange = { selection = selection.copy(importCast = it) }
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TmdbCheckboxRow(
                            label = "Overview",
                            checked = selection.importOverview,
                            onCheckedChange = { selection = selection.copy(importOverview = it) }
                        )
                        TmdbCheckboxRow(
                            label = "Trailer Link",
                            checked = selection.importTrailer,
                            onCheckedChange = { selection = selection.copy(importTrailer = it) }
                        )
                        TmdbCheckboxRow(
                            label = "Year & Rating",
                            checked = selection.importYearAndRating,
                            onCheckedChange = { selection = selection.copy(importYearAndRating = it) }
                        )
                        TmdbCheckboxRow(
                            label = "Seasons / Eps",
                            checked = selection.importSeasons,
                            onCheckedChange = { selection = selection.copy(importSeasons = it) }
                        )
                    }
                }

                Text(
                    text = "Optional: Add 3 Video Quality Stream Links",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = manualVideoUrl1080p,
                    onValueChange = { manualVideoUrl1080p = it },
                    label = { Text("1. 1080p Full HD Stream Link (.mp4 / .m3u8)", maxLines = 1) },
                    placeholder = { Text("https://.../movie_1080p.mp4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualVideoUrl720p,
                    onValueChange = { manualVideoUrl720p = it },
                    label = { Text("2. 720p HD Stream Link (.mp4 / .m3u8)", maxLines = 1) },
                    placeholder = { Text("https://.../movie_720p.mp4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualVideoUrl480p,
                    onValueChange = { manualVideoUrl480p = it },
                    label = { Text("3. 480p SD Stream Link (.mp4 / .m3u8)", maxLines = 1) },
                    placeholder = { Text("https://.../movie_480p.mp4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val item = preview ?: return@Button
                        val matchedCatIds = categories.filter { cat ->
                            item.genres.any { g -> g.equals(cat.name, ignoreCase = true) }
                        }.map { it.id }.ifEmpty {
                            listOfNotNull(categories.firstOrNull()?.id)
                        }
                        val primaryUrl = manualVideoUrl1080p.trim().ifBlank {
                            manualVideoUrl720p.trim().ifBlank { manualVideoUrl480p.trim() }
                        }
                        val resolvedVipPts = if (isVipImport) (vipPointsInput.toIntOrNull() ?: defaultVipPoints).coerceAtLeast(1) else 0
                        val itemWithQualities = item.copy(
                            videoUrl = primaryUrl,
                            videoUrl1080p = manualVideoUrl1080p.trim(),
                            videoUrl720p = manualVideoUrl720p.trim(),
                            videoUrl480p = manualVideoUrl480p.trim(),
                            isVip = isVipImport,
                            vipPointsRequired = resolvedVipPts
                        )
                        val existing = findExistingDuplicate(item.tmdbId, item.isSeries)
                        if (existing != null) {
                            duplicateConfirmTarget = existing
                        } else {
                            onCommitImport(itemWithQualities, selection, primaryUrl, matchedCatIds, null)
                        }
                    },
                    enabled = preview != null && !tmdbState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Import to Database",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                }

                Text(
                    text = "Attribution: This product uses the TMDB API but is not endorsed or certified by TMDB. Imported entries do not include copyrighted full-movie streams.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }

    duplicateConfirmTarget?.let { existing ->
        AlertDialog(
            onDismissRequest = { duplicateConfirmTarget = null },
            title = { Text("Duplicate TMDB Entry Found", fontWeight = FontWeight.Bold) },
            text = {
                Text("'${existing.title}' (TMDB ID: ${existing.tmdbId}) already exists in your Firebase catalog. Would you like to update its metadata with the selected fields?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val item = tmdbState.selectedPreview
                        if (item != null) {
                            val matchedCatIds = categories.filter { cat ->
                                item.genres.any { g -> g.equals(cat.name, ignoreCase = true) }
                            }.map { it.id }.ifEmpty { existing.categoryIds }
                            val primaryUrl = manualVideoUrl1080p.trim().ifBlank {
                                manualVideoUrl720p.trim().ifBlank { manualVideoUrl480p.trim() }
                            }
                            val resolvedVipPts = if (isVipImport) (vipPointsInput.toIntOrNull() ?: defaultVipPoints).coerceAtLeast(1) else 0
                            val itemWithQualities = item.copy(
                                videoUrl = primaryUrl,
                                videoUrl1080p = manualVideoUrl1080p.trim(),
                                videoUrl720p = manualVideoUrl720p.trim(),
                                videoUrl480p = manualVideoUrl480p.trim(),
                                isVip = isVipImport,
                                vipPointsRequired = resolvedVipPts
                            )
                            onCommitImport(itemWithQualities, selection, primaryUrl, matchedCatIds, existing)
                        }
                        duplicateConfirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("Update Existing", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicateConfirmTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (apiKeyDialogVisible) {
        AlertDialog(
            onDismissRequest = { apiKeyDialogVisible = false },
            title = { Text("Configure TMDB v3 API Key", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter your TMDB v3 API key. It will be stored in DataStore preferences.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("TMDB v3 API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfigureApiKey(apiKeyInput)
                        apiKeyDialogVisible = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("Save Key", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { apiKeyDialogVisible = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TmdbCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = CrimsonRed,
                uncheckedColor = TextSecondary
            )
        )
        Text(
            text = label,
            color = TextWhite,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
