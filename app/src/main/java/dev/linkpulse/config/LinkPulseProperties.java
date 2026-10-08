package dev.linkpulse.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades da aplicação no prefixo {@code linkpulse}.
 *
 * <p>Os defaults ficam no {@code application.yml} e podem ser sobrescritos por variável de
 * ambiente via relaxed binding ({@code LINKPULSE_BASE_URL}, {@code LINKPULSE_CODE_ALPHABET},
 * {@code LINKPULSE_CODE_MULTIPLIER}).
 *
 * @param baseUrl base da URL curta devolvida na criação (D-12); nunca derivada de {@code Host}
 *     nem de {@code X-Forwarded-*}
 * @param code parâmetros do gerador de códigos curtos
 */
@ConfigurationProperties("linkpulse")
@Validated
public record LinkPulseProperties(@NotNull URI baseUrl, @NotNull @Valid Code code) {

    /**
     * Parâmetros do {@code CodeGenerator} (D-03). Trocar qualquer um deles muda todos os códigos
     * já publicados.
     *
     * @param alphabet alfabeto Base62 (62 caracteres alfanuméricos ASCII distintos)
     * @param multiplier multiplicador da permutação, coprimo de 62^7 (validado na subida)
     */
    public record Code(@NotBlank String alphabet, @Positive long multiplier) {
    }
}
