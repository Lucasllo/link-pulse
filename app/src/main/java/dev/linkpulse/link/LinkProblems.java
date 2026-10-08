package dev.linkpulse.link;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Fábrica dos erros de link em Problem Details (RFC 9457).
 *
 * <p>O {@code detail} é montado aqui, só com o código e a expiração: nunca leva SQL, stack trace
 * ou nome de constraint.
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
}
