package dev.linkpulse.config;

import dev.linkpulse.link.AliasPolicy;
import dev.linkpulse.link.CodeGenerator;
import dev.linkpulse.link.TargetHostPolicy;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans de infraestrutura da aplicação.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LinkPulseProperties.class)
public class ApplicationConfig {

    /**
     * Único ponto de obtenção do tempo; os testes podem trocá-lo por um relógio fixo.
     *
     * @return relógio UTC do sistema
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Gerador de códigos curtos. Alfabeto ou multiplicador inválido derruba o contexto na subida
     * (validação de inicialização do D-03).
     *
     * @param properties propriedades {@code linkpulse.*}
     * @return o gerador configurado
     */
    @Bean
    CodeGenerator codeGenerator(LinkPulseProperties properties) {
        return new CodeGenerator(properties.code().alphabet(), properties.code().multiplier());
    }

    /**
     * Política de alias com os reservados de {@code linkpulse.alias.reserved} (D-07).
     *
     * @param properties propriedades {@code linkpulse.*}
     * @return a política configurada
     */
    @Bean
    AliasPolicy aliasPolicy(LinkPulseProperties properties) {
        return new AliasPolicy(properties.alias().reserved());
    }

    /**
     * Política do host de destino: o host de {@code linkpulse.base-url} e os de
     * {@code linkpulse.self-hosts} não podem ser destino de um link (T-08-01).
     *
     * @param properties propriedades {@code linkpulse.*}
     * @return a política configurada
     */
    @Bean
    TargetHostPolicy targetHostPolicy(LinkPulseProperties properties) {
        return new TargetHostPolicy(properties.baseUrl(), properties.selfHosts());
    }
}
