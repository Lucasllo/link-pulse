package dev.linkpulse.link;

import dev.linkpulse.config.LinkPulseProperties;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * API de criação de links curtos.
 */
@RestController
@RequestMapping("/links")
public class LinkController {

    private final LinkService linkService;
    private final LinkPulseProperties properties;

    /**
     * Cria o controller.
     *
     * @param linkService service dos links
     * @param properties propriedades {@code linkpulse.*} (base da URL curta)
     */
    public LinkController(LinkService linkService, LinkPulseProperties properties) {
        this.linkService = linkService;
        this.properties = properties;
    }

    /**
     * Cria um link curto.
     *
     * <p>A URL curta sai sempre de {@code linkpulse.base-url} (D-12), nunca do header
     * {@code Host} nem de {@code X-Forwarded-*}.
     *
     * @param request URL de destino e opcionais
     * @return 201 com {@code Location} igual à URL curta e o corpo D-09
     */
    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        Link link = linkService.create(request);
        URI shortUrl = UriComponentsBuilder.fromUri(properties.baseUrl())
                .pathSegment(link.getCode())
                .build()
                .toUri();
        return ResponseEntity.created(shortUrl).body(LinkResponse.from(link, shortUrl));
    }
}
