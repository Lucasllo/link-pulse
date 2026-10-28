#!/usr/bin/env bash
# make k8s: cria (ou reaproveita) o cluster kind e instala Traefik, as dependências e o app pelo
# Helm. Roda no Git Bash (Windows) e no Linux/CI. Sem make: make image e depois
# bash deploy/kind/up.sh (porta: K8S_HTTP_PORT=8080 bash deploy/kind/up.sh).
set -euo pipefail
# Git Bash: sem conversão de argumentos /caminho para os .exe (docker, kind, kubectl, helm). Seguro
# aqui: este script não chama o ./mvnw (exportada para o mvnw, quebraria o classpath).
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
# Raiz do repositório (aguenta espaço no caminho).
cd "$(dirname "$0")/../.."

CLUSTER=${KIND_CLUSTER:-link-pulse}
CTX="kind-$CLUSTER"  # padrão: kind-link-pulse
HOST=link-pulse.localtest.me
CHARTS=deploy/helm  # charts do repositório, instalados só por h() (--kube-context fixo)
TRAEFIK_CHART=oci://ghcr.io/traefik/helm/traefik  # chart OCI oficial, só via h() (--kube-context)

# Toda chamada ao cluster leva o contexto do kind: nunca age no cluster que estiver no contexto
# atual do kubeconfig do avaliador (Pitfall 4). Função, e não variável com o comando, porque uma
# variável não se divide em palavras do mesmo jeito em todo shell.
k() { kubectl --context "$CTX" "$@"; }
h() { helm --kube-context "$CTX" "$@"; }

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Falta '$1' no PATH. Windows: winget install $2 (e abra um terminal novo)." >&2
    exit 1
  }
}
need docker Docker.DockerDesktop
need kind Kubernetes.kind
need kubectl Kubernetes.kubectl
need helm Helm.Helm
need curl cURL.cURL

docker info >/dev/null 2>&1 || {
  echo "O Docker não respondeu. Ligue o Docker Desktop e rode de novo." >&2
  exit 1
}
HV=$(h version --template '{{.Version}}')
case "$HV" in
  v4*) ;;
  *)
    echo "Precisa do Helm v4 (encontrado: $HV). Windows: winget install Helm.Helm" >&2
    exit 1
    ;;
esac

# ---------- cluster ----------
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  # Cluster de pé: a porta é a do port mapping que ele já tem.
  MAPPED=$(docker port "$CLUSTER-control-plane" 30080/tcp | head -1)
  PORT=${MAPPED##*:}
  if [ -n "${K8S_HTTP_PORT:-}" ] && [ "$K8S_HTTP_PORT" != "$PORT" ]; then
    echo "Aviso: o cluster $CLUSTER já escuta em 127.0.0.1:$PORT; K8S_HTTP_PORT=$K8S_HTTP_PORT foi ignorada." >&2
    echo "Trocar a porta exige recriar o cluster: make k8s-down e make k8s K8S_HTTP_PORT=$K8S_HTTP_PORT" >&2
  fi
  echo "Cluster kind $CLUSTER já existe (127.0.0.1:$PORT); reaproveitando."
else
  PORT=${K8S_HTTP_PORT:-80}
  echo "Criando o cluster kind $CLUSTER (127.0.0.1:$PORT)..."
  if ! sed "s/hostPort: 80\$/hostPort: $PORT/" deploy/kind/kind-config.yaml \
      | kind create cluster --name "$CLUSTER" --config=- --wait 120s; then
    echo "Falha ao criar o cluster. Se a porta $PORT estiver ocupada (IIS, http.sys):" >&2
    echo "  netstat -ano | findstr :$PORT" >&2
    echo "  make k8s-down && make k8s K8S_HTTP_PORT=8080" >&2
    exit 1
  fi
fi

BASE="http://$HOST"
[ "$PORT" = 80 ] || BASE="$BASE:$PORT"

# ---------- imagem ----------
# Tag única por build (local-{12 hex do Id}): com pullPolicy Never e uma tag fixa, um rebuild não
# mudaria o Deployment e o pod seguiria com a imagem antiga.
IMG_ID=$(docker image inspect --format '{{.Id}}' link-pulse:local | sed 's/^sha256://' | cut -c1-12)
TAG="local-$IMG_ID"
docker tag link-pulse:local "link-pulse:$TAG"
# save de uma plataforma só + image-archive: o carregamento direto do daemon quebra no containerd
# image store do Docker 29 (kind#3795, "content digest not found").
PLATFORM=$(docker version --format '{{.Server.Os}}/{{.Server.Arch}}')
mkdir -p .kind
docker image save --platform "$PLATFORM" -o .kind/link-pulse.tar "link-pulse:$TAG" \
  || docker image save -o .kind/link-pulse.tar "link-pulse:$TAG"
kind load image-archive .kind/link-pulse.tar --name "$CLUSTER"

# ---------- metrics-server (base do HPA) ----------
h upgrade --install metrics-server metrics-server --repo https://kubernetes-sigs.github.io/metrics-server/ --version 3.14.0 \
  -n kube-system -f deploy/kind/values/metrics-server.yaml --wait --timeout 5m
k wait --for=condition=Available apiservice/v1beta1.metrics.k8s.io --timeout=180s

# ---------- Ingress controller ----------
h upgrade --install traefik "$TRAEFIK_CHART" --version 42.1.0 \
  -n traefik --create-namespace -f deploy/kind/values/traefik.yaml --wait --timeout 5m

# ---------- Prometheus e Grafana (kube-prometheus-stack) ----------
# Antes do app: o chart do app cria ServiceMonitor e PrometheusRule, que exigem os CRDs do Operator.
# Com outra porta, links gerados pelo Grafana (root_url) e pelo Prometheus (externalUrl) levam :porta.
KPS_SET=()
if [ "$PORT" != 80 ]; then
  KPS_SET+=(--set-string "grafana.grafana\.ini.server.root_url=http://grafana.localtest.me:$PORT")
  KPS_SET+=(--set "prometheus.prometheusSpec.externalUrl=http://prometheus.localtest.me:$PORT")
fi
h upgrade --install monitoring oci://ghcr.io/prometheus-community/charts/kube-prometheus-stack --version 92.10.1 \
  -n monitoring --create-namespace -f deploy/kind/values/kube-prometheus-stack.yaml \
  ${KPS_SET[@]+"${KPS_SET[@]}"} --wait --timeout 15m
# O --wait do Helm 4 pode terminar antes de o Operator criar o StatefulSet do Prometheus (Pitfall
# 11): espera o pod existir e ficar Ready.
echo "Esperando o Prometheus..."
for _ in $(seq 1 100); do
  [ -n "$(k -n monitoring get pod -l app.kubernetes.io/name=prometheus -o name 2>/dev/null)" ] && break
  sleep 3
done
k -n monitoring wait --for=condition=Ready pod -l app.kubernetes.io/name=prometheus --timeout=10m
k -n monitoring rollout status deploy/monitoring-grafana --timeout=10m

# ---------- dependências (Postgres, Valkey, Mongo) ----------
h upgrade --install deps "$CHARTS/deps" -n link-pulse --create-namespace --wait --timeout 5m

# ---------- app ----------
APP_SET=(--set image.tag="$TAG")
[ "$PORT" = 80 ] || APP_SET+=(--set "env.LINKPULSE_BASE_URL=$BASE")
# Alertas e dashboards direto de observability/, a fonte única (D-09): o Helm não lê fora do chart,
# então o conteúdo vai como value. Dashboard novo nessa pasta entra no próximo make k8s.
APP_SET+=(--set-file observability.rules=observability/prometheus/rules/link-pulse-alerts.yml)
for f in observability/grafana/dashboards/*.json; do
  APP_SET+=(--set-file "observability.dashboards.$(basename "$f" .json)=$f")
done
h upgrade --install link-pulse "$CHARTS/link-pulse" -n link-pulse \
  -f "$CHARTS/link-pulse/values-dev.yaml" "${APP_SET[@]}" --wait --timeout 10m

# O --wait do Helm termina com o pod Ready, mas o Traefik só põe o pod no load balancer da rota
# depois do evento do EndpointSlice e da recarga seguinte (throttle de 2 s). Nessa janela a rota
# responde 503 "no available server" (allowEmptyServices do chart), e um curl ou o smoke logo em
# seguida falha. Espera a rota do Ingress chegar ao app: só o app responde JSON (inclusive o 404 de
# código inexistente); o Traefik responde text/plain. GET no /stats não gasta o rate limit do
# POST /links. Corpo e Content-Type pelo stdout, como no smoke.sh: no Git Bash o curl é um .exe
# nativo e não enxerga o /dev/null do bash.
echo "Esperando a rota do Ingress chegar ao app..."
NL=$'\n'
deadline=$((SECONDS + 60))
while :; do
  resp=$(curl -s --max-time 2 --resolve "$HOST:$PORT:127.0.0.1" -w '\n%{content_type}' \
    "$BASE/links/up-probe/stats" 2>/dev/null || true)
  case "${resp##*"$NL"}" in *json*) break ;; esac
  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "A rota $BASE não chegou ao app em 60 s (o Traefik ainda responde no lugar dele)." >&2
    echo "  kubectl --context $CTX -n link-pulse get ingress,endpointslices" >&2
    echo "  kubectl --context $CTX -n traefik logs deploy/traefik --tail=50" >&2
    exit 1
  fi
  sleep 1
done

SUFFIX=
[ "$PORT" = 80 ] || SUFFIX=":$PORT"

cat <<EOF

Link Pulse no kind (contexto $CTX, imagem link-pulse:$TAG)

  App:        $BASE  (Swagger: $BASE/swagger-ui.html)
  Grafana:    http://grafana.localtest.me$SUFFIX  (anônimo, só leitura; admin/admin não é segredo)
  Prometheus: http://prometheus.localtest.me$SUFFIX  (alertas em /alerts)

Criar um link:
  curl -s -X POST $BASE/links -H 'Content-Type: application/json' -d '{"url":"https://example.com"}'
Seguir o link (troque {code} pelo campo code da resposta):
  curl -i $BASE/{code}

localtest.me resolve para 127.0.0.1 por DNS público; se a sua rede bloquear (proteção a DNS
rebinding), use curl --resolve $HOST:$PORT:127.0.0.1 ou o arquivo hosts. Se o IPv6 (::1)
atrasar, use curl -4.
Smoke: make k8s-smoke (sem make: bash deploy/kind/smoke.sh)
Apagar o cluster (dados incluídos): make k8s-down
EOF
