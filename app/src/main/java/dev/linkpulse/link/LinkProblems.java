package dev.linkpulse.link;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Fábrica dos erros de link em Problem Details (RFC 9457).
 *
 * <p>O {@code detail} é montado aqui, só com o código, o alias e a expiração: nunca leva SQL,
 * stack trace, mensagem de exceção ou nome de constraint.
 */
public final class LinkProblems {

    private LinkProblems() {
    }

    /**
     * Link inexistente.
     *
     * @param code código pedido
     * @return 404 com type {@code /problems/link-not-found} e a propriedade {@code code}
     */
    public static ErrorResponseException notFound(String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Nenhum link com o código '" + code + "'.");
        problem.setType(URI.create("/problems/link-not-found"));
        problem.setTitle("Link não encontrado");
        problem.setProperty("code", code);
        return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
    }

    /**
     * Link expirado.
     *
     * @param code código pedido
     * @param expiredAt instante em que o link expirou
     * @return 410 com type {@code /problems/link-expired} e as propriedades {@code code} e
     *     {@code expiredAt}
     */
    public static ErrorResponseException expired(String code, Instant expiredAt) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.GONE, "O link '" + code + "' expirou.");
        problem.setType(URI.create("/problems/link-expired"));
        problem.setTitle("Link expirado");
        problem.setProperty("code", code);
        problem.setProperty("expiredAt", expiredAt.toString());
        return new ErrorResponseException(HttpStatus.GONE, problem, null);
    }

    /**
     * Alias já em uso, inclusive quando a duplicata só aparece na constraint (corrida, D-08).
     *
     * @param alias alias pedido
     * @return 409 com type {@code /problems/alias-conflict} e a propriedade {@code alias}
     */
    public static ErrorResponseException aliasConflict(String alias) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "O alias '" + alias + "' já está em uso.");
        problem.setType(URI.create("/problems/alias-conflict"));
        problem.setTitle("Alias já existe");
        problem.setProperty("alias", alias);
        return new ErrorResponseException(HttpStatus.CONFLICT, problem, null);
    }

    /**
     * Campo da requisição inválido por regra de domínio. Usa o mesmo formato de {@code errors[]}
     * dos erros de Bean Validation.
     *
     * @param field nome do campo no JSON
     * @param message motivo em pt-BR, sem ecoar exceção nem detalhe interno
     * @return 400 com type {@code /problems/validation-error} e {@code errors[{field, message}]}
     */
    public static ErrorResponseException invalidField(String field, String message) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos.");
        problem.setType(URI.create("/problems/validation-error"));
        problem.setTitle("Requisição inválida");
        problem.setProperty("errors", List.of(Map.of("field", field, "message", message)));
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }
}
