package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.AbstractIT;
import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ITs da publicação de cliques no stream {@code lp:clicks} contra PostgreSQL e Valkey reais.
 *
 * <p>O stream é compartilhado entre todos os ITs, então cada teste usa um código próprio e
 * procura só as entradas dele.
 */
class ClickPublishIT extends AbstractIT {

    private static final String STREAM = "lp:clicks";
    private static final String CLIENT_IP = "198.51.100.7";
    private static final String FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";
    private static final Duration WAIT = Duration.ofSeconds(10);

    @Autowired
    private LinkRepository links;

    @Autowired
    private StringRedisTemplate redis;

    private Link saveLink(String code, String target) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return links.saveAndFlush(Link.create(links.nextId(), code, target, null, now));
    }

    /**
     * Procura a entrada do stream com o código informado.
     *
     * @param code código curto
     * @return os campos da primeira entrada com {@code code} igual, se houver
     */
    private Optional<Map<Object, Object>> findEntry(String code) {
        List<MapRecord<String, Object, Object>> entries =
                redis.opsForStream().range(STREAM, Range.unbounded());
        if (entries == null) {
            return Optional.empty();
        }
        return entries.stream()
                .map(MapRecord::getValue)
                .filter(fields -> code.equals(fields.get("code")))
                .findFirst();
    }

    private Map<Object, Object> awaitEntry(String code) {
        AtomicReference<Map<Object, Object>> found = new AtomicReference<>();
        await().atMost(WAIT).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            Optional<Map<Object, Object>> entry = findEntry(code);
            assertThat(entry).as("entrada de %s no stream %s", code, STREAM).isPresent();
            found.set(entry.get());
        });
        return found.get();
    }

    @Test
    void redirectPublishesClickWithHashedIp() {
        saveLink("pub-ok1", "https://example.com/publica");

        long before = System.currentTimeMillis();
        MvcTestResult result = mvc.get().uri("/pub-ok1")
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                })
                .header(HttpHeaders.USER_AGENT, FIREFOX)
                .exchange();
        long after = System.currentTimeMillis();

        assertThat(result).hasStatus(HttpStatus.FOUND);

        Map<Object, Object> entry = awaitEntry("pub-ok1");
        assertThat(Long.parseLong(String.valueOf(entry.get("ts")))).isBetween(before, after);
        assertThat(String.valueOf(entry.get("iph"))).matches("^[0-9a-f]{64}$");
        assertThat(entry.get("ua")).isEqualTo(FIREFOX);
        assertThat(entry.values())
                .allSatisfy(value -> assertThat(String.valueOf(value)).doesNotContain(CLIENT_IP));
    }
}
