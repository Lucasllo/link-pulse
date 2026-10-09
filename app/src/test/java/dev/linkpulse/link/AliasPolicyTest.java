package dev.linkpulse.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Testes unitários da {@link AliasPolicy} (D-04, D-05, D-07). Rodam sem Docker.
 */
class AliasPolicyTest {

    private static final List<String> RESERVED = List.of(
            "links", "actuator", "swagger-ui", "v3", "api-docs", "health", "favicon.ico",
            "robots.txt", "admin", "api", "static", "login");

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final long MULTIPLIER = 2_176_477_521_915L;

    private static final String ALIAS_32 = "a-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ALIAS_33 = "a-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private final AliasPolicy policy = new AliasPolicy(RESERVED);

    @ParameterizedTest
    @ValueSource(strings = {"minha-promo", "black_friday", "ab-c", ALIAS_32})
    void acceptsValidAliases(String alias) {
        assertThat(policy.matchesFormat(alias)).isTrue();
        assertThat(policy.isReserved(alias)).isFalse();
        policy.check(alias);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "abcd", "a b-c", "ação-1", ALIAS_33, "abc.d-e", ""})
    void rejectsAliasesOutsideTheFormat(String alias) {
        assertThat(policy.matchesFormat(alias)).isFalse();
        assertInvalidAlias(alias);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Swagger-UI", "API-DOCS", "swagger-ui"})
    void rejectsReservedWordsIgnoringCase(String alias) {
        assertThat(policy.matchesFormat(alias)).isTrue();
        assertThat(policy.isReserved(alias)).isTrue();
        assertInvalidAlias(alias);
    }

    @Test
    void reservedListIsStoredInLowerCase() {
        AliasPolicy mixedCase = new AliasPolicy(List.of("Black-Friday"));

        assertThat(mixedCase.isReserved("black-friday")).isTrue();
        assertThat(mixedCase.isReserved("BLACK-FRIDAY")).isTrue();
    }

    @Test
    void checkReportsTheAliasFieldInErrors() {
        assertThatThrownBy(() -> policy.check("abcd"))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getBody().getType().toString())
                            .isEqualTo("/problems/validation-error");
                    Map<String, Object> properties = e.getBody().getProperties();
                    assertThat(properties).isNotNull().containsKey("errors");
                    List<?> errors = (List<?>) properties.get("errors");
                    assertThat(errors).isNotEmpty();
                    assertThat(((Map<?, ?>) errors.get(0)).get("field")).isEqualTo("alias");
                });
    }

    @Test
    void generatedCodesNeverMatchTheAliasRegex() {
        CodeGenerator generator = new CodeGenerator(ALPHABET, MULTIPLIER);
        Pattern alias = Pattern.compile(AliasPolicy.ALIAS_REGEX);
        for (long id = 1; id <= 200_000; id++) {
            assertNotAlias(alias, generator.encode(id));
        }
        for (long id = CodeGenerator.SPACE - 10_000; id < CodeGenerator.SPACE; id++) {
            assertNotAlias(alias, generator.encode(id));
        }
    }

    private static void assertNotAlias(Pattern alias, String code) {
        if (alias.matcher(code).matches()) {
            throw new AssertionError("código gerado casa com a regex de alias: " + code);
        }
    }

    private void assertInvalidAlias(String alias) {
        assertThatThrownBy(() -> policy.check(alias))
                .isInstanceOfSatisfying(ErrorResponseException.class, e ->
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
