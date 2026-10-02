package com.example.cinelocal.cast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.Collections
import java.util.regex.Pattern

/**
 * Embedded HTTP server for Google Cast streaming.
 * Provides:
 * 1. HTTP Byte-Range support (206 Partial Content) for seeking local video files (MP4, MKV, etc.)
 * 2. Dynamic WebVTT conversion endpoint (/subtitles.vtt) for SRT/SUB subtitles
 * 3. Local network IP resolver
 */
class LocalStreamServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    var serverPort = 8899
        private set

    @Volatile
    var isRunning = false
        private set

    // Currently mounted media uri and subtitle uri
    private var activeMediaUri: Uri? = null
    private var activeSubtitleUri: Uri? = null
    private var activeMimeType: String = "video/mp4"

    fun setMedia(mediaUri: Uri, subtitleUri: Uri? = null, mimeType: String = "video/mp4") {
        activeMediaUri = mediaUri
        activeSubtitleUri = subtitleUri
        activeMimeType = mimeType
        start()
    }

    fun start() {
        if (isRunning) return
        try {
            serverSocket = ServerSocket(serverPort)
            isRunning = true
            serverJob = scope.launch {
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            }
            Log.d("LocalStreamServer", "Server started on port $serverPort")
        } catch (e: Exception) {
            Log.e("LocalStreamServer", "Failed to start server on $serverPort, trying fallback port", e)
            try {
                serverSocket = ServerSocket(0) // Random free port
                serverPort = serverSocket?.localPort ?: 8899
                isRunning = true
                serverJob = scope.launch {
                    while (isActive && isRunning) {
                        try {
                            val clientSocket = serverSocket?.accept() ?: break
                            launch(Dispatchers.IO) {
                                handleClient(clientSocket)
                            }
                        } catch (e: Exception) {
                            if (!isRunning) break
                        }
                    }
                }
            } catch (ex: Exception) {
                Log.e("LocalStreamServer", "Could not start server socket", ex)
            }
        }
    }

    fun stop() {
        isRunning = false
        serverJob?.cancel()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignored
        }
        serverSocket = null
    }

    fun getStreamUrl(): String {
        val ip = getDeviceIpAddress()
        return "http://$ip:$serverPort/stream"
    }

    fun getSubtitleUrl(): String? {
        if (activeSubtitleUri == null) return null
        val ip = getDeviceIpAddress()
        return "http://$ip:$serverPort/subtitles.vtt"
    }

    fun getDeviceIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (wifiIp != 0) {
                return Formatter.formatIpAddress(wifiIp)
            }

            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LocalStreamServer", "Error getting IP", e)
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

                val method = parts[0]
                val path = parts[1]

                // Read headers
                var rangeHeader: String? = null
                var line = reader.readLine()
                while (!line.isNullOrBlank()) {
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                    line = reader.readLine()
                }

                if (path.startsWith("/subtitles.vtt")) {
                    serveSubtitles(out)
                } else {
                    serveVideo(out, method, rangeHeader)
                }
            }
        } catch (e: Exception) {
            // Client disconnect or socket error
        }
    }

    private fun serveSubtitles(out: OutputStream) {
        val uri = activeSubtitleUri
        if (uri == null) {
            val res = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
            out.write(res.toByteArray())
            out.flush()
            return
        }

        try {
            val input = openInputStream(uri) ?: throw IllegalStateException("Cannot read subtitles")
            val content = input.bufferedReader().use { it.readText() }
            val vttContent = convertSrtToVtt(content)
            val bytes = vttContent.toByteArray(Charsets.UTF_8)

            val headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/vtt; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Connection: close\r\n\r\n"

            out.write(headers.toByteArray())
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            val res = "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\n\r\n"
            out.write(res.toByteArray())
            out.flush()
        }
    }

    private fun serveVideo(out: OutputStream, method: String, rangeHeader: String?) {
        val uri = activeMediaUri
        if (uri == null) {
            val res = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n"
            out.write(res.toByteArray())
            out.flush()
            return
        }

        val totalLength = getFileSize(uri)
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
        val headerBuilder = StringBuilder()
        headerBuilder.append(statusLine)
        headerBuilder.append("Content-Type: $activeMimeType\r\n")
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

        val inputStream = openInputStream(uri) ?: return
        try {
            inputStream.use { stream ->
                if (start > 0) {
                    stream.skip(start)
                }

                val buffer = ByteArray(64 * 1024)
                var bytesRemaining = if (contentLength > 0) contentLength else Long.MAX_VALUE

                while (bytesRemaining > 0) {
                    val toRead = if (bytesRemaining < buffer.size) bytesRemaining.toInt() else buffer.size
                    val bytesRead = stream.read(buffer, 0, toRead)
                    if (bytesRead == -1) break
                    out.write(buffer, 0, bytesRead)
                    bytesRemaining -= bytesRead
                }
                out.flush()
            }
        } catch (e: Exception) {
            // Client closed stream
        }
    }

    private fun openInputStream(uri: Uri): InputStream? {
        return try {
            if (uri.scheme == "file") {
                FileInputStream(File(uri.path ?: ""))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return try {
            if (uri.scheme == "file") {
                File(uri.path ?: "").length()
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    it.statSize
                } ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun convertSrtToVtt(srtText: String): String {
        if (srtText.trim().startsWith("WEBVTT")) return srtText

        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")

        val lines = srtText.lines()
        val timecodePattern = Pattern.compile("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}),(\\d{3})")

        for (rawLine in lines) {
            val line = rawLine.trim()
            val matcher = timecodePattern.matcher(line)
            if (matcher.find()) {
                val vttLine = line.replace(',', '.')
                sb.append(vttLine).append("\n")
            } else {
                sb.append(line).append("\n")
            }
        }

        return sb.toString()
    }
}
