package com.moviebox.app.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moviebox.app.data.model.*
import com.moviebox.app.data.repository.AdminAuthState
import com.moviebox.app.data.repository.CatalogState
import com.moviebox.app.ui.MovieBoxViewModel
import com.moviebox.app.ui.components.FilterPill
import com.moviebox.app.ui.components.MovieBoxLogo
import com.moviebox.app.ui.components.PosterImageWithFallback
import com.moviebox.app.ui.theme.CrimsonLight
import com.moviebox.app.ui.theme.CrimsonRed
import com.moviebox.app.ui.theme.StarGold
import com.moviebox.app.ui.theme.StatusAmber
import com.moviebox.app.ui.theme.StatusBlue
import com.moviebox.app.ui.theme.StatusGreen
import com.moviebox.app.ui.theme.TextSecondary
import com.moviebox.app.ui.theme.TextWhite
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AdminSection(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard),
    CATEGORIES("Categories", Icons.Filled.Category),
    MOVIES("Movies", Icons.Filled.Movie),
    WEB_SERIES("Web Series", Icons.Filled.LiveTv),
    TMDB_IMPORT("Import TMDB", Icons.Filled.CloudDownload),
    MOVIE_REQUESTS("Request Movie", Icons.Filled.PlaylistAddCheck),
    PUSH_NOTIFICATION("Push Notification", Icons.Filled.NotificationsActive),
    APP_UPDATE("In-App Update", Icons.Filled.SystemUpdate),
    USER_MANAGEMENT("User Management", Icons.Filled.People),
    SETTINGS("Settings", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminShellScreen(
    vm: MovieBoxViewModel,
    catalogState: CatalogState,
    authState: AdminAuthState,
    preferences: AppPreferences,
    cacheSizeBytes: Long,
    onExitAdminToUserApp: () -> Unit
) {
    val tmdbState by vm.tmdbState.collectAsState()
    val enrichedItemMap by vm.enrichedItemMap.collectAsState()
    val serviceAccountCreds by vm.serviceAccountCreds.collectAsState()
    val serviceAccountFileName by vm.serviceAccountFileName.collectAsState()
    val isSendingPush by vm.isSendingPush.collectAsState()

    if (authState !is AdminAuthState.VerifiedAdmin) {
        AdminLoginScreen(
            authState = authState,
            onSignIn = vm::signInAdmin,
            onSignOut = vm::signOutAdmin,
            onBackToUserApp = onExitAdminToUserApp
        )
        return
    }

    var currentSection by rememberSaveable { mutableStateOf(AdminSection.DASHBOARD) }
    var pendingEditMovie by remember { mutableStateOf<CatalogItem?>(null) }
    var openMovieEditorOnLaunch by remember { mutableStateOf(false) }
    var pendingEditSeries by remember { mutableStateOf<CatalogItem?>(null) }
    var openSeriesEditorOnLaunch by remember { mutableStateOf(false) }
    var pushTargetItem by remember { mutableStateOf<CatalogItem?>(null) }

    val pendingRequestsCount = remember(catalogState.movieRequests) {
        catalogState.movieRequests.count { it.status.equals("PENDING", ignoreCase = true) }
    }

    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 720
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            currentSection != AdminSection.DASHBOARD -> currentSection = AdminSection.DASHBOARD
            else -> onExitAdminToUserApp()
        }
    }

    val sidebarContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MovieBoxLogo(subtitle = "Admin Panel")
            }

            Spacer(modifier = Modifier.height(2.dp))

            AdminSection.entries.forEach { item ->
                val selected = currentSection == item
                Surface(
                    color = if (selected) CrimsonRed else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable {
                            pendingEditMovie = null
                            openMovieEditorOnLaunch = false
                            pendingEditSeries = null
                            openSeriesEditorOnLaunch = false
                            currentSection = item
                            scope.launch { drawerState.close() }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 7.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = if (selected) Color.White else TextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = item.label,
                                color = if (selected) Color.White else TextSecondary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (item == AdminSection.MOVIE_REQUESTS && pendingRequestsCount > 0) {
                            Surface(
                                color = if (selected) Color.White else StatusAmber,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "$pendingRequestsCount",
                                    color = Color.Black,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { vm.signOutAdmin() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = "Logout",
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp
                    )
                }
            }

            OutlinedButton(
                onClick = onExitAdminToUserApp,
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.55f)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                Text(
                    text = "Switch to User App",
                    color = CrimsonLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bottom-left User Presence Summary Card (Fully visible, never clipped)
            val totalUsers = catalogState.users.size
            val onlineUsers = catalogState.users.count { it.online }
            val offlineUsers = (totalUsers - onlineUsers).coerceAtLeast(0)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    SidebarStatLine(label = "Total Users", value = totalUsers.toString(), dotColor = CrimsonRed)
                    SidebarStatLine(label = "Online", value = onlineUsers.toString(), dotColor = StatusGreen)
                    SidebarStatLine(label = "Offline", value = offlineUsers.toString(), dotColor = StatusAmber)
                    SidebarStatLine(label = "Movie Requests", value = catalogState.movieRequests.size.toString(), dotColor = StatusBlue)
                }
            }
        }
    }

    val defaultVipPts = catalogState.vipAccessConfig.vipPointsToAccess

    val mainBody: @Composable () -> Unit = {
        when (currentSection) {
            AdminSection.DASHBOARD -> AdminDashboardScreen(
                catalogState = catalogState,
                onAddMovie = {
                    pendingEditMovie = null
                    openMovieEditorOnLaunch = true
                    currentSection = AdminSection.MOVIES
                },
                onAddSeries = {
                    pendingEditSeries = null
                    openSeriesEditorOnLaunch = true
                    currentSection = AdminSection.WEB_SERIES
                },
                onOpenTmdbImport = {
                    currentSection = AdminSection.TMDB_IMPORT
                },
                onEditItem = { item ->
                    if (item.isSeries) {
                        pendingEditSeries = item
                        openSeriesEditorOnLaunch = true
                        currentSection = AdminSection.WEB_SERIES
                    } else {
                        pendingEditMovie = item
                        openMovieEditorOnLaunch = true
                        currentSection = AdminSection.MOVIES
                    }
                },
                onSendPushForItem = { item ->
                    pushTargetItem = item
                    currentSection = AdminSection.PUSH_NOTIFICATION
                },
                onTogglePublish = vm::togglePublish,
                onDeleteItem = { item -> vm.deleteCatalogItem(item) },
                onSeedDemoCatalog = vm::seedOpenDemoCatalog
            )

            AdminSection.CATEGORIES -> AdminCategoriesScreen(
                categories = catalogState.categories,
                countUsage = vm::countItemsUsingCategory,
                onSaveCategory = { cat -> vm.saveCategory(cat) },
                onDeleteCategory = vm::deleteCategory
            )

            AdminSection.MOVIES -> AdminMoviesScreen(
                movies = catalogState.movies,
                categories = catalogState.categories,
                mediaAnalyticsMap = catalogState.mediaAnalyticsMap,
                defaultVipPoints = defaultVipPts,
                initialEditingMovie = pendingEditMovie,
                openEditorInitially = openMovieEditorOnLaunch,
                onQuickFetchTmdb = { idOrTitle, onResult ->
                    vm.fetchTmdbQuickFill(idOrTitle, isSeries = false, onResult = onResult)
                },
                onSaveMovie = { item ->
                    vm.saveCatalogItem(item)
                    pendingEditMovie = null
                    openMovieEditorOnLaunch = false
                },
                onDeleteMovie = { item -> vm.deleteCatalogItem(item) },
                onSendPushForMovie = { movie ->
                    pushTargetItem = movie
                    currentSection = AdminSection.PUSH_NOTIFICATION
                },
                onNavigateToTmdbImport = { currentSection = AdminSection.TMDB_IMPORT }
            )

            AdminSection.WEB_SERIES -> AdminWebSeriesScreen(
                webSeries = catalogState.series,
                categories = catalogState.categories,
                mediaAnalyticsMap = catalogState.mediaAnalyticsMap,
                defaultVipPoints = defaultVipPts,
                initialEditingSeries = pendingEditSeries,
                openEditorInitially = openSeriesEditorOnLaunch,
                onSaveSeries = { item ->
                    vm.saveCatalogItem(item)
                    pendingEditSeries = null
                    openSeriesEditorOnLaunch = false
                },
                onDeleteSeries = { item -> vm.deleteCatalogItem(item) },
                onSendPushForSeries = { series ->
                    pushTargetItem = series
                    currentSection = AdminSection.PUSH_NOTIFICATION
                },
                onNavigateToTmdbImport = { currentSection = AdminSection.TMDB_IMPORT }
            )

            AdminSection.TMDB_IMPORT -> AdminTmdbImportScreen(
                isTmdbConfigured = vm.hasTmdbKeyConfigured,
                tmdbState = tmdbState,
                categories = catalogState.categories,
                defaultVipPoints = defaultVipPts,
                onSearch = vm::searchTmdb,
                onSelectPreview = vm::selectTmdbPreview,
                findExistingDuplicate = vm::findExistingByTmdbId,
                onCommitImport = { preview, selection, manualVideoUrl, selectedCategoryIds, existing ->
                    vm.applySelectionAndSaveTmdb(
                        preview = preview,
                        selection = selection,
                        manualVideoUrl = manualVideoUrl,
                        selectedCategoryIds = selectedCategoryIds,
                        existingToOverwrite = existing
                    )
                },
                onConfigureApiKey = vm::saveTmdbApiKeyOverride
            )

            AdminSection.MOVIE_REQUESTS -> AdminMovieRequestsScreen(
                requests = catalogState.movieRequests,
                onUploadRequestedItem = { req ->
                    val draftItem = CatalogItem(
                        id = "",
                        tmdbId = req.tmdbId,
                        title = req.title,
                        originalTitle = req.title,
                        mediaType = if (req.isSeries) MediaType.SERIES.name else MediaType.MOVIE.name,
                        posterUrl = req.posterUrl,
                        backdropUrl = req.backdropUrl,
                        overview = req.overview,
                        releaseYear = req.releaseYear,
                        releaseDate = req.releaseDate,
                        rating = req.rating,
                        published = true
                    )
                    if (req.isSeries) {
                        pendingEditSeries = draftItem
                        openSeriesEditorOnLaunch = true
                        currentSection = AdminSection.WEB_SERIES
                    } else {
                        pendingEditMovie = draftItem
                        openMovieEditorOnLaunch = true
                        currentSection = AdminSection.MOVIES
                    }
                },
                onImportViaTmdb = { req ->
                    vm.searchTmdb(req.title, req.isSeries)
                    currentSection = AdminSection.TMDB_IMPORT
                },
                onUpdateStatus = { req, newStatus ->
                    vm.markMovieRequestStatus(req, newStatus)
                },
                onDeleteRequest = { req ->
                    vm.deleteMovieRequest(req)
                }
            )

            AdminSection.PUSH_NOTIFICATION -> AdminPushNotificationScreen(
                targetItem = pushTargetItem,
                enrichedItem = pushTargetItem?.let { enrichedItemMap[it.id] },
                allCatalogItems = catalogState.movies + catalogState.series,
                users = catalogState.users,
                serviceAccountCreds = serviceAccountCreds,
                serviceAccountFileName = serviceAccountFileName,
                isSending = isSendingPush,
                onSelectTargetItem = { pushTargetItem = it },
                onRequestTmdbBackdrops = vm::ensureCastImagesLoaded,
                onServiceAccountLoaded = vm::setServiceAccountCredentials,
                onFetchAllUsers = vm::refreshUsersForPush,
                onSendPushNotification = { creds, item, title, body, img ->
                    vm.sendPushNotificationToAllUsers(creds, item, title, body, img)
                },
                onBack = { currentSection = AdminSection.DASHBOARD }
            )

            AdminSection.APP_UPDATE -> AdminAppUpdateScreen(
                currentConfig = catalogState.appUpdateConfig,
                onSaveAndSendUpdate = vm::saveAndPublishAppUpdate
            )

            AdminSection.USER_MANAGEMENT -> AdminUsersScreen(
                users = catalogState.users,
                vipConfig = catalogState.vipAccessConfig,
                onSaveVipConfig = vm::saveVipAccessConfig,
                onUpdateUser = { u, pts, shares, isVip ->
                    vm.adminUpdateUserRecord(u, pts, shares, isVip)
                }
            )

            AdminSection.SETTINGS -> AdminSettingsScreen(
                preferences = preferences,
                cacheSizeBytes = cacheSizeBytes,
                isFirebaseConnected = catalogState.isConnectedToFirebase,
                isTmdbConfigured = vm.hasTmdbKeyConfigured,
                adminEmail = authState.email,
                onSetThemeVariant = vm::setThemeVariant,
                onSetDefaultQuality = vm::setDefaultQuality,
                onSetAutoPlayNext = vm::setAutoPlayNextEpisode,
                onSetNotifications = vm::setNotificationsEnabled,
                onSetTmdbApiKey = vm::saveTmdbApiKeyOverride,
                onClearCache = vm::clearCache,
                onSignOut = vm::signOutAdmin
            )
        }
    }

    if (isWideScreen) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            sidebarContent()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                mainBody()
            }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                    sidebarContent()
                }
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { MovieBoxLogo(subtitle = "Admin Panel") },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open Admin Menu", tint = TextWhite)
                            }
                        },
                        actions = {
                            TextButton(onClick = onExitAdminToUserApp) {
                                Text("User App", color = CrimsonLight, fontSize = 12.sp)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    mainBody()
                }
            }
        }
    }
}

// =============================================================================
// ADMIN MOVIE & WEB SERIES REQUESTS SECTION (Embedded inside AdminShellScreen.kt)
// =============================================================================
@Composable
private fun AdminMovieRequestsScreen(
    requests: List<MovieRequestItem>,
    onUploadRequestedItem: (MovieRequestItem) -> Unit,
    onImportViaTmdb: (MovieRequestItem) -> Unit,
    onUpdateStatus: (MovieRequestItem, String) -> Unit,
    onDeleteRequest: (MovieRequestItem) -> Unit
) {
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val pendingCount = remember(requests) { requests.count { it.status.equals("PENDING", ignoreCase = true) } }
    val uploadedCount = remember(requests) { requests.count { it.status.equals("UPLOADED", ignoreCase = true) } }

    val filteredRequests = remember(requests, selectedFilter, searchQuery) {
        requests.filter { req ->
            val matchesTab = when (selectedFilter) {
                "Pending" -> req.status.equals("PENDING", ignoreCase = true)
                "Uploaded" -> req.status.equals("UPLOADED", ignoreCase = true)
                "Movies" -> !req.isSeries
                "Web Series" -> req.isSeries
                else -> true
            }
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                req.title.contains(q, ignoreCase = true) ||
                req.requestedByDeviceId.contains(q, ignoreCase = true) ||
                req.requestedByShareCode.contains(q, ignoreCase = true)
            matchesTab && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Request Movie & Web Series",
                    color = TextWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "User-submitted notifications for Movies & Web Series not yet available in the app",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        // 3 Stat Summary Cards: Total | Pending | Uploaded
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RequestStatBox(
                title = "Total Requests",
                value = requests.size.toString(),
                accent = StatusBlue,
                modifier = Modifier.weight(1f)
            )
            RequestStatBox(
                title = "Pending",
                value = pendingCount.toString(),
                accent = StatusAmber,
                modifier = Modifier.weight(1f)
            )
            RequestStatBox(
                title = "Uploaded",
                value = uploadedCount.toString(),
                accent = StatusGreen,
                modifier = Modifier.weight(1f)
            )
        }

        // Horizontally Scrollable Filter Pills (Fixed so 'Web Series' pill never gets squished)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("All", "Pending", "Uploaded", "Movies", "Web Series").forEach { tab ->
                FilterPill(
                    label = tab,
                    selected = selectedFilter == tab,
                    onClick = { selectedFilter = tab }
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search requested title, device ID, or 5-digit code...",
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            maxLines = 1,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )

        if (filteredRequests.isEmpty()) {
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
                        imageVector = Icons.Filled.PlaylistAddCheck,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No Movie or Web Series Requests Found",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "When users tap 'Notify to Admin' on unavailable titles in Search, their requests appear here immediately.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = filteredRequests,
                    key = { it.id }
                ) { req ->
                    AdminMovieRequestCard(
                        req = req,
                        onUpload = { onUploadRequestedItem(req) },
                        onImportTmdb = { onImportViaTmdb(req) },
                        onToggleUploaded = {
                            val nextStatus = if (req.status.equals("UPLOADED", ignoreCase = true)) "PENDING" else "UPLOADED"
                            onUpdateStatus(req, nextStatus)
                        },
                        onDelete = { onDeleteRequest(req) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestStatBox(
    title: String,
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                color = accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AdminMovieRequestCard(
    req: MovieRequestItem,
    onUpload: () -> Unit,
    onImportTmdb: () -> Unit,
    onToggleUploaded: () -> Unit,
    onDelete: () -> Unit
) {
    val isUploaded = req.status.equals("UPLOADED", ignoreCase = true)
    val formattedDate = remember(req.requestedAt) {
        runCatching {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(req.requestedAt))
        }.getOrDefault("")
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUploaded) StatusGreen.copy(alpha = 0.5f) else StatusAmber.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(98.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                ) {
                    PosterImageWithFallback(
                        imageUrl = req.posterUrl.ifBlank { req.backdropUrl },
                        title = req.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (req.isSeries) StatusBlue.copy(alpha = 0.2f) else CrimsonRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(1.dp, if (req.isSeries) StatusBlue else CrimsonLight)
                            ) {
                                Text(
                                    text = if (req.isSeries) "WEB SERIES" else "MOVIE",
                                    color = if (req.isSeries) StatusBlue else CrimsonLight,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                color = if (isUploaded) StatusGreen.copy(alpha = 0.2f) else StatusAmber.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(1.dp, if (isUploaded) StatusGreen else StatusAmber)
                            ) {
                                Text(
                                    text = if (isUploaded) "UPLOADED" else "PENDING",
                                    color = if (isUploaded) StatusGreen else StatusAmber,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (req.requestCount > 1) {
                                Surface(
                                    color = StarGold.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(5.dp),
                                    border = BorderStroke(1.dp, StarGold)
                                ) {
                                    Text(
                                        text = "${req.requestCount}x Requested",
                                        color = StarGold,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = req.title,
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = buildString {
                            append("Year: ${req.releaseYear}")
                            if (req.releaseDate.isNotBlank()) append(" (${req.releaseDate})")
                            if (req.tmdbId != null) append(" • TMDB #${req.tmdbId}")
                        },
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )

                    if (req.requestedByShareCode.isNotBlank() || req.requestedByDeviceId.isNotBlank()) {
                        Text(
                            text = "User Code: ${req.requestedByShareCode.ifBlank { "N/A" }} • Device ID: ${req.requestedByDeviceId.ifBlank { "N/A" }}",
                            color = StatusBlue,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (formattedDate.isNotBlank()) {
                        Text(
                            text = "Requested: $formattedDate",
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onUpload,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Upload Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onImportTmdb,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = StatusBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("TMDB Import", color = StatusBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onToggleUploaded,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isUploaded) StatusAmber else StatusGreen),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (isUploaded) StatusAmber else StatusGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isUploaded) "Pending" else "Done",
                        color = if (isUploaded) StatusAmber else StatusGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete Request",
                        tint = CrimsonLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarStatLine(
    label: String,
    value: String,
    dotColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = TextSecondary, fontSize = 11.5.sp)
        }
        Text(text = value, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
