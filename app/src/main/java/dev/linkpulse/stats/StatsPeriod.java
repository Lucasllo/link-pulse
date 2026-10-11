package dev.linkpulse.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Período das estatísticas: datas inclusivas, agrupadas em UTC (D-01).
 *
 * <p>30 e 366 são constantes do contrato público, e não configuração: o mesmo endpoint não pode
 * responder diferente em cada ambiente.
 *
 * @param from primeiro dia (inclusivo)
 * @param to último dia (inclusivo)
 */
public record StatsPeriod(LocalDate from, LocalDate to) {

    /** Tamanho do período padrão, em dias (D-01). */
    public static final int DEFAULT_DAYS = 30;

    /** Tamanho máximo do período, em dias (D-02). */
    public static final int MAX_DAYS = 366;

    /**
     * Resolve o período pedido, completando o que faltar.
     *
     * <p>Os dois nulos: os últimos 30 dias até hoje. Só {@code from}: até hoje. Só {@code to}: os
     * 30 dias que terminam em {@code to}. Depois dos defaults, {@code from} depois de {@code to}
     * ou mais de {@link #MAX_DAYS} dias é período inválido (D-02).
     *
     * @param from primeiro dia pedido, ou {@code null}
     * @param to último dia pedido, ou {@code null}
     * @param todayUtc hoje em UTC
     * @return o período efetivo
     * @throws org.springframework.web.ErrorResponseException 400 {@code /problems/invalid-period}
     */
    public static StatsPeriod resolve(LocalDate from, LocalDate to, LocalDate todayUtc) {
        LocalDate end = to != null ? to : todayUtc;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        StatsPeriod period = new StatsPeriod(start, end);
        if (start.isAfter(end) || period.days() > MAX_DAYS) {
            throw StatsProblems.invalidPeriod(start, end);
        }
        return period;
    }

    /**
     * Quantidade de dias do período, contando as duas pontas.
     *
     * @return dias do período
     */
    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /**
     * Início do período.
     *
     * @return {@code from} às 00:00 UTC
     */
    public Instant startInclusive() {
        return from.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Fim exclusivo do período.
     *
     * @return o dia seguinte a {@code to}, às 00:00 UTC
     */
    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
