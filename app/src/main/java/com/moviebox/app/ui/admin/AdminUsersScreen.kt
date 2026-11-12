package com.moviebox.app.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moviebox.app.data.model.UserPresenceRecord
import com.moviebox.app.data.model.VipAccessConfig
import com.moviebox.app.ui.components.StatusPillBadge
import com.moviebox.app.ui.theme.CrimsonDark
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.StatusBlue
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextMuted
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun AdminUsersScreen(
    users: List<UserPresenceRecord>,
    vipConfig: VipAccessConfig = VipAccessConfig(),
    onSaveVipConfig: (VipAccessConfig) -> Unit = {},
    onUpdateUser: (user: UserPresenceRecord, points: Int, shareCount: Int, isVip: Boolean) -> Unit = { _, _, _, _ -> }
) {
    val totalUsers = users.size
    val onlineCount = users.count { it.online }
    val offlineCount = (totalUsers - onlineCount).coerceAtLeast(0)
    val vipCount = users.count { it.hasGlobalVip(vipConfig.vipPointsToAccess) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<UserPresenceRecord?>(null) }
    var showVipSettingsExpanded by remember { mutableStateOf(true) }

    var vipPointsInput by remember(vipConfig.vipPointsToAccess) {
        mutableStateOf(vipConfig.vipPointsToAccess.toString())
    }
    var referralPointsInput by remember(vipConfig.pointsPerReferral) {
        mutableStateOf(vipConfig.pointsPerReferral.toString())
    }

    val filteredUsers = remember(users, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) users
        else users.filter { u ->
            u.deviceId.contains(q, ignoreCase = true) ||
                u.shareCode.contains(q, ignoreCase = true) ||
                u.uid.contains(q, ignoreCase = true) ||
                u.displayName.contains(q, ignoreCase = true) ||
                u.deviceModel.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "User Management & VIP Points",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Tap any user row to view ID, 5-digit Share Code, Points & Share Count",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showVipSettingsExpanded = !showVipSettingsExpanded },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = StarGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (showVipSettingsExpanded) "Hide VIP Setup" else "VIP Setup",
                        fontSize = 11.5.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // 2. Clean 2x2 KPI Summary Grid (Zero crowding on mobile screens)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UserStatCard(
                        title = "Total Users",
                        count = totalUsers.toString(),
                        icon = Icons.Filled.People,
                        accentColor = CrimsonRed,
                        modifier = Modifier.weight(1f)
                    )
                    UserStatCard(
                        title = "Online Now",
                        count = onlineCount.toString(),
                        icon = Icons.Filled.Wifi,
                        accentColor = StatusGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UserStatCard(
                        title = "Offline Users",
                        count = offlineCount.toString(),
                        icon = Icons.Filled.PersonOff,
                        accentColor = StatusAmber,
                        modifier = Modifier.weight(1f)
                    )
                    UserStatCard(
                        title = "VIP Unlocked",
                        count = vipCount.toString(),
                        icon = Icons.Filled.WorkspacePremium,
                        accentColor = StarGold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Set VIP Movie & Web Series Points Configuration Card (Clean 2-Column Layout)
        if (showVipSettingsExpanded) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StarGold.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(StarGold.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = StarGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Set VIP Movie & Web Series Points",
                                    color = TextWhite,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp
                                )
                                Text(
                                    text = "Points are added only when a user redeems a 5-digit Share Code",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        // 2-Column Inputs: VIP Points Required + Points Per Code Redeem
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = vipPointsInput,
                                onValueChange = { vipPointsInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("VIP Points to Access", maxLines = 1) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = referralPointsInput,
                                onValueChange = { referralPointsInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Pts / Code Redeem", maxLines = 1) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Quick Preset Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(25, 50, 100, 200).forEach { preset ->
                                OutlinedButton(
                                    onClick = { vipPointsInput = preset.toString() },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${preset} Pts", fontSize = 11.5.sp, maxLines = 1)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val nextCfg = vipConfig.copy(
                                    vipPointsToAccess = (vipPointsInput.toIntOrNull() ?: 100).coerceAtLeast(1),
                                    pointsPerReferral = (referralPointsInput.toIntOrNull() ?: 50).coerceAtLeast(1),
                                    updatedAt = System.currentTimeMillis()
                                )
                                onSaveVipConfig(nextCfg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Save VIP Point Settings",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search user by Device ID, 5-digit Share Code, or Model...", fontSize = 12.5.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 5. Users List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registered Devices (${filteredUsers.size})",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )
                Text(
                    text = "Tap user for full details",
                    color = CrimsonLight,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 6. Users Items
        if (filteredUsers.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.People,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = if (users.isEmpty()) "No registered device records in /users yet" else "No matching user found",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Every device automatically registers its real Device ID, 5-digit Share Code, Points & Share Count in Firebase Realtime Database.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            itemsIndexed(filteredUsers, key = { _, u -> u.deviceId.ifBlank { u.uid } }) { index, user ->
                val isUserVip = user.hasGlobalVip(vipConfig.vipPointsToAccess)
                val resolvedCode = user.shareCode.ifBlank {
                    UserPresenceRecord.generateFiveDigitShareCode(user.deviceId.ifBlank { user.uid })
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = 1.dp,
                            color = if (isUserVip) StarGold.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedUser = user }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top Row: Index + Avatar + Device Model + Online Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isUserVip) StarGold.copy(alpha = 0.22f) else CrimsonDark)
                                    .border(1.dp, if (isUserVip) StarGold else CrimsonRed, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isUserVip) StarGold else TextWhite,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = user.deviceModel.ifBlank { user.displayName },
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isUserVip) {
                                        Surface(
                                            color = StarGold.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, StarGold.copy(alpha = 0.6f))
                                        ) {
                                            Text(
                                                text = "VIP",
                                                color = StarGold,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Device ID: ${user.deviceId.ifBlank { user.uid }}",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                StatusPillBadge(
                                    active = user.online,
                                    activeLabel = "Online",
                                    inactiveLabel = "Offline"
                                )
                                Text(
                                    text = formatRelativeLastSeen(user.lastSeen),
                                    color = TextSecondary,
                                    fontSize = 10.5.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Bottom Row: 3 Equal Metric Pills (5-Digit Code | Points | Share Counting)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = StatusBlue.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Code: $resolvedCode",
                                    color = StatusBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                color = StarGold.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "★ ${user.points} Pts",
                                    color = StarGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                color = CrimsonRed.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Shares: ${user.shareCount}",
                                    color = CrimsonLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Full User Details Dialog when Admin clicks any User Row
    selectedUser?.let { target ->
        val liveTarget = users.find {
            it.uid == target.uid || (target.deviceId.isNotBlank() && it.deviceId == target.deviceId)
        } ?: target

        AdminUserDetailDialog(
            user = liveTarget,
            vipPointsThreshold = vipConfig.vipPointsToAccess,
            onDismiss = { selectedUser = null },
            onSaveUser = { newPts, newShares, newVip ->
                onUpdateUser(liveTarget, newPts, newShares, newVip)
                selectedUser = null
            }
        )
    }
}

@Composable
private fun AdminUserDetailDialog(
    user: UserPresenceRecord,
    vipPointsThreshold: Int,
    onDismiss: () -> Unit,
    onSaveUser: (points: Int, shareCount: Int, isVip: Boolean) -> Unit
) {
    val context = LocalContext.current
    val resolvedCode = user.shareCode.ifBlank {
        UserPresenceRecord.generateFiveDigitShareCode(user.deviceId.ifBlank { user.uid })
    }
    var pointsInput by remember(user.points) { mutableStateOf(user.points.toString()) }
    var shareCountInput by remember(user.shareCount) { mutableStateOf(user.shareCount.toString()) }
    var directVipSwitch by remember(user.isVip) { mutableStateOf(user.isVip) }

    val currentPtsVal = pointsInput.toIntOrNull() ?: user.points
    val currentSharesVal = shareCountInput.toIntOrNull() ?: user.shareCount
    val hasEffectiveVip = directVipSwitch || currentPtsVal >= vipPointsThreshold

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
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
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (hasEffectiveVip) StarGold.copy(alpha = 0.22f) else CrimsonDark)
                                .border(1.5.dp, if (hasEffectiveVip) StarGold else CrimsonRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (hasEffectiveVip) Icons.Filled.WorkspacePremium else Icons.Filled.Person,
                                contentDescription = null,
                                tint = if (hasEffectiveVip) StarGold else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "User Full Details",
                                color = TextWhite,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = user.deviceModel.ifBlank { user.displayName },
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // 3 Highlight KPI Boxes: Points | 5-Digit Share Code | Share Counting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailMetricBox(
                        label = "Points",
                        value = "$currentPtsVal Pts",
                        accent = StarGold,
                        modifier = Modifier.weight(1f)
                    )
                    DetailMetricBox(
                        label = "5-Digit Code",
                        value = resolvedCode,
                        accent = StatusBlue,
                        modifier = Modifier.weight(1f)
                    )
                    DetailMetricBox(
                        label = "Share Count",
                        value = "$currentSharesVal",
                        accent = CrimsonLight,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Detailed Information Table
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CopyableDetailRow(
                            label = "Real Device ID",
                            value = user.deviceId.ifBlank { "—" },
                            context = context
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        CopyableDetailRow(
                            label = "Firebase User UID",
                            value = user.uid.ifBlank { "—" },
                            context = context
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        CopyableDetailRow(
                            label = "5-Digit Share Code",
                            value = resolvedCode,
                            context = context
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Share Counting (Code Redeems)", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = "$currentSharesVal Share(s)",
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("VIP Access Status", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = if (hasEffectiveVip) "👑 VIP ACTIVE"
                                else "FREE (${currentPtsVal}/${vipPointsThreshold} Pts)",
                                color = if (hasEffectiveVip) StarGold else TextWhite,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Redeemed Code(s)", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = user.redeemedCodes.joinToString(", ").ifBlank {
                                    user.redeemedCode.ifBlank { "None" }
                                },
                                color = StatusGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("App Version & Status", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = "v${user.appVersion.ifBlank { "18.0.0" }} • ${if (user.online) "Online Now" else formatRelativeLastSeen(user.lastSeen)}",
                                color = if (user.online) StatusGreen else TextWhite,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Admin Edit Points, Share Count & VIP Status Section
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Manage User Points, Share Count & VIP Access",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = pointsInput,
                                onValueChange = { pointsInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("User Points", maxLines = 1) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = shareCountInput,
                                onValueChange = { shareCountInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Share Count", maxLines = 1) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pointsInput = ((pointsInput.toIntOrNull() ?: 0) + 25).toString() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+25", fontSize = 11.5.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { pointsInput = ((pointsInput.toIntOrNull() ?: 0) + 50).toString() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+50", fontSize = 11.5.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { pointsInput = ((pointsInput.toIntOrNull() ?: 0) + 100).toString() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+100", fontSize = 11.5.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { pointsInput = "0" },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset", fontSize = 11.5.sp, maxLines = 1)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Direct VIP Access Override",
                                    color = TextWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Unlock all VIP Movies & Web Series for this user",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = directVipSwitch,
                                onCheckedChange = { directVipSwitch = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = StarGold
                                )
                            )
                        }
                    }
                }

                // Dialog Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Close", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            onSaveUser(
                                (pointsInput.toIntOrNull() ?: user.points).coerceAtLeast(0),
                                (shareCountInput.toIntOrNull() ?: user.shareCount).coerceAtLeast(0),
                                directVipSwitch
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(46.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetricBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = value,
                color = accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                maxLines = 1
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CopyableDetailRow(
    label: String,
    value: String,
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = TextSecondary, fontSize = 11.5.sp)
            Text(
                text = value,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "$label copied: $value", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy $label",
                tint = CrimsonLight,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun UserStatCard(
    title: String,
    count: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = count,
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    maxLines = 1
                )
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatRelativeLastSeen(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Unknown"
    val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestampMs))
    }
}
