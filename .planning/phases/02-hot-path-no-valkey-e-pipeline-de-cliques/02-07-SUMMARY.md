---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
plan: 07
subsystem: stats
tags: [mongodb, aggregation, facet, utc, problem-details, openapi, springdoc, testcontainers]

requires:
  - phase: 02-05
    provides: "click_events com browser, os e isBot; índice code_ts {code:1, ts:-1}; timeouts curtos do driver do Mongo (MongoConfig)"
  - phase: 01
    provides: "LinkRepository.existsByCode, LinkProblems.notFound e GlobalExceptionHandler (Problem Details)"
provides:
  - "GET /links/{code}/stats com total, série diária contínua em UTC e tops de referrer, navegador e SO (10 itens, desempate por valor)"
  - "StatsPeriod: defaults de 30 dias, máximo de 366, startInclusive/endExclusive em UTC"
  - "StatsProblems: 400 /problems/invalid-period (from, to, maxDays) e 503 /problems/stats-unavailable"
  - "GlobalExceptionHandler.handleTypeMismatch: 400 /problems/invalid-parameter (parameter), sem ecoar o valor"
  - "Contrato do /stats documentado no OpenAPI (200, 400, 404, 503) e no README"
affects: [02-08, 02-09, 03-observabilidade, 04-kubernetes]

actuals:
  tokens: 11035
  tasks: 3
  commits: 6
plan_head_before: 6a5d1bbd26ec2b1e25331fbe13f7676890b88226

tech-stack:
  added: []
  patterns:
    - "Uma ida ao Mongo por consulta: $match comum + $facet (total, daily, referrers, browsers, os)"
    - "Série contínua montada em Java (datesUntil) a partir do mapa do $group, sem $densify"
    - "Erros de infraestrutura convertidos em ErrorResponseException 503 no service (catch DataAccessException), com detail fixo"
    - "Parâmetro malformado vira Problem Details com o nome do parâmetro e sem o valor recebido"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/stats/StatsController.java
    - app/src/main/java/dev/linkpulse/stats/StatsService.java
    - app/src/main/java/dev/linkpulse/stats/StatsResponse.java
    - app/src/main/java/dev/linkpulse/stats/StatsPeriod.java
    - app/src/main/java/dev/linkpulse/stats/StatsProblems.java
    - app/src/test/java/dev/linkpulse/stats/StatsIT.java
    - app/src/test/java/dev/linkpulse/stats/StatsPeriodTest.java
  modified:
    - app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java
    - app/src/test/java/dev/linkpulse/OpenApiIT.java
    - README.md

key-decisions:
  - "Contrato JSON do /stats travado: {code, from, to, includeBots, total, daily[{date, clicks}], topReferrers/topBrowsers/topOs[{value, clicks}]}, datas YYYY-MM-DD (Jackson 3 do Boot 4.1.1 serializa LocalDate assim sem configuração extra)"
  - "Documentos sem isBot contam como humanos ($ne: true) e valores nulos nos tops viram (unknown); o smoke de dev mostrou um documento anterior à 02-05 caindo nos dois casos"
  - "Mongo fora: DataAccessException vira 503 /problems/stats-unavailable em cerca de 8 s (read timeout de 5 s do driver mais a nova tentativa de leitura), abaixo do limite de 15 s"
  - "ITs com documentos inseridos à mão usam datas relativas a hoje: o índice TTL de 90 dias do click_events apagaria datas fixas antigas no meio do teste"

patterns-established:
  - "Endpoints de leitura do Mongo: existência no Postgres antes, depois um único aggregate com $match + $facet"
  - "Constantes de contrato público (30, 366, 10) no código, nunca em configuração por ambiente"

requirements-completed: [STAT-01, STAT-02, STAT-03, STAT-04, QUAL-02]

coverage:
  - id: D1
    description: "Depois de 3 redirects com Firefox, GET /links/{code}/stats devolve total 3, includeBots false e from = to - 29 dias"
    requirement: STAT-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#statsCountRedirectsOfTheLink"
        status: pass
    human_judgment: false
  - id: D2
    description: "Período: defaults (30 dias, só from, só to), 1 dia, 366 aceito, 367 e from > to como 400 invalid-period com maxDays"
    requirement: QUAL-02
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/stats/StatsPeriodTest.java"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#invalidPeriodIsProblem"
        status: pass
    human_judgment: false
  - id: D3
    description: "Série diária contínua em UTC: 23:59:59.999Z conta no dia, 00:00:00.000Z no seguinte, e os dias sem clique saem com 0; link sem cliques tem 30 dias com 0"
    requirement: STAT-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#dailySeriesIsContinuousInUtc"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#linkWithoutClicksHasZeroSeries"
        status: pass
    human_judgment: false
  - id: D4
    description: "Tops com no máximo 10 itens, por cliques decrescente e, no empate, por valor; navegador e SO refletem os documentos"
    requirement: STAT-03
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#topsAreLimitedToTenWithStableTieBreak"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#browserAndOsTopsFollowDocuments"
        status: pass
    human_judgment: false
  - id: D5
    description: "Bots ficam fora do total e dos tops por padrão e entram com includeBots=true"
    requirement: STAT-04
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#botsAreExcludedByDefaultAndIncludedOnRequest"
        status: pass
    human_judgment: false
  - id: D6
    description: "Erros em Problem Details: invalid-parameter sem ecoar o valor (from=2026-13-01, includeBots=talvez), 404 link-not-found, link expirado com 200"
    requirement: STAT-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#malformedParameterIsProblem"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#unknownCodeIsNotFound"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#expiredLinkStillHasStats"
        status: pass
    human_judgment: false
  - id: D7
    description: "A resposta nunca traz ipHash nem o valor do hash de nenhum documento"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#responseNeverExposesIpHash"
        status: pass
    human_judgment: false
  - id: D8
    description: "Com o Mongo pausado, o /stats responde 503 /problems/stats-unavailable em menos de 15 s (8030 ms medidos) e volta a 200 depois do unpause"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/stats/StatsIT.java#mongoOutageIsServiceUnavailable"
        status: pass
    human_judgment: false
  - id: D9
    description: "OpenAPI do /links/{code}/stats com 200, 400, 404 e 503, e o 400 em application/problem+json"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/OpenApiIT.java#statsDocumentsSuccessAndErrors"
        status: pass
    human_judgment: false
  - id: D10
    description: "Smoke de dev (compose.dev.yaml + profile dev): depois de GET /demo-link, /links/demo-link/stats?includeBots=true traz total > 0, daily e topReferrers"
    verification:
      - kind: e2e
        ref: "segundo <verify> da Task 3 (compose up, java -jar --spring.profiles.active=dev, curl /demo-link, curl /stats)"
        status: pass
    human_judgment: false
  - id: D11
    description: "Build completo verde: Checkstyle, 214 unitários, 87 ITs e SpotBugs sem achados"
    requirement: QUAL-02
    verification:
      - kind: other
        ref: "cd app && ./mvnw -B -ntp verify"
        status: pass
    human_judgment: false
  - id: D12
    description: "README: seção Ver estatísticas com curl e JSON de exemplo, nota de que o curl conta como bot e três tipos de erro novos"
    verification: []
    human_judgment: true
    rationale: "Clareza e precisão da documentação para o avaliador são julgamento humano; os greps do acceptance só provam a presença dos termos"

duration: 18min
completed: 2026-10-11
status: complete
---

# Phase 02 Plan 07: Estatísticas de cliques por link Summary

**`GET /links/{code}/stats` responde com uma única ida ao MongoDB (`$match` + `$facet`). Traz o total, a série diária contínua em UTC e os tops de referrer, navegador e SO (10 itens, desempate pelo valor), com bots fora por padrão e `includeBots=true` para incluí-los. Período inválido, parâmetro malformado e Mongo fora saem em Problem Details (400, 400 e 503), e o contrato está no OpenAPI e no README.**

## Performance

- **Duração:** 18 min
- **Início:** 2026-10-11T19:41:31Z
- **Fim:** 2026-10-11T19:59:15Z
- **Tasks:** 3
- **Arquivos modificados:** 10 (7 criados, 3 alterados)

## Accomplishments

- O endpoint confere a existência do código no Postgres (404 se não existe; link expirado continua com 200) e roda um único aggregate no `click_events`, atendido pelo índice `code_ts`.
- `StatsPeriod` resolve os defaults (30 dias até hoje em UTC; só `from` vai até hoje; só `to` começa 29 dias antes) e rejeita `from > to` e mais de 366 dias. O filtro é `ts >= from 00:00Z` e `ts < (to + 1) 00:00Z`.
- A série diária vem do `$dateToString` com `timezone: "UTC"` e é completada em Java com `datesUntil`, com 0 nos dias sem clique.
- Os tops usam `$group` → `$sort {n: -1, _id: 1}` → `$limit 10`. A ordem é estável e `null` vira `(unknown)`.
- O `handleTypeMismatch` trata qualquer parâmetro de query ou path em formato errado como `/problems/invalid-parameter`, com `parameter` e sem o valor recebido. Antes, o corpo padrão do Spring ecoava o valor.
- Com o Mongo fora, a resposta é 503 `/problems/stats-unavailable`, sem 500 genérico e sem logar código nem período.

## Contrato JSON do `/stats` (Phase 3 seed, Phase 4 smoke)

```json
{
  "code": "demo-link",
  "from": "2026-09-12",
  "to": "2026-10-11",
  "includeBots": true,
  "total": 3,
  "daily": [{ "date": "2026-09-12", "clicks": 0 }, "... 30 itens, contínuos ..."],
  "topReferrers": [{ "value": "(direct)", "clicks": 3 }],
  "topBrowsers": [{ "value": "Other", "clicks": 2 }, { "value": "(unknown)", "clicks": 1 }],
  "topOs": [{ "value": "Other", "clicks": 2 }, { "value": "(unknown)", "clicks": 1 }]
}
```

Este é o corpo real do smoke de dev, com a série encurtada. O `(unknown)` vem de um documento gravado antes da 02-05, sem `browser`, `os` e `isBot`, que ficou no volume de dev. Sem `isBot`, ele conta como humano: sem `includeBots`, o `total` foi 1.

- **Datas:** `from`, `to` e `daily[].date` saem como `YYYY-MM-DD`. Confirmado com o Jackson 3 do Boot 4.1.1 sem nenhuma configuração extra (smoke de dev e `StatsIT`).
- **Smoke da Phase 4:** o `curl` conta como bot. Use `includeBots=true` ou mande um `User-Agent` de navegador no redirect.
- **Seed da Phase 3:** os documentos devem ter `isBot: false` (ou um UA de navegador no redirect) para aparecer na consulta padrão. Datas com mais de 90 dias são apagadas pelo TTL.

## Tempo até o 503 com o Mongo pausado

**8030 ms** (`StatsIT#mongoOutageIsServiceUnavailable`, `docker pause` no container do Mongo, abaixo do limite de 15 s). A conta bate com o read timeout de 5 s do `MongoConfig` mais a nova tentativa de leitura do driver. O RED, ainda sem o `catch`, devolveu 500 em cerca de 10 s.

## Task Commits

1. **Task 1 (RED): stats deve contar os redirects do link** - `8989682` (test)
2. **Task 1 (GREEN): GET /links/{code}/stats com total do período** - `f6b599a` (feat)
3. **Task 2 (RED): período, série diária UTC e erros do stats** - `a1fca14` (test)
4. **Task 2 (GREEN): período validado, série diária UTC e erros em Problem Details** - `918163c` (feat)
5. **Task 3 (RED): tops, bots, 503 e documentação do stats** - `b08bd7b` (test)
6. **Task 3 (GREEN): tops por dimensão, bots opcionais e contrato documentado** - `38d8a1e` (feat)

## TDD Gate Compliance

- Task 1 (tracer): RED `8989682`, `RED_EVIDENCE_OK` com o alvo `statsCountRedirectsOfTheLink` (esperava 200, veio 404). GREEN `f6b599a`. O gate do tracer (interativo, `end-of-phase`, verify só automatizado) rodou de novo o `<verify>` (`StatsIT` + `ClickPipelineIT`, 9/9) sobre o código commitado e passou. A expansão seguiu sem checkpoint.
- Task 2: RED `a1fca14`, `RED_EVIDENCE_OK` com o alvo `dailySeriesIsContinuousInUtc`. Falharam por asserção 2 unitários (`oneDayOverMaxIsInvalidPeriod`, `fromAfterToIsInvalidPeriod`) e 4 ITs. GREEN `918163c`.
- Task 3: RED `b08bd7b`, `RED_EVIDENCE_OK` com o alvo `topsAreLimitedToTenWithStableTieBreak`. Falharam 4 ITs do `StatsIT` (o 503 veio como 500) e `OpenApiIT#statsDocumentsSuccessAndErrors`. GREEN `38d8a1e`.
- Nenhum REFACTOR foi necessário.

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/stats/StatsController.java`: rota `/links/{code:[A-Za-z0-9_-]+}/stats`, `@Tag(name = "Stats")`, `@Operation`, `@Parameter` e `@ApiResponse` 200/400/404/503
- `app/src/main/java/dev/linkpulse/stats/StatsService.java`: existência no Postgres, `$match` + `$facet`, série em Java, tops, `catch DataAccessException` → 503
- `app/src/main/java/dev/linkpulse/stats/StatsResponse.java`: records `StatsResponse`, `DailyCount` e `TopItem` com `@Schema`
- `app/src/main/java/dev/linkpulse/stats/StatsPeriod.java`: `DEFAULT_DAYS = 30`, `MAX_DAYS = 366`, `resolve`, `days`, `startInclusive`, `endExclusive`
- `app/src/main/java/dev/linkpulse/stats/StatsProblems.java`: `invalidPeriod(from, to)` e `unavailable()`
- `app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java`: override de `handleTypeMismatch`
- `app/src/test/java/dev/linkpulse/stats/StatsIT.java`: 12 cenários (353 linhas)
- `app/src/test/java/dev/linkpulse/stats/StatsPeriodTest.java`: 8 casos unitários, sem Docker
- `app/src/test/java/dev/linkpulse/OpenApiIT.java`: `statsDocumentsSuccessAndErrors`
- `README.md`: seção "Ver estatísticas", nota do curl como bot, linhas `invalid-period`, `invalid-parameter` e `stats-unavailable` na tabela de erros, e `link-not-found` citando o `/stats`

## Decisions Made

- O facet `daily` não tem `$sort`: a ordem vem da série montada em Java, que percorre todos os dias do período.
- `StatsProblems.invalidPeriod` recebe o período **efetivo** (depois dos defaults), então `from`, `to` e `maxDays` no corpo são exatamente o que foi rejeitado.
- O `detail` do `invalid-period` é montado com `StatsPeriod.MAX_DAYS` ("O período precisa ter from <= to e no máximo 366 dias."), para o texto não divergir da constante.
- O `handleTypeMismatch` usa `String.valueOf(ex.getPropertyName())` (o nome do `@RequestParam`) e responde sempre 400, seja o erro de query ou de path.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Datas relativas no IT da série diária**
- **Found during:** Task 2 (RED)
- **Issue:** o plano usa cliques em 2026-03-01..04. Com hoje em 2026-10-11, esses documentos têm mais de 90 dias, e o índice TTL `ts_ttl` do `click_events` (criado pelo consumer no contexto compartilhado) pode apagá-los entre o insert e a consulta. Isso deixaria o teste intermitente.
- **Fix:** o teste usa `first = hoje (UTC) - 10 dias`, com cliques em `first T23:59:59.999Z`, `first+1 T00:00:00.000Z` e `first+3 T12:00:00Z`, e período `first..first+4`. A borda do dia UTC e a série com zeros são as mesmas do plano. Os testes de período inválido continuam com as datas fixas do plano, porque não inserem documentos.
- **Files modified:** `app/src/test/java/dev/linkpulse/stats/StatsIT.java`
- **Verification:** `dailySeriesIsContinuousInUtc` verde no build completo
- **Committed in:** `a1fca14`

**2. [Rule 3 - Blocking] Qualificador do container do Mongo**
- **Found during:** Task 3 (RED)
- **Issue:** o plano manda pausar o Mongo com `@Qualifier("mongo")`, mas o bean do container se chama `mongoContainer` (`mongo` é o `MongoClient` da auto-configuração).
- **Fix:** `@Autowired @Qualifier("mongoContainer") MongoDBContainer`.
- **Files modified:** `app/src/test/java/dev/linkpulse/stats/StatsIT.java`
- **Committed in:** `b08bd7b`

---

**Total deviations:** 2 auto-fixed (1 bug de teste, 1 bloqueio).
**Impact on plan:** nenhum desvio de contrato; nomes, rotas, constantes e comportamento são os do `<interfaces>`.

## Issues Encountered

- Três ITs já passavam no RED e ficam como testes de caracterização: `unknownCodeIsNotFound` e `expiredLinkStillHasStats` (a Task 1 já conferia a existência no Postgres e não olhava a expiração) e `responseNeverExposesIpHash` (o record nunca teve o campo). O RED de cada task se apoia nos alvos listados em "TDD Gate Compliance".
- No smoke de dev, o `total` com `includeBots=true` foi 3, e não 1, porque o volume do `compose.dev.yaml` guarda cliques de smokes anteriores. O critério (`total > 0`, `daily`, `topReferrers`) passou.

## User Setup Required

None. Não há serviço externo novo.

## Next Phase Readiness

- A 02-08 pode reaproveitar o `mongoOutageIsServiceUnavailable` como referência do comportamento do `/stats` com o Mongo fora (503 em cerca de 8 s).
- A 02-09 limita só o `POST /links`: o `/links/{code}/stats` fica em outro path e não precisa de exceção no rate limit.
- O seed (Phase 3) e o smoke (Phase 4) têm o contrato JSON acima e a regra de bots.
- Sem bloqueios.

## Self-Check: PASSED

- Arquivos criados presentes: `StatsController.java`, `StatsService.java`, `StatsResponse.java`, `StatsPeriod.java`, `StatsProblems.java`, `StatsIT.java`, `StatsPeriodTest.java`.
- Commits presentes: `8989682`, `f6b599a`, `a1fca14`, `918163c`, `b08bd7b`, `38d8a1e` (`git rev-list --count 6a5d1bb..HEAD` = 6).
- `./mvnw -B -ntp verify`: BUILD SUCCESS (214 unitários, 87 ITs, 0 violações de Checkstyle, 0 achados do SpotBugs). Smoke de dev: redirect 302, `/stats?includeBots=true` com `total` 3, `daily` e `topReferrers`, e `from=2026-13-01` com 400.

---
*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Completed: 2026-10-11*
