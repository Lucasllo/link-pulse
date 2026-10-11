package dev.linkpulse.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.AbstractIT;
import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private static final long NEGATIVE_TTL_MS = TimeUnit.SECONDS.toMillis(60);

    @Autowired
    private LinkRepository links;

    @Autowired
    private MeterRegistry registry;

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

    @Test
    void ttlNeverExceedsTimeUntilExpiration() {
        Instant expiresAt = Instant.now().plusSeconds(2).truncatedTo(ChronoUnit.MICROS);
        saveLink("cache-exp1", "https://example.com/expira", expiresAt);

        assertThat(mvc.get().uri("/cache-exp1").exchange()).hasStatus(HttpStatus.FOUND);

        Long ttl = redis.getExpire(key("cache-exp1"), TimeUnit.MILLISECONDS);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(2000L);

        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    MvcTestResult result = mvc.get().uri("/cache-exp1").exchange();
                    assertThat(result).hasStatus(HttpStatus.GONE);
                    assertThat(result).bodyJson().extractingPath("$.type")
                            .isEqualTo("/problems/link-expired");
                });
    }

    @Test
    void goneServedFromCacheKeepsMicrosecondPrecision() {
        Instant expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.SECONDS).plusNanos(123_456_000);
        saveLink("gone-prec1", "https://example.com/velho", expiresAt);

        MvcTestResult fromPostgres = mvc.get().uri("/gone-prec1").exchange();
        assertThat(fromPostgres).hasStatus(HttpStatus.GONE);
        assertThat(fromPostgres).bodyJson().extractingPath("$.expiredAt")
                .isEqualTo(expiresAt.toString());

        assertThat(redis.opsForValue().get(key("gone-prec1"))).startsWith("G|");

        MvcTestResult fromCache = mvc.get().uri("/gone-prec1").exchange();
        assertThat(fromCache).hasStatus(HttpStatus.GONE);
        assertThat(fromCache).bodyJson().extractingPath("$.expiredAt")
                .isEqualTo(expiresAt.toString());

        Long ttl = redis.getExpire(key("gone-prec1"), TimeUnit.MILLISECONDS);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(DEFAULT_TTL_MS);
    }

    @Test
    void negativeCacheIsInvalidatedWhenAliasIsCreated() {
        assertThat(mvc.get().uri("/neg-alias_1").exchange()).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(redis.opsForValue().get(key("neg-alias_1"))).isEqualTo("N");
        Long ttl = redis.getExpire(key("neg-alias_1"), TimeUnit.MILLISECONDS);
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(NEGATIVE_TTL_MS);

        MvcTestResult created = mvc.post().uri("/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/neg\",\"alias\":\"neg-alias_1\"}")
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);

        assertThat(redis.hasKey(key("neg-alias_1"))).isFalse();

        MvcTestResult redirect = mvc.get().uri("/neg-alias_1").exchange();
        assertThat(redirect).hasStatus(HttpStatus.FOUND);
        assertThat(redirect.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("https://example.com/neg");
    }

    @Test
    void hitMissAndNegativeHitAreCounted() {
        saveLink("count-hit1", "https://example.com/conta", null);

        double miss = count("miss");
        double hit = count("hit");
        double negativeHit = count("negative_hit");
        assertThat(mvc.get().uri("/count-hit1").exchange()).hasStatus(HttpStatus.FOUND);
        assertThat(mvc.get().uri("/count-hit1").exchange()).hasStatus(HttpStatus.FOUND);
        assertThat(count("miss") - miss).isEqualTo(1.0);
        assertThat(count("hit") - hit).isEqualTo(1.0);
        assertThat(count("negative_hit") - negativeHit).isEqualTo(0.0);

        miss = count("miss");
        hit = count("hit");
        negativeHit = count("negative_hit");
        assertThat(mvc.get().uri("/count-none1").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/count-none1").exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(count("miss") - miss).isEqualTo(1.0);
        assertThat(count("negative_hit") - negativeHit).isEqualTo(1.0);
        assertThat(count("hit") - hit).isEqualTo(0.0);
    }

    private double count(String result) {
        return registry.counter("linkpulse.cache.requests", "result", result).count();
    }
}
