# 06 · Testes

**Em uma frase:** o Link Pulse separa testes unitários (`*Test`: rápidos, sem Docker, rodados pelo Surefire) de testes de integração (`*IT`: a aplicação inteira contra Postgres, Valkey e MongoDB de verdade, subidos pelo Testcontainers e rodados pelo Failsafe).

## O que você vai aprender

- Por que escrever testes automáticos e quais tipos existem.
- Como ler um teste (o padrão *arrange / act / assert*).
- Como o projeto sobe bancos de verdade dentro dos testes com o Testcontainers.
- Como testar algo que acontece "depois" (assíncrono) e como simular um banco caindo.
- Como rodar os testes e onde ver os resultados.

## Antes de começar: conceitos

### Por que testes automáticos

Um **teste automático** é um pequeno programa que usa o seu código e confere se o resultado é o esperado. Em vez de abrir o navegador e clicar toda vez que muda algo, você roda centenas de verificações em minutos. Se uma mudança quebra algo que funcionava, o teste avisa na hora (isso se chama **regressão**).

### Pirâmide de testes

- **Unitários:** testam uma classe isolada, criada com `new`, sem banco nem servidor. São muitos e rodam em segundos.
- **Integração:** sobem a aplicação (ou boa parte dela) e conversam com serviços reais. São menos numerosos e mais lentos, e pegam o que o unitário não vê: SQL, migrações, conversão de JSON, configuração, comportamento quando um banco cai.

A forma de pirâmide vem da proporção: muitos unitários na base, menos testes de integração no topo.

### O padrão *arrange / act / assert*

Quase todo teste tem três partes:

1. **Arrange (preparar):** monta o cenário. Ex.: grava um link no banco.
2. **Act (agir):** executa o que está sendo testado. Ex.: faz `GET /codigo`.
3. **Assert (conferir):** verifica o resultado. Ex.: o status é 302 e o `Location` é o esperado.

### JUnit e AssertJ

**JUnit Jupiter** é o motor de testes. Os principais recursos:

| Recurso | Para que serve |
|---------|----------------|
| `@Test` | marca um método como teste |
| `@ParameterizedTest` + `@ValueSource` | roda o mesmo teste para vários valores |
| `@Nested` | agrupa cenários numa classe interna |
| `@BeforeEach` | roda antes de cada teste (preparar o terreno) |

**AssertJ** dá as conferências em estilo de frase: `assertThat(codigo).hasSize(7)`. Os dois vêm no `spring-boot-starter-test`, junto com o **Awaitility** (veja "Testando o assíncrono" abaixo).

### Surefire × Failsafe

São dois plugins do Maven que rodam testes em momentos diferentes:

- **Surefire** roda, na fase `test`, as classes cujo nome termina em `Test`.
- **Failsafe** roda, nas fases `integration-test` e `verify`, as classes que terminam em `IT`. Se um IT falha, ele deixa os outros terminarem e só quebra o build no `verify`.

O nome da classe decide quem a roda. É por isso que a convenção `*Test` / `*IT` importa.

### Testcontainers

O **Docker** roda programas dentro de **containers**: caixas isoladas com tudo de que o programa precisa. O **Testcontainers** é uma biblioteca que, durante o teste, sobe containers de verdade (um Postgres, um Valkey, um MongoDB), espera ficarem prontos e os derruba no fim. Você testa contra a mesma tecnologia de produção, em vez de um banco "de mentira" em memória que se comporta diferente. Precisa do Docker ligado.

## No Link Pulse

### Onde estão os testes

| Tipo | Classes | Comando |
|------|---------|---------|
| Unitários (`*Test`) | `CodeGeneratorTest`, `AliasPolicyTest`, `TargetHostPolicyTest`, `HttpUrlValidatorTest`, `LinkPulsePropertiesTest`, `LinkCacheCodecTest`, `ValkeyAvailabilityTest`, `ClickPublisherTest`, `ClickDocumentTest`, `ReferrerNormalizerTest`, `UserAgentClassifierTest`, `IpHasherTest`, `RateLimiterTest`, `StatsPeriodTest` | `make test` (sem Docker) |
| Integração (`*IT`) | `LinkApiIT`, `RedirectIT`, `ConcurrencyIT`, `LiquibaseContextsIT`, `OpenApiIT`, `CacheIT`, `ValkeyOutageIT`, `ClickPublishIT`, `ClickPipelineIT`, `ClickRedeliveryIT`, `ClickResilienceIT`, `ClickConsumerLifecycleIT`, `StatsIT`, `RateLimitIT`, `TrustedProxyRateLimitIT`, `MetricsCardinalityIT` | `make verify` (com Docker) |

O `app/pom.xml` só declara o `maven-failsafe-plugin`; o comentário explica que "Os goals integration-test/verify (classes *IT) já vêm configurados no parent". O Checkstyle também foi ajustado: a regra `AbbreviationAsWordInName` libera a sigla `IT` (comentário "classes de teste de integração do failsafe terminam em IT"), para que `LinkApiIT` não seja tratado como abreviação proibida.

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

- O gerador é criado com `new`: nada de Spring nem de Docker. É por isso que as classes de regra não dependem do Spring (guia 02).
- **Vetores conhecidos** "congelam" o comportamento: se alguém mudar o algoritmo, o teste quebra e avisa que os links já publicados mudariam.
- `@ParameterizedTest` roda o mesmo teste para cada multiplicador inválido da lista.
- `assertThatIllegalArgumentException().isThrownBy(...)` confere que o código **lança** o erro esperado.
- O mesmo arquivo usa `ApplicationContextRunner`, um contexto Spring mínimo que também roda sem Docker, para provar que um multiplicador inválido derruba a subida.

Outro truque comum nos unitários: o **relógio fixo**. O `ClickPublisherTest` usa `Clock.fixed(NOW, ZoneOffset.UTC)`, e o `ValkeyAvailabilityTest` usa um relógio que o próprio teste avança. É o motivo de o `Clock` ser um bean (guia 02).

### A base dos ITs: `AbstractIT`

Quase todo IT estende uma classe base comum:

`app/src/test/java/dev/linkpulse/AbstractIT.java`

```java
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
```

- **`@SpringBootTest`** sobe a aplicação inteira. As `properties` ajustam a configuração só para os testes:
  - uma `ip-hash-key` de teste (ela é obrigatória, guia 13);
  - um rate limit altíssimo, porque todos os testes saem do mesmo IP e esbarrariam no limite de 20 por minuto;
  - tempos curtos (200 ms, 1 s) para o consumer de cliques e o cache reagirem rápido, em vez de esperar os 2 s, 30 s e 60 s de produção.
- **`@AutoConfigureMockMvc`** + **`MockMvcTester`** simulam requisições HTTP sem abrir uma porta de rede de verdade.
- **`@Import(TestcontainersConfiguration.class)`** traz os três containers.
- O javadoc avisa: todos os ITs "compartilham o mesmo contexto (e o mesmo banco), então cada teste deve usar dados próprios, como códigos únicos". Por isso os testes usam códigos como `redir-ok1` e `pipe-ok1`, que não se repetem.

### Os containers: `TestcontainersConfiguration`

`app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java`

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> valkey() {
        return new GenericContainer<>(DockerImageName.parse("valkey/valkey:8.1"))
                .withExposedPorts(6379)
                .withCommand("valkey-server", "--maxmemory", "128mb",
                        "--maxmemory-policy", "volatile-lru");
    }

    @Bean
    @ServiceConnection
    MongoDBContainer mongoContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:8.0"));
    }
}
```

Leitura guiada:

- **As mesmas versões do `compose.dev.yaml`**: `postgres:18.6`, `valkey/valkey:8.1` e `mongo:8.0`. O que passa nos testes passa no seu computador.
- **`@ServiceConnection`** é a "mágica" que liga o container à aplicação. O Boot olha o container, descobre o endereço e a porta (que é sorteada a cada execução) e configura a conexão sozinho. Por isso os testes não têm nenhuma URL de banco escrita. Essa ligação também tem prioridade sobre o `localhost:5432` do `application-dev.yml`.
- **O Valkey não tem módulo próprio no Testcontainers**, então é um `GenericContainer` (um container "genérico"). O `name = "redis"` diz ao Boot: "trate este container como um Redis". Como o Valkey fala a mesma língua do Redis, funciona.
- **O bean do Mongo se chama `mongoContainer`**, e não `mongo`, porque `mongo` já é o nome de um bean que o Boot cria sozinho (o javadoc explica).

**Por que os containers são beans, e não um campo `static @Container`?** O Spring guarda o contexto dos testes em cache e o reaproveita entre classes, para não subir tudo de novo a cada classe. Como bean, o container vive tanto quanto o contexto. Um `static @Container` seria parado no fim de cada classe, e o contexto em cache ficaria apontando para uma porta morta.

### Um IT simples

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

As três partes estão visíveis: **arrange** (grava o link `redir-ok1` direto pelo repositório), **act** (faz o `GET`), **assert** (confere status, `Location` e `Cache-Control`).

### Testando o assíncrono: Awaitility

O clique não é gravado na hora do redirect: ele passa pela fila, pelo stream e pelo consumer (guia 13). Um teste que procurasse o documento no MongoDB logo depois do `GET` falharia quase sempre, porque o clique ainda está a caminho.

A solução é **esperar com paciência**, mas com limite. O `ClickPipelineIT` tem um método auxiliar:

`app/src/test/java/dev/linkpulse/click/ClickPipelineIT.java`

```java
    private List<Document> awaitDocuments(String code, int count) {
        AtomicReference<List<Document>> found = new AtomicReference<>();
        await().atMost(WAIT).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            List<Document> documents = documentsOf(code);
            assertThat(documents).as("documentos de %s em %s", code, COLLECTION)
                    .hasSize(count);
            found.set(documents);
        });
        return found.get();
    }
```

`await().atMost(...).pollInterval(...).untilAsserted(...)` quer dizer: "repita esta conferência a cada 200 ms; se passar, siga; se não passar dentro do tempo máximo, falhe". É o Awaitility. Nunca use `Thread.sleep(5000)` para esperar: ou espera demais (teste lento), ou de menos (teste que falha de vez em quando).

E o teste que o usa:

```java
    void redirectBecomesClickDocumentInMongo() {
        saveLink("pipe-ok1", "https://example.com/pipeline");

        MvcTestResult result = mvc.get().uri("/pipe-ok1")
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                })
                .header(HttpHeaders.REFERER, "https://news.example.com/x?y=1")
                .header(HttpHeaders.USER_AGENT, FIREFOX)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FOUND);

        Document document = awaitDocuments("pipe-ok1", 1).getFirst();

        assertThat(document.get("_id")).isEqualTo(streamIdOf("pipe-ok1"));
        assertThat(document.getString("_id")).matches("^\\d+-\\d+$");
        assertThat(document.get("referrer")).isEqualTo("news.example.com");
        assertThat(document.getString("ipHash")).matches("^[0-9a-f]{64}$");
        assertThat(document.get("ts")).isInstanceOf(Date.class);
        assertThat(document.values()).allSatisfy(value -> assertThat(String.valueOf(value))
                .isNotEqualTo(CLIENT_IP)
                .isNotEqualTo(FIREFOX));
    }
```

Repare na última conferência: nenhum campo do documento pode conter o IP do cliente nem o user-agent cru. É um **teste de privacidade**.

### Simulando um banco caindo: `docker pause`

Alguns ITs provam que a aplicação aguenta a queda de um banco. O `ValkeyOutageIT` e o `ClickResilienceIT` **pausam** o container (como apertar "pause" num vídeo: o processo para de responder, mas não morre):

`app/src/test/java/dev/linkpulse/cache/ValkeyOutageIT.java`

```java
    private void pauseValkey() {
        valkey.getDockerClient().pauseContainerCmd(valkey.getContainerId()).exec();
```

Com o Valkey pausado, o teste faz vários redirects e confere que todos respondem 302, e rápido (guia 12). Pausar é mais realista que parar: simula um servidor "travado", o caso mais difícil, em que a conexão continua aberta mas nada responde. O `unpause` fica num bloco `finally`, que roda sempre, para o container voltar mesmo se o teste falhar.

### ITs com servidor de verdade

Três ITs **não** estendem o `AbstractIT`: `RateLimitIT`, `TrustedProxyRateLimitIT` e `MetricsCardinalityIT`. Eles usam `@SpringBootTest(webEnvironment = RANDOM_PORT)`, que sobe o Tomcat de verdade numa porta sorteada, e fazem requisições com o `HttpClient` do Java.

Por quê? O javadoc do `RateLimitIT` explica: o MockMvc "não passa pelo `RemoteIpValve` do Tomcat", a peça que decide qual é o IP real do cliente (guia 15). E o `MetricsCardinalityIT` precisa ler o `/actuator/prometheus` de verdade, na porta de gerenciamento (guia 16).

### O que cada IT prova

| IT | Prova |
|----|-------|
| `LinkApiIT` | O contrato do `POST /links`: 201, `Location`, alias, 409 (inclusive em corrida), validações e o 500 genérico sem vazar detalhes |
| `RedirectIT` | 302 com `no-store, private`, 404, 410, busca case-sensitive, rotas com `.` ou `/` fora do redirect, schema vindo do Liquibase |
| `ConcurrencyIT` | 8 threads × 50 criações simultâneas geram 400 códigos diferentes |
| `LiquibaseContextsIT` | O seed `demo-link` só existe com o profile dev |
| `OpenApiIT` | Swagger UI e `/v3/api-docs` no ar, sem expor o Actuator |
| `CacheIT` | Redirect repetido sai do cache, TTL limitado pela expiração, negative cache apagado ao criar o alias, contadores hit/miss |
| `ValkeyOutageIT` | Com o Valkey pausado, o redirect continua 302 e rápido, e a falha de publicação é contada |
| `ClickPublishIT` | Cada `GET` vira uma entrada no stream; `HEAD` não; Referer reduzido ao host |
| `ClickPipelineIT` | O clique chega ao MongoDB com os campos certos, sem IP nem user-agent cru; índices criados |
| `ClickRedeliveryIT` | Um clique cuja gravação falhou é reprocessado sem duplicar |
| `ClickResilienceIT` | Com o MongoDB pausado, nenhum clique se perde: todos chegam quando ele volta |
| `ClickConsumerLifecycleIT` | Ao desligar, o consumer termina o lote em andamento antes de parar; ele volta depois de parado e religado; as métricas de pendentes e atraso acompanham o grupo |
| `StatsIT` | Total, série diária, tops, bots fora por padrão, 400/404/503 |
| `RateLimitIT` | Com limite 3 no teste, o quarto `POST` do mesmo IP na janela recebe 429 com `Retry-After`; requisições simultâneas nunca passam do limite; com o Valkey fora, a criação passa (fail-open) |
| `TrustedProxyRateLimitIT` | O `X-Forwarded-For` só vale quando vem de um proxy confiável |
| `MetricsCardinalityIT` | As métricas existem e nenhuma carrega código, URL, IP ou user-agent |

### Rodando

Da raiz do repositório:

```bash
make test      # só unitários, sem Docker
make verify    # Checkstyle + unitários + ITs + SpotBugs (Docker ligado)
```

Para rodar uma única classe de teste unitário:

```bash
cd app && ./mvnw -B -ntp -Dtest=CodeGeneratorTest test
```

Executado durante a escrita da versão anterior deste guia, terminou com `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0` e `BUILD SUCCESS`. No log aparece um `WARN` com "multiplicador deve ser coprimo de 62^7": é esperado, vem do teste que prova a falha de subida.

Os relatórios ficam em `app/target/surefire-reports` (unitários) e `app/target/failsafe-reports` (ITs). O CI publica essas pastas quando o build falha (guia 08).

## Por que assim?

- **Bancos reais nos testes:** migrações, a constraint `uk_links_code`, o `nextval`, os scripts Lua do Valkey e as agregações do MongoDB só são provados de verdade contra os bancos reais.
- **Unitários sem Docker:** quem não tem Docker ainda roda `make test` e valida a lógica (README, "Pré-requisitos").
- **Um contexto compartilhado:** subir Spring + três containers é caro. Reaproveitar o contexto deixa a suíte bem mais rápida, ao custo de cada teste usar dados próprios.
- **Testar a falha, não só o sucesso:** um portfólio de DevOps precisa mostrar o que acontece quando o Valkey ou o MongoDB caem. Os ITs com `docker pause` transformam essa promessa em prova.

## Experimente

1. Rode `make test` com o Docker desligado e depois `make verify` com ele ligado. Compare o tempo e o que falha.
2. Mude temporariamente um valor em `knownVectors`, rode só o `CodeGeneratorTest` e leia a falha. Desfaça a mudança.
3. Enquanto o `make verify` roda, abra outro terminal e rode `docker ps`: veja os containers do Testcontainers aparecendo.
4. Depois de um `make verify`, abra um XML em `app/target/failsafe-reports` e encontre o tempo de cada IT.

## Perguntas para fixar

1. Como o Maven decide se uma classe roda no Surefire ou no Failsafe?
2. O que o `@ServiceConnection` substitui na configuração dos testes?
3. Por que o container é um `@Bean` e não um campo `static @Container`?
4. Por que os ITs precisam usar códigos únicos em cada teste?
5. Por que o `ClickPipelineIT` usa `await()` em vez de `Thread.sleep`?
6. Por que o `RateLimitIT` sobe um Tomcat de verdade em vez de usar o MockMvc?

<details>
<summary>Respostas</summary>

1. Pelo nome da classe: `*Test` vai para o Surefire (fase `test`); `*IT` vai para o Failsafe (`integration-test`/`verify`).
2. A configuração de conexão (URL, porta, usuário, senha) de cada banco: o Boot a descobre direto do container.
3. Para o container viver tanto quanto o contexto Spring em cache. Um `static @Container` seria parado no fim da classe, e o contexto reaproveitado ficaria apontando para uma porta morta.
4. Porque todos compartilham o mesmo contexto e os mesmos bancos; códigos repetidos fariam um teste interferir no outro.
5. Porque o clique chega ao MongoDB depois de um tempo variável. O `await()` confere várias vezes até passar (ou estourar o limite), sem esperar demais nem de menos.
6. Porque o MockMvc não passa pelo `RemoteIpValve` do Tomcat, que decide o IP real do cliente, e o rate limit depende desse IP.

</details>

---

[← Anterior: 05 · Validação e erros](05-validacao-e-erros.md) · [Índice](README.md) · [Próximo: 07 · Build e qualidade →](07-build-e-qualidade.md)
