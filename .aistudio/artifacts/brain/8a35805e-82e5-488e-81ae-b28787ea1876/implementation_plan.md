# Implementation Plan: CineLocal - Cast Streaming Fix & Developer Mode Diagnostics

Resolve Google Cast failures when streaming local phone files, magnet torrents, and IPTV channels, and introduce a dedicated **Developer Mode & Real-Time Cast Diagnostics** screen to provide transparent status reporting and live HTTP server logs.

## User Review & Confirmed Decisions

> [!IMPORTANT]
> The diagnostic suite and local server enhancements provide end-to-end visibility into network binding, SAF URI read permissions, HTTP Range headers, and Google Cast player state events.

- **Developer Mode UX**: Added a "Modo Desenvolvedor" toggle in Settings unlocking a dedicated `CastDiagnosticsScreen.kt` with live log streaming, active IP/port indicators, SAF access verification, and Cast error diagnostics.
- **HTTP Streaming Server Logging**: Enriched `MediaProxyServer` with full HTTP request/response logging, method tracking (`GET`, `HEAD`, `OPTIONS`), byte-range inspection, and multi-port fallback (ports `8899`, `8080`, `9090`, dynamic).
- **Local Phone, Torrent & IPTV Proxying**: Updated `CastMediaResolver` and `MediaProxyServer` to proxy all local `content://` files, magnet torrent buffers, and IPTV streams over the phone's primary Wi-Fi/Ethernet IPv4 address.

---

## 1. Overview & Core Concept

When casting local phone videos, magnet torrents, or IPTV channels, Chromecast requires an accessible HTTP/HTTPS stream on the local Wi-Fi network (`192.168.x.x`) with valid CORS headers and byte-range support (`206 Partial Content`). 

This update fixes the IP resolution, SAF URI file descriptor reading, and CORS/Range headers in `MediaProxyServer.kt` and `CastMediaResolver.kt`, while exposing a **Developer Mode & Real-Time Diagnostics** UI so users and developers can inspect the live status of every Cast transaction.

---

## 2. User Experience & Visual Design

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Settings -> Modo Desenvolvedor                       │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Cast & Local Server Diagnostics                      │
├────────────────────────────────────────────────────────────────────────┤
│ Server Status: RUNNING (192.168.1.45:8899)  [Test Endpoint Ping]       │
│ Active Interface: wlan0 (Wi-Fi 5GHz)                                  │
├────────────────────────────────────────────────────────────────────────┤
│ Real-Time HTTP & Cast Logs (Auto-scrolling feed):                      │
│ [09:55:12] Google Cast Session Started: "Living Room TV"               │
│ [09:55:14] Registered Media: content://media/external/video/media/102  │
│ [09:55:15] GET /m/a1b2c3d4.mp4 | Range: bytes=0- | 206 Partial Content │
│ [09:55:16] Cast Status: PLAYING (Position: 0s / Duration: 01:45:12)    │
├────────────────────────────────────────────────────────────────────────┤
│ [Limpar Logs]                                      [Copiar Diagnóstico]│
└────────────────────────────────────────────────────────────────────────┘
```

- **Settings Screen**: Toggle "Modo Desenvolvedor" to reveal the "Diagnóstico do Cast e Servidor" entry point.
- **Diagnostics Screen**: Displays real-time status of the local HTTP server, current Wi-Fi IP, port number, active SAF permissions, and a scrolling terminal log of all HTTP traffic and Cast session events.
- **Live Feedback**: Any error decoding media on Chromecast (`PLAYER_STATE_IDLE` with `IDLE_REASON_ERROR`) appears immediately in the diagnostic log with human-readable explanations.

---

## 3. System Architecture & Component Flow

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Cast Streaming & Diagnostics Flow              │
└────────────────────────────────────────────────────────────────────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    ▼                               ▼                               ▼
┌──────────────────────┐   ┌──────────────────────┐   ┌──────────────────┐
│ Local Phone Video    │   │ Magnet Torrent       │   │ IPTV Channel     │
│ (content:// / file://)│   │ (TorrentStreamEngine)│   │ (M3U8 / TS)      │
└──────────┬───────────┘   └──────────┬───────────┘   └──────────┬───────┘
           │                          │                          │
           └──────────────────────────┼──────────────────────────┘
                                      ▼
                        ┌──────────────────────────┐
                        │ CastMediaResolver        │
                        │ - Resolve LAN IP         │
                        │ - Register in Proxy      │
                        └─────────────┬────────────┘
                                      │
                                      ▼
                        ┌──────────────────────────┐
                        │ MediaProxyServer         │
                        │ - HTTP 206 Partial Content│
                        │ - CORS headers & Range   │
                        │ - SAF File Descriptor    │
                        │ - Real-time Log Buffer   │
                        └─────────────┬────────────┘
                                      │
                 ┌────────────────────┴────────────────────┐
                 ▼                                         ▼
   ┌───────────────────────────┐             ┌──────────────────────────┐
   │ Google Cast Device (TV)   │             │ CastDiagnosticsScreen    │
   │ http://192.168.x.x:8899/m/│             │ (Developer Mode UI)      │
   └───────────────────────────┘             └──────────────────────────┘
```

---

## 4. Key Technical Strategy & Fixes

1. **IP Network Interface Resolution (`MediaProxyServer.kt` & `LocalNetworkUtils.kt`)**:
   - Inspect network interfaces (`wlan0`, `eth0`, `en0`) and `ConnectivityManager.getLinkProperties()`.
   - Exclude loopback (`127.0.0.1`), cellular data (`rmnet`), and VPN (`tun0`) interfaces when picking the Cast server address.
   - Return valid site-local IPv4 address (`192.168.x.x`, `10.x.x.x`).

2. **SAF Permission & Stream Handling (`MediaProxyServer.kt`)**:
   - Safely open SAF `content://` URIs using `ContentResolver.openFileDescriptor(uri, "r")` with fallback to `ContentResolver.openInputStream(uri)`.
   - Catch `SecurityException` and log detailed diagnostic messages if persistable permissions are missing.

3. **HTTP Server Range & CORS Headers (`MediaProxyServer.kt`)**:
   - Serve HTTP `206 Partial Content` with `Content-Range: bytes START-END/TOTAL`.
   - Send `Accept-Ranges: bytes` and `Access-Control-Allow-Origin: *`.
   - Handle `OPTIONS` preflight, `HEAD` metadata checks, and `GET` chunk streaming.

4. **IPTV Proxying (`MediaProxyServer.kt` & `CastMediaResolver.kt`)**:
   - Proxy cleartext HTTP IPTV streams through the local server to bypass mixed-content and CORS restrictions on Chromecast.

5. **Developer Mode & Real-time Diagnostics (`CastDiagnosticsScreen.kt` & `SettingsScreen.kt`)**:
   - Store developer mode state in Room Settings (`developer_mode_enabled`).
   - Create `CastDiagnosticsScreen.kt` with live log subscription, ping test button, and log export feature.
   - Attach `RemoteMediaClient` error listener to log detailed Cast decoder errors (`IDLE_REASON_ERROR`).

---

## 5. Implementation Steps

1. **Local Network & Server Refactoring**:
   - Refactor `LocalNetworkUtils.kt` and `MediaProxyServer.kt` to improve IP resolution, multi-port binding, and SAF content streaming.
   - Add thread-safe circular log buffer `recentLogs` to `MediaProxyServer`.

2. **Cast Resolver & IPTV Proxying**:
   - Update `CastMediaResolver.kt` to route local SAF URIs, active torrent buffers, and IPTV streams through `MediaProxyServer`.
   - Attach detailed loggers to `CastManager.kt`.

3. **Developer Mode UI**:
   - Create `CastDiagnosticsScreen.kt` with live log viewer and server controls.
   - Add Developer Mode toggle switch in `SettingsScreen.kt`.

4. **Build Verification**:
   - Execute `compile_applet` to verify compilation.
