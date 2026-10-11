package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.AbstractIT;
import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.data.domain.Range;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
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
 * um código próprio e só olha os documentos dele. O teste do consumer ocioso instancia um
 * consumer próprio, com stream, grupo e coleção exclusivos.
 */
class ClickPipelineIT extends AbstractIT {

    private static final String STREAM = "lp:clicks";
    private static final String COLLECTION = "click_events";
    private static final String GROUP = "click-writers";
    private static final String CURL = "curl/8.5.0";
    private static final long NINETY_DAYS_SECONDS = 7_776_000L;
    private static final long THIRTY_DAYS_SECONDS = 2_592_000L;
    private static final Set<String> DOCUMENT_KEYS = Set.of("_id", "_class", "code", "ts",
            "referrer", "browser", "os", "isBot", "ipHash");
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

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private DataRedisConnectionDetails redisDetails;

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

    private void click(String code, String userAgent) {
        MvcTestResult result = mvc.get().uri("/" + code)
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                })
                .header(HttpHeaders.USER_AGENT, userAgent)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FOUND);
    }

    private static Map<String, String> validEntry(String code) {
        return Map.of(
                "code", code,
                "ts", String.valueOf(System.currentTimeMillis()),
                "ref", "(direct)",
                "ua", FIREFOX,
                "iph", "b".repeat(64));
    }

    private Map<String, Document> indexesOf(String collection) {
        List<Document> indexes = mongo.getCollection(collection).listIndexes()
                .into(new ArrayList<>());
        Map<String, Document> byName = new HashMap<>();
        indexes.forEach(index -> byName.put(index.getString("name"), index));
        return byName;
    }

    private static long expireAfterSeconds(Document index) {
        Object value = index.get("expireAfterSeconds");
        assertThat(value).as("expireAfterSeconds de %s", index.get("name"))
                .isInstanceOf(Number.class);
        return ((Number) value).longValue();
    }

    private double invalidCount() {
        return registry.get("linkpulse.clicks.invalid").counter().count();
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

    @Test
    void documentCarriesBrowserOsAndBotFlag() {
        saveLink("pipe-ua1", "https://example.com/ua1");
        saveLink("pipe-ua2", "https://example.com/ua2");

        click("pipe-ua1", FIREFOX);
        click("pipe-ua2", CURL);

        Document browser = awaitDocuments("pipe-ua1", 1).getFirst();
        assertThat(browser.get("browser")).isEqualTo("Firefox");
        assertThat(browser.get("os")).isEqualTo("Linux");
        assertThat(browser.get("isBot")).isEqualTo(false);
        assertThat(browser.keySet()).isSubsetOf(DOCUMENT_KEYS);

        Document bot = awaitDocuments("pipe-ua2", 1).getFirst();
        assertThat(bot.get("isBot")).isEqualTo(true);
        assertThat(bot.keySet()).isSubsetOf(DOCUMENT_KEYS);
        assertThat(bot.values()).allSatisfy(value -> assertThat(String.valueOf(value))
                .isNotEqualTo(CLIENT_IP)
                .isNotEqualTo(CURL));
    }

    @Test
    void invalidEntryIsAcknowledgedWithoutDocument() {
        double invalidBefore = invalidCount();
        Map<String, String> withoutIpHash = Map.of(
                "code", "pipe-inv1",
                "ts", String.valueOf(System.currentTimeMillis()),
                "ref", "(direct)",
                "ua", FIREFOX);

        RecordId invalidId = redis.opsForStream().add(MapRecord.create(STREAM, withoutIpHash));
        redis.opsForStream().add(MapRecord.create(STREAM, validEntry("pipe-inv2")));

        awaitDocuments("pipe-inv2", 1);
        await().atMost(WAIT).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            assertThat(invalidCount()).isGreaterThanOrEqualTo(invalidBefore + 1);
            PendingMessages pending = redis.opsForStream()
                    .pending(STREAM, GROUP, Range.closed(invalidId.getValue(),
                            invalidId.getValue()), 10);
            assertThat(pending).as("entrada inválida no PEL").isEmpty();
        });
        assertThat(documentsOf("pipe-inv1")).isEmpty();
    }

    @Test
    void idleConsumerDoesNotWriteAndSingleEntryIsAcknowledged() {
        String suffix = UUID.randomUUID().toString();
        String stream = "lp:test:" + suffix;
        String collection = "click_events_test_" + suffix;
        MongoClickEventWriter delegate = new MongoClickEventWriter(mongo, collection);
        List<Integer> calls = new CopyOnWriteArrayList<>();
        ClickEventWriter counting = documents -> {
            calls.add(documents.size());
            return delegate.write(documents);
        };
        Duration block = Duration.ofMillis(200);
        ClickStreamConsumer consumer = new ClickStreamConsumer(
                new ClickStreamConsumer.Settings(stream, stream, "it-idle", 10, block,
                        Duration.ofSeconds(1), Duration.ofSeconds(1)),
                ClickStreamConsumer.dedicatedConnectionFactory(redisDetails, block),
                counting, new ClickIndexes(mongo, collection, Duration.ofDays(90)),
                new SimpleMeterRegistry(), Clock.systemUTC());
        consumer.start();
        try {
            await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(calls).as("escritas com o consumer ocioso")
                            .isEmpty());

            redis.opsForStream().add(MapRecord.create(stream, validEntry("pipe-idle1")));

            await().atMost(WAIT).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
                assertThat(calls).containsExactly(1);
                assertThat(redis.opsForStream().pending(stream, stream).getTotalPendingMessages())
                        .isZero();
            });
            assertThat(mongo.getCollection(collection).countDocuments()).isEqualTo(1);
        } finally {
            consumer.stop();
            redis.delete(stream);
            mongo.dropCollection(collection);
        }
    }

    @Test
    void clickEventsHasCodeTsAndTtlIndexes() {
        saveLink("pipe-idx1", "https://example.com/idx");
        click("pipe-idx1", FIREFOX);
        awaitDocuments("pipe-idx1", 1);

        await().atMost(WAIT).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            Map<String, Document> indexes = indexesOf(COLLECTION);
            assertThat(indexes).containsKeys(ClickIndexes.CODE_TS, ClickIndexes.TS_TTL);
            assertThat(indexes.get(ClickIndexes.CODE_TS).get("key", Document.class))
                    .isEqualTo(new Document("code", 1).append("ts", -1));
            Document ttl = indexes.get(ClickIndexes.TS_TTL);
            assertThat(ttl.get("key", Document.class)).isEqualTo(new Document("ts", 1));
            assertThat(expireAfterSeconds(ttl)).isEqualTo(NINETY_DAYS_SECONDS);
        });
    }

    @Test
    void retentionChangeUpdatesTtlWithCollMod() {
        String collection = "click_events_idx_" + UUID.randomUUID();
        try {
            ClickIndexes ninetyDays = new ClickIndexes(mongo, collection, Duration.ofDays(90));
            assertThatNoException().isThrownBy(() -> {
                ninetyDays.ensure();
                ninetyDays.ensure();
            });
            Map<String, Document> before = indexesOf(collection);
            assertThat(before).containsKeys(ClickIndexes.CODE_TS, ClickIndexes.TS_TTL);
            assertThat(expireAfterSeconds(before.get(ClickIndexes.TS_TTL)))
                    .isEqualTo(NINETY_DAYS_SECONDS);

            new ClickIndexes(mongo, collection, Duration.ofDays(30)).ensure();

            Map<String, Document> after = indexesOf(collection);
            assertThat(expireAfterSeconds(after.get(ClickIndexes.TS_TTL)))
                    .isEqualTo(THIRTY_DAYS_SECONDS);
            assertThat(after.get(ClickIndexes.CODE_TS)).as("code_ts intacto")
                    .isEqualTo(before.get(ClickIndexes.CODE_TS));
            assertThat(after).hasSameSizeAs(before);
        } finally {
            mongo.dropCollection(collection);
        }
    }
}
