package dev.linkpulse.config;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Timeouts curtos do driver do MongoDB (Pitfall P7).
 *
 * <p>Os defaults do driver são 30 s de server selection e leitura sem limite. Com o MongoDB
 * pausado ou fora, o consumer de cliques ficaria parado num insert por 30 s ou para sempre, o
 * {@code stop} estouraria o {@code timeout-per-shutdown-phase} e o {@code /stats} seguraria a
 * thread do request. Com estes valores, a falha chega rápido: o consumer cai no backoff e deixa
 * o lote no PEL, e o {@code /stats} responde erro.
 *
 * <p>Só customiza a auto-configuração do Boot (como o {@link RedisConfig}): a URI, o banco e a
 * conexão do {@code @ServiceConnection} dos testes continuam vindo dela.
 */
@Configuration(proxyBeanMethods = false)
public class MongoConfig {

    /** Espera máxima por um servidor disponível antes de falhar a operação. */
    static final long SERVER_SELECTION_TIMEOUT_SECONDS = 3;

    /** Espera máxima para abrir a conexão TCP. */
    static final long CONNECT_TIMEOUT_SECONDS = 2;

    /** Espera máxima por uma resposta numa conexão aberta. */
    static final long READ_TIMEOUT_SECONDS = 5;

    /**
     * Aplica os timeouts às configurações do cluster e do socket.
     *
     * @return customizador do {@code MongoClientSettings}
     */
    @Bean
    MongoClientSettingsBuilderCustomizer mongoTimeouts() {
        return builder -> builder
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(
                        SERVER_SELECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .applyToSocketSettings(socket -> socket
                        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
}
