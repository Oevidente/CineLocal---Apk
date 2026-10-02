# CineLocal Android - Biblioteca de Mídia & Google Cast Nativo

Plano de portabilidade e desenvolvimento da aplicação nativa Android em Kotlin com Jetpack Compose, trazendo a experiência completa de streaming de mídia local (filmes/séries), canais de TV ao vivo (IPTV), torrents e integração nativa com o **Google Cast SDK** (Chromecast) para espelhamento e reprodução contínua na TV.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Decisões confirmadas com base no alinhamento inicial:**
> - **Gerenciamento de Arquivos Locais**: Utilização do *Storage Access Framework (SAF)* com Document Tree Picker e `MediaStore` para permitir que o usuário escolha pastas no armazenamento interno, cartão SD ou pendrive USB OTG, persistindo as URIs com permissões persistentes.
> - **Google Cast Nativo**: Integração com `play-services-cast-framework` (Media3 Cast Extension / CastContext) com botão nativo `MediaRouteButton`, mini-controller na barra inferior e expanded-controller para controle remoto completo do Chromecast.
> - **Fontes de Conteúdo**: Suporte completo a Filmes & Séries Locais, Canais IPTV ao vivo (M3U/M3U8) e suporte a reprodução de torrents.
> - **Política de Custos e IA**: Sem uso de IA paga ou backends com cobrança, persistência 100% local com Room Database e geração de APK para download direto.

---

### 1. Overview & Core Concept

- **O que o aplicativo faz**: CineLocal Android é um media center local no estilo Netflix, permitindo escanear pastas de vídeos no dispositivo móvel ou pendrive USB, organizar automaticamente episódios e temporadas, assistir canais abertos via IPTV, gerenciar links torrent e transmitir qualquer mídia instantaneamente para TVs com Chromecast ou Google TV.
- **Público-Alvo**: Usuários que desejam uma central de entretenimento offline e online portátil no smartphone, tablet ou Android TV box, com interface fluida e moderna em Dark Theme.
- **Principal Valor**: Centralização de mídias locais e streams em uma interface premium com transições fluidas e Google Cast integrado com 1 toque.

---

### 2. User Experience & Visual Design

#### Fluxos Principais
1. **Página Inicial (Home / Catálogo)**:
   - Hero Banner cinematográfico destacando a última mídia assistida com botão "Continuar Assistindo" e "Detalhes".
   - Carrosséis horizontais categorizados: *Em Andamento*, *Filmes*, *Séries*, *Canais Favoritos*.
   - Barra de navegação inferior (ou Navigation Rail em tablets/foldables) com abas: *Início*, *Filmes*, *Séries*, *Canais IPTV*, *Configurações & Pastas*.
2. **Player de Vídeo Nativo & Google Cast**:
   - Player construído com Jetpack Media3 ExoPlayer com suporte a aceleração por hardware, seleção de faixas de áudio e legendas (SRT / VTT embutidas ou externas).
   - Botão Cast na barra superior do player e da home: ao conectar, o vídeo é transferido para o Chromecast, exibindo o Mini-Controller flutuante no app.
3. **Gerenciador de Pastas & Scanner Local**:
   - Seletor nativo do sistema Android (`ActivityResultContracts.OpenDocumentTree`) para selecionar pastas do armazenamento ou pendrive OTG.
   - Detecção automática de estrutura de pastas (Série -> Temporada -> Episódios) com extração de metadados.
4. **Canais IPTV ao Vivo**:
   - Visualização em grade ou lista de canais com filtro por categorias e busca rápida.
   - Marcador de canais favoritos armazenados no banco local Room.

#### Identidade Visual & Tema
- **Estilo**: *Cinema Dark Theme* (Inspirado no visual premium do Netflix / Plex).
- **Paleta de Cores**:
  - Fundo principal: `#0E0E10` / `#141414` (Preto profundo com alto contraste).
  - Superfícies de Cards: `#1F1F23` e `#2A2A2E` com bordas sutis translúcidas.
  - Acentos Primários: `#E50914` (Vermelho Cinema) e `#38BDF8` (Azul Cast/Destaque).
  - Textos: `#FFFFFF` (Títulos), `#A1A1AA` (Metadados secundários).
- **Tipografia**: Material 3 Typography com pesos Medium e SemiBold para pôsteres e títulos, legibilidade otimizada em qualquer tamanho de tela.

---

### 3. Key Product Decisions & Trade-Offs

- **Media3 ExoPlayer + Cast Extension**:
  - *Abordagem*: Adotar o Jetpack Media3 (`androidx.media3:media3-exoplayer`, `media3-ui`, `media3-cast`) com `CastContext` nativo do Google Play Services.
  - *Motivo*: É o padrão oficial recomendado pelo Google, garantindo sincronização automática de estado entre reprodução local e transmissão remota via Chromecast.
- **Armazenamento e Metadados com Room**:
  - *Abordagem*: Banco de dados local SQLite via Room para guardar pastas mapeadas, catálogo de mídias, histórico de reprodução (timestamps) e favoritos de IPTV.
  - *Motivo*: 100% offline, veloz, sem consumo de cotas de banco de dados na nuvem e sem custos para o usuário.
- **Layout Adaptável (Mobile & Tablet)**:
  - *Abordagem*: Adaptação com `WindowWidthSizeClass` (Bottom Navigation em celulares, Navigation Rail / Dual Pane em tablets e telas dobráveis).

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
├────────────────────────────────────────────────────────────────────────┤
│  HomeScreen  │  MediaDetailSheet  │  ExoVideoPlayer  │  IptvChannelView│
│  (Hero &     │  (Episódios &      │  (Subtitles,     │  (Grid, Search, │
│   Carrossel) │   Metadados)       │   Audio, Cast)   │   Favorites)    │
└────────┬──────────────────┬─────────────────┬─────────────────┬────────┘
         │                  │                 │                 │
         ▼                  ▼                 ▼                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                      ViewModel & StateFlow Layer                       │
├────────────────────────────────────────────────────────────────────────┤
│     HomeViewModel   │   PlayerViewModel   │   ChannelsViewModel        │
└────────┬──────────────────────────────────────────────────────┬────────┘
         │                                                      │
         ▼                                                      ▼
┌──────────────────────────────────┐  ┌──────────────────────────────────┐
│         Local Data Source        │  │       Media & Cast Engine        │
│ ┌──────────────────────────────┐ │  │ ┌──────────────────────────────┐ │
│ │   Room Database (SQLite)     │ │  │ │  Media3 ExoPlayer & Cast     │ │
│ │ - MediaEntities (Filmes/Ep)  │ │  │ │  - Hardware Transcoding/Play │ │
│ │ - FolderTreeUriEntity        │ │  │ │  - CastSessionManager        │ │
│ │ - PlaybackHistoryEntity      │ │  │ └──────────────────────────────┘ │
│ │ - IptvFavoritesEntity        │ │  │ ┌──────────────────────────────┐ │
│ └──────────────────────────────┘ │  │ │  Storage Access Scanner      │ │
│                                  │  │ │  - DocumentFile Tree Parser  │ │
│                                  │  │ └──────────────────────────────┘ │
└──────────────────────────────────┘  └──────────────────────────────────┘
```

#### Entidades do Banco de Dados (Room)
1. `MediaFolderEntity`: Caminho/URI persistida de pastas adicionadas pelo usuário.
2. `MediaItemEntity`: Identificação de filme ou série, título, banner, tipo, ano, duração e caminhos dos arquivos.
3. `EpisodeEntity`: Episódios organizados por temporada, índice e URI de arquivo.
4. `PlaybackProgressEntity`: Posição em milissegundos para retomar reprodução de onde parou.
5. `IptvChannelEntity` / `IptvFavoriteEntity`: Canais e status de favoritos.

---

### Plan Approval & Next Steps
Após a aprovação deste plano, o projeto será estruturado com todas as dependências nativas (`media3`, `play-services-cast-framework`, `room`, `compose-material3`), implementando o visual completo, scanner de arquivos via SAF, player de vídeo nativo, canais IPTV e suporte nativo ao Google Cast.
