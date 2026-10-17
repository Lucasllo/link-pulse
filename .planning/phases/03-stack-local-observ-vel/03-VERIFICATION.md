---
phase: 03-stack-local-observ-vel
verified: 2026-10-17T14:33:00Z
status: human_needed
score: 5/5 must-haves verified
covered_files:
  - ".github/dependabot.yml"
  - ".gitignore"
  - ".planning/REQUIREMENTS.md"
  - ".planning/phases/03-stack-local-observ-vel/03-01-PLAN.md"
  - ".planning/phases/03-stack-local-observ-vel/03-01-SUMMARY.md"
  - ".planning/phases/03-stack-local-observ-vel/03-02-PLAN.md"
  - ".planning/phases/03-stack-local-observ-vel/03-02-SUMMARY.md"
  - ".planning/phases/03-stack-local-observ-vel/03-03-PLAN.md"
  - ".planning/phases/03-stack-local-observ-vel/03-03-SUMMARY.md"
  - ".planning/phases/03-stack-local-observ-vel/03-04-PLAN.md"
  - ".planning/phases/03-stack-local-observ-vel/03-04-SUMMARY.md"
  - "Makefile"
  - "README.md"
  - "app/.dockerignore"
  - "app/Dockerfile"
  - "compose.dev.yaml"
  - "compose.yaml"
  - "load/lib/common.js"
  - "load/load.js"
  - "load/results/load-summary.json"
  - "load/seed.js"
  - "observability/README.md"
  - "observability/grafana/dashboards/link-pulse-overview.json"
  - "observability/grafana/dashboards/link-pulse-pipeline.json"
  - "observability/grafana/provisioning/dashboards/link-pulse.yml"
  - "observability/grafana/provisioning/datasources/prometheus.yml"
  - "observability/prometheus/prometheus.yml"
  - "observability/prometheus/rules/link-pulse-alerts.yml"
  - "observability/prometheus/tests/link-pulse-alerts-test.yml"
  - "observability/runbooks/LinkPulseClickConsumerLag.md"
  - "observability/runbooks/LinkPulseHigh5xxRate.md"
  - "observability/runbooks/LinkPulseHighP99Latency.md"
  - "observability/runbooks/LinkPulseTargetDown.md"
covered_digest: "v1:sha256:ff28314a2f055a735d53bf6f7281cac240f5508e4c918118da23e927bc873a19"
behavior_unverified: 0
overrides_applied: 0
human_verification:
  - test: "Com a stack de pé e depois de `make seed`, abrir http://localhost:3000 sem login e percorrer os dashboards 'Link Pulse — Aplicação' e 'Link Pulse — Pipeline de cliques'"
    expected: "A home redireciona para o dashboard da aplicação; os painéis de requisições/s, p95/p99 do redirect e da criação, cache hit ratio e cliques/min mostram séries; o pipeline mostra lag, pendentes, tamanho do stream, publicados x gravados e descartados; sem tráfego o hit ratio exibe 'sem tráfego' e o lag NaN exibe 'indefinido'; nenhum painel em erro"
    why_human: "O verificador conferiu por API (busca, datasource, consultas via /api/ds/query do anônimo) que os dados chegam, mas a renderização, legibilidade e layout dos painéis só se avaliam visualmente"
  - test: "Revisar as proibições marcadas como 'unverified-prohibition' (portas só no loopback, sem segredo real versionado, dashboards sem filtro por código/URL/IP, seed só pela API pública, carga sem mexer em config/thresholds/resumo)"
    expected: "Confirmar o veredito não autoritativo do verificador (todas respeitadas, ver tabela de proibições) ou apontar violação"
    why_human: "Proibições de tier test sem teste automatizado ligado e proibições de tier judgment não podem ser fechadas sozinhas pelo verificador (fail-closed)"
  - test: "Ler os quatro runbooks em observability/runbooks/ do ponto de vista de um avaliador/operador"
    expected: "Texto claro e útil; aceitar ou corrigir a afirmação imprecisa do runbook de lag sobre linkpulse_clicks_lost_total (WR-04)"
    why_human: "Clareza e exatidão operacional do texto pt-BR é julgamento humano (os testes só conferem seções e links)"
---

# Phase 3: Stack local observável — Relatório de Verificação

**Objetivo da fase:** Um avaliador roda `make up`, popula dados de demonstração e vê o encurtador funcionando com métricas reais em dashboards e alertas versionados, com o desempenho comprovado por teste de carga.
**Verificado em:** 2026-10-17T14:33:00Z
**Status:** human_needed
**Re-verificação:** Não (verificação inicial)

Toda a verificação abaixo foi feita ao vivo neste processo, não a partir dos SUMMARYs. Sequência executada: `make up` → inspeção da imagem e dos containers → consultas ao Prometheus e ao Grafana (anônimo) → `make seed` → consultas das métricas dos painéis → `docker compose stop app` e acompanhamento do alerta → `make up` (recuperação) → `make load` → `make obs-check` → `make down`. O `load/results/load-summary.json` regravado pelo `make load` foi restaurado com `git checkout` para não sujar a árvore (cópia do resultado em scratchpad).

## Atingimento do objetivo

### Verdades observáveis (critérios de sucesso do ROADMAP)

| # | Verdade | Status | Evidência |
|---|---------|--------|-----------|
| 1 | `make up` sobe app, Postgres, Mongo, Valkey, Prometheus e Grafana na ordem dos healthchecks; imagem multi-stage com layered jar, usuário não-root numérico e HEALTHCHECK saudável | ✓ VERIFICADO | `make up` saiu com código 0 em 31 s; o log mostra postgres/valkey/mongo `Healthy` antes de `app Starting`, e todos os 6 `Healthy` no fim. `docker image inspect link-pulse:local`: `User=10001:10001`, `Entrypoint=["java","org.springframework.boot.loader.launch.JarLauncher"]`, HEALTHCHECK `curl -fsS http://localhost:8081/actuator/health/readiness`. `docker history`: 4 camadas COPY separadas (dependencies 84.6MB, spring-boot-loader, snapshot-dependencies, application 492kB). `id` no container: `uid=10001(app)`. Health do app: `healthy`. Portas: só `127.0.0.1:8080`, `127.0.0.1:3000`, `127.0.0.1:9090` no host. |
| 2 | Depois do seed, o Grafana mostra sem configuração manual o dashboard com taxa de requisições, p95/p99, cache hit ratio e cliques/min, e o de saúde do pipeline com lag, pendentes e descartados | ✓ VERIFICADO (visual em human_verification) | `make seed` código 0 ("Seed: 4 links criados, aliases existentes: 8, 429 recebidos: 0", 1351 checks 100%). `/api/search` anônimo lista `link-pulse-overview` e `link-pulse-pipeline` na pasta `link-pulse` (provisionados, `canEdit=false`); `/api/dashboards/home` → `/d/link-pulse-overview`. Consultas dos painéis no Prometheus depois do seed: cliques/min ≈ 1196, hit ratio 0,9989, p99 do redirect 5,2 ms, lag 0, pendentes 0, stream 1351, publish `published=1351/rejected=0/failed=0`, lost/invalid 0. A mesma consulta via `/api/ds/query` do Grafana anônimo com o datasource `uid=prometheus` responde 200. `/stats` do `spring-docs`: total 773 (padrão) < 816 (`includeBots=true`). |
| 3 | O Prometheus carrega os alertas de 5xx, p99, lag do consumer e target down, cada um com runbook; parar o app faz o target down disparar | ✓ VERIFICADO | `/api/v1/rules`: as 4 regras com `health=ok` e `runbook_url` para `observability/runbooks/<alerta>.md` (os 4 arquivos existem, cada um com Significado/Impacto/Diagnóstico/Mitigação). `docker compose stop app` às 14:25:44 → `LinkPulseTargetDown` pending às 14:25:58 → **firing às 14:27:16** (~92 s). Depois de `make up`, o alerta sumiu às 14:28:08. `make obs-check`: `promtool check config` ("SUCCESS: 4 rules found") e `promtool test rules` SUCCESS, código 0. |
| 4 | Dashboards, regras e datasource (UID fixo) vivem só em `observability/`, e o compose os carrega de lá, sem cópias, prontos para o kind | ✓ VERIFICADO | `compose.yaml` monta `./observability/prometheus:/etc/prometheus:ro`, `./observability/grafana/provisioning/{datasources,dashboards}` e `./observability/grafana/dashboards:/etc/grafana/dashboards:ro`. `git ls-files` não mostra nenhum JSON de dashboard, regra ou datasource fora de `observability/`. Datasource `uid: prometheus` (confirmado em `/api/datasources/uid/prometheus`); todos os painéis e alvos usam `{type: prometheus, uid: prometheus}`, sem `templating`, sem filtro por job/instance/namespace. `observability/README.md` mapeia cada arquivo para o kind (ConfigMap com `grafana_dashboard: "1"`, PrometheusRule). |
| 5 | O teste k6 (redirect e criação, sem seguir redirects) roda contra o compose e passa nos thresholds de p95/p99 e erro | ✓ VERIFICADO | `make load` código 0: redirect p95=2,06 ms (<50), p99=4,39 ms (<100); create p95=38,51 ms (<200); `http_req_failed` 0,00% nos dois cenários (24001 redirects, 19 criações); checks 100% (25511). `load.js`: `maxRedirects: 0` global, `redirects: 0` por GET e `check status === 302`; o warmup fica fora dos thresholds. Depois da carga: `linkpulse_ratelimit_requests_total{result="rejected"}` = 0, pendentes 0, lag 0, gravados/publicados = 1. |

**Score:** 5/5 verdades verificadas (0 presentes sem comprovação de comportamento)

As verdades dos PLANs (03-01 a 03-04) foram conferidas junto: stacks `linkpulse` e `linkpulse-dev` com nomes de projeto distintos e sem portas em comum; segredos só como defaults `${VAR:-...}` marcados "não é segredo" e `.env` ignorado pelo git (`git check-ignore`); acesso anônimo só leitura (POST de dashboard anônimo → 403); hit ratio com `percentunit` 0–1, 1 casa e mapeamento NaN → "sem tráfego"; lag NaN → "indefinido"; cliques/min = rate×60 com 0 casas; regra de TargetDown com `== 0 or absent(...)` e `for: 1m`; alvos `seed`, `load`, `obs-check` no Makefile.

### Itens adiados

Nenhum.

### Artefatos obrigatórios

| Artefato | Esperado | Status | Detalhes |
|----------|----------|--------|----------|
| `app/Dockerfile` | Multi-stage, layered jar, usuário 10001, HEALTHCHECK | ✓ VERIFICADO | Build em `21-jdk-noble` com `./mvnw` e cache `/root/.m2`; `-Djarmode=tools extract --layers --launcher`; runtime `21-jre-noble`. Desvio aceito: instala `unzip` no stage de build (necessário para o SHA-256 do wrapper). |
| `app/.dockerignore` | Sem target/, IDE, .env | ✓ VERIFICADO | |
| `compose.yaml` | Stack completa `name: linkpulse` | ✓ VERIFICADO | Subiu e ficou healthy ao vivo. |
| `compose.dev.yaml` | `name: linkpulse-dev` | ✓ VERIFICADO | |
| `Makefile` | up/down/clean/image/seed/load/obs-check | ✓ VERIFICADO | Todos executados com código 0 (exceto clean/image, não executados). |
| `observability/prometheus/prometheus.yml` | Scrape `app:8081`, `rule_files` | ✓ VERIFICADO | `up{job="link-pulse",instance="app:8081"}=1`. |
| `observability/grafana/provisioning/datasources/prometheus.yml` | UID fixo | ✓ VERIFICADO | |
| `observability/grafana/provisioning/dashboards/link-pulse.yml` | Provider da pasta `link-pulse` | ✓ VERIFICADO | |
| `observability/grafana/dashboards/link-pulse-overview.json` | OBS-02 | ✓ VERIFICADO | 12 painéis, formato clássico, `editable: false`, schemaVersion 41. |
| `observability/grafana/dashboards/link-pulse-pipeline.json` | OBS-03 | ✓ VERIFICADO | 9 painéis. |
| `observability/prometheus/rules/link-pulse-alerts.yml` | 4 alertas | ✓ VERIFICADO | |
| `observability/prometheus/tests/link-pulse-alerts-test.yml` | Testes do promtool | ✓ VERIFICADO | `promtool test rules` SUCCESS. |
| `observability/runbooks/*.md` (4) | Runbooks | ✓ VERIFICADO | Imprecisão no runbook de lag (WR-04), ver anti-padrões. |
| `observability/README.md` | Mapa compose x kind | ✓ VERIFICADO | |
| `load/lib/common.js`, `load/seed.js`, `load/load.js` | Seed e carga k6 | ✓ VERIFICADO | |
| `load/results/load-summary.json` | Resumo versionado | ✓ VERIFICADO | Contém `http_req_duration{scenario:redirect}`. |

### Verificação dos key links

| De | Para | Via | Status | Detalhes |
|----|------|-----|--------|----------|
| `compose.yaml` | `app/Dockerfile` | `build: ./app` + `image: link-pulse:local` | WIRED | Imagem `link-pulse:local` construída pelo `make up`. |
| `compose.yaml` | `observability/prometheus/` | bind mount ro | WIRED | Regras carregadas ao vivo. |
| `prometheus.yml` | `application.yml` | `app:8081` + `/actuator/prometheus` | WIRED | `up=1`. |
| datasource | serviço prometheus | `url: http://prometheus:9090` | WIRED | `/api/ds/query` responde 200. |
| `Dockerfile` | readiness | HEALTHCHECK | WIRED | Container `healthy`. |
| `compose.yaml` | dashboards | bind mount ro `/etc/grafana/dashboards` | WIRED | Dashboards listados pelo Grafana. |
| provider | dashboards | `path: /etc/grafana/dashboards` | WIRED | |
| dashboards | datasource | `"uid": "prometheus"` | WIRED | Todos os 21 painéis e alvos. |
| `seed.js`/`load.js` | `lib/common.js` | `from './lib/common.js'` | WIRED | |
| `compose.yaml` | `load/` | serviço k6 `./load:/work` | WIRED | `make seed`/`make load` rodaram. |
| `prometheus.yml` | `rules/link-pulse-alerts.yml` | `rule_files: /etc/prometheus/rules/*.yml` | WIRED | |
| regras | runbooks | `runbook_url` | WIRED | |
| teste de regra | regras | `../rules/link-pulse-alerts.yml` | WIRED | |
| `Makefile` | `compose.yaml` | `run --rm -T k6 run --quiet --summary-export=...` | WIRED | |
| `load.js` | rate limit (`limit=20`) | `startTime: '60s'`, 12/min | WIRED | 0 rejeições. |

### Data-flow trace (nível 4)

| Artefato | Variável | Fonte | Dados reais | Status |
|----------|----------|-------|-------------|--------|
| Dashboard aplicação | `http_server_requests_seconds_*` | Micrometer do app via scrape | Sim (1351 GET 302, 4 POST 201, 8 HEAD 302) | ✓ FLOWING |
| Dashboard aplicação | `linkpulse_cache_requests_total` | `LinkCache` | Sim (hit ratio 0,9989) | ✓ FLOWING |
| Dashboard aplicação | `linkpulse_clicks_persisted_total` | consumer do stream | Sim (≈1196/min durante o seed) | ✓ FLOWING |
| Dashboard pipeline | gauges `linkpulse_clicks_stream_*`, `publish_queue` | consumer/publisher | Sim (stream 1351, lag/pendentes 0) | ✓ FLOWING |
| Dashboard pipeline | `publish_total`, `lost`, `invalid`, `duplicates`, `claimed`, `consumer_errors`, `consumer_write_seconds` | app | Séries existem (valores 0 ou reais) | ✓ FLOWING |

### Spot-checks de comportamento

| Comportamento | Comando | Resultado | Status |
|---------------|---------|-----------|--------|
| Stack sobe healthy | `make up` | exit 0, 6 healthy | ✓ PASS |
| Seed popula | `make seed` | exit 0, 429 = 0, checks 100% | ✓ PASS |
| TargetDown dispara | `docker compose stop app` + poll `/api/v1/alerts` | firing em ~92 s; resolvido após `make up` | ✓ PASS |
| Carga passa thresholds | `make load` | exit 0, todos os thresholds ✓ | ✓ PASS |
| Regras/config válidas | `make obs-check` | exit 0 | ✓ PASS |
| Grafana anônimo só leitura | `POST /api/dashboards/db` anônimo | 403 | ✓ PASS |

### Execução de probes

Nenhum probe `scripts/*/tests/probe-*.sh` declarado ou existente; não se aplica.

### Proibições (must_haves.prohibitions)

| Plano | Proibição | Tier | Disposição | Evidência |
|-------|-----------|------|------------|-----------|
| 03-01 | Não publicar Postgres/Mongo/Valkey/8081 no host nem fora de 127.0.0.1 | test | unverified-prohibition — revisão humana recomendada | Sem teste automatizado ligado. Observado ao vivo: só `127.0.0.1:8080/3000/9090` publicadas. Veredito não autoritativo: respeitada. |
| 03-01 | Não versionar segredo real | test | unverified-prohibition — revisão humana recomendada | Sem teste ligado. Observado: só defaults `${VAR:-...}` comentados como "não é segredo"; `.env` ignorado. Veredito não autoritativo: respeitada. |
| 03-02 | Não seguir o 302 no seed | test | ✓ verificada | Enforcement ligado: `maxRedirects: 0`, `redirects: 0` e check `status === 302` com threshold `checks>0.99`; seed ao vivo 100% de 302. |
| 03-02 | Sem label/variável/filtro por código, URL ou IP nos dashboards | test | unverified-prohibition — revisão humana recomendada | Sem teste ligado. Observado: `templating.list` vazio nos dois JSONs; nenhuma query filtra por esses rótulos. Veredito não autoritativo: respeitada. |
| 03-02 | Seed só pela API pública, sem timestamps fabricados | judgment | unverified-prohibition — revisão humana recomendada | Veredito LLM não autoritativo: respeitada (seed usa só `http.head/get/post` contra `BASE_URL`; nenhum cliente de banco). |
| 03-04 | Não gerar carga contra os destinos (não seguir 302) | test | ✓ verificada | Mesmo enforcement do seed; `make load` com check `redirect 302` 100%. |
| 03-04 | Não mudar config do app, nem afrouxar thresholds, nem editar o resumo à mão | judgment | unverified-prohibition — revisão humana recomendada | Veredito LLM não autoritativo: respeitada (`git diff 42aef9c..HEAD -- app/` só mostra Dockerfile e .dockerignore; thresholds batem com D-10; o resumo foi regenerado com valores coerentes nesta verificação). |

### Cobertura de requisitos

| Requisito | Plano | Descrição | Status | Evidência |
|-----------|-------|-----------|--------|-----------|
| CONT-01 | 03-01 | Dockerfile multi-stage, layered jar, não-root numérico, HEALTHCHECK | ✓ SATISFEITO | Verdade 1 |
| CONT-02 | 03-01 | `make up` com 6 serviços e dependências por healthcheck | ✓ SATISFEITO | Verdade 1 |
| CONT-04 | 03-02 | Seed/demo popula links e cliques | ✓ SATISFEITO | Verdade 2 (ressalva WR-01) |
| OBS-02 | 03-02 | Dashboard req/s, p95/p99, hit ratio, cliques/min | ✓ SATISFEITO | Verdade 2 |
| OBS-03 | 03-02 | Dashboard do pipeline (lag, pendentes, descartados) | ✓ SATISFEITO | Verdade 2 |
| OBS-04 | 03-03 | 4 alertas com runbook | ✓ SATISFEITO | Verdade 3 |
| OBS-05 | 03-02, 03-03 | Fonte única `observability/` | ✓ SATISFEITO (parte do compose) | Verdade 4; o uso pelo kind é escopo da Phase 4 |
| LOAD-01 | 03-04 | k6 sem seguir redirects, thresholds p95/p99 e erro | ✓ SATISFEITO | Verdade 5 |

Requisitos órfãos: nenhum. Os 8 IDs mapeados para a Phase 3 no REQUIREMENTS.md aparecem todos no frontmatter dos planos.

### Anti-padrões encontrados

| Arquivo | Linha | Padrão | Severidade | Impacto |
|---------|-------|--------|------------|---------|
| (todos os arquivos da fase) | — | TBD/FIXME/XXX | — | Nenhum marcador de dívida encontrado. Também nenhum TODO/HACK/PLACEHOLDER. |
| `load/seed.js` | 57 | WR-01: alias `promo-30-dias` com `expiresAt` +30 dias; `linkExists` só aceita 302/404 | ⚠️ Warning | Com o volume `pgdata` mantido por mais de 30 dias, o HEAD responde 410 (falha em `http_req_failed`), o alias entra nos códigos sorteados e ≈4% dos cliques recebem 410, o que reprova `checks>0.99` e faz `make seed` sair com 99. Contradiz o "pode rodar de novo" do README e a verdade de idempotência da 03-02. Não afeta o fluxo do avaliador num clone novo (critério 2 verificado), por isso não é bloqueador; correção barata recomendada. |
| `.github/dependabot.yml` | 81-92 | WR-02: sem `ignore` para majors (temurin 25, postgres 19, mongo 8.x rapid, valkey 9) | ⚠️ Warning | Risco futuro de PR verde que quebra volumes/restrições. Não afeta os critérios da fase. |
| `.github/workflows/ci.yml` | 39-46 | WR-03: CI não roda `make obs-check` nem build da imagem | ⚠️ Warning | As regras e o Dockerfile não têm gate a cada push. O build/publicação da imagem no CI é escopo da Phase 4 (CI-02); o `obs-check` no CI não está coberto por nenhuma fase posterior. Não afeta os critérios (verificados localmente). |
| `observability/runbooks/LinkPulseClickConsumerLag.md` | 24 | WR-04: afirma que todo clique aparado aparece em `linkpulse_clicks_lost_total` | ⚠️ Warning | Runbook existe e está vinculado (critério 3 atendido), mas induz o operador a concluir "sem perda" quando houve. Item de revisão humana. |
| `observability/grafana/dashboards/link-pulse-overview.json` | painel 10 | IN-01: "Respostas 5xx/s" sem `uri!~"/actuator.*"` | ℹ️ Info | Inconsistente com o alerta; neste compose o actuator não aparece em `http_server_requests` (conferido ao vivo), então o efeito hoje é nulo. |

### Verificação humana necessária

#### 1. Inspeção visual dos dashboards no Grafana

**Teste:** `make up` e `make seed`, depois abrir http://localhost:3000 sem login e percorrer os dois dashboards da pasta Link Pulse.
**Esperado:** home redireciona para "Link Pulse — Aplicação"; painéis de requisições/s, p95/p99 (redirect e criação), cache hit ratio e cliques/min com séries; pipeline com lag, pendentes, tamanho do stream, publicados x gravados e descartados; "sem tráfego"/"indefinido" quando aplicável; nenhum painel em erro.
**Por que humano:** os dados foram comprovados por API; a renderização e a legibilidade só se avaliam visualmente.

#### 2. Revisão das proibições sinalizadas

**Teste:** conferir as 5 proibições marcadas "unverified-prohibition" na tabela acima.
**Esperado:** confirmar o veredito não autoritativo (todas respeitadas) ou apontar violação.
**Por que humano:** proibições de tier test sem teste ligado e de tier judgment não fecham sozinhas (fail-closed).

#### 3. Clareza dos runbooks

**Teste:** ler os 4 runbooks; decidir sobre a correção do WR-04.
**Esperado:** texto útil para um operador; afirmação sobre `linkpulse_clicks_lost_total` corrigida ou aceita.
**Por que humano:** julgamento de texto.

### Resumo de lacunas

Nenhuma lacuna bloqueadora. Os cinco critérios de sucesso do ROADMAP foram comprovados ao vivo: a stack sobe healthy na ordem certa com a imagem multi-stage não-root, o seed popula métricas reais que chegam ao Grafana pelo datasource de UID fixo, os quatro alertas carregam com runbook e o TargetDown dispara em ~92 s com o app parado, a fonte única `observability/` é montada sem cópias, e o k6 passa em todos os thresholds sem seguir o 302 e sem nenhum 429.

O status é `human_needed` por causa da inspeção visual do Grafana, das proibições sinalizadas pela regra fail-closed e da clareza dos runbooks. Entre os warnings do code review, o WR-01 (seed que falha 30 dias depois do primeiro seed com o volume mantido) é o único que contradiz uma verdade de plano (idempotência do `make seed`). É uma falha que depende do tempo e não aparece no fluxo do avaliador num clone novo; a correção é barata e recomendada antes da Phase 5 (README/vitrine).

---

_Verificado em: 2026-10-17T14:33:00Z_
_Verificador: Claude (gsd-verifier)_
