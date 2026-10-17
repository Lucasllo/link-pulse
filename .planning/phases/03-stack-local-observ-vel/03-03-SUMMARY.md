---
phase: 03-stack-local-observ-vel
plan: 03
subsystem: observability
tags: [prometheus, promtool, alerting, runbooks, grafana, compose, kind]

requires:
  - phase: 03-01
    provides: "compose.yaml com prometheus (diretório observability/prometheus montado, rule_files com glob, lifecycle API desligada) e Makefile com STACK/NOCONV"
  - phase: 03-02
    provides: "dashboards Link Pulse — Aplicação e Pipeline de cliques, provisioning do Grafana, GF_USERS_HOME_PAGE"
  - phase: 02-08
    provides: "gauges linkpulse_clicks_stream_lag (NaN = indefinido) e _pending do grupo"
provides:
  - "Quatro regras de alerta no grupo link-pulse (formato groups:, reaproveitável como spec de PrometheusRule)"
  - "Testes de unidade das regras para o promtool (8 grupos: target down por 0 e por ausência, borda do for, 5xx, p99, lag)"
  - "Runbooks pt-BR por alerta em observability/runbooks/"
  - "Alvo make obs-check (promtool check config + test rules na imagem prom/prometheus:v3.15.0)"
  - "observability/README.md: mapa da fonte única compose x kind"
  - "Seção Observabilidade no README com a demonstração do target down"
affects: [phase-04-kind, servicemonitor, prometheusrule, grafana-sidecar, ci-obs-check, phase-05-readme]

actuals:
  tokens: 8200
  tasks: 3
  commits: 4
plan_head_before: bccdcac999c9c49cd6d2fba25adeb45cbd28c207

tech-stack:
  added: []
  patterns:
    - "Regras de alerta testadas com promtool test rules dentro da imagem do Prometheus (sem ferramenta no host)"
    - "Annotations sem $value para os testes compararem texto exato"
    - "TargetDown com up == 0 or absent(up) para cobrir compose e kind"
    - "Alerta de lag com max() e or nos pendentes (Mongo fora deixa o lag perto de zero)"

key-files:
  created:
    - observability/prometheus/rules/link-pulse-alerts.yml
    - observability/prometheus/tests/link-pulse-alerts-test.yml
    - observability/runbooks/LinkPulseTargetDown.md
    - observability/runbooks/LinkPulseHigh5xxRate.md
    - observability/runbooks/LinkPulseHighP99Latency.md
    - observability/runbooks/LinkPulseClickConsumerLag.md
    - observability/README.md
  modified:
    - Makefile
    - README.md

key-decisions:
  - "Limiares: 5xx > 5% por 5 min (critical), p99 do redirect > 500 ms por 5 min (warning), lag ou pendentes > 1000 por 5 min (warning), target down por 1 min (critical)"
  - "Lag NaN no promtool: a notação NaNx40 não é aceita; NaN+0x40 repete o NaN (confirmado com count(m != m))"
  - "O caso de 5xx que dispara inclui uma série /actuator/prometheus de alto volume: prova que o filtro uri!~\"/actuator.*\" é necessário (sem ele a taxa cai para 0,9%)"
  - "observability/README.md mapeia os GF_* do compose para grafana.ini do chart, incluindo users.home_page (não default_home_dashboard_path)"

patterns-established:
  - "Recarga de regras no compose por docker compose kill -s SIGHUP prometheus (lifecycle API desligada)"
  - "Fonte única: nenhum JSON fora de observability/grafana/dashboards e nenhum groups: fora de observability/prometheus/rules"

requirements-completed: [OBS-04, OBS-05]

coverage:
  - id: D1
    description: "LinkPulseTargetDown dispara com o app parado (up == 0) e com a série ausente (kind), não antes de 1 min, com runbook"
    requirement: OBS-04
    verification:
      - kind: unit
        ref: "observability/prometheus/tests/link-pulse-alerts-test.yml#LinkPulseTargetDown (eval 1m sem alerta, eval 2m firing, série ausente)"
        status: pass
      - kind: e2e
        ref: "docker compose stop app -> /api/v1/alerts LinkPulseTargetDown firing em 86 s -> docker compose up -d --wait (TARGETDOWN-FIRING-OK)"
        status: pass
    human_judgment: false
  - id: D2
    description: "LinkPulseHigh5xxRate, LinkPulseHighP99Latency e LinkPulseClickConsumerLag com casos que disparam e que não disparam, carregados ao vivo com runbook_url exato"
    requirement: OBS-04
    verification:
      - kind: unit
        ref: "make obs-check (promtool test rules: SUCCESS, 8 grupos)"
        status: pass
      - kind: integration
        ref: "curl /api/v1/rules após SIGHUP: 4 regras, runbook_url exato, health ok (RULES-OK, ALERTS-OK)"
        status: pass
    human_judgment: false
  - id: D3
    description: "make obs-check valida config e regras com o promtool da imagem, sem Go/promtool no host; make build continua passando"
    requirement: OBS-04
    verification:
      - kind: other
        ref: "make obs-check (exit 0) e make build (BUILD SUCCESS) no Git Bash"
        status: pass
    human_judgment: false
  - id: D4
    description: "Fonte única em observability/ montada pelo compose sem cópias, com mapa compose x kind no observability/README.md"
    requirement: OBS-05
    verification:
      - kind: other
        ref: "Task 3 verify: docker compose config com as 4 montagens, nenhum JSON/groups: fora de observability/ (OBS-SOURCE-OK)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Runbooks e seção Observabilidade do README claros e acionáveis para o avaliador"
    requirement: OBS-04
    verification: []
    human_judgment: true
    rationale: "Clareza e utilidade do texto pt-BR para um avaliador é julgamento humano; os testes só conferem as seções e os links"

duration: 9min
completed: 2026-10-17
status: complete
---

# Phase 3 Plan 3: Alertas, runbooks e fonte única de observabilidade Summary

**Quatro alertas do Prometheus (5xx, p99 do redirect, lag/pendentes do consumer e target down com `absent()`) com testes de unidade no `promtool`, runbooks pt-BR linkados por `runbook_url`, alvo `make obs-check` e um `observability/README.md` que mapeia cada arquivo do compose para o kind. Parar o app fez o LinkPulseTargetDown ficar firing em 86 s.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-10-17T13:47:06Z
- **Completed:** 2026-10-17T13:55:53Z
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments

- `LinkPulseTargetDown` (`up{job="link-pulse"} == 0 or absent(up{job="link-pulse"})`, `for: 1m`) provado ao vivo: com `docker compose stop app`, o alerta ficou **firing 86 s depois do stop**. A medição confirma a assumption A6, que estimava cerca de 1,5 min. O app voltou healthy com `docker compose up -d --wait`.
- `LinkPulseHigh5xxRate`, `LinkPulseHighP99Latency` e `LinkPulseClickConsumerLag` carregados no Prometheus. Cada um tem um caso de teste que dispara e outro que não dispara. O caso "pendentes sozinhos disparam" cobre o cenário do Mongo fora, e o de lag NaN fica sem alerta.
- `make obs-check`: `promtool check config` e `promtool test rules` dentro de `prom/prometheus:v3.15.0`, com `--no-deps -T`, sem nada no host e sem subir a stack.
- `observability/README.md`: árvore, tabela compose x kind, mapeamento `GF_*` → `grafana.ini` (`users.home_page`), regras de edição, tabela dos alertas e comandos de validação.
- README: linha `make obs-check` na tabela de Comandos e seção `## Observabilidade` com os dashboards, os alertas linkados aos runbooks e a demonstração do target down.

## Task Commits

1. **Task 1: LinkPulseTargetDown com runbook, teste de regra e obs-check** - `baef795` (feat), tracer verificado ao vivo
2. **Task 2: alertas de 5xx, p99 e lag com runbooks (TDD)** - RED `d9ba52e` (test), GREEN `0c4c050` (feat)
3. **Task 3: mapa da fonte única e seção no README** - `5cdc859` (docs)

## Files Created/Modified

- `observability/prometheus/rules/link-pulse-alerts.yml` - grupo `link-pulse` com as quatro regras e o porquê de cada expressão
- `observability/prometheus/tests/link-pulse-alerts-test.yml` - 8 grupos de teste (`alert_rule_test`)
- `observability/runbooks/LinkPulseTargetDown.md`, `LinkPulseHigh5xxRate.md`, `LinkPulseHighP99Latency.md`, `LinkPulseClickConsumerLag.md` - Significado, Impacto, Diagnóstico e Mitigação
- `observability/README.md` - mapa da fonte única compose x kind
- `Makefile` - alvo `obs-check` e `.PHONY`
- `README.md` - linha `make obs-check` e seção Observabilidade

## Respostas pedidas pelo plano

- **Tempo até o firing (A6):** 86 s entre o `docker compose stop app` e o estado firing em `/api/v1/alerts`, dentro do limite de 3 min. O `activeAt` do alerta saiu cerca de 15 s depois do stop.
- **`promtool` no PATH (A9):** sim. `--entrypoint promtool` funciona direto, e o `/bin/promtool` não foi necessário.
- **NaN na notação de série:** `NaNx40` dá parse error (`unexpected identifier "NaNx40"`), e `NaN+0x40` funciona. Um probe com `count(m != m)` confirmou que as amostras são NaN de verdade. O caso de lag indefinido usa NaN real, sem recorrer à série ausente.
- **Notas para a Phase 4:**
  - O job do ServiceMonitor precisa continuar `link-pulse`. Por padrão o job é o nome do Service, então o Service deve se chamar `link-pulse` ou o ServiceMonitor deve fixar `jobLabel`. Sem isso o TargetDown não casa.
  - O Helm não lê arquivos fora do chart. Para levar `observability/` ao kind sem cópia: `kubectl create configmap --from-file` com o label `grafana_dashboard: "1"` no `make k8s`, e o `groups:` de `rules/link-pulse-alerts.yml` vira o `spec` de um PrometheusRule gerado no deploy.
  - O datasource padrão do kube-prometheus-stack já usa o UID `prometheus`.

## TDD Gate Compliance

Task 2 (`tdd="true"`):

- **RED** `d9ba52e`: com as regras ainda ausentes, os três casos que disparam falharam no `promtool` exatamente na asserção do comportamento: `alertname: LinkPulseHigh5xxRate / LinkPulseHighP99Latency / LinkPulseClickConsumerLag, time: 10m, exp:[...], got:[]`, com exit 2. Os três casos que não disparam passaram por vacuidade, o que é esperado.
- **GREEN** `0c4c050`: `promtool test rules` com SUCCESS e `check config` com "4 rules found".
- REFACTOR: não houve.
- O `gsd check tdd-red-evidence` devolveu `INVALID_RED / invalid_record` porque só lê saída node:test/TAP. Foi a mesma limitação registrada na 02-08. A evidência acima é a saída do `promtool`.

## Decisions Made

- O caso de 5xx que dispara tem uma série `/actuator/prometheus` de alto volume (só 200). Se o filtro `uri!~"/actuator.*"` saísse, a taxa cairia para cerca de 0,9% e o teste falharia. Assim o filtro defensivo (A10) também fica provado.
- No `observability/README.md`, as variáveis `GF_*` do compose foram mapeadas para as chaves de `grafana.ini` do chart, com `users.home_page` (o desvio da 03-02) e não `default_home_dashboard_path`.
- Os runbooks citam só nomes de variáveis (`LINKPULSE_IP_HASH_KEY`), nunca valores (T-03-17).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Stack subida pelo executor para atender a precondição**
- **Found during:** Task 1 (precondição)
- **Issue:** a precondição pedia `app` e `prometheus` rodando, mas a stack estava parada no início.
- **Fix:** `make up`, como o orchestrator mandou para a verificação ao vivo. No fim, `make down` derrubou a stack; nenhum container ficou de pé.
- **Verification:** `docker compose ps --status running --services` listou os 6 serviços antes da Task 1, e `docker compose ps -a` ficou vazio depois do `make down`.
- **Committed in:** n/a (só ambiente)

**2. [Rule 1 - Bug] Notação NaN no teste de lag**
- **Found during:** Task 2 (RED)
- **Issue:** `values: 'NaNx40'` deu parse error no `promtool`.
- **Fix:** troca por `NaN+0x40`, validada num probe isolado, e um comentário no teste.
- **Committed in:** `d9ba52e`

**3. [Rule 2 - Correção] Série do actuator no caso de 5xx que dispara**
- **Found during:** Task 2 (RED)
- **Issue:** sem uma série do actuator, o teste não provava o filtro `uri!~"/actuator.*"`.
- **Fix:** série `/actuator/prometheus` com status 200 em `0+1000x40` no caso que dispara.
- **Committed in:** `d9ba52e`

---

**Total deviations:** 3 auto-fixed (1 blocking, 1 bug, 1 correção de teste)
**Impact on plan:** nenhum desvio de escopo. Os commits foram direto na `main`, como nos planos anteriores: o executor é sequencial, `branching_strategy: none` e o orchestrator despachou para a working tree principal.

## Issues Encountered

- O `docker compose kill -s SIGHUP prometheus` imprime "Killing/Killed", mas o container continua rodando e recarrega as regras. As quatro ficaram com `health: ok` depois de um ciclo de avaliação.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Os critérios 3 e 4 da ROADMAP estão cobertos. Falta a 03-04 (teste de carga).
- Phase 4: ver as notas acima (job `link-pulse`/`jobLabel`, PrometheusRule a partir de `groups:`, ConfigMaps dos dashboards). O CI pode reaproveitar o `make obs-check`.

## Self-Check: PASSED

- Os 7 arquivos criados existem, e `Makefile` e `README.md` foram modificados (`git diff --stat bccdcac..HEAD`: 9 files).
- Commits presentes: `baef795`, `d9ba52e`, `0c4c050`, `5cdc859` (`git rev-list --count bccdcac..HEAD` = 4).
- Verificações finais: `make obs-check` SUCCESS, `make build` BUILD SUCCESS, Task 3 verify OBS-SOURCE-OK.

---
*Phase: 03-stack-local-observ-vel*
*Completed: 2026-10-17*
