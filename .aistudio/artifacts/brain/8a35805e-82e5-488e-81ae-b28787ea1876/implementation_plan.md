# Transmissão Cast Continuada em Segundo Plano & Modo Picture-in-Picture (PiP)

Plano de execução para garantir que a transmissão para o Google Cast continue reproduzindo sem pausas quando o aplicativo for minimizado ou o usuário alternar para outros apps, mantendo um serviço em primeiro plano (*Foreground Service*) com controles de notificação, além da implementação do modo Picture-in-Picture (PiP) para o player nativo do smartphone.

---

## User Review & Decisões Confirmadas

> [!IMPORTANT]
> Decisões confirmadas pelo usuário na etapa de alinhamento:
> - **Transmissão Cast em Segundo Plano**: Manter o `CastServerService` e o `MediaProxyServer` rodando como *Foreground Service* dedicado (`mediaPlayback`). Garantir que minimização e troca de app **NÃO** causem pause nem desconexão no Chromecast.
> - **Notificação de Controle Cast**: Notificação persistente no painel do Android com botões de ação rápidos (*Play/Pause*, *Sair do Cast*).
> - **Modo Picture-in-Picture (PiP)**: Ativação automática do modo PiP ao minimizar o aplicativo enquanto um vídeo local estiver rodando no celular, além de botão manual de acionamento de PiP nos controles do player.

---

## 1. Overview & Core Concept

- **Problema 1 (Pausa do Cast ao Minimizar)**: Atualmente, quando o app vai para o segundo plano, os métodos do ciclo de vida da `Activity` ou destruição parcial do player local acionavam pausas indeferidas ou o sistema Android matava o socket do servidor HTTP local (`MediaProxyServer`).
- **Problema 2 (Navegação Multitarefa no Smartphone)**: Usuários que assistem no próprio celular precisam sair do app para responder mensagens ou navegar sem interromper o vídeo.
- **Soluções**:
  1. **Ajuste de Ciclo de Vida do Cast & Foreground Service**: Atualizar `CastServerService` para rodar como Foreground Service com tipo `mediaPlayback` e notificação de mídia rica. Isolar o estado do Cast no `CastManager` para ignorar `onStop` da Activity quando a sessão de Cast estiver ativa.
  2. **Android Picture-in-Picture (PiP)**: Declarar `android:supportsPictureInPicture="true"` no `AndroidManifest.xml`, configurar `PictureInPictureParams` no `MainActivity`, disparar `enterPictureInPictureMode` em `onUserLeaveHint()` e ocultar overlays de controle durante o modo PiP.

---

## 2. User Experience & Visual Design

### Fluxo do Usuário (Cast em Segundo Plano)
1. **Início do Cast**: O usuário inicia a transmissão para o Chromecast.
2. **Minimização**: O usuário minimiza o app ou troca de tela/aplicativo.
3. **Comportamento Esperado**: A TV continua reproduzindo o vídeo sem qualquer interrupção ou travamento. Uma notificação fixa do CineLocal aparece na barra de status com título da mídia, nome do dispositivo Cast e controles (Play, Pause, Desconectar).

```
┌─────────────────────────────────────────────────────────────┐
│ 📺 CineLocal • Transmitindo em Sala de Estar                │
│ Stranger Things - S01E01                                    │
│ [ ⏸ Pausar ]             [ ❌ Desconectar ]                  │
└─────────────────────────────────────────────────────────────┘
```

### Fluxo do Usuário (Modo PiP)
1. **Início da Reprodução Local**: O usuário assiste a um vídeo diretamente na tela do celular.
2. **Gesto de Início/Home**: O usuário retorna à tela inicial ou troca de app.
3. **Comportamento Esperado**: A tela do player se reduz suavemente a um quadro flutuante (PiP) no canto da tela, mantendo a proporção do vídeo e ocultando botões de controle para melhor visualização.

---

## 3. Principais Decisões Técnicas e Trade-Offs

- **Isolamento de Ciclo de Vida para Cast**:
  - *Abordagem*: No `MainActivity.kt` e `PlayerViewModel.kt`, impedir chamadas automáticas de `exoPlayer.pause()` em `onStop()` ou `onDispose()` caso `castState.isConnected == true`.
  - *Por que*: Durante o Cast, a mídia é renderizada pela TV e servida pelo `MediaProxyServer`. A Activity do celular é apenas o controle remoto.
- **Serviço de Segundo Plano Garantido (`CastServerService`)**:
  - *Abordagem*: Registrar o serviço no manifesto com `android:foregroundServiceType="mediaPlayback"` e utilizar `startForeground()` imediatamente ao iniciar qualquer stream local para Cast.
- **Android PiP API**:
  - *Abordagem*: Utilizar `setPictureInPictureParams` com `Rational(width, height)` do vídeo para proporção adequada no quadro flutuante.

---

## 4. Technical Architecture & Data Strategy

### Diagrama do Fluxo de Segundo Plano & PiP

```
┌─────────────────────────────────────────────────────────────┐
│ Android System (Lifecycle & Background)                     │
└──────────────┬──────────────────────────────┬───────────────┘
               │ (App minimizado)             │ (Local Video)
               ▼                              ▼
┌─────────────────────────────┐  ┌────────────────────────────┐
│ CastServerService           │  │ MainActivity / ExoPlayer   │
│ - Foreground Service        │  │ - onUserLeaveHint()        │
│ - MediaProxyServer Active   │  │ - enterPictureInPictureMode│
│ - Notificação de Controles  │  │ - Esconde Overlays na UI   │
└──────────────┬──────────────┘  └────────────────────────────┘
               │
               ▼
┌─────────────────────────────┐
│ Chromecast Device (TV)      │
│ (Reprodução contínua 100%)  │
└─────────────────────────────┘
```

### Modificações nos Arquivos

1. **`AndroidManifest.xml`**:
   - Adicionar `android:supportsPictureInPicture="true"` e `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"` na `MainActivity`.
   - Garantir declaração da permissão `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

2. **`CastServerService.kt`**:
   - Adicionar ações de Intent na notificação (`ACTION_PLAY_PAUSE`, `ACTION_STOP_CAST`).
   - Manter notificação atualizada com estado de reprodução e metadados.

3. **`MainActivity.kt`**:
   - Implementar `onUserLeaveHint()` para entrar em PiP se um vídeo local estiver ativo (`!castState.isConnected && isPlayerActive && isPlaying`).
   - Tratar `onPictureInPictureModeChanged()` para passar o estado `isInPictureInPictureMode` para o `PlayerScreen`.
   - Garantir que `onStop()` não interrompa a sessão do Cast.

4. **`PlayerOverlay.kt` / `PlayerScreen.kt`**:
   - Adicionar botão de PiP na barra de controles do player.
   - Ocultar botões, barra de progresso e gradientes de fundo no modo PiP.

5. **Incremento de Versão**:
   - Atualizar `versionCode` para `215` e `versionName` para `1.6.7` em `build.gradle.kts`, `package.json` e `index.html`.
