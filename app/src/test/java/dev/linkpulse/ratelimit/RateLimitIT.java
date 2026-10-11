package dev.linkpulse.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.linkpulse.TestcontainersConfiguration;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * ITs do rate limit do {@code POST /links} (RATE-01, D-08) com Tomcat real.
 *
 * <p>Contexto próprio com {@code RANDOM_PORT}, limite 3 e janela de 1 h (o teste não atravessa a
 * virada da janela). Os requests saem por {@link HttpClient} em {@code 127.0.0.1}, nunca
 * {@code localhost}, que pode ir por {@code ::1} e mudar o IP do cliente. O MockMvc não serve
 * aqui: ele não passa pelo {@code RemoteIpValve} do Tomcat (Pitfall P8).
 *
 * <p>Este contexto não sobrescreve {@code server.tomcat.remoteip.internal-proxies}: vale o default
 * do projeto (vazio, nenhum proxy confiável). Todos os testes saem do mesmo IP, então o
 * {@code @BeforeEach} apaga as chaves {@code lp:rl:*}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "linkpulse.ratelimit.limit=3",
    "linkpulse.ratelimit.window=1h",
    "management.server.port=0",
    "linkpulse.ip-hash-key=test-ip-hash-key-0123456789",
    "linkpulse.clicks.consumer.block=200ms",
    "linkpulse.cache.bypass-cooldown=200ms"
})
@Import(TestcontainersConfiguration.class)
class RateLimitIT {

    private static final String HOST = "127.0.0.1";
    private static final String BODY = "{\"url\":\"https://example.com/rl\"}";
    private static final long WINDOW_SECONDS = 3600;

    @Autowired
    private Environment environment;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private LinkRepository links;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void clearRateLimitKeys() {
        Set<String> keys = redis.keys("lp:rl:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private URI uri(String path) {
        String port = environment.getProperty("local.server.port");
        assertThat(port).as("local.server.port").isNotBlank();
        return URI.create("http://" + HOST + ":" + port + path);
    }

    /**
     * {@code POST /links} com o corpo fixo.
     *
     * @param forwardedFor valor do {@code X-Forwarded-For}, ou {@code null} para não mandar
     */
    HttpResponse<String> post(String forwardedFor) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/links"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(BODY));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertRateLimited(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type ->
                assertThat(type).startsWith("application/problem+json"));
        assertThat((String) JsonPath.read(response.body(), "$.type"))
                .isEqualTo("/problems/rate-limited");
        String retryAfter = response.headers().firstValue("Retry-After").orElseThrow();
        long seconds = Long.parseLong(retryAfter);
        assertThat(seconds).isBetween(1L, WINDOW_SECONDS);
        assertThat(((Number) JsonPath.read(response.body(), "$.retryAfterSeconds")).longValue())
                .isEqualTo(seconds);
    }

    @Test
    void fourthCreationInTheWindowIsRateLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post(null).statusCode()).as("POST %d", i + 1).isEqualTo(201);
        }

        assertRateLimited(post(null));
    }

    @Test
    void redirectHeadAndStatsAreNeverRateLimited() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        links.saveAndFlush(Link.create(links.nextId(), "rl-free_1", "https://example.com/free",
                null, now));
        for (int i = 0; i < 3; i++) {
            assertThat(post(null).statusCode()).isEqualTo(201);
        }
        assertRateLimited(post(null));

        for (int i = 0; i < 5; i++) {
            assertThat(send("GET", "/rl-free_1").statusCode()).as("GET").isEqualTo(302);
            assertThat(send("HEAD", "/rl-free_1").statusCode()).as("HEAD").isEqualTo(302);
            assertThat(send("GET", "/links/rl-free_1/stats").statusCode()).as("stats")
                    .isEqualTo(200);
        }
    }
}
