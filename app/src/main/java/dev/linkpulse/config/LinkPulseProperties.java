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
 * {@code LINKPULSE_CLICKS_PUBLISHER_THREADS}, {@code LINKPULSE_CLICKS_PUBLISHER_QUEUE_CAPACITY},
 * {@code LINKPULSE_CLICKS_GROUP}, {@code LINKPULSE_CLICKS_RETENTION},
 * {@code LINKPULSE_CLICKS_CONSUMER_ENABLED}, {@code LINKPULSE_CLICKS_CONSUMER_BATCH_SIZE},
 * {@code LINKPULSE_CLICKS_CONSUMER_BLOCK}, {@code LINKPULSE_CLICKS_CONSUMER_RECLAIM_INTERVAL},
 * {@code LINKPULSE_CLICKS_CONSUMER_MIN_IDLE}, {@code LINKPULSE_RATELIMIT_ENABLED},
 * {@code LINKPULSE_RATELIMIT_LIMIT}, {@code LINKPULSE_RATELIMIT_WINDOW} etc.).
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
 * @param clicks publicação dos cliques no stream do Valkey e consumo pelo consumer group
 *     ({@code linkpulse.clicks.*}); os defaults ficam nas anotações, então o bloco é opcional
 * @param ratelimit rate limit de criação de links por IP ({@code linkpulse.ratelimit.*}, D-08);
 *     os defaults ficam nas anotações, então o bloco é opcional
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
        @Valid @DefaultValue Clicks clicks,
        @Valid @DefaultValue RateLimit ratelimit) {

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
     * @param ratelimit rate limit de criação de links por IP
     * @throws IllegalArgumentException se {@code baseUrl} é inválida
     */
    public LinkPulseProperties {
        if (baseUrl != null && !isValidBaseUrl(baseUrl)) {
            throw new IllegalArgumentException("linkpulse.base-url deve ser uma URL http ou https "
                    + "absoluta, com host e sem credenciais, query ou fragmento");
        }
        selfHosts = selfHosts == null ? List.of() : List.copyOf(selfHosts);
    }

    /**
     * Mesmo formato do {@code toString()} gerado para o record, mas com {@code ipHashKey=****}
     * quando houver chave. Evita vazar a chave do HMAC em log, em relatório de falha de binding
     * ou em mensagem de erro que imprima as propriedades (T-02-44).
     *
     * @return representação das propriedades sem a chave do HMAC
     */
    @Override
    public String toString() {
        return "LinkPulseProperties[baseUrl=" + baseUrl
                + ", code=" + code
                + ", alias=" + alias
                + ", selfHosts=" + selfHosts
                + ", cache=" + cache
                + ", ipHashKey=" + (ipHashKey == null ? "null" : "****")
                + ", clicks=" + clicks
                + ", ratelimit=" + ratelimit + "]";
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
     * Publicação dos cliques no Redis Stream (CLICK-01) e consumo pelo consumer group que grava
     * em {@code click_events} no MongoDB (CLICK-02, CLICK-03).
     *
     * <p>O nome {@code PublisherSettings} evita colisão com o {@code Consumer} do Spring Data
     * Redis; pelo mesmo motivo o lado do consumo é {@code ConsumerSettings}.
     *
     * @param streamKey chave do stream. Env {@code LINKPULSE_CLICKS_STREAM_KEY}; padrão
     *     {@code lp:clicks}
     * @param maxLen limite aproximado de entradas ({@code XADD MAXLEN ~}), para o stream não
     *     crescer sem limite. Env {@code LINKPULSE_CLICKS_MAX_LEN}; padrão 100000
     * @param publisher executor da publicação
     * @param group nome do consumer group que lê o stream. Env {@code LINKPULSE_CLICKS_GROUP};
     *     padrão {@code click-writers}
     * @param retention por quanto tempo um clique fica em {@code click_events} (índice TTL do
     *     MongoDB, criado pelo {@code ClickIndexes}). Env {@code LINKPULSE_CLICKS_RETENTION};
     *     padrão 90d
     * @param consumer leitura em lote do stream pelo consumer group
     */
    public record Clicks(
            @DefaultValue("lp:clicks") @NotBlank String streamKey,
            @DefaultValue("100000") @Positive long maxLen,
            @Valid @DefaultValue PublisherSettings publisher,
            @DefaultValue("click-writers") @NotBlank String group,
            @DefaultValue("90d") Duration retention,
            @Valid @DefaultValue ConsumerSettings consumer) {

        /**
         * Exige retenção positiva; a mensagem é fixa e não ecoa o valor.
         *
         * @param streamKey chave do stream
         * @param maxLen limite aproximado de entradas
         * @param publisher executor da publicação
         * @param group nome do consumer group
         * @param retention retenção dos cliques no MongoDB
         * @param consumer leitura em lote do stream
         * @throws IllegalArgumentException se {@code retention} é nula, zero ou negativa
         */
        public Clicks {
            if (retention == null || retention.isNegative() || retention.isZero()) {
                throw new IllegalArgumentException(
                        "linkpulse.clicks.retention deve ser uma duração positiva");
            }
        }

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

        /**
         * Leitura do stream pelo consumer group ({@code XREADGROUP COUNT/BLOCK}).
         *
         * @param enabled liga o consumer nesta instância. Env
         *     {@code LINKPULSE_CLICKS_CONSUMER_ENABLED}; padrão {@code true}
         * @param batchSize máximo de entradas por leitura ({@code COUNT}), gravadas num único
         *     bulk no MongoDB. Env {@code LINKPULSE_CLICKS_CONSUMER_BATCH_SIZE}; padrão 500
         * @param block quanto tempo a leitura espera por entradas novas ({@code BLOCK}). Também
         *     limita quanto o shutdown espera o loop sair. Env
         *     {@code LINKPULSE_CLICKS_CONSUMER_BLOCK}; padrão 2s
         * @param reclaimInterval de quanto em quanto tempo o consumer procura entradas pendentes
         *     paradas no grupo ({@code XPENDING IDLE}) e as assume ({@code XCLAIM}) para gravar de
         *     novo (CLICK-04). Env {@code LINKPULSE_CLICKS_CONSUMER_RECLAIM_INTERVAL}; padrão 30s
         * @param minIdle há quanto tempo, no mínimo, uma entrada precisa estar entregue sem
         *     {@code XACK} para ser assumida no reclaim; abaixo disso ela ainda pode estar em
         *     processamento por outra instância. Env {@code LINKPULSE_CLICKS_CONSUMER_MIN_IDLE};
         *     padrão 60s
         */
        public record ConsumerSettings(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("500") @Positive int batchSize,
                @DefaultValue("2s") Duration block,
                @DefaultValue("30s") Duration reclaimInterval,
                @DefaultValue("60s") Duration minIdle) {

            /**
             * Exige durações positivas; a mensagem é fixa e não ecoa o valor.
             *
             * @param enabled liga o consumer
             * @param batchSize máximo de entradas por leitura
             * @param block espera da leitura bloqueante
             * @param reclaimInterval intervalo entre as buscas de pendentes parados
             * @param minIdle tempo mínimo sem {@code XACK} para assumir uma entrada
             * @throws IllegalArgumentException se {@code block}, {@code reclaimInterval} ou
             *     {@code minIdle} é nulo, zero ou negativo
             */
            public ConsumerSettings {
                if (!isPositive(block)) {
                    throw new IllegalArgumentException(
                            "linkpulse.clicks.consumer.block deve ser uma duração positiva");
                }
                if (!isPositive(reclaimInterval)) {
                    throw new IllegalArgumentException("linkpulse.clicks.consumer.reclaim-interval "
                            + "deve ser uma duração positiva");
                }
                if (!isPositive(minIdle)) {
                    throw new IllegalArgumentException(
                            "linkpulse.clicks.consumer.min-idle deve ser uma duração positiva");
                }
            }

            private static boolean isPositive(Duration value) {
                return value != null && !value.isNegative() && !value.isZero();
            }
        }
    }

    /**
     * Rate limit de criação de links por IP, em janela fixa no Valkey (D-08). Vale só para o
     * {@code POST /links}; o redirect, o {@code HEAD} e o {@code /stats} nunca são limitados.
     *
     * @param enabled liga o rate limit. Env {@code LINKPULSE_RATELIMIT_ENABLED}; padrão
     *     {@code true}
     * @param limit criações aceitas por IP em cada janela; a seguinte responde 429. Env
     *     {@code LINKPULSE_RATELIMIT_LIMIT}; padrão 20
     * @param window tamanho da janela fixa. Env {@code LINKPULSE_RATELIMIT_WINDOW}; padrão 1m
     */
    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("20") @Positive int limit,
            @DefaultValue("1m") Duration window) {

        /**
         * Exige janela positiva; a mensagem é fixa e não ecoa o valor.
         *
         * @param enabled liga o rate limit
         * @param limit criações aceitas por IP em cada janela
         * @param window tamanho da janela fixa
         * @throws IllegalArgumentException se {@code window} é nula, zero ou negativa
         */
        public RateLimit {
            if (window == null || window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException(
                        "linkpulse.ratelimit.window deve ser uma duração positiva");
            }
        }
    }
}
