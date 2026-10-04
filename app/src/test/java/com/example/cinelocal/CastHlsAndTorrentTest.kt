package com.example.cinelocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import java.net.URLEncoder

class CastHlsAndTorrentTest {

    @Test
    fun testHlsPlaylistRewriting() {
        val originalPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-KEY:METHOD=AES-128,URI="crypt.key"
            #EXTINF:10.0,
            segment0.ts
            #EXTINF:10.0,
            https://external-cdn.com/live/segment1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val baseUrl = URL("https://stream.provider.com/hls/master.m3u8")
        val localIp = "192.168.1.50"
        val localPort = 8899

        val lines = originalPlaylist.lines()
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
                        """URI="http://$localIp:$localPort/iptv_res?u=$encoded""""
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
                result.append("http://$localIp:$localPort/iptv_res?u=$encoded").append("\n")
            }
        }

        val rewritten = result.toString()

        // Verify key URI proxification
        assertTrue(rewritten.contains("""URI="http://192.168.1.50:8899/iptv_res?u="""))
        assertTrue(rewritten.contains(URLEncoder.encode("https://stream.provider.com/hls/crypt.key", "UTF-8")))

        // Verify relative segment proxification
        assertTrue(rewritten.contains("http://192.168.1.50:8899/iptv_res?u=" + URLEncoder.encode("https://stream.provider.com/hls/segment0.ts", "UTF-8")))

        // Verify absolute segment proxification
        assertTrue(rewritten.contains("http://192.168.1.50:8899/iptv_res?u=" + URLEncoder.encode("https://external-cdn.com/live/segment1.ts", "UTF-8")))

        // Ensure Chromecast never receives raw segment without proxy prefix
        assertFalse(rewritten.lines().any { !it.startsWith("#") && it.isNotBlank() && !it.startsWith("http://192.168.1.50:8899/iptv_res") })
    }

    @Test
    fun testCastMimeSanitization() {
        assertEquals("application/x-mpegURL", com.example.cinelocal.cast.CastMediaResolver.sanitizeMimeForCast("https://stream.com/playlist.m3u8"))
        assertEquals("video/x-matroska", com.example.cinelocal.cast.CastMediaResolver.sanitizeMimeForCast("/storage/emulated/0/Movies/film.mkv"))
        assertEquals("video/webm", com.example.cinelocal.cast.CastMediaResolver.sanitizeMimeForCast("http://localhost/video.webm"))
        assertEquals("video/mp4", com.example.cinelocal.cast.CastMediaResolver.sanitizeMimeForCast("http://localhost/video.mp4"))
    }
}
