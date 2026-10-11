---
gsd_state_version: "1.0"
current_phase: 02
current_phase_name: hot-path-no-valkey-e-pipeline-de-cliques
status: executing
stopped_at: Phase 2 context gathered
last_updated: "2026-10-10T15:26:34.285Z"
last_activity: 2026-10-09
last_activity_desc: Phase 1 complete, transitioned to Phase 2
state_head: dd3268dc567d5492a3cacff118db82d130794acb
progress:
  total_phases: 5
  completed_phases: 1
  total_plans: 17
  completed_plans: 8
  percent: 20
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-09)

**Core value:** Um avaliador clona o repositório, roda `make up` (compose) ou `make k8s` (kind + Prometheus/Grafana) e vê um encurtador funcionando de ponta a ponta, com redirect rápido via cache e métricas reais nos dashboards versionados.
**Current focus:** Phase 2 — Hot path no Valkey e pipeline de cliques

## Current Position

Phase: 02 (hot-path-no-valkey-e-pipeline-de-cliques) — READY TO EXECUTE
Plan: Not started
Status: Ready to execute
Last activity: 2026-10-09 - Completed quick task 261009-tdc: Criar pasta estudos com material didático sobre estrutura e tecnologias

Progress: [██░░░░░░░░] 20%

## Performance Metrics

**Velocity:**

- Total plans completed: 8
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 8 | - | - |

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

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1 → todas]: `linkpulse.code.alphabet`/`multiplier` são contrato one-way: mudar depois altera os códigos já emitidos
- [Phase 1 → 2]: Loop de redirect via DNS segue como mitigação parcial (T-08-06); o rate limit por IP da Phase 2 (RATE-01) é parte da mitigação
- [Phase 2]: Pesquisar a API do Spring Data Redis na versão do Boot 4.1 (`cancelOnError`, `BLOCK` com Lettuce, `XAUTOCLAIM`) e o desenho dos testes de reentrega; `maxmemory-policy volatile-lru` em todos os ambientes
- [Phase 4]: Pesquisar versões atuais dos charts Traefik, kube-prometheus-stack e metrics-server, seletores (`*SelectorNilUsesHelmValues`) e sidecar de dashboards
- [Phase 5]: Spike do token do LocalStack Hobby e do `mock_provider`; sem token, a validação fica nas camadas lint + `terraform test`

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 261009-tdc | Criar pasta estudos com material didático sobre estrutura e tecnologias | 2026-10-09 | 4d759c6 | [261009-tdc-criar-pasta-estudos-com-material-did-tic](./quick/261009-tdc-criar-pasta-estudos-com-material-did-tic/) |

## Deferred Items

Itens reconhecidos e adiados no fechamento de milestone, do mais recente para o mais antigo:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| *(none)* | | | | |

## Session Continuity

Last session: 2026-10-10T13:32:51.589Z
Stopped at: Phase 2 context gathered
Resume file: .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-CONTEXT.md
