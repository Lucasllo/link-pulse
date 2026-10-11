package dev.linkpulse.click;

import com.mongodb.bulk.BulkWriteError;
import java.util.List;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Grava os cliques no MongoDB com um único bulk {@code UNORDERED} por lote (D-05, CLICK-03).
 *
 * <p>Não usa o insert em lote do template, que é ordenado e para na primeira duplicata: no bulk
 * não ordenado o MongoDB tenta todos os documentos e devolve os erros no fim. Se todos os erros
 * forem duplicate key (código 11000), o lote é sucesso e as duplicatas são contadas. Qualquer
 * outro erro (ou falha sem {@code BulkOperationException}, como timeout ou write concern) é
 * relançado, e o consumer não confirma o lote.
 */
public class MongoClickEventWriter implements ClickEventWriter {

    /** Código de erro do MongoDB para chave duplicada. */
    static final int DUPLICATE_KEY = 11000;

    private final MongoTemplate mongo;
    private final String collection;

    /**
     * Cria o writer.
     *
     * @param mongo template do Spring Data MongoDB
     * @param collection coleção de destino (normalmente {@link ClickDocument#COLLECTION})
     */
    public MongoClickEventWriter(MongoTemplate mongo, String collection) {
        this.mongo = mongo;
        this.collection = collection;
    }

    @Override
    public int write(List<ClickDocument> documents) {
        if (documents.isEmpty()) {
            return 0;
        }
        try {
            mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, ClickDocument.class, collection)
                    .insert(documents)
                    .execute();
            return 0;
        } catch (BulkOperationException e) {
            List<BulkWriteError> errors = e.getErrors();
            boolean onlyDuplicates = !errors.isEmpty()
                    && errors.stream().allMatch(error -> error.getCode() == DUPLICATE_KEY);
            if (!onlyDuplicates) {
                throw e;
            }
            return errors.size();
        }
    }
}
