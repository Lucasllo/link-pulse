package dev.linkpulse.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
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
 * {@code LINKPULSE_SELF_HOSTS}, {@code LINKPULSE_CACHE_DEFAULT_TTL}, {@code LINKPULSE_IP_HASH_KEY},
 * {@code LINKPULSE_CLICKS_STREAM_KEY}, {@code LINKPULSE_CLICKS_MAX_LEN},
 * {@code LINKPULSE_CLICKS_PUBLISHER_THREADS}, {@code LINKPULSE_CLICKS_PUBLISHER_QUEUE_CAPACITY}
 * etc.).
 *
 * @param baseUrl base da URL curta devolvida na criação (D-12); nunca derivada de {@code Host}
 *     nem de {@code X-Forwarded-*}. Precisa ser URL {@code http} ou {@code https} absoluta, com
 *     host e sem credenciais, query ou fragmento: valor inválido derruba a subida (WR-01)
 * @param code parâmetros do gerador de códigos curtos
 * @param alias regras do alias customizado; sem configuração, a lista de reservados fica vazia
 * @param selfHosts hosts extras que também são o próprio encurtador (por exemplo, o DNS do load
 *     balancer ou do Ingress); env {@code LINKPULSE_SELF_HOSTS}, separada por vírgula; vazia por
 *     padrão
 * @param cache TTLs do cache do redirect no Valkey ({@code linkpulse.cache.*}); os defaults ficam
 *     nas anotações, então o bloco é opcional
 * @param ipHashKey chave do HMAC que transforma o IP do cliente em hash antes de qualquer
 *     publicação ({@code linkpulse.ip-hash-key}). Obrigatória fora do dev (env
 *     {@code LINKPULSE_IP_HASH_KEY}); ausente ou com menos de 16 caracteres, o {@code IpHasher}
 *     derruba a subida. É chave de topo porque o rate limit também a usa
 * @param clicks publicação dos cliques no stream do Valkey ({@code linkpulse.clicks.*}); os
 *     defaults ficam nas anotações, então o bloco é opcional
 */
@ConfigurationProperties("linkpulse")
@Validated
public record LinkPulseProperties(
        @NotNull URI baseUrl,
        @NotNull @Valid Code code,
        @DefaultValue Alias alias,
        List<String> selfHosts,
        @Valid @DefaultValue Cache cache,
        String ipHashKey,
        @Valid @DefaultValue Clicks clicks) {

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
     * @param cache TTLs do cache do redirect
     * @param ipHashKey chave do HMAC do IP (validada pelo {@code IpHasher})
     * @param clicks publicação dos cliques no stream
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

    /**
     * TTLs do cache do redirect ({@code lp:link:{code}} no Valkey, D-06).
     *
     * @param defaultTtl TTL de um link válido sem expiração; com expiração, o TTL é o menor entre
     *     este valor e o tempo até expirar. Env {@code LINKPULSE_CACHE_DEFAULT_TTL}; padrão 24h
     * @param negativeTtl TTL do negative cache de código inexistente (404). Curto porque a criação
     *     do alias invalida a chave e uma corrida residual fica limitada a ele. Env
     *     {@code LINKPULSE_CACHE_NEGATIVE_TTL}; padrão 60s
     * @param goneTtl TTL do registro de link expirado (410); um link expirado nunca volta a valer.
     *     Env {@code LINKPULSE_CACHE_GONE_TTL}; padrão 24h
     * @param bypassCooldown tempo em que o cache fica desviado depois de uma falha do Valkey; só é
     *     lido pelo gate de disponibilidade do plano 02-03. Env
     *     {@code LINKPULSE_CACHE_BYPASS_COOLDOWN}; padrão 2s
     */
    public record Cache(
            @DefaultValue("24h") Duration defaultTtl,
            @DefaultValue("60s") Duration negativeTtl,
            @DefaultValue("24h") Duration goneTtl,
            @DefaultValue("2s") Duration bypassCooldown) {

        /**
         * Exige durações positivas; a mensagem é fixa e não ecoa o valor.
         *
         * @param defaultTtl TTL de link válido
         * @param negativeTtl TTL do negative cache
         * @param goneTtl TTL de link expirado
         * @param bypassCooldown cooldown do desvio do cache
         * @throws IllegalArgumentException se algum valor é nulo, zero ou negativo
         */
        public Cache {
            Duration[] values = {defaultTtl, negativeTtl, goneTtl, bypassCooldown};
            for (Duration value : values) {
                if (value == null || value.isNegative() || value.isZero()) {
                    throw new IllegalArgumentException(
                            "linkpulse.cache.* deve ser uma duração positiva");
                }
            }
        }
    }

    /**
     * Publicação dos cliques no Redis Stream (CLICK-01).
     *
     * <p>O nome {@code PublisherSettings} evita colisão com o {@code Consumer} do Spring Data
     * Redis.
     *
     * @param streamKey chave do stream. Env {@code LINKPULSE_CLICKS_STREAM_KEY}; padrão
     *     {@code lp:clicks}
     * @param maxLen limite aproximado de entradas ({@code XADD MAXLEN ~}), para o stream não
     *     crescer sem limite. Env {@code LINKPULSE_CLICKS_MAX_LEN}; padrão 100000
     * @param publisher executor da publicação
     */
    public record Clicks(
            @DefaultValue("lp:clicks") @NotBlank String streamKey,
            @DefaultValue("100000") @Positive long maxLen,
            @Valid @DefaultValue PublisherSettings publisher) {

        /**
         * Executor limitado da publicação: threads fixas e fila com capacidade fixa; o que não
         * cabe na fila é descartado e contado, nunca espera.
         *
         * @param threads threads de plataforma do executor. Env
         *     {@code LINKPULSE_CLICKS_PUBLISHER_THREADS}; padrão 2
         * @param queueCapacity capacidade da fila. Env
         *     {@code LINKPULSE_CLICKS_PUBLISHER_QUEUE_CAPACITY}; padrão 10000
         */
        public record PublisherSettings(
                @DefaultValue("2") @Positive int threads,
                @DefaultValue("10000") @Positive int queueCapacity) {
        }
    }
}
