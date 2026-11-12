package com.moviebox.app

import com.moviebox.app.data.model.CatalogItem
import com.moviebox.app.data.model.Episode
import com.moviebox.app.data.model.MediaType
import com.moviebox.app.data.model.Season
import com.moviebox.app.data.model.TmdbImportSelection
import com.moviebox.app.data.model.UserPresenceRecord
import com.moviebox.app.data.remote.TmdbCastDto
import com.moviebox.app.data.remote.TmdbCreditsDto
import com.moviebox.app.data.remote.TmdbGenreDto
import com.moviebox.app.data.remote.TmdbMapper
import com.moviebox.app.data.remote.TmdbMovieDto
import com.moviebox.app.data.remote.TmdbVideoDto
import com.moviebox.app.data.remote.TmdbVideosResponseDto
import com.moviebox.app.finder.DirectStreamResolver
import com.moviebox.app.finder.VideoExtractor
import com.moviebox.app.player.PlayHeaders
import com.moviebox.app.util.StreamFormat
import com.moviebox.app.util.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieBoxUnitTests {

    @Test
    fun validateStreamUrl_acceptsAuthorizedHttpsHlsMp4AndGofileMkvUrls() {
        val hls = UrlValidator.validateStreamUrl("https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8")
        assertTrue(hls.isValid)
        assertEquals(StreamFormat.HLS, hls.format)

        val mp4 = UrlValidator.validateStreamUrl("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
        assertTrue(mp4.isValid)
        assertEquals(StreamFormat.PROGRESSIVE, mp4.format)

        val gofileMkv = UrlValidator.validateStreamUrl(
            "https://file-eu-par-2.gofile.io/download/web/e58fb61c-a772-4529-b6a6-41834000187d/Indubala%20Bhabhi%20Naya%20Devar%20(2026)%20App%20Conntent%20Hindi%20Short%20Video%20720p%20HDRip%20x264%20AAC%20150MB.mkv"
        )
        assertTrue(gofileMkv.isValid)
        assertEquals(StreamFormat.PROGRESSIVE, gofileMkv.format)
    }

    @Test
    fun playHeaders_buildsGofileStreamHeadersAndWebsiteToken() {
        val rawUrl = "https://file-eu-par-2.gofile.io/download/web/e58fb61c-a772-4529-b6a6-41834000187d/Indubala%20Bhabhi%20Naya%20Devar%20(2026)%20App%20Conntent%20Hindi%20Short%20Video%20720p%20HDRip%20x264%20AAC%20150MB.mkv"
        PlayHeaders.saveGofileTokenCookie("test_token_123", rawUrl)
        val headers = PlayHeaders.buildHeadersForUrl(rawUrl)
        assertEquals("https://gofile.io/", headers["Referer"])
        assertTrue(headers["Cookie"].orEmpty().contains("accountToken=test_token_123"))
        assertNull(headers["Authorization"])
        assertTrue(VideoExtractor.looksLikeMedia(rawUrl))
        assertEquals("MKV", VideoExtractor.typeOf(rawUrl))

        val wt = DirectStreamResolver.generateGofileWebsiteToken(
            userAgent = PlayHeaders.userAgent,
            accountToken = "test_token_123"
        )
        assertEquals(64, wt.length)
    }

    @Test
    fun validateStreamUrl_rejectsYouTubeWatchLinksAsDirectStreams() {
        val yt = UrlValidator.validateStreamUrl("https://www.youtube.com/watch?v=uYPbbksJxIg")
        assertFalse(yt.isValid)
        assertNotNull(yt.errorMessage)
        assertTrue(yt.errorMessage!!.contains("YouTube"))
    }

    @Test
    fun validateTrailerUrl_acceptsYouTubeAndHttpsTrailerLinks() {
        assertTrue(UrlValidator.validateTrailerUrl("https://www.youtube.com/watch?v=uYPbbksJxIg"))
        assertFalse(UrlValidator.validateTrailerUrl("not_a_url"))
    }

    @Test
    fun validateSeasonEpisodeOrdering_detectsDuplicateSeasonsAndEpisodes() {
        val validSeasons = listOf(
            Season(
                id = "s1",
                seasonNumber = 1,
                title = "Season 1",
                episodes = listOf(
                    Episode(id = "e1", seasonNumber = 1, episodeNumber = 1, title = "Ep 1"),
                    Episode(id = "e2", seasonNumber = 1, episodeNumber = 2, title = "Ep 2")
                )
            )
        )
        assertNull(UrlValidator.validateSeasonEpisodeOrdering(validSeasons))

        val duplicateEpisodes = listOf(
            Season(
                id = "s1",
                seasonNumber = 1,
                title = "Season 1",
                episodes = listOf(
                    Episode(id = "e1", seasonNumber = 1, episodeNumber = 1, title = "Ep 1"),
                    Episode(id = "e2", seasonNumber = 1, episodeNumber = 1, title = "Duplicate Ep 1")
                )
            )
        )
        assertNotNull(UrlValidator.validateSeasonEpisodeOrdering(duplicateEpisodes))
    }

    @Test
    fun tmdbMapper_mapsMovieDetailsAndLeavesVideoUrlBlank() {
        val dto = TmdbMovieDto(
            id = 872585,
            title = "Oppenheimer",
            overview = "The story of J. Robert Oppenheimer.",
            releaseDate = "2023-07-19",
            voteAverage = 8.8,
            runtime = 180,
            posterPath = "/ptpr0kGAckfQkJeJIt8st5dglvd.jpg",
            backdropPath = "/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
            genres = listOf(TmdbGenreDto(18, "Drama")),
            credits = TmdbCreditsDto(
                cast = listOf(
                    TmdbCastDto(id = 1, name = "Cillian Murphy", character = "J. Robert Oppenheimer", profilePath = "/profile.jpg")
                )
            ),
            videos = TmdbVideosResponseDto(
                results = listOf(
                    TmdbVideoDto(id = "v1", key = "uYPbbksJxIg", name = "Official Trailer", site = "YouTube", type = "Trailer", official = true)
                )
            )
        )

        val mapped = TmdbMapper.mapMovieToCatalogItem(
            dto = dto,
            selection = TmdbImportSelection()
        )

        assertEquals("Oppenheimer", mapped.title)
        assertEquals(2023, mapped.releaseYear)
        assertEquals(8.8, mapped.rating, 0.01)
        assertEquals("https://image.tmdb.org/t/p/w500/ptpr0kGAckfQkJeJIt8st5dglvd.jpg", mapped.posterUrl)
        assertEquals("https://www.youtube.com/watch?v=uYPbbksJxIg", mapped.trailerUrl)
        assertEquals("", mapped.videoUrl)
    }

    @Test
    fun filterCatalog_filtersBySearchQueryCategoryYearAndRating() {
        val items = listOf(
            CatalogItem(
                id = "1",
                title = "Oppenheimer",
                normalizedTitle = "oppenheimer",
                mediaType = MediaType.MOVIE.name,
                releaseYear = 2023,
                rating = 8.8,
                genres = listOf("Drama", "History"),
                categoryIds = listOf("cat_drama"),
                published = true
            ),
            CatalogItem(
                id = "2",
                title = "Sintel",
                normalizedTitle = "sintel",
                mediaType = MediaType.MOVIE.name,
                releaseYear = 2010,
                rating = 7.5,
                genres = listOf("Fantasy"),
                categoryIds = listOf("cat_fantasy"),
                published = true
            )
        )

        val byQuery = UrlValidator.filterCatalog(
            items = items,
            query = "oppen"
        )
        assertEquals(1, byQuery.size)
        assertEquals("Oppenheimer", byQuery.first().title)

        val byRating = UrlValidator.filterCatalog(
            items = items,
            minRating = 8.0,
            sortBy = "RATING"
        )
        assertEquals(1, byRating.size)
        assertEquals("Oppenheimer", byRating.first().title)
    }

    @Test
    fun generateFiveDigitShareCode_producesDeterministicFiveDigitNumericCode() {
        val code1 = UserPresenceRecord.generateFiveDigitShareCode("a8f4c2910b3e7d12")
        val code2 = UserPresenceRecord.generateFiveDigitShareCode("a8f4c2910b3e7d12")
        assertEquals(5, code1.length)
        assertTrue(code1.all { it.isDigit() })
        assertEquals(code1, code2)
    }

    @Test
    fun vipAccess_gatesVipMoviesUntilPointsThresholdReached() {
        val vipMovie = CatalogItem(
            id = "movie_vip_1",
            title = "VIP Blockbuster",
            isVip = true,
            vipPointsRequired = 100
        )
        val freeUser = UserPresenceRecord(uid = "u1", points = 50, isVip = false)
        val qualifiedUser = UserPresenceRecord(uid = "u2", points = 100, isVip = false)
        val directVipUser = UserPresenceRecord(uid = "u3", points = 0, isVip = true)

        assertFalse(freeUser.hasVipAccessFor(vipMovie, 100))
        assertTrue(qualifiedUser.hasVipAccessFor(vipMovie, 100))
        assertTrue(directVipUser.hasVipAccessFor(vipMovie, 100))
    }
}
