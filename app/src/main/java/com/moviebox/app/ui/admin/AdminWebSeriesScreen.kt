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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.*
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.components.StatusPillBadge
import com.moviebox.app.ui.components.VipAccessBadge
import com.moviebox.app.ui.components.MediaAnalyticsBadgeRow
import com.moviebox.app.ui.components.MediaAnalyticsLogsDialog
import com.moviebox.app.ui.theme.StatusBlue
import com.moviebox.app.ui.theme.CrimsonLight
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
fun AdminWebSeriesScreen(
    webSeries: List<CatalogItem>,
    categories: List<Category>,
    mediaAnalyticsMap: Map<String, ItemMediaAnalytics> = emptyMap(),
    defaultVipPoints: Int = 100,
    initialEditingSeries: CatalogItem? = null,
    openEditorInitially: Boolean = false,
    onSaveSeries: (CatalogItem) -> Unit,
    onDeleteSeries: (CatalogItem) -> Unit,
    onSendPushForSeries: (CatalogItem) -> Unit = {},
    onNavigateToTmdbImport: () -> Unit
) {
    var editingSeries by remember(initialEditingSeries) { mutableStateOf(initialEditingSeries) }
    var isEditorOpen by remember(openEditorInitially, initialEditingSeries) {
        mutableStateOf(openEditorInitially || initialEditingSeries != null)
    }
    var deleteConfirmTarget by remember { mutableStateOf<CatalogItem?>(null) }
    var analyticsTargetSeries by remember { mutableStateOf<CatalogItem?>(null) }

    analyticsTargetSeries?.let { target ->
        MediaAnalyticsLogsDialog(
            item = target,
            analytics = mediaAnalyticsMap[target.id] ?: ItemMediaAnalytics(itemId = target.id),
            onDismiss = { analyticsTargetSeries = null }
        )
    }

    if (isEditorOpen) {
        AdminWebSeriesEditorView(
            initialSeries = editingSeries,
            categories = categories.filter {
                it.type == CategoryType.SERIES.name || it.type == CategoryType.BOTH.name
            },
            defaultVipPoints = defaultVipPoints,
            onSave = { item ->
                onSaveSeries(item)
                isEditorOpen = false
                editingSeries = null
            },
            onCancel = {
                isEditorOpen = false
                editingSeries = null
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.LiveTv, contentDescription = null, tint = CrimsonRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Web Series (${webSeries.size})",
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
                        editingSeries = null
                        isEditorOpen = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Series", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            if (webSeries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No web series configured yet. Tap '+ Add Series' above.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(webSeries, key = { _, s -> s.id }) { index, series ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                            PosterImageWithFallback(
                                imageUrl = series.posterUrl,
                                title = series.title,
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
                                    text = series.title,
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val epCount = series.seasons.sumOf { it.episodes.size }
                                Text(
                                    text = "${series.seasons.size} Seasons • $epCount Eps • ★ ${String.format(Locale.US, "%.1f", series.rating)}",
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
                                        active = series.published,
                                        activeLabel = "Active",
                                        inactiveLabel = "Draft"
                                    )
                                    VipAccessBadge(
                                        isVip = series.isVip,
                                        vipPoints = if (series.isVip) series.effectiveVipPoints(defaultVipPoints) else 0
                                    )
                                }
                                MediaAnalyticsBadgeRow(
                                    analytics = mediaAnalyticsMap[series.id] ?: ItemMediaAnalytics(itemId = series.id),
                                    onClick = { analyticsTargetSeries = series }
                                )
                            }
                            var rowMenuOpen by remember { mutableStateOf(false) }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        editingSeries = series
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
                                                analyticsTargetSeries = series
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (series.isVip) "Switch to FREE" else "Set as VIP (${defaultVipPoints} Pts)") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.WorkspacePremium,
                                                    contentDescription = null,
                                                    tint = StarGold
                                                )
                                            },
                                            onClick = {
                                                rowMenuOpen = false
                                                val nextVip = !series.isVip
                                                onSaveSeries(
                                                    series.copy(
                                                        isVip = nextVip,
                                                        vipPointsRequired = if (nextVip && series.vipPointsRequired <= 0) defaultVipPoints else series.vipPointsRequired
                                                    )
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Send Push Notification") },
                                            leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = CrimsonRed) },
                                            onClick = {
                                                rowMenuOpen = false
                                                onSendPushForSeries(series)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Edit Web Series") },
                                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                            onClick = {
                                                rowMenuOpen = false
                                                editingSeries = series
                                                isEditorOpen = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete Web Series") },
                                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = StatusRed) },
                                            onClick = {
                                                rowMenuOpen = false
                                                deleteConfirmTarget = series
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
        val totalEpisodes = target.seasons.sumOf { it.episodes.size }
        AlertDialog(
            onDismissRequest = { deleteConfirmTarget = null },
            title = { Text("Delete Web Series & Episodes?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Deleting '${target.title}' will permanently remove ${target.seasons.size} season(s) and $totalEpisodes child episode(s). Proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSeries(target)
                        deleteConfirmTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Delete Series", color = Color.White)
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
private fun AdminWebSeriesEditorView(
    initialSeries: CatalogItem?,
    categories: List<Category>,
    defaultVipPoints: Int,
    onSave: (CatalogItem) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }
    var title by remember { mutableStateOf(initialSeries?.title ?: "") }
    var genre by remember {
        mutableStateOf(
            initialSeries?.genres?.firstOrNull()
                ?: categories.firstOrNull()?.name
                ?: "Sci-Fi"
        )
    }
    var releaseYear by remember { mutableStateOf((initialSeries?.releaseYear ?: 2024).toString()) }
    var rating by remember { mutableStateOf((initialSeries?.rating ?: 8.5).toString()) }
    var overview by remember { mutableStateOf(initialSeries?.overview ?: "") }
    var posterUrl by remember { mutableStateOf(initialSeries?.posterUrl ?: "") }
    var backdropUrl by remember { mutableStateOf(initialSeries?.backdropUrl ?: "") }
    var trailerUrl by remember { mutableStateOf(initialSeries?.trailerUrl ?: "") }
    var isVip by remember { mutableStateOf(initialSeries?.isVip ?: false) }
    var vipPointsInput by remember {
        mutableStateOf((initialSeries?.vipPointsRequired?.takeIf { it > 0 } ?: defaultVipPoints).toString())
    }
    var published by remember { mutableStateOf(initialSeries?.published ?: true) }
    var seasons by remember {
        mutableStateOf(
            initialSeries?.seasons ?: listOf(
                Season(
                    id = "s_1",
                    seasonNumber = 1,
                    title = "Season 1",
                    episodes = listOf(
                        Episode(
                            id = "s1_e1",
                            seasonNumber = 1,
                            episodeNumber = 1,
                            title = "Episode 1",
                            overview = "Series premiere episode.",
                            runtimeMinutes = 45,
                            videoUrl = ""
                        )
                    )
                )
            )
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (initialSeries == null) "Add Web Series" else "Edit Series — ${initialSeries.title}",
                color = TextWhite,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
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
                                    text = "Web Series Access Mode (VIP / Free)",
                                    color = TextWhite,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (isVip) "VIP Web Series — Requires Points or Share Code to play"
                                    else "Free Web Series — Playable by all users immediately",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                label = { Text("VIP Points Required to Access Web Series", maxLines = 1) },
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

                // Full-width Series Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Series Title", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3-field info row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Primary Genre", maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = releaseYear,
                        onValueChange = { releaseYear = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Release Year", maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(0.9f)
                    )
                    OutlinedTextField(
                        value = rating,
                        onValueChange = { rating = it },
                        label = { Text("Rating", maxLines = 1) },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                OutlinedTextField(
                    value = overview,
                    onValueChange = { overview = it },
                    label = { Text("Series Overview", maxLines = 1) },
                    minLines = 2,
                    maxLines = 5,
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
                OutlinedTextField(
                    value = trailerUrl,
                    onValueChange = { trailerUrl = it },
                    label = { Text("Trailer URL (YouTube or HTTPS)", maxLines = 1) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Seasons & Episodes (${seasons.size})",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    OutlinedButton(
                        onClick = {
                            val nextSeasonNum = (seasons.maxOfOrNull { it.seasonNumber } ?: 0) + 1
                            seasons = seasons + Season(
                                id = "s_$nextSeasonNum",
                                seasonNumber = nextSeasonNum,
                                title = "Season $nextSeasonNum",
                                episodes = listOf(
                                    Episode(
                                        id = "s${nextSeasonNum}_e1",
                                        seasonNumber = nextSeasonNum,
                                        episodeNumber = 1,
                                        title = "Episode 1",
                                        runtimeMinutes = 45
                                    )
                                )
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Season", fontSize = 12.sp, maxLines = 1)
                    }
                }

                seasons.forEachIndexed { sIdx, season ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
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
                                Text(
                                    text = "Season ${season.seasonNumber} (${season.episodes.size} Eps)",
                                    color = CrimsonLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            val nextEpNum = (season.episodes.maxOfOrNull { it.episodeNumber } ?: 0) + 1
                                            val updatedSeason = season.copy(
                                                episodes = season.episodes + Episode(
                                                    id = "s${season.seasonNumber}_e$nextEpNum",
                                                    seasonNumber = season.seasonNumber,
                                                    episodeNumber = nextEpNum,
                                                    title = "Episode $nextEpNum",
                                                    runtimeMinutes = 45
                                                )
                                            )
                                            seasons = seasons.toMutableList().also { it[sIdx] = updatedSeason }
                                        }
                                    ) {
                                        Text("+ Episode", color = StatusGreen, fontSize = 12.sp, maxLines = 1)
                                    }
                                    if (seasons.size > 1) {
                                        TextButton(
                                            onClick = {
                                                seasons = seasons.filterIndexed { i, _ -> i != sIdx }
                                            }
                                        ) {
                                            Text("Remove", color = StatusRed, fontSize = 12.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }

                            season.episodes.forEachIndexed { eIdx, ep ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = CrimsonRed.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "E${ep.episodeNumber}",
                                                    color = CrimsonLight,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                )
                                            }
                                            OutlinedTextField(
                                                value = ep.title,
                                                onValueChange = { newTitle ->
                                                    val updatedEp = ep.copy(title = newTitle)
                                                    val updatedSeason = season.copy(
                                                        episodes = season.episodes.toMutableList().also { it[eIdx] = updatedEp }
                                                    )
                                                    seasons = seasons.toMutableList().also { it[sIdx] = updatedSeason }
                                                },
                                                label = { Text("Episode Title", maxLines = 1) },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (season.episodes.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        val updatedSeason = season.copy(
                                                            episodes = season.episodes.filterIndexed { i, _ -> i != eIdx }
                                                        )
                                                        seasons = seasons.toMutableList().also { it[sIdx] = updatedSeason }
                                                    },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Remove Episode", tint = StatusRed)
                                                }
                                            }
                                        }
                                        OutlinedTextField(
                                            value = ep.videoUrl1080p.ifBlank { ep.videoUrl },
                                            onValueChange = { newUrl ->
                                                val primary = newUrl.trim().ifBlank { ep.videoUrl720p.ifBlank { ep.videoUrl480p } }
                                                val updatedEp = ep.copy(videoUrl = primary, videoUrl1080p = newUrl)
                                                val updatedSeason = season.copy(
                                                    episodes = season.episodes.toMutableList().also { it[eIdx] = updatedEp }
                                                )
                                                seasons = seasons.toMutableList().also { it[sIdx] = updatedSeason }
                                            },
                                            label = { Text("1. 1080p Full HD Episode Link (.mp4/.m3u8)", maxLines = 1) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = ep.videoUrl720p,
                                            onValueChange = { newUrl ->
                                                val primary = ep.videoUrl1080p.ifBlank { newUrl.trim().ifBlank { ep.videoUrl480p } }
                                                val updatedEp = ep.copy(videoUrl = primary, videoUrl720p = newUrl)
                                                val updatedSeason = season.copy(
                                                    episodes = season.episodes.toMutableList().also { it[eIdx] = updatedEp }
                                                )
                                                seasons = seasons.toMutableList().also { it[sIdx] = updatedSeason }
                                            },
                                            label = { Text("2. 720p HD Episode Link (.mp4/.m3u8)", maxLines = 1) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = ep.videoUrl480p,
                                            onValueChange = { newUrl ->
                                                val primary = ep.videoUrl1080p.ifBlank { ep.videoUrl720p.ifBlank { newUrl.trim() } }
                                                val updatedEp = ep.copy(videoUrl = primary, videoUrl480p = newUrl)
                                                val updatedSeason = season.copy(
                                                    episodes = season.episodes.toMutableList().also { it[eIdx] = updatedEp }
                                                )
                                                seasons = seasons.toMutableList().also { it[sIdx] = updatedSeason }
                                            },
                                            label = { Text("3. 480p SD Episode Link (.mp4/.m3u8)", maxLines = 1) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                validationError?.let { err ->
                    Text(text = err, color = StatusRed, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = published, onCheckedChange = { published = it })
                    Text("Published in User Catalog", color = TextWhite, fontSize = 13.sp)
                }

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
                                validationError = "Series title is required."
                                return@Button
                            }
                            val orderError = UrlValidator.validateSeasonEpisodeOrdering(seasons)
                            if (orderError != null) {
                                validationError = orderError
                                return@Button
                            }
                            val firstEpStream = seasons.firstOrNull()?.episodes?.firstOrNull()?.videoUrl.orEmpty()
                            val resolvedVipPoints = if (isVip) {
                                (vipPointsInput.toIntOrNull() ?: defaultVipPoints).coerceAtLeast(1)
                            } else {
                                0
                            }
                            val baseSeries = initialSeries ?: CatalogItem()
                            val seriesItem = baseSeries.copy(
                                id = initialSeries?.id ?: "series_${UUID.randomUUID().toString().take(8)}",
                                tmdbId = initialSeries?.tmdbId,
                                title = title.trim(),
                                normalizedTitle = UrlValidator.normalizeTitle(title),
                                overview = overview.trim(),
                                mediaType = MediaType.SERIES.name,
                                releaseYear = releaseYear.toIntOrNull() ?: 2024,
                                rating = (rating.toDoubleOrNull() ?: 8.5).coerceIn(0.0, 10.0),
                                runtimeMinutes = initialSeries?.runtimeMinutes?.takeIf { it > 0 } ?: 45,
                                posterUrl = posterUrl.trim(),
                                backdropUrl = backdropUrl.trim().ifBlank { posterUrl.trim() },
                                trailerUrl = trailerUrl.trim(),
                                videoUrl = firstEpStream,
                                genres = listOf(genre.trim().ifBlank { "Sci-Fi" }),
                                categoryIds = listOf(
                                    categories.firstOrNull { it.name.equals(genre.trim(), ignoreCase = true) }?.id
                                        ?: "cat_scifi"
                                ),
                                seasons = seasons,
                                isVip = isVip,
                                vipPointsRequired = resolvedVipPoints,
                                published = published,
                                trending = initialSeries?.trending ?: true,
                                popular = initialSeries?.popular ?: true,
                                sourceAttribution = initialSeries?.sourceAttribution ?: "MANUAL",
                                createdAt = initialSeries?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSave(seriesItem)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Web Series", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
