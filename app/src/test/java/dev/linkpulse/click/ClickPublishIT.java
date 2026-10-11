package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.AbstractIT;
import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Durations;
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
 *
 * <p>Privacidade (CLICK-06, D-03): nenhum campo carrega o IP, nem path, query ou credenciais do
 * {@code Referer}. HEAD não conta como clique (REDIR-06).
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

    @Autowired
    private MeterRegistry registry;

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

    private double publishedCount() {
        return registry.counter("linkpulse.clicks.publish", "result", "published").count();
    }

    /**
     * Espera o contador {@code published} parar de mudar, para que publicações em voo de outros
     * testes não contaminem a comparação.
     *
     * @return o valor estável do contador
     */
    private double awaitStablePublishedCount() {
        AtomicReference<Double> last = new AtomicReference<>(publishedCount());
        await().atMost(WAIT)
                .pollDelay(Duration.ofMillis(300))
                .pollInterval(Duration.ofMillis(300))
                .until(() -> {
                    double now = publishedCount();
                    return now == last.getAndSet(now);
                });
        return last.get();
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

    @Test
    void headRequestDoesNotPublishClick() {
        saveLink("pub-head1", "https://example.com/head");
        double before = awaitStablePublishedCount();

        MvcTestResult result = mvc.head().uri("/pub-head1").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        await().pollDelay(Durations.ONE_SECOND).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    assertThat(findEntry("pub-head1")).isEmpty();
                    assertThat(publishedCount()).isEqualTo(before);
                });
    }

    @Test
    void refererIsReducedToHost() {
        saveLink("pub-ref1", "https://example.com/referer");

        MvcTestResult result = mvc.get().uri("/pub-ref1")
                .header(HttpHeaders.REFERER, "https://news.example.com/artigo?token=segredo")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        Map<Object, Object> entry = awaitEntry("pub-ref1");
        assertThat(entry.get("ref")).isEqualTo("news.example.com");
        assertThat(entry.values()).allSatisfy(value -> assertThat(String.valueOf(value))
                .doesNotContain("token")
                .doesNotContain("segredo"));
    }

    @Test
    void missingRefererIsDirect() {
        saveLink("pub-dir1", "https://example.com/direto");

        assertThat(mvc.get().uri("/pub-dir1").exchange()).hasStatus(HttpStatus.FOUND);

        Map<Object, Object> entry = awaitEntry("pub-dir1");
        assertThat(entry.get("ref")).isEqualTo("(direct)");
    }

    @Test
    void longUserAgentIsTruncated() {
        saveLink("pub-ua1", "https://example.com/ua");
        String userAgent = "U".repeat(600);

        MvcTestResult result = mvc.get().uri("/pub-ua1")
                .header(HttpHeaders.USER_AGENT, userAgent)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        Map<Object, Object> entry = awaitEntry("pub-ua1");
        assertThat(String.valueOf(entry.get("ua"))).hasSize(512)
                .isEqualTo(userAgent.substring(0, 512));
    }
}
