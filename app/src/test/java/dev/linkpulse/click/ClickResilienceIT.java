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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * IT da queda do MongoDB com o consumer do contexto (CLICK-04, D-07).
 *
 * <p>O container do MongoDB é pausado ({@code docker pause}), e não parado, para manter a porta
 * mapeada e simular o pior caso, "conectado, mas sem resposta" (Pitfall P6). Durante a pausa o
 * redirect segue respondendo 302 (o clique só vai para o stream), a gravação do consumer falha
 * pelos timeouts do driver ({@code MongoConfig}) e o lote fica no PEL do grupo. Depois do
 * {@code unpause}, o reclaim assume os pendentes e os grava: exatamente um documento por clique,
 * sem perda e sem duplicata.
 */
class ClickResilienceIT extends AbstractIT {

    private static final String COLLECTION = "click_events";
    private static final String ERRORS = "linkpulse.clicks.consumer.errors";
    private static final String FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";
    private static final int CLICKS = 3;

    @Autowired
    @Qualifier("mongoContainer")
    private MongoDBContainer mongoContainer;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private LinkRepository links;

    private void saveLink(String code, String target) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        links.saveAndFlush(Link.create(links.nextId(), code, target, null, now));
    }

    private double consumerErrors() {
        return registry.counter(ERRORS).count();
    }

    private long documentsOf(String code) {
        return mongo.count(Query.query(Criteria.where("code").is(code)), COLLECTION);
    }

    private void pauseMongo() {
        mongoContainer.getDockerClient().pauseContainerCmd(mongoContainer.getContainerId()).exec();
    }

    private void unpauseMongo() {
        mongoContainer.getDockerClient().unpauseContainerCmd(mongoContainer.getContainerId())
                .exec();
    }

    @Test
    void consumerSurvivesMongoOutageAndCatchesUp() {
        saveLink("resil-1", "https://example.com/resilience");
        final double errorsBefore = consumerErrors();

        pauseMongo();
        long pausedAt = System.nanoTime();
        try {
            for (int i = 0; i < CLICKS; i++) {
                MvcTestResult result = mvc.get().uri("/resil-1")
                        .header(HttpHeaders.USER_AGENT, FIREFOX)
                        .exchange();
                assertThat(result).hasStatus(HttpStatus.FOUND);
            }
            await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200))
                    .untilAsserted(() -> assertThat(consumerErrors())
                            .as("erros do consumer com o MongoDB pausado")
                            .isGreaterThan(errorsBefore));
        } finally {
            unpauseMongo();
        }
        long unpausedAt = System.nanoTime();
        System.out.println("ClickResilienceIT: MongoDB pausado por (ms) = "
                + Duration.ofNanos(unpausedAt - pausedAt).toMillis());

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(documentsOf("resil-1"))
                        .as("documentos de resil-1 depois do unpause")
                        .isEqualTo(CLICKS));
        System.out.println("ClickResilienceIT: recuperação depois do unpause (ms) = "
                + Duration.ofNanos(System.nanoTime() - unpausedAt).toMillis());

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(documentsOf("resil-1"))
                        .as("nenhum clique duplicado depois da recuperação")
                        .isEqualTo(CLICKS));
    }
}
