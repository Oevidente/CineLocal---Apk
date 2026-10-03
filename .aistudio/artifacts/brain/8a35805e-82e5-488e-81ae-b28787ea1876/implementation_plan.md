# Ativação do Motor de Torrent Magnet e Seletor de Episódios para o CineLocal

Ativar o suporte a links **Torrent Magnet** no CineLocal, eliminando a mensagem de erro *"O motor de torrent ainda não está ativo"*, permitindo streaming de vídeo no player interno com contagem de peers/buffering, seleção de episódios para séries completas e opção direta de abrir em apps de torrent dedicados (LibreTorrent, Flud).

> [!IMPORTANT]
> **Decisões Confirmadas com o Usuário:**
> - **Reprodução:** Streaming com buffering no player do app e botão para abrir no cliente de torrent externo (Flud ou LibreTorrent).
> - **Séries e Múltiplos Arquivos:** Listar todos os episódios/arquivos do torrent para seleção pelo usuário antes ou durante a reprodução.

---

## 1. Visão Geral e Conceito Central

- **O que faz:** Ao tocar em um item torrent ou colar um link magnet (como a série `Neagley: Detetive Particular 2026`), o CineLocal processa os metadados (infoHash, trackers, tamanho, nome), lista os episódios disponíveis e inicia o streaming sequencial para o player interno, além de oferecer o botão de 1 toque para enviar o magnet diretamente para o LibreTorrent ou Flud.
- **Público-Alvo:** Usuários que consomem lançamentos e temporadas completas via magnet link sem precisar esperar o download total de 10GB terminar para começar a assistir.
- **Valor Agregado:** Experiência integrada, sem telas de erro e com controle total dos episódios da temporada.

---

## 2. Experiência do Usuário & Fluxo Visual

```
┌─────────────────────────────────────────────────────────────────┐
│  CineLocal - Reproduzir Torrent Magnet                          │
├─────────────────────────────────────────────────────────────────┤
│  🎬 Neagley.Detetive.Particular.2026.S01.WEB-DL.1080p           │
│  Tamanho Total: 8.88 GB • 23 Trackers                           │
├─────────────────────────────────────────────────────────────────┤
│  SELECIONAR EPISÓDIO / ARQUIVO:                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ ▶️ S01E01 - Episódio 1 (1.1 GB)              [ Assistir ] │  │
│  │ ▶️ S01E02 - Episódio 2 (1.05 GB)             [ Assistir ] │  │
│  │ ▶️ S01E03 - Episódio 3 (1.15 GB)             [ Assistir ] │  │
│  └───────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│  [ ⚡ Assistir no CineLocal ]  [ 📥 Abrir no LibreTorrent/Flud ] │
└─────────────────────────────────────────────────────────────────┘
```

### Principais Fluxos:
1. **Identificação e Lista de Episódios:**
   - O CineLocal lê o link magnet, extrai a lista de episódios (ex: `S01E01`, `S01E02`...) ou divide a temporada em episódios navegáveis.
   - O usuário escolhe o episódio desejado para reproduzir.
2. **Buffer e Streaming Integrado:**
   - O player exibe overlay com status: *Conectando a peers (Trackers ativos)*, taxa de transferência e barra de progresso.
   - O ExoPlayer recebe os dados via proxy HTTP local (`http://127.0.0.1:8997/torrent/stream`).
3. **Opção de Download Externo de Alta Velocidade:**
   - Se o usuário preferir baixar para assistir offline em viagem, um botão *"Abrir no App de Torrent (Flud/LibreTorrent)"* envia a Intent com o magnet link de forma instantânea.

---

## 3. Decisões de Arquitetura & Implementação Técnica

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Camada de Player & UI                           │
│  ┌─────────────────────────┐         ┌───────────────────────────────┐ │
│  │  TorrentEpisodeDialog   │         │    PlayerOverlay / Screen     │ │
│  │  (Lista de Episódios)   │         │ (Mostra peers, buffer, opção) │ │
│  └───────────┬─────────────┘         └───────────────┬───────────────┘ │
└──────────────┼───────────────────────────────────────┼─────────────────┘
               │                                       │
               ▼                                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    PlaybackSourceResolver & Provider                   │
├────────────────────────────────────────────────────────────────────────┤
│  • Valida magnet:?xt=urn:btih:... e extrai parâmetros                 │
│  • Inicia TorrentStreamProvider e serve URL http local                 │
│  • Intent de fallback para apps externos (android.intent.action.VIEW)  │
└──────────────┬───────────────────────────────────────┬─────────────────┘
               │                                       │
               ▼                                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│                 Local Torrent Proxy & Tracker Announcer                │
│  • Conecta aos trackers UDP/HTTP (opentrackr, openbittorrent, etc.)   │
│  • Serve streaming HTTP para ExoPlayer Media3 e Chromecast             │
└────────────────────────────────────────────────────────────────────────┘
```

### Componentes a Desenvolver / Atualizar:
1. **`player/PlaybackSourceResolver.kt`:**
   - Remover o bloqueio `ResolveResult.Fail("O motor de torrent ainda não está ativo.")`.
   - Adicionar resolução de fontes magnet para o `TorrentStreamEngine`.
2. **`player/TorrentStreamProvider.kt` & `player/TorrentStreamEngine.kt`:**
   - Implementar provedor ativo de streaming de magnet links com proxy HTTP local e anúncio em trackers UDP/HTTP.
   - Suporte a medição de peers, progresso e taxa de download.
3. **`ui/components/TorrentEpisodeDialog.kt` & `TorrentLaunchHelper.kt`:**
   - Modal para seleção de episódios de temporadas completas de séries baixadas via magnet.
   - Helper de Intent para abrir o magnet no LibreTorrent, Flud ou cliente do dispositivo.
4. **`player/PlayerViewModel.kt` & `PlayerScreen.kt`:**
   - Conectar o fluxo de reprodução de torrents sem erros, exibindo estatísticas e opções de controle.
