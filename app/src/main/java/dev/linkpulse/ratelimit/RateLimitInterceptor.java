package dev.linkpulse.ratelimit;

import dev.linkpulse.config.LinkPulseProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica o rate limit ao {@code POST /links} (D-08). O registro no path exato {@code /links}
 * fica no {@code WebMvcConfig}; aqui só o método {@code POST} é contado.
 *
 * <p>O IP do cliente vem <b>só</b> de {@link HttpServletRequest#getRemoteAddr()}. Quem decide
 * se um proxy é confiável é o {@code RemoteIpValve} do Tomcat
 * ({@code server.forward-headers-strategy=native} com
 * {@code server.tomcat.remoteip.internal-proxies}): ele troca o endereço remoto pelo cliente
 * real só quando a conexão vem de um proxy listado. O código da aplicação nunca lê header de
 * proxy, então um cliente que fala direto com a aplicação não burla o limite forjando esse
 * header.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final boolean enabled;

    /**
     * Cria o interceptor.
     *
     * @param rateLimiter contador da janela fixa
     * @param properties propriedades {@code linkpulse.*} ({@code ratelimit.enabled})
     */
    public RateLimitInterceptor(RateLimiter rateLimiter, LinkPulseProperties properties) {
        this.rateLimiter = rateLimiter;
        this.enabled = properties.ratelimit().enabled();
    }

    /**
     * Conta o {@code POST} e recusa com 429 acima do limite.
     *
     * @param request requisição; o IP sai só de {@code getRemoteAddr()}
     * @param response resposta (não usada)
     * @param handler handler escolhido (não usado)
     * @return {@code true} quando a requisição pode seguir
     * @throws org.springframework.web.ErrorResponseException 429 {@code /problems/rate-limited}
     *     com {@code Retry-After}, tratado pelo {@code GlobalExceptionHandler}
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        if (!enabled || !"POST".equals(request.getMethod())) {
            return true;
        }
        RateLimiter.Decision decision = rateLimiter.check(request.getRemoteAddr());
        if (!decision.allowed()) {
            throw RateLimitProblems.tooManyRequests(decision.retryAfterSeconds());
        }
        return true;
    }
}
