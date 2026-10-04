# Correção e Aprimoramento da Descoberta de Pastas SMB no Windows

Plano completo para solucionar a listagem vazia de pastas compartilhadas do PC (SMB/Windows), aprimorar os métodos de autenticação e permitir acesso direto a qualquer compartilhamento configurado na rede local.

---

### Decisões Confirmadas e Contexto do Usuário

> [!IMPORTANT]
> **Ambiente Confirmado:**
> - **Sistema Operacional do PC:** Windows configurado com compartilhamento protegido por senha desativado (acesso Convidado/Todos).
> - **Nomes das Pastas:** Nomes como `Filmes`, `Series`, `Videos` ou compartilhamentos personalizados.
> - **Sintoma:** O aplicativo conecta ao PC via IP/Wi-Fi, porém a lista de compartilhamentos/pastas fica em branco ou inacessível.

- **Causa Raiz Identificada:**
  1. No Windows 10/11, conexões SMB anônimas sem contexto de autenticação são frequentemente rejeitadas pelo subsistema SMB2/SMB3 a menos que sejam negociadas com contexto `Guest`/Convidado ou usuário local.
  2. A função de listagem de compartilhamentos dependia de testes sequenciais com tratamento silencioso de exceções, ocultando erros de permissão NTFS (permissão de segurança "Todos" vs "Compartilhamento").
  3. Ausência de campo para digitar diretamente o nome de um compartilhamento customizado (ex: `Filmes` ou `//192.168.1.X/Filmes`).

---

## 1. Visão Geral e Experiência do Usuário

O CineLocal permitirá navegar, descobrir e catalogar com facilidade arquivos de vídeo localizados em computadores Windows, Linux ou servidores NAS na rede Wi-Fi local sem travar ou exibir listas vazias.

### Principais Benefícios:
1. **Multi-Estratégia de Autenticação Automática:** Ao conectar com "Acesso Sem Senha / Convidado", o app tenta automaticamente o modo Convidado (`Guest`), Anônimo (`Anonymous`) e usuário em branco, garantindo compatibilidade com todas as configurações do Windows.
2. **Entrada Direta de Compartilhamento:** Botão para informar diretamente o nome do compartilhamento (ex: `Filmes`, `Series`, `Videos`, `D$`), permitindo acesso imediato mesmo quando a enumeração do servidor estiver restrita.
3. **Diagnóstico e Feedback Visual Claro:** Em vez de tela em branco, o usuário recebe mensagens claras caso falte permissão NTFS no Windows, com link direto para o guia passo a passo ilustrado.
4. **Guia Atualizado do Windows no App:** Instruções específicas para marcar permissão de leitura na aba "Segurança" (NTFS) e aba "Compartilhamento" no Windows 10/11.

---

## 2. Fluxo do Usuário e Interface

```
┌────────────────────────────────────────────────────────────────────────┐
│                   DIÁLOGO DE EXPLORADOR SMB                            │
├────────────────────────────────────────────────────────────────────────┤
│  [Computador Sala (192.168.1.100)]                  [❓ Guia PC] [✕]    │
│  Status: Conectado • Modo: Convidado (Automático)                      │
├────────────────────────────────────────────────────────────────────────┤
│  [➕ Adicionar Nome de Compartilhamento]  [🔄 Atualizar Lista]         │
│                                                                        │
│  PASTAS COMPARTILHADAS ENCONTRADAS:                                    │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ 📁 Filmes                                                        │  │
│  │    Compartilhamento SMB • Conexão estabelecida                   │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │ 📁 Series                                                        │  │
│  │    Compartilhamento SMB • Conexão estabelecida                   │  │
│  ├──────────────────────────────────────────────────────────────────┤  │
│  │ 📁 Videos                                                        │  │
│  │    Compartilhamento SMB • Conexão estabelecida                   │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  [+ Conectar a outro compartilhamento manualmente]                     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Arquitetura Técnica e Estratégia de Dados

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CAMADA DE UI (Compose)                        │
│   SmbExplorerDialog  ◄──►  PcSetupGuideDialog  ◄──►  MainViewModel      │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼─────────────────────────────────────┐
│                    SmbClientManager / SmbFolderScanner                  │
│  1. getOrCreateSession(config):                                         │
│     - Tenta AuthContext(username, password) ou Guest/Anonymous fallback │
│  2. listShares(config):                                                 │
│     - Varre nomes comuns + shares salvos + fallback ativo               │
│  3. directConnectShare(config, shareName):                              │
│     - Testa acesso direto ao share digitado pelo usuário                │
│  4. listDirectory(config, share, path):                                 │
│     - Retorna arquivos e subpastas de vídeo com tratamento de erro      │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼─────────────────────────────────────┐
│                    PERSISTÊNCIA & SEGURANÇA (Room)                      │
│   - NetworkServerEntity (host, port, username, isAnonymous, lastShare)  │
│   - Credenciais seguras sem expor em URLs ou logs                       │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Plano Detalhado de Modificações

### A. Melhorias no `SmbClientManager.kt`
- Implementar estratégia de autenticação inteligente:
  - Se `isAnonymous = true` ou `username` em branco: tentar primeiro `AuthenticationContext.guest()` (usuário "Guest", domínio "", senha vazia) e depois `AuthenticationContext.anonymous()`.
  - Ampliar a lista de nomes comuns de compartilhamento para incluir variações comuns em português e inglês (`Filmes`, `Series`, `Videos`, `Downloads`, `Public`, `Users`, `Media`, `Cinema`, `Animes`, `Compartilhado`, `Shared`, `Novelas`, `Musicas`, `Disco Local (C)`, `C`, `D`, `E`).
  - Adicionar função `testAndConnectShare(config, shareName)` para validar e salvar novos compartilhamentos informados pelo usuário.
  - Tratar exceções específicas do SMB (STATUS_ACCESS_DENIED, STATUS_LOGON_FAILURE, STATUS_BAD_NETWORK_NAME) e retornar mensagens de erro amigáveis e explicativas.

### B. Melhorias no `SmbExplorerDialog.kt`
- Adicionar botão **"Adicionar Compartilhamento Manual"** (permite que o usuário digite o nome exato da pasta compartilhada, ex: `Filmes`, `Series`, etc.).
- Exibir estado de conexão com detalhes úteis (ex: se o Windows recusou a conexão por falta de permissão ou se o compartilhamento foi encontrado).
- Adicionar botão de alternância rápida entre "Modo Convidado/Sem Senha" e "Modo com Usuário/Senha do Windows" direto no cabeçalho do explorador, sem precisar recriar o computador.
- Salvar os compartilhamentos descobertos/acessados para que fiquem sempre disponíveis em acessos futuros.

### C. Atualização do `PcSetupGuideDialog.kt`
- Destacar a permissão da aba **Segurança** do Windows:
  1. Clicar com o botão direito na pasta > Propriedades > aba **Compartilhamento** > Compartilhamento Avançado > Permissões > Adicionar "Todos".
  2. Aba **Segurança** (Crítico no Windows 10/11) > Editar > Adicionar "Todos" com permissão de Leitura e Execução.
  3. Configurações de Rede > Central de Rede e Compartilhamento > Configurações de compartilhamento avançadas > Ativar descoberta de rede e compartilhamento de arquivos.

### D. Testes e Validação
- Compilação do aplicativo via Gradle.
- Testes unitários para autenticação SMB e tratamento de nomes de compartilhamento.
- Atualização do número de versão do aplicativo e integridade do PWA / GitHub Pages.
