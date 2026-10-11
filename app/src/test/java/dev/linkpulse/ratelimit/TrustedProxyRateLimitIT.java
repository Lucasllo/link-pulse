package dev.linkpulse.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.linkpulse.TestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * ITs do IP real atrás de um proxy confiável (RATE-02, D-08) com Tomcat real.
 *
 * <p>Mesmo contexto do {@code RateLimitIT} (limite 3, janela de 1 h), mais
 * {@code server.tomcat.remoteip.internal-proxies=127.0.0.1/32}: o cliente HTTP do teste, em
 * {@code 127.0.0.1}, faz o papel do proxy confiável (o Traefik no kind). O {@code RemoteIpValve}
 * troca o endereço remoto pelo hop não confiável mais à direita do {@code X-Forwarded-For}, então
 * entradas forjadas à esquerda da cadeia não mudam o cliente contado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "linkpulse.ratelimit.limit=3",
    "linkpulse.ratelimit.window=1h",
    "management.server.port=0",
    "linkpulse.ip-hash-key=test-ip-hash-key-0123456789",
    "linkpulse.clicks.consumer.block=200ms",
    "linkpulse.cache.bypass-cooldown=200ms",
    "server.tomcat.remoteip.internal-proxies=127.0.0.1/32"
})
@Import(TestcontainersConfiguration.class)
class TrustedProxyRateLimitIT {

    private static final String HOST = "127.0.0.1";
    private static final String BODY = "{\"url\":\"https://example.com/rl-proxy\"}";

    @Autowired
    private Environment environment;

    @Autowired
    private StringRedisTemplate redis;

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
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

    /**
     * {@code POST /links} saindo do "proxy" em 127.0.0.1 com o {@code X-Forwarded-For} dado.
     */
    private HttpResponse<String> post(String forwardedFor)
            throws IOException, InterruptedException {
        String port = environment.getProperty("local.server.port");
        assertThat(port).as("local.server.port").isNotBlank();
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://" + HOST + ":" + port + "/links"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString(BODY))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void assertRateLimited(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(response.headers().firstValue("Retry-After")).isPresent();
        assertThat((String) JsonPath.read(response.body(), "$.type"))
                .isEqualTo("/problems/rate-limited");
    }

    @Test
    void trustedProxyForwardsTheRealClientIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post("203.0.113.10").statusCode()).as("POST %d", i + 1).isEqualTo(201);
        }

        assertRateLimited(post("203.0.113.10"));
        assertThat(post("203.0.113.11").statusCode()).as("outro cliente").isEqualTo(201);
    }

    @Test
    void spoofedLeftmostEntriesAreIgnoredBehindTrustedProxy() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post("198.51.100.1, 203.0.113.20").statusCode()).as("POST %d", i + 1)
                    .isEqualTo(201);
        }

        // Só a entrada forjada à esquerda muda: o cliente real continua 203.0.113.20.
        assertRateLimited(post("198.51.100.99, 203.0.113.20"));
        // Controle: outro cliente real à direita da cadeia passa, então o valve está ativo.
        assertThat(post("198.51.100.1, 203.0.113.21").statusCode()).as("outro cliente")
                .isEqualTo(201);
    }

    /**
     * Contrato do Helm (Phase 4): a variável de ambiente no formato com {@code _} entre
     * {@code INTERNAL} e {@code PROXIES} chega à propriedade
     * {@code server.tomcat.remoteip.internal-proxies}.
     */
    @Test
    void internalProxiesBindsFromEnvironmentVariable() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES", "10.244.0.0/16")));
        ConfigurationPropertySources.attach(env);

        assertThat(Binder.get(env).bind("server.tomcat.remoteip.internal-proxies", String.class)
                .orElse(null)).isEqualTo("10.244.0.0/16");
    }
}
