---
phase: 03-stack-local-observ-vel
plan: 02
subsystem: infra
tags: [k6, seed, grafana, dashboards, prometheus, promql, compose, makefile]

requires:
  - phase: 03-01
    provides: compose.yaml (projeto linkpulse), imagem link-pulse:local, job link-pulse e datasource Grafana uid prometheus
provides:
  - load/lib/common.js (BASE_URL, PUBLIC_BASE_URL, GRAFANA_URL, BROWSER_UAS, BROWSER_UA, BOT_UA, REFERRERS, pick, weightedPick, linkExists, createLink) para a 03-04
  - load/seed.js (make seed) com 8 aliases + 4 códigos gerados e cerca de 1.350 cliques em 45 s
  - dashboards link-pulse-overview (home) e link-pulse-pipeline na pasta Link Pulse (folderUid link-pulse)
  - serviço k6 no compose (profile tools, working_dir /work, ./load:/work)
  - alvo make seed
affects: [03-03, 03-04, phase-04-kind, phase-05-readme]

actuals:
  tokens: 11300
  tasks: 3
  commits: 3
plan_head_before: 6023f4782af7bd4e03f8943013b99c1892e26ba9

tech-stack:
  added: [grafana/k6:2.3.0]
  patterns:
    - "Dashboards Grafana em JSON clássico escrito à mão (uid fixo, editable false, sem apiVersion), datasource {type prometheus, uid prometheus} em todo painel e alvo"
    - "Gauges do grupo do stream sempre com max(), contadores com sum()/increase(), sem filtro por job/instance/namespace"
    - "Ferramentas k6 como serviço compose com profile tools e caminhos relativos ao working_dir"
    - "Seed idempotente: HEAD /{alias} antes do POST, retry único por Retry-After no 429"

key-files:
  created:
    - load/lib/common.js
    - load/seed.js
    - observability/grafana/provisioning/dashboards/link-pulse.yml
    - observability/grafana/dashboards/link-pulse-overview.json
    - observability/grafana/dashboards/link-pulse-pipeline.json
  modified:
    - compose.yaml
    - Makefile
    - README.md

key-decisions:
  - "Home do Grafana por GF_USERS_HOME_PAGE=/d/link-pulse-overview, e não por GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH: no Grafana 13.2.3 o path vira um dashboard sintético uid default-home-dashboard que o anônimo Viewer não lê (403)"
  - "A1 confirmado: o Grafana 13.2.3 carrega o JSON clássico pelo provisioning por arquivo sem nenhuma feature toggle"
  - "docker compose run --rm -T k6 run --quiet funciona no Git Bash (make seed) sem TTY e sem barra de progresso"
  - "Hit ratio conta negative_hit como hit; lag NaN = indefinido; descartados = publish rejected + failed + lost + invalid"

patterns-established:
  - "make seed = $(STACK) run --rm -T k6 run --quiet seed.js (sempre pelo $(STACK), nunca export global do MSYS)"
  - "Saída do seed termina com 'Seed: <n> links criados, aliases existentes: <n>, 429 recebidos: <k>'"

requirements-completed: [CONT-04, OBS-02, OBS-03, OBS-05]

coverage:
  - id: D1
    description: "make seed cria até 12 links (8 aliases + 4 gerados) e cerca de 1.350 cliques em 45 s pela API pública, sem 429 e sem seguir o 302"
    requirement: CONT-04
    verification:
      - kind: e2e
        ref: "Task 1 <verify>: SEED-TRACER-OK (1371 requisições = 8 HEAD + 12 POST + 1351 GET 302; rejected do rate limit 0.0 antes e depois; sum(linkpulse_clicks_persisted_total)=1351; /stats spring-docs total > 0)"
        status: pass
    human_judgment: false
  - id: D2
    description: "make seed repetível: segunda execução pula os 8 aliases por HEAD, cria só 4 links, sem 429; tops do /stats com 3+ valores e mais cliques com includeBots=true"
    requirement: CONT-04
    verification:
      - kind: e2e
        ref: "Task 3 <verify>: SEED-OK + TOPS-OK (duas execuções seguidas de make seed, 'aliases existentes: 8', '429 recebidos: 0'; spring-docs T=1905, TB=1971; topBrowsers 4, topOs 5, topReferrers 5)"
        status: pass
      - kind: other
        ref: "Task 3 <verify> de docs: SEED-DOCS-OK (Makefile, make help, README, 7 linhas Mozilla/5.0 sem token de bot)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Dashboard da aplicação com req/s por rota e status, p95/p99 do redirect e da criação, cache hit ratio, cliques/min, 5xx, 429 e Valkey indisponível"
    requirement: OBS-02
    verification:
      - kind: other
        ref: "Task 2 lint em node: DASH-LINT-OK (expressões, unidades, percentunit/1 casa/min 0/max 1/NaN, 0 casas em cliques/min, sem filtro proibido)"
        status: pass
      - kind: e2e
        ref: "Task 2 <verify> ao vivo: DASH-LIVE-OK (p95 do redirect 0.0014 s, hit ratio 0.997 depois do seed; home redireciona para link-pulse-overview)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Dashboard do pipeline com lag (NaN = indefinido), pendentes, tamanho do stream e fila com max(), publicados x gravados, descartados, duplicados/reclaim/erros e gravação do lote"
    requirement: OBS-03
    verification:
      - kind: other
        ref: "Task 2 lint em node: DASH-LINT-OK (max() nas gauges, nenhum sum( em gauge do grupo, mapping NaN no lag)"
        status: pass
      - kind: e2e
        ref: "Task 2 <verify> ao vivo: max(linkpulse_clicks_stream_pending)=0, max(linkpulse_clicks_stream_length)=4052; /api/dashboards/uid/link-pulse-pipeline anônimo contém max(linkpulse_clicks_stream_lag)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Dashboards e provider só em observability/grafana/, montados read-only pelo compose e listados para o anônimo na pasta Link Pulse"
    requirement: OBS-05
    verification:
      - kind: e2e
        ref: "GET /api/search?tag=link-pulse anônimo lista link-pulse-overview e link-pulse-pipeline (folderUid link-pulse); docker inspect mostra os binds :ro de provisioning/dashboards e dashboards"
        status: pass
    human_judgment: false
  - id: D6
    description: "Leitura visual dos painéis no navegador (layout, legendas, textos 'sem tráfego'/'indefinido', home anônima renderizando o overview)"
    verification: []
    human_judgment: true
    rationale: "As checagens foram feitas pela API do Grafana; a renderização dos painéis no navegador e a clareza para o avaliador são julgamento visual"

duration: 14min
completed: 2026-10-17
status: complete
---

# Phase 3 Plan 02: Seed k6 e dashboards Grafana Summary

**`make seed` (k6 2.3.0 em container) cria 8 aliases + 4 códigos e cerca de 1.350 cliques com navegadores, referrers e 5% de Googlebot, sem 429 e sem seguir o 302; o Grafana anônimo abre direto no dashboard "Link Pulse — Aplicação" e lista o "Link Pulse — Pipeline de cliques", os dois em JSON clássico provisionado de `observability/`**

## Performance

- **Duration:** 14 min
- **Started:** 2026-10-17T13:27:51Z
- **Completed:** 2026-10-17T13:41:46Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- Roteiro do avaliador fechado: `make up` → `make seed` → <http://localhost:3000>, sem nenhum clique de configuração. O tracer provou o caminho inteiro (API → stream → consumer → Mongo → métricas → Prometheus → Grafana).
- Dashboard da aplicação (12 painéis) com taxa, p95/p99 do redirect e da criação (`histogram_quantile`), hit ratio (`percentunit`, NaN = "sem tráfego"), cliques/min, 5xx, 429 e Valkey indisponível.
- Dashboard do pipeline (9 painéis) com lag (NaN = "indefinido"), pendentes, tamanho do stream e fila do publisher com `max()`, publicados x gravados, descartados, duplicados/reclaim/erros e tempo médio de gravação do lote.
- Seed idempotente e abaixo do rate limit: 12 POSTs na primeira execução, 4 nas seguintes; os tops do `/stats` do `spring-docs` saem com 4 navegadores, 5 SOs e 5 referrers, e `includeBots=true` mostra os cliques do Googlebot.

## Task Commits

1. **Task 1: O seed cria links e cliques e o Grafana mostra os cliques/min sem configuração manual** (tracer): `b3194ec` (feat)
2. **Task 2: Dashboards completos da aplicação e do pipeline, portáveis para o kind**: `d986d37` (feat)
3. **Task 3: make seed realista, idempotente e com resumo, documentado no README**: `23b98a2` (feat)

## Files Created/Modified
- `load/lib/common.js`: helpers do k6 compartilhados com a 03-04
- `load/seed.js`: setup idempotente (HEAD + POST), cenário `clicks` a 30/s por 45 s e teardown com o resumo
- `observability/grafana/provisioning/dashboards/link-pulse.yml`: provider de arquivo da pasta `Link Pulse` (`folderUid: link-pulse`, `allowUiUpdates: false`, `disableDeletion: true`)
- `observability/grafana/dashboards/link-pulse-overview.json`: dashboard da aplicação (OBS-02)
- `observability/grafana/dashboards/link-pulse-pipeline.json`: dashboard do pipeline (OBS-03)
- `compose.yaml`: serviço `k6` (profile `tools`), binds read-only dos dashboards e `GF_USERS_HOME_PAGE`
- `Makefile`: alvo `seed` pelo `$(STACK)`
- `README.md`: linha `make seed` na tabela, linha do Grafana atualizada e subseção "Dados de demonstração (make seed)"

## Contrato exportado de `load/lib/common.js` (a 03-04 importa)

```text
export const BASE_URL        = __ENV.BASE_URL || 'http://app:8080'
export const PUBLIC_BASE_URL = __ENV.PUBLIC_BASE_URL || 'http://localhost:8080'
export const GRAFANA_URL     = __ENV.GRAFANA_URL || 'http://localhost:3000'
export const BROWSER_UAS     // 6 UAs: Chrome/Windows, Safari/iPhone, Firefox/Linux, Edge/Windows, Chrome/Android, Safari/macOS
export const BROWSER_UA      // = BROWSER_UAS[0]
export const BOT_UA          // Googlebot/2.1
export const REFERRERS       // HN, Google, t.co, LinkedIn e null (sem Referer -> (direct))
export function pick(arr)
export function weightedPick(items, weights)
export function linkExists(code) -> boolean                              // HEAD, redirects 0, 302/404 esperados
export function createLink(body) -> {code, shortUrl, created, rateLimited} // 201/409 esperados; 429 -> sleep(Retry-After + 1) e 1 nova tentativa; outro status lança Error
```

`createLink` usa `responseType: 'text'` e por isso funciona com `discardResponseBodies: true`. Um 429 conta em `http_req_failed` (o `responseCallback` só aceita 201/409), o que é intencional: 429 é sinal de orçamento estourado.

## Decisions Made
- **A1 (JSON clássico no Grafana 13.2.3): confirmado.** Os dois JSONs carregaram pelo provider de arquivo sem nenhuma feature toggle e sem erro no log. A contingência `GF_FEATURE_TOGGLES_DISABLE` não foi necessária.
- **A3 (home): `GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH` não serve no Grafana 13.** Ver Deviations. A home ficou em `GF_USERS_HOME_PAGE=/d/link-pulse-overview`: `/api/dashboards/home` responde `{"redirectUri":"/d/link-pulse-overview"}`, e o dashboard é o provisionado, legível pelo anônimo. `GF_AUTH_ANONYMOUS_*` funcionou (03-01).
- **Pitfall 16 (`-T` e `--quiet` no Git Bash): funcionou.** `make seed` no Git Bash roda `docker compose run --rm -T k6 run --quiet seed.js` sem erro de TTY; a saída tem os logs do `console.log` (formato `level=info msg=... source=console`) e o resumo final do k6, sem barra de progresso.
- **Seed medido:** primeira execução com 1371 requisições (8 HEAD + 12 POST + 1351 cliques), p95 de 7,4 ms do lado do k6; execuções seguintes com 1363 (8 HEAD + 4 POST + 1351). Nenhuma execução recebeu 429.
- **Descrições dos painéis conferidas no código:** `lost` = pendentes que sumiram do stream (aparadas pelo MAXLEN ou apagadas), `duplicates` = entrega repetida de clique já gravado, `valkey_unavailable` = aberturas da janela de bypass (`ValkeyAvailability`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Home do Grafana por `GF_USERS_HOME_PAGE` em vez de `GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH`**
- **Found during:** Task 2 (primeira execução do `<verify>` ao vivo, que falhou na checagem da home)
- **Issue:** no Grafana 13.2.3, com `GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH`, `/api/dashboards/home` responde `{"redirectUri":"/d/default-home-dashboard/..."}`. Esse `default-home-dashboard` é um dashboard sintético montado do arquivo, fora da pasta provisionada: o admin lê, mas o anônimo Viewer recebe 403 (`dashboards:read` em `dashboards:uid:default-home-dashboard`). A home do avaliador ficaria quebrada, e o redirect nem continha o UID `link-pulse-overview`.
- **Fix:** `GF_USERS_HOME_PAGE: /d/link-pulse-overview` no serviço `grafana`, que tem precedência quando a organização não tem home própria, e remoção do `GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH`, com comentário pt-BR explicando o motivo. Testei antes com as duas variáveis juntas: o `home_page` vence, o que deixaria o path como configuração morta.
- **Files modified:** `compose.yaml`
- **Verification:** `/api/dashboards/home` anônimo → `{"redirectUri":"/d/link-pulse-overview"}`; `/api/dashboards/uid/link-pulse-overview` anônimo → 200; o `<verify>` da Task 2 rodou de novo do zero e imprimiu `DASH-LIVE-OK`.
- **Committed in:** `d986d37` (commit da Task 2)
- **Efeito no critério de aceite:** o critério da Task 1 "`compose.yaml` contém `GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH`" passou no commit da Task 1 (`b3194ec`), mas deixa de valer a partir da Task 2. A intenção (home = overview, OBS-05 sem configuração manual) é atendida por `GF_USERS_HOME_PAGE`.

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** necessária para a home anônima funcionar. Sem mudança de escopo; a Phase 4 (kind) precisa do mesmo cuidado se quiser home fixa (`grafana.ini.users.home_page` no values do chart).

## Issues Encountered
- **Branch `main` marcada como protegida pelo `gsd-tools`:** a checagem `git.base-branch --is-protected main` devolveu `true`. Segui com commits na `main` porque o orquestrador despachou este executor em modo sequencial na árvore principal, com `branching_strategy: none` no `config.json`, como nos planos anteriores do repositório.
- **Stack parada no início:** a precondição pedia a stack da 03-01 de pé; subi com `docker compose up -d --wait` (imagem `link-pulse:local` já existia) e puxei `grafana/k6:2.3.0` antes do tracer.
- **Volumes:** o banco (volumes `linkpulse_*`) ficou com os links do seed e cerca de 5.400 cliques de demonstração no dia 2026-10-17. A stack foi derrubada com `make down` no fim, mantendo os volumes.
- **Retry por `Retry-After`:** o caminho do 429 está no código e no README, mas não foi exercitado ao vivo, porque a sequência documentada nunca encostou no limite (o que é o comportamento esperado).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- 03-03 (alertas) pode reaproveitar os nomes de métricas e as expressões dos painéis (5xx, p99, lag com `max()`); o compose do Grafana não precisa de mudança.
- 03-04 (carga) importa de `load/lib/common.js` (contrato acima), roda pelo mesmo serviço `k6` e acrescenta o alvo `load` pelo `$(STACK)`. Atenção ao orçamento: o seed gasta 12 criações na primeira execução e 4 nas seguintes.
- Phase 4: os dois JSONs entram como ConfigMaps com `grafana_dashboard: "1"` sem mudança; a home fixa precisa de `users.home_page`, nunca do `default_home_dashboard_path`.

---
*Phase: 03-stack-local-observ-vel*
*Completed: 2026-10-17*

## Self-Check: PASSED
