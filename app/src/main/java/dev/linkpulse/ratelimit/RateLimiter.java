package dev.linkpulse.ratelimit;

import dev.linkpulse.cache.ValkeyAvailability;
import dev.linkpulse.common.IpHasher;
import dev.linkpulse.config.LinkPulseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Rate limit de janela fixa no Valkey (D-08): conta as criações de link de cada IP na janela
 * corrente e recusa as que passam de {@code linkpulse.ratelimit.limit}.
 *
 * <p>A janela é {@code floor(epochMillis / window)} pelo {@link Clock} da aplicação, então o
 * contador zera exatamente no início da janela seguinte. A chave é
 * {@code lp:rl:{hmac do ip}:{índice da janela}}: o IP em claro nunca chega ao Valkey (usa o
 * mesmo HMAC do {@link IpHasher} dos cliques).
 *
 * <p>O contador é um script Lua atômico: {@code INCR} e, quando a chave é nova ou está sem TTL,
 * {@code PEXPIRE} com a janela. Requisições simultâneas nunca passam do limite, e um crash entre
 * os dois comandos não deixa chave eterna.
 *
 * <p><b>Fail-open, por decisão consciente (D-08).</b> Com o Valkey fora (gate de
 * {@link ValkeyAvailability} fechado ou erro no comando), a criação passa sem limite: ninguém é
 * limitado enquanto o Valkey não volta. Disponibilidade da escrita vale mais que o limite; a
 * queda fica visível em {@code linkpulse.ratelimit.requests{result=error}} e em
 * {@code linkpulse.valkey.unavailable}, e o risco está registrado no README.
 *
 * <p>Toda decisão conta em {@code linkpulse.ratelimit.requests} com a tag {@code result}
 * ({@code allowed}, {@code rejected} ou {@code error}); nenhuma tag carrega o IP, o hash nem a
 * chave (D-10). Nada é logado com o IP ou a chave.
 */
@Component
public class RateLimiter {

    /** Prefixo das chaves do rate limit no Valkey. */
    static final String KEY_PREFIX = "lp:rl:";

    private static final RedisScript<Long> FIXED_WINDOW = RedisScript.of(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 or redis.call('PTTL', KEYS[1]) < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class);

    private static final double MILLIS_PER_SECOND = 1000.0;

    /** Nome do contador de decisões do rate limit. */
    static final String REQUESTS_METRIC = "linkpulse.ratelimit.requests";

    private final StringRedisTemplate redis;
    private final ValkeyAvailability availability;
    private final IpHasher ipHasher;
    private final Clock clock;
    private final int limit;
    private final long windowMillis;
    private final Counter allowed;
    private final Counter rejected;
    private final Counter errors;

    /**
     * Resultado da checagem.
     *
     * @param allowed {@code true} se a requisição pode seguir
     * @param retryAfterSeconds segundos até a próxima janela quando recusada; 0 quando aceita
     */
    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    /**
     * Cria o rate limiter e registra os contadores de decisão.
     *
     * @param redis template do Valkey
     * @param availability gate de disponibilidade do Valkey
     * @param ipHasher HMAC do IP usado na chave
     * @param properties propriedades {@code linkpulse.*} ({@code ratelimit.limit} e
     *     {@code ratelimit.window})
     * @param clock relógio da aplicação
     * @param registry registry do Micrometer
     */
    public RateLimiter(StringRedisTemplate redis, ValkeyAvailability availability,
            IpHasher ipHasher, LinkPulseProperties properties, Clock clock,
            MeterRegistry registry) {
        this.redis = redis;
        this.availability = availability;
        this.ipHasher = ipHasher;
        this.clock = clock;
        this.limit = properties.ratelimit().limit();
        this.windowMillis = properties.ratelimit().window().toMillis();
        this.allowed = requests(registry, "allowed");
        this.rejected = requests(registry, "rejected");
        this.errors = requests(registry, "error");
    }

    private static Counter requests(MeterRegistry registry, String result) {
        return Counter.builder(REQUESTS_METRIC)
                .description("Decisões do rate limit do POST /links")
                .tag("result", result)
                .register(registry);
    }

    /**
     * Conta uma criação do IP na janela corrente e decide se ela passa.
     *
     * @param clientIp IP do cliente, já corrigido pelo {@code RemoteIpValve}
     * @return {@code Decision(true, 0)} dentro do limite ou com o Valkey fora (fail-open); senão
     *     {@code Decision(false, n)} com os segundos até a próxima janela
     */
    public Decision check(String clientIp) {
        if (!availability.allowRequest()) {
            errors.increment();
            return new Decision(true, 0);
        }
        long now = clock.millis();
        String key = KEY_PREFIX + ipHasher.hash(clientIp, clock.instant()) + ":"
                + windowIndex(now, windowMillis);
        Long count;
        try {
            count = redis.execute(FIXED_WINDOW, List.of(key), Long.toString(windowMillis));
        } catch (RuntimeException e) {
            // Sem log: a exceção do Lettuce pode citar a chave. A queda já conta no gate.
            availability.recordFailure();
            errors.increment();
            return new Decision(true, 0);
        }
        if (count != null && count > limit) {
            rejected.increment();
            return new Decision(false, retryAfterSeconds(now, windowMillis));
        }
        allowed.increment();
        return new Decision(true, 0);
    }

    /**
     * Índice da janela fixa que contém o instante.
     *
     * @param nowMillis instante em epoch millis
     * @param windowMillis tamanho da janela em ms
     * @return {@code floor(nowMillis / windowMillis)}
     */
    static long windowIndex(long nowMillis, long windowMillis) {
        return Math.floorDiv(nowMillis, windowMillis);
    }

    /**
     * Segundos até o fim da janela que contém o instante, arredondados para cima.
     *
     * @param nowMillis instante em epoch millis
     * @param windowMillis tamanho da janela em ms
     * @return teto dos segundos restantes, nunca menos de 1
     */
    static long retryAfterSeconds(long nowMillis, long windowMillis) {
        long end = (windowIndex(nowMillis, windowMillis) + 1) * windowMillis;
        return Math.max(1, (long) Math.ceil((end - nowMillis) / MILLIS_PER_SECOND));
    }
}
