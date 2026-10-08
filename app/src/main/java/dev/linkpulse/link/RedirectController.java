package dev.linkpulse.link;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redireciona {@code GET /{code}} para a URL original.
 */
@RestController
@Tag(name = "Redirect", description = "Redirecionamento do código curto para a URL original")
public class RedirectController {

    private final LinkService linkService;

    /**
     * Cria o controller.
     *
     * @param linkService service que resolve o código
     */
    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    /**
     * Responde 302 para a URL do link, sem permitir cache no navegador nem em proxies.
     *
     * <p>A regex do path não aceita {@code .} nem {@code /}: {@code favicon.ico},
     * {@code swagger-ui.html} e caminhos de vários segmentos não chegam aqui.
     *
     * @param code código curto
     * @return 302 com {@code Location} e {@code Cache-Control: no-store, private}
     */
    @GetMapping("/{code:[A-Za-z0-9_-]+}")
    @Operation(summary = "Redireciona para a URL original",
            description = "Resolve o código curto (gerado ou alias, com diferença entre maiúsculas "
                    + "e minúsculas) e responde 302 para a URL de destino.")
    @ApiResponse(responseCode = "302",
            description = "Redirect; o header Location traz a URL original",
            headers = {
                @Header(name = "Location", description = "URL original do link"),
                @Header(name = "Cache-Control", description = "no-store, private")
            })
    @ApiResponse(responseCode = "404", description = "Código inexistente (link-not-found)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "410", description = "Link expirado (link-expired)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = linkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .build();
    }
}
