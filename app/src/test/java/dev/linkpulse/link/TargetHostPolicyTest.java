package dev.linkpulse.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Testes unitários do {@link TargetHostPolicy} (T-08-01 a T-08-03): próprio encurtador com host
 * normalizado, loopback em qualquer forma literal e IP numérico fora da forma canônica, tudo sem
 * DNS. Rodam sem Docker.
 */
class TargetHostPolicyTest {

    private final TargetHostPolicy policy = new TargetHostPolicy(
            URI.create("https://lnk.example.com"),
            Arrays.asList("lb.example.com", "ALIAS.example.com.", "  ", null));

    @ParameterizedTest
    @ValueSource(strings = {
        "https://lnk.example.com/x",
        "https://LNK.EXAMPLE.COM/x",
        "https://lnk.example.com./x",
        "http://lnk.example.com:8080/x",
        "https://lb.example.com/x",
        "https://lb.example.com./x",
        "https://alias.example.com/x"
    })
    void rejectsTheShortenerItself(String url) {
        assertRejected(policy, url, TargetHostPolicy.SELF_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/x",
        "http://LOCALHOST./x",
        "http://app.localhost:8080/x",
        "http://127.0.0.1:8080/x",
        "http://127.255.255.254/x",
        "http://0.0.0.0:8080/x",
        "http://[::1]:8080/x",
        "http://[0:0:0:0:0:0:0:1]/x",
        "http://[::]/x",
        "http://[::ffff:127.0.0.1]/x",
        "http://[::ffff:7f00:1]/x"
    })
    void rejectsLoopbackDestinations(String url) {
        assertRejected(policy, url, TargetHostPolicy.LOOPBACK_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://0x7f000001:8080/x",
        "http://2130706433:8080/x",
        "http://0177.0.0.1:8080/x",
        "http://127.000.000.001/x",
        "http://0x08080808/x"
    })
    void rejectsNonCanonicalNumericHosts(String url) {
        assertRejected(policy, url, TargetHostPolicy.NUMERIC_HOST_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com/x",
        "http://93.184.215.14/x",
        "http://126.255.255.255/x",
        "http://128.0.0.1/x",
        "http://[2001:db8::1]/x",
        "https://1password.com/x",
        "https://localhost.example.com/x",
        "http://notlocalhost/x",
        "https://sub.lnk.example.com/x",
        "https://lnk.example.com.evil.com/x",
        "http://[fe80::1%25en0]/x"
    })
    void acceptsOtherDestinations(String url) {
        assertThatNoException().isThrownBy(() -> policy.check(url));
    }

    @Test
    void normalizesCaseAndTrailingDots() {
        assertThat(TargetHostPolicy.normalizeHost("LocalHost..")).isEqualTo("localhost");
        assertThat(TargetHostPolicy.normalizeHost(null)).isNull();
        assertThat(TargetHostPolicy.normalizeHost("[::1]")).isEqualTo("[::1]");
    }

    @Test
    void baseUrlWithTrailingDotStillMatches() {
        TargetHostPolicy dotted = new TargetHostPolicy(
                URI.create("http://localhost.:8080"), List.of());

        assertRejected(dotted, "http://localhost:8080/x", TargetHostPolicy.SELF_MESSAGE);
    }

    @Test
    void constructorRejectsBaseUrlWithoutHost() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new TargetHostPolicy(URI.create("lnk.example.com"), List.of()));
    }

    private static void assertRejected(TargetHostPolicy policy, String url, String message) {
        assertThatThrownBy(() -> policy.check(url))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getBody().getType().toString())
                            .isEqualTo("/problems/validation-error");
                    Map<String, Object> properties = e.getBody().getProperties();
                    assertThat(properties).isNotNull().containsKey("errors");
                    List<?> errors = (List<?>) properties.get("errors");
                    assertThat(errors).isNotEmpty();
                    Map<?, ?> first = (Map<?, ?>) errors.get(0);
                    assertThat(first.get("field")).isEqualTo("url");
                    assertThat(first.get("message")).isEqualTo(message);
                });
    }
}
