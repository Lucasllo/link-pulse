package dev.linkpulse.stats;

import dev.linkpulse.click.ClickDocument;
import dev.linkpulse.link.LinkProblems;
import dev.linkpulse.link.LinkRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Service;

/**
 * Estatísticas de cliques de um link, numa única ida ao MongoDB ({@code $match} + {@code $facet}).
 *
 * <p>A existência do código é conferida no Postgres antes (D-04): código inexistente é 404, e
 * link expirado continua com estatísticas. O {@code $match} usa o índice {@code code_ts}
 * (igualdade em {@code code} e intervalo em {@code ts}).
 */
@Service
public class StatsService {

    private final LinkRepository links;
    private final MongoTemplate mongo;
    private final Clock clock;

    /**
     * Cria o service.
     *
     * @param links repositório dos links (existência do código)
     * @param mongo acesso à coleção {@code click_events}
     * @param clock relógio da aplicação (define o "hoje" em UTC)
     */
    public StatsService(LinkRepository links, MongoTemplate mongo, Clock clock) {
        this.links = links;
        this.mongo = mongo;
        this.clock = clock;
    }

    /**
     * Calcula as estatísticas do link no período.
     *
     * @param code código curto
     * @param from primeiro dia pedido, ou {@code null}
     * @param to último dia pedido, ou {@code null}
     * @param includeBots se os cliques de bots entram na conta
     * @return as estatísticas do período efetivo
     */
    public StatsResponse stats(String code, LocalDate from, LocalDate to, boolean includeBots) {
        if (!links.existsByCode(code)) {
            throw LinkProblems.notFound(code);
        }
        StatsPeriod period = StatsPeriod.resolve(from, to,
                LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));

        Document match = new Document("code", code)
                .append("ts", new Document("$gte", Date.from(period.startInclusive()))
                        .append("$lt", Date.from(period.endExclusive())));
        if (!includeBots) {
            match.append("isBot", new Document("$ne", true));
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.stage(new Document("$match", match)),
                Aggregation.stage(new Document("$facet",
                        new Document("total", List.of(new Document("$count", "n"))))));
        Document facets = mongo.aggregate(aggregation, ClickDocument.COLLECTION, Document.class)
                .getUniqueMappedResult();

        return new StatsResponse(code, period.from(), period.to(), includeBots,
                total(facets));
    }

    /**
     * Lê o total do facet {@code total}. O {@code $count} sobre zero documentos devolve lista
     * vazia, que vira 0.
     */
    private static long total(Document facets) {
        if (facets == null) {
            return 0;
        }
        List<Document> total = facets.getList("total", Document.class, List.of());
        return total.isEmpty() ? 0 : number(total.getFirst().get("n"));
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }
}
