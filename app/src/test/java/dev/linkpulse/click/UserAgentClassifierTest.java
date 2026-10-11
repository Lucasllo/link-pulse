package dev.linkpulse.click;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linkpulse.click.UserAgentClassifier.UserAgentInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Vetores versionados do {@link UserAgentClassifier} (CLICK-07, D-10): família de navegador,
 * família de SO e marca de bot. Rodam sem Docker.
 *
 * <p>O separador do {@code @CsvSource} é {@code |} porque os user-agents têm vírgulas
 * ({@code KHTML, like Gecko}).
 */
class UserAgentClassifierTest {

    private static final String CHROME_WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/129.0.0.0 Safari/537.36 | Chrome | Windows | false",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0 | Edge | Windows | false",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/129.0.0.0 Safari/537.36 OPR/114.0.0.0 | Opera | Windows | false",
        "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0 "
                + "| Firefox | Linux | false",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                + "Version/18.0 Safari/605.1.15 | Safari | macOS | false",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1 "
                + "| Safari | iOS | false",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) CriOS/129.0.6668.69 Mobile/15E148 Safari/604.1 "
                + "| Chrome | iOS | false",
        "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "SamsungBrowser/25.0 Chrome/121.0.0.0 Mobile Safari/537.36 "
                + "| Samsung Internet | Android | false",
        "Mozilla/5.0 (Linux; Android 13; CUBOT X70) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/129.0.0.0 Mobile Safari/537.36 | Chrome | Android | false",
        "Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/129.0.0.0 Safari/537.36 | Chrome | ChromeOS | false",
        "okhttp/4.12.0 | Other | Other | false",
        "curl/8.5.0 | Other | Other | true",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "HeadlessChrome/129.0.0.0 Safari/537.36 | Chrome | Linux | true"
    })
    void classifiesBrowserOsAndBot(String userAgent, String browser, String os, boolean bot) {
        assertThat(UserAgentClassifier.classify(userAgent))
                .isEqualTo(new UserAgentInfo(browser, os, bot));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
        "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)",
        "facebookexternalhit/1.1",
        "Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)",
        "WhatsApp/2.23.20.0",
        "TelegramBot (like TwitterBot)",
        "Discordbot/2.0",
        "curl/8.5.0",
        "Wget/1.21.4",
        "python-requests/2.32.3",
        "Go-http-client/1.1",
        "Java-http-client/21.0.10",
        "k6/2.3.0 (https://k6.io/)",
        CHROME_WINDOWS + " HeadlessChrome/129.0.0.0",
        "Mozilla/5.0 (compatible; Examplebot/1.0)"
    })
    void crawlersPreviewsAndCommandLineClientsAreBots(String userAgent) {
        assertThat(UserAgentClassifier.classify(userAgent).bot()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void missingUserAgentIsBot(String userAgent) {
        assertThat(UserAgentClassifier.classify(userAgent))
                .isEqualTo(new UserAgentInfo(UserAgentClassifier.OTHER, UserAgentClassifier.OTHER,
                        true));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        CHROME_WINDOWS,
        "Mozilla/5.0 (Linux; Android 13; CUBOT X70) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/129.0.0.0 Mobile Safari/537.36",
        "okhttp/4.12.0"
    })
    void deviceNamesAndAppClientsAreNotBots(String userAgent) {
        assertThat(UserAgentClassifier.classify(userAgent).bot()).isFalse();
    }
}
