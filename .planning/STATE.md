---
gsd_state_version: "1.0"
current_phase: 5
current_phase_name: Terraform AWS e vitrine
status: planning
stopped_at: Phase 04 complete, ready to plan Phase 5
last_updated: "2026-10-30T00:51:33.203Z"
last_activity: 2026-10-29
last_activity_desc: Phase 04 complete, transitioned to Phase 5
state_head: 921913b7cc81216cf4119f441ae72416e0e28a82
progress:
  total_phases: 5
  completed_phases: 4
  total_plans: 25
  completed_plans: 25
  percent: 80
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-30)

**Core value:** Um avaliador clona o repositório, roda `make up` (compose) ou `make k8s` (kind + Prometheus/Grafana) e vê um encurtador funcionando de ponta a ponta, com redirect rápido via cache e métricas reais nos dashboards versionados.
**Current focus:** Phase 05 — Terraform AWS e vitrine

## Current Position

Phase: 5 — Terraform AWS e vitrine
Plan: Not started
Status: Ready to plan
Last activity: 2026-10-29 — Phase 04 complete, transitioned to Phase 5

Progress: [████████░░] 80%

## Performance Metrics

**Velocity:**

- Total plans completed: 25
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 8 | - | - |
| 02 | 9 | - | - |
| 03 | 4 | - | - |
| 04 | 4 | - | - |

**Recent Trend:**

- Last 5 plans: -
- Trend: -

*Atualizado após cada plano concluído*
**Per-Plan Metrics:**

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 01 P01 | 21min | 3 tasks | 13 files |
| Phase 01 P02 | 10min | 2 tasks | 13 files |
| Phase 01 P03 | 5min | 2 tasks | 11 files |
| Phase 01 P04 | 6 min | 1 tasks | 8 files |
| Phase 01 P05 | 4min | 1 tasks | 7 files |
| Phase 01 P06 | 8min | 2 tasks | 8 files |
| Phase 01 P07 | 7min | 2 tasks | 6 files |
| Phase 01 P08 | 14 min | 3 tasks | 9 files |
| Phase 02 P01 | 14min | 2 tasks | 12 files |
| Phase 02 P02 | 13min | 2 tasks | 11 files |
| Phase 02 P03 | 22 min | 2 tasks | 13 files |
| Phase 02 P04 | 15 min | 2 tasks | 14 files |
| Phase 02 P05 | 13 min | 2 tasks | 9 files |
| Phase 02 P06 | 13 min | 2 tasks | 8 files |
| Phase 02 P07 | 18min | 3 tasks | 10 files |
| Phase 02 P08 | 28min | 3 tasks | 6 files |
| Phase 02 P09 | 21 min | 3 tasks | 14 files |
| Phase 03 P02 | 14min | 3 tasks | 8 files |
| Phase 03 P03 | 9min | 3 tasks | 9 files |
| Phase 03 P04 | 11min | 2 tasks | 6 files |
| Phase 04 P01 | 47 min | 2 tasks | 22 files |
| Phase 04 P02 | 22 min | 3 tasks | 13 files |
| Phase 04 P03 | 41min | 3 tasks | 9 files |

## Accumulated Context

### Decisions

As decisões ficam registradas na tabela "Decisões Importantes" do PROJECT.md.
Decisões recentes que afetam o trabalho atual:

- [Roadmap]: Granularidade coarse — as 7 fases sugeridas pela pesquisa viraram 5: camada Valkey + pipeline de cliques numa fase só (Phase 2); k6 junto da stack local (Phase 3); GHCR/Trivy/SBOM junto do kind (Phase 4); Terraform + README como vitrine final (Phase 5)
- [Roadmap]: Terraform depende só do contrato de runtime da Phase 3 e pode correr em paralelo à Phase 4
- [Stack]: Spring Boot 4.1.1, Valkey 8.1 em todos os ambientes, Traefik + manifests próprios no kind, coleção normal `click_events` (ver PROJECT.md)
- [Phase 01]: 01-01: Spring Boot 4.1.1 + Java 21; Checkstyle 14.3.0 (google adaptado, severity error) em validate e SpotBugs 4.10.4.1 Max/Medium em verify
- [Phase 01]: 01-01: actions do CI fixadas por SHA (checkout v7.0.1, setup-java v6.0.1, upload-artifact v7.0.1) e conferidas por git ls-remote
- [Phase 01]: 01-02: Cache-Control do 302 fixado em 'no-store, private'; expiração inclusiva (now == expiresAt → 410) pelo Clock UTC da aplicação
- [Phase 01]: 01-02: LinkRepository.nextId() com @Transactional de escrita (evita nextval em transação read-only); spring.mvc.problemdetails.enabled provisório até o GlobalExceptionHandler da 01-06
- [Phase 01]: 01-03: CodeGenerator relança falha de modInverse como IllegalArgumentException 'coprimo' sem encadear a causa (causa raiz legível na falha de subida); linkpulse.code.alphabet/multiplier são contrato one-way
- [Phase 01]: 01-03: POST /links devolve expiresAt null explícito; shortUrl só de linkpulse.base-url via UriComponentsBuilder.pathSegment; alias aceito e ignorado até a 01-04
- [Phase 01]: 01-04: corrida de alias detectada pelo nome da constraint uk_links_code (Hibernate ConstraintViolationException) com fallback SQLSTATE 23505; outras violações de integridade viram 500
- [Phase 01]: 01-04: alias case-sensitive no armazenamento e lookup; reservados (linkpulse.alias.reserved) comparados sem caixa via Locale.ROOT
- [Phase 01]: 01-05: seed demo-link em changeset separado (002-seed-dev.yaml) com contextFilter: dev; contexts explícito por profile (prod default/explícito, dev no application-dev.yml), provado por LiquibaseContextsIT
- [Phase 01]: 01-05: Postgres de dev publicado só em 127.0.0.1:5432 com volume em /var/lib/postgresql; Makefile ganha db-up/db-down e run depende de db-up
- [Phase 01]: 01-06: URL com userinfo (user:senha@host) é rejeitada no @HttpUrl (anti-phishing)
- [Phase 01]: 01-06: A2 confirmada — Jackson 3 rejeita OffsetDateTime sem offset; expiresAt segue OffsetDateTime + @Future, erro sai como /problems/malformed-request
- [Phase 01]: 01-06: GlobalExceptionHandler próprio substitui o handler de Problem Details do Boot; 500 genérico /problems/internal-error sem detalhe interno
- [Phase 01]: Swagger UI/OpenAPI por anotações nos controllers; springdoc publica o redirect como /{code}
- [Phase 01]: Liquibase 5.0.3 (FSL-1.1-ALv2) mantido e registrado no README e no PROJECT.md; alternativa Apache 4.33.0 documentada
- [Phase 01]: 01-08: loopback (127/8, 0.0.0.0, [::1], [::], IPv4-mapped, localhost, *.localhost) é recusado como destino em qualquer perfil, não só em dev
- [Phase 01]: 01-08: host numérico só é aceito na forma canônica a.b.c.d (regra WHATWG ends-in-a-number); TargetHostPolicy nunca resolve DNS
- [Phase 01]: 01-08: LINKPULSE_SELF_HOSTS (linkpulse.self-hosts) vira contrato de configuração das Phases 3-5; binding com underscore confirmado no Boot 4.1.1
- [Phase 01]: 01-08: linkpulse.base-url inválida derruba a subida (mensagem fixa, sem ecoar o valor); T-06-03 substituído por T-08-01 e T-08-06 (mitigação parcial por DNS)
- [Phase 02]: 02-01: valores de lp:link:{code} são L|<ISO-8601 ou ->|<url>, N e G|<ISO-8601>; instantes em Instant.toString(), nunca epoch; formato fora do padrão = miss
- [Phase 02]: 02-01: LinkService.resolve saiu; RedirectService (sem transação) consulta o cache e só chama LinkService.find (read-only) no miss
- [Phase 02]: 02-01: construtor do LinkCache = (StringRedisTemplate, LinkPulseProperties, Clock, MeterRegistry); a 02-03 insere ValkeyAvailability como segundo parâmetro
- [Phase 02]: 02-01: LinkService.create apaga lp:link:{code} no afterCommit (record EvictAfterCommit), sempre; janela residual limitada ao negative-ttl
- [Phase 02]: 02-02: stream lp:clicks com campos code, ts (epoch ms em texto), ref (host ou (direct)), ua (até 512, vazio se ausente) e iph (HMAC hex de 64); contrato lido pelo consumer da 02-04
- [Phase 02]: 02-02: iph = HMAC-SHA256(linkpulse.ip-hash-key, '<dia UTC>|<ip>'); LINKPULSE_IP_HASH_KEY (>= 16 caracteres) é obrigatória fora do dev e vira contrato de configuração das Phases 3-5; o rate limit da 02-09 reusa a chave
- [Phase 02]: 02-02: construtor do ClickPublisher = (StringRedisTemplate, IpHasher, LinkPulseProperties.Clicks, Clock, MeterRegistry); a 02-03 insere ValkeyAvailability como segundo parâmetro
- [Phase 02]: 02-02: RedirectController publica só depois do resolve e só em GET (HttpMethod.GET.matches); Referer normalizado ao host na ingestão, (direct) quando não há host
- [Phase 02]: 02-04: click_events nesta fase = _id (ID do stream), code, ts (Date), referrer, ipHash; a 02-05 acrescenta browser, os e isBot
- [Phase 02]: 02-04: assumption A8 confirmada (spring.mongodb.database=linkpulse prevalece sobre o @ServiceConnection)
- [Phase 02]: 02-04: bean do container Mongo dos ITs = mongoContainer (mongo já é o MongoClient do Boot)
- [Phase 02]: 02-04: consumer com fábrica Lettuce dedicada (timeout block + 3 s), XACK só depois do bulk UNORDERED
- [Phase 02]: 02-05: clientes HTTP de linha de comando (curl, wget, python-requests, Go/Java http client, k6) e UA vazio contam como bot; okhttp não. Seed (Phase 3) e smoke (Phase 4) precisam de UA de navegador ou includeBots=true
- [Phase 02]: 02-05: índices code_ts e ts_ttl criados pelo consumer no loop (com backoff), nunca na subida; mudança de retenção usa collMod
- [Phase 02]: 02-05: driver do Mongo com server selection 3 s, connect 2 s e read 5 s (MongoConfig); contingência A4 não foi necessária
- [Phase 02]: [02-06] Reclaim por XPENDING IDLE + XCLAIM tipados (sem XAUTOCLAIM), a cada reclaim-interval (30s) para pendentes parados há min-idle (60s), paginado até 20 páginas; ClickStreamConsumer recebe Clock no construtor (a 02-08 depende dele)
- [Phase 02]: [02-06] Sem limite de reentregas: só saem do PEL sem documento as entradas que não existem mais no stream (XRANGE id id vazio), confirmadas e contadas em linkpulse.clicks.lost; A5 confirmada (XCLAIM do Valkey 8.1 já remove do PEL as apagadas)
- [Phase 02]: 02-07: contrato JSON do /stats travado ({code, from, to, includeBots, total, daily[{date, clicks}], topReferrers/topBrowsers/topOs[{value, clicks}]}, datas YYYY-MM-DD); documentos sem isBot contam como humanos e valores nulos nos tops viram (unknown)
- [Phase 02]: 02-07: Mongo fora no /stats vira 503 /problems/stats-unavailable (~8 s com os timeouts do driver); parâmetro malformado vira /problems/invalid-parameter sem ecoar o valor
- [Phase 02]: 02-08: ClickStreamConsumer e ClickPublisher são reiniciáveis (stop libera fábrica/executor, start recria, destroy é terminal): o Spring Framework 7 pausa e retoma contextos de teste em cache
- [Phase 02]: 02-08: gauges linkpulse_clicks_stream_pending/lag/length são do grupo; dashboards e alerta de lag usam max(), lag NaN = indefinido
- [Phase 02]: 02-08: spring-boot-micrometer-metrics-test dispensado; /actuator/prometheus responde em @SpringBootTest RANDOM_PORT no Boot 4.1.1 sem @AutoConfigureMetrics
- [Phase 02]: Rate limit do POST /links: script Lua de janela fixa (INCR + PEXPIRE quando sem TTL), chave lp:rl:{hmac}:{janela}, fail-open pelo ValkeyAvailability e linkpulse.ratelimit.requests{result}
- [Phase 02]: IP real só via RemoteIpValve: forward-headers-strategy native e internal-proxies vazio por padrão; SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES (forma com _) faz binding; Phase 4 usa o CIDR dos pods do Traefik
- [Phase 02]: A2 confirmada: ErrorResponseException no preHandle vira 429 problem+json com Retry-After pelo GlobalExceptionHandler
- [Phase 03]: 03-02: home do Grafana por GF_USERS_HOME_PAGE=/d/link-pulse-overview; no Grafana 13 o GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH vira o dashboard sintético default-home-dashboard, que o anônimo não lê (403)
- [Phase 03]: 03-02: Grafana 13.2.3 carrega dashboards em JSON clássico pelo provisioning por arquivo sem feature toggles (A1 confirmado)
- [Phase 03]: 03-03: alertas com limiares 5xx>5%/5m, p99 redirect>500ms/5m, lag ou pendentes>1000/5m (max + or), TargetDown up==0 or absent (1m); runbooks por runbook_url na main do GitHub; sem Alertmanager
- [Phase 03]: 03-03: make obs-check roda promtool check config + test rules na imagem prom/prometheus:v3.15.0; NaN no promtool usa a notação NaN+0x40
- [Phase 03]: 03-03: Phase 4 deve manter o job link-pulse no ServiceMonitor (nome do Service ou jobLabel) e levar observability/ ao kind sem cópia (ConfigMaps grafana_dashboard, PrometheusRule a partir de groups:)
- [Phase 03]: 03-04: k6 2.3.0 confirma --summary-export (A5) e a tag scenario (A4); perfil de D-10 passa num i7-4790/Docker Desktop com redirect p95 3,18 ms/p99 9,29 ms, create p95 39,31 ms, 0 erro e zero 429 na sequência clean→up→seed→load
- [Phase 03]: 03-04: K6_USER (uid:gid do host) exportado pelo Makefile vira o user do serviço k6; o load-summary.json é versionado como saída crua do make load
- [Phase 04]: 04-01: Traefik 42.1.0 usa service.spec.type; NodePort 30080, externalTrafficPolicy Local, trustedIPs vazio (host chega como 172.21.0.1)
- [Phase 04]: 04-01: indicador do Mongo no Boot 4.1.1 é 'mongo'; readiness = readinessState,db,redis; liveness = livenessState
- [Phase 04]: 04-01: contrato kind: cluster link-pulse, contexto kind-link-pulse, Service link-pulse (http/management), hosts *.localtest.me, tag local-{12 hex}
- [Phase 04]: 04-02: kube-prometheus-stack 92.10.1 enxuto (release monitoring), seletores abertos, sem Alertmanager; Grafana 13.2.4 anônimo Viewer; observability/ entra por --set-file (PrometheusRule + ConfigMaps grafana_dashboard), sem cópia
- [Phase 04]: 04-02: contrato chart: observability.{enabled,rules,dashboards}, metrics.serviceMonitor.{enabled,interval}, autoscaling.*; render falha sem os --set-file (CI passa os mesmos ou observability.enabled=false); hosts grafana./prometheus.localtest.me
- [Phase 04]: 04-02: HPA autoscaling/v2 por CPU 70% (kind 1-3) com metrics-server 3.14.0 (--kubelet-insecure-tls só no kind); Deployment sem spec.replicas com HPA
- [Phase 04]: 04-03: preStop.sleep nativo passou no kubeconform -strict 1.37.1 (sem fallback exec)
- [Phase 04]: 04-03: guardas do lint.sh exigem a mensagem da própria guarda, não só exit != 0
- [Phase 04]: 04-03: interface do CI = bash deploy/kind/lint.sh, up.sh e smoke.sh (sem make)
- [Phase 04]: 04-04: Spring Boot 4.1.2 (patch) zera 3 CVEs CRITICAL do Tomcat; publish carrega o tar testado (sem build); `ghcr.io/lucasllo/link-pulse:sha-{sha7}` + `gh attestation verify` são contrato da Phase 5
- [Phase 04]: 04-04: Mongo 8.0.32 com GLIBC_TUNABLES rseq=1 (kernel 6.19+ do runner, SERVER-121912); a espera da rota fica no up.sh

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1 → todas]: `linkpulse.code.alphabet`/`multiplier` são contrato one-way: mudar depois altera os códigos já emitidos
- [Phase 1]: Loop de redirect via DNS segue como mitigação parcial (T-08-06), agora reforçada pelo rate limit por IP entregue na Phase 2
- [Phase 3]: WR-04 aceito na UAT: o runbook de lag não menciona que entradas nunca entregues e aparadas pelo MAXLEN não entram em `linkpulse_clicks_lost_total`
- [Phase 2]: WR-01 aceito na UAT: com Valkey pausado/particionado, requisições concorrentes no fim da janela de bypass pagam ~150 ms (limitação conhecida, documentada)
- [Phase 4 → 5]: 04-REVIEW com 9 warnings abertos (0 critical); o mais relevante para a Phase 5 é o HEALTHCHECK da imagem no grupo readiness (com Postgres/Valkey), que vira reinício em loop no ECS. Também: README falso sobre HIGH no log do Trivy, XFF confiando no /16 sem NetworkPolicy no values-prod, sem PDB, aviso falso de porta no `make k8s`
- [Phase 4]: Tag `local-{id}` da imagem muda a cada `make image` sem mudança de código (deferred-items da 04-02): todo `make k8s` faz rollout e acumula tags
- [Phase 5]: Spike do token do LocalStack Hobby e do `mock_provider`; sem token, a validação fica nas camadas lint + `terraform test`

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 261009-tdc | Criar pasta estudos com material didático sobre estrutura e tecnologias | 2026-10-09 | 5a5c520 | [261009-tdc-criar-pasta-estudos-com-material-did-tic](./quick/261009-tdc-criar-pasta-estudos-com-material-did-tic/) |
| 2 | Configurar Dependabot para ignorar apache-maven 3.10.x e fechar o PR #2 | 2026-10-11 | 9e5adc5 | — |
| 3 | Adicionar estudos/00-visao-geral-do-projeto.md (visão geral para iniciantes) | 2026-10-11 | 5dbeb90 | — |
| 4 | Adicionar estudos/11-dependabot.md (Dependabot para iniciantes) | 2026-10-11 | 2b12c4d | — |
| 5 | Detalhar ApplicationConfig, LinkPulseProperties e application.yml no guia 02 (para iniciantes) | 2026-10-11 | 25bdd44 | — |
| 6 | Reescrever estudos/ para iniciantes (00-11) e adicionar guias 12-16 da Phase 2 (cache, cliques, stats, rate limit, métricas) | 2026-10-11 | f7d08f5..a0d5164 | — |
| 7 | Corrigir o diagrama mermaid do Fluxo 2 (GET /{code}) no guia 01 | 2026-10-12 | d0f3de9 | — |
| 8 | Atualizar hashes de commits na documentação para o histórico reescrito | 2026-10-15 | 6deeaf4 | — |

## Deferred Items

Itens reconhecidos e adiados no fechamento de milestone, do mais recente para o mais antigo:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| *(none)* | | | | |

## Session Continuity

Last session: 2026-10-30
Stopped at: Phase 04 complete, ready to plan Phase 5
Resume file: None
