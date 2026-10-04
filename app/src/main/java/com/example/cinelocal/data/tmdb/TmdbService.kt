package com.example.cinelocal.data.tmdb

import android.util.Log
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface TmdbApi {

    @GET("search/movie")
    suspend fun searchMovie(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "pt-BR",
        @Query("year") year: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false
    ): Response<TmdbSearchResponse<TmdbMovieSearchResult>>

    @GET("search/tv")
    suspend fun searchTv(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("language") language: String = "pt-BR",
        @Query("first_air_date_year") year: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false
    ): Response<TmdbSearchResponse<TmdbTvSearchResult>>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Long,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "pt-BR"
    ): Response<TmdbMovieDetails>

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Long,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "pt-BR"
    ): Response<TmdbTvDetails>
}

object TmdbClient {

    private const val TAG = "TmdbClient"
    const val DEFAULT_API_KEY = "84102d1d052d92ae17d2105e46820056" // Default fallback API Key
    const val IMAGE_BASE_POSTER = "https://image.tmdb.org/t/p/w500"
    const val IMAGE_BASE_BACKDROP = "https://image.tmdb.org/t/p/w1280"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        })
        .build()

    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)

    fun getEffectiveKey(customKey: String?): String {
        return if (!customKey.isNullOrBlank()) customKey.trim() else DEFAULT_API_KEY
    }

    suspend fun enrichMedia(
        item: MediaItemEntity,
        userApiKey: String? = null
    ): MediaItemEntity = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveKey(userApiKey)
        val cleanTitle = item.title.trim()
        if (cleanTitle.isBlank()) return@withContext item

        try {
            if (item.kind == MediaKind.SERIES) {
                val searchRes = api.searchTv(apiKey = apiKey, query = cleanTitle, year = item.year)
                if (searchRes.isSuccessful && !searchRes.body()?.results.isNullOrEmpty()) {
                    val tvResult = searchRes.body()!!.results.first()
                    val detailsRes = api.getTvDetails(tvResult.id, apiKey = apiKey)

                    if (detailsRes.isSuccessful && detailsRes.body() != null) {
                        val d = detailsRes.body()!!
                        val posterUrl = d.posterPath?.let { if (it.startsWith("http")) it else "$IMAGE_BASE_POSTER$it" }
                        val backdropUrl = d.backdropPath?.let { if (it.startsWith("http")) it else "$IMAGE_BASE_BACKDROP$it" }
                        val year = d.firstAirDate?.take(4)?.toIntOrNull() ?: item.year
                        val genreStr = d.genres?.joinToString(", ") { it.name }

                        Log.i(TAG, "TMDb Série enriquecida com sucesso: ${d.name}")
                        return@withContext item.copy(
                            title = if (item.title.length < d.name.length && !item.title.contains("S0")) d.name else item.title,
                            originalTitle = d.originalName ?: item.originalTitle,
                            overview = d.overview?.takeIf { it.isNotBlank() } ?: item.overview,
                            tagline = d.tagline?.takeIf { it.isNotBlank() } ?: item.tagline,
                            posterPath = posterUrl ?: item.posterPath,
                            backdropPath = backdropUrl ?: item.backdropPath,
                            year = year,
                            rating = d.voteAverage ?: item.rating,
                            genres = genreStr ?: item.genres,
                            totalSeasons = d.numberOfSeasons ?: item.totalSeasons,
                            totalEpisodes = d.numberOfEpisodes ?: item.totalEpisodes
                        )
                    }
                }
            } else {
                val searchRes = api.searchMovie(apiKey = apiKey, query = cleanTitle, year = item.year)
                if (searchRes.isSuccessful && !searchRes.body()?.results.isNullOrEmpty()) {
                    val movieResult = searchRes.body()!!.results.first()
                    val detailsRes = api.getMovieDetails(movieResult.id, apiKey = apiKey)

                    if (detailsRes.isSuccessful && detailsRes.body() != null) {
                        val d = detailsRes.body()!!
                        val posterUrl = d.posterPath?.let { if (it.startsWith("http")) it else "$IMAGE_BASE_POSTER$it" }
                        val backdropUrl = d.backdropPath?.let { if (it.startsWith("http")) it else "$IMAGE_BASE_BACKDROP$it" }
                        val year = d.releaseDate?.take(4)?.toIntOrNull() ?: item.year
                        val genreStr = d.genres?.joinToString(", ") { it.name }

                        Log.i(TAG, "TMDb Filme enriquecido com sucesso: ${d.title}")
                        return@withContext item.copy(
                            title = d.title.ifBlank { item.title },
                            originalTitle = d.originalTitle ?: item.originalTitle,
                            overview = d.overview?.takeIf { it.isNotBlank() } ?: item.overview,
                            tagline = d.tagline?.takeIf { it.isNotBlank() } ?: item.tagline,
                            posterPath = posterUrl ?: item.posterPath,
                            backdropPath = backdropUrl ?: item.backdropPath,
                            year = year,
                            rating = d.voteAverage ?: item.rating,
                            genres = genreStr ?: item.genres
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar metadados TMDb para '${item.title}': ${e.message}")
        }

        item
    }
}
