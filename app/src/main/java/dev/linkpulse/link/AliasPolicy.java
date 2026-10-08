package dev.linkpulse.link;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Regras do alias customizado (D-05, D-06, D-07).
 *
 * <p>Os códigos gerados pelo {@link CodeGenerator} são só alfanuméricos (7 caracteres), e o alias
 * exige pelo menos um {@code -} ou {@code _}: os dois espaços nunca se cruzam, então um alias
 * nunca ocupa um código que a sequência ainda vai gerar. A constraint {@code uk_links_code}
 * continua no banco como garantia final.
 *
 * <p>O alias é case-sensitive no armazenamento e no lookup ({@code Minha-Promo} e
 * {@code minha-promo} são links diferentes). Só a comparação com as palavras reservadas ignora a
 * caixa, para que {@code Swagger-UI} não sombreie a rota {@code swagger-ui}.
 */
public final class AliasPolicy {

    /** Formato aceito: 4 a 32 caracteres ASCII em {@code [A-Za-z0-9_-]}, com um '-' ou '_'. */
    public static final String ALIAS_REGEX = "^(?=.*[-_])[A-Za-z0-9_-]{4,32}$";

    private static final Pattern ALIAS_PATTERN = Pattern.compile(ALIAS_REGEX);

    private static final String FORMAT_MESSAGE = "deve ter de 4 a 32 caracteres entre letras, "
            + "dígitos, '-' e '_', com pelo menos um '-' ou '_'";

    private final Set<String> reserved;

    /**
     * Cria a política.
     *
     * @param reserved palavras reservadas; guardadas em minúsculas ({@link Locale#ROOT})
     */
    public AliasPolicy(Collection<String> reserved) {
        this.reserved = reserved.stream()
                .map(word -> word.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Indica se o alias está no formato aceito.
     *
     * @param alias alias pedido
     * @return {@code true} se casa com {@link #ALIAS_REGEX}
     */
    public boolean matchesFormat(String alias) {
        return ALIAS_PATTERN.matcher(alias).matches();
    }

    /**
     * Indica se o alias é uma palavra reservada, sem diferenciar maiúsculas.
     *
     * @param alias alias pedido
     * @return {@code true} se reservado
     */
    public boolean isReserved(String alias) {
        return reserved.contains(alias.toLowerCase(Locale.ROOT));
    }

    /**
     * Valida o alias.
     *
     * @param alias alias pedido
     * @throws org.springframework.web.ErrorResponseException 400 {@code validation-error} com
     *     {@code errors[0].field = "alias"} se o formato é inválido ou o alias é reservado
     */
    public void check(String alias) {
        if (!matchesFormat(alias)) {
            throw LinkProblems.invalidField("alias", FORMAT_MESSAGE);
        }
        if (isReserved(alias)) {
            throw LinkProblems.invalidField("alias", "é uma palavra reservada");
        }
    }
}
