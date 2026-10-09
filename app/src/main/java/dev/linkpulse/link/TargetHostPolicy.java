package dev.linkpulse.link;

import java.net.URI;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Regras do host de destino de um link (T-08-01).
 *
 * <p>A URL de destino não pode apontar para o próprio encurtador: o conjunto de hosts próprios é o
 * host de {@code linkpulse.base-url} mais os de {@code linkpulse.self-hosts}. A comparação usa o
 * host normalizado: minúsculas com {@link Locale#ROOT} e sem pontos finais, porque o FQDN com
 * ponto final ({@code localhost.}) é o mesmo host para o DNS e para o navegador.
 *
 * <p>A porta não entra na comparação: atrás de um proxy, o mesmo host em outra porta continua
 * sendo o encurtador.
 *
 * <p>A classe nunca resolve DNS. Um domínio de terceiros que aponte para o encurtador só é barrado
 * se estiver em {@code linkpulse.self-hosts}.
 */
public final class TargetHostPolicy {

    /** Motivo da recusa quando o destino é o próprio encurtador. */
    static final String SELF_MESSAGE = "não pode apontar para o próprio encurtador";

    /** Motivo da recusa quando o destino é um endereço de loopback. */
    static final String LOOPBACK_MESSAGE = "não pode apontar para endereço de loopback";

    /** Motivo da recusa quando o host numérico não está na forma canônica. */
    static final String NUMERIC_HOST_MESSAGE = "host numérico deve estar na forma a.b.c.d";

    private final Set<String> selfHosts;

    /**
     * Cria a política.
     *
     * @param baseUrl base da URL curta ({@code linkpulse.base-url}); precisa ter host
     * @param selfHosts hosts extras que também são o encurtador; nulos e itens em branco são
     *     descartados
     * @throws IllegalArgumentException se {@code baseUrl} não tem host
     */
    public TargetHostPolicy(URI baseUrl, Collection<String> selfHosts) {
        String baseHost = normalizeHost(baseUrl.getHost());
        if (baseHost == null || baseHost.isBlank()) {
            throw new IllegalArgumentException("linkpulse.base-url precisa ter host");
        }
        Stream<String> extra = selfHosts == null ? Stream.empty() : selfHosts.stream();
        this.selfHosts = Stream.concat(Stream.of(baseHost), extra
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(host -> !host.isEmpty())
                        .map(TargetHostPolicy::normalizeHost))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Normaliza o host para comparação: minúsculas ({@link Locale#ROOT}) e sem pontos finais.
     *
     * @param host host como veio do {@link URI#getHost()}
     * @return o host normalizado, ou {@code null} se {@code host} é {@code null}
     */
    static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        int end = lower.length();
        while (end > 0 && lower.charAt(end - 1) == '.') {
            end--;
        }
        return lower.substring(0, end);
    }

    /**
     * Valida o host da URL de destino.
     *
     * <p>Host nulo passa: o {@code @HttpUrl} já recusa essa URL na borda HTTP.
     *
     * @param url URL de destino
     * @throws org.springframework.web.ErrorResponseException 400 {@code validation-error} com
     *     {@code errors[0].field = "url"} se o destino é o próprio encurtador
     */
    public void check(String url) {
        String host = normalizeHost(URI.create(url).getHost());
        if (host == null) {
            return;
        }
        if (selfHosts.contains(host)) {
            throw LinkProblems.invalidField("url", SELF_MESSAGE);
        }
    }
}
