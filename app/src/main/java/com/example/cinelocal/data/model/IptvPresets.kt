package com.example.cinelocal.data.model

data class IptvPreset(
    val name: String,
    val description: String,
    val url: String
)

object IptvPresets {
    val presets = listOf(
        IptvPreset(
            name = "Canais Abertos Brasil (IPTV-org)",
            description = "Seleção de canais de TV aberta e públicos do Brasil.",
            url = "https://iptv-org.github.io/iptv/countries/br.m3u"
        ),
        IptvPreset(
            name = "Canais Noticiosos Internacionais",
            description = "Canais de notícias mundiais em inglês e espanhol.",
            url = "https://iptv-org.github.io/iptv/categories/news.m3u"
        ),
        IptvPreset(
            name = "Filmes & Entretenimento",
            description = "Canais de filmes, cultura e documentários livres.",
            url = "https://iptv-org.github.io/iptv/categories/movies.m3u"
        ),
        IptvPreset(
            name = "Esportes e Lutas",
            description = "Canais e transmissões de esportes variados.",
            url = "https://iptv-org.github.io/iptv/categories/sports.m3u"
        )
    )

    val POPULAR_SOURCES = presets
}
