package dev.linkpulse.click;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Consumer group que lê o stream de cliques em lote e grava no MongoDB (CLICK-02, CLICK-03).
 *
 * <p>Loop próprio numa virtual thread (D-07): {@code XREADGROUP GROUP <group> <consumer>
 * COUNT <batchSize> BLOCK <block> STREAMS <stream> >}, conversão de cada entrada em
 * {@link ClickDocument}, um único {@link ClickEventWriter#write} para o lote e, <b>só depois</b>
 * que a escrita volta, {@code XACK} de todos os IDs do lote (válidos e inválidos). Se a escrita
 * lança, nada é confirmado e o lote fica no PEL do grupo para ser entregue de novo. O container
 * de listeners de stream do Spring Data Redis não serve aqui porque entrega uma mensagem por vez.
 *
 * <p>Antes da primeira leitura, o loop garante os índices de {@code click_events} com
 * {@link ClickIndexes#ensure()} (D-07, DATA-02). Uma falha (MongoDB fora) cai no mesmo
 * tratamento de erro do resto do ciclo e é tentada de novo depois do backoff, sem ler o stream
 * antes disso. Nada disso roda no construtor nem na inicialização do bean: com o MongoDB fora,
 * a aplicação ainda sobe e redireciona.
 *
 * <p>O loop nunca morre: qualquer {@link RuntimeException} conta em
 * {@code linkpulse.clicks.consumer.errors} e espera um backoff de 500 ms, que dobra até 10 s e
 * volta a zero no primeiro ciclo bem-sucedido. {@code NOGROUP} (stream apagado ou expulso da
 * memória) faz o grupo ser recriado no ciclo seguinte. O log de erro leva só a classe da
 * exceção e o nome do stream, nunca o conteúdo das entradas.
 *
 * <p>Métricas (sem tags de alta cardinalidade, D-10):
 * <ul>
 *   <li>{@code linkpulse.clicks.persisted}: documentos novos gravados;</li>
 *   <li>{@code linkpulse.clicks.duplicates}: documentos que já existiam (entrega repetida);</li>
 *   <li>{@code linkpulse.clicks.invalid}: entradas descartadas por formato inválido;</li>
 *   <li>{@code linkpulse.clicks.consumer.batch}: tamanho de cada lote lido;</li>
 *   <li>{@code linkpulse.clicks.consumer.write}: duração da escrita, com
 *       {@code outcome=success|failure};</li>
 *   <li>{@code linkpulse.clicks.consumer.errors}: ciclos do loop que falharam.</li>
 * </ul>
 *
 * <p>Ciclo de vida: fase {@code DEFAULT_PHASE - 8192}, então o consumer para depois do web
 * server e do {@code ClickPublisher} ({@code DEFAULT_PHASE - 4096}) e antes da conexão Lettuce
 * do Boot (fase 0). O {@code stop} não interrompe a thread: baixa a flag, e o loop sai depois do
 * lote corrente (a espera é limitada pelo {@code BLOCK}). O consumer é dono da fábrica de
 * conexões recebida e a destrói ao parar; por isso não pode ser reiniciado depois do stop.
 */
public class ClickStreamConsumer implements SmartLifecycle, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(ClickStreamConsumer.class);

    private static final long INITIAL_BACKOFF_MILLIS = 500;
    private static final long MAX_BACKOFF_MILLIS = 10_000;
    private static final long SLEEP_SLICE_MILLIS = 100;
    private static final Duration STOP_GRACE = Duration.ofSeconds(10);
    private static final int MAX_CAUSE_DEPTH = 16;

    private final Settings settings;
    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redis;
    private final ClickEventWriter writer;
    private final ClickIndexes indexes;
    private final Counter persisted;
    private final Counter duplicates;
    private final Counter invalid;
    private final Counter errors;
    private final DistributionSummary batchSizes;
    private final Timer writeSuccess;
    private final Timer writeFailure;
    private final AtomicReference<Runnable> stopCallback = new AtomicReference<>();
    private final AtomicBoolean factoryReleased = new AtomicBoolean();
    private volatile boolean running;
    private volatile boolean loopDone = true;
    private volatile boolean groupReady;
    private volatile boolean indexesReady;
    private volatile Thread worker;

    /**
     * Configuração do consumer.
     *
     * @param streamKey chave do stream ({@code lp:clicks})
     * @param group nome do consumer group ({@code click-writers})
     * @param consumerName nome deste consumer dentro do grupo (um por instância)
     * @param batchSize máximo de entradas por leitura ({@code COUNT})
     * @param block espera máxima da leitura ({@code BLOCK})
     */
    public record Settings(String streamKey, String group, String consumerName, int batchSize,
            Duration block) {
    }

    /**
     * Cria o consumer e registra as métricas. A thread só sobe no {@link #start()}.
     *
     * @param settings configuração do consumer
     * @param connectionFactory fábrica de conexões dedicada, com timeout acima do {@code BLOCK}
     *     (ver {@link #dedicatedConnectionFactory}); o consumer passa a ser dono dela
     * @param writer gravação idempotente do lote
     * @param indexes índices da coleção de destino, garantidos no loop (nunca aqui)
     * @param registry registry do Micrometer
     */
    public ClickStreamConsumer(Settings settings, RedisConnectionFactory connectionFactory,
            ClickEventWriter writer, ClickIndexes indexes, MeterRegistry registry) {
        this.settings = settings;
        this.connectionFactory = connectionFactory;
        this.redis = new StringRedisTemplate(connectionFactory);
        this.writer = writer;
        this.indexes = indexes;
        this.persisted = Counter.builder("linkpulse.clicks.persisted")
                .description("Cliques novos gravados em click_events")
                .register(registry);
        this.duplicates = Counter.builder("linkpulse.clicks.duplicates")
                .description("Cliques ignorados por já existirem em click_events")
                .register(registry);
        this.invalid = Counter.builder("linkpulse.clicks.invalid")
                .description("Entradas do stream descartadas por formato inválido")
                .register(registry);
        this.errors = Counter.builder("linkpulse.clicks.consumer.errors")
                .description("Ciclos do consumer de cliques que falharam")
                .register(registry);
        this.batchSizes = DistributionSummary.builder("linkpulse.clicks.consumer.batch")
                .description("Entradas por lote lido do stream")
                .register(registry);
        this.writeSuccess = writeTimer(registry, "success");
        this.writeFailure = writeTimer(registry, "failure");
    }

    private static Timer writeTimer(MeterRegistry registry, String outcome) {
        return Timer.builder("linkpulse.clicks.consumer.write")
                .description("Duração da gravação de um lote de cliques no MongoDB")
                .tag("outcome", outcome)
                .register(registry);
    }

    /**
     * Cria a fábrica Lettuce do consumer, fora do contexto Spring.
     *
     * <p>Pitfall P4: a leitura bloqueante ({@code XREADGROUP ... BLOCK}) espera no máximo o
     * {@code commandTimeout} da conexão. O {@code spring.data.redis.timeout} de 150 ms da 02-03,
     * bom para o fail-open do redirect, faria toda leitura de 2 s terminar em timeout e deixaria
     * o consumer em backoff permanente. Esta fábrica usa {@code block + 3 s}.
     *
     * <p>Não pode virar bean: um segundo {@code RedisConnectionFactory} no contexto desligaria a
     * auto-configuração do Lettuce do Boot. A conexão é sem TLS, como no compose e no kind.
     *
     * @param details host, porta, banco e credenciais do Valkey (vindos das propriedades ou do
     *     {@code @ServiceConnection} dos testes)
     * @param block espera máxima da leitura bloqueante
     * @return a fábrica já inicializada
     * @throws IllegalStateException se o Valkey não está em modo standalone
     */
    public static LettuceConnectionFactory dedicatedConnectionFactory(
            DataRedisConnectionDetails details, Duration block) {
        DataRedisConnectionDetails.Standalone standalone = details.getStandalone();
        if (standalone == null) {
            throw new IllegalStateException(
                    "O consumer de cliques exige o Valkey em modo standalone");
        }
        RedisStandaloneConfiguration server =
                new RedisStandaloneConfiguration(standalone.getHost(), standalone.getPort());
        server.setDatabase(standalone.getDatabase());
        if (details.getUsername() != null) {
            server.setUsername(details.getUsername());
        }
        if (details.getPassword() != null) {
            server.setPassword(RedisPassword.of(details.getPassword()));
        }
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(block.plusSeconds(3))
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(server, client);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * Sobe o loop numa virtual thread chamada {@code click-consumer}.
     *
     * <p>No Java 21, uma virtual thread que bloqueia segurando um monitor intrínseco
     * ({@code synchronized}) prende a thread carregadora (pinning, resolvido só no JEP 491 do
     * Java 24). Por isso o loop só usa campos {@code volatile} e atômicos, sem
     * {@code synchronized}.
     */
    @Override
    public void start() {
        loopDone = false;
        running = true;
        worker = Thread.ofVirtual().name("click-consumer").start(this::loop);
    }

    private void loop() {
        try {
            long backoffMillis = 0;
            while (running) {
                try {
                    ensureIndexes();
                    ensureGroup();
                    List<MapRecord<String, Object, Object>> batch = readBatch();
                    if (batch != null && !batch.isEmpty()) {
                        process(batch);
                    }
                    backoffMillis = 0;
                } catch (RuntimeException e) {
                    errors.increment();
                    if (causeContains(e, "NOGROUP")) {
                        groupReady = false;
                    }
                    LOG.warn("Falha no consumer de cliques do stream {}: {}",
                            settings.streamKey(), e.getClass().getName());
                    backoffMillis = backoffMillis == 0
                            ? INITIAL_BACKOFF_MILLIS
                            : Math.min(backoffMillis * 2, MAX_BACKOFF_MILLIS);
                    sleepWhileRunning(backoffMillis);
                }
            }
        } finally {
            loopDone = true;
            finishStopIfRequested();
        }
    }

    /**
     * Lê o próximo lote do grupo: só entradas nunca entregues ({@code >}), até
     * {@code batchSize}, esperando até {@code block}.
     *
     * @return as entradas lidas; vazio ou {@code null} quando o {@code BLOCK} venceu sem nada
     */
    // O read recebe StreamOffset<K>... (varargs genérico): o aviso é só da criação do array.
    @SuppressWarnings("unchecked")
    private List<MapRecord<String, Object, Object>> readBatch() {
        return redis.opsForStream().read(
                Consumer.from(settings.group(), settings.consumerName()),
                StreamReadOptions.empty().count(settings.batchSize()).block(settings.block()),
                StreamOffset.create(settings.streamKey(), ReadOffset.lastConsumed()));
    }

    /**
     * Garante os índices de {@code click_events} uma vez por vida do consumer. Se o
     * {@link ClickIndexes#ensure()} lança, a flag não sobe e o próximo ciclo tenta de novo.
     */
    private void ensureIndexes() {
        if (indexesReady) {
            return;
        }
        indexes.ensure();
        indexesReady = true;
    }

    /**
     * Cria o grupo na primeira passada (e de novo depois de um {@code NOGROUP}), a partir do
     * início do stream e com {@code MKSTREAM}. {@code BUSYGROUP} significa que o grupo já existe.
     */
    private void ensureGroup() {
        if (groupReady) {
            return;
        }
        try {
            redis.opsForStream().createGroup(settings.streamKey(), ReadOffset.from("0"),
                    settings.group());
        } catch (RuntimeException e) {
            if (!causeContains(e, "BUSYGROUP")) {
                throw e;
            }
        }
        groupReady = true;
    }

    /**
     * Grava o lote e só depois confirma todas as entradas dele. Se o {@code write} lança, a
     * exceção sobe sem {@code XACK}, e o lote inteiro fica no PEL.
     *
     * @param batch entradas lidas do stream
     */
    private void process(List<MapRecord<String, Object, Object>> batch) {
        List<ClickDocument> documents = new ArrayList<>(batch.size());
        RecordId[] ids = new RecordId[batch.size()];
        for (int i = 0; i < ids.length; i++) {
            MapRecord<String, Object, Object> entry = batch.get(i);
            ids[i] = entry.getId();
            ClickDocument.fromStream(entry.getId().getValue(), entry.getValue())
                    .ifPresent(documents::add);
        }
        batchSizes.record(batch.size());

        long start = System.nanoTime();
        int duplicated;
        try {
            duplicated = writer.write(documents);
        } catch (RuntimeException e) {
            writeFailure.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            throw e;
        }
        writeSuccess.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        persisted.increment(documents.size() - duplicated);
        duplicates.increment(duplicated);

        redis.opsForStream().acknowledge(settings.streamKey(), settings.group(), ids);
        invalid.increment(batch.size() - documents.size());
    }

    private void sleepWhileRunning(long millis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (running && System.nanoTime() < deadline) {
            try {
                Thread.sleep(SLEEP_SLICE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static boolean causeContains(Throwable error, String token) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            String message = current.getMessage();
            if (message != null && message.contains(token)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Para de ler e espera a thread sair por até {@code block + 10 s}.
     */
    @Override
    public void stop() {
        stop(() -> { });
        Thread thread = worker;
        if (thread != null) {
            try {
                thread.join(settings.block().plus(STOP_GRACE));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Baixa a flag e devolve na hora. Quando o loop sai, depois do lote corrente, a fábrica de
     * conexões é destruída e o {@code callback} roda. Se a thread nunca subiu, isso acontece já.
     *
     * @param callback aviso ao Spring de que o consumer parou
     */
    @Override
    public void stop(Runnable callback) {
        running = false;
        stopCallback.set(callback);
        finishStopIfRequested();
    }

    /**
     * Roda o callback do stop uma única vez, quando o loop já saiu e o stop já foi pedido; as
     * duas threads chamam este método, e o {@code getAndSet} garante que só uma o executa.
     */
    private void finishStopIfRequested() {
        if (!loopDone) {
            return;
        }
        Runnable callback = stopCallback.getAndSet(null);
        if (callback != null) {
            releaseFactory();
            callback.run();
        }
    }

    /**
     * Destrói a fábrica de conexões se o consumer for destruído sem ter subido.
     */
    @Override
    public void destroy() {
        releaseFactory();
    }

    private void releaseFactory() {
        if (factoryReleased.compareAndSet(false, true)
                && connectionFactory instanceof DisposableBean disposable) {
            try {
                disposable.destroy();
            } catch (Exception e) {
                LOG.warn("Falha ao fechar a conexão do consumer de cliques: {}",
                        e.getClass().getName());
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE - 8192;
    }
}
