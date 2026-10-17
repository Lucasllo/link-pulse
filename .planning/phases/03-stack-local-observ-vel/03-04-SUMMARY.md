---
phase: 03-stack-local-observ-vel
plan: 04
subsystem: testing
tags: [k6, load-test, docker-compose, makefile, thresholds, rate-limit]

requires:
  - phase: 03-stack-local-observ-vel
    provides: "03-01 make up (compose.yaml, link-pulse:local); 03-02 serviço k6, load/lib/common.js (createLink com retry no 429, BROWSER_UA) e dashboards; 03-03 alertas e runbooks"
provides:
  - "load/load.js: cenários warmup/redirect/create com os thresholds de D-10 por tag scenario"
  - "make load (k6 2.3.0 em container) com --summary-export em load/results/load-summary.json"
  - "load/results/load-summary.json versionado, gerado pela sequência limpa make clean → up → seed → load"
  - "K6_USER exportado pelo Makefile e user do serviço k6 no compose.yaml"
  - "README: linha make load e seção Teste de carga (make load)"
affects: [04-kind-e-ci, 05-terraform-e-readme]

plan_head_before: 0bad505b5c27fe9d6d2245e1cac4685c9abb34c6
actuals:
  tokens: 4400
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "k6: maxRedirects 0 global + redirects 0 por requisição + check status === 302"
    - "Thresholds por tag de sistema scenario; warmup separado fora dos thresholds"
    - "Resumo nativo + --summary-export (sem handleSummary, sem jslib remota)"
    - "Orçamento do rate limit: create a 12/min com startTime 60s, setup com 5 POSTs e retry por Retry-After"

key-files:
  created:
    - load/load.js
    - load/results/.gitkeep
    - load/results/load-summary.json
  modified:
    - Makefile
    - compose.yaml
    - README.md

key-decisions:
  - "03-04: A4 e A5 confirmados no k6 2.3.0: --summary-export grava o JSON relativo ao working_dir e a tag scenario separa os submétricos; contingência handleSummary não foi necessária"
  - "03-04: K6_USER com o uid:gid do Git Bash (197609:197121) funciona no Docker Desktop; no Linux é o que dá permissão de escrita em load/results"
  - "03-04: perfil de D-10 passa com folga num i7-4790/16 GB (Docker Desktop 29.8.0, 8 CPUs e 7,7 GB para a VM): redirect p95 3,18 ms / p99 9,29 ms, create p95 39,31 ms, 0% de erro, 0 dropped_iterations"

patterns-established:
  - "Teste de carga em container pelo serviço k6 (profile tools), sempre com caminho relativo ao working_dir /work"
  - "Evidência de desempenho versionada como saída crua do make load, nunca editada à mão"

requirements-completed: [LOAD-01]

coverage:
  - id: D1
    description: "make load roda o k6 em container, mede o redirect sem seguir o 302 e grava load/results/load-summary.json com p(99) por cenário"
    requirement: LOAD-01
    verification:
      - kind: e2e
        ref: "Task 1 <verify>: docker compose up -d --wait && make load && grep http_req_duration{scenario:redirect} e \"p(99)\" no JSON → LOAD-TRACER-OK"
        status: pass
    human_judgment: false
  - id: D2
    description: "Perfil completo de D-10 passa nos thresholds na sequência limpa make clean → make up → make seed → make load, sem 429 e com o pipeline drenado"
    requirement: LOAD-01
    verification:
      - kind: e2e
        ref: "Task 2 <verify> 1: make clean && make up && make seed && make load + pending=0, persisted/published>=0.99, rejected=0, quatro submétricos → PHASE-E2E-OK"
        status: pass
    human_judgment: false
  - id: D3
    description: "README documenta make load, perfil, thresholds, rate limit, resumo versionado e K6_USER"
    requirement: LOAD-01
    verification:
      - kind: other
        ref: "Task 2 <verify> 2 (greps em load.js/README.md + make help) → LOAD-DOCS-OK"
        status: pass
    human_judgment: false
  - id: D4
    description: "Checagem humana de fim de fase: dashboards com dados (requisições/s, p95/p99, hit ratio, cliques/min; pipeline com pendentes de volta a 0) e as quatro regras com runbook em /alerts"
    verification:
      - kind: other
        ref: "Proxy automatizado com a stack de pé: /api/v1/rules lista 4 regras com runbook_url; Grafana anônimo 200 nos dois dashboards; hit 26836 x miss 25; pending 0"
        status: pass
    human_judgment: true
    rationale: "Leitura visual dos painéis no navegador; a stack foi derrubada (make down, volumes mantidos) a pedido do orquestrador, então o verificador precisa de make up e, se quiser dados frescos, make load"

duration: 11min
completed: 2026-10-17
status: complete
---

# Phase 3 Plan 04: Teste de carga k6 (make load) Summary

**`make load` roda o k6 2.3.0 em container com warmup, redirect a 200 req/s e criação a 12/min. Ele nunca segue o 302 e passa nos thresholds de D-10 com folga: redirect p95 de 3,18 ms e p99 de 9,29 ms, criação p95 de 39,31 ms, 0% de erro. A sequência limpa `make clean` → `up` → `seed` → `load` roda sem nenhum 429, e o resumo JSON fica versionado.**

## Performance

- **Duration:** cerca de 11 min
- **Started:** 2026-10-17T13:59:27Z
- **Completed:** 2026-10-17T14:10:30Z
- **Tasks:** 2
- **Files modified:** 6

## Resultado da execução limpa (para o README da Phase 5)

Máquina: Intel Core i7-4790 @ 3,60 GHz, 16 GB de RAM, Windows 11 Pro, Docker Desktop (engine 29.8.0, VM com 8 CPUs e 7,7 GB). Sequência: `make clean` às 14:04:31Z, `make up` (com build) até 14:05:07Z, `make seed` até 14:06:02Z e `make load` até 14:08:36Z.

| Métrica | Valor |
|---------|-------|
| Duração do `make load` | cerca de 2 min 34 s (setup + 2 min 30 s de cenários) |
| `http_reqs` total | 25.526 (169,99/s na média da execução inteira) |
| Redirect (`scenario:redirect`) | 24.001 requisições (200/s em 2 min); p95 3,18 ms, p99 9,29 ms, máx. 56,8 ms; 0% de falha |
| Criação (`scenario:create`) | 19 requisições; p95 39,31 ms, p99 55,04 ms; 0% de falha |
| Warmup | cerca de 1.500 requisições a 50/s (fora dos thresholds) |
| checks | 25.521/25.521 (100%) |
| `dropped_iterations` | 0 (a métrica nem aparece no resumo) |
| 429 (`linkpulse_ratelimit_requests_total{result="rejected"}`) | 0 (36 aceitos: 12 do seed, 5 do setup e 19 do create) |
| Pipeline depois da carga | pendentes 0 e gravados/publicados = 1 já na primeira leitura, 5 s depois |
| Cache | 26.836 hits contra 25 misses (cerca de 99,9%) |

## Accomplishments

- `load/load.js` com os cenários `warmup`, `redirect` e `create`. O script usa `maxRedirects: 0` global, `redirects: 0` em cada requisição, o check `status === 302`, UA de navegador, `p(99)` no resumo e thresholds por tag `scenario`. Não há nenhum import remoto.
- `make load` grava o resumo nativo no terminal e o arquivo `load/results/load-summary.json`, sem remote write (D-11). O `K6_USER` é exportado pelo Makefile e vira o `user` do serviço `k6`.
- A sequência do avaliador a partir do zero passou de ponta a ponta: zero 429, pipeline drenado e os quatro submétricos no JSON.
- README com a linha `make load` na tabela de comandos e a seção "Teste de carga (make load)".

## Task Commits

1. **Task 1: make load mede o redirect sem seguir o 302 e grava o resumo JSON** - `c58b215` (feat)
2. **Task 2: perfil completo de D-10 passa nos thresholds na sequência limpa, com resumo versionado** - `59e45d9` (feat)

## Files Created/Modified

- `load/load.js`: teste de carga k6 (setup, cenários warmup/redirect/create, thresholds de D-10)
- `load/results/.gitkeep`: diretório de saída do `--summary-export`
- `load/results/load-summary.json`: saída crua do `make load` da execução limpa
- `Makefile`: `K6_USER ?= uid:gid` exportado, alvo `load` no `.PHONY`
- `compose.yaml`: `user: "${K6_USER:-12345:12345}"` no serviço `k6`
- `README.md`: linha `make load` e seção "Teste de carga (make load)"

## Decisions Made

- **A5 confirmado:** o k6 2.3.0 aceita `--summary-export` e grava em caminho relativo ao `working_dir`. O `handleSummary` de contingência não foi usado.
- **A4 confirmado:** a tag de sistema `scenario` gera `http_req_duration{scenario:redirect}`, `{scenario:create}` e os `http_req_failed` correspondentes no JSON. O `http_req_failed` conta 302 e 201 como sucesso.
- **K6_USER no Git Bash:** `id -u`/`id -g` devolvem `197609:197121`, e o container grava normalmente no bind mount do Docker Desktop.
- O create fez 19 iterações em 90 s (12/min mais a iteração do instante zero), sempre em janelas posteriores à do setup, e nenhum POST recebeu 429.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] A stack não estava de pé na precondição da Task 1**
- **Found during:** Task 1 (precondição `docker compose ps` listando `app`)
- **Issue:** o Docker estava rodando, mas a stack `linkpulse` estava parada.
- **Fix:** rodei `make up` antes do verify. O próprio `<verify>` da Task 1 já roda `docker compose up -d --wait`, e o orquestrador pediu verificação ao vivo.
- **Verification:** `docker compose ps --status running --services` listou os 6 serviços.
- **Committed in:** sem mudança de arquivo

**2. [Rule 1 - Evidência] A primeira execução do tracer estourou o p99 com a JVM fria**
- **Found during:** Task 1
- **Issue:** a primeira execução logo depois do `make up` deu p95 de 17 ms e p99 de 605 ms, e o threshold `p(99)<100` falhou. O perfil da Task 1 ainda não tem o `warmup` (Pitfall 10, coberto pela Task 2).
- **Fix:** nenhum threshold foi afrouxado e nenhuma config do app foi alterada. Rodei de novo com a JVM aquecida: p95 de 2,7 ms e p99 de 5,62 ms, `LOAD-TRACER-OK`. Depois do commit, o tracer passou de novo (p99 de 3,39 ms). Na execução limpa da Task 2, com o cenário `warmup`, o p99 foi de 9,29 ms já na primeira tentativa.
- **Files modified:** nenhum
- **Committed in:** n/a

---

**Total deviations:** 2 (1 blocking de ambiente e 1 de evidência). Nenhuma mudou código.
**Impact on plan:** nenhum. O cenário `warmup` da Task 2 é exatamente a mitigação prevista para o JIT.

## Issues Encountered

- **Guarda de branch:** o `gsd-tools query git.base-branch --is-protected main` responde `true`, e o `git.allow_default_branch_commits` não está configurado. Mesmo assim, os commits foram feitos na `main`. Este é o fluxo configurado do projeto (`branching_strategy: none`, executor sequencial na árvore principal), igual ao da 03-02 e da 03-03. O HEAD não "derivou" para a `main`. Se o projeto quiser silenciar a guarda, defina `git.allow_default_branch_commits: true` no `.planning/config.json` (decisão do usuário).
- No primeiro verify, o `make load` passou por um `| grep -v Container` que mascararia o código de saída. A execução que valeu (re-run do tracer e o e2e da Task 2) rodou sem pipe e saiu com 0.

## Checagem humana de fim de fase

O modo é `end-of-phase`, então a checagem fica para o verificador. Com a stack de pé, depois da carga, fiz um proxy automatizado:

- `/api/v1/rules`: as quatro regras (`LinkPulseHigh5xxRate`, `LinkPulseHighP99Latency`, `LinkPulseClickConsumerLag` e `LinkPulseTargetDown`), cada uma com `runbook_url`.
- Grafana anônimo: `GET /` responde 200 (home), `/api/dashboards/uid/link-pulse-overview` responde 200, e a busca lista "Link Pulse — Aplicação" e "Link Pulse — Pipeline de cliques".
- Dados: cerca de 92 req/s de média em 5 min, cerca de 27.700 cliques gravados em 10 min, cache com 26.836 hits contra 25 misses, e pendentes em 0.

A leitura visual dos painéis continua pendente. A stack foi derrubada com `make down`, mantendo os volumes. Para olhar: `make up` e, para ter dados frescos, `make load`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- A Phase 3 está completa (4 de 4 planos). Os critérios 1 a 5 da ROADMAP foram exercidos a partir do zero.
- Para o README da Phase 5, os números e a máquina estão na tabela acima, e o JSON versionado está em `load/results/load-summary.json`.
- Para a Phase 4, o `load.js` usa `BASE_URL` por variável de ambiente e serve para um smoke contra o Traefik/kind, se for o caso.

## Self-Check: PASSED

- FOUND: load/load.js, load/results/.gitkeep, load/results/load-summary.json (rastreado pelo git)
- FOUND: c58b215, 59e45d9
- Acceptance da Task 1 e da Task 2 reexecutados: todos PASS. Imports remotos: 0. `prometheus-rw` no Makefile: 0. `make build`: BUILD SUCCESS.

---
*Phase: 03-stack-local-observ-vel*
*Completed: 2026-10-17*
