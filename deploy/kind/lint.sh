#!/usr/bin/env bash
# make k8s-lint: helm lint --strict e kubeconform -strict dos charts (values dev e prod e o chart
# deps), guardas fail-closed do chart e paridade das tags de imagem. O CI roda o mesmo script.
# Não precisa de cluster: o kubeconform roda em container (nada instalado no host).
set -euo pipefail
# Git Bash: sem conversão de argumentos /caminho para os .exe (docker, helm).
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
# Raiz do repositório (aguenta espaço no caminho).
cd "$(dirname "$0")/../.."

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Falta '$1' no PATH. Windows: winget install $2 (e abra um terminal novo)." >&2
    exit 1
  }
}
need helm Helm.Helm
need docker Docker.DockerDesktop

C=deploy/helm/link-pulse
DEPS=deploy/helm/deps
KUBECONFORM=(docker run --rm -i ghcr.io/yannh/kubeconform:v0.8.0 -strict -summary
  -kubernetes-version 1.37.1 -schema-location default)
# ServiceMonitor e PrometheusRule (CRDs do Prometheus Operator) pelo catálogo da comunidade.
CRDS=(-schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json')

fail() {
  echo "$*" >&2
  exit 1
}

# ---------- scripts sem CR ----------
echo "==> scripts do kind sem CR"
for f in deploy/kind/*.sh; do
  if grep -q $'\r' "$f"; then
    fail "$f tem CR (CRLF): o bash do Linux/CI quebra. Converta para LF."
  fi
done

# ---------- values do deploy ----------
# Mesma montagem do up.sh: alertas e dashboards de observability/, a fonte única, por --set-file.
OBS=(--set-file observability.rules=observability/prometheus/rules/link-pulse-alerts.yml)
DASH=()
for f in observability/grafana/dashboards/*.json; do
  DASH+=(--set-file "observability.dashboards.$(basename "$f" .json)=$f")
done
SETS=("${OBS[@]}" "${DASH[@]}" --set image.tag=lint)

# ---------- helm lint ----------
echo "==> helm lint --strict (link-pulse dev, link-pulse prod, deps)"
helm lint --strict "$C" -f "$C/values-dev.yaml" "${SETS[@]}"
helm lint --strict "$C" -f "$C/values-prod.yaml" "${SETS[@]}"
helm lint --strict "$DEPS"

# ---------- kubeconform ----------
for profile in dev prod; do
  echo "==> kubeconform link-pulse ($profile, Kubernetes 1.37.1 + CRDs do Prometheus Operator)"
  helm template link-pulse "$C" -n link-pulse -f "$C/values-$profile.yaml" "${SETS[@]}" \
    | "${KUBECONFORM[@]}" "${CRDS[@]}"
done
echo "==> kubeconform deps (Kubernetes 1.37.1)"
helm template deps "$DEPS" -n link-pulse | "${KUBECONFORM[@]}"

# ---------- guardas fail-closed ----------
# Cada caso TEM de falhar no render, e pela mensagem da própria guarda (um erro qualquer, como YAML
# quebrado, não conta): se renderizar, a proteção do chart sumiu.
echo "==> guardas fail-closed do chart"
guard() {
  local caso=$1 esperado=$2 out
  shift 2
  if out=$(helm template link-pulse "$C" -n link-pulse "$@" 2>&1); then
    fail "guarda falhou: $caso (o render deveria ter falhado)"
  fi
  if ! printf '%s\n' "$out" | grep -qF "$esperado"; then
    printf '%s\n' "$out" >&2
    fail "guarda falhou: $caso (falhou, mas sem a mensagem esperada: $esperado)"
  fi
  echo "    recusado: $caso"
}
guard "sem image.tag" "image.tag é obrigatória" \
  -f "$C/values-dev.yaml" "${OBS[@]}" "${DASH[@]}"
guard "LINKPULSE_IP_HASH_KEY com menos de 16 caracteres" "pelo menos 16 caracteres" \
  -f "$C/values-dev.yaml" "${SETS[@]}" --set secret.data.LINKPULSE_IP_HASH_KEY=curta
guard "secret.create=false sem existingSecret" "secret.existingSecret é obrigatório" \
  -f "$C/values-dev.yaml" "${SETS[@]}" --set secret.create=false
guard "sem as regras de observability/" "observability.rules vazio" \
  -f "$C/values-dev.yaml" "${DASH[@]}" --set image.tag=lint

# ---------- paridade das imagens ----------
# O Dependabot atualiza o compose; o chart deps e os ITs não podem ficar para trás. A regex exige
# X.Y ou X.Y.Z, então não casa mongo:27017/linkpulse nem postgres:5432/linkpulse.
echo "==> paridade das tags de imagem (compose, compose.dev, chart deps, Testcontainers)"
PARITY_FILES=(compose.yaml compose.dev.yaml "$DEPS/values.yaml"
  app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java)
for img in postgres valkey/valkey mongo; do
  re="${img}:[0-9]+\.[0-9]+(\.[0-9]+)?"
  all=
  report=
  for f in "${PARITY_FILES[@]}"; do
    tags=$(grep -ohE "(^|[^A-Za-z0-9/_.-])$re" "$f" | grep -oE "$re" | sort -u | tr '\n' ' ' | sed 's/ $//')
    report="$report $f=[${tags:-nenhuma}]"
    all="$all $tags"
  done
  distinct=$(printf '%s\n' $all | sort -u | grep -c . || true)
  missing=0
  for f in "${PARITY_FILES[@]}"; do
    grep -qE "(^|[^A-Za-z0-9/_.-])$re" "$f" || missing=1
  done
  if [ "$distinct" != 1 ] || [ "$missing" = 1 ]; then
    fail "tags divergentes para $img:$report"
  fi
  echo "    $img: $(printf '%s\n' $all | sort -u)"
done

echo "lint OK"
