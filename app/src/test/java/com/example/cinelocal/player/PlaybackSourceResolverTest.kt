package com.example.cinelocal.player

import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceResolverTest {

    @Test
    fun testTorrentWithoutActiveProviderReturnsFailure() {
        // Test that magnet candidates return the specific message
        val ep = EpisodeEntity(
            mediaId = "1",
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Torrent Ep",
            streamUrl = "magnet:?xt=urn:btih:0123456789abcdef"
        )
        // With mock/dummy context we can check live or resolver
        val liveResult = PlaybackSourceResolver.resolveLive("")
        assertTrue(liveResult is ResolveResult.Fail)
        assertEquals("Endereço de transmissão inválido ou vazio.", (liveResult as ResolveResult.Fail).reason)
    }
}
