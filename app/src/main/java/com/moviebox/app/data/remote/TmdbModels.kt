package com.moviebox.app.data.remote

import com.google.gson.annotations.SerializedName

data class TmdbSearchResponse<T>(
    @SerializedName("page") val page: Int = 1,
    @SerializedName("results") val results: List<T> = emptyList(),
    @SerializedName("total_results") val totalResults: Int = 0
)

data class TmdbGenreDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = ""
)

data class TmdbCastDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("character") val character: String? = null,
    @SerializedName("profile_path") val profilePath: String? = null
)

data class TmdbCreditsDto(
    @SerializedName("cast") val cast: List<TmdbCastDto> = emptyList()
)

data class TmdbVideoDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("key") val key: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("site") val site: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("official") val official: Boolean = false
)

data class TmdbVideosResponseDto(
    @SerializedName("results") val results: List<TmdbVideoDto> = emptyList()
)

data class TmdbImageFileDto(
    @SerializedName("file_path") val filePath: String? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null
)

data class TmdbImagesDto(
    @SerializedName("posters") val posters: List<TmdbImageFileDto>? = null,
    @SerializedName("backdrops") val backdrops: List<TmdbImageFileDto>? = null
)

data class TmdbCompanyDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null
)

data class TmdbCountryDto(
    @SerializedName("iso_3166_1") val isoCode: String? = null,
    @SerializedName("name") val name: String? = null
)

data class TmdbMovieDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String? = null,
    @SerializedName("original_title") val originalTitle: String? = null,
    @SerializedName("tagline") val tagline: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("runtime") val runtime: Int? = null,
    @SerializedName("budget") val budget: Long? = null,
    @SerializedName("revenue") val revenue: Long? = null,
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("adult") val adult: Boolean? = null,
    @SerializedName("genres") val genres: List<TmdbGenreDto>? = null,
    @SerializedName("production_companies") val productionCompanies: List<TmdbCompanyDto>? = null,
    @SerializedName("production_countries") val productionCountries: List<TmdbCountryDto>? = null,
    @SerializedName("credits") val credits: TmdbCreditsDto? = null,
    @SerializedName("videos") val videos: TmdbVideosResponseDto? = null,
    @SerializedName("images") val images: TmdbImagesDto? = null
)

data class TmdbSeasonSummaryDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 1,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("episode_count") val episodeCount: Int? = null
)

data class TmdbEpisodeDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("episode_number") val episodeNumber: Int = 1,
    @SerializedName("season_number") val seasonNumber: Int = 1,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("still_path") val stillPath: String? = null,
    @SerializedName("air_date") val airDate: String? = null,
    @SerializedName("runtime") val runtime: Int? = null
)

data class TmdbSeasonDetailDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("season_number") val seasonNumber: Int = 1,
    @SerializedName("name") val name: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("episodes") val episodes: List<TmdbEpisodeDto>? = null
)

data class TmdbTvDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("original_name") val originalName: String? = null,
    @SerializedName("tagline") val tagline: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("overview") val overview: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("poster_path") val posterPath: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int>? = null,
    @SerializedName("original_language") val originalLanguage: String? = null,
    @SerializedName("genres") val genres: List<TmdbGenreDto>? = null,
    @SerializedName("production_companies") val productionCompanies: List<TmdbCompanyDto>? = null,
    @SerializedName("production_countries") val productionCountries: List<TmdbCountryDto>? = null,
    @SerializedName("seasons") val seasons: List<TmdbSeasonSummaryDto>? = null,
    @SerializedName("credits") val credits: TmdbCreditsDto? = null,
    @SerializedName("videos") val videos: TmdbVideosResponseDto? = null,
    @SerializedName("images") val images: TmdbImagesDto? = null
)

data class TmdbPersonImagesDto(
    @SerializedName("profiles") val profiles: List<TmdbImageFileDto>? = null
)

data class TmdbPersonDetailDto(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String? = null,
    @SerializedName("biography") val biography: String? = null,
    @SerializedName("birthday") val birthday: String? = null,
    @SerializedName("deathday") val deathday: String? = null,
    @SerializedName("place_of_birth") val placeOfBirth: String? = null,
    @SerializedName("known_for_department") val knownForDepartment: String? = null,
    @SerializedName("gender") val gender: Int? = null,
    @SerializedName("popularity") val popularity: Double? = null,
    @SerializedName("profile_path") val profilePath: String? = null,
    @SerializedName("also_known_as") val alsoKnownAs: List<String>? = null,
    @SerializedName("images") val images: TmdbPersonImagesDto? = null
)
