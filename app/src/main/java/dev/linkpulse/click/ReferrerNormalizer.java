package dev.linkpulse.click;

/**
 * Reduz o {@code Referer} ao host (D-03). Esqueleto do RED: sempre devolve {@link #DIRECT}.
 */
public final class ReferrerNormalizer {

    /** Bucket de clique sem {@code Referer} utilizável. */
    public static final String DIRECT = "(direct)";

    private ReferrerNormalizer() {
    }

    /**
     * Devolve o host do {@code Referer}.
     *
     * @param referer valor do header, ou {@code null}
     * @return {@link #DIRECT} (esqueleto)
     */
    public static String toHost(String referer) {
        return DIRECT;
    }
}
