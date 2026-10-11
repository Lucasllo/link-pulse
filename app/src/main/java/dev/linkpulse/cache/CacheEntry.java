package dev.linkpulse.cache;

import java.time.Instant;

/**
 * Valor tipado de uma chave {@code lp:link:{code}} do cache do redirect.
 *
 * <p>O cache guarda também os resultados negativos (tombstones): um código inexistente e um link
 * expirado saem do Valkey sem ir ao Postgres.
 */
public sealed interface CacheEntry permits CacheEntry.Found, CacheEntry.Missing, CacheEntry.Gone {

    /**
     * Link válido no momento da gravação.
     *
     * @param targetUrl URL de destino
     * @param expiresAt instante de expiração, ou {@code null} se o link não expira; quem lê confere
     *     de novo contra o relógio da aplicação, porque o TTL do Valkey e o relógio divergem
     */
    record Found(String targetUrl, Instant expiresAt) implements CacheEntry {
    }

    /**
     * Código inexistente (404), gravado com o TTL curto do negative cache.
     */
    record Missing() implements CacheEntry {
    }

    /**
     * Link expirado (410). Guarda o instante de expiração porque o Problem Details do 410 o
     * devolve em {@code expiredAt}.
     *
     * @param expiredAt instante em que o link expirou
     */
    record Gone(Instant expiredAt) implements CacheEntry {
    }
}
