# Correção de Compatibilidade Cast & Otimização do Streaming Local

Plano abrangente para sanar a falha de decodificação e interrupção de conexão no Google Cast ao transmitir vídeos locais do dispositivo (MP4/MKV/4K), aprimorando o servidor HTTP de proxy, os cabeçalhos de streaming (`206 Partial Content`, `Keep-Alive`, `Accept-Ranges`), o tratamento de conexões simultâneas de sondagem de metadados pelo Chromecast, e implementando o banner de diagnóstico inteligente com ação imediata para continuar a reprodução localmente no celular.

---

## User Review & Decisões Confirmadas

> [!IMPORTANT]
> Decisões confirmadas pelo usuário na etapa de alinhamento:
> - **Otimização de Streaming Cast**: Implementação de headers HTTP completos (`Accept-Ranges`, `Content-Range`, suporte a `Keep-Alive`, chunking assíncrono de 128KB, e resposta rápida a requisições `HEAD`/`OPTIONS`).
> - **Tratamento de Incompatibilidade de Codec/Resolução**: Quando a TV/Chromecast emitir `IDLE_REASON_ERROR` (ex.: arquivo 4K AVC ou perfil incompatível com o hardware do Chromecast), a aplicação exibirá um banner informativo com detalhes da mídia e botão de 1 toque para **"Assistir no Celular"** mantendo a posição atual do vídeo.
> - **Abrangência de QA**: Execução de todas as melhorias e correções dos itens de estabilidade do relatório de QA.

---

## 1. Overview & Core Concept

- **Problema Diagnosticado**: Na tentativa de Cast de vídeo local (`3840x2160 AVC`), o Chromecast efetuou requisições parciais iniciais (15MB transmitidos) e abortou a conexão (`Connection reset` / `IDLE_REASON_ERROR`), pois servidores locais com fechamento prematuro de socket (`Connection: close`) ou falta de persistência em requisições de metadados (caixa `moov`) impedem o demuxer do Chromecast de bufferizar o fluxo. Além disso, dispositivos Chromecast de 1ª a 3ª geração não decodificam AVC 4K, necessitando de fallback claro para o usuário.
- **Solução Implementada**:
  1. **Refatoração do `MediaProxyServer` e `LocalStreamServer`**: Suporte a conexões persistentes (`Keep-Alive`), tratamento limpo de probes do Chromecast (requisições de início e fim de arquivo para atoms MP4), buffers assíncronos não bloqueantes e timeouts adaptativos.
  2. **Detecção e Banner de Diagnóstico Cast**: Componente de feedback proativo que detecta o erro da TV em tempo real, explica o motivo técnico (ex: resolução/codec não suportado pelo receptor) e oferece o botão para reproduzir na tela do smartphone a partir do mesmo minuto/segundo.
  3. **Resolução de Fontes e MimeTypes**: Refinamento de `CastMediaResolver` para identificar adequadamente codecs, taxas de amostragem de áudio e containers.

---

## 2. User Experience & Visual Design

### Fluxo do Usuário
1. **Início do Cast**: O usuário seleciona um vídeo do celular ou rede local e toca no botão de Cast.
2. **Streaming Estável**: O servidor local envia blocos otimizados de dados com suporte a saltos de tempo (seek) instantâneos.
3. **Detecção de Falha do Receptor**: Caso o receptor físico não suporte a resolução (ex: 4K em Chromecast 1080p) ou o codec de áudio:
   - A interface do player/home exibe um banner acolhedor no padrão Material Design 3 (cores de aviso `errorContainer`/`onSurfaceVariant`).
   - Texto claro: *"A TV não conseguiu reproduzir este formato (Vídeo 4K AVC). Deseja continuar assistindo na tela do celular?"*
   - Botões de ação rápida: **"Reproduzir no Celular"** (abre player imediatamente no timestamp atual) e **"Ver Diagnóstico"** (exibe logs detalhados de rede/mídia).

```
┌─────────────────────────────────────────────────────────────┐
│ 📺 Falha na reprodução do Chromecast                        │
│ A TV recusou o vídeo (4K AVC 3840x2160 incompatível no Cast)│
│                                                             │
│ [ ▶ Assistir no Celular ]       [ ℹ Ver Detalhes / Logs ]   │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Principais Decisões Técnicas e Trade-Offs

- **Headers HTTP & Keep-Alive no MediaProxyServer**:
  - *Abordagem*: Utilizar suporte a `Connection: keep-alive` com `Keep-Alive: timeout=30, max=100`, `Accept-Ranges: bytes`, e entrega contínua com chunks de `128KB` via canais NIO (`FileChannel` e `ParcelFileDescriptor`).
  - *Por que*: O receiver do Google Cast dispara múltiplos threads HTTP em paralelo (um para validar o cabeçalho `moov` no fim do arquivo e outro para bufferizar os primeiros frames). Fechar a conexão com `Connection: close` quebrava a inicialização do ExoPlayer interno do Chromecast.
- **Tratamento de Desconexão Normal de Sondagem**:
  - *Abordagem*: Diferenciar socket reset de sondagem (leitura parcial de metadados) de erros de I/O reais, evitando logs espúrios de erro durante a operação normal do Cast.
- **Fallback Automático & Contexto de Playback**:
  - *Abordagem*: Preservar a URI local, o título, legendas e o tempo decorrido no `PlayerViewModel`/`CastManager` para transição suave para o ExoPlayer local caso o usuário decida tocar no celular.

---

## 4. Technical Architecture & Data Strategy

### Diagrama de Comunicação do Streaming

```
┌────────────────────────┐         HTTP GET Range: 0- / Tail
│   Chromecast Device    │ ◄────────────────────────────────────────┐
│ (Default MediaReceiver)│ ──────────────────────────────────────┐  │
└────────────────────────┘         IDLE_REASON_ERROR / Playing   │  │
                                                                 │  │
                                                                 ▼  │
┌───────────────────────────────────────────────────────────────────┴┐
│ CineLocal App (Android)                                            │
│                                                                    │
│  ┌───────────────────────┐         ┌────────────────────────────┐  │
│  │   CastManager / UI    │ ◄────── │  MediaProxyServer (8899)   │  │
│  │  - Monitora Status    │         │  - HTTP 206 Partial Content│  │
│  │  - Banner Diagnóstico │         │  - Keep-Alive & NIO Chunks │  │
│  │  - Fallback Celular   │         │  - MimeType & CORS Headers │  │
│  └──────────┬────────────┘         └─────────────┬──────────────┘  │
│             │                                    │                 │
│             ▼                                    ▼                 │
│  ┌───────────────────────┐         ┌────────────────────────────┐  │
│  │ ExoPlayer (Local)     │         │ Android Storage / SAF      │  │
│  │ (Reprodução Fallback) │         │ (ParcelFileDescriptor/Uri) │  │
│  └───────────────────────┘         └────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
```

### Componentes e Modificações Planejadas

1. **`MediaProxyServer.kt`**:
   - Ajustar cabeçalhos HTTP para incluir `Connection: keep-alive`, `Keep-Alive: timeout=30`, `Accept-Ranges: bytes`, `Content-Length` exato e `Content-Range`.
   - Gerenciar requisições `HEAD` e `OPTIONS` com retorno imediato sem travar streams.
   - Reforçar o loop de envio com `FileChannel` para tolerar sondagens rápidas do Chromecast sem interromper a sessão.
   - Tratamento de timeout e desconexões seguras de clientes.

2. **`CastMediaResolver.kt`**:
   - Detecção precisa de MIME type com priorização correta para `video/mp4`, `video/x-matroska`, e `application/x-mpegURL`.
   - Logging enriquecido dos formatos de áudio/vídeo para diagnóstico facilitado na UI.

3. **`CastManager.kt`**:
   - Capturar o evento `IDLE_REASON_ERROR` e emitir estado de falha formatado (`CastPlaybackError`) contendo os metadados do episódio/mídia atual, posição salva e causa provável.
   - Fornecer callback/método `resumeLocally(context)` para abrir o player nativo na mesma posição.

4. **`PlayerScreen.kt` / `HomeScreen.kt` / Componentes UI**:
   - Adicionar o banner/dialog de recuperação de reprodução remota quando o Cast falhar.
   - Incrementar número de versão conforme diretrizes do projeto.

---

## 5. Plano de Verificação

1. **Compilação**: Executar `compile_applet` para validar integridade sintática e de tipos.
2. **Testes Unitários**: Executar testes de unidade existentes (`PlaybackSourceResolverTest`, `CastHlsAndTorrentTest`) e adicionar testes para validar os novos headers e comportamento do proxy.
3. **Validação de Fluxos**: Garantir que as requisições de stream respondem com código HTTP 206 e os cabeçalhos esperados pelo receptor Cast.
