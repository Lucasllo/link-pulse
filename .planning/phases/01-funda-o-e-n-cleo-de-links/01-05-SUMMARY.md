---
phase: 01-funda-o-e-n-cleo-de-links
plan: 05
subsystem: infra
tags: [compose, postgres-18, liquibase, contexts, seed, makefile, profiles, testcontainers]
status: complete

requires:
  - phase: 01-03
    provides: "Tabela links + link_id_seq (001-create-links.yaml), RedirectController GET /{code} 302, LinkRepository.findByCode, AbstractIT/TestcontainersConfiguration, application.yml com contexts=prod default"
provides:
  - "compose.dev.yaml: serviço postgres (postgres:18.6, 127.0.0.1:5432, volume pgdata:/var/lib/postgresql, healthcheck pg_isready)"
  - "application-dev.yml: datasource jdbc:postgresql://localhost:5432/linkpulse (linkpulse/linkpulse) e spring.liquibase.contexts=dev"
  - "application-prod.yml: spring.liquibase.contexts=prod explícito"
  - "changeSet 002-seed-dev-demo-link (contextFilter: dev) → links(code=demo-link, target_url=https://example.com/)"
  - "Makefile: COMPOSE := docker compose -f compose.dev.yaml, alvos db-up/db-down, run: db-up"
  - "LiquibaseContextsIT: prova de contexts default/prod/dev"
affects: [01-07, phase-03-compose, phase-04-kind, phase-05-terraform]

plan_head_before: f18b0567eb1a8ad2b9d328d252d2f4e1b2ee941a
actuals:
  tokens: 2000
  tasks: 1
  commits: 1

tech-stack:
  added: []
  patterns:
    - "Seed de demonstração em changeset próprio com contextFilter: dev; DDL nunca leva context"
    - "contexts do Liquibase explícito em cada profile (default prod no application.yml, prod no application-prod.yml, dev no application-dev.yml)"
    - "IT com @Nested @ActiveProfiles: cada classe aninhada sobe contexto Spring e container Postgres próprios; o @ServiceConnection vence a URL localhost do profile"
    - "Banco de dev publicado só no loopback (127.0.0.1:5432)"

key-files:
  created:
    - compose.dev.yaml
    - app/src/main/resources/application-dev.yml
    - app/src/main/resources/application-prod.yml
    - app/src/main/resources/db/changelog/changes/002-seed-dev.yaml
    - app/src/test/java/dev/linkpulse/LiquibaseContextsIT.java
  modified:
    - app/src/main/resources/db/changelog/db.changelog-master.yaml
    - Makefile

key-decisions:
  - "O seed demo-link é um changeset separado (002-seed-dev.yaml) com contextFilter: dev; 001-create-links.yaml fica intocado (changesets aplicados são imutáveis)"
  - "LiquibaseContextsIT cobre três cenários: classe externa (default prod do application.yml), @Nested ProdProfile e @Nested DevProfile, e confere tanto o link quanto a linha da DATABASECHANGELOG"

patterns-established:
  - "Comando equivalente sem make documentado no topo do Makefile: docker compose -f compose.dev.yaml up -d --wait (e down)"

requirements-completed: [DATA-01, CONT-03]

coverage:
  - id: D1
    description: "Com o context default prod (application.yml), demo-link não existe e 002-seed-dev-demo-link não está na DATABASECHANGELOG"
    requirement: DATA-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/LiquibaseContextsIT.java#defaultContextHasNoSeedLink"
        status: pass
      - kind: integration
        ref: "LiquibaseContextsIT#defaultContextDoesNotRunSeedChangeset"
        status: pass
    human_judgment: false
  - id: D2
    description: "Com o profile prod (application-prod.yml), mesmos asserts: sem seed e sem changeset"
    requirement: DATA-01
    verification:
      - kind: integration
        ref: "LiquibaseContextsIT$ProdProfile#prodProfileHasNoSeedLink"
        status: pass
      - kind: integration
        ref: "LiquibaseContextsIT$ProdProfile#prodProfileDoesNotRunSeedChangeset"
        status: pass
    human_judgment: false
  - id: D3
    description: "Com o profile dev, demo-link existe (target https://example.com/), o changeset está registrado e GET /demo-link responde 302 com Location https://example.com/"
    requirement: DATA-01
    verification:
      - kind: integration
        ref: "LiquibaseContextsIT$DevProfile#devProfileSeedsDemoLink"
        status: pass
      - kind: integration
        ref: "LiquibaseContextsIT$DevProfile#devProfileRegistersSeedChangeset"
        status: pass
      - kind: integration
        ref: "LiquibaseContextsIT$DevProfile#demoLinkRedirects"
        status: pass
    human_judgment: false
  - id: D4
    description: "Runtime local: docker compose -f compose.dev.yaml up -d --wait + jar com --spring.profiles.active=dev → GET http://localhost:8080/demo-link 302 Location https://example.com/; compose down preserva o volume pgdata"
    requirement: CONT-03
    verification:
      - kind: command
        ref: "verify #2 do plano (compose up, java -jar --spring.profiles.active=dev, curl /demo-link → app/target/demo-headers.txt)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Makefile com COMPOSE, db-up (up -d --wait), db-down (down) e run: db-up; compose só no loopback e volume em /var/lib/postgresql"
    requirement: CONT-03
    verification:
      - kind: command
        ref: "verify #3 do plano (grep em Makefile e compose.dev.yaml)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Execução real dos alvos make db-up/make db-down/make run no Git Bash"
    requirement: CONT-03
    human_judgment: true
    rationale: "GNU make não está instalado nesta máquina; os comandos subjacentes (docker compose ... up -d --wait / down) foram executados e passaram, mas os alvos make em si não rodaram"

duration: 4min
completed: 2026-10-08
---

# Phase 1 Plan 05: Runtime local com Postgres do compose e seed dev Summary

**Postgres 18.6 local via `compose.dev.yaml` (só loopback, volume em `/var/lib/postgresql`), profile dev com seed `demo-link` por `contextFilter: dev`, alvos `db-up`/`db-down` no Makefile e `LiquibaseContextsIT` provando que o seed não existe no context prod.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-10-08T23:05:21Z
- **Completed:** 2026-10-08T23:09:37Z
- **Tasks:** 1 (tracer)
- **Files modified:** 7 (5 criados, 2 modificados)

## Accomplishments

- `compose.dev.yaml` com `postgres:18.6`, porta `127.0.0.1:5432:5432`, volume nomeado `pgdata` em `/var/lib/postgresql` (PGDATA do Postgres 18) e healthcheck `pg_isready`.
- `application-dev.yml` (datasource local, `contexts: dev`) e `application-prod.yml` (`contexts: prod` explícito).
- Changeset `002-seed-dev-demo-link` com `contextFilter: dev`, `id` via `nextval('link_id_seq')`, incluído no master depois do `001`.
- Makefile: `COMPOSE := docker compose -f compose.dev.yaml`, `db-up` (`up -d --wait`), `db-down` (`down`, mantém volume), `run: db-up`; comentário com o comando equivalente sem make.
- `LiquibaseContextsIT`: 7 testes (2 no default prod, 2 no `@Nested ProdProfile`, 3 no `@Nested DevProfile`, incluindo o redirect 302).

## Task Commits

1. **Task 1: Rodar local com dados de demo (tracer)** - `63b86a3` (feat)

## Files Created/Modified

- `compose.dev.yaml` - Postgres 18.6 local com healthcheck, só no loopback
- `app/src/main/resources/application-dev.yml` - datasource local e `contexts: dev`
- `app/src/main/resources/application-prod.yml` - `contexts: prod` explícito
- `app/src/main/resources/db/changelog/changes/002-seed-dev.yaml` - seed `demo-link` só no context dev
- `app/src/main/resources/db/changelog/db.changelog-master.yaml` - segundo `include`
- `Makefile` - `COMPOSE`, `db-up`, `db-down`, `run: db-up`
- `app/src/test/java/dev/linkpulse/LiquibaseContextsIT.java` - prova dos contexts dev/prod

## Verification

- `./mvnw -B -ntp -Dtest=NoUnit -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=LiquibaseContextsIT verify`: BUILD SUCCESS, `Tests run: 7, Failures: 0` (o relatório do failsafe confirma os 7 casos atribuídos a `LiquibaseContextsIT`, `$ProdProfile` e `$DevProfile`).
- Prova de runtime: compose saudável, API saudável em 5 s, `app/target/demo-headers.txt` com `HTTP/1.1 302` e `Location: https://example.com/` (mais `Cache-Control: no-store, private`). Depois do `docker compose ... down`, o volume `projeto4_pgdata` continuava listado (preservado).
- Grep do Makefile/compose (verify #3): PASS.
- `./mvnw -B -ntp verify` completo: 36 testes unitários e 27 ITs verdes, Checkstyle OK, SpotBugs com `BugInstance size is 0`.
- Gate do tracer: `human_verify_mode=end-of-phase` e `<verify>` só automatizado; os comandos foram reexecutados e passaram, sem checkpoint.

## Decisions Made

- Seed em changeset separado com `contextFilter: dev`; o `001-create-links.yaml` não foi alterado.
- O IT confere o link (repositório) e também a linha na `DATABASECHANGELOG`, para distinguir "changeset não rodou" de "dado apagado depois".

## Deviations from Plan

### Ajustes de ambiente (não alteram o escopo)

**1. [Rule 3 - Blocking] Encerramento do java.exe com taskkill no lugar de `kill $PID`**
- **Found during:** Task 1 (verify #2)
- **Issue:** No Git Bash do Windows, `kill $!` não encerra o `java.exe` iniciado em background.
- **Fix:** A prova de runtime pegou o PID Windows de quem escutava na 8080 (`netstat -ano`) e encerrou com `taskkill //PID <pid> //F`. Os demais passos do comando de verify ficaram iguais.
- **Files modified:** nenhum (só o comando de verificação)
- **Commit:** n/a

**2. [Rule 1 - Bug] Linha com 102 caracteres no IT reprovada pelo Checkstyle**
- **Found during:** Task 1 (primeira execução do verify)
- **Fix:** Argumentos do `queryForObject` quebrados em várias linhas antes do commit.
- **Files modified:** `app/src/test/java/dev/linkpulse/LiquibaseContextsIT.java`
- **Commit:** `63b86a3`

**Total deviations:** 2 auto-fixed (1 blocking de ambiente, 1 bug de estilo). **Impact:** nenhum no escopo.

## Issues Encountered

- GNU make não está instalado: `make db-up`/`make db-down`/`make run` não rodaram literalmente. Os comandos subjacentes (`docker compose -f compose.dev.yaml up -d --wait` e `down`) foram executados e passaram. A instrução de instalação do make no README é da 01-07.
- Limpeza pós-verificação: depois de confirmar que o `down` preservou o volume, removi `projeto4_pgdata` (`docker volume rm`) para não deixar estado no ambiente. As portas 5432/8080/8081 ficaram livres.
- Os WARN `duplicate key value violates unique constraint "uk_links_code"` no verify completo vêm do IT de corrida da 01-04 (esperados).

## Known Stubs

Nenhum.

## Threat Flags

Nenhuma superfície nova além do threat model: T-05-02 mitigada (`127.0.0.1:5432:5432`, conferido por grep) e T-05-03 mitigada (`contextFilter: dev` + contexts prod default/explícito, provado pelo IT). T-05-01 (credenciais `linkpulse` de dev) aceita.

## Next Phase Readiness

- A 01-07 pode documentar no README: `make run` / `make db-up` / `make db-down`, os comandos equivalentes sem make, o `curl -i http://localhost:8080/demo-link` e a natureza "só dev" das credenciais.
- Ready for 01-06 / 01-07.

## Self-Check: PASSED

- FOUND: compose.dev.yaml, application-dev.yml, application-prod.yml, 002-seed-dev.yaml, LiquibaseContextsIT.java
- FOUND: commit 63b86a3
- commits medidos: `git rev-list --count f18b056..HEAD` = 1
