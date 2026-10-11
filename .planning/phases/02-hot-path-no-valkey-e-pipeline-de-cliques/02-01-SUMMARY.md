---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
plan: 01
subsystem: cache
tags: [valkey, redis, lettuce, spring-data-redis, micrometer, testcontainers, cache-aside]

# Dependency graph
requires:
  - phase: 01-funda-o-e-n-cleo-de-links
    provides: LinkService.create, LinkProblems (404/410), Link.isExpiredAt, RedirectController, Testcontainers como beans
provides:
  - LinkCache (cache-aside explícito em lp:link:{code}, codec L/N/G, TTL limitado pela expiração)
  - CacheEntry (sealed interface Found/Missing/Gone)
  - RedirectService (cache -> Postgres fora de transação; substitui LinkService.resolve)
  - LinkService.find(String) read-only e invalidação afterCommit na criação
  - linkpulse.cache.* (default-ttl, negative-ttl, gone-ttl, bypass-cooldown)
  - Métrica linkpulse.cache.requests{result=hit|miss|negative_hit}
  - Valkey 8.1 (volatile-lru) no Testcontainers e no compose.dev.yaml
affects: [02-02, 02-03, 02-08, 02-09, 03-stack-local, 04-kind]

# Actuals (#2632)
actuals:
  tokens: 10700
  tasks: 2
  commits: 4
plan_head_before: ddde21aaa97b2ff491cd9638b62819ba8d38183e

# Tech tracking
tech-stack:
  added: [spring-boot-starter-data-redis (Lettuce, BOM 4.1.1), micrometer-registry-prometheus (runtime, BOM), valkey/valkey:8.1]
  patterns:
    - "Cache-aside explícito com StringRedisTemplate e valor autodescritivo (L/N/G) com prefixo de tipo obrigatório"
    - "Service de leitura do hot path sem @Transactional; transação read-only só no miss"
    - "Invalidação de cache via TransactionSynchronization.afterCommit (record privado EvictAfterCommit)"
    - "Contadores Micrometer registrados uma vez no construtor, só com tag de baixa cardinalidade"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/cache/CacheEntry.java
    - app/src/main/java/dev/linkpulse/cache/LinkCache.java
    - app/src/main/java/dev/linkpulse/link/RedirectService.java
    - app/src/test/java/dev/linkpulse/cache/CacheIT.java
    - app/src/test/java/dev/linkpulse/cache/LinkCacheCodecTest.java
  modified:
    - app/pom.xml
    - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
    - app/src/main/java/dev/linkpulse/link/LinkService.java
    - app/src/main/java/dev/linkpulse/link/RedirectController.java
    - app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java
    - compose.dev.yaml
    - Makefile

key-decisions:
  - "02-01: valores de lp:link:{code} são L|<ISO-8601 ou ->|<url>, N e G|<ISO-8601>; instantes em Instant.toString() (microssegundos preservados), nunca epoch; formato fora do padrão = miss"
  - "02-01: LinkService.resolve saiu; o lookup do redirect mora no RedirectService (sem transação), e LinkService.find(code) é read-only e só roda no miss"
  - "02-01: construtor final do LinkCache nesta fase é (StringRedisTemplate, LinkPulseProperties, Clock, MeterRegistry); a 02-03 insere ValkeyAvailability como segundo parâmetro"
  - "02-01: foundTtl nunca é negativo (expirado ou < 1 ms vira ZERO) e putFound grava G nesse caso; sem expiração e default sub-ms, nada é gravado"
  - "02-01: invalidação do negative cache via record privado EvictAfterCommit (TransactionSynchronization) em vez de classe anônima, sempre registrada, inclusive para código gerado"

patterns-established:
  - "Hot path: RedirectService sem transação consulta LinkCache; LinkService.find só no miss"
  - "Tombstones no cache: N (negative-ttl 60s) e G (gone-ttl 24h) carregam o mesmo Problem Details 404/410 do Postgres"
  - "Métricas de cache: linkpulse.cache.requests com tag result apenas (D-10)"

requirements-completed: [REDIR-03, REDIR-04, OBS-01, QUAL-02]

coverage:
  - id: D1
    description: "Redirect repetido sai do Valkey: depois do primeiro 302, apagar a linha do Postgres não muda o segundo GET"
    requirement: "REDIR-03"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/cache/CacheIT.java#repeatedRedirectIsServedFromCacheEvenAfterRowIsGone"
        status: pass
      - kind: other
        ref: "smoke de dev: docker compose -f compose.dev.yaml up + java -jar --spring.profiles.active=dev; GET /demo-link -> 302 e valkey-cli EXISTS lp:link:demo-link = 1"
        status: pass
    human_judgment: false
  - id: D2
    description: "TTL do link válido nunca passa da expiração (24h sem expiração; <= 2000 ms para link que expira em 2 s) e o GET vira 410 depois"
    requirement: "REDIR-03"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/cache/CacheIT.java#cachedEntryOfLinkWithoutExpirationUsesDefaultTtl"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/cache/CacheIT.java#ttlNeverExceedsTimeUntilExpiration"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/cache/LinkCacheCodecTest.java#foundTtl*"
        status: pass
    human_judgment: false
  - id: D3
    description: "410 servido do cache (G) com expiredAt idêntico, em microssegundos, ao do Postgres; codec L/N/G resistente a URL com '|' e a valores corrompidos"
    requirement: "REDIR-03"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/cache/CacheIT.java#goneServedFromCacheKeepsMicrosecondPrecision"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/cache/LinkCacheCodecTest.java"
        status: pass
    human_judgment: false
  - id: D4
    description: "Negative cache N (TTL <= 60 s) invalidado depois do commit do POST /links com o mesmo alias; o GET seguinte responde 302"
    requirement: "REDIR-03"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/cache/CacheIT.java#negativeCacheIsInvalidatedWhenAliasIsCreated"
        status: pass
    human_judgment: false
  - id: D5
    description: "linkpulse.cache.requests conta hit só para L, negative_hit para N/G e miss para ausente/corrompido, sem tags de código, URL ou IP"
    requirement: "REDIR-04"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/cache/CacheIT.java#hitMissAndNegativeHitAreCounted"
        status: pass
    human_judgment: false
  - id: D6
    description: "Suíte completa da Phase 1 continua verde com o cache no caminho (RedirectIT, LinkApiIT, ConcurrencyIT, LiquibaseContextsIT, OpenApiIT), Checkstyle e SpotBugs"
    requirement: "QUAL-02"
    verification:
      - kind: integration
        ref: "cd app && ./mvnw -B -ntp verify (121 unitários, 56 ITs, BugInstance size 0)"
        status: pass
    human_judgment: false

# Metrics
duration: 14min
completed: 2026-10-11
status: complete
---

# Phase 2 Plan 01: Hot path do redirect no Valkey Summary

**Cache-aside explícito em `lp:link:{code}` no Valkey 8.1 com codec L/N/G em ISO-8601, TTL limitado pela expiração, negative cache invalidado no afterCommit e hit/miss em `linkpulse.cache.requests`**

## Performance

- **Duration:** 14 min
- **Started:** 2026-10-11T15:00:07Z
- **Completed:** 2026-10-11T15:14:35Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments

- `GET /{code}` passa pelo `RedirectService` (sem transação): hit no Valkey não toca o Postgres; só o miss chama `LinkService.find(code)` (read-only).
- `LinkService.resolve` saiu. O lookup do redirect agora mora no `RedirectService`, com os mesmos 404/410 (`LinkProblems`) da Phase 1.
- Formato dos valores em `lp:link:{code}`: `L|<expiresAt ISO-8601 ou ->|<url>` (TTL `min(default-ttl, expiresAt - now)` em ms), `N` (TTL `negative-ttl` = 60s) e `G|<expiredAt ISO-8601>` (TTL `gone-ttl` = 24h). Valor fora do formato é miss.
- Leitura de `L` confere o `Clock` da aplicação (expiração inclusiva); um `L` vencido vira `G` e 410.
- `LinkService.create` registra `afterCommit` que apaga `lp:link:{code}`, sempre (inclusive código gerado).
- Construtor final do `LinkCache` nesta fase: `LinkCache(StringRedisTemplate, LinkPulseProperties, Clock, MeterRegistry)`. A 02-03 insere `ValkeyAvailability` como segundo parâmetro.
- `linkpulse.cache.requests{result=hit|miss|negative_hit}` registrado uma vez no construtor (D-10: só a tag `result`).
- Valkey 8.1 com `volatile-lru` no Testcontainers (`GenericContainer` + `@ServiceConnection(name = "redis")`, 128mb) e no `compose.dev.yaml` (256mb, `127.0.0.1:6379`, healthcheck `valkey-cli ping`).
- Smoke de dev: `GET /demo-link` respondeu `HTTP/1.1 302` com `Location: https://example.com/` e `EXISTS lp:link:demo-link` = `1` (valor `L|-|https://example.com/`, PTTL ~86 398 693 ms).

## Task Commits

1. **Task 1: Redirect repetido servido do Valkey de ponta a ponta** (tracer)
   - `9588d26` test(02-01): redirect repetido deve sair do cache
   - `81cb575` feat(02-01): redirect servido do Valkey com cache-aside fora da transação
2. **Task 2: Negative cache, TTL limitado pela expiração, invalidação depois do commit e hit/miss medidos** (TDD)
   - `4b0ea51` test(02-01): negative cache, TTL e métricas do cache
   - `dfa6ffd` feat(02-01): negative cache invalidado depois do commit e hit/miss no Micrometer

Sem commit de refactor (nada a limpar depois do GREEN).

## TDD Gate Compliance

| Task | RED | GREEN | REFACTOR | Status |
|------|-----|-------|----------|--------|
| 1 (tracer) | `9588d26`: 2/2 ITs falharam por asserção (302 esperado; TTL ausente) | `81cb575` | n/a | Pass |
| 2 (tdd) | `4b0ea51`: `check tdd-red-evidence` = `RED_EVIDENCE_OK` (alvo `negativeCacheIsInvalidatedWhenAliasIsCreated`, 3/6 ITs falhando, exit 1); codec `N`/`G` sem codificação | `dfa6ffd` | não necessário | Pass |

Tracer feedback gate (modo interativo, `end-of-phase`, verify só automatizado): ITs `CacheIT,RedirectIT,LinkApiIT` (38 testes) e smoke de dev passaram sobre a árvore commitada, então a Task 2 seguiu sem checkpoint.

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/cache/CacheEntry.java`: sealed interface `Found`/`Missing`/`Gone`
- `app/src/main/java/dev/linkpulse/cache/LinkCache.java`: cache-aside, codec, `foundTtl`, contadores
- `app/src/main/java/dev/linkpulse/link/RedirectService.java`: cache -> Postgres, tombstones, sem transação
- `app/src/main/java/dev/linkpulse/link/LinkService.java`: `find` read-only, `LinkCache` no construtor, `EvictAfterCommit`
- `app/src/main/java/dev/linkpulse/link/RedirectController.java`: delega a `redirectService.resolve(code)`
- `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java`: record `Cache` com defaults nas anotações e validação de duração positiva
- `app/pom.xml`: `spring-boot-starter-data-redis`, `micrometer-registry-prometheus` (runtime), sem versões
- `app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java`: bean `valkey()`
- `app/src/test/java/dev/linkpulse/cache/CacheIT.java`: 6 ITs (hit, TTL, expiração, precisão do G, negative cache, contadores)
- `app/src/test/java/dev/linkpulse/cache/LinkCacheCodecTest.java`: 18 casos sem Docker
- `compose.dev.yaml`: serviço `valkey`
- `Makefile`: textos de ajuda de `run`/`db-up`/`db-down`

## Decisions Made

- Instantes do codec em ISO-8601 (`Instant.toString()`/`Instant.parse`), como pede o plano; a pesquisa sugeria epoch em ms, o que perderia os microssegundos do 410.
- `foundTtl` devolve `Duration.ZERO` para link já expirado (nunca negativo); `putFound` grava `G` quando o TTL fica abaixo de 1 ms.
- `decode` é estrito: `N` só exato, `G` só com 2 partes, `L` só com 3 partes e URL não vazia.
- `EvictAfterCommit` como record privado em vez de classe anônima (evita aviso de inner class do SpotBugs e deixa a sincronização testável).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Dependência data-redis e bean do Valkey adiantados para o commit RED da Task 1**
- **Found during:** Task 1 (RED)
- **Issue:** o `CacheIT` injeta `StringRedisTemplate`; sem o starter e o container, o RED seria erro de compilação/contexto (RED inválido), não falha de asserção.
- **Fix:** `spring-boot-starter-data-redis` no `pom.xml` e o bean `valkey()` no `TestcontainersConfiguration` entraram no commit `test(02-01)`; o resto do pom (`micrometer-registry-prometheus`) ficou no GREEN.
- **Files modified:** app/pom.xml, app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java
- **Verification:** RED falhou por asserção (`expected: 302`, TTL ausente)
- **Committed in:** `9588d26`

**2. [Rule 2 - Missing Critical] Casos extras de robustez do codec e do TTL**
- **Found during:** Task 2 (RED)
- **Issue:** o `<behavior>` não cobria `G|<não-data>`, `N|x` nem link já vencido no `foundTtl`.
- **Fix:** casos adicionados ao `LinkCacheCodecTest` (todos viram miss ou `Duration.ZERO`), coerentes com T-02-03/T-02-04.
- **Files modified:** app/src/test/java/dev/linkpulse/cache/LinkCacheCodecTest.java
- **Verification:** 18/18 verdes
- **Committed in:** `4b0ea51`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** nenhuma mudança de escopo; contratos do `<interfaces>` mantidos.

## Issues Encountered

- Checkstyle (`LineLength`) recusou uma linha de 102 colunas no construtor compacto de `Cache`; corrigido antes do commit da Task 1.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 02-03 pode inserir `ValkeyAvailability` no `LinkCache` e acrescentar `error`/`bypass` em `linkpulse.cache.requests`; `bypassCooldown` já existe em `linkpulse.cache.*`.
- `/actuator/prometheus` ainda não é exposto (exposição fica com a 02-03); o registry Prometheus já está no classpath.
- `make run` agora sobe Postgres e Valkey (`compose.dev.yaml`).

## Self-Check: PASSED

- Arquivos criados presentes: CacheEntry.java, LinkCache.java, RedirectService.java, CacheIT.java, LinkCacheCodecTest.java
- Commits presentes: 9588d26, 81cb575, 4b0ea51, dfa6ffd
- `./mvnw -B -ntp verify`: BUILD SUCCESS

---
*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Completed: 2026-10-11*
