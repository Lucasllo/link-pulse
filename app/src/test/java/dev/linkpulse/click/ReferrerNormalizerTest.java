package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testes unitários do {@link ReferrerNormalizer} (D-03): só o host sai, nunca userinfo, porta,
 * path, query ou fragmento. Rodam sem Docker.
 */
class ReferrerNormalizerTest {

    @ParameterizedTest
    @CsvSource({
        "https://News.Example.com./a/b?token=secret#x, news.example.com",
        "http://user:pass@blog.example.org:8080/p, blog.example.org",
        "android-app://com.google.android.gm, com.google.android.gm",
        "http://[2001:db8::1]/x, [2001:db8::1]",
        "'  https://example.com/  ', example.com"
    })
    void reducesRefererToHost(String referer, String host) {
        assertThat(ReferrerNormalizer.toHost(referer)).isEqualTo(host);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "not a url",
        "/relativo",
        "http://exa mple.com/",
        "mailto:fulano@example.com"
    })
    void unusableRefererIsDirect(String referer) {
        assertThat(ReferrerNormalizer.toHost(referer)).isEqualTo(ReferrerNormalizer.DIRECT);
    }

    @Test
    void oversizedRefererIsDirectWithoutParsing() {
        String referer = "https://example.com/" + "a".repeat(3000);

        assertThat(ReferrerNormalizer.toHost(referer)).isEqualTo(ReferrerNormalizer.DIRECT);
    }

    @Test
    void longHostIsTruncatedTo255() {
        String host = "a".repeat(300) + ".example.com";

        String result = ReferrerNormalizer.toHost("https://" + host + "/x");

        assertThat(result).hasSize(255).isEqualTo(host.substring(0, 255));
    }

    @Test
    void directBucketName() {
        assertThat(ReferrerNormalizer.DIRECT).isEqualTo("(direct)");
    }
}
