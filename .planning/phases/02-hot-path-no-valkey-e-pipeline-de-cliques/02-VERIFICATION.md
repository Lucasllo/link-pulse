---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
verified: 2026-10-11T21:20:00Z
status: human_needed
score: "73/74 must-haves verificados (SC do roadmap 5/5; truths dos planos 68/69, 1 presente mas sem teste de comportamento)"
covered_files:
  - .github/dependabot.yml
  - .planning/REQUIREMENTS.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-01-PLAN.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-01-SUMMARY.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-02-PLAN.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-02-SUMMARY.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-03-PLAN.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-03-SUMMARY.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-04-PLAN.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-04-SUMMARY.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-05-PLAN.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-05-SUMMARY.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-06-PLAN.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-06-SUMMARY.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-07-PLAN.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-07-SUMMARY.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-08-PLAN.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-08-SUMMARY.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-09-PLAN.md
  - .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-09-SUMMARY.md
  - Makefile
  - README.md
  - app/pom.xml
  - app/src/main/java/dev/linkpulse/cache/CacheEntry.java
  - app/src/main/java/dev/linkpulse/cache/LinkCache.java
  - app/src/main/java/dev/linkpulse/cache/ValkeyAvailability.java
  - app/src/main/java/dev/linkpulse/click/ClickDocument.java
  - app/src/main/java/dev/linkpulse/click/ClickEventWriter.java
  - app/src/main/java/dev/linkpulse/click/ClickIndexes.java
  - app/src/main/java/dev/linkpulse/click/ClickPublisher.java
  - app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java
  - app/src/main/java/dev/linkpulse/click/MongoClickEventWriter.java
  - app/src/main/java/dev/linkpulse/click/ReferrerNormalizer.java
  - app/src/main/java/dev/linkpulse/click/UserAgentClassifier.java
  - app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java
  - app/src/main/java/dev/linkpulse/common/IpHasher.java
  - app/src/main/java/dev/linkpulse/config/ClickConfig.java
  - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
  - app/src/main/java/dev/linkpulse/config/MongoConfig.java
  - app/src/main/java/dev/linkpulse/config/RedisConfig.java
  - app/src/main/java/dev/linkpulse/config/WebMvcConfig.java
  - app/src/main/java/dev/linkpulse/link/LinkController.java
  - app/src/main/java/dev/linkpulse/link/LinkService.java
  - app/src/main/java/dev/linkpulse/link/RedirectController.java
  - app/src/main/java/dev/linkpulse/link/RedirectService.java
  - app/src/main/java/dev/linkpulse/ratelimit/RateLimitInterceptor.java
  - app/src/main/java/dev/linkpulse/ratelimit/RateLimitProblems.java
  - app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java
  - app/src/main/java/dev/linkpulse/stats/StatsController.java
  - app/src/main/java/dev/linkpulse/stats/StatsPeriod.java
  - app/src/main/java/dev/linkpulse/stats/StatsProblems.java
  - app/src/main/java/dev/linkpulse/stats/StatsResponse.java
  - app/src/main/java/dev/linkpulse/stats/StatsService.java
  - app/src/main/resources/application-dev.yml
  - app/src/main/resources/application.yml
  - app/src/test/java/dev/linkpulse/AbstractIT.java
  - app/src/test/java/dev/linkpulse/MetricsCardinalityIT.java
  - app/src/test/java/dev/linkpulse/OpenApiIT.java
  - app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java
  - app/src/test/java/dev/linkpulse/cache/CacheIT.java
  - app/src/test/java/dev/linkpulse/cache/LinkCacheCodecTest.java
  - app/src/test/java/dev/linkpulse/cache/ValkeyAvailabilityTest.java
  - app/src/test/java/dev/linkpulse/cache/ValkeyOutageIT.java
  - app/src/test/java/dev/linkpulse/click/ClickConsumerLifecycleIT.java
  - app/src/test/java/dev/linkpulse/click/ClickDocumentTest.java
  - app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java
  - app/src/test/java/dev/linkpulse/click/ClickPublishIT.java
  - app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java
  - app/src/test/java/dev/linkpulse/click/ClickRedeliveryIT.java
  - app/src/test/java/dev/linkpulse/click/ClickResilienceIT.java
  - app/src/test/java/dev/linkpulse/click/ReferrerNormalizerTest.java
  - app/src/test/java/dev/linkpulse/click/UserAgentClassifierTest.java
  - app/src/test/java/dev/linkpulse/common/IpHasherTest.java
  - app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java
  - app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java
  - app/src/test/java/dev/linkpulse/ratelimit/RateLimiterTest.java
  - app/src/test/java/dev/linkpulse/ratelimit/TrustedProxyRateLimitIT.java
  - app/src/test/java/dev/linkpulse/stats/StatsIT.java
  - app/src/test/java/dev/linkpulse/stats/StatsPeriodTest.java
  - compose.dev.yaml
covered_digest: "v1:sha256:1027bfb5610bfe8e5b10ab64e4240cd6a9cf7ac17d90a5297ed260dca6bb85d3"
behavior_unverified: 1
overrides_applied: 0
behavior_unverified_items:
  - truth: "02-06: uma entrada pendente que ainda existe no stream e que outro consumer acabou de assumir não é confirmada pelo reclaim (só entradas que não existem mais no stream contam como lost)"
    test: "Com duas réplicas no mesmo grupo, provocar a corrida em que a réplica A faz XCLAIM de uma entrada entre o XPENDING e o XCLAIM da réplica B; conferir que B não faz XACK dela e que linkpulse.clicks.lost não sobe"
    expected: "A entrada continua no PEL com dono replicaA até a gravação de A; lost = 0; nenhum clique some se a escrita de A falhar"
    why_human: "Nenhum IT reproduz a corrida de forma determinística. O ramo está no código (ClickStreamConsumer.acknowledgeVanished, XRANGE id id), e a semântica do Valkey 8.1 foi conferida à mão pelo verificador com valkey-cli (XCLAIM MIN-IDLE da segunda réplica volta vazio e XRANGE ainda acha a entrada), mas o caminho Java não é exercitado por teste"
human_verification:
  - test: "Revisar a corrida XPENDING/XCLAIM entre duas réplicas (ClickStreamConsumer.reclaimIdle e acknowledgeVanished, linhas ~470-535)"
    expected: "Um ID pedido ao XCLAIM que não voltou e que ainda existe no stream nunca recebe XACK; só IDs ausentes do stream viram lost"
    why_human: "Invariante de ordenação entre processos; sem IT determinístico (sinalizado pela própria 02-06-SUMMARY, D4)"
  - test: "Decidir sobre o WR-01 da revisão: com o Valkey pausado ou particionado (não parado), cada vez que a janela de bypass de 2 s vence, todas as requisições concorrentes que chegam nos próximos ~150 ms pagam o timeout do Lettuce"
    expected: "Aceitar como limitação conhecida e corrigir o Javadoc do ValkeyAvailability e o README (que dizem 'só a primeira chamada de cada janela paga'), ou implementar o half-open com sonda única"
    why_human: "O critério 2 (docker stop) foi comprovado: 302 em 11-15 ms com o Valkey parado. A pausa sob carga concorrente gera picos limitados a ~150 ms a cada ~2,15 s; se isso é 'atraso perceptível' é decisão de produto, e a documentação hoje afirma algo que o código não garante"
  - test: "Confirmar as três proibições de nível 'judgment' sem teste próprio: (a) 02-05 índices do Mongo nunca criados na inicialização dos beans; (b) 02-06 sem limite de reentregas; (c) 02-08 scrape das gauges nunca consulta Valkey/Mongo"
    expected: "Veredito do verificador (não autoritativo): as três se sustentam no código. ClickIndexes não toca o Mongo no construtor e ensure() só roda no loop; não há deliveryCount no código; as gauges leem AtomicLong atualizados por refreshGaugesIfDue no loop"
    why_human: "Proibições de julgamento exigem resolução humana explícita no verify interativo"
  - test: "Ler o README como avaliador (seções Valkey fora, Ver estatísticas, rate limit, erros e riscos conhecidos)"
    expected: "Texto claro e correto; incluir browser/os/isBot na descrição do documento de clique (IN-13) e corrigir a frase do gate (WR-01)"
    why_human: "Clareza da documentação é julgamento humano (02-07 D12 e 02-09 D11 já pediam leitura humana)"
---

# Phase 2: Hot path no Valkey e pipeline de cliques — Relatório de Verificação

**Objetivo da fase:** Redirects saem do cache Valkey e continuam funcionando se ele cair; cada clique vira um evento processado de forma assíncrona e idempotente no MongoDB, e o cliente consulta estatísticas confiáveis de cada link.
**Verificado em:** 2026-10-11T21:20:00Z (HEAD `1ade0be`, branch `gsd/phase-02-hot-path-no-valkey-e-pipeline-de-cliques`)
**Status:** human_needed
**Re-verificação:** Não (verificação inicial)

> **Nota sobre o modo MVP.** O ROADMAP marca a fase com `Mode: mvp`, mas o objetivo não está no formato de user story (os próprios planos dizem isso). Seguindo o precedente da Phase 1, apliquei a verificação goal-backward padrão contra os 5 critérios de sucesso do ROADMAP. Recomendação: reescrever o objetivo com `/gsd mvp-phase 2` ou tirar `mode: mvp` da fase.

## Evidência própria do verificador

Além de ler o código (não os SUMMARYs), rodei:

1. **Relatórios do Surefire/Failsafe da execução do `make verify` do orquestrador** (18:07-18:09, depois do HEAD): 225 unitários e 103 ITs, 0 falhas e 0 erros. Somei suíte por suíte e bate com o total.
2. **Smoke de dev próprio** (`compose.dev.yaml` + jar com profile dev, depois `docker compose stop valkey`; ambiente derrubado no fim, sem containers nem processos sobrando):
   - Valkey no ar: 3 GETs `/demo-link` com UA do Chrome e `Referer: https://News.Example.com/a?token=x` deram 302 (o primeiro em 995 ms com a JVM fria; os outros em 10-13 ms). `HEAD` deu 302. `cache_requests` ficou com miss 1 e hit 3, e `clicks_publish{published}` ficou em 3: o HEAD não publicou.
   - `/links/demo-link/stats` respondeu 200 com a série de 30 dias contínua e `topReferrers` com `news.example.com` (host em minúsculas, sem path nem query), `topBrowsers` Chrome e `topOs` Windows.
   - **`docker compose stop valkey`:** 8 GETs deram 302 em **11-16 ms**, e um código inexistente deu 404 em 32 ms. As métricas mostraram `cache_requests{error}` 3, `{bypass}` 6, `cache_errors{get}` 3, `valkey_unavailable` 3 e `clicks_publish{failed}` 8.
   - Mongo: os documentos de `click_events` têm só `code, ts, referrer, browser, os, isBot, ipHash, _class` (sem IP nem UA cru). Os índices são `_id_`, `code_ts {code:1, ts:-1}` e `ts_ttl {ts:1}` com `expireAfterSeconds 7776000`.
   - O `/actuator/prometheus` (8081) expôs o catálogo inteiro (cache, publish, persisted, pending, lag, length, ratelimit, `http_server_requests`), com `uri` em forma de template.
3. **Semântica do XCLAIM no Valkey 8.1** (container efêmero, `valkey-cli`): depois do XCLAIM da réplica A, o XCLAIM com MIN-IDLE da réplica B volta vazio, `XRANGE id id` ainda acha a entrada e o PEL mostra o dono `replicaA`. Isso sustenta o ramo de `acknowledgeVanished`, mas não substitui um teste do caminho Java.
4. Greps de proibição: nenhum `@Cacheable`, nenhuma leitura de `X-Forwarded*` no código (só comentários), sem Jedis, `@Transactional` = 0 no `RedirectService` e nenhum `deliveryCount`. Nenhum `TBD`/`FIXME`/`XXX`/`TODO` nos 61 arquivos alterados na fase.

## Atingimento do objetivo

### Critérios de sucesso do ROADMAP (contrato)

| # | Critério | Status | Evidência |
|---|----------|--------|-----------|
| 1 | Redirects repetidos saem do Valkey, com TTL que nunca passa da expiração; um 404 vira 302 logo depois da criação; `/actuator/prometheus` expõe hit/miss, requisições, latência, cliques e pipeline, sem tags por código, URL ou IP | ✓ VERIFICADO | `RedirectService` consulta `LinkCache.get` antes de `LinkService.find`. O `foundTtl` é `min(default, expiresAt-now)`. `LinkService.create` registra `afterCommit` → `evict`. ITs que passaram: `CacheIT#repeatedRedirectIsServedFromCacheEvenAfterRowIsGone`, `#ttlNeverExceedsTimeUntilExpiration`, `#negativeCacheIsInvalidatedWhenAliasIsCreated` e `#hitMissAndNegativeHitAreCounted`. `MetricsCardinalityIT` faz scrape HTTP real, confere o catálogo e limita as chaves de tag a `{result, operation, outcome}`. O smoke mostrou o catálogo e o histograma. |
| 2 | Com o Valkey parado (`docker stop`), `GET /{code}` responde 302 via Postgres sem atraso perceptível; falha do cache e cliques descartados em métricas; HEAD nunca gera clique | ✓ VERIFICADO | O smoke com `docker compose stop valkey` deu 302 em 11-16 ms, e os contadores `error`/`bypass`/`valkey_unavailable`/`publish{failed}` subiram. `ValkeyOutageIT` (caso mais duro, `docker pause`) passa com o limite de 1000 ms. `RedirectController` só publica com `HttpMethod.GET.matches`; `ClickPublishIT#headRequestDoesNotPublishClick` passa, e o smoke confirmou que o HEAD não publicou. Ressalva WR-01 (pausa + carga concorrente) em "Verificação humana". |
| 3 | `/stats` com total (filtro `from`/`to`), cliques por dia, top referrers e navegadores/SO, bots fora por padrão e dentro com `?includeBots=true`; `click_events` com índice `{code, ts}` e TTL; nenhum documento com IP em claro | ✓ VERIFICADO | `StatsService`: `$match` (code, ts, `isBot $ne true`) + `$facet` (total, daily com `timezone: UTC`, 3 tops com `$sort {n:-1,_id:1}` e `$limit 10`). Os 12 testes do `StatsIT` passam (`dailySeriesIsContinuousInUtc`, `botsAreExcludedByDefaultAndIncludedOnRequest`, `topsAreLimitedToTenWithStableTieBreak`, `responseNeverExposesIpHash`...). `ClickIndexes` cria `code_ts` e `ts_ttl` com `collMod`, conferido no Mongo do smoke. O documento só tem `ipHash` (HMAC-SHA256 com chave e dia UTC). |
| 4 | O pipeline não perde nem duplica: ITs com Testcontainers (Postgres + Mongo + Valkey) cobrem redirect, cache, rate limit e stats; XACK só depois da gravação; pendentes de consumer morto reprocessados sem duplicata; consumer vivo depois da queda do Mongo; shutdown conclui o lote; lag e pendentes como métricas | ✓ VERIFICADO | `ClickStreamConsumer.process` chama `writer.write` antes do `acknowledge`, e uma exceção sobe sem XACK. ITs que passaram: `ClickRedeliveryIT#batchIsAcknowledgedOnlyAfterSuccessfulWrite` (PEL = 5 e 0 docs com o writer falhando; depois 5 docs e PEL 0), `#deadConsumerPendingIsReprocessedWithoutDuplicates` (6 docs, `_id` = IDs do stream, duplicates >= 3), `ClickResilienceIT#consumerSurvivesMongoOutageAndCatchesUp` (Mongo pausado, 302 e N docs exatos depois) e `ClickConsumerLifecycleIT#stopWaitsForInFlightBatch` (o callback só roda depois de gravar e confirmar) e `#pendingLagAndLengthGaugesFollowTheGroup`. Os containers estão em `TestcontainersConfiguration` (postgres, `valkey/valkey:8.1`, `mongo:8.0`). |
| 5 | `POST /links` acima do limite por IP responde 429 com `Retry-After`; IP real só via proxy confiável; `X-Forwarded-For` de origem não confiável ignorado | ✓ VERIFICADO | O script Lua faz `INCR` + `PEXPIRE` atômicos; o interceptor só age no `POST` em `/links` e lê o IP de `getRemoteAddr()`. A config tem `forward-headers-strategy: native` e `internal-proxies: ""`. ITs que passaram: `RateLimitIT#fourthCreationInTheWindowIsRateLimited`, `#concurrentRequestsNeverExceedTheLimit`, `#spoofedForwardedForFromUntrustedPeerIsIgnored` e `#redirectHeadAndStatsAreNeverRateLimited`, e `TrustedProxyRateLimitIT#trustedProxyForwardsTheRealClientIp` e `#spoofedLeftmostEntriesAreIgnoredBehindTrustedProxy`, com controle positivo. |

### Truths dos planos (PLAN frontmatter)

| Plano | Truths | Status | Evidência principal |
|-------|--------|--------|---------------------|
| 02-01 | 7 | 7/7 ✓ | `CacheIT` (6 ITs), `LinkCacheCodecTest` (18 casos, inclusive borda `foundTtl(now,now)=0` e 500 µs), regra inclusiva `!isBefore` no `RedirectService`, codec ISO-8601 (`Instant.toString/parse`), contadores só com `result`; smoke: miss 1 → hit 3 em `demo-link` |
| 02-02 | 8 | 8/8 ✓ | `ClickPublishIT` (5), `IpHasherTest` (11), `ReferrerNormalizerTest` (15), `ClickPublisherTest#xaddTrimsTheStreamApproximatelyToMaxLen`; executor `ArrayBlockingQueue(10000)` com handler de rejeição; smoke: `stream_length` 3 |
| 02-03 | 8 | 8/8 ✓ | `ValkeyOutageIT` (2), `ValkeyAvailabilityTest` (5), `ClickPublisherTest` (7), `LinkPulsePropertiesTest#toStringDoesNotExposeIpHashKey` e `#ipHashKeyBindsFromEnvironmentVariable`; `REJECT_COMMANDS` e `timeout: 150ms`; smoke com Valkey parado: 302 em < 20 ms |
| 02-04 | 6 | 6/6 ✓ | `ClickPipelineIT#redirectBecomesClickDocumentInMongo`, `#mongoDatabaseIsLinkpulse`, `#identicalEntriesBecomeDistinctDocuments`; `dedicatedConnectionFactory` com `block + 3 s`; `BulkMode.UNORDERED` tolerando só 11000; smoke: documentos em `click_events` |
| 02-05 | 7 | 7/7 ✓ | `UserAgentClassifierTest` (35), `ClickDocumentTest` (10), `ClickPipelineIT#documentCarriesBrowserOsAndBotFlag`, `#invalidEntryIsAcknowledgedWithoutDocument`, `#idleConsumerDoesNotWriteAndSingleEntryIsAcknowledged`, `#clickEventsHasCodeTsAndTtlIndexes`, `#retentionChangeUpdatesTtlWithCollMod`; `MongoConfig` 3 s/2 s/5 s; smoke: índices presentes |
| 02-06 | 6 | 5/6 ✓, 1 ⚠️ | `ClickRedeliveryIT` (3) cobre XACK depois da escrita, consumer morto, crash entre insert e ACK com duplicatas intercaladas e entradas aparadas como `lost`. **⚠️ PRESENT_BEHAVIOR_UNVERIFIED:** "entrada assumida por outro consumer não é confirmada pelo reclaim" (ver abaixo) |
| 02-07 | 12 | 12/12 ✓ | `StatsIT` (12) e `StatsPeriodTest` (8) cobrem contrato JSON, defaults de 30 dias, bordas de 366/367 dias, borda do dia UTC, série com zeros, tops estáveis, bots, 404/200 expirado, `invalid-parameter` sem eco, 503 com Mongo pausado em ~8 s e ausência de `ipHash` |
| 02-08 | 6 | 6/6 ✓ | `ClickResilienceIT`, `ClickConsumerLifecycleIT` (4), `MetricsCardinalityIT`; fases `DEFAULT_PHASE - 4096`/`- 8192` e `timeout-per-shutdown-phase: 30s` declaradas no código (ordem garantida pelo Spring). Nota: no stop a fábrica dedicada é *parada* e só no `destroy` é destruída (desvio documentado na 02-08, que mantém a garantia de não fechar antes do ACK) |
| 02-09 | 9 | 9/9 ✓ | `RateLimitIT` (7), `TrustedProxyRateLimitIT` (3), `RateLimiterTest` (8, teto do `Retry-After` e índice da janela), `OpenApiIT#createLinkDocumentsSuccessAndErrors` (429) |

**Pontuação:** 73/74 truths verificados (1 presente, sem verificação de comportamento).

**⚠️ Truth presente, sem teste de comportamento (02-06, truth 6):** o ramo existe e está ligado (`reclaimIdle` → `acknowledgeVanished` com `XRANGE id id`, que só confirma IDs ausentes do stream). Testei à mão a semântica do Valkey (ver evidência 3). Mesmo assim, nenhum teste exercita a corrida entre duas réplicas, e um erro aqui poderia confirmar uma entrada que outra réplica ainda vai gravar, ou seja, perder um clique. Por isso o item vai para verificação humana.

### Artefatos obrigatórios

| Artefato | Esperado | Status | Detalhe |
|----------|----------|--------|---------|
| `cache/LinkCache.java` | Cache-aside, codec L/N/G, TTL limitado, métricas, fail-open | ✓ VERIFICADO | 285 linhas; `lp:link:`, `split("\\|", 3)`, gate em toda leitura e escrita |
| `cache/CacheEntry.java` | sealed Found/Missing/Gone | ✓ VERIFICADO | usado no `switch` do `RedirectService` |
| `cache/ValkeyAvailability.java` | Janela de bypass compartilhada | ✓ VERIFICADO (WR-01) | `AtomicLong`, sem `synchronized`; não tem sonda half-open |
| `config/RedisConfig.java` | `REJECT_COMMANDS` e bean do gate | ✓ VERIFICADO | — |
| `link/RedirectService.java` | Cache → Postgres sem transação | ✓ VERIFICADO | 0 `@Transactional` |
| `click/ClickPublisher.java` | Executor limitado, `MAXLEN ~`, fail-open | ✓ VERIFICADO | `approximateTrimming(true)`, `ipHasher.hash(` na thread do publisher |
| `common/IpHasher.java` | HMAC-SHA256 com dia UTC | ✓ VERIFICADO | mensagem fixa, sem eco da chave |
| `click/ReferrerNormalizer.java` | Só host ou `(direct)` | ✓ VERIFICADO | smoke: `news.example.com` |
| `click/ClickStreamConsumer.java` | Loop próprio, XACK depois do write, reclaim, gauges, stop gracioso | ✓ VERIFICADO | 689 linhas, `implements SmartLifecycle`, `XClaimOptions`, `linkpulse.clicks.stream.pending` |
| `click/MongoClickEventWriter.java` | Bulk UNORDERED que ignora só o 11000 | ✓ VERIFICADO | — |
| `click/ClickDocument.java` | Documento de `click_events` sem IP nem UA cru | ✓ VERIFICADO | smoke conferiu os campos |
| `click/UserAgentClassifier.java` / `ClickIndexes.java` / `config/MongoConfig.java` | Classificação, índices, timeouts | ✓ VERIFICADO | — |
| `stats/*` (Controller, Service, Period, Response, Problems) | `/links/{code}/stats` | ✓ VERIFICADO | `$facet`, `MAX_DAYS = 366`, `"timezone", "UTC"` |
| `ratelimit/*` + `config/WebMvcConfig.java` | Rate limit só no POST /links | ✓ VERIFICADO | `addPathPatterns("/links")`, `PEXPIRE`, `/problems/rate-limited` |
| `compose.dev.yaml` | Valkey 8.1 + Mongo 8.0 | ✓ VERIFICADO | o smoke subiu os 3 serviços com healthcheck |
| ITs (`CacheIT`, `ValkeyOutageIT`, `ClickPublishIT`, `ClickPipelineIT`, `ClickRedeliveryIT`, `ClickResilienceIT`, `ClickConsumerLifecycleIT`, `MetricsCardinalityIT`, `StatsIT`, `RateLimitIT`, `TrustedProxyRateLimitIT`) | Linhas mínimas | ✓ VERIFICADO | todos acima do `min_lines` e verdes no Failsafe |

### Verificação de ligações (key links)

| De | Para | Via | Status |
|----|------|-----|--------|
| `RedirectController` | `RedirectService` | `redirectService.resolve(code)` | ✓ LIGADO |
| `RedirectService` | `LinkCache` / `LinkService` | `linkCache.get(code)` e, no miss, `linkService.find(code)` | ✓ LIGADO |
| `LinkService.create` | `LinkCache` | `registerSynchronization` → `afterCommit` → `linkCache.evict(code)` | ✓ LIGADO |
| `RedirectController` | `ClickPublisher` | `HttpMethod.GET.matches(request.getMethod())` depois do `resolve` | ✓ LIGADO |
| `ClickPublisher` | `IpHasher` / stream | `ipHasher.hash(` e `XAddOptions.maxlen(...).approximateTrimming(true)` | ✓ LIGADO |
| `LinkCache` / `ClickPublisher` / `RateLimiter` | `ValkeyAvailability` | `allowRequest()` / `recordFailure()` | ✓ LIGADO |
| `RedisConfig` | `LinkPulseProperties` | `properties.cache().bypassCooldown()` | ✓ LIGADO |
| `ClickConfig` | `ClickStreamConsumer` | `dedicatedConnectionFactory(details, block)`, `new ClickIndexes(`, `reclaimInterval()` | ✓ LIGADO |
| `ClickStreamConsumer` | grupo / writer / índices | `ReadOffset.lastConsumed()`, `writer.write(` antes do `acknowledge`, `indexes.ensure()`, `pending(settings.streamKey()`, `claim(`, `groups(settings.streamKey())` | ✓ LIGADO |
| `ClickDocument` | `UserAgentClassifier` | `UserAgentClassifier.classify(` | ✓ LIGADO |
| `StatsController` | `StatsService` | `statsService.stats(` e, no service, `existsByCode(code)` | ✓ LIGADO |
| `GlobalExceptionHandler` | `/stats` | `handleTypeMismatch` → `invalid-parameter` | ✓ LIGADO |
| `WebMvcConfig` | `RateLimitInterceptor` | `addPathPatterns("/links")` | ✓ LIGADO |
| `RateLimitInterceptor` | `RemoteIpValve` | só `request.getRemoteAddr()`; `forward-headers-strategy: native` | ✓ LIGADO |
| `RateLimiter` | `IpHasher` | `ipHasher.hash(` na chave `lp:rl:` | ✓ LIGADO |

### Rastreamento do fluxo de dados (nível 4)

| Artefato | Dado | Fonte | Dado real | Status |
|----------|------|-------|-----------|--------|
| 302 do `GET /{code}` | `Location` | Valkey `lp:link:` (hit) ou Postgres `links` (miss) | Sim: o CacheIT apaga a linha e o 302 continua; smoke miss 1 → hit 3 | ✓ FLUINDO |
| `click_events` | documento do clique | stream `lp:clicks` ← `ClickPublisher` ← request | Sim: smoke com referrer, browser, os e `ipHash` vindos do request real | ✓ FLUINDO |
| `/stats` | total, daily, tops | aggregate `$match` + `$facet` em `click_events` | Sim: smoke com total 4 e tops derivados dos GETs do smoke | ✓ FLUINDO |
| gauges pending/lag/length | `AtomicLong` | `XINFO GROUPS` / `XLEN` no loop | Sim: smoke `stream_length` 3, pending 0, lag 0 | ✓ FLUINDO |

### Spot-checks de comportamento

| Comportamento | Comando | Resultado | Status |
|---------------|---------|-----------|--------|
| Suíte completa no HEAD (execução do orquestrador) | leitura de `target/{surefire,failsafe}-reports/*.xml` | 225 unitários e 103 ITs, 0 falhas e 0 erros | ✓ PASS |
| Redirect com Valkey parado (SC2 literal) | smoke com `docker compose stop valkey` | 8× 302 em 11-16 ms; 404 em 32 ms | ✓ PASS |
| HEAD não gera clique | smoke: 3 GET + 1 HEAD | `published` = 3 | ✓ PASS |
| Pipeline até o Mongo e índices | `mongosh` no Mongo do compose | documentos sem IP nem UA cru; `code_ts` e `ts_ttl` (7776000) | ✓ PASS |
| `/stats` real | `curl /links/demo-link/stats` | 200, série de 30 dias, tops com host/Chrome/Windows | ✓ PASS |
| Semântica do XCLAIM na corrida | `valkey-cli` em `valkey/valkey:8.1` efêmero | XCLAIM da 2ª réplica vazio; `XRANGE` presente | ✓ PASS (primitiva do servidor; o caminho Java segue sem teste) |

### Execução de probes

Nenhum probe declarado (`scripts/*/tests/probe-*.sh` não existe; fase de aplicação, não de tooling). Step 7c: não se aplica.

### Cobertura de requisitos

| Requisito | Plano(s) | Descrição | Status | Evidência |
|-----------|----------|-----------|--------|-----------|
| REDIR-03 | 02-01 | Cache-aside, TTL = min(padrão, até expirar), negative cache invalidado | ✓ ATENDIDO | `CacheIT` (6) |
| REDIR-04 | 02-01, 02-03 | Hit/miss em métrica | ✓ ATENDIDO | `linkpulse_cache_requests_total{result}` |
| REDIR-05 | 02-03 | Fail-open via Postgres, problema em métrica | ✓ ATENDIDO | `ValkeyOutageIT` e smoke com stop (WR-01 em verificação humana) |
| REDIR-06 | 02-02 | HEAD não conta clique | ✓ ATENDIDO | `ClickPublishIT#headRequestDoesNotPublishClick` e smoke |
| CLICK-01 | 02-02, 02-03 | Publicação sem bloquear, fail-open, descarte contado, `MAXLEN ~` | ✓ ATENDIDO | `ClickPublisherTest` (7) e `ValkeyOutageIT` |
| CLICK-02 | 02-04, 02-05 | Lote, `click_events`, XACK depois da escrita | ✓ ATENDIDO | `ClickPipelineIT` e `ClickRedeliveryIT` |
| CLICK-03 | 02-04, 02-06 | Idempotente por `_id` = ID do stream | ✓ ATENDIDO | `deadConsumerPendingIsReprocessedWithoutDuplicates` |
| CLICK-04 | 02-06, 02-08 | Recupera pendentes; consumer não para | ✓ ATENDIDO (corrida entre réplicas em verificação humana) | `ClickRedeliveryIT`, `ClickResilienceIT` |
| CLICK-05 | 02-08 | Shutdown conclui o lote | ✓ ATENDIDO | `stopWaitsForInFlightBatch` |
| CLICK-06 | 02-02, 02-04 | IP só como hash com segredo | ✓ ATENDIDO | `IpHasher` (HMAC + dia UTC); smoke |
| CLICK-07 | 02-05 | `isBot` e navegador/SO | ✓ ATENDIDO | `UserAgentClassifierTest` (35) |
| CLICK-08 | 02-08 | Lag e pendentes como métricas | ✓ ATENDIDO | gauges e `pendingLagAndLengthGaugesFollowTheGroup` |
| STAT-01..04 | 02-07 | Total com from/to, por dia, tops, bots opcionais | ✓ ATENDIDO | `StatsIT` (12) e smoke |
| RATE-01 | 02-09 | Limite por IP atômico, 429 + `Retry-After` | ✓ ATENDIDO | `RateLimitIT` (WR-05: o limite é por endereço IPv6 exato, não por /64) |
| RATE-02 | 02-09 | IP real só de proxies confiáveis | ✓ ATENDIDO | `TrustedProxyRateLimitIT` |
| DATA-02 | 02-05 | Índices `{code, ts}` e TTL explícitos | ✓ ATENDIDO | `ClickIndexes` e smoke |
| QUAL-02 | todos | ITs com Testcontainers (PG + Mongo + Valkey) | ✓ ATENDIDO | 103 ITs verdes |
| OBS-01 | 02-01/02/03/04/08/09 | Micrometer/Prometheus sem alta cardinalidade | ✓ ATENDIDO | `MetricsCardinalityIT` e smoke |

Os 21 IDs do ROADMAP aparecem no campo `requirements` de algum plano. **Nenhum requisito órfão.**

### Proibições (`must_haves.prohibitions`)

- **Nível test:** as 18 proibições de nível test têm teste de enforcement que passa. São elas: cache depois da expiração; IP, Referer, HEAD e eco da chave (02-02); 5xx/atraso, bloqueio da publicação e `toString` (02-03); IP e UA crus (02-04 e 02-05); poison message; XACK antes da escrita; bots e `ipHash` no `/stats`; tags; `X-Forwarded-For` não confiável; rate limit fora do POST; IP na chave do rate limit. Nenhuma ficou sem enforcement.
- **Nível judgment:**
  - 02-01 (tags de métrica) e 02-04 (XACK antes da escrita) também ficaram cobertas por teste: `MetricsCardinalityIT` e `ClickRedeliveryIT`.
  - Três ficaram só com o veredito do verificador, **não autoritativo**: índices fora da inicialização dos beans, sem limite de reentregas e scrape sem I/O. O veredito é de que as três se sustentam no código, e elas foram para a verificação humana (item 3).

### Anti-padrões e achados da revisão relevantes para os critérios

| Arquivo | Linha | Padrão | Severidade | Impacto |
|---------|-------|--------|------------|---------|
| `cache/ValkeyAvailability.java` | 16-17, 56-58 | WR-01: o Javadoc e o README dizem "só a primeira chamada de cada janela paga", mas a janela vencida libera todas as threads concorrentes | ⚠️ Warning | Não derruba o SC2 (`docker stop` deu 302 em 11-16 ms, porque o `REJECT_COMMANDS` falha na hora). Com o Valkey pausado ou particionado e carga concorrente, há picos de até ~150 ms a cada ~2,15 s. A documentação está imprecisa → decisão humana |
| `LinkCache`, `ClickPublisher`, `RateLimiter` | — | WR-02: qualquer exceção (OOM, WRONGTYPE) fecha o gate global | ⚠️ Warning | Em OOM do Valkey, o cache é desviado em ciclos e o rate limit fica em fail-open; os critérios continuam valendo |
| `RedirectController.java` | 60 | WR-03: a regex do código não tem tamanho máximo, e o negative cache pode ser amplificado | ⚠️ Warning | Risco de DoS de memória no Valkey; fora dos critérios |
| `StatsPeriod.java` | 62-73 | WR-04: datas extremas (`+999999999-12-31`) viram 500 | ⚠️ Warning | Entrada inválida não vira 400; o contrato do `/stats` é cumprido para entradas normais |
| `RateLimiter.java` | 116-124 | WR-05: o limite é por endereço IPv6 exato | ⚠️ Warning | RATE-01 é cumprido ao pé da letra (por IP); um cliente com /64 burla o limite |
| `ClickStreamConsumer.java` | 288-346 | WR-06: `start()` logo depois de `stop(callback)` não espera o loop anterior | ⚠️ Warning | Só aparece em restart de contexto; o shutdown normal (CLICK-05) está provado |
| `ClickStreamConsumer.java` | 252-274 | WR-07: a fábrica dedicada não propaga TLS | ⚠️ Warning | Quebraria o pipeline no ElastiCache com criptografia em trânsito. A Phase 5 só valida o Terraform (sem deploy real), então hoje não afeta nada, mas convém registrar como pré-requisito |
| — | — | Marcadores TBD/FIXME/XXX/TODO | — | Nenhum nos 61 arquivos da fase |

Os achados IN-01..IN-13 da revisão são informativos e não afetam nenhum critério. Um detalhe do smoke: um documento antigo de `demo-link` no volume de dev, sem `isBot`, aparece como `(unknown)` e conta como humano. É o comportamento documentado (`$ne: true`) e só existe no volume de dev.

### Verificação humana necessária

#### 1. Corrida XPENDING/XCLAIM entre duas réplicas
**Teste:** revisar `ClickStreamConsumer.reclaimIdle`/`acknowledgeVanished`. Opcionalmente, criar um IT com dois consumers e um `minIdle` controlado.
**Esperado:** um ID pedido ao XCLAIM que não voltou e que ainda existe no stream nunca recebe XACK nem conta como `lost`.
**Por que humano:** é um invariante de ordenação entre processos, sem IT determinístico. A primitiva do Valkey foi conferida pelo verificador com `valkey-cli`.

#### 2. Decisão sobre o WR-01 (gate sem sonda única)
**Teste:** decidir se picos de até ~150 ms, periódicos e só com o Valkey pausado ou particionado sob carga concorrente, são aceitáveis.
**Esperado:** aceitar e corrigir o Javadoc e o README, ou implementar o half-open com sonda única via CAS.
**Por que humano:** é decisão de produto. O critério literal (`docker stop`) passou, mas a documentação afirma uma garantia que o código não dá.

#### 3. Três proibições de nível judgment
**Teste:** confirmar no código que (a) `ClickIndexes` não toca o Mongo na criação do bean; (b) não há limite de reentregas; (c) as gauges só leem memória no scrape.
**Esperado:** o veredito do verificador, não autoritativo, é de que as três se sustentam.
**Por que humano:** proibições de nível judgment pedem resolução humana explícita.

#### 4. Leitura do README como avaliador
**Teste:** ler as seções de Valkey fora, estatísticas, rate limit, erros e riscos.
**Esperado:** texto correto, com IN-13 (browser/os/isBot) e WR-01 ajustados.
**Por que humano:** clareza de documentação é julgamento.

### Resumo das lacunas

Nenhuma lacuna bloqueante. Os 5 critérios de sucesso do ROADMAP foram comprovados com evidência própria: código lido, relatórios do Failsafe e do Surefire da execução no HEAD e um smoke de dev com `docker compose stop valkey`, além da inspeção do Mongo e do `/actuator/prometheus`. Os 21 requisitos estão cobertos, sem órfãos. A fase fica em `human_needed` por causa de:

- um truth da 02-06 presente e ligado, mas sem teste de comportamento (a corrida entre réplicas no reclaim);
- uma decisão sobre o WR-01 da revisão, em que a documentação do gate promete mais do que o código entrega sob pausa com carga concorrente;
- três proibições de nível judgment;
- a leitura humana do README.

Os warnings WR-02..WR-07 da revisão não derrubam nenhum critério. Vale planejá-los como endurecimento, e o WR-07 (TLS) deve ficar registrado como pré-requisito antes de qualquer deploy real no ElastiCache.

---

_Verificado em: 2026-10-11T21:20:00Z_
_Verificador: Claude (gsd-verifier)_
