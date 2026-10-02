# Correção do Botão e Diálogo Nativo do Google Cast

Ajustar a integração do Google Cast no CineLocal para utilizar o **`MediaRouteButton` nativo oficial da biblioteca Google Play Services Cast Framework** com o filtro correto de categoria (`CastMediaControlIntent.categoryForCast(...)`). O botão será exibido dinamicamente apenas quando houver dispositivos Cast detectados na rede local ou quando uma sessão já estiver conectada, acionando o diálogo nativo do sistema.

## Decisões Confirmadas pelo Usuário

- **Visibilidade Dinâmica**: O botão de Cast será ocultado caso nenhum dispositivo Chromecast/Smart TV esteja disponível na rede Wi-Fi local, reaparecendo automaticamente assim que um receptor Cast for detectado.
- **Botão Nativo Oficial**: Utilização do `MediaRouteButton` encapsulado em `AndroidView` com `CastButtonFactory.setUpMediaRouteButton(...)`, garantindo acionamento direto do diálogo nativo do Android e do Google Play Services.

---

## 1. Causa Raiz do Problema

1. **Categoria de Controle Incorreta no `MediaRouteSelector`**:
   Anteriormente o seletor utilizava `MediaControlIntent.CATEGORY_REMOTE_PLAYBACK` genérico em vez do seletor específico do Google Cast (`CastMediaControlIntent.categoryForCast(DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)`), o que impedia o descobrimento de receptores Google Cast oficiais na rede Wi-Fi.
2. **Integração do Botão Nativo**:
   Para garantir que o diálogo de seleção do sistema seja aberto nativamente e que a visibilidade seja gerenciada automaticamente pelo framework do Android, usaremos o `MediaRouteButton` configurado com `CastButtonFactory.setUpMediaRouteButton(context, mediaRouteButton)`.

---

## 2. Arquitetura e Mudanças Técnicas

```
┌────────────────────────────────────────────────────────┐
│                   TopAppBar & PlayerScreen             │
│  - AndroidView { MediaRouteButton }                    │
│  - Configurado via CastButtonFactory.setUpMediaRouteButton
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│          Google Cast Framework & MediaRouter           │
│  - Seletor: CastMediaControlIntent.categoryForCast()   │
│  - Oculta botão se selector.isMatched() for falso       │
│  - Exibe diálogo nativo do Play Services               │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             Chromecast / Smart TV Receiver             │
└────────────────────────────────────────────────────────┘
```

### Detalhes das Alterações
1. **`CastManager.kt`**:
   - Atualização do `MediaRouteSelector` para usar `CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)`.
   - Gerenciamento preciso de estado do `MediaRouter` para verificar rotas ativas e notificar a UI sobre disponibilidade.
2. **`NativeCastButton.kt`**:
   - Novo componente Jetpack Compose utilizando `AndroidView` que renderiza o `MediaRouteButton` oficial do Android.
   - Vinculação direta com `CastButtonFactory.setUpMediaRouteButton(context, mediaRouteButton)`.
3. **Visibilidade Dinâmica em `MainActivity.kt` & `PlayerOverlay.kt`**:
   - O botão de Cast em ambos os locais só ficará visível quando `castState.hasAvailableDevices == true` ou `castState.isConnected == true`.

---

## Plano de Verificação
- Recompilar a aplicação via `compile_applet`.
- Atualizar e salvar o APK em `/apk/CineLocal-debug.apk`.
