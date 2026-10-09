package dev.linkpulse.link;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Gera o código curto de 7 caracteres a partir do ID da sequência (D-01, D-02).
 *
 * <p>O código é {@code base62(pad7((id * M) mod 62^7))}. Como {@code M} é coprimo de 62^7, a
 * multiplicação é uma bijeção em {@code [0, 62^7)}: IDs distintos sempre dão códigos distintos, e
 * {@link #decode(String)} desfaz a conta com o inverso modular de {@code M}. O efeito prático é
 * que IDs consecutivos viram códigos sem relação visível entre si.
 *
 * <p>A multiplicação usa {@link BigInteger}: com {@code M} na casa de 2e12, {@code id * M} em
 * {@code long} estoura a partir de {@code id} perto de 4,2 milhões, sem exceção, e quebraria a
 * bijeção.
 *
 * <p><strong>Isto é ofuscação, não segurança.</strong> Quem conhece dois códigos de IDs
 * consecutivos recupera {@code M} (a diferença entre eles é {@code M mod 62^7}) e passa a
 * enumerar todos os links. O código curto não deve ser tratado como segredo nem como controle de
 * acesso.
 */
public final class CodeGenerator {

    /** Comprimento fixo de todo código gerado. */
    public static final int LENGTH = 7;

    /** Tamanho do espaço de códigos: 62^7. */
    public static final long SPACE = 3_521_614_606_208L;

    private static final int BASE = 62;
    private static final int ASCII_SIZE = 128;
    private static final BigInteger MODULUS = BigInteger.valueOf(SPACE);

    private final char[] alphabet;
    private final int[] indexOf;
    private final BigInteger multiplier;
    private final BigInteger inverse;

    /**
     * Cria o gerador e valida a configuração.
     *
     * @param alphabet 62 caracteres distintos de {@code [0-9A-Za-z]}
     * @param multiplier multiplicador em {@code (0, 62^7)} e coprimo de 62^7
     * @throws IllegalArgumentException se o alfabeto ou o multiplicador forem inválidos
     */
    public CodeGenerator(String alphabet, long multiplier) {
        this.alphabet = validAlphabet(alphabet);
        this.indexOf = new int[ASCII_SIZE];
        Arrays.fill(indexOf, -1);
        for (int i = 0; i < this.alphabet.length; i++) {
            indexOf[this.alphabet[i]] = i;
        }
        if (multiplier <= 0 || multiplier >= SPACE) {
            throw new IllegalArgumentException(
                    "multiplicador deve estar em (0, 62^7), recebido: " + multiplier);
        }
        this.multiplier = BigInteger.valueOf(multiplier);
        try {
            this.inverse = this.multiplier.modInverse(MODULUS);
        } catch (ArithmeticException e) {
            // Sem encadear a causa: "BigInteger not invertible" não acrescenta nada, e a
            // mensagem em pt-BR fica como causa raiz da falha de subida do contexto (D-03).
            throw new IllegalArgumentException("multiplicador deve ser coprimo de 62^7");
        }
    }

    private static char[] validAlphabet(String alphabet) {
        if (alphabet == null || alphabet.length() != BASE) {
            throw new IllegalArgumentException("alfabeto deve ter exatamente 62 caracteres");
        }
        char[] chars = alphabet.toCharArray();
        boolean[] seen = new boolean[ASCII_SIZE];
        for (char c : chars) {
            if (!isAsciiAlphanumeric(c)) {
                throw new IllegalArgumentException(
                        "alfabeto só aceita caracteres de [0-9A-Za-z], recebido: '" + c + "'");
            }
            if (seen[c]) {
                throw new IllegalArgumentException("alfabeto tem caractere repetido: '" + c + "'");
            }
            seen[c] = true;
        }
        return chars;
    }

    private static boolean isAsciiAlphanumeric(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    /**
     * Codifica o ID em 7 caracteres.
     *
     * @param id ID da sequência, em {@code [0, 62^7)}
     * @return o código curto
     * @throws IllegalArgumentException se o ID estiver fora do espaço de 7 caracteres (D-02)
     */
    public String encode(long id) {
        if (id < 0 || id >= SPACE) {
            throw new IllegalArgumentException("id fora do espaço de 7 caracteres: " + id);
        }
        long x = BigInteger.valueOf(id).multiply(multiplier).mod(MODULUS).longValueExact();
        char[] out = new char[LENGTH];
        for (int i = LENGTH - 1; i >= 0; i--) {
            out[i] = alphabet[(int) (x % BASE)];
            x /= BASE;
        }
        return new String(out);
    }

    /**
     * Decodifica o código de volta no ID.
     *
     * @param code código de 7 caracteres do alfabeto
     * @return o ID que gerou o código
     * @throws IllegalArgumentException se o código não tiver 7 caracteres do alfabeto
     */
    public long decode(String code) {
        if (code == null || code.length() != LENGTH) {
            throw new IllegalArgumentException("código deve ter exatamente 7 caracteres");
        }
        long x = 0;
        for (int i = 0; i < LENGTH; i++) {
            char c = code.charAt(i);
            int digit = c < ASCII_SIZE ? indexOf[c] : -1;
            if (digit < 0) {
                throw new IllegalArgumentException("caractere fora do alfabeto: '" + c + "'");
            }
            x = x * BASE + digit;
        }
        return BigInteger.valueOf(x).multiply(inverse).mod(MODULUS).longValueExact();
    }
}
