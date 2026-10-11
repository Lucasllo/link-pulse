package dev.linkpulse.config;

import dev.linkpulse.click.ClickPublisher;
import dev.linkpulse.common.IpHasher;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Beans da publicação de cliques.
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
     * @param ipHasher hasher do IP
     * @param properties propriedades {@code linkpulse.*}; usa {@code linkpulse.clicks.*}
     * @param clock relógio da aplicação
     * @param registry registry do Micrometer
     * @return o publisher configurado
     */
    @Bean
    ClickPublisher clickPublisher(StringRedisTemplate redis, IpHasher ipHasher,
            LinkPulseProperties properties, Clock clock, MeterRegistry registry) {
        return new ClickPublisher(redis, ipHasher, properties.clicks(), clock, registry);
    }
}
