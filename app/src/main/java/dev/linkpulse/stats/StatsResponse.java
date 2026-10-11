package dev.linkpulse.stats;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Corpo da resposta do {@code GET /links/{code}/stats} (D-01).
 *
 * <p>Só agregados: nunca leva {@code ipHash} nem outro identificador por visitante.
 *
 * @param code código curto
 * @param from primeiro dia do período efetivo (inclusivo, UTC)
 * @param to último dia do período efetivo (inclusivo, UTC)
 * @param includeBots se os cliques de bots foram contados
 * @param total cliques no período
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
        long total) {
}
