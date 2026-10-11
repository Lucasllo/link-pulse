package dev.linkpulse.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Unitários da janela de bypass do {@link ValkeyAvailability}, com relógio controlado pelo teste.
 */
class ValkeyAvailabilityTest {

    private static final Duration COOLDOWN = Duration.ofSeconds(2);
    private static final String UNAVAILABLE = "linkpulse.valkey.unavailable";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-11T12:00:00Z"));
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ValkeyAvailability availability =
            new ValkeyAvailability(COOLDOWN, clock, registry);

    private double unavailable() {
        return registry.get(UNAVAILABLE).counter().count();
    }

    @Test
    void allowsRequestsBeforeAnyFailure() {
        assertThat(availability.allowRequest()).isTrue();
    }

    @Test
    void deniesDuringCooldownAndAllowsWhenItEnds() {
        availability.recordFailure();

        clock.advanceMillis(1999);
        assertThat(availability.allowRequest()).isFalse();

        clock.advanceMillis(1);
        assertThat(availability.allowRequest()).isTrue();
    }

    @Test
    void countsUnavailableOncePerWindow() {
        availability.recordFailure();
        assertThat(unavailable()).isEqualTo(1.0);

        clock.advanceMillis(500);
        availability.recordFailure();
        assertThat(unavailable()).isEqualTo(1.0);
    }

    @Test
    void secondFailureInsideWindowExtendsIt() {
        availability.recordFailure();
        clock.advanceMillis(1000);
        availability.recordFailure();

        clock.advanceMillis(1500);
        assertThat(availability.allowRequest()).isFalse();

        clock.advanceMillis(500);
        assertThat(availability.allowRequest()).isTrue();
    }

    @Test
    void failureAfterWindowClosesCountsAgain() {
        availability.recordFailure();
        clock.advanceMillis(COOLDOWN.toMillis());
        assertThat(availability.allowRequest()).isTrue();

        availability.recordFailure();

        assertThat(unavailable()).isEqualTo(2.0);
        assertThat(availability.allowRequest()).isFalse();
    }

    /**
     * Relógio que só anda quando o teste manda.
     */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
