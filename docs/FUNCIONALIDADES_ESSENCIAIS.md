# Funcionalidades Essenciais do CineLocal

Este documento descreve as funcionalidades essenciais do aplicativo que NUNCA devem ser removidas em refatorações ou edições futuras.

## 1. Seleção de Vídeo(s) Solto(s)
- **Launcher:** `ActivityResultContracts.OpenMultipleDocuments()` em `MainActivity.kt`.
- **Botão UI:** Opção "Selecionar Vídeo(s)" em `AddMediaDialog.kt` (aba "Mídia Local").
- **Gravação & Reprodutor:** Método `addStandaloneVideoFiles` em `MainViewModel.kt` e `MediaRepository.kt`. Permite selecionar 1 ou mais vídeos (.mp4, .mkv, .avi) e iniciar reprodução imediata.

## 2. Seleção de Pasta Local
- **Launcher:** `ActivityResultContracts.OpenDocumentTree()` em `MainActivity.kt`.
- **Botão UI:** Opção "Selecionar Pasta Local" em `AddMediaDialog.kt`.

## 3. Navegação Mobile Organizada (BottomBar)
- **Layout:** Barra inferior com 7 abas (`AppTab`: Início, Filmes, Séries, Torrents, Ao Vivo, Favoritos, Config).
- **Format:** Rótulos dos botões com `softWrap = false`, `maxLines = 1`, `fontSize = 9.sp` para garantir exibição do rótulo "Favoritos" sem quebrar em duas linhas.

## 4. Integrações de Mídia
- Suporte a reprodução local (SAF), streaming direto, Magnet Torrent e canais IPTV ao vivo.
- Suporte a servidores e navegadores SMB de rede local.
