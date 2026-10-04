package com.example.cinelocal.cast

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.example.cinelocal.data.torrent.LocalNetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ProxyMediaSource(
    val uri: Uri,
    val mimeType: String,
    val totalLength: Long
)

class MediaProxyServer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    var port: Int = 8899
        private set

    @Volatile
    var isRunning = false
        private set

    private val mediaSources = ConcurrentHashMap<String, ProxyMediaSource>()
    private val subtitleSources = ConcurrentHashMap<String, String>() // token -> WebVTT string
    private val iptvSources = ConcurrentHashMap<String, String>() // token -> channelUrl

    // Circular log buffer for diagnostic screen
    val recentLogs = Collections.synchronizedList(mutableListOf<String>())

    fun log(msg: String) {
        Log.d("MediaProxyServer", msg)
        synchronized(recentLogs) {
            if (recentLogs.size >= 120) recentLogs.removeAt(0)
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            recentLogs.add("[$timeStr] $msg")
        }
    }

    fun clearLogs() {
        recentLogs.clear()
    }

    fun registerMedia(uri: Uri, mimeType: String): String {
        ensureStarted()
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        val size = calculateFileSize(uri)
        val uriStr = uri.toString()
        val safeMime = when {
            mimeType.contains("matroska", ignoreCase = true) || uriStr.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
            mimeType.contains("webm", ignoreCase = true) || uriStr.endsWith(".webm", ignoreCase = true) -> "video/webm"
            mimeType.contains("mpegurl", ignoreCase = true) || mimeType.contains("m3u8", ignoreCase = true) -> "application/x-mpegURL"
            mimeType.isBlank() || mimeType == "application/octet-stream" -> "video/mp4"
            else -> mimeType
        }
        mediaSources[token] = ProxyMediaSource(uri, safeMime, size)
        val ip = getDeviceIpAddress()
        val ext = when {
            safeMime.contains("matroska") -> "mkv"
            safeMime.contains("webm") -> "webm"
            else -> "mp4"
        }
        val url = "http://$ip:$port/m/$token.$ext"
        log("Mídia registrada: token=$token URI=$uri size=$size mime=$safeMime -> URL=$url")
        return url
    }

    fun registerSubtitle(vttContent: String): String {
        ensureStarted()
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        subtitleSources[token] = vttContent
        val ip = getDeviceIpAddress()
        val url = "http://$ip:$port/s/$token.vtt"
        log("Legenda registrada: token=$token -> URL=$url")
        return url
    }

    fun registerIptvUrl(channelUrl: String): String {
        ensureStarted()
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        iptvSources[token] = channelUrl
        val ip = getDeviceIpAddress()
        val ext = if (channelUrl.contains(".m3u8", ignoreCase = true)) "m3u8" else "ts"
        val url = "http://$ip:$port/iptv/$token.$ext"
        log("Canal IPTV registrado para proxy local: token=$token -> Original=$channelUrl (Proxy: $url)")
        return url
    }

    fun clearRegistrations() {
        mediaSources.clear()
        subtitleSources.clear()
        iptvSources.clear()
    }

    @Synchronized
    fun ensureStarted() {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return
        start()
    }

    @Synchronized
    fun start() {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return
        val portsToTry = listOf(8899, 8080, 9090, 0)
        for (p in portsToTry) {
            try {
                serverSocket = ServerSocket(p)
                port = serverSocket?.localPort ?: p
                isRunning = true
                listenOnSocket()
                val ip = getDeviceIpAddress()
                log("Servidor HTTP iniciado com sucesso no IP $ip na porta $port")
                return
            } catch (e: Exception) {
                log("Tentativa de iniciar na porta $p falhou: ${e.message}")
            }
        }
        log("FALHA CRÍTICA: Não foi possível vincular o servidor HTTP em nenhuma porta.")
    }

    private fun listenOnSocket() {
        serverJob?.cancel()
        serverJob = scope.launch {
            while (isActive && isRunning) {
                try {
                    val client = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        clearRegistrations()
        log("Servidor HTTP parado.")
    }

    fun getDeviceIpAddress(): String {
        return LocalNetworkUtils.getLocalIpAddress(context)
    }

    suspend fun pingSelf(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        ensureStarted()
        val ip = getDeviceIpAddress()
        val pingUrl = "http://$ip:$port/ping"
        try {
            val conn = (URL(pingUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            val code = conn.responseCode
            val text = conn.inputStream.bufferedReader().readText()
            if (code == 200 && text.trim() == "PONG") {
                log("Teste Ping local bem-sucedido ($pingUrl -> HTTP 200 PONG)")
                Pair(true, "Servidor operando normalmente no IP $ip na porta $port")
            } else {
                log("Teste Ping respondeu com código diferente ($pingUrl -> HTTP $code: $text)")
                Pair(false, "Servidor respondeu HTTP $code em vez de 200 OK")
            }
        } catch (e: Exception) {
            log("Erro no teste Ping local ($pingUrl): ${e.message}")
            Pair(false, "Falha de conexão com o servidor local ($ip:$port): ${e.localizedMessage}")
        }
    }

    private fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "desconhecido"
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val out = BufferedOutputStream(s.getOutputStream())

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0].uppercase()
                val path = parts[1]

                var rangeHeader: String? = null
                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                    line = reader.readLine()
                }

                if (method == "OPTIONS") {
                    sendCorsPreflight(out)
                    return
                }

                if (path == "/ping") {
                    sendPong(out)
                    return
                }

                when {
                    path.startsWith("/m/") -> {
                        val token = path.removePrefix("/m/").substringBefore(".").substringBefore("?").substringBefore("/")
                        serveMedia(out, token, method, rangeHeader, clientIp)
                    }
                    path.startsWith("/s/") -> {
                        val token = path.removePrefix("/s/").substringBefore(".").substringBefore("?")
                        serveSubtitle(out, token, method)
                    }
                    path.startsWith("/iptv_res") -> {
                        val query = path.substringAfter("?", "")
                        serveIptvResource(out, query, method, rangeHeader)
                    }
                    path.startsWith("/iptv/") -> {
                        val token = path.removePrefix("/iptv/").substringBefore(".").substringBefore("?")
                        serveIptvProxy(out, token, method)
                    }
                    else -> {
                        sendNotFound(out)
                    }
                }
            }
        } catch (e: Exception) {
            // Sockets normais encerrados pelo receptor
        }
    }

    private fun sendPong(out: OutputStream) {
        val res = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: 4\r\n\r\nPONG"
        out.write(res.toByteArray())
        out.flush()
    }

    private fun sendCorsPreflight(out: OutputStream) {
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin, User-Agent, X-Requested-With, Authorization\r\n" +
            "Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges, Content-Type\r\n" +
            "Access-Control-Max-Age: 86400\r\n" +
            "Content-Length: 0\r\n\r\n"
        out.write(headers.toByteArray())
        out.flush()
    }

    private fun sendNotFound(out: OutputStream) {
        val res = "HTTP/1.1 404 Not Found\r\nAccess-Control-Allow-Origin: *\r\nContent-Length: 0\r\n\r\n"
        out.write(res.toByteArray())
        out.flush()
    }

    private fun serveSubtitle(out: OutputStream, token: String, method: String) {
        val vtt = subtitleSources[token]
        if (vtt == null) {
            log("Legenda token=$token não encontrada (404)")
            sendNotFound(out)
            return
        }

        val bytes = vtt.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/vtt; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin, User-Agent, X-Requested-With, Authorization\r\n" +
            "Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges, Content-Type\r\n" +
            "Connection: close\r\n\r\n"

        out.write(headers.toByteArray())
        if (method != "HEAD") {
            out.write(bytes)
        }
        out.flush()
        log("Legenda servida com sucesso: token=$token (${bytes.size} bytes)")
    }

    private fun rewriteHlsPlaylist(playlist: String, baseUrl: URL, ip: String, port: Int): String {
        val lines = playlist.lines()
        val uriAttrRegex = Regex("""URI="([^"]+)"""")
        val result = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.append("\n")
                continue
            }

            if (trimmed.startsWith("#")) {
                if (uriAttrRegex.containsMatchIn(trimmed)) {
                    val replacedLine = uriAttrRegex.replace(trimmed) { matchResult ->
                        val rawUri = matchResult.groupValues[1]
                        val absoluteUrl = try {
                            URL(baseUrl, rawUri).toString()
                        } catch (_: Exception) {
                            rawUri
                        }
                        val encoded = URLEncoder.encode(absoluteUrl, "UTF-8")
                        """URI="http://$ip:$port/iptv_res?u=$encoded""""
                    }
                    result.append(replacedLine).append("\n")
                } else {
                    result.append(line).append("\n")
                }
            } else {
                val absoluteUrl = try {
                    URL(baseUrl, trimmed).toString()
                } catch (_: Exception) {
                    trimmed
                }
                val encoded = URLEncoder.encode(absoluteUrl, "UTF-8")
                result.append("http://$ip:$port/iptv_res?u=$encoded").append("\n")
            }
        }
        return result.toString()
    }

    private fun serveIptvProxy(out: OutputStream, token: String, method: String) {
        val originalUrl = iptvSources[token]
        if (originalUrl == null) {
            log("IPTV token=$token não encontrado (404)")
            sendNotFound(out)
            return
        }

        try {
            log("Iniciando proxy IPTV para Chromecast -> $originalUrl")
            var conn = (URL(originalUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                instanceFollowRedirects = true
                requestMethod = method
                setRequestProperty("User-Agent", "CineLocal/1.6.4 (Android)")
            }

            var redirectCount = 0
            while (conn.responseCode in 301..308 && redirectCount < 5) {
                val location = conn.getHeaderField("Location") ?: break
                val newUrl = URL(conn.url, location).toString()
                conn.disconnect()
                conn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 12000
                    instanceFollowRedirects = true
                    requestMethod = method
                    setRequestProperty("User-Agent", "CineLocal/1.6.4 (Android)")
                }
                redirectCount++
            }

            val code = conn.responseCode
            val effectiveUrl = conn.url
            val contentType = conn.contentType ?: "application/x-mpegURL"

            if (code !in 200..299) {
                log("IPTV HTTP error $code para $originalUrl")
                val headers = "HTTP/1.1 $code ${conn.responseMessage}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(headers.toByteArray())
                out.flush()
                return
            }

            if (method == "HEAD") {
                val headers = "HTTP/1.1 $code OK\r\n" +
                    "Content-Type: $contentType\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(headers.toByteArray())
                out.flush()
                return
            }

            val rawBytes = conn.inputStream.use { it.readBytes() }
            val rawString = String(rawBytes, Charsets.UTF_8)

            if (rawString.startsWith("#EXTM3U") || contentType.contains("mpegurl", ignoreCase = true) || originalUrl.contains(".m3u8", ignoreCase = true)) {
                val ip = getDeviceIpAddress()
                val rewrittenPlaylist = rewriteHlsPlaylist(rawString, effectiveUrl, ip, port)
                val playlistBytes = rewrittenPlaylist.toByteArray(Charsets.UTF_8)

                val headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/x-mpegURL\r\n" +
                    "Content-Length: ${playlistBytes.size}\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Expires: 0\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                    "Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin\r\n" +
                    "Connection: close\r\n\r\n"

                out.write(headers.toByteArray())
                out.write(playlistBytes)
                out.flush()
                log("IPTV HLS Playlist entregue ao Chromecast com URIs proxificadas (${playlistBytes.size} bytes)")
            } else {
                val headers = "HTTP/1.1 $code OK\r\n" +
                    "Content-Type: $contentType\r\n" +
                    "Content-Length: ${rawBytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(headers.toByteArray())
                out.write(rawBytes)
                out.flush()
            }
        } catch (e: Exception) {
            log("Erro ao retransmitir IPTV ($originalUrl): ${e.message}")
            sendNotFound(out)
        }
    }

    private fun serveIptvResource(out: OutputStream, query: String?, method: String, rangeHeader: String?) {
        val encodedUrl = query?.split("&")
            ?.firstOrNull { it.startsWith("u=") }
            ?.removePrefix("u=")
        if (encodedUrl.isNullOrBlank()) {
            sendNotFound(out)
            return
        }

        val targetUrl = try {
            URLDecoder.decode(encodedUrl, "UTF-8")
        } catch (_: Exception) {
            sendNotFound(out)
            return
        }

        try {
            var conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                instanceFollowRedirects = true
                requestMethod = method
                setRequestProperty("User-Agent", "CineLocal/1.6.4 (Android)")
                if (!rangeHeader.isNullOrBlank()) {
                    setRequestProperty("Range", rangeHeader)
                }
            }

            var redirectCount = 0
            while (conn.responseCode in 301..308 && redirectCount < 5) {
                val location = conn.getHeaderField("Location") ?: break
                val newUrl = URL(conn.url, location).toString()
                conn.disconnect()
                conn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    requestMethod = method
                    setRequestProperty("User-Agent", "CineLocal/1.6.4 (Android)")
                    if (!rangeHeader.isNullOrBlank()) {
                        setRequestProperty("Range", rangeHeader)
                    }
                }
                redirectCount++
            }

            val code = conn.responseCode
            val contentType = when {
                targetUrl.contains(".ts", ignoreCase = true) -> "video/MP2T"
                targetUrl.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
                targetUrl.contains(".m4s", ignoreCase = true) || targetUrl.contains(".mp4", ignoreCase = true) -> "video/mp4"
                targetUrl.contains(".aac", ignoreCase = true) -> "audio/aac"
                else -> conn.contentType ?: "application/octet-stream"
            }

            if (targetUrl.contains(".m3u8", ignoreCase = true) || contentType.contains("mpegurl", ignoreCase = true)) {
                val rawBytes = conn.inputStream.use { it.readBytes() }
                val rawString = String(rawBytes, Charsets.UTF_8)
                val ip = getDeviceIpAddress()
                val rewritten = rewriteHlsPlaylist(rawString, conn.url, ip, port)
                val rewrittenBytes = rewritten.toByteArray(Charsets.UTF_8)

                val headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/x-mpegURL\r\n" +
                    "Content-Length: ${rewrittenBytes.size}\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(headers.toByteArray())
                if (method != "HEAD") {
                    out.write(rewrittenBytes)
                }
                out.flush()
                return
            }

            val contentLength = conn.contentLengthLong
            val contentRange = conn.getHeaderField("Content-Range")
            val statusLine = if (code == 206) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 $code ${conn.responseMessage}\r\n"

            val headerBuilder = StringBuilder()
            headerBuilder.append(statusLine)
            headerBuilder.append("Content-Type: $contentType\r\n")
            headerBuilder.append("Accept-Ranges: bytes\r\n")
            headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
            headerBuilder.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            headerBuilder.append("Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin\r\n")
            headerBuilder.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges, Content-Type\r\n")
            if (contentLength > 0) {
                headerBuilder.append("Content-Length: $contentLength\r\n")
            }
            if (!contentRange.isNullOrBlank()) {
                headerBuilder.append("Content-Range: $contentRange\r\n")
            }
            headerBuilder.append("Connection: close\r\n\r\n")

            out.write(headerBuilder.toString().toByteArray())
            out.flush()

            if (method != "HEAD" && code in 200..299) {
                conn.inputStream.use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
                out.flush()
            }
        } catch (e: Exception) {
            log("Erro ao servir recurso IPTV ($targetUrl): ${e.message}")
            sendNotFound(out)
        }
    }

    private fun serveMedia(out: OutputStream, token: String, method: String, rangeHeader: String?, clientIp: String) {
        val source = mediaSources[token]
        if (source == null) {
            log("Mídia token=$token não registrada no servidor (404)")
            sendNotFound(out)
            return
        }

        val dynamicSize = calculateFileSize(source.uri)
        val totalLength = if (dynamicSize > 0) dynamicSize else source.totalLength

        var start = 0L
        var end = if (totalLength > 0) totalLength - 1 else Long.MAX_VALUE
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
            isPartial = true

            if (rangeSpec.startsWith("-")) {
                val suffixLength = rangeSpec.removePrefix("-").toLongOrNull() ?: 0L
                if (totalLength > 0) {
                    start = (totalLength - suffixLength).coerceAtLeast(0L)
                    end = totalLength - 1
                }
            } else {
                val parts = rangeSpec.split("-")
                start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                if (parts.size > 1 && parts[1].isNotBlank()) {
                    end = parts[1].toLongOrNull() ?: end
                }
            }
        }

        if (totalLength > 0 && start >= totalLength) {
            log("Range fora dos limites: start=$start total=$totalLength para token=$token")
            val errorRes = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                "Content-Range: bytes */$totalLength\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Content-Length: 0\r\n\r\n"
            out.write(errorRes.toByteArray())
            out.flush()
            return
        }

        if (totalLength > 0 && end >= totalLength) {
            end = totalLength - 1
        }

        val contentLength = if (totalLength > 0) (end - start + 1) else -1
        val statusLine = if (isPartial && totalLength > 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"

        val headerBuilder = StringBuilder()
        headerBuilder.append(statusLine)
        headerBuilder.append("Content-Type: ${source.mimeType}\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
        headerBuilder.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        headerBuilder.append("Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin, User-Agent, X-Requested-With, Authorization\r\n")
        headerBuilder.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges, Content-Type\r\n")
        headerBuilder.append("Server: CineLocal-MediaProxy/1.6.3\r\n")

        if (contentLength > 0) {
            headerBuilder.append("Content-Length: $contentLength\r\n")
        }
        if (isPartial && totalLength > 0) {
            headerBuilder.append("Content-Range: bytes $start-$end/$totalLength\r\n")
        }
        headerBuilder.append("Connection: close\r\n\r\n")

        out.write(headerBuilder.toString().toByteArray())
        out.flush()

        if (totalLength > 0 && start > totalLength / 2) {
            log("[SONDAGEM MOOV/TAIL] Chromecast buscando metadados no final do arquivo [$clientIp]: range=$start-$end/$totalLength ($contentLength bytes)")
        } else {
            log("Servindo mídia [$clientIp]: ${source.mimeType} range=$start-$end/$totalLength ($contentLength bytes)")
        }

        if (method == "HEAD") return

        streamChannel(source.uri, start, contentLength, out)
    }

    private fun streamChannel(uri: Uri, startPos: Long, totalBytesToSend: Long, out: OutputStream) {
        var pfd: ParcelFileDescriptor? = null
        var fis: FileInputStream? = null
        var channel: FileChannel? = null
        var bytesWritten = 0L

        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.canRead()) {
                    log("[ALERTA SAF] Arquivo local sem permissão de leitura direta: ${file.absolutePath}")
                }
                fis = FileInputStream(file)
                channel = fis.channel
            } else {
                pfd = try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (se: SecurityException) {
                    log("[ERRO SAF/PERMISSÃO] Sem permissão de leitura para $uri: ${se.message}")
                    null
                } catch (e: Exception) {
                    log("[ERRO SAF] Falha ao abrir FileDescriptor para $uri: ${e.message}")
                    null
                }

                if (pfd != null) {
                    fis = FileInputStream(pfd.fileDescriptor)
                    channel = fis.channel
                } else {
                    // Fallback para openInputStream se FileDescriptor falhar
                    log("[FALLBACK SAF] Tentando abrir InputStream direto para $uri")
                    val isStream = context.contentResolver.openInputStream(uri)
                    if (isStream != null) {
                        isStream.use { input ->
                            if (startPos > 0) input.skip(startPos)
                            val buffer = ByteArray(64 * 1024)
                            var remaining = if (totalBytesToSend > 0) totalBytesToSend else Long.MAX_VALUE
                            while (remaining > 0) {
                                val toRead = if (remaining < buffer.size) remaining.toInt() else buffer.size
                                val read = input.read(buffer, 0, toRead)
                                if (read <= 0) break
                                out.write(buffer, 0, read)
                                remaining -= read
                                bytesWritten += read
                            }
                            out.flush()
                        }
                        log("[SUCESSO SAF] Mídia transmitida via InputStream fallback ($bytesWritten bytes)")
                    } else {
                        log("[ERRO CRÍTICO SAF] Falha total ao abrir InputStream e FileDescriptor para $uri")
                    }
                    return
                }
            }

            if (channel != null) {
                if (startPos > 0) {
                    channel.position(startPos)
                }

                val buffer = ByteBuffer.allocateDirect(128 * 1024)
                val tempArray = ByteArray(128 * 1024)
                var bytesRemaining = if (totalBytesToSend > 0) totalBytesToSend else Long.MAX_VALUE

                while (bytesRemaining > 0) {
                    buffer.clear()
                    if (bytesRemaining < buffer.capacity()) {
                        buffer.limit(bytesRemaining.toInt())
                    }

                    val read = channel.read(buffer)
                    if (read <= 0) break

                    buffer.flip()
                    buffer.get(tempArray, 0, read)
                    out.write(tempArray, 0, read)
                    bytesRemaining -= read
                    bytesWritten += read
                }
                out.flush()
                log("[SUCESSO HTTP] Mídia transmitida com sucesso ($bytesWritten bytes)")
            }
        } catch (e: Exception) {
            log("[SONDAGEM/SEEK REALIZADO] Conexão concluída ou fechada pelo receptor no offset $startPos ($bytesWritten bytes enviados): ${e.message}")
        } finally {
            try { channel?.close() } catch (_: Exception) {}
            try { fis?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun calculateFileSize(uri: Uri): Long {
        var size = -1L
        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    val len = file.length()
                    if (len > 0) return len
                }
            }

            val torrentStatus = com.example.cinelocal.data.torrent.TorrentStreamEngine.status.value
            if (torrentStatus.totalSizeBytes > 0 && (uri.path?.contains("torrents") == true || uri.toString().contains("torrents"))) {
                return torrentStatus.totalSizeBytes
            }

            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val pfdSize = pfd.statSize
                if (pfdSize > 0) {
                    size = pfdSize
                } else {
                    try {
                        FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                            val chSize = ch.size()
                            if (chSize > 0) size = chSize
                        }
                    } catch (_: Exception) {}
                }
            }
            if (size <= 0) {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx != -1) size = cursor.getLong(idx)
                    }
                }
            }
        } catch (_: Exception) {}
        return if (size > 0) size else -1L
    }
}
