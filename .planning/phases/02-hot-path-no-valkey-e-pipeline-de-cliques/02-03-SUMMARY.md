---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
plan: 03
subsystem: cache
tags: [valkey, lettuce, fail-open, redis-streams, micrometer, prometheus, spring-boot]

requires:
  - phase: 02-01
    provides: LinkCache (cache-aside explícito com hit/miss/negative_hit) e o hot path do redirect
  - phase: 02-02
    provides: ClickPublisher (executor limitado, XADD MAXLEN ~), IpHasher e linkpulse.ip-hash-key
provides:
  - ValkeyAvailability, gate de bypass do Valkey (cooldown de 2 s) compartilhado por cache, publicação e rate limit (02-09)
  - RedisConfig com Lettuce REJECT_COMMANDS e timeouts curtos (150 ms de comando, 250 ms de conexão)
  - LinkCache com fail-open (result=error|bypass, linkpulse.cache.errors{operation})
  - ClickPublisher com gate, result=failed, rejected com o publisher parado e gauge linkpulse.clicks.publish.queue
  - /actuator/prometheus na porta 8081 com o histograma de http.server.requests
  - LinkPulseProperties.toString() com ipHashKey=****
affects: [02-08 cardinalidade de métricas, 02-09 rate limit, phase-03 dashboards e alertas]

actuals:
  tokens: 13747   # chars/4 do diff realizado (54987 caracteres em 2bb154f..HEAD)
  tasks: 2
  commits: 4
plan_head_before: 2bb154f303d22cf5b7aeb6261c874eee89013ba4

tech-stack:
  added: []
  patterns:
    - "Gate de disponibilidade: AtomicLong openUntil, sem monitor (sem pinning de virtual thread); recordFailure conta uma vez por janela"
    - "Fail-open em quatro camadas: timeout curto do Lettuce, REJECT_COMMANDS, gate com cooldown e catch RuntimeException contado"
    - "Publicação nunca lança: rejected (fila cheia, parado, sobras do stop) e failed (Valkey falhou ou gate fechado)"
    - "ITs de queda pausam o container (docker pause), aquecem o caminho antes e fazem unpause + espera do gate no finally"
    - "RED com esqueleto de API sem efeito para o teste falhar por asserção, não por compilação"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/cache/ValkeyAvailability.java
    - app/src/main/java/dev/linkpulse/config/RedisConfig.java
    - app/src/test/java/dev/linkpulse/cache/ValkeyAvailabilityTest.java
    - app/src/test/java/dev/linkpulse/cache/ValkeyOutageIT.java
    - app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java
  modified:
    - app/src/main/java/dev/linkpulse/cache/LinkCache.java
    - app/src/main/java/dev/linkpulse/click/ClickPublisher.java
    - app/src/main/java/dev/linkpulse/config/ClickConfig.java
    - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
    - app/src/main/resources/application.yml
    - app/src/test/java/dev/linkpulse/AbstractIT.java
    - app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java
    - README.md

key-decisions:
  - "ValkeyAvailability é o gate único do Valkey (cache, publicação e o rate limit da 02-09); o cooldown vem de linkpulse.cache.bypass-cooldown (2 s; 200 ms nos ITs)"
  - "LinkCache.evict sempre tenta, mesmo com o gate fechado: a staleness no caminho de escrita pesa mais que o custo de um timeout"
  - "ClickPublisher conta failed quando o gate está fechado (sem chamar o Valkey) e quando o XADD falha (fecha o gate); descartados = rejected + failed"
  - "publish com o publisher parado conta rejected sem submeter; stop que estoura 5 s cancela a fila e soma as sobras em rejected"
  - "LinkPulseProperties.toString() mascara qualquer ipHashKey não nula como ****"
  - "ITs de queda fazem um GET de aquecimento com o Valkey no ar antes da pausa, para medir a queda com a aplicação rodando e não a JVM fria"

patterns-established:
  - "Métricas sem dado pessoal: só tags result e operation (D-10)"
  - "Toda chamada ao Valkey passa por availability.allowRequest() e toda falha por availability.recordFailure()"

requirements-completed: [REDIR-04, REDIR-05, CLICK-01, OBS-01, QUAL-02]

coverage:
  - id: D1
    description: "Com o Valkey pausado, GET /{code} responde 302 via Postgres em menos de 1000 ms; error/bypass e linkpulse.valkey.unavailable aumentam"
    requirement: REDIR-05
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/cache/ValkeyOutageIT.java#redirectKeepsWorkingFastWhileValkeyIsPaused"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/cache/ValkeyAvailabilityTest.java"
        status: pass
    human_judgment: false
  - id: D2
    description: "No make run com o Valkey do compose parado, GET /demo-link segue 302 em menos de 0,5 s"
    requirement: REDIR-05
    verification:
      - kind: e2e
        ref: "smoke de dev da Task 1 (docker compose -f compose.dev.yaml stop valkey; 3x curl /demo-link)"
        status: pass
    human_judgment: false
  - id: D3
    description: "/actuator/prometheus (8081) expõe linkpulse_cache_requests_total por result e http_server_requests_seconds_bucket"
    requirement: OBS-01
    verification:
      - kind: e2e
        ref: "smoke de dev da Task 1 (target/prom.txt com result=\"hit\" >= 1 e o histograma)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Publicação limitada e contada: fila cheia e stop viram rejected, Valkey fora ou gate fechado viram failed, XADD com MAXLEN ~ 100000, gauge da fila"
    requirement: CLICK-01
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/cache/ValkeyOutageIT.java#clickPublishFailureIsCountedWhileValkeyIsPaused"
        status: pass
    human_judgment: false
  - id: D5
    description: "A chave do HMAC não vaza no toString de LinkPulseProperties; LINKPULSE_IP_HASH_KEY faz binding por env; defaults de linkpulse.clicks.*"
    requirement: QUAL-02
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java#toStringDoesNotExposeIpHashKey"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java#ipHashKeyBindsFromEnvironmentVariable"
        status: pass
    human_judgment: false
  - id: D6
    description: "Demonstração do avaliador: com o make run ligado, parar o Valkey e ver os 302 sem atraso e error/bypass crescendo no Prometheus"
    requirement: REDIR-04
    verification:
      - kind: manual_procedural
        ref: "human-check da Task 1 (checkpoint aprovado pelo usuário)"
        status: pass
    human_judgment: true
    rationale: "A percepção de 'sem atraso perceptível' na demonstração ao vivo é julgamento humano; o checkpoint da Task 1 foi aprovado"

duration: 22min
completed: 2026-10-11
status: complete
---

# Phase 02 Plan 03: Fail-open do Valkey e publicação com descarte contado Summary

**Com o Valkey parado ou pausado, o redirect segue 302 pelo Postgres em menos de 10 ms depois da primeira falha: timeouts de 150/250 ms no Lettuce, `REJECT_COMMANDS` e o gate `ValkeyAvailability`. A publicação de cliques passou a contar `failed` e `rejected` em vez de lançar, e o `/actuator/prometheus` serve o hit ratio e o histograma de latência.**

## Performance

- **Duration:** 22 min (as duas tasks, contando o checkpoint da Task 1)
- **Started:** 2026-10-11T15:34:22Z
- **Completed:** 2026-10-11T15:56:28Z
- **Tasks:** 2
- **Files modified:** 13

## Accomplishments

- **Gate `ValkeyAvailability`** (`dev.linkpulse.cache`): `allowRequest()` / `recordFailure()` sobre um `AtomicLong`, com janela de 2 s (`linkpulse.cache.bypass-cooldown`). `linkpulse.valkey.unavailable` conta uma vez por janela. É compartilhado pelo `LinkCache` e pelo `ClickPublisher`, e fica pronto para o rate limit da 02-09.
- **`RedisConfig`**: `LettuceClientOptionsBuilderCustomizer` com `DisconnectedBehavior.REJECT_COMMANDS` e o bean do gate. Nenhuma fábrica de conexão foi declarada (Pitfall P4).
- **`LinkCache` com fail-open**: gate fechado conta `result=bypass` sem chamar o Valkey; uma exceção conta `result=error` e `linkpulse.cache.errors{operation=get|put|evict}` e fecha o gate. Nenhuma exceção do Valkey sai do cache, e `evict` sempre tenta.
- **`ClickPublisher` com fail-open**: gate fechado ou XADD falhando contam `result=failed` (a falha fecha o gate). Com o publisher parado, `publish` conta `rejected` sem submeter. Um `stop()` que estoura 5 s soma as sobras da fila em `rejected`. A gauge nova é `linkpulse.clicks.publish.queue`.
- **`/actuator/prometheus`** exposto na porta 8081 (`health,info,prometheus`), com `percentiles-histogram` de `http.server.requests` para p95/p99.
- **`LinkPulseProperties.toString()`** mostra `ipHashKey=****`. O README e o `application.yml` documentam `LINKPULSE_IP_HASH_KEY` e as chaves `linkpulse.clicks.*`.

## Medições

- **`ValkeyOutageIT#redirectKeepsWorkingFastWhileValkeyIsPaused`** (5 GETs, Valkey pausado). Execução da Task 1: [161, 6, 5, 4, 4] ms. `verify` final: [171, 5, 6, 7, 7] ms. O primeiro GET paga o timeout de 150 ms do Lettuce; os seguintes ficam em bypass.
- **`ValkeyOutageIT#clickPublishFailureIsCountedWhileValkeyIsPaused`** (3 GETs com publicação, Valkey pausado): [169, 5, 5] ms, e `result=failed` aumentou dentro da pausa.
- **Smoke de dev na Task 1** (`docker compose stop valkey`): 302 em cerca de 10 a 15 ms, `result="error"` 1, `result="bypass"` 2, `cache_errors{operation="get"}` 1, `valkey_unavailable` 1.
- **Smoke de dev repetido no fim do plano**, com o código final:
  - 302 em 14 / 8 / 8 ms;
  - `linkpulse_clicks_publish_total{result="failed"} 3.0`, `published 2.0`, `rejected 0.0`;
  - `linkpulse_clicks_publish_queue 0.0`, `linkpulse_valkey_unavailable_total 1.0`;
  - `cache_requests` com hit 1, miss 1, error 1 e bypass 2.
- **`./mvnw -B -ntp verify` final:** BUILD SUCCESS, 161 unitários, 63 ITs, 0 violações de Checkstyle, 0 achados do SpotBugs.

## Contratos finais

```java
public LinkCache(StringRedisTemplate redis, ValkeyAvailability availability,
                 LinkPulseProperties properties, Clock clock, MeterRegistry registry);
public ClickPublisher(StringRedisTemplate redis, ValkeyAvailability availability, IpHasher ipHasher,
                      LinkPulseProperties.Clicks clicks, Clock clock, MeterRegistry registry);
public final class ValkeyAvailability {           // bean em RedisConfig.valkeyAvailability(...)
    public ValkeyAvailability(Duration cooldown, Clock clock, MeterRegistry registry);
    public boolean allowRequest();
    public void recordFailure();
}
```

A 02-09 (rate limit) deve injetar o mesmo bean `ValkeyAvailability`: consultar `allowRequest()` antes do script Lua e chamar `recordFailure()` em exceção.

## Respostas pedidas pelo plano (`<output>`)

- **Contingência A3 (`DockerClientFactory`):** não foi necessária. `valkey.getDockerClient().pauseContainerCmd(...)` e `unpauseContainerCmd(...)` funcionam.
- **Tempos medidos:** ver "Medições". O primeiro GET leva cerca de 160 a 170 ms (timeout do Lettuce mais o Postgres) e os seguintes, 4 a 8 ms (bypass).
- **Construtores finais e gate:** ver "Contratos finais".
- **Binding de `LINKPULSE_IP_HASH_KEY`:** funcionou com o nome padrão do relaxed binding; não precisou de outra forma (`LinkPulsePropertiesTest#ipHashKeyBindsFromEnvironmentVariable`).

## Task Commits

1. **Task 1: Valkey fora não atrasa nem derruba o redirect, e /actuator/prometheus** (tracer, checkpoint aprovado)
   - `e6c82ad` test(02-03): fail-open do redirect com o Valkey pausado (RED)
   - `e070c6e` feat(02-03): fail-open do Valkey com gate de disponibilidade e /actuator/prometheus (GREEN)
2. **Task 2: Publicação nunca atrasa nem derruba o redirect; chave do HMAC mascarada** (TDD)
   - `5c34062` test(02-03): backpressure, fail-open e descarte da publicação (RED)
   - `b229431` feat(02-03): publicação com fila limitada, fail-open e descarte contado (GREEN)

Não houve commit de REFACTOR: nenhuma limpeza foi necessária.

## TDD Gate Compliance

| Task | RED | GREEN | Evidência do RED |
|------|-----|-------|------------------|
| 1 | `e6c82ad` | `e070c6e` | Executor anterior; o RED falhou por asserção, com o esqueleto do `ValkeyAvailability` |
| 2 | `5c34062` | `b229431` | `check tdd-red-evidence` = `RED_EVIDENCE_OK` para as cinco metas (ver abaixo) |

As cinco metas do RED da Task 2:

- `ClickPublisherTest#queueGaugeReflectsPendingTasks`: a gauge não existia.
- `#valkeyFailureCountsFailedAndClosesTheGate`: `failed` esperado 1.0, veio 0.0.
- `#closedGateCountsFailedWithoutCallingValkey`: `failed` esperado 1.0, veio 0.0.
- `LinkPulsePropertiesTest#toStringDoesNotExposeIpHashKey`: o `toString` gerado continha a chave.
- `ValkeyOutageIT#clickPublishFailureIsCountedWhileValkeyIsPaused`: o `QueryTimeoutException` do XADD escapava sem contagem pela thread do publisher (o Awaitility reportou a exceção).

Os outros três casos do `ClickPublisherTest` (fila cheia, stop e MAXLEN) já passavam no RED. Eles documentam o comportamento entregue pela 02-02 e agora protegem esse comportamento contra regressão.

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/cache/ValkeyAvailability.java`: gate de bypass com cooldown.
- `app/src/main/java/dev/linkpulse/config/RedisConfig.java`: `REJECT_COMMANDS` e o bean do gate.
- `app/src/main/java/dev/linkpulse/cache/LinkCache.java`: bypass, error e `cache.errors`; evict sempre tenta.
- `app/src/main/java/dev/linkpulse/click/ClickPublisher.java`: gate, `failed`, `rejected` com o publisher parado, gauge da fila, sobras do stop.
- `app/src/main/java/dev/linkpulse/config/ClickConfig.java`: injeta o gate no publisher.
- `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java`: `toString()` com `ipHashKey=****`.
- `app/src/main/resources/application.yml`: timeouts do Lettuce, `repositories.enabled=false`, exposição do `prometheus`, histograma e comentários de `LINKPULSE_IP_HASH_KEY` e `linkpulse.clicks.*`.
- `app/src/test/java/dev/linkpulse/AbstractIT.java`: `linkpulse.cache.bypass-cooldown=200ms`.
- `app/src/test/java/dev/linkpulse/cache/ValkeyAvailabilityTest.java`: janela e contagem do gate.
- `app/src/test/java/dev/linkpulse/cache/ValkeyOutageIT.java`: os dois ITs de queda com `docker pause`.
- `app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java`: os seis casos unitários do publisher.
- `app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java`: defaults de clicks, binding por env e `toString` mascarado.
- `README.md`: Valkey em "Rodando local", a demonstração `stop valkey`, o Actuator com `prometheus` e a linha `LINKPULSE_IP_HASH_KEY`.

## Decisions Made

- O gate é único e compartilhado. Uma falha vista pelo cache poupa também a publicação, e o contrário vale igual: na queda, só a primeira chamada de cada janela paga o timeout.
- Na publicação, gate fechado conta `failed`, não `rejected`. `rejected` fica reservado para "nem entrou na fila", e os dois somam os descartados do painel da Phase 3.
- `toString()` mascara qualquer chave não nula. Uma chave vazia também vira `****`, para não revelar nem o tamanho.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug de teste] GET de aquecimento antes de pausar o Valkey nos ITs de queda**
- **Found during:** Task 2 (RED do `ValkeyOutageIT`)
- **Issue:** a ordem dos métodos do JUnit pôs `clickPublishFailureIsCountedWhileValkeyIsPaused` primeiro, numa JVM fria. O primeiro GET levou 1343 ms: primeiro request do MVC, e a conexão do Lettuce aberta com o container já pausado. A falha não tinha relação com o comportamento sob teste e poderia acontecer também em `redirectKeepsWorkingFastWhileValkeyIsPaused`, se ele rodasse sozinho.
- **Fix:** helper `warmUp(code)` faz um GET com o Valkey no ar antes da pausa, nos dois testes. Assim o IT mede a queda com a aplicação já rodando, que é o cenário do critério.
- **Files modified:** `app/src/test/java/dev/linkpulse/cache/ValkeyOutageIT.java`
- **Verification:** os tempos passaram para [169, 5, 5] e [171, 5, 6, 7, 7] ms no `verify` completo.
- **Committed in:** `5c34062`

**2. [Processo] Esqueleto do construtor no commit de RED**
- **Found during:** Task 2 (RED)
- **Issue:** sem o novo parâmetro `ValkeyAvailability` no construtor, o `ClickPublisherTest` não compila, e um RED por erro de compilação é INVALID_RED.
- **Fix:** o commit de RED inclui só a assinatura nova do construtor do `ClickPublisher`, ainda sem efeito, e a passagem do bean no `ClickConfig`. É o mesmo padrão do RED da Task 1.
- **Files modified:** `ClickPublisher.java`, `ClickConfig.java`
- **Committed in:** `5c34062`

---

**Total deviations:** 2 (1 correção de teste, 1 de processo de TDD).
**Impact on plan:** nenhum desvio de escopo; o comportamento entregue é o do plano.

## Issues Encountered

- O `check tdd-red-evidence` lê saída TAP. Os resultados do Surefire e do Failsafe (XML JUnit) foram transcritos para TAP só para alimentar o checker; os registros ficaram no scratchpad da sessão.
- Os comandos travados durante a pausa já foram escritos no socket. Depois do `unpause`, o Valkey ainda pode processar um XADD que a aplicação contou como `failed`, e o clique entra no stream. A semântica fica "no máximo uma vez, com raros duplicados de contagem na queda", o que é aceitável para analytics. O consumidor idempotente das próximas waves trata a gravação.

## User Setup Required

None. `LINKPULSE_IP_HASH_KEY` já era exigida pela 02-02; este plano só a documentou.

## Next Phase Readiness

- A 02-09 (rate limit) pode reutilizar o bean `ValkeyAvailability`.
- A 02-08 (cardinalidade de métricas) tem novas séries para checar: `linkpulse.cache.requests{result=error|bypass}`, `linkpulse.cache.errors{operation}`, `linkpulse.valkey.unavailable`, `linkpulse.clicks.publish{result=failed}` e `linkpulse.clicks.publish.queue`. Todas usam só as tags `result` e `operation`.
- A Phase 3 já tem o histograma de `http.server.requests` e os contadores de descarte para os painéis e alertas.

## Self-Check: PASSED

- Arquivos criados existem: `ValkeyAvailability.java`, `RedisConfig.java`, `ValkeyAvailabilityTest.java`, `ValkeyOutageIT.java` e `ClickPublisherTest.java` (183 linhas).
- Commits existem: `e6c82ad`, `e070c6e`, `5c34062` e `b229431`. `git rev-list --count 2bb154f..HEAD` = 4.
- Critérios de aceite da Task 2: todos PASS, verificados por grep e pelos relatórios do Surefire e do Failsafe.

---
*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Completed: 2026-10-11*
