package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.linkpulse.cache.ValkeyAvailability;
import dev.linkpulse.common.IpHasher;
import dev.linkpulse.config.LinkPulseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Testes unitários do {@link ClickPublisher} (CLICK-01, T-02-10, T-02-12): a publicação nunca
 * bloqueia nem lança. Fila cheia e publisher parado contam {@code rejected}; Valkey falhando ou
 * gate fechado contam {@code failed}. Rodam sem Docker, com o template do Redis em mock.
 */
class ClickPublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String IP_HASH_KEY = "test-ip-hash-key-0123456789";
    private static final String PUBLISH = "linkpulse.clicks.publish";
    private static final long MAX_PUBLISH_MILLIS = 50;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ValkeyAvailability availability =
            new ValkeyAvailability(Duration.ofSeconds(2), clock, registry);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final CountDownLatch release = new CountDownLatch(1);

    @SuppressWarnings("unchecked")
    private final StreamOperations<String, Object, Object> stream = mock(StreamOperations.class);

    private ClickPublisher publisher;

    @BeforeEach
    void setUp() {
        doReturn(stream).when(redis).opsForStream();
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        if (publisher != null) {
            publisher.stop();
        }
    }

    private ClickPublisher start(int threads, int queueCapacity) {
        LinkPulseProperties.Clicks clicks = new LinkPulseProperties.Clicks("lp:clicks", 100_000,
                new LinkPulseProperties.Clicks.PublisherSettings(threads, queueCapacity));
        publisher = new ClickPublisher(redis, availability, new IpHasher(IP_HASH_KEY), clicks,
                clock, registry);
        publisher.start();
        return publisher;
    }

    private double count(String result) {
        Counter counter = registry.find(PUBLISH).tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }

    /** Faz o {@code XADD} ficar preso até o {@code release}; {@code entered} marca a entrada. */
    @SuppressWarnings("unchecked")
    private CountDownLatch blockXadd() {
        CountDownLatch entered = new CountDownLatch(1);
        when(stream.add(any(MapRecord.class), any(XAddOptions.class))).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return RecordId.autoGenerate();
        });
        return entered;
    }

    private void publishOnce() {
        publisher.publish("abc1234", "203.0.113.7", "https://news.example.com/post", "curl/8");
    }

    @Test
    void fullQueueRejectsWithoutBlocking() throws InterruptedException {
        CountDownLatch entered = blockXadd();
        start(1, 1);

        for (int i = 0; i < 3; i++) {
            long begin = System.nanoTime();
            publishOnce();
            long elapsed = Duration.ofNanos(System.nanoTime() - begin).toMillis();
            assertThat(elapsed).isLessThan(MAX_PUBLISH_MILLIS);
        }

        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(count("rejected")).isEqualTo(1.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void valkeyFailureCountsFailedAndClosesTheGate() {
        when(stream.add(any(MapRecord.class), any(XAddOptions.class)))
                .thenThrow(new RedisConnectionFailureException("valkey fora"));
        start(1, 10);

        publishOnce();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(count("failed")).isEqualTo(1.0));
        assertThat(count("published")).isZero();
        assertThat(availability.allowRequest()).isFalse();
    }

    @Test
    void closedGateCountsFailedWithoutCallingValkey() {
        availability.recordFailure();
        start(1, 10);

        publishOnce();

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(count("failed")).isEqualTo(1.0));
        verify(redis, never()).opsForStream();
    }

    @Test
    void publishAfterStopIsRejectedWithoutSubmitting() {
        start(1, 10);
        publisher.stop();

        publishOnce();

        assertThat(count("rejected")).isEqualTo(1.0);
        verify(redis, never()).opsForStream();
    }

    @Test
    @SuppressWarnings("unchecked")
    void xaddTrimsTheStreamApproximatelyToMaxLen() {
        start(1, 10);
        ArgumentCaptor<XAddOptions> options = ArgumentCaptor.forClass(XAddOptions.class);

        publishOnce();

        verify(stream, timeout(5000)).add(any(MapRecord.class), options.capture());
        assertThat(options.getValue().getMaxlen()).isEqualTo(100_000L);
        assertThat(options.getValue().isApproximateTrimming()).isTrue();
    }

    @Test
    void queueGaugeReflectsPendingTasks() throws InterruptedException {
        CountDownLatch entered = blockXadd();
        start(1, 10);

        publishOnce();
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        publishOnce();

        Gauge queue = registry.find("linkpulse.clicks.publish.queue").gauge();
        assertThat(queue).isNotNull();
        assertThat(queue.value()).isEqualTo(1.0);
    }
}
