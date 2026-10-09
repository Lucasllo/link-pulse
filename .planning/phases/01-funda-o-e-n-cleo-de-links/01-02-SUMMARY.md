---
phase: 01-funda-o-e-n-cleo-de-links
plan: 02
subsystem: api
tags: [spring-data-jpa, liquibase, postgresql-18, testcontainers-2, problem-details, redirect, mockmvctester]

requires:
  - phase: 01-01
    provides: "app/ Maven sobre Boot 4.1.1, gates de Checkstyle/SpotBugs, failsafe para *IT"
provides:
  - "Schema Liquibase: sequência link_id_seq e tabela links (pk_links, uk_links_code), sem context"
  - "Entidade Link (Persistable<Long>, fábrica create, isExpiredAt) e LinkRepository (nextId, findByCode, existsByCode)"
  - "LinkService.resolve(code) com Clock injetado: 404/410 via LinkProblems"
  - "RedirectController GET /{code:[A-Za-z0-9_-]+} → 302 com Cache-Control no-store, private"
  - "LinkProblems (notFound/expired) e ApplicationConfig (bean Clock UTC)"
  - "Infra de ITs: TestcontainersConfiguration (postgres:18.6 @Bean @ServiceConnection) e AbstractIT (MockMvcTester)"
affects: [01-03, 01-04, 01-05, 01-06, phase-02-cache, phase-03-containers]

plan_head_before: 47f876469689d276a46e94263ff2f4042f87c33f
actuals:
  tokens: 4900
  tasks: 2
  commits: 3

tech-stack:
  added: [spring-boot-starter-data-jpa, spring-boot-starter-liquibase, postgresql (runtime), spring-boot-testcontainers, testcontainers-postgresql 2.x, postgres:18.6]
  patterns:
    - "Schema só via Liquibase (YAML, DDL sem context) e Hibernate em ddl-auto=validate"
    - "Entidade com ID atribuído implementa Persistable<Long> (flag @Transient alternada em @PostLoad/@PostPersist)"
    - "Erros de domínio como ErrorResponseException montada por fábrica (LinkProblems), type relativo /problems/<slug>"
    - "Tempo só via bean Clock; nunca Instant.now() no código de produção"
    - "ITs estendem AbstractIT; containers como @Bean @ServiceConnection; cada teste usa códigos próprios"

key-files:
  created:
    - app/src/main/resources/db/changelog/db.changelog-master.yaml
    - app/src/main/resources/db/changelog/changes/001-create-links.yaml
    - app/src/main/java/dev/linkpulse/link/Link.java
    - app/src/main/java/dev/linkpulse/link/LinkRepository.java
    - app/src/main/java/dev/linkpulse/link/LinkService.java
    - app/src/main/java/dev/linkpulse/link/RedirectController.java
    - app/src/main/java/dev/linkpulse/link/LinkProblems.java
    - app/src/main/java/dev/linkpulse/config/ApplicationConfig.java
    - app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java
    - app/src/test/java/dev/linkpulse/AbstractIT.java
    - app/src/test/java/dev/linkpulse/link/RedirectIT.java
  modified:
    - app/pom.xml
    - app/src/main/resources/application.yml

key-decisions:
  - "Cache-Control do 302 fixado em 'no-store, private' (CacheControl.noStore().cachePrivate())"
  - "Expiração no limite exato (now == expiresAt) conta como expirada (410), medida pelo Clock UTC da aplicação, não pelo banco"
  - "nextId() do LinkRepository leva @Transactional de escrita para não herdar o readOnly do SimpleJpaRepository"
  - "spring.mvc.problemdetails.enabled=true provisório até o GlobalExceptionHandler da 01-06"

patterns-established:
  - "Changesets imutáveis: ids 001-create-link-id-seq e 001-create-links-table são contrato"
  - "Testes de rota fora do redirect checam 404 sem o type link-not-found"

requirements-completed: [REDIR-01, REDIR-02, DATA-01]

coverage:
  - id: D1
    description: "GET /{code} de link gravado no Postgres → 302 com Location igual ao alvo e Cache-Control no-store, private"
    requirement: REDIR-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/link/RedirectIT.java#redirectsToTargetWithoutCache"
        status: pass
    human_judgment: false
  - id: D2
    description: "Inexistente → 404 problem+json /problems/link-not-found com code; expirado → 410 /problems/link-expired com code e expiredAt; expiração futura → 302"
    requirement: REDIR-02
    verification:
      - kind: integration
        ref: "RedirectIT#unknownCodeIsNotFoundProblem, #expiredLinkIsGoneProblem, #linkExpiringInTheFutureStillRedirects"
        status: pass
    human_judgment: false
  - id: D3
    description: "Lookup case-sensitive (AbCd-123 → 302, abcd-123 → 404) e /favicon.ico e /a/b fora do controller de redirect"
    requirement: REDIR-01
    verification:
      - kind: integration
        ref: "RedirectIT#lookupIsCaseSensitive, #pathWithDotIsNotRedirectRoute, #multiSegmentPathIsNotRedirectRoute"
        status: pass
    human_judgment: false
  - id: D4
    description: "Schema criado só pelo Liquibase (DATABASECHANGELOG com os 2 changesets), ddl-auto=validate, contexts default prod, analytics desligado"
    requirement: DATA-01
    verification:
      - kind: integration
        ref: "RedirectIT#schemaComesFromLiquibaseChangelog, #hibernateOnlyValidatesSchema; log 'ChangeSet ... ran successfully' x2"
        status: pass
    human_judgment: false
  - id: D5
    description: "./mvnw -B -ntp verify completo verde (Checkstyle 0 violações, 9 ITs, SpotBugs 0) com Docker Desktop local"
    requirement: DATA-01
    verification:
      - kind: integration
        ref: "cd app && ./mvnw -B -ntp verify"
        status: pass
    human_judgment: false

duration: 10min
completed: 2026-10-08
status: complete
---

# Phase 1 Plan 02: Redirect a partir do Postgres Summary

**`GET /{code}` responde 302 (`Cache-Control: no-store, private`) para links gravados no PostgreSQL 18.6, com schema vindo só do Liquibase e Hibernate em `validate`. Código inexistente dá 404 e link expirado dá 410, ambos em `application/problem+json` com `type` próprio. A suíte de ITs roda com Testcontainers 2.x e Postgres declarado como bean `@ServiceConnection`.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-10-08T22:35:54Z
- **Completed:** 2026-10-08T22:46:00Z
- **Tasks:** 2/2
- **Files modified:** 13 (11 criados, 2 alterados)

## Accomplishments

- O Liquibase aplica `001-create-link-id-seq` e `001-create-links-table` na subida, e o Hibernate valida o schema sem erro (`Instant` × `TIMESTAMP WITH TIME ZONE` passa). O IT lê a `DATABASECHANGELOG` e encontra os dois ids.
- `GET /redir-ok1` → 302, `Location` exato e `Cache-Control` com `no-store` e `private`.
- `GET /nao-existe1` → 404 `{"type":"/problems/link-not-found","title":"Link não encontrado","status":404,"code":"nao-existe1",...}`. Link expirado há 1 h → 410 com `/problems/link-expired`, `code` e `expiredAt` em ISO-8601. Link que expira daqui a 1 h → 302.
- O lookup diferencia maiúsculas: `/AbCd-123` → 302 e `/abcd-123` → 404. `/favicon.ico` e `/a/b` → 404 sem o type `link-not-found`, porque a regex do path não aceita `.` nem `/`.
- `./mvnw -B -ntp verify` completo: 0 violações de Checkstyle, 9/9 ITs, `BugInstance size is 0`, `BUILD SUCCESS`.

## Task Commits

1. **Task 1 (tracer): Seguir um link gravado no Postgres** - `931d2ae` (feat)
2. **Task 2 (TDD): 404/410 em Problem Details, Clock e rotas**
   - RED - `4731561` (test)
   - GREEN - `0c61827` (feat)
   - REFACTOR: não houve; o código já saiu limpo.

**Plan metadata:** commit `docs(01-02)` logo após este SUMMARY

## Files Created/Modified

- `app/pom.xml`: `spring-boot-starter-data-jpa`, `spring-boot-starter-liquibase`, `postgresql` (runtime), `spring-boot-testcontainers` e `testcontainers-postgresql` (test). Nenhuma versão declarada; tudo vem do BOM 4.1.1.
- `app/src/main/resources/application.yml`: `ddl-auto: validate`, `open-in-view: false`, `change-log`, `contexts: prod`, `analytics-enabled: false`, `problemdetails.enabled: true`. Sem URL de datasource.
- `db/changelog/db.changelog-master.yaml`: um `include` relativo de `changes/001-create-links.yaml`.
- `db/changelog/changes/001-create-links.yaml`: sequência `link_id_seq` e tabela `links` (`id BIGINT` com `pk_links`, `code VARCHAR(32)` com `uk_links_code`, `target_url VARCHAR(2048)`, `expires_at`/`created_at` `TIMESTAMP WITH TIME ZONE`, com `created_at` default `now()`). Sem context.
- `link/Link.java`: entidade `Persistable<Long>` com fábrica `create` e `isExpiredAt`.
- `link/LinkRepository.java`: `nextId()` (`@Transactional` + `nextval('link_id_seq')`), `findByCode`, `existsByCode`.
- `link/LinkService.java`: `resolve(code)` com `Clock`, que lança `LinkProblems.notFound`/`expired`.
- `link/RedirectController.java`: `GET /{code:[A-Za-z0-9_-]+}` → 302 sem cache.
- `link/LinkProblems.java`: fábrica de `ErrorResponseException` (404/410) com `detail` em pt-BR.
- `config/ApplicationConfig.java`: bean `Clock.systemUTC()`.
- Testes: `TestcontainersConfiguration` (postgres:18.6), `AbstractIT` (`MockMvcTester`) e `RedirectIT` (9 testes, 127 linhas).

## TDD Gate Compliance

- RED `4731561` → GREEN `0c61827`, nessa ordem.
- Evidência RED (comando do `<verify>`, exit 1, failsafe convertido em TAP) validada com `gsd-tools check tdd-red-evidence`:
  - `unknownCodeIsNotFoundProblem`: `RED_EVIDENCE_OK` (`target_test_failed`). Antes da implementação, o corpo era `{"instance":"/nao-existe1","status":404,"title":"Not Found"}`, sem `type`.
  - `expiredLinkIsGoneProblem`: `RED_EVIDENCE_OK` (`target_test_failed`). Antes da implementação, a resposta era `expected: 410 but was: 302`.
  - `lookupIsCaseSensitive` também falhou no RED, porque o `$.code` estava ausente no 404. Os testes de expiração futura, `/favicon.ico` e `/a/b` já passavam no RED, como esperado: o comportamento deles vem da Task 1 (regex e ausência de checagem de expiração). Eles servem de guarda de regressão.
- A primeira tentativa de RED quebrou no Checkstyle (linha com 101 caracteres e `AbbreviationAsWordInName` em `...NotARedirectRoute`), e por isso não contou como RED. Corrigi o estilo antes de gerar a evidência válida.

## Decisions Made

- `Cache-Control: no-store, private` no 302. Foi a opção registrada no plano (Claude's Discretion).
- Expiração conta a partir do limite inclusivo (`!now.isBefore(expiresAt)`) e usa o relógio da aplicação.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Assinatura de `Link.create` quebrada em duas linhas**
- **Found during:** Task 1
- **Issue:** Com a assinatura do plano numa linha só, a linha ficava com 109 caracteres e violava o `LineLength` (máximo 100), o que derrubava o build em `validate`.
- **Fix:** Parâmetros na linha seguinte, com indentação de continuação de 8. A assinatura pública não mudou.
- **Files modified:** `app/src/main/java/dev/linkpulse/link/Link.java`
- **Commit:** `931d2ae`

**2. [Rule 1 - Bug] Nomes de teste sem `ARedirect`**
- **Found during:** Task 2 (RED)
- **Issue:** `pathWithDotIsNotARedirectRoute` violava o `AbbreviationAsWordInName` (2 maiúsculas seguidas).
- **Fix:** Renomeei para `pathWithDotIsNotRedirectRoute` e `multiSegmentPathIsNotRedirectRoute`.
- **Files modified:** `app/src/test/java/dev/linkpulse/link/RedirectIT.java`
- **Commit:** `4731561`

---

**Total deviations:** 2 auto-fixed (ambos de estilo exigido pelo Checkstyle)
**Impact on plan:** Nenhum. Contratos e critérios de aceite seguem literais.

## Issues Encountered

- Docker Desktop já estava ligado (Engine 29.8.0), então não houve checkpoint. O primeiro IT levou ~67 s por causa do pull de `postgres:18.6`; as execuções seguintes levaram ~16 s.
- O surefire roda 0 testes unitários (ainda não existe nenhum `*Test`). Isso é esperado; a 01-03 traz os primeiros.

## Known Stubs

Nenhum.

## Next Phase Readiness

- A 01-03 pode acrescentar o `CodeGenerator` ao `ApplicationConfig` e usar `LinkRepository.nextId()`.
- A 01-04 já tem `existsByCode` e `Link.create` para o `POST /links`.
- A 01-05 sobrescreve `spring.liquibase.contexts` no profile dev e adiciona o seed `contextFilter: dev`. O DATA-01 só fica completo no REQUIREMENTS depois da 01-05, que também o declara.
- A 01-06 troca `spring.mvc.problemdetails.enabled` pelo `GlobalExceptionHandler`. `LinkProblems` continua valendo, porque devolve `ErrorResponseException`.

---
*Phase: 01-funda-o-e-n-cleo-de-links*
*Completed: 2026-10-08*

## Self-Check: PASSED

- 11/11 arquivos criados encontrados; commits 931d2ae, 4731561 e 0c61827 presentes no histórico.
