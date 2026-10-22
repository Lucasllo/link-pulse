package dev.linkpulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * IT dos grupos de health do Kubernetes (K8S-02, D-10) na porta de gerenciamento real.
 *
 * <p>A liveness só olha o estado interno da aplicação: Postgres, Valkey ou Mongo fora nunca
 * reiniciam o pod. A readiness olha Postgres ({@code db}) e Valkey ({@code redis}): sem eles o
 * pod sai do Service até as dependências voltarem. O Mongo fica fora da readiness de propósito:
 * só o consumer de cliques e o {@code /stats} dependem dele, e os cliques esperam no stream do
 * Valkey enquanto ele está fora.
 *
 * <p>Contexto próprio com {@code RANDOM_PORT} e {@code management.server.port=0}, como o
 * {@link MetricsCardinalityIT}: a porta de gerenciamento sobe de verdade e é lida de
 * {@code local.management.port}. O {@code show-components} por grupo só existe neste teste, para
 * a resposta listar os indicadores de cada grupo.
 *
 * <p>Valkey e Mongo são pausados ({@code docker pause}), o pior caso de "conectado, mas sem
 * resposta", com o {@code unpause} sempre no {@code finally}. O Postgres pausado fica fora daqui:
 * o health de {@code db} espera a conexão sem timeout próprio, e a queda dele é provada no kind.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0",
    "linkpulse.ip-hash-key=test-ip-hash-key-0123456789",
    "management.endpoint.health.group.liveness.show-components=always",
    "management.endpoint.health.group.readiness.show-components=always",
    "linkpulse.cache.bypass-cooldown=200ms",
    "linkpulse.clicks.consumer.block=200ms"
})
@Import(TestcontainersConfiguration.class)
class HealthGroupsIT {

    private static final String HOST = "127.0.0.1";
    private static final String LIVENESS = "/actuator/health/liveness";
    private static final String READINESS = "/actuator/health/readiness";
    private static final Duration OUTAGE_DETECTION = Duration.ofSeconds(5);
    private static final Duration RECOVERY = Duration.ofSeconds(30);
    private static final Pattern COMPONENT = Pattern.compile("\"([A-Za-z]+)\":\\{\"status\"");

    @Autowired
    private Environment environment;

    @Autowired
    @Qualifier("valkey")
    private GenericContainer<?> valkey;

    @Autowired
    private MongoDBContainer mongo;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private int managementPort() {
        String value = environment.getProperty("local.management.port");
        assertThat(value).as("local.management.port").isNotBlank();
        return Integer.parseInt(value);
    }

    private HttpResponse<String> health(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://" + HOST + ":" + managementPort() + path))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private int status(String path) {
        try {
            return health(path).statusCode();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** Nomes dos componentes da resposta ({@code "nome":{"status"...}}), sem o status do grupo. */
    private static Set<String> components(String body) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = COMPONENT.matcher(body);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private void waitUntilReady() {
        await().atMost(RECOVERY).pollInterval(Duration.ofMillis(200))
                .until(() -> status(READINESS) == 200);
    }

    @Test
    void livenessOnlyLooksAtTheInternalState() throws Exception {
        HttpResponse<String> response = health(LIVENESS);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(components(response.body())).containsExactly("livenessState");
    }

    @Test
    void readinessLooksAtPostgresAndValkeyButNotMongo() throws Exception {
        HttpResponse<String> response = health(READINESS);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(components(response.body()))
                .containsExactlyInAnyOrder("readinessState", "db", "redis")
                .doesNotContain("mongodb", "mongo");
    }

    @Test
    void valkeyDownMakesThePodNotReadyButKeepsItAlive() throws Exception {
        waitUntilReady();
        valkey.getDockerClient().pauseContainerCmd(valkey.getContainerId()).exec();
        try {
            await().atMost(OUTAGE_DETECTION).pollInterval(Duration.ofMillis(200))
                    .until(() -> status(READINESS) == 503);
            HttpResponse<String> readiness = health(READINESS);
            assertThat(readiness.statusCode()).isEqualTo(503);
            assertThat(readiness.body()).contains("\"status\":\"DOWN\"");

            HttpResponse<String> liveness = health(LIVENESS);
            assertThat(liveness.statusCode()).isEqualTo(200);
            assertThat(liveness.body()).contains("\"status\":\"UP\"");
        } finally {
            valkey.getDockerClient().unpauseContainerCmd(valkey.getContainerId()).exec();
            waitUntilReady();
        }
        assertThat(status(READINESS)).isEqualTo(200);
    }

    @Test
    void mongoDownKeepsThePodReadyAndAlive() throws Exception {
        waitUntilReady();
        mongo.getDockerClient().pauseContainerCmd(mongo.getContainerId()).exec();
        try {
            HttpResponse<String> readiness = health(READINESS);
            assertThat(readiness.statusCode()).isEqualTo(200);
            assertThat(readiness.body()).contains("\"status\":\"UP\"");

            HttpResponse<String> liveness = health(LIVENESS);
            assertThat(liveness.statusCode()).isEqualTo(200);
            assertThat(liveness.body()).contains("\"status\":\"UP\"");
        } finally {
            mongo.getDockerClient().unpauseContainerCmd(mongo.getContainerId()).exec();
            waitUntilReady();
        }
    }
}
