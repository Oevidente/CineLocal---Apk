package com.example.cinelocal.data.subtitles

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.File
import java.util.concurrent.TimeUnit

interface OpenSubtitlesApi {

    @GET("subtitles")
    suspend fun searchSubtitles(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = "CineLocal v1.0",
        @Query("query") query: String,
        @Query("languages") languages: String = "pt,en",
        @Query("order_by") orderBy: String = "download_count",
        @Query("order_direction") orderDirection: String = "desc"
    ): OpenSubtitlesSearchResponse

    @POST("download")
    suspend fun requestDownloadLink(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String = "CineLocal v1.0",
        @Body request: SubtitleDownloadRequest
    ): SubtitleDownloadResponse
}

class OpenSubtitlesClient(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val api: OpenSubtitlesApi = Retrofit.Builder()
        .baseUrl("https://api.opensubtitles.com/api/v1/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OpenSubtitlesApi::class.java)

    suspend fun searchSubtitles(
        apiKey: String,
        query: String,
        languages: String = "pt-br,pt,en"
    ): List<SubtitleItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || query.isBlank()) return@withContext emptyList()

        try {
            val response = api.searchSubtitles(
                apiKey = apiKey.trim(),
                query = query.trim(),
                languages = languages
            )

            response.data.mapNotNull { item ->
                val firstFile = item.attributes.files.firstOrNull() ?: return@mapNotNull null
                SubtitleItem(
                    id = item.id,
                    fileId = firstFile.fileId,
                    fileName = firstFile.fileName ?: "${item.attributes.release ?: "legenda"}.srt",
                    language = item.attributes.language,
                    releaseName = item.attributes.release ?: item.attributes.comments ?: firstFile.fileName ?: "Release Desconhecido",
                    downloadCount = item.attributes.downloadCount,
                    rating = item.attributes.ratings,
                    hearingImpaired = item.attributes.hearingImpaired
                )
            }
        } catch (e: Exception) {
            Log.e("OpenSubtitlesClient", "Erro ao buscar legendas: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun downloadAndConvertSubtitle(
        apiKey: String,
        fileId: Long,
        fileName: String
    ): Pair<File, String>? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null

        try {
            // 1. Obter link de download
            val downloadResponse = api.requestDownloadLink(
                apiKey = apiKey.trim(),
                request = SubtitleDownloadRequest(fileId = fileId)
            )

            val downloadUrl = downloadResponse.link
            if (downloadUrl.isBlank()) return@withContext null

            // 2. Fazer download do arquivo SRT
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "CineLocal v1.0")
                .build()

            val call = httpClient.newCall(request).execute()
            if (!call.isSuccessful) return@withContext null

            val body = call.body ?: return@withContext null
            val srtContent = SubtitleUtils.readStreamWithEncodingFallback(body.byteStream())

            // 3. Converter para WebVTT
            val vttContent = SubtitleUtils.convertSrtToVtt(srtContent)

            // 4. Salvar no cache
            val baseName = fileName.substringBeforeLast('.')
            val file = SubtitleUtils.saveVttToCache(context, "$baseName.vtt", vttContent)

            Pair(file, vttContent)
        } catch (e: Exception) {
            Log.e("OpenSubtitlesClient", "Erro ao baixar legenda: ${e.message}", e)
            null
        }
    }
}
