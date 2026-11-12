package com.moviebox.app.ui.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.BuildConfig
import com.moviebox.app.security.NhNativeSecurityVault
import com.moviebox.app.data.model.AppPreferences
import com.moviebox.app.data.model.ThemeVariant
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextMuted
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminSettingsScreen(
    preferences: AppPreferences,
    cacheSizeBytes: Long,
    isFirebaseConnected: Boolean,
    isTmdbConfigured: Boolean,
    adminEmail: String?,
    onSetThemeVariant: (ThemeVariant) -> Unit,
    onSetDefaultQuality: (String) -> Unit,
    onSetAutoPlayNext: (Boolean) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onSetTmdbApiKey: (String) -> Unit,
    onClearCache: () -> Unit,
    onSignOut: () -> Unit
) {
    val qualityOptions = listOf("Auto", "HD (1080p)", "HD (720p)", "Data Saver (480p)")
    var qualityExpanded by remember { mutableStateOf(false) }
    var tmdbKeyInput by remember(preferences.tmdbApiKeyOverride) {
        mutableStateOf(preferences.tmdbApiKeyOverride.ifBlank { BuildConfig.TMDB_API_KEY })
    }
    val cacheSizeFormatted = remember(cacheSizeBytes) {
        String.format(Locale.US, "%.1f MB", cacheSizeBytes / (1024.0 * 1024.0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            color = TextWhite,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
        )

        // App Settings Card (Screen 13)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "App Settings",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                // App Theme: Dark vs Red radio pills (Screen 13)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("App Theme", color = TextSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ThemeTogglePill(
                            label = "Dark",
                            selected = preferences.themeVariant == ThemeVariant.DEEP_BLACK,
                            onClick = { onSetThemeVariant(ThemeVariant.DEEP_BLACK) }
                        )
                        ThemeTogglePill(
                            label = "Red",
                            selected = preferences.themeVariant == ThemeVariant.DEEP_RED,
                            onClick = { onSetThemeVariant(ThemeVariant.DEEP_RED) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Default Video Quality Dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Video Quality", color = TextSecondary, fontSize = 13.sp)
                        Text(
                            text = "Applies when adaptive HLS/DASH source provides multiple bitrates",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    ExposedDropdownMenuBox(
                        expanded = qualityExpanded,
                        onExpandedChange = { qualityExpanded = !qualityExpanded }
                    ) {
                        OutlinedTextField(
                            value = preferences.defaultQuality,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .width(170.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        ExposedDropdownMenu(
                            expanded = qualityExpanded,
                            onDismissRequest = { qualityExpanded = false }
                        ) {
                            qualityOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, fontSize = 13.sp) },
                                    onClick = {
                                        onSetDefaultQuality(option)
                                        qualityExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Auto Play Next Episode Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Play Next Episode", color = TextSecondary, fontSize = 13.sp)
                    Switch(
                        checked = preferences.autoPlayNextEpisode,
                        onCheckedChange = onSetAutoPlayNext,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CrimsonRed
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Notifications Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notifications", color = TextSecondary, fontSize = 13.sp)
                    Switch(
                        checked = preferences.notificationsEnabled,
                        onCheckedChange = onSetNotifications,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CrimsonRed
                        )
                    )
                }
            }
        }

        // Diagnostics & TMDB Configuration Card
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Backend & TMDB Diagnostics",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Firebase Realtime Database", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        text = if (isFirebaseConnected) "Connected (Live)" else "Offline / Local Cache",
                        color = if (isFirebaseConnected) StatusGreen else StarGold,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TMDB API Status", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        text = if (isTmdbConfigured) "Configured" else "Not Configured",
                        color = if (isTmdbConfigured) StatusGreen else StatusAmber,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tmdbKeyInput,
                        onValueChange = { tmdbKeyInput = it },
                        label = { Text("TMDB v3 API Key Override (Stored in DataStore)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onSetTmdbApiKey(tmdbKeyInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Apply", color = Color.White)
                    }
                }
            }
        }

        // About & Clear Cache Card (Screen 13 bottom card)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "About",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )

                val context = LocalContext.current
                val nativeDevInfo = remember(context) {
                    NhNativeSecurityVault.getDeveloperInfo(context)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Version", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        text = "${BuildConfig.VERSION_NAME} ✓",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "About Developer",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = nativeDevInfo.developerName,
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (nativeDevInfo.telegramUrl.isNotBlank()) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(nativeDevInfo.telegramUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Telegram Channel", color = TextSecondary, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Telegram",
                            tint = Color(0xFF29B6F6),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = nativeDevInfo.telegramUrl,
                            color = Color(0xFF29B6F6),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Clear Cache", color = TextSecondary, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(cacheSizeFormatted, color = TextMuted, fontSize = 12.sp)
                        Button(
                            onClick = onClearCache,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Clear", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                if (!adminEmail.isNullOrBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Signed in as $adminEmail", color = TextSecondary, fontSize = 12.sp)
                        OutlinedButton(onClick = onSignOut) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Logout", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeTogglePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) CrimsonRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .border(
                width = 1.dp,
                color = if (selected) CrimsonRed else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.size(16.dp),
                colors = RadioButtonDefaults.colors(selectedColor = CrimsonRed)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
