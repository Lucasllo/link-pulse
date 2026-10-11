package dev.linkpulse.stats;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
    public StatsResponse stats(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeBots) {
        return statsService.stats(code, from, to, includeBots);
    }
}
