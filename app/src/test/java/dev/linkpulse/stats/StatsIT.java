package dev.linkpulse.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import dev.linkpulse.AbstractIT;
import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ITs do {@code GET /links/{code}/stats} (STAT-01 a STAT-04, D-01 a D-04).
 *
 * <p>A coleção {@code click_events} é compartilhada por todos os ITs do contexto, então cada
 * teste usa um código próprio ({@code stats-*}) e só consulta as estatísticas dele.
 */
class StatsIT extends AbstractIT {

    private static final String CLIENT_IP = "198.51.100.21";
    private static final String FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private LinkRepository links;

    private Link saveLink(String code, Instant expiresAt) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return links.saveAndFlush(Link.create(links.nextId(), code,
                "https://example.com/" + code, expiresAt, now));
    }

    private void click(String code, String userAgent) {
        MvcTestResult result = mvc.get().uri("/" + code)
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                })
                .header(HttpHeaders.USER_AGENT, userAgent)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FOUND);
    }

    private MvcTestResult stats(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void statsCountRedirectsOfTheLink() {
        saveLink("stats-e2e1", null);
        for (int i = 0; i < 3; i++) {
            click("stats-e2e1", FIREFOX);
        }

        await().atMost(WAIT).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            MvcTestResult result = stats("/links/stats-e2e1/stats");
            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(((Number) JsonPath.read(body(result), "$.total")).longValue())
                    .isEqualTo(3);
        });

        String raw = body(stats("/links/stats-e2e1/stats"));
        assertThat((String) JsonPath.read(raw, "$.code")).isEqualTo("stats-e2e1");
        assertThat((Boolean) JsonPath.read(raw, "$.includeBots")).isFalse();
        LocalDate to = LocalDate.parse(JsonPath.read(raw, "$.to"));
        LocalDate from = LocalDate.parse(JsonPath.read(raw, "$.from"));
        assertThat(from).isEqualTo(to.minusDays(29));
    }
}
