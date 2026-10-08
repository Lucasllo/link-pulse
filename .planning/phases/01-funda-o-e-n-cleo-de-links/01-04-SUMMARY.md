---
phase: 01-funda-o-e-n-cleo-de-links
plan: 04
subsystem: api
tags: [alias, regex, reservados, problem-details, rfc-9457, unique-constraint, corrida, testcontainers]

requires:
  - phase: 01-03
    provides: "CodeGenerator (alfabeto/M, SPACE), LinkPulseProperties, ApplicationConfig, LinkService.create com nextval, LinkApiIT"
provides:
  - "AliasPolicy: ALIAS_REGEX ^(?=.*[-_])[A-Za-z0-9_-]{4,32}$, reservados case-insensitive (Locale.ROOT), matchesFormat/isReserved/check"
  - "LinkProblems.aliasConflict(alias) → 409 /problems/alias-conflict com a propriedade alias"
  - "LinkProblems.invalidField(field, message) → 400 /problems/validation-error com errors[{field, message}]"
  - "LinkPulseProperties.alias() → Alias(List<String> reserved) com @DefaultValue; linkpulse.alias.reserved no application.yml (env LINKPULSE_ALIAS_RESERVED)"
  - "@Bean AliasPolicy no ApplicationConfig"
  - "LinkService(LinkRepository, CodeGenerator, AliasPolicy, Clock): alias validado, existsByCode → 409 e violação de uk_links_code traduzida em 409 (corrida)"
affects: [01-05, 01-06, 01-07, phase-02-cache]

plan_head_before: 0767a3d3ab548bbb80689419456366286489c5c8
actuals:
  tokens: 7100
  tasks: 1
  commits: 1

tech-stack:
  added: []
  patterns:
    - "Regras de domínio da requisição lançam LinkProblems.invalidField (400 validation-error com errors[]) no mesmo formato que o Bean Validation da 01-06 vai usar"
    - "Duplicata detectada em dois pontos: checagem prévia (existsByCode) e tradução da violação da constraint pelo nome (uk_links_code) ou SQLSTATE 23505, percorrendo a cadeia de causas"
    - "Componente de @ConfigurationProperties opcional com @DefaultValue + construtor compacto que normaliza null para List.of()"
    - "ITs que compartilham estado no banco usam @TestMethodOrder e garantem as próprias pré-condições para rodar isolados"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/link/AliasPolicy.java
    - app/src/test/java/dev/linkpulse/link/AliasPolicyTest.java
  modified:
    - app/src/main/java/dev/linkpulse/link/LinkProblems.java
    - app/src/main/java/dev/linkpulse/link/LinkService.java
    - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
    - app/src/main/java/dev/linkpulse/config/ApplicationConfig.java
    - app/src/main/resources/application.yml
    - app/src/test/java/dev/linkpulse/link/LinkApiIT.java

key-decisions:
  - "isCodeConflict reconhece a corrida pelo nome da constraint (org.hibernate.exception.ConstraintViolationException.getConstraintName() == uk_links_code) ou, como fallback, SQLException com SQLSTATE 23505 e mensagem contendo uk_links_code; qualquer outra DataIntegrityViolationException é relançada (500)"
  - "Os cenários de minha-promo no LinkApiIT rodam em ordem (@Order 1-3), mas (f) e (h) recriam minha-promo antes de agir, para passarem também isolados"

patterns-established:
  - "Alias case-sensitive no armazenamento e no lookup; só a comparação com reservados ignora a caixa (Locale.ROOT)"
  - "Corpo de erro de alias nunca ecoa SQL, nome de constraint nem mensagem de exceção; o IT confere a ausência de uk_links_code, SQL e Exception"

requirements-completed: [LINK-04, LINK-06]

coverage:
  - id: D1
    description: "Alias válido vira o code (201, shortUrl http://localhost:8080/minha-promo) e GET /minha-promo responde 302 para o destino"
    requirement: LINK-04
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/link/LinkApiIT.java#aliasBecomesTheCodeAndRedirects"
        status: pass
    human_judgment: false
  - id: D2
    description: "Alias repetido → 409 application/problem+json, type /problems/alias-conflict, alias=minha-promo, sem uk_links_code/SQL/Exception no corpo"
    requirement: LINK-06
    verification:
      - kind: integration
        ref: "LinkApiIT#repeatedAliasIsConflictWithoutInternalDetails"
        status: pass
    human_judgment: false
  - id: D3
    description: "10 threads criando corrida-alias ao mesmo tempo → exatamente 1 sucesso e 9 ErrorResponseException 409, nenhuma outra exceção (a violação de uk_links_code apareceu no log e foi traduzida)"
    requirement: LINK-04
    verification:
      - kind: integration
        ref: "LinkApiIT#concurrentCreationsOfTheSameAliasYieldOneSuccessAndConflicts"
        status: pass
    human_judgment: false
  - id: D4
    description: "Minha-Promo e minha-promo são links distintos, cada um redireciona para o próprio destino (D-06)"
    requirement: LINK-04
    verification:
      - kind: integration
        ref: "LinkApiIT#aliasIsCaseSensitive"
        status: pass
    human_judgment: false
  - id: D5
    description: "Swagger-UI (reservado em outra caixa) e abcd (sem - ou _) → 400 /problems/validation-error com errors[0].field = alias"
    requirement: LINK-06
    verification:
      - kind: integration
        ref: "LinkApiIT#reservedAliasIsValidationErrorIgnoringCase, #aliasWithoutHyphenOrUnderscoreIsValidationError"
        status: pass
    human_judgment: false
  - id: D6
    description: "AliasPolicy: aceitos minha-promo/black_friday/ab-c/32 chars; rejeitados abc/abcd/a b-c/ação-1/33 chars/abc.d-e/vazio; reservados Swagger-UI/API-DOCS/swagger-ui; check → 400 com errors[0].field=alias; nenhum código gerado em 1..200000 e SPACE-10000..SPACE-1 casa com ALIAS_REGEX"
    requirement: LINK-04
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/link/AliasPolicyTest.java (17 casos)"
        status: pass
    human_judgment: false
  - id: D7
    description: "./mvnw -B -ntp verify completo verde: Checkstyle, 36 unit (AliasPolicyTest 17 + CodeGeneratorTest 19), 20 ITs (ConcurrencyIT 1, LinkApiIT 10, RedirectIT 9), SpotBugs 0"
    requirement: LINK-06
    verification:
      - kind: integration
        ref: "cd app && ./mvnw -B -ntp verify"
        status: pass
    human_judgment: false

duration: 6min
completed: 2026-10-08
status: complete
---

# Phase 1 Plan 04: Alias customizado Summary

**`POST /links` aceita `alias` com a regex `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$`, disjunta dos códigos Base62 gerados, e com reservados comparados sem caixa. Alias repetido responde 409 `/problems/alias-conflict`, inclusive quando 10 criações simultâneas disputam o mesmo alias: a violação de `uk_links_code` é traduzida em 409, sem 500. Alias inválido ou reservado responde 400 `/problems/validation-error` com `errors[]`. Nenhum dos corpos de erro expõe SQL, constraint ou nome de exceção.**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-10-08T22:56:24Z
- **Completed:** 2026-10-08T23:02:31Z
- **Tasks:** 1/1
- **Files modified:** 8 (2 criados, 6 alterados)

## Accomplishments

- `{"url":"https://example.com/promo","alias":"minha-promo"}` → 201 com `code: minha-promo`, e `GET /minha-promo` → 302. `Minha-Promo` com outro destino → 201, e cada alias redireciona para o próprio destino (D-06).
- Repetir `minha-promo` → 409 `application/problem+json`, `type: /problems/alias-conflict`, `alias: minha-promo`. O corpo não contém `uk_links_code`, `SQL` nem `Exception` (LINK-06, T-04-06).
- Corrida (D-08, T-04-05): 10 threads liberadas por `CountDownLatch` resultam em 1 sucesso e 9 conflitos 409. O log do `verify` mostra `duplicate key value violates unique constraint "uk_links_code"`, ou seja, parte das threads passou do `existsByCode` e foi barrada pela constraint, que o `isCodeConflict` traduziu.
- `Swagger-UI` e `abcd` → 400 `/problems/validation-error` com `errors[0].field = alias` (D-05, D-07, T-04-01).
- Disjunção (D-04): nenhum código gerado para os IDs 1..200 000 e `SPACE-10 000..SPACE-1` casa com `ALIAS_REGEX`.
- `./mvnw -B -ntp verify`: Checkstyle sem violações, 36 testes unitários, 20 ITs, `BugInstance size is 0`, `BUILD SUCCESS`. O `CodeGeneratorTest` (`ApplicationContextRunner` sem `linkpulse.alias`) continua verde graças ao `@DefaultValue`.

## Task Commits

1. **Task 1 (tracer): Alias customizado de ponta a ponta** - `1a23dc8` (feat). Gate do tracer (`end-of-phase`, verify só automatizado): os três comandos de `<verify>` (`AliasPolicyTest`, `LinkApiIT` e `verify` completo) passaram. Esta é a única task do plano, então não houve expansão depois do gate.

**Plan metadata:** commit `docs(01-04)` logo após este SUMMARY

## Files Created/Modified

- `link/AliasPolicy.java`: `final`; `ALIAS_REGEX`, `Pattern` pré-compilado, set imutável de reservados em minúsculas (`Locale.ROOT`), `matchesFormat`, `isReserved` e `check`, que lança `invalidField("alias", ...)`. O Javadoc explica a disjunção e a política de caixa.
- `link/LinkProblems.java`: `aliasConflict` (409) e `invalidField` (400 com `errors[{field, message}]`).
- `link/LinkService.java`: construtor `(LinkRepository, CodeGenerator, AliasPolicy, Clock)`. O `create` valida o alias, checa `existsByCode`, usa o alias como `code` e envolve o `saveAndFlush` com a tradução de `DataIntegrityViolationException` via `isCodeConflict`.
- `config/LinkPulseProperties.java`: terceiro componente `@DefaultValue Alias alias`, e `record Alias(List<String> reserved)` com construtor compacto (`null` vira `List.of()`, e o conteúdo vai por `List.copyOf`).
- `config/ApplicationConfig.java`: `@Bean AliasPolicy aliasPolicy(LinkPulseProperties)`.
- `application.yml`: `linkpulse.alias.reserved` com as 12 palavras do D-07 e menção a `LINKPULSE_ALIAS_RESERVED`.
- Testes: `AliasPolicyTest` (17 casos, sem Docker) e `LinkApiIT` (+6 cenários, de (e) a (j), mantendo os 4 da 01-03).

## Decisions Made

- `isCodeConflict` olha primeiro `ConstraintViolationException.getConstraintName()` do Hibernate e, como fallback, `SQLException` com SQLSTATE `23505` e o nome da constraint na mensagem. O laço para se uma causa aponta para si mesma. Outras violações de integridade são relançadas e viram 500, porque colisão de código gerado é impossível (D-05) e seria bug.
- `LinkApiIT` usa `@TestMethodOrder(OrderAnnotation)` para (e), (f) e (h). Os testes (f) e (h) recriam `minha-promo` (aceitando o 409 dessa chamada) antes de agir, para passarem também quando rodam sozinhos.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Constantes de alias de 32/33 caracteres não compilavam em `@ValueSource`**
- **Found during:** Task 1, na primeira execução do `AliasPolicyTest`
- **Issue:** `"a-" + "b".repeat(30)` não é uma expressão constante, e o `javac` rejeita esse valor em anotação. Além disso, `containsEntry` sobre `Map<?, ?>` não compilava (captura de wildcard).
- **Fix:** Literais de 32 e 33 caracteres e `assertThat(((Map<?, ?>) errors.get(0)).get("field")).isEqualTo("alias")`.
- **Files modified:** `app/src/test/java/dev/linkpulse/link/AliasPolicyTest.java`
- **Commit:** `1a23dc8`

---

**Total deviations:** 1 auto-fixed (compilação do teste)
**Impact on plan:** Nenhum. Contratos, regex, lista de reservados, `type` URIs e cenários seguem o plano literalmente.

## Issues Encountered

- Nenhum. O Docker Desktop já estava ligado (Engine 29.8.0). O `LinkApiIT` isolado levou ~24 s, e o `verify` completo ~1 min.

## Known Stubs

Nenhum.

## Threat Flags

Nenhuma superfície nova fora do `<threat_model>`. T-04-01, T-04-05, T-04-06 e T-04-07 estão mitigados e cobertos por teste. A allow-list de URL (`javascript:` etc.), a validação de expiração e o `GlobalExceptionHandler` continuam com a 01-06, como o plano define.

## Next Phase Readiness

- 01-06: o `LinkService` recebe `LinkPulseProperties` (próprio host). O `GlobalExceptionHandler` deve gerar `errors[{field, message}]` no mesmo formato de `LinkProblems.invalidField`. O 400 de `@Valid` ainda sai do `problemdetails` padrão, sem `errors[]`.
- 01-07: o README pode citar `/problems/alias-conflict` (409, `alias`) e `/problems/validation-error` (400, `errors[]`) da tabela de Artifacts do plano.
- Fase 2 (cache): o alias é case-sensitive, então a chave do cache precisa usar o `code` exatamente como veio.

## Self-Check: PASSED
