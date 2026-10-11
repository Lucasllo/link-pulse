package dev.linkpulse.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Testes unitários da aritmética da janela fixa do {@link RateLimiter}: índice da janela pelo
 * {@code floor} e {@code Retry-After} como teto dos segundos restantes, nunca menos de 1. Rodam
 * sem Docker.
 */
class RateLimiterTest {

    private static final long MINUTE = 60_000;

    @ParameterizedTest(name = "windowIndex({0}, 60000) = {1}")
    @CsvSource({
        "0, 0",
        "59999, 0",
        "60000, 1"
    })
    void windowIndexIsTheFloorOfNowOverWindow(long nowMillis, long expected) {
        assertThat(RateLimiter.windowIndex(nowMillis, MINUTE)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "retryAfterSeconds({0}, 60000) = {1}")
    @CsvSource({
        "0, 60",
        "30500, 30",
        "59001, 1",
        "59999, 1",
        "60000, 60"
    })
    void retryAfterIsTheCeilingOfTheSecondsLeftAndAtLeastOne(long nowMillis, long expected) {
        assertThat(RateLimiter.retryAfterSeconds(nowMillis, MINUTE)).isEqualTo(expected);
    }
}
