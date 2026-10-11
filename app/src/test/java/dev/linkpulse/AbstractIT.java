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
 */
@SpringBootTest(properties = {"linkpulse.ip-hash-key=test-ip-hash-key-0123456789"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIT {

    @Autowired
    protected MockMvcTester mvc;
}
