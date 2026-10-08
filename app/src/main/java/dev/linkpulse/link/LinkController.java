package dev.linkpulse.link;

import dev.linkpulse.config.LinkPulseProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Links", description = "Criação de links curtos")
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
    @Operation(summary = "Cria um link curto",
            description = "Gera um código curto para a URL de destino (http ou https). O alias é "
                    + "opcional: com ele, o código é o próprio alias (4 a 32 caracteres, com pelo "
                    + "menos um '-' ou '_'). O expiresAt é opcional, em ISO-8601 com offset (por "
                    + "exemplo 2026-12-31T23:59:59Z), e precisa estar no futuro.")
    @ApiResponse(responseCode = "201",
            description = "Link criado; o header Location traz a URL curta",
            headers = @Header(name = "Location", description = "URL curta do link criado"))
    @ApiResponse(responseCode = "400",
            description = "Campo inválido (validation-error, com errors[]) ou JSON malformado "
                    + "(malformed-request)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "O alias já existe (alias-conflict)",
            content = @Content(mediaType = "application/problem+json",
                    schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        Link link = linkService.create(request);
        URI shortUrl = UriComponentsBuilder.fromUri(properties.baseUrl())
                .pathSegment(link.getCode())
                .build()
                .toUri();
        return ResponseEntity.created(shortUrl).body(LinkResponse.from(link, shortUrl));
    }
}
