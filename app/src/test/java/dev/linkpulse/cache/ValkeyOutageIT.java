package dev.linkpulse.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.AbstractIT;
import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.GenericContainer;

/**
 * ITs do fail-open do redirect com o Valkey pausado ({@code docker pause}).
 *
 * <p>A pausa simula o pior caso, "conectado, mas sem resposta": a conexão TCP continua aberta e
 * cada comando só falha pelo timeout do Lettuce. Pausar, em vez de parar, mantém a porta mapeada
 * do container (Pitfall P6). O {@code unpause} fica sempre no {@code finally}, seguido da espera
 * do gate, para o contexto compartilhado não seguir em bypass nos ITs seguintes.
 */
class ValkeyOutageIT extends AbstractIT {

    private static final long MAX_REDIRECT_MILLIS = 1000;
    private static final String CACHE_REQUESTS = "linkpulse.cache.requests";
    private static final String UNAVAILABLE = "linkpulse.valkey.unavailable";

    @Autowired
    @Qualifier("valkey")
    private GenericContainer<?> valkey;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private ValkeyAvailability availability;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private LinkRepository links;

    private void saveLink(String code, String target) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        links.saveAndFlush(Link.create(links.nextId(), code, target, null, now));
    }

    private double count(String name, String result) {
        Counter counter = result == null
                ? registry.find(name).counter()
                : registry.find(name).tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private void pauseValkey() {
        valkey.getDockerClient().pauseContainerCmd(valkey.getContainerId()).exec();
    }

    private void unpauseValkeyAndWaitForGate() {
        valkey.getDockerClient().unpauseContainerCmd(valkey.getContainerId()).exec();
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
                .until(() -> availability.allowRequest() && valkeyAnswers());
    }

    private boolean valkeyAnswers() {
        try {
            redis.opsForValue().get("lp:probe");
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Test
    void redirectKeepsWorkingFastWhileValkeyIsPaused() {
        String target = "https://example.com/outage";
        saveLink("outage-1", target);
        final double errorsBefore = count(CACHE_REQUESTS, "error");
        final double bypassBefore = count(CACHE_REQUESTS, "bypass");
        final double unavailableBefore = count(UNAVAILABLE, null);
        List<Long> elapsedMillis = new ArrayList<>();

        pauseValkey();
        try {
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                MvcTestResult result = mvc.get().uri("/outage-1").exchange();
                long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
                elapsedMillis.add(elapsed);

                assertThat(result).hasStatus(HttpStatus.FOUND);
                assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(target);
                assertThat(elapsed).isLessThan(MAX_REDIRECT_MILLIS);
            }
        } finally {
            unpauseValkeyAndWaitForGate();
        }
        System.out.println("ValkeyOutageIT: tempos dos GETs com o Valkey pausado (ms) = "
                + elapsedMillis);

        double errors = count(CACHE_REQUESTS, "error") - errorsBefore;
        double bypass = count(CACHE_REQUESTS, "bypass") - bypassBefore;
        assertThat(errors).isGreaterThanOrEqualTo(1.0);
        assertThat(errors + bypass).isGreaterThanOrEqualTo(5.0);
        assertThat(count(UNAVAILABLE, null) - unavailableBefore).isGreaterThanOrEqualTo(1.0);

        double hitOrMissBefore = count(CACHE_REQUESTS, "hit") + count(CACHE_REQUESTS, "miss");
        assertThat(mvc.get().uri("/outage-1").exchange()).hasStatus(HttpStatus.FOUND);
        double hitOrMiss = count(CACHE_REQUESTS, "hit") + count(CACHE_REQUESTS, "miss");
        assertThat(hitOrMiss - hitOrMissBefore).isEqualTo(1.0);
    }
}
