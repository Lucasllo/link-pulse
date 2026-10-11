package dev.linkpulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * IT do {@code /actuator/prometheus} real (OBS-01, D-10): o catálogo de métricas da fase aparece
 * no scrape e nenhuma métrica da aplicação carrega valor vindo do cliente.
 *
 * <p>Contexto próprio com {@code RANDOM_PORT} e {@code management.server.port=0}: a API e a
 * porta de gerenciamento sobem de verdade, em portas aleatórias lidas de
 * {@code local.server.port} e {@code local.management.port}. Os requests saem por
 * {@link HttpClient} sem seguir redirect, sempre em {@code 127.0.0.1}. O link é salvo pelo
 * {@link LinkRepository}, sem {@code POST /links}, para o teste não depender do rate limit.
 *
 * <p>Regra de cardinalidade: nenhuma linha {@code linkpulse_*} ou {@code http_server_requests*}
 * contém código curto, URL de destino, host ou query do Referer, user-agent ou o IP do cliente,
 * e as chaves de tag das métricas {@code linkpulse_*} ficam em {@code result}, {@code operation}
 * e {@code outcome}. Métricas de infraestrutura (JVM, Hikari, Lettuce) ficam fora: as tags delas
 * descrevem o servidor, não o cliente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0",
    "linkpulse.ip-hash-key=test-ip-hash-key-0123456789",
    "linkpulse.clicks.consumer.block=200ms",
    "linkpulse.cache.bypass-cooldown=200ms"
})
@Import(TestcontainersConfiguration.class)
class MetricsCardinalityIT {

    private static final String CODE = "card-test_1";
    private static final String MISSING = "nao-existe_9";
    private static final String TARGET = "https://example.com/cardinality";
    private static final String FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";
    private static final String REFERER = "https://ref.example/path?q=segredo";
    private static final String HOST = "127.0.0.1";
    private static final Set<String> ALLOWED_TAG_KEYS = Set.of("result", "operation", "outcome");
    private static final List<String> CATALOG = List.of(
            "linkpulse_cache_requests_total",
            "linkpulse_clicks_publish_total",
            "linkpulse_clicks_persisted_total",
            "linkpulse_clicks_stream_pending",
            "linkpulse_clicks_stream_lag",
            "linkpulse_clicks_stream_length",
            "linkpulse_valkey_unavailable_total",
            "http_server_requests_seconds_bucket");
    private static final List<String> CLIENT_VALUES = List.of(CODE, MISSING,
            "example.com/cardinality", "ref.example", "segredo", "Firefox", HOST);
    private static final Pattern SAMPLE = Pattern.compile("^([a-z_]+)\\{(.*)\\}\\s");
    private static final Pattern LABEL = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*)=\"");

    @Autowired
    private Environment environment;

    @Autowired
    private LinkRepository links;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private int port(String property) {
        String value = environment.getProperty(property);
        assertThat(value).as(property).isNotBlank();
        return Integer.parseInt(value);
    }

    private HttpResponse<String> get(int port, String path, boolean browser)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create("http://" + HOST + ":" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (browser) {
            request.header("User-Agent", FIREFOX).header("Referer", REFERER);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static boolean isApplicationLine(String line) {
        return line.startsWith("linkpulse_") || line.startsWith("http_server_requests");
    }

    /**
     * Chaves de tag de uma linha de amostra ({@code nome{k="v",...} valor}). As chaves são lidas
     * antes de cada {@code ="}; um {@code }} dentro de um valor, como {@code uri="/{code}"}, não
     * corta a lista.
     */
    private static Set<String> tagKeys(String line) {
        Set<String> keys = new TreeSet<>();
        Matcher sample = SAMPLE.matcher(line);
        if (!sample.find()) {
            return keys;
        }
        Matcher label = LABEL.matcher(sample.group(2));
        while (label.find()) {
            keys.add(label.group(1));
        }
        return keys;
    }

    @Test
    void prometheusExposesLowCardinalityMetrics() throws Exception {
        int serverPort = port("local.server.port");
        final int managementPort = port("local.management.port");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        links.saveAndFlush(Link.create(links.nextId(), CODE, TARGET, null, now));

        for (int i = 0; i < 2; i++) {
            HttpResponse<String> redirect = get(serverPort, "/" + CODE, true);
            assertThat(redirect.statusCode()).isEqualTo(302);
            assertThat(redirect.headers().firstValue("Location")).contains(TARGET);
        }
        assertThat(get(serverPort, "/" + MISSING, true).statusCode()).isEqualTo(404);

        AtomicReference<String> scrape = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    HttpResponse<String> response =
                            get(managementPort, "/actuator/prometheus", false);
                    assertThat(response.statusCode()).as("GET /actuator/prometheus")
                            .isEqualTo(200);
                    assertThat(response.body()).contains(CATALOG);
                    scrape.set(response.body());
                });

        List<String> applicationLines = scrape.get().lines()
                .filter(MetricsCardinalityIT::isApplicationLine)
                .toList();
        assertThat(applicationLines).isNotEmpty();
        assertThat(applicationLines).allSatisfy(line -> {
            for (String value : CLIENT_VALUES) {
                assertThat(line).as("valor do cliente numa métrica").doesNotContain(value);
            }
        });

        List<String> linkpulseLines = applicationLines.stream()
                .filter(line -> line.startsWith("linkpulse_"))
                .toList();
        assertThat(linkpulseLines).allSatisfy(line ->
                assertThat(tagKeys(line)).as("chaves de tag de %s", line)
                        .isSubsetOf(ALLOWED_TAG_KEYS));
        assertThat(linkpulseLines).as("linhas linkpulse_ com tags")
                .anySatisfy(line -> assertThat(tagKeys(line)).contains("result"));
    }
}
