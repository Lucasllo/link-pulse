package dev.linkpulse.config;

import dev.linkpulse.cache.ValkeyAvailability;
import dev.linkpulse.click.ClickDocument;
import dev.linkpulse.click.ClickEventWriter;
import dev.linkpulse.click.ClickIndexes;
import dev.linkpulse.click.ClickPublisher;
import dev.linkpulse.click.ClickStreamConsumer;
import dev.linkpulse.click.MongoClickEventWriter;
import dev.linkpulse.common.IpHasher;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Beans da publicação de cliques e do consumer group que os grava no MongoDB.
 *
 * <p>Ficam fora do {@link ApplicationConfig} de propósito: os testes com
 * {@code ApplicationContextRunner} ({@code LinkPulsePropertiesTest}, {@code CodeGeneratorTest})
 * carregam só o {@code ApplicationConfig} e não precisam de {@code linkpulse.ip-hash-key} nem do
 * Valkey.
 */
@Configuration(proxyBeanMethods = false)
public class ClickConfig {

    /**
     * Hasher do IP com a chave de {@code linkpulse.ip-hash-key}. Chave ausente ou curta derruba
     * a subida, com mensagem que não ecoa o valor.
     *
     * @param properties propriedades {@code linkpulse.*}
     * @return o hasher configurado
     */
    @Bean
    IpHasher ipHasher(LinkPulseProperties properties) {
        return new IpHasher(properties.ipHashKey());
    }

    /**
     * Publisher assíncrono e limitado dos cliques no stream do Valkey.
     *
     * @param redis template de strings do Spring Data Redis
     * @param availability gate de disponibilidade do Valkey, compartilhado com o cache
     * @param ipHasher hasher do IP
     * @param properties propriedades {@code linkpulse.*}; usa {@code linkpulse.clicks.*}
     * @param clock relógio da aplicação
     * @param registry registry do Micrometer
     * @return o publisher configurado
     */
    @Bean
    ClickPublisher clickPublisher(StringRedisTemplate redis, ValkeyAvailability availability,
            IpHasher ipHasher, LinkPulseProperties properties, Clock clock,
            MeterRegistry registry) {
        return new ClickPublisher(redis, availability, ipHasher, properties.clicks(), clock,
                registry);
    }

    /**
     * Gravação idempotente dos cliques em {@code click_events}.
     *
     * @param mongo template do Spring Data MongoDB
     * @return o writer configurado
     */
    @Bean
    MongoClickEventWriter clickEventWriter(MongoTemplate mongo) {
        return new MongoClickEventWriter(mongo, ClickDocument.COLLECTION);
    }

    /**
     * Índices {@code code_ts} e {@code ts_ttl} de {@code click_events}, com o TTL igual a
     * {@code linkpulse.clicks.retention}. O bean não toca no MongoDB: quem chama
     * {@link ClickIndexes#ensure()} é o loop do consumer, com retry.
     *
     * @param mongo template do Spring Data MongoDB
     * @param properties propriedades {@code linkpulse.*}; usa {@code linkpulse.clicks.retention}
     * @return os índices configurados
     */
    @Bean
    ClickIndexes clickIndexes(MongoTemplate mongo, LinkPulseProperties properties) {
        return new ClickIndexes(mongo, ClickDocument.COLLECTION, properties.clicks().retention());
    }

    /**
     * Consumer group que lê {@code linkpulse.clicks.stream-key} e grava em {@code click_events}.
     * Desligável por instância com {@code linkpulse.clicks.consumer.enabled=false}.
     *
     * <p>O nome do consumer no grupo é o {@code HOSTNAME} (nome do pod ou do container), para que
     * as entradas pendentes de uma instância sejam reconhecíveis no {@code XPENDING}; sem ele,
     * {@code lp-<uuid>}. A fábrica de conexões vem de
     * {@link ClickStreamConsumer#dedicatedConnectionFactory} e de propósito não é bean: um
     * segundo {@code RedisConnectionFactory} no contexto desligaria a auto-configuração do Boot.
     *
     * @param properties propriedades {@code linkpulse.*}; usa {@code linkpulse.clicks.*}
     * @param details conexão do Valkey resolvida pelo Boot
     * @param writer gravação do lote
     * @param indexes índices de {@code click_events}, garantidos no loop do consumer
     * @param registry registry do Micrometer
     * @param environment ambiente, para ler o {@code HOSTNAME}
     * @param clock relógio da aplicação, que agenda o reclaim de pendentes
     * @return o consumer, que sobe com o contexto
     */
    @Bean
    @ConditionalOnProperty(prefix = "linkpulse.clicks.consumer", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    ClickStreamConsumer clickStreamConsumer(LinkPulseProperties properties,
            DataRedisConnectionDetails details, ClickEventWriter writer, ClickIndexes indexes,
            MeterRegistry registry, Environment environment, Clock clock) {
        LinkPulseProperties.Clicks clicks = properties.clicks();
        String hostname = environment.getProperty("HOSTNAME");
        String consumerName = hostname == null || hostname.isBlank()
                ? "lp-" + UUID.randomUUID()
                : hostname;
        ClickStreamConsumer.Settings settings = new ClickStreamConsumer.Settings(
                clicks.streamKey(), clicks.group(), consumerName,
                clicks.consumer().batchSize(), clicks.consumer().block(),
                clicks.consumer().reclaimInterval(), clicks.consumer().minIdle());
        return new ClickStreamConsumer(settings,
                ClickStreamConsumer.dedicatedConnectionFactory(details, clicks.consumer().block()),
                writer, indexes, registry, clock);
    }
}
