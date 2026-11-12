package com.moviebox.app.ui.admin

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.UserPresenceRecord
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.StatusRed
import com.moviebox.app.ui.theme.TextMuted
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import com.moviebox.app.util.FcmPushSender
import com.moviebox.app.util.ServiceAccountCredentials
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun AdminPushNotificationScreen(
    targetItem: CatalogItem?,
    enrichedItem: CatalogItem?,
    allCatalogItems: List<CatalogItem>,
    users: List<UserPresenceRecord>,
    serviceAccountCreds: ServiceAccountCredentials?,
    serviceAccountFileName: String,
    isSending: Boolean,
    onSelectTargetItem: (CatalogItem) -> Unit,
    onRequestTmdbBackdrops: (CatalogItem) -> Unit,
    onServiceAccountLoaded: (ServiceAccountCredentials, String) -> Unit,
    onFetchAllUsers: () -> Unit,
    onSendPushNotification: (
        creds: ServiceAccountCredentials,
        item: CatalogItem?,
        title: String,
        body: String,
        imageUrl: String
    ) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    val activeItem = remember(targetItem, enrichedItem, allCatalogItems) {
        val base = targetItem ?: allCatalogItems.firstOrNull()
        if (base != null && enrichedItem != null && enrichedItem.id == base.id) {
            base.copy(
                backdropGallery = if (enrichedItem.backdropGallery.isNotEmpty()) enrichedItem.backdropGallery else base.backdropGallery,
                posterGallery = if (enrichedItem.posterGallery.isNotEmpty()) enrichedItem.posterGallery else base.posterGallery
            )
        } else base
    }

    // Automatically fetch all TMDB backdrop images for the selected movie / web series
    LaunchedEffect(activeItem?.id, activeItem?.tmdbId) {
        if (activeItem != null) {
            onRequestTmdbBackdrops(activeItem)
        }
    }

    val allBackdrops = remember(activeItem, enrichedItem) {
        if (activeItem == null) emptyList()
        else {
            val primaryBackdrop = activeItem.backdropUrl.ifBlank { activeItem.posterUrl }
            val enrichedBackdrops = if (enrichedItem?.id == activeItem.id) enrichedItem.backdropGallery else emptyList()
            (listOfNotNull(primaryBackdrop.takeIf { it.isNotBlank() }) +
                enrichedBackdrops +
                activeItem.backdropGallery +
                listOfNotNull(activeItem.posterUrl.takeIf { it.isNotBlank() }))
                .distinct()
        }
    }

    var notifTitle by remember(activeItem?.id) {
        mutableStateOf(
            if (activeItem != null) {
                "New on MovieBox: ${activeItem.title} (${activeItem.releaseYear})"
            } else {
                "New Release Streaming Now on MovieBox!"
            }
        )
    }

    var notifBody by remember(activeItem?.id) {
        mutableStateOf(
            if (activeItem != null) {
                activeItem.overview.ifBlank {
                    "Watch ${activeItem.title} (★ ${String.format(Locale.US, "%.1f", activeItem.rating)}) in Full HD now on MovieBox!"
                }
            } else {
                "Open MovieBox to watch the latest movies and web series in Full HD!"
            }
        )
    }

    var selectedImageUrl by remember(activeItem?.id, allBackdrops.size) {
        mutableStateOf(allBackdrops.firstOrNull().orEmpty())
    }

    var jsonValidationError by remember { mutableStateOf<String?>(null) }
    var itemDropdownExpanded by remember { mutableStateOf(false) }

    // File Picker for Google Service Account JSON (Never saved inside project source!)
    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val rawJson = context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader().readText()
                    } ?: error("Could not read selected file.")
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "service-account.json"
                    val validation = FcmPushSender.parseAndValidateServiceAccountJson(rawJson)
                    validation.onSuccess { creds ->
                        jsonValidationError = null
                        onServiceAccountLoaded(creds, fileName)
                        onFetchAllUsers()
                    }.onFailure { err ->
                        jsonValidationError = err.message ?: "Invalid Google Service Account JSON file."
                    }
                }.onFailure { err ->
                    jsonValidationError = err.message ?: "Failed to read JSON file."
                }
            }
        }
    }

    val isJsonValid = serviceAccountCreds != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Page Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Send Push Notification",
                    color = TextWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Step 1: Upload Service Account JSON • Step 2: Select Backdrop & Send",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // =========================================================================
        // STEP 1: UPLOAD GOOGLE SERVICE ACCOUNT JSON FILE (Mobile-Perfect Card)
        // =========================================================================
        Surface(
            color = if (isJsonValid) StatusGreen.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = 1.5.dp,
                color = when {
                    isJsonValid -> StatusGreen
                    jsonValidationError != null -> StatusRed
                    else -> MaterialTheme.colorScheme.outline
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Row: Icon + Status Heading + Upload/Change Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isJsonValid) StatusGreen.copy(alpha = 0.22f)
                                else CrimsonRed.copy(alpha = 0.18f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isJsonValid) Icons.Filled.CheckCircle else Icons.Filled.UploadFile,
                            contentDescription = null,
                            tint = if (isJsonValid) StatusGreen else CrimsonRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isJsonValid) {
                                "✓ Valid Service Account Active"
                            } else {
                                "1. Upload Service Account JSON"
                            },
                            color = if (isJsonValid) StatusGreen else TextWhite,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isJsonValid) {
                                "Project: ${serviceAccountCreds?.projectId}"
                            } else {
                                "Authenticates FCM HTTP v1 API (Never saved in project)"
                            },
                            color = if (isJsonValid) StatusGreen.copy(alpha = 0.9f) else TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { jsonPickerLauncher.launch(arrayOf("application/json", "*/*")) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isJsonValid) StatusGreen else CrimsonRed
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = null,
                            tint = if (isJsonValid) Color.Black else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (isJsonValid) "Change JSON" else "Upload JSON",
                            color = if (isJsonValid) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Full-width File Name Pill (Prevents narrow multi-line wrapping!)
                if (isJsonValid && serviceAccountFileName.isNotBlank()) {
                    Surface(
                        color = StatusGreen.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "File: $serviceAccountFileName",
                                color = StatusGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                jsonValidationError?.let { err ->
                    Text(
                        text = err,
                        color = StatusRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isJsonValid) {
                    HorizontalDivider(color = StatusGreen.copy(alpha = 0.28f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.People,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Audience: All Users (moviebox_all) + ${users.size} Registered",
                                color = TextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(
                            onClick = onFetchAllUsers,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = "Refresh",
                                color = StatusGreen,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // STEP 2: PUSH NOTIFICATION FORM & TMDB BACKDROP IMAGE SELECTOR
        // =========================================================================
        if (!isJsonValid) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = StatusAmber,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Upload Valid Service Account JSON Above to Open Send Form",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Once your Firebase Service Account JSON is validated (turns green), all users and the notification composer with TMDB backdrop images will open automatically.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Target Movie / Web Series Picker
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Selected Movie / Web Series",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                            Text(
                                text = activeItem?.let {
                                    "${it.title} (${if (it.isSeries) "Web Series" else "Movie"} • ${it.releaseYear})"
                                } ?: "Custom Notification",
                                color = TextWhite,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (allCatalogItems.size > 1) {
                            Box {
                                OutlinedButton(
                                    onClick = { itemDropdownExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = "Switch Title",
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                DropdownMenu(
                                    expanded = itemDropdownExpanded,
                                    onDismissRequest = { itemDropdownExpanded = false }
                                ) {
                                    allCatalogItems.take(25).forEach { catItem ->
                                        DropdownMenuItem(
                                            text = { Text("${catItem.title} (${catItem.releaseYear})") },
                                            onClick = {
                                                itemDropdownExpanded = false
                                                onSelectTargetItem(catItem)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Notification Title Input
                    OutlinedTextField(
                        value = notifTitle,
                        onValueChange = { notifTitle = it },
                        label = { Text("Notification Title", maxLines = 1) },
                        placeholder = { Text("e.g. New Movie Added: Deva (2025)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Notification Body Message Input
                    OutlinedTextField(
                        value = notifBody,
                        onValueChange = { notifBody = it },
                        label = { Text("Body Message", maxLines = 1) },
                        placeholder = { Text("Watch now in 1080p Full HD on MovieBox!") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // TMDB All Backdrop Images Selector (Stacked Header — Never squishes vertically!)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Select TMDB Backdrop Image",
                                color = TextWhite,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = CrimsonRed.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "${allBackdrops.size} Available",
                                    color = CrimsonLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "Tap any backdrop image below to select it for the push notification",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (allBackdrops.isEmpty()) {
                            Text(
                                text = "Fetching TMDB backdrop images...",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(allBackdrops, key = { it }) { imgUrl ->
                                    val isSelected = selectedImageUrl == imgUrl
                                    Box(
                                        modifier = Modifier
                                            .width(164.dp)
                                            .height(94.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) StatusGreen else MaterialTheme.colorScheme.outline,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { selectedImageUrl = imgUrl }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imgUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Backdrop option",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(StatusGreen),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = selectedImageUrl,
                            onValueChange = { selectedImageUrl = it },
                            label = { Text("Selected Notification Image URL", maxLines = 1) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Live Push Notification Preview Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Live Notification Preview",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = notifTitle.ifBlank { "MovieBox Notification" },
                                color = TextWhite,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = notifBody.ifBlank { "Tap to watch now on MovieBox" },
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (selectedImageUrl.isNotBlank()) {
                                PosterImageWithFallback(
                                    imageUrl = selectedImageUrl,
                                    title = notifTitle,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(145.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            }
                        }
                    }

                    // Send Push Notification Button
                    Button(
                        onClick = {
                            val creds = serviceAccountCreds ?: return@Button
                            onSendPushNotification(
                                creds,
                                activeItem,
                                notifTitle.trim(),
                                notifBody.trim(),
                                selectedImageUrl.trim()
                            )
                        },
                        enabled = !isSending && notifTitle.isNotBlank() && notifBody.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Sending Push Notification...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Send Push Notification to All Users",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
