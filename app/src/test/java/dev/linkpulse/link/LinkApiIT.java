package dev.linkpulse.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.linkpulse.AbstractIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ITs do contrato de criação {@code POST /links} (D-09) e do redirect do link criado.
 */
class LinkApiIT extends AbstractIT {

    private static final String TARGET = "https://example.com/artigo?id=42";

    private MvcTestResult create(String json) {
        return mvc.post().uri("/links")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }

    private static Map<String, Object> body(MvcTestResult result) throws Exception {
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$");
    }

    @Test
    void createsLinkWithGeneratedCodeAndLocation() throws Exception {
        MvcTestResult result = create("{\"url\":\"" + TARGET + "\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        Map<String, Object> body = body(result);
        String code = (String) body.get("code");
        assertThat(code).matches("^[0-9A-Za-z]{7}$");
        assertThat(body.get("shortUrl")).isEqualTo("http://localhost:8080/" + code);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo(body.get("shortUrl"));
        assertThat(body.get("targetUrl")).isEqualTo(TARGET);
        assertThat((String) body.get("createdAt"))
                .matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");
        assertThat(body).containsEntry("expiresAt", null);
    }

    @Test
    void createdLinkRedirectsImmediately() throws Exception {
        MvcTestResult created = create("{\"url\":\"" + TARGET + "\"}");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String code = (String) body(created).get("code");

        MvcTestResult redirect = mvc.get().uri("/" + code).exchange();

        assertThat(redirect).hasStatus(HttpStatus.FOUND);
        assertThat(redirect.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(TARGET);
    }

    @Test
    void sameUrlTwiceCreatesIndependentLinks() throws Exception {
        String json = "{\"url\":\"https://example.com/repetida\"}";

        MvcTestResult first = create(json);
        MvcTestResult second = create(json);

        assertThat(first).hasStatus(HttpStatus.CREATED);
        assertThat(second).hasStatus(HttpStatus.CREATED);
        assertThat(body(first).get("code")).isNotEqualTo(body(second).get("code"));
    }

    @Test
    void expiresAtWithOffsetIsReturnedInUtcTruncatedToMicros() throws Exception {
        MvcTestResult result = create("{\"url\":\"https://example.com/expira\","
                + "\"expiresAt\":\"2099-12-31T23:59:59.123456789-03:00\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(body(result).get("expiresAt")).isEqualTo("2100-01-01T02:59:59.123456Z");
    }
}
