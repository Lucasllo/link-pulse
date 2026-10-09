---
phase: 01-funda-o-e-n-cleo-de-links
plan: 06
subsystem: api
tags: [validacao, bean-validation, problem-details, rfc-9457, url-allow-list, seguranca, controller-advice]
status: complete

requires:
  - phase: 01-04
    provides: "LinkProblems (notFound/expired/aliasConflict/invalidField), LinkService(LinkRepository, CodeGenerator, AliasPolicy, Clock), LinkApiIT com @TestMethodOrder"
  - phase: 01-03
    provides: "CreateLinkRequest, LinkPulseProperties.baseUrl, LinkController"
provides:
  - "@HttpUrl + HttpUrlValidator (dev.linkpulse.common.validation): URI estrita, http/https, host obrigatório, sem userinfo"
  - "GlobalExceptionHandler (dev.linkpulse.common, extends ResponseEntityExceptionHandler): validation-error com errors[], malformed-request e internal-error"
  - "CreateLinkRequest: url @NotBlank @Size(max = 2048) @HttpUrl; expiresAt @Future"
  - "LinkService(LinkRepository, CodeGenerator, AliasPolicy, LinkPulseProperties, Clock): rejeita destino com o host do linkpulse.base-url"
  - "application.yml sem o handler de Problem Details do Boot"
affects: [01-07, phase-02-cache, k6, smoke-test]

plan_head_before: e650dd5a834959f0c9505aa99c868fce8ce5f59f
actuals:
  tokens: 5800
  tasks: 2
  commits: 4

tech-stack:
  added: []
  patterns:
    - "Advice próprio estendendo ResponseEntityExceptionHandler: só sobrescreve os casos em que o contrato muda o corpo; ErrorResponseException e exceções do MVC seguem nos handlers herdados"
    - "@ExceptionHandler(Exception.class) como rede final: 500 genérico, stack só no log, sem request/corpo"
    - "Detail fixo em erros de parse: ex.getMessage() nunca vai para o cliente"
    - "Constraint customizada com @Target FIELD só, para não validar em dobro em componente de record"
    - "IT de falha inesperada em @Nested com @MockitoBean e MockMvcTester próprio (contexto separado)"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/common/validation/HttpUrl.java
    - app/src/main/java/dev/linkpulse/common/validation/HttpUrlValidator.java
    - app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java
    - app/src/test/java/dev/linkpulse/common/validation/HttpUrlValidatorTest.java
  modified:
    - app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java
    - app/src/main/java/dev/linkpulse/link/LinkService.java
    - app/src/main/resources/application.yml
    - app/src/test/java/dev/linkpulse/link/LinkApiIT.java

key-decisions:
  - "URL com userinfo (user:senha@host) é rejeitada no @HttpUrl: credenciais na URL são truque comum de phishing (https://banco.com@evil.com)"
  - "Suposição A2 confirmada: o Jackson 3 do Boot 4.1.1 rejeita OffsetDateTime sem offset com HttpMessageNotReadableException; o DTO segue tipado como OffsetDateTime, sem a contingência String + @Pattern"
  - "Rejeição do próprio host fica no LinkService (não no validador), comparando sem caixa com linkpulse.base-url, e devolve o mesmo 400 validation-error com errors[].field=url"
  - "Handler do Boot (spring.mvc.problemdetails) desligado: o GlobalExceptionHandler é o único ResponseEntityExceptionHandler ativo"

requirements-completed: [LINK-01, LINK-05, LINK-06]

duration: 8min
completed: 2026-10-08
---

# Phase 1 Plan 06: Validação de URL e expiração com Problem Details centralizado Summary

**`@HttpUrl` com allow-list http/https, host obrigatório e sem userinfo, `@Future` na expiração, bloqueio do próprio host no `LinkService` e um `GlobalExceptionHandler` que devolve todo erro como Problem Details (`validation-error` com `errors[]`, `malformed-request` e um 500 genérico sem vazamento).**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-10-08T23:12:11Z
- **Completed:** 2026-10-08T23:19:44Z
- **Tasks:** 2 (cada uma com commit RED e GREEN)
- **Files modified:** 8 (4 criados, 4 modificados)

## Accomplishments

- `POST /links` só aceita URL `http`/`https` absoluta, com host, sem credenciais e com até 2048 caracteres. `ftp://`, `javascript:`, `mailto:`, `data:`, `file:`, URL sem host, sintaxe inválida e `user:pass@` respondem 400 `/problems/validation-error` com `errors[].field = url`. Uma URL de exatamente 2048 caracteres dá 201, e uma de 2049 dá 400.
- Destino com o host do `linkpulse.base-url` (`http://LOCALHOST:8080/qualquer`) responde 400 no campo `url`. Isso fecha o loop de redirect (T-06-03).
- `expiresAt` no passado responde 400 no campo `expiresAt`. Sem offset (`2030-01-01T00:00:00`) ou com JSON malformado, a resposta é 400 `/problems/malformed-request` com detail fixo em pt-BR, sem texto do parser.
- Exceção inesperada vira 500 `/problems/internal-error` com detail genérico. O IT confirma que `falha`, `uk_links_code`, `RuntimeException` e `SQL` não aparecem no corpo.
- O advice próprio substituiu o handler do Boot sem mudar o contrato anterior: 404 `link-not-found`, 410 `link-expired`, 409 `alias-conflict` e 400 de alias continuam iguais (`RedirectIT` 9/9 e os 13 cenários anteriores do `LinkApiIT` verdes).
- `./mvnw -B -ntp verify` completo: Checkstyle 0 violações; 52 testes unitários (HttpUrlValidatorTest 16, AliasPolicyTest 17, CodeGeneratorTest 19); 35 ITs (LinkApiIT 18, incluindo o `@Nested`, RedirectIT 9, ConcurrencyIT 1, LiquibaseContextsIT 7); SpotBugs 0.

## Task Commits

1. **Task 1 (tracer): URL inválida de ponta a ponta**
   - RED `8eb19a9`: `test(01-06): URL fora da allow-list, longa demais ou com credenciais vira 400 com errors[]`
   - GREEN `2282686`: `feat(01-06): @HttpUrl e GlobalExceptionHandler com errors[] em Problem Details`
2. **Task 2 (tdd): expiração, próprio host, JSON malformado e 500 genérico**
   - RED `11e2007`: `test(01-06): próprio host, expiração passada, JSON malformado e 500 em Problem Details`
   - GREEN `7d2efa3`: `feat(01-06): expiração futura, rejeição do próprio host, malformed-request e 500 genérico`

Nenhuma task precisou de commit de REFACTOR.

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/common/validation/HttpUrl.java`: anotação de Bean Validation, `@Target(FIELD)` só.
- `app/src/main/java/dev/linkpulse/common/validation/HttpUrlValidator.java`: `new URI` estrito, esquema http/https sem diferenciar caixa, `getHost()` não vazio e `getUserInfo() == null`.
- `app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java`: `handleMethodArgumentNotValid` (errors[] ordenado por field e message), `handleHttpMessageNotReadable` (detail fixo) e `handleUnexpected` (500 com log ERROR).
- `app/src/test/java/dev/linkpulse/common/validation/HttpUrlValidatorTest.java`: 16 casos, rodam sem Docker.
- `app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java`: `@HttpUrl` em `url` e `@Future` em `expiresAt`; assinatura e `@Schema` iguais.
- `app/src/main/java/dev/linkpulse/link/LinkService.java`: construtor recebe `LinkPulseProperties`; `create` começa por `pointsToShortener`.
- `app/src/main/resources/application.yml`: saiu o bloco `spring.mvc` do handler de Problem Details do Boot.
- `app/src/test/java/dev/linkpulse/link/LinkApiIT.java`: 8 cenários novos (3 de URL, próprio host, expiração passada, sem offset, JSON malformado e o `@Nested UnexpectedFailure`) e os helpers `assertProblem`/`assertValidationErrorOn`.

## Decisions Made

- **Userinfo rejeitado (discricionariedade):** `https://user:pass@example.com` e `https://banco.com@evil.com/login` são inválidos. Credencial em URL encurtada é vazamento ou phishing; não existe caso legítimo para um encurtador público.
- **Suposição A2 confirmada:** o Jackson 3 (Boot 4.1.1) recusa `2030-01-01T00:00:00` para `OffsetDateTime`. O 400 sai pelo `handleHttpMessageNotReadable` como `malformed-request`, e a contingência (`String` + `@Pattern`) não foi aplicada. Já no RED dava para ver o 400, ainda com `type` padrão.
- **Próprio host no service:** o validador não conhece a configuração. A regra fica no `LinkService` e usa o mesmo `LinkProblems.invalidField("url", ...)`, então o cliente recebe um formato único de erro. O `pointsToShortener` aceita host nulo para não quebrar em chamada direta ao service.
- **Logger do advice só na Task 2:** a classe ganhou o logger SLF4J (`LOG`) junto com o `handleUnexpected`, que é o único ponto que loga. Assim nenhum campo fica sem uso entre as tasks.

## Deviations from Plan

### Ajustes de processo

**1. [Rule 3 - Blocking] Esqueleto do validador no commit RED da Task 1**
- **Found during:** Task 1 (RED)
- **Issue:** sem `HttpUrl`/`HttpUrlValidator`, o `HttpUrlValidatorTest` não compila, e falha de compilação é INVALID_RED pela regra da tdd.md.
- **Fix:** o commit RED leva a anotação completa e um validador que devolve sempre `true`. Assim o RED falhou nas asserções certas: 11 rejeições com "Expecting value to be false but was true", e as 5 aceitações passando.
- **Commit:** `8eb19a9`

**2. [Rule 2 - Cobertura] Casos extras no HttpUrlValidatorTest**
- Além dos casos do plano, entraram `file:///etc/passwd` (T-06-01) e `https://banco.com@evil.com/login` (T-06-02, o exemplo do threat model). O cenário de 500 também confere a ausência de `SQL` no corpo.

Fora isso, o plano foi executado como escrito.

## TDD Gate Compliance

| Task | RED | GREEN | Evidência do RED |
|------|-----|-------|------------------|
| 1 | `8eb19a9` | `2282686` | `./mvnw -B -ntp -Dtest=HttpUrlValidatorTest test`: exit 1, 16 testes / 11 falhas por asserção (`rejects*`). `-Dit.test=LinkApiIT verify`: exit 1, `urlOutsideTheAllowList...` (esperado 400, veio 201) e `urlLongerThan2048...` (esperado `/problems/validation-error`, veio o `type` padrão). |
| 2 | `11e2007` | `7d2efa3` | `-Dit.test='LinkApiIT*' verify`: exit 1, 18 testes / 5 falhas, só nos cenários novos: próprio host (esperado 400, veio 201), passado (esperado 400, veio 201), sem offset e malformado (esperado `/problems/malformed-request`, veio o padrão), 500 (`RuntimeException` não tratada). Os 13 cenários anteriores passaram. |

O verificador `gsd_run check tdd-red-evidence` interpreta só saída TAP (node:test) e não lê relatórios do Surefire/Failsafe. Por isso a evidência acima foi registrada à mão, sem forjar TAP.

## Issues Encountered

Nenhum. O gate do tracer (Task 1; modo interativo, `human_verify_mode: end-of-phase`, verify só automatizado) reexecutou o verify de ponta a ponta, que passou (LinkApiIT 13, RedirectIT 9, SpotBugs 0), e a expansão seguiu.

## Known Stubs

Nenhum. O esqueleto que aceitava tudo existiu só no commit RED `8eb19a9` e foi trocado pela implementação no `2282686`.

## Threat Model Coverage

T-06-01 a T-06-06 mitigados como planejado, cada um com teste (unitário ou IT). T-06-07 (open redirect inerente ao produto) é `accept`; a documentação no README fica com a 01-07. Não surgiu superfície nova fora do threat model.

## User Setup Required

Nenhum.

## Next Phase Readiness

- O contrato de erros da fase está completo: `validation-error`, `malformed-request`, `internal-error`, `link-not-found`, `link-expired` e `alias-conflict`. O README da 01-07 pode documentá-lo como está.
- Quem construir `LinkService` à mão precisa da nova assinatura de 5 argumentos (hoje ninguém faz isso; o Spring injeta).

## Self-Check: PASSED

- FOUND: app/src/main/java/dev/linkpulse/common/validation/HttpUrl.java
- FOUND: app/src/main/java/dev/linkpulse/common/validation/HttpUrlValidator.java
- FOUND: app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java
- FOUND: app/src/test/java/dev/linkpulse/common/validation/HttpUrlValidatorTest.java
- FOUND commits: 8eb19a9, 2282686, 11e2007, 7d2efa3
