package dev.linkpulse.click;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Reduz o {@code Referer} ao host já na ingestão (D-03).
 *
 * <p>Path, query e fragmento do {@code Referer} podem carregar tokens, e-mails e outros dados
 * pessoais (por exemplo, {@code ?token=...} de um link mágico), e o userinfo pode carregar
 * credenciais. Por isso só o host sai daqui: em minúsculas ({@link Locale#ROOT}), sem pontos
 * finais (mesma regra do {@code TargetHostPolicy}) e truncado em 255 caracteres. Porta, userinfo,
 * path, query e fragmento nunca saem.
 *
 * <p>Sem {@code Referer}, ou com um valor que não dá host (texto livre, caminho relativo,
 * {@code mailto:}, sintaxe inválida ou entrada acima de 2048 caracteres), o resultado é o bucket
 * {@link #DIRECT}. Esquemas sem host HTTP mas com autoridade, como
 * {@code android-app://com.google.android.gm}, mantêm o host.
 */
public final class ReferrerNormalizer {

    /** Bucket de clique sem {@code Referer} utilizável. */
    public static final String DIRECT = "(direct)";

    /** Acima disso o valor nem é interpretado. */
    static final int MAX_REFERER_LENGTH = 2048;

    /** Tamanho máximo do host publicado (limite de um nome DNS). */
    static final int MAX_HOST_LENGTH = 255;

    private ReferrerNormalizer() {
    }

    /**
     * Devolve o host do {@code Referer}, ou {@link #DIRECT} se não há host utilizável.
     *
     * @param referer valor do header, ou {@code null}
     * @return host em minúsculas, sem pontos finais e com até 255 caracteres; ou
     *     {@link #DIRECT}
     */
    public static String toHost(String referer) {
        if (referer == null || referer.isBlank() || referer.length() > MAX_REFERER_LENGTH) {
            return DIRECT;
        }
        String host;
        try {
            host = new URI(referer.strip()).getHost();
        } catch (URISyntaxException e) {
            return DIRECT;
        }
        if (host == null) {
            return DIRECT;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        int end = normalized.length();
        while (end > 0 && normalized.charAt(end - 1) == '.') {
            end--;
        }
        if (end == 0) {
            return DIRECT;
        }
        return normalized.substring(0, Math.min(end, MAX_HOST_LENGTH));
    }
}
