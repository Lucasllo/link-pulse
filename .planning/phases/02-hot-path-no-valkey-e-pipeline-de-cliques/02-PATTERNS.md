# Phase 2: Hot path no Valkey e pipeline de cliques - Pattern Map

**Mapped:** 2026-10-10
**Files analyzed:** 38 (new + modified)
**Analogs found:** 31 / 38 (the rest are Valkey, Mongo, or stream infrastructure that has no precedent in Phase 1, so use the RESEARCH.md patterns)

All analogs are git-tracked sources under `app/` (verified with `git ls-files`). Base path: `app/src/main/java/dev/linkpulse/`, shortened to `main/`. Tests live in `app/src/test/java/dev/linkpulse/`, shortened to `test/`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `app/pom.xml` (MOD) | config | build | itself (lines 29-81, dependency block) | exact |
| `app/src/main/resources/application.yml` (MOD) | config | — | itself | exact |
| `compose.dev.yaml` (MOD: + valkey, mongo) | config | — | itself (postgres service) | exact |
| `main/config/LinkPulseProperties.java` (MOD: + `Cache`, `Clicks`, `RateLimit`, `Stats` sub-records) | config | — | itself (nested records `Code`, `Alias`) | exact |
| `main/config/RedisConfig.java` (NEW: `LettuceClientOptionsBuilderCustomizer`) | config | — | `main/config/ApplicationConfig.java` | role-match |
| `main/config/MongoConfig.java` (NEW: `MongoClientSettingsBuilderCustomizer` timeouts) | config | — | `main/config/ApplicationConfig.java` | role-match |
| `main/config/WebMvcConfig.java` (or `ratelimit/`) (NEW: registers the interceptor) | config | — | `main/config/ApplicationConfig.java` | role-match |
| `main/cache/ValkeyAvailability.java` (NEW) | utility (gate) | event-driven | none (Clock injection from `LinkService`) | partial |
| `main/cache/LinkCache.java` (NEW) | service/adapter | CRUD (cache) | `main/link/LinkService.java` (constructor DI, Clock, Javadoc) | partial |
| `main/link/LinkLookup.java` (NEW record: L/N/G result) | model | transform | `main/link/LinkResponse.java` | role-match |
| `main/link/RedirectService.java` (NEW, no `@Transactional`) | service | request-response | `main/link/LinkService.java` | exact |
| `main/link/LinkService.java` (MOD: `find` + `afterCommit` evict in `create`) | service | CRUD | itself (lines 72-97, 132-139) | exact |
| `main/link/RedirectController.java` (MOD: uses RedirectService, publishes only on GET) | controller | request-response | itself | exact |
| `main/link/LinkController.java` (MOD: OpenAPI 429 doc only) | controller | request-response | itself (lines 60-67) | exact |
| `main/ratelimit/RateLimiter.java` (NEW, Lua) | service/adapter | request-response | `LinkService` (DI) + RESEARCH Pattern 5 | partial |
| `main/ratelimit/RateLimitInterceptor.java` (NEW) | middleware | request-response | none (throws Problem like `LinkProblems`) | partial |
| `main/ratelimit/RateLimitProblems.java` (or a method in a common factory) (NEW) | utility | — | `main/link/LinkProblems.java` | exact |
| `main/click/ClickPublisher.java` (NEW, SmartLifecycle + bounded executor) | service | event-driven (pub) | none | no analog |
| `main/click/ClickRequest.java` (NEW record) | model | — | `main/link/LinkResponse.java` | role-match |
| `main/click/IpHasher.java` (NEW, HMAC) | utility | transform | `main/link/CodeGenerator.java` / `AliasPolicy` (pure class built by `@Bean`) | role-match |
| `main/click/ReferrerNormalizer.java` (NEW) | utility | transform | `main/link/TargetHostPolicy.java` (URI host handling) | role-match |
| `main/click/UserAgentClassifier.java` (NEW) | utility | transform | `main/link/AliasPolicy.java` | role-match |
| `main/click/ClickStreamConsumer.java` (NEW, SmartLifecycle loop) | worker | streaming/batch | none | no analog |
| `main/click/ClickEventWriter.java` + `MongoClickEventWriter.java` (NEW) | repository | batch | none (Mongo is new) | no analog |
| `main/click/ClickDocument.java` (NEW `@Document` record) | model | — | `main/link/LinkResponse.java` (record + Javadoc `@param`) | partial |
| `main/click/ClickIndexes.java` (NEW) | migration-like | batch | `db/changelog/changes/001-create-links.yaml` (conceptual only) | no analog |
| `main/click/ClickMetrics.java` (NEW) | utility | — | none | no analog |
| `main/stats/StatsController.java` (NEW) | controller | request-response | `main/link/LinkController.java` + `RedirectController.java` | exact |
| `main/stats/StatsService.java` (NEW, aggregation) | service | CRUD read | `main/link/LinkService.java` | role-match |
| `main/stats/StatsResponse.java` (+ nested records) (NEW) | model | — | `main/link/LinkResponse.java` | exact |
| `main/stats/StatsProblems.java` (NEW: invalid-period 400) | utility | — | `main/link/LinkProblems.java` | exact |
| `main/common/GlobalExceptionHandler.java` (MOD: `MethodArgumentTypeMismatch` → 400 invalid-period, if needed) | middleware | request-response | itself (lines 66-83) | exact |
| `test/TestcontainersConfiguration.java` (MOD: + Mongo, Valkey) | test config | — | itself | exact |
| `test/AbstractIT.java` (MOD: high rate-limit property) | test base | — | itself | exact |
| `test/cache/*IT`, `test/ratelimit/*IT`, `test/click/*IT`, `test/stats/StatsIT` (NEW) | test (IT) | — | `test/link/RedirectIT.java` | exact |
| `test/click/UserAgentClassifierTest`, `ReferrerNormalizerTest`, `IpHasherTest`, `test/cache/LinkCacheCodecTest` (NEW) | test (unit) | — | `test/link/AliasPolicyTest.java` | exact |
| `test/config/LinkPulsePropertiesTest.java` (MOD: new sub-records) | test (unit) | — | itself (`ApplicationContextRunner`, line 31) | exact |
| `test/.../MetricsCardinalityIT` (NEW) | test (IT) | — | `test/link/RedirectIT.java` | role-match |

## Pattern Assignments

### `main/link/RedirectService.java` (service, request-response). Also used for `StatsService`, `LinkCache`, `RateLimiter`, and `ClickPublisher`

**Analog:** `main/link/LinkService.java`

**Class header + constructor DI + Clock** (lines 13-51): `@Service`, `private final` fields, an explicit constructor with pt-BR Javadoc `@param` for every argument, and no Lombok.
```java
/**
 * Regras de negócio dos links.
 *
 * <p>Não registra em log a URL de destino nem o corpo das criações: ...
 */
@Service
public class LinkService {
    private final LinkRepository repository;
    ...
    private final Clock clock;

    /**
     * Cria o service.
     *
     * @param repository repositório dos links
     * ...
     * @param clock relógio da aplicação (base da expiração e do {@code createdAt})
     */
    public LinkService(LinkRepository repository, CodeGenerator codeGenerator,
            AliasPolicy aliasPolicy, TargetHostPolicy targetHostPolicy, Clock clock) {
```

**Resolve logic to split** (lines 132-139). `RedirectService` must NOT carry `@Transactional`. On a miss it calls a new `LinkService.find(code)` (`@Transactional(readOnly = true)`, returns the `Link` or empty). Then `RedirectService` encodes L/N/G into the cache and throws the same problems:
```java
@Transactional(readOnly = true)
public String resolve(String code) {
    Link link = repository.findByCode(code).orElseThrow(() -> LinkProblems.notFound(code));
    if (link.isExpiredAt(clock.instant())) {
        throw LinkProblems.expired(code, link.getExpiresAt());
    }
    return link.getTargetUrl();
}
```
Keep `resolve` or replace it. In either case, `RedirectController` switches to `RedirectService`.

### `main/link/LinkService.java` (MOD: afterCommit eviction)

Insert the eviction into `create` (lines 72-97) right after `saveAndFlush` succeeds, before the `return`. Register `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { afterCommit() { linkCache.evict(code); } })` and always evict, including generated codes (RESEARCH Pitfall P3). Add `LinkCache` as a constructor parameter with a matching `@param`. Keep the existing pattern that rethrows `DataIntegrityViolationException` (lines 90-96).

### `main/link/RedirectController.java` (MOD)

**Analog:** itself, lines 45-67. Keep the regex path, the OpenAPI annotations, and `CacheControl.noStore().cachePrivate()`:
```java
@GetMapping("/{code:[A-Za-z0-9_-]+}")
...
public ResponseEntity<Void> redirect(@PathVariable String code) {
    String target = linkService.resolve(code);
    return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(target))
            .cacheControl(CacheControl.noStore().cachePrivate())
            .build();
}
```
Add a `HttpServletRequest request` parameter. Publish only when `HttpMethod.GET.matches(request.getMethod())` (REDIR-06: `@GetMapping` also serves HEAD), using `request.getRemoteAddr()`, `getHeader("Referer")`, and `getHeader("User-Agent")`. Never read `X-Forwarded-For` (D-08).

### `main/stats/StatsController.java` (controller, request-response)

**Analog:** `main/link/LinkController.java` (lines 23-75) and `RedirectController.java` (OpenAPI style)
```java
@RestController
@RequestMapping("/links")
@Tag(name = "Links", description = "Criação de links curtos")
public class LinkController {
    ...
    @PostMapping
    @Operation(summary = "...", description = "...")
    @ApiResponse(responseCode = "400",
            description = "...",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
```
For stats: `@GetMapping("/{code}/stats")` (on its own controller, either with `@RequestMapping("/links")` or the full path) and `@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from/to`, plus `@RequestParam(defaultValue = "false") boolean includeBots`. Document the 200, 400 (`invalid-period`), and 404 (`link-not-found`) responses with the same `@ApiResponse` + `problem+json` block. Use a new tag, `@Tag(name = "Stats", ...)`. Document UTC grouping and the "curl counts as bot" rule in `@Operation(description=...)`.

### `main/stats/StatsResponse.java`, `main/link/LinkLookup.java`, `main/click/ClickRequest.java`, `main/click/ClickDocument.java` (records)

**Analog:** `main/link/LinkResponse.java` (lines 7-40): a record with `@Schema(description, example)` on each component, Javadoc `@param` per component, and a static factory `from(...)`.
```java
/**
 * Corpo da resposta 201 do {@code POST /links} (contrato D-09).
 *
 * @param code código curto
 * ...
 */
public record LinkResponse(
        @Schema(description = "Código curto", example = "cJinsNv")
        String code,
        ...
        Instant createdAt) {

    public static LinkResponse from(Link link, URI shortUrl) { ... }
}
```
`@Schema` is only needed on API-facing records (StatsResponse and its nested `DailyCount`/`TopItem`). `ClickDocument` follows RESEARCH "Documento do clique" (`@Document("click_events")`, `@Id String id`, `Instant ts`, `@Field("isBot") boolean bot`).

### `main/stats/StatsProblems.java`, rate-limit 429 problem (utility)

**Analog:** `main/link/LinkProblems.java` (lines 17-53): a final class with a private constructor and static factories that return `ErrorResponseException`. The type is a relative `/problems/<slug>`, the title is in pt-BR, and the detail never echoes an exception.
```java
public final class LinkProblems {
    private LinkProblems() {
    }

    public static ErrorResponseException notFound(String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Nenhum link com o código '" + code + "'.");
        problem.setType(URI.create("/problems/link-not-found"));
        problem.setTitle("Link não encontrado");
        problem.setProperty("code", code);
        return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
    }
```
- 429: `type=/problems/rate-limited`, `title="Muitas requisições"`, `HttpStatus.TOO_MANY_REQUESTS`. After construction, call `ex.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(seconds))` and add the property `retryAfterSeconds` if desired.
- 400: `type=/problems/invalid-period`, with properties `from`/`to`/`maxDays`.
- 404 on stats: reuse `LinkProblems.notFound(code)` directly.

### `main/common/GlobalExceptionHandler.java` (MOD)

**Analog:** itself, override pattern at lines 66-83. A malformed `from`/`to` raises `MethodArgumentTypeMismatchException` (a `TypeMismatchException`). Override `handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request)` in the same style: use a fixed detail that never echoes the parser message, set the type with `URI.create(...)` as a static constant, and return `handleExceptionInternal(ex, body, headers, status, request)`.
```java
@Override
protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status,
        WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "...fixo...");
    body.setType(MALFORMED_REQUEST);
    body.setTitle("Corpo da requisição inválido");
    return handleExceptionInternal(ex, body, headers, status, request);
}
```
An `ErrorResponseException` thrown from `preHandle` (429) is already handled by the inherited handlers (Javadoc lines 25-28), and `getHeaders()` (Retry-After) is propagated.

### `main/config/LinkPulseProperties.java` (MOD)

**Analog:** itself. Add components to the top record with `@DefaultValue` or `@NotNull @Valid` (lines 32-36). Sub-records follow `Code` and `Alias` (lines 81-100): Bean Validation on components and a compact constructor that normalizes nulls. Update the class Javadoc env list (lines 16-19) and `@param` entries.
```java
public record LinkPulseProperties(
        @NotNull URI baseUrl,
        @NotNull @Valid Code code,
        @DefaultValue Alias alias,
        List<String> selfHosts) {
...
    public record Code(@NotBlank String alphabet, @Positive long multiplier) {
    }
```
New components: `@NotNull @Valid Cache cache`, `Clicks clicks` (nested `Publisher`, `Consumer`), `RateLimit ratelimit`, and `Stats stats`. Use `Duration` for TTL, window, block, and retention values. Invalid values must fail startup with a fixed message that never echoes secrets (compact constructor pattern at lines 55-61), which matters especially for `ipHashKey`.

### `main/config/RedisConfig.java`, `MongoConfig.java`, `WebMvcConfig.java` (config)

**Analog:** `main/config/ApplicationConfig.java` (lines 14-61)
```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LinkPulseProperties.class)
public class ApplicationConfig {

    /**
     * Único ponto de obtenção do tempo; ...
     *
     * @return relógio UTC do sistema
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AliasPolicy aliasPolicy(LinkPulseProperties properties) {
        return new AliasPolicy(properties.alias().reserved());
    }
```
- Use package-private `@Bean` methods and Javadoc with `@param`/`@return`. Do NOT repeat `@EnableConfigurationProperties`.
- In `RedisConfig`, the `LettuceClientOptionsBuilderCustomizer` sets `REJECT_COMMANDS` (RESEARCH Pattern 2). Never declare a second `RedisConnectionFactory` bean (Pitfall P4). The consumer builds its own factory internally.
- `MongoConfig`: `MongoClientSettingsBuilderCustomizer` timeouts (Pitfall P7).
- `WebMvcConfig implements WebMvcConfigurer`: `registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/links")`.
- Pure utility classes (`IpHasher`, `UserAgentClassifier`, `ReferrerNormalizer`) are built via `@Bean` from properties, the same way as `aliasPolicy`/`targetHostPolicy`, so they stay unit-testable without Spring.

### `main/click/UserAgentClassifier.java`, `ReferrerNormalizer.java`, `IpHasher.java` (pure utilities)

**Analog:** `main/link/AliasPolicy.java` / `TargetHostPolicy.java`. These are plain classes (no `@Component`) with a constructor that takes config values, wired in `ApplicationConfig`-style config. The unit tests mirror `AliasPolicyTest`.

### `app/src/main/resources/application.yml` (MOD)

**Analog:** itself. Every key gets a pt-BR comment explaining the reason and the env override (lines 24-26, 28-29). Extend `management.endpoints.web.exposure.include` (line 63) to `health,info,prometheus`. Add the `spring.data.redis.*`, `spring.mongodb.*`, `server.forward-headers-strategy: native`, `server.tomcat.remoteip.internal-proxies: ""`, and `linkpulse.{cache,clicks,ratelimit,stats}` blocks from RESEARCH "Configuração (application.yml, adições)". Keep `spring.threads.virtual.enabled: true` (line 4-6).

### `app/pom.xml` (MOD)

**Analog:** its own dependency block. Main starters sit at lines 29-58 and test dependencies at 61-81. Add `spring-boot-starter-data-redis` and `spring-boot-starter-data-mongodb` next to `spring-boot-starter-data-jpa` (line 48), add `micrometer-registry-prometheus` with runtime scope next to `postgresql` (line 57), and add `org.testcontainers:testcontainers-mongodb` with test scope next to `testcontainers-postgresql` (line 79). Declare no versions (BOM).

### `compose.dev.yaml` (MOD)

**Analog:** the postgres service (lines 5-21), which uses a pinned image tag, a loopback-only port binding `127.0.0.1:PORT:PORT`, a named volume, a healthcheck, and a pt-BR header comment. Add `valkey` (`valkey/valkey:8.1`, command `--maxmemory-policy volatile-lru`, healthcheck `valkey-cli ping`) and `mongo` (`mongo:8.0`, healthcheck `mongosh --eval "db.adminCommand('ping')"`).

### `test/TestcontainersConfiguration.java` (MOD)

**Analog:** itself (lines 15-23). Containers are beans, never `static @Container` (the Javadoc at lines 10-14 explains why):
```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    }
}
```
Add `@Bean @ServiceConnection(name = "redis") GenericContainer<?> valkey()` and `@Bean @ServiceConnection MongoDBContainer mongo()` (`org.testcontainers.mongodb.MongoDBContainer`), as in RESEARCH "Testcontainers".

### `test/AbstractIT.java` (MOD)

**Analog:** itself (lines 15-22). Add `@SpringBootTest(properties = "linkpulse.ratelimit.limit=100000")` (Pitfall P8). Keep `@AutoConfigureMockMvc`, `@Import(TestcontainersConfiguration.class)`, and the protected `MockMvcTester mvc`. Rate-limit ITs and `X-Forwarded-For` ITs need their own context: `webEnvironment = RANDOM_PORT`, a low limit, and a real HTTP client, because MockMvc bypasses the `RemoteIpValve`.

### New ITs (cache, ratelimit, click, stats, metrics)

**Analog:** `test/link/RedirectIT.java`
- Lines 21-35: `extends AbstractIT`, `@Autowired LinkRepository`, and a `saveLink(code, target, expiresAt)` helper that uses `links.saveAndFlush(Link.create(links.nextId(), code, target, expiresAt, now))`. Reuse this helper to seed links without POST, which also avoids the rate limit.
- Lines 63-74: Problem Details assertions:
```java
assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
assertThat(result).bodyJson().extractingPath("$.type")
        .isEqualTo("/problems/link-not-found");
```
- Use unique codes per test, since the context is shared (AbstractIT Javadoc, lines 12-13).
- Use Awaitility (already in starter-test) to poll stats and Mongo.
- In reentrega ITs, instantiate `ClickStreamConsumer` directly with its own stream key and group, plus an injectable `ClickEventWriter` (RESEARCH summary).

### New unit tests

**Analog:** `test/link/AliasPolicyTest.java` (lines 15-31, 33-46). The class Javadoc reads "Testes unitários da {@link X} ... Rodam sem Docker." Use `@ParameterizedTest @ValueSource(strings = {...})` for UA and bot tables and referrer cases, `assertThatThrownBy(...).isInstanceOfSatisfying(ErrorResponseException.class, ...)` for problem factories, and instantiate the class under test with a constant config.

### `test/config/LinkPulsePropertiesTest.java` (MOD)

**Analog:** itself. It uses `ApplicationContextRunner().withUserConfiguration(ApplicationConfig.class)` (line 31) and asserts `IllegalArgumentException` on invalid values (lines 54, 66). Extend it with invalid `ratelimit.limit`, `stats.max-days`, and a blank `ip-hash-key` where it is required.

## Shared Patterns

### Problem Details
**Source:** `main/link/LinkProblems.java` + `main/common/GlobalExceptionHandler.java`
**Apply to:** the 429 rate limit, the 400 invalid period, and the 404 on stats. Throw `ErrorResponseException` from a `*Problems` factory. Never build the response manually, and never echo exception messages.

### Constructor DI + injected Clock
**Source:** `main/link/LinkService.java` lines 28-51; `Clock` bean in `main/config/ApplicationConfig.java` lines 23-26
**Apply to:** `ValkeyAvailability`, `LinkCache` (TTL math), `RateLimiter` (window index), `IpHasher` (UTC day), `ClickPublisher` (ts), `ClickStreamConsumer` (reclaim schedule), and `StatsService` (default period). Never call `Instant.now()` in main code.

### Logging / privacy
**Source:** `LinkService` Javadoc lines 16-17 and `GlobalExceptionHandler` lines 85-103
**Apply to:** all new classes. Never log URLs, IPs, UAs, or request bodies. Use an SLF4J `private static final Logger LOG`. Metric tags must never include code, URL, IP, or UA (D-10).

### Fail-open adapter
**Source:** RESEARCH Pattern 2 (no codebase analog)
**Apply to:** `LinkCache`, `RateLimiter`, `ClickPublisher`. Check `availability.allowRequest()`, then use `try { ... } catch (RuntimeException e) { availability.recordFailure(); counter.increment(); fallback }`.

### Style gates
Checkstyle (Google adapted, error severity) and SpotBugs Max/Medium break the build. See `app/config/checkstyle/checkstyle.xml` and `app/config/spotbugs/exclude.xml`. Every public type and method needs pt-BR Javadoc, as every analog above shows. Use 4-space indentation, 8-space continuation, and lines under 100 characters.

## No Analog Found

| File | Role | Data Flow | Reason / Use instead |
|---|---|---|---|
| `main/click/ClickPublisher.java` | service | event-driven | No async or SmartLifecycle code exists yet. Use RESEARCH Pattern 3 and Pitfall P14 (phases). |
| `main/click/ClickStreamConsumer.java` | worker | streaming/batch | No background loop exists. Use RESEARCH Pattern 4 and Pitfalls P4, P9, P10, P14. |
| `main/click/MongoClickEventWriter.java` | repository | batch | Mongo is new. Use the RESEARCH "Writer idempotente" pattern (bulk UNORDERED + 11000). |
| `main/click/ClickIndexes.java` | migration | batch | Use RESEARCH "Índices explícitos" + Pitfall P12 (collMod). Run from the consumer startup, not `@PostConstruct`. |
| `main/click/ClickMetrics.java` | utility | — | No Micrometer usage exists yet. Use the RESEARCH "Catálogo de métricas". |
| `main/cache/ValkeyAvailability.java` | utility | — | Use the RESEARCH Pattern 2 snippet. |
| `main/ratelimit/RateLimitInterceptor.java` | middleware | request-response | No interceptors exist. Use RESEARCH Pattern 5 and act only on POST. |

## Metadata

**Analog search scope:** `app/src/main/java/dev/linkpulse/**`, `app/src/test/java/dev/linkpulse/**`, `app/src/main/resources/`, `app/pom.xml`, `compose.dev.yaml`
**Files scanned:** 39 tracked files
**Pattern extraction date:** 2026-10-10
