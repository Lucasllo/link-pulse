package dev.linkpulse.cache;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;

/**
 * Esqueleto do gate de disponibilidade do Valkey (RED da 02-03): ainda não bloqueia nada.
 */
public final class ValkeyAvailability {

    /**
     * Cria o gate.
     *
     * @param cooldown janela de bypass depois de uma falha
     * @param clock relógio da aplicação
     * @param registry registry do Micrometer
     */
    public ValkeyAvailability(Duration cooldown, Clock clock, MeterRegistry registry) {
        // esqueleto
    }

    /**
     * Diz se o Valkey pode ser chamado agora.
     *
     * @return sempre {@code true} no esqueleto
     */
    public boolean allowRequest() {
        return true;
    }

    /**
     * Registra uma falha do Valkey.
     */
    public void recordFailure() {
        // esqueleto
    }
}
