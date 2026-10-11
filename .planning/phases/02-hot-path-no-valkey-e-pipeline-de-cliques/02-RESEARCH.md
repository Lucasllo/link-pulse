# Phase 2: Hot path no Valkey e pipeline de cliques - Research

**Researched:** 2026-10-10
**Domain:** Spring Boot 4.1.1 + Spring Data Redis 4.1.1 (Lettuce 7.5.2) sobre Valkey 8.1, Redis Streams com consumer group, Spring Data MongoDB 5.1.1, Tomcat RemoteIpValve, Micrometer/Prometheus, Testcontainers 2.0.5
**Confidence:** ALTA para APIs e versões (lidas nos jars/BOMs reais nesta sessão); MÉDIA para comportamentos de runtime que só um IT confirma (pausa de container, lag `null`, XCLAIM de entradas aparadas)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

### Contrato do `GET /links/{code}/stats` (discutido)
- **D-01:** Sem `from`/`to`, o período padrão é **os últimos 30 dias**. A resposta sempre ecoa o período efetivo (`from`, `to`) aplicado, e total, série diária e tops usam todos o mesmo período. — **Reversibility:** costly — contrato público consumido pelo smoke test do CI (Phase 4), pelo seed/demo e pelo README.
- **D-02:** A série de cliques por dia é **contínua do `from` ao `to`, com dias sem clique preenchidos com 0**. Há um limite máximo de intervalo (sugestão: 366 dias), e acima dele a resposta é 400 em Problem Details.
- **D-03:** Os tops são **10 itens fixos por dimensão** (sem parâmetro `limit`). O referrer é **normalizado para o host** (sem path nem query, que podem levar tokens e dados pessoais), e requisição sem `Referer` entra no bucket `"(direct)"`. Os tops de navegador e de SO saem do user-agent parseado (CLICK-07).
- **D-04:** Código inexistente → **404** em Problem Details (existência conferida no Postgres, podendo passar pelo cache). Link **expirado → 200** com o histórico normal: o 410 é só do redirect.

### Herdado de decisões anteriores (não rediscutir)
- **D-05:** Coleção Mongo **normal** `click_events` (não time-series), com `_id` = ID da mensagem do stream; `insertMany` não ordenado ignorando duplicate key (PROJECT.md, SUMMARY.md).
- **D-06:** Cache-aside **explícito** com `StringRedisTemplate` (sem `@Cacheable`), TTL = min(padrão, tempo até expirar), negative cache para 404/410 invalidado na criação do link e contadores de hit/miss no Micrometer (CLAUDE.md).
- **D-07:** Consumer em **loop próprio** (`SmartLifecycle`, virtual thread) com `XREADGROUP COUNT/BLOCK` → `insertAll` → `XACK`. Não usar `StreamMessageListenerContainer` (CLAUDE.md).
- **D-08:** Rate limit por **script Lua de janela fixa** (`INCR` + `PEXPIRE`), aplicado só no `POST /links`, fail-open se o Valkey cair. O IP real vem do `server.forward-headers-strategy=native` (`RemoteIpValve`) com `internal-proxies` restrito; o código nunca lê `X-Forwarded-For` diretamente (ARCHITECTURE.md Padrão 6, PITFALLS.md Armadilha 8).
- **D-09:** Valkey 8.1 (`valkey/valkey:8.1`) nos ITs via `GenericContainer` + `@ServiceConnection(name = "redis")`; `maxmemory-policy volatile-lru` (STATE.md, PITFALLS.md).
- **D-10:** Nenhuma tag de métrica com código, URL, IP ou user-agent (OBS-01, PITFALLS.md).

### Claude's Discretion
Áreas não discutidas. O pesquisador e o planejador decidem, partindo destes defaults da pesquisa:
- **Formato de `from`/`to`:** datas ISO (`YYYY-MM-DD`, inclusivas) ou instantes ISO-8601 com offset; agrupamento diário em **UTC** (`$dateTrunc` com `timezone: "UTC"`), documentado no OpenAPI.
- **Formato do JSON do /stats:** nomes dos campos e estrutura (ex.: `{code, from, to, includeBots, total, daily[{date, clicks}], topReferrers[{value, clicks}], topBrowsers[...], topOs[...]}`).
- **Momento da normalização do referrer:** a recomendação é guardar só o host já na ingestão (privacidade).
- **Privacidade e retenção:** hash `SHA-256(salt + ip)` com salt vindo de config/secret (fixo ou rotativo diário, a critério); TTL de retenção configurável em `ts` (default sugerido: 90 dias).
- **Rate limit:** limite padrão configurável por env (sugestão: 20 req/min por IP); chave `lp:rl:{ip}:{janela}`; `Retry-After` = segundos até o fim da janela; proxies confiáveis configuráveis por env (vazio no compose, CIDR do cluster no kind).
- **Bots e user-agent:** lista própria de substrings versionada com teste unitário (preview crawlers, Googlebot/bingbot etc.) **ou** uma lib leve; decidir se `curl`/`wget`/clientes HTTP contam como bot; granularidade navegador + SO.
- **Ajustes de infraestrutura:** timeouts do Lettuce (curtos, ~100–200 ms, para o fail-open não virar fail-slow), executor limitado para o publish com descarte contado, `MAXLEN ~`, tamanho do lote e `BLOCK`, `XAUTOCLAIM` com min-idle, TTL padrão do cache e do negative cache, `server.shutdown=graceful` e timeout de shutdown.
- **Desenho dos ITs de reentrega:** consumer morto, queda do Mongo e shutdown com lote em andamento (pesquisar a API do Spring Data Redis no Boot 4.1, como pede o STATE.md).

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REDIR-03 | Cache-aside no Valkey, TTL = min(padrão, tempo até expirar), negative caching 404/410 invalidado na criação | Padrão 1 (codificação do valor com `expiresAt`, tombstones `N`/`G`), invalidação `afterCommit` (Armadilha P3), orquestração fora da transação (Armadilha P2) |
| REDIR-04 | Hit/miss medido em métrica | Catálogo de métricas: `linkpulse.cache.requests{result=hit\|miss\|negative_hit\|error\|bypass}` |
| REDIR-05 | Valkey fora → redirect via Postgres, problema em métrica | Padrão 2 (connect-timeout curto + `REJECT_COMMANDS` + gate de disponibilidade); Armadilhas P1 e P4 |
| REDIR-06 | HEAD não conta clique | `@GetMapping` atende HEAD de forma transparente (CITED Spring 7.0.9); publicar só se `GET` |
| RATE-01 | `POST /links` limitado por IP, atômico, 429 + `Retry-After` | Padrão 5 (Lua `INCR`+`PEXPIRE`, `RedisScript.of`), `HandlerInterceptor` em `/links` POST, `ErrorResponseException.getHeaders()` |
| RATE-02 | IP real só de proxies confiáveis | Padrão 6: Boot sempre repassa `internal-proxies` ao `RemoteIpValve`; vazio = ninguém confiável (bytecode do Tomcat 11.0.24); default do Boot confia em 10/8, 172.16/12, 192.168/16, 127/8 (Armadilha P5) |
| CLICK-01 | Publish no stream sem bloquear, timeout curto, descarte contado, `MAXLEN ~` | Padrão 3 (`ThreadPoolExecutor` limitado + handler que conta; `XAddOptions.maxlen(n).approximateTrimming(true)`) |
| CLICK-02 | Consumer group em lote → Mongo → XACK só depois | Padrão 4 (loop próprio, `read(Consumer, StreamReadOptions.count/block, StreamOffset.lastConsumed)`), fábrica Lettuce dedicada (Armadilha P6) |
| CLICK-03 | Idempotência por `_id` = ID do stream | `bulkOps(UNORDERED).insert(list).execute()`; `BulkOperationException.getErrors()` só com código 11000 → ACK |
| CLICK-04 | Pendentes de consumers mortos recuperados; consumer não morre | `pending(key, group, Range, count, Duration idle)` (XPENDING IDLE) + `claim(...)`; **sem XAUTOCLAIM na API** (verificado); loop com `catch (RuntimeException)` + backoff |
| CLICK-05 | Shutdown conclui o lote | `SmartLifecycle.stop(Runnable)` com fase entre o web server (2147481599) e o Lettuce (0); timeouts do Mongo limitados |
| CLICK-06 | IP só como hash com salt | HMAC-SHA256 com chave de config + dia UTC, calculado no publisher (o IP cru não entra nem no stream) |
| CLICK-07 | `isBot` + UA parseado em navegador/SO | Classificador próprio por substring, com teste unitário (sem lib nova) |
| CLICK-08 | Lag e pendentes como métricas | `groups(key)` → `XInfoGroup.pendingCount()` e `getRaw().get("lag")` (pode ser NULL); gauges atualizadas pelo loop, não pelo scrape |
| STAT-01..04 | Total com `from`/`to`, por dia, top referrers/navegadores/SO, bots fora por padrão | Padrão 7 (`$match` + `$facet` com `Aggregation.stage(Bson)`, preenchimento de zeros em Java, desempate estável) |
| DATA-02 | Índices `{code, ts}` e TTL explícitos | `indexOps(...).createIndex(new Index().on(...).expire(Duration))`; TTL precisa ser índice de campo único em `Date`; mudar o TTL exige `collMod` |
| QUAL-02 | ITs Testcontainers (Postgres + Mongo + Valkey) com reentrega | Seção "Desenho dos testes de integração"; `@ServiceConnection(name = "redis")` funciona com `GenericContainer` (verificado na factory) |
| OBS-01 | Métricas sem tags de alta cardinalidade | Catálogo de métricas; prometheus no `exposure.include`; IT de cardinalidade |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Diretivas acionáveis de `.claude/CLAUDE.md` (mesma autoridade das decisões travadas):

- **Stack efetiva é Spring Boot 4.1.1** (a Phase 1 adotou a variante recomendada, conforme `app/pom.xml` e PROJECT.md). Versões vêm do BOM: não declarar versão de Lettuce, Spring Data, Mongo driver, Micrometer ou Testcontainers.
- **Lettuce, nunca Jedis.** `StringRedisTemplate`/`StreamOperations`/`RedisScript`.
- **Cache-aside explícito com `StringRedisTemplate`**, sem `@Cacheable`.
- **Consumer: loop de polling próprio** (`SmartLifecycle`, virtual thread), `read(Consumer.from(group, instanceId), StreamReadOptions.empty().count(500).block(Duration.ofSeconds(2)), StreamOffset.create(key, ReadOffset.lastConsumed()))` → `mongoTemplate` em lote → `acknowledge(...)`. **Não usar `StreamMessageListenerContainer`.**
- **`XADD ... MAXLEN ~ N`** no redirect, sem esperar o processamento.
- Reprocessamento com `pending(...)` + `claim(...)`; gauge de pendentes vira métrica de lag.
- **Rate limit por script Lua próprio** (fixed window, `INCR` + `PEXPIRE`), sem Bucket4j.
- Índice `{code: 1, ts: -1}`; stats via `Aggregation`.
- Contadores `cache.hits`/`cache.misses` (tag `result=hit|miss`) no Micrometer.
- `spring.threads.virtual.enabled=true` (já ativo); cuidado com pinning em `synchronized` no Java 21.
- `management.server.port=8081`; expor `health,info,prometheus`; `percentiles-histogram.http.server.requests=true`.
- **Records em vez de Lombok.** Checkstyle 14.3.0 (Google adaptado, severity error) e SpotBugs Max/Medium quebram o build.
- Testcontainers: `GenericContainer("valkey/valkey:8.1")` + `@ServiceConnection(name = "redis")`.
- Documentação e Javadoc em pt-BR; identificadores em inglês.
- **Não adicionar Co-Authored-By/"Generated with Claude"** em commits (memória do usuário).
- Fluxo GSD: edições só dentro de um comando GSD.

## Summary

A Phase 1 entregou um núcleo Postgres com Boot 4.1.1 (`app/pom.xml:7-12`). Esta fase acrescenta três dependências gerenciadas pelo BOM (`spring-boot-starter-data-redis`, `spring-boot-starter-data-mongodb`, `micrometer-registry-prometheus`) e um módulo de teste (`testcontainers-mongodb`). Nenhuma biblioteca de terceiros é necessária: rate limit, gate de disponibilidade do Valkey, hash de IP e classificação de user-agent cabem em classes pequenas e testáveis. As versões efetivas, lidas no BOM local, são Spring Data Redis **4.1.1**, Lettuce **7.5.2.RELEASE**, Spring Data MongoDB **5.1.1**, Mongo driver **5.8.1**, Micrometer **1.17.1**, Testcontainers **2.0.5** e Tomcat **11.0.24**.

Seis fatos verificados nos jars mudam o desenho. (1) A `StreamOperations` 4.1.1 **não tem XAUTOCLAIM**, mas tem `pending(key, group, Range, count, Duration idle)` (XPENDING com IDLE) e `claim(...)`, que bastam. (2) `XREADGROUP` com `BLOCK` usa uma conexão Lettuce **dedicada**, mas o `LettuceConnection` espera cada comando com `awaitOrCancel(timeout = commandTimeout)`. Um `spring.data.redis.timeout` curto (150 ms), necessário para o fail-open, **mata qualquer `BLOCK 2s`**. Por isso o consumer precisa de uma `LettuceConnectionFactory` própria com timeout maior, criada fora do contexto, porque o Boot faz `@ConditionalOnMissingBean(RedisConnectionFactory.class)`. (3) O default de `server.tomcat.remoteip.internal-proxies` no Boot confia em `10/8`, `172.16/12`, `192.168/16` e `127/8`, faixas que incluem a rede bridge do Docker; com `native`, qualquer cliente atrás do NAT do compose falsificaria o `X-Forwarded-For`. O valor precisa ser explícito: vazio desliga a confiança, e isso foi verificado no bytecode do `RemoteIpValve`. (4) O `server.shutdown` já é `graceful` por padrão no Boot 4.1.1. (5) A factory de service connection do Redis aceita `Container<?>` com nome `redis`, então `GenericContainer` + `@ServiceConnection(name = "redis")` funciona. (6) `LinkService.resolve` é `@Transactional(readOnly = true)`. Se o cache entrar lá dentro, cada hit abre transação e pega conexão do Hikari. A consulta ao cache precisa ficar fora da transação.

A parte mais arriscada é o fail-open ser rápido de verdade. Sem ajustes, o Valkey parado custa até `connect-timeout` (default de 10 s do Lettuce) ou `commandTimeout` em **cada** redirect. A receita: `connect-timeout` e `timeout` curtos, `ClientOptions.disconnectedBehavior(REJECT_COMMANDS)` via `LettuceClientOptionsBuilderCustomizer`, e um gate de disponibilidade simples (janela de bypass de 1 a 2 s depois de uma falha), compartilhado por cache, rate limit e publisher.

**Recomendação principal:** construir em camadas. Primeiro a infraestrutura (deps, config, gate, containers de teste); depois o cache-aside fora da transação e o rate limit com `RemoteIpValve` explícito; depois o publisher (executor limitado, HMAC do IP, host do referrer); depois o consumer em loop próprio, com fábrica Lettuce dedicada, bulk `UNORDERED`, reclaim por XPENDING IDLE + XCLAIM e `SmartLifecycle.stop(callback)`; e por fim `/stats` com `$facet`. Os ITs de reentrega instanciam o consumer diretamente, com stream e grupo próprios e um writer injetável.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Resolver código → URL (cache-aside) | API / Backend (`RedirectService`, sem transação) | Database / Storage (Valkey cache, Postgres fonte da verdade) | O Postgres é a verdade; o Valkey é só aceleração descartável |
| Negative cache e invalidação | API / Backend (`LinkService.create` → `afterCommit`) | Valkey | A invalidação precisa acontecer depois do commit |
| Fail-open do Valkey | API / Backend (gate + timeouts do cliente Lettuce) | — | É decisão do cliente; o servidor não ajuda |
| IP real do cliente | Frontend Server / container web (Tomcat `RemoteIpValve`) | API (só lê `getRemoteAddr()`) | A confiança em proxy é config de borda, nunca código de app (D-08) |
| Rate limit | API / Backend (`HandlerInterceptor` em `/links` POST) | Valkey (contador Lua atômico) | Só a rota de escrita paga o custo |
| Publicação do clique | API / Backend (executor limitado, fora da thread do request) | Valkey Stream | O redirect nunca espera o analytics |
| Hash do IP, host do referrer | API / Backend (publisher) | — | O IP cru não deve chegar nem ao stream |
| Parsing de UA e bot | Worker (consumer, mesmo processo) | — | Fora do hot path |
| Persistência idempotente | Worker (consumer) | Database (Mongo `_id`) | A idempotência é garantida pela unicidade do `_id` |
| Agregações de stats | Database (aggregation pipeline) | API (preenche zeros, valida período) | O Mongo agrega; o Java formata o contrato |
| Métricas | API / Backend (Micrometer) | Prometheus (Phase 3) | Tags só de baixa cardinalidade |

## Standard Stack

### Core (todas gerenciadas pelo BOM do Boot 4.1.1; não declarar versão)

| Library | Version (efetiva) | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.springframework.boot:spring-boot-starter-data-redis` | 4.1.1 → spring-data-redis **4.1.1**, lettuce-core **7.5.2.RELEASE** | Cache, Stream, Lua | Starter oficial; Lettuce é o default [VERIFIED: spring-boot-dependencies-4.1.1.pom:126 `<lettuce.version>7.5.2.RELEASE</lettuce.version>`; spring-data-bom-2026.0.1.pom:118-119 `<artifactId>spring-data-redis</artifactId>` / `<version>4.1.1</version>`] |
| `org.springframework.boot:spring-boot-starter-data-mongodb` | 4.1.1 → spring-data-mongodb **5.1.1**, driver **5.8.1** | `click_events`, bulk, aggregation | Starter oficial [VERIFIED: spring-data-bom-2026.0.1.pom:108-109 `<artifactId>spring-data-mongodb</artifactId>` / `<version>5.1.1</version>`; spring-boot-dependencies-4.1.1.pom:153 `<mongodb.version>5.8.1</mongodb.version>`] |
| `io.micrometer:micrometer-registry-prometheus` | **1.17.1** | `/actuator/prometheus` | Registry atual (client_java 1.x) [VERIFIED: spring-boot-dependencies-4.1.1.pom:150 `<micrometer.version>1.17.1</micrometer.version>`; micrometer-bom-1.17.1.pom:167] |
| `org.testcontainers:testcontainers-mongodb` (test) | **2.0.5** | `org.testcontainers.mongodb.MongoDBContainer` | Artefato 2.x renomeado [VERIFIED: testcontainers-bom-2.0.5.pom:183; classe `org/testcontainers/mongodb/MongoDBContainer.class` no jar] |
| Valkey (container) | `valkey/valkey:8.1` | Cache/stream/rate limit | Tag ativa no Docker Hub (atualizada em 2026-09-21) [VERIFIED: hub.docker.com API] |
| MongoDB (container) | `mongo:8.0` (8.0.32) | Eventos de clique | Tag ativa (2026-10-02) [VERIFIED: hub.docker.com API] |

Já presentes e relevantes: Awaitility **4.3.0** chega pelo `spring-boot-starter-test` (para os ITs assíncronos) [VERIFIED: spring-boot-starter-test-4.1.1.pom lista `awaitility`; BOM:35 `<awaitility.version>4.3.0</awaitility.version>`].

### Supporting (sem dependência nova)

| Componente | Implementação | When to Use |
|---------|---------|-------------|
| Gate de disponibilidade do Valkey | `AtomicLong openUntil` + cooldown configurável | Cache, rate limit e publisher consultam antes de chamar o Valkey |
| Rate limit | `RedisScript.of(lua, Long.class)` + `StringRedisTemplate.execute` | Só `POST /links` |
| Hash de IP | `javax.crypto.Mac` `HmacSHA256` (JDK) | Publisher e chave do rate limit |
| UA/bot | Classe `UserAgentClassifier` com regras ordenadas | Consumer |
| Clientes em teste | `RestClient`/`java.net.http.HttpClient` (JDK) contra `RANDOM_PORT` | ITs de `X-Forwarded-For` (o MockMvc não passa pelo `RemoteIpValve`) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Classificador próprio de UA | Yauaa 8.2.0 (`nl.basjes.parse.useragent:yauaa`, release 2026-07-24) [VERIFIED: maven-metadata] | Muito mais preciso, mas pesado em memória e startup [ASSUMED]. Para um top-10 de família de navegador/SO, regras por substring bastam |
| Classificador próprio | uap-java 1.6.1 | Último release em 2023-11 [VERIFIED: maven-metadata lastUpdated 20231128]; puxa SnakeYAML; parado |
| Gate próprio | Resilience4j CircuitBreaker | Mais uma dependência para um caso de duas linhas |
| XPENDING IDLE + XCLAIM | XAUTOCLAIM via `connection.execute("XAUTOCLAIM", ...)` | Resposta crua (bytes), sem conversor; a API tipada atende |
| Fábrica Lettuce dedicada para o consumer | Polling sem `BLOCK` (read sem bloqueio + `sleep` quando vazio) | Dispensa a segunda fábrica e funciona com timeout curto; perde o `BLOCK` pedido no CLAUDE.md e faz cerca de 5 a 10 comandos/s ociosos. É o plano B se a fábrica dedicada complicar |

**Installation (pom.xml):**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>
<!-- test -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-mongodb</artifactId>
    <scope>test</scope>
</dependency>
```
O Valkey nos testes usa só `GenericContainer` (já no `testcontainers` core). **Não** adicionar `com.redis:testcontainers-redis` (está no BOM, linha 3148, mas não é necessário).

## Package Legitimacy Audit

O seam `package-legitimacy` cobre npm/PyPI/crates, não Maven. A verificação foi feita por fonte autoritativa: cada artefato foi lido no BOM do Boot 4.1.1 ou no `testcontainers-bom` 2.0.5 baixado do Maven Central nesta sessão.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| spring-boot-starter-data-redis 4.1.1 | Maven Central | starter desde 2014 | n/d | github.com/spring-projects/spring-boot | OK (gerenciado pelo BOM) | Approved |
| spring-boot-starter-data-mongodb 4.1.1 | Maven Central | desde 2014 | n/d | github.com/spring-projects/spring-boot | OK (BOM) | Approved |
| micrometer-registry-prometheus 1.17.1 | Maven Central | desde 2017 | n/d | github.com/micrometer-metrics/micrometer | OK (micrometer-bom) | Approved |
| testcontainers-mongodb 2.0.5 | Maven Central | módulo 2.x | n/d | github.com/testcontainers/testcontainers-java | OK (testcontainers-bom) | Approved |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none. Yauaa e uap-java aparecem só como alternativas, sem recomendação de instalação.

## Architecture Patterns

### System Architecture Diagram

```
                ┌──────────────────────────── Tomcat (RemoteIpValve: XFF só de internal-proxies) ───────────────────────────┐
 cliente ──GET /{code}──▶ RedirectController ──▶ RedirectService (SEM @Transactional)
                │                                   │ 1. gate aberto? ──não──▶ pula o cache (result=bypass)
                │                                   │ 2. LinkCache.get(lp:link:{code})  ──erro──▶ gate.fail(); result=error
                │                                   │      ├─ "L|exp|url" → exp vencido? 410 : 302 (result=hit)
                │                                   │      ├─ "N"        → 404 (negative_hit)
                │                                   │      ├─ "G|exp"    → 410 (negative_hit)
                │                                   │      └─ ausente    → miss
                │                                   │ 3. miss/erro/bypass → LinkService.find (tx read-only, Postgres)
                │                                   │      └─ grava L|N|G com TTL ≤ expiração (se o gate deixar)
                │                                   ▼
                │                       302 + Cache-Control: no-store, private
                │                                   │ só se método == GET e status 302
                │                                   ▼
                │                       ClickPublisher.publish(code, ref, ua, ip)  ── fila cheia ──▶ result=rejected
                │                          (ThreadPoolExecutor 2 threads, fila limitada)
                │                                   │ thread do pool: HMAC(ip), host(ref), trunc(ua)
                │                                   ▼
                │                   XADD lp:clicks MAXLEN ~ 100000 * code ts ref ua iph ── erro ──▶ result=failed
 cliente ──POST /links──▶ RateLimitInterceptor ──Lua INCR/PEXPIRE lp:rl:{hmac(ip)}:{janela}──▶ >limite? 429 + Retry-After
                │                (erro no Valkey → permite, result=error)
                │         └─▶ LinkController ──▶ LinkService.create (tx) ──afterCommit──▶ DEL lp:link:{code}
                └──────────────────────────────────────────────────────────────────────────────────────────────────────────┘

 ClickStreamConsumer (SmartLifecycle, virtual thread, fábrica Lettuce própria com timeout > BLOCK)
   loop: ensureGroup (XGROUP CREATE 0 MKSTREAM; BUSYGROUP ok) ─▶ ensureIndexes (uma vez, com retry)
         a cada reclaimInterval: XPENDING IDLE ≥ minIdle ─▶ XCLAIM p/ si ─▶ processa
         XREADGROUP GROUP click-writers {HOSTNAME} COUNT 500 BLOCK 2000 STREAMS lp:clicks >
           ─▶ converte (UA → browser/os/isBot) ─▶ bulkOps(UNORDERED).insert ─▶ só dup-key? ─▶ XACK lote
                                                              └─ outro erro ─▶ sem ACK (fica no PEL), backoff, métrica
         a cada N s: XINFO GROUPS → gauges pending/lag (AtomicLong)
   stop(callback): running=false → termina o lote corrente → XACK → fecha a fábrica → callback.run()

 GET /links/{code}/stats ─▶ existsByCode (Postgres) ─404─▶ Problem
                         └▶ $match{code, ts∈[from,to+1d), isBot:false?} ─▶ $facet{total, daily, referrers, browsers, os}
                            ─▶ Java preenche dias com 0 ─▶ JSON
```

### Recommended Project Structure

```
app/src/main/java/dev/linkpulse/
├── config/          # LinkPulseProperties (+ Cache, Clicks, RateLimit, Stats), RedisConfig, MongoConfig, MetricsConfig
├── common/          # GlobalExceptionHandler (+ 429/400 de período/503)
├── link/            # existentes + RedirectService (orquestra cache → Postgres), LinkLookup (record)
├── cache/           # LinkCache (codec L/N/G, TTL), ValkeyAvailability (gate)
├── ratelimit/       # RateLimiter (Lua), RateLimitInterceptor, WebMvcConfig (registra o interceptor)
├── click/           # ClickPublisher, IpHasher, ReferrerNormalizer, UserAgentClassifier,
│                    # ClickStreamConsumer, ClickEventWriter (interface) + MongoClickEventWriter,
│                    # ClickDocument (record), ClickIndexes, StreamSettings (record), ClickMetrics
└── stats/           # StatsController, StatsService (aggregation), StatsResponse (records), StatsProblems
app/src/main/resources/
└── application.yml  # spring.data.redis.*, spring.mongodb.*, server.tomcat.remoteip.*, linkpulse.{cache,clicks,ratelimit,stats}
app/src/test/java/dev/linkpulse/
├── TestcontainersConfiguration.java   # + MongoDBContainer, GenericContainer valkey
├── cache/ ratelimit/ click/ stats/    # *IT e *Test
```

### Pattern 1: Cache-aside com valor autodescritivo e tombstones

**What:** chave `lp:link:{code}`; valores `L|<expiresEpochMs ou 0>|<url>`, `N` (não existe) e `G|<expiresEpochMs>` (expirado). O `G` carrega o instante porque `LinkProblems.expired(code, expiredAt)` precisa dele (`LinkProblems.java`, método `expired(String code, Instant expiredAt)`, lido nesta sessão).
**When:** em todo `GET /{code}`.
**Regras:**
- TTL do `L` = `min(defaultTtl, expiresAt - now)` em **milissegundos** (`Duration`), nunca maior que a expiração; sem expiração, `defaultTtl` (24 h). Se `expiresAt - now <= 0`, o link já é `G`.
- Na leitura de `L`, também comparar `exp` com `clock.instant()`: o TTL do Valkey e o relógio do app divergem.
- TTL do `N` curto (60 s); do `G`, longo (24 h): um link expirado nunca volta a valer.
- `split("\\|", 3)`: a URL pode conter `|`, então o limite 3 preserva o resto.
- **Hit-ratio honesto:** `result=hit` só para `L`; `negative_hit` para `N`/`G`.

### Pattern 2: Fail-open rápido (cliente Lettuce + gate)

**What:** três camadas, porque cada uma cobre um modo de falha diferente:
1. `spring.data.redis.connect-timeout: 250ms` evita que a primeira conexão (ou uma reconexão) segure o request pelo default de 10 s do Lettuce [ASSUMED: default de 10 s do `SocketOptions`].
2. `spring.data.redis.timeout: 150ms` é o `commandTimeout`; o `LettuceConnection.await` usa `LettuceFutures.awaitOrCancel(future, timeout, MILLISECONDS)` [VERIFIED: bytecode de `LettuceConnection.await` no spring-data-redis-4.1.1.jar, campo `timeout` vindo de `LettuceClientConfiguration.getCommandTimeout()` em `LettuceConnectionFactory`].
3. `ClientOptions.disconnectedBehavior(REJECT_COMMANDS)`: com o Valkey parado (`docker stop`), os comandos falham na hora em vez de enfileirar até o timeout [VERIFIED: enum `io.lettuce.core.ClientOptions$DisconnectedBehavior` com `DEFAULT, ACCEPT_COMMANDS, REJECT_COMMANDS` no lettuce-core-7.5.2]. Aplicar via `LettuceClientOptionsBuilderCustomizer` [VERIFIED: interface `customize(io.lettuce.core.ClientOptions$Builder)` em spring-boot-data-redis-4.1.1].
4. `ValkeyAvailability`: depois de qualquer falha, abre uma janela de bypass (`cooldown`, default 2 s) em que ninguém chama o Valkey. Isso cobre o caso "conectado, mas sem resposta" (pausa, partição), em que o `REJECT_COMMANDS` não ajuda e cada chamada custaria 150 ms.

```java
// Source: padrão do projeto; APIs verificadas nos jars desta sessão
@Bean
LettuceClientOptionsBuilderCustomizer failFastClientOptions() {
    return builder -> builder.disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
}

/** Janela curta de bypass do Valkey depois de uma falha (fail-open sem fail-slow). */
@Component
public class ValkeyAvailability {
    private final Clock clock;
    private final long cooldownMillis;
    private final AtomicLong openUntil = new AtomicLong();
    private final Counter failures;

    public boolean allowRequest() {
        return clock.millis() >= openUntil.get();
    }

    public void recordFailure() {
        failures.increment();
        openUntil.set(clock.millis() + cooldownMillis);
    }
}
```

Capturar `RuntimeException` no adaptador (`LinkCache`, `RateLimiter`, `ClickPublisher`). As exceções do Spring Data Redis (`QueryTimeoutException`, `RedisConnectionFailureException`, `RedisSystemException`) são `DataAccessException`, mas um `RedisException` do Lettuce pode chegar sem tradução [ASSUMED]. O contrato é: **nenhuma falha do Valkey vira 500 no redirect**.

### Pattern 3: Publisher não bloqueante e limitado

```java
// Source: APIs verificadas: XAddOptions.maxlen(long), approximateTrimming(boolean), MapRecord.create(S, Map)
public class ClickPublisher implements SmartLifecycle {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10_000),
            Thread.ofPlatform().name("click-publisher-", 0).daemon(true).factory(),
            (task, pool) -> rejected.increment());          // fila cheia: descarta e conta

    public void publish(ClickRequest click) {               // chamado na thread do request
        if (!running) { rejected.increment(); return; }
        executor.execute(() -> send(click));                // nunca bloqueia: o handler conta
    }

    private void send(ClickRequest click) {
        if (!availability.allowRequest()) { failed.increment(); return; }
        Map<String, String> fields = Map.of(
                "code", click.code(),
                "ts", Long.toString(click.ts().toEpochMilli()),
                "ref", referrers.toHost(click.referer()),       // host ou "(direct)"
                "ua", truncate(click.userAgent(), 512),
                "iph", ipHasher.hash(click.remoteAddr(), click.ts()));
        try {
            redis.opsForStream().add(MapRecord.create(streamKey, fields),
                    RedisStreamCommands.XAddOptions.maxlen(maxLen).approximateTrimming(true));
            published.increment();
        } catch (RuntimeException e) {
            availability.recordFailure();
            failed.increment();
        }
    }
    // getPhase(): SmartLifecycle.DEFAULT_PHASE - 4096 → para DEPOIS do web server (2147481599)
    // e ANTES da LettuceConnectionFactory (fase 0). stop(): shutdown() + awaitTermination(curto).
}
```
- O controller publica **só** quando `HttpMethod.GET.matches(request.getMethod())`. `@GetMapping` também atende HEAD [CITED: docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html, "`@GetMapping` ... support HTTP HEAD transparently"]. Sem esse guard, o REDIR-06 falha.
- `Map.of` rejeita valor `null`: normalizar `ua` ausente para `""` antes.
- **Não** usar o executor default do Boot nem `@Async`. Com `spring.threads.virtual.enabled=true`, ele é ilimitado e não dá backpressure.

### Pattern 4: Consumer em loop próprio

```java
// Source: StreamOperations 4.1.1 (verificado): read(Consumer, StreamReadOptions, StreamOffset...),
// acknowledge(K, String, RecordId...), pending(K, String, Range<?>, long, Duration), claim(K, String, String,
// XClaimOptions), createGroup(K, ReadOffset, String) [MKSTREAM = true no DefaultStreamOperations], groups(K)
private void loop() {
    while (running) {
        try {
            ensureGroupAndIndexes();                         // idempotente; tenta de novo se falhou
            if (clock.millis() >= nextReclaim) { reclaimIdle(); nextReclaim = clock.millis() + reclaimMs; }
            List<MapRecord<String, Object, Object>> batch = ops.read(
                    Consumer.from(group, consumerName),
                    StreamReadOptions.empty().count(batchSize).block(block),
                    StreamOffset.create(streamKey, ReadOffset.lastConsumed()));
            if (batch != null && !batch.isEmpty()) {
                process(batch);                             // write → ack (ou nada)
            }
            refreshGaugesIfDue();
            backoff = 0;
        } catch (RuntimeException e) {                      // o loop NUNCA morre
            loopErrors.increment();
            if (isNoGroup(e)) { groupReady = false; }       // stream apagado/evicted → recria o grupo
            sleepBackoff();                                 // 0,5 s → 10 s
        }
    }
    stopCallback.run();                                     // só depois do último lote
}

private void process(List<MapRecord<String, Object, Object>> batch) {
    List<ClickDocument> docs = new ArrayList<>();
    List<RecordId> invalid = new ArrayList<>();
    for (var r : batch) {
        ClickDocument d = converter.toDocument(r);          // _id = r.getId().getValue()
        if (d == null) { invalid.add(r.getId()); } else { docs.add(d); }
    }
    writer.write(docs);                                     // lança se falhou de verdade
    ops.acknowledge(streamKey, group, batch.stream().map(Record::getId).toArray(RecordId[]::new));
}

private void reclaimIdle() {
    PendingMessages idle = ops.pending(streamKey, group, Range.unbounded(), batchSize, minIdle);
    if (idle.isEmpty()) return;
    RecordId[] ids = /* ids de idle */;
    List<MapRecord<String, Object, Object>> claimed = ops.claim(streamKey, group, consumerName,
            RedisStreamCommands.XClaimOptions.minIdle(minIdle).ids(ids));
    // IDs pedidos que não voltaram foram aparados pelo MAXLEN: XACK neles para limpar o PEL (ver Armadilha P9)
    process(claimed);
}
```

Writer idempotente:
```java
// Source: BulkOperations/BulkOperationException em spring-data-mongodb-5.1.1 (verificado)
public void write(List<ClickDocument> docs) {
    if (docs.isEmpty()) return;
    try {
        mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, ClickDocument.class).insert(docs).execute();
    } catch (BulkOperationException e) {
        boolean onlyDuplicates = e.getErrors().stream().allMatch(err -> err.getCode() == 11000);
        if (!onlyDuplicates) throw e;                      // sem ACK: tudo fica no PEL e volta pelo reclaim
        duplicates.increment(e.getErrors().size());
    }
}
```
- O `DefaultBulkOperations` só converte `MongoBulkWriteException` **sem** write-concern error em `BulkOperationException`; o resto sai como outra `DataAccessException`, tratada como falha (sem ACK) [VERIFIED: bytecode `DefaultBulkOperations`, linhas com `getWriteConcernError` e `new BulkOperationException`].
- `mongoTemplate.insertAll` usa insert **ordenado** [ASSUMED], que para na primeira duplicata. Por isso o bulk `UNORDERED`.
- Conversão inválida (campo faltando, `ts` não numérico) não pode envenenar o lote: o ID vai para ACK + `linkpulse.clicks.invalid`. **Não** descartar por `deliveryCount`: num Mongo fora por muito tempo, isso perderia cliques válidos.

### Pattern 5: Rate limit Lua de janela fixa

```java
// Source: RedisScript.of(String, Class) verificado em spring-data-redis-4.1.1
private static final RedisScript<Long> FIXED_WINDOW = RedisScript.of("""
        local current = redis.call('INCR', KEYS[1])
        if current == 1 or redis.call('PTTL', KEYS[1]) < 0 then
          redis.call('PEXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """, Long.class);

public Decision check(String clientIp) {
    long now = clock.millis();
    long window = props.window().toMillis();
    long index = now / window;
    long retryAfterSec = Math.max(1, (long) Math.ceil(((index + 1) * window - now) / 1000.0));
    if (!availability.allowRequest()) return Decision.allowedFailOpen();
    try {
        Long count = redis.execute(FIXED_WINDOW,
                List.of("lp:rl:" + ipHasher.hashForKey(clientIp) + ":" + index), Long.toString(window));
        return count != null && count > props.limit() ? Decision.rejected(retryAfterSec) : Decision.allowed();
    } catch (RuntimeException e) {
        availability.recordFailure();
        return Decision.allowedFailOpen();                // métrica result=error
    }
}
```
- `HandlerInterceptor` registrado em `/links` que age só se `"POST".equals(request.getMethod())`. Um 429 lança `ErrorResponseException` com `getHeaders().set(HttpHeaders.RETRY_AFTER, ...)`. `ErrorResponseException` tem `getHeaders()` sobre um `HttpHeaders` final mutável [VERIFIED: spring-web-7.0.9 `private final HttpHeaders headers; public HttpHeaders getHeaders()`]. Exceções de `preHandle` passam pelos `HandlerExceptionResolver`s e chegam ao `GlobalExceptionHandler` [ASSUMED: comportamento padrão do `DispatcherServlet`; um IT confirma o corpo `application/problem+json`].
- Problem `type=/problems/rate-limited`, `title="Muitas requisições"`.
- A chave usa o hash do IP: o IP cru não fica no Valkey nem pelos 60 s da janela.

### Pattern 6: IP real via `RemoteIpValve`

```yaml
server:
  forward-headers-strategy: native
  tomcat:
    remoteip:
      # Vazio = nenhum proxy confiável: X-Forwarded-For é ignorado. Compose: vazio. kind: CIDR dos pods do
      # Traefik (ex.: 10.244.0.0/16). Override: SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES. Use CIDR: regex é
      # deprecated no Tomcat 11 e sai no 12.
      internal-proxies: ""
```
- O Boot sempre chama `valve.setInternalProxies(remoteIp.getInternalProxies())` quando o valve existe [VERIFIED: bytecode de `TomcatWebServerFactoryCustomizer.customizeRemoteIpValve`, spring-boot-tomcat-4.1.1]. No Tomcat 11.0.24, `setInternalProxies` com `null` ou `""` zera os dois matchers (regex e CIDR); com `/` no valor usa `NetMaskSet` (CIDR), senão regex [VERIFIED: bytecode `RemoteIpValve.setInternalProxies`].
- O valve é criado quando `getOrDeduceUseForwardHeaders()` é verdadeiro ou há header configurado [VERIFIED: mesmo bytecode]. Sem `remote-ip-header`, o default do valve é `x-forwarded-for` [CITED: tomcat.apache.org/tomcat-11.0-doc/config/valve.html].
- Default perigoso do Boot: `"192.168.0.0/16, 172.16.0.0/12, 169.254.0.0/16, fc00::/7, 10.0.0.0/8, 100.64.0.0/10, 127.0.0.0/8, fe80::/10, ::1/128"` [VERIFIED: spring-configuration-metadata.json do spring-boot-tomcat-4.1.1, propriedade `server.tomcat.remoteip.internal-proxies`].
- O código lê só `request.getRemoteAddr()`.

### Pattern 7: Stats com uma ida ao Mongo

```java
// Source: Aggregation.stage(Bson), newAggregation(...), MongoTemplate.aggregate(Aggregation, Class, Class) verificados
Document match = new Document("code", code)
        .append("ts", new Document("$gte", Date.from(fromStart)).append("$lt", Date.from(toExclusive)));
if (!includeBots) match.append("isBot", false);
Aggregation agg = Aggregation.newAggregation(
        Aggregation.stage(new Document("$match", match)),
        Aggregation.stage(new Document("$facet", new Document()
                .append("total", List.of(new Document("$count", "n")))
                .append("daily", List.of(
                        new Document("$group", new Document("_id",
                                new Document("$dateToString", new Document("format", "%Y-%m-%d")
                                        .append("date", "$ts").append("timezone", "UTC")))
                                .append("n", new Document("$sum", 1))),
                        new Document("$sort", new Document("_id", 1))))
                .append("referrers", top("$referrer"))
                .append("browsers", top("$browser"))
                .append("os", top("$os")))));
// top(f) = [$group{_id:f, n:{$sum:1}}, $sort{n:-1, _id:1}, $limit:10]  ← desempate estável por _id
```
- `from`/`to`: `LocalDate` (`@DateTimeFormat(iso = ISO.DATE)`), inclusivos, em UTC. Default: `to = hoje (UTC)`, `from = to - 29 dias` (30 dias inclusivos). Validar `from <= to` e `days <= 366`; fora disso, 400 `/problems/invalid-period`. Formato inválido (`MethodArgumentTypeMismatchException`) também vira 400 no mesmo padrão.
- `$count` sobre zero documentos devolve array vazio: `total = 0`.
- O Java gera a série contínua (`from.datesUntil(to.plusDays(1))`) e preenche com o mapa vindo do Mongo.
- Existência via `linkRepository.existsByCode(code)`; link expirado continua 200 (D-04).
- Resposta sugerida: `{code, from, to, includeBots, total, daily:[{date, clicks}], topReferrers:[{value, clicks}], topBrowsers:[...], topOs:[...]}`.
- O índice `{code: 1, ts: -1}` atende o `$match` (igualdade + intervalo).

### Anti-Patterns to Avoid
- **Cache dentro de `@Transactional`:** `LinkService.resolve` é `@Transactional(readOnly = true)` [VERIFIED: app/.../LinkService.java:132 `@Transactional(readOnly = true)`]; colocar o cache ali abre transação e pega conexão em todo hit.
- **Um único `spring.data.redis.timeout` para tudo:** curto mata o `BLOCK`, longo transforma o fail-open em fail-slow.
- **Declarar um segundo `@Bean RedisConnectionFactory`:** desliga a auto-config do Boot [VERIFIED: `@ConditionalOnMissingBean(value=[RedisConnectionFactory])` no `LettuceConnectionConfiguration`].
- **Gauge que consulta o Valkey no scrape:** com o Valkey fora, o scrape trava. O loop atualiza `AtomicLong`s e a gauge só lê.
- **`StreamMessageListenerContainer`, `autoAcknowledge()`, `noack()`:** proibidos (D-07).
- **`acknowledgeAndDelete` / `TrimOptions.deletionPolicy(...)`:** existem na API 4.1.1 [VERIFIED: `StreamOperations.acknowledgeAndDelete`, `TrimOptions.deletionPolicy`], mas mapeiam para XACKDEL/KEEPREF/ACKED do Redis 8.2, que o Valkey 8.1 não tem [ASSUMED].
- **`code`, URL, IP ou UA como tag**, ou `request.getRequestURI()` como tag.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Confiança em proxy / XFF | Parser de `X-Forwarded-For` | Tomcat `RemoteIpValve` (`native`) | Ordem direita→esquerda, IPv6, `X-Forwarded-Proto` |
| Atomicidade INCR+TTL | Dois comandos | Script Lua (`RedisScript`) | Crash entre os comandos deixa a chave sem TTL |
| Idempotência | "Checar se existe antes de inserir" | `_id` único + bulk `UNORDERED` | Corrida entre réplicas; o banco garante |
| Retenção | Job de limpeza | Índice TTL do Mongo | Nativo, roda a cada 60 s |
| Histogramas/percentis | Timers próprios | `http.server.requests` + `percentiles-histogram` | Agregável entre réplicas |
| HMAC | SHA-256 com concatenação | `Mac.getInstance("HmacSHA256")` | Construção padrão |
| Graceful shutdown do web | Hooks próprios | `server.shutdown` (já `graceful` no 4.1.1) + `spring.lifecycle.timeout-per-shutdown-phase` | Ordem de fases do Spring |

**Key insight:** o que vale construir à mão aqui é só a cola de política (gate, codec do cache, loop do consumer). Tudo que envolve protocolo, atomicidade ou unicidade fica com o Valkey, o Mongo ou o Tomcat.

## Common Pitfalls

### Pitfall P1: Fail-open que vira fail-slow
**What goes wrong:** com o Valkey fora, cada redirect espera o `connect-timeout` (10 s por padrão) ou o `commandTimeout`.
**Why:** defaults do Lettuce e comandos enfileirados durante a reconexão (`DisconnectedBehavior.DEFAULT`).
**How to avoid:** Pattern 2 completo (connect-timeout 250 ms, timeout 150 ms, `REJECT_COMMANDS`, gate com cooldown).
**Warning signs:** IT com container pausado mede > 500 ms no segundo request.

### Pitfall P2: Hit do cache abrindo transação JPA
**What goes wrong:** p99 do hit igual ao do miss; o pool Hikari esgota sob k6.
**How to avoid:** `RedirectService` sem `@Transactional` consulta o cache e só chama `LinkService.find(code)` (transacional) no miss.

### Pitfall P3: Invalidação do negative cache antes do commit
**What goes wrong:** `DEL` dentro da transação e um GET concorrente lê o Postgres antes do commit e regrava `N`.
**How to avoid:** `TransactionSynchronizationManager.registerSynchronization(afterCommit → cache.evict(code))` em `LinkService.create`. Sempre fazer o `DEL`, inclusive para código gerado, que pode ter sido varrido antes. A janela residual é limitada pelo TTL curto do `N`; registrar no Javadoc. Falha no `DEL` com o Valkey fora conta em `linkpulse.cache.errors`.

### Pitfall P4: `XREADGROUP BLOCK` com timeout curto
**What goes wrong:** com `spring.data.redis.timeout=150ms`, todo `read(... block(2s))` termina em `QueryTimeoutException`, o loop entra em backoff e o lag cresce.
**Why:** a leitura bloqueante vai para a conexão dedicada [VERIFIED: `LettuceStreamCommands.xReadGroup` chama `isBlocking()` → `getAsyncDedicatedConnection()`], mas a espera continua limitada pelo `commandTimeout` (Pattern 2, item 2).
**How to avoid:** o consumer cria a própria `LettuceConnectionFactory` (fora do contexto Spring como `RedisConnectionFactory`) a partir de `DataRedisConnectionDetails.getStandalone()` (`getHost()`, `getPort()`, `getDatabase()`) + `getUsername()`/`getPassword()` [VERIFIED: interface no spring-boot-data-redis-4.1.1], com `commandTimeout = block + 3s`. `afterPropertiesSet()` já chama `start()`, porque `earlyStartup` é `true` por padrão [VERIFIED: construtor e `afterPropertiesSet` da `LettuceConnectionFactory`]. Usar `StringRedisTemplate` próprio (`setConnectionFactory`, `afterPropertiesSet`) e `destroy()` no fim do `stop`.

### Pitfall P5: `internal-proxies` default confia na rede do Docker
**What goes wrong:** no compose, o tráfego do host chega pelo gateway `172.x`, que está no default. Qualquer cliente manda `X-Forwarded-For` e escapa do rate limit. No kind, o Boot detecta Kubernetes e deduz forward headers mesmo sem config.
**How to avoid:** Pattern 6 (valor explícito; vazio por padrão).

### Pitfall P6: Containers parados no meio do IT mudam de porta
**What goes wrong:** `container.stop()`/`start()` do Testcontainers recria o container com outra porta mapeada, e o contexto Spring fica apontando para a porta morta.
**How to avoid:** simular a queda com `docker pause`/`unpause`: `container.getDockerClient().pauseContainerCmd(container.getContainerId()).exec()` [ASSUMED: API docker-java exposta pelo Testcontainers 2.x], sempre com `unpause` em `finally`. A pausa testa o pior caso (conectado, sem resposta). Fail-fast por `REJECT_COMMANDS` fica para a demonstração manual (`docker stop`) e a UAT.

### Pitfall P7: Mongo sem timeouts trava o consumer e o shutdown
**What goes wrong:** com o Mongo pausado, o insert espera `serverSelectionTimeout` (30 s) ou fica sem read timeout (socket sem limite), e o `stop` estoura o `timeout-per-shutdown-phase`.
**How to avoid:** `MongoClientSettingsBuilderCustomizer` [VERIFIED: classe em `org.springframework.boot.mongodb.autoconfigure`] com `applyToClusterSettings(b -> b.serverSelectionTimeout(3, SECONDS))` e `applyToSocketSettings(b -> b.connectTimeout(2, SECONDS).readTimeout(5, SECONDS))` [ASSUMED: nomes dos métodos do driver 5.8].

### Pitfall P8: Rate limit quebrando os ITs da Phase 1
**What goes wrong:** todos os ITs do contexto compartilhado fazem POST pelo MockMvc com `remoteAddr=127.0.0.1`; um limite de 20/min derruba o `LinkApiIT`.
**How to avoid:** `AbstractIT` com `linkpulse.ratelimit.limit` alto (ex.: 100000) e ITs de rate limit em contexto próprio com limite baixo. No MockMvc, isolar o IP por teste com `.with(r -> { r.setRemoteAddr("198.51.100.7"); return r; })`.

### Pitfall P9: `MAXLEN ~` apaga entradas ainda pendentes
**What goes wrong:** com backlog grande (Mongo fora por muito tempo), o trim remove entradas sem ACK; o `claim` não as devolve e os IDs ficam presos no PEL.
**How to avoid:** `maxLen` bem acima do backlog esperado (100000); no reclaim, dar XACK nos IDs pedidos que não voltaram e contar `linkpulse.clicks.lost`. O Redis 7+ remove do PEL entradas apagadas no XCLAIM [ASSUMED]; o XACK defensivo resolve nos dois casos.

### Pitfall P10: Lag `NULL`
**What goes wrong:** a gauge de lag some ou vira erro.
**Why:** o `lag` é NULL quando o grupo foi criado com um ID arbitrário ou quando entradas entre o último entregue e o último gerado foram apagadas [CITED: valkey.io/commands/xinfo-groups].
**How to avoid:** criar o grupo com `ReadOffset.from("0")` (o `createGroup` já faz MKSTREAM [VERIFIED: `DefaultStreamOperations.createGroup` passa `true` a `xGroupCreate(..., mkStream)`]); ler `getRaw().get("lag")` como `Number` e gravar `NaN` quando for null. O `XInfoGroup` 4.1.1 não tem accessor de lag; só `groupName()`, `consumerCount()`, `pendingCount()` e `lastDeliveredId()` [VERIFIED: javap].

### Pitfall P11: Gauges duplicadas entre réplicas
**What goes wrong:** pending e lag são do grupo (globais); com N réplicas, `sum()` no dashboard multiplica o valor.
**How to avoid:** documentar no Javadoc e na Phase 3 o uso de `max()`.

### Pitfall P12: Índice TTL não muda com `createIndex`
**What goes wrong:** trocar `linkpulse.clicks.retention` e reiniciar falha com conflito de opções do índice.
**Why:** "You cannot use `createIndex()` to change the value of `expireAfterSeconds` of an existing index. Instead, use the `collMod` database command" [CITED: mongodb.com/docs/manual/core/index-ttl/].
**How to avoid:** no startup, ler `getIndexInfo()` e, se o TTL divergir, rodar `collMod` (via `mongo.executeCommand(new Document("collMod", "click_events").append("index", ...))`). Também: "TTL indexes are single-field indexes", então `ts` precisa de índice próprio, separado de `{code, ts}`. O campo `ts` precisa ser `Date` (`Instant` no record).

### Pitfall P13: Bots, curl e o smoke test do CI
**What goes wrong:** se `curl` for bot, o smoke test da Phase 4 (`curl` → redirect → `stats`) vê `total=0`.
**How to avoid:** decisão explícita (ver Open Questions). Recomendação: clientes HTTP de linha de comando contam como bot, e o smoke test e o seed mandam um UA de navegador ou usam `?includeBots=true`. Isso vai documentado no OpenAPI.

### Pitfall P14: Shutdown na ordem errada
**How to avoid:** fases. Web graceful = 2147482623 [VERIFIED: `WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE`]; web stop = 2147481599 [VERIFIED: `WebServerStartStopLifecycle.getPhase`]; publisher = `DEFAULT_PHASE - 4096`; consumer = `DEFAULT_PHASE - 8192`; `LettuceConnectionFactory` = 0 [VERIFIED: construtor atribui `phase = 0`]. Fases maiores param primeiro. O `MongoClient` fecha na destruição dos beans, depois de todos os `stop`. O stop do consumer **não** interrompe a thread: ele só baixa a flag, e o `BLOCK` (2 s) limita a espera. `server.shutdown` já é `graceful` [VERIFIED: metadata `"defaultValue": "graceful"`]; manter `spring.lifecycle.timeout-per-shutdown-phase` (default 30 s [ASSUMED]) acima de `block + timeout de escrita`.

## Code Examples

### Testcontainers (Boot 4.1.1 + TC 2.0.5)
```java
// Source: RedisContainerConnectionDetailsFactory aceita Container<?> com nome "redis" (bytecode: ANY_CONNECTION_NAME,
// nomes "redis", "redis/redis-stack", "redis/redis-stack-server"); MongoDbContainerConnectionDetailsFactory<org.testcontainers.mongodb.MongoDBContainer>
@Bean
@ServiceConnection(name = "redis")
GenericContainer<?> valkey() {
    return new GenericContainer<>(DockerImageName.parse("valkey/valkey:8.1"))
            .withExposedPorts(6379)
            .withCommand("valkey-server", "--maxmemory", "128mb", "--maxmemory-policy", "volatile-lru");
}

@Bean
@ServiceConnection
MongoDBContainer mongo() {
    return new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
}
```

### Índices explícitos (DATA-02)
```java
// Source: IndexOperations.createIndex(IndexDefinition), Index.on/named/expire(Duration) verificados (5.1.1)
IndexOperations idx = mongo.indexOps("click_events");
idx.createIndex(new Index().on("code", Sort.Direction.ASC).on("ts", Sort.Direction.DESC).named("code_ts"));
idx.createIndex(new Index().on("ts", Sort.Direction.ASC).expire(retention).named("ts_ttl"));
```
Rodar no startup do consumer (com retry), **não** num `@PostConstruct`: com o Mongo fora, o app ainda precisa subir e redirecionar.

### Documento do clique
```java
@Document("click_events")
public record ClickDocument(
        @Id String id,                 // ID do stream, ex. "1728212345678-0"
        String code,
        Instant ts,                    // BSON Date (TTL e $dateToString)
        String referrer,               // host ou "(direct)"
        String browser,
        String os,
        @Field("isBot") boolean bot,
        String ipHash) {               // HMAC hex; nunca o IP
}
```

### Configuração (application.yml, adições)
```yaml
spring:
  data:
    redis:
      timeout: 150ms
      connect-timeout: 250ms
      repositories:
        enabled: false               # evita varredura de repositórios Redis (só JPA usa repositório)
    mongodb:
      repositories:
        type: none
  mongodb:                           # Boot 4: prefixo spring.mongodb.* (spring.data.mongodb.* é legado)
    database: linkpulse
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
linkpulse:
  cache: { default-ttl: 24h, negative-ttl: 60s, gone-ttl: 24h, bypass-cooldown: 2s }
  clicks:
    stream-key: lp:clicks
    group: click-writers
    max-len: 100000
    publisher: { threads: 2, queue-capacity: 10000 }
    consumer: { enabled: true, batch-size: 500, block: 2s, reclaim-interval: 30s, min-idle: 60s }
    retention: 90d
  # ip-hash-key: chave de topo (linkpulse.ip-hash-key), porque cliques (02-02) e rate limit (02-09) usam o mesmo IpHasher.
  # Sem valor no application.yml: vem de LINKPULSE_IP_HASH_KEY (obrigatória fora do dev, >= 16 caracteres);
  # o application-dev.yml traz uma chave fixa só de desenvolvimento (Open Question 1, RESOLVED).
  ratelimit: { enabled: true, limit: 20, window: 1m }
  # stats: 30 dias padrão, 366 dias no máximo e 10 itens por top viraram constantes do contrato público
  # (StatsPeriod.DEFAULT_DAYS/MAX_DAYS e StatsService.TOP_SIZE, plano 02-07), não configuração.
```
Propriedades verificadas no metadata 4.1.1: `spring.data.redis.timeout`, `spring.data.redis.connect-timeout`, `spring.data.redis.repositories.enabled`, `spring.data.mongodb.repositories.type`, `spring.mongodb.database`/`uri`, `management.metrics.distribution.percentiles-histogram`, `server.tomcat.remoteip.internal-proxies`, `server.forward-headers-strategy`, `server.shutdown`. Que o `spring.mongodb.database` prevaleça sobre a connection string do `@ServiceConnection` é [ASSUMED]; um IT confirma lendo `mongo.getDb().getName()`.

## Catálogo de métricas (OBS-01)

| Métrica (Micrometer) | Tipo | Tags (todas de baixa cardinalidade) |
|---|---|---|
| `linkpulse.cache.requests` | Counter | `result=hit\|miss\|negative_hit\|error\|bypass` |
| `linkpulse.cache.errors` | Counter | `operation=get\|put\|evict` |
| `linkpulse.valkey.unavailable` | Counter | — (aberturas do gate) |
| `linkpulse.ratelimit.requests` | Counter | `result=allowed\|rejected\|error` |
| `linkpulse.clicks.publish` | Counter | `result=published\|rejected\|failed` (rejected+failed = "descartados") |
| `linkpulse.clicks.publish.queue` | Gauge | — |
| `linkpulse.clicks.persisted` / `.duplicates` / `.invalid` / `.lost` / `.claimed` | Counter | — |
| `linkpulse.clicks.consumer.batch` | DistributionSummary | — |
| `linkpulse.clicks.consumer.write` | Timer | `outcome=success\|failure` |
| `linkpulse.clicks.consumer.errors` | Counter | — |
| `linkpulse.clicks.stream.pending` / `.lag` / `.length` | Gauge (AtomicLong/AtomicReference) | — |
| `http.server.requests` | Timer (Boot) | `uri` = padrão da rota |

Guardar a referência forte do objeto da gauge: o `MeterRegistry.gauge` mantém só referência fraca [ASSUMED: comportamento documentado do Micrometer].

## Desenho dos testes de integração (QUAL-02)

**Base:** `TestcontainersConfiguration` ganha `valkey()` e `mongo()` (acima). `AbstractIT` ganha `properties = {"linkpulse.ratelimit.limit=100000", "linkpulse.clicks.consumer.block=200ms", "linkpulse.clicks.consumer.reclaim-interval=1s", "linkpulse.clicks.consumer.min-idle=1s", "linkpulse.cache.bypass-cooldown=200ms", "linkpulse.ip-hash-key=test-ip-hash-key-0123456789"}` (a chave precisa de pelo menos 16 caracteres). Contextos diferentes iniciam containers diferentes (são beans), então o número de contextos novos deve ficar no mínimo.

| Cenário | Contexto | Como provar |
|---|---|---|
| Hit servido do cache | compartilhado | GET → 302; `jdbc.update("delete from links where code=?")`; GET de novo → ainda 302; `cache.requests{result=hit}` +1 |
| TTL ≤ expiração | compartilhado | Link expirando em 2 s → GET → `redis.getExpire(key, MILLISECONDS) <= 2000`; Awaitility → 410 |
| Negative cache invalidado | compartilhado | GET `/meu-alias_1` → 404; POST com alias → 201; GET → 302 |
| HEAD não gera clique | compartilhado | `mvc.head()` → 302 e `clicks.publish` inalterado; XLEN inalterado |
| Valkey fora → 302 rápido | compartilhado | `pause` valkey; 1º GET 302 (≤ timeout); 2º GET 302 em < 100 ms (gate); `cache.requests{result=bypass\|error}` > 0; `clicks.publish{result=failed}` > 0; `unpause` no `finally` |
| Fluxo ponta a ponta + stats | compartilhado | N GETs com UAs/referrers variados + 1 bot → Awaitility até `/links/{code}/stats` `total == N` (sem bot); `includeBots=true` → N+1; tops e série com zeros; nenhum campo `ip`/valor de IP em `click_events`; `listIndexes` contém `code_ts` e `ts_ttl` (com `expireAfterSeconds`) |
| XACK só após gravação | consumer instanciado no teste, stream/grupo `lp:test:{uuid}` | Writer injetado que lança → `pending(...).getTotalPendingMessages() == n`, Mongo vazio; writer real + reclaim → Mongo n, pending 0 |
| Pendentes de consumer morto, sem duplicata | idem | XADD n; `read(Consumer.from(g, "dead-1"), ...)` sem ACK; inserir parte dos docs direto no Mongo (crash entre insert e ACK); consumer "live" com `min-idle=100ms` → Mongo **exatamente** n, pending 0, `duplicates` > 0 |
| Consumer vivo após queda do Mongo | compartilhado | `pause` mongo; GETs; `consumer.errors` > 0; `unpause`; Awaitility → stats `total` correto |
| Shutdown conclui o lote | consumer instanciado | Writer que bloqueia num `CountDownLatch` → `stop(callback)` → callback ainda não rodou → libera o latch → callback roda; docs gravados e pending 0 |
| 429 + Retry-After + XFF de proxy confiável | **contexto próprio** `RANDOM_PORT`, `limit=3`, `internal-proxies=127.0.0.1/32` | `HttpClient` real: 4º POST com XFF A → 429, `Retry-After` ≥ 1, `problem+json`; POST com XFF B → 201 |
| XFF de origem não confiável ignorado | **contexto próprio** `RANDOM_PORT`, `limit=3`, `internal-proxies` default do projeto (vazio) | 4 POSTs com XFFs diferentes → 4º 429 |
| Cardinalidade/exposição | um dos contextos `RANDOM_PORT` | GET `http://localhost:{managementPort}/actuator/prometheus` contém `linkpulse_cache_requests_total` e **não** contém o código criado; se o endpoint não existir no teste, adicionar `spring-boot-starter-micrometer-metrics-test` e `@AutoConfigureMetrics` [VERIFIED: a classe existe no módulo `spring-boot-micrometer-metrics-test` 4.1.1, que hoje não está no classpath] |

Detalhes: o MockMvc **não** passa pelo `RemoteIpValve` (é um valve do Tomcat), então RATE-02 exige `RANDOM_PORT` com cliente HTTP real. Em `RANDOM_PORT`, a porta de management também precisa ser aleatória; usar `@LocalManagementPort` [ASSUMED: o `@SpringBootTest` sobrescreve a porta de management separada]. Para o consumer instanciado no teste, a classe precisa de construtor com `(StreamSettings, StringRedisTemplate/fábrica, ClickEventWriter, ClickMetrics, Clock)`, e o bean do contexto é só uma instância dela.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RedisConnectionDetails` / `spring.data.mongodb.*` | `DataRedisConnectionDetails` / `spring.mongodb.*` | Boot 4.0 (modularização) | Nomes de classes e prefixos novos [VERIFIED: jars 4.1.1] |
| `@AutoConfigureObservability` | `@AutoConfigureMetrics` (módulo `spring-boot-micrometer-metrics-test`) | Boot 4.0 | Só se o IT precisar do export [VERIFIED: classe existe] |
| `org.testcontainers.containers.MongoDBContainer` | `org.testcontainers.mongodb.MongoDBContainer` | TC 2.0 | A antiga ainda existe (factory "Deprecated") [VERIFIED] |
| `server.shutdown=immediate` default | `graceful` default | Boot 3.4+ [ASSUMED quanto à versão] | Não precisa configurar; manter explícito para documentar |
| `internal-proxies` por regex | CIDR | Tomcat 11 (regex deprecated) | Usar CIDR [CITED: Tomcat 11 valve docs] |

**Deprecated/outdated:** `IndexOperations.ensureIndex` (default que delega) → usar `createIndex` [VERIFIED: `ensureIndex` é `default`, `createIndex` é `abstract`].

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Default de connect timeout do Lettuce é 10 s | Pattern 2 | Baixo: o valor é sobrescrito de qualquer jeito |
| A2 | Exceção de `preHandle` chega ao `GlobalExceptionHandler` com corpo Problem | Pattern 5 | Médio: 429 sem `problem+json`; o IT detecta |
| A3 | `pauseContainerCmd`/`unpauseContainerCmd` acessíveis via `container.getDockerClient()` no TC 2.0.5 | Pitfall P6 | Médio: usar `DockerClientFactory.instance().client()` |
| A4 | Nomes `serverSelectionTimeout`/`readTimeout` nos builders do driver 5.8 | Pitfall P7 | Baixo: o compilador acusa |
| A5 | XCLAIM no Valkey 8.1 omite e remove do PEL entradas aparadas | Pitfall P9 | Baixo: o XACK defensivo cobre |
| A6 | Valkey 8.1 não implementa XACKDEL nem as políticas KEEPREF/ACKED | Anti-patterns | Baixo: a recomendação é não usar |
| A7 | `insertAll` é ordenado (para na 1ª duplicata) | Pattern 4 | Baixo: o bulk `UNORDERED` é usado de qualquer forma |
| A8 | `spring.mongodb.database` prevalece sobre o `@ServiceConnection` | Config | Baixo: o banco cai em `test`; o IT verifica |
| A9 | `@SpringBootTest(RANDOM_PORT)` randomiza a porta de management separada | Testes | Médio: conflito na 8081; fixar `management.server.port=0` no teste |
| A10 | Micrometer guarda referência fraca da gauge | Métricas | Baixo |
| A11 | Exceções do Lettuce podem escapar da tradução do Spring | Pattern 2 | Baixo: o `catch (RuntimeException)` cobre |
| A12 | Yauaa é pesado em memória e startup | Alternatives | Nenhum (não recomendado) |
| A13 | `server.shutdown=graceful` virou default no Boot 3.4 | State of the Art | Nenhum: o 4.1.1 verificado já é `graceful` |

## Open Questions (RESOLVED)

Todas as perguntas abaixo foram fechadas no planejamento. Cada item traz a decisão que os planos adotaram e onde ela é implementada.

1. **Chave do HMAC do IP em prod/compose** — RESOLVED
   - What we know: a chave do HMAC precisa existir; dev e test usam um valor fixo.
   - What's unclear (na pesquisa): falhar a subida sem a chave (como `base-url`) ou gerar uma aleatória por boot (hash não comparável entre réplicas e reinícios)?
   - **Decisão (02-02, 02-03, 02-09):** a propriedade é **`linkpulse.ip-hash-key`**, de topo e não dentro de `linkpulse.clicks`, porque cliques (02-02) e rate limit (02-09) usam o mesmo `IpHasher`. A variável de ambiente é **`LINKPULSE_IP_HASH_KEY`**. Ela é obrigatória fora do profile dev: o construtor do `IpHasher` (bean do `ClickConfig`) lança `IllegalArgumentException` de mensagem fixa, sem ecoar o valor, se a chave for nula, em branco ou tiver menos de 16 caracteres, e a aplicação não sobe. O `application.yml` não traz valor; o `application-dev.yml` traz uma chave fixa só de desenvolvimento, e os ITs usam `linkpulse.ip-hash-key=test-ip-hash-key-0123456789` no `AbstractIT`. O hash é `HMAC-SHA256(key, "<dia UTC>|<ip>")`, com rotação diária sem estado. O `toString()` de `LinkPulseProperties` mascara a chave (`ipHashKey=****`, 02-03). O compose da Phase 3 passa `LINKPULSE_IP_HASH_KEY` e o Helm passa via Secret.
2. **`curl`/`wget`/clientes HTTP e UA vazio contam como bot?** — RESOLVED
   - **Decisão (02-05, `UserAgentClassifier`):** sim. `curl`, `wget`, `python-requests`, `python-urllib`, `aiohttp`, `Go-http-client`, `Java-http-client`, `k6` e outros clientes de linha de comando contam como bot, assim como crawlers, previews de link, `HeadlessChrome` e UA vazio ou em branco. `okhttp` não conta (apps Android usam essa lib). A lista é versionada em `BOT_TOKENS` e coberta pelo `UserAgentClassifierTest`. A Phase 3 (seed) e a Phase 4 (smoke) usam UA de navegador ou `includeBots=true`; o README e o OpenAPI do `/stats` (02-07) avisam que o `curl` conta como bot.
3. **Health com Redis/Mongo no classpath** — RESOLVED
   - What we know: os starters registram health indicators; com o Valkey parado, o `/actuator/health` agregado pode ir a DOWN.
   - What's unclear (na pesquisa): o K8S-02 pede readiness com Valkey, o que contradiz o fail-open.
   - **Decisão:** nesta fase nenhum plano mexe em `management.endpoint.health.group.*`; o health agregado continua o default do Boot. Os smokes de dev esperam o `/actuator/health` antes de derrubar o Valkey (02-03), então o DOWN agregado com o Valkey parado não quebra nenhuma verificação. A separação liveness (sem dependências) × readiness (provavelmente só Postgres, coerente com o fail-open) fica para a Phase 3 (imagem e `HEALTHCHECK`) e a Phase 4 (probes do Helm, K8S-02).
4. **Referrer não parseável** (`android-app://...`, lixo) — RESOLVED
   - **Decisão (02-02, `ReferrerNormalizer.toHost`):** host quando `new URI(referer.strip()).getHost()` existir (inclui `android-app://com.google.android.gm` → `com.google.android.gm`); senão, `"(direct)"`. Também viram `"(direct)"`: nulo, em branco, `URISyntaxException` e entrada com mais de 2048 caracteres. O host sai em minúsculas (`Locale.ROOT`), sem pontos finais, sem `www.` removido (mantém fiel) e truncado em 255; userinfo, porta, path, query e fragmento nunca saem. A normalização acontece na ingestão (o stream e o Mongo só guardam o host).
5. **Stats com o Mongo fora** — RESOLVED
   - **Decisão (02-07):** o `StatsService` envolve a ida ao Mongo num `catch (DataAccessException e)` (cobre `DataAccessResourceFailureException` e os timeouts traduzidos) e lança `StatsProblems.unavailable()`: 503 `/problems/stats-unavailable` em Problem Details, com detail fixo e sem logar código nem período. A tradução fica no serviço, e não no `GlobalExceptionHandler`, para que só o `/stats` vire 503 e as outras rotas mantenham o 500 genérico. Os timeouts curtos do driver (`MongoConfig`, 02-05) limitam a espera; `StatsIT#mongoOutageIsServiceUnavailable` prova o 503 em menos de 15 s.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | build | ✓ | 21.0.10 (Oracle) | — |
| Docker CLI | Testcontainers, compose dev | ✓ CLI | 29.8.0 | — |
| Docker daemon | ITs | ✗ no momento da pesquisa (`npipe ... dockerDesktopLinuxEngine` não encontrado) | — | Iniciar o Docker Desktop antes do `make verify`; o CI (ubuntu-latest) tem Docker |
| GNU make | `make run`/`verify` | ✓ | 4.4.1 | `cd app && ./mvnw ...` |
| Maven deps 4.1.1 | build | ✓ BOM em `~/.m2` | — | — |

**Missing dependencies with no fallback:** nenhuma (o daemon só precisa ser iniciado).
**Missing dependencies with fallback:** nenhuma.

Também: `compose.dev.yaml` ganha `valkey/valkey:8.1` (`127.0.0.1:6379`, `--maxmemory 256mb --maxmemory-policy volatile-lru`, healthcheck `valkey-cli ping`) e `mongo:8.0` (`127.0.0.1:27017`, healthcheck `mongosh --quiet --eval "db.adminCommand('ping')"`). O `application-dev.yml` aponta para eles; o alvo `db-up` do Makefile sobe os três.

## Security Domain

### Applicable ASVS Categories (nível 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | API sem auth (fora de escopo) |
| V3 Session Management | no | — |
| V4 Access Control | no | Stats públicas: decisão consciente, documentar |
| V5 Input Validation | yes | `from`/`to` `LocalDate` + limites; `code` pela regex do path; UA truncado em 512, Referer em 2048 antes do parse; campos do stream validados no consumer (inválido → ACK + métrica) |
| V6 Cryptography | yes | `HmacSHA256` do JDK; chave só por config/secret, nunca logada |
| V7 Error/Logging | yes | Logs sem IP, UA, URL ou referrer completo; Problem sem detalhe interno |
| V8 Data Protection | yes | IP nunca em claro (nem no stream, nem na chave do rate limit); retenção por TTL; UA cru não persistido |
| V11 Business Logic / anti-automação | yes | Rate limit atômico por IP real; fail-open documentado |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Spoof de `X-Forwarded-For` para burlar o rate limit | Spoofing | `RemoteIpValve` com `internal-proxies` explícito (vazio por padrão) |
| Enumeração de códigos batendo no Postgres | DoS | Negative cache `N` com TTL curto |
| Envenenamento do cache (URL com `|`) | Tampering | Codec com `split(..., 3)` e prefixo de tipo |
| Inflar cliques por bot | Repudiation/Integrity | `isBot` + exclusão padrão; rate limit não cobre GET (decisão: o redirect não tem limite) |
| Vazamento de PII por Referer completo | Information Disclosure | Só o host é persistido |
| Reidentificação por hash de IP | Information Disclosure | HMAC com chave secreta + dia; sem a chave, a força bruta do espaço IPv4 não funciona |
| Stream crescendo até OOM do Valkey | DoS | `MAXLEN ~ 100000` + `volatile-lru` (stream sem TTL não é despejado) |
| Exaustão de memória pelo publisher | DoS | Fila limitada (10000) e descarte contado |
| Mitigação T-08-06 (loop via DNS) | DoS | O rate limit de criação é parte da mitigação parcial (STATE.md) |

## Sources

### Primary (HIGH confidence)
- `~/.m2/.../spring-boot-dependencies-4.1.1.pom` (linhas 35, 49, 83, 126, 150, 153, 195, 209, 215, 3148, 3598) e `spring-data-bom-2026.0.1.pom` (108-119): versões efetivas
- Jars baixados do Maven Central e inspecionados com `javap`: spring-data-redis-4.1.1 (`StreamOperations`, `RedisStreamCommands`, `XAddOptions`, `TrimOptions`, `XClaimOptions`, `StreamReadOptions`, `StreamInfo$XInfoGroup`, `PendingMessages*`, `LettuceStreamCommands`, `LettuceConnection`, `LettuceConnectionFactory`, `DefaultStreamOperations`, `RedisScript`, `MapRecord`, `ReadOffset`, `StreamOffset`, `Consumer`, `RecordId`); spring-boot-data-redis-4.1.1 (`LettuceConnectionConfiguration`, `DataRedisConnectionDetails`, customizers, `RedisContainerConnectionDetailsFactory`, metadata); spring-data-mongodb-5.1.1 (`BulkOperations`, `BulkOperationException`, `DefaultBulkOperations`, `Index`, `IndexOperations`, `Aggregation`, `DateOperators`, `MongoTemplate`); spring-boot-mongodb/data-mongodb-4.1.1 (metadata, factories do Testcontainers, `MongoClientSettingsBuilderCustomizer`); spring-boot-tomcat-4.1.1 (`TomcatWebServerFactoryCustomizer`, metadata remoteip); spring-boot-web-server-4.1.1 (fases de lifecycle, metadata `server.shutdown`); tomcat-embed-core-11.0.24 (`RemoteIpValve.setInternalProxies`); lettuce-core-7.5.2 (`ClientOptions`); spring-web/context 7.0.9 (`ErrorResponseException`, `SmartLifecycle`); testcontainers-bom/mongodb 2.0.5; micrometer-bom 1.17.1; spring-boot-micrometer-metrics(-test) 4.1.1
- Código do repo lido nesta sessão: `app/pom.xml`, `application*.yml`, `LinkService.java`, `RedirectController.java`, `LinkPulseProperties.java`, `LinkProblems.java`, `GlobalExceptionHandler.java`, `ApplicationConfig.java`, `LinkController.java`, `Link.java`, `LinkRepository.java`, `AbstractIT.java`, `TestcontainersConfiguration.java`, `RedirectIT.java`, `compose.dev.yaml`, `Makefile`, `ci.yml`, `config/spotbugs/exclude.xml`

### Secondary (MEDIUM confidence)
- [valkey.io/commands/xinfo-groups](https://valkey.io/commands/xinfo-groups/): campos `pending`, `entries-read`, `lag` e casos de NULL
- [mongodb.com/docs/manual/core/index-ttl](https://www.mongodb.com/docs/manual/core/index-ttl/): TTL de campo único, monitor de 60 s, `collMod`
- [Spring Framework 7.0.9: Mapping Requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html): HEAD implícito em `@GetMapping`
- [Tomcat 11 Valve docs](https://tomcat.apache.org/tomcat-11.0-doc/config/valve.html): `internalProxies`/`trustedProxies`, regex deprecated
- Docker Hub tags API: `valkey/valkey:8.1`, `mongo:8.0`, `mongo:8.0.32`; Maven metadata de Yauaa e uap-java

### Tertiary (LOW confidence)
- Itens do Assumptions Log

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH (BOM e jars lidos)
- Architecture: HIGH nas APIs; MEDIUM nos tempos de fail-open e no comportamento com container pausado (dependem de IT)
- Pitfalls: HIGH nos verificados em bytecode (P4, P5, P10, P14); MEDIUM nos demais

**Research date:** 2026-10-10
**Valid until:** 2026-11-10 (stack fixada pelo BOM 4.1.1; revalidar se o parent mudar)
