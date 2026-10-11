package dev.linkpulse.click;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Índices explícitos da coleção de cliques (DATA-02, D-05).
 *
 * <ul>
 *   <li>{@value #CODE_TS}: {@code {code: 1, ts: -1}}, que atende o {@code $match} por código e
 *       período do {@code /stats};</li>
 *   <li>{@value #TS_TTL}: {@code {ts: 1}} com {@code expireAfterSeconds} igual a
 *       {@code linkpulse.clicks.retention} (90 dias por padrão), que apaga cliques antigos.</li>
 * </ul>
 *
 * <p>O TTL fica num índice próprio porque o MongoDB só aplica TTL a índice de campo único, e o
 * campo precisa ser {@code Date} ({@code Instant} no {@link ClickDocument}). O
 * {@code createIndex} não altera o {@code expireAfterSeconds} de um índice que já existe: com
 * outro valor ele falha por conflito de opções (Pitfall P12). Por isso {@link #ensure()} lê os
 * índices e, se o TTL mudou, usa o comando {@code collMod}, sem recriar a coleção nem o índice.
 *
 * <p>Quem chama {@link #ensure()} é o loop do {@link ClickStreamConsumer}, com retry e backoff,
 * nunca a inicialização dos beans: com o MongoDB fora, a aplicação ainda precisa subir e
 * redirecionar.
 */
public class ClickIndexes {

    /** Índice composto {@code {code: 1, ts: -1}}. */
    public static final String CODE_TS = "code_ts";

    /** Índice TTL {@code {ts: 1}}. */
    public static final String TS_TTL = "ts_ttl";

    private static final String EXPIRE_AFTER_SECONDS = "expireAfterSeconds";

    private final MongoTemplate mongo;
    private final String collection;
    private final Duration retention;

    /**
     * Cria o gerenciador de índices. Não toca no MongoDB.
     *
     * @param mongo template do Spring Data MongoDB
     * @param collection coleção dos cliques (normalmente {@link ClickDocument#COLLECTION})
     * @param retention retenção dos cliques, que vira o {@code expireAfterSeconds} do TTL
     */
    public ClickIndexes(MongoTemplate mongo, String collection, Duration retention) {
        this.mongo = mongo;
        this.collection = collection;
        this.retention = retention;
    }

    /**
     * Garante os dois índices. É idempotente: rodar de novo com a mesma retenção não muda nada.
     * Cria a coleção se ela ainda não existe.
     *
     * @throws org.springframework.dao.DataAccessException se o MongoDB falha; o chamador tenta
     *     de novo depois
     */
    public void ensure() {
        IndexOperations indexes = mongo.indexOps(collection);
        indexes.createIndex(new Index()
                .on("code", Sort.Direction.ASC)
                .on("ts", Sort.Direction.DESC)
                .named(CODE_TS));

        Document ttl = findIndex(TS_TTL);
        if (ttl == null) {
            indexes.createIndex(new Index()
                    .on("ts", Sort.Direction.ASC)
                    .named(TS_TTL)
                    .expire(retention));
            return;
        }
        Object current = ttl.get(EXPIRE_AFTER_SECONDS);
        long wanted = retention.toSeconds();
        if (!(current instanceof Number number) || number.longValue() != wanted) {
            mongo.executeCommand(new Document("collMod", collection)
                    .append("index", new Document("name", TS_TTL)
                            .append(EXPIRE_AFTER_SECONDS, wanted)));
        }
    }

    private Document findIndex(String name) {
        List<Document> existing = mongo.getCollection(collection).listIndexes()
                .into(new ArrayList<>());
        for (Document index : existing) {
            if (name.equals(index.getString("name"))) {
                return index;
            }
        }
        return null;
    }
}
