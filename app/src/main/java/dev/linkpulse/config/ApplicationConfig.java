package dev.linkpulse.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans de infraestrutura da aplicação.
 */
@Configuration(proxyBeanMethods = false)
public class ApplicationConfig {

    /**
     * Único ponto de obtenção do tempo; os testes podem trocá-lo por um relógio fixo.
     *
     * @return relógio UTC do sistema
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
