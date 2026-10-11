---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
plan: 04
subsystem: click-pipeline
tags: [mongodb, valkey, redis-streams, consumer-group, xreadgroup, lettuce, virtual-threads, testcontainers, docker-compose]

requires:
  - phase: 02-02
    provides: "ClickPublisher (XADD em lp:clicks com code, ts, ref, ua, iph; MAXLEN ~ 100000), IpHasher, ClickConfig"
  - phase: 02-03
    provides: "fail-open do Valkey (timeout 150 ms, REJECT_COMMANDS, ValkeyAvailability), AbstractIT com bypass-cooldown=200ms"
provides:
  - "ClickStreamConsumer: consumer group click-writers com loop próprio (SmartLifecycle, virtual thread), XREADGROUP COUNT/BLOCK, bulk no MongoDB e só depois XACK"
  - "ClickStreamConsumer.dedicatedConnectionFactory: fábrica Lettuce fora do contexto, timeout = block + 3 s"
  - "ClickDocument (coleção click_events, _id = ID do stream) e ClickDocument.fromStream que nunca lança"
  - "ClickEventWriter + MongoClickEventWriter: bulk UNORDERED que tolera só duplicate key (11000)"
  - "linkpulse.clicks.group / retention / consumer.{enabled,batch-size,block}"
  - "spring.mongodb.database=linkpulse; Mongo 8.0 no Testcontainers e no compose.dev.yaml"
  - "métricas linkpulse.clicks.persisted, duplicates, invalid, consumer.batch, consumer.write{outcome}, consumer.errors"
affects: [02-05, 02-06, 02-07, 02-08, 03-observabilidade, 04-kubernetes, 05-terraform]

actuals:
  tokens: 14000
  tasks: 2
  commits: 3
plan_head_before: 2a4ca1ebeff7a5d748026e9777f5c396924a9d71

tech-stack:
  added: [spring-boot-starter-data-mongodb, testcontainers-mongodb, "mongo:8.0"]
  patterns:
    - "Loop de consumo próprio em virtual thread com SmartLifecycle; stop(callback) devolve na hora e o callback roda quando o loop sai"
    - "Fábrica Lettuce dedicada criada a partir de DataRedisConnectionDetails, nunca registrada como bean"
    - "Escrita idempotente por _id = ID do stream + bulk UNORDERED; XACK só depois do write"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java
    - app/src/main/java/dev/linkpulse/click/ClickDocument.java
    - app/src/main/java/dev/linkpulse/click/ClickEventWriter.java
    - app/src/main/java/dev/linkpulse/click/MongoClickEventWriter.java
    - app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java
  modified:
    - app/pom.xml
    - app/src/main/java/dev/linkpulse/config/ClickConfig.java
    - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
    - app/src/main/resources/application.yml
    - app/src/test/java/dev/linkpulse/AbstractIT.java
    - app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java
    - app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java
    - compose.dev.yaml
    - README.md

key-decisions:
  - "Formato de click_events nesta fase: _id (ID do stream <ms>-<seq>), code, ts (BSON Date), referrer (host ou (direct)), ipHash (64 hex) e o _class do Spring Data; a 02-05 acrescenta browser, os e isBot entre referrer e ipHash"
  - "Assumption A8 confirmada: spring.mongodb.database=linkpulse prevalece sobre a connection string do @ServiceConnection (mongoDatabaseIsLinkpulse verde)"
  - "Bean do container Mongo dos ITs se chama mongoContainer: mongo já é o MongoClient da auto-configuração e a sobrescrita de beans é desligada"
  - "ClickStreamConsumer também implementa DisposableBean: se o contexto fecha sem o consumer ter subido, a fábrica dedicada é destruída mesmo assim (releaseFactory é idempotente)"
  - "Métricas: persisted/duplicates contam logo depois do write; invalid conta depois do XACK, porque o descarte só é definitivo com a confirmação"

patterns-established:
  - "Consumer de stream: ensureGroup (BUSYGROUP = pronto, NOGROUP = recriar) -> readBatch -> process(write -> XACK) -> backoff 0,5-10 s em fatias de 100 ms"
  - "Logs do pipeline só com a classe da exceção e o nome do stream, nunca com o conteúdo das entradas"

requirements-completed: [CLICK-02, CLICK-03, CLICK-06, OBS-01, QUAL-02]

coverage:
  - id: D1
    description: "Um GET 302 vira, em até 30 s, um documento em click_events com _id igual ao ID da entrada do stream, referrer reduzido ao host, ipHash de 64 hex, ts como Date e sem IP ou user-agent crus"
    requirement: CLICK-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java#redirectBecomesClickDocumentInMongo"
        status: pass
    human_judgment: false
  - id: D2
    description: "Entradas idênticas (mesmo code e ts) viram documentos distintos; nada é fundido por conteúdo"
    requirement: CLICK-03
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java#identicalEntriesBecomeDistinctDocuments"
        status: pass
    human_judgment: false
  - id: D3
    description: "Banco do MongoDB é linkpulse mesmo com o @ServiceConnection (assumption A8)"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java#mongoDatabaseIsLinkpulse"
        status: pass
    human_judgment: false
  - id: D4
    description: "Nenhum campo de click_events guarda o IP em claro nem o user-agent cru"
    requirement: CLICK-06
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java#redirectBecomesClickDocumentInMongo"
        status: pass
    human_judgment: false
  - id: D5
    description: "Build completo verde: Checkstyle, 161 unitários, 66 ITs e SpotBugs sem achados"
    requirement: QUAL-02
    verification:
      - kind: other
        ref: "cd app && ./mvnw -B -ntp verify"
        status: pass
    human_judgment: false
  - id: D6
    description: "No ambiente local (compose.dev.yaml + profile dev), GET /demo-link responde 302 e vira pelo menos um documento em click_events"
    verification:
      - kind: e2e
        ref: "smoke do <verify> da Task 2 (compose up, java -jar --spring.profiles.active=dev, curl /demo-link, mongosh countDocuments)"
        status: pass
    human_judgment: false
  - id: D7
    description: "XACK só depois do write do lote (falha no write deixa o lote no PEL)"
    verification: []
    human_judgment: true
    rationale: "Ordem garantida por leitura de código (process: write antes de acknowledge); o teste de falha do write e da reentrega é da 02-06 (ClickRedeliveryIT)"
  - id: D8
    description: "Métricas do consumer (linkpulse.clicks.persisted, duplicates, invalid, consumer.batch, consumer.write, consumer.errors) expostas em /actuator/prometheus"
    requirement: OBS-01
    verification: []
    human_judgment: true
    rationale: "Os meters são registrados no construtor, mas nenhum IT desta fase consulta os valores; o painel da Phase 3 e a 02-05/02-06 exercitam os contadores"

duration: 15min
completed: 2026-10-11
status: complete
---

# Phase 02 Plan 04: Consumer group grava cliques em click_events Summary

**Consumer group `click-writers` num loop próprio em virtual thread: lê `lp:clicks` com XREADGROUP COUNT/BLOCK numa conexão Lettuce dedicada (timeout = block + 3 s), grava o lote no MongoDB com bulk UNORDERED que tolera só duplicate key e só depois faz XACK; o Mongo 8.0 entrou no Testcontainers e no `compose.dev.yaml`.**

## Performance

- **Duration:** 15 min
- **Started:** 2026-10-11T15:59:42Z
- **Completed:** 2026-10-11T16:15:06Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments

- Um clique real atravessa controller → publisher → stream → consumer group → MongoDB: o documento tem `_id` igual ao ID do stream, `ts` como Date, `referrer` reduzido ao host e só o `ipHash`.
- Idempotência por `_id` mais bulk `UNORDERED`: entradas idênticas viram documentos distintos, e uma duplicata (reentrega) não derruba o lote.
- O consumer não morre: erros contam em `linkpulse.clicks.consumer.errors`, o backoff vai de 0,5 a 10 s e é cortado pelo stop em fatias de 100 ms, e `NOGROUP` recria o grupo.
- O `make run` agora sobe Postgres, Valkey e Mongo; o README mostra como consultar `click_events`.

## Formato do documento de `click_events` (esta fase)

| Campo | Tipo | Origem |
|---|---|---|
| `_id` | string `<ms>-<seq>` | ID da entrada em `lp:clicks` |
| `code` | string | campo `code` do stream (`[A-Za-z0-9_-]{1,64}`) |
| `ts` | BSON Date | campo `ts` do stream (epoch ms positivo) |
| `referrer` | string | campo `ref` (host ou `(direct)`, até 255 caracteres) |
| `ipHash` | string 64 hex | campo `iph` (HMAC-SHA256 do IP) |
| `_class` | string | gravado pelo Spring Data |

O campo `ua` do stream é ignorado. A 02-05 acrescenta `browser`, `os` e `isBot` entre `referrer` e `ipHash`.

## Construtor do `ClickStreamConsumer` (a 02-05 estende)

```java
public ClickStreamConsumer(Settings settings, RedisConnectionFactory connectionFactory,
        ClickEventWriter writer, MeterRegistry registry)
// Settings(String streamKey, String group, String consumerName, int batchSize, Duration block)
// 02-05: (Settings, RedisConnectionFactory, ClickEventWriter, ClickIndexes, MeterRegistry)
```

## Assumption A8

Confirmada. Com `spring.mongodb.database: linkpulse` no `application.yml`, `mongo.getDb().getName()` dá `linkpulse` mesmo com a connection string do `@ServiceConnection` (que traz o banco `test`). Teste: `ClickPipelineIT#mongoDatabaseIsLinkpulse`.

## Task Commits

1. **Task 1 (RED): clique deve virar documento em click_events** - `5c74ba5` (test)
2. **Task 1 (GREEN): consumer group grava cliques em click_events com XACK depois da escrita** - `011d371` (feat)
3. **Task 2: Mongo no ambiente local e cliques visíveis em click_events** - `78371e9` (feat)

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java` - loop do consumer group, fábrica Lettuce dedicada, métricas e ciclo de vida
- `app/src/main/java/dev/linkpulse/click/ClickDocument.java` - documento de `click_events` e `fromStream` que nunca lança
- `app/src/main/java/dev/linkpulse/click/ClickEventWriter.java` - contrato da escrita idempotente
- `app/src/main/java/dev/linkpulse/click/MongoClickEventWriter.java` - bulk `UNORDERED` que tolera só o código 11000
- `app/src/main/java/dev/linkpulse/config/ClickConfig.java` - beans `clickEventWriter` e `clickStreamConsumer` (condicional, nome = `HOSTNAME` ou `lp-<uuid>`)
- `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java` - `Clicks.group`, `Clicks.retention` (positiva), `Clicks.ConsumerSettings`
- `app/src/main/resources/application.yml` - `spring.mongodb.database`, `spring.data.mongodb.repositories.type: none`, `server.shutdown: graceful`, `timeout-per-shutdown-phase: 30s`
- `app/pom.xml` - `spring-boot-starter-data-mongodb` e `testcontainers-mongodb` (versões do BOM)
- `app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java` - bean `mongoContainer` (`mongo:8.0`)
- `app/src/test/java/dev/linkpulse/AbstractIT.java` - `linkpulse.clicks.consumer.block=200ms`
- `app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java` - três ITs ponta a ponta
- `app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java` - construtor novo do `Clicks`
- `compose.dev.yaml` - serviço `mongo` (loopback, volume `mongodata`, healthcheck `mongosh`)
- `README.md` - Mongo nos comandos, consulta de `click_events` e envs fora do dev

## Decisions Made

- O bean do container Mongo dos ITs se chama `mongoContainer`. `mongo` já é o nome do `MongoClient` da auto-configuração, e a sobrescrita de beans está desligada.
- `ClickStreamConsumer` também implementa `DisposableBean`. Se o contexto fecha sem o consumer ter subido, a fábrica dedicada é destruída mesmo assim; `releaseFactory` é idempotente.
- `persisted` e `duplicates` contam logo depois do write; `invalid` conta depois do XACK, porque o descarte só fica definitivo quando a entrada é confirmada.
- A leitura do lote ficou num helper `readBatch()` com `@SuppressWarnings("unchecked")`, porque o `read(..., StreamOffset<K>...)` é varargs genérico. Assim a compilação não emite aviso.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Bean do container Mongo renomeado para `mongoContainer`**
- **Found during:** Task 1 (RED)
- **Issue:** o plano pedia `@Bean MongoDBContainer mongo()`, mas o contexto não subia: `The bean 'mongo' ... could not be registered. A bean with that name has already been defined` (o `MongoClient` da `MongoAutoConfiguration` se chama `mongo`).
- **Fix:** método `mongoContainer()`, com Javadoc explicando o motivo.
- **Files modified:** `app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java`
- **Verification:** o contexto sobe e os três ITs falharam por asserção no RED (timeout do Awaitility e banco `test`), depois passaram no GREEN.
- **Committed in:** `5c74ba5`

**2. [Process] Dependências do Mongo e bean do container no commit de RED**
- **Found during:** Task 1 (RED)
- **Issue:** sem o starter do Mongo, o `ClickPipelineIT` (que injeta `MongoTemplate`) não compila, e um RED por erro de compilação não prova nada.
- **Fix:** o commit de RED inclui `pom.xml` (starter e `testcontainers-mongodb`) e o bean do container. É o mesmo padrão dos REDs da 02-03.
- **Files modified:** `app/pom.xml`, `app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java`
- **Committed in:** `5c74ba5`

**3. [Rule 3 - Blocking] `ClickPublisherTest` atualizado para o construtor novo de `Clicks`**
- **Found during:** Task 1 (GREEN)
- **Issue:** o record `Clicks` ganhou `group`, `retention` e `consumer`, e o teste unitário montava o record com três argumentos.
- **Fix:** o teste passa os defaults (`click-writers`, 90 dias, `ConsumerSettings(true, 500, 2s)`).
- **Files modified:** `app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java`
- **Verification:** 161 unitários verdes.
- **Committed in:** `011d371`

**4. [Rule 3 - Blocking] Checkstyle `VariableDeclarationUsageDistance` no `fromStream`**
- **Found during:** Task 1 (GREEN)
- **Issue:** os quatro campos eram lidos no início do método, longe do uso.
- **Fix:** cada campo é lido logo antes da sua validação.
- **Files modified:** `app/src/main/java/dev/linkpulse/click/ClickDocument.java`
- **Committed in:** `011d371`

---

**Total deviations:** 4 (3 bloqueios resolvidos, 1 de processo).
**Impact on plan:** nenhum desvio de escopo; contratos e comportamento são os do plano. O único nome diferente é o do bean de teste (`mongoContainer`).

## Issues Encountered

- A conexão dedicada do consumer não usa TLS, como no compose e no kind. Se o ElastiCache da Phase 5 ligar a criptografia em trânsito, `dedicatedConnectionFactory` precisa propagar o `SslBundle` de `DataRedisConnectionDetails`. Isso está registrado no Javadoc do método.
- O primeiro RED levou cerca de 180 s porque baixou a imagem `mongo:8.0`. As execuções seguintes ficaram em segundos.

## User Setup Required

None. O Mongo local sobe com o `compose.dev.yaml`; fora do dev, a conexão vem de `SPRING_MONGODB_URI`.

## Next Phase Readiness

- A 02-05 pode inserir `ClickIndexes` no construtor do consumer (antes do `MeterRegistry`), acrescentar `browser`, `os` e `isBot` ao `ClickDocument` e ler `linkpulse.clicks.retention` para o índice TTL.
- A 02-06 tem o PEL pronto para a reentrega: o lote que falha no write não é confirmado.
- Sem bloqueios.

## Self-Check: PASSED

- Arquivos criados presentes: `ClickStreamConsumer.java`, `ClickDocument.java`, `ClickEventWriter.java`, `MongoClickEventWriter.java`, `ClickPipelineIT.java`.
- Commits presentes: `5c74ba5`, `011d371`, `78371e9` (`git rev-list --count 2a4ca1e..HEAD` = 3).
- `./mvnw -B -ntp verify`: BUILD SUCCESS (161 unitários, 66 ITs, 0 violações de Checkstyle, 0 achados do SpotBugs). Smoke de dev: `HTTP/1.1 302` e 1 documento de `demo-link`.

---
*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Completed: 2026-10-11*
