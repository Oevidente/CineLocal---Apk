# Implementation Plan - CineLocal Data Layer, Torrent Magnet Streaming & Build Fix

CineLocal is a modern local & network media player app built with Jetpack Compose, ExoPlayer, and Google Cast support for Android. This revised plan explicitly includes full support for adding, parsing, and streaming media directly from **Magnet Torrent links** (P2P / WebTorrent HTTP gateway stream bridge), alongside Room database recovery and CI workflow build updates.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following key decisions were confirmed through user feedback:

- **Magnet Torrent Streaming Engine**: Full support for adding magnet links (`magnet:?xt=urn:btih:...`), auto-extracting infoHashes and titles via `TorrentUtils`, saving torrent items in the Room database, and playing them via local HTTP streaming proxy bridge / WebTorrent gateway.
- **Confirmed Data Layer Architecture**: Full Room database with `MediaItemEntity`, `EpisodeEntity`, `IptvChannelEntity`, and `SettingEntity` for offline persistence and resume progress.
- **Confirmed Metadata Strategy**: Optional TMDB API key input in Settings with automatic metadata fetching and fallback to local metadata.
- **Confirmed CI/CD Strategy**: Updated GitHub Actions workflow to compile debug APK and save the output directly into `apk/CineLocal-debug.apk` for easy download.

---

## 1. Overview & Core Concept

CineLocal enables users to stream local videos, IPTV playlists, direct HTTP/HLS links, and **P2P Magnet Torrents**. Users can add torrent magnet links via the "Adicionar" button or use "Colar Magnet & Reproduzir" from clipboard. The player resolves torrent infoHashes, connects via webseed/gateway proxy server, and streams directly into ExoPlayer.

---

## 2. User Experience & Visual Design

- **Magnet Torrent Management**: Dedicated **Torrents Screen** tab with "Adicionar", "Colar Magnet & Reproduzir", and a step-by-step usage guide for copying magnet links from movie sites.
- **Add Media Dialog (Torrent Tab)**: Tabbed modal allowing instant magnet link input, title override, and TV Series vs. Movie classification.
- **Cinematic Dark Theme**: Deep dark gray surfaces `#121212`, obsidian backgrounds `#0D0D0D`, crimson accent `#E50914`, and clear text contrast.

---

## 3. Key Technical Architecture for Torrent Streaming

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            TorrentsScreen / AddMediaDialog                  │
│   (User enters or pastes magnet:?xt=urn:btih:... link from clipboard)       │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              TorrentUtils & MediaRepository                 │
│  - Extracts infoHash, display name (&dn=), trackers (&tr=)                   │
│  - Saves MediaItemEntity (kind = TORRENT, streamUrl = magnetUri, infoHash)  │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       PlayerViewModel & LocalStreamServer                   │
│  - Converts magnet URI to HTTP stream URL via local proxy / gateway         │
│  - Passes stream URL to ExoPlayer / Google Cast receiver                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Torrent & Data Layer Specifications

1. **`TorrentUtils` (`com.example.cinelocal.data.torrent`)**:
   - Parses `magnet:?` URLs to extract `infoHash`, display name (`dn`), trackers (`tr`), and webseeds (`ws`).
   - Generates streaming proxy URLs (`http://127.0.0.1:8080/torrent?hash=...` / public WebTorrent gateway fallbacks) for ExoPlayer.

2. **Entities & Enums (`com.example.cinelocal.data.model`)**:
   - `MediaKind`: `MOVIE`, `SERIES`, `TORRENT`, `DIRECT_STREAM`
   - `MediaItemEntity`: Stores `infoHash`, `streamUrl` (magnet link), title, poster/backdrop, `progressSeconds`, and `isFavorite`.
   - `EpisodeEntity`, `IptvChannelEntity`, `SettingEntity`, `MediaWithEpisodes`.

3. **DAOs & Room Database**:
   - `MediaDao`, `EpisodeDao`, `IptvChannelDao`, `SettingDao`, and `AppDatabase`.

4. **Repository (`MediaRepository`)**:
   - `addTorrentMedia(magnetUri, customTitle, isSeries)`: Parses magnet link, fetches metadata from TMDB if title is available, and inserts into Room DB.

5. **CI Workflow (`.github/workflows/android.yml`)**:
   - Configures Java 17 environment, compiles debug APK with Gradle, copies binary to `apk/CineLocal-debug.apk`, and uploads build artifact.

---

## 4. Implementation Step Plan

1. **Step 1: Torrent Utilities & Models**: Create `TorrentUtils` for magnet link parsing and `MediaKind.TORRENT` data models under `com.example.cinelocal.data`.
2. **Step 2: Room Entities, DAOs & Database**: Implement `MediaItemEntity`, `EpisodeEntity`, `IptvChannelEntity`, `SettingEntity`, DAOs, and `AppDatabase`.
3. **Step 3: Repository & Magnet Proxy Gateway**: Implement `MediaRepository` with magnet addition, M3U parser, TMDB metadata sync, and torrent stream proxy URL generator.
4. **Step 4: ViewModel & UI Integration**: Wire magnet adding in `AddMediaDialog`, `TorrentsScreen`, `MainViewModel`, `PlayerViewModel`, and ExoPlayer.
5. **Step 5: GitHub Actions CI Script**: Update `.github/workflows/android.yml` to compile and output `apk/CineLocal-debug.apk`.
6. **Step 6: Build Verification**: Run `compile_applet` to verify compilation and generate the updated APK.
