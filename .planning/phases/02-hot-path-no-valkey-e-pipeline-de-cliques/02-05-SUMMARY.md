---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
plan: 05
subsystem: click-pipeline
tags: [mongodb, user-agent, bots, ttl-index, collmod, mongo-driver-timeouts, redis-streams, testcontainers]

requires:
  - phase: 02-04
    provides: "ClickStreamConsumer (loop próprio, XACK depois do write), ClickDocument.fromStream que nunca lança, MongoClickEventWriter, linkpulse.clicks.retention (90d), Mongo 8.0 no Testcontainers e no compose.dev.yaml"
provides:
  - "UserAgentClassifier.classify(String) -> UserAgentInfo(browser, os, bot): família de navegador/SO sem versão e isBot"
  - "ClickDocument com browser, os e isBot entre referrer e ipHash"
  - "ClickIndexes: code_ts {code:1, ts:-1} e ts_ttl {ts:1, expireAfterSeconds = retention}, com collMod quando a retenção muda"
  - "ClickStreamConsumer(Settings, RedisConnectionFactory, ClickEventWriter, ClickIndexes, MeterRegistry), com indexes.ensure() no loop"
  - "MongoConfig: server selection 3 s, connect 2 s e read 5 s no driver"
affects: [02-06, 02-07, 02-08, 03-observabilidade, 04-kubernetes]

actuals:
  tokens: 11308
  tasks: 2
  commits: 4
plan_head_before: 8fb7bbab0ad3a111e95d15feab2329f351f7dd35

tech-stack:
  added: []
  patterns:
    - "Classificador de user-agent por substring em minúsculas (Locale.ROOT), primeira regra vence, vetores versionados em @CsvSource"
    - "Índices do Mongo garantidos no loop do consumer (flag volatile + retry com backoff), nunca na inicialização dos beans"
    - "TTL ajustável: listIndexes + collMod em vez de recriar o índice"
    - "MongoClientSettingsBuilderCustomizer para timeouts, sem substituir a auto-configuração"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/click/UserAgentClassifier.java
    - app/src/main/java/dev/linkpulse/click/ClickIndexes.java
    - app/src/main/java/dev/linkpulse/config/MongoConfig.java
    - app/src/test/java/dev/linkpulse/click/UserAgentClassifierTest.java
    - app/src/test/java/dev/linkpulse/click/ClickDocumentTest.java
  modified:
    - app/src/main/java/dev/linkpulse/click/ClickDocument.java
    - app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java
    - app/src/main/java/dev/linkpulse/config/ClickConfig.java
    - app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java

key-decisions:
  - "Formato final de click_events: _id (ID do stream), _class, code, ts (Date), referrer, browser, os, isBot e ipHash; sem IP em claro, user-agent cru ou versões"
  - "Regra de bots: UA ausente/em branco, crawlers, previews de link, headless e clientes HTTP de linha de comando ou de biblioteca (curl, wget, python-requests, Go-http-client, Java-http-client, k6) contam como bot; okhttp não. O seed (Phase 3) e o smoke (Phase 4) precisam de UA de navegador ou de includeBots=true"
  - "Token do ChromeOS é \"cros \" (com espaço) para não casar com \"microsoft\""
  - "O consumer garante os índices antes do grupo e da primeira leitura: com o Mongo fora, ele fica em backoff sem ler o stream, então nada vai para o PEL à toa"
  - "Contingência A4 não foi necessária: serverSelectionTimeout, connectTimeout e readTimeout compilam no driver 5.8.1"

patterns-established:
  - "Classificação de texto livre do cliente em rótulos de cardinalidade baixa antes de persistir"
  - "Índices explícitos por código (ClickIndexes.ensure) chamados pelo worker com retry"

requirements-completed: [CLICK-02, CLICK-07, DATA-02, QUAL-02]

coverage:
  - id: D1
    description: "O user-agent vira família de navegador e de SO, e crawlers, previews, headless, clientes de linha de comando e UA vazio têm isBot = true (okhttp e CUBOT não)"
    requirement: CLICK-07
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/click/UserAgentClassifierTest.java"
        status: pass
    human_judgment: false
  - id: D2
    description: "fromStream devolve vazio para entradas inválidas e preenche browser/os/isBot pelo classificador (ua ausente conta como bot)"
    requirement: CLICK-07
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/click/ClickDocumentTest.java"
        status: pass
    human_judgment: false
  - id: D3
    description: "Um GET com Firefox no Linux grava browser=Firefox, os=Linux e isBot=false; com curl grava isBot=true; as chaves do documento ficam em {_id, _class, code, ts, referrer, browser, os, isBot, ipHash}"
    requirement: CLICK-07
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java#documentCarriesBrowserOsAndBotFlag"
        status: pass
    human_judgment: false
  - id: D4
    description: "Entrada sem iph é confirmada sem documento, conta em linkpulse.clicks.invalid e sai do PEL; a entrada válida seguinte vira documento"
    requirement: CLICK-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java#invalidEntryIsAcknowledgedWithoutDocument"
        status: pass
    human_judgment: false
  - id: D5
    description: "Consumer ocioso por 1 s não chama o writer; um único XADD vira 1 chamada com 1 documento e o pending do grupo volta a 0"
    requirement: CLICK-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java#idleConsumerDoesNotWriteAndSingleEntryIsAcknowledged"
        status: pass
    human_judgment: false
  - id: D6
    description: "click_events tem code_ts {code:1, ts:-1} e ts_ttl {ts:1} com expireAfterSeconds = 7776000, criados pelo consumer"
    requirement: DATA-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java#clickEventsHasCodeTsAndTtlIndexes"
        status: pass
    human_judgment: false
  - id: D7
    description: "ensure() duas vezes não falha, e a retenção de 30 dias muda o TTL para 2592000 via collMod sem recriar o code_ts"
    requirement: DATA-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java#retentionChangeUpdatesTtlWithCollMod"
        status: pass
    human_judgment: false
  - id: D8
    description: "No ambiente local (compose.dev.yaml + profile dev), depois de GET /demo-link, o Mongo lista _id_, code_ts e ts_ttl em click_events"
    verification:
      - kind: e2e
        ref: "smoke do <verify> da Task 2 (compose up, java -jar --spring.profiles.active=dev, curl /demo-link, mongosh getIndexes)"
        status: pass
    human_judgment: false
  - id: D9
    description: "Build completo verde: Checkstyle, 206 unitários, 71 ITs e SpotBugs sem achados"
    requirement: QUAL-02
    verification:
      - kind: other
        ref: "cd app && ./mvnw -B -ntp verify"
        status: pass
    human_judgment: false
  - id: D10
    description: "Timeouts curtos do driver (3 s / 2 s / 5 s) fazem um Mongo fora falhar rápido no consumer e no /stats"
    verification: []
    human_judgment: true
    rationale: "Nesta fase os valores só são aplicados por configuração e conferidos por leitura de código; a queda do Mongo é provada nas 02-07 (503 do /stats) e 02-08 (consumer)"
  - id: D11
    description: "Os índices não são criados na inicialização dos beans; com o Mongo fora, a aplicação sobe e redireciona"
    verification: []
    human_judgment: true
    rationale: "Prohibition com verificação por julgamento: ensure() só é chamado no loop do consumer (leitura de código); a subida com o Mongo fora é exercitada na 02-08"

duration: 13min
completed: 2026-10-11
status: complete
---

# Phase 02 Plan 05: Navegador, SO, bots e índices de click_events Summary

**Cada clique em `click_events` agora traz a família do navegador, a família do SO e `isBot`, vindos de um classificador próprio por substring (o curl conta como bot e o okhttp não). Entradas inválidas são confirmadas e contadas sem travar o grupo. O consumer cria os índices `code_ts` e `ts_ttl` (90 dias, alterado via `collMod`), e o driver do Mongo ganhou timeouts de 3 s, 2 s e 5 s.**

## Performance

- **Duração:** 13 min
- **Início:** 2026-10-11T16:17:51Z
- **Fim:** 2026-10-11T16:30:49Z
- **Tasks:** 2
- **Arquivos modificados:** 9

## Accomplishments

- `UserAgentClassifier` gera rótulos de cardinalidade baixa (sem versões), cobertos por 35 vetores unitários de navegadores, SOs, bots e falsos positivos (CUBOT, okhttp).
- `ClickDocument.fromStream` classifica o `ua` e o descarta. O documento final não guarda o user-agent cru nem o IP.
- Entrada inválida e consumer ocioso agora têm ITs: o PEL esvazia, `linkpulse.clicks.invalid` aumenta e o writer não é chamado enquanto o consumer está ocioso.
- `ClickIndexes.ensure()` é idempotente. Ele cria `code_ts` e `ts_ttl`, e quando a retenção muda usa `collMod` em vez de recriar o índice. Roda no loop do consumer, com backoff.
- `MongoConfig` limita server selection, connect e read, para um Mongo fora não segurar o consumer por 30 s.

## Formato final do documento de `click_events` (02-07 e seed da Phase 3)

| Campo | Tipo | Origem |
|---|---|---|
| `_id` | string `<ms>-<seq>` | ID da entrada em `lp:clicks` |
| `_class` | string | gravado pelo Spring Data |
| `code` | string | `code` do stream |
| `ts` | BSON Date | `ts` do stream (epoch ms) |
| `referrer` | string | host do `Referer` ou `(direct)` |
| `browser` | string | `Chrome`, `Edge`, `Firefox`, `Safari`, `Opera`, `Samsung Internet` ou `Other` |
| `os` | string | `Windows`, `macOS`, `iOS`, `Android`, `Linux`, `ChromeOS` ou `Other` |
| `isBot` | boolean | `UserAgentClassifier` (UA ausente conta como bot) |
| `ipHash` | string 64 hex | HMAC-SHA256 do IP |

Índices: `_id_`, `code_ts` `{code: 1, ts: -1}` e `ts_ttl` `{ts: 1}` com `expireAfterSeconds = linkpulse.clicks.retention` (padrão 7776000).

## Regra de bots (Phase 3 seed, Phase 4 smoke)

Contam como bot: user-agent ausente ou em branco, crawlers (Googlebot, bingbot, GPTBot, ClaudeBot...), previews de link (facebookexternalhit, Slackbot, WhatsApp, TelegramBot, Discordbot...), `HeadlessChrome`/Lighthouse e **clientes HTTP de linha de comando ou de biblioteca** (`curl/`, `wget/`, `python-requests`, `Go-http-client`, `Java-http-client`, `k6/`, Postman, HTTPie...). A regra genérica é `<letra>bot/<dígito>`. `okhttp` **não** conta, porque apps Android usam essa biblioteca.

Consequência: o seed e o smoke test que usam `curl` precisam mandar um user-agent de navegador ou consultar `/stats` com `includeBots=true`. O smoke de dev desta plan confirma isso: o `curl /demo-link` gravou `isBot: true`.

## Construtor do `ClickStreamConsumer` (a 02-06 estende com o reclaim)

```java
public ClickStreamConsumer(Settings settings, RedisConnectionFactory connectionFactory,
        ClickEventWriter writer, ClickIndexes indexes, MeterRegistry registry)
// Loop: ensureIndexes() -> ensureGroup() -> readBatch() -> process(write -> XACK)
```

## Contingência A4

Não foi necessária. `applyToClusterSettings(b -> b.serverSelectionTimeout(3, SECONDS))` e `applyToSocketSettings(b -> b.connectTimeout(2, SECONDS).readTimeout(5, SECONDS))` compilam com o driver 5.8.1 sem aviso de deprecation.

## Task Commits

1. **Task 1 (RED): user-agent, bots e entradas inválidas no consumer** - `ca91858` (test)
2. **Task 1 (GREEN): navegador, SO e isBot por classificador versionado** - `2aaaf9d` (feat)
3. **Task 2 (RED): índices code_ts e TTL de retenção** - `73bc13f` (test)
4. **Task 2 (GREEN): índices code_ts e TTL de retenção e timeouts curtos do Mongo** - `75e7ec9` (feat)

## TDD Gate Compliance

- Task 1: RED `ca91858`. `RED_EVIDENCE_OK` com o alvo `ClickPipelineIT.documentCarriesBrowserOsAndBotFlag` (esperava `"Firefox"`, veio `"Other"`), mais 34 falhas de asserção nos unitários. GREEN `2aaaf9d`.
- Task 2: RED `73bc13f`. `RED_EVIDENCE_OK` com o alvo `ClickPipelineIT.retentionChangeUpdatesTtlWithCollMod` (índices `{}`, sem `code_ts`/`ts_ttl`). GREEN `75e7ec9`.
- Nenhum REFACTOR foi necessário.
- O gate do tracer (Task 1, `end-of-phase`, verify só automatizado) rodou de novo o `<verify>` depois do GREEN e passou. A expansão para a Task 2 seguiu sem checkpoint.

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/click/UserAgentClassifier.java`: regras de navegador, SO e bots (`BOT_TOKENS` e `[a-z]bot/\d`)
- `app/src/main/java/dev/linkpulse/click/ClickIndexes.java`: `code_ts`, `ts_ttl` e `collMod`
- `app/src/main/java/dev/linkpulse/config/MongoConfig.java`: timeouts do driver como constantes nomeadas
- `app/src/main/java/dev/linkpulse/click/ClickDocument.java`: `browser`, `os` e `@Field("isBot") bot`; `fromStream` classifica o `ua`
- `app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java`: recebe `ClickIndexes` e faz `ensureIndexes()` no loop
- `app/src/main/java/dev/linkpulse/config/ClickConfig.java`: bean `clickIndexes`, que é passado ao consumer
- `app/src/test/java/dev/linkpulse/click/UserAgentClassifierTest.java`: vetores `@CsvSource` (delimitador `|`) e `@ValueSource`
- `app/src/test/java/dev/linkpulse/click/ClickDocumentTest.java`: entradas inválidas e válidas, sem Docker
- `app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java`: cinco ITs novos (330 linhas)

## Decisions Made

- O token do ChromeOS é `"cros "`, com espaço. `"cros"` sozinho casaria com `microsoft` (a regra de Windows vem antes, mas o espaço evita a armadilha em UAs sem `windows`).
- O consumer chama `ensureIndexes()` antes de `ensureGroup()` e da leitura. Com o Mongo fora, o loop fica em backoff sem ler o stream, então nenhuma entrada vai para o PEL sem poder ser gravada.
- `ClickIndexes.ensure()` roda `collMod` também quando o `ts_ttl` existe sem `expireAfterSeconds` (índice criado à mão com o mesmo nome).
- Os vetores de bots que não são navegador ficam num `@ValueSource` que só confere `bot = true`. O `@CsvSource` cobre os casos com navegador e SO definidos. O delimitador é `|` porque os UAs têm vírgulas.

## Deviations from Plan

### Auto-fixed Issues

**1. [Process] Esqueletos de produção nos commits de RED**
- **Found during:** Task 1 (RED) e Task 2 (RED)
- **Issue:** sem `UserAgentClassifier`, sem os componentes novos do `ClickDocument` e sem `ClickIndexes`, os testes não compilariam, e um RED por erro de compilação não prova nada (INVALID_RED).
- **Fix:** os RED incluem esqueletos mínimos. `classify` devolvia `(Other, Other, false)`, o `ClickDocument` recebia placeholders `Other`/`false` e `ensure()` era vazio. O padrão é o mesmo das 02-03 e 02-04. Os testes falharam por asserção e o `check tdd-red-evidence` deu `RED_EVIDENCE_OK` nas duas tasks.
- **Files modified:** `UserAgentClassifier.java`, `ClickDocument.java`, `ClickIndexes.java`
- **Committed in:** `ca91858`, `73bc13f`

**2. [Rule 3 - Blocking] Checkstyle nos testes novos**
- **Found during:** Task 1 (RED) e Task 2 (RED)
- **Issue:** indentação das continuações dentro do `@CsvSource`, linhas acima de 100 caracteres e ordem dos imports (`ArrayList` depois de `HashMap`).
- **Fix:** continuações com 16 espaços, linhas quebradas e imports reordenados.
- **Files modified:** `UserAgentClassifierTest.java`, `ClickPipelineIT.java`
- **Committed in:** `ca91858`, `73bc13f`

---

**Total deviations:** 2 (1 de processo, 1 bloqueio de estilo resolvido).
**Impact on plan:** nenhum desvio de escopo; contratos, nomes e comportamento são os do `<interfaces>`.

## Issues Encountered

- `invalidEntryIsAcknowledgedWithoutDocument` e `idleConsumerDoesNotWriteAndSingleEntryIsAcknowledged` já passavam no RED, porque a 02-04 já confirmava inválidos e pulava leituras vazias (o plano previa "conferir; corrigir se o IT mostrar o contrário"). Eles ficam como testes de caracterização. O RED da Task 1 se apoia em `documentCarriesBrowserOsAndBotFlag` e nos unitários.
- O `javac` avisa de API deprecated em `ClickPublisher.java`. O aviso é anterior a esta plan e fica fora de escopo; o build não falha por isso.

## User Setup Required

None. Não há serviço externo novo.

## Next Phase Readiness

- A 02-06 pode estender o construtor do consumer com o reclaim. `ClickIndexes` fica antes do `MeterRegistry`.
- A 02-07 tem `browser`, `os` e `isBot` para agregar e excluir bots, e o `code_ts` atende o `$match` do `/stats`.
- Sem bloqueios.

## Self-Check: PASSED

- Arquivos criados presentes: `UserAgentClassifier.java`, `ClickIndexes.java`, `MongoConfig.java`, `UserAgentClassifierTest.java`, `ClickDocumentTest.java`.
- Commits presentes: `ca91858`, `2aaaf9d`, `73bc13f`, `75e7ec9` (`git rev-list --count 8fb7bba..HEAD` = 4).
- `./mvnw -B -ntp verify`: BUILD SUCCESS (206 unitários, 71 ITs, 0 violações de Checkstyle, 0 achados do SpotBugs). Smoke de dev: `302`, e os índices `_id_,code_ts,ts_ttl` aparecem em `click_events`.

---
*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Completed: 2026-10-11*
