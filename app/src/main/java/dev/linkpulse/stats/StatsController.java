package dev.linkpulse.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estatísticas de cliques por link.
 *
 * <p>Fica fora do path {@code /links} exato: o rate limit por IP vale só para o
 * {@code POST /links}.
 */
@RestController
@Tag(name = "Stats", description = "Estatísticas de cliques por link")
public class StatsController {

    private final StatsService statsService;

    /**
     * Cria o controller.
     *
     * @param statsService service das estatísticas
     */
    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * Estatísticas do link no período pedido (padrão: últimos 30 dias em UTC).
     *
     * @param code código curto
     * @param from primeiro dia ({@code YYYY-MM-DD}, inclusivo, UTC), opcional
     * @param to último dia ({@code YYYY-MM-DD}, inclusivo, UTC), opcional
     * @param includeBots se os cliques de bots entram na conta
     * @return as estatísticas do período efetivo
     */
    @GetMapping("/links/{code:[A-Za-z0-9_-]+}/stats")
    @Operation(summary = "Estatísticas de cliques do link",
            description = "Total, cliques por dia e top referrers, navegadores e sistemas "
                    + "operacionais do link. As datas from e to são YYYY-MM-DD, inclusivas e "
                    + "agrupadas em UTC. Sem from e to, o período é os últimos 30 dias (to = hoje "
                    + "em UTC); com só from, to = hoje; com só to, from = to - 29 dias. O período "
                    + "tem no máximo 366 dias, e a resposta ecoa o from e o to efetivos. A série "
                    + "diária é contínua, com 0 nos dias sem clique. Cada top tem até 10 itens, "
                    + "por cliques e, no empate, por valor. O referrer é o host de origem, ou "
                    + "(direct) quando não houve Referer. Bots (crawlers, previews de link e "
                    + "clientes de linha de comando como curl e wget) ficam fora, a menos que "
                    + "includeBots=true. Link expirado continua com estatísticas.")
    @ApiResponse(responseCode = "200", description = "Estatísticas do período efetivo")
    @ApiResponse(responseCode = "400",
            description = "Período inválido, com from depois de to ou mais de 366 dias "
                    + "(invalid-period), ou parâmetro em formato inválido (invalid-parameter)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Código inexistente (link-not-found)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503",
            description = "MongoDB indisponível; tente de novo em instantes (stats-unavailable)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public StatsResponse stats(
            @PathVariable String code,
            @Parameter(description = "Primeiro dia, YYYY-MM-DD, inclusivo, em UTC (padrão: "
                    + "to - 29 dias)", example = "2026-09-11")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @Parameter(description = "Último dia, YYYY-MM-DD, inclusivo, em UTC (padrão: hoje "
                    + "em UTC)", example = "2026-10-10")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @Parameter(description = "Inclui os cliques de bots (crawlers, previews de link, "
                    + "curl, wget...)", example = "false")
            @RequestParam(defaultValue = "false") boolean includeBots) {
        return statsService.stats(code, from, to, includeBots);
    }
}
