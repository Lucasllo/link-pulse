package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Testes unitários do {@link ClickDocument#fromStream} (CLICK-02, CLICK-07): entradas inválidas
 * viram vazio em vez de lançar, e as válidas trazem navegador, SO e {@code isBot} vindos do
 * {@link UserAgentClassifier}. Rodam sem Docker.
 */
class ClickDocumentTest {

    private static final String ID = "1760000000000-0";
    private static final String FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";

    private static Map<String, String> valid() {
        Map<String, String> fields = new HashMap<>();
        fields.put("code", "abc_123-X");
        fields.put("ts", "1760000000000");
        fields.put("ref", "(direct)");
        fields.put("ua", FIREFOX);
        fields.put("iph", "0123456789abcdef".repeat(4));
        return fields;
    }

    private static Map<String, String> with(String field, String value) {
        Map<String, String> fields = valid();
        if (value == null) {
            fields.remove(field);
        } else {
            fields.put(field, value);
        }
        return fields;
    }

    static Stream<Arguments> invalidEntries() {
        return Stream.of(
                Arguments.of("sem code", with("code", null)),
                Arguments.of("code com barra", with("code", "a/b")),
                Arguments.of("ts não numérico", with("ts", "abc")),
                Arguments.of("ts zero", with("ts", "0")),
                Arguments.of("iph com 63 caracteres", with("iph", "a".repeat(63))),
                Arguments.of("iph com maiúsculas", with("iph", "A".repeat(64))),
                Arguments.of("sem ref", with("ref", null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEntries")
    void invalidEntryBecomesEmpty(String description, Map<String, String> fields) {
        assertThat(ClickDocument.fromStream(ID, fields)).isEmpty();
    }

    @Test
    void validEntryCarriesBrowserOsAndBotFlag() {
        Optional<ClickDocument> document = ClickDocument.fromStream(ID, valid());

        assertThat(document).contains(new ClickDocument(ID, "abc_123-X",
                Instant.ofEpochMilli(1_760_000_000_000L), "(direct)", "Firefox", "Linux", false,
                "0123456789abcdef".repeat(4)));
    }

    @Test
    void missingUserAgentIsTreatedAsEmptyAndCountsAsBot() {
        ClickDocument document = ClickDocument.fromStream(ID, with("ua", null)).orElseThrow();

        assertThat(document.browser()).isEqualTo(UserAgentClassifier.OTHER);
        assertThat(document.os()).isEqualTo(UserAgentClassifier.OTHER);
        assertThat(document.bot()).isTrue();
    }

    @Test
    void commandLineClientCountsAsBot() {
        ClickDocument document =
                ClickDocument.fromStream(ID, with("ua", "curl/8.5.0")).orElseThrow();

        assertThat(document.bot()).isTrue();
    }
}
