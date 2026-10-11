package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.AbstractIT;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.stream.MapRecord;
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
                fixture.stream(), fixture.group(), name, BATCH_SIZE, BLOCK);
        return new ClickStreamConsumer(settings,
                ClickStreamConsumer.dedicatedConnectionFactory(redisDetails, BLOCK),
                writer, new ClickIndexes(mongo, fixture.collection(), Duration.ofDays(90)),
                registry);
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
}
