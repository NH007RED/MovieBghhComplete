package com.moviebox.app.ui.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.repository.CatalogState
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.components.StatusPillBadge
import com.moviebox.app.ui.components.VipAccessBadge
import com.moviebox.app.ui.components.MediaAnalyticsBadgeRow
import com.moviebox.app.ui.components.MediaAnalyticsLogsDialog
import com.moviebox.app.data.model.ItemMediaAnalytics
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Download
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StatusBlue
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import java.util.Calendar

@Composable
fun AdminDashboardScreen(
    catalogState: CatalogState,
    onAddMovie: () -> Unit,
    onAddSeries: () -> Unit,
    onOpenTmdbImport: () -> Unit,
    onEditItem: (CatalogItem) -> Unit,
    onSendPushForItem: (CatalogItem) -> Unit = {},
    onTogglePublish: (CatalogItem) -> Unit,
    onDeleteItem: (CatalogItem) -> Unit,
    onSeedDemoCatalog: () -> Unit
) {
    val movies = catalogState.movies
    val series = catalogState.series
    val categories = catalogState.categories
    val users = catalogState.users
    val recentUploads = (movies + series).sortedByDescending { it.updatedAt }.take(8)
    val defaultVipPts = catalogState.vipAccessConfig.vipPointsToAccess
    var analyticsTargetItem by remember { mutableStateOf<CatalogItem?>(null) }
    val totalCatalogViews = remember(catalogState.mediaAnalyticsMap) {
        catalogState.mediaAnalyticsMap.values.sumOf { it.viewCount }
    }
    val totalCatalogDownloads = remember(catalogState.mediaAnalyticsMap) {
        catalogState.mediaAnalyticsMap.values.sumOf { it.downloadCount }
    }

    analyticsTargetItem?.let { target ->
        MediaAnalyticsLogsDialog(
            item = target,
            analytics = catalogState.analyticsFor(target.id),
            onDismiss = { analyticsTargetItem = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 4 KPI Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiStatCard(
                title = "Total Movies",
                value = movies.size.toString(),
                subtitle = "${movies.count { it.published }} published • ${movies.count { it.isVip }} VIP",
                icon = Icons.Filled.Movie,
                accent = CrimsonRed,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                title = "Total Web Series",
                value = series.size.toString(),
                subtitle = "${series.count { it.published }} published • ${series.count { it.isVip }} VIP",
                icon = Icons.Filled.LiveTv,
                accent = StatusBlue,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiStatCard(
                title = "Total Categories",
                value = categories.size.toString(),
                subtitle = "${categories.count { it.active }} active",
                icon = Icons.Filled.Category,
                accent = Color(0xFFAB47BC),
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                title = if (users.isNotEmpty()) "Total Users" else "Streamable Titles",
                value = if (users.isNotEmpty()) {
                    users.size.toString()
                } else {
                    (movies.count { it.videoUrl.isNotBlank() } + series.count { s -> s.seasons.any { sn -> sn.episodes.any { it.videoUrl.isNotBlank() } } }).toString()
                },
                subtitle = if (users.isNotEmpty()) "${users.count { it.online }} online" else "authorized streams",
                icon = Icons.Filled.People,
                accent = StatusGreen,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiStatCard(
                title = "Total Views",
                value = totalCatalogViews.toString(),
                subtitle = "Movies & Series watched",
                icon = Icons.Filled.Visibility,
                accent = StatusBlue,
                modifier = Modifier.weight(1f)
            )
            KpiStatCard(
                title = "Total Downloads",
                value = totalCatalogDownloads.toString(),
                subtitle = "Offline downloads",
                icon = Icons.Filled.Download,
                accent = StatusGreen,
                modifier = Modifier.weight(1f)
            )
        }

        // Quick Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onAddMovie,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Movie", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onAddSeries,
                colors = ButtonDefaults.buttonColors(containerColor = StatusBlue),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Series", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onOpenTmdbImport,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("TMDB", color = TextWhite, fontSize = 12.5.sp)
            }
        }

        if (movies.isEmpty() && series.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Database is Currently Empty",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "You can add movies/series manually, import metadata from TMDB, or seed the open-source Blender Foundation CC-BY demo catalog to test video streaming right away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                OutlinedButton(onClick = onSeedDemoCatalog) {
                    Text("Seed Open-Source Demo Catalog (CC-BY Streams)")
                }
            }
        }

        ContentOverviewChartCard(movies = movies, series = series)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Recent Uploads",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            if (recentUploads.isEmpty()) {
                Text(
                    text = "No movies or web series uploaded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                recentUploads.forEach { item ->
                    RecentUploadRow(
                        item = item,
                        analytics = catalogState.analyticsFor(item.id),
                        defaultVipPoints = defaultVipPts,
                        onOpenAnalytics = { analyticsTargetItem = item },
                        onEdit = { onEditItem(item) },
                        onSendPush = { onSendPushForItem(item) },
                        onTogglePublish = { onTogglePublish(item) },
                        onDelete = { onDeleteItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, style = MaterialTheme.typography.headlineSmall, color = TextWhite, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ContentOverviewChartCard(
    movies: List<CatalogItem>,
    series: List<CatalogItem>
) {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val movieCounts = IntArray(12)
    val seriesCounts = IntArray(12)
    val cal = Calendar.getInstance()

    movies.forEach { m ->
        val ts = if (m.createdAt > 0L) m.createdAt else m.updatedAt
        if (ts > 0L) {
            cal.timeInMillis = ts
            val mIdx = cal.get(Calendar.MONTH).coerceIn(0, 11)
            movieCounts[mIdx]++
        }
    }
    series.forEach { s ->
        val ts = if (s.createdAt > 0L) s.createdAt else s.updatedAt
        if (ts > 0L) {
            cal.timeInMillis = ts
            val mIdx = cal.get(Calendar.MONTH).coerceIn(0, 11)
            seriesCounts[mIdx]++
        }
    }
    val maxVal = ((movieCounts.maxOrNull() ?: 0).coerceAtLeast(seriesCounts.maxOrNull() ?: 0)).coerceAtLeast(4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Content Overview",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(StatusBlue))
                    Text("Movies", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(CrimsonRed))
                    Text("Web Series", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val groupWidth = size.width / 12f
            val barW = (groupWidth * 0.28f).coerceAtLeast(4f)
            val chartH = size.height

            for (i in 0 until 12) {
                val gx = i * groupWidth + groupWidth * 0.2f
                val mH = (movieCounts[i].toFloat() / maxVal.toFloat()) * (chartH * 0.85f)
                val sH = (seriesCounts[i].toFloat() / maxVal.toFloat()) * (chartH * 0.85f)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.05f),
                    topLeft = Offset(gx, chartH * 0.1f),
                    size = Size(barW * 2.2f, chartH * 0.9f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                if (mH > 0f) {
                    drawRoundRect(
                        color = StatusBlue,
                        topLeft = Offset(gx, chartH - mH),
                        size = Size(barW, mH),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
                if (sH > 0f) {
                    drawRoundRect(
                        color = CrimsonRed,
                        topLeft = Offset(gx + barW * 1.2f, chartH - sH),
                        size = Size(barW, sH),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            months.forEach { m ->
                Text(text = m, fontSize = 9.5.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun RecentUploadRow(
    item: CatalogItem,
    analytics: ItemMediaAnalytics,
    defaultVipPoints: Int,
    onOpenAnalytics: () -> Unit,
    onEdit: () -> Unit,
    onSendPush: () -> Unit,
    onTogglePublish: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterImageWithFallback(
            imageUrl = item.posterUrl.ifBlank { item.backdropUrl },
            title = item.title,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${if (item.isSeries) "Web Series" else "Movie"} • ${item.releaseYear}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                VipAccessBadge(
                    isVip = item.isVip,
                    vipPoints = if (item.isVip) item.effectiveVipPoints(defaultVipPoints) else 0
                )
            }
            MediaAnalyticsBadgeRow(
                analytics = analytics,
                onClick = onOpenAnalytics
            )
        }
        StatusPillBadge(active = item.published, activeLabel = "Active", inactiveLabel = "Draft")
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = TextSecondary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("View & Download Logs") },
                    leadingIcon = { Icon(Icons.Filled.Visibility, contentDescription = null, tint = StatusBlue) },
                    onClick = { menuOpen = false; onOpenAnalytics() }
                )
                DropdownMenuItem(
                    text = { Text("Send Push Notification") },
                    leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = CrimsonRed) },
                    onClick = { menuOpen = false; onSendPush() }
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(if (item.published) "Unpublish (Set Draft)" else "Publish") },
                    onClick = { menuOpen = false; onTogglePublish() }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}
