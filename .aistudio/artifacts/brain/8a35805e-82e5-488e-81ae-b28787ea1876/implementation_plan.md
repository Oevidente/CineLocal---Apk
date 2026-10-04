# Padronização Visual do CineLocal — Logotipo e Ícones "C"

Unificar a identidade visual do **CineLocal** em todas as plataformas (Android nativo e PWA Web), implementando o logotipo oficial com a letra **C** branca sobre o fundo vermelho cinema (`#E50914`), atualizando ícones adaptativos do APK, favicon, manifesto PWA e elementos da interface do aplicativo.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> Decisões confirmadas com base nas preferências selecionadas:
> - **Estilo da Letra C**: Sólida, elegante e minimalista, com tipografia em peso bold e fundo vermelho cinema puro (`#E50914`).
> - **Escopo de Aplicação**:
>   1. **Aplicativo Android (APK)**: Novo ícone adaptativo (`ic_launcher.xml`, `ic_launcher_round.xml`, `ic_launcher_foreground.xml`, `ic_launcher_background.xml` e rasters PNG em todas as densidades).
>   2. **PWA & Web**: Favicon SVG/PNG, `manifest.json`, `index.html` e telas de instalação.
>   3. **UI do Aplicativo**: Cabeçalho superior (Header / TopAppBar) e Hero Banner / Apresentação exibindo o novo emblema "C" em destaque.

---

## 1. Overview & Core Concept

- **O que será feito**:
  - Geração de vetores SVG/XML nítidos e bitmaps de alta definição do emblema "C" branco sobre `#E50914`.
  - Configuração do ícone adaptativo oficial do Android com safe-zone de 66dp no canvas de 108dp.
  - Atualização dos metadados e assets da versão PWA instalável no desktop e mobile.
  - Atualização do componente visual de logo na barra superior e telas do app.
- **Público-alvo**: Usuários do CineLocal no Android e navegadores desktop/mobile.
- **Valor agregado**: Reconhecimento visual imediato na tela inicial do celular, no launcher da TV, na aba do navegador e dentro da experiência do app.

---

## 2. User Experience & Visual Design

### Identidade Visual & Paleta
- **Cor de Fundo**: Vermelho Cinema Puro (`#E50914` / `CineRed`).
- **Símbolo Central**: Letra **C** estilizada em branco puro (`#FFFFFF`), traço limpo, cantos harmônicos e proporção equilibrada.
- **Tema do App**: Modo escuro cinematográfico (`#141414` background, `#1F1F1F` surface) com acentos em vermelho cinema (`#E50914`).

### Onde a Nova Marca Aparece
1. **Ícone do Launcher (Android)**: Ícone adaptativo quadrado e redondo na home do Android.
2. **Barra Superior (TopAppBar)**: Emblema "C" estilizado ao lado do texto "CineLocal" com badge de status.
3. **Hero Banner & Splash**: Card de boas-vindas com o emblema em destaque.
4. **PWA / Web Browser**: Favicon na aba do navegador, tela de splash PWA ao abrir no Android/Windows.

---

## 3. Key Product Decisions & Trade-Offs

- **Vetor XML Nativo vs Imagem Raster no Foreground**:
  - *Abordagem*: Utilizar desenho vetorial limpo no `ic_launcher_foreground.xml` e no cabeçalho do app para garantir nitidez matemática em qualquer resolução (de telas 720p até 4K e TVs).
  - *Por quê*: Evita distorções, reduz tamanho do APK e carrega instantaneamente.
- **Manutenção de Versão e Pasta APK**:
  - O APK continuará sendo mantido atualizado na pasta `apk/` com incremento de versão para download direto.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                   Identidade Visual                    │
│            Letra C Branca sobre Fundo #E50914          │
└───────────────────────────┬────────────────────────────┘
                            │
       ┌────────────────────┼────────────────────┐
       ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ Android APK  │    │   PWA Web    │    │  Compose UI  │
│  - Launcher  │    │  - Favicon   │    │  - Header    │
│  - RoundIcon │    │  - Manifest  │    │  - Hero Logo │
│  - Vectors   │    │  - Index PWA │    │  - Dialogs   │
└──────────────┘    └──────────────┘    └──────────────┘
```

- **Arquivos e Recursos Envolvidos**:
  - Android Resources:
    - `res/drawable/ic_launcher_foreground.xml`
    - `res/drawable/ic_launcher_background.xml`
    - `res/mipmap-*/ic_launcher.png` e `ic_launcher_round.png`
    - `res/drawable/ic_cinelocal_logo.xml`
  - Web / PWA:
    - `index.html` (logo SVG inline / favicon)
    - `manifest.json` (ícones do app instalado)
  - UI Components:
    - `HomeScreen.kt` / `HeroBanner.kt` / `MainActivity.kt` (atualização do logotipo do app bar)
