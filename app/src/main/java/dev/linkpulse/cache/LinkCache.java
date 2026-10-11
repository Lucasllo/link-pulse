package dev.linkpulse.cache;

import dev.linkpulse.config.LinkPulseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Cache-aside explícito do redirect no Valkey (D-06).
 *
 * <p>A chave é {@code lp:link:{code}} e o valor descreve o próprio tipo, com prefixo obrigatório:
 * <ul>
 *   <li>{@code L|<expiresAt ISO-8601 ou ->|<url>}: link válido, com TTL
 *       {@code min(default-ttl, expiresAt - now)};</li>
 *   <li>{@code N}: código inexistente (404), com TTL {@code negative-ttl};</li>
 *   <li>{@code G|<expiredAt ISO-8601>}: link expirado (410), com TTL {@code gone-ttl}.</li>
 * </ul>
 *
 * <p>Os instantes vão em ISO-8601 ({@link Instant#toString()}), que preserva os microssegundos do
 * Postgres: o {@code expiredAt} de um 410 servido do cache é idêntico ao servido do banco. Um
 * valor fora do formato é tratado como miss e regravado, nunca como outro destino.
 *
 * <p>Cada consulta conta em {@code linkpulse.cache.requests} com a tag {@code result}:
 * {@code hit} só para {@code L}, {@code negative_hit} para {@code N}/{@code G} e {@code miss}
 * para ausente ou corrompido. Nenhuma tag carrega código, URL ou IP (D-10), e a classe não
 * registra nada em log.
 */
@Component
public class LinkCache {

    /** Prefixo das chaves do cache do redirect. */
    public static final String KEY_PREFIX = "lp:link:";

    /** Nome do contador de consultas ao cache. */
    static final String REQUESTS_METRIC = "linkpulse.cache.requests";

    private static final String SEPARATOR = "|";
    private static final String FOUND = "L";
    private static final String MISSING = "N";
    private static final String GONE = "G";
    private static final String NO_EXPIRATION = "-";
    private static final Duration MIN_TTL = Duration.ofMillis(1);

    private final StringRedisTemplate redis;
    private final LinkPulseProperties.Cache properties;
    private final Clock clock;
    private final Counter hits;
    private final Counter negativeHits;
    private final Counter misses;

    /**
     * Cria o cache e registra os contadores (uma vez só).
     *
     * @param redis template de strings do Spring Data Redis (Lettuce)
     * @param properties propriedades {@code linkpulse.*}; usa {@code linkpulse.cache.*}
     * @param clock relógio da aplicação (base do TTL limitado pela expiração)
     * @param registry registry do Micrometer
     */
    public LinkCache(StringRedisTemplate redis, LinkPulseProperties properties, Clock clock,
            MeterRegistry registry) {
        this.redis = redis;
        this.properties = properties.cache();
        this.clock = clock;
        this.hits = requests(registry, "hit");
        this.negativeHits = requests(registry, "negative_hit");
        this.misses = requests(registry, "miss");
    }

    private static Counter requests(MeterRegistry registry, String result) {
        return Counter.builder(REQUESTS_METRIC)
                .description("Consultas ao cache do redirect por resultado")
                .tag("result", result)
                .register(registry);
    }

    /**
     * Lê o valor do código e conta o resultado.
     *
     * @param code código curto
     * @return o valor decodificado, ou vazio se a chave não existe ou está corrompida (miss)
     */
    public Optional<CacheEntry> get(String code) {
        Optional<CacheEntry> entry = decode(redis.opsForValue().get(key(code)));
        if (entry.isEmpty()) {
            misses.increment();
        } else if (entry.get() instanceof CacheEntry.Found) {
            hits.increment();
        } else {
            negativeHits.increment();
        }
        return entry;
    }

    /**
     * Grava um link válido com TTL {@code min(default-ttl, expiresAt - now)}. Se sobra menos de
     * 1 ms até a expiração, grava o link como expirado ({@code G}) em vez de válido.
     *
     * @param code código curto
     * @param targetUrl URL de destino
     * @param expiresAt expiração, ou {@code null}
     */
    public void putFound(String code, String targetUrl, Instant expiresAt) {
        Duration ttl = foundTtl(properties.defaultTtl(), expiresAt, clock.instant());
        if (ttl.compareTo(MIN_TTL) >= 0) {
            redis.opsForValue().set(key(code), encode(new CacheEntry.Found(targetUrl, expiresAt)),
                    ttl);
        } else if (expiresAt != null) {
            putGone(code, expiresAt);
        }
    }

    /**
     * Grava o negative cache de um código inexistente, com TTL {@code negative-ttl}.
     *
     * @param code código curto
     */
    public void putMissing(String code) {
        redis.opsForValue().set(key(code), MISSING, properties.negativeTtl());
    }

    /**
     * Grava um link expirado, com TTL {@code gone-ttl}.
     *
     * @param code código curto
     * @param expiredAt instante da expiração (vai no {@code expiredAt} do 410)
     */
    public void putGone(String code, Instant expiredAt) {
        redis.opsForValue().set(key(code), encode(new CacheEntry.Gone(expiredAt)),
                properties.goneTtl());
    }

    /**
     * Remove a chave do código (por exemplo, o negative cache de um alias recém-criado).
     *
     * @param code código curto
     */
    public void evict(String code) {
        redis.delete(key(code));
    }

    private static String key(String code) {
        return KEY_PREFIX + code;
    }

    /**
     * Codifica o valor guardado no Valkey.
     *
     * @param entry valor tipado
     * @return {@code L|<ISO ou ->|<url>}, {@code N} ou {@code G|<ISO>}
     */
    static String encode(CacheEntry entry) {
        return switch (entry) {
            case CacheEntry.Found found -> FOUND + SEPARATOR
                    + (found.expiresAt() == null ? NO_EXPIRATION : found.expiresAt().toString())
                    + SEPARATOR + found.targetUrl();
            case CacheEntry.Missing missing -> MISSING;
            case CacheEntry.Gone gone -> GONE + SEPARATOR + gone.expiredAt().toString();
        };
    }

    /**
     * Decodifica o valor lido do Valkey. Qualquer formato inesperado vira vazio (miss), nunca um
     * destino diferente.
     *
     * @param raw valor bruto, possivelmente {@code null}
     * @return o valor tipado, ou vazio se ausente ou corrompido
     */
    static Optional<CacheEntry> decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        if (MISSING.equals(raw)) {
            return Optional.of(new CacheEntry.Missing());
        }
        // Limite 3: a URL pode conter '|'.
        String[] parts = raw.split("\\|", 3);
        try {
            if (FOUND.equals(parts[0]) && parts.length == 3 && !parts[2].isEmpty()) {
                Instant expiresAt = NO_EXPIRATION.equals(parts[1])
                        ? null
                        : Instant.parse(parts[1]);
                return Optional.of(new CacheEntry.Found(parts[2], expiresAt));
            }
            if (GONE.equals(parts[0]) && parts.length == 2) {
                return Optional.of(new CacheEntry.Gone(Instant.parse(parts[1])));
            }
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * TTL de um link válido: {@code defaultTtl} sem expiração; com expiração, o menor entre
     * {@code defaultTtl} e o tempo até expirar, truncado em milissegundos e nunca negativo.
     *
     * @param defaultTtl TTL padrão
     * @param expiresAt expiração, ou {@code null}
     * @param now instante atual pelo relógio da aplicação
     * @return o TTL; {@link Duration#ZERO} se o link já expirou ou falta menos de 1 ms
     */
    static Duration foundTtl(Duration defaultTtl, Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return defaultTtl;
        }
        Duration remaining = Duration.between(now, expiresAt);
        Duration ttl = remaining.compareTo(defaultTtl) < 0 ? remaining : defaultTtl;
        ttl = ttl.truncatedTo(ChronoUnit.MILLIS);
        return ttl.isNegative() ? Duration.ZERO : ttl;
    }
}
