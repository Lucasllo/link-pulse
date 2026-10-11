---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
plan: 06
subsystem: click-pipeline
tags: [redis-streams, valkey, xpending, xclaim, at-least-once, idempotency, mongodb, testcontainers]

requires:
  - phase: 02-04
    provides: "ClickStreamConsumer (loop próprio, XACK depois do write), MongoClickEventWriter (bulk UNORDERED tolerante a 11000), _id = ID do stream"
  - phase: 02-05
    provides: "ClickIndexes.ensure() no loop, ClickDocument com browser/os/isBot, mongoContainer"
provides:
  - "Reclaim de pendentes no ClickStreamConsumer: XPENDING IDLE >= minIdle + XCLAIM a cada reclaimInterval, pelo mesmo caminho write -> XACK"
  - "Settings(streamKey, group, consumerName, batchSize, block, reclaimInterval, minIdle)"
  - "Construtor ClickStreamConsumer(Settings, RedisConnectionFactory, ClickEventWriter, ClickIndexes, MeterRegistry, Clock) (a 02-08 depende dele)"
  - "linkpulse.clicks.consumer.reclaim-interval (30s) e min-idle (60s), positivos"
  - "Métricas linkpulse.clicks.claimed e linkpulse.clicks.lost"
  - "ACK das entradas pendentes que não existem mais no stream (aparadas pelo MAXLEN), contadas como lost"
  - "ClickRedeliveryIT com 3 cenários de reentrega"
affects: [02-07, 02-08, observabilidade, alertas-de-lag]

actuals:
  tokens: 9214
  tasks: 2
  commits: 4
plan_head_before: d85305191243ae27a7dce8bb24e577cbe9bde014

tech-stack:
  added: []
  patterns:
    - "Reclaim at-least-once: XPENDING IDLE + XCLAIM tipados (sem XAUTOCLAIM), reprocessados pelo mesmo process()"
    - "ID pedido e não devolvido pelo XCLAIM: XRANGE id id decide entre 'outro consumer assumiu' (não toca) e 'apagado' (XACK + lost)"
    - "Reclaim agendado antes de rodar (falha não vira reclaim a cada ciclo) e paginado até 20 páginas cheias por rodada"
    - "IT de reentrega com consumer instanciado no teste: stream, grupo e coleção exclusivos por teste"

key-files:
  created:
    - app/src/test/java/dev/linkpulse/click/ClickRedeliveryIT.java
  modified:
    - app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java
    - app/src/main/java/dev/linkpulse/config/ClickConfig.java
    - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
    - app/src/main/resources/application.yml
    - app/src/test/java/dev/linkpulse/AbstractIT.java
    - app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java
    - app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java

key-decisions:
  - "Reclaim por XPENDING IDLE + XCLAIM tipados no lugar do XAUTOCLAIM (ausente da StreamOperations 4.1.1); defaults 30s de intervalo e 60s de min-idle"
  - "O próximo reclaim é agendado antes de rodar: com o MongoDB fora o loop continua lendo entradas novas e só tenta o reclaim de novo no intervalo seguinte"
  - "Reclaim paginado: uma página cheia (batchSize) puxa a próxima na mesma rodada, até 20, para um backlog do PEL não escoar a um lote por intervalo"
  - "Entradas perdidas (ausentes do stream) são confirmadas antes da gravação do resto do lote assumido: não fazem parte do lote e não dependem do MongoDB"
  - "Sem limite de reentregas: clique válido espera no PEL o tempo que o MongoDB ficar fora; só sai sem documento o que o MAXLEN apagou"
  - "Assumption A5 confirmada: o XCLAIM do Valkey 8.1 já remove do PEL as entradas apagadas; o XACK defensivo continua para servidores que não fazem isso"

patterns-established:
  - "Consumer de stream recebe Clock para agendamentos internos (testável sem sleep longo)"
  - "Contadores de reentrega: claimed (assumidas) e lost (apagadas antes da gravação), sem tags"

requirements-completed: [CLICK-03, CLICK-04, QUAL-02]

coverage:
  - id: D1
    description: "XACK só depois da gravação: lote cuja escrita falhou fica no PEL sem documento e volta por um consumer saudável até virar documento"
    requirement: CLICK-04
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickRedeliveryIT.java#batchIsAcknowledgedOnlyAfterSuccessfulWrite"
        status: pass
    human_judgment: false
  - id: D2
    description: "Pendentes de consumer morto com crash entre insert e ACK (0, 2 e 4 já gravados) terminam em exatamente 6 documentos, PEL vazio e duplicates >= 3"
    requirement: CLICK-03
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickRedeliveryIT.java#deadConsumerPendingIsReprocessedWithoutDuplicates"
        status: pass
    human_judgment: false
  - id: D3
    description: "Pendentes apagados do stream pelo trim saem do PEL sem documento e contam em linkpulse.clicks.lost"
    requirement: CLICK-04
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickRedeliveryIT.java#trimmedPendingEntriesAreAcknowledgedAndCountedAsLost"
        status: pass
    human_judgment: false
  - id: D4
    description: "Entrada pendente que ainda existe no stream e que o XCLAIM não devolveu (outro consumer assumiu) não é confirmada nem contada como lost"
    requirement: CLICK-04
    verification: []
    human_judgment: true
    rationale: "A corrida entre XPENDING e XCLAIM de duas réplicas não é reproduzível de forma determinística no IT; o comportamento vem da checagem XRANGE id id em acknowledgeVanished e precisa de revisão de código"
  - id: D5
    description: "Suíte completa (unit + ITs com Testcontainers, Checkstyle e SpotBugs) verde com a nova assinatura do consumer"
    requirement: QUAL-02
    verification:
      - kind: other
        ref: "cd app && ./mvnw -B -ntp verify (206 unit, 74 IT, 0 Checkstyle, 0 SpotBugs)"
        status: pass
    human_judgment: false

duration: 13min
completed: 2026-10-11
status: complete
---

# Phase 02 Plan 06: Reentrega at-least-once do pipeline de cliques Summary

**Reclaim de pendentes por XPENDING IDLE + XCLAIM no ClickStreamConsumer, com XACK só depois da gravação, reprocessamento sem duplicata via `_id` = ID do stream, e entradas aparadas pelo MAXLEN confirmadas e contadas em `linkpulse.clicks.lost`, tudo provado pelo ClickRedeliveryIT**

## Performance

- **Duration:** 13 min
- **Started:** 2026-10-11T16:33:42Z
- **Completed:** 2026-10-11T16:46:30Z
- **Tasks:** 2
- **Files modified:** 8 (1 criado, 7 alterados)

## Accomplishments

- O `ClickStreamConsumer` recupera pendentes a cada `reclaimInterval`. Pede ao grupo as entradas paradas há pelo menos `minIdle` (`pending(stream, group, Range.unbounded(), batchSize, minIdle)`), assume-as com `claim(..., XClaimOptions.minIdle(minIdle).ids(ids))` e as passa pelo mesmo `process` (write → XACK). Vale para consumers mortos e para o próprio consumer depois de uma falha de escrita.
- Os IDs pedidos que o XCLAIM não devolveu são checados com `XRANGE id id`. Se a entrada não existe mais no stream, entra num único XACK e conta em `linkpulse.clicks.lost`. Se ainda existe, outro consumer a assumiu e o reclaim não a toca.
- Configuração nova: `linkpulse.clicks.consumer.reclaim-interval` (30s) e `min-idle` (60s), validados como positivos, com envs `LINKPULSE_CLICKS_CONSUMER_RECLAIM_INTERVAL` e `LINKPULSE_CLICKS_CONSUMER_MIN_IDLE`. Métricas novas: `linkpulse.clicks.claimed` e `linkpulse.clicks.lost`.
- `ClickRedeliveryIT` cobre três cenários. (1) Um writer que sempre falha deixa 5 entradas no PEL sem documento, e um consumer saudável as grava. (2) Crash entre insert e ACK com duplicatas intercaladas nas posições 0, 2 e 4 termina com exatamente 6 documentos, cujos `_id` são os IDs do stream. (3) Entradas aparadas saem do PEL como lost.

## Task Commits

1. **Task 1 (tracer): XACK só depois da escrita e reclaim por XPENDING IDLE + XCLAIM**
   - RED `683019a` (test): `batchIsAcknowledgedOnlyAfterSuccessfulWrite` falhava no segundo await (0 de 5 documentos, porque ninguém recuperava os pendentes)
   - GREEN `dfee8f6` (feat)
2. **Task 2 (tdd): consumer morto, crash entre insert e ACK e entradas aparadas**
   - RED `74e2ddd` (test): `trimmedPendingEntriesAreAcknowledgedAndCountedAsLost` falhava na asserção `linkpulse.clicks.lost` (0.0 < 4.0)
   - GREEN `814b086` (feat)

REFACTOR: nenhum, porque o código já saiu limpo do GREEN.

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java`: `Settings` com `reclaimInterval` e `minIdle`; construtor com `Clock`; `reclaimIfDue`, `reclaimIdle` e `acknowledgeVanished`; contadores `claimed` e `lost`; Javadoc da entrega at-least-once, da assumption A5 e da ausência de limite de reentregas.
- `app/src/main/java/dev/linkpulse/config/ClickConfig.java`: passa `reclaimInterval()`, `minIdle()` e o bean `Clock` ao consumer.
- `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java`: `ConsumerSettings` ganha `reclaimInterval` (30s) e `minIdle` (60s), positivos, com mensagem fixa.
- `app/src/main/resources/application.yml`: comentário com as duas propriedades novas e as envs.
- `app/src/test/java/dev/linkpulse/AbstractIT.java`: `reclaim-interval=1s` e `min-idle=1s` no contexto compartilhado.
- `app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java`: consumer do teste ocioso na assinatura nova (o comportamento não muda).
- `app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java`: `ConsumerSettings` com 5 argumentos.
- `app/src/test/java/dev/linkpulse/click/ClickRedeliveryIT.java` (novo, 258 linhas): três ITs de reentrega.

## Decisions Made

- **Assumption A5 confirmada.** No RED da Task 2, a asserção "PEL = 0" já passava e só `lost` falhava. Ou seja, o XCLAIM do Valkey 8.1 remove do PEL as entradas que não existem mais no stream e não as devolve. O XACK defensivo continua no código para servidores que não fazem isso (num ID que já saiu, o XACK não tem efeito).
- **Tempo de recuperação observado** (`min-idle` e `reclaim-interval` de 200 ms, `block` de 200 ms), pela duração de cada teste no Failsafe, que inclui start, stop e limpeza: `deadConsumerPendingIsReprocessedWithoutDuplicates` levou 0,75 s, `batchIsAcknowledgedOnlyAfterSuccessfulWrite` 0,91 s e `trimmedPendingEntriesAreAcknowledgedAndCountedAsLost` 1,39 s. A recuperação em si ficou abaixo de 1 s depois do start do consumer saudável.
- **O `ClickStreamConsumer` agora recebe `Clock`** como último argumento do construtor, e a 02-08 depende dessa assinatura. O `Clock` só agenda o reclaim (`nextReclaimAt`, `volatile`).
- O próximo reclaim é agendado **antes** de rodar. Se o reclaim lança (MongoDB fora), o loop entra no backoff normal e volta a ler entradas novas; o reclaim não se repete a cada ciclo.
- Sem limite de reentregas (prohibition do plano): `grep -ci deliverycount` no código sem comentários dá 0.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `ClickPublisherTest` construía `ConsumerSettings` com 3 argumentos**
- **Found during:** Task 1 (GREEN)
- **Issue:** Os dois componentes novos do record quebrariam a compilação dos testes. O arquivo não estava na lista do plano.
- **Fix:** Passar `Duration.ofSeconds(30)` e `Duration.ofSeconds(60)` (os defaults).
- **Files modified:** app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java
- **Verification:** `./mvnw verify` completo verde (ClickPublisherTest 6/6)
- **Committed in:** dfee8f6

**2. [Rule 2 - Missing Critical] Reclaim paginado (até 20 páginas por rodada)**
- **Found during:** Task 1 (GREEN)
- **Issue:** Com uma única página de `batchSize` por intervalo (500 a cada 30 s), um backlog acumulado no PEL durante uma queda do MongoDB levaria horas para escoar.
- **Fix:** `reclaimIfDue` busca a próxima página enquanto a anterior vier cheia, até `MAX_RECLAIM_PAGES = 20`, e para se o consumer estiver parando. Termina sempre, porque cada página é confirmada, assumida por outro (o idle zera) ou confirmada como lost.
- **Files modified:** app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java
- **Verification:** ClickRedeliveryIT (3/3) e suíte completa
- **Committed in:** dfee8f6

**3. [Doc] Propriedades novas documentadas no `application.yml`**
- **Found during:** Task 1 (GREEN)
- **Issue:** O bloco de comentários do `application.yml` lista todas as propriedades `linkpulse.clicks.consumer.*`, e as duas novas ficariam de fora.
- **Fix:** Duas linhas de comentário, sem mudança de configuração.
- **Files modified:** app/src/main/resources/application.yml
- **Committed in:** dfee8f6

**4. [Processo] RED da Task 1 escrito contra a API antiga**
- O teste do RED usou o `Settings` de 5 campos, para falhar numa asserção (0 de 5 documentos) e não por erro de compilação. No GREEN, o helper `newConsumer` passou para a assinatura nova.

---

**Total deviations:** 3 auto-fixed (1 blocking, 1 missing critical, 1 doc) + 1 nota de processo
**Impact on plan:** Correções necessárias para compilar e para o reclaim escoar backlog em tempo útil. Sem mudança de contrato além da prevista no `<interfaces>`.

## TDD Gate Compliance

- Task 2 (`tdd="true"`): RED `74e2ddd` → GREEN `814b086`.
- A evidência de RED foi validada com `gsd-tools check tdd-red-evidence` → `RED_EVIDENCE_OK` (`target_test_failed`, alvo `trimmedPendingEntriesAreAcknowledgedAndCountedAsLost`). O checker só lê TAP, então a evidência é uma transcrição em TAP do relatório real do Failsafe (3 testes, 2 passaram, 1 falhou).
- No mesmo RED, `deadConsumerPendingIsReprocessedWithoutDuplicates` já passava: o cenário era coberto pelo reclaim da Task 1 junto com o writer tolerante a 11000 da 02-04. Ele fica como teste de caracterização.

## Issues Encountered

- Um heredoc grande no Git Bash falhou com `unexpected EOF`. Os scripts de edição passaram para o scratchpad. Nenhum efeito no código.

## Known Stubs

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Na 02-07 (stats), nada muda: ela só lê `click_events`.
- Na 02-08: o construtor com `Clock` e os contadores `claimed` e `lost` estão prontos. A parte "o consumer não para após um erro" (CLICK-04) e o gauge de lag do grupo ficam com a 02-08.
- Fica para revisão humana a corrida de duas réplicas entre XPENDING e XCLAIM (D4): a lógica existe, mas nenhum IT determinístico a cobre.

## Self-Check: PASSED

- FOUND: app/src/test/java/dev/linkpulse/click/ClickRedeliveryIT.java (258 linhas, >= 120)
- FOUND: commits 683019a, dfee8f6, 74e2ddd, 814b086
- Acceptance criteria das Tasks 1 e 2: PASS (grep e relatórios do Failsafe sem `<failure>`/`<error>`)
- `./mvnw -B -ntp verify`: BUILD SUCCESS

---
*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Completed: 2026-10-11*
