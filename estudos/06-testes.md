# 06 · Testes

**Em uma frase:** o Link Pulse separa testes unitários (`*Test`, rápidos e sem Docker, rodados pelo Surefire) de testes de integração (`*IT`, contexto Spring completo contra um Postgres 18.6 real subido pelo Testcontainers, rodados pelo Failsafe).

## Conceitos

### Pirâmide de testes

- **Unitários:** testam uma classe isolada, com `new`, sem banco nem servidor. São muitos e rodam em segundos.
- **Integração:** sobem a aplicação (ou parte dela) e conversam com serviços reais. São menos numerosos, mais lentos, e pegam o que o unitário não vê: SQL, migrações, serialização JSON, configuração.

### JUnit Jupiter e AssertJ

JUnit Jupiter (JUnit 5+) é o motor de testes: `@Test`, `@ParameterizedTest` com `@ValueSource`, `@Nested` para agrupar cenários. AssertJ dá as asserções fluentes (`assertThat(x).isEqualTo(y)`). Os dois vêm no `spring-boot-starter-test`, com versões definidas pelo BOM do Boot 4.1.1.

### Surefire × Failsafe

São dois plugins do Maven:

- **Surefire** roda, na fase `test`, as classes cujo nome termina em `Test`.
- **Failsafe** roda, nas fases `integration-test` e `verify`, as classes que terminam em `IT`. Se um IT falha, ele deixa o restante da fase `integration-test` terminar e só quebra o build no `verify`.

### Testcontainers

Testcontainers sobe containers Docker durante o teste (um Postgres de verdade, por exemplo) e os derruba no fim. Você testa contra a mesma tecnologia de produção, em vez de um banco em memória com dialeto diferente. Precisa de Docker em execução.

## No Link Pulse

### Onde estão os testes

| Tipo | Classes | Comando |
|------|---------|---------|
| Unitários (`*Test`) | `CodeGeneratorTest`, `AliasPolicyTest`, `TargetHostPolicyTest`, `HttpUrlValidatorTest`, `LinkPulsePropertiesTest` | `make test` (sem Docker) |
| Integração (`*IT`) | `LinkApiIT`, `RedirectIT`, `ConcurrencyIT`, `LiquibaseContextsIT`, `OpenApiIT` | `make verify` (com Docker) |

O `app/pom.xml` só declara o `maven-failsafe-plugin`; o comentário explica que "Os goals integration-test/verify (classes *IT) já vêm configurados no parent". O Checkstyle foi ajustado para essa convenção: no `app/config/checkstyle/checkstyle.xml`, a regra `AbbreviationAsWordInName` libera a sigla `IT` (`allowedAbbreviations`), com o comentário "classes de teste de integração do failsafe terminam em IT", para que nomes como `LinkApiIT` não sejam tratados como abreviação proibida.

### Um teste unitário real

`app/src/test/java/dev/linkpulse/link/CodeGeneratorTest.java`

```java
class CodeGeneratorTest {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final long MULTIPLIER = 2_176_477_521_915L;
    // ...
    private final CodeGenerator generator = new CodeGenerator(ALPHABET, MULTIPLIER);
    // ...
    @Test
    void knownVectors() {
        assertThat(generator.encode(0)).isEqualTo("0000000");
        assertThat(generator.encode(1)).isEqualTo("cJinsNv");
        assertThat(generator.encode(2)).isEqualTo("EdRbklq");
        // ...
    }
    // ...
    @ParameterizedTest
    @ValueSource(longs = {62, 31, 2, 0, -1, SPACE})
    void constructorRejectsInvalidMultipliers(long multiplier) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CodeGenerator(ALPHABET, multiplier));
    }
```

- O objeto é criado com `new`: nada de Spring, nada de Docker.
- **Vetores conhecidos** travam o comportamento: se alguém mudar o algoritmo, o teste quebra e avisa que os links publicados mudariam.
- `@ParameterizedTest` roda o mesmo teste para cada valor inválido.
- O mesmo arquivo usa `ApplicationContextRunner` (um contexto Spring mínimo, ainda sem Docker) para provar que um multiplicador não coprimo derruba a subida.

### A base dos ITs

`app/src/test/java/dev/linkpulse/AbstractIT.java`

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIT {

    @Autowired
    protected MockMvcTester mvc;
}
```

- `@SpringBootTest` sobe o contexto completo da aplicação.
- `@AutoConfigureMockMvc` + `MockMvcTester` simulam requisições HTTP sem abrir porta de rede.
- `@Import(TestcontainersConfiguration.class)` traz o container do Postgres.
- O javadoc avisa: todos os ITs "compartilham o mesmo contexto (e o mesmo banco), então cada teste deve usar dados próprios, como códigos únicos".

`app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java`

```java
/**
 * Containers dos testes de integração, declarados como beans.
 *
 * <p>Como beans, o ciclo de vida acompanha o contexto Spring em cache. Um {@code static @Container}
 * seria parado ao fim de cada classe de IT e deixaria o contexto apontando para uma porta morta.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    }
}
```

**O que faz o `@ServiceConnection`:** o Spring Boot olha o container e cria as `ConnectionDetails` do banco (URL JDBC com a porta aleatória, usuário e senha). Por isso não há URL de banco nem `@DynamicPropertySource` nos testes, e o comentário do `application.yml` diz que "os ITs usam @ServiceConnection". Ele também tem precedência sobre a URL `localhost:5432` do `application-dev.yml`, como registra o javadoc do `LiquibaseContextsIT`.

**Por que bean e não `static @Container`:** o Spring guarda contextos de teste em cache e os reaproveita entre classes. Com o container como bean, ele vive tanto quanto o contexto. Um `static @Container` seria parado no fim de cada classe e deixaria o contexto em cache apontando para uma porta morta.

### Testcontainers 2.x

`app/pom.xml`

```xml
        <!-- Testcontainers 2.x renomeou o artefato (antes org.testcontainers:postgresql). Versão vem do BOM. -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
```

O pacote da classe também mudou: `org.testcontainers.postgresql.PostgreSQLContainer`. O `.claude/CLAUDE.md` registra que versões antigas do Testcontainers (abaixo da 1.21.4) falham com o Docker Engine 29 ("client version 1.32 is too old"); a versão vinda do BOM do Boot 4.1.1 não tem esse problema. Com o Docker parado, os ITs falham com `Could not find a valid Docker environment` (README).

### Um IT real

`app/src/test/java/dev/linkpulse/link/RedirectIT.java`

```java
    @Test
    void redirectsToTargetWithoutCache() {
        String target = "https://example.com/destino?x=1";
        saveLink("redir-ok1", target, null);

        MvcTestResult result = mvc.get().uri("/redir-ok1").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo(target);
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL))
                .contains("no-store")
                .contains("private");
    }
```

O padrão é *arrange / act / assert*: grava um link direto pelo repositório (com código único, `redir-ok1`), faz o `GET` e confere status e headers.

### O que cada IT prova

- `LinkApiIT`: o contrato do `POST /links` (201, `Location`, corpo), alias, 409 inclusive em corrida, todas as validações e o 500 genérico sem vazar detalhes.
- `RedirectIT`: 302 com `no-store, private`, 404, 410, lookup case-sensitive, rotas com `.` ou `/` fora do redirect, schema vindo do Liquibase e `ddl-auto` em `validate`.
- `ConcurrencyIT`: 8 threads × 50 criações simultâneas geram 400 códigos distintos.
- `LiquibaseContextsIT`: o seed `demo-link` só existe com o profile dev.
- `OpenApiIT`: `/v3/api-docs` e Swagger UI no ar, endpoints documentados, sem expor o Actuator.

### Rodando

Da raiz do repositório:

```bash
make test      # só unitários, sem Docker
make verify    # Checkstyle + unitários + ITs + SpotBugs (Docker ligado)
```

Para rodar uma única classe de teste unitário, a partir de `app/`:

```bash
cd app && ./mvnw -B -ntp -Dtest=CodeGeneratorTest test
```

Executado durante a escrita deste guia, terminou com `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0` e `BUILD SUCCESS`. No log aparece um `WARN` com "multiplicador deve ser coprimo de 62^7": é esperado, vem do teste que prova a falha de subida.

Relatórios ficam em `target/surefire-reports` (unitários) e `target/failsafe-reports` (ITs), dentro de `app/`. O CI publica essas pastas quando o build falha (guia 08).

## Por que assim?

- **Banco real nos testes:** o README resume a estratégia em "Testcontainers (Postgres 18.6 real)". Migrações, constraint `uk_links_code` e `nextval` só são provados de verdade contra o Postgres.
- **Unitários sem Docker:** quem não tem Docker ainda roda `make test` e valida a lógica (README, "Pré-requisitos").
- **Um contexto compartilhado:** subir Spring + container é caro; reaproveitar o contexto deixa a suíte rápida, ao custo de cada teste usar dados próprios.

## Experimente

1. Rode `make test` com o Docker desligado e depois `make verify`. Compare o tempo e o que falha.
2. Mude temporariamente um valor em `knownVectors`, rode só o `CodeGeneratorTest` e leia a falha. Desfaça a mudança.
3. Abra `LiquibaseContextsIT` e explique por que cada classe `@Nested` com `@ActiveProfiles` ganha um container próprio.
4. Depois de um `make verify`, abra um XML em `target/failsafe-reports` e encontre o tempo de cada IT.

## Perguntas para fixar

1. Como o Maven decide se uma classe roda no Surefire ou no Failsafe?
2. O que o `@ServiceConnection` substitui na configuração dos testes?
3. Por que o container é um `@Bean` e não um campo `static @Container`?
4. Por que os ITs precisam usar códigos únicos em cada teste?
5. Qual teste prova que criações concorrentes não geram código duplicado?

---

[← Anterior: 05 · Validação e erros](05-validacao-e-erros.md) · [Índice](README.md) · [Próximo: 07 · Build e qualidade →](07-build-e-qualidade.md)
