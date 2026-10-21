# Phase 4: Kubernetes no kind e entrega contínua - Pattern Map

**Mapped:** 2026-10-20
**Files analyzed:** 30 (aprox.; contando templates do chart)
**Analogs found:** 9 / 30 (a maioria é infraestrutura nova sem precedente no repo; usar os Patterns 1-12 do 04-RESEARCH.md)

Todos os analogs abaixo são arquivos rastreados pelo git (`git ls-files`). Não existe nenhum arquivo Kubernetes, Helm ou script bash no repositório ainda.

## File Classification

| Arquivo novo/modificado | Role | Data Flow | Analog mais próximo | Qualidade |
|---|---|---|---|---|
| `deploy/kind/kind-config.yaml` | config | - | nenhum (RESEARCH Pattern 1) | sem analog |
| `deploy/kind/up.sh` | utility (script) | batch | `Makefile` (NOCONV, `image`) | parcial |
| `deploy/kind/smoke.sh` | test (smoke) | request-response | `load/lib/common.js` (UA de navegador) | parcial |
| `deploy/kind/lint.sh` | utility | batch | `Makefile` alvo `obs-check` | parcial |
| `deploy/kind/values/traefik.yaml` | config | - | nenhum (Pattern 2) | sem analog |
| `deploy/kind/values/metrics-server.yaml` | config | - | nenhum (Pattern 3) | sem analog |
| `deploy/kind/values/kube-prometheus-stack.yaml` | config | - | `compose.yaml` serviços `prometheus`/`grafana` + `observability/grafana/provisioning/datasources/prometheus.yml` | role-match |
| `deploy/helm/deps/**` (Chart, values, postgres/valkey/mongo/secret) | config (manifests) | CRUD (stateful) | `compose.yaml` linhas 12-50 | role-match |
| `deploy/helm/link-pulse/values*.yaml`, `templates/deployment.yaml`, `configmap.yaml`, `secret.yaml` | config | request-response | `compose.yaml` linhas 52-80 | role-match |
| `deploy/helm/link-pulse/templates/{service,ingress,hpa,servicemonitor,_helpers.tpl,NOTES.txt}` | config | - | nenhum (Pattern 6) | sem analog |
| `deploy/helm/link-pulse/templates/prometheusrule.yaml` | config | - | `observability/prometheus/rules/link-pulse-alerts.yml` (fonte via `.Files.Get`) | fonte |
| `deploy/helm/link-pulse/templates/dashboards-configmap.yaml` | config | - | `observability/grafana/dashboards/*.json` | fonte |
| `app/src/main/resources/application.yml` (modificado: health groups) | config | - | o próprio arquivo, bloco `management` (~118-125) | exato |
| `.github/workflows/ci.yml` (modificado: jobs image/GHCR/Trivy/SBOM, kind smoke, lint) | config (CI) | batch | o próprio `ci.yml` job `build-test` | exato |
| `Makefile` (modificado: `k8s`, `k8s-down`, `k8s-smoke`, `k8s-lint`) | config | batch | o próprio `Makefile` | exato |
| `.gitignore` (modificado: `.kind/`) | config | - | o próprio | exato |
| `observability/README.md` (modificado: tabela "kind (Phase 4)") | docs | - | o próprio | exato |
| IT opcional de health groups (`app/src/test/java/dev/linkpulse/...HealthGroupsIT.java`) | test | request-response | `app/src/test/java/dev/linkpulse/MetricsCardinalityIT.java` / `AbstractIT.java` | role-match |

## Pattern Assignments

### `Makefile` (novos alvos k8s)

**Analog:** `Makefile` (ele mesmo)

Convenções a copiar:
- Toda linha de alvo tem `## comentário em pt-BR` para o `help` (linha 20-21). Atenção: o `printf "  %-10s"` corta em 10 colunas; `k8s-smoke` cabe, nomes maiores desalinham.
- Atualizar o `.PHONY` (linha 18).
- Comentário "Sem make: ..." equivalente acima do bloco (linhas 3-7).
- NOCONV só no docker/kind/helm, nunca exportado globalmente (linhas 8-11):
```make
# Git Bash: NOCONV impede a conversão de argumentos /caminho quando o sh chama o docker.exe.
# Vale só para o docker: exportada para o make inteiro, quebra o classpath do ./mvnw.
NOCONV := MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
STACK := $(NOCONV) docker compose
```
- Reaproveitar o alvo `image` (linhas 50-51) como pré-requisito de `k8s`:
```make
image: ## Builda só a imagem local do app (link-pulse:local)
	$(STACK) build app
```
- Delegar a lógica para `deploy/kind/up.sh` (RESEARCH Pattern 9), passando `K8S_HTTP_PORT ?= 80` (padrão `?=` igual a `K6_USER`, linha 15).

### `deploy/kind/up.sh`, `smoke.sh`, `lint.sh` (scripts bash)

**Analog parcial:** `Makefile` + RESEARCH Patterns 8, 9, 10, 12.
- Cabeçalho `#!/usr/bin/env bash` + `set -euo pipefail`, comentários em pt-BR, `export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'` dentro do script (seguro porque não chama `./mvnw`).
- Arquivos `.sh` precisam ser LF e executáveis: espelhar a checagem do CI `test -x mvnw && ! grep -q $'\r' mvnw` (`ci.yml` linha 39) e conferir `.gitattributes`.
- `lint.sh` segue a ideia do `obs-check` (linhas 67-69): ferramenta em container/binário fixado, sem instalar nada novo no host quando possível.

### `deploy/kind/smoke.sh` (smoke link → 302 → stats)

**Analog:** `load/lib/common.js` linhas 12-30 — reusar o mesmo UA de navegador (D-11):
```js
// UA padrão do k6 ... contém "k6/", que o app classifica como bot (decisão 02-05)
'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36',
```
Usar `curl --resolve link-pulse.localtest.me:${PORT}:127.0.0.1`, sem seguir redirect (`-o /dev/null -w '%{http_code}'`, esperar 302), e ficar abaixo de 20 criações/min por IP (D-06).

### `deploy/helm/deps/templates/{postgres,valkey,mongo}.yaml`

**Analog:** `compose.yaml` linhas 12-50. Copiar imagem, args e probes 1:1:
```yaml
  postgres:
    image: postgres:18.6
    environment: { POSTGRES_DB: linkpulse, POSTGRES_USER: linkpulse, POSTGRES_PASSWORD: ... }
    volumes: [ pgdata:/var/lib/postgresql ]      # monta o pai (PGDATA do 18)
    healthcheck: pg_isready -h 127.0.0.1 -U linkpulse -d linkpulse
  valkey:
    image: valkey/valkey:8.1
    command: ["valkey-server", "--maxmemory", "256mb", "--maxmemory-policy", "volatile-lru"]
    healthcheck: valkey-cli ping
  mongo:
    image: mongo:8.0
    command: ["mongod", "--wiredTigerCacheSizeGB", "0.25"]
    volumes: [ mongodata:/data/db ]
    healthcheck: mongosh --quiet --eval "db.adminCommand('ping')"
```
Healthchecks viram `readinessProbe.exec`; volumes viram `volumeClaimTemplates` (mountPath `/var/lib/postgresql`). Comentários "Não é segredo: default de demonstração." no Secret (compose linha 17).

### `deploy/helm/link-pulse/values*.yaml`, `templates/deployment.yaml`, `configmap.yaml`, `secret.yaml`

**Analog:** `compose.yaml` linhas 52-80 (env do app):
```yaml
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/linkpulse
      SPRING_DATASOURCE_USERNAME: linkpulse
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-linkpulse}
      SPRING_DATA_REDIS_HOST: valkey
      SPRING_DATA_REDIS_PORT: "6379"
      SPRING_MONGODB_URI: mongodb://mongo:27017/linkpulse
      LINKPULSE_IP_HASH_KEY: ${LINKPULSE_IP_HASH_KEY:-compose-demo-ip-hash-key-not-a-secret}
      LINKPULSE_BASE_URL: http://localhost:8080
      LINKPULSE_SELF_HOSTS: app
    stop_grace_period: 45s
```
Mapeamento:
- ConfigMap: profile, URLs, `LINKPULSE_BASE_URL=http://link-pulse.localtest.me` (D-03), `LINKPULSE_SELF_HOSTS` (nome do Service + host do Ingress), `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES=10.244.0.0/16` no values dev (D-06).
- Secret: `SPRING_DATASOURCE_PASSWORD`, `LINKPULSE_IP_HASH_KEY` (>= 16 chars), com `existingSecret` em prod.
- `stop_grace_period: 45s` vira `terminationGracePeriodSeconds: 45` (shutdown 30s por fase, `application.yml` linha ~44).
- Imagem `link-pulse:local` com `imagePullPolicy: IfNotPresent` (comentário do compose linha 54: tag fixa, nunca latest).
- Portas: 8080 http, 8081 management; probes `/actuator/health/liveness` e `/readiness` na 8081.

### `deploy/kind/values/kube-prometheus-stack.yaml`

**Analog:** `compose.yaml` linhas 83-133 + `observability/grafana/provisioning/datasources/prometheus.yml`:
```yaml
      GF_AUTH_ANONYMOUS_ENABLED: "true"
      GF_AUTH_ANONYMOUS_ORG_ROLE: Viewer
      GF_ANALYTICS_REPORTING_ENABLED: "false"
      GF_USERS_HOME_PAGE: /d/link-pulse-overview
```
```yaml
    uid: prometheus          # mesmo UID do datasource padrão do kube-prometheus-stack
    jsonData:
      timeInterval: 15s
```
Senha admin no values comentada "não é segredo" (D-04). Retenção enxuta (compose usa 7d). Seletores `*SelectorNilUsesHelmValues: false` e sem Alertmanager (RESEARCH Pattern 4).

### `templates/prometheusrule.yaml` e `templates/dashboards-configmap.yaml`

**Fontes (não copiar, ler via `.Files.Get`):** `observability/prometheus/rules/link-pulse-alerts.yml`, `observability/grafana/dashboards/link-pulse-overview.json`, `link-pulse-pipeline.json`. Como o Helm só lê arquivos dentro do chart, o planner deve decidir entre symlink/cópia no `up.sh`, ou `kubectl create configmap --from-file` (RESEARCH Pattern 6 trata disso). JSON nunca passa por `tpl` (os `{{ }}` do Grafana quebrariam). O ServiceMonitor precisa gerar `job="link-pulse"` (D-09) — conferir `observability/prometheus/prometheus.yml` para o job_name atual.

### `app/src/main/resources/application.yml` (health groups)

**Analog:** o próprio bloco `management` (linhas ~118-125). Inserir a seção `management.endpoint.health.group` do RESEARCH Pattern 7 (liveness: `livenessState`; readiness: `readinessState,db,redis`). Seguir o estilo do arquivo: comentário em pt-BR explicando o porquê acima de cada chave (ver linhas 52-62 sobre `remoteip`). Não mexer no `internal-proxies: ""`.

### `.github/workflows/ci.yml` (novos jobs)

**Analog:** job `build-test` (linhas 16-58). Copiar:
```yaml
permissions:
  contents: read            # topo; cada job novo amplia só o que precisa (packages: write, security-events: write, id-token/attestations se usar attestation)
...
    defaults:
      run:
        working-directory: "link pulse/app"   # checkout com espaço
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          path: "link pulse"
...
      - name: Relatórios em caso de falha
        if: failure()
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
```
- Toda action nova fixada por SHA com `# vX.Y.Z` (D-12), `timeout-minutes` explícito, nomes de step em pt-BR.
- Jobs de publicação com `if: github.ref == 'refs/heads/main' && github.event_name == 'push'` e `needs: build-test`.
- Nome da imagem em minúsculas (`ghcr.io/${{ github.repository_owner }}` precisa de lowercase). Detalhes: RESEARCH Pattern 11.
- Manter `concurrency` existente (linhas 12-14).

### IT opcional de health groups

**Analog:** `app/src/test/java/dev/linkpulse/MetricsCardinalityIT.java` (estende `AbstractIT`, consulta a porta de management). Ler na hora do plano se for incluído.

## Shared Patterns

### Comentários e segredos de demo
**Fonte:** `compose.yaml` linhas 3-6, 17, 64. Todo default de senha/chave leva `# Não é segredo: default de demonstração.` Aplica a: values dev, Secret do deps e do app, values do kube-prometheus-stack.

### Paridade de versões com o compose
**Fonte:** `compose.yaml`. `postgres:18.6`, `valkey/valkey:8.1`, `mongo:8.0`, `link-pulse:local`. Comentário do compose linha 10-11 pede manter tags iguais entre compose files; estender a regra ao chart `deps` (adicionar nota nos dois lugares).

### Git Bash / Windows
**Fonte:** `Makefile` linhas 8-11 e `ci.yml` linha 22. NOCONV só para docker/kind/kubectl/helm; caminho com espaço em todo script (sempre aspas em `"$ROOT"`).

### Actions por SHA e permissões mínimas
**Fonte:** `ci.yml` linhas 8-10, 26, 31, 50.

## No Analog Found

| Arquivo | Role | Motivo |
|---|---|---|
| `deploy/kind/kind-config.yaml` | config | Nenhum manifesto kind no repo; usar RESEARCH Pattern 1 |
| `deploy/kind/values/traefik.yaml`, `metrics-server.yaml` | config | Sem Helm no repo; Patterns 2 e 3 |
| `deploy/helm/link-pulse/Chart.yaml`, `_helpers.tpl`, `service.yaml`, `ingress.yaml`, `hpa.yaml`, `servicemonitor.yaml`, `NOTES.txt` | config | Primeiro chart Helm do projeto; Pattern 6 |
| `deploy/helm/deps/Chart.yaml` | config | Pattern 5 |

## Metadata

**Escopo da busca:** raiz do repo (`git ls-files`), `Makefile`, `compose.yaml`, `.github/workflows/`, `observability/`, `load/`, `app/src/main/resources/`
**Arquivos lidos:** 7
**Data:** 2026-10-20
