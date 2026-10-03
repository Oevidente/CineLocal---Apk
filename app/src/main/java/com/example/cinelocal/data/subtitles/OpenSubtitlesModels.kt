package com.example.cinelocal.data.subtitles

import com.google.gson.annotations.SerializedName

data class OpenSubtitlesSearchResponse(
    @SerializedName("total_pages") val totalPages: Int = 0,
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("data") val data: List<SubtitleData> = emptyList()
)

data class SubtitleData(
    @SerializedName("id") val id: String,
    @SerializedName("type") val type: String,
    @SerializedName("attributes") val attributes: SubtitleAttributes
)

data class SubtitleAttributes(
    @SerializedName("subtitle_id") val subtitleId: String? = null,
    @SerializedName("language") val language: String = "pt-br",
    @SerializedName("download_count") val downloadCount: Int = 0,
    @SerializedName("ratings") val ratings: Float = 0f,
    @SerializedName("votes") val votes: Int = 0,
    @SerializedName("points") val points: Int = 0,
    @SerializedName("release") val release: String? = null,
    @SerializedName("comments") val comments: String? = null,
    @SerializedName("hearing_impaired") val hearingImpaired: Boolean = false,
    @SerializedName("files") val files: List<SubtitleFile> = emptyList(),
    @SerializedName("feature_details") val featureDetails: FeatureDetails? = null
)

data class SubtitleFile(
    @SerializedName("file_id") val fileId: Long,
    @SerializedName("file_name") val fileName: String? = null
)

data class FeatureDetails(
    @SerializedName("feature_id") val featureId: Long? = null,
    @SerializedName("feature_type") val featureType: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("year") val year: Int? = null
)

data class SubtitleDownloadRequest(
    @SerializedName("file_id") val fileId: Long
)

data class SubtitleDownloadResponse(
    @SerializedName("link") val link: String,
    @SerializedName("file_name") val fileName: String? = null,
    @SerializedName("requests") val requests: Int? = null,
    @SerializedName("remaining") val remaining: Int? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("reset_time") val resetTime: String? = null
)

data class SubtitleItem(
    val id: String,
    val fileId: Long,
    val fileName: String,
    val language: String,
    val releaseName: String,
    val downloadCount: Int,
    val rating: Float,
    val hearingImpaired: Boolean
)
