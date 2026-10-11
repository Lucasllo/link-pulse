package dev.linkpulse.click;

/**
 * Classifica o user-agent em família de navegador, família de SO e marca de bot (esqueleto do
 * RED da 02-05; as regras entram no GREEN).
 */
public final class UserAgentClassifier {

    /** Família usada quando nenhuma regra casa. */
    public static final String OTHER = "Other";

    private UserAgentClassifier() {
    }

    /**
     * Resultado da classificação.
     *
     * @param browser família do navegador
     * @param os família do sistema operacional
     * @param bot se o cliente é robô, preview de link ou cliente de linha de comando
     */
    public record UserAgentInfo(String browser, String os, boolean bot) {
    }

    /**
     * Classifica o user-agent.
     *
     * @param userAgent cabeçalho {@code User-Agent}, possivelmente nulo
     * @return a classificação
     */
    public static UserAgentInfo classify(String userAgent) {
        return new UserAgentInfo(OTHER, OTHER, false);
    }
}
