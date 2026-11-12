package com.moviebox.app.ui.user

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.moviebox.app.data.model.AppUpdateConfig
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.StatusRed
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Composable
fun InAppUpdateDialog(
    updateConfig: AppUpdateConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var downloadedApkFile by remember { mutableStateOf<File?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = {
            if (!updateConfig.forceUpdate && !isDownloading) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateConfig.forceUpdate && !isDownloading,
            dismissOnClickOutside = !updateConfig.forceUpdate && !isDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color(0xFF141822),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (downloadedApkFile != null) StatusGreen.copy(alpha = 0.2f)
                                    else CrimsonRed.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (downloadedApkFile != null) Icons.Filled.CheckCircle else Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                tint = if (downloadedApkFile != null) StatusGreen else CrimsonRed,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (downloadedApkFile != null) {
                                    "Update Ready to Install"
                                } else {
                                    "New Update Available!"
                                },
                                color = TextWhite,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "MovieBox v${updateConfig.versionName} (Build ${updateConfig.versionCode})",
                                color = CrimsonLight,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // What's New / Update Features Box
                    if (updateConfig.updateFeatures.isNotBlank()) {
                        Surface(
                            color = Color(0xFF1C2230),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "What's New:",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = updateConfig.updateFeatures,
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    // Live Download Progress Section
                    if (isDownloading || downloadedApkFile != null) {
                        val pct = (downloadProgress * 100).toInt().coerceIn(0, 100)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (downloadedApkFile != null) "Download Complete" else "Downloading APK...",
                                    color = TextWhite,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "$pct%",
                                    color = if (downloadedApkFile != null) StatusGreen else CrimsonLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                color = if (downloadedApkFile != null) StatusGreen else CrimsonRed,
                                trackColor = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            if (totalBytes > 0L) {
                                val dlMb = String.format(Locale.US, "%.1f MB", downloadedBytes / (1024.0 * 1024.0))
                                val totMb = String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0))
                                Text(
                                    text = "$dlMb / $totMb",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    downloadError?.let { err ->
                        Text(
                            text = err,
                            color = StatusRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Action Buttons: [Later] + [Update Now / Downloading... / Install]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!updateConfig.forceUpdate && !isDownloading) {
                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Later", color = TextSecondary, maxLines = 1)
                            }
                        }

                        if (downloadedApkFile != null) {
                            // Install Button (Triggered when download completes!)
                            Button(
                                onClick = {
                                    installDownloadedApk(context, downloadedApkFile!!, updateConfig.apkUrl)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "Install",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                            }
                        } else {
                            // Download / Update Now Button
                            Button(
                                onClick = {
                                    isDownloading = true
                                    downloadError = null
                                    downloadProgress = 0f
                                    scope.launch {
                                        val res = downloadApkWithProgress(
                                            context = context,
                                            apkUrl = updateConfig.apkUrl,
                                            versionName = updateConfig.versionName
                                        ) { read, total ->
                                            downloadedBytes = read
                                            totalBytes = total
                                            downloadProgress = if (total > 0L) {
                                                (read.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                            } else 0.5f
                                        }
                                        isDownloading = false
                                        res.onSuccess { file ->
                                            downloadProgress = 1f
                                            downloadedApkFile = file
                                        }.onFailure { e ->
                                            downloadError = "Download failed: ${e.message ?: "Check APK URL"}"
                                        }
                                    }
                                },
                                enabled = !isDownloading,
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = if (isDownloading) "Downloading..." else "Update Now",
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
        }
    }
}

private suspend fun downloadApkWithProgress(
    context: Context,
    apkUrl: String,
    versionName: String,
    onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val req = Request.Builder().url(apkUrl.trim()).build()
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!dir.exists()) dir.mkdirs()
        val safeVer = versionName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val outFile = File(dir, "MovieBox_Update_$safeVer.apk")
        if (outFile.exists()) outFile.delete()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                error("HTTP ${resp.code} while downloading APK")
            }
            val body = resp.body ?: error("Empty APK response body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var readSoFar = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        readSoFar += read
                        onProgress(readSoFar, total)
                    }
                    output.flush()
                }
            }
        }
        outFile
    }
}

private fun installDownloadedApk(context: Context, apkFile: File, fallbackApkUrl: String) {
    runCatching {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }.onFailure {
        runCatching {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackApkUrl.trim())).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }.onFailure { e ->
            Toast.makeText(
                context,
                "Could not launch installer: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
