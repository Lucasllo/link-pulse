package dev.linkpulse.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linkpulse.AbstractIT;
import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ITs do cache do redirect ({@code lp:link:{code}}) contra PostgreSQL e Valkey reais.
 *
 * <p>Cada teste usa códigos próprios: o contexto, o banco e o Valkey são compartilhados entre
 * todos os ITs.
 */
class CacheIT extends AbstractIT {

    private static final long DEFAULT_TTL_MS = TimeUnit.HOURS.toMillis(24);

    @Autowired
    private LinkRepository links;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private Link saveLink(String code, String target, Instant expiresAt) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return links.saveAndFlush(Link.create(links.nextId(), code, target, expiresAt, now));
    }

    private static String key(String code) {
        return "lp:link:" + code;
    }

    @Test
    void repeatedRedirectIsServedFromCacheEvenAfterRowIsGone() {
        String target = "https://example.com/cache";
        saveLink("cache-hit1", target, null);

        MvcTestResult first = mvc.get().uri("/cache-hit1").exchange();
        assertThat(first).hasStatus(HttpStatus.FOUND);

        jdbc.update("delete from links where code = ?", "cache-hit1");

        MvcTestResult second = mvc.get().uri("/cache-hit1").exchange();
        assertThat(second).hasStatus(HttpStatus.FOUND);
        assertThat(second.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(target);
    }

    @Test
    void cachedEntryOfLinkWithoutExpirationUsesDefaultTtl() {
        saveLink("cache-ttl1", "https://example.com/ttl", null);

        assertThat(mvc.get().uri("/cache-ttl1").exchange()).hasStatus(HttpStatus.FOUND);

        Long ttl = redis.getExpire(key("cache-ttl1"), TimeUnit.MILLISECONDS);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(DEFAULT_TTL_MS);
    }
}
