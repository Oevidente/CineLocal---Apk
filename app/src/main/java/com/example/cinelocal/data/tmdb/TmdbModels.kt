package com.example.cinelocal.data.tmdb

import com.google.gson.annotations.SerializedName

data class TmdbSearchResponse<T>(
    val page: Int,
    val results: List<T>,
    @SerializedName("total_results") val totalResults: Int,
    @SerializedName("total_pages") val totalPages: Int
)

data class TmdbMovieSearchResult(
    val id: Long,
    val title: String,
    @SerializedName("original_title") val originalTitle: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val voteAverage: Float?
)

data class TmdbTvSearchResult(
    val id: Long,
    val name: String,
    @SerializedName("original_name") val originalName: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Float?
)

data class TmdbGenre(
    val id: Int,
    val name: String
)

data class TmdbMovieDetails(
    val id: Long,
    val title: String,
    @SerializedName("original_title") val originalTitle: String?,
    val overview: String?,
    val tagline: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val voteAverage: Float?,
    val genres: List<TmdbGenre>?
)

data class TmdbTvDetails(
    val id: Long,
    val name: String,
    @SerializedName("original_name") val originalName: String?,
    val overview: String?,
    val tagline: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("vote_average") val voteAverage: Float?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int?,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int?,
    val genres: List<TmdbGenre>?
)
