package dev.linkpulse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base dos testes de integração: contexto completo contra containers reais.
 *
 * <p>Todos os ITs que estendem esta classe compartilham o mesmo contexto (e o mesmo banco),
 * então cada teste deve usar dados próprios, como códigos únicos.
 *
 * <p>{@code linkpulse.ip-hash-key} é obrigatória; os ITs usam uma chave fixa de teste.
 *
 * <p>{@code linkpulse.cache.bypass-cooldown} cai para 200 ms: os ITs que pausam o Valkey não
 * podem deixar o contexto compartilhado em bypass por 2 s depois do {@code unpause}.
 *
 * <p>{@code linkpulse.clicks.consumer.block} cai para 200 ms: o consumer de cliques volta mais
 * rápido de cada leitura vazia, e o fechamento do contexto não espera um {@code BLOCK} de 2 s.
 *
 * <p>{@code linkpulse.clicks.consumer.reclaim-interval} e {@code min-idle} caem para 1 s: um lote
 * que falhou no consumer do contexto volta pelo reclaim em segundos, e não em 30 a 90 s.
 *
 * <p>{@code linkpulse.ratelimit.limit} sobe para 100000: todos os ITs MockMvc saem de 127.0.0.1 e
 * não podem esbarrar no rate limit do {@code POST /links} (Pitfall P8).
 */
@SpringBootTest(properties = {
    "linkpulse.ip-hash-key=test-ip-hash-key-0123456789",
    "linkpulse.ratelimit.limit=100000",
    "linkpulse.cache.bypass-cooldown=200ms",
    "linkpulse.clicks.consumer.block=200ms",
    "linkpulse.clicks.consumer.reclaim-interval=1s",
    "linkpulse.clicks.consumer.min-idle=1s"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIT {

    @Autowired
    protected MockMvcTester mvc;
}
