package dev.linkpulse.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Transforma o IP do cliente em hash antes de ele sair da thread que o recebeu (CLICK-06).
 *
 * <p>O hash é {@code HMAC-SHA256(chave, "<dia UTC>|<ip>")} em hex minúsculo (64 caracteres).
 *
 * <ul>
 *   <li><b>Por que HMAC com chave.</b> Um SHA-256 simples do IP é revertido por força bruta: o
 *       espaço IPv4 tem só 2^32 valores. Sem a chave secreta ({@code linkpulse.ip-hash-key}),
 *       quem lê o stream ou o MongoDB não consegue montar essa tabela.</li>
 *   <li><b>Por que o dia UTC.</b> O mesmo IP gera o mesmo hash dentro do dia (dá para contar
 *       visitantes únicos por dia) e um hash diferente no dia seguinte, o que limita a correlação
 *       de um visitante entre dias. A rotação não precisa de estado.</li>
 * </ul>
 *
 * <p>A chave nunca aparece em mensagem de exceção. A classe é {@code final} porque o construtor
 * lança exceção.
 */
public final class IpHasher {

    /** Tamanho mínimo da chave, em caracteres. */
    static final int MIN_KEY_LENGTH = 16;

    private static final String ALGORITHM = "HmacSHA256";
    private static final String INVALID_KEY_MESSAGE = "linkpulse.ip-hash-key é obrigatória e "
            + "precisa de pelo menos 16 caracteres (env LINKPULSE_IP_HASH_KEY)";

    private final SecretKeySpec key;

    /**
     * Cria o hasher com a chave secreta.
     *
     * @param key chave do HMAC ({@code linkpulse.ip-hash-key})
     * @throws IllegalArgumentException se a chave é nula, em branco ou tem menos de 16
     *     caracteres; a mensagem é fixa e não contém a chave
     */
    public IpHasher(String key) {
        if (key == null || key.isBlank() || key.length() < MIN_KEY_LENGTH) {
            throw new IllegalArgumentException(INVALID_KEY_MESSAGE);
        }
        this.key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /**
     * Calcula o hash do IP no dia UTC do instante informado.
     *
     * <p>Cria um {@link Mac} por chamada: o {@code Mac} não é thread-safe.
     *
     * @param ip endereço do cliente
     * @param at instante do clique; só o dia UTC entra no hash
     * @return HMAC-SHA256 em hex minúsculo (64 caracteres)
     * @throws IllegalStateException se o JDK não oferece {@code HmacSHA256} (a mensagem não
     *     contém a chave)
     */
    public String hash(String ip, Instant at) {
        String day = LocalDate.ofInstant(at, ZoneOffset.UTC).toString();
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((day + "|" + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível para o hash do IP", e);
        }
    }
}
