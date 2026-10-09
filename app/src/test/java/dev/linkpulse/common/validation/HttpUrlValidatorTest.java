package dev.linkpulse.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testes unitários do {@link HttpUrlValidator}: allow-list {@code http}/{@code https}, host
 * obrigatório e nada de credenciais na URL. Rodam sem Docker.
 */
class HttpUrlValidatorTest {

    private final HttpUrlValidator validator = new HttpUrlValidator();

    @Test
    void acceptsNullBecauseNotBlankHandlesIt() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com",
        "http://example.com:8080/a?b=c#d",
        "HTTPS://EXAMPLE.COM/x",
        "http://[::1]/"
    })
    void acceptsAbsoluteHttpAndHttpsUrlsWithHost(String url) {
        assertThat(validator.isValid(url, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://example.com",
        "javascript:alert(1)",
        "mailto:a@b.com",
        "data:text/html,x",
        "file:///etc/passwd"
    })
    void rejectsSchemesOutsideTheAllowList(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://", "http:///caminho", "example.com", "https://exa mple.com"})
    void rejectsUrlsWithoutHostOrWithInvalidSyntax(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://user:pass@example.com",
        "https://banco.com@evil.com/login"
    })
    void rejectsUrlsWithCredentials(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }
}
