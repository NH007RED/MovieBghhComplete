package com.moviebox.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.BuildConfig
import com.moviebox.app.data.model.AppUpdateConfig
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.StatusRed
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import com.moviebox.app.util.UrlValidator

@Composable
fun AdminAppUpdateScreen(
    currentConfig: AppUpdateConfig?,
    onSaveAndSendUpdate: (AppUpdateConfig) -> Unit
) {
    var versionName by remember(currentConfig) {
        mutableStateOf(currentConfig?.versionName ?: "2.0.0")
    }
    var versionCodeText by remember(currentConfig) {
        mutableStateOf((currentConfig?.versionCode ?: (BuildConfig.VERSION_CODE + 1)).toString())
    }
    var updateFeatures by remember(currentConfig) {
        mutableStateOf(
            currentConfig?.updateFeatures?.ifBlank {
                "• Faster Native ExoPlayer with 1080p/720p/480p switching\n• New Movies & Web Series Added\n• Improved UI & Performance"
            } ?: "• Faster Native ExoPlayer with 1080p/720p/480p switching\n• New Movies & Web Series Added\n• Improved UI & Performance"
        )
    }
    var apkUrl by remember(currentConfig) {
        mutableStateOf(currentConfig?.apkUrl ?: "")
    }
    var forceUpdate by remember(currentConfig) {
        mutableStateOf(currentConfig?.forceUpdate ?: false)
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
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = CrimsonRed)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "In-App Update Manager",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Configure application version, release notes, and direct APK download link for all users",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Current Live Update Status Card
        if (currentConfig != null && currentConfig.apkUrl.isNotBlank()) {
            Surface(
                color = StatusGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Live Published Update: v${currentConfig.versionName} (Code ${currentConfig.versionCode})",
                        color = StatusGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = "APK Link: ${currentConfig.apkUrl}",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )
                }
            }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = versionName,
                        onValueChange = { versionName = it },
                        label = { Text("App Version Name", maxLines = 1) },
                        placeholder = { Text("e.g. 2.0.0") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = versionCodeText,
                        onValueChange = { versionCodeText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Version Code", maxLines = 1) },
                        placeholder = { Text("e.g. 2") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = updateFeatures,
                    onValueChange = { updateFeatures = it },
                    label = { Text("Update Features / What's New", maxLines = 1) },
                    placeholder = { Text("• Added new features\n• Fixed bugs") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apkUrl,
                    onValueChange = { apkUrl = it },
                    label = { Text("APK Direct Download Link (HTTPS .apk)", maxLines = 1) },
                    placeholder = { Text("https://example.com/MovieBox_v2.0.apk") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mandatory / Force Update", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "If enabled, hides the 'Later' button so users must update to continue",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                    Switch(
                        checked = forceUpdate,
                        onCheckedChange = { forceUpdate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CrimsonRed
                        )
                    )
                }

                validationError?.let { err ->
                    Text(text = err, color = StatusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (versionName.isBlank()) {
                            validationError = "Application version name is required."
                            return@Button
                        }
                        if (apkUrl.isBlank() || !UrlValidator.validateTrailerUrl(apkUrl)) {
                            validationError = "Please enter a valid HTTP/HTTPS direct APK download link."
                            return@Button
                        }
                        validationError = null
                        val vCode = versionCodeText.toIntOrNull() ?: (BuildConfig.VERSION_CODE + 1)
                        onSaveAndSendUpdate(
                            AppUpdateConfig(
                                versionName = versionName.trim(),
                                versionCode = vCode,
                                updateFeatures = updateFeatures.trim(),
                                apkUrl = apkUrl.trim(),
                                forceUpdate = forceUpdate,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Set & Send Update to All Users",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.5.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
