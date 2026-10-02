# CineLocal - Central de Mídia & IPTV Nativa para Android

CineLocal é uma aplicação nativa Android desenvolvida em **Kotlin** e **Jetpack Compose** no estilo Netflix, focada na reprodução de filmes, séries e canais de TV ao vivo via IPTV.

## 📱 Recursos Principais

- **Interface Netflix-Style**: Dark mode refinado (#141414 com acentos em vermelho #E50914), Banner Destaque Hero com pôsteres em alta definição, notas e sinopses.
- **Player de Vídeo Nativo Media3 (ExoPlayer)**:
  - Controles modernos integrados com avanço/retrocesso de 10s.
  - Seletor de faixas de áudio e legendas embutidas/externas.
  - Controle de velocidade (0.5x até 2.0x).
  - Modos de aspecto (Ajustar, Zoom / Preencher, Esticar).
  - Memorização de progresso e "Continuar Assistindo".
  - Avanço automático de episódios para séries.
- **TV Ao Vivo (IPTV)**:
  - Suporte completo a listas M3U / M3U8 com categorias (Notícias, Filmes, Séries, Esportes, Cultura, etc.).
  - Canais públicos pré-configurados e importador de playlists personalizadas.
  - Indicador ao vivo pulsante e sistema de canais favoritos.
- **Scanner de Mídia Local (SAF)**:
  - Seleção de diretórios locais, pendrives e cartões de memória via Storage Access Framework.
  - Reconhecimento automático de formatos (.mp4, .mkv, .avi, .webm, .ts, etc.).
  - Parser inteligente de temporadas/episódios (S01E02, 1x05, etc.).
- **Integração TheMovieDB (TMDb)**:
  - Busca automática e manual de pôsteres, sinopses e notas.
- **Persistência Local (Room Database)**:
  - Banco de dados SQLite reativo com Kotlin Coroutines & Flow.

## 🛠️ Tecnologias
- **Linguagem**: Kotlin 2.0
- **UI**: Jetpack Compose com Material Design 3
- **Player de Vídeo**: AndroidX Media3 ExoPlayer 1.5
- **Banco de Dados**: AndroidX Room com KSP
- **Carregamento de Imagens**: Coil Compose
- **Rede & HTTP**: Retrofit 2 + OkHttp + Gson
