# CineLocal Premium Glassmorphism & Netflix Upgrade

Melhoria visual completa do CineLocal para alcançar um design extremamente moderno e premium próximo ao da Netflix, aplicando o efeito de glassmorphism (vidro fosco semi-transparente) na barra de navegação flutuante, cartões de mídia, botões secundários e folhas de detalhes.

## User Review & Critical Decisions

> [!IMPORTANT]
> Com base nas suas respostas às perguntas de esclarecimento, adotaremos as seguintes diretrizes de design de alta fidelidade:

-   **Nível do Glassmorphism**: Transparência elegante com fundo escuro suave combinada a bordas ultrafinas de vidro fosco (`Color(0x22FFFFFF)`) para garantir contraste e legibilidade impecáveis.
-   **Estilo da Barra de Navegação**: Barra flutuante em formato de cápsula com cantos arredondados e fundo semi-transparente, posicionada na parte inferior central da tela, permitindo que o conteúdo role por trás com efeito translúcido (overlap).
-   **Elementos com Efeito**: Cards de mídia (`MediaCard`), botões secundários (como "Detalhes" no HeroBanner e botões de ação na sheet), e a folha de detalhes do filme/série (`MediaDetailSheet`).

---

## 1. Overview & Core Concept

-   **O que faz**: Atualiza e eleva todo o visual do aplicativo CineLocal, transformando-o de uma interface com cinzas planos para uma estética luxuosa escura ("luxury dark") inspirada na Netflix.
-   **Principal Valor**: Aumentar o prazer visual ao navegar pelas coleções de filmes e séries locais, criando profundidade tridimensional com efeitos de sobreposição e transparência reflexiva.

---

## 2. User Experience & Visual Design

### Fluxo de Navegação e Sobreposição

O usuário navega através das abas do app mantendo a barra de navegação flutuante fixa na base da tela. Ao rolar as fileiras de mídias, as imagens e títulos passam suavemente sob a barra de navegação, criando uma rica sensação de profundidade tridimensional.

### Identidade Visual & Temas

-   **Paleta de Cores**:
    -   `DarkBackground`: `#0D0D0D` (Preto puro Netflix para contraste infinito em telas OLED).
    -   `GlassContainer`: `rgba(26, 26, 26, 0.75)` (Cinza grafite semi-transparente).
    -   `GlassBorder`: `rgba(255, 255, 255, 0.12)` (Borda reflexiva fina para delimitar os cards e barras).
    -   `CineRed`: `#E50914` (Vermelho icônico Netflix mantido como cor de destaque e seleção).
-   **Efeitos de Transparência e Blur**:
    -   Nas versões do Android compatíveis (Android 12+), utilizaremos a renderização de efeito de desfoque de fundo nativo para um autêntico vidro fosco.
    -   Nas versões anteriores do Android, usaremos uma composição especial de gradientes escuros refinados e opacidade ajustada, garantindo elegância e excelente legibilidade em qualquer dispositivo.

---

## 3. Key Product Decisions & Trade-Offs

-   **Decisão 1: Barra de Navegação Flutuante Overlaid**
    -   *Abordagem Escolhida*: Remover a barra de navegação do slot fixo do `Scaffold` e renderizá-la dentro de um contêiner `Box` fixado no fundo central.
    -   *Vantagem*: Garante que o conteúdo role por baixo dela de forma translúcida.
    -   *Impacto*: Será necessário adicionar um espaçador inferior (`Spacer` de 90.dp) no final de todas as telas roláveis para que nenhuma informação ou card importante fique inacessível sob a barra.
-   **Decisão 2: Estilização do MediaCard**
    -   *Abordagem Escolhida*: Cards de mídias terão cantos arredondados mais suaves, um fundo translúcido escuro com borda reflexiva branca sutil de 1.dp e um leve gradiente interno.

---

## 4. Technical Architecture & Data Strategy

### Componentes & Hierarquia Visual

```
┌─────────────────────────────────────────────────────────┐
│                     CineLocal App                       │
├─────────────────────────────────────────────────────────┤
│ [Top Bar] C CINELOCAL                [Cast] [Search] [+] │
├─────────────────────────────────────────────────────────┤
│ [Scrollable Content: HomeScreen / MoviesScreen / etc.]   │
│   │                                                     │
│   ├─► [Hero Banner] (Featured Movie & Glass Buttons)    │
│   │                                                     │
│   ├─► [Media Row] (Horizontal scroll of Glass Cards)    │
│   │                                                     │
│   └─► [Spacer] (Ensures content scrolls above dock)     │
│                                                         │
├─────────────────────────────────────────────────────────┤
│ [Floating Glass Navigation Dock]                        │
│   [ Início ] [ Filmes ] [ Séries ] ... [ Config ]      │
└─────────────────────────────────────────────────────────┘
```

### Componentes a serem Refatorados

1.  **`MainActivity.kt`**:
    -   Substituir a `NavigationBar` padrão do Scaffold por um dock de navegação flutuante customizado com efeito de vidro (semi-transparente, cantos arredondados de 24.dp, bordas sutis e fundo flutuante sobreposto).
    -   Ajustar os paddings das telas para que o conteúdo preencha toda a tela.
2.  **`HeroBanner.kt`**:
    -   Atualizar o botão secundário "Detalhes" para um visual de vidro semi-transparente refinado com fundo `Color(0x33FFFFFF)` e borda suave.
3.  **`MediaCard.kt`**:
    -   Aplicar borda reflexiva suave e fundo glassmorphism translúcido ao card de mídia.
4.  **`MediaDetailSheet.kt`**:
    -   Atualizar os botões secundários da folha de detalhes ("Favoritar", "Remover", "Baixar") para herdar o visual de vidro fosco de alta fidelidade.
    -   Modificar o fundo geral da folha para um visual escuro semi-transparente premium.
