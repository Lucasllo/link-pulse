# Walking Skeleton — Link Pulse

**Phase:** 1
**Generated:** 2026-10-06

## Capability Proven End-to-End

Um cliente faz `POST /links` com uma URL, recebe um código curto de 7 caracteres (ou o alias que pediu) e, ao chamar `GET /{code}`, é redirecionado (302, sem cache no navegador) para a URL original lida do PostgreSQL. Tudo isso passa pelo mesmo `./mvnw -B -ntp verify`, que roda igual no Git Bash do Windows (caminho com espaço) e no GitHub Actions (checkout em `link pulse/`).

Planos que compõem o esqueleto: `01-01` (build/CI), `01-02` (redirect a partir do Postgres), `01-03` (criação com código gerado). `01-04` a `01-07` são slices de expansão em cima dele (alias; runtime local com seed; URL/expiração/Problem Details; Swagger UI e README).

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Spring Boot **4.1.1** + Java 21 (Temurin no CI), Spring MVC via `spring-boot-starter-webmvc`, virtual threads ligadas | Linha 3.x sem suporte OSS desde 2026-06-30 (PROJECT.md). MVC + virtual threads é o caminho padrão e legível do Spring |
| Build | Maven 3.9.16 via wrapper `only-script` 3.3.4 com `distributionSha256Sum`; projeto Maven em `app/` | Build idêntico local/CI sem Maven instalado. `app/` vira o contexto Docker autocontido da Phase 3 (layout = `costly`: mudar exige `git mv` + CI/Makefile) |
| Coordenadas | `groupId dev.linkpulse`, `artifactId link-pulse`, pacote raiz `dev.linkpulse` (D-13) | Decisão do usuário (`costly`) |
| Layout de código | Por feature: `dev.linkpulse.link`, `dev.linkpulse.config`, `dev.linkpulse.common` (D-14) | As Phases 2–5 adicionam `.click`, `.stats`, `.ratelimit`, `.cache` |
| Data layer | PostgreSQL 18 (`postgres:18.6`) + Spring Data JPA (Hibernate 7.4.5, `ddl-auto=validate`, `open-in-view=false`) + Liquibase 5.0.3 (BOM, licença FSL-1.1-ALv2) com changelogs YAML e contexts `dev`/`prod` | O Liquibase é a única fonte do schema; o Hibernate só confere |
| Geração de ID | `select nextval('link_id_seq')` numa transação de escrita, antes do INSERT; entidade `Link implements Persistable<Long>` + `saveAndFlush` | O código existe antes do commit; um único INSERT, sem `merge` |
| Código curto | Permutação multiplicativa própria `base62(pad7((id·M) mod 62^7))` em `BigInteger`, M coprimo de 62^7 em `linkpulse.code.multiplier` (D-01..D-03) | `one-way`: trocar algoritmo, comprimento ou M muda todos os códigos publicados. Ofuscação, não segurança |
| Alias | Regex `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$`, reservados case-insensitive em `linkpulse.alias.reserved`, lookup case-sensitive, `UNIQUE uk_links_code` + 409 (D-05..D-08) | Namespaces disjuntos por construção (códigos gerados são só alfanuméricos) |
| Contrato HTTP | `POST /links` → 201 + `Location` + `{code, shortUrl, targetUrl, expiresAt, createdAt}`; `GET /{code:[A-Za-z0-9_-]+}` → 302 + `Cache-Control: no-store, private`; erros RFC 9457 com `type` `/problems/<slug>` (D-09..D-12) | Contrato público usado por k6, smoke test e README (`costly`) |
| Auth | Nenhuma | Fora de escopo do projeto (REQUIREMENTS.md) |
| Configuração | Prefixo `linkpulse.*` num record `@ConfigurationProperties` + `@Validated`; defaults no `application.yml`, override por env (`LINKPULSE_BASE_URL`, `LINKPULSE_CODE_MULTIPLIER`) | Contrato de runtime das Phases 3–5 |
| Portas | API 8080; management 8081 expondo só `health,info` | Contrato de runtime (probes completos na Phase 4) |
| Testes | JUnit 6 + AssertJ; `*Test` no surefire (sem Docker); `*IT` no failsafe com Testcontainers 2.0.5 declarados como `@Bean @ServiceConnection` em `TestcontainersConfiguration`; `AbstractIT` com `MockMvcTester` | O MockMvc não segue redirect; containers como beans não morrem entre classes |
| Qualidade | Checkstyle 14.3.0 (`google_checks.xml` adaptado: 4 espaços, `severity=error`, `IT` permitido como abreviação) na fase `validate`; SpotBugs 4.10.4.1 (`effort=Max`, `threshold=Medium`) na fase `verify` | Gates quebram o build local e o CI |
| Deployment target (Phase 1) | Execução local documentada: `make run` = `compose.dev.yaml` (só Postgres 18, porta 127.0.0.1:5432) + profile `dev` com seed `demo-link`; CI no GitHub Actions `ubuntu-latest` | Imagem/compose completo é Phase 3; kind é Phase 4 |
| Portabilidade | `.gitattributes` (LF, `*.cmd` CRLF) como primeiro arquivo; `app/mvnw` com modo 100755; Makefile só com caminhos relativos; CI faz checkout em diretório com espaço | Windows (Git Bash/WSL) e Linux buildam igual |

## Stack Touched in Phase 1

- [ ] Project scaffold (framework, build, lint, test runner) — `01-01`
- [ ] Routing — `GET /{code}` (`01-02`) e `POST /links` (`01-03`)
- [ ] Database — leitura (`findByCode`, `01-02`) e escrita (`nextval` + `saveAndFlush`, `01-03`)
- [ ] "UI" — API HTTP consumida via curl/Swagger UI (`01-07`); não há frontend no projeto
- [ ] Deployment — `make run` com Postgres local + seed dev (`01-05`) e CI verde no GitHub Actions (`01-01`)

## Out of Scope (Deferred to Later Slices)

- Cache Valkey, negative cache, rate limit, pipeline de cliques, MongoDB, `/links/{code}/stats`, métricas de negócio (Phase 2)
- Dockerfile da app, `make up` com os 6 serviços, dashboards, alertas, seed de cliques, k6 (Phase 3)
- Helm chart, kind, Traefik, kube-prometheus-stack, GHCR, Trivy/SBOM, liveness/readiness completos (Phase 4)
- Terraform AWS e README vitrine com diagrama, prints e trade-offs (Phase 5)
- `GET /links/{code}` de metadados (não é requisito; colidiria em forma com `/links/{code}/stats`)
- PATCH/DELETE de links, autenticação, redirect 301, deduplicação de URL (fora de escopo do projeto)
- find-sec-bugs (adiado pela pesquisa: ruído `SPRING_ENDPOINT`/`UNVALIDATED_REDIRECT` sem ganho), logs JSON estruturados (OBS2-01, v2)

## Subsequent Slice Plan

Cada fase seguinte adiciona um slice vertical em cima deste esqueleto, sem mudar as decisões acima:

- Phase 2: redirect servido do cache Valkey com fail-open, rate limit por IP, cliques assíncronos via Redis Streams → MongoDB e `GET /links/{code}/stats`
- Phase 3: imagem multi-stage da app, `make up` com app + Postgres + Mongo + Valkey + Prometheus + Grafana, dashboards/alertas versionados em `observability/`, seed e k6
- Phase 4: Helm chart + `make k8s` no kind, publicação no GHCR com Trivy/SBOM e smoke test em kind no CI
- Phase 5: módulos Terraform validados em camadas e README pt-BR com evidências
