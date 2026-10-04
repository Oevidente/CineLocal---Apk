# CineLocal v1.6.1 — Correção Completa do Google Cast & Suporte Multimídia

Correção integral do fluxo de transmissão Google Cast (Chromecast), eliminando falhas de carregamento e paradas na TV para todas as 4 origens de vídeo (Local/SAF/Pendrive, Redes SMB/Windows, Canais IPTV ao Vivo e Torrents P2P), incorporando diagnóstico detalhado de streaming em tempo real, elevação de versão para v1.6.1 e sincronização de artefatos APK para download.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Decisões confirmadas pelo usuário:**
> 1. **Origens de Vídeo Priorizadas no Cast**: Suporte completo e prioritário para arquivos locais (SAF/pendrive), compartilhamentos de rede SMB/Windows, canais ao vivo IPTV e streaming de torrents/magnets.
> 2. **Diagnóstico e Feedback**: Exibição de status detalhado de streaming na tela do player, diálogo de Cast e na interface diagnóstica (IP local, porta, taxa de transferência, buffer e faixas de legenda).
> 3. **Versionamento e Distribuição**: Incremento da versão para **v1.6.1** em todos os arquivos de configuração (Android Gradle, manifestos PWA, HTML e scripts de release), salvando o APK gerado na pasta `apk/` e `dist/`.

---

## 1. Overview & Core Concept

- **O que faz**: Permite transmitir qualquer vídeo do CineLocal diretamente para Smart TVs e aparelhos Google Cast/Chromecast conectados à mesma rede Wi-Fi/LAN com carregamento contínuo, controles remotos de reprodução (Play, Pause, Seek ±10s, barra de progresso) e legendas WebVTT sincronizadas.
- **Causa Raiz do Problema Anterior**:
  1. *Incompatibilidade de Content-Type*: O receptor padrão do Chromecast rejeitava o tipo de mídia `video/x-matroska` ou `application/octet-stream` em arquivos `.mkv` e locais. O tipo de mídia sanitizado para o Default Media Receiver deve ser `video/mp4` ou `video/webm` para contêineres e `application/x-mpegURL` para HLS.
  2. *Falta de Suporte a Preflight CORS (OPTIONS) e Headers HTTP*: O receptor HTML5 do Chromecast envia requisições `OPTIONS` e `GET bytes=...` com cabeçalhos CORS rigorosos (`Access-Control-Allow-Origin: *`, `Access-Control-Allow-Headers: Range, ...`, `Access-Control-Expose-Headers`). No proxy SMB e nos proxies de arquivo, a ausência de resposta `OPTIONS` e de cálculo dinâmico de `Content-Range` provocava falha imediata após o handshake inicial.
  3. *Resolução do IP da Rede Wi-Fi*: Em aparelhos Android com conexões simultâneas (dados móveis + Wi-Fi ou interfaces virtuais), o IP informado ao Chromecast podia resolver para interfaces internas (`127.0.0.1` ou `100.x.x.x`), impedindo o Chromecast de baixar os blocos de vídeo.
  4. *Exceções em Foreground Services no Android 13+*: O início de serviço sem permissão de notificação abortava o Cast antes do envio do `MediaLoadRequestData`.

---

## 2. User Experience & Visual Design

- **Experiência de Transmissão (Cast Flow)**:
  - Ao tocar no botão de Cast, o diálogo exibe dispositivos descobertos com indicação de força de sinal e IP de rede local.
  - Ao selecionar a TV e dar play no vídeo, a interface exibe badge animado **"Transmitindo para [Nome da TV]"** com status em tempo real (Conectando ➔ Carregando buffer ➔ Reproduzindo).
  - O player local pausa suavemente e os controles da tela passam a comandar o Chromecast (Play/Pause, Seek deslizante, pular 10s para frente/trás).
  - Um card de diagnóstico rápido no diálogo de Cast mostra o IP de rede da transmissão, porta ativa do proxy HTTP e estado dos buffers.
- **Página Web & PWA (GitHub Pages)**:
  - Badge e cabeçalhos atualizados para **v1.6.1**.
  - Botão de download com link direto para `apk/CineLocal-v1.6.1.apk`.
  - Service worker com cache versionado `cinelocal-pwa-v1.6.1` para atualização instantânea sem problemas de cache legado.

---

## 3. Key Product Decisions & Trade-Offs

- **Decisão 1: Servidor HTTP Unificado de Streaming com Suporte HTTP 206 Completo e CORS**
  - *Abordagem*: Refatorar o `MediaProxyServer` e `SmbStreamProxy` para responder adequadamente a requisições HTTP `OPTIONS` (Preflight), `HEAD` e `GET` com suporte a `Range: bytes=X-Y`, `Content-Range: bytes X-Y/Z` e `Accept-Ranges: bytes`.
  - *Por que*: O motor de reprodução do Chromecast exige requisições parciais com seeking e cabeçalhos CORS liberados para carregar contêineres de vídeo e faixas de áudio.
- **Decisão 2: Mapeamento Seguro de MimeTypes para o Chromecast Default Media Receiver**
  - *Abordagem*: Mapear contêineres de vídeo padrão (MP4, MKV, AVI, MOV, TS) para `video/mp4`, WebM para `video/webm` e fluxos HLS (.m3u8) para `application/x-mpegURL`.
  - *Por que*: O player receptor web do Chromecast aceita decodificação direta de streams compatíveis quando identificado com MimeType aceito pelo navegador.
- **Decisão 3: Descoberta Robusta de IPv4 na Rede Wi-Fi/Ethernet**
  - *Abordagem*: Priorizar interfaces físicas de rede local (`wlan0`, `eth0`, `en0`) com endereços privados (`192.168.x.x`, `10.x.x.x`, `172.16-31.x.x`) via `ConnectivityManager` e `NetworkInterface`.
  - *Por que*: Garante que o Chromecast receba a URL acessível pela LAN sem conflitos com redes móveis ou VPNs.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                    CineLocal Android App                    │
│                                                             │
│  ┌───────────────────────┐      ┌────────────────────────┐  │
│  │   PlayerViewModel     │◄────►│      CastManager       │  │
│  └──────────┬────────────┘      └───────────┬────────────┘  │
│             │                               │               │
│             ▼                               ▼               │
│  ┌───────────────────────┐      ┌────────────────────────┐  │
│  │ PlaybackSourceResolver│      │    MediaProxyServer    │  │
│  │ (Local, SMB, IPTV,    │      │  (HTTP 206 / Range     │  │
│  │  Torrents P2P)        │      │   CORS / WebVTT Subs)  │  │
│  └───────────────────────┘      └───────────┬────────────┘  │
└─────────────────────────────────────────────┼───────────────┘
                                              │ HTTP LAN Stream
                                              ▼
                                 ┌────────────────────────┐
                                 │   Google Chromecast    │
                                 │  Default Media Receiver│
                                 └────────────────────────┘
```

### Component & File Strategy:
1. **`CastMediaResolver.kt` & `CastManager.kt`**:
   - Sanitização de mimeTypes para Chromecast.
   - Tratamento seguro de exceções de permissão/serviço no Android 13+.
   - Carregamento de faixas de legenda WebVTT e metadados completos.
2. **`MediaProxyServer.kt` & `SmbStreamProxy.kt`**:
   - Tratamento completo de `OPTIONS` (Preflight) com cabeçalhos CORS essenciais.
   - Respostas `206 Partial Content` precisas com `Content-Range` e `Content-Length`.
   - Cálculo dinâmico e robusto do tamanho de arquivos (incluindo torrents em download).
   - Resolução precisa do endereço IPv4 Wi-Fi local (`wlan0`/`eth0`).
3. **`PlayerViewModel.kt` & Telas de UI**:
   - Sincronização suave de estado de reprodução entre player local e Cast sem travamentos.
   - Feedback visual de status de transmissão.
4. **Versionamento e Distribuição**:
   - Atualização da versão para `1.6.1` em `build.gradle.kts`, `metadata.json`, `index.html`, `manifest.json`, `sw.js` e `package.json`.
   - Cópia do APK construído para a pasta `apk/CineLocal-v1.6.1.apk` e `apk/CineLocal.apk`.
