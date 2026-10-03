# Motor P2P de Torrent Nativo Integrado com Cast para o CineLocal

Implementação de um **Motor BitTorrent P2P Nativo e Exclusivo** dentro do CineLocal para streaming direto de links Magnet sem depender de aplicativos externos, com downloads sequenciais prioritários (cabeçalhos MP4/MKV), cache temporário auto-limpante e retransmissão para Chromecast / Google Cast na rede Wi-Fi local.

---

## 1. Visão Geral e Conceito Central

- **O que faz:** O CineLocal atua como um cliente P2P completo. Ao selecionar um magnet link, o aplicativo conecta aos trackers UDP/HTTP, descobre peers na rede BitTorrent, baixa prioritariamente o início do arquivo (para que o ExoPlayer leia o cabeçalho do vídeo instantaneamente) e serve os dados via servidor HTTP local.
- **Transmissão para Cast (Chromecast/Smart TV):** O servidor proxy local expõe o fluxo na interface Wi-Fi da rede local (`http://192.168.X.X:8997/p2p/stream.mp4`), permitindo espelhar o torrent P2P em qualquer tela conectada.
- **Gerenciamento de Memória:** Todo o vídeo é salvo no diretório de cache temporário (`cacheDir/torrent_stream/`) e apagado automaticamente ao fechar o player, evitando lotar o armazenamento do aparelho.

---

## 2. Experiência do Usuário & Painel P2P no Player

```
┌─────────────────────────────────────────────────────────────────┐
│  CineLocal Player - P2P Torrent Engine                          │
├─────────────────────────────────────────────────────────────────┤
│  🎬 Neagley.Detetive.Particular.2026.S01.WEB-DL.1080p           │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ ⚡ P2P Status: Conectado a 14 Peers (3 Seeders)           │  │
│  │ 📥 Velocidade: 4.2 MB/s  •  Buffer: 15% (35 MB carregados) │  │
│  │ 📶 Transmitindo na rede local: http://192.168.1.15:8997   │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  [ ▶ Reproduzir ]   [ 📡 Enviar para Chromecast / TV ]          │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Decisões de Arquitetura & Implementação Técnica

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Camada de Player UI & Cast                      │
│  ┌─────────────────────────┐         ┌───────────────────────────────┐ │
│  │   PlayerOverlay (P2P)   │         │    CastManager / Chromecast   │ │
│  │  (Stats, Peers, Buffer) │         │ (Endereço IP da Wi-Fi Local)  │ │
│  └───────────┬─────────────┘         └───────────────┬───────────────┘ │
└──────────────┼───────────────────────────────────────┼─────────────────┘
               │                                       │
               ▼                                       ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   NativeP2PTorrentEngine (Servidor)                    │
├────────────────────────────────────────────────────────────────────────┤
│  • Conexão UDP/HTTP Trackers & Handshake P2P                           │
│  • Download Sequencial (Peça 0 = Cabeçalho MP4 / EBML MKV Primeiro)     │
│  • Grava blocos válidos em cacheDir/torrent_cache/                     │
│  • Servidor HTTP Proxy com Suporte a Range 206 para ExoPlayer & Cast   │
└────────────────────────────────────────────────────────────────────────┘
```

### Principais Arquivos e Alterações:
1. **`data/torrent/NativeP2PTorrentEngine.kt`**:
   - Cliente BitTorrent P2P completo com montagem de pacotes, handshake, download sequencial prioritário de blocos e proxy HTTP servidor local.
2. **`data/torrent/LocalNetworkUtils.kt`**:
   - Identifica o IP local da Wi-Fi (`192.168.x.x`) para permitir envio HLS/HTTP direto para o Chromecast/Google Cast.
3. **`player/PlaybackSourceResolver.kt` & `PlayerViewModel.kt`**:
   - Conecta a fonte torrent ao `NativeP2PTorrentEngine`, alimentando o ExoPlayer e o `CastManager`.
4. **`ui/components/PlayerOverlay.kt`**:
   - Exibe o painel de estatísticas P2P em tempo real (Peers ativos, velocidade em MB/s e barra de buffer do torrent).
