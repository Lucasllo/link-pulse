# Phase 1: Fundação e núcleo de links - Pattern Map

**Mapped:** 2026-10-06
**Files analyzed:** 38
**Analogs found:** 0 / 38 (repositório greenfield)

> `git ls-files` fora de `.planning/` só lista `.claude/CLAUDE.md`. Ainda não existe código de aplicação, build nem CI. **Nenhum arquivo tem analog no repositório.** A fonte canônica de cada arquivo é a seção indicada em `01-RESEARCH.md` (abreviado como **R**). Esta fase *cria* os padrões que as Phases 2 a 5 vão copiar. Os arquivos marcados com ★ viram os analogs de referência das próximas fases.

## File Classification

| New File | Role | Data Flow | Fonte canônica (R = 01-RESEARCH.md) |
|----------|------|-----------|-------------------------------------|
| `.gitattributes` | config | — | R Pitfall 10 (primeiro commit) |
| `.gitignore` | config | — | padrão Maven/IDE (`target/`, `.idea/`, `*.iml`) |
| `Makefile` | config/tooling | batch | R Code Examples "Makefile" |
| `compose.dev.yaml` | config | — | R Code Examples "compose.dev.yaml" |
| `README.md` | doc | — | R Pitfall 10/12/14 (make no Windows, ofuscação ≠ segurança, Docker para ITs) |
| `.github/workflows/ci.yml` | config (CI) | batch | R Code Examples "ci.yml" |
| `.github/dependabot.yml` (opcional) | config | — | R Recommended Project Structure |
| `app/pom.xml` | config (build) | — | R Standard Stack "Installation" + Code Examples "Plugins de qualidade" |
| `app/mvnw`, `app/mvnw.cmd` | tooling | — | R Code Examples "maven-wrapper.properties" (copiar do zip only-script 3.3.4, sem editar) |
| `app/.mvn/wrapper/maven-wrapper.properties` | config | — | R Code Examples (texto literal) |
| `app/config/checkstyle/checkstyle.xml` | config | — | R Pitfall 4 (google_checks 14.3.0 + ajustes) |
| `app/config/spotbugs/exclude.xml` | config | — | R Pitfall 5 |
| `app/src/main/java/dev/linkpulse/LinkPulseApplication.java` | bootstrap | — | R Project Structure (`@SpringBootApplication` + `@ConfigurationPropertiesScan`) |
| `.../link/Link.java` ★ | model (JPA entity) | CRUD | R Pattern 1 |
| `.../link/LinkRepository.java` ★ | repository | CRUD | R Pattern 1 |
| `.../link/LinkService.java` ★ | service | CRUD + request-response | R Pattern 1 (`create`), Pattern 5 (`resolve`) |
| `.../link/LinkController.java` ★ | controller | request-response | R Pattern 4 + Diagram (201 + `Location`) |
| `.../link/RedirectController.java` ★ | controller | request-response | R Pattern 5 |
| `.../link/CodeGenerator.java` | utility (pura) | transform | R Pattern 2 |
| `.../link/AliasPolicy.java` | utility/validator | transform | R Requirements LINK-04 (regex `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$`) |
| `.../link/CreateLinkRequest.java`, `LinkResponse.java` | DTO (record) | request-response | R Pattern 4 |
| `.../link/LinkNotFoundException.java`, `LinkExpiredException.java`, `AliasConflictException.java`, `AliasReservedException`/`InvalidAliasException` ★ | exception | — | R Pattern 3 (`LinkExpiredException`) |
| `.../config/LinkPulseProperties.java` ★ | config (record) | — | R Pattern 2 (nota final) + Pattern 7 (`linkpulse:` no yml) |
| `.../config/ClockConfig.java`, `OpenApiConfig.java` (e o `@Bean CodeGenerator`) | config | — | R Pattern 2 / QUAL-04 |
| `.../common/GlobalExceptionHandler.java` ★ | middleware (advice) | request-response | R Pattern 3 |
| `.../common/validation/HttpUrl.java`, `HttpUrlValidator.java` | validator | transform | R Pattern 4 |
| `app/src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml` | config | — | R Pattern 7 |
| `.../db/changelog/db.changelog-master.yaml` + `changes/001..003` ★ | migration | — | R Pattern 7 |
| `app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java` ★ | test infra | — | R Pattern 6 |
| `.../AbstractIT.java` ★, `TestLinkPulseApplication.java` | test infra | — | R Pattern 6 |
| `.../link/CodeGeneratorTest.java`, `AliasPolicyTest.java`, `HttpUrlValidatorTest.java` | test (unit) | — | R Validation Architecture (LINK-03/04, QUAL-01) |
| `.../link/LinkApiIT.java`, `RedirectIT.java`, `ConcurrencyIT.java`, `LiquibaseContextsIT.java`, `OpenApiIT.java` | test (IT) | — | R Validation Architecture (Test Map) |

## Pattern Assignments

Sem analog no repositório para nenhum arquivo. Para cada grupo, o planner deve referenciar o trecho do RESEARCH.md por seção e linha:

| Grupo | Copiar de | Linhas do 01-RESEARCH.md |
|-------|-----------|--------------------------|
| Entidade `Persistable` + repository com `nextId()` + `LinkService.create` | Pattern 1 | 301-370 |
| `CodeGenerator` (BigInteger, M = `2176477521915`, M⁻¹ = `3013170320947`; IDs 1..5 → `cJinsNv`, `EdRbklq`, `qxAPd9l`, `TGtDVXg`, `5ac1Nvb`, que servem de vetores de teste) | Pattern 2 | 372-418 |
| `GlobalExceptionHandler` + exceções `ErrorResponseException` + `type` URIs `/problems/*` | Pattern 3 | 420-457 |
| `@HttpUrl` + `CreateLinkRequest` | Pattern 4 | 459-482 |
| `RedirectController` (`/{code:[A-Za-z0-9_-]+}`, `CacheControl.noStore().cachePrivate()`) | Pattern 5 | 484-499 |
| `TestcontainersConfiguration` / `AbstractIT` (MockMvcTester) / `TestLinkPulseApplication` | Pattern 6 | 501-534 |
| Liquibase master + changesets + `application.yml` | Pattern 7 | 536-590 |
| `pom.xml` (dependências) | Standard Stack, Installation | 161-191 |
| `pom.xml` (Checkstyle/SpotBugs/failsafe) | Code Examples | 720-750 |
| `maven-wrapper.properties` | Code Examples | 710-718 |
| `ci.yml` (actions fixadas por SHA, checkout em `link pulse/`) | Code Examples | 752-786 |
| `Makefile` | Code Examples | 788-810 |
| `compose.dev.yaml` | Code Examples | 812-826 |
| `checkstyle.xml` (ajustes no google_checks) | Pitfall 4 | 635-643 |
| `exclude.xml` (só `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`, cada um com comentário) | Pitfall 5 | 645-650 |
| `.gitattributes` | Pitfall 10 | 681-690 |
| Testes unitários e ITs (casos exigidos) | Validation Architecture, Test Map | 902-918 |

## Shared Patterns (convenções que esta fase estabelece)

Valem para todos os arquivos Java da fase e viram a referência das Phases 2 a 5:

- **Pacotes por feature:** `dev.linkpulse.link`, `dev.linkpulse.config`, `dev.linkpulse.common` (D-13/D-14).
- **Records** para DTOs e properties. Sem Lombok.
- **Classes com validação no construtor são `final`** (SpotBugs `CT_CONSTRUCTOR_THROW`). Entidades JPA usam fábrica estática sem `throw`.
- **Erros:** toda exceção de domínio estende `ErrorResponseException` com `ProblemDetail` (`type` `/problems/<slug>`, `title`/`detail` em pt-BR e a propriedade `code` quando houver). O tratamento fica centralizado no `GlobalExceptionHandler extends ResponseEntityExceptionHandler`. Nunca expor SQL ou nome de constraint.
- **Tempo:** `Clock` injetado; `Instant` truncado para `MICROS`; colunas `TIMESTAMP WITH TIME ZONE`.
- **Transações:** escrita com `@Transactional` no service (por causa do `nextval`); `saveAndFlush` para a violação de unique aparecer dentro do método.
- **Config:** prefixo `linkpulse.*` via `LinkPulseProperties` (`@ConfigurationProperties` + `@Validated`), defaults no `application.yml` e override por env.
- **APIs do Boot 4** (R Pitfall 6, 652-664): `spring-boot-starter-webmvc`, `@MockitoBean`, `org.testcontainers.postgresql.PostgreSQLContainer`, `tools.jackson.*`, `@AutoConfigureMockMvc` em `org.springframework.boot.webmvc.test.autoconfigure`.
- **Testes:** `*Test` roda no surefire, sem Docker; `*IT` estende `AbstractIT`, no failsafe. Containers declarados como `@Bean @ServiceConnection`, nunca `static @Container`.
- **Estilo:** 4 espaços, imports no formato do `CustomImportOrder` do Google (estáticos primeiro, depois um bloco ASCII) e Javadoc por tipo.

## No Analog Found

Os 38 arquivos acima não têm analog: o repositório não tem código de aplicação. O planner deve usar os trechos do RESEARCH.md da tabela "Pattern Assignments". Não há padrão legado com que conflitar.

## Metadata

**Analog search scope:** repositório inteiro (`git ls-files`). Fora de `.planning/` só existe `.claude/CLAUDE.md`.
**Files scanned:** 1 (fora do planning)
**Pattern extraction date:** 2026-10-06
