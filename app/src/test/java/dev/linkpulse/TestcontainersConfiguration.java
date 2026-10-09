package dev.linkpulse;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Containers dos testes de integração, declarados como beans.
 *
 * <p>Como beans, o ciclo de vida acompanha o contexto Spring em cache. Um {@code static @Container}
 * seria parado ao fim de cada classe de IT e deixaria o contexto apontando para uma porta morta.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    }
}
