package com.example.cinelocal.data.subtitles

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.cinelocal.BuildConfig
import com.example.cinelocal.data.model.SubtitleFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
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

    @POST("login")
    suspend fun login(
        @Header("Api-Key") apiKey: String,
        @Header("User-Agent") userAgent: String,
        @Body request: OpenSubtitlesLoginRequest
    ): Response<OpenSubtitlesLoginResponse>

    @GET("subtitles")
    suspend fun searchSubtitles(
        @Header("Api-Key") apiKey: String,
        @Header("Authorization") authorization: String? = null,
        @Header("User-Agent") userAgent: String,
        @Query("query") query: String? = null,
        @Query("moviehash") movieHash: String? = null,
        @Query("season_number") seasonNumber: Int? = null,
        @Query("episode_number") episodeNumber: Int? = null,
        @Query("year") year: Int? = null,
        @Query("languages") languages: String = "pt-br,pt,en",
        @Query("order_by") orderBy: String = "download_count",
        @Query("order_direction") orderDirection: String = "desc"
    ): Response<OpenSubtitlesSearchResponse>

    @POST("download")
    suspend fun requestDownloadLink(
        @Header("Api-Key") apiKey: String,
        @Header("Authorization") authorization: String? = null,
        @Header("User-Agent") userAgent: String,
        @Body request: SubtitleDownloadRequest
    ): Response<SubtitleDownloadResponse>
}

class OpenSubtitlesClient(private val context: Context) {

    private val userAgentString = "CineLocal v${BuildConfig.VERSION_NAME}"

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

    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenTimestampMs: Long = 0L

    private val TOKEN_TTL_MS = 20 * 3600 * 1000L // 20 horas

    suspend fun login(
        apiKey: String,
        username: String,
        password: String
    ): OsResult<OpenSubtitlesLoginResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext OsResult.Error(400, "Configure a chave de API do OpenSubtitles.com nas Configurações.")
        }
        if (username.isBlank() || password.isBlank()) {
            return@withContext OsResult.Error(400, "Informe usuário e senha da sua conta OpenSubtitles.com.")
        }

        try {
            val response = api.login(
                apiKey = apiKey.trim(),
                userAgent = userAgentString,
                request = OpenSubtitlesLoginRequest(username = username.trim(), password = password.trim())
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body != null && !body.token.isNullOrBlank()) {
                    cachedToken = body.token
                    tokenTimestampMs = System.currentTimeMillis()
                    Log.i("OpenSubtitlesClient", "Login realizado com sucesso. Token obtido.")
                    return@withContext OsResult.Success(body)
                } else {
                    return@withContext OsResult.Error(response.code(), body?.message ?: "Resposta de login inválida.")
                }
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Login recusado. Verifique o nome de usuário e a senha."
                    403 -> "Chave de API inválida ou sem permissão."
                    429 -> "Muitas tentativas de login. Aguarde alguns segundos."
                    else -> "Erro no login (HTTP ${response.code()})."
                }
                return@withContext OsResult.Error(response.code(), errorMsg)
            }
        } catch (e: Exception) {
            Log.e("OpenSubtitlesClient", "Falha na requisição de login: ${e.message}", e)
            return@withContext OsResult.Error(null, "Sem conexão com OpenSubtitles: ${e.localizedMessage}")
        }
    }

    private suspend fun ensureValidToken(apiKey: String, username: String, password: String): String? {
        val currentToken = cachedToken
        val isExpired = (System.currentTimeMillis() - tokenTimestampMs) > TOKEN_TTL_MS
        if (currentToken != null && !isExpired) {
            return currentToken
        }
        if (username.isNotBlank() && password.isNotBlank()) {
            val result = login(apiKey, username, password)
            if (result is OsResult.Success) {
                return result.data.token
            }
        }
        return null
    }

    suspend fun searchSubtitles(
        apiKey: String,
        username: String = "",
        password: String = "",
        query: String? = null,
        videoUri: Uri? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        year: Int? = null,
        languages: String = "pt-br,pt,en"
    ): OsResult<List<SubtitleItem>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext OsResult.Error(400, "Configure a chave de API do OpenSubtitles.com.")
        }

        val token = ensureValidToken(apiKey, username, password)
        val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else null

        // Tentar buscar por MovieHash se videoUri estiver disponível
        var movieHash: String? = null
        if (videoUri != null) {
            movieHash = SubtitleUtils.computeMovieHash(context, videoUri)
        }

        try {
            var response = api.searchSubtitles(
                apiKey = apiKey.trim(),
                authorization = authHeader,
                userAgent = userAgentString,
                query = query?.trim()?.takeIf { it.isNotBlank() },
                movieHash = movieHash,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                year = year,
                languages = languages
            )

            // Tratar 401: renovar token e tentar novamente uma única vez
            if (response.code() == 401 && username.isNotBlank() && password.isNotBlank()) {
                cachedToken = null
                val newToken = ensureValidToken(apiKey, username, password)
                if (newToken != null) {
                    response = api.searchSubtitles(
                        apiKey = apiKey.trim(),
                        authorization = "Bearer $newToken",
                        userAgent = userAgentString,
                        query = query?.trim()?.takeIf { it.isNotBlank() },
                        movieHash = movieHash,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        year = year,
                        languages = languages
                    )
                }
            }

            if (response.isSuccessful) {
                val searchBody = response.body() ?: return@withContext OsResult.Success(emptyList())
                val items = searchBody.data.mapNotNull { item ->
                    val firstFile = item.attributes.files.firstOrNull() ?: return@mapNotNull null
                    SubtitleItem(
                        id = item.id,
                        fileId = firstFile.fileId,
                        fileName = firstFile.fileName ?: "${item.attributes.release ?: "legenda"}.srt",
                        language = item.attributes.language,
                        releaseName = item.attributes.release ?: item.attributes.comments ?: firstFile.fileName ?: "Release Desconhecido",
                        downloadCount = item.attributes.downloadCount,
                        rating = item.attributes.ratings,
                        hearingImpaired = item.attributes.hearingImpaired,
                        isHashMatch = item.attributes.moviehashMatch
                    )
                }
                return@withContext OsResult.Success(items)
            } else {
                val errorMsg = when (response.code()) {
                    401 -> "Login recusado ou expirado. Verifique usuário e senha no OpenSubtitles."
                    403 -> "Chave de API inválida ou sem permissão."
                    406 -> "Cota diária de requisições ou downloads atingida."
                    429 -> "Muitas requisições. Tente novamente em alguns segundos."
                    else -> "Erro na busca OpenSubtitles (HTTP ${response.code()})."
                }
                return@withContext OsResult.Error(response.code(), errorMsg)
            }
        } catch (e: Exception) {
            Log.e("OpenSubtitlesClient", "Erro na busca de legendas: ${e.message}", e)
            return@withContext OsResult.Error(null, "OpenSubtitles indisponível. Verifique sua conexão.")
        }
    }

    suspend fun downloadAndConvertSubtitle(
        apiKey: String,
        username: String = "",
        password: String = "",
        fileId: Long,
        fileName: String,
        episodeId: String,
        language: String,
        releaseName: String
    ): OsResult<SubtitleFileEntity> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext OsResult.Error(400, "Configure a chave de API do OpenSubtitles.com.")
        }

        val token = ensureValidToken(apiKey, username, password)
        val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else null

        try {
            // 1. Obter link de download
            var downloadResponse = api.requestDownloadLink(
                apiKey = apiKey.trim(),
                authorization = authHeader,
                userAgent = userAgentString,
                request = SubtitleDownloadRequest(fileId = fileId)
            )

            // Se der 401, tenta relogar e repetir uma vez
            if (downloadResponse.code() == 401 && username.isNotBlank() && password.isNotBlank()) {
                cachedToken = null
                val newToken = ensureValidToken(apiKey, username, password)
                if (newToken != null) {
                    downloadResponse = api.requestDownloadLink(
                        apiKey = apiKey.trim(),
                        authorization = "Bearer $newToken",
                        userAgent = userAgentString,
                        request = SubtitleDownloadRequest(fileId = fileId)
                    )
                }
            }

            if (!downloadResponse.isSuccessful) {
                val code = downloadResponse.code()
                val errorMsg = when (code) {
                    401 -> "É necessário realizar login com usuário e senha para baixar legendas."
                    403 -> "Chave de API inválida ou sem cota."
                    406 -> "Cota diária de downloads esgotada no OpenSubtitles.com."
                    429 -> "Limite de requisições por segundo atingido. Aguarde alguns instantes."
                    else -> "Falha ao solicitar link de download (HTTP $code)."
                }
                return@withContext OsResult.Error(code, errorMsg)
            }

            val body = downloadResponse.body()
            val downloadUrl = body?.link
            if (downloadUrl.isNullOrBlank()) {
                return@withContext OsResult.Error(
                    downloadResponse.code(),
                    body?.message ?: "Link de download não retornado pela API."
                )
            }

            // 2. Fazer download do arquivo SRT
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", userAgentString)
                .build()

            val call = httpClient.newCall(request).execute()
            if (!call.isSuccessful) {
                return@withContext OsResult.Error(call.code, "Erro ao baixar arquivo SRT (HTTP ${call.code}).")
            }

            val responseBody = call.body ?: return@withContext OsResult.Error(null, "Corpo do arquivo de legenda vazio.")
            val srtContent = SubtitleUtils.readStreamWithEncodingFallback(responseBody.byteStream())

            // 3. Converter para WebVTT
            val vttContent = SubtitleUtils.convertSrtToVtt(srtContent)

            // 4. Salvar persistentemente no armazenamento interno em filesDir/subtitles/<episodeId>/
            val baseName = fileName.substringBeforeLast('.')
            val savedFile = SubtitleUtils.saveVttToPersistentStorage(
                context = context,
                episodeId = episodeId,
                filename = "$baseName.vtt",
                vttContent = vttContent
            )

            val entity = SubtitleFileEntity(
                episodeId = episodeId,
                language = language.ifBlank { "pt-BR" },
                label = releaseName.ifBlank { fileName },
                filePath = savedFile.absolutePath,
                source = "opensubtitles",
                osFileId = fileId
            )

            Log.i("OpenSubtitlesClient", "Legenda salva com sucesso em: ${savedFile.absolutePath}")
            return@withContext OsResult.Success(entity)

        } catch (e: Exception) {
            Log.e("OpenSubtitlesClient", "Erro durante o download da legenda: ${e.message}", e)
            return@withContext OsResult.Error(null, "Erro no download: ${e.localizedMessage}")
        }
    }
}
