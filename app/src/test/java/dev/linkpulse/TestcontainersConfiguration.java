package dev.linkpulse;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Containers dos testes de integração (PostgreSQL, Valkey e MongoDB), declarados como beans.
 *
 * <p>Como beans, o ciclo de vida acompanha o contexto Spring em cache. Um {@code static @Container}
 * seria parado ao fim de cada classe de IT e deixaria o contexto apontando para uma porta morta.
 *
 * <p>O Valkey não tem módulo próprio no Testcontainers: é um {@link GenericContainer} com
 * {@code @ServiceConnection(name = "redis")}, que faz o Boot criar a conexão Redis a partir dele.
 * A política {@code volatile-lru} é a mesma do compose e do ElastiCache (D-09).
 *
 * <p>O MongoDB usa a classe {@code org.testcontainers.mongodb.MongoDBContainer} (a do pacote
 * antigo é deprecated no Testcontainers 2.x) com a mesma major do compose ({@code mongo:8.0}).
 * O bean se chama {@code mongoContainer} porque {@code mongo} já é o nome do {@code MongoClient}
 * da auto-configuração do Boot, e a sobrescrita de beans é desligada.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> valkey() {
        return new GenericContainer<>(DockerImageName.parse("valkey/valkey:8.1"))
                .withExposedPorts(6379)
                .withCommand("valkey-server", "--maxmemory", "128mb",
                        "--maxmemory-policy", "volatile-lru");
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
    }
}
