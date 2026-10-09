---
phase: 01-funda-o-e-n-cleo-de-links
plan: 07
subsystem: docs
tags: [openapi, springdoc, swagger-ui, readme, problem-details, liquibase, licenca, windows]
status: complete

requires:
  - phase: 01-04
    provides: "LinkController/RedirectController, contrato de erros /problems/* e regras de alias"
  - phase: 01-05
    provides: "compose.dev.yaml, profile dev com seed demo-link, Makefile com db-up/db-down/run"
  - phase: 01-06
    provides: "Regras de URL e expiresAt, GlobalExceptionHandler (validation-error com errors[], malformed-request, internal-error)"
provides:
  - "OpenApiConfig (dev.linkpulse.config): @Bean OpenAPI linkPulseOpenApi() com info Link Pulse API 0.1.0"
  - "@Tag/@Operation/@ApiResponse em LinkController (201/400/409) e RedirectController (302/404/410), erros em application/problem+json com schema ProblemDetail"
  - "OpenApiIT: /v3/api-docs (título, responses dos dois paths, sem /actuator), /swagger-ui/index.html 200 e /swagger-ui.html 302"
  - "README.md pt-BR na raiz: pré-requisitos, comandos com e sem make, exemplos curl, regras, tabela de erros, código curto (ofuscação), riscos, CI e licenças"
  - "PROJECT.md: decisão Liquibase 5.0.3 / FSL-1.1-ALv2 registrada"
affects: [phase-02-readme, phase-05-vitrine, verify-work]

plan_head_before: 4bbd8eb52881a25d7b6a5551dae986bca0461124
actuals:
  tokens: 5600
  tasks: 2
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Documentação OpenAPI só por anotações nos controllers; nenhum comportamento ou assinatura muda"
    - "Respostas de erro declaradas com @Content(mediaType = application/problem+json, schema = ProblemDetail)"
    - "Exemplos do README conferidos contra a API rodando (profile dev), não escritos de memória"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/config/OpenApiConfig.java
    - app/src/test/java/dev/linkpulse/OpenApiIT.java
    - README.md
  modified:
    - app/src/main/java/dev/linkpulse/link/LinkController.java
    - app/src/main/java/dev/linkpulse/link/RedirectController.java
    - .planning/PROJECT.md

key-decisions:
  - "O springdoc publica o path do redirect como /{code} (sem a regex); o assert do OpenApiIT usa essa chave, como o plano previa"
  - "OpenApiIT ganhou um teste extra: nenhum path /actuator na spec (reforça T-07-01, Actuator fica na 8081)"
  - "Exemplos do README alinhados à saída real: num banco dev novo o primeiro POST recebe EdRbklq (ID 1 é o seed demo-link), e o validation-error traz instance e a mensagem real do campo url"
  - "Liquibase 5.0.3 (FSL-1.1-ALv2) mantido; a alternativa Apache (4.33.0 por property liquibase.version) fica documentada no README"

requirements-completed: [QUAL-04, CONT-03]

duration: 7min
completed: 2026-10-08
---

# Phase 1 Plan 07: Swagger UI e README pt-BR Summary

**Swagger UI (springdoc 3.1.1) documenta `POST /links` (201/400/409) e `GET /{code}` (302/404/410) com erros em Problem Details. O README pt-BR leva do clone ao link redirecionado no Windows e no Linux, com e sem make, e registra a licença FSL-1.1-ALv2 do Liquibase 5.0.3.**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-10-08T23:22:19Z
- **Completed:** 2026-10-08T23:29Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- `GET /v3/api-docs` → 200 com `info.title = Link Pulse API` e `version = 0.1.0`. `paths['/links'].post.responses` tem `201`, `400` e `409`, e `paths['/{code}'].get.responses` tem `302`, `404` e `410`. Os erros saem como `application/problem+json` com schema `ProblemDetail`. Nenhum path `/actuator` aparece na spec.
- `GET /swagger-ui/index.html` → 200 e `GET /swagger-ui.html` → 302 para `/swagger-ui/index.html`, tanto no MockMvc quanto na API rodando.
- README pt-BR na raiz com as 11 seções do plano. O grep de tópicos da Task 2 passa, e os 6 `type` de erro estão na tabela.
- Prova de runtime dos comandos sem make do README: `docker compose -f compose.dev.yaml up -d --wait` → Healthy; `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`; `curl -i /demo-link` → 302 `Location: https://example.com/` e `Cache-Control: no-store, private`; `POST /links` → 201 `EdRbklq`; `ftp://x` → 400 `validation-error` com `errors[].field = url`; `:8081/actuator/health` → UP. Depois, app encerrada com `taskkill` e `docker compose ... down`.
- `./mvnw -B -ntp verify` completo: Checkstyle 0 violações, 52 testes unitários, 41 ITs (35 anteriores + 6 do `OpenApiIT`), `BugInstance size is 0`, `BUILD SUCCESS`.

## Task Commits

1. **Task 1 (tracer): Swagger UI de ponta a ponta** - `79277c1` (feat). Gate do tracer (`end-of-phase`, verify só automatizado): `-Dit.test=OpenApiIT verify` re-rodado depois do commit, 6/6 verde, antes de seguir para a Task 2.
2. **Task 2: README pt-BR e licença do Liquibase** - `99464b3` (docs)
3. **Ajuste da Task 2: exemplos do README conferidos contra a API rodando** - `e4848c3` (docs)

**Plan metadata:** commit `docs(01-07)` logo após este SUMMARY

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/config/OpenApiConfig.java` - `info` da spec (título, versão, descrição pt-BR com RFC 9457)
- `app/src/main/java/dev/linkpulse/link/LinkController.java` - `@Tag("Links")`, `@Operation` e `@ApiResponse` 201 (header `Location`), 400 e 409
- `app/src/main/java/dev/linkpulse/link/RedirectController.java` - `@Tag("Redirect")`, `@Operation` e `@ApiResponse` 302 (headers `Location` e `Cache-Control`), 404 e 410
- `app/src/test/java/dev/linkpulse/OpenApiIT.java` - 6 ITs da spec e da Swagger UI
- `README.md` - README pt-BR de uso da Phase 1
- `.planning/PROJECT.md` - linha da decisão Liquibase 5.0.3 / FSL-1.1-ALv2 na tabela "Decisões Importantes"

## Decisions Made

- A chave do path do redirect na spec é `/{code}`: o springdoc remove a regex da path variable, como o plano previa. Não foi preciso ajustar o assert.
- Teste extra no `OpenApiIT` para confirmar que a spec não expõe `/actuator` (T-07-01).
- No README, `make` é indicado para Git Bash ou WSL, porque as receitas do `Makefile` usam sintaxe POSIX. No PowerShell, a instrução é usar `.\mvnw.cmd`.
- O README cita o rate limit como Phase 2, que é o que a ROADMAP prevê (critério 5 da Phase 2).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Exemplos do README divergiam da saída real da API**
- **Found during:** Task 2, na prova de runtime dos comandos sem make
- **Issue:** O primeiro exemplo mostrava o código `cJinsNv` (ID 1), mas num banco dev o ID 1 é do seed `demo-link`, e o primeiro POST recebe `EdRbklq`. O exemplo de erro também omitia o campo `instance`, que a API devolve.
- **Fix:** Exemplos substituídos pela saída observada, com uma frase explicando a origem do código. Antes da prova, duas afirmações não verificadas já tinham sido removidas da primeira versão (licença LGPL do Hibernate, que na linha 7 é Apache 2.0, e o `instance`, depois confirmado e recolocado).
- **Files modified:** `README.md`
- **Commit:** `e4848c3`

### Ajustes de ambiente (não alteram o escopo)

**2. [Rule 3 - Blocking] Sem GNU make na máquina**
- **Issue:** O make não está instalado neste Windows.
- **Fix:** Os comandos equivalentes sem make (os mesmos que o README documenta) foram rodados diretamente. O `make -n verify` e o `make help` seguem cobertos pelo CI no Linux.
- **Commit:** n/a

---

**Total deviations:** 1 auto-fixed (Rule 1) e 1 ajuste de ambiente.
**Impact on plan:** nenhum aumento de escopo. O ajuste só deixa o README fiel ao comportamento real.

## Issues Encountered

None.

## Human Verification Pending (end-of-phase)

O `<human-check>` da Task 2 fica para o fim da fase (`human_verify_mode: end-of-phase`): no Git Bash, com o make instalado e o Docker Desktop ligado, seguir o README do zero (`make run`, `curl -i http://localhost:8080/demo-link`, abrir `http://localhost:8080/swagger-ui.html`, criar links com e sem alias pela Swagger UI e depois `make db-down`). A parte automatizável (redirect do demo-link, Swagger UI 200/302, POST 201 e erro 400) já foi conferida na API rodando, sem make.

## Next Phase Readiness

- Com este plano, os 7 planos da Phase 1 estão concluídos, e a fase está pronta para verificação (`/gsd-verify-work`).
- A licença do Liquibase, que era blocker no STATE.md, está registrada no README e no PROJECT.md.
- A Phase 5 (vitrine) estende este README com diagrama, prints e k6. A Phase 2 deve atualizar as seções "Riscos conhecidos" (rate limit) e "Erros" (429).

## Self-Check: PASSED

- FOUND: app/src/main/java/dev/linkpulse/config/OpenApiConfig.java
- FOUND: app/src/test/java/dev/linkpulse/OpenApiIT.java
- FOUND: README.md
- FOUND: commits 79277c1, 99464b3, e4848c3
