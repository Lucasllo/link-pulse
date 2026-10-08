package dev.linkpulse.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.linkpulse.AbstractIT;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.ErrorResponseException;

/**
 * ITs do contrato de criação {@code POST /links} (D-09), do alias customizado (D-05 a D-08) e do
 * redirect do link criado.
 *
 * <p>Os cenários de {@code minha-promo} rodam em ordem (criação, repetição, variação de caixa);
 * os que dependem de o alias já existir o garantem por conta própria, para rodarem isolados.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LinkApiIT extends AbstractIT {

    private static final String TARGET = "https://example.com/artigo?id=42";
    private static final String PROMO_TARGET = "https://example.com/promo";
    private static final String PROMO_JSON =
            "{\"url\":\"" + PROMO_TARGET + "\",\"alias\":\"minha-promo\"}";
    private static final String PROBLEM_JSON = "application/problem+json";

    @Autowired
    private LinkService linkService;

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

    @Test
    @Order(1)
    void aliasBecomesTheCodeAndRedirects() throws Exception {
        MvcTestResult result = create(PROMO_JSON);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(body(result).get("code")).isEqualTo("minha-promo");
        assertThat(body(result).get("shortUrl")).isEqualTo("http://localhost:8080/minha-promo");

        MvcTestResult redirect = mvc.get().uri("/minha-promo").exchange();
        assertThat(redirect).hasStatus(HttpStatus.FOUND);
        assertThat(redirect.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(PROMO_TARGET);
    }

    @Test
    @Order(2)
    void repeatedAliasIsConflictWithoutInternalDetails() throws Exception {
        create(PROMO_JSON);

        MvcTestResult result = create(PROMO_JSON);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result.getResponse().getContentType()).startsWith(PROBLEM_JSON);
        Map<String, Object> body = body(result);
        assertThat(body.get("type")).isEqualTo("/problems/alias-conflict");
        assertThat(body.get("alias")).isEqualTo("minha-promo");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("uk_links_code", "SQL", "Exception");
    }

    @Test
    @Order(3)
    void aliasIsCaseSensitive() throws Exception {
        create(PROMO_JSON);
        String otherTarget = "https://example.com/outra-promo";

        MvcTestResult upper = create(
                "{\"url\":\"" + otherTarget + "\",\"alias\":\"Minha-Promo\"}");

        assertThat(upper).hasStatus(HttpStatus.CREATED);
        assertThat(body(upper).get("code")).isEqualTo("Minha-Promo");
        assertThat(mvc.get().uri("/Minha-Promo").exchange().getResponse()
                .getHeader(HttpHeaders.LOCATION)).isEqualTo(otherTarget);
        assertThat(mvc.get().uri("/minha-promo").exchange().getResponse()
                .getHeader(HttpHeaders.LOCATION)).isEqualTo(PROMO_TARGET);
    }

    @Test
    void concurrentCreationsOfTheSameAliasYieldOneSuccessAndConflicts() throws Exception {
        final int threads = 10;
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    try {
                        start.await();
                        linkService.create(new CreateLinkRequest(
                                "https://example.com/corrida", "corrida-alias", null));
                        successes.incrementAndGet();
                    } catch (ErrorResponseException e) {
                        if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                            conflicts.incrementAndGet();
                        } else {
                            unexpected.add(e);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        unexpected.add(e);
                    } catch (RuntimeException e) {
                        unexpected.add(e);
                    }
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(unexpected).isEmpty();
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
    }

    @Test
    void reservedAliasIsValidationErrorIgnoringCase() throws Exception {
        assertAliasValidationError("Swagger-UI");
    }

    @Test
    void aliasWithoutHyphenOrUnderscoreIsValidationError() throws Exception {
        assertAliasValidationError("abcd");
    }

    private void assertAliasValidationError(String alias) throws Exception {
        MvcTestResult result = create(
                "{\"url\":\"https://example.com/x\",\"alias\":\"" + alias + "\"}");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result.getResponse().getContentType()).startsWith(PROBLEM_JSON);
        String raw = result.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(raw, "$.type")).isEqualTo("/problems/validation-error");
        assertThat((String) JsonPath.read(raw, "$.errors[0].field")).isEqualTo("alias");
        assertThat(raw).doesNotContain("Exception");
    }
}
