package dev.linkpulse.click;

import dev.linkpulse.cache.ValkeyAvailability;
import dev.linkpulse.common.IpHasher;
import dev.linkpulse.config.LinkPulseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
 * threads fixas e fila limitada ({@code linkpulse.clicks.publisher.*}). O executor do Boot e a
 * execução assíncrona por anotação ficam de fora porque, com virtual threads, não têm limite nem
 * backpressure. {@link #publish} nunca bloqueia nem lança: o redirect não depende do Valkey.
 *
 * <p>Cada clique termina em um dos resultados de {@code linkpulse.clicks.publish}:
 * <ul>
 *   <li>{@code published}: o {@code XADD} foi aceito pelo Valkey;</li>
 *   <li>{@code rejected}: o clique nem entrou na fila (fila cheia, publisher parado ou tarefas
 *       que sobraram na fila quando o {@code stop()} estourou o prazo);</li>
 *   <li>{@code failed}: a tarefa rodou, mas o Valkey falhou (timeout, conexão recusada) ou o
 *       gate {@link ValkeyAvailability} estava fechado e o {@code XADD} nem foi tentado. Uma falha
 *       fecha o gate, então os cliques seguintes não pagam o timeout de novo.</li>
 * </ul>
 * Os cliques <b>descartados</b> são {@code rejected + failed} (painel da Phase 3). A gauge
 * {@code linkpulse.clicks.publish.queue} mostra quantos cliques esperam na fila.
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

    /** Nome da gauge com o tamanho da fila do publisher. */
    static final String QUEUE_METRIC = "linkpulse.clicks.publish.queue";

    private static final long STOP_TIMEOUT_SECONDS = 5;

    private final StringRedisTemplate redis;
    private final ValkeyAvailability availability;
    private final IpHasher ipHasher;
    private final LinkPulseProperties.Clicks clicks;
    private final Clock clock;
    private final Counter published;
    private final Counter rejected;
    private final Counter failed;
    /** Referência forte: a gauge da fila guarda o executor só por referência fraca. */
    private final ThreadPoolExecutor executor;
    private volatile boolean running;

    /**
     * Cria o publisher, os contadores, a gauge da fila e o executor limitado.
     *
     * @param redis template de strings do Spring Data Redis (Lettuce)
     * @param availability gate de disponibilidade do Valkey, compartilhado com o cache
     * @param ipHasher hasher do IP do cliente
     * @param clicks propriedades {@code linkpulse.clicks.*}
     * @param clock relógio da aplicação (instante do clique)
     * @param registry registry do Micrometer
     */
    public ClickPublisher(StringRedisTemplate redis, ValkeyAvailability availability,
            IpHasher ipHasher, LinkPulseProperties.Clicks clicks, Clock clock,
            MeterRegistry registry) {
        this.redis = redis;
        this.availability = availability;
        this.ipHasher = ipHasher;
        this.clicks = clicks;
        this.clock = clock;
        this.published = publishCounter(registry, "published");
        this.rejected = publishCounter(registry, "rejected");
        this.failed = publishCounter(registry, "failed");
        int threads = clicks.publisher().threads();
        this.executor = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(clicks.publisher().queueCapacity()),
                Thread.ofPlatform().name("click-publisher-", 0).daemon(true).factory(),
                (task, pool) -> rejected.increment());
        Gauge.builder(QUEUE_METRIC, executor, pool -> pool.getQueue().size())
                .description("Cliques na fila do publisher esperando o XADD")
                .register(registry);
    }

    private static Counter publishCounter(MeterRegistry registry, String result) {
        return Counter.builder(PUBLISH_METRIC)
                .description("Publicações de cliques no stream por resultado")
                .tag("result", result)
                .register(registry);
    }

    /**
     * Agenda a publicação de um clique. Nunca bloqueia nem lança: com a fila cheia ou o
     * publisher parado, o clique é descartado e contado como {@code rejected}, sem tarefa nova.
     *
     * @param code código curto
     * @param remoteAddr IP do cliente ({@code getRemoteAddr()}); só sai daqui como hash
     * @param referer header {@code Referer}, ou {@code null}
     * @param userAgent header {@code User-Agent}, ou {@code null}
     */
    public void publish(String code, String remoteAddr, String referer, String userAgent) {
        if (!running) {
            rejected.increment();
            return;
        }
        Instant ts = clock.instant();
        // Fila cheia (ou executor já desligado) cai no handler de rejeição, que só conta.
        executor.execute(() -> send(code, remoteAddr, referer, userAgent, ts));
    }

    private void send(String code, String remoteAddr, String referer, String userAgent,
            Instant ts) {
        if (!availability.allowRequest()) {
            failed.increment();
            return;
        }
        Map<String, String> fields = Map.of(
                FIELD_CODE, code,
                FIELD_TS, Long.toString(ts.toEpochMilli()),
                FIELD_REF, ReferrerNormalizer.toHost(referer),
                FIELD_UA, truncate(userAgent),
                FIELD_IPH, ipHasher.hash(remoteAddr, ts));
        try {
            redis.opsForStream().add(MapRecord.create(clicks.streamKey(), fields),
                    RedisStreamCommands.XAddOptions.maxlen(clicks.maxLen())
                            .approximateTrimming(true));
        } catch (RuntimeException e) {
            // Sem log do clique: IP, user-agent e referer não saem daqui (D-10). A falha vira
            // métrica aqui e em linkpulse.valkey.unavailable.
            availability.recordFailure();
            failed.increment();
            return;
        }
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
     * Para de aceitar cliques e espera até 5 s a fila esvaziar. Se o prazo estourar (ou a
     * espera for interrompida), as tarefas que sobraram na fila são canceladas e contadas como
     * {@code rejected}.
     */
    @Override
    public void stop() {
        running = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                dropPending();
            }
        } catch (InterruptedException e) {
            dropPending();
            Thread.currentThread().interrupt();
        }
    }

    private void dropPending() {
        List<Runnable> pending = executor.shutdownNow();
        rejected.increment(pending.size());
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
