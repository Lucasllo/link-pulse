---
phase: 04-kubernetes-no-kind-e-entrega-cont-nua
plan: 02
subsystem: infra
tags: [kind, helm, kube-prometheus-stack, prometheus-operator, grafana, servicemonitor, prometheusrule, hpa, metrics-server]
status: complete

requires:
  - phase: 04-01
    provides: "make k8s com kind, Traefik, deps e chart link-pulse (Service link-pulse com portas http/management, hosts *.localtest.me, contexto kind-link-pulse)"
  - phase: 03
    provides: "observability/ como fonte única (regras groups:, dashboards JSON com UID prometheus, pasta Link Pulse/link-pulse, runbooks)"
provides:
  - "kube-prometheus-stack 92.10.1 enxuto no kind (release monitoring): seletores abertos, sem Alertmanager, Grafana 13.2.4 anônimo Viewer"
  - "Ingress grafana.localtest.me e prometheus.localtest.me (porta propagada ao root_url e ao externalUrl)"
  - "ServiceMonitor com jobLabel app.kubernetes.io/name (job link-pulse), PrometheusRule e ConfigMaps grafana_dashboard por --set-file a partir de observability/"
  - "metrics-server 3.14.0 + HPA autoscaling/v2 por CPU (1 a 3 no kind), Deployment sem spec.replicas com HPA"
  - "make k8s imprime App, Grafana, Prometheus e curl de exemplo"
affects: [04-03, 04-04, 05]

actuals:
  tokens: 4100
  tasks: 3
  commits: 3
plan_head_before: 27aa84ebf2924a1595ac0485960148d2b1fe3bc5

tech-stack:
  added: [kube-prometheus-stack 92.10.1 (Prometheus v3.15.0, Operator v0.94.1, grafana chart 14.0.1 / Grafana 13.2.4), metrics-server chart 3.14.0 (v0.9.0)]
  patterns:
    - "Fonte única por --set-file: o up.sh passa observability/prometheus/rules/link-pulse-alerts.yml e cada JSON de observability/grafana/dashboards como values; o chart nunca copia nem reprocessa o conteúdo"
    - "Templates de observabilidade falham fechado (required/fail) quando regras ou dashboards vêm vazios"
    - "CRDs de terceiros atrás de flag (metrics.serviceMonitor.enabled), não de .Capabilities, para o helm template do CI ser determinístico"
    - "Espera explícita pelo pod do Prometheus depois do --wait do Helm 4"

key-files:
  created:
    - deploy/kind/values/kube-prometheus-stack.yaml
    - deploy/kind/values/metrics-server.yaml
    - deploy/helm/link-pulse/templates/servicemonitor.yaml
    - deploy/helm/link-pulse/templates/prometheusrule.yaml
    - deploy/helm/link-pulse/templates/dashboards-configmap.yaml
    - deploy/helm/link-pulse/templates/hpa.yaml
  modified:
    - deploy/kind/up.sh
    - deploy/helm/link-pulse/values.yaml
    - deploy/helm/link-pulse/values-dev.yaml
    - deploy/helm/link-pulse/templates/deployment.yaml
    - observability/README.md
    - compose.yaml
    - Makefile

key-decisions:
  - "kube-prometheus-stack 92.10.1: chaves conferidas com helm show values iguais às da pesquisa; pod do Prometheus prometheus-monitoring-kube-prometheus-prometheus-0 (label app.kubernetes.io/name=prometheus), Deployment do Grafana monitoring-grafana"
  - "Modo enxuto: Alertmanager, defaultRules, nodeExporter, kubeStateMetrics e componentes do control plane desligados; ServiceMonitors de apiserver, kubelet e coredns do chart ficam (todos up no kind)"
  - "Contrato para 04-03 e 04-04: chaves observability.{enabled,rules,dashboards}, metrics.serviceMonitor.{enabled,interval}, autoscaling.{enabled,minReplicas,maxReplicas,targetCPUUtilizationPercentage}; hosts grafana.localtest.me e prometheus.localtest.me; release monitoring no namespace monitoring"
  - "O render do chart sem --set-file falha de propósito (observability.enabled=true por padrão); o helm template do CI precisa passar os --set-file ou observability.enabled=false"

patterns-established:
  - "Um ConfigMap por JSON de observability/grafana/dashboards, nome {fullname}-dashboard-{nome}, label grafana_dashboard: \"1\""
  - "Porta do kind propagada por --set-string grafana.grafana\\.ini.server.root_url e --set prometheus.prometheusSpec.externalUrl só quando não é 80"

requirements-completed: [K8S-01, K8S-03, K8S-04]

coverage:
  - id: D1
    description: "Prometheus do kind raspa o app pelo ServiceMonitor (min(up{job=\"link-pulse\"}) = 1), /api/v1/rules lista exatamente os 4 alertas com runbook_url da main, Grafana anônimo mostra os 2 dashboards na pasta link-pulse com {{method}} intacto, datasource UID prometheus, home no overview e escrita anônima recusada"
    requirement: K8S-04
    verification:
      - kind: e2e
        ref: "04-02-PLAN.md Task 1 <verify> #1 (make k8s + curl --resolve) -> K8S-OBS-TRACER-OK"
        status: pass
    human_judgment: false
  - id: D2
    description: "Com o Deployment em 0 réplicas o LinkPulseTargetDown fica firing pelo absent() (~95 s) e volta a inactive com o app de pé (~15 s)"
    requirement: K8S-04
    verification:
      - kind: e2e
        ref: "04-02-PLAN.md Task 1 <verify> #2 -> K8S-TARGETDOWN-FIRING-OK"
        status: pass
    human_judgment: false
  - id: D3
    description: "Fonte única: nenhum JSON nem groups: fora de observability/; o chart recusa renderizar sem regras ou dashboards e renderiza ServiceMonitor, PrometheusRule e ConfigMaps com {{method}} intacto"
    requirement: K8S-04
    verification:
      - kind: other
        ref: "04-02-PLAN.md Task 1 <verify> #3 -> OBS-SINGLE-SOURCE-OK"
        status: pass
    human_judgment: false
  - id: D4
    description: "metrics-server responde em metrics.k8s.io; HPA link-pulse autoscaling/v2 mostra utilização (14%), min/max 1/3, e o Deployment instalado não tem spec.replicas"
    requirement: K8S-01
    verification:
      - kind: e2e
        ref: "04-02-PLAN.md Task 2 <verify> -> HPA-OK"
        status: pass
    human_judgment: false
  - id: D5
    description: "make k8s imprime App, Grafana e Prometheus (com :porta fora da 80), curl de exemplo e o contexto; make help cita Prometheus/Grafana; compose no Grafana 13.2.4; observability/README com o mecanismo real"
    requirement: K8S-03
    verification:
      - kind: e2e
        ref: "04-02-PLAN.md Task 3 <verify> -> K8S-OUTPUT-DOCS-OK"
        status: pass
    human_judgment: false
  - id: D6
    description: "No navegador do host: shortUrl abre o destino, Grafana abre sem login no dashboard Link Pulse — Aplicação com dados, Prometheus /alerts lista os 4 alertas inativos"
    requirement: K8S-03
    verification: []
    human_judgment: true
    rationale: "Human-check da Task 3: clique na shortUrl no navegador do host (D-03) e leitura visual dos painéis com dados"

duration: 22min
completed: 2026-10-22
---

# Phase 4 Plan 02: Prometheus, Grafana e HPA no kind Summary

**O `make k8s` agora instala metrics-server e um kube-prometheus-stack 92.10.1 enxuto: o Prometheus raspa o app pelo job `link-pulse` (ServiceMonitor com `jobLabel`), carrega os 4 alertas de `observability/` num PrometheusRule e o Grafana 13.2.4 anônimo (Viewer) mostra os dois dashboards na pasta `link-pulse`, tudo por `--set-file` sem copiar arquivo; o app ganhou HPA por CPU (1 a 3 réplicas) e o script termina imprimindo as três URLs e o `curl` de exemplo.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-10-22T00:31:03Z
- **Completed:** 2026-10-22T00:53:30Z
- **Tasks:** 3
- **Files modified:** 13

## Accomplishments

- Primeiro `make k8s` com o kube-prometheus-stack (cluster já de pé, imagem rebuildada): 307 s. Com tudo instalado, `make k8s` levou 108 s (inclui o rebuild da imagem e o metrics-server novo).
- Prometheus em `http://prometheus.localtest.me`: `min(up{job="link-pulse"})` = 1 sem passo manual, todos os 11 targets `up` (app, Grafana, apiserver, kubelet, coredns, operator, Prometheus), e `/api/v1/rules` com exatamente os 4 alertas, cada um com o `runbook_url` da main.
- Grafana em `http://grafana.localtest.me`: anônimo abre a home em `/d/link-pulse-overview`, os dois dashboards ficam na pasta `Link Pulse` (UID `link-pulse`), as legendas `{{method}}` chegam intactas e o POST anônimo em `/api/folders` recebe 403.
- `LinkPulseTargetDown` disparou pelo `absent()` ~95 s depois de escalar o app para 0 e voltou a inactive ~15 s depois de o app subir.
- HPA `link-pulse` com `cpu: 14%/70%` (depois 5%), min 1 e max 3; o manifest instalado do Deployment não tem `spec.replicas`.
- Nó do kind com tudo no ar: 455m de CPU e ~3,9 GiB de memória (`kubectl top node`, 50% do nó); maiores consumidores: apiserver 643Mi, Grafana 415Mi, app 407Mi, Prometheus 240Mi.
- Criação de link pelo Ingress e `GET /{code}` com 302 continuam funcionando no estado final.

## Task Commits

1. **Task 1: Prometheus e Grafana do kind com scrape, alertas e dashboards de observability/** - `8e1bfae` (feat)
2. **Task 2: HPA por CPU com metrics-server no kind** - `f22fb5e` (feat)
3. **Task 3: make k8s imprime as URLs e observability/README descreve o kind** - `4689b64` (docs)

## Fatos registrados (pedidos pelo `<output>` do plano)

- **Chaves do kube-prometheus-stack 92.10.1 x pesquisa:** nenhuma diferença. Conferidas com `helm show values` e `helm pull` (digest `sha256:1b5c871f…b726`): `prometheus.prometheusSpec.{service,pod,rule}MonitorSelectorNilUsesHelmValues` (default `true`), `prometheus.ingress.{ingressClassName,hosts,paths,pathType}`, `grafana.sidecar.dashboards.{label,labelValue,searchNamespace}` (defaults `grafana_dashboard`, `"1"`, `ALL`), `grafana.sidecar.dashboards.provider.{folder,folderUid,disableDelete,allowUiUpdates}` no subchart grafana 14.0.1, `grafana.sidecar.datasources.uid: prometheus` e `grafana.sidecar.datasources.alertmanager.enabled` (default `true`). O `externalUrl` vem de `index .Values.prometheus.ingress.hosts 0` quando `prometheusSpec.externalUrl` está vazio. O subchart grafana é 14.0.1 com `appVersion: 13.2.4`.
- **Nomes reais (A1, A3):** pod do Prometheus `prometheus-monitoring-kube-prometheus-prometheus-0` (StatefulSet `prometheus-monitoring-kube-prometheus-prometheus`, label `app.kubernetes.io/name=prometheus`); Deployment do Grafana `monitoring-grafana`; Operator `monitoring-kube-prometheus-operator`. Na primeira instalação o pod do Prometheus já existia quando o `--wait` do Helm terminou, mas a espera explícita ficou no `up.sh` como proteção.
- **Tempo e memória:** primeiro `make k8s` com o kube-prometheus-stack 307 s; idempotente 108 s; nó em ~3,9 GiB.
- **Contrato da 04-03 e 04-04:** chaves `observability.{enabled,rules,dashboards}`, `metrics.serviceMonitor.{enabled,interval}`, `autoscaling.{enabled,minReplicas,maxReplicas,targetCPUUtilizationPercentage}`; hosts `grafana.localtest.me` e `prometheus.localtest.me`; release `monitoring` no namespace `monitoring`, release `metrics-server` no `kube-system`. O `helm template`/kubeconform da 04-03 precisa passar os mesmos `--set-file` do `up.sh` (ou `observability.enabled=false`), porque o chart falha de propósito sem eles.

## Files Created/Modified

- `deploy/kind/values/kube-prometheus-stack.yaml` - kube-prometheus-stack enxuto: seletores abertos, sem Alertmanager, Ingress de Grafana e Prometheus, Grafana anônimo Viewer, sidecar na pasta `Link Pulse`/`link-pulse`
- `deploy/kind/values/metrics-server.yaml` - `--kubelet-insecure-tls` só para o kind
- `deploy/helm/link-pulse/templates/servicemonitor.yaml` - ServiceMonitor na porta `management` com `jobLabel: app.kubernetes.io/name`
- `deploy/helm/link-pulse/templates/prometheusrule.yaml` - PrometheusRule a partir de `observability.rules` (`required`, `fromYaml`, `fail`)
- `deploy/helm/link-pulse/templates/dashboards-configmap.yaml` - um ConfigMap `grafana_dashboard: "1"` por dashboard
- `deploy/helm/link-pulse/templates/hpa.yaml` - HPA autoscaling/v2 por CPU com `behavior`
- `deploy/helm/link-pulse/templates/deployment.yaml` - `replicas` só sem HPA
- `deploy/helm/link-pulse/values.yaml` / `values-dev.yaml` - chaves `metrics.serviceMonitor.*`, `observability.*`, `autoscaling.*` (HPA ligado no kind)
- `deploy/kind/up.sh` - releases `metrics-server` e `monitoring`, espera do Prometheus/Grafana, array de `--set-file`, porta propagada, bloco final com as três URLs
- `observability/README.md` - coluna kind com o mecanismo real, por que `--set-file`, URLs do kind
- `compose.yaml` - `grafana/grafana:13.2.4`
- `Makefile` - descrição do `k8s` com metrics-server e Prometheus/Grafana

## Decisions Made

- Mantidos os ServiceMonitors padrão do chart para apiserver, kubelet e coredns (o plano não os desligava): no kind todos ficam `up`, então `/targets` continua todo verde e o custo de memória ficou baixo (Prometheus em 240Mi).
- A linha impressa usa `$CTX` (padrão `kind-link-pulse`) em vez do nome fixo, para continuar certa com `KIND_CLUSTER`; o literal `kind-link-pulse` fica no comentário da variável.
- A nota de DNS do fim do `make k8s` passou a dar o `curl --resolve` pronto (`link-pulse.localtest.me:{porta}:127.0.0.1`), além do arquivo hosts e do `curl -4`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Um `sed` de substituição no `up.sh` falhou no parse (barra invertida no padrão) antes de alterar o arquivo; a troca foi feita com a ferramenta de edição, sem efeito no resultado.
- Fora do escopo, registrado em `deferred-items.md`: o Id da imagem `link-pulse:local` muda a cada `make image` sem mudança de código (quatro `make k8s` geraram quatro tags `local-*` com o mesmo `CreatedAt`), então todo `make k8s` faz rollout do app. Comportamento herdado da 04-01.

## User Setup Required

None - nenhuma configuração externa além da já atendida na 04-01.

## Next Phase Readiness

- Pronto para a 04-03 (lint dos charts, kubeconform e CI): o render precisa dos `--set-file` de `observability/` ou de `observability.enabled=false`; o ServiceMonitor e o PrometheusRule exigem os schemas do Prometheus Operator no kubeconform.
- Pronto para a 04-04 (smoke): `up{job="link-pulse"}` e os hosts `grafana.`/`prometheus.localtest.me` respondem por `--resolve`.
- Human-check pendente para o fim da fase (D6): abrir a shortUrl, o Grafana e o `/alerts` no navegador.
- O cluster `kind-link-pulse` continua de pé com tudo instalado.

---
*Phase: 04-kubernetes-no-kind-e-entrega-cont-nua*
*Completed: 2026-10-22*

## Self-Check: PASSED

- 6 de 6 arquivos criados presentes (`[ -f ]`).
- Commits `8e1bfae`, `f22fb5e` e `4689b64` presentes no `git log`.
- Verifies: `K8S-OBS-TRACER-OK`, `K8S-TARGETDOWN-FIRING-OK`, `OBS-SINGLE-SOURCE-OK`, `HPA-OK` e `K8S-OUTPUT-DOCS-OK`; acceptance criteria das 3 tasks conferidos.
