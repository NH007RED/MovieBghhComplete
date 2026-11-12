package com.moviebox.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos,images",
        @Query("include_image_language") includeImageLanguage: String = "en,null,hi"
    ): TmdbMovieDto

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,videos,images",
        @Query("include_image_language") includeImageLanguage: String = "en,null,hi"
    ): TmdbTvDto

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbSeasonDetailDto

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbSearchResponse<TmdbMovieDto>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbSearchResponse<TmdbTvDto>

    @GET("discover/movie")
    suspend fun discoverIndianMovies(
        @Query("api_key") apiKey: String,
        @Query("with_origin_country") originCountry: String = "IN",
        @Query("region") region: String = "IN",
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse<TmdbMovieDto>

    @GET("movie/upcoming")
    suspend fun getUpcomingMovies(
        @Query("api_key") apiKey: String,
        @Query("region") region: String = "IN",
        @Query("page") page: Int = 1
    ): TmdbSearchResponse<TmdbMovieDto>

    @GET("discover/movie")
    suspend fun discoverUpcomingMovies(
        @Query("api_key") apiKey: String,
        @Query("primary_release_date.gte") minReleaseDate: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse<TmdbMovieDto>

    @GET("discover/tv")
    suspend fun discoverUpcomingSeries(
        @Query("api_key") apiKey: String,
        @Query("first_air_date.gte") minAirDate: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1
    ): TmdbSearchResponse<TmdbTvDto>

    @GET("person/{person_id}")
    suspend fun getPersonDetails(
        @Path("person_id") personId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "images"
    ): TmdbPersonDetailDto

    @GET("search/person")
    suspend fun searchPerson(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbSearchResponse<TmdbPersonDetailDto>
}
