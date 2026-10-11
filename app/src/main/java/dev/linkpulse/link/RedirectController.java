package dev.linkpulse.link;

import dev.linkpulse.click.ClickPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redireciona {@code GET /{code}} para a URL original e publica o clique sem esperar.
 *
 * <p>Só {@code GET} conta como clique (REDIR-06): o {@code @GetMapping} também atende
 * {@code HEAD}, que vem de prefetch e de link preview. O IP vem só de
 * {@link HttpServletRequest#getRemoteAddr()}; o controller nunca lê headers de proxy (D-08).
 */
@RestController
@Tag(name = "Redirect", description = "Redirecionamento do código curto para a URL original")
public class RedirectController {

    private final RedirectService redirectService;
    private final ClickPublisher clickPublisher;

    /**
     * Cria o controller.
     *
     * @param redirectService service que resolve o código (cache e, no miss, Postgres)
     * @param clickPublisher publisher assíncrono dos cliques
     */
    public RedirectController(RedirectService redirectService, ClickPublisher clickPublisher) {
        this.redirectService = redirectService;
        this.clickPublisher = clickPublisher;
    }

    /**
     * Responde 302 para a URL do link, sem permitir cache no navegador nem em proxies.
     *
     * <p>A regex do path não aceita {@code .} nem {@code /}: {@code favicon.ico},
     * {@code swagger-ui.html} e caminhos de vários segmentos não chegam aqui.
     *
     * <p>O clique só é publicado depois que o código resolveu (404 e 410 lançam antes) e só em
     * {@code GET}. A publicação não bloqueia o redirect.
     *
     * @param code código curto
     * @param request request HTTP (método, IP do socket, {@code Referer} e {@code User-Agent})
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
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        String target = redirectService.resolve(code);
        if (HttpMethod.GET.matches(request.getMethod())) {
            clickPublisher.publish(code, request.getRemoteAddr(),
                    request.getHeader(HttpHeaders.REFERER),
                    request.getHeader(HttpHeaders.USER_AGENT));
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .build();
    }
}
