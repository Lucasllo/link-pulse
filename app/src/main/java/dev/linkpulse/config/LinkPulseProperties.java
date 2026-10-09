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
 *     nem de {@code X-Forwarded-*}
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
     * Normaliza {@code selfHosts}: {@code null} vira lista vazia e o conteúdo é copiado (imutável).
     *
     * @param baseUrl base da URL curta
     * @param code parâmetros do gerador
     * @param alias regras do alias
     * @param selfHosts hosts extras do próprio encurtador
     */
    public LinkPulseProperties {
        selfHosts = selfHosts == null ? List.of() : List.copyOf(selfHosts);
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
