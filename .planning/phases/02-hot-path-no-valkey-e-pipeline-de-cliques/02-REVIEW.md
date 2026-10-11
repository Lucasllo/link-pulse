---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
reviewed: 2026-10-11T21:05:13Z
depth: standard
files_reviewed: 61
files_reviewed_list:
  - .github/dependabot.yml
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
  - app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java
  - app/src/main/java/dev/linkpulse/ratelimit/RateLimitInterceptor.java
  - app/src/main/java/dev/linkpulse/ratelimit/RateLimitProblems.java
  - app/src/main/java/dev/linkpulse/stats/StatsController.java
  - app/src/main/java/dev/linkpulse/stats/StatsPeriod.java
  - app/src/main/java/dev/linkpulse/stats/StatsProblems.java
  - app/src/main/java/dev/linkpulse/stats/StatsResponse.java
  - app/src/main/java/dev/linkpulse/stats/StatsService.java
  - app/src/main/resources/application.yml
  - app/src/main/resources/application-dev.yml
  - app/src/test/java/dev/linkpulse/AbstractIT.java
  - app/src/test/java/dev/linkpulse/cache/CacheIT.java
  - app/src/test/java/dev/linkpulse/cache/LinkCacheCodecTest.java
  - app/src/test/java/dev/linkpulse/cache/ValkeyAvailabilityTest.java
  - app/src/test/java/dev/linkpulse/cache/ValkeyOutageIT.java
  - app/src/test/java/dev/linkpulse/click/ClickConsumerLifecycleIT.java
  - app/src/test/java/dev/linkpulse/click/ClickDocumentTest.java
  - app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java
  - app/src/test/java/dev/linkpulse/click/ClickPublisherTest.java
  - app/src/test/java/dev/linkpulse/click/ClickPublishIT.java
  - app/src/test/java/dev/linkpulse/click/ClickRedeliveryIT.java
  - app/src/test/java/dev/linkpulse/click/ClickResilienceIT.java
  - app/src/test/java/dev/linkpulse/click/ReferrerNormalizerTest.java
  - app/src/test/java/dev/linkpulse/click/UserAgentClassifierTest.java
  - app/src/test/java/dev/linkpulse/common/IpHasherTest.java
  - app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java
  - app/src/test/java/dev/linkpulse/MetricsCardinalityIT.java
  - app/src/test/java/dev/linkpulse/OpenApiIT.java
  - app/src/test/java/dev/linkpulse/ratelimit/RateLimiterTest.java
  - app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java
  - app/src/test/java/dev/linkpulse/ratelimit/TrustedProxyRateLimitIT.java
  - app/src/test/java/dev/linkpulse/stats/StatsIT.java
  - app/src/test/java/dev/linkpulse/stats/StatsPeriodTest.java
  - app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java
  - compose.dev.yaml
  - Makefile
  - README.md
findings:
  critical: 0
  warning: 7
  info: 13
  total: 20
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-10-11T21:05:13Z
**Depth:** standard
**Files Reviewed:** 61
**Status:** issues_found

## Summary

Revisei o código de produção em `app/src/main` (cache-aside com gate de disponibilidade, publisher e consumer do stream de cliques, rate limit, `/stats`), a configuração e, em segundo plano, os testes, o compose, o Makefile e o README.

No geral o código é cuidadoso. O XACK só acontece depois da gravação, a idempotência usa o `_id` do stream, o script Lua do rate limit é atômico, o `RemoteIpValve` só confia em proxy listado e as métricas têm cardinalidade controlada. Não achei nenhum defeito de nível BLOCKER.

Os pontos fracos estão nas bordas:

- **Gate do Valkey:** abre para todo mundo de uma vez quando a janela vence (thundering herd) e fecha por qualquer exceção, inclusive erros que não são de disponibilidade.
- **Entradas sem limite vindas do cliente:** o código no path do redirect não tem tamanho máximo e vira chave de negative cache. Datas extremas no `/stats` geram 500 com stack no log.
- **Rate limit por IPv6 exato:** um único cliente com um /64 burla o limite.
- **Consumer:** um `start()` logo depois de um `stop()` não espera o loop anterior sair. A fábrica dedicada ignora TLS, o que quebra o pipeline no ElastiCache com criptografia em trânsito.

## Warnings

### WR-01: Gate do Valkey libera todas as requisições ao mesmo tempo quando a janela vence (fail-slow sob carga)

**File:** `app/src/main/java/dev/linkpulse/cache/ValkeyAvailability.java:56-58`
**Issue:** O Javadoc (linhas 16-17) e o README (linha 80) dizem que "só a primeira chamada de cada janela paga" o timeout. Não é o que o código faz. `allowRequest()` só compara `clock.millis() >= openUntil`. Quando a janela de 2 s vence com o Valkey ainda pausado ou particionado, todas as threads que chegam antes de a primeira falha voltar (até 150 ms de timeout do Lettuce) passam pelo gate. Cada uma paga os 150 ms em `LinkCache.get`, no `XADD` e no script do rate limit. Com centenas de requests por segundo, isso significa dezenas de requests lentos a cada ciclo de ~2,15 s, que é o fail-slow que o REDIR-05 quer evitar. As virtual threads não limitam esse efeito.
**Fix:** Usar half-open com uma única sonda: só a thread que ganhar um CAS testa o Valkey, e as outras continuam em bypass até ela voltar.
```java
private static final long PROBING = Long.MAX_VALUE;

public boolean allowRequest() {
    long until = openUntil.get();
    long now = clock.millis();
    if (now < until) {
        return false;
    }
    if (until == Long.MIN_VALUE) {
        return true;                       // fechado (saudável)
    }
    // janela venceu: só uma thread vira a sonda
    return openUntil.compareAndSet(until, PROBING);
}

public void recordSuccess() {              // chamado após uma operação bem-sucedida
    openUntil.set(Long.MIN_VALUE);
}
```
`recordFailure()` continua gravando `now + cooldown`, e os chamadores (`LinkCache`, `ClickPublisher`, `RateLimiter`) chamam `recordSuccess()` depois de um comando bem-sucedido. Corrigir também o Javadoc e o README.

### WR-02: Qualquer exceção do Valkey fecha o gate global, inclusive erros determinísticos (OOM, WRONGTYPE)

**File:** `app/src/main/java/dev/linkpulse/cache/LinkCache.java:132-136,209-211`, `app/src/main/java/dev/linkpulse/click/ClickPublisher.java:177-182`, `app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java:127-132`
**Issue:** Os três chamadores tratam qualquer `RuntimeException` como "Valkey indisponível" e chamam `availability.recordFailure()`. Erros de comando com o servidor saudável também caem aí:
- `OOM command not allowed when used memory > 'maxmemory'`: com `volatile-lru` e o stream `lp:clicks` sem TTL, isso acontece quando não sobra chave volátil para despejar;
- `WRONGTYPE`, se `lp:clicks` existir com outro tipo;
- `NOSCRIPT`/`BUSY`.

Um `XADD` que falha por OOM abre a janela, e com ela o cache do redirect e o rate limit também ficam desviados. As leituras funcionariam, mas todo o tráfego vai para o Postgres. Como cada clique tenta de novo depois do cooldown, o cache fica desligado em ciclos enquanto o problema durar, e o rate limit fica em fail-open o tempo todo.
**Fix:** Só abrir o gate para falhas de disponibilidade (conexão e timeout) e contar os outros erros sem mexer no gate:
```java
static boolean isUnavailability(RuntimeException e) {
    return e instanceof RedisConnectionFailureException
            || e instanceof QueryTimeoutException          // org.springframework.dao
            || e.getCause() instanceof RedisCommandTimeoutException;
}
...
} catch (RuntimeException e) {
    if (ValkeyAvailability.isUnavailability(e)) {
        availability.recordFailure();
    }
    failed.increment();
}
```

### WR-03: Código do path sem tamanho máximo vira chave de negative cache: amplificação de memória no Valkey e despejo do cache legítimo

**File:** `app/src/main/java/dev/linkpulse/link/RedirectController.java:60`, `app/src/main/java/dev/linkpulse/link/RedirectService.java:81-84`, `app/src/main/java/dev/linkpulse/cache/LinkCache.java:171-173`, `app/src/main/java/dev/linkpulse/stats/StatsController.java:47`
**Issue:** A regex `{code:[A-Za-z0-9_-]+}` não tem limite de tamanho, mas nenhum código válido passa de 32 caracteres (alias de 4 a 32; código gerado com 7). O `GET /{code}` não tem rate limit e não tem autenticação. Cada código inexistente custa um `SELECT` no Postgres e grava `lp:link:<code>` com até ~8 KB (limite da linha de request do Tomcat) e TTL de 60 s. Com o `maxmemory 256mb` + `volatile-lru` do compose (e do ElastiCache), um cliente a ~500 req/s de códigos longos e aleatórios enche o Valkey em um minuto. O LRU então despeja os `L|...` legítimos e as chaves `lp:rl:*`, e o hot path cai inteiro no Postgres. O `/links/{code}/stats` também consulta o Postgres sem limite.
**Fix:** Limitar o domínio do código na rota e recusar com 404 sem tocar no cache nem no banco:
```java
@GetMapping("/{code:[A-Za-z0-9_-]{1,32}}")
```
Mesmo limite no `StatsController` (`/links/{code:[A-Za-z0-9_-]{1,32}}/stats`). Opcionalmente, centralizar `MAX_CODE_LENGTH = 32` junto do `AliasPolicy` e só gravar `N` para códigos que passariam pelo formato de código gerado ou de alias.

### WR-04: `/stats` com datas extremas responde 500 e registra stack trace em ERROR (input não validado)

**File:** `app/src/main/java/dev/linkpulse/stats/StatsPeriod.java:62-73`, `app/src/main/java/dev/linkpulse/stats/StatsService.java:89-90,147`
**Issue:** `@DateTimeFormat(iso = DATE)` aceita anos estendidos (`+999999999-12-31`, enviado como `%2B999999999-12-31`). Só com `to` extremo, o período (30 dias) é válido, mas `endExclusive()` chama `to.plusDays(1)`, que lança `DateTimeException` acima de `LocalDate.MAX`. Com anos como `+300000000`, o `Date.from(...)` lança `IllegalArgumentException` (overflow de epoch millis). As duas exceções escapam do `catch (DataAccessException)` e chegam ao `handleUnexpected`, que responde 500 e faz `LOG.error(..., ex)` com a stack inteira. Qualquer cliente anônimo consegue gerar 500s e encher o log.
**Fix:** Validar o intervalo de datas em `StatsPeriod.resolve` e responder com o 400 de período inválido:
```java
private static final LocalDate MIN_DATE = LocalDate.of(1970, 1, 1);
private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 30);
...
if (start.isBefore(MIN_DATE) || end.isAfter(MAX_DATE)
        || start.isAfter(end) || period.days() > MAX_DAYS) {
    throw StatsProblems.invalidPeriod(start, end);
}
```
Também dá para limitar a `to <= todayUtc.plusDays(1)`, porque cliques futuros não existem.

### WR-05: Rate limit por endereço IPv6 exato: um único cliente com um /64 tem limite praticamente infinito

**File:** `app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java:116-124`, `app/src/main/java/dev/linkpulse/ratelimit/RateLimitInterceptor.java:54`
**Issue:** A chave é `HMAC(dia|ip)` do texto exato de `getRemoteAddr()`. Um cliente IPv6 comum recebe um /64 (2^64 endereços) e pode trocar de endereço a cada request (privacy extensions, ou de propósito). Cada endereço novo ganha 20 criações por minuto, então o controle do RATE-01 fica sem efeito para atacantes IPv6. Atrás de um proxy confiável, o IP vem do `X-Forwarded-For` como texto, e o mesmo endereço em formas diferentes (`2001:db8::1` e `2001:0db8:0:0::1`) vira chaves diferentes.
**Fix:** Normalizar e agregar antes do hash:
```java
static String rateLimitSubject(String ip) {
    try {
        InetAddress address = InetAddress.ofLiteral(ip);       // Java 22+; no 21: parse manual
        if (address instanceof Inet6Address v6) {
            byte[] b = v6.getAddress();
            Arrays.fill(b, 8, 16, (byte) 0);                   // /64
            return InetAddress.getByAddress(b).getHostAddress() + "/64";
        }
        return address.getHostAddress();
    } catch (Exception e) {
        return ip;
    }
}
```
No Java 21, sem `InetAddress.ofLiteral`, validar o literal antes de usar `getByName`, para não haver resolução DNS. Documentar no README que o limite IPv6 é por /64.

### WR-06: `start()` logo depois de `stop(callback)` não espera o loop anterior: dois loops simultâneos e fábrica fechada sob o loop novo

**File:** `app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java:288-295,306-346,638-647`
**Issue:** `stop(Runnable)` só baixa `running` e volta na hora. O loop sai depois do lote corrente, que pode levar vários segundos com Mongo ou Valkey lentos. Se `start()` rodar antes disso (restart do contexto depois de um timeout de fase, ou pausa e retomada de contexto de teste do Spring Framework 7, citada no próprio Javadoc), acontece o seguinte:
1. `start()` grava `loopDone = false; running = true` e sobe um worker novo;
2. o worker antigo vê `running == true` de novo e **continua rodando**, então ficam dois loops com o mesmo `consumerName` competindo;
3. quando um deles sai, o `finally` grava `loopDone = true` mesmo com o outro vivo. No próximo `stop`, `finishStopIfRequested` para a fábrica de conexões com um loop ainda rodando, e esse loop entra em erro e backoff;
4. `stop()` só faz `join` em `worker`, o último criado.

A classe diz que suporta stop seguido de start, mas não protege esse caso.
**Fix:** Dar a cada execução um token próprio e esperar o worker anterior no `start()`:
```java
@Override
public void start() {
    Thread previous = worker;
    if (previous != null && previous.isAlive()) {
        try {
            if (!previous.join(settings.block().plus(STOP_GRACE))) {
                throw new IllegalStateException("loop anterior do consumer ainda ativo");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }
    ...
}
```
Outra opção: o loop recebe uma geração (`long generation`) e só continua enquanto `running && generation == currentGeneration`.

### WR-07: Fábrica dedicada do consumer descarta TLS (e o resto da configuração do cliente): o pipeline quebra em silêncio no ElastiCache com criptografia em trânsito

**File:** `app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java:252-274`, `app/src/main/java/dev/linkpulse/config/ClickConfig.java:123-125`
**Issue:** `dedicatedConnectionFactory` monta um `LettuceClientConfiguration` só com `commandTimeout`. SSL (`spring.data.redis.ssl.*` ou URL `rediss://`), client name e connect timeout ficam de fora. O Javadoc diz "sem TLS, como no compose e no kind", mas o alvo de produção do projeto é ElastiCache Valkey, que normalmente roda com `transit_encryption_enabled`. Nesse cenário, o `StringRedisTemplate` do Boot conecta e o redirect publica cliques, mas o consumer falha em todo ciclo. O único sinal é um WARN e `linkpulse.clicks.consumer.errors`. O stream é aparado pelo `MAXLEN ~` e os cliques somem sem documento.
**Fix:** Propagar o SSL da configuração do Boot e falhar na subida quando ela pedir algo que a fábrica dedicada não suporta:
```java
LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
        LettuceClientConfiguration.builder().commandTimeout(block.plusSeconds(3));
if (redisProperties.getSsl().isEnabled() || details.getSslBundle() != null) {
    builder.useSsl();   // e aplicar o SslBundle/SslOptions quando houver
}
```
Se não for feito nesta fase, registrar como pré-requisito explícito da fase de Terraform/ECS e adicionar uma checagem que derrube a subida com SSL habilitado.

## Info

### IN-01: Exceção em `send()` fora do `try` não conta em nenhuma métrica

**File:** `app/src/main/java/dev/linkpulse/click/ClickPublisher.java:167-172`
**Issue:** `ReferrerNormalizer.toHost`, `ipHasher.hash` (`IllegalStateException`) e `Map.of` (NPE) rodam fora do `try`. Uma exceção ali mata a thread do pool (o `ThreadPoolExecutor` cria outra) e imprime a stack no stderr. O clique não entra em `published`, `failed` nem `rejected`, e a soma "descartados = rejected + failed" do painel da Phase 3 fica errada.
**Fix:** Envolver a montagem dos campos em `try/catch (RuntimeException)` contando `failed`, sem log do conteúdo.

### IN-02: O loop do consumer só captura `RuntimeException`; um `Error` deixa um consumer zumbi com `isRunning() == true`

**File:** `app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java:306-346`
**Issue:** Um `Error` (por exemplo `OutOfMemoryError` ou `StackOverflowError` vindo do driver) sai do `while`. O `finally` grava `loopDone = true`, mas `running` continua `true`: o bean diz que está rodando e ninguém consome o stream, sem nenhum log.
**Fix:** No `finally`, se `running` ainda for `true`, registrar um ERROR com só a classe da exceção e baixar `running`, ou expor um health indicator baseado em `worker.isAlive()`.

### IN-03: Consumers mortos nunca saem do grupo

**File:** `app/src/main/java/dev/linkpulse/config/ClickConfig.java:115-118`
**Issue:** O nome é o `HOSTNAME` (novo a cada pod do Deployment) ou `lp-<uuid>` (novo a cada restart no dev do Windows, que não define `HOSTNAME`). O reclaim assume os pendentes, mas a entrada do consumer continua em `XINFO CONSUMERS` para sempre (não há `XGROUP DELCONSUMER`), e a lista cresce a cada deploy.
**Fix:** No reclaim, remover consumers com `pending == 0` e `idle > N × minIdle` (`redis.opsForStream().deleteConsumer(...)`).

### IN-04: O mesmo HMAC identifica o IP no rate limit e nos cliques (sem separação de domínio)

**File:** `app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java:122-123`, `app/src/main/java/dev/linkpulse/common/IpHasher.java:65-70`
**Issue:** A chave `lp:rl:<hash>` usa exatamente o mesmo valor que o `iph` do stream e o `ipHash` do Mongo. Quem lê o Valkey consegue cruzar "quem criou links" com "quem clicou em quais links" no mesmo dia, o que vai contra o espírito do D-10.
**Fix:** Separar os domínios na mensagem do HMAC, por exemplo `hash("rl|" + day + "|" + ip)` para o rate limit e `"click|..."` para os cliques, com um método `hash(String purpose, String ip, Instant at)`.

### IN-05: O contador do rate limit zera na virada do dia UTC quando a janela não divide 24 h

**File:** `app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java:121-123`
**Issue:** A chave inclui o HMAC, que muda à meia-noite UTC. Com uma janela configurada como `7m` ou `90m`, a janela que atravessa a meia-noite ganha um contador novo, e o cliente consegue até `2 × limit` nela. Além disso, `clock.millis()` e `clock.instant()` são lidos separadamente.
**Fix:** Ler o relógio uma vez (`Instant now = clock.instant()`) e calcular o dia do hash a partir do início da janela (`Instant.ofEpochMilli(windowIndex * windowMillis)`), ou documentar que a janela precisa dividir 24 h.

### IN-06: Durações abaixo de 1 ms (ou de 1 s) passam na validação e quebram em runtime

**File:** `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java:176-184,225-230,326-331`
**Issue:** As validações só exigem duração positiva:
- `linkpulse.ratelimit.window=500us` dá `windowMillis = 0`, e `Math.floorDiv(now, 0)` lança `ArithmeticException` fora do `try` em todo `POST /links` (500);
- `linkpulse.cache.negative-ttl=500us` faz o `SET ... PX 0` falhar e fechar o gate a cada 404;
- `linkpulse.clicks.retention=500ms` vira `expireAfterSeconds: 0`.
**Fix:** Exigir mínimos coerentes nos construtores compactos: pelo menos 1 ms para TTLs, cooldown e janela, e pelo menos 1 s para `retention`.

### IN-07: `ReferrerNormalizer` usa o parser estrito do `java.net.URI` e manda referers legítimos para `(direct)`

**File:** `app/src/main/java/dev/linkpulse/click/ReferrerNormalizer.java:47-54`
**Issue:** Em alguns casos o parser descarta referers válidos:
- `URI.getHost()` devolve `null` para hosts com `_` (autoridade "registry-based"), como `my_app.example.com`;
- `new URI(...)` lança exceção com caracteres que navegadores às vezes não escapam no path ou na query (`|`, `{`, `^`, espaço).

Nos dois casos o clique vira `(direct)` e o top de referrers fica distorcido.
**Fix:** Se `getHost()` vier nulo, extrair a autoridade com uma regex simples (`^[a-zA-Z][a-zA-Z0-9+.-]*://(?:[^@/?#]*@)?([^:/?#]+)`), aplicando as mesmas regras de minúsculas, pontos finais e truncamento.

### IN-08: Bulk só com duplicatas também ignora um write concern error

**File:** `app/src/main/java/dev/linkpulse/click/MongoClickEventWriter.java:47-54`
**Issue:** `BulkOperationException.getErrors()` só traz os write errors. Se o bulk tiver duplicatas e também um write concern error, o lote é tratado como sucesso e confirmado com `XACK`. Os documentos novos podem ser revertidos num failover, e a perda não aparece em nenhuma métrica.
**Fix:** Inspecionar `((MongoBulkWriteException) e.getCause()).getWriteConcernError()` e relançar quando não for nulo.

### IN-09: `/stats` com o Postgres fora responde 500 genérico, e com o Mongo fora responde 503

**File:** `app/src/main/java/dev/linkpulse/stats/StatsService.java:74-76`
**Issue:** `links.existsByCode` está fora do `try`. Uma `DataAccessException` do JPA cai no `handleUnexpected` (500 e stack em ERROR), enquanto a mesma situação no Mongo vira o 503 `stats-unavailable`. O contrato fica inconsistente.
**Fix:** Tratar `DataAccessException` do lookup também como `StatsProblems.unavailable()`, ou documentar o 500.

### IN-10: O `readTimeout` global de 5 s do Mongo também vale para agregações e criação de índice

**File:** `app/src/main/java/dev/linkpulse/config/MongoConfig.java:30,42-44`
**Issue:** O socket timeout é do cliente inteiro. Um `/stats` de 366 dias num link muito clicado, ou o `createIndex` do `ClickIndexes.ensure()` numa coleção grande, pode passar de 5 s. O resultado é 503 permanente para uma consulta legítima, e o `ensure()` entra em retry infinito, o que impede o consumer de ler o stream.
**Fix:** Manter timeouts curtos de seleção e conexão, mas usar `maxTimeMS` por operação (`AggregationOptions.builder().maxTime(...)`) ou o CSOT (`timeoutMS`) do driver, com um valor maior para a criação de índices.

### IN-11: O `_id` do clique não tem namespace do stream

**File:** `app/src/main/java/dev/linkpulse/click/ClickDocument.java:36`, `app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java:551`
**Issue:** O `_id` é só `<ms>-<seq>`. Duas fontes gravando na mesma coleção (troca de `stream-key`, dois ambientes apontando para o mesmo Mongo, um Valkey novo com o relógio atrás) geram o mesmo ID no mesmo milissegundo. O segundo clique é descartado em silêncio como "duplicata".
**Fix:** Usar `_id = streamKey + ":" + entryId`, ou documentar a premissa de um único stream por coleção.

### IN-12: `RateLimitIT` pode atravessar a virada da janela fixa de 1 h (teste intermitente)

**File:** `app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java:41-42,51-54`
**Issue:** O comentário diz que "o teste não atravessa a virada da janela". A janela, porém, é alinhada ao epoch (`floor(now / 1h)`): um teste que começa às hh:59:59 faz os três `POST` na janela N e o quarto na N+1, e recebe 201 em vez de 429. O mesmo vale para a virada do dia UTC, que troca o HMAC. A chance é baixa, mas não é zero. O mesmo padrão aparece no `TrustedProxyRateLimitIT`.
**Fix:** Injetar um `Clock` fixo (`@TestConfiguration` com `@Primary Clock`) nesses contextos, ou usar uma janela grande (`window=3650d`).

### IN-13: O README descreve o documento de clique de forma incompleta

**File:** `README.md:60`
**Issue:** O README diz que o documento guarda "o código, o instante, o host do `Referer` e o hash do IP". Ele também guarda `browser`, `os` e `isBot` (`ClickDocument.java:23-24`), que alimentam os tops do `/stats` documentados mais abaixo.
**Fix:** Atualizar a frase: "... o host do `Referer`, a família do navegador e do SO, a marca de bot e o hash do IP".

---

_Reviewed: 2026-10-11T21:05:13Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
