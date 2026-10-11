package dev.linkpulse.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testes unitários do codec {@code L/N/G} e do TTL limitado pela expiração do {@link LinkCache}.
 * Rodam sem Docker.
 */
class LinkCacheCodecTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Duration DAY = Duration.ofHours(24);

    @Test
    void foundWithPipeInUrlRoundTripsKeepingMicroseconds() {
        CacheEntry found = new CacheEntry.Found("https://a.example/x|y",
                Instant.parse("2026-12-31T23:59:59.123456Z"));

        String raw = LinkCache.encode(found);

        assertThat(raw).isEqualTo("L|2026-12-31T23:59:59.123456Z|https://a.example/x|y");
        assertThat(LinkCache.decode(raw)).contains(found);
    }

    @Test
    void foundWithoutExpirationUsesDash() {
        CacheEntry found = new CacheEntry.Found("https://a.example/sem", null);

        String raw = LinkCache.encode(found);

        assertThat(raw).isEqualTo("L|-|https://a.example/sem");
        assertThat(LinkCache.decode(raw)).contains(found);
    }

    @Test
    void missingIsN() {
        assertThat(LinkCache.encode(new CacheEntry.Missing())).isEqualTo("N");
        assertThat(LinkCache.decode("N")).contains(new CacheEntry.Missing());
    }

    @Test
    void goneKeepsMicroseconds() {
        Instant expiredAt = Instant.parse("2026-10-01T08:30:15.654321Z");

        String raw = LinkCache.encode(new CacheEntry.Gone(expiredAt));

        assertThat(raw).isEqualTo("G|2026-10-01T08:30:15.654321Z");
        assertThat(LinkCache.decode(raw)).contains(new CacheEntry.Gone(expiredAt));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"X|1", "L|nao-e-data|url", "G|", "L|-", "G|nao-e-data", "N|x"})
    void corruptedValueIsMiss(String raw) {
        assertThat(LinkCache.decode(raw)).isEqualTo(Optional.empty());
    }

    @Test
    void foundTtlWithoutExpirationIsDefault() {
        assertThat(LinkCache.foundTtl(DAY, null, NOW)).isEqualTo(DAY);
    }

    @Test
    void foundTtlIsTimeUntilExpirationWhenShorter() {
        assertThat(LinkCache.foundTtl(DAY, NOW.plusSeconds(2), NOW))
                .isEqualTo(Duration.ofMillis(2000));
    }

    @Test
    void foundTtlIsDefaultWhenExpirationIsFarther() {
        assertThat(LinkCache.foundTtl(Duration.ofSeconds(1), NOW.plusSeconds(3600), NOW))
                .isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void foundTtlBelowOneMillisecondIsZero() {
        assertThat(LinkCache.foundTtl(DAY, NOW.plusNanos(500_000), NOW)).isEqualTo(Duration.ZERO);
    }

    @Test
    void foundTtlAtExactExpirationIsZero() {
        assertThat(LinkCache.foundTtl(DAY, NOW, NOW)).isEqualTo(Duration.ZERO);
    }

    @Test
    void foundTtlAfterExpirationIsZero() {
        assertThat(LinkCache.foundTtl(DAY, NOW.minusSeconds(1), NOW)).isEqualTo(Duration.ZERO);
    }
}
