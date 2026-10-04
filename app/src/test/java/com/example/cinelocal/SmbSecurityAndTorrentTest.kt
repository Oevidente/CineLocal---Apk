package com.example.cinelocal

import com.example.cinelocal.data.smb.SmbConnectionConfig
import com.example.cinelocal.data.smb.SmbStreamProxy
import com.example.cinelocal.data.torrent.TorrentLaunchHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbSecurityAndTorrentTest {

    @Test
    fun testSmbProxyUrlNeverExposesCredentials() {
        val config = SmbConnectionConfig(
            host = "192.168.1.100",
            port = 445,
            username = "admin_user",
            password = "super_secret_password_123",
            domain = "WORKGROUP",
            isAnonymous = false
        )

        val proxyUrl = SmbStreamProxy.createProxyUrl(config, "Videos", "Movies/Action/Film.mkv")

        // QA-005 verification: URL MUST NOT contain username, password, or domain in plaintext
        assertFalse("URL must not contain plaintext password", proxyUrl.contains("super_secret_password_123"))
        assertFalse("URL must not contain plaintext username", proxyUrl.contains("admin_user"))
        assertFalse("URL must not contain plaintext domain", proxyUrl.contains("WORKGROUP"))
        assertTrue("URL must use opaque ticket token", proxyUrl.contains("?t="))
    }

    @Test
    fun testTorrentSeasonDetection() {
        // QA-003 & QA-021 verification: Season detection must not be limited to S01-S05
        assertTrue(TorrentLaunchHelper.isSeasonPack("Breaking.Bad.S02.720p", "magnet:?xt=urn:btih:abc"))
        assertTrue(TorrentLaunchHelper.isSeasonPack("Show.S06.Complete", "magnet:?xt=urn:btih:def"))

        assertEquals(2, TorrentLaunchHelper.parseSeasonNumber("Breaking.Bad.S02.720p", "magnet:?xt=urn:btih:abc"))
        assertEquals(6, TorrentLaunchHelper.parseSeasonNumber("Show.S06.Complete", "magnet:?xt=urn:btih:def"))
        assertEquals(3, TorrentLaunchHelper.parseSeasonNumber("Minha Serie Temporada 3", "magnet:?xt=urn:btih:ghi"))

        // Verification of real files mapping without fake 8 episodes
        val realFiles = listOf(
            "Show.S02E01.mkv",
            "Show.S02E02.mkv",
            "Show.S02E03.mkv"
        )
        val episodes = TorrentLaunchHelper.generateSeasonEpisodes(
            mediaId = "media123",
            seriesTitle = "Show",
            magnetUri = "magnet:?xt=urn:btih:test",
            fileNames = realFiles
        )

        assertEquals("Must generate exactly 3 episodes for 3 real files", 3, episodes.size)
        assertEquals(2, episodes[0].seasonNumber)
        assertEquals(1, episodes[0].episodeNumber)
        assertEquals(2, episodes[1].seasonNumber)
        assertEquals(2, episodes[1].episodeNumber)
        assertEquals("Show.S02E01.mkv", episodes[0].filePath)
    }

    @Test
    fun testParserGrandParentFolderSeriesRecognition() {
        // QA-019 verification: Structure Série/Season 1/01.mkv must recognize series name from grandparent
        val parsed = com.example.cinelocal.data.scanner.MediaNameParser.parse(
            fileName = "01.mkv",
            parentFolderName = "Season 1",
            grandParentFolderName = "Minha Serie Incrivel"
        )
        assertTrue(parsed.isSeries)
        assertEquals("Minha Serie Incrivel", parsed.title)
        assertEquals(1, parsed.seasonNumber)
        assertEquals(1, parsed.episodeNumber)
    }

    @Test
    fun testYearParserWithTrailingFilename() {
        // QA-020 verification: Film 2024.mkv must recognize 2024 even without trailing punctuation before extension
        val parsed = com.example.cinelocal.data.scanner.MediaNameParser.parse(
            fileName = "Filme de Acao 2024.mkv"
        )
        assertFalse(parsed.isSeries)
        assertEquals(2024, parsed.year)
        assertEquals("Filme de Acao", parsed.title)
    }
}
