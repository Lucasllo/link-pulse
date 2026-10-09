package dev.linkpulse.link;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;

/**
 * Corpo da resposta 201 do {@code POST /links} (contrato D-09).
 *
 * @param code código curto
 * @param shortUrl URL curta, montada a partir de {@code linkpulse.base-url}
 * @param targetUrl URL de destino
 * @param expiresAt instante de expiração em UTC, ou {@code null} se o link não expira
 * @param createdAt instante de criação em UTC
 */
public record LinkResponse(
        @Schema(description = "Código curto", example = "cJinsNv")
        String code,
        @Schema(description = "URL curta", example = "http://localhost:8080/cJinsNv")
        URI shortUrl,
        @Schema(description = "URL de destino", example = "https://example.com/artigo?id=42")
        String targetUrl,
        @Schema(description = "Expiração em UTC (null se o link não expira)",
                example = "2026-12-31T23:59:59Z")
        Instant expiresAt,
        @Schema(description = "Criação em UTC", example = "2026-10-08T22:50:00Z")
        Instant createdAt) {

    /**
     * Monta a resposta a partir da entidade.
     *
     * @param link link persistido
     * @param shortUrl URL curta do link
     * @return a resposta
     */
    public static LinkResponse from(Link link, URI shortUrl) {
        return new LinkResponse(link.getCode(), shortUrl, link.getTargetUrl(),
                link.getExpiresAt(), link.getCreatedAt());
    }
}
