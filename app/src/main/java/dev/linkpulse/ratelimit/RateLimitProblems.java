package dev.linkpulse.ratelimit;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Fábrica do erro de rate limit em Problem Details (RFC 9457).
 *
 * <p>O {@code detail} só leva os segundos de espera: nunca o IP, a chave nem o limite configurado.
 */
public final class RateLimitProblems {

    private RateLimitProblems() {
    }

    /**
     * Limite de criações por IP atingido.
     *
     * @param retryAfterSeconds segundos até a próxima janela
     * @return 429 com type {@code /problems/rate-limited}, a propriedade
     *     {@code retryAfterSeconds} e o header {@code Retry-After} com o mesmo valor
     */
    public static ErrorResponseException tooManyRequests(long retryAfterSeconds) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Limite de criação de links por IP atingido. Tente de novo em "
                        + retryAfterSeconds + " s.");
        problem.setType(URI.create("/problems/rate-limited"));
        problem.setTitle("Muitas requisições");
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);
        ErrorResponseException ex =
                new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS, problem, null);
        ex.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return ex;
    }
}
