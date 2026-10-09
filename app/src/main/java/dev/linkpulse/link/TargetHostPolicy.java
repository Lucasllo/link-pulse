package dev.linkpulse.link;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Regras do host de destino de um link (T-08-01 a T-08-03, T-08-07).
 *
 * <p>Todas as regras olham o host normalizado: minúsculas com {@link Locale#ROOT} e sem pontos
 * finais, porque o FQDN com ponto final ({@code localhost.}) é o mesmo host para o DNS e para o
 * navegador. A ordem é:
 *
 * <ol>
 *   <li><b>Host numérico fora da forma canônica.</b> Se o último rótulo "termina em número" (só
 *       dígitos ou {@code 0x} seguido de dígitos hex, regra "ends in a number" do WHATWG URL
 *       Standard), o navegador lê o host como IPv4: {@code 0x7f000001}, {@code 2130706433} e
 *       {@code 0177.0.0.1} viram 127.0.0.1. Só a forma {@code a.b.c.d} decimal, sem zero à
 *       esquerda, é aceita.</li>
 *   <li><b>Próprio encurtador.</b> O host de {@code linkpulse.base-url} e os de
 *       {@code linkpulse.self-hosts}. A porta não entra na comparação: atrás de um proxy, o mesmo
 *       host em outra porta continua sendo o encurtador.</li>
 *   <li><b>Loopback</b>, em qualquer perfil: {@code localhost}, {@code *.localhost} (RFC 6761
 *       §6.3), 127.0.0.0/8, 0.0.0.0 e os literais IPv6 de loopback e "any" ({@code [::1]},
 *       {@code [::]}, inclusive IPv4-mapped como {@code [::ffff:127.0.0.1]}). Em dev, loopback é
 *       o próprio encurtador; em prod, aponta para a máquina de quem clica e nunca é um destino
 *       público.</li>
 * </ol>
 *
 * <p>A classe <b>não resolve DNS</b>. Só literal IPv6 entre colchetes vai ao
 * {@link InetAddress#getByName(String)}, que o interpreta ou rejeita sem consulta; nomes nunca
 * chegam ao resolvedor. Por isso, um domínio de terceiros que aponte para o encurtador (DNS
 * curinga, CNAME para o load balancer) só é barrado se estiver em {@code linkpulse.self-hosts}
 * (mitigação parcial, T-08-06).
 */
public final class TargetHostPolicy {

    /** Motivo da recusa quando o destino é o próprio encurtador. */
    static final String SELF_MESSAGE = "não pode apontar para o próprio encurtador";

    /** Motivo da recusa quando o destino é um endereço de loopback. */
    static final String LOOPBACK_MESSAGE = "não pode apontar para endereço de loopback";

    /** Motivo da recusa quando o host numérico não está na forma canônica. */
    static final String NUMERIC_HOST_MESSAGE = "host numérico deve estar na forma a.b.c.d";

    /** IPv4 canônico: quatro octetos decimais de 0 a 255, sem zero à esquerda. */
    static final Pattern CANONICAL_IPV4 = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    /** Rótulo que "termina em número" pela regra WHATWG: só dígitos, ou {@code 0x} + hex. */
    private static final Pattern NUMERIC_LABEL = Pattern.compile("^(\\d+|0x[0-9a-f]*)$");

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
     * Indica se o host é um IP numérico fora da forma canônica {@code a.b.c.d}.
     *
     * @param normalizedHost host já normalizado
     * @return {@code true} se o último rótulo termina em número e o host não é IPv4 canônico
     */
    static boolean isNonCanonicalNumeric(String normalizedHost) {
        if (normalizedHost.startsWith("[")) {
            return false;
        }
        String lastLabel = normalizedHost.substring(normalizedHost.lastIndexOf('.') + 1);
        return NUMERIC_LABEL.matcher(lastLabel).matches()
                && !CANONICAL_IPV4.matcher(normalizedHost).matches();
    }

    /**
     * Indica se o host é loopback (ou "any"), sem consultar DNS.
     *
     * @param normalizedHost host já normalizado
     * @return {@code true} para {@code localhost}, {@code *.localhost}, 127.0.0.0/8, 0.0.0.0 e
     *     literais IPv6 de loopback ou "any"
     */
    static boolean isLoopback(String normalizedHost) {
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            return true;
        }
        if (CANONICAL_IPV4.matcher(normalizedHost).matches()) {
            return normalizedHost.startsWith("127.") || "0.0.0.0".equals(normalizedHost);
        }
        if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
            try {
                // Literal entre colchetes: o JDK interpreta ou lança exceção, sem DNS.
                InetAddress address = InetAddress.getByName(normalizedHost);
                return address.isLoopbackAddress() || address.isAnyLocalAddress();
            } catch (UnknownHostException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Valida o host da URL de destino, na ordem: numérico não canônico, próprio encurtador,
     * loopback.
     *
     * <p>Host nulo passa: o {@code @HttpUrl} já recusa essa URL na borda HTTP.
     *
     * @param url URL de destino
     * @throws org.springframework.web.ErrorResponseException 400 {@code validation-error} com
     *     {@code errors[0].field = "url"} se alguma regra recusa o host
     */
    public void check(String url) {
        String host = normalizeHost(URI.create(url).getHost());
        if (host == null) {
            return;
        }
        if (isNonCanonicalNumeric(host)) {
            throw LinkProblems.invalidField("url", NUMERIC_HOST_MESSAGE);
        }
        if (selfHosts.contains(host)) {
            throw LinkProblems.invalidField("url", SELF_MESSAGE);
        }
        if (isLoopback(host)) {
            throw LinkProblems.invalidField("url", LOOPBACK_MESSAGE);
        }
    }
}
