package dev.linkpulse.click;

import dev.linkpulse.common.IpHasher;
import dev.linkpulse.config.LinkPulseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Publica cada clique no Redis Stream ({@code linkpulse.clicks.stream-key}, padrão
 * {@code lp:clicks}) sem que o redirect espere (CLICK-01).
 *
 * <p>A thread do request só captura o instante e entrega a tarefa a um executor próprio, com
 * threads fixas e fila limitada ({@code linkpulse.clicks.publisher.*}). Fila cheia não bloqueia:
 * o clique é descartado e contado em {@code linkpulse.clicks.publish{result=rejected}}. O executor
 * do Boot e a execução assíncrona por anotação ficam de fora porque, com virtual threads, não têm
 * limite nem backpressure.
 *
 * <p>Cada entrada tem os campos {@code code}, {@code ts} (epoch ms do clique), {@code ref} (só o
 * host do {@code Referer}, ou {@code (direct)}, ver {@link ReferrerNormalizer}), {@code ua}
 * (user-agent truncado em 512 caracteres, vazio quando ausente) e {@code iph} (HMAC do IP, ver
 * {@link IpHasher}). O {@code XADD} sai com {@code MAXLEN ~ linkpulse.clicks.max-len}, então o
 * stream não cresce sem limite.
 *
 * <p>O IP cru e o {@code Referer} completo só vivem na memória da fila: viram hash e host na
 * thread do publisher, antes do {@code XADD}, e nunca são publicados, logados nem usados como tag
 * de métrica (D-10).
 *
 * <p>Fase do ciclo de vida: {@code DEFAULT_PHASE - 4096}. O publisher para depois do web server
 * (que para de aceitar requests antes) e antes da conexão Lettuce (fase 0), então a fila é
 * esvaziada com o Valkey ainda conectado.
 */
public class ClickPublisher implements SmartLifecycle {

    /** Campo do stream com o código curto. */
    public static final String FIELD_CODE = "code";

    /** Campo do stream com o instante do clique, em epoch ms (texto). */
    public static final String FIELD_TS = "ts";

    /** Campo do stream com o host do Referer ou {@code (direct)}. */
    public static final String FIELD_REF = "ref";

    /** Campo do stream com o user-agent truncado (pode ser vazio). */
    public static final String FIELD_UA = "ua";

    /** Campo do stream com o HMAC do IP. */
    public static final String FIELD_IPH = "iph";

    /** Tamanho máximo do user-agent publicado. */
    static final int MAX_USER_AGENT = 512;

    /** Nome do contador de publicações. */
    static final String PUBLISH_METRIC = "linkpulse.clicks.publish";

    private static final long STOP_TIMEOUT_SECONDS = 5;

    private final StringRedisTemplate redis;
    private final IpHasher ipHasher;
    private final LinkPulseProperties.Clicks clicks;
    private final Clock clock;
    private final Counter published;
    private final Counter rejected;
    private final ThreadPoolExecutor executor;
    private volatile boolean running;

    /**
     * Cria o publisher, os contadores e o executor limitado.
     *
     * @param redis template de strings do Spring Data Redis (Lettuce)
     * @param ipHasher hasher do IP do cliente
     * @param clicks propriedades {@code linkpulse.clicks.*}
     * @param clock relógio da aplicação (instante do clique)
     * @param registry registry do Micrometer
     */
    public ClickPublisher(StringRedisTemplate redis, IpHasher ipHasher,
            LinkPulseProperties.Clicks clicks, Clock clock, MeterRegistry registry) {
        this.redis = redis;
        this.ipHasher = ipHasher;
        this.clicks = clicks;
        this.clock = clock;
        this.published = publishCounter(registry, "published");
        this.rejected = publishCounter(registry, "rejected");
        int threads = clicks.publisher().threads();
        this.executor = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(clicks.publisher().queueCapacity()),
                Thread.ofPlatform().name("click-publisher-", 0).daemon(true).factory(),
                (task, pool) -> rejected.increment());
    }

    private static Counter publishCounter(MeterRegistry registry, String result) {
        return Counter.builder(PUBLISH_METRIC)
                .description("Publicações de cliques no stream por resultado")
                .tag("result", result)
                .register(registry);
    }

    /**
     * Agenda a publicação de um clique. Nunca bloqueia nem lança: com a fila cheia (ou o
     * executor parado), o clique é descartado e contado como {@code rejected}.
     *
     * @param code código curto
     * @param remoteAddr IP do cliente ({@code getRemoteAddr()}); só sai daqui como hash
     * @param referer header {@code Referer}, ou {@code null}
     * @param userAgent header {@code User-Agent}, ou {@code null}
     */
    public void publish(String code, String remoteAddr, String referer, String userAgent) {
        Instant ts = clock.instant();
        executor.execute(() -> send(code, remoteAddr, referer, userAgent, ts));
    }

    private void send(String code, String remoteAddr, String referer, String userAgent,
            Instant ts) {
        Map<String, String> fields = Map.of(
                FIELD_CODE, code,
                FIELD_TS, Long.toString(ts.toEpochMilli()),
                FIELD_REF, ReferrerNormalizer.toHost(referer),
                FIELD_UA, truncate(userAgent),
                FIELD_IPH, ipHasher.hash(remoteAddr, ts));
        redis.opsForStream().add(MapRecord.create(clicks.streamKey(), fields),
                RedisStreamCommands.XAddOptions.maxlen(clicks.maxLen()).approximateTrimming(true));
        published.increment();
    }

    private static String truncate(String userAgent) {
        if (userAgent == null) {
            return "";
        }
        return userAgent.length() > MAX_USER_AGENT
                ? userAgent.substring(0, MAX_USER_AGENT)
                : userAgent;
    }

    @Override
    public void start() {
        running = true;
    }

    /**
     * Para de aceitar cliques e espera até 5 s a fila esvaziar.
     */
    @Override
    public void stop() {
        running = false;
        executor.shutdown();
        try {
            executor.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE - 4096;
    }
}
