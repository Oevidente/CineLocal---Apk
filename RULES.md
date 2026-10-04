# Regras do projeto
Leia antes de qualquer alteração. O CI (`scripts/check-rules.sh`) verifica as regras marcadas.

| ID | Regra | Verificada no CI |
|----|-------|------------------|
| V1 | Aumentar a versão a cada mudança de código (`versionName` em `app/build.gradle.kts`, semver). Mostrar a versão na interface. | R02 |
| D1 | Poupar o banco: cache com validade, paginação, sem listener em tempo real em coleção grande, sem polling < 60 s, escritas em lote com debounce. | R34 (aviso) |
| I1 | Nenhuma API/SDK de IA no site. | R03 |
| F1 | Apenas ferramentas gratuitas. | manual |
| G1 | Pronto para GitHub Pages via Actions (projetos web). | R30, R31 |
| P1 | PWA instalável: manifest, ícones 192/512, service worker, caminhos relativos. | R32, R33 |
| N1 | Navegação mobile organizada (barra inferior com no máximo 5 itens, rótulos em 1 linha) e desktop fluida. | R22 (aviso) |
| A1 | APK oficial só pelo GitHub Actions, na Release `latest`. Nunca commitar APK, keystore ou senha. | R04, R05, R24 |
| C1 | Nunca simular dados, progresso ou estados. | R07 (aviso) |
| C2 | Nunca remover funcionalidade existente sem ordem do dono; registrar novas em `rules/features.json`. | R06 |
| C3 | Nunca engolir erro: log + mensagem clara na tela. | manual |
| C4 | Edição cirúrgica: não reescrever arquivos inteiros. | manual |
| C5 | Fazer só o que o plano/pedido manda. | manual |
| C6 | Não inventar bibliotecas, versões ou APIs. | build |
| C7 | Não mudar Gradle/AGP/Kotlin, `applicationId` ou assinatura sem pedido. | R20, R21 |
| C8 | Nenhum segredo no repositório (ele é público). | R04 |
| C9 | `.gitignore` nunca ignora pasta de código. | R01 |

Ao final de toda tarefa: relatório de conformidade (regra, status, evidência) e lista "NÃO TESTADO".
