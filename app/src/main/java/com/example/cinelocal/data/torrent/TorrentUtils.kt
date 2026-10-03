package com.example.cinelocal.data.torrent

import java.net.URLDecoder
import java.util.Locale

object TorrentUtils {

    data class ParsedMagnet(
        val originalUri: String,
        val infoHash: String,
        val displayName: String,
        val trackers: List<String> = emptyList()
    ) {
        val name: String get() = displayName
        val streamUrl: String get() = getStreamableUrl(originalUri, infoHash)
    }

    fun extractInfoHash(magnetUri: String): String? {
        if (!magnetUri.startsWith("magnet:", ignoreCase = true)) return null
        val regex = Regex("xt=urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(magnetUri) ?: return null
        return match.groupValues.getOrNull(1)?.lowercase(Locale.ROOT)
    }

    fun extractDisplayName(magnetUri: String): String? {
        if (!magnetUri.startsWith("magnet:", ignoreCase = true)) return null
        val regex = Regex("dn=([^&]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(magnetUri) ?: return null
        val rawName = match.groupValues.getOrNull(1) ?: return null
        return try {
            URLDecoder.decode(rawName, "UTF-8").replace("+", " ")
        } catch (_: Exception) {
            rawName
        }
    }

    fun parseMagnetUri(magnetUri: String): ParsedMagnet? {
        val hash = extractInfoHash(magnetUri) ?: return null
        val dn = extractDisplayName(magnetUri) ?: "Torrent ($hash)"
        
        val trackerRegex = Regex("tr=([^&]+)", RegexOption.IGNORE_CASE)
        val trackers = trackerRegex.findAll(magnetUri).mapNotNull {
            try {
                URLDecoder.decode(it.groupValues[1], "UTF-8")
            } catch (_: Exception) {
                null
            }
        }.toList()

        return ParsedMagnet(
            originalUri = magnetUri,
            infoHash = hash,
            displayName = dn,
            trackers = trackers
        )
    }

    fun parseMagnet(magnetUri: String): ParsedMagnet {
        return parseMagnetUri(magnetUri) ?: ParsedMagnet(
            originalUri = magnetUri,
            infoHash = extractInfoHash(magnetUri) ?: "",
            displayName = extractDisplayName(magnetUri) ?: "Torrent"
        )
    }

    fun getStreamableUrl(magnetUri: String, infoHash: String? = null): String {
        val hash = infoHash ?: extractInfoHash(magnetUri)
        if (!hash.isNullOrBlank()) {
            return "https://webtorrent.now.sh/$hash"
        }
        return magnetUri
    }
}
