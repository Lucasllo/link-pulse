---
gsd_state_version: "1.0"
current_phase: 01
current_phase_name: Fundação e núcleo de links
status: executing
stopped_at: Completed 01-04-PLAN.md
last_updated: "2026-10-08T23:03:40.693Z"
last_activity: 2026-10-08
last_activity_desc: Phase 01 execution started
state_head: 4dc8cf27d07b403ad80568c3873be746d83d28dc
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 7
  completed_plans: 4
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-10-06)

**Core value:** Um avaliador clona o repositório, roda `make up` (compose) ou `make k8s` (kind + Prometheus/Grafana) e vê um encurtador funcionando de ponta a ponta, com redirect rápido via cache e métricas reais nos dashboards versionados.
**Current focus:** Phase 01 — Fundação e núcleo de links

## Current Position

Phase: 01 (Fundação e núcleo de links) — EXECUTING
Plan: 5 of 7
Status: Ready to execute
Last activity: 2026-10-08 — Phase 01 execution started

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: -
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

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

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1]: Pesquisa leve sobre o impacto do Boot 4.1 (Jackson 3, Testcontainers 2, Liquibase 5.x sob licença FSL) e compatibilidade do springdoc; confirmar e registrar a licença da Liquibase
- [Phase 1]: Sqids/permutação (LINK-03) precisa estar definido antes do primeiro código gerado; mudar depois altera os códigos existentes
- [Phase 2]: Pesquisar a API do Spring Data Redis na versão do Boot 4.1 (`cancelOnError`, `BLOCK` com Lettuce, `XAUTOCLAIM`) e o desenho dos testes de reentrega; `maxmemory-policy volatile-lru` em todos os ambientes
- [Phase 4]: Pesquisar versões atuais dos charts Traefik, kube-prometheus-stack e metrics-server, seletores (`*SelectorNilUsesHelmValues`) e sidecar de dashboards
- [Phase 5]: Spike do token do LocalStack Hobby e do `mock_provider`; sem token, a validação fica nas camadas lint + `terraform test`

## Deferred Items

Itens reconhecidos e adiados no fechamento de milestone, do mais recente para o mais antigo:

| Category | Item | Status | Deferred At | Milestone |
|----------|------|--------|-------------|-----------|
| *(none)* | | | | |

## Session Continuity

Last session: 2026-10-08T23:03:40.656Z
Stopped at: Completed 01-04-PLAN.md
Resume file: None
