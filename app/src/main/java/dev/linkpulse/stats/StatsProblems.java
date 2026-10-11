package dev.linkpulse.stats;

import java.net.URI;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Fábrica dos erros das estatísticas em Problem Details (RFC 9457).
 *
 * <p>O {@code detail} é fixo: nunca leva mensagem de exceção, consulta nem detalhe do MongoDB.
 */
public final class StatsProblems {

    private StatsProblems() {
    }

    /**
     * Período inválido: {@code from} depois de {@code to}, ou mais de {@link StatsPeriod#MAX_DAYS}
     * dias (D-02).
     *
     * @param from primeiro dia efetivo
     * @param to último dia efetivo
     * @return 400 com type {@code /problems/invalid-period} e as propriedades {@code from},
     *     {@code to} (ISO) e {@code maxDays}
     */
    public static ErrorResponseException invalidPeriod(LocalDate from, LocalDate to) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "O período precisa ter from <= to e no máximo " + StatsPeriod.MAX_DAYS
                        + " dias.");
        problem.setType(URI.create("/problems/invalid-period"));
        problem.setTitle("Período inválido");
        problem.setProperty("from", from.toString());
        problem.setProperty("to", to.toString());
        problem.setProperty("maxDays", StatsPeriod.MAX_DAYS);
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }

    /**
     * MongoDB indisponível: as estatísticas não podem ser calculadas agora.
     *
     * @return 503 com type {@code /problems/stats-unavailable}
     */
    public static ErrorResponseException unavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível consultar as estatísticas agora. Tente novamente em instantes.");
        problem.setType(URI.create("/problems/stats-unavailable"));
        problem.setTitle("Estatísticas indisponíveis");
        return new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE, problem, null);
    }
}
