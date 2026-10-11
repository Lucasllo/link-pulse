package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.AbstractIT;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
 * ITs do ciclo de vida do consumer de cliques: o stop conclui o lote em andamento (CLICK-05) e
 * as gauges de pendentes, lag e tamanho do stream acompanham o grupo (CLICK-08).
 *
 * <p>Como no {@code ClickRedeliveryIT}, cada teste usa stream ({@code lp:test:<uuid>}), grupo,
 * coleção e {@link SimpleMeterRegistry} próprios, e uma fábrica Lettuce dedicada com
 * {@code BLOCK} de 200 ms. Os consumers são sempre parados no {@code finally}, com o writer
 * liberado antes, para um teste que falhou não deixar uma thread presa.
 */
class ClickConsumerLifecycleIT extends AbstractIT {

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
            return new Fixture("lp:test:" + suffix, "lifecycle-" + suffix,
                    "click_events_test_" + suffix);
        }
    }

    private ClickStreamConsumer newConsumer(Fixture fixture, ClickEventWriter writer,
            Duration minIdle, MeterRegistry registry) {
        ClickStreamConsumer.Settings settings = new ClickStreamConsumer.Settings(
                fixture.stream(), fixture.group(), "it-lifecycle", BATCH_SIZE, BLOCK,
                Duration.ofMillis(200), minIdle);
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

    private static double gauge(MeterRegistry registry, String name) {
        Gauge gauge = registry.find(name).gauge();
        assertThat(gauge).as("gauge %s", name).isNotNull();
        return gauge.value();
    }

    private void cleanUp(Fixture fixture) {
        redis.delete(fixture.stream());
        mongo.dropCollection(fixture.collection());
    }

    @Test
    void stopWaitsForInFlightBatch() throws InterruptedException {
        Fixture fixture = Fixture.create();
        String code = "life-stop1";
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        MongoClickEventWriter delegate = new MongoClickEventWriter(mongo, fixture.collection());
        ClickEventWriter gated = documents -> {
            entered.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("writer interrompido", e);
            }
            return delegate.write(documents);
        };
        // As 3 entradas entram antes do start: o grupo nasce do início do stream e a primeira
        // leitura traz as 3 num único lote.
        for (int i = 0; i < 3; i++) {
            addClick(fixture.stream(), code, i);
        }
        ClickStreamConsumer consumer = newConsumer(fixture, gated, Duration.ofHours(1),
                new SimpleMeterRegistry());
        try {
            consumer.start();
            assertThat(entered.await(WAIT.toMillis(), TimeUnit.MILLISECONDS))
                    .as("lote entrou no writer").isTrue();

            long start = System.nanoTime();
            consumer.stop(stopped::countDown);
            assertThat(Duration.ofNanos(System.nanoTime() - start))
                    .as("stop(callback) volta sem esperar o lote")
                    .isLessThan(Duration.ofSeconds(1));
            assertThat(consumer.isRunning()).isFalse();

            assertThat(stopped.await(500, TimeUnit.MILLISECONDS))
                    .as("callback antes de o lote terminar").isFalse();

            release.countDown();
            assertThat(stopped.await(10, TimeUnit.SECONDS))
                    .as("callback depois de o lote ser gravado e confirmado").isTrue();

            assertThat(count(fixture.collection(), code)).isEqualTo(3);
            assertThat(pending(fixture.stream(), fixture.group())).isZero();
            assertThat(consumer.isRunning()).isFalse();
        } finally {
            release.countDown();
            consumer.stop();
            cleanUp(fixture);
        }
    }

    @Test
    void pendingLagAndLengthGaugesFollowTheGroup() {
        Fixture fixture = Fixture.create();
        for (int i = 0; i < 4; i++) {
            addClick(fixture.stream(), "life-gauge1", i);
        }
        ClickEventWriter failing = documents -> {
            throw new DataAccessResourceFailureException("MongoDB fora (simulado)");
        };
        MeterRegistry registry = new SimpleMeterRegistry();
        ClickStreamConsumer consumer = newConsumer(fixture, failing, Duration.ofHours(1),
                registry);
        try {
            consumer.start();

            await().atMost(WAIT).pollInterval(POLL).untilAsserted(() -> {
                assertThat(gauge(registry, "linkpulse.clicks.stream.pending"))
                        .as("pendentes do grupo").isEqualTo(4.0);
                assertThat(gauge(registry, "linkpulse.clicks.stream.length"))
                        .as("XLEN do stream").isEqualTo(4.0);
                assertThat(gauge(registry, "linkpulse.clicks.stream.lag"))
                        .as("lag do grupo").isEqualTo(0.0);
            });
        } finally {
            consumer.stop();
            cleanUp(fixture);
        }
    }

    @Test
    void lagGaugeIsNanWhenServerReportsNull() {
        assertThat(ClickStreamConsumer.lagValue(null)).isNaN();
        assertThat(ClickStreamConsumer.lagValue(7L)).isEqualTo(7.0);
        assertThat(ClickStreamConsumer.lagValue("x")).isNaN();
    }
}
