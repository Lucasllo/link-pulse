package dev.linkpulse.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
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

    @Test
    void urlOutsideTheAllowListIsValidationErrorOnUrl() throws Exception {
        MvcTestResult result = create("{\"url\":\"ftp://example.com\"}");

        assertValidationErrorOn(result, "url");
    }

    @Test
    void urlLongerThan2048CharactersIsValidationErrorOnUrl() throws Exception {
        MvcTestResult result = create("{\"url\":\"" + urlOfLength(2049) + "\"}");

        assertValidationErrorOn(result, "url");
    }

    @Test
    void urlWithExactly2048CharactersIsAccepted() throws Exception {
        String url = urlOfLength(2048);

        MvcTestResult result = create("{\"url\":\"" + url + "\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(body(result).get("targetUrl")).isEqualTo(url);
    }

    @Test
    void urlPointingToTheShortenerItselfIsValidationErrorOnUrl() throws Exception {
        MvcTestResult result = create("{\"url\":\"http://LOCALHOST:8080/qualquer\"}");

        assertValidationErrorOn(result, "url");
    }

    @Test
    void trailingDotOnTheShortenerHostIsRejectedAndNoLoopIsCreated() throws Exception {
        MvcTestResult result = create(
                "{\"url\":\"http://localhost.:8080/loop-a\",\"alias\":\"loop-a\"}");

        assertValidationErrorOn(result, "url");
        String raw = result.getResponse().getContentAsString();
        assertThat((String) JsonPath.read(raw, "$.errors[0].message"))
                .isEqualTo("não pode apontar para o próprio encurtador");

        MvcTestResult redirect = mvc.get().uri("/loop-a").exchange();
        assertProblem(redirect, HttpStatus.NOT_FOUND, "/problems/link-not-found");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1:8080/x",
        "http://[::1]:8080/x",
        "http://0.0.0.0:8080/x",
        "http://app.localhost:8080/x",
        "http://0x7f000001:8080/x",
        "http://2130706433:8080/x",
        "http://0177.0.0.1:8080/x"
    })
    void loopbackOrObfuscatedHostIsValidationErrorOnUrl(String url) throws Exception {
        MvcTestResult result = create("{\"url\":\"" + url + "\"}");

        assertValidationErrorOn(result, "url");
    }

    @Test
    void publicIpv4LiteralIsAccepted() throws Exception {
        MvcTestResult result = create("{\"url\":\"http://93.184.215.14/artigo\"}");

        assertThat(result).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void expiresAtInThePastIsValidationErrorOnExpiresAt() throws Exception {
        MvcTestResult result = create("{\"url\":\"https://example.com/passado\","
                + "\"expiresAt\":\"2020-01-01T00:00:00Z\"}");

        assertValidationErrorOn(result, "expiresAt");
    }

    @Test
    void expiresAtWithoutOffsetIsMalformedRequest() throws Exception {
        MvcTestResult result = create("{\"url\":\"https://example.com/sem-offset\","
                + "\"expiresAt\":\"2030-01-01T00:00:00\"}");

        assertProblem(result, HttpStatus.BAD_REQUEST, "/problems/malformed-request");
    }

    @Test
    void malformedJsonIsMalformedRequestWithoutParserDetails() throws Exception {
        MvcTestResult result = create("{\"url\":");

        assertProblem(result, HttpStatus.BAD_REQUEST, "/problems/malformed-request");
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("JSON parse error", "jackson", "line:", "column:");
    }

    /**
     * Falha inesperada no service. Contexto próprio, com o {@link LinkService} trocado por um
     * mock que lança uma exceção com cara de erro de banco.
     */
    @Nested
    class UnexpectedFailure {

        @MockitoBean
        private LinkService failingService;

        @Autowired
        private MockMvcTester nestedMvc;

        @Test
        void becomesGenericInternalErrorWithoutLeakingDetails() throws Exception {
            given(failingService.create(any()))
                    .willThrow(new RuntimeException("falha SQL uk_links_code"));

            MvcTestResult result = nestedMvc.post().uri("/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"url\":\"https://example.com/falha\"}")
                    .exchange();

            assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, "/problems/internal-error");
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("falha", "uk_links_code", "RuntimeException", "SQL");
        }
    }

    private static String urlOfLength(int length) {
        String prefix = "https://example.com/";
        return prefix + "a".repeat(length - prefix.length());
    }

    /**
     * Confere um 400 {@code validation-error} em Problem Details com um item de {@code errors[]}
     * no campo pedido.
     */
    private static void assertValidationErrorOn(MvcTestResult result, String field)
            throws Exception {
        assertProblem(result, HttpStatus.BAD_REQUEST, "/problems/validation-error");
        String raw = result.getResponse().getContentAsString();
        List<String> fields = JsonPath.read(raw, "$.errors[*].field");
        assertThat(fields).contains(field);
    }

    /**
     * Confere o envelope de Problem Details: content type, {@code type}, {@code title},
     * {@code status} e nenhum nome de classe de exceção no corpo.
     */
    private static void assertProblem(MvcTestResult result, HttpStatus status, String type)
            throws Exception {
        assertThat(result).hasStatus(status);
        assertThat(result.getResponse().getContentType()).startsWith(PROBLEM_JSON);
        Map<String, Object> body = body(result);
        assertThat(body.get("type")).isEqualTo(type);
        assertThat(body.get("title")).isNotNull();
        assertThat(body.get("status")).isEqualTo(status.value());
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Exception");
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
