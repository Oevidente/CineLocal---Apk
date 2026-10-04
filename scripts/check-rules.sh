#!/usr/bin/env bash
# scripts/check-rules.sh — verificador das regras do projeto (roda no CI e localmente).
# Falha (exit 1) quando uma regra "ERRO" é violada. Avisos não falham o build.
# Uso local:  bash scripts/check-rules.sh
# No CI:      BEFORE_SHA=<commit anterior> bash scripts/check-rules.sh
set -u
cd "$(dirname "$0")/.." || exit 2

FAIL=0
err()  { echo "::error title=$1::$2"; FAIL=1; }
warn() { echo "::warning title=$1::$2"; }
pass() { echo "ok   $1 - $2"; }

HAS_GIT=0
if [ -d .git ] && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  HAS_GIT=1
fi

# ---------- perfil do projeto ----------
# rules/profile pode conter: android | web | "android web". Sem o arquivo, detecta sozinho.
if [ -f rules/profile ]; then PROFILE=" $(tr '\n' ' ' < rules/profile) "
elif [ -f app/build.gradle.kts ]; then PROFILE=" android "
elif [ -f package.json ] || [ -f index.html ]; then PROFILE=" web "
else PROFILE=" "; fi
is() { case "$PROFILE" in *" $1 "*) return 0;; *) return 1;; esac; }

# ---------- base de comparação (commit anterior) ----------
BASE="${BEFORE_SHA:-}"
if [ "$HAS_GIT" = "1" ]; then
  if [ -z "$BASE" ] || [ "$BASE" = "0000000000000000000000000000000000000000" ] || ! git cat-file -e "$BASE^{commit}" 2>/dev/null; then
    BASE=$(git rev-parse --verify -q HEAD~1 || true)
  fi
fi

version_at() { # $1=rev  -> imprime a versão do projeto naquele commit
  if [ "$HAS_GIT" = "1" ]; then
    if is android; then
      git show "$1:app/build.gradle.kts" 2>/dev/null | grep -oP 'versionName\s*=\s*"\K[^"]+' | head -1
    else
      git show "$1:package.json" 2>/dev/null | grep -oP '"version"\s*:\s*"\K[^"]+' | head -1
    fi
  else
    if is android; then
      grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1
    else
      grep -oP '"version"\s*:\s*"\K[^"]+' package.json | head -1
    fi
  fi
}

# ============ R01 .gitignore não pode ignorar pastas de código ============
BAD=$(grep -nE '^[[:space:]]*/?(data|src|lib|app|main|java|kotlin|res|assets|public|components|utils|player|cast|ui)/?[[:space:]]*$' .gitignore 2>/dev/null)
if [ -n "$BAD" ]; then err R01 ".gitignore ignora pasta de código-fonte: $(echo "$BAD" | tr '\n' ' ')"; else pass R01 ".gitignore ok"; fi

# ============ R02 versão aumentou quando houve mudança de código ============
if [ "$HAS_GIT" = "1" ] && [ -n "$BASE" ]; then
  CHANGED=$(git diff --name-only "$BASE" HEAD 2>/dev/null \
    | grep -E '^(app/src/|src/|public/)|^index\.html$|\.(kt|kts|js|jsx|ts|tsx|css|html)$' \
    | grep -vE '^(docs/|scripts/|rules/|\.github/|tests?/)|\.md$')
  if [ -n "$CHANGED" ]; then
    OLD=$(version_at "$BASE"); NEW=$(version_at HEAD)
    if [ -z "$NEW" ]; then err R02 "Não encontrei a versão do projeto (versionName em app/build.gradle.kts ou \"version\" em package.json)."
    elif [ -z "$OLD" ]; then pass R02 "sem versão anterior para comparar"
    elif [ "$OLD" = "$NEW" ]; then err R02 "Código mudou mas a versão continua $NEW. Aumente a versão."
    elif [ "$(printf '%s\n%s\n' "$OLD" "$NEW" | sort -V | tail -1)" != "$NEW" ]; then err R02 "Versão diminuiu: $OLD -> $NEW."
    else pass R02 "versão $OLD -> $NEW"; fi
  else pass R02 "sem mudança de código"; fi
else
  pass R02 "versão atualizada para: $(version_at HEAD) (sem ambiente git para comparar)"
fi

# ============ R03 sem IA como ferramenta / sem resíduo de template ============
AI_RE='generativelanguage\.googleapis\.com|api\.openai\.com|api\.anthropic\.com|@google/genai|@google/generative-ai|firebase/ai|GoogleGenAI|GEMINI_API_KEY|OPENAI_API_KEY|ANTHROPIC_API_KEY'
if [ "$HAS_GIT" = "1" ]; then
  HITS=$(git grep -nE "$AI_RE" -- . ':!scripts' ':!rules' ':!docs' ':!*.md' ':!package-lock.json' ':!.aistudio' 2>/dev/null | head -5)
else
  HITS=$(grep -rnE "$AI_RE" . --exclude-dir=scripts --exclude-dir=rules --exclude-dir=docs --exclude-dir=.aistudio --exclude=*.md --exclude=package-lock.json 2>/dev/null | head -5)
fi

if [ -n "$HITS" ]; then
  if is web; then err R03 "Uso de IA no projeto (proibido): $(echo "$HITS" | tr '\n' ' ')"; else warn R03 "Referência a IA: $(echo "$HITS" | tr '\n' ' ')"; fi
else pass R03 "sem IA"; fi

# ============ R04 segredos e binários sensíveis fora do repositório ============
if [ "$HAS_GIT" = "1" ]; then
  FILES=$(git ls-files | grep -E '\.(jks|keystore|pem|p12)$|(^|/)\.env(\.|$)' | grep -vE '\.env\.example$|debug\.keystore$')
else
  FILES=$(find . -type f \( -name "*.jks" -o -name "*.keystore" -o -name "*.pem" -o -name "*.p12" -o -name ".env" \) 2>/dev/null | grep -vE '\.env\.example$|debug\.keystore$|debug\.keystore\.base64$')
fi
[ -n "$FILES" ] && err R04 "Arquivo sensível detectado: $(echo "$FILES" | tr '\n' ' ')"

if [ "$HAS_GIT" = "1" ]; then
  KEYS=$(git grep -nE "sk-[A-Za-z0-9]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|(secret|password|passwd|token|api[_-]?key)[\"']?[[:space:]]*[:=][[:space:]]*[\"'][A-Za-z0-9_/+=.-]{16,}[\"']" -- . ':!scripts' ':!rules' ':!docs' ':!*.md' ':!package-lock.json' ':!.aistudio' ':!.github' ':!**/test/**' ':!**/androidTest/**' ':!**/tests/**' 2>/dev/null | head -3)
else
  KEYS=$(grep -rnE "sk-[A-Za-z0-9]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----" . --exclude-dir=scripts --exclude-dir=rules --exclude-dir=docs --exclude-dir=.aistudio --exclude-dir=.github --exclude=*.md --exclude=package-lock.json 2>/dev/null | head -3)
fi
[ -n "$KEYS" ] && err R04 "Possível segredo no código: $(echo "$KEYS" | cut -c1-120 | tr '\n' ' ')"

if [ "$HAS_GIT" = "1" ]; then
  AIZA=$(git grep -nE 'AIza[0-9A-Za-z_-]{35}' -- . ':!scripts' ':!rules' ':!docs' ':!*.md' ':!.aistudio' 2>/dev/null | head -2)
else
  AIZA=$(grep -rnE 'AIza[0-9A-Za-z_-]{35}' . --exclude-dir=scripts --exclude-dir=rules --exclude-dir=docs --exclude-dir=.aistudio --exclude=*.md --exclude=package-lock.json 2>/dev/null | head -2)
fi
[ -n "$AIZA" ] && warn R04 "Chave de API do Google no código: $(echo "$AIZA" | cut -c1-100 | tr '\n' ' ')"
[ -z "$FILES$KEYS" ] && pass R04 "sem segredos óbvios"

# ============ R05 APK nunca é commitado à mão ============
if [ "$HAS_GIT" = "1" ]; then
  APKS=$(git ls-files | grep -E '\.(apk|aab)$')
else
  APKS=$(find . -name "*.apk" -o -name "*.aab" 2>/dev/null | grep -vE '^./app/build/' | grep -vE '^./apk/')
fi
if [ -n "$APKS" ]; then warn R05 "APK versionado no repositório: $APKS"; else pass R05 "sem APK no repo"; fi

# ============ R06 funcionalidades essenciais não podem sumir ============
if [ -f rules/features.json ]; then
  OUT=$(python3 - <<'PYEOF'
import json, os
feats = json.load(open("rules/features.json", encoding="utf-8"))
bad = 0
for f in feats:
    path = f["file"]
    if not os.path.isfile(path):
        print(f"::error title=R06::[{f['id']}] arquivo sumiu: {path} ({f['desc']})"); bad += 1
    elif f["pattern"] not in open(path, encoding="utf-8", errors="ignore").read():
        print(f"::error title=R06::[{f['id']}] funcionalidade removida: {f['desc']} (esperava '{f['pattern']}' em {path})"); bad += 1
print(f"__R06_RESULT__ {len(feats)} {bad}")
PYEOF
)
  echo "$OUT" | grep -v '^__R06_RESULT__'
  read -r _ TOTAL BADN <<< "$(echo "$OUT" | grep '^__R06_RESULT__')"
  if [ "${BADN:-1}" != "0" ]; then FAIL=1; else pass R06 "$TOTAL funcionalidades presentes"; fi
else warn R06 "rules/features.json não existe"; fi

# ============ R07 sinais de dado simulado ============
if [ "$HAS_GIT" = "1" ]; then
  SIM=$(git grep -nEi 'simula[cç][aã]o|simulado|fake data|dados fict|mock data' -- '*.kt' '*.js' '*.jsx' '*.ts' '*.tsx' ':!*Test*' ':!tests' ':!scripts' 2>/dev/null | head -3)
else
  SIM=$(grep -rnEi 'simula[cç][aã]o|simulado|fake data|dados fict|mock data' . --include='*.kt' --include='*.js' --include='*.jsx' --include='*.ts' --include='*.tsx' --exclude-dir=*Test* --exclude-dir=tests --exclude-dir=scripts 2>/dev/null | head -3)
fi
[ -n "$SIM" ] && warn R07 "Possível dado simulado (revise): $(echo "$SIM" | cut -c1-110 | tr '\n' ' ')"

# ============ ANDROID ============
if is android; then
  # R20 applicationId não muda (mudar quebra a atualização por cima)
  AID_NEW=$(grep -oP 'applicationId\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1)
  if [ "$HAS_GIT" = "1" ] && [ -n "$BASE" ]; then
    AID_OLD=$(git show "$BASE:app/build.gradle.kts" 2>/dev/null | grep -oP 'applicationId\s*=\s*"\K[^"]+' | head -1)
    if [ -n "$AID_OLD" ] && [ "$AID_OLD" != "$AID_NEW" ]; then err R20 "applicationId mudou ($AID_OLD -> $AID_NEW). Isso impede atualizar o app por cima."; else pass R20 "applicationId estável"; fi
  else
    pass R20 "applicationId verificado ($AID_NEW)"
  fi
  # R21 troca de Gradle/AGP/Kotlin só com motivo (aviso)
  if [ "$HAS_GIT" = "1" ] && [ -n "$BASE" ] && ! git diff --quiet "$BASE" HEAD -- gradle/wrapper/gradle-wrapper.properties gradle/libs.versions.toml 2>/dev/null; then
    warn R21 "Versões de Gradle/AGP/Kotlin/bibliotecas mudaram. Confirme que era necessário e que o build passou."
  fi
  # R22 barra de navegação com no máximo 5 abas (aviso)
  TABS=$(awk '/enum class AppTab/{f=1;next} f&&/^}/{exit} f&&/Icons\./{c++} END{print c+0}' app/src/main/java/com/example/cinelocal/MainActivity.kt 2>/dev/null)
  [ "${TABS:-0}" -gt 5 ] && warn R22 "Barra inferior com $TABS abas (recomendado: no máximo 5)."
  # R23 package.json (se existir) acompanha a versão do app
  if [ -f package.json ]; then
    PV=$(grep -oP '"version"\s*:\s*"\K[^"]+' package.json | head -1); AV=$(version_at HEAD)
    [ -n "$PV" ] && [ -n "$AV" ] && [ "$PV" != "$AV" ] && warn R23 "package.json diz $PV mas o app é $AV. Mantenha igual ou remova o campo."
  fi
  # R24 workflow precisa publicar a Release
  if [ "$HAS_GIT" = "1" ]; then
    WFW=$(git grep -qE 'gh release create|action-gh-release' -- .github/workflows 2>/dev/null && echo "1" || echo "0")
  else
    WFW=$(grep -qE 'gh release create|action-gh-release' .github/workflows/*.yml 2>/dev/null && echo "1" || echo "0")
  fi
  if [ "$WFW" = "1" ]; then pass R24 "workflow publica Release"; else err R24 "O workflow não publica a Release 'latest' (APK)."; fi
fi

# ============ WEB (GitHub Pages + PWA) ============
if is web; then
  # R30 deploy no Pages via Actions
  if [ "$HAS_GIT" = "1" ]; then
    PAGES=$(git grep -q 'actions/deploy-pages' -- .github/workflows 2>/dev/null && echo "1" || echo "0")
  else
    PAGES=$(grep -q 'actions/deploy-pages' .github/workflows/*.yml 2>/dev/null && echo "1" || echo "0")
  fi
  if [ "$PAGES" = "1" ]; then pass R30 "deploy Pages por Actions"; else err R30 "Falta workflow de deploy para o GitHub Pages (actions/deploy-pages)."; fi

  # R31 caminho base (Pages serve em /nome-do-repo/)
  if ls vite.config.* >/dev/null 2>&1; then
    grep -qE 'base\s*:' vite.config.* || warn R31 "vite.config sem 'base': os arquivos podem dar 404 no GitHub Pages (/repo/)."
  fi
  # R32 PWA: manifest
  MAN=$(ls public/manifest.webmanifest public/manifest.json manifest.webmanifest manifest.json 2>/dev/null | head -1)
  if [ -n "$MAN" ]; then
    OUT=$(python3 - "$MAN" <<'PYEOF'
import json, os, sys
p = sys.argv[1]; m = json.load(open(p, encoding="utf-8")); d = os.path.dirname(p)
errs = []
for k in ("name", "short_name", "start_url"):
    if not m.get(k): errs.append(f"campo ausente: {k}")
if m.get("display") not in ("standalone", "fullscreen", "minimal-ui"): errs.append("display deve ser standalone")
sizes = [i.get("sizes", "") for i in m.get("icons", [])]
for need in ("192x192", "512x512"):
    if not any(need in s for s in sizes): errs.append(f"falta ícone {need}")
if str(m.get("start_url", "")).startswith("/"): errs.append("start_url absoluto quebra no GitHub Pages; use './'")
for i in m.get("icons", []):
    src = i.get("src", "").lstrip("./")
    if not any(os.path.isfile(os.path.join(b, src)) for b in (d, "public", ".")): errs.append(f"ícone inexistente: {i.get('src')}")
print("; ".join(errs))
PYEOF
)
    [ -z "$OUT" ] && pass R32 "manifest ok ($MAN)" || err R32 "Manifest PWA ($MAN): $OUT"
  elif ! grep -q 'vite-plugin-pwa' package.json 2>/dev/null; then err R32 "Sem manifest PWA (e sem vite-plugin-pwa)."; fi
  # R33 PWA: service worker registrado
  if [ "$HAS_GIT" = "1" ]; then
    SWR=$(git grep -qE 'serviceWorker\.register|registerSW|vite-plugin-pwa' -- . ':!scripts' ':!rules' ':!*.md' ':!package-lock.json' 2>/dev/null && echo "1" || echo "0")
  else
    SWR=$(grep -rnE 'serviceWorker\.register|registerSW|vite-plugin-pwa' . --exclude-dir=scripts --exclude-dir=rules --exclude=*.md --exclude=package-lock.json 2>/dev/null && echo "1" || echo "0")
  fi
  if [ "$SWR" = "1" ]; then pass R33 "service worker registrado"; else err R33 "Service worker não registrado (PWA não instala/offline)."; fi

  # R34 uso de banco: listeners em tempo real sem limite (aviso)
  if [ "$HAS_GIT" = "1" ]; then
    RT=$(git grep -nE 'onSnapshot\(' -- '*.js' '*.jsx' '*.ts' '*.tsx' 2>/dev/null | head -3)
  else
    RT=$(grep -rnE 'onSnapshot\(' . --include='*.js' --include='*.jsx' --include='*.ts' --include='*.tsx' 2>/dev/null | head -3)
  fi
  [ -n "$RT" ] && warn R34 "Listener em tempo real (consome cota do BD). Garanta limit() e unsubscribe: $(echo "$RT" | cut -c1-100 | tr '\n' ' ')"
fi

echo "-----"
if [ "$FAIL" = 0 ]; then
  echo "TODAS AS REGRAS OBRIGATÓRIAS PASSARAM"
else
  echo "HÁ REGRAS OBRIGATÓRIAS VIOLADAS"
fi
exit $FAIL
