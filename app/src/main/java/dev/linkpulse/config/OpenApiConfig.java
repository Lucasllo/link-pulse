package dev.linkpulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da especificação OpenAPI publicada pelo springdoc em {@code /v3/api-docs} e na
 * Swagger UI ({@code /swagger-ui.html}).
 *
 * <p>Os endpoints são descritos pelas anotações {@code @Tag}, {@code @Operation} e
 * {@code @ApiResponse} nos controllers; aqui fica só o cabeçalho da API.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /**
     * Cabeçalho ({@code info}) da especificação.
     *
     * @return o documento OpenAPI base, completado pelo springdoc com os paths
     */
    @Bean
    OpenAPI linkPulseOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Link Pulse API")
                .version("0.1.0")
                .description("Encurtador de URLs: cria links curtos (gerados ou com alias) e "
                        + "redireciona para a URL original com 302. Os erros seguem a RFC 9457 "
                        + "(Problem Details, application/problem+json)."));
    }
}
