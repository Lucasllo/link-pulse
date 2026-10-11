package dev.linkpulse.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gate de disponibilidade do Valkey: depois de uma falha, ninguém chama o Valkey durante uma
 * janela curta ({@code linkpulse.cache.bypass-cooldown}, padrão 2 s). É o que impede o fail-open
 * de virar fail-slow.
 *
 * <p>Os timeouts curtos do Lettuce e o {@code REJECT_COMMANDS} cobrem o Valkey parado ou a
 * conexão caída. Este gate cobre o caso "conectado, mas sem resposta" (container pausado,
 * partição de rede): sem ele, cada request pagaria o timeout de comando inteiro. Com ele, só a
 * primeira chamada de cada janela paga; as outras seguem direto para o Postgres.
 *
 * <p>O gate é compartilhado pelo cache do redirect ({@link LinkCache}), pela publicação de
 * cliques e pelo rate limit (02-09). Cada abertura da janela conta uma vez em
 * {@code linkpulse.valkey.unavailable}; uma nova falha dentro da janela só a estende.
 *
 * <p>O estado é um {@link AtomicLong}, sem monitor intrínseco: no Java 21 um bloco sincronizado
 * prende a virtual thread do request à thread de plataforma (pinning).
 */
public final class ValkeyAvailability {

    /** Nome do contador de aberturas da janela de bypass. */
    static final String UNAVAILABLE_METRIC = "linkpulse.valkey.unavailable";

    private final long cooldownMillis;
    private final Clock clock;
    private final Counter unavailable;
    private final AtomicLong openUntil = new AtomicLong(Long.MIN_VALUE);

    /**
     * Cria o gate e registra o contador.
     *
     * @param cooldown janela de bypass depois de uma falha
     * @param clock relógio da aplicação
     * @param registry registry do Micrometer
     */
    public ValkeyAvailability(Duration cooldown, Clock clock, MeterRegistry registry) {
        this.cooldownMillis = cooldown.toMillis();
        this.clock = clock;
        this.unavailable = Counter.builder(UNAVAILABLE_METRIC)
                .description("Aberturas da janela de bypass do Valkey depois de uma falha")
                .register(registry);
    }

    /**
     * Diz se o Valkey pode ser chamado agora.
     *
     * @return {@code false} dentro da janela de bypass aberta pela última falha
     */
    public boolean allowRequest() {
        return clock.millis() >= openUntil.get();
    }

    /**
     * Registra uma falha do Valkey e abre (ou estende) a janela de bypass. Só conta em
     * {@code linkpulse.valkey.unavailable} quando a janela estava fechada.
     */
    public void recordFailure() {
        long now = clock.millis();
        // max: uma thread com leitura de relógio mais antiga nunca encurta a janela.
        long previous = openUntil.getAndAccumulate(now + cooldownMillis, Math::max);
        if (now >= previous) {
            unavailable.increment();
        }
    }
}
