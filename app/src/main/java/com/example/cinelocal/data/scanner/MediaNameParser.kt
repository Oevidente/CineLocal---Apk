package com.example.cinelocal.data.scanner

data class ParsedMediaName(
    val title: String,
    val isSeries: Boolean,
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 1,
    val year: Int? = null
)

object MediaNameParser {

    private val SERIES_PATTERNS = listOf(
        // S01E02 / s01e02 / S1E2 / S01.E02 / S01_E02
        Regex("""(?i)[._ -]?s(\d{1,2})[._ -]?e(\d{1,3})"""),
        // 1x02 / 01x02
        Regex("""(?i)[._ -]?(\d{1,2})x(\d{1,3})"""),
        // T01E02 / t1e2
        Regex("""(?i)[._ -]?t(\d{1,2})[._ -]?e(\d{1,3})"""),
        // Temporada 1 Episodio 2 / Season 1 Episode 2
        Regex("""(?i)[._ -]?(?:temporada|season)[._ -]?(\d{1,2})[._ -]?(?:episodio|episode|ep)[._ -]?(\d{1,3})""")
    )

    private val SEASON_FOLDER_PATTERNS = listOf(
        Regex("""(?i)(?:season|temporada)[._ -]?(\d{1,2})"""),
        Regex("""(?i)^s(\d{1,2})$""")
    )

    private val EPISODE_STANDALONE_PATTERNS = listOf(
        Regex("""(?i)^[._ -]?(?:ep|episodio|episode)[._ -]?(\d{1,3})"""),
        Regex("""^(\d{1,3})[._ -]""")
    )

    private val YEAR_PATTERN = Regex("""[._ (](19\d{2}|20\d{2})[._ )]""")

    private val TECHNICAL_TAGS = listOf(
        "2160p", "1080p", "720p", "480p", "4k", "uhd",
        "web-dl", "webdl", "webrip", "bluray", "brrip", "hdrip", "dvdrip", "hdtv",
        "x264", "x265", "hevc", "h264", "h265", "avc", "10bit",
        "aac", "ddp5.1", "dd5.1", "ac3", "dts", "atmos", "mp3",
        "dual", "dublado", "legendado", "subbed", "dubbed", "multi", "remux"
    )

    fun parse(fileName: String, parentFolderName: String? = null): ParsedMediaName {
        val nameWithoutExt = fileName.substringBeforeLast('.')

        // 1. Verificar padrões de série no próprio nome do arquivo
        for (pattern in SERIES_PATTERNS) {
            val match = pattern.find(nameWithoutExt)
            if (match != null) {
                val sNum = match.groupValues[1].toIntOrNull() ?: 1
                val eNum = match.groupValues[2].toIntOrNull() ?: 1
                val rawTitle = nameWithoutExt.substring(0, match.range.first)
                val cleanTitle = cleanTitle(rawTitle)
                val year = extractYear(nameWithoutExt)
                return ParsedMediaName(
                    title = if (cleanTitle.isNotBlank()) cleanTitle else (parentFolderName ?: "Série"),
                    isSeries = true,
                    seasonNumber = sNum,
                    episodeNumber = eNum,
                    year = year
                )
            }
        }

        // 2. Verificar se a pasta pai indica temporada (ex: "Season 1" ou "Temporada 2")
        if (!parentFolderName.isNullOrBlank()) {
            for (pattern in SEASON_FOLDER_PATTERNS) {
                val match = pattern.find(parentFolderName)
                if (match != null) {
                    val sNum = match.groupValues[1].toIntOrNull() ?: 1
                    var eNum = 1
                    for (epPattern in EPISODE_STANDALONE_PATTERNS) {
                        val epMatch = epPattern.find(nameWithoutExt)
                        if (epMatch != null) {
                            eNum = epMatch.groupValues[1].toIntOrNull() ?: 1
                            break
                        }
                    }
                    val cleanTitle = cleanTitle(nameWithoutExt)
                    return ParsedMediaName(
                        title = cleanTitle.ifBlank { nameWithoutExt },
                        isSeries = true,
                        seasonNumber = sNum,
                        episodeNumber = eNum,
                        year = extractYear(nameWithoutExt)
                    )
                }
            }
        }

        // 3. Caso contrário: Filme
        val year = extractYear(nameWithoutExt)
        val cleanTitle = cleanTitle(nameWithoutExt)
        return ParsedMediaName(
            title = if (cleanTitle.isNotBlank()) cleanTitle else nameWithoutExt,
            isSeries = false,
            seasonNumber = 0,
            episodeNumber = 1,
            year = year
        )
    }

    private fun extractYear(text: String): Int? {
        val match = YEAR_PATTERN.find(text) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun cleanTitle(raw: String): String {
        var result = raw.replace('.', ' ').replace('_', ' ')

        // Remover ano do título se presente
        val yearMatch = YEAR_PATTERN.find(result)
        if (yearMatch != null) {
            result = result.substring(0, yearMatch.range.first)
        }

        // Remover colchetes no início (ex: [Grupo] Titulo)
        result = result.replace(Regex("""^\[[^\]]*\]\s*"""), "")

        // Cortar a partir de tags técnicas conhecidas
        for (tag in TECHNICAL_TAGS) {
            val idx = result.indexOf(tag, ignoreCase = true)
            if (idx != -1) {
                result = result.substring(0, idx)
            }
        }

        // Remover caracteres residuais como traços ou colchetes soltos no final
        result = result.replace(Regex("""[-–—\s]+$"""), "")
        return result.trim()
    }
}
