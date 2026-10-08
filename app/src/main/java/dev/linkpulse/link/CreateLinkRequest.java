package dev.linkpulse.link;

import dev.linkpulse.common.validation.HttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Corpo do {@code POST /links}.
 *
 * @param url URL de destino do link
 * @param alias código escolhido pelo cliente; opcional (regras de alias na 01-04)
 * @param expiresAt instante de expiração em ISO-8601 com offset obrigatório (D-10); opcional
 */
public record CreateLinkRequest(
        @Schema(description = "URL de destino do link curto",
                example = "https://example.com/artigo?id=42")
        @NotBlank @Size(max = 2048) @HttpUrl String url,
        @Schema(description = "Código escolhido pelo cliente no lugar do gerado (opcional)",
                example = "minha-promo")
        String alias,
        @Schema(description = "Expiração em ISO-8601 com offset obrigatório (opcional)",
                example = "2026-12-31T23:59:59Z")
        OffsetDateTime expiresAt) {
}
