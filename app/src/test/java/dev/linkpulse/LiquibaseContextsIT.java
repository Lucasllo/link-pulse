package dev.linkpulse;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Prova que os contexts do Liquibase separam o seed de demonstração (DATA-01).
 *
 * <p>A classe externa usa o default {@code contexts=prod} do {@code application.yml}. Cada classe
 * aninhada ativa um profile, o que gera um contexto Spring próprio e, com ele, um container
 * Postgres próprio. O {@code @ServiceConnection} tem precedência sobre a URL
 * {@code localhost:5432} do {@code application-dev.yml}.
 */
class LiquibaseContextsIT extends AbstractIT {

    private static final String SEED_CODE = "demo-link";
    private static final String SEED_CHANGESET = "002-seed-dev-demo-link";
    private static final String SEED_TARGET = "https://example.com/";

    @Autowired
    private LinkRepository links;

    @Autowired
    private JdbcTemplate jdbc;

    private static int changesetCount(JdbcTemplate jdbc) {
        Integer count = jdbc.queryForObject(
                "select count(*) from databasechangelog where id = ?",
                Integer.class,
                SEED_CHANGESET);
        return count == null ? 0 : count;
    }

    @Test
    void defaultContextHasNoSeedLink() {
        assertThat(links.findByCode(SEED_CODE)).isEmpty();
    }

    @Test
    void defaultContextDoesNotRunSeedChangeset() {
        assertThat(changesetCount(jdbc)).isZero();
    }

    @Nested
    @ActiveProfiles("prod")
    class ProdProfile {

        @Test
        void prodProfileHasNoSeedLink() {
            assertThat(links.findByCode(SEED_CODE)).isEmpty();
        }

        @Test
        void prodProfileDoesNotRunSeedChangeset() {
            assertThat(changesetCount(jdbc)).isZero();
        }
    }

    @Nested
    @ActiveProfiles("dev")
    class DevProfile {

        @Test
        void devProfileSeedsDemoLink() {
            Optional<Link> demo = links.findByCode(SEED_CODE);

            assertThat(demo).isPresent();
            assertThat(demo.get().getTargetUrl()).isEqualTo(SEED_TARGET);
        }

        @Test
        void devProfileRegistersSeedChangeset() {
            assertThat(changesetCount(jdbc)).isEqualTo(1);
        }

        @Test
        void demoLinkRedirects() {
            MvcTestResult result = mvc.get().uri("/" + SEED_CODE).exchange();

            assertThat(result).hasStatus(HttpStatus.FOUND);
            assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(SEED_TARGET);
        }
    }
}
