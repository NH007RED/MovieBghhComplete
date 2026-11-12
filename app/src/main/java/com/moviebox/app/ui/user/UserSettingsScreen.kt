package com.moviebox.app.ui.user

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.BuildConfig
import com.moviebox.app.R
import com.moviebox.app.data.model.AppPreferences
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.MediaType
import com.moviebox.app.data.model.ThemeVariant
import com.moviebox.app.data.model.UserPresenceRecord
import com.moviebox.app.data.model.VipAccessConfig
import com.moviebox.app.data.repository.UserPresenceManager
import com.moviebox.app.security.NhNativeSecurityVault
import com.moviebox.app.ui.components.FilterPill
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
fun UserSettingsScreen(
    preferences: AppPreferences,
    userRecord: UserPresenceRecord = UserPresenceRecord(),
    vipConfig: VipAccessConfig = VipAccessConfig(),
    upcomingItems: List<CatalogItem> = emptyList(),
    allPublishedItems: List<CatalogItem> = emptyList(),
    onSelectTheme: (ThemeVariant) -> Unit,
    onSelectQuality: (String) -> Unit,
    onToggleAutoPlay: (Boolean) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onRedeemShareCode: (String) -> Unit = {},
    onOpenOfflineDownloads: () -> Unit = {},
    onOpenItemDetails: (CatalogItem) -> Unit = {},
    onRefreshUpcoming: () -> Unit = {}
) {
    var showUpcomingPosterScreen by rememberSaveable { mutableStateOf(false) }

    if (showUpcomingPosterScreen) {
        UpcomingMoviesAndSeriesPosterScreen(
            upcomingItems = upcomingItems,
            allPublishedItems = allPublishedItems,
            onBack = { showUpcomingPosterScreen = false },
            onRefresh = onRefreshUpcoming,
            onSelectPoster = { clickedItem ->
                val matchedPlayable = allPublishedItems.firstOrNull { pub ->
                    (pub.tmdbId != null && pub.tmdbId == clickedItem.tmdbId && pub.mediaType == clickedItem.mediaType) ||
                        (pub.title.equals(clickedItem.title, ignoreCase = true) && pub.mediaType == clickedItem.mediaType)
                }
                onOpenItemDetails(matchedPlayable ?: clickedItem)
            }
        )
        return
    }

    val context = LocalContext.current
    var qualityMenu by remember { mutableStateOf(false) }
    val qualities = listOf("Auto", "HD (1080p)", "HD (720p)", "SD (480p)")

    var redeemCodeInput by remember { mutableStateOf("") }
    var redeemStatusNote by remember { mutableStateOf<String?>(null) }

    val resolvedDeviceId = remember(userRecord.deviceId, context) {
        userRecord.deviceId.ifBlank { UserPresenceManager.getRealDeviceId(context) }
    }
    val resolvedShareCode = remember(userRecord.shareCode, resolvedDeviceId, context) {
        userRecord.shareCode.ifBlank { UserPresenceManager.getFiveDigitShareCode(context) }
    }
    val requiredVipPoints = vipConfig.vipPointsToAccess.coerceAtLeast(1)
    val isVipUnlocked = userRecord.hasGlobalVip(requiredVipPoints)
    val progressFraction = (userRecord.points.toFloat() / requiredVipPoints.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Slim Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CrimsonRed.copy(alpha = 0.18f))
                        .border(1.dp, CrimsonRed.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = CrimsonLight,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Settings",
                    fontSize = 18.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }

        // =========================================================================
        // 1. SLIM VIP POINTS, 5-DIGIT SHARE CODE & REDEEM CARD
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF1A1523),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (isVipUnlocked) StarGold.copy(alpha = 0.7f) else CrimsonRed.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Compact Top Row: VIP Crown + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isVipUnlocked) StarGold.copy(alpha = 0.2f) else CrimsonRed.copy(alpha = 0.18f))
                            .border(1.dp, if (isVipUnlocked) StarGold else CrimsonRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = null,
                            tint = if (isVipUnlocked) StarGold else CrimsonLight,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isVipUnlocked) "VIP Access Active" else "VIP Points & Share Code",
                            color = TextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            maxLines = 1
                        )
                        Text(
                            text = if (isVipUnlocked) {
                                "All VIP Movies & Web Series unlocked"
                            } else {
                                "Redeem 5-digit Share Code to unlock VIP"
                            },
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Surface(
                    color = if (isVipUnlocked) StarGold.copy(alpha = 0.2f) else CrimsonRed.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, if (isVipUnlocked) StarGold else CrimsonLight)
                ) {
                    Text(
                        text = if (isVipUnlocked) "👑 VIP" else "FREE",
                        color = if (isVipUnlocked) StarGold else CrimsonLight,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.5.dp)
                    )
                }
            }

            // Slim 2-Column Stat Boxes: My Points | Share Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = StarGold.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StarGold.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(StarGold.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(15.dp))
                        }
                        Column {
                            Text(
                                text = "${userRecord.points} Pts",
                                color = StarGold,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "My Points",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Surface(
                    color = CrimsonRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.4f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CrimsonRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.People, contentDescription = null, tint = CrimsonLight, modifier = Modifier.size(15.dp))
                        }
                        Column {
                            Text(
                                text = "${userRecord.shareCount}",
                                color = CrimsonLight,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                maxLines = 1
                            )
                            Text(
                                text = "Share Count",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Slim Progress Bar toward VIP Access
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isVipUnlocked) "VIP Access Unlocked!"
                        else "${userRecord.points} / $requiredVipPoints Pts to Unlock VIP",
                        color = if (isVipUnlocked) StatusGreen else TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "+${vipConfig.pointsPerReferral} Pts / Code",
                        color = StarGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                }
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = if (isVipUnlocked) StatusGreen else StarGold,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                )
            }

            // Compact 5-Digit Share Code + Device ID Box
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            Text(
                                text = "MY CODE:",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = resolvedShareCode.toCharArray().joinToString(" "),
                                color = StatusBlue,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("5-Digit Share Code", resolvedShareCode))
                                Toast.makeText(context, "Copied 5-Digit Code: $resolvedShareCode", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy Share Code",
                                tint = StatusBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Copy", color = StatusBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Smartphone,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "ID: $resolvedDeviceId",
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Device ID", resolvedDeviceId))
                                Toast.makeText(context, "Device ID copied: $resolvedDeviceId", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy Device ID",
                                tint = TextSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Compact Redeem 5-Digit Share Code Section (Side-by-Side Box + Button)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CardGiftcard,
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Redeem Friend's 5-Digit Code (+${vipConfig.pointsPerReferral} Pts)",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = redeemCodeInput,
                        onValueChange = { input ->
                            redeemCodeInput = input.filter { it.isDigit() }.take(5)
                            redeemStatusNote = null
                        },
                        placeholder = {
                            Text(
                                text = "5-Digit Code (e.g. 48291)",
                                fontSize = 11.5.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        textStyle = TextStyle(
                            color = TextWhite,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(9.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StarGold,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )

                    Button(
                        onClick = {
                            val clean = redeemCodeInput.trim()
                            if (clean.length != 5) {
                                redeemStatusNote = "Enter a valid 5-digit code."
                            } else {
                                onRedeemShareCode(clean)
                                redeemCodeInput = ""
                            }
                        },
                        enabled = redeemCodeInput.length == 5,
                        colors = ButtonDefaults.buttonColors(containerColor = StarGold),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "Redeem",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.5.sp,
                            maxLines = 1
                        )
                    }
                }

                redeemStatusNote?.let { note ->
                    Text(
                        text = note,
                        color = CrimsonLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (userRecord.redeemedCodes.isNotEmpty() || userRecord.redeemedCode.isNotBlank()) {
                    val redeemedDisplay = userRecord.redeemedCodes.joinToString(", ").ifBlank { userRecord.redeemedCode }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = StatusGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Redeemed: $redeemedDisplay",
                            color = StatusGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 2. UPCOMING MOVIES & WEB SERIES QUICK ACCESS ROW (NEW IN v20.0)
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF191D2B),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .border(1.dp, StatusAmber.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .clickable {
                    onRefreshUpcoming()
                    showUpcomingPosterScreen = true
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(StatusAmber.copy(alpha = 0.18f))
                        .border(1.dp, StatusAmber.copy(alpha = 0.5f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = StatusAmber,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Upcoming Movies & Web Series",
                            fontSize = 13.5.sp,
                            color = TextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (upcomingItems.isNotEmpty()) {
                            Surface(
                                color = StatusAmber.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "${upcomingItems.size}",
                                    color = StatusAmber,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "View posters, live release countdown & play available titles",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    onRefreshUpcoming()
                    showUpcomingPosterScreen = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = StatusAmber),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = "View",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.5.sp
                )
            }
        }

        // =========================================================================
        // 3. SLIM OFFLINE DOWNLOADS ROW
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, CrimsonRed.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenOfflineDownloads)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(CrimsonRed.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DownloadForOffline,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "My Offline Downloads",
                        fontSize = 13.5.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Watch downloaded Movies & Web Series offline",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onOpenOfflineDownloads,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Open", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            }
        }

        // =========================================================================
        // 4. SLIM APP SETTINGS CARD
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "App Settings",
                fontSize = 13.5.sp,
                color = TextWhite,
                fontWeight = FontWeight.ExtraBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("App Theme", fontSize = 12.5.sp, color = TextWhite)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onSelectTheme(ThemeVariant.DEEP_BLACK) }
                    ) {
                        RadioButton(
                            selected = preferences.themeVariant == ThemeVariant.DEEP_BLACK,
                            onClick = { onSelectTheme(ThemeVariant.DEEP_BLACK) },
                            colors = RadioButtonDefaults.colors(selectedColor = CrimsonRed),
                            modifier = Modifier.size(28.dp)
                        )
                        Text("Dark", fontSize = 12.sp, color = TextWhite)
                    }
                    Spacer(Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onSelectTheme(ThemeVariant.DEEP_RED) }
                    ) {
                        RadioButton(
                            selected = preferences.themeVariant == ThemeVariant.DEEP_RED,
                            onClick = { onSelectTheme(ThemeVariant.DEEP_RED) },
                            colors = RadioButtonDefaults.colors(selectedColor = CrimsonRed),
                            modifier = Modifier.size(28.dp)
                        )
                        Text("Red", fontSize = 12.sp, color = TextWhite)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preferred Quality", fontSize = 12.5.sp, color = TextWhite)
                    Text(
                        "Default stream resolution",
                        fontSize = 10.5.sp,
                        color = TextSecondary
                    )
                }
                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(7.dp))
                            .clickable { qualityMenu = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(preferences.defaultQuality, fontSize = 11.5.sp, color = TextWhite, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(
                        expanded = qualityMenu,
                        onDismissRequest = { qualityMenu = false }
                    ) {
                        qualities.forEach { q ->
                            DropdownMenuItem(
                                text = { Text(q, fontSize = 12.5.sp) },
                                onClick = {
                                    onSelectQuality(q)
                                    qualityMenu = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Auto Play Next Episode", fontSize = 12.5.sp, color = TextWhite)
                Switch(
                    checked = preferences.autoPlayNextEpisode,
                    onCheckedChange = onToggleAutoPlay,
                    modifier = Modifier.scale(0.82f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CrimsonRed
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Push Notifications", fontSize = 12.5.sp, color = TextWhite)
                Switch(
                    checked = preferences.notificationsEnabled,
                    onCheckedChange = onToggleNotifications,
                    modifier = Modifier.scale(0.82f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CrimsonRed
                    )
                )
            }
        }

        // =========================================================================
        // 5. SLIM ABOUT & DEVELOPER CARD
        // =========================================================================
        val nativeDevInfo = remember(context) {
            NhNativeSecurityVault.getDeveloperInfo(context)
        }
        val openTelegramLink: () -> Unit = {
            if (nativeDevInfo.telegramUrl.isNotBlank()) {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(nativeDevInfo.telegramUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "About & Developer",
                    fontSize = 13.5.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = nativeDevInfo.developerName,
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, Color(0xFF29B6F6).copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                    .clickable(onClick = openTelegramLink)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF29B6F6).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Telegram",
                            tint = Color(0xFF29B6F6),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Official Telegram Channel",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Text(
                            text = nativeDevInfo.telegramUrl,
                            color = Color(0xFF29B6F6),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(
                    onClick = openTelegramLink,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        text = "Join",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(13.dp))
                Text(
                    text = stringResource(R.string.tmdb_attribution),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

// =============================================================================
// UPCOMING MOVIES & WEB SERIES POSTER GRID SCREEN (Embedded inside UserSettingsScreen.kt)
// =============================================================================
@Composable
private fun UpcomingMoviesAndSeriesPosterScreen(
    upcomingItems: List<CatalogItem>,
    allPublishedItems: List<CatalogItem>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectPoster: (CatalogItem) -> Unit
) {
    BackHandler { onBack() }

    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        onRefresh()
        while (true) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    // Combine TMDB upcoming items with any catalog items marked Upcoming or with future release dates
    val combinedList = remember(upcomingItems, allPublishedItems, selectedFilter) {
        val catalogUpcoming = allPublishedItems.filter { item ->
            item.status.equals("Upcoming", ignoreCase = true) ||
                item.releaseDate.isNotBlank()
        }
        val merged = (upcomingItems + catalogUpcoming)
            .distinctBy { "${it.mediaType}_${it.tmdbId ?: it.title.lowercase()}" }

        when (selectedFilter) {
            "Movies" -> merged.filter { !it.isSeries }
            "Web Series" -> merged.filter { it.isSeries }
            else -> merged
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite
                    )
                }
                Column {
                    Text(
                        text = "Upcoming Movies & Web Series",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Tap any poster for live release countdown or to Play & Download",
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = StatusAmber
                )
            }
        }

        // Filter Pills: All | Movies | Web Series (Horizontally scrollable so pills never squish)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("All", "Movies", "Web Series").forEach { tab ->
                    FilterPill(
                        label = tab,
                        selected = selectedFilter == tab,
                        onClick = { selectedFilter = tab }
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${combinedList.size} titles",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        if (combinedList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = StatusAmber,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = "Loading Upcoming Movies & Web Series...",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Tap refresh to fetch the latest upcoming posters from TMDB.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = combinedList,
                    key = { "${it.id}_${it.tmdbId ?: it.title}" }
                ) { item ->
                    val matchedPlayable = allPublishedItems.firstOrNull { pub ->
                        (pub.tmdbId != null && pub.tmdbId == item.tmdbId && pub.mediaType == item.mediaType) ||
                            (pub.title.equals(item.title, ignoreCase = true) && pub.mediaType == item.mediaType)
                    }
                    val hasPlayableStream = matchedPlayable != null && (
                        UrlValidator.validateStreamUrl(matchedPlayable.primaryStreamUrl).isValid ||
                            matchedPlayable.seasons.flatMap { s -> s.episodes }
                                .any { ep -> UrlValidator.validateStreamUrl(ep.primaryStreamUrl).isValid }
                        )

                    UpcomingPosterGridCard(
                        item = matchedPlayable ?: item,
                        releaseDateOverride = item.releaseDate.ifBlank { matchedPlayable?.releaseDate.orEmpty() },
                        isPlayableNow = hasPlayableStream,
                        nowMillis = nowMillis,
                        onClick = { onSelectPoster(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingPosterGridCard(
    item: CatalogItem,
    releaseDateOverride: String,
    isPlayableNow: Boolean,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val effectiveDate = releaseDateOverride.ifBlank { item.releaseDate }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isPlayableNow) StatusGreen.copy(alpha = 0.55f) else StatusAmber.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        ) {
            PosterImageWithFallback(
                imageUrl = item.posterUrl.ifBlank { item.backdropUrl },
                title = item.title,
                modifier = Modifier.fillMaxSize()
            )

            // Top-left Movie / Series pill
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
            ) {
                Text(
                    text = if (item.isSeries) "SERIES" else "MOVIE",
                    color = if (item.isSeries) StatusBlue else CrimsonLight,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            // Bottom status overlay: Playable vs Release Date
            Surface(
                color = if (isPlayableNow) StatusGreen.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.84f),
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isPlayableNow) Icons.Filled.PlayCircle else Icons.Filled.Timer,
                        contentDescription = null,
                        tint = if (isPlayableNow) Color.Black else StatusAmber,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (isPlayableNow) {
                            "AVAILABLE NOW"
                        } else {
                            effectiveDate.ifBlank { "COMING SOON" }
                        },
                        color = if (isPlayableNow) Color.Black else StatusAmber,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.title,
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isPlayableNow) "Tap to Play / Download" else "Tap for Countdown",
                color = if (isPlayableNow) StatusGreen else TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
