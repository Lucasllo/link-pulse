package dev.linkpulse.stats;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * Corpo da resposta do {@code GET /links/{code}/stats} (D-01).
 *
 * <p>Só agregados: nunca leva {@code ipHash} nem outro identificador por visitante. Total, série
 * diária e tops usam o mesmo período e o mesmo filtro de bots.
 *
 * @param code código curto
 * @param from primeiro dia do período efetivo (inclusivo, UTC)
 * @param to último dia do período efetivo (inclusivo, UTC)
 * @param includeBots se os cliques de bots foram contados
 * @param total cliques no período
 * @param daily cliques por dia, contínuo do {@code from} ao {@code to} (0 nos dias sem clique)
 * @param topReferrers até 10 hosts de origem, {@code (direct)} quando não houve {@code Referer}
 * @param topBrowsers até 10 famílias de navegador
 * @param topOs até 10 famílias de sistema operacional
 */
public record StatsResponse(
        @Schema(description = "Código curto", example = "cJinsNv")
        String code,
        @Schema(description = "Primeiro dia do período efetivo (inclusivo, UTC)",
                example = "2026-09-11")
        LocalDate from,
        @Schema(description = "Último dia do período efetivo (inclusivo, UTC)",
                example = "2026-10-10")
        LocalDate to,
        @Schema(description = "Se os cliques de bots foram contados", example = "false")
        boolean includeBots,
        @Schema(description = "Cliques no período", example = "42")
        long total,
        @Schema(description = "Cliques por dia (UTC), do from ao to, com 0 nos dias sem clique")
        List<DailyCount> daily,
        @ArraySchema(arraySchema = @Schema(description = "Até 10 hosts de origem, por cliques "
                + "decrescente e, no empate, por valor; (direct) quando não houve Referer"))
        List<TopItem> topReferrers,
        @ArraySchema(arraySchema = @Schema(description = "Até 10 famílias de navegador, por "
                + "cliques decrescente e, no empate, por valor"))
        List<TopItem> topBrowsers,
        @ArraySchema(arraySchema = @Schema(description = "Até 10 famílias de sistema "
                + "operacional, por cliques decrescente e, no empate, por valor"))
        List<TopItem> topOs) {

    /**
     * Cliques de um dia.
     *
     * @param date dia em UTC
     * @param clicks cliques no dia
     */
    public record DailyCount(
            @Schema(description = "Dia em UTC", example = "2026-10-10")
            LocalDate date,
            @Schema(description = "Cliques no dia", example = "7")
            long clicks) {
    }

    /**
     * Um item de top: valor da dimensão e cliques.
     *
     * @param value host do referrer ({@code (direct)} sem {@code Referer}), família do navegador
     *     ou do SO; {@code (unknown)} quando o valor não foi gravado
     * @param clicks cliques com esse valor
     */
    public record TopItem(
            @Schema(description = "Valor da dimensão: host do referrer, navegador ou SO",
                    example = "(direct)")
            String value,
            @Schema(description = "Cliques com esse valor", example = "12")
            long clicks) {
    }
}
