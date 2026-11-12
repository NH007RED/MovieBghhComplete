package com.moviebox.app.ui.user

import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moviebox.app.ui.components.FilterPill
import com.moviebox.app.ui.components.MovieBoxLogo
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.StatusRed
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import com.moviebox.app.util.OfflineDownloadEntry
import com.moviebox.app.util.OfflineDownloadManager
import com.moviebox.app.util.OfflineDownloadStatus
import java.util.Locale

@Composable
fun OfflineDownloadsScreen(
    downloads: List<OfflineDownloadEntry>,
    isDeviceOnline: Boolean,
    onPlayOfflineItem: (OfflineDownloadEntry) -> Unit,
    onDeleteOfflineItem: (String) -> Unit,
    onBrowseOnlineCatalog: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") }

    BackHandler(enabled = isDeviceOnline) {
        onBrowseOnlineCatalog()
    }

    val filteredList = remember(downloads, selectedFilter) {
        val sorted = downloads.sortedByDescending { it.createdAt }
        when (selectedFilter) {
            "MOVIES" -> sorted.filter { !it.isSeries }
            "SERIES" -> sorted.filter { it.isSeries }
            else -> sorted
        }
    }

    val completedCount = remember(downloads) {
        downloads.count { it.status == OfflineDownloadStatus.COMPLETED }
    }
    val totalChunks = remember(downloads) {
        downloads.sumOf { it.chunkCount }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MovieBoxLogo(
                iconSize = 28.dp,
                fontSizeSp = 20,
                subtitle = if (!isDeviceOnline) "Offline Mode • Downloads" else "My Offline Downloads"
            )

            if (isDeviceOnline) {
                OutlinedButton(
                    onClick = onBrowseOnlineCatalog,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Home",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            } else {
                Surface(
                    color = StatusAmber.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Offline Mode",
                            color = StatusAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Offline Vault Status Banner
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (!isDeviceOnline) StatusAmber.copy(alpha = 0.45f) else StatusGreen.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (!isDeviceOnline) StatusAmber.copy(alpha = 0.18f)
                            else StatusGreen.copy(alpha = 0.18f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (!isDeviceOnline) Icons.Filled.WifiOff else Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = if (!isDeviceOnline) StatusAmber else StatusGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!isDeviceOnline) {
                            "No Internet Connection — Showing Offline Downloads"
                        } else {
                            "$completedCount Title(s) Ready for Offline Playback"
                        },
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Stored in $totalChunks encrypted .mbx chunk files • Supports Pause, Resume & Retry",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Filter Pills: All | Movies | Web Series
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(
                label = "All (${downloads.size})",
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" }
            )
            FilterPill(
                label = "Movies (${downloads.count { !it.isSeries }})",
                selected = selectedFilter == "MOVIES",
                onClick = { selectedFilter = "MOVIES" }
            )
            FilterPill(
                label = "Web Series (${downloads.count { it.isSeries }})",
                selected = selectedFilter == "SERIES",
                onClick = { selectedFilter = "SERIES" }
            )
        }

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DownloadForOffline,
                            contentDescription = null,
                            tint = CrimsonRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "No Offline Downloads Yet",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Tap the Download icon on any Movie or Web Series details page to save it in encrypted chunks and watch offline anytime.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    if (isDeviceOnline) {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onBrowseOnlineCatalog,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Explore, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Explore Movies & Web Series", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filteredList, key = { it.downloadId }) { entry ->
                    OfflineDownloadCard(
                        entry = entry,
                        onPlay = { onPlayOfflineItem(entry) },
                        onPause = { OfflineDownloadManager.pauseDownload(context, entry.downloadId) },
                        onResumeOrRetry = { OfflineDownloadManager.resumeOrRetryDownload(context, entry.downloadId) },
                        onDelete = { onDeleteOfflineItem(entry.downloadId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineDownloadCard(
    entry: OfflineDownloadEntry,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResumeOrRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isCompleted = entry.status == OfflineDownloadStatus.COMPLETED
    val isDownloading = entry.status == OfflineDownloadStatus.DOWNLOADING
    val isPaused = entry.status == OfflineDownloadStatus.PAUSED
    val isFailed = entry.status == OfflineDownloadStatus.FAILED

    val localPosterFile = remember(entry.localPosterPath) {
        entry.localPosterPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
    }

    val borderColor = when (entry.status) {
        OfflineDownloadStatus.COMPLETED -> StatusGreen.copy(alpha = 0.38f)
        OfflineDownloadStatus.PAUSED -> StatusAmber.copy(alpha = 0.45f)
        OfflineDownloadStatus.FAILED -> StatusRed.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(width = 1.dp, color = borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isCompleted, onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster Thumbnail
            Box(
                modifier = Modifier
                    .width(78.dp)
                    .height(110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF151924)),
                contentAlignment = Alignment.Center
            ) {
                if (localPosterFile != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(localPosterFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PosterImageWithFallback(
                        imageUrl = entry.posterUrl.ifBlank { entry.backdropUrl },
                        title = entry.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (entry.isSeries) CrimsonRed else Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (entry.isSeries) "SERIES" else "MOVIE",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Metadata & Progress Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title,
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${entry.releaseYear}",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = StarGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f", entry.rating),
                            color = TextWhite,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• ${entry.formattedSize}",
                        color = CrimsonLight,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                when (entry.status) {
                    OfflineDownloadStatus.DOWNLOADING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { (entry.progressPercent / 100f).coerceIn(0f, 1f) },
                                color = CrimsonRed,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Text(
                                text = "${entry.progressPercent}%",
                                color = CrimsonLight,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "Downloading encrypted chunks (${entry.chunkCount} parts)...",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    OfflineDownloadStatus.PAUSED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { (entry.progressPercent / 100f).coerceIn(0f, 1f) },
                                color = StatusAmber,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                            Text(
                                text = "${entry.progressPercent}%",
                                color = StatusAmber,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = "Paused • ${entry.chunkCount} chunks saved (Tap Resume)",
                            color = StatusAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OfflineDownloadStatus.COMPLETED -> {
                        Text(
                            text = "✓ ${entry.chunkCount} Encrypted Chunks • Ready to Play Offline",
                            color = StatusGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OfflineDownloadStatus.FAILED -> {
                        Text(
                            text = entry.errorMessage ?: "Download failed — Tap Retry",
                            color = StatusRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OfflineDownloadStatus.IDLE -> {}
                }
            }

            // Right Action Buttons: Pause | Resume | Retry | Play Offline + Delete
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when {
                    isDownloading -> {
                        // Pause Button with Circular Progress Ring
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onPause),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { (entry.progressPercent / 100f).coerceIn(0.04f, 1f) },
                                color = CrimsonRed,
                                trackColor = Color.White.copy(alpha = 0.16f),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "Pause Download",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    isPaused -> {
                        // Resume Button
                        IconButton(
                            onClick = onResumeOrRetry,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(StatusAmber.copy(alpha = 0.2f))
                                .border(1.5.dp, StatusAmber, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Resume Download",
                                tint = StatusAmber,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    isFailed -> {
                        // Retry Button
                        IconButton(
                            onClick = onResumeOrRetry,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(StatusRed.copy(alpha = 0.2f))
                                .border(1.5.dp, StatusRed, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Retry Download",
                                tint = StatusRed,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    isCompleted -> {
                        // Play Offline Button
                        IconButton(
                            onClick = onPlay,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(CrimsonRed)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play Offline",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete Download",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
