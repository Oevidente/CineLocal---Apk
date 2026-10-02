# Reformulação Completa do Sistema de Transmissão Cast (Google Cast + DLNA/UPnP + IP)

Reformular todo o sistema de transmissão para garantir que o botão de Cast fique **sempre visível permanentemente** e forneça uma busca multi-protocolo ativa (Google Cast oficial, DLNA/UPnP, mDNS) além de permitir conexão direta por IP local da Smart TV/Chromecast.

## Decisões Confirmadas pelo Usuário

- **Botão Permanente**: O ícone do Cast fica visível de forma fixa e permanente no topo e no player, garantindo acesso imediato a qualquer momento sem depender de pré-detecção oculta.
- **Suporte Multi-Protocolo**:
  1. **Google Cast (Chromecast & Google TV)**
  2. **DLNA / UPnP / Smart TV (Samsung Tizen, LG WebOS, Roku, Fire TV, etc.)**
  3. **Conexão Direta por IP Local**: Campo no painel para digitar o IP da TV (ex: `192.168.1.50`) com teste de conectividade e envio do stream.

---

## 1. Modificações na Arquitetura

```
┌────────────────────────────────────────────────────────┐
│                   TopAppBar & PlayerScreen             │
│  - Botão de Cast Permanente (CastButton com ícone M3)  │
│  - Dispara o diálogo de busca ativa e conexão por IP   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                    CastDeviceDialog                    │
│  - Lista em tempo real de dispositivos Google Cast      │
│  - Lista de dispositivos DLNA / Smart TVs conectadas   │
│  - Opção de reconexão / busca forçada                  │
│  - Aba/Campo de "Conexão Direta por IP da TV"           │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             CastManager Multi-Protocolo                │
│  - Google Cast SDK (CastContext & MediaRouter)          │
│  - mDNS / SSDP Discovery (DLNA/UPnP)                   │
│  - Servidor Local de Streaming HTTP (LocalStreamServer)│
└────────────────────────────────────────────────────────┘
```

---

## 2. Detalhes de Implementação

1. **`CastManager.kt`**:
   - Inicialização no escopo da aplicação com requisições de descoberta ativa `MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN`.
   - Adição de múltiplos seletores: `CastMediaControlIntent.categoryForCast(...)`, `MediaControlIntent.CATEGORY_REMOTE_PLAYBACK`, `MediaControlIntent.CATEGORY_LIVE_AUDIO/LIVE_VIDEO`.
   - Suporte a verificação de IP e envio do stream local (`LocalStreamServer`) para Smart TVs e DLNA.

2. **`CastButton.kt`**:
   - Botão Compose sempre visível com destaque visual quando conectado.
   - Aciona diretamente o `CastDeviceDialog` e força re-escaneamento ativo.

3. **`CastDeviceDialog.kt`**:
   - Exibição limpa de dispositivos encontrados na rede local.
   - Campo para inserção direta de IP com botão "Conectar por IP".
   - Botão para acionar também o seletor nativo do sistema Android caso disponível.

4. **Permissões de Rede em `AndroidManifest.xml`**:
   - `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_FINE_LOCATION`, `NEARBY_WIFI_DEVICES`.

---

## Plano de Verificação

- Recompilar a aplicação via `compile_applet`.
- Salvar o novo APK atualizado em `/apk/CineLocal-debug.apk`.
