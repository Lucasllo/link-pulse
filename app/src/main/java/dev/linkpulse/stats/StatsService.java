package dev.linkpulse.stats;

import dev.linkpulse.click.ClickDocument;
import dev.linkpulse.link.LinkProblems;
import dev.linkpulse.link.LinkRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Service;

/**
 * Estatísticas de cliques de um link, numa única ida ao MongoDB ({@code $match} + {@code $facet}).
 *
 * <p>A existência do código é conferida no Postgres antes (D-04): código inexistente é 404, e
 * link expirado continua com estatísticas. O {@code $match} usa o índice {@code code_ts}
 * (igualdade em {@code code} e intervalo em {@code ts}) e vale para todos os facets (D-01): sem
 * {@code includeBots}, os cliques com {@code isBot: true} ficam fora do total, da série e dos
 * tops. Documentos sem {@code isBot} contam como humanos ({@code $ne: true}).
 *
 * <p>Os dias são agrupados em UTC ({@code $dateToString} com {@code timezone: "UTC"}), e a série
 * contínua, com 0 nos dias sem clique, é montada em Java a partir do mapa vindo do Mongo (D-02).
 * Os tops têm {@link #TOP_SIZE} itens, por cliques decrescente e, no empate, por valor crescente,
 * o que deixa a ordem estável (D-03).
 *
 * <p>O pipeline só recebe valores tipados (o código já validado pela regex da rota, datas e o
 * booleano); nenhuma string do cliente vira operador.
 */
@Service
public class StatsService {

    /** Itens por top (D-03): constante do contrato, sem parâmetro {@code limit}. */
    static final int TOP_SIZE = 10;

    /** Valor de top quando o campo não foi gravado no documento. */
    static final String UNKNOWN = "(unknown)";

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
     * @throws org.springframework.web.ErrorResponseException 404 {@code link-not-found}, 400
     *     {@code invalid-period} ou 503 {@code stats-unavailable} (MongoDB fora)
     */
    public StatsResponse stats(String code, LocalDate from, LocalDate to, boolean includeBots) {
        if (!links.existsByCode(code)) {
            throw LinkProblems.notFound(code);
        }
        StatsPeriod period = StatsPeriod.resolve(from, to,
                LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));

        Document facets = aggregate(match(code, period, includeBots));

        return new StatsResponse(code, period.from(), period.to(), includeBots,
                total(facets), daily(facets, period),
                top(facets, "referrers"), top(facets, "browsers"), top(facets, "os"));
    }

    private static Document match(String code, StatsPeriod period, boolean includeBots) {
        Document match = new Document("code", code)
                .append("ts", new Document("$gte", Date.from(period.startInclusive()))
                        .append("$lt", Date.from(period.endExclusive())));
        if (!includeBots) {
            match.append("isBot", new Document("$ne", true));
        }
        return match;
    }

    /**
     * Roda o {@code $match} + {@code $facet}. Com o MongoDB fora, os timeouts do driver
     * ({@code MongoConfig}) encerram a espera e o erro vira 503, sem logar código nem período.
     */
    private Document aggregate(Document match) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.stage(new Document("$match", match)),
                Aggregation.stage(new Document("$facet", new Document()
                        .append("total", List.of(new Document("$count", "n")))
                        .append("daily", List.of(new Document("$group",
                                new Document("_id", new Document("$dateToString",
                                        new Document("format", "%Y-%m-%d")
                                                .append("date", "$ts")
                                                .append("timezone", "UTC")))
                                        .append("n", new Document("$sum", 1)))))
                        .append("referrers", topPipeline("$referrer"))
                        .append("browsers", topPipeline("$browser"))
                        .append("os", topPipeline("$os")))));
        try {
            return mongo.aggregate(aggregation, ClickDocument.COLLECTION, Document.class)
                    .getUniqueMappedResult();
        } catch (DataAccessException e) {
            throw StatsProblems.unavailable();
        }
    }

    /**
     * Pipeline de um top: agrupa pelo campo, ordena por cliques decrescente e, no empate, pelo
     * valor, e corta em {@link #TOP_SIZE}.
     */
    private static List<Document> topPipeline(String field) {
        return List.of(
                new Document("$group", new Document("_id", field)
                        .append("n", new Document("$sum", 1))),
                new Document("$sort", new Document("n", -1).append("_id", 1)),
                new Document("$limit", TOP_SIZE));
    }

    /**
     * Monta a série contínua do {@code from} ao {@code to}, com 0 nos dias ausentes do facet
     * {@code daily}.
     */
    private static List<StatsResponse.DailyCount> daily(Document facets, StatsPeriod period) {
        Map<String, Long> byDay = new HashMap<>();
        for (Document day : facet(facets, "daily")) {
            Object id = day.get("_id");
            if (id != null) {
                byDay.put(id.toString(), number(day.get("n")));
            }
        }
        return period.from().datesUntil(period.to().plusDays(1))
                .map(date -> new StatsResponse.DailyCount(date,
                        byDay.getOrDefault(date.toString(), 0L)))
                .toList();
    }

    private static List<StatsResponse.TopItem> top(Document facets, String name) {
        return facet(facets, name).stream()
                .map(item -> new StatsResponse.TopItem(
                        item.get("_id") == null ? UNKNOWN : item.get("_id").toString(),
                        number(item.get("n"))))
                .toList();
    }

    private static List<Document> facet(Document facets, String name) {
        return facets == null ? List.of() : facets.getList(name, Document.class, List.of());
    }

    /**
     * Lê o total do facet {@code total}. O {@code $count} sobre zero documentos devolve lista
     * vazia, que vira 0.
     */
    private static long total(Document facets) {
        List<Document> total = facet(facets, "total");
        return total.isEmpty() ? 0 : number(total.getFirst().get("n"));
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }
}
