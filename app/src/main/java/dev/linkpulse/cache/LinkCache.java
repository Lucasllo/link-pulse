package dev.linkpulse.cache;

import dev.linkpulse.config.LinkPulseProperties;
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
 * <p>A chave é {@code lp:link:{code}} e o valor descreve o próprio tipo:
 * {@code L|<expiresAt ISO-8601 ou ->|<url>} para um link válido. Os instantes vão em ISO-8601
 * ({@link Instant#toString()}), que preserva os microssegundos do Postgres. O TTL de um link
 * válido nunca passa do tempo que falta até a expiração.
 *
 * <p>Não registra em log código nem URL.
 */
@Component
public class LinkCache {

    /** Prefixo das chaves do cache do redirect. */
    public static final String KEY_PREFIX = "lp:link:";

    private static final String SEPARATOR = "|";
    private static final String FOUND = "L";
    private static final String NO_EXPIRATION = "-";
    private static final Duration MIN_TTL = Duration.ofMillis(1);

    private final StringRedisTemplate redis;
    private final LinkPulseProperties.Cache properties;
    private final Clock clock;

    /**
     * Cria o cache.
     *
     * @param redis template de strings do Spring Data Redis (Lettuce)
     * @param properties propriedades {@code linkpulse.*}; usa {@code linkpulse.cache.*}
     * @param clock relógio da aplicação (base do TTL limitado pela expiração)
     */
    public LinkCache(StringRedisTemplate redis, LinkPulseProperties properties, Clock clock) {
        this.redis = redis;
        this.properties = properties.cache();
        this.clock = clock;
    }

    /**
     * Lê o valor do código.
     *
     * @param code código curto
     * @return o valor decodificado, ou vazio se a chave não existe ou está corrompida (miss)
     */
    public Optional<CacheEntry> get(String code) {
        return decode(redis.opsForValue().get(key(code)));
    }

    /**
     * Grava um link válido com TTL {@code min(default-ttl, expiresAt - now)}. Se sobra menos de
     * 1 ms até a expiração, nada é gravado.
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
        }
    }

    private static String key(String code) {
        return KEY_PREFIX + code;
    }

    /**
     * Codifica o valor guardado no Valkey.
     *
     * @param entry valor tipado
     * @return {@code L|<ISO ou ->|<url>}
     * @throws IllegalArgumentException para tipos ainda sem codificação
     */
    static String encode(CacheEntry entry) {
        if (entry instanceof CacheEntry.Found found) {
            String expires = found.expiresAt() == null
                    ? NO_EXPIRATION
                    : found.expiresAt().toString();
            return FOUND + SEPARATOR + expires + SEPARATOR + found.targetUrl();
        }
        throw new IllegalArgumentException("tipo de entrada do cache sem codificação");
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
        // Limite 3: a URL pode conter '|'.
        String[] parts = raw.split("\\|", 3);
        if (parts.length != 3 || !FOUND.equals(parts[0]) || parts[2].isEmpty()) {
            return Optional.empty();
        }
        try {
            Instant expiresAt = NO_EXPIRATION.equals(parts[1]) ? null : Instant.parse(parts[1]);
            return Optional.of(new CacheEntry.Found(parts[2], expiresAt));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
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
