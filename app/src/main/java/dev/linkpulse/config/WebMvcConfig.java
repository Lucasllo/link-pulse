package dev.linkpulse.config;

import dev.linkpulse.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuração do Spring MVC: registra o rate limit só no path exato {@code /links}.
 *
 * <p>O padrão exato não pega {@code /links/{code}/stats} nem o redirect {@code /{code}}; o
 * interceptor ainda confere o método e só conta o {@code POST} (D-08).
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    /**
     * Cria a configuração.
     *
     * @param rateLimitInterceptor interceptor do rate limit do {@code POST /links}
     */
    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/links");
    }
}
