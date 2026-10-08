package dev.linkpulse.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Exige uma URL {@code http} ou {@code https} absoluta, com host e sem credenciais
 * ({@code user:senha@}).
 *
 * <p>{@code null} é aceito: a obrigatoriedade fica com {@code @NotBlank} e o tamanho com
 * {@code @Size}. O alvo é só {@link ElementType#FIELD}, para que a anotação num componente de
 * record vá apenas para o campo e não seja validada duas vezes.
 */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {

    /**
     * Mensagem de erro devolvida em {@code errors[].message}.
     *
     * @return a mensagem em pt-BR
     */
    String message() default "deve ser uma URL http ou https absoluta, com host e sem credenciais";

    /**
     * Grupos de validação.
     *
     * @return os grupos
     */
    Class<?>[] groups() default {};

    /**
     * Payload da constraint.
     *
     * @return o payload
     */
    Class<? extends Payload>[] payload() default {};
}
