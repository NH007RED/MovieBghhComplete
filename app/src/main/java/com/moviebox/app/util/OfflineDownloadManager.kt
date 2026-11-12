package com.moviebox.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Episode
import com.moviebox.app.finder.DirectStreamResolver
import com.moviebox.app.net.Net
import com.moviebox.app.player.PlayHeaders
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class OfflineDownloadStatus {
    IDLE,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class OfflineDownloadEntry(
    val downloadId: String = "",
    val itemId: String = "",
    val title: String = "",
    val seriesTitle: String = "",
    val mediaType: String = "MOVIE",
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val releaseYear: Int = 2025,
    val rating: Double = 8.0,
    val overview: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val localPosterPath: String = "",
    val originalStreamUrl: String = "",
    val containerExtension: String = "mp4",
    val chunkCount: Int = 0,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progressPercent: Int = 0,
    val status: OfflineDownloadStatus = OfflineDownloadStatus.IDLE,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isSeries: Boolean
        get() = mediaType.equals("SERIES", ignoreCase = true) ||
            mediaType.equals("WEB_SERIES", ignoreCase = true) ||
            seasonNumber != null

    val offlineStreamUri: String
        get() = "moviebox-offline://$downloadId"

    val formattedSize: String
        get() {
            val bytes = if (totalBytes > 0L) totalBytes else downloadedBytes
            if (bytes <= 0L) return "0 MB"
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format(Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
}

object OfflineDownloadManager {
    const val OFFLINE_URI_PREFIX = "moviebox-offline://"
    private const val VAULT_DIR_NAME = "offline_vault"
    private const val PLAYBACK_SESSION_DIR = "offline_playback_session"
    private const val CHUNK_SIZE_BYTES = 512 * 1024 // 512 KB per encrypted .mbx chunk

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedByAdminOrUser = ConcurrentHashMap.newKeySet<String>()

    private val httpClient: OkHttpClient
        get() = Net.client

    private val _downloadsMap = MutableStateFlow<Map<String, OfflineDownloadEntry>>(emptyMap())
    val downloadsMap: StateFlow<Map<String, OfflineDownloadEntry>> = _downloadsMap.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun buildDownloadId(item: CatalogItem, seasonNumber: Int? = null, episodeNumber: Int? = null): String {
        val base = item.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return if (seasonNumber != null && episodeNumber != null) {
            "${base}_S${seasonNumber}_E${episodeNumber}"
        } else {
            base
        }
    }

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            isInitialized = true
            val appContext = context.applicationContext
            scope.launch {
                loadSavedDownloadsFromDisk(appContext)
            }
        }
    }

    private fun vaultRootDir(context: Context): File {
        val dir = File(context.filesDir, VAULT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            runCatching { noMedia.createNewFile() }
        }
        return dir
    }

    private fun itemVaultDir(context: Context, downloadId: String): File {
        val safeId = downloadId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dir = File(vaultRootDir(context), safeId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun loadSavedDownloadsFromDisk(context: Context) {
        val root = vaultRootDir(context)
        val map = mutableMapOf<String, OfflineDownloadEntry>()
        root.listFiles()?.forEach { folder ->
            if (folder.isDirectory) {
                val metaFile = File(folder, "metadata.json")
                if (metaFile.exists()) {
                    runCatching {
                        val entry = gson.fromJson(metaFile.readText(), OfflineDownloadEntry::class.java)
                        if (entry != null && entry.downloadId.isNotBlank()) {
                            val chunkFiles = folder.listFiles { f -> f.name.endsWith(".mbx") }.orEmpty()
                            val chunks = chunkFiles.size
                            val diskBytes = chunkFiles.sumOf { it.length() }
                            val restoredStatus = when (entry.status) {
                                OfflineDownloadStatus.COMPLETED ->
                                    if (chunks > 0) OfflineDownloadStatus.COMPLETED else OfflineDownloadStatus.FAILED
                                OfflineDownloadStatus.DOWNLOADING ->
                                    OfflineDownloadStatus.PAUSED
                                else -> entry.status
                            }
                            map[entry.downloadId] = entry.copy(
                                chunkCount = chunks,
                                downloadedBytes = if (diskBytes > 0L) diskBytes else entry.downloadedBytes,
                                status = restoredStatus,
                                progressPercent = if (restoredStatus == OfflineDownloadStatus.COMPLETED) 100 else entry.progressPercent
                            )
                        }
                    }
                }
            }
        }
        _downloadsMap.value = map
    }

    private fun saveMetadataToDisk(context: Context, entry: OfflineDownloadEntry) {
        runCatching {
            val dir = itemVaultDir(context, entry.downloadId)
            val metaFile = File(dir, "metadata.json")
            metaFile.writeText(gson.toJson(entry))
        }
    }

    /**
     * Reversible XOR byte obfuscation so individual .mbx chunk files cannot be opened
     * or recognized by external video players or file managers.
     */
    private fun transformBytesInPlace(buffer: ByteArray, length: Int, chunkIndex: Int) {
        val seed = ((chunkIndex * 73) and 0xFF) xor 0x5A
        for (i in 0 until length) {
            val mask = ((i and 0xFF) xor seed) and 0xFF
            buffer[i] = (buffer[i].toInt() xor mask).toByte()
        }
    }

    /**
     * Pauses an active download while preserving all already-downloaded .mbx chunk files on disk.
     */
    fun pauseDownload(context: Context, downloadId: String) {
        val appContext = context.applicationContext
        pausedByAdminOrUser.add(downloadId)
        activeJobs.remove(downloadId)?.cancel()

        val current = _downloadsMap.value[downloadId] ?: return
        val dir = itemVaultDir(appContext, downloadId)
        val chunkFiles = dir.listFiles { f -> f.isFile && f.name.startsWith("part_") && f.name.endsWith(".mbx") }.orEmpty()
        val savedBytes = chunkFiles.sumOf { it.length() }

        val pausedEntry = current.copy(
            chunkCount = chunkFiles.size.coerceAtLeast(current.chunkCount),
            downloadedBytes = savedBytes.coerceAtLeast(current.downloadedBytes),
            status = OfflineDownloadStatus.PAUSED,
            errorMessage = null
        )
        saveMetadataToDisk(appContext, pausedEntry)
        updateEntry(pausedEntry)
    }

    /**
     * Resumes a PAUSED download or Retries a FAILED download from the saved .mbx chunk offset.
     */
    fun resumeOrRetryDownload(
        context: Context,
        downloadId: String,
        fallbackItem: CatalogItem? = null,
        fallbackEpisode: Episode? = null
    ) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)
        if (activeJobs.containsKey(downloadId)) return

        val existing = _downloadsMap.value[downloadId]
        if (existing != null && existing.originalStreamUrl.isNotBlank()) {
            launchChunkDownloadJob(appContext, existing, resumeExistingChunks = true)
        } else if (fallbackItem != null) {
            startDownload(appContext, fallbackItem, fallbackEpisode)
        }
    }

    /**
     * Starts downloading a Movie or Web Series Episode into encrypted .mbx chunk files.
     */
    fun startDownload(
        context: Context,
        item: CatalogItem,
        episode: Episode? = null,
        streamUrlOverride: String? = null
    ) {
        val appContext = context.applicationContext
        ensureInitialized(appContext)

        val firstEp = episode ?: if (item.isSeries) {
            item.seasons.flatMap { it.episodes }.firstOrNull { it.primaryStreamUrl.isNotBlank() }
        } else null

        val sNum = firstEp?.seasonNumber
        val eNum = firstEp?.episodeNumber
        val downloadId = buildDownloadId(item, sNum, eNum)

        // If already downloading, ignore duplicate click
        if (activeJobs.containsKey(downloadId)) return

        // If already paused or failed for this exact item, resume/retry it
        val existingEntry = _downloadsMap.value[downloadId]
        if (existingEntry != null &&
            (existingEntry.status == OfflineDownloadStatus.PAUSED || existingEntry.status == OfflineDownloadStatus.FAILED) &&
            existingEntry.originalStreamUrl.isNotBlank()
        ) {
            launchChunkDownloadJob(appContext, existingEntry, resumeExistingChunks = true)
            return
        }

        val rawStreamUrl = (streamUrlOverride
            ?: firstEp?.primaryStreamUrl
            ?: item.primaryStreamUrl).trim()

        val displayTitle = if (firstEp != null) {
            "${item.title} • S${sNum} E${eNum} - ${firstEp.title}"
        } else {
            item.title
        }

        if (rawStreamUrl.isBlank()) {
            val failedEntry = OfflineDownloadEntry(
                downloadId = downloadId,
                itemId = item.id,
                title = displayTitle,
                seriesTitle = if (item.isSeries) item.title else "",
                mediaType = item.mediaType,
                seasonNumber = sNum,
                episodeNumber = eNum,
                releaseYear = item.releaseYear,
                rating = item.rating,
                overview = firstEp?.overview?.ifBlank { item.overview } ?: item.overview,
                posterUrl = item.posterUrl.ifBlank { item.backdropUrl },
                backdropUrl = firstEp?.thumbnailUrl?.ifBlank { item.backdropUrl } ?: item.backdropUrl,
                originalStreamUrl = rawStreamUrl,
                status = OfflineDownloadStatus.FAILED,
                errorMessage = "No stream URL configured"
            )
            saveMetadataToDisk(appContext, failedEntry)
            updateEntry(failedEntry)
            return
        }

        val initialEntry = OfflineDownloadEntry(
            downloadId = downloadId,
            itemId = item.id,
            title = displayTitle,
            seriesTitle = if (item.isSeries) item.title else "",
            mediaType = item.mediaType,
            seasonNumber = sNum,
            episodeNumber = eNum,
            releaseYear = item.releaseYear,
            rating = item.rating,
            overview = firstEp?.overview?.ifBlank { item.overview } ?: item.overview,
            posterUrl = item.posterUrl.ifBlank { item.backdropUrl },
            backdropUrl = firstEp?.thumbnailUrl?.ifBlank { item.backdropUrl } ?: item.backdropUrl,
            originalStreamUrl = rawStreamUrl,
            status = OfflineDownloadStatus.DOWNLOADING,
            progressPercent = 1
        )
        launchChunkDownloadJob(appContext, initialEntry, resumeExistingChunks = false)
    }

    private fun launchChunkDownloadJob(
        appContext: Context,
        entry: OfflineDownloadEntry,
        resumeExistingChunks: Boolean
    ) {
        val downloadId = entry.downloadId
        pausedByAdminOrUser.remove(downloadId)

        val resumedEntry = entry.copy(
            status = OfflineDownloadStatus.DOWNLOADING,
            progressPercent = entry.progressPercent.coerceAtLeast(1),
            errorMessage = null
        )
        saveMetadataToDisk(appContext, resumedEntry)
        updateEntry(resumedEntry)

        val job = scope.launch {
            try {
                val dir = itemVaultDir(appContext, downloadId)
                if (!resumeExistingChunks) {
                    dir.listFiles()?.forEach { f ->
                        if (f.name.endsWith(".mbx")) {
                            runCatching { f.delete() }
                        }
                    }
                }

                // 1. Download and cache poster image locally for offline display
                val localPosterFile = File(dir, "poster.jpg")
                val posterToFetch = resumedEntry.posterUrl.ifBlank { resumedEntry.backdropUrl }
                val cachedPosterPath = if (localPosterFile.exists() && localPosterFile.length() > 0L) {
                    localPosterFile.absolutePath
                } else if (posterToFetch.isNotBlank()) {
                    runCatching {
                        val req = Request.Builder().url(PlayHeaders.normalizeUrl(posterToFetch)).build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { input ->
                                    FileOutputStream(localPosterFile).use { out ->
                                        input.copyTo(out)
                                    }
                                }
                                localPosterFile.absolutePath
                            } else ""
                        }
                    }.getOrDefault("")
                } else ""

                val entryWithPoster = resumedEntry.copy(localPosterPath = cachedPosterPath)
                saveMetadataToDisk(appContext, entryWithPoster)

                // 2. Resolve direct stream URL + Gofile/cookie headers before downloading
                val resolved = DirectStreamResolver.resolveForDirectPlay(
                    context = appContext,
                    rawUrl = entryWithPoster.originalStreamUrl
                )
                val resolvedUrl = resolved.url
                val lowerUrl = resolvedUrl.lowercase(Locale.US).substringBefore('?')
                if (lowerUrl.endsWith(".m3u8")) {
                    downloadHlsStreamAsChunks(
                        context = appContext,
                        dir = dir,
                        baseEntry = entryWithPoster,
                        playlistUrl = resolvedUrl
                    )
                } else {
                    downloadProgressiveStreamAsChunks(
                        context = appContext,
                        dir = dir,
                        baseEntry = entryWithPoster,
                        streamUrl = resolvedUrl
                    )
                }
            } catch (ce: CancellationException) {
                if (pausedByAdminOrUser.contains(downloadId)) {
                    val cur = _downloadsMap.value[downloadId] ?: resumedEntry
                    val paused = cur.copy(status = OfflineDownloadStatus.PAUSED, errorMessage = null)
                    saveMetadataToDisk(appContext, paused)
                    updateEntry(paused)
                }
            } catch (e: Exception) {
                if (!pausedByAdminOrUser.contains(downloadId)) {
                    val current = _downloadsMap.value[downloadId] ?: resumedEntry
                    val failed = current.copy(
                        status = OfflineDownloadStatus.FAILED,
                        errorMessage = e.localizedMessage ?: "Download interrupted — Tap Retry"
                    )
                    saveMetadataToDisk(appContext, failed)
                    updateEntry(failed)
                }
            } finally {
                activeJobs.remove(downloadId)
            }
        }
        activeJobs[downloadId] = job
    }

    private suspend fun downloadProgressiveStreamAsChunks(
        context: Context,
        dir: File,
        baseEntry: OfflineDownloadEntry,
        streamUrl: String
    ) {
        val safeUrl = PlayHeaders.normalizeUrl(streamUrl)
        val ext = guessExtension(safeUrl)
        val existingFiles = dir.listFiles { f ->
            f.isFile && f.name.startsWith("part_") && f.name.endsWith(".mbx")
        }?.sortedBy { it.name }.orEmpty()

        var chunkIndex = existingFiles.size
        var totalRead = existingFiles.sumOf { it.length() }

        val reqBuilder = Request.Builder().url(safeUrl)
        PlayHeaders.buildHeadersForUrl(safeUrl).forEach { (k, v) ->
            reqBuilder.header(k, v)
        }

        if (chunkIndex > 0 && totalRead > 0L) {
            reqBuilder.header("Range", "bytes=$totalRead-")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                error("Server returned HTTP ${response.code}")
            }

            // If server did not honor Range (returned 200 instead of 206), restart from chunk 0
            if (chunkIndex > 0 && response.code == 200) {
                existingFiles.forEach { runCatching { it.delete() } }
                chunkIndex = 0
                totalRead = 0L
            }

            val body = response.body ?: error("Empty video stream")
            val bodyLen = body.contentLength()
            val totalExpectedBytes = when {
                response.code == 206 && bodyLen > 0L -> totalRead + bodyLen
                bodyLen > 0L -> bodyLen
                baseEntry.totalBytes > 0L -> baseEntry.totalBytes
                else -> -1L
            }

            val input = BufferedInputStream(body.byteStream(), 64 * 1024)
            val chunkBuffer = ByteArray(CHUNK_SIZE_BYTES)
            var lastReportedPct = baseEntry.progressPercent.coerceAtLeast(1)

            while (scope.isActive && !pausedByAdminOrUser.contains(baseEntry.downloadId)) {
                var bytesInCurrentChunk = 0
                while (bytesInCurrentChunk < CHUNK_SIZE_BYTES) {
                    val read = input.read(
                        chunkBuffer,
                        bytesInCurrentChunk,
                        CHUNK_SIZE_BYTES - bytesInCurrentChunk
                    )
                    if (read == -1) break
                    bytesInCurrentChunk += read
                }

                if (bytesInCurrentChunk <= 0) break

                // Obfuscate chunk bytes in place so external players cannot read it
                transformBytesInPlace(chunkBuffer, bytesInCurrentChunk, chunkIndex)

                val chunkFile = File(dir, String.format(Locale.US, "part_%04d.mbx", chunkIndex))
                FileOutputStream(chunkFile).use { fos ->
                    fos.write(chunkBuffer, 0, bytesInCurrentChunk)
                }

                chunkIndex++
                totalRead += bytesInCurrentChunk

                val pct = if (totalExpectedBytes > 0L) {
                    ((totalRead * 100L) / totalExpectedBytes).toInt().coerceIn(1, 99)
                } else {
                    (10 + (chunkIndex * 3)).coerceIn(2, 95)
                }

                if (pct != lastReportedPct || chunkIndex % 2 == 0) {
                    lastReportedPct = pct
                    val inProgress = baseEntry.copy(
                        containerExtension = ext,
                        chunkCount = chunkIndex,
                        downloadedBytes = totalRead,
                        totalBytes = if (totalExpectedBytes > 0L) totalExpectedBytes else totalRead,
                        progressPercent = pct,
                        status = OfflineDownloadStatus.DOWNLOADING
                    )
                    updateEntry(inProgress)
                    if (chunkIndex % 4 == 0) {
                        saveMetadataToDisk(context, inProgress)
                    }
                }
            }

            if (pausedByAdminOrUser.contains(baseEntry.downloadId)) {
                val paused = baseEntry.copy(
                    containerExtension = ext,
                    chunkCount = chunkIndex,
                    downloadedBytes = totalRead,
                    totalBytes = if (totalExpectedBytes > 0L) totalExpectedBytes else totalRead,
                    progressPercent = lastReportedPct,
                    status = OfflineDownloadStatus.PAUSED
                )
                saveMetadataToDisk(context, paused)
                updateEntry(paused)
                return
            }

            if (chunkIndex == 0) {
                error("Stream returned 0 bytes")
            }

            val completed = baseEntry.copy(
                containerExtension = ext,
                chunkCount = chunkIndex,
                downloadedBytes = totalRead,
                totalBytes = totalRead,
                progressPercent = 100,
                status = OfflineDownloadStatus.COMPLETED,
                errorMessage = null
            )
            saveMetadataToDisk(context, completed)
            updateEntry(completed)
        }
    }

    private suspend fun downloadHlsStreamAsChunks(
        context: Context,
        dir: File,
        baseEntry: OfflineDownloadEntry,
        playlistUrl: String
    ) {
        val segmentUrls = resolveHlsSegmentUrls(playlistUrl)
        if (segmentUrls.isEmpty()) {
            downloadProgressiveStreamAsChunks(context, dir, baseEntry, playlistUrl)
            return
        }

        val existingFiles = dir.listFiles { f ->
            f.isFile && f.name.startsWith("part_") && f.name.endsWith(".mbx")
        }?.sortedBy { it.name }.orEmpty()

        var chunkIndex = existingFiles.size
        var totalBytesRead = existingFiles.sumOf { it.length() }
        val totalSegments = segmentUrls.size

        for (idx in chunkIndex until totalSegments) {
            if (!scope.isActive || pausedByAdminOrUser.contains(baseEntry.downloadId)) break
            val segUrl = PlayHeaders.normalizeUrl(segmentUrls[idx])
            val reqBuilder = Request.Builder().url(segUrl)
            PlayHeaders.buildHeadersForUrl(segUrl).forEach { (k, v) ->
                reqBuilder.header(k, v)
            }

            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        transformBytesInPlace(bytes, bytes.size, chunkIndex)
                        val chunkFile = File(dir, String.format(Locale.US, "part_%04d.mbx", chunkIndex))
                        FileOutputStream(chunkFile).use { it.write(bytes) }
                        chunkIndex++
                        totalBytesRead += bytes.size
                    }
                }
            }

            val pct = (((idx + 1) * 100) / totalSegments).coerceIn(1, 99)
            val progressEntry = baseEntry.copy(
                containerExtension = "ts",
                chunkCount = chunkIndex,
                downloadedBytes = totalBytesRead,
                totalBytes = totalBytesRead,
                progressPercent = pct,
                status = OfflineDownloadStatus.DOWNLOADING
            )
            updateEntry(progressEntry)
            if (chunkIndex % 4 == 0) {
                saveMetadataToDisk(context, progressEntry)
            }
        }

        if (pausedByAdminOrUser.contains(baseEntry.downloadId)) {
            val pct = ((chunkIndex * 100) / totalSegments.coerceAtLeast(1)).coerceIn(1, 99)
            val paused = baseEntry.copy(
                containerExtension = "ts",
                chunkCount = chunkIndex,
                downloadedBytes = totalBytesRead,
                totalBytes = totalBytesRead,
                progressPercent = pct,
                status = OfflineDownloadStatus.PAUSED
            )
            saveMetadataToDisk(context, paused)
            updateEntry(paused)
            return
        }

        if (chunkIndex == 0) {
            error("Unable to download HLS segments")
        }

        val completed = baseEntry.copy(
            containerExtension = "ts",
            chunkCount = chunkIndex,
            downloadedBytes = totalBytesRead,
            totalBytes = totalBytesRead,
            progressPercent = 100,
            status = OfflineDownloadStatus.COMPLETED,
            errorMessage = null
        )
        saveMetadataToDisk(context, completed)
        updateEntry(completed)
    }

    private fun resolveHlsSegmentUrls(playlistUrl: String): List<String> {
        val text = fetchText(playlistUrl) ?: return emptyList()
        val lines = text.lines().map { it.trim() }

        val variantIdx = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF") }
        if (variantIdx >= 0) {
            val variantLine = lines.drop(variantIdx + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (variantLine != null) {
                val resolvedVariant = resolveRelativeUrl(playlistUrl, variantLine)
                return resolveHlsSegmentUrls(resolvedVariant)
            }
        }

        return lines
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .take(180)
            .map { resolveRelativeUrl(playlistUrl, it) }
    }

    private fun fetchText(url: String): String? = runCatching {
        val safeUrl = PlayHeaders.normalizeUrl(url)
        val reqBuilder = Request.Builder().url(safeUrl)
        PlayHeaders.buildHeadersForUrl(safeUrl).forEach { (k, v) ->
            reqBuilder.header(k, v)
        }
        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    }.getOrNull()

    private fun resolveRelativeUrl(baseUrl: String, relativeOrAbsolute: String): String = runCatching {
        if (UrlValidator.isHttpOrHttpsUrl(relativeOrAbsolute)) {
            relativeOrAbsolute
        } else {
            URI(PlayHeaders.normalizeUrl(baseUrl)).resolve(relativeOrAbsolute).toString()
        }
    }.getOrDefault(relativeOrAbsolute)

    private fun guessExtension(url: String): String {
        val clean = url.lowercase(Locale.US).substringBefore('?')
        return when {
            clean.endsWith(".mkv") -> "mkv"
            clean.endsWith(".webm") -> "webm"
            clean.endsWith(".mov") -> "mov"
            clean.endsWith(".ts") -> "ts"
            else -> "mp4"
        }
    }

    /**
     * Reassembles encrypted .mbx chunk files inside a private internal playback cache
     * so only MovieBox's native ExoPlayer can play the offline video.
     */
    suspend fun prepareOfflinePlaybackUri(context: Context, rawUrlOrDownloadId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val downloadId = rawUrlOrDownloadId.removePrefix(OFFLINE_URI_PREFIX).trim()
                val vaultDir = itemVaultDir(context, downloadId)
                val metaFile = File(vaultDir, "metadata.json")
                val entry = if (metaFile.exists()) {
                    gson.fromJson(metaFile.readText(), OfflineDownloadEntry::class.java)
                } else {
                    _downloadsMap.value[downloadId]
                }

                val chunkFiles = vaultDir.listFiles { f ->
                    f.isFile && f.name.startsWith("part_") && f.name.endsWith(".mbx")
                }?.sortedBy { it.name }.orEmpty()

                if (chunkFiles.isEmpty()) return@withContext null

                val ext = entry?.containerExtension?.ifBlank { "mp4" } ?: "mp4"
                val sessionDir = File(context.cacheDir, PLAYBACK_SESSION_DIR)
                if (!sessionDir.exists()) sessionDir.mkdirs()

                val targetFile = File(sessionDir, "play_${downloadId}.$ext")
                val expectedBytes = chunkFiles.sumOf { it.length() }

                if (!targetFile.exists() || targetFile.length() != expectedBytes) {
                    FileOutputStream(targetFile).use { out ->
                        chunkFiles.forEachIndexed { index, chunkFile ->
                            val bytes = FileInputStream(chunkFile).use { it.readBytes() }
                            transformBytesInPlace(bytes, bytes.size, index)
                            out.write(bytes)
                        }
                    }
                }
                Uri.fromFile(targetFile).toString()
            }.getOrNull()
        }

    fun cleanupPlaybackSessions(context: Context) {
        scope.launch {
            runCatching {
                val sessionDir = File(context.cacheDir, PLAYBACK_SESSION_DIR)
                sessionDir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    fun cancelOrDeleteDownload(context: Context, downloadId: String) {
        val appContext = context.applicationContext
        pausedByAdminOrUser.remove(downloadId)
        activeJobs.remove(downloadId)?.cancel()
        scope.launch {
            runCatching {
                val dir = itemVaultDir(appContext, downloadId)
                dir.deleteRecursively()
                val sessionFileDir = File(appContext.cacheDir, PLAYBACK_SESSION_DIR)
                sessionFileDir.listFiles()?.filter { it.name.contains(downloadId) }?.forEach { it.delete() }
            }
            val updated = _downloadsMap.value.toMutableMap()
            updated.remove(downloadId)
            _downloadsMap.value = updated
        }
    }

    private fun updateEntry(entry: OfflineDownloadEntry) {
        val updated = _downloadsMap.value.toMutableMap()
        updated[entry.downloadId] = entry
        _downloadsMap.value = updated
    }

    fun isNetworkAvailable(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    }.getOrDefault(true)

    fun observeNetworkOnline(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        trySend(isNetworkAvailable(appContext))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isNetworkAvailable(appContext))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasNet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(hasNet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }

        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()
}
