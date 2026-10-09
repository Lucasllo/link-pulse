package dev.linkpulse.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propriedades da aplicação no prefixo {@code linkpulse}.
 *
 * <p>Os defaults ficam no {@code application.yml} e podem ser sobrescritos por variável de
 * ambiente via relaxed binding ({@code LINKPULSE_BASE_URL}, {@code LINKPULSE_CODE_ALPHABET},
 * {@code LINKPULSE_CODE_MULTIPLIER}, {@code LINKPULSE_ALIAS_RESERVED},
 * {@code LINKPULSE_SELF_HOSTS}).
 *
 * @param baseUrl base da URL curta devolvida na criação (D-12); nunca derivada de {@code Host}
 *     nem de {@code X-Forwarded-*}. Precisa ser URL {@code http} ou {@code https} absoluta, com
 *     host e sem credenciais, query ou fragmento: valor inválido derruba a subida (WR-01)
 * @param code parâmetros do gerador de códigos curtos
 * @param alias regras do alias customizado; sem configuração, a lista de reservados fica vazia
 * @param selfHosts hosts extras que também são o próprio encurtador (por exemplo, o DNS do load
 *     balancer ou do Ingress); env {@code LINKPULSE_SELF_HOSTS}, separada por vírgula; vazia por
 *     padrão
 */
@ConfigurationProperties("linkpulse")
@Validated
public record LinkPulseProperties(
        @NotNull URI baseUrl,
        @NotNull @Valid Code code,
        @DefaultValue Alias alias,
        List<String> selfHosts) {

    /**
     * Valida {@code baseUrl} e normaliza {@code selfHosts}.
     *
     * <p>Uma {@code baseUrl} que não seja URL {@code http} ou {@code https} absoluta, com host e
     * sem credenciais, query ou fragmento, derruba a subida (D-12, WR-01): sem isso, um valor sem
     * esquema desligaria em silêncio a checagem do próprio encurtador e geraria {@code shortUrl}
     * relativa. A mensagem é fixa e nunca ecoa o valor, que pode conter credenciais. {@code null}
     * fica a cargo do {@code @NotNull}.
     *
     * <p>{@code selfHosts} nulo vira lista vazia; o conteúdo é copiado (imutável).
     *
     * @param baseUrl base da URL curta
     * @param code parâmetros do gerador
     * @param alias regras do alias
     * @param selfHosts hosts extras do próprio encurtador
     * @throws IllegalArgumentException se {@code baseUrl} é inválida
     */
    public LinkPulseProperties {
        if (baseUrl != null && !isValidBaseUrl(baseUrl)) {
            throw new IllegalArgumentException("linkpulse.base-url deve ser uma URL http ou https "
                    + "absoluta, com host e sem credenciais, query ou fragmento");
        }
        selfHosts = selfHosts == null ? List.of() : List.copyOf(selfHosts);
    }

    private static boolean isValidBaseUrl(URI baseUrl) {
        String scheme = baseUrl.getScheme();
        String host = baseUrl.getHost();
        return baseUrl.isAbsolute()
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && host != null && !host.isBlank()
                && baseUrl.getUserInfo() == null
                && baseUrl.getQuery() == null
                && baseUrl.getFragment() == null;
    }

    /**
     * Parâmetros do {@code CodeGenerator} (D-03). Trocar qualquer um deles muda todos os códigos
     * já publicados.
     *
     * @param alphabet alfabeto Base62 (62 caracteres alfanuméricos ASCII distintos)
     * @param multiplier multiplicador da permutação, coprimo de 62^7 (validado na subida)
     */
    public record Code(@NotBlank String alphabet, @Positive long multiplier) {
    }

    /**
     * Regras do alias customizado (D-07).
     *
     * @param reserved palavras que não podem virar alias, comparadas sem diferenciar maiúsculas;
     *     cobre as rotas da aplicação ({@code links}, {@code actuator}, {@code swagger-ui} etc.)
     */
    public record Alias(List<String> reserved) {

        /**
         * Normaliza a lista: {@code null} vira lista vazia e o conteúdo é copiado (imutável).
         *
         * @param reserved palavras reservadas
         */
        public Alias {
            reserved = reserved == null ? List.of() : List.copyOf(reserved);
        }
    }
}
