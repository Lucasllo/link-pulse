package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.AbstractIT;
import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ITs ponta a ponta do pipeline de cliques: GET 302 → stream {@code lp:clicks} → consumer group
 * → coleção {@code click_events} do MongoDB (CLICK-02, CLICK-03, CLICK-06).
 *
 * <p>O stream e a coleção são compartilhados por todos os ITs do contexto, então cada teste usa
 * um código próprio e só olha os documentos dele.
 */
class ClickPipelineIT extends AbstractIT {

    private static final String STREAM = "lp:clicks";
    private static final String COLLECTION = "click_events";
    private static final String CLIENT_IP = "198.51.100.9";
    private static final String FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private LinkRepository links;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MongoTemplate mongo;

    private Link saveLink(String code, String target) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return links.saveAndFlush(Link.create(links.nextId(), code, target, null, now));
    }

    private List<Document> documentsOf(String code) {
        return mongo.find(Query.query(Criteria.where("code").is(code)), Document.class,
                COLLECTION);
    }

    private List<Document> awaitDocuments(String code, int count) {
        AtomicReference<List<Document>> found = new AtomicReference<>();
        await().atMost(WAIT).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            List<Document> documents = documentsOf(code);
            assertThat(documents).as("documentos de %s em %s", code, COLLECTION)
                    .hasSize(count);
            found.set(documents);
        });
        return found.get();
    }

    /**
     * Procura o ID da entrada do stream com o código informado.
     *
     * @param code código curto
     * @return o ID da primeira entrada com {@code code} igual
     */
    private String streamIdOf(String code) {
        List<MapRecord<String, Object, Object>> entries =
                redis.opsForStream().range(STREAM, Range.unbounded());
        assertThat(entries).isNotNull();
        return entries.stream()
                .filter(entry -> code.equals(entry.getValue().get("code")))
                .map(entry -> entry.getId().getValue())
                .findFirst()
                .orElseThrow();
    }

    @Test
    void redirectBecomesClickDocumentInMongo() {
        saveLink("pipe-ok1", "https://example.com/pipeline");

        MvcTestResult result = mvc.get().uri("/pipe-ok1")
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                })
                .header(HttpHeaders.REFERER, "https://news.example.com/x?y=1")
                .header(HttpHeaders.USER_AGENT, FIREFOX)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FOUND);

        Document document = awaitDocuments("pipe-ok1", 1).getFirst();

        assertThat(document.get("_id")).isEqualTo(streamIdOf("pipe-ok1"));
        assertThat(document.getString("_id")).matches("^\\d+-\\d+$");
        assertThat(document.get("referrer")).isEqualTo("news.example.com");
        assertThat(document.getString("ipHash")).matches("^[0-9a-f]{64}$");
        assertThat(document.get("ts")).isInstanceOf(Date.class);
        assertThat(document.values()).allSatisfy(value -> assertThat(String.valueOf(value))
                .isNotEqualTo(CLIENT_IP)
                .isNotEqualTo(FIREFOX));
    }

    @Test
    void mongoDatabaseIsLinkpulse() {
        assertThat(mongo.getDb().getName()).isEqualTo("linkpulse");
    }

    @Test
    void identicalEntriesBecomeDistinctDocuments() {
        Map<String, String> fields = Map.of(
                "code", "pipe-adj1",
                "ts", "1760000000000",
                "ref", "(direct)",
                "ua", "",
                "iph", "a".repeat(64));

        RecordId first = redis.opsForStream().add(MapRecord.create(STREAM, fields));
        RecordId second = redis.opsForStream().add(MapRecord.create(STREAM, fields));

        List<Document> documents = awaitDocuments("pipe-adj1", 2);
        assertThat(documents).extracting(document -> document.get("_id"))
                .containsExactlyInAnyOrder(first.getValue(), second.getValue())
                .doesNotHaveDuplicates();
    }
}
