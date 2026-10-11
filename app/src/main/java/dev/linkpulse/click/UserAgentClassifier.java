package dev.linkpulse.click;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifica o user-agent em família de navegador, família de SO e marca de bot (CLICK-07).
 *
 * <p>Classificador próprio por substring, sem biblioteca: as regras são poucas, versionadas no
 * código e cobertas por vetores no {@code UserAgentClassifierTest}. Tudo roda sobre o
 * user-agent em minúsculas ({@link Locale#ROOT}), e a primeira regra que casa vence.
 *
 * <p><b>Só a família, nunca a versão</b> (D-10). Navegador e SO viram rótulos de cardinalidade
 * baixa ({@code Chrome}, {@code Android}...), bons para agregar no {@code /stats}. Versões
 * completas multiplicariam os grupos e, somadas ao {@code ipHash} e ao horário, ajudariam a
 * identificar um aparelho (fingerprinting). O user-agent cru nunca vai para o MongoDB.
 *
 * <p><b>Bots.</b> Contam como bot: user-agent ausente ou em branco, crawlers de busca e de IA,
 * previews de link de mensageiros e redes sociais, navegadores headless e <b>clientes HTTP de
 * linha de comando ou de biblioteca</b> ({@code curl}, {@code wget}, {@code python-requests},
 * {@code Go-http-client}, {@code Java-http-client}, {@code k6}...). Nenhum deles é uma pessoa
 * clicando num link, e o {@code /stats} exclui bots por padrão. Consequência: o seed de dados
 * e o smoke test que usam {@code curl} precisam mandar um user-agent de navegador ou consultar
 * com {@code includeBots=true}. O {@code okhttp} fica de fora de propósito, porque é a
 * biblioteca HTTP de muitos apps Android, que abrem links de gente de verdade.
 *
 * <p>O token {@code bot} sozinho não é usado: pegaria nomes de aparelho como {@code CUBOT}. A
 * regra genérica é {@code <letra>bot/<dígito>} ({@code Examplebot/1.0}).
 */
public final class UserAgentClassifier {

    /** Família usada quando nenhuma regra casa. */
    public static final String OTHER = "Other";

    /** Substrings (em minúsculas) que marcam robôs, previews e clientes não interativos. */
    static final List<String> BOT_TOKENS = List.of(
            "googlebot", "bingbot", "slurp", "duckduckbot", "baiduspider", "yandexbot",
            "applebot", "facebookexternalhit", "facebot", "twitterbot", "linkedinbot",
            "slackbot", "discordbot", "telegrambot", "whatsapp", "skypeuripreview", "embedly",
            "pinterestbot", "redditbot", "ahrefsbot", "semrushbot", "mj12bot", "petalbot",
            "bytespider", "gptbot", "claudebot", "ccbot", "headlesschrome", "lighthouse",
            "crawler", "spider", "curl/", "wget/", "python-requests", "python-urllib",
            "aiohttp", "go-http-client", "java-http-client", "java/", "apache-httpclient",
            "libwww-perl", "httpie", "postmanruntime", "insomnia", "k6/");

    private static final Pattern GENERIC_BOT = Pattern.compile("[a-z]bot/\\d");

    private UserAgentClassifier() {
    }

    /**
     * Resultado da classificação.
     *
     * @param browser família do navegador ({@code Chrome}, {@code Edge}, {@code Firefox},
     *     {@code Safari}, {@code Opera}, {@code Samsung Internet} ou {@code Other})
     * @param os família do sistema operacional ({@code Windows}, {@code macOS}, {@code iOS},
     *     {@code Android}, {@code Linux}, {@code ChromeOS} ou {@code Other})
     * @param bot se o cliente é robô, preview de link, navegador headless ou cliente de linha de
     *     comando
     */
    public record UserAgentInfo(String browser, String os, boolean bot) {
    }

    /**
     * Classifica o user-agent.
     *
     * @param userAgent cabeçalho {@code User-Agent}, possivelmente nulo
     * @return a classificação; nulo ou em branco dá {@code (Other, Other, bot)}
     */
    public static UserAgentInfo classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new UserAgentInfo(OTHER, OTHER, true);
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return new UserAgentInfo(browser(ua), os(ua), isBot(ua));
    }

    private static boolean isBot(String ua) {
        for (String token : BOT_TOKENS) {
            if (ua.contains(token)) {
                return true;
            }
        }
        return GENERIC_BOT.matcher(ua).find();
    }

    /**
     * Família do navegador. A ordem importa: Edge, Opera e Samsung Internet também anunciam
     * {@code Chrome/}, e quase todos anunciam {@code Safari/}.
     */
    private static String browser(String ua) {
        if (containsAny(ua, "edg/", "edge/", "edga/", "edgios/")) {
            return "Edge";
        }
        if (containsAny(ua, "opr/", "opera")) {
            return "Opera";
        }
        if (ua.contains("samsungbrowser/")) {
            return "Samsung Internet";
        }
        if (containsAny(ua, "firefox/", "fxios/")) {
            return "Firefox";
        }
        if (containsAny(ua, "crios/", "chrome/", "chromium/")) {
            return "Chrome";
        }
        if (ua.contains("safari/") && ua.contains("version/")) {
            return "Safari";
        }
        return OTHER;
    }

    /**
     * Família do SO. iOS vem antes de macOS porque o iPhone anuncia {@code like Mac OS X};
     * Android e ChromeOS vêm antes de Linux porque também anunciam {@code Linux}/{@code X11}.
     * O token do ChromeOS é {@code "cros "} (com espaço) para não casar com {@code microsoft}.
     */
    private static String os(String ua) {
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (containsAny(ua, "iphone", "ipad", "ipod")) {
            return "iOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("cros ")) {
            return "ChromeOS";
        }
        if (containsAny(ua, "mac os x", "macintosh")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return OTHER;
    }

    private static boolean containsAny(String ua, String... tokens) {
        for (String token : tokens) {
            if (ua.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
