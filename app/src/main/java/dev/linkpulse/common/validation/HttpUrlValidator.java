package dev.linkpulse.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validador de {@link HttpUrl}: allow-list de esquema, host obrigatório e nada de credenciais.
 *
 * <p>O parse é o {@link URI} estrito do JDK, que rejeita espaços e caracteres ilegais. Isso
 * barra destinos como {@code javascript:}, {@code data:} e {@code file:}. URLs com
 * {@code userinfo} ({@code https://banco.com@evil.com}) também são rejeitadas, porque esse é um
 * truque comum de phishing: o leitor vê o primeiro host, mas o navegador vai para o segundo.
 */
public final class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return false;
        }
        String host = uri.getHost();
        return host != null && !host.isBlank() && uri.getUserInfo() == null;
    }
}
