---
phase: 04-kubernetes-no-kind-e-entrega-cont-nua
plan: 03
subsystem: infra
tags: [kind, helm, kubeconform, smoke-test, values-prod, traefik, readme]
status: complete

requires:
  - phase: 04-02
    provides: "kube-prometheus-stack no kind, Ingress grafana/prometheus.localtest.me, ServiceMonitor (job link-pulse), PrometheusRule e dashboards por --set-file"
  - phase: 04-01
    provides: "make k8s (up.sh), charts link-pulse e deps, contexto kind-link-pulse, porta por K8S_HTTP_PORT"
provides:
  - "deploy/kind/smoke.sh (make k8s-smoke): POST /links -> 302 sem seguir -> clique no /stats em até 90 s -> up{job=link-pulse}=1, regra LinkPulseTargetDown, dashboard link-pulse-overview; sai com 'smoke OK: {code}'"
  - "deploy/kind/lint.sh (make k8s-lint): helm lint --strict + kubeconform v0.8.0 -strict (K8s 1.37.1 + CRDs-catalog) de dev, prod e deps, guardas fail-closed e paridade de tags; sai com 'lint OK'"
  - "values-prod.yaml de referência (GHCR, HPA 2-6, ECS, existingSecret, TLS, spread por zona), só renderizado"
  - "Chaves de chart ingress.tls e topologySpreadConstraints (labelSelector injetado pelo template)"
  - "Seção 'Kubernetes local (make k8s)' no README"
affects: [04-04, 05]

actuals:
  tokens: 6950
  tasks: 3
  commits: 3
plan_head_before: 8ee6ab0cff26a4a8e8bc0aa029252ca892756c07

tech-stack:
  added: [kubeconform v0.8.0 (ghcr.io/yannh/kubeconform, em container), datreeio/CRDs-catalog (schemas de ServiceMonitor/PrometheusRule)]
  patterns:
    - "Scripts de verificação em bash + curl + sed, sem jq nem ferramenta nova no host; kubeconform em container"
    - "Guardas fail-closed testadas a cada lint e conferidas pela mensagem da própria guarda (erro qualquer não conta)"
    - "Paridade de tags de imagem entre compose, compose.dev, chart deps e Testcontainers checada no lint"
    - "Lista de values com labelSelector injetado pelo template (omit + selectorLabels)"

key-files:
  created:
    - deploy/kind/smoke.sh
    - deploy/kind/lint.sh
    - deploy/helm/link-pulse/values-prod.yaml
  modified:
    - deploy/kind/up.sh
    - Makefile
    - deploy/helm/link-pulse/values.yaml
    - deploy/helm/link-pulse/templates/deployment.yaml
    - deploy/helm/link-pulse/templates/ingress.yaml
    - README.md

key-decisions:
  - "preStop.sleep nativo passou no kubeconform -strict do 1.37.1: o fallback exec sleep 10 não foi necessário"
  - "Guardas do lint.sh exigem a mensagem esperada no erro do render (image.tag é obrigatória, pelo menos 16 caracteres, secret.existingSecret é obrigatório, observability.rules vazio), e não só exit != 0"
  - "Interface do CI (04-04): bash deploy/kind/lint.sh, bash deploy/kind/up.sh e bash deploy/kind/smoke.sh, sem make; os três leem KIND_CLUSTER (default link-pulse)"
  - "values-prod com nomes genéricos (link-pulse-db, link-pulse-cache, link-pulse-clicks), domínio lp.example.com e SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES 10.0.0.0/16 como exemplo comentado"

patterns-established:
  - "Saída verificável de script: última linha fixa ('smoke OK: {code}', 'lint OK') e falha com o que foi recebido"
  - "Cada execução do smoke cria exatamente um link (abaixo das 20/min por IP)"

requirements-completed: [K8S-01, K8S-03, CI-04]

duration: ~41min (Tasks 2 e 3 nesta sessão; Task 1 numa sessão anterior)
completed: 2026-10-24
---

# Phase 4 Plan 03: smoke, lint e documentação do kind Summary

**`make k8s-smoke` prova link -> 302 -> clique no /stats e a observabilidade no kind, `make k8s-lint` valida dev e prod com kubeconform 1.37.1 + CRDs, guardas fail-closed e paridade de imagens, e o README ensina o `make k8s` do zero, provado em duas portas.**

## Performance

- **Duração:** ~41 min nesta sessão (12:56 a 13:37 UTC), mais a Task 1 em sessão anterior
- **Iniciado:** 2026-10-24T12:56:12Z (retomada na Task 2)
- **Concluído:** 2026-10-24T13:37:41Z
- **Tasks:** 3 (Task 1 já estava commitada; Tasks 2 e 3 nesta execução)
- **Arquivos:** 9 (3 criados, 6 modificados)

### Tempos medidos

| Etapa | Tempo |
|-------|-------|
| `make k8s-down` (cluster antigo) | 12 s |
| `make k8s K8S_HTTP_PORT=8080` do zero (cluster novo, nó sem cache de imagens; imagem do app já no cache do BuildKit) | 752 s (~12,5 min) |
| `make k8s-smoke` na 8080 | 8 s |
| `make k8s-down` | 6 s |
| `make k8s` do zero na 80 | 667 s (~11 min) |
| `make k8s-smoke` na 80 | 16 s |
| `make k8s-lint` (inclui pull do kubeconform na primeira vez) | 82 s na primeira, ~60 s depois |

## Accomplishments

- **Task 1 (commit `3faa12c`, sessão anterior):** `deploy/kind/smoke.sh` com `--resolve` por host, UA do Chrome 141 (D-11), um único POST, 302 conferido pelo `redirect_url`, polling de `/stats`, `min(up{job="link-pulse"})`, regra `LinkPulseTargetDown` e dashboard `link-pulse-overview` no Grafana anônimo; alvo `make k8s-smoke` e sugestão no fim do `up.sh`. Reverificado nesta sessão: critérios estáticos (modo 100755, sem CR, strings exigidas) e o `<verify>` completo imprimiu `SMOKE-OK` (verde, vermelho com o app em 0 réplicas com "POST /links respondeu 503: no available server", verde de novo após o rollout).
- **Task 2:** `values-prod.yaml` sem nenhum segredo (`secret.create: false` + `existingSecret: link-pulse-secrets`), com GHCR, HPA 2-6, resources 500m/768Mi -> 1536Mi, `LOGGING_STRUCTURED_FORMAT_CONSOLE: ecs`, TLS `link-pulse-tls` e spread por zona; templates com `spec.tls` e `topologySpreadConstraints` (labelSelector injetado); `deploy/kind/lint.sh` + `make k8s-lint`. Kubeconform: dev 10/10, prod 9/9, deps 7/7 válidos, `Invalid: 0, Errors: 0, Skipped: 0`. O render de prod não tem `kind: Secret`. Paridade testada no negativo (trocar `mongo:8.0` por `mongo:8.2` no chart deps fez o lint sair com "tags divergentes para mongo: ...").
- **Task 3:** README com o escopo da Phase 4, pré-requisito kind/Helm (`winget install Kubernetes.kind Helm.Helm`, 6 GB no WSL2), quatro linhas na tabela de comandos e a seção "Kubernetes local (make k8s)" (ordem do up.sh com versões, contexto `kind-link-pulse`, URLs, `localtest.me`/`curl -4`/hosts, porta 80 ocupada com `netstat -ano`, probes e a troca consciente do Valkey -> `NotReady` -> 503, rate limit atrás do Traefik com `10.244.0.0/16`, charts e values, lint, sem `make seed` no kind, limpeza). Sequência do zero: `K8S-CLEAN-SEQUENCE-OK`, com `shortUrl` `http://link-pulse.localtest.me:8080/EdRbklq` na 8080; o segundo verify imprimiu `README-K8S-OK`.

## Task Commits

1. **Task 1: make k8s-smoke** - `3faa12c` (feat) — sessão anterior, não refeita
2. **Task 2: values-prod e make k8s-lint** - `a5cabf9` (feat)
3. **Task 3: README do make k8s** - `58390c9` (docs)

## Files Created/Modified

- `deploy/kind/smoke.sh` - smoke link -> 302 -> stats + Prometheus/Grafana (Task 1)
- `deploy/kind/lint.sh` - helm lint, kubeconform, guardas fail-closed e paridade de tags
- `deploy/helm/link-pulse/values-prod.yaml` - perfil de produção de referência, só renderizado
- `deploy/helm/link-pulse/values.yaml` - `ingress.tls: []` e `topologySpreadConstraints: []`
- `deploy/helm/link-pulse/templates/ingress.yaml` - `spec.tls` a partir de `ingress.tls`
- `deploy/helm/link-pulse/templates/deployment.yaml` - `topologySpreadConstraints` com labelSelector injetado
- `deploy/kind/up.sh` - sugere o `make k8s-smoke` (Task 1)
- `Makefile` - alvos `k8s-smoke` (Task 1) e `k8s-lint`, `.PHONY` e comentário "sem make"
- `README.md` - seção "Kubernetes local (make k8s)", tabela e pré-requisitos

## Decisions Made

- `preStop.sleep` nativo aceito pelo kubeconform `-strict` com os schemas do 1.37.1; o fallback `exec sleep 10` não foi aplicado.
- O kubeconform roda uma vez por perfil (dev, prod, deps) para cada resumo aparecer separado no log do CI.
- Interface do CI da 04-04: `bash deploy/kind/lint.sh`, `bash deploy/kind/up.sh` e `bash deploy/kind/smoke.sh`, sem make.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Correção] Guardas do lint conferem a mensagem do erro, não só o exit code**
- **Encontrado durante:** Task 2
- **Problema:** como escrita no plano, uma guarda passaria se o render falhasse por qualquer motivo (YAML quebrado, value com erro de digitação), escondendo a perda da proteção real.
- **Correção:** `guard` recebe a mensagem esperada (`image.tag é obrigatória`, `pelo menos 16 caracteres`, `secret.existingSecret é obrigatório`, `observability.rules vazio`) e falha com "guarda falhou: ... (falhou, mas sem a mensagem esperada ...)" se ela não aparecer.
- **Arquivos:** deploy/kind/lint.sh
- **Commit:** a5cabf9

**2. [Rule 3 - Bloqueio] Docker Desktop estava parado no início da retomada**
- **Encontrado durante:** precondição da Task 2 (`docker info`)
- **Problema:** a precondição da Task 2 (e da Task 3) exige o daemon; a distro `docker-desktop` do WSL estava `Stopped`.
- **Correção:** `docker desktop start` (o app que o projeto já usa); o daemon 29.8.0 respondeu e o cluster kind antigo voltou. Nenhum arquivo do repositório envolvido.

**Total de desvios:** 2 (1 correção de robustez, 1 ambiente). **Impacto:** nenhum escopo novo.

## Issues Encountered

- Nenhum problema no código. Observação: o código do smoke saiu igual (`cJinsNv`) nos dois clusters novos, porque o banco é recriado e a sequência do Postgres recomeça; é esperado (Base62 de `nextval`).

## User Setup Required

None - nenhuma configuração externa.

## Next Phase Readiness

- A 04-04 (CI) tem o que executar: `bash deploy/kind/lint.sh` (sem cluster), `bash deploy/kind/up.sh` (com a imagem vinda do artifact) e `bash deploy/kind/smoke.sh`.
- O cluster `kind-link-pulse` ficou de pé na porta 80 depois da sequência.

## Self-Check: PASSED

- FOUND: deploy/kind/smoke.sh, deploy/kind/lint.sh, deploy/helm/link-pulse/values-prod.yaml, README.md
- FOUND commits: 3faa12c, a5cabf9, 58390c9
- Verifies executados: SMOKE-OK, LINT-PROD-OK, K8S-CLEAN-SEQUENCE-OK, README-K8S-OK
