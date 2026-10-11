package dev.linkpulse.config;

import dev.linkpulse.cache.ValkeyAvailability;
import io.lettuce.core.ClientOptions;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ajustes do cliente Lettuce para o fail-open do Valkey (REDIR-05).
 *
 * <p>Os timeouts curtos ficam no {@code application.yml} ({@code spring.data.redis.timeout} e
 * {@code connect-timeout}). Esta classe só customiza a auto-configuração do Boot: ela não declara
 * fábrica de conexão própria, porque isso desligaria a auto-configuração do Lettuce e a conexão
 * vinda do {@code @ServiceConnection} dos testes (Pitfall P4).
 */
@Configuration(proxyBeanMethods = false)
public class RedisConfig {

    /**
     * Com a conexão caída (Valkey parado), os comandos falham na hora em vez de esperar na fila
     * do Lettuce até o timeout.
     *
     * @return customizador das {@code ClientOptions} do Lettuce
     */
    @Bean
    LettuceClientOptionsBuilderCustomizer failFastClientOptions() {
        return builder -> builder.disconnectedBehavior(
                ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }

    /**
     * Gate de disponibilidade compartilhado por cache, publicação de cliques e rate limit, com a
     * janela de bypass de {@code linkpulse.cache.bypass-cooldown}.
     *
     * @param properties propriedades {@code linkpulse.*}
     * @param clock relógio da aplicação
     * @param registry registry do Micrometer
     * @return o gate configurado
     */
    @Bean
    ValkeyAvailability valkeyAvailability(LinkPulseProperties properties, Clock clock,
            MeterRegistry registry) {
        return new ValkeyAvailability(properties.cache().bypassCooldown(), clock, registry);
    }
}
