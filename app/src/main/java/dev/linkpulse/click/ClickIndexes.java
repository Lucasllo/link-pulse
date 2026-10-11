package dev.linkpulse.click;

import java.time.Duration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Índices de {@code click_events} (esqueleto do RED da 02-05; a criação entra no GREEN).
 */
public class ClickIndexes {

    /** Índice composto {@code {code: 1, ts: -1}}. */
    public static final String CODE_TS = "code_ts";

    /** Índice TTL {@code {ts: 1}}. */
    public static final String TS_TTL = "ts_ttl";

    private final MongoTemplate mongo;
    private final String collection;
    private final Duration retention;

    /**
     * Cria o gerenciador de índices.
     *
     * @param mongo template do Spring Data MongoDB
     * @param collection coleção dos cliques
     * @param retention retenção dos cliques
     */
    public ClickIndexes(MongoTemplate mongo, String collection, Duration retention) {
        this.mongo = mongo;
        this.collection = collection;
        this.retention = retention;
    }

    /** Garante os índices (ainda não faz nada). */
    public void ensure() {
        // RED: sem implementação.
    }
}
