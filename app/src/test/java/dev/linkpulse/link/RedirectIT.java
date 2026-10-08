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
}
