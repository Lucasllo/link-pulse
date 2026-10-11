package dev.linkpulse.click;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.context.Lifecycle;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamInfo;
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
 * <p>Entrega at-least-once (CLICK-03, CLICK-04): o {@code XACK} vem sempre depois da gravação.
 * Um lote cuja escrita falhou fica no PEL (lista de pendentes do grupo) e não volta pela leitura
 * com {@code >}, que só entrega entradas novas. Quem o traz de volta é o reclaim: a cada
 * {@code reclaimInterval}, o loop pede ao grupo as entradas entregues há pelo menos
 * {@code minIdle} sem {@code XACK} ({@code XPENDING ... IDLE}), assume-as com {@code XCLAIM} e as
 * passa pelo mesmo caminho gravação → {@code XACK}. Isso vale para os pendentes de um consumer
 * morto (outra instância, ou a mesma reiniciada com outro nome) e para os do próprio consumer.
 * Reprocessar nunca duplica: o {@code _id} do documento é o ID da entrada no stream e o writer
 * ignora duplicate key (D-05). O comando {@code XAUTOCLAIM} não existe na API do Spring Data
 * Redis; {@code XPENDING IDLE} + {@code XCLAIM} dão o mesmo resultado.
 *
 * <p>Nenhuma entrada válida é descartada por número de reentregas: com o MongoDB fora por muito
 * tempo, os cliques esperam no PEL e voltam quando ele volta. As únicas que saem sem documento
 * são as que não existem mais no stream (aparadas pelo {@code MAXLEN ~} durante um backlog
 * grande, Pitfall P9): o reclaim as confirma e as conta em {@code linkpulse.clicks.lost}.
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
 * exceção e o nome do stream, nunca o conteúdo das entradas: WARN na primeira falha de uma
 * sequência e INFO, com o número de falhas, no primeiro ciclo bem-sucedido depois dela.
 *
 * <p>Métricas (sem tags de alta cardinalidade, D-10):
 * <ul>
 *   <li>{@code linkpulse.clicks.persisted}: documentos novos gravados;</li>
 *   <li>{@code linkpulse.clicks.duplicates}: documentos que já existiam (entrega repetida);</li>
 *   <li>{@code linkpulse.clicks.invalid}: entradas descartadas por formato inválido;</li>
 *   <li>{@code linkpulse.clicks.claimed}: entradas pendentes assumidas pelo reclaim;</li>
 *   <li>{@code linkpulse.clicks.lost}: entradas pendentes que já não existiam no stream e foram
 *       confirmadas sem documento;</li>
 *   <li>{@code linkpulse.clicks.consumer.batch}: tamanho de cada lote lido;</li>
 *   <li>{@code linkpulse.clicks.consumer.write}: duração da escrita, com
 *       {@code outcome=success|failure};</li>
 *   <li>{@code linkpulse.clicks.consumer.errors}: ciclos do loop que falharam;</li>
 *   <li>{@code linkpulse.clicks.stream.pending} (gauge): entradas entregues ao grupo e ainda sem
 *       {@code XACK} ({@code XINFO GROUPS}, campo {@code pending});</li>
 *   <li>{@code linkpulse.clicks.stream.lag} (gauge): entradas do stream que o grupo ainda não
 *       leu ({@code XINFO GROUPS}, campo {@code lag}); {@code NaN} quando o servidor devolve
 *       {@code null} (Pitfall P10);</li>
 *   <li>{@code linkpulse.clicks.stream.length} (gauge): tamanho do stream ({@code XLEN}).</li>
 * </ul>
 *
 * <p>As três gauges são do <b>grupo</b>, e não da réplica: todas as instâncias leem o mesmo
 * {@code XINFO GROUPS} e expõem o mesmo valor. No dashboard e no alerta de lag, agregar com
 * {@code max()}, nunca com {@code sum()} (Pitfall P11). O loop as atualiza no máximo uma vez
 * por intervalo de {@code BLOCK}; o scrape só lê os valores em memória e nunca consulta o
 * Valkey. Uma falha na atualização mantém os valores anteriores e não conta como erro do
 * consumer.
 *
 * <p>Ciclo de vida (CLICK-05, Pitfall P14): fases maiores param primeiro, então a ordem de
 * parada é web server (graceful: para de aceitar requests e espera os em andamento) →
 * {@code ClickPublisher} ({@code DEFAULT_PHASE - 4096}: esvazia a fila de XADD) → este consumer
 * ({@code DEFAULT_PHASE - 8192}) → {@code LettuceConnectionFactory} do Boot (fase 0). O
 * {@code stop} não interrompe a thread: baixa a flag, e o loop sai depois do lote corrente,
 * já gravado e confirmado. Interromper no meio do {@code write} perderia o {@code XACK} e faria
 * o lote ser reentregue; a espera fica limitada pelo {@code BLOCK} da leitura somado ao timeout
 * de escrita do MongoDB, abaixo do {@code spring.lifecycle.timeout-per-shutdown-phase} (30 s).
 * O consumer é dono da fábrica de conexões recebida: para a fábrica (fecha as conexões) só
 * depois de o loop sair, abre-a de novo no {@code start} e só a destrói no {@code destroy} do
 * bean. Assim um stop seguido de start funciona, como na pausa e retomada dos contextos em
 * cache dos testes do Spring Framework 7.
 */
public class ClickStreamConsumer implements SmartLifecycle, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(ClickStreamConsumer.class);

    private static final long INITIAL_BACKOFF_MILLIS = 500;
    private static final long MAX_BACKOFF_MILLIS = 10_000;
    private static final long SLEEP_SLICE_MILLIS = 100;
    private static final Duration STOP_GRACE = Duration.ofSeconds(10);
    private static final int MAX_CAUSE_DEPTH = 16;
    private static final int MAX_RECLAIM_PAGES = 20;

    private final Settings settings;
    private final RedisConnectionFactory connectionFactory;
    private final StringRedisTemplate redis;
    private final ClickEventWriter writer;
    private final ClickIndexes indexes;
    private final Clock clock;
    private final Counter persisted;
    private final Counter duplicates;
    private final Counter invalid;
    private final Counter errors;
    private final Counter claimed;
    private final Counter lost;
    private final DistributionSummary batchSizes;
    private final Timer writeSuccess;
    private final Timer writeFailure;
    // Referências fortes: o registry guarda só referência fraca ao objeto da gauge (A10).
    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong streamLength = new AtomicLong();
    private final AtomicLong lagBits = new AtomicLong(Double.doubleToLongBits(Double.NaN));
    private final AtomicReference<Runnable> stopCallback = new AtomicReference<>();
    private volatile boolean running;
    private volatile boolean loopDone = true;
    private volatile boolean groupReady;
    private volatile boolean indexesReady;
    private volatile long nextReclaimAt;
    private volatile long nextGaugeRefreshAt;
    private volatile Thread worker;

    /**
     * Configuração do consumer.
     *
     * @param streamKey chave do stream ({@code lp:clicks})
     * @param group nome do consumer group ({@code click-writers})
     * @param consumerName nome deste consumer dentro do grupo (um por instância)
     * @param batchSize máximo de entradas por leitura ({@code COUNT})
     * @param block espera máxima da leitura ({@code BLOCK})
     * @param reclaimInterval intervalo entre as buscas de pendentes parados no grupo
     * @param minIdle tempo mínimo sem {@code XACK} para uma entrada pendente ser assumida
     */
    public record Settings(String streamKey, String group, String consumerName, int batchSize,
            Duration block, Duration reclaimInterval, Duration minIdle) {
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
     * @param clock relógio que agenda o reclaim
     */
    public ClickStreamConsumer(Settings settings, RedisConnectionFactory connectionFactory,
            ClickEventWriter writer, ClickIndexes indexes, MeterRegistry registry, Clock clock) {
        this.settings = settings;
        this.connectionFactory = connectionFactory;
        this.redis = new StringRedisTemplate(connectionFactory);
        this.writer = writer;
        this.indexes = indexes;
        this.clock = clock;
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
        this.claimed = Counter.builder("linkpulse.clicks.claimed")
                .description("Entradas pendentes do grupo assumidas pelo reclaim (XCLAIM)")
                .register(registry);
        this.lost = Counter.builder("linkpulse.clicks.lost")
                .description("Entradas pendentes apagadas do stream antes da gravação")
                .register(registry);
        this.batchSizes = DistributionSummary.builder("linkpulse.clicks.consumer.batch")
                .description("Entradas por lote lido do stream")
                .register(registry);
        this.writeSuccess = writeTimer(registry, "success");
        this.writeFailure = writeTimer(registry, "failure");
        Gauge.builder("linkpulse.clicks.stream.pending", pendingCount, AtomicLong::get)
                .description("Entradas entregues ao grupo e ainda sem XACK (valor do grupo)")
                .register(registry);
        Gauge.builder("linkpulse.clicks.stream.length", streamLength, AtomicLong::get)
                .description("Tamanho do stream de cliques (XLEN)")
                .register(registry);
        Gauge.builder("linkpulse.clicks.stream.lag", lagBits,
                        bits -> Double.longBitsToDouble(bits.get()))
                .description("Entradas do stream ainda não lidas pelo grupo (valor do grupo)")
                .register(registry);
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
     *
     * <p>Depois de um stop, a fábrica de conexões está parada; ela é aberta de novo aqui antes
     * de a thread subir. Numa fábrica já aberta, o {@code start} dela não faz nada.
     */
    @Override
    public void start() {
        if (connectionFactory instanceof Lifecycle lifecycle) {
            lifecycle.start();
        }
        loopDone = false;
        running = true;
        worker = Thread.ofVirtual().name("click-consumer").start(this::loop);
    }

    /**
     * Corpo da thread do consumer. Todo o ciclo (índices, grupo, reclaim, leitura e gravação)
     * fica dentro de um único {@code catch (RuntimeException)}: uma falha do Valkey ou do MongoDB
     * conta em {@code linkpulse.clicks.consumer.errors}, dorme o backoff em fatias (o stop não
     * espera o backoff inteiro) e tenta de novo. Só a primeira falha de uma sequência vai para o
     * log em WARN, e o primeiro ciclo bem-sucedido depois dela registra em INFO quantas falhas
     * houve: uma queda longa do MongoDB não inunda o log. Os logs levam só o nome do stream e a
     * classe da exceção; a mensagem pode trazer conteúdo das entradas.
     */
    private void loop() {
        try {
            long backoffMillis = 0;
            long consecutiveFailures = 0;
            while (running) {
                refreshGaugesIfDue();
                try {
                    ensureIndexes();
                    ensureGroup();
                    reclaimIfDue();
                    List<MapRecord<String, Object, Object>> batch = readBatch();
                    if (batch != null && !batch.isEmpty()) {
                        process(batch);
                    }
                    if (consecutiveFailures > 0) {
                        LOG.info("Consumer de cliques do stream {} recuperado depois de {} "
                                + "falhas seguidas", settings.streamKey(), consecutiveFailures);
                    }
                    consecutiveFailures = 0;
                    backoffMillis = 0;
                } catch (RuntimeException e) {
                    errors.increment();
                    if (causeContains(e, "NOGROUP")) {
                        groupReady = false;
                    }
                    if (consecutiveFailures == 0) {
                        LOG.warn("Falha no consumer de cliques do stream {}: {}; tentando de novo "
                                + "com backoff", settings.streamKey(), e.getClass().getName());
                    }
                    consecutiveFailures++;
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
     * Atualiza as gauges do grupo e do stream no máximo uma vez por intervalo de {@code BLOCK},
     * com {@code XINFO GROUPS} e {@code XLEN}. Roda na thread do loop, nunca no scrape. Uma
     * falha (Valkey fora, stream ainda inexistente) mantém os valores anteriores, vai para o log
     * em DEBUG e não conta em {@code linkpulse.clicks.consumer.errors}.
     */
    private void refreshGaugesIfDue() {
        long now = clock.millis();
        if (now < nextGaugeRefreshAt) {
            return;
        }
        nextGaugeRefreshAt = now + settings.block().toMillis();
        try {
            StreamInfo.XInfoGroups groups = redis.opsForStream().groups(settings.streamKey());
            if (groups != null) {
                groups.stream()
                        .filter(group -> settings.group().equals(group.groupName()))
                        .findFirst()
                        .ifPresent(group -> {
                            Long pending = group.pendingCount();
                            pendingCount.set(pending == null ? 0 : pending);
                            lagBits.set(Double.doubleToLongBits(
                                    lagValue(group.getRaw().get("lag"))));
                        });
            }
            Long length = redis.opsForStream().size(settings.streamKey());
            if (length != null) {
                streamLength.set(length);
            }
        } catch (RuntimeException e) {
            LOG.debug("Falha ao atualizar as gauges do stream {}: {}", settings.streamKey(),
                    e.getClass().getName());
        }
    }

    /**
     * Converte o campo {@code lag} do {@code XINFO GROUPS}. O servidor devolve {@code null}
     * quando não consegue calcular o lag (grupo criado num ID arbitrário ou entradas apagadas
     * entre o último entregue e o último gerado, Pitfall P10); a gauge vira {@code NaN} em vez
     * de sumir ou de mostrar um zero falso.
     *
     * @param raw valor bruto do campo {@code lag}
     * @return o lag, ou {@link Double#NaN} se não for numérico
     */
    static double lagValue(Object raw) {
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return Double.NaN;
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
     * Roda o reclaim quando o intervalo venceu. O próximo é agendado antes de rodar: se o reclaim
     * falha (MongoDB fora), o loop segue lendo entradas novas e tenta de novo no intervalo
     * seguinte, sem repetir o reclaim a cada ciclo. Uma página cheia de pendentes faz buscar a
     * próxima na mesma rodada (até {@value #MAX_RECLAIM_PAGES} páginas), para que um backlog
     * acumulado durante uma queda do MongoDB não escoe a um lote por intervalo.
     */
    private void reclaimIfDue() {
        long now = clock.millis();
        if (now < nextReclaimAt) {
            return;
        }
        nextReclaimAt = now + settings.reclaimInterval().toMillis();
        for (int page = 0; page < MAX_RECLAIM_PAGES && running; page++) {
            if (reclaimIdle() < settings.batchSize()) {
                return;
            }
        }
    }

    /**
     * Assume até {@code batchSize} entradas pendentes há pelo menos {@code minIdle}
     * ({@code XPENDING ... IDLE} + {@code XCLAIM}) e as grava e confirma pelo mesmo
     * {@link #process}.
     *
     * <p>O {@code XCLAIM} não devolve dois tipos de ID pedido: os que outro consumer assumiu entre
     * o {@code XPENDING} e o {@code XCLAIM} (já não estão parados) e os que não existem mais no
     * stream. Para separar os dois, cada ID não devolvido é procurado no stream
     * ({@code XRANGE id id}): presente, é de outra instância e fica como está; ausente, foi
     * aparado ou apagado e é confirmado sem documento, contando em {@code linkpulse.clicks.lost}.
     * O Valkey (assumption A5, confirmada no {@code ClickRedeliveryIT} com o Valkey 8.1) já
     * remove do PEL, no próprio {@code XCLAIM}, as entradas apagadas; o {@code XACK} defensivo
     * cobre servidores que não fazem isso e não tem efeito quando a entrada já saiu. As entradas
     * perdidas são confirmadas antes da gravação do resto, que pode falhar, porque não fazem parte
     * do lote: não há o que gravar delas.
     *
     * @return quantas entradas paradas o {@code XPENDING} listou
     */
    private int reclaimIdle() {
        PendingMessages idle = redis.opsForStream().pending(settings.streamKey(),
                settings.group(), Range.unbounded(), settings.batchSize(), settings.minIdle());
        if (idle == null || idle.isEmpty()) {
            return 0;
        }
        RecordId[] ids = idle.stream().map(PendingMessage::getId).toArray(RecordId[]::new);
        List<MapRecord<String, Object, Object>> taken = redis.opsForStream().claim(
                settings.streamKey(), settings.group(), settings.consumerName(),
                RedisStreamCommands.XClaimOptions.minIdle(settings.minIdle()).ids(ids));
        if (taken == null) {
            taken = List.of();
        }
        acknowledgeVanished(ids, taken);
        if (!taken.isEmpty()) {
            claimed.increment(taken.size());
            process(taken);
        }
        return ids.length;
    }

    /**
     * Confirma os IDs pedidos ao {@code XCLAIM} que não voltaram e que também não existem mais no
     * stream. Um ID não devolvido que ainda existe foi assumido por outro consumer e não é tocado.
     *
     * @param requested IDs pedidos ao {@code XCLAIM}
     * @param taken entradas que o {@code XCLAIM} devolveu
     */
    private void acknowledgeVanished(RecordId[] requested,
            List<MapRecord<String, Object, Object>> taken) {
        if (taken.size() == requested.length) {
            return;
        }
        Set<RecordId> returned = new HashSet<>();
        taken.forEach(entry -> returned.add(entry.getId()));
        List<RecordId> vanished = new ArrayList<>();
        for (RecordId id : requested) {
            if (returned.contains(id)) {
                continue;
            }
            List<MapRecord<String, Object, Object>> stored = redis.opsForStream().range(
                    settings.streamKey(), Range.closed(id.getValue(), id.getValue()));
            if (stored == null || stored.isEmpty()) {
                vanished.add(id);
            }
        }
        if (vanished.isEmpty()) {
            return;
        }
        redis.opsForStream().acknowledge(settings.streamKey(), settings.group(),
                vanished.toArray(RecordId[]::new));
        lost.increment(vanished.size());
        LOG.warn("{} entradas pendentes do stream {} já tinham sido apagadas e saíram sem "
                + "documento", vanished.size(), settings.streamKey());
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
     *
     * @throws IllegalStateException se a thread não terminou nesse prazo (uma thread presa não
     *     pode passar despercebida, nem em teste)
     */
    @Override
    public void stop() {
        stop(() -> { });
        Thread thread = worker;
        if (thread == null) {
            return;
        }
        Duration limit = settings.block().plus(STOP_GRACE);
        try {
            if (!thread.join(limit)) {
                throw new IllegalStateException("O consumer de cliques do stream "
                        + settings.streamKey() + " não terminou dentro de " + limit);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Baixa a flag e devolve na hora. Quando o loop sai, depois do lote corrente (gravado e
     * confirmado), a fábrica de conexões é parada e o {@code callback} roda. Se a thread nunca
     * subiu, isso acontece já.
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
            stopFactory();
            callback.run();
        }
    }

    /**
     * Fecha as conexões e o cliente Lettuce da fábrica, que pode ser aberta de novo no
     * {@link #start()}.
     */
    private void stopFactory() {
        if (connectionFactory instanceof Lifecycle lifecycle) {
            try {
                lifecycle.stop();
            } catch (RuntimeException e) {
                LOG.warn("Falha ao fechar a conexão do consumer de cliques: {}",
                        e.getClass().getName());
            }
        }
    }

    /**
     * Destrói a fábrica de conexões no fim da vida do bean (depois do stop, ou sem ele, se o
     * consumer nunca subiu). Destruir de novo uma fábrica já destruída não tem efeito.
     */
    @Override
    public void destroy() {
        if (connectionFactory instanceof DisposableBean disposable) {
            try {
                disposable.destroy();
            } catch (Exception e) {
                LOG.warn("Falha ao destruir a conexão do consumer de cliques: {}",
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
