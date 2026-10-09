package dev.linkpulse.link;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linkpulse.AbstractIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ITs do redirect {@code GET /{code}} contra PostgreSQL real.
 */
class RedirectIT extends AbstractIT {

    @Autowired
    private LinkRepository links;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment environment;

    private Link saveLink(String code, String target, Instant expiresAt) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return links.saveAndFlush(Link.create(links.nextId(), code, target, expiresAt, now));
    }

    @Test
    void redirectsToTargetWithoutCache() {
        String target = "https://example.com/destino?x=1";
        saveLink("redir-ok1", target, null);

        MvcTestResult result = mvc.get().uri("/redir-ok1").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(target);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .contains("no-store")
                .contains("private");
    }

    @Test
    void schemaComesFromLiquibaseChangelog() {
        List<String> ids = jdbc.queryForList("select id from databasechangelog", String.class);

        assertThat(ids).contains("001-create-link-id-seq", "001-create-links-table");
    }

    @Test
    void hibernateOnlyValidatesSchema() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @Test
    void unknownCodeIsNotFoundProblem() {
        MvcTestResult result = mvc.get().uri("/nao-existe1").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("/problems/link-not-found");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Link não encontrado");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("nao-existe1");
    }

    @Test
    void expiredLinkIsGoneProblem() {
        Instant expiresAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        saveLink("expired-1", "https://example.com/velho", expiresAt);

        MvcTestResult result = mvc.get().uri("/expired-1").exchange();

        assertThat(result).hasStatus(HttpStatus.GONE)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo("/problems/link-expired");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("expired-1");
        assertThat(result).bodyJson().extractingPath("$.expiredAt").isEqualTo(expiresAt.toString());
    }

    @Test
    void linkExpiringInTheFutureStillRedirects() {
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        saveLink("future-1", "https://example.com/futuro", expiresAt);

        MvcTestResult result = mvc.get().uri("/future-1").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("https://example.com/futuro");
    }

    @Test
    void lookupIsCaseSensitive() {
        saveLink("AbCd-123", "https://example.com/caso", null);

        assertThat(mvc.get().uri("/AbCd-123").exchange()).hasStatus(HttpStatus.FOUND);
        MvcTestResult lower = mvc.get().uri("/abcd-123").exchange();
        assertThat(lower).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(lower).bodyJson().extractingPath("$.code").isEqualTo("abcd-123");
    }

    @Test
    void pathWithDotIsNotRedirectRoute() throws Exception {
        MvcTestResult result = mvc.get().uri("/favicon.ico").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("link-not-found");
    }

    @Test
    void multiSegmentPathIsNotRedirectRoute() throws Exception {
        MvcTestResult result = mvc.get().uri("/a/b").exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("link-not-found");
    }
}
