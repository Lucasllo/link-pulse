---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
plan: 08
subsystem: pipeline-de-cliques
tags: [valkey, redis-streams, mongodb, micrometer, prometheus, smartlifecycle, testcontainers, resiliencia]

requires:
  - phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
    provides: "ClickStreamConsumer com reclaim XPENDING IDLE + XCLAIM (02-06), ClickPublisher (02-04), timeouts do MongoConfig (02-05), /links/{code}/stats (02-07)"
provides:
  - "Gauges linkpulse_clicks_stream_pending / _lag / _length, atualizadas pelo loop do consumer (nunca no scrape)"
  - "Log WARN na primeira falha de uma sequência e INFO na recuperação, sem dados de clique"
  - "stop() do consumer com espera limitada que lança IllegalStateException se a thread prender"
  - "Consumer e publisher reiniciáveis depois do stop (pausa/retomada de contexto do Spring Framework 7)"
  - "ClickResilienceIT (queda do MongoDB), ClickConsumerLifecycleIT (shutdown, gauges, restart) e MetricsCardinalityIT (/actuator/prometheus real)"
affects: [phase-03-observabilidade, dashboards-grafana, alerta-de-lag, kind, compose]

actuals:
  tokens: 11484
  tasks: 3
  commits: 6
plan_head_before: b59f627ad3a8993656cada419a0e7fdbd74bf911

tech-stack:
  added: []
  patterns:
    - "Gauge sobre AtomicLong (lag como bits de double) atualizada pela thread de trabalho no máximo uma vez por BLOCK; o scrape só lê memória"
    - "Beans SmartLifecycle reiniciáveis: o stop libera recursos (fábrica parada, executor desligado) e o start os recria; o destroy é o único passo terminal"
    - "IT de cardinalidade sobre o /actuator/prometheus real (RANDOM_PORT + management.server.port=0), com lista de chaves de tag permitidas"

key-files:
  created:
    - app/src/test/java/dev/linkpulse/click/ClickResilienceIT.java
    - app/src/test/java/dev/linkpulse/click/ClickConsumerLifecycleIT.java
    - app/src/test/java/dev/linkpulse/MetricsCardinalityIT.java
  modified:
    - app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java
    - app/src/main/java/dev/linkpulse/click/ClickPublisher.java
    - app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java

key-decisions:
  - "spring-boot-micrometer-metrics-test NÃO foi adicionado: no Boot 4.1.1 o /actuator/prometheus já responde num @SpringBootTest RANDOM_PORT sem @AutoConfigureMetrics (contingência prevista no plano); pom.xml sem mudança"
  - "O consumer para a fábrica Lettuce dedicada (fecha conexões) quando o loop sai e só a destrói no destroy do bean; o start a reabre. Antes ela era destruída no stop e o consumer não podia ser reiniciado"
  - "O ClickPublisher cria um executor novo no start se o stop desligou o anterior; a gauge da fila lê o executor corrente via referência forte ao publisher"
  - "As gauges do stream são do grupo (globais): no dashboard e no alerta de lag da Phase 3 usar max(), nunca sum() (Pitfall P11)"
  - "O parse das tags na MetricsCardinalityIT lê as chaves antes de cada =\" (e não pela regex \\{([^}]*)\\} do plano), porque uri=\"/{code:...}\" tem } dentro do valor"

patterns-established:
  - "Log de falha em loop de background: WARN só na primeira falha da sequência (classe da exceção, nunca getMessage()), INFO com o número de falhas na recuperação"
  - "Todo SmartLifecycle novo precisa sobreviver a stop() + start() (pausa de contexto do Spring 7); testar com um IT de restart"

requirements-completed: [CLICK-04, CLICK-05, CLICK-08, OBS-01, QUAL-02]

coverage:
  - id: D1
    description: "Com o MongoDB pausado, GET /{code} segue 302, linkpulse.clicks.consumer.errors sobe, o loop não morre e, depois do unpause, os 3 cliques viram exatamente 3 documentos (sem duplicata 3 s depois)"
    requirement: CLICK-04
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickResilienceIT.java#consumerSurvivesMongoOutageAndCatchesUp"
        status: pass
    human_judgment: false
  - id: D2
    description: "stop(callback) com um lote em andamento volta na hora, o callback só roda depois de o lote ser gravado e confirmado (pending 0), isRunning() false"
    requirement: CLICK-05
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickConsumerLifecycleIT.java#stopWaitsForInFlightBatch"
        status: pass
    human_judgment: false
  - id: D3
    description: "Gauges linkpulse.clicks.stream.pending/length/lag acompanham o grupo (4/4/0 com writer que falha); lagValue(null) e lagValue(\"x\") são NaN"
    requirement: CLICK-08
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickConsumerLifecycleIT.java#pendingLagAndLengthGaugesFollowTheGroup"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickConsumerLifecycleIT.java#lagGaugeIsNanWhenServerReportsNull"
        status: pass
    human_judgment: false
  - id: D4
    description: "/actuator/prometheus real expõe o catálogo da fase e nenhuma linha linkpulse_/http_server_requests carrega código, URL, referrer, user-agent ou IP; chaves de tag linkpulse_* em {result, operation, outcome}"
    requirement: OBS-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/MetricsCardinalityIT.java#prometheusExposesLowCardinalityMetrics"
        status: pass
      - kind: other
        ref: "smoke de dev: compose.dev.yaml + jar --spring.profiles.active=dev + GET /demo-link + scrape :8081/actuator/prometheus (persisted_total 1.0, stream_pending/lag presentes, sem demo-link)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Consumer e publisher voltam a funcionar depois de stop() + start() (pausa/retomada de contexto do Spring Framework 7)"
    requirement: QUAL-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickConsumerLifecycleIT.java#consumerResumesAfterStopAndStart"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java#publishesAgainAfterStopAndStart"
        status: pass
      - kind: integration
        ref: "cd app && ./mvnw -B -ntp verify (215 unit + 93 IT, Checkstyle e SpotBugs verdes)"
        status: pass
    human_judgment: false

duration: 28min
completed: 2026-10-11
status: complete
---

# Phase 2 Plan 08: Resiliência do pipeline e métricas do stream Summary

**O consumer de cliques sobrevive a uma queda do MongoDB sem perder nem duplicar cliques, conclui o lote em andamento no shutdown e expõe pending/lag/length do grupo como gauges baratas. Uma IT sobre o `/actuator/prometheus` real prova o catálogo de métricas da fase sem tags de alta cardinalidade.**

## Performance

- **Duration:** 28 min
- **Started:** 2026-10-11T20:02:18Z
- **Completed:** 2026-10-11T20:30:07Z
- **Tasks:** 3
- **Files modified:** 6 (3 criados, 3 alterados)

## Accomplishments

- **CLICK-04:** `ClickResilienceIT` pausa o container do MongoDB (`docker pause`) durante 3 redirects. Os 3 respondem 302, o contador de erros do consumer sobe e, depois do `unpause`, os 3 cliques viram exatamente 3 documentos. **O teste passou já no RED (caracterização):** o desenho das 02-04/02-06 (catch único, backoff e reclaim) já cobria o caso. O GREEN acrescentou só a observabilidade: WARN na primeira falha da sequência e INFO na recuperação. Medido: MongoDB pausado por ~5,2 s e **recuperação em ~0,5 s depois do unpause** (reclaim de 1 s nos ITs).
- **CLICK-05:** `stopWaitsForInFlightBatch` trava o writer num latch, chama `stop(callback)` e confirma que o callback só roda depois de o lote ser gravado e confirmado. Também passou já no RED (o stop das 02-04/02-06 já era assim). O `stop()` sem argumento agora lança `IllegalStateException` se a thread não terminar em `block + 10 s`.
- **CLICK-08:** gauges `linkpulse.clicks.stream.pending`, `.lag` e `.length` sobre `AtomicLong`, atualizadas pelo loop no máximo uma vez por `BLOCK` (`XINFO GROUPS` + `XLEN`). O scrape só lê memória. `lagValue(null)` dá `NaN` (Pitfall P10).
- **OBS-01:** `MetricsCardinalityIT` sobe a API e a porta de gerenciamento em portas aleatórias e faz scrape HTTP real. Confere o catálogo e que nenhuma linha `linkpulse_*`/`http_server_requests*` contém código, URL, host ou query do Referer, user-agent ou IP. As chaves de tag ficam em `{result, operation, outcome}`, e o `uri` do `http.server.requests` é o template da rota (`/{code:[A-Za-z0-9_-]+}`).
- **Restart (bug latente encontrado nesta plan):** consumer e publisher agora sobrevivem a `stop()` + `start()`. Ver Deviations.

## Nomes finais das métricas (formato Prometheus, para a Phase 3)

| Prometheus | Tipo | Tags |
|---|---|---|
| `linkpulse_cache_requests_total` | counter | `result` = hit, miss, negative_hit, error, bypass |
| `linkpulse_cache_errors_total` | counter | `operation` = get, put, evict |
| `linkpulse_valkey_unavailable_total` | counter | — |
| `linkpulse_clicks_publish_total` | counter | `result` = published, rejected, failed |
| `linkpulse_clicks_publish_queue` | gauge | — |
| `linkpulse_clicks_persisted_total`, `_duplicates_total`, `_invalid_total`, `_claimed_total`, `_lost_total` | counter | — |
| `linkpulse_clicks_consumer_errors_total` | counter | — |
| `linkpulse_clicks_consumer_batch_count` / `_sum` / `_max` | summary | — |
| `linkpulse_clicks_consumer_write_seconds_count` / `_sum` / `_max` | timer | `outcome` = success, failure |
| `linkpulse_clicks_stream_pending` | gauge (do grupo: usar `max()`) | — |
| `linkpulse_clicks_stream_lag` | gauge (do grupo: usar `max()`; `NaN` = indefinido) | — |
| `linkpulse_clicks_stream_length` | gauge (do stream: usar `max()`) | — |
| `http_server_requests_seconds_bucket` / `_count` / `_sum` | histograma | `uri` (template), `method`, `status`, `outcome`, `exception`, `error` |

A `linkpulse_ratelimit_requests_total` (`result`) chega com a 02-09 e já passa pela lista de chaves permitidas da IT.

## Task Commits

1. **Task 1: Mongo cai durante uma rajada de redirects** (tracer)
   - `e5b0ce4` test(02-08): consumer sobrevive à queda do Mongo (RED de caracterização: passou)
   - `0fa3df3` feat(02-08): consumer resiliente à queda do Mongo, com log de falha e de recuperação
2. **Task 2: Shutdown conclui o lote e gauges do grupo** (TDD)
   - `0a000e3` test(02-08): shutdown com lote em andamento e gauges do grupo (RED)
   - `9fec9e8` feat(02-08): gauges de pending, lag e tamanho do stream e shutdown que conclui o lote (GREEN)
3. **Task 3: /actuator/prometheus sem tags de alta cardinalidade** (TDD)
   - `bbaf276` test(02-08): /actuator/prometheus sem tags de alta cardinalidade (passou no primeiro run; contingência)
4. **Desvio (Rule 1/3):** `14e5c0f` fix(02-08): consumer e publisher de cliques reiniciáveis depois do stop

## TDD Gate Compliance

- **Task 2:** RED `0a000e3` → GREEN `9fec9e8`. RED válido, com as asserções do comportamento falhando. `pendingLagAndLengthGaugesFollowTheGroup` deu `[gauge linkpulse.clicks.stream.pending] Expecting actual not to be null within 15 seconds` e `lagGaugeIsNanWhenServerReportsNull` deu `expected: NaN but was 0.0`. Para o RED compilar, o commit de teste incluiu um `lagValue` placeholder que devolvia 0, trocado no GREEN. `stopWaitsForInFlightBatch` passou no RED (caracterização). O `gsd check tdd-red-evidence` não se aplica: ele só lê saída node:test/TAP, não relatórios do Failsafe; a evidência acima saiu de `target/failsafe-reports`.
- **Task 3:** só o commit `test(...)`, sem `feat(...)`. O teste passou no primeiro run porque o comportamento (export do Prometheus e tags de baixa cardinalidade) já existia. O plano prevê esse caso: não adicionar a dependência e registrar aqui.

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java`: gauges do stream, `refreshGaugesIfDue()`, `lagValue(Object)`, logs WARN/INFO, `stop()` com prazo, fábrica parada no stop e reaberta no start, Javadoc das fases de parada.
- `app/src/main/java/dev/linkpulse/click/ClickPublisher.java`: executor recriado no `start()` depois de um stop; a gauge da fila lê o executor corrente.
- `app/src/test/java/dev/linkpulse/click/ClickResilienceIT.java`: queda do MongoDB com o consumer do contexto.
- `app/src/test/java/dev/linkpulse/click/ClickConsumerLifecycleIT.java`: shutdown com lote em andamento, gauges do grupo, `lagValue` e restart.
- `app/src/test/java/dev/linkpulse/MetricsCardinalityIT.java`: scrape real do `/actuator/prometheus`, catálogo e cardinalidade.
- `app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java`: regressão `publishesAgainAfterStopAndStart`.

## Decisions Made

- **Sem `spring-boot-micrometer-metrics-test`:** o `/actuator/prometheus` respondeu 200 com o catálogo completo no `@SpringBootTest(RANDOM_PORT)` sem a dependência e sem `@AutoConfigureMetrics`. Pela contingência do plano, o `pom.xml` não mudou (T-02-SC6 sem superfície nova).
- **Bean do container do MongoDB:** o IT injeta `@Qualifier("mongoContainer")`, nome real do bean no `TestcontainersConfiguration`, em vez do `"mongo"` do plano (que é o `MongoClient` do Boot).
- **Gauges do grupo com `max()`** no dashboard e no alerta (Pitfall P11). Isso está registrado no Javadoc da classe.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Consumer e publisher não sobreviviam a stop() + start()**
- **Found during:** Task 3 (`./mvnw -B -ntp verify` completo)
- **Issue:** `StatsIT.statsCountRedirectsOfTheLink` ficava com 0 cliques em 30 s. O Spring Framework 7 pausa os contextos de teste em cache (stop dos `SmartLifecycle`) e os retoma (start). A `MetricsCardinalityIT` abre um segundo contexto completo, então o contexto compartilhado dos ITs foi pausado e retomado. Na volta, o consumer usava a fábrica Lettuce que tinha destruído no stop (`IllegalStateException: LettuceConnectionFactory was destroyed and cannot be used anymore`, stack capturado com log temporário, já revertido). O publisher seguia com o executor desligado e rejeitava todo clique. O mesmo vale para um `ConfigurableApplicationContext.stop()/start()` em produção.
- **Fix:** o `ClickStreamConsumer` passa a parar a fábrica dedicada (`Lifecycle.stop()`, que fecha as conexões e o cliente) só depois de o loop sair e a abre de novo no `start()`; o `destroy()` do bean é o único passo terminal. O `ClickPublisher` cria um executor novo no `start()` se o anterior foi desligado, e a gauge da fila lê o executor corrente.
- **Files modified:** `ClickStreamConsumer.java`, `ClickPublisher.java` (fora dos `files_modified` do plano, mas também fora dos arquivos da 02-09), `ClickConsumerLifecycleIT.java`, `ClickPublisherTest.java`
- **Verification:** `ClickConsumerLifecycleIT#consumerResumesAfterStopAndStart`, `ClickPublisherTest#publishesAgainAfterStopAndStart` e a sequência `RedirectIT → MetricsCardinalityIT → OpenApiIT → StatsIT` (antes falhava, agora passa); `./mvnw -B -ntp verify` verde.
- **Committed in:** `14e5c0f`
- **Nota sobre o must-have:** a frase "só então a fábrica Lettuce dedicada é destruída" agora vale para o fim da vida do bean. No stop, a fábrica é **parada** (conexões fechadas) depois do lote e do ACK, e destruída no `destroy` logo a seguir, no fechamento do contexto. A garantia de não fechar a conexão antes do ACK continua igual.

**2. [Rule 3 - Blocking] Checkstyle**
- **Found during:** Tasks 1, 2 e 3
- **Issue:** `AbbreviationAsWordInName` em `failuresInARow`, ordem de import do `StreamInfo` e `VariableDeclarationUsageDistance` em `managementPort`.
- **Fix:** renomeado para `consecutiveFailures`, import reordenado e `final int managementPort`.
- **Committed in:** `0fa3df3`, `9fec9e8`, `bbaf276`

---

**Total deviations:** 2 auto-fixed (1 bug, 1 bloqueio de estilo)
**Impact on plan:** o desvio 1 corrige um bug real de ciclo de vida das 02-04/02-06 que só aparecia com mais de um contexto completo; sem ele, a suíte da fase ficava vermelha. Sem mudança de escopo.

## Issues Encountered

- O `gsd check tdd-red-evidence` não sabe ler saída do Maven/Failsafe (só node:test/TAP). A evidência do RED foi registrada a partir dos relatórios do Failsafe (ver TDD Gate Compliance).
- Um heredoc com Python no Git Bash quebrou por causa das aspas; as edições grandes foram feitas por script no scratchpad.

## Verification

- `./mvnw -B -ntp -Dit.test='ClickResilienceIT,ClickPipelineIT' verify`: verde
- `./mvnw -B -ntp -Dit.test='ClickConsumerLifecycleIT,ClickRedeliveryIT' verify`: verde
- `./mvnw -B -ntp -Dit.test=MetricsCardinalityIT verify`: verde
- `./mvnw -B -ntp verify`: **BUILD SUCCESS**, com 215 testes unitários, 93 ITs, Checkstyle e SpotBugs
- Smoke de dev (`compose.dev.yaml` + jar com profile dev): `GET /demo-link` deu 302. O scrape `:8081/actuator/prometheus` mostrou `linkpulse_clicks_persisted_total 1.0`, `linkpulse_clicks_stream_pending 0.0`, `linkpulse_clicks_stream_lag 0.0` e `linkpulse_clicks_stream_length 1.0`, sem `demo-link` em nenhuma linha `linkpulse_`/`http_server_requests`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Pronto para a 02-09 (rate limit): a `MetricsCardinalityIT` aceita `linkpulse_ratelimit_requests_total{result=...}` pela lista de chaves.
- Phase 3: as métricas da tabela acima são o insumo do dashboard de saúde do pipeline e do alerta de lag. Usar `max(linkpulse_clicks_stream_lag)` e `max(linkpulse_clicks_stream_pending)`. Tratar `NaN` do lag como "indefinido", não como zero.
- Edge assumptions do plano (CLICK-05, CLICK-08 e OBS-01, sem SPEC) seguem como assumidas: "concluir o lote" é gravar e confirmar o lote já lido, e lag/pending são do grupo, atualizados a cada `BLOCK`.

---
*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Completed: 2026-10-11*

## Self-Check: PASSED

- Arquivos criados e alterados presentes no disco (5/5 conferidos).
- Commits e5b0ce4, 0fa3df3, 0a000e3, 9fec9e8, bbaf276 e 14e5c0f presentes no histórico.
- ClickConsumerLifecycleIT tem 233 linhas (mínimo 80) e MetricsCardinalityIT tem 170 (mínimo 60).
