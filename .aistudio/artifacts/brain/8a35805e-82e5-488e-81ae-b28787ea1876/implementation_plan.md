# Conexão e Armazenamento em Rede PC (SMB / Windows Share) para o CineLocal

Permitir que o usuário conecte o CineLocal a computadores na mesma rede Wi-Fi/local usando compartilhamento de arquivos do Windows (SMBv2/v3), explore pastas remotas, selecione vídeos para reprodução instantânea ou catalogue temporadas e filmes na biblioteca local com suporte a streaming contínuo e Cast para TV.

> [!IMPORTANT]
> **Decisões Confirmadas com o Usuário:**
> - **Protocolo:** Rede Windows e pastas compartilhadas SMB (SMBv2/SMBv3).
> - **Organização & Visualização:** Explorador de pastas do PC com opção de catalogar na biblioteca (Filmes e Séries).
> - **Descoberta:** Busca e varredura automática de computadores na rede Wi-Fi local, além de conexão manual (IP/Host).

---

## 1. Visão Geral e Conceito Central

- **O que faz:** Transforma qualquer computador conectado à rede Wi-Fi local em um provedor de armazenamento de mídia para o CineLocal. O usuário pode escanear a rede local, parear com o PC, navegar pelas pastas e arquivos de vídeo como se fossem locais no celular, reproduzir vídeos diretamente com suporte a busca rápida (seek) e transmitir via Google Cast.
- **Público-Alvo:** Usuários com coleções de filmes, séries e vídeos no computador desktop/notebook que desejam assistir no celular ou transmitir na TV sem precisar copiar arquivos pesados para a memória interna do smartphone.
- **Valor Agregado:** Acesso direto a terabytes de mídia do PC sem custos, sem dependência de nuvem e sem estourar cotas de dados.

---

## 2. Experiência do Usuário & Design Visual

```
┌─────────────────────────────────────────────────────────────────┐
│  CineLocal - Armazenamento no Computador (PC / Rede Local)      │
├─────────────────────────────────────────────────────────────────┤
│  [ 🔍 Descobrir PCs na Rede Wi-Fi ]   [ + Adicionar IP Manual ] │
├─────────────────────────────────────────────────────────────────┤
│  COMPUTADORES ENCONTRADOS                                       │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 💻 PC-SALA (192.168.1.15)                    [ Conectar ] │  │
│  │    3 compartilhamentos: /Filmes, /Series, /Downloads       │  │
│  └───────────────────────────────────────────────────────────┘  │
│  SERVIDORES SALVOS                                              │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ 🖥️ Meu Computador (192.168.1.100)           ● Conectado    │  │
│  │    Pastas salvas: C:\Media\Filmes (48 vídeos)             │  │
│  └───────────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│  EXPLORADOR DE PASTAS (PC-SALA > Filmes > Ação)                 │
│  📁 .. (Voltar)                                                  │
│  📁 Matrix Trilogia [4K]                 [ Catalogar Pasta ]   │
│  🎬 Matrix_Reloaded_2003_1080p.mkv       [ Assistir ] [ + Lib ]│
│  🎬 Matrix_Revolutions_2003_1080p.mkv    [ Assistir ] [ + Lib ]│
└─────────────────────────────────────────────────────────────────┘
```

### Principais Fluxos do Usuário

1. **Descoberta Automática ou Cadastro de PC:**
   - O usuário clica em "Adicionar Mídia" ou na nova aba/seção "Armazenamento no PC (Rede)".
   - O app executa uma varredura rápida na sub-rede Wi-Fi (ex: `192.168.1.0/24`) procurando PCs com a porta 445 (SMB) aberta ou dispositivos NetBIOS/mDNS.
   - O usuário seleciona o PC encontrado ou digita o IP/Host, usuário e senha (com suporte a compartilhamentos anônimos/convidado).

2. **Navegação de Pastas e Compartilhamentos Remotos:**
   - Lista os compartilhamentos disponíveis do Windows (`C$`, `Filmes`, `Videos`, `Public`).
   - Navegação por diretórios com indicador de caminho (breadcrumbs), ícones de pastas e badges de formato de vídeo (`MKV`, `MP4`, `AVI`).

3. **Reprodução Instantânea & Streaming:**
   - Tocar em um arquivo de vídeo inicia imediatamente a reprodução no ExoPlayer do CineLocal com buffering otimizado e busca por range HTTP (206 Partial Content).
   - Suporte completo para transmissão Google Cast na TV direto da fonte de rede.

4. **Catalogação na Biblioteca:**
   - Botão "Catalogar Pasta no CineLocal" que lê a estrutura da pasta selecionada recursivamente no PC, aplica o `MediaNameParser` para identificar títulos/temporadas/episódios e adiciona à biblioteca de Filmes e Séries.

---

## 3. Decisões de Produto e Arquitetura

1. **Protocolo SMB nativo com Java/Kotlin:**
   - *Decisão:* Utilizar a biblioteca `com.hierynomus:smbj` para comunicação SMB2/SMB3 moderna, segura e com suporte a autenticação NTLMv2/Kerberos e compartilhamentos do Windows 10/11 e Linux Samba.
   - *Alternativas:* FTP/WebDAV exigiria que o usuário instalasse softwares adicionais no Windows. O SMB funciona nativamente com pastas compartilhadas do Windows sem instalar nada no computador.

2. **Ponte de Streaming HTTP Proxy Local para ExoPlayer e Google Cast:**
   - *Decisão:* Como o ExoPlayer e dispositivos Google Cast não abrem sockets SMB brutos diretamente, o CineLocal contará com um servidor proxy HTTP interno (`SmbStreamProxy`) rodando em `localhost`. Ele intercepta requisições HTTP e serve os bytes do arquivo SMB sob demanda com suporte a `Content-Range`.
   - *Vantagem:* Reprodução ultra-rápida, busca instantânea no vídeo (seek), compatibilidade total com o motor de legendas e transmissão direta para smart TVs e Chromecast.

3. **Persistência de Conexões e Credenciais no Room:**
   - *Decisão:* Criar `NetworkServerEntity` no Room para salvar IPs, nomes amigáveis, credenciais e pastas favoritas catalogadas, garantindo reconexão automática e sincronização offline.

---

## 4. Arquitetura Técnica & Diagrama de Componentes

```
┌────────────────────────────────────────────────────────────────────────┐
│                          UI (Jetpack Compose)                          │
│  ┌───────────────────────┐  ┌──────────────────┐  ┌──────────────────┐ │
│  │ NetworkDiscoveryScreen│  │  SmbExplorerView │  │ AddMediaDialog   │ │
│  └───────────┬───────────┘  └────────┬─────────┘  └────────┬─────────┘ │
└──────────────┼───────────────────────┼─────────────────────┼───────────┘
               │                       │                     │
               ▼                       ▼                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                 MainViewModel / NetworkMediaViewModel                  │
├────────────────────────────────────────────────────────────────────────┤
│  • scanLocalNetwork()       • browseDirectory()   • catalogRemoteFolder()│
└──────────────┬───────────────────────┬─────────────────────┬───────────┘
               │                       │                     │
               ▼                       ▼                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        Data & Streaming Layer                          │
│  ┌─────────────────────────┐         ┌───────────────────────────────┐ │
│  │   SmbClientRepository   │         │    SmbHttpProxyServer         │ │
│  │   (smbj SMB2/3 Client)  │         │    (Local HTTP Stream Bridge) │ │
│  └───────────┬─────────────┘         └───────────────┬───────────────┘ │
│              │                                       │                 │
│              ▼                                       ▼                 │
│  ┌─────────────────────────┐         ┌───────────────────────────────┐ │
│  │  AppDatabase (Room)     │         │ ExoPlayer & CastService       │ │
│  │  - NetworkServerEntity  │         │ (Plays http://localhost:PORT) │ │
│  │  - MediaItemEntity      │         │                               │ │
│  └─────────────────────────┘         └───────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### Componentes a Serem Desenvolvidos

1. **`gradle/libs.versions.toml` & `app/build.gradle.kts`:**
   - Adicionar dependência `smbj` para protocolo SMB2/SMB3.
2. **`data/model/NetworkServerEntity.kt` & `data/dao/NetworkServerDao.kt`:**
   - Tabela e DAO para persistência de servidores PC salvos, credenciais e pastas raiz configuradas.
3. **`data/smb/SmbManager.kt` & `data/smb/NetworkDiscovery.kt`:**
   - Gerenciador de sessões SMB, listagem de shares, navegação em diretórios e descoberta automática de hosts na sub-rede local.
4. **`data/smb/SmbHttpProxyServer.kt`:**
   - Servidor HTTP local de streaming para prover URLs `http://127.0.0.1:PORT/smb/...` compatíveis com ExoPlayer e Chromecast.
5. **`data/scanner/SmbFolderScanner.kt`:**
   - Escaneador recursivo de pastas do PC para catalogar filmes e séries no Room usando `MediaNameParser`.
6. **`ui/screens/NetworkStorageScreen.kt` & `ui/components/SmbExplorerDialog.kt`:**
   - Interface de gerenciamento de computadores, descoberta Wi-Fi, navegador de pastas e seleção de vídeos.
7. **Integração no `MainActivity.kt`, `AddMediaDialog.kt` e `PlaybackSourceResolver.kt`:**
   - Unificar a seleção de mídias locais do celular e do computador.
