package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.AbstractIT;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * ITs da entrega at-least-once do pipeline de cliques (CLICK-03, CLICK-04): o {@code XACK} só
 * vem depois da gravação, pendentes de consumers mortos ou de lotes que falharam voltam pelo
 * reclaim ({@code XPENDING IDLE} + {@code XCLAIM}) e o reprocessamento não duplica documentos.
 *
 * <p>Cada teste instancia os próprios consumers, com stream ({@code lp:test:<uuid>}), grupo e
 * coleção ({@code click_events_test_<uuid>}) exclusivos. Assim os IDs do stream, que viram
 * {@code _id} no MongoDB, não colidem com os do {@code lp:clicks} do contexto compartilhado, e o
 * consumer do contexto nunca vê estas entradas. A fábrica Lettuce de cada consumer vem de
 * {@link ClickStreamConsumer#dedicatedConnectionFactory}, como em produção.
 */
class ClickRedeliveryIT extends AbstractIT {

    private static final Duration BLOCK = Duration.ofMillis(200);
    private static final Duration RECLAIM_INTERVAL = Duration.ofMillis(200);
    private static final Duration WAIT = Duration.ofSeconds(15);
    private static final Duration POLL = Duration.ofMillis(100);
    private static final int BATCH_SIZE = 100;

    @Autowired
    private DataRedisConnectionDetails redisDetails;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MongoTemplate mongo;

    /**
     * Stream, grupo e coleção exclusivos de um teste.
     *
     * @param stream chave do stream
     * @param group nome do consumer group
     * @param collection coleção de destino
     */
    private record Fixture(String stream, String group, String collection) {

        static Fixture create() {
            String suffix = UUID.randomUUID().toString();
            return new Fixture("lp:test:" + suffix, "redelivery-" + suffix,
                    "click_events_test_" + suffix);
        }
    }

    private ClickStreamConsumer newConsumer(Fixture fixture, String name, ClickEventWriter writer,
            Duration minIdle, MeterRegistry registry) {
        ClickStreamConsumer.Settings settings = new ClickStreamConsumer.Settings(
                fixture.stream(), fixture.group(), name, BATCH_SIZE, BLOCK, RECLAIM_INTERVAL,
                minIdle);
        return new ClickStreamConsumer(settings,
                ClickStreamConsumer.dedicatedConnectionFactory(redisDetails, BLOCK),
                writer, new ClickIndexes(mongo, fixture.collection(), Duration.ofDays(90)),
                registry, Clock.systemUTC());
    }

    private void addClick(String stream, String code, int i) {
        Map<String, String> fields = Map.of(
                "code", code,
                "ts", String.valueOf(System.currentTimeMillis()),
                "ref", "(direct)",
                "ua", "",
                "iph", String.format("%064x", i));
        redis.opsForStream().add(MapRecord.create(stream, fields));
    }

    private long count(String collection, String code) {
        return mongo.count(Query.query(Criteria.where("code").is(code)), collection);
    }

    private long pending(String stream, String group) {
        return redis.opsForStream().pending(stream, group).getTotalPendingMessages();
    }

    private static double counter(MeterRegistry registry, String name) {
        Counter counter = registry.find(name).counter();
        return counter == null ? 0 : counter.count();
    }

    /**
     * Simula um consumer que leu entradas e morreu antes do {@code XACK}: cria o grupo do início
     * do stream e lê tudo como {@code dead-1}, sem confirmar. A leitura não usa {@code BLOCK},
     * então o template do contexto (timeout curto) serve.
     *
     * @param fixture stream e grupo do teste
     * @return as entradas que ficaram pendentes com {@code dead-1}
     */
    // O read recebe StreamOffset<K>... (varargs genérico): o aviso é só da criação do array.
    @SuppressWarnings("unchecked")
    private List<MapRecord<String, Object, Object>> readAsDeadConsumer(Fixture fixture) {
        redis.opsForStream().createGroup(fixture.stream(), ReadOffset.from("0"), fixture.group());
        List<MapRecord<String, Object, Object>> read = redis.opsForStream().read(
                Consumer.from(fixture.group(), "dead-1"),
                StreamReadOptions.empty().count(10),
                StreamOffset.create(fixture.stream(), ReadOffset.lastConsumed()));
        assertThat(read).isNotNull();
        return read;
    }

    private void cleanUp(Fixture fixture) {
        redis.delete(fixture.stream());
        mongo.dropCollection(fixture.collection());
    }

    @Test
    void batchIsAcknowledgedOnlyAfterSuccessfulWrite() {
        Fixture fixture = Fixture.create();
        String code = "redeliv-a1";
        ClickEventWriter failing = documents -> {
            throw new DataAccessResourceFailureException("MongoDB fora (simulado)");
        };
        ClickStreamConsumer broken = newConsumer(fixture, "it-broken", failing,
                Duration.ofMillis(200), new SimpleMeterRegistry());
        ClickStreamConsumer healthy = null;
        try {
            broken.start();
            for (int i = 0; i < 5; i++) {
                addClick(fixture.stream(), code, i);
            }

            await().atMost(WAIT).pollInterval(POLL).ignoreExceptions().untilAsserted(() -> {
                assertThat(pending(fixture.stream(), fixture.group()))
                        .as("entradas lidas e não confirmadas").isEqualTo(5);
                assertThat(count(fixture.collection(), code)).isZero();
            });
            broken.stop();

            MeterRegistry registry = new SimpleMeterRegistry();
            healthy = newConsumer(fixture, "it-healthy",
                    new MongoClickEventWriter(mongo, fixture.collection()),
                    Duration.ofMillis(200), registry);
            healthy.start();

            await().atMost(WAIT).pollInterval(POLL).ignoreExceptions().untilAsserted(() -> {
                assertThat(count(fixture.collection(), code)).isEqualTo(5);
                assertThat(pending(fixture.stream(), fixture.group())).isZero();
            });
            assertThat(counter(registry, "linkpulse.clicks.claimed"))
                    .as("linkpulse.clicks.claimed").isGreaterThanOrEqualTo(5);
        } finally {
            broken.stop();
            if (healthy != null) {
                healthy.stop();
            }
            cleanUp(fixture);
        }
    }

    @Test
    void deadConsumerPendingIsReprocessedWithoutDuplicates() {
        Fixture fixture = Fixture.create();
        String code = "redeliv-b1";
        for (int i = 0; i < 6; i++) {
            addClick(fixture.stream(), code, i);
        }
        List<MapRecord<String, Object, Object>> read = readAsDeadConsumer(fixture);
        assertThat(read).hasSize(6);
        assertThat(pending(fixture.stream(), fixture.group())).isEqualTo(6);

        // Crash entre o insert e o XACK: as entradas 0, 2 e 4 (intercaladas no lote) já foram
        // gravadas, e nenhuma foi confirmada.
        for (int i = 0; i < 6; i += 2) {
            MapRecord<String, Object, Object> entry = read.get(i);
            ClickDocument document = ClickDocument
                    .fromStream(entry.getId().getValue(), entry.getValue())
                    .orElseThrow();
            mongo.insert(document, fixture.collection());
        }
        assertThat(count(fixture.collection(), code)).isEqualTo(3);

        MeterRegistry registry = new SimpleMeterRegistry();
        ClickStreamConsumer alive = newConsumer(fixture, "it-alive",
                new MongoClickEventWriter(mongo, fixture.collection()), Duration.ofMillis(200),
                registry);
        try {
            alive.start();

            await().atMost(WAIT).pollInterval(POLL).ignoreExceptions().untilAsserted(() -> {
                assertThat(pending(fixture.stream(), fixture.group())).isZero();
                assertThat(count(fixture.collection(), code)).isEqualTo(6);
            });
            List<Object> ids = mongo.find(Query.query(Criteria.where("code").is(code)),
                    Document.class, fixture.collection()).stream()
                    .map(document -> document.get("_id"))
                    .toList();
            assertThat(ids).containsExactlyInAnyOrderElementsOf(
                    read.stream().map(entry -> (Object) entry.getId().getValue()).toList());
            assertThat(counter(registry, "linkpulse.clicks.duplicates"))
                    .as("linkpulse.clicks.duplicates").isGreaterThanOrEqualTo(3);
            assertThat(counter(registry, "linkpulse.clicks.claimed"))
                    .as("linkpulse.clicks.claimed").isGreaterThanOrEqualTo(6);
        } finally {
            alive.stop();
            cleanUp(fixture);
        }
    }

    @Test
    void trimmedPendingEntriesAreAcknowledgedAndCountedAsLost() {
        Fixture fixture = Fixture.create();
        String code = "redeliv-c1";
        for (int i = 0; i < 4; i++) {
            addClick(fixture.stream(), code, i);
        }
        assertThat(readAsDeadConsumer(fixture)).hasSize(4);
        // O MAXLEN apagou as entradas ainda pendentes (backlog maior que o limite do stream).
        redis.opsForStream().trim(fixture.stream(), 0);
        assertThat(redis.opsForStream().size(fixture.stream())).isZero();
        assertThat(pending(fixture.stream(), fixture.group())).isEqualTo(4);

        MeterRegistry registry = new SimpleMeterRegistry();
        ClickStreamConsumer alive = newConsumer(fixture, "it-alive",
                new MongoClickEventWriter(mongo, fixture.collection()), Duration.ofMillis(200),
                registry);
        try {
            alive.start();

            await().atMost(WAIT).pollInterval(POLL).ignoreExceptions().untilAsserted(() -> {
                assertThat(pending(fixture.stream(), fixture.group()))
                        .as("PEL depois do reclaim").isZero();
                assertThat(counter(registry, "linkpulse.clicks.lost"))
                        .as("linkpulse.clicks.lost").isGreaterThanOrEqualTo(4);
            });
            assertThat(count(fixture.collection(), code)).isZero();
        } finally {
            alive.stop();
            cleanUp(fixture);
        }
    }
}
