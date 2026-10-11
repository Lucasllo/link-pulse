package dev.linkpulse.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testes unitários do {@link IpHasher} (CLICK-06): HMAC-SHA256 por dia UTC, dependente da chave,
 * e mensagem de erro fixa que nunca ecoa a chave ({@code linkpulse.ip-hash-key}). Rodam sem
 * Docker.
 */
class IpHasherTest {

    private static final String KEY = "test-ip-hash-key-0123456789";
    private static final String IP = "198.51.100.7";
    private static final Instant MORNING = Instant.parse("2026-10-10T10:00:00Z");
    private static final Instant END_OF_DAY = Instant.parse("2026-10-10T23:59:59.999Z");
    private static final Instant NEXT_DAY = Instant.parse("2026-10-11T00:00:00Z");

    private final IpHasher hasher = new IpHasher(KEY);

    @Test
    void sameIpOnSameUtcDayHasSameHash() {
        assertThat(hasher.hash(IP, MORNING)).isEqualTo(hasher.hash(IP, END_OF_DAY));
    }

    @Test
    void nextUtcDayChangesHash() {
        assertThat(hasher.hash(IP, MORNING)).isNotEqualTo(hasher.hash(IP, NEXT_DAY));
    }

    @Test
    void differentIpChangesHash() {
        assertThat(hasher.hash(IP, MORNING)).isNotEqualTo(hasher.hash("198.51.100.8", MORNING));
    }

    @Test
    void differentKeyChangesHash() {
        IpHasher other = new IpHasher("outra-chave-de-teste-0123456789");

        assertThat(hasher.hash(IP, MORNING)).isNotEqualTo(other.hash(IP, MORNING));
    }

    @Test
    void hashIsLowercaseHexOf64Chars() {
        assertThat(hasher.hash(IP, MORNING)).matches("^[0-9a-f]{64}$");
    }

    @Test
    void hashNeverContainsTheIp() {
        assertThat(hasher.hash(IP, MORNING)).doesNotContain(IP);
    }

    @Test
    void keyWithExactlyMinimumLengthIsAccepted() {
        assertThatNoException().isThrownBy(() -> new IpHasher("x".repeat(IpHasher.MIN_KEY_LENGTH)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "curta-15-chars!"})
    void invalidKeyFailsWithFixedMessage(String key) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new IpHasher(key))
                .withMessageContaining("linkpulse.ip-hash-key")
                .satisfies(e -> {
                    if (key != null && !key.isBlank()) {
                        assertThat(e.getMessage()).doesNotContain(key);
                    }
                });
    }
}
