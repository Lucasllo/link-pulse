package dev.linkpulse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ITs da documentação da API: {@code /v3/api-docs} (springdoc) e Swagger UI (QUAL-04).
 */
class OpenApiIT extends AbstractIT {

    private MvcTestResult apiDocs() {
        return mvc.get().uri("/v3/api-docs").exchange();
    }

    @Test
    void apiDocsHasTitle() {
        MvcTestResult result = apiDocs();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.info.title").isEqualTo("Link Pulse API");
        assertThat(result).bodyJson().extractingPath("$.info.version").isEqualTo("0.1.0");
    }

    @Test
    void createLinkDocumentsSuccessAndErrors() {
        MvcTestResult result = apiDocs();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.paths['/links'].post.responses")
                .asMap()
                .containsKeys("201", "400", "409");
        assertThat(result).bodyJson()
                .extractingPath("$.paths['/links'].post.responses['409'].content")
                .asMap()
                .containsKey("application/problem+json");
    }

    @Test
    void redirectDocumentsSuccessAndErrors() {
        MvcTestResult result = apiDocs();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.paths['/{code}'].get.responses")
                .asMap()
                .containsKeys("302", "404", "410");
        assertThat(result).bodyJson()
                .extractingPath("$.paths['/{code}'].get.responses['410'].content")
                .asMap()
                .containsKey("application/problem+json");
    }

    @Test
    void apiDocsDoesNotExposeManagementEndpoints() {
        MvcTestResult result = apiDocs();

        assertThat(result).bodyJson().extractingPath("$.paths")
                .asMap()
                .satisfies(paths -> assertThat(paths.keySet())
                        .noneMatch(path -> path.startsWith("/actuator")));
    }

    @Test
    void swaggerUiIndexIsServed() {
        MvcTestResult result = mvc.get().uri("/swagger-ui/index.html").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    void swaggerUiShortcutIsServedOrRedirected() {
        MvcTestResult result = mvc.get().uri("/swagger-ui.html").exchange();

        assertThat(result.getResponse().getStatus()).isBetween(200, 399);
    }
}
