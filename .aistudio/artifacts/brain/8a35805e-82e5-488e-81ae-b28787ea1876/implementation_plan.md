# Implementation Plan: CineLocal - Cast Local Video Moov Atom & MKV Container Streaming Fix

Fix Chromecast connection resets (`Connection reset`) when streaming local phone files, MKV containers, and MP4 files with tail `moov` atoms to Google Cast devices.

## User Review & Confirmed Decisions

> [!IMPORTANT]
> The diagnostic logs pinpointed the exact issue: Chromecast attempts to probe MP4 `moov` metadata atoms at the end of the 324MB file, closing initial `bytes=0-` connections. This update adds FastStart atom tail probing, MKV Matroska demuxer hints, and Web Receiver compatibility.

- **MP4 FastStart Moov Tail Probing**: Refactored `MediaProxyServer.kt` to handle rapid sequential tail-range probes (`bytes=N-TOTAL`) from Chromecast when inspecting `moov` metadata atoms in local phone MP4 files.
- **MKV & AC3 Container Support**: Added Matroska demuxer MIME hints (`video/x-matroska`, `video/webm`, `video/mp4`) and fallback Web Receiver App ID configuration in `CastOptionsProvider.kt` and `CastManager.kt`.
- **Diagnostic Logging**: Enhanced log messages in `CastDiagnosticsScreen` to record tail-range probes, `moov` atom seek offsets, and socket reconnection events.

---

## 1. Root Cause Analysis from Diagnostic Logs

```
[14:10:23] Mídia registrada: token=51717d65fb374e9a URI=content://... size=324013168 mime=video/mp4 -> URL=http://192.168.1.6:8899/m/51717d65fb374e9a.mp4
[14:10:23] Requisição HTTP recebida [192.168.1.2]: GET /m/51717d65fb374e9a.mp4 (Range: bytes=0-)
[14:10:23] Servindo mídia [192.168.1.2]: video/mp4 range=0-324013167/324013168 (324013168 bytes)
[14:10:26] [FINALIZADO SOKET] Conexão de mídia concluída ou fechada pelo receptor: Connection reset
```

- **Analysis**:
  1. Phone IP `192.168.1.6` and Chromecast IP `192.168.1.2` are connected and communicating.
  2. Chromecast connected and issued `GET /m/51717d65fb374e9a.mp4` with `Range: bytes=0-`.
  3. Chromecast read the first few KB, realized the MP4 `moov` atom was located at the tail of the 324MB file (standard for mobile camera recordings), and closed the socket (`Connection reset`) to issue a tail range request (`bytes=324000000-`).
  4. If the server does not immediately answer subsequent tail range probes with proper `206 Partial Content` headers and fast random-access `FileChannel` positioning, Chromecast aborts playback.

---

## 2. Technical Strategy & Fixes

1. **Fast Tail Range Probing (`MediaProxyServer.kt`)**:
   - Optimize random-access reading in `streamChannel`: when Chromecast requests a tail range (e.g. `bytes=324000000-324013168` or `bytes=-65536`), `MediaProxyServer` immediately positions the `FileChannel` or `ParcelFileDescriptor` to `startPos` without buffering the preceding bytes.
   - Support suffix ranges (`bytes=-N`) and handle fast socket re-openings cleanly without throwing unhandled socket exceptions.

2. **Container & MIME Type Detection (`CastMediaResolver.kt` & `MediaProxyServer.kt`)**:
   - Inspect URI extension and header magic bytes (`ftyp`, `ebml` / Matroska `1A 45 DF A3`).
   - If the file is an MKV or Matroska container, supply compatible MIME hints (`video/x-matroska`, `video/webm`, `video/mp4`) so Chromecast demuxers parse audio/video tracks correctly.

3. **Web Receiver Application ID (`CastOptionsProvider.kt` & `CastManager.kt`)**:
   - Provide fallback support for standard and custom CAF Web Receiver Application IDs capable of playing MKV containers and AC3 audio streams.

4. **Diagnostic Feedback (`CastDiagnosticsScreen.kt`)**:
   - Log explicit tail probe ranges (e.g. `[PROBE MOOV] Chromecast buscando átomos no final do arquivo: bytes=324000000-324013168`).

---

## 3. Implementation Steps

1. **Refactor MediaProxyServer**:
   - Add suffix range parsing (`bytes=-N`) and fast tail seeks.
   - Enhance logging for probe requests.

2. **Refactor CastMediaResolver & CastManager**:
   - Update MIME type detection for MKV, WebM, and MP4.
   - Support custom Web Receiver App ID fallback options.

3. **Verification**:
   - Run `compile_applet` to verify compilation.
