package com.example.cinelocal.data.smb

import android.content.Context
import android.net.Uri
import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.EnumSet

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object SmbStreamProxy {

    private const val TAG = "SmbStreamProxy"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    var proxyPort = 8996
        private set

    @Volatile
    var isRunning = false
        private set

    // Armazenamento em memória de tickets de streaming com credenciais SMB opacas (QA-005)
    data class StreamTicket(
        val config: SmbConnectionConfig,
        val shareName: String,
        val filePath: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val tickets = ConcurrentHashMap<String, StreamTicket>()

    fun start() {
        if (isRunning) return
        try {
            serverSocket = ServerSocket(proxyPort)
            isRunning = true
            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (_: Exception) {
                        if (!isRunning) break
                    }
                }
            }
            Log.d(TAG, "SmbStreamProxy iniciado na porta $proxyPort")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar proxy na porta padrão, tentando porta livre", e)
            try {
                serverSocket = ServerSocket(0)
                proxyPort = serverSocket?.localPort ?: 8996
                isRunning = true
                serverJob = scope.launch {
                    while (isActive && isRunning) {
                        try {
                            val clientSocket = serverSocket?.accept() ?: break
                            launch(Dispatchers.IO) {
                                handleClient(clientSocket)
                            }
                        } catch (_: Exception) {
                            if (!isRunning) break
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Não foi possível iniciar SmbStreamProxy", ex)
            }
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        tickets.clear()
    }

    /**
     * Cria uma URL HTTP local reproduzível usando apenas um ticket aleatório opaco.
     * NENHUMA credencial (usuário, senha, domínio) é colocada na URL (QA-005).
     */
    fun createProxyUrl(
        config: SmbConnectionConfig,
        shareName: String,
        filePath: String
    ): String {
        start()
        cleanOldTickets()
        val ticketId = UUID.randomUUID().toString()
        tickets[ticketId] = StreamTicket(config, shareName, filePath)
        return "http://127.0.0.1:$proxyPort/smb/video?t=$ticketId"
    }

    private fun cleanOldTickets() {
        if (tickets.size > 200) {
            val now = System.currentTimeMillis()
            val expiry = 24 * 3600 * 1000L // 24h
            tickets.entries.removeIf { now - it.value.createdAt > expiry }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val out = BufferedOutputStream(s.getOutputStream())

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val uriString = parts[1]

                var rangeHeader: String? = null
                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                    line = reader.readLine()
                }

                if (!uriString.startsWith("/smb/video")) {
                    val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
                    out.write(notFound.toByteArray())
                    out.flush()
                    return
                }

                serveSmbVideo(uriString, out, method, rangeHeader)
            }
        } catch (_: Exception) {
            // Cliente desconectou ou erro de socket
        }
    }

    private fun serveSmbVideo(
        requestUri: String,
        out: OutputStream,
        method: String,
        rangeHeader: String?
    ) {
        val uri = Uri.parse("http://localhost$requestUri")
        val ticketId = uri.getQueryParameter("t")
        val ticket = if (ticketId != null) tickets[ticketId] else null

        val (config, shareName, filePath) = if (ticket != null) {
            Triple(ticket.config, ticket.shareName, ticket.filePath)
        } else {
            // Suporte legado se parâmetros antigos forem recebidos
            val host = uri.getQueryParameter("h") ?: return
            val port = uri.getQueryParameter("p")?.toIntOrNull() ?: 445
            val s = uri.getQueryParameter("s") ?: return
            val f = uri.getQueryParameter("f") ?: return
            val username = uri.getQueryParameter("u") ?: ""
            val password = uri.getQueryParameter("pwd") ?: ""
            val domain = uri.getQueryParameter("d") ?: ""
            val isAnonymous = uri.getQueryParameter("a") == "1" || username.isBlank()
            Triple(
                SmbConnectionConfig(
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    domain = domain,
                    isAnonymous = isAnonymous
                ),
                s,
                f
            )
        }

        var session: com.hierynomus.smbj.session.Session? = null
        var share: DiskShare? = null
        var smbFile: SmbFile? = null

        try {
            session = SmbClientManager.getOrCreateSessionSync(config)
            share = session.connectShare(shareName) as? DiskShare ?: throw IllegalStateException("Share inacessível: $shareName")

            val cleanPath = filePath.trim().trim('/').replace('/', '\\')
            smbFile = share.openFile(
                cleanPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )

            val totalLength = smbFile.fileInformation.standardInformation.endOfFile
            var start: Long = 0
            var end: Long = if (totalLength > 0) totalLength - 1 else Long.MAX_VALUE

            val isRange = rangeHeader != null && rangeHeader.startsWith("bytes=")
            if (isRange) {
                val ranges = rangeHeader!!.substringAfter("bytes=").split("-")
                start = ranges[0].toLongOrNull() ?: 0
                if (ranges.size > 1 && ranges[1].isNotBlank()) {
                    end = ranges[1].toLongOrNull() ?: end
                }
            }

            if (totalLength > 0 && end >= totalLength) {
                end = totalLength - 1
            }

            val contentLength = if (totalLength > 0) (end - start + 1) else -1

            val statusLine = if (isRange && totalLength > 0) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n"
            val mimeType = inferMimeType(filePath)

            val headerBuilder = StringBuilder()
            headerBuilder.append(statusLine)
            headerBuilder.append("Content-Type: $mimeType\r\n")
            headerBuilder.append("Accept-Ranges: bytes\r\n")
            headerBuilder.append("Access-Control-Allow-Origin: *\r\n")

            if (contentLength > 0) {
                headerBuilder.append("Content-Length: $contentLength\r\n")
            }
            if (isRange && totalLength > 0) {
                headerBuilder.append("Content-Range: bytes $start-$end/$totalLength\r\n")
            }
            headerBuilder.append("Connection: close\r\n\r\n")

            out.write(headerBuilder.toString().toByteArray())
            out.flush()

            if (method.equals("HEAD", ignoreCase = true)) {
                return
            }

            // Transmissão por streaming de bytes com buffer otimizado
            val buffer = ByteArray(128 * 1024)
            var currentOffset = start
            var bytesRemaining = if (contentLength > 0) contentLength else Long.MAX_VALUE

            while (bytesRemaining > 0 && currentOffset <= end) {
                val toRead = if (bytesRemaining < buffer.size) bytesRemaining.toInt() else buffer.size
                val bytesRead = smbFile.read(buffer, currentOffset, 0, toRead)
                if (bytesRead <= 0) break

                out.write(buffer, 0, bytesRead)
                currentOffset += bytesRead
                bytesRemaining -= bytesRead
            }
            out.flush()

        } catch (e: Exception) {
            Log.d(TAG, "Conexão de streaming encerrada para $filePath: ${e.message}")
        } finally {
            try { smbFile?.close() } catch (_: Exception) {}
            try { share?.close() } catch (_: Exception) {}
        }
    }

    private fun inferMimeType(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "ts" -> "video/mp2t"
            else -> "video/mp4"
        }
    }
}
