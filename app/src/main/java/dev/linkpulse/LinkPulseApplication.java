package dev.linkpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da API do Link Pulse.
 */
@SpringBootApplication
public class LinkPulseApplication {

    /**
     * Sobe o contexto Spring da aplicação.
     *
     * @param args argumentos de linha de comando repassados ao Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(LinkPulseApplication.class, args);
    }
}
