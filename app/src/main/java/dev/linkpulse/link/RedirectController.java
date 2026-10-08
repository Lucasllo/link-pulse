package dev.linkpulse.link;

import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redireciona {@code GET /{code}} para a URL original.
 */
@RestController
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
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = linkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .build();
    }
}
