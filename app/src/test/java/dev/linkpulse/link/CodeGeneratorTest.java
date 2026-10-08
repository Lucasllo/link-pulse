package dev.linkpulse.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.linkpulse.config.ApplicationConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Testes unitários do {@link CodeGenerator} (QUAL-01, D-02, D-03, D-04). Rodam sem Docker.
 */
class CodeGeneratorTest {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final long MULTIPLIER = 2_176_477_521_915L;
    private static final long SPACE = CodeGenerator.SPACE;
    private static final Pattern CODE = Pattern.compile("^[0-9A-Za-z]{7}$");

    private final CodeGenerator generator = new CodeGenerator(ALPHABET, MULTIPLIER);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationConfig.class)
            .withPropertyValues(
                    "linkpulse.base-url=http://localhost:8080",
                    "linkpulse.code.alphabet=" + ALPHABET);

    @Test
    void knownVectors() {
        assertThat(generator.encode(0)).isEqualTo("0000000");
        assertThat(generator.encode(1)).isEqualTo("cJinsNv");
        assertThat(generator.encode(2)).isEqualTo("EdRbklq");
        assertThat(generator.encode(3)).isEqualTo("qxAPd9l");
        assertThat(generator.encode(4)).isEqualTo("TGtDVXg");
        assertThat(generator.encode(5)).isEqualTo("5ac1Nvb");
    }

    @Test
    void roundTripOnLowAndHighRanges() {
        for (long id = 1; id <= 1_000_000; id++) {
            assertRoundTrip(id);
        }
        for (long id = SPACE - 100_000; id < SPACE; id++) {
            assertRoundTrip(id);
        }
    }

    private void assertRoundTrip(long id) {
        long decoded = generator.decode(generator.encode(id));
        if (decoded != id) {
            throw new AssertionError("decode(encode(" + id + ")) devolveu " + decoded);
        }
    }

    @Test
    void codesAreUniqueOnSampledRanges() {
        Set<String> codes = new HashSet<>();
        long expected = 0;
        for (long id = 1; id <= 200_000; id++) {
            codes.add(generator.encode(id));
            expected++;
        }
        for (long id = SPACE - 100_000; id < SPACE; id++) {
            codes.add(generator.encode(id));
            expected++;
        }

        assertThat(codes).hasSize((int) expected);
    }

    @Test
    void everyCodeHasSevenAlphanumericChars() {
        for (long id = 0; id <= 50_000; id++) {
            assertMatchesCode(generator.encode(id));
        }
        for (long id = SPACE - 50_000; id < SPACE; id++) {
            assertMatchesCode(generator.encode(id));
        }
    }

    private static void assertMatchesCode(String code) {
        if (code.length() != CodeGenerator.LENGTH || !CODE.matcher(code).matches()) {
            throw new AssertionError("código fora do formato: " + code);
        }
    }

    @Test
    void consecutiveIdsAreNotEnumerable() {
        for (long n = 1; n <= 10_000; n++) {
            String current = generator.encode(n).substring(0, 6);
            String next = generator.encode(n + 1).substring(0, 6);
            if (current.equals(next)) {
                throw new AssertionError("ids " + n + " e " + (n + 1) + " têm o mesmo prefixo");
            }
        }

        List<String> codes = new ArrayList<>();
        for (long id = 1; id <= 1000; id++) {
            codes.add(generator.encode(id));
        }
        assertThat(codes).isNotEqualTo(codes.stream().sorted().toList());
    }

    @Test
    void encodeRejectsIdsOutsideTheSpace() {
        assertThatIllegalArgumentException().isThrownBy(() -> generator.encode(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> generator.encode(SPACE));

        String last = generator.encode(SPACE - 1);
        assertThat(last).matches(CODE);
        assertThat(generator.decode(last)).isEqualTo(SPACE - 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "abcdefgh", "abc-def", ""})
    void decodeRejectsInvalidCodes(String code) {
        assertThatIllegalArgumentException().isThrownBy(() -> generator.decode(code));
    }

    @ParameterizedTest
    @ValueSource(longs = {62, 31, 2, 0, -1, SPACE})
    void constructorRejectsInvalidMultipliers(long multiplier) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CodeGenerator(ALPHABET, multiplier));
    }

    @Test
    void constructorRejectsInvalidAlphabets() {
        String shortAlphabet = ALPHABET.substring(1);
        String repeated = "00" + ALPHABET.substring(2);
        String withHyphen = "-" + ALPHABET.substring(1);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CodeGenerator(shortAlphabet, MULTIPLIER));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CodeGenerator(repeated, MULTIPLIER));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CodeGenerator(withHyphen, MULTIPLIER));
    }

    @Test
    void contextFailsOnStartupWhenMultiplierIsNotCoprime() {
        contextRunner.withPropertyValues("linkpulse.code.multiplier=62").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("coprimo");
        });
    }

    @Test
    void contextStartsWithDefaultMultiplier() {
        contextRunner.withPropertyValues("linkpulse.code.multiplier=" + MULTIPLIER).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CodeGenerator.class).encode(1)).isEqualTo("cJinsNv");
        });
    }
}
