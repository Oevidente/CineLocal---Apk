package com.example.cinelocal.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
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

    // Circular log buffer for diagnostic screen
    val recentLogs = Collections.synchronizedList(mutableListOf<String>())

    fun log(msg: String) {
        Log.d("MediaProxyServer", msg)
        synchronized(recentLogs) {
            if (recentLogs.size >= 50) recentLogs.removeAt(0)
            recentLogs.add("[${System.currentTimeMillis() % 100000}] $msg")
        }
    }

    fun registerMedia(uri: Uri, mimeType: String): String {
        ensureStarted()
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        val size = calculateFileSize(uri)
        val safeMime = when {
            mimeType.contains("matroska", ignoreCase = true) -> "video/mp4"
            mimeType.contains("webm", ignoreCase = true) -> "video/webm"
            mimeType.contains("mpegurl", ignoreCase = true) || mimeType.contains("m3u8", ignoreCase = true) -> "application/x-mpegURL"
            mimeType.isBlank() || mimeType == "application/octet-stream" -> "video/mp4"
            else -> mimeType
        }
        mediaSources[token] = ProxyMediaSource(uri, safeMime, size)
        val ip = getDeviceIpAddress()
        val ext = if (safeMime == "video/webm") "webm" else "mp4"
        val url = "http://$ip:$port/m/$token.$ext"
        log("Mídia registrada: token=$token size=$size url=$url mime=$safeMime")
        return url
    }

    fun registerSubtitle(vttContent: String): String {
        ensureStarted()
        val token = UUID.randomUUID().toString().replace("-", "").take(16)
        subtitleSources[token] = vttContent
        val ip = getDeviceIpAddress()
        val url = "http://$ip:$port/s/$token.vtt"
        log("Legenda registrada: token=$token url=$url")
        return url
    }

    fun clearRegistrations() {
        mediaSources.clear()
        subtitleSources.clear()
    }

    @Synchronized
    fun ensureStarted() {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return
        start()
    }

    @Synchronized
    fun start() {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) return
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            listenOnSocket()
            log("Servidor iniciado na porta $port")
        } catch (e: Exception) {
            log("Porta $port em uso, tentando porta dinâmica...")
            try {
                serverSocket = ServerSocket(0)
                port = serverSocket?.localPort ?: 8899
                isRunning = true
                listenOnSocket()
                log("Servidor iniciado com porta dinâmica $port")
            } catch (ex: Exception) {
                log("Falha crítica ao iniciar ServerSocket: ${ex.message}")
            }
        }
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
        log("Servidor parado.")
    }

    fun getDeviceIpAddress(): String {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                // Inspecionar rede ativa com preferência para Wi-Fi ou Ethernet
                val allNets = cm.allNetworks
                for (net in allNets) {
                    val caps = cm.getNetworkCapabilities(net)
                    if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                    ) {
                        val linkProps = cm.getLinkProperties(net)
                        val ipv4 = linkProps?.linkAddresses?.mapNotNull { it.address as? Inet4Address }
                            ?.firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress && !it.hostAddress.isNullOrBlank() }
                        if (ipv4 != null) {
                            return ipv4.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            }

            // Fallback com prioridade explícita para interfaces wlan/eth
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val sortedIntfs = interfaces.sortedWith(compareByDescending {
                val name = it.name.lowercase()
                when {
                    name.startsWith("wlan") -> 3
                    name.startsWith("eth") || name.startsWith("en") -> 2
                    name.startsWith("ap") || name.startsWith("p2p") -> 1
                    else -> 0
                }
            })

            for (intf in sortedIntfs) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (addr.isSiteLocalAddress && !host.startsWith("127.") && !host.startsWith("169.254.")) {
                            return host
                        }
                    }
                }
            }

            // Fallback secundário
            for (intf in sortedIntfs) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: ""
                        if (!host.startsWith("127.") && !host.startsWith("169.254.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log("Erro ao obter IP da rede local: ${e.message}")
        }
        return "127.0.0.1"
    }

    private fun handleClient(socket: Socket) {
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

                when {
                    path.startsWith("/m/") -> {
                        val token = path.removePrefix("/m/").substringBefore(".").substringBefore("?").substringBefore("/")
                        serveMedia(out, token, method, rangeHeader)
                    }
                    path.startsWith("/s/") -> {
                        val token = path.removePrefix("/s/").substringBefore(".").substringBefore("?")
                        serveSubtitle(out, token, method)
                    }
                    else -> {
                        sendNotFound(out)
                    }
                }
            }
        } catch (_: Exception) {
            // Cliente desconectou normalmente durante streaming
        }
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
    }

    private fun serveMedia(out: OutputStream, token: String, method: String, rangeHeader: String?) {
        val source = mediaSources[token]
        if (source == null) {
            sendNotFound(out)
            return
        }

        // Determina o tamanho dinamicamente caso tenha sido registrado preliminarmente (ex: torrent)
        val dynamicSize = calculateFileSize(source.uri)
        val totalLength = if (dynamicSize > 0) dynamicSize else source.totalLength

        var start = 0L
        var end = if (totalLength > 0) totalLength - 1 else Long.MAX_VALUE
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=")) {
            val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
            isPartial = true

            if (rangeSpec.startsWith("-")) {
                // Suffix range: bytes=-500 (últimos 500 bytes)
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
        headerBuilder.append("Server: CineLocal-MediaProxy/1.6.1\r\n")

        if (contentLength > 0) {
            headerBuilder.append("Content-Length: $contentLength\r\n")
        }
        if (isPartial && totalLength > 0) {
            headerBuilder.append("Content-Range: bytes $start-$end/$totalLength\r\n")
        }
        headerBuilder.append("Connection: close\r\n\r\n")

        out.write(headerBuilder.toString().toByteArray())
        out.flush()

        if (method == "HEAD") return

        // Leitura e streaming com Random Access otimizado
        streamChannel(source.uri, start, contentLength, out)
    }

    private fun streamChannel(uri: Uri, startPos: Long, totalBytesToSend: Long, out: OutputStream) {
        var pfd: ParcelFileDescriptor? = null
        var fis: FileInputStream? = null
        var channel: FileChannel? = null

        try {
            if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                fis = FileInputStream(file)
                channel = fis.channel
            } else {
                pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return
                fis = FileInputStream(pfd.fileDescriptor)
                channel = fis.channel
            }

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
            }
            out.flush()
        } catch (_: Exception) {
            // Conexão encerrada pelo receptor (Chromecast)
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

            // Checagem de tamanho de torrent em download se for arquivo no diretório de torrents
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

