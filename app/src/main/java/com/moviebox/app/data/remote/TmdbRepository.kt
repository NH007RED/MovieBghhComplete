package com.moviebox.app.data.remote

import com.moviebox.app.BuildConfig
import com.moviebox.app.data.model.ActorProfileDetails
import com.moviebox.app.data.model.CastMember
import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Episode
import com.moviebox.app.data.model.MediaType
import com.moviebox.app.data.model.Season
import com.moviebox.app.data.model.TmdbImportSelection
import com.moviebox.app.security.NhNativeSecurityVault
import com.moviebox.app.util.UrlValidator
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object TmdbMapper {
    const val POSTER_SIZE = "w500"
    const val BACKDROP_SIZE = "w1280"
    const val PROFILE_SIZE = "w185"
    const val STILL_SIZE = "w500"

    fun buildImageUrl(path: String?, size: String = POSTER_SIZE): String {
        val clean = path?.trim().orEmpty()
        if (clean.isEmpty()) return ""
        if (UrlValidator.isHttpOrHttpsUrl(clean)) return clean
        val normalized = if (clean.startsWith("/")) clean.substring(1) else clean
        return "${BuildConfig.TMDB_IMAGE_BASE}$size/$normalized"
    }

    fun extractYoutubeTrailer(videos: TmdbVideosResponseDto?): String {
        val list = videos?.results.orEmpty()
        val youtubeVideos = list.filter {
            it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank()
        }
        val chosen = youtubeVideos.firstOrNull {
            it.type.equals("Trailer", ignoreCase = true) && it.official
        } ?: youtubeVideos.firstOrNull {
            it.type.equals("Trailer", ignoreCase = true)
        } ?: youtubeVideos.firstOrNull {
            it.type.equals("Teaser", ignoreCase = true)
        } ?: youtubeVideos.firstOrNull()

        return if (chosen != null) UrlValidator.buildYouTubeUrl(chosen.key) else ""
    }

    fun parseReleaseYear(dateStr: String?): Int {
        val clean = dateStr?.trim().orEmpty()
        if (clean.length >= 4) {
            clean.substring(0, 4).toIntOrNull()?.let { return it }
        }
        return 2024
    }

    fun mapMovieToCatalogItem(
        dto: TmdbMovieDto,
        selection: TmdbImportSelection = TmdbImportSelection(),
        existing: CatalogItem? = null
    ): CatalogItem {
        val now = System.currentTimeMillis()
        val base = existing ?: CatalogItem(
            id = "movie_tmdb_${dto.id}_${UUID.randomUUID().toString().take(6)}",
            mediaType = MediaType.MOVIE.name,
            createdAt = now
        )
        val rawTitle = dto.title?.trim().orEmpty().ifBlank { "Untitled Movie" }
        val finalTitle = if (selection.importTitle || base.title.isBlank()) rawTitle else base.title
        val year = parseReleaseYear(dto.releaseDate)
        val roundedRating = String.format(Locale.US, "%.1f", dto.voteAverage ?: 0.0).toDoubleOrNull() ?: 0.0

        val mappedCast = dto.credits?.cast.orEmpty().take(16).map { c ->
            CastMember(
                id = c.id.toString(),
                name = c.name,
                character = c.character.orEmpty(),
                profileUrl = buildImageUrl(c.profilePath, PROFILE_SIZE)
            )
        }

        val primaryPoster = buildImageUrl(dto.posterPath, POSTER_SIZE)
        val primaryBackdrop = buildImageUrl(dto.backdropPath, BACKDROP_SIZE)
        val rawPosters = dto.images?.posters.orEmpty().mapNotNull {
            buildImageUrl(it.filePath, POSTER_SIZE).takeIf(String::isNotBlank)
        }
        val rawBackdrops = dto.images?.backdrops.orEmpty().mapNotNull {
            buildImageUrl(it.filePath, BACKDROP_SIZE).takeIf(String::isNotBlank)
        }
        val posterList = (listOfNotNull(primaryPoster.takeIf { it.isNotBlank() }) + rawPosters).distinct()
        val backdropList = (listOfNotNull(primaryBackdrop.takeIf { it.isNotBlank() }) + rawBackdrops).distinct()

        val mappedCompanies = dto.productionCompanies.orEmpty()
            .mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
        val mappedCountry = dto.productionCountries.orEmpty()
            .firstOrNull()?.name?.trim().orEmpty().ifBlank { base.country.ifBlank { "United States" } }

        return base.copy(
            tmdbId = dto.id,
            title = finalTitle,
            originalTitle = dto.originalTitle?.trim().orEmpty().ifBlank { finalTitle },
            tagline = dto.tagline?.trim().orEmpty().ifBlank { base.tagline },
            normalizedTitle = UrlValidator.normalizeTitle(finalTitle),
            overview = if (selection.importOverview) dto.overview?.trim().orEmpty() else base.overview,
            mediaType = MediaType.MOVIE.name,
            releaseYear = if (selection.importYearAndRating) year else base.releaseYear,
            releaseDate = if (selection.importYearAndRating) dto.releaseDate.orEmpty() else base.releaseDate,
            status = dto.status?.trim().orEmpty().ifBlank { "Released" },
            rating = if (selection.importYearAndRating) roundedRating else base.rating,
            runtimeMinutes = dto.runtime ?: base.runtimeMinutes,
            contentAdvisory = if (dto.adult == true) "A (18+)" else base.contentAdvisory.ifBlank { "U/A 13+" },
            language = dto.originalLanguage ?: base.language,
            country = mappedCountry,
            budget = dto.budget ?: base.budget,
            revenue = dto.revenue ?: base.revenue,
            companies = mappedCompanies.ifEmpty { base.companies },
            posterUrl = if (selection.importPoster) primaryPoster else base.posterUrl,
            backdropUrl = if (selection.importBackdrop) primaryBackdrop else base.backdropUrl,
            posterGallery = posterList.take(60),
            backdropGallery = backdropList.take(60),
            totalPostersCount = rawPosters.size.coerceAtLeast(posterList.size),
            totalBackdropsCount = rawBackdrops.size.coerceAtLeast(backdropList.size),
            genres = dto.genres?.mapNotNull { it.name.takeIf(String::isNotBlank) } ?: base.genres,
            cast = if (selection.importCast) mappedCast else base.cast,
            trailerUrl = if (selection.importTrailer) extractYoutubeTrailer(dto.videos) else base.trailerUrl,
            sourceAttribution = "TMDB",
            importedAt = now,
            updatedAt = now
        )
    }

    fun mapTvToCatalogItem(
        dto: TmdbTvDto,
        seasonDetails: List<TmdbSeasonDetailDto> = emptyList(),
        selection: TmdbImportSelection = TmdbImportSelection(),
        existing: CatalogItem? = null
    ): CatalogItem {
        val now = System.currentTimeMillis()
        val base = existing ?: CatalogItem(
            id = "series_tmdb_${dto.id}_${UUID.randomUUID().toString().take(6)}",
            mediaType = MediaType.SERIES.name,
            createdAt = now
        )
        val rawTitle = dto.name?.trim().orEmpty().ifBlank { "Untitled Series" }
        val finalTitle = if (selection.importTitle || base.title.isBlank()) rawTitle else base.title
        val year = parseReleaseYear(dto.firstAirDate)
        val roundedRating = String.format(Locale.US, "%.1f", dto.voteAverage ?: 0.0).toDoubleOrNull() ?: 0.0

        val mappedCast = dto.credits?.cast.orEmpty().take(16).map { c ->
            CastMember(
                id = c.id.toString(),
                name = c.name,
                character = c.character.orEmpty(),
                profileUrl = buildImageUrl(c.profilePath, PROFILE_SIZE)
            )
        }

        val primaryPoster = buildImageUrl(dto.posterPath, POSTER_SIZE)
        val primaryBackdrop = buildImageUrl(dto.backdropPath, BACKDROP_SIZE)
        val rawPosters = dto.images?.posters.orEmpty().mapNotNull {
            buildImageUrl(it.filePath, POSTER_SIZE).takeIf(String::isNotBlank)
        }
        val rawBackdrops = dto.images?.backdrops.orEmpty().mapNotNull {
            buildImageUrl(it.filePath, BACKDROP_SIZE).takeIf(String::isNotBlank)
        }
        val posterList = (listOfNotNull(primaryPoster.takeIf { it.isNotBlank() }) + rawPosters).distinct()
        val backdropList = (listOfNotNull(primaryBackdrop.takeIf { it.isNotBlank() }) + rawBackdrops).distinct()

        val mappedCompanies = dto.productionCompanies.orEmpty()
            .mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
        val mappedCountry = dto.productionCountries.orEmpty()
            .firstOrNull()?.name?.trim().orEmpty().ifBlank { base.country.ifBlank { "United States" } }

        val mappedSeasons = if (selection.importSeasons) {
            if (seasonDetails.isNotEmpty()) {
                seasonDetails.filter { it.seasonNumber > 0 }.sortedBy { it.seasonNumber }.map { sDto ->
                    Season(
                        id = "s_${sDto.seasonNumber}",
                        seasonNumber = sDto.seasonNumber,
                        title = sDto.name?.takeIf { it.isNotBlank() } ?: "Season ${sDto.seasonNumber}",
                        overview = sDto.overview.orEmpty(),
                        posterUrl = buildImageUrl(sDto.posterPath, POSTER_SIZE),
                        episodes = sDto.episodes.orEmpty().sortedBy { it.episodeNumber }.map { eDto ->
                            Episode(
                                id = "s${sDto.seasonNumber}_e${eDto.episodeNumber}",
                                seasonNumber = sDto.seasonNumber,
                                episodeNumber = eDto.episodeNumber,
                                title = eDto.name?.takeIf { it.isNotBlank() } ?: "Episode ${eDto.episodeNumber}",
                                overview = eDto.overview.orEmpty(),
                                thumbnailUrl = buildImageUrl(eDto.stillPath, STILL_SIZE),
                                videoUrl = "",
                                runtimeMinutes = eDto.runtime ?: dto.episodeRunTime?.firstOrNull() ?: 0,
                                airDate = eDto.airDate.orEmpty()
                            )
                        }
                    )
                }
            } else {
                dto.seasons.orEmpty().filter { it.seasonNumber > 0 }.sortedBy { it.seasonNumber }.map { sum ->
                    Season(
                        id = "s_${sum.seasonNumber}",
                        seasonNumber = sum.seasonNumber,
                        title = sum.name?.takeIf { it.isNotBlank() } ?: "Season ${sum.seasonNumber}",
                        overview = sum.overview.orEmpty(),
                        posterUrl = buildImageUrl(sum.posterPath, POSTER_SIZE),
                        episodes = emptyList()
                    )
                }
            }
        } else {
            base.seasons
        }

        return base.copy(
            tmdbId = dto.id,
            title = finalTitle,
            originalTitle = dto.originalName?.trim().orEmpty().ifBlank { finalTitle },
            tagline = dto.tagline?.trim().orEmpty().ifBlank { base.tagline },
            normalizedTitle = UrlValidator.normalizeTitle(finalTitle),
            overview = if (selection.importOverview) dto.overview?.trim().orEmpty() else base.overview,
            mediaType = MediaType.SERIES.name,
            releaseYear = if (selection.importYearAndRating) year else base.releaseYear,
            releaseDate = if (selection.importYearAndRating) dto.firstAirDate.orEmpty() else base.releaseDate,
            status = dto.status?.trim().orEmpty().ifBlank { "Returning Series" },
            rating = if (selection.importYearAndRating) roundedRating else base.rating,
            runtimeMinutes = dto.episodeRunTime?.firstOrNull() ?: base.runtimeMinutes,
            contentAdvisory = base.contentAdvisory.ifBlank { "U/A 16+" },
            language = dto.originalLanguage ?: base.language,
            country = mappedCountry,
            companies = mappedCompanies.ifEmpty { base.companies },
            posterUrl = if (selection.importPoster) primaryPoster else base.posterUrl,
            backdropUrl = if (selection.importBackdrop) primaryBackdrop else base.backdropUrl,
            posterGallery = posterList.take(60),
            backdropGallery = backdropList.take(60),
            totalPostersCount = rawPosters.size.coerceAtLeast(posterList.size),
            totalBackdropsCount = rawBackdrops.size.coerceAtLeast(backdropList.size),
            genres = dto.genres?.mapNotNull { it.name.takeIf(String::isNotBlank) } ?: base.genres,
            cast = if (selection.importCast) mappedCast else base.cast,
            trailerUrl = if (selection.importTrailer) extractYoutubeTrailer(dto.videos) else base.trailerUrl,
            seasons = mappedSeasons,
            sourceAttribution = "TMDB",
            importedAt = now,
            updatedAt = now
        )
    }
}

class TmdbRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val api: TmdbApiService = Retrofit.Builder()
        .baseUrl(BuildConfig.TMDB_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApiService::class.java)

    fun resolveApiKey(runtimeOverride: String): String =
        runtimeOverride.trim().ifBlank {
            NhNativeSecurityVault.getCachedTmdbApiKey().ifBlank { BuildConfig.TMDB_API_KEY.trim() }
        }

    suspend fun fetchMovieById(movieId: Int, apiKeyOverride: String = ""): Result<CatalogItem> {
        val key = resolveApiKey(apiKeyOverride)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key is not configured."))
        }
        return runCatching {
            val dto = api.getMovieDetails(movieId, key)
            TmdbMapper.mapMovieToCatalogItem(dto)
        }.recoverCatching { throwable ->
            throw Exception(friendlyTmdbError(throwable))
        }
    }

    suspend fun fetchSeriesById(tvId: Int, apiKeyOverride: String = ""): Result<CatalogItem> {
        val key = resolveApiKey(apiKeyOverride)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key is not configured."))
        }
        return runCatching {
            val dto = api.getTvDetails(tvId, key)
            val seasonsToFetch = dto.seasons.orEmpty()
                .filter { it.seasonNumber > 0 }
                .take(6)
            val seasonDetails = seasonsToFetch.mapNotNull { s ->
                runCatching { api.getTvSeasonDetails(tvId, s.seasonNumber, key) }.getOrNull()
            }
            TmdbMapper.mapTvToCatalogItem(dto, seasonDetails)
        }.recoverCatching { throwable ->
            throw Exception(friendlyTmdbError(throwable))
        }
    }

    suspend fun searchMovies(query: String, apiKeyOverride: String = ""): Result<List<CatalogItem>> {
        val key = resolveApiKey(apiKeyOverride)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key is not configured."))
        }
        return runCatching {
            val resp = api.searchMovies(query.trim(), key)
            resp.results.take(12).map { TmdbMapper.mapMovieToCatalogItem(it) }
        }.recoverCatching { throwable ->
            throw Exception(friendlyTmdbError(throwable))
        }
    }

    suspend fun searchSeries(query: String, apiKeyOverride: String = ""): Result<List<CatalogItem>> {
        val key = resolveApiKey(apiKeyOverride)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key is not configured."))
        }
        return runCatching {
            val resp = api.searchTv(query.trim(), key)
            resp.results.take(12).map { TmdbMapper.mapTvToCatalogItem(it) }
        }.recoverCatching { throwable ->
            throw Exception(friendlyTmdbError(throwable))
        }
    }

    suspend fun fetchCastAndDetailsForItem(item: CatalogItem, apiKeyOverride: String = ""): Result<CatalogItem> {
        val key = resolveApiKey(apiKeyOverride)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key not configured"))
        }
        return runCatching {
            val resolvedId = item.tmdbId ?: run {
                if (item.isSeries) {
                    api.searchTv(item.title.trim(), key).results.firstOrNull()?.id
                } else {
                    api.searchMovies(item.title.trim(), key).results.firstOrNull()?.id
                }
            } ?: error("No matching TMDB ID found for ${item.title}")

            if (item.isSeries) {
                fetchSeriesById(resolvedId, apiKeyOverride).getOrThrow()
            } else {
                fetchMovieById(resolvedId, apiKeyOverride).getOrThrow()
            }
        }
    }


    suspend fun fetchActorProfileDetails(
        member: CastMember,
        apiKeyOverride: String = ""
    ): Result<ActorProfileDetails> {
        val key = resolveApiKey(apiKeyOverride)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key not configured"))
        }
        return runCatching {
            val numericId = member.id.trim().toIntOrNull()
            val resolvedPersonId = numericId ?: run {
                api.searchPerson(member.name.trim(), key).results.firstOrNull()?.id
            } ?: error("No TMDB actor profile found for ${member.name}")

            val dto = api.getPersonDetails(resolvedPersonId, key, "images")
            val primaryImg = TmdbMapper.buildImageUrl(dto.profilePath, "h632")
                .ifBlank { member.profileUrl }
            val galleryImgs = dto.images?.profiles.orEmpty().mapNotNull { img ->
                TmdbMapper.buildImageUrl(img.filePath, "h632").takeIf { it.isNotBlank() }
            }
            val allPhotos = (listOfNotNull(primaryImg.takeIf { it.isNotBlank() }) + galleryImgs).distinct()

            val genderText = when (dto.gender) {
                1 -> "Female"
                2 -> "Male"
                3 -> "Non-Binary"
                else -> "Not Specified"
            }

            ActorProfileDetails(
                personId = dto.id,
                name = dto.name?.trim().orEmpty().ifBlank { member.name },
                characterRole = member.character,
                biography = dto.biography?.trim().orEmpty(),
                birthday = dto.birthday?.trim().orEmpty(),
                deathday = dto.deathday?.trim().orEmpty(),
                placeOfBirth = dto.placeOfBirth?.trim().orEmpty(),
                knownForDepartment = dto.knownForDepartment?.trim().orEmpty().ifBlank { "Acting" },
                genderLabel = genderText,
                popularity = dto.popularity ?: 0.0,
                primaryProfileUrl = primaryImg,
                profileGallery = allPhotos,
                alsoKnownAs = dto.alsoKnownAs.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct()
            )
        }
    }

suspend fun searchCombinedMoviesAndSeries(
        query: String,
        apiKeyOverride: String = ""
    ): Result<List<CatalogItem>> {
        val clean = query.trim()
        if (clean.isEmpty()) return Result.success(emptyList())
        val key = resolveApiKey(apiKeyOverride)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key not configured"))
        }
        return runCatching {
            val movies = runCatching {
                api.searchMovies(clean, key).results.take(10).map { TmdbMapper.mapMovieToCatalogItem(it) }
            }.getOrDefault(emptyList())
            val series = runCatching {
                api.searchTv(clean, key).results.take(10).map { TmdbMapper.mapTvToCatalogItem(it) }
            }.getOrDefault(emptyList())
            (movies + series)
                .filter { it.title.isNotBlank() }
                .distinctBy { "${it.mediaType}_${it.tmdbId ?: it.normalizedTitle}" }
        }
    }

    suspend fun fetchUpcomingMoviesAndSeries(
        apiKeyOverride: String = ""
    ): Result<List<CatalogItem>> {
        val key = resolveApiKey(apiKeyOverride)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key not configured"))
        }
        return runCatching {
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(java.util.Date(System.currentTimeMillis() + 86_400_000L))
            val upcomingMovies1 = runCatching {
                api.discoverUpcomingMovies(apiKey = key, minReleaseDate = todayStr, page = 1).results
                    .map { TmdbMapper.mapMovieToCatalogItem(it).copy(status = "Upcoming") }
            }.getOrDefault(emptyList())
            val upcomingMovies2 = runCatching {
                api.getUpcomingMovies(apiKey = key, page = 1).results
                    .map { TmdbMapper.mapMovieToCatalogItem(it).copy(status = "Upcoming") }
            }.getOrDefault(emptyList())
            val upcomingSeries = runCatching {
                api.discoverUpcomingSeries(apiKey = key, minAirDate = todayStr, page = 1).results
                    .map { TmdbMapper.mapTvToCatalogItem(it).copy(status = "Upcoming") }
            }.getOrDefault(emptyList())

            (upcomingMovies1 + upcomingSeries + upcomingMovies2)
                .filter { it.posterUrl.isNotBlank() || it.backdropUrl.isNotBlank() }
                .distinctBy { "${it.mediaType}_${it.tmdbId ?: it.normalizedTitle}" }
        }
    }


    suspend fun fetchIndianMoviePosters(apiKeyOverride: String = ""): Result<List<String>> {
        val key = resolveApiKey(apiKeyOverride)
        if (key.isBlank()) {
            return Result.failure(IllegalStateException("TMDB API key not configured"))
        }
        return runCatching {
            val page1 = api.discoverIndianMovies(apiKey = key, page = 1).results
            val urls = page1.mapNotNull { dto ->
                TmdbMapper.buildImageUrl(dto.posterPath, TmdbMapper.POSTER_SIZE).takeIf { it.isNotBlank() }
            }.distinct()
            urls
        }
    }

    private fun friendlyTmdbError(t: Throwable): String = when (t) {
        is HttpException -> when (t.code()) {
            401 -> "TMDB API key rejected (HTTP 401). Check your TMDB v3 API key in Admin Settings."
            404 -> "No TMDB record found for that ID (HTTP 404)."
            429 -> "TMDB rate limit reached (HTTP 429). Please wait a moment and retry."
            else -> "TMDB server error (HTTP ${t.code()})."
        }
        else -> t.message ?: "Network error while contacting TMDB."
    }
}
