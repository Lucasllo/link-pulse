package dev.linkpulse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.linkpulse.link.TargetHostPolicy;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Testes unitários do {@link LinkPulseProperties} (D-12, WR-01, T-08-04, T-08-05): uma
 * {@code linkpulse.base-url} inválida derruba a subida sem ecoar o valor, e
 * {@code linkpulse.self-hosts} faz binding por propriedade e por variável de ambiente. Rodam sem
 * Docker.
 */
class LinkPulsePropertiesTest {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /** Contexto mínimo, sem {@code linkpulse.base-url}. */
    private final ApplicationContextRunner baseRunner = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationConfig.class)
            .withPropertyValues(
                    "linkpulse.code.alphabet=" + ALPHABET,
                    "linkpulse.code.multiplier=2176477521915");

    private final ApplicationContextRunner contextRunner =
            baseRunner.withPropertyValues("linkpulse.base-url=http://localhost:8080");

    @ParameterizedTest
    @ValueSource(strings = {
        "lnk.example.com",
        "/caminho",
        "ftp://lnk.example.com",
        "mailto:a@b.com",
        "http://user:senha@lnk.example.com",
        "https://lnk.example.com/?x=1",
        "https://lnk.example.com/#topo"
    })
    void contextFailsOnStartupWhenBaseUrlIsInvalid(String baseUrl) {
        contextRunner.withPropertyValues("linkpulse.base-url=" + baseUrl).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("linkpulse.base-url");
        });
    }

    @Test
    void startupFailureDoesNotEchoTheBaseUrl() {
        contextRunner.withPropertyValues("linkpulse.base-url=http://user:senha@lnk.example.com")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalArgumentException.class)
                            .hasMessageNotContaining("senha");
                    for (Throwable cause = context.getStartupFailure(); cause != null;
                            cause = cause.getCause()) {
                        assertThat(String.valueOf(cause.getMessage())).doesNotContain("senha");
                    }
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost:8080",
        "https://lnk.example.com",
        "HTTPS://LNK.EXAMPLE.COM/"
    })
    void contextStartsWithValidBaseUrl(String baseUrl) {
        contextRunner.withPropertyValues("linkpulse.base-url=" + baseUrl)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void selfHostsBindFromCommaSeparatedProperty() {
        contextRunner.withPropertyValues("linkpulse.self-hosts=lb.example.com,alias.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LinkPulseProperties.class).selfHosts())
                            .containsExactly("lb.example.com", "alias.example.com");
                    TargetHostPolicy policy = context.getBean(TargetHostPolicy.class);
                    assertThatThrownBy(() -> policy.check("https://lb.example.com./x"))
                            .isInstanceOfSatisfying(ErrorResponseException.class, e ->
                                    assertThat(e.getStatusCode())
                                            .isEqualTo(HttpStatus.BAD_REQUEST));
                });
    }

    @Test
    void selfHostsDefaultToEmpty() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LinkPulseProperties.class).selfHosts()).isEmpty();
        });
    }

    @Test
    void environmentVariablesBindBaseUrlAndSelfHosts() {
        Map<String, Object> env = Map.of(
                "LINKPULSE_BASE_URL", "https://lnk.example.com",
                "LINKPULSE_SELF_HOSTS", "lb.example.com,alias.example.com");
        baseRunner.withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                env)))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    LinkPulseProperties properties = context.getBean(LinkPulseProperties.class);
                    assertThat(properties.baseUrl())
                            .isEqualTo(URI.create("https://lnk.example.com"));
                    assertThat(properties.selfHosts())
                            .containsExactly("lb.example.com", "alias.example.com");
                });
    }
}
