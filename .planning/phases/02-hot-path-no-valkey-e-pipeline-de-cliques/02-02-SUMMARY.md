---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
plan: 02
subsystem: api
tags: [valkey, redis-streams, lettuce, spring-data-redis, micrometer, hmac, privacy, testcontainers]

# Dependency graph
requires:
  - phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
    provides: "02-01: RedirectService/RedirectController com cache-aside no Valkey, spring-boot-starter-data-redis, Valkey 8.1 no Testcontainers e no compose.dev.yaml"
provides:
  - ClickPublisher (SmartLifecycle, executor com fila limitada, XADD MAXLEN ~ no stream lp:clicks)
  - IpHasher (HMAC-SHA256 de "<dia UTC>|<ip>" com linkpulse.ip-hash-key)
  - ReferrerNormalizer (Referer reduzido ao host ou "(direct)")
  - ClickConfig (beans IpHasher e ClickPublisher)
  - linkpulse.ip-hash-key (LINKPULSE_IP_HASH_KEY) obrigatória e linkpulse.clicks.* (stream-key, max-len, publisher.threads, publisher.queue-capacity)
  - Métrica linkpulse.clicks.publish{result=published|rejected}
  - Contrato do stream lp:clicks: campos code, ts, ref, ua, iph
affects: [02-03, 02-04, 02-09, 03-stack-local, 04-kind, 05-terraform]

# Actuals (#2632)
actuals:
  tokens: 7753
  tasks: 2
  commits: 4
plan_head_before: 04e2a75828498cc06238101fd937b5c8982f9a50

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Publicação assíncrona com ThreadPoolExecutor próprio (threads fixas, ArrayBlockingQueue) e handler de rejeição que conta; nunca @Async nem o executor do Boot"
    - "Dado pessoal transformado na thread do publisher antes do XADD: IP vira HMAC, Referer vira host"
    - "Segredo obrigatório validado no construtor de uma classe final, com mensagem fixa que não ecoa o valor"
    - "Beans que exigem segredo ficam num @Configuration separado (ClickConfig) para os testes com ApplicationContextRunner não precisarem dele"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/click/ClickPublisher.java
    - app/src/main/java/dev/linkpulse/click/ReferrerNormalizer.java
    - app/src/main/java/dev/linkpulse/common/IpHasher.java
    - app/src/main/java/dev/linkpulse/config/ClickConfig.java
    - app/src/test/java/dev/linkpulse/click/ClickPublishIT.java
    - app/src/test/java/dev/linkpulse/click/ReferrerNormalizerTest.java
    - app/src/test/java/dev/linkpulse/common/IpHasherTest.java
  modified:
    - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
    - app/src/main/java/dev/linkpulse/link/RedirectController.java
    - app/src/main/resources/application-dev.yml
    - app/src/test/java/dev/linkpulse/AbstractIT.java

key-decisions:
  - "02-02: stream lp:clicks com campos code, ts (epoch ms em texto), ref (host ou (direct)), ua (até 512, vazio se ausente) e iph (HMAC hex de 64); é o contrato que o consumer da 02-04 lê"
  - "02-02: iph = HMAC-SHA256(linkpulse.ip-hash-key, '<dia UTC>|<ip>') em hex minúsculo; a chave é de topo (o rate limit da 02-09 também a usa), obrigatória fora do dev (LINKPULSE_IP_HASH_KEY, >= 16 caracteres) e vira contrato de configuração das Phases 3-5"
  - "02-02: construtor do ClickPublisher = (StringRedisTemplate, IpHasher, LinkPulseProperties.Clicks, Clock, MeterRegistry); a 02-03 insere ValkeyAvailability como segundo parâmetro"
  - "02-02: RedirectController publica só depois do resolve (404/410 lançam antes) e só quando HttpMethod.GET.matches(request.getMethod()); IP só de getRemoteAddr(), nunca de header de proxy (D-08)"
  - "02-02: Referer normalizado na ingestão: URI.getHost() em minúsculas (Locale.ROOT), sem pontos finais, até 255; nulo, em branco, > 2048, URISyntaxException ou sem host viram (direct)"

patterns-established:
  - "Publisher limitado: ThreadPoolExecutor(threads, threads, ArrayBlockingQueue(capacity), threads de plataforma daemon 'click-publisher-N', handler de rejeição contando result=rejected)"
  - "SmartLifecycle do pipeline: publisher em DEFAULT_PHASE - 4096 (para depois do web server e antes do Lettuce); stop() = shutdown + awaitTermination(5 s)"
  - "Métricas do pipeline de cliques: linkpulse.clicks.publish só com a tag result (D-10)"

requirements-completed: [CLICK-01, CLICK-06, REDIR-06, OBS-01, QUAL-02]

coverage:
  - id: D1
    description: "Cada GET 302 publica, sem esperar, uma entrada em lp:clicks com code, ts (epoch ms dentro do intervalo do GET), ua e iph em HMAC hex de 64 caracteres; nenhum campo contém o IP do cliente"
    requirement: "CLICK-01"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPublishIT.java#redirectPublishesClickWithHashedIp"
        status: pass
      - kind: other
        ref: "smoke de dev: docker compose -f compose.dev.yaml up + java -jar --spring.profiles.active=dev; GET /demo-link -> HTTP/1.1 302 e XLEN lp:clicks = 1"
        status: pass
    human_judgment: false
  - id: D2
    description: "IP só como HMAC-SHA256 da chave sobre '<dia UTC>|<ip>': mesmo hash no mesmo dia UTC, outro no dia seguinte, dependente da chave e do IP; chave nula, em branco ou com menos de 16 caracteres falha com mensagem fixa que não contém a chave"
    requirement: "CLICK-06"
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/common/IpHasherTest.java"
        status: pass
    human_judgment: false
  - id: D3
    description: "HEAD /{code} responde 302 e não publica clique: nenhuma entrada no stream para o código e linkpulse.clicks.publish{result=published} inalterado"
    requirement: "REDIR-06"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPublishIT.java#headRequestDoesNotPublishClick"
        status: pass
    human_judgment: false
  - id: D4
    description: "Referer reduzido ao host em minúsculas sem ponto final (path, query, userinfo e porta nunca saem); sem Referer ou sem host vira (direct); user-agent truncado em 512"
    requirement: "CLICK-06"
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/click/ReferrerNormalizerTest.java"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPublishIT.java#refererIsReducedToHost"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPublishIT.java#missingRefererIsDirect"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPublishIT.java#longUserAgentIsTruncated"
        status: pass
    human_judgment: false
  - id: D5
    description: "Publicação limitada e medida: executor com ArrayBlockingQueue(linkpulse.clicks.publisher.queue-capacity), XADD com MAXLEN ~ linkpulse.clicks.max-len e contador linkpulse.clicks.publish{result=published|rejected} só com a tag result"
    requirement: "OBS-01"
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/click/ClickPublishIT.java#headRequestDoesNotPublishClick (lê linkpulse.clicks.publish{result=published})"
        status: pass
    human_judgment: true
    rationale: "O caminho rejected (fila cheia) e o MAXLEN aproximado só são provados por teste na 02-03 (ClickPublisherTest captura o XAddOptions); aqui a prova é a revisão do código e o critério de aceite approximateTrimming(true)"
  - id: D6
    description: "Suíte completa verde com o publisher no caminho do redirect (Phase 1, CacheIT, ClickPublishIT), Checkstyle e SpotBugs"
    requirement: "QUAL-02"
    verification:
      - kind: integration
        ref: "cd app && ./mvnw -B -ntp verify (147 unitários, 61 ITs, 0 violações de Checkstyle, BugInstance size 0)"
        status: pass
    human_judgment: false

# Metrics
duration: 13min
completed: 2026-10-11
status: complete
---

# Phase 2 Plan 02: Publicação de cliques no stream lp:clicks Summary

**Cada GET 302 publica, sem esperar, uma entrada em `lp:clicks` (executor limitado + `XADD MAXLEN ~ 100000`), com IP só como HMAC-SHA256 por dia UTC, Referer reduzido ao host e HEAD fora da contagem**

## Performance

- **Duration:** 13 min
- **Started:** 2026-10-11T15:18:12Z
- **Completed:** 2026-10-11T15:31:05Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments

- `RedirectController` publica o clique depois do `resolve` (404/410 lançam antes) e só em `GET`, com `request.getRemoteAddr()`, `Referer` e `User-Agent`. Nenhum header de proxy é lido (D-08).
- `ClickPublisher` (`SmartLifecycle`, fase `DEFAULT_PHASE - 4096`): a thread do request só captura `clock.instant()` e submete a tarefa a um `ThreadPoolExecutor` com 2 threads de plataforma e `ArrayBlockingQueue(10000)`. Fila cheia conta `linkpulse.clicks.publish{result=rejected}` e nunca bloqueia; sucesso conta `result=published`.
- **Contrato do stream `lp:clicks` (lido pelo consumer da 02-04):** `code` (código curto), `ts` (epoch ms em texto), `ref` (host ou `(direct)`), `ua` (user-agent truncado em 512, vazio quando ausente), `iph` (HMAC hex de 64 caracteres). `XADD` com `MAXLEN ~ linkpulse.clicks.max-len` (100000).
- **Regra do hash:** `iph = HMAC-SHA256(linkpulse.ip-hash-key, "<dia UTC>|<ip>")` em hex minúsculo. A chave é obrigatória e de topo: `linkpulse.ip-hash-key` / env `LINKPULSE_IP_HASH_KEY`, com pelo menos 16 caracteres. Sem ela a aplicação não sobe (`IllegalArgumentException` de mensagem fixa, sem ecoar a chave). As Phases 3-5 (compose, Secret do Helm, ECS) precisam fornecê-la.
- `ReferrerNormalizer.toHost`: host em minúsculas (`Locale.ROOT`), sem pontos finais, até 255; `(direct)` para nulo, em branco, acima de 2048, `URISyntaxException` ou sem host (`/relativo`, `mailto:`). `android-app://com.google.android.gm` mantém o host.
- **Construtor do `ClickPublisher` nesta fase:** `ClickPublisher(StringRedisTemplate, IpHasher, LinkPulseProperties.Clicks, Clock, MeterRegistry)`. A 02-03 insere `ValkeyAvailability` como segundo parâmetro (e o `result=failed`, a gauge da fila e o `toString` mascarado).
- `LinkPulseProperties` ganha `ipHashKey` e `Clicks(streamKey="lp:clicks", maxLen=100000, PublisherSettings(threads=2, queueCapacity=10000))`, com defaults nas anotações.
- Profile dev com chave fixa só de desenvolvimento (`dev-only-ip-hash-key-not-a-secret`); `AbstractIT` com `@SpringBootTest(properties = {"linkpulse.ip-hash-key=test-ip-hash-key-0123456789"})`.
- Smoke de dev: `GET /demo-link` respondeu `HTTP/1.1 302` e `XLEN lp:clicks` = 1, com a entrada `code=demo-link`, `ref=(direct)`, `ua=curl/8.18.0` e `iph` de 64 caracteres hex (sem o IP).

## Task Commits

1. **Task 1: Cada GET 302 vira uma entrada no stream lp:clicks, com o IP já em hash** (tracer)
   - `c52e972` test(02-02): redirect deve publicar o clique no stream
   - `d78b3a6` feat(02-02): redirect publica o clique no stream com IP em HMAC
2. **Task 2: Referer só como host, user-agent truncado, HEAD sem clique e hash de IP testado** (TDD)
   - `b73ecd8` test(02-02): privacidade do Referer, HEAD sem clique e hash de IP
   - `ff9a113` feat(02-02): referer reduzido ao host e user-agent truncado na publicação

Sem commit de refactor (nada a limpar depois do GREEN).

## TDD Gate Compliance

| Task | RED | GREEN | REFACTOR | Status |
|------|-----|-------|----------|--------|
| 1 (tracer) | `c52e972`: `redirectPublishesClickWithHashedIp` falhou por asserção (Awaitility: "entrada de pub-ok1 no stream lp:clicks", `Optional` vazio em 10 s) | `d78b3a6` | n/a | Pass |
| 2 (tdd) | `b73ecd8`: `check tdd-red-evidence` = `RED_EVIDENCE_OK` (alvo `ReferrerNormalizerTest.reducesRefererToHost`, 6/15 falhando por asserção, `expected "news.example.com" but was "(direct)"`, exit 1); no IT, `refererIsReducedToHost` e `missingRefererIsDirect` falharam por asserção | `ff9a113` | não necessário | Pass |

No RED da Task 2, `IpHasherTest` (11), `headRequestDoesNotPublishClick` e `longUserAgentIsTruncated` já passavam: o plano entrega o `IpHasher`, o guard de HEAD e o truncamento do UA na Task 1, e a Task 2 só os prova ("esta task só o prova"). Eles são testes de caracterização; os alvos do RED foram os casos do Referer.

Tracer feedback gate (modo interativo, `end-of-phase`, verify só automatizado): o verify da Task 1 (`ClickPublishIT,RedirectIT,CacheIT,LinkApiIT`, 43 ITs, Checkstyle e SpotBugs) passou sobre a mesma árvore que foi commitada, então a Task 2 seguiu sem checkpoint.

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/click/ClickPublisher.java`: executor limitado, campos do stream, `XADD MAXLEN ~`, contadores, `SmartLifecycle`
- `app/src/main/java/dev/linkpulse/click/ReferrerNormalizer.java`: `toHost` e `DIRECT`
- `app/src/main/java/dev/linkpulse/common/IpHasher.java`: HMAC-SHA256 por dia UTC, `MIN_KEY_LENGTH = 16`, `Mac` por chamada
- `app/src/main/java/dev/linkpulse/config/ClickConfig.java`: beans `IpHasher` e `ClickPublisher`
- `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java`: `ipHashKey`, `Clicks`, `Clicks.PublisherSettings`
- `app/src/main/java/dev/linkpulse/link/RedirectController.java`: `HttpServletRequest`, publicação só em GET
- `app/src/main/resources/application-dev.yml`: `linkpulse.ip-hash-key` de desenvolvimento
- `app/src/test/java/dev/linkpulse/AbstractIT.java`: chave fixa nos ITs via `properties`
- `app/src/test/java/dev/linkpulse/click/ClickPublishIT.java`: 5 ITs (campos e hash do IP, HEAD, Referer, `(direct)`, UA)
- `app/src/test/java/dev/linkpulse/click/ReferrerNormalizerTest.java`: 15 casos sem Docker
- `app/src/test/java/dev/linkpulse/common/IpHasherTest.java`: 11 casos sem Docker

## Decisions Made

- `ClickConfig` separado do `ApplicationConfig`, como pede o plano: `LinkPulsePropertiesTest` e `CodeGeneratorTest` carregam só o `ApplicationConfig` e seguem sem chave nem Valkey.
- A falha do `XADD` (Valkey fora) ainda não é capturada no publisher: a exceção encerra só a tarefa no worker do executor, sem chegar ao request. O `result=failed` e o gate de disponibilidade são da 02-03, conforme o plano.
- `stop()` faz `shutdown()` + `awaitTermination(5 s)` e restaura a flag de interrupção; depois do `shutdown`, `publish` cai no handler de rejeição (conta `rejected`), então não há checagem extra de `running` no caminho do request.
- `ReferrerNormalizer` também devolve `(direct)` para host vazio depois de remover os pontos finais (ex.: `http://./`), para nunca publicar `ref` vazio.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Esqueleto do `ReferrerNormalizer` no commit RED da Task 2**
- **Found during:** Task 2 (RED)
- **Issue:** sem a classe, o `ReferrerNormalizerTest` não compila, e o RED seria erro de compilação (INVALID_RED), não falha de asserção.
- **Fix:** o commit `test(02-02)` traz um esqueleto com `DIRECT` e `toHost` sempre devolvendo `(direct)`; a implementação real entrou no GREEN. Mesmo padrão da 02-01.
- **Files modified:** app/src/main/java/dev/linkpulse/click/ReferrerNormalizer.java
- **Verification:** `check tdd-red-evidence` = `RED_EVIDENCE_OK`
- **Committed in:** `b73ecd8`

**2. [Rule 2 - Missing Critical] Casos extras de privacidade e robustez nos testes**
- **Found during:** Task 2 (RED)
- **Issue:** o `<behavior>` não cobria `mailto:` (esquema opaco sem host), Referer com espaços nas pontas, chave com exatamente 16 caracteres nem a garantia de que o hash não contém o IP.
- **Fix:** casos adicionados ao `ReferrerNormalizerTest` e ao `IpHasherTest`; o `headRequestDoesNotPublishClick` espera o contador `published` estabilizar antes de medir, para publicações em voo de outros ITs não deixarem o teste instável.
- **Files modified:** app/src/test/java/dev/linkpulse/click/ReferrerNormalizerTest.java, app/src/test/java/dev/linkpulse/common/IpHasherTest.java, app/src/test/java/dev/linkpulse/click/ClickPublishIT.java
- **Verification:** 15/15 e 11/11 verdes; ITs 5/5 verdes
- **Committed in:** `b73ecd8`

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** nenhuma mudança de escopo; contratos do `<interfaces>` mantidos.

## Issues Encountered

- `gsd_run check tdd-red-evidence` lê saída TAP; o RED do JUnit foi convertido do relatório XML do Surefire (uma linha `ok`/`not ok` por caso executado) para o registro de evidência.

## User Setup Required

None - no external service configuration required. (Fora do dev, `LINKPULSE_IP_HASH_KEY` passa a ser obrigatória; o compose da Phase 3, o Secret do Helm da Phase 4 e o ECS da Phase 5 precisam fornecê-la.)

## Next Phase Readiness

- 02-03 pode inserir `ValkeyAvailability` no `ClickPublisher` (segundo parâmetro), acrescentar `result=failed`, a gauge da fila, o `try/catch` do `XADD` e o `toString` mascarado da chave.
- 02-04 consome `lp:clicks` com os campos `code`, `ts`, `ref`, `ua`, `iph` (constantes `ClickPublisher.FIELD_*`).
- 02-09 (rate limit) reutiliza o bean `IpHasher` e a mesma `linkpulse.ip-hash-key`.

## Self-Check: PASSED

- Arquivos criados presentes: ClickPublisher.java, ReferrerNormalizer.java, IpHasher.java, ClickConfig.java, ClickPublishIT.java, ReferrerNormalizerTest.java, IpHasherTest.java
- Commits presentes: c52e972, d78b3a6, b73ecd8, ff9a113
- `./mvnw -B -ntp verify`: BUILD SUCCESS (147 unitários, 61 ITs, 0 Checkstyle, 0 SpotBugs); smoke de dev: 302 e `XLEN lp:clicks` = 1

---
*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Completed: 2026-10-11*
