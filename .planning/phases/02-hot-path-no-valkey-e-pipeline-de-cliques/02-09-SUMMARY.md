---
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
plan: 09
subsystem: rate-limit
tags: [valkey, lua, rate-limit, tomcat, remoteipvalve, x-forwarded-for, problem-details, micrometer, testcontainers]

requires:
  - phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
    provides: "IpHasher (HMAC do IP, 02-02), ValkeyAvailability compartilhado (02-03), GlobalExceptionHandler/Problem Details e OpenApiIT (01/02-07), MetricsCardinalityIT (02-08)"
provides:
  - "Rate limit de janela fixa por IP no POST /links: script Lua atômico INCR + PEXPIRE (reaplica PEXPIRE sem TTL), chave lp:rl:{hmac do ip}:{janela}"
  - "429 /problems/rate-limited em application/problem+json com header Retry-After e propriedade retryAfterSeconds"
  - "Fail-open com o gate do ValkeyAvailability e métrica linkpulse.ratelimit.requests{result=allowed|rejected|error}"
  - "IP real só pelo RemoteIpValve (server.forward-headers-strategy=native) com server.tomcat.remoteip.internal-proxies explícito e vazio por padrão"
  - "linkpulse.ratelimit.enabled/limit/window (LINKPULSE_RATELIMIT_*) e SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES como contrato de configuração das Phases 3 a 5"
  - "RateLimitIT (7 cenários), TrustedProxyRateLimitIT (3), RateLimiterTest (8 casos), 2 testes novos no LinkPulsePropertiesTest, OpenApiIT com 429"
affects: [phase-03-observabilidade, phase-04-kind-traefik, phase-05-ecs, compose, helm, readme]

actuals:
  tokens: 12851
  tasks: 3
  commits: 6
plan_head_before: cd6501dea5e5d436ee6efa1af13ea62566de00dc

tech-stack:
  added: []
  patterns:
    - "Rate limit como HandlerInterceptor registrado no path exato (/links) que confere o método; exceção ErrorResponseException no preHandle vira Problem Details pelo GlobalExceptionHandler, com headers propagados"
    - "Script Lua via RedisScript.of(texto, Long.class) em constante estática, com text block no estilo Google (aspas em linha própria) exigido pelo Checkstyle"
    - "ITs de proxy com Tomcat real (RANDOM_PORT + HttpClient HTTP/1.1 em 127.0.0.1), porque o MockMvc não passa pelo RemoteIpValve"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java
    - app/src/main/java/dev/linkpulse/ratelimit/RateLimitInterceptor.java
    - app/src/main/java/dev/linkpulse/ratelimit/RateLimitProblems.java
    - app/src/main/java/dev/linkpulse/config/WebMvcConfig.java
    - app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java
    - app/src/test/java/dev/linkpulse/ratelimit/TrustedProxyRateLimitIT.java
    - app/src/test/java/dev/linkpulse/ratelimit/RateLimiterTest.java
  modified:
    - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
    - app/src/main/java/dev/linkpulse/link/LinkController.java
    - app/src/main/resources/application.yml
    - app/src/test/java/dev/linkpulse/AbstractIT.java
    - app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java
    - app/src/test/java/dev/linkpulse/OpenApiIT.java
    - README.md

key-decisions:
  - "Assumption A2 confirmada: a ErrorResponseException lançada no preHandle chega ao GlobalExceptionHandler e sai como 429 application/problem+json com o header Retry-After (getHeaders() propagado)"
  - "SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES (com _ entre INTERNAL e PROXIES) faz binding em server.tomcat.remoteip.internal-proxies pelo nome legado do relaxed binding (provado por Binder no TrustedProxyRateLimitIT); a forma canônica SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES também vale. A Phase 4 pode usar a variável como documentada"
  - "internal-proxies=127.0.0.1/32 (CIDR) liga o RemoteIpValve e o cliente contado é o hop não confiável mais à direita do X-Forwarded-For; entradas forjadas à esquerda não mudam o contador"
  - "Fail-open consciente (D-08): com o gate fechado ou erro no script, a criação passa e conta result=error; sem log com IP ou chave (a exceção do Lettuce pode citar a chave)"
  - "windowIndex usa Math.floorDiv (igual a / para epoch positivo, correto também para valores negativos)"
  - "O T-08-06 da Phase 1 (loop via DNS) passa a ter o rate limit de criação como parte da mitigação parcial, registrado no README"

patterns-established:
  - "Todo IT de rate limit roda em contexto próprio RANDOM_PORT com limite baixo e janela de 1 h; os ITs MockMvc herdam linkpulse.ratelimit.limit=100000 do AbstractIT"
  - "Testes de proxy confiável sempre com controle positivo (outro cliente real passa), para provar que o valve está ativo e o teste discrimina"

requirements-completed: [RATE-01, RATE-02, OBS-01, QUAL-02]

coverage:
  - id: D1
    description: "Na mesma janela, o quarto POST /links do mesmo IP (limite 3) responde 429 /problems/rate-limited em application/problem+json, com Retry-After inteiro entre 1 e 3600 igual a retryAfterSeconds"
    requirement: RATE-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java#fourthCreationInTheWindowIsRateLimited"
        status: pass
    human_judgment: false
  - id: D2
    description: "GET /{code}, HEAD /{code} e GET /links/{code}/stats nunca respondem 429, mesmo depois do 429 do POST"
    requirement: RATE-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java#redirectHeadAndStatsAreNeverRateLimited"
        status: pass
    human_judgment: false
  - id: D3
    description: "10 POSTs simultâneos com limite 3 resultam em exatamente 3 respostas 201 e 7 respostas 429"
    requirement: RATE-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java#concurrentRequestsNeverExceedTheLimit"
        status: pass
    human_judgment: false
  - id: D4
    description: "Toda chave lp:rl:* tem 0 < PTTL <= janela e nenhuma contém o IP em claro"
    requirement: RATE-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java#rateLimitKeysAlwaysExpireAndHideTheIp"
        status: pass
    human_judgment: false
  - id: D5
    description: "Janela floor(now/window) e Retry-After como teto dos segundos restantes, mínimo 1 (oito casos)"
    requirement: RATE-01
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/ratelimit/RateLimiterTest.java"
        status: pass
    human_judgment: false
  - id: D6
    description: "Com o Valkey pausado, o POST /links responde 201 em menos de 1000 ms (medido: 334 ms) e linkpulse.ratelimit.requests{result=error} aumenta"
    requirement: RATE-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java#valkeyOutageFailsOpen"
        status: pass
    human_judgment: false
  - id: D7
    description: "linkpulse.ratelimit.requests conta allowed (+3) e rejected (+1) só com a tag result; o /actuator/prometheus segue sem tags de alta cardinalidade"
    requirement: OBS-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java#decisionsAreCounted"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/MetricsCardinalityIT.java#prometheusExposesLowCardinalityMetrics"
        status: pass
    human_judgment: false
  - id: D8
    description: "Sem proxy confiável (internal-proxies vazio, default do projeto), X-Forwarded-For diferentes do mesmo socket não burlam o limite"
    requirement: RATE-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java#spoofedForwardedForFromUntrustedPeerIsIgnored"
        status: pass
    human_judgment: false
  - id: D9
    description: "Com internal-proxies=127.0.0.1/32, o cliente real vem do X-Forwarded-For do proxy confiável; entradas forjadas à esquerda são ignoradas; o env SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES faz binding"
    requirement: RATE-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/TrustedProxyRateLimitIT.java#trustedProxyForwardsTheRealClientIp"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/TrustedProxyRateLimitIT.java#spoofedLeftmostEntriesAreIgnoredBehindTrustedProxy"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/ratelimit/TrustedProxyRateLimitIT.java#internalProxiesBindsFromEnvironmentVariable"
        status: pass
    human_judgment: false
  - id: D10
    description: "Defaults linkpulse.ratelimit (true, 20, 1m) e janela zero derruba a subida; OpenAPI do POST /links documenta 429 problem+json com Retry-After"
    requirement: QUAL-02
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java#rateLimitDefaultsBindWithoutConfiguration"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java#zeroWindowFailsStartup"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/OpenApiIT.java#createLinkDocumentsSuccessAndErrors"
        status: pass
    human_judgment: false
  - id: D11
    description: "README atualizado (regra do rate limit, erro /problems/rate-limited, quatro linhas de configuração, riscos conhecidos e T-08-06) e legível para o avaliador"
    verification:
      - kind: other
        ref: "gate de texto RL-DOCS-OK (grep em README.md e application.yml)"
        status: pass
    human_judgment: true
    rationale: "O gate de texto só prova a presença das linhas; clareza e precisão da redação para o avaliador precisam de leitura humana"

duration: 21min
completed: 2026-10-11
status: complete
---

# Phase 2 Plan 09: Rate limit por IP no POST /links Summary

**Rate limit de janela fixa no Valkey (script Lua INCR + PEXPIRE atômico, chave com HMAC do IP) só no POST /links, com 429 /problems/rate-limited + Retry-After, fail-open medido e IP real só via RemoteIpValve com internal-proxies vazio por padrão**

## Performance

- **Duration:** 21 min
- **Started:** 2026-10-11T20:33:29Z
- **Completed:** 2026-10-11T20:54:18Z
- **Tasks:** 3 (tracer + 2 TDD)
- **Files modified:** 14 (7 criados, 7 modificados)

## Accomplishments

- `RateLimiter`: janela fixa `floor(epochMillis / window)` pelo `Clock` da aplicação, script Lua que faz `INCR` e reaplica `PEXPIRE` quando a chave é nova ou está sem TTL, chave `lp:rl:{hmac}:{janela}` (o IP nunca chega em claro ao Valkey). Com 10 POSTs simultâneos e limite 3, o resultado é exatamente 3 respostas 201 e 7 respostas 429.
- O `RateLimitInterceptor` age só no `POST`, fica registrado no path exato `/links` e lê o IP só de `getRemoteAddr()`. O redirect, o `HEAD` e o `/stats` nunca recebem 429.
- O 429 sai como `application/problem+json` (`type` `/problems/rate-limited`, `title` "Muitas requisições"), com o header `Retry-After` e `retryAfterSeconds` iguais. A assumption A2 se confirmou.
- Fail-open: com o Valkey pausado o POST responde 201 em 334 ms (limite do teste: 1000 ms). Toda decisão conta em `linkpulse.ratelimit.requests{result=allowed|rejected|error}`, só com a tag `result`.
- `server.forward-headers-strategy: native` + `server.tomcat.remoteip.internal-proxies: ""`: sem proxy confiável, o `X-Forwarded-For` é ignorado. Com `127.0.0.1/32`, o cliente real vem do hop não confiável mais à direita e entradas forjadas à esquerda são ignoradas.
- OpenAPI documenta o 429 com `Retry-After`; o README tem a regra, o erro, quatro variáveis de configuração e os riscos conhecidos atualizados (fail-open, sem limite no redirect, T-08-06).

## Task Commits

1. **Task 1: O quarto POST /links na janela recebe 429 com Retry-After, por HTTP real (tracer)**
   - `b34e340` (test, RED)
   - `ba0b340` (feat, GREEN)
2. **Task 2: Janela exata, concorrência atômica, TTL garantido, fail-open e métricas**
   - `2efeeb9` (test, RED)
   - `3421867` (feat, GREEN)
3. **Task 3: IP real só por proxy confiável (RemoteIpValve), 429 documentado e README**
   - `f6ecf98` (test, RED)
   - `524c33e` (feat, GREEN)

**Plan metadata:** commit `docs(02-09)` do SUMMARY e o de STATE/ROADMAP/REQUIREMENTS, feitos logo depois deste arquivo

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java`: janela fixa, script Lua, fail-open e contadores
- `app/src/main/java/dev/linkpulse/ratelimit/RateLimitInterceptor.java`: `preHandle` só no `POST`, IP de `getRemoteAddr()`
- `app/src/main/java/dev/linkpulse/ratelimit/RateLimitProblems.java`: 429 `/problems/rate-limited` com `Retry-After`
- `app/src/main/java/dev/linkpulse/config/WebMvcConfig.java`: `addPathPatterns("/links")`
- `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java`: record `RateLimit(enabled=true, limit=20, window=1m)` com janela positiva; `toString()` mascarado inclui o bloco
- `app/src/main/java/dev/linkpulse/link/LinkController.java`: `@ApiResponse` 429 e descrição do limite
- `app/src/main/resources/application.yml`: `forward-headers-strategy: native`, `internal-proxies: ""` e envs `LINKPULSE_RATELIMIT_*` no comentário
- `app/src/test/java/dev/linkpulse/AbstractIT.java`: `linkpulse.ratelimit.limit=100000`
- `app/src/test/java/dev/linkpulse/ratelimit/RateLimitIT.java`: 7 cenários com Tomcat real
- `app/src/test/java/dev/linkpulse/ratelimit/TrustedProxyRateLimitIT.java`: proxy confiável e binding do env
- `app/src/test/java/dev/linkpulse/ratelimit/RateLimiterTest.java`: aritmética da janela
- `app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java`: defaults e janela zero
- `app/src/test/java/dev/linkpulse/OpenApiIT.java`: 429 em `application/problem+json`
- `README.md`: regras, erros, configuração e riscos conhecidos

## Decisions Made

- **A2 confirmada:** exceção do `preHandle` → `GlobalExceptionHandler` (handler herdado de `ErrorResponseException`) → 429 `problem+json` com `Retry-After`.
- **Binding do `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` funciona como documentado** (nome legado do relaxed binding, `-` vira `_`), provado com `Binder` sobre um `SystemEnvironmentPropertySource`. O efeito do valor no valve foi provado pela propriedade `server.tomcat.remoteip.internal-proxies=127.0.0.1/32` no Tomcat real. Para a Phase 4: usar o CIDR dos pods do Traefik nessa variável.
- **T-08-06 (loop via DNS, Phase 1):** o rate limit de criação passa a ser parte da mitigação parcial, e o README diz isso no bullet do loop e no de "API sem autenticação".
- Fail-open sem log: a exceção do Lettuce pode citar a chave, e a queda já aparece em `linkpulse.valkey.unavailable` e `result=error`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Text block do script Lua no estilo exigido pelo Checkstyle**
- **Found during:** Task 1 (GREEN)
- **Issue:** `TextBlockGoogleStyleFormatting` recusou `RedisScript.of("""` com as aspas na mesma linha
- **Fix:** aspas de abertura e fechamento em linha própria, alinhadas
- **Files modified:** `RateLimiter.java`
- **Verification:** 0 violações do Checkstyle no `verify`
- **Committed in:** `ba0b340`

**2. [Rule 2 - Missing Critical] `toString()` mascarado do `LinkPulseProperties` inclui o `ratelimit`**
- **Found during:** Task 1 (GREEN)
- **Issue:** o `toString()` manual (T-02-44) listaria só os componentes antigos e esconderia o novo bloco de relatórios de falha de binding
- **Fix:** `", ratelimit=" + ratelimit` no `toString()`
- **Files modified:** `LinkPulseProperties.java`
- **Verification:** `LinkPulsePropertiesTest#toStringDoesNotExposeIpHashKey` segue verde
- **Committed in:** `ba0b340`

**3. [Rule 2 - Test strength] Controle positivo no `spoofedLeftmostEntriesAreIgnoredBehindTrustedProxy` e teste de binding do env**
- **Found during:** Task 3 (RED)
- **Issue:** como escrito no plano, o teste passaria mesmo sem o valve (o quarto POST do mesmo socket já é 429). O plano também pede para registrar se o binding de `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` funciona, mas nenhum teste o provava
- **Fix:** depois do 429, um POST com outro cliente à direita da cadeia (`198.51.100.1, 203.0.113.21`) precisa ser 201, o que prova o valve ativo e a escolha do hop mais à direita. Novo `internalProxiesBindsFromEnvironmentVariable`
- **Files modified:** `TrustedProxyRateLimitIT.java`
- **Verification:** os dois falharam no RED sem o valve e passam no GREEN
- **Committed in:** `f6ecf98`

**4. [Rule 3 - Blocking] `HttpClient` dos ITs fixado em HTTP/1.1**
- **Found during:** Task 2 (RED)
- **Issue:** o default `HTTP_2` tenta upgrade h2c em `http://`; com 10 requisições simultâneas, uma negociação de protocolo no meio do teste de concorrência só traria ruído
- **Fix:** `.version(HttpClient.Version.HTTP_1_1)` no `RateLimitIT` e no `TrustedProxyRateLimitIT`
- **Committed in:** `2efeeb9`, `f6ecf98`

---

**Total deviations:** 4 auto-fixed (2 Rule 2, 2 Rule 3)
**Impact on plan:** nenhuma mudança de escopo; todas reforçam correção ou a força dos testes.

## TDD Gate Compliance

- RED → GREEN em todas as tasks: `b34e340`→`ba0b340`, `2efeeb9`→`3421867`, `f6ecf98`→`524c33e`. Sem REFACTOR (nada a limpar).
- **Task 2 RED parcial, esperado:** `RateLimiterTest`, `concurrentRequestsNeverExceedTheLimit`, `rateLimitKeysAlwaysExpireAndHideTheIp` e os dois testes de propriedades já passaram no RED, porque a Task 1 entregou a aritmética da janela, o Lua atômico e o record `RateLimit` como o próprio plano pedia. O RED pretendido (o plano cita "sem fail-open, o POST com o Valkey pausado vira 500") falhou na asserção certa: `valkeyOutageFailsOpen` (500 em vez de 201, por timeout do Lettuce) e `decisionsAreCounted` (contador ausente).
- **Task 3 RED:** `spoofedForwardedForFromUntrustedPeerIsIgnored` e o teste de binding do env já passaram no RED, como esperado: o primeiro é guarda de regressão contra o default perigoso do Boot quando o valve for ligado, e o segundo testa o relaxed binding do Boot, não código novo. O RED pretendido falhou: `trustedProxyForwardsTheRealClientIp`, `spoofedLeftmostEntriesAreIgnoredBehindTrustedProxy` (sem o valve, o outro cliente toma 429) e `OpenApiIT#createLinkDocumentsSuccessAndErrors` (sem 429).

## Issues Encountered

- Um script auxiliar com o mesmo nome de outra sessão no scratchpad rodou por engano. Ele falhou na primeira leitura de arquivo, sem nenhuma escrita (`git status` limpo), e foi refeito com outro nome. Nenhum efeito no repositório.

## Verification

- `./mvnw -B -ntp -Dtest=NoUnit -Dsurefire.failIfNoSpecifiedTests=false -Dit.test='RateLimitIT,LinkApiIT,ConcurrencyIT' verify`: BUILD SUCCESS (Task 1)
- `./mvnw -B -ntp -Dtest='RateLimiterTest,LinkPulsePropertiesTest' -Dit.test=RateLimitIT verify`: BUILD SUCCESS (Task 2)
- `./mvnw -B -ntp -Dtest=NoUnit ... -Dit.test='RateLimitIT,TrustedProxyRateLimitIT,OpenApiIT' verify`: BUILD SUCCESS (Task 3)
- `./mvnw -B -ntp verify` completo: **BUILD SUCCESS**, 225 unitários + 103 ITs, 0 violações do Checkstyle, SpotBugs `BugInstance size is 0`
- Gate de texto: `RL-DOCS-OK`
- `grep -rn 'getHeader("X-Forwarded' app/src/main/java`: vazio

## User Setup Required

None. Nenhuma configuração externa é necessária. O default (`internal-proxies` vazio) é o seguro para o compose; a Phase 4 configura o CIDR do Traefik.

## Next Phase Readiness

- A Phase 2 tem os 9 planos concluídos e está pronta para `/gsd-verify-work 02`.
- **Phase 3 (compose/observabilidade):** `linkpulse_ratelimit_requests_total{result}` entra no dashboard; `result="error"` subindo junto com `linkpulse_valkey_unavailable_total` indica rate limit desligado por fail-open. No compose, manter `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` vazio (o tráfego do host chega pelo gateway da bridge, que o default do Boot confiaria).
- **Phase 4 (kind):** `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` = CIDR dos pods do Traefik (por exemplo `10.244.0.0/16`), em CIDR e não regex. A flagged assumption RATE-02 (o CIDR real do Traefik) continua aberta para essa fase.
- **Phase 5 (ECS):** `LINKPULSE_RATELIMIT_*` e o CIDR do ALB entram nas task definitions.

---
*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Completed: 2026-10-11*

## Self-Check: PASSED

- 7 arquivos criados presentes; commits b34e340, ba0b340, 2efeeb9, 3421867, f6ecf98, 524c33e e b0013f3 existem
- RateLimitIT 274 linhas (min 120), TrustedProxyRateLimitIT 135 linhas (min 50)
