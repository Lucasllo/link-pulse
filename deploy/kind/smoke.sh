#!/usr/bin/env bash
# make k8s-smoke: link -> 302 -> stats + observabilidade no cluster kind; o CI roda o mesmo script.
# Cria um link, confere o redirect sem segui-lo, espera o clique aparecer no /stats e confere o
# scrape do Prometheus, a regra de alerta e o dashboard do Grafana. Sai com "smoke OK: {code}".
# Sem make: bash deploy/kind/smoke.sh (o cluster vem do make k8s ou do bash deploy/kind/up.sh).
set -euo pipefail
# Git Bash: sem conversão de argumentos /caminho para os .exe (docker, curl).
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'

CLUSTER=${KIND_CLUSTER:-link-pulse}
APP_HOST=link-pulse.localtest.me
PROM_HOST=prometheus.localtest.me
GRAFANA_HOST=grafana.localtest.me

# A porta é a do próprio cluster (port mapping do nó), e não uma variável que pode estar errada.
MAPPED=$(docker port "$CLUSTER-control-plane" 30080/tcp 2>/dev/null | head -1 || true)
PORT=${MAPPED##*:}
if [ -z "$MAPPED" ] || [ -z "$PORT" ]; then
  echo "Cluster kind não encontrado ($CLUSTER-control-plane sem a porta 30080): rode make k8s" >&2
  exit 1
fi
SUFFIX=
[ "$PORT" = 80 ] || SUFFIX=":$PORT"
APP="http://$APP_HOST$SUFFIX"
PROM="http://$PROM_HOST$SUFFIX"
GRAFANA="http://$GRAFANA_HOST$SUFFIX"

# --resolve: não depende do DNS público, de proteção a DNS rebinding nem do ::1 (o kind só escuta
# em 127.0.0.1). --max-time em todo curl: nada fica pendurado.
CURL=(curl -sS --max-time 10
  --resolve "$APP_HOST:$PORT:127.0.0.1"
  --resolve "$PROM_HOST:$PORT:127.0.0.1"
  --resolve "$GRAFANA_HOST:$PORT:127.0.0.1")
# D-11: User-Agent de navegador, o mesmo de load/lib/common.js (BROWSER_UAS[0], Chrome no Windows).
# O UA do curl conta como bot e o clique sumiria do /stats padrão.
UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'
NL=$'\n'

fail() {
  echo "smoke FALHOU: $*" >&2
  exit 1
}

# Faz a requisição e deixa o corpo em BODY e o status HTTP em STATUS (000 se não conectou). Tudo
# pelo stdout, sem arquivo temporário: no Git Bash o curl é um .exe nativo e, sem a conversão de
# caminhos, um /tmp/... do bash seria outro diretório para ele.
http() {
  local resp
  resp=$("${CURL[@]}" -w '\n%{http_code}' "$@" 2>/dev/null || true)
  STATUS=${resp##*"$NL"}
  BODY=
  [ "$resp" = "$STATUS" ] || BODY=${resp%"$NL"*}
}

echo "Smoke no kind $CLUSTER ($APP)"

# 1. Criar um link. Uma criação por execução: o limite é de 20 por minuto por IP (D-06), e todo
# tráfego do host chega ao app com o mesmo IP de origem.
TARGET="https://example.com/smoke-$(date +%s)"
echo "1/5 POST /links -> $TARGET"
http -A "$UA" -H 'Content-Type: application/json' -d "{\"url\":\"$TARGET\"}" "$APP/links"
[ "$STATUS" = 201 ] || fail "POST /links respondeu $STATUS: $BODY"
code=$(printf '%s' "$BODY" | sed -n 's/.*"code":"\([^"]*\)".*/\1/p')
[ -n "$code" ] || fail "POST /links sem code: $BODY"
echo "    code=$code"

# 2. Redirect sem segui-lo (D-11): seguir o 302 testaria o site de destino, não a API. O curl não
# segue por padrão; o redirect_url do -w é o Location da resposta.
echo "2/5 GET /$code (sem seguir o redirect)"
out=$("${CURL[@]}" -A "$UA" -w '\n%{http_code} %{redirect_url}' "$APP/$code" 2>/dev/null || true)
out=${out##*"$NL"}
status=${out%% *}
location=${out#* }
[ "$status" = 302 ] && [ "$location" = "$TARGET" ] \
  || fail "redirect esperado 302 -> $TARGET, recebido: ${out:-nada}"
echo "    302 -> $location"

# 3. O clique passa pelo pipeline assíncrono (stream -> consumer -> Mongo): até 45 x 2 s.
echo "3/5 GET /links/$code/stats até total >= 1 (até 90 s)"
total=
for _ in $(seq 1 45); do
  http -A "$UA" "$APP/links/$code/stats"
  if [ "$STATUS" = 200 ]; then
    total=$(printf '%s' "$BODY" | sed -n 's/.*"total":\([0-9]*\).*/\1/p')
    [ "${total:-0}" -ge 1 ] && break
  fi
  sleep 2
done
[ "${total:-0}" -ge 1 ] || fail "clique não apareceu no /stats em 90 s (último status $STATUS: $BODY)"
echo "    total=$total"

# 4. Prometheus: o app raspado pelo ServiceMonitor (job link-pulse) e a regra de alerta carregada.
echo "4/5 Prometheus: min(up{job=\"link-pulse\"}) = 1 e regra LinkPulseTargetDown (até 120 s)"
up=
for _ in $(seq 1 60); do
  http -G --data-urlencode 'query=min(up{job="link-pulse"})' "$PROM/api/v1/query"
  if [ "$STATUS" = 200 ] && printf '%s' "$BODY" | grep -Eq '"value":\[[0-9.]+,"1"\]'; then
    up=1
    break
  fi
  sleep 2
done
[ -n "$up" ] || fail "min(up{job=\"link-pulse\"}) não chegou a 1 em 120 s (status $STATUS: $BODY)"
http "$PROM/api/v1/rules?type=alert"
[ "$STATUS" = 200 ] && printf '%s' "$BODY" | grep -q '"name":"LinkPulseTargetDown"' \
  || fail "regra LinkPulseTargetDown ausente em /api/v1/rules (status $STATUS)"
echo "    up=1, LinkPulseTargetDown carregada"

# 5. Grafana anônimo: o dashboard principal na pasta link-pulse.
echo "5/5 Grafana: dashboard link-pulse-overview na pasta link-pulse (até 120 s)"
dash=
for _ in $(seq 1 60); do
  http "$GRAFANA/api/dashboards/uid/link-pulse-overview"
  if [ "$STATUS" = 200 ] && printf '%s' "$BODY" | grep -q '"folderUid":"link-pulse"'; then
    dash=1
    break
  fi
  sleep 2
done
[ -n "$dash" ] || fail "dashboard link-pulse-overview não apareceu no Grafana em 120 s (último status $STATUS)"
echo "    link-pulse-overview ok"

echo "smoke OK: $code"
