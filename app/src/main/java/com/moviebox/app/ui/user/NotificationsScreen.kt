package com.moviebox.app.ui.user

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.PushNotificationPayload
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextMuted
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(
    notifications: List<PushNotificationPayload>,
    allPublishedItems: List<CatalogItem>,
    onOpenItemDetails: (CatalogItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val sortedNotifications = remember(notifications) {
        notifications.distinctBy { it.id }.sortedByDescending { it.sentAt }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CrimsonRed.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Notifications",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "New movie & web series release alerts",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }

            Surface(
                color = CrimsonRed.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.45f))
            ) {
                Text(
                    text = "${sortedNotifications.size} Alert(s)",
                    color = CrimsonLight,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        if (sortedNotifications.isEmpty()) {
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
                            imageVector = Icons.Filled.NotificationsNone,
                            contentDescription = null,
                            tint = CrimsonRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Text(
                        text = "No Notifications Yet",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Whenever a new Movie or Web Series push notification arrives, it will be saved and displayed right here.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(sortedNotifications, key = { it.id }) { notif ->
                    val matchedItem = remember(notif.itemId, notif.title, allPublishedItems) {
                        allPublishedItems.find { it.id == notif.itemId }
                            ?: allPublishedItems.find {
                                it.title.isNotBlank() && notif.title.contains(it.title, ignoreCase = true)
                            }
                    }
                    NotificationInboxCard(
                        notif = notif,
                        matchedItem = matchedItem,
                        onClick = {
                            if (matchedItem != null) {
                                onOpenItemDetails(matchedItem)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationInboxCard(
    notif: PushNotificationPayload,
    matchedItem: CatalogItem?,
    onClick: () -> Unit
) {
    val timeLabel = remember(notif.sentAt) {
        if (notif.sentAt <= 0L) "Just now"
        else {
            runCatching {
                SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.US).format(Date(notif.sentAt))
            }.getOrDefault("Recent")
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = matchedItem != null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card Header: Bell badge + Title + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CrimsonRed.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notif.title.ifBlank { "MovieBox New Release" },
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = timeLabel,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                if (matchedItem != null) {
                    Surface(
                        color = StatusGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (matchedItem.isSeries) "SERIES" else "MOVIE",
                            color = StatusGreen,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Notification Body Text
            if (notif.body.isNotBlank()) {
                Text(
                    text = notif.body,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            // 16:9 Rounded Backdrop Image
            val displayImage = notif.imageUrl.ifBlank {
                matchedItem?.backdropUrl.orEmpty().ifBlank { matchedItem?.posterUrl.orEmpty() }
            }
            if (displayImage.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                ) {
                    PosterImageWithFallback(
                        imageUrl = displayImage,
                        title = notif.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Watch / View Details Button when linked to a catalog item
            if (matchedItem != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Watch Now",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
