# 02 · Spring Boot e Java 21

**Em uma frase:** o Link Pulse roda em Spring Boot 4.1.1 sobre Java 21. Quase toda a infraestrutura (servidor web, conexões com Postgres, Valkey e MongoDB, Liquibase, Actuator) vem pronta dos starters e da auto-configuração, ajustada pelos arquivos `application*.yml` e por um record de propriedades próprio.

## O que você vai aprender

- O que é um framework e o que o Spring Boot faz por você.
- O que são starters, auto-configuração, profiles e beans.
- Como o projeto declara seus próprios objetos (`ApplicationConfig`) e lê a configuração (`LinkPulseProperties` e `application.yml`).
- Duas novidades do Java usadas aqui: records e virtual threads.

## Antes de começar: conceitos

### Java, JDK e framework

**Java** é a linguagem em que o programa é escrito. O **JDK** (*Java Development Kit*) é o kit que compila e roda programas Java; este projeto usa o **JDK 21** da distribuição Eclipse Temurin.

Um **framework** é um esqueleto pronto de aplicação. Sem ele, para receber uma requisição HTTP você teria de abrir uma porta de rede, ler bytes, interpretar o protocolo HTTP, converter JSON... Milhares de linhas antes de escrever a primeira regra de negócio. O **Spring Boot** já faz tudo isso: você escreve só o que é específico do seu projeto.

### Starter

Um **starter** é uma dependência "guarda-chuva" do Maven. Em vez de listar dez bibliotecas compatíveis entre si, você declara uma só, e ela traz o pacote completo de um tema. Exemplo: declarar `spring-boot-starter-data-jpa` traz Hibernate, Spring Data JPA e o pool de conexões HikariCP. Você também não escolhe versões: quem decide é o **BOM** do Boot, uma lista de versões testadas juntas (guia 07).

No Boot 4, os starters ficaram mais "fatiados". O antigo `spring-boot-starter-web` virou `spring-boot-starter-webmvc`, e o Liquibase passou a exigir um starter próprio, `spring-boot-starter-liquibase`.

### Auto-configuração

Na subida, o Boot olha duas coisas, **quais bibliotecas estão no projeto** e **quais propriedades foram configuradas**, e monta os objetos necessários sozinho:

- Há driver do Postgres e `spring.datasource.url`? Ele cria a conexão com o banco.
- Há Liquibase e `spring.liquibase.change-log`? Ele roda as migrações antes de aceitar requisições.
- Há o starter do Redis? Ele cria o cliente Lettuce e o `StringRedisTemplate`.
- Há Actuator? Ele sobe os endpoints de saúde e métricas.

Você só escreve configuração quando quer algo **diferente** do padrão. É por isso que o projeto tem pouquíssimo código de "ligação".

### Profiles

Um **profile** é um nome para um conjunto de configurações de um ambiente. `application.yml` vale sempre. `application-dev.yml` só vale com o profile `dev` ativo, e `application-prod.yml` só com o `prod`. As chaves do arquivo do profile sobrescrevem as do arquivo base.

Formas de ativar um profile: a variável de ambiente `SPRING_PROFILES_ACTIVE=dev` ou, com o Maven, `-Dspring-boot.run.profiles=dev` (é o que o `make run` faz).

### Records (Java 16+)

Um **record** é uma classe feita só para carregar dados. Esta linha:

```java
record Ponto(int x, int y) {}
```

gera sozinha o construtor, os métodos de leitura `x()` e `y()`, o `equals`, o `hashCode` e o `toString`. Os campos não podem mudar depois de criados (são *imutáveis*). Antes dos records, era comum usar a biblioteca Lombok para gerar esse código; aqui ela não é necessária.

### Virtual threads (Java 21)

Uma **thread** é uma "linha de execução": um trabalhador que executa código. Num servidor web tradicional, cada requisição ocupa uma thread do sistema operacional do começo ao fim, inclusive enquanto **espera** o banco responder. Threads do sistema são caras (memória, troca de contexto), então o servidor tem um número limitado delas.

**Virtual threads** (JEP 444) são threads leves gerenciadas pela própria JVM. Quando uma virtual thread fica esperando (o banco, o Valkey), ela "solta" a thread do sistema para outra usar. Resultado: dá para atender muitas requisições simultâneas mantendo o código simples, no estilo "uma thread por requisição".

Um cuidado no Java 21: se a espera acontece dentro de um bloco `synchronized`, a virtual thread fica **presa** à thread do sistema (*pinning*) e perde a vantagem. A correção (JEP 491) só chega no Java 24. Por isso o projeto usa `AtomicLong` em vez de `synchronized` em classes como `ValkeyAvailability` (guia 12).

## No Link Pulse

### Versões e starters

`app/pom.xml`

```xml
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <!-- ... -->
    <properties>
        <!-- O parent usa 17 por padrão; o projeto roda em Java 21. -->
        <java.version>21</java.version>
```

O `<parent>` é o "pai" do projeto Maven: dele vêm o BOM de versões e a configuração dos plugins. `java.version` 21 diz ao compilador qual versão do Java usar.

Dependências de produção e o que cada uma traz:

| Dependência | Traz | Usada para |
|-------------|------|------------|
| `spring-boot-starter-webmvc` | Spring MVC + Tomcat embutido | Receber HTTP e devolver JSON |
| `spring-boot-starter-validation` | Bean Validation (Hibernate Validator) | `@Valid`, `@NotBlank`... (guia 05) |
| `spring-boot-starter-actuator` | Endpoints de saúde e métricas | `/actuator/health`, `/actuator/prometheus` |
| `springdoc-openapi-starter-webmvc-ui` | Geração do OpenAPI + Swagger UI | Documentação interativa da API |
| `spring-boot-starter-data-jpa` | Hibernate + Spring Data JPA + HikariCP | Tabela `links` no Postgres (guia 03) |
| `spring-boot-starter-data-redis` | Spring Data Redis + cliente Lettuce | Cache, stream de cliques e rate limit no Valkey (guias 12, 13, 15) |
| `spring-boot-starter-data-mongodb` | Spring Data MongoDB + driver | Coleção `click_events` (guias 13, 14) |
| `spring-boot-starter-liquibase` | Liquibase | Migrações do schema (guia 03) |
| `postgresql` (escopo `runtime`) | Driver JDBC | Conversar com o Postgres |
| `micrometer-registry-prometheus` (escopo `runtime`) | Formato Prometheus para as métricas | `/actuator/prometheus` (guia 16) |

O próprio pom comenta a dependência do Liquibase: "No Boot 4 a autoconfig do Liquibase saiu do core e precisa do starter próprio." Escopo `runtime` quer dizer "não é usada para compilar o código, só para rodá-lo".

### Ponto de entrada

`app/src/main/java/dev/linkpulse/LinkPulseApplication.java`

```java
@SpringBootApplication
public class LinkPulseApplication {
    // ...
    public static void main(String[] args) {
        SpringApplication.run(LinkPulseApplication.class, args);
    }
}
```

Todo programa Java começa num método `main`. Aqui ele tem uma linha só: `SpringApplication.run(...)` liga o Spring, que faz todo o resto.

`@SpringBootApplication` junta três anotações:

- **configuração:** a classe pode declarar beans;
- **auto-configuração:** o Boot monta a infraestrutura sozinho;
- **varredura de componentes:** o Spring procura classes anotadas (`@RestController`, `@Service`, `@Component`, `@Configuration`) a partir do pacote `dev.linkpulse` e em todos os subpacotes.

É por isso que `LinkController`, `LinkService`, `LinkCache` e os demais são encontrados sem registro manual.

### `ApplicationConfig`: os beans que o projeto declara

#### Primeiro: o que é um bean?

Imagine que o Spring é o **almoxarifado** da aplicação. Na subida, ele fabrica os objetos importantes, guarda **uma cópia de cada** numa prateleira e entrega essa cópia a quem pedir. Cada objeto guardado nessa prateleira se chama **bean**, e a prateleira inteira se chama **contexto** (ou contêiner) do Spring.

"Pedir" um bean é simples: basta declarar o objeto como parâmetro do construtor. Veja o `LinkService`:

`app/src/main/java/dev/linkpulse/link/LinkService.java`

```java
    public LinkService(LinkRepository repository, CodeGenerator codeGenerator,
            AliasPolicy aliasPolicy, TargetHostPolicy targetHostPolicy, LinkCache linkCache,
            Clock clock) {
```

O `LinkService` nunca escreve `new CodeGenerator(...)`. Ele só diz "eu preciso de um `CodeGenerator`, de um `Clock`..." e o Spring procura esses objetos na prateleira e os entrega. Isso se chama **injeção de dependência**: as dependências são "injetadas" de fora, em vez de criadas lá dentro.

#### Como um objeto vai parar na prateleira?

Existem dois caminhos:

1. **A classe se apresenta sozinha.** Classes anotadas com `@Service`, `@RestController`, `@Component` etc. são encontradas pela varredura do `@SpringBootApplication`. O `LinkService` (que tem `@Service`) entra assim.
2. **Alguém escreve uma "receita".** Quando a classe não tem anotação do Spring, ou quando construí-la exige valores de configuração, uma classe `@Configuration` ensina ao Spring como fabricá-la. Cada receita é um método anotado com `@Bean`.

`CodeGenerator`, `AliasPolicy` e `TargetHostPolicy` são classes Java comuns, sem nenhuma anotação do Spring. Elas precisam de valores que estão no `application.yml` (o alfabeto, as palavras reservadas, a URL base). Então alguém precisa fazer a ponte: ler a configuração e chamar o `new` certo. Esse alguém é o `ApplicationConfig`.

#### O código, linha por linha

`app/src/main/java/dev/linkpulse/config/ApplicationConfig.java` (javadocs resumidos)

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LinkPulseProperties.class)
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CodeGenerator codeGenerator(LinkPulseProperties properties) {
        return new CodeGenerator(properties.code().alphabet(), properties.code().multiplier());
    }

    @Bean
    AliasPolicy aliasPolicy(LinkPulseProperties properties) {
        return new AliasPolicy(properties.alias().reserved());
    }

    @Bean
    TargetHostPolicy targetHostPolicy(LinkPulseProperties properties) {
        return new TargetHostPolicy(properties.baseUrl(), properties.selfHosts());
    }
}
```

**`@Configuration`** avisa o Spring: "esta classe é um livro de receitas; leia os métodos `@Bean` dela na subida".

**`proxyBeanMethods = false`** é um detalhe de desempenho. Por padrão, o Spring "embrulha" a classe de configuração num objeto intermediário (um *proxy*) para que, se um método `@Bean` chamar outro diretamente (por exemplo, `codeGenerator()` chamando `clock()`), ele receba a cópia que já está na prateleira, e não um objeto novo. Aqui nenhum método chama outro: quem precisa de algo recebe por **parâmetro**. O embrulho seria trabalho à toa, então ele é desligado. A subida fica um pouco mais rápida e a classe fica mais simples.

**`@EnableConfigurationProperties(LinkPulseProperties.class)`** liga o record de configuração (próxima seção). Sem essa linha, o Spring não criaria o `LinkPulseProperties` a partir do `application.yml`, e os métodos abaixo não teriam de onde tirar os valores.

**Cada método `@Bean`** tem três partes que importam:

| Parte | Exemplo | O que significa para o Spring |
|-------|---------|-------------------------------|
| Tipo de retorno | `CodeGenerator` | "Esta receita produz um `CodeGenerator`." |
| Parâmetros | `LinkPulseProperties properties` | "Para fabricar, preciso destes ingredientes." O Spring os busca na prateleira e passa para o método. |
| Nome do método | `codeGenerator` | Vira o nome do bean (útil só em casos avançados). |

Os métodos não são `public`. Não precisam ser: quem os chama é o Spring, e não outras classes do projeto.

#### A ordem do que acontece na subida

1. O Spring lê o `application.yml` (e o arquivo do profile ativo, mais as variáveis de ambiente).
2. Por causa do `@EnableConfigurationProperties`, ele cria o `LinkPulseProperties` com esses valores e o valida.
3. Ele percebe que `codeGenerator(...)` precisa de um `LinkPulseProperties`, pega o que acabou de criar e chama o método. O `CodeGenerator` resultante vai para a prateleira. O mesmo vale para `aliasPolicy` e `targetHostPolicy`.
4. `clock()` não precisa de nada e é chamado direto.
5. Quando chega a vez do `LinkService`, todos os ingredientes já estão prontos e o construtor dele é chamado.

```
application.yml + variáveis de ambiente
            │
            ▼
   LinkPulseProperties ──► codeGenerator(...)     ──► CodeGenerator    ─┐
                       ──► aliasPolicy(...)       ──► AliasPolicy      ─┼─► LinkService
                       ──► targetHostPolicy(...)  ──► TargetHostPolicy ─┤
                            clock()               ──► Clock            ─┘ (e LinkCache, ClickPublisher...)
```

Se qualquer passo falhar (um valor inválido, por exemplo), o Spring **para a subida** e mostra o erro. A aplicação nem chega a abrir a porta 8080.

#### Os quatro beans, um por um

**1. `Clock`: o relógio da aplicação**

`Clock` é uma classe do próprio Java que responde "que horas são?". `Clock.systemUTC()` devolve o relógio real do computador, no fuso UTC (sem horário de verão nem fuso local, o mesmo em qualquer servidor).

Por que não usar simplesmente `Instant.now()` no código? Porque `Instant.now()` não pode ser controlado. Pense num teste que precisa verificar "um link que expira amanhã ainda funciona hoje": com o relógio real, o resultado depende do momento em que o teste roda. Com um `Clock` injetado, o teste passa um relógio parado (`Clock.fixed(...)`) e o "agora" vira um valor conhecido. É o que faz o `ClickPublisherTest`; o `ValkeyAvailabilityTest` vai além e usa um relógio que o próprio teste avança.

Por isso o javadoc diz que este é o "único ponto de obtenção do tempo". Hoje quem pede o `Clock` é o `LinkService`, o `LinkCache`, o `ClickPublisher`, o `ClickStreamConsumer` e o `ValkeyAvailability`.

**2. `CodeGenerator`: o fabricante de códigos curtos**

Transforma o número da sequência do Postgres num código de 7 caracteres (detalhes no guia 04). Ele precisa de dois valores do `application.yml`: o **alfabeto** (`linkpulse.code.alphabet`) e o **multiplicador** (`linkpulse.code.multiplier`).

O construtor do `CodeGenerator` confere esses valores: o alfabeto precisa ter exatamente 62 caracteres sem repetição, e o multiplicador precisa ser coprimo de 62^7. Se algo estiver errado, ele lança `IllegalArgumentException`. Como isso acontece dentro de um método `@Bean`, o Spring não consegue colocar o bean na prateleira e **a aplicação inteira não sobe**. É isso que o javadoc chama de "derruba o contexto na subida". Parece drástico, mas é o comportamento desejado: é muito melhor descobrir o erro no deploy do que depois de gerar milhares de links quebrados.

**3. `AliasPolicy`: a lista de apelidos proibidos**

Quando alguém cria um link com alias (por exemplo, `/meu-blog`), o alias não pode ser uma rota que a aplicação já usa. Um alias `actuator` ou `swagger-ui` brigaria com essas rotas. A política recebe a lista de `linkpulse.alias.reserved` e recusa essas palavras sem diferenciar maiúsculas.

**4. `TargetHostPolicy`: "não aponte para mim mesmo"**

Um link curto não pode ter como destino o próprio encurtador. Se `http://localhost:8080/abc` redirecionasse para `http://localhost:8080/abc`, o navegador entraria em loop. Essa política recebe o host da `linkpulse.base-url` e os hosts extras de `linkpulse.self-hosts` (por exemplo, o DNS do load balancer) e recusa links que apontem para eles.

#### Por que essas classes não têm `@Component`?

Se `CodeGenerator` tivesse `@Component`, ele mesmo teria de saber ler o `application.yml`, e o código de regra de negócio ficaria preso ao Spring. Do jeito que está:

- a classe só recebe valores no construtor e não sabe que o Spring existe;
- nos testes unitários basta `new CodeGenerator("0123...xyz", 2176477521915L)`, sem subir o Spring;
- quem decide **de onde vêm** os valores é só o `ApplicationConfig`.

> O `ApplicationConfig` é a configuração "geral". As fases seguintes criaram outras classes no mesmo estilo, cada uma com seu tema, no mesmo pacote `config`: `RedisConfig`, `MongoConfig`, `ClickConfig`, `WebMvcConfig` e `OpenApiConfig`. Lendo uma, você entende todas.

### `LinkPulseProperties`: configuração tipada

#### O problema que ela resolve

O jeito mais básico de ler uma configuração no Spring é colar a chave como texto onde ela é usada:

```java
@Value("${linkpulse.base-url}")   // exemplo do que o projeto NÃO faz
private String baseUrl;
```

Isso funciona, mas tem problemas: se você erra uma letra da chave, só descobre na execução; o valor chega como texto e cada classe precisa convertê-lo; e ninguém confere se o valor faz sentido.

"Configuração tipada" é a alternativa: um único objeto Java, com um campo para cada chave e o **tipo certo** em cada campo (`URI`, `Duration`, `List<String>`, `long`...). O Spring preenche esse objeto uma vez, valida, coloca na prateleira, e o resto do código pede o objeto como qualquer outro bean.

#### O record

`app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java` (resumido)

```java
@ConfigurationProperties("linkpulse")
@Validated
public record LinkPulseProperties(
        @NotNull URI baseUrl,
        @NotNull @Valid Code code,
        @DefaultValue Alias alias,
        List<String> selfHosts,
        @Valid @DefaultValue Cache cache,
        String ipHashKey,
        @Valid @DefaultValue Clicks clicks,
        @Valid @DefaultValue RateLimit ratelimit) {
    // ...
    public record Code(@NotBlank String alphabet, @Positive long multiplier) {
    }
    // ...
    public record Cache(
            @DefaultValue("24h") Duration defaultTtl,
            @DefaultValue("60s") Duration negativeTtl,
            // ...
```

Lembrete: um **record** é uma classe só de dados. Os valores entram pelo construtor e não mudam depois (veja a seção "Records e virtual threads" no começo deste guia).

#### Como o YAML vira objeto (binding)

**`@ConfigurationProperties("linkpulse")`** diz: "preencha este record com tudo o que estiver debaixo de `linkpulse` na configuração". O Spring então casa cada chave com um campo:

| Chave no YAML | Campo no record | Tipo Java | Conversão feita pelo Spring |
|---------------|-----------------|-----------|-----------------------------|
| `linkpulse.base-url` | `baseUrl` | `URI` | texto `http://localhost:8080` → objeto `URI` |
| `linkpulse.code.alphabet` | `code().alphabet()` | `String` | nenhuma |
| `linkpulse.code.multiplier` | `code().multiplier()` | `long` | texto `2176477521915` → número |
| `linkpulse.alias.reserved` | `alias().reserved()` | `List<String>` | lista YAML (`- links`) → lista Java |
| `linkpulse.self-hosts` | `selfHosts` | `List<String>` | `a.com,b.com` → lista com 2 itens |
| `linkpulse.cache.default-ttl` | `cache().defaultTtl()` | `Duration` | texto `24h` → 24 horas |
| `linkpulse.ratelimit.window` | `ratelimit().window()` | `Duration` | texto `1m` → 1 minuto |

Três regras explicam a tabela:

- **Hífen vira camelCase:** `base-url` no YAML casa com `baseUrl` no Java.
- **Cada nível do YAML pode virar um record aninhado:** o bloco `linkpulse.code` vira o record `Code`; `linkpulse.clicks.consumer` vira `Clicks.ConsumerSettings`, dentro de `Clicks`.
- **O tipo do campo decide a conversão:** como `defaultTtl` é `Duration`, o Spring entende `24h`, `60s`, `90d`, `150ms`.

#### Valores padrão com `@DefaultValue`

Repare que o `application.yml` **não tem** bloco `linkpulse.cache`, `linkpulse.clicks` nem `linkpulse.ratelimit`. Mesmo assim, `properties.cache().defaultTtl()` vale 24 horas. Isso vem do `@DefaultValue`:

- `@DefaultValue("24h")` num campo diz: "se ninguém configurou, use 24h".
- `@DefaultValue` sem valor num record aninhado (como `@DefaultValue Cache cache`) diz: "se o bloco inteiro não existe, crie-o mesmo assim, com os padrões dos campos dele". Sem isso, `cache` viria `null` e o código quebraria com `NullPointerException`.

Por isso o `application.yml` só lista essas chaves em **comentários**, para documentar o que dá para mudar.

#### Validação: dois guardas na porta

A configuração é conferida **antes** de a aplicação aceitar requisições, em duas camadas.

**Guarda 1: anotações de Bean Validation.** `@Validated` na classe liga a conferência das anotações dos campos:

| Anotação | Regra |
|----------|-------|
| `@NotNull` | o valor precisa existir (`baseUrl`, `code`) |
| `@NotBlank` | o texto não pode ser vazio nem só espaços (`alphabet`, `streamKey`) |
| `@Positive` | o número precisa ser maior que zero (`multiplier`, `limit`, `batchSize`) |
| `@Valid` | "entre neste record aninhado e confira as anotações dele também" |

Sem o `@Valid` em `code`, o `@NotBlank` do `alphabet` lá dentro seria ignorado.

**Guarda 2: o construtor compacto.** Um record pode ter um construtor "compacto", sem lista de parâmetros, que roda antes de os campos receberem os valores. É o lugar para regras que uma anotação não expressa:

```java
    public LinkPulseProperties {
        if (baseUrl != null && !isValidBaseUrl(baseUrl)) {
            throw new IllegalArgumentException("linkpulse.base-url deve ser uma URL http ou https "
                    + "absoluta, com host e sem credenciais, query ou fragmento");
        }
        selfHosts = selfHosts == null ? List.of() : List.copyOf(selfHosts);
    }
```

Ele faz duas coisas:

1. **Valida o formato da `baseUrl`.** Não basta existir: precisa começar com `http` ou `https`, ter host e não ter usuário/senha, `?query` nem `#fragmento`. Repare que a mensagem de erro **não repete o valor recebido**. Se alguém configurasse `https://usuario:senha@host`, a senha não apareceria no log.
2. **Normaliza `selfHosts`.** Se a chave não foi configurada, o valor chega `null`; o construtor troca por uma lista vazia. `List.copyOf` ainda cria uma cópia que não pode ser alterada. Assim, o resto do código nunca precisa testar `if (selfHosts != null)`.

Os records aninhados `Cache`, `Clicks`, `ConsumerSettings` e `RateLimit` fazem o mesmo para as durações: zero ou negativo derruba a subida.

#### Escondendo o segredo no `toString()`

Todo record gera um `toString()` que imprime todos os campos. Só que `ipHashKey` é uma chave secreta (usada para transformar o IP do cliente em hash). Se o Spring imprimisse as propriedades num erro, a chave vazaria no log. Por isso o record sobrescreve o `toString()` e mostra `ipHashKey=****`. O `LinkPulsePropertiesTest` tem um teste só para isso (`toStringDoesNotExposeIpHashKey`).

#### Variáveis de ambiente (relaxed binding)

Em produção ninguém edita o `application.yml` dentro da imagem Docker: os valores chegam por **variáveis de ambiente**. O Spring aceita a mesma chave em formatos diferentes ("relaxed binding", ou ligação flexível). Para montar o nome da variável a partir da chave:

1. troque os pontos por `_`;
2. troque o hífen por `_` (a documentação do Spring também aceita simplesmente apagar o hífen);
3. escreva tudo em maiúsculas.

| Chave | Variável de ambiente |
|-------|----------------------|
| `linkpulse.base-url` | `LINKPULSE_BASE_URL` |
| `linkpulse.self-hosts` | `LINKPULSE_SELF_HOSTS` |
| `linkpulse.clicks.consumer.batch-size` | `LINKPULSE_CLICKS_CONSUMER_BATCH_SIZE` |
| `linkpulse.ip-hash-key` | `LINKPULSE_IP_HASH_KEY` |

Listas viram texto separado por vírgula: `LINKPULSE_SELF_HOSTS=lb.example.com,alias.example.com`. O teste `environmentVariablesBindBaseUrlAndSelfHosts` prova exatamente isso. O javadoc do record e os comentários do `application.yml` listam todas as variáveis aceitas.

#### Quem usa o `LinkPulseProperties`

Como ele também é um bean, qualquer classe pode pedi-lo no construtor. Hoje pedem: o `ApplicationConfig` (para montar os quatro beans acima), o `ClickConfig`, o `RedisConfig` e o `LinkController`, que usa `baseUrl` para montar a `shortUrl` devolvida na criação. Algumas classes recebem só um pedaço, como o `ClickPublisher`, que recebe só `LinkPulseProperties.Clicks`.

### `application.yml`: o arquivo de configuração

#### Lendo YAML

YAML é um formato de texto para configuração. Três regras bastam para ler o arquivo:

- **A indentação (espaços, nunca tab) mostra quem está dentro de quem.** Isto:

  ```yaml
  spring:
    application:
      name: link-pulse
  ```

  é a chave `spring.application.name` com o valor `link-pulse`. Você vai ver as duas formas (indentada e com pontos) nos guias, e elas significam a mesma coisa.
- **`-` no começo da linha é um item de lista** (como em `linkpulse.alias.reserved`).
- **`#` começa um comentário**, ignorado pelo Spring. Neste projeto os comentários explicam o **porquê** de cada valor; vale lê-los.

#### Quem lê cada parte

O arquivo tem quatro blocos de primeiro nível. Três são do Spring Boot, com nomes definidos pela documentação dele; um é nosso:

| Bloco | Dono | Quem lê |
|-------|------|---------|
| `spring.*` | Spring Boot | a auto-configuração (banco, Liquibase, Valkey, MongoDB...) |
| `server.*` | Spring Boot | o servidor web embutido (Tomcat) |
| `management.*` | Spring Boot | o Actuator (health, métricas) |
| `linkpulse.*` | **projeto** | o `LinkPulseProperties` da seção anterior |

#### Quem ganha quando o mesmo valor aparece em dois lugares

Do mais forte para o mais fraco:

1. **variável de ambiente** (`LINKPULSE_BASE_URL=...`);
2. **arquivo do profile ativo** (`application-dev.yml` ou `application-prod.yml`);
3. **`application.yml`** (a base);
4. **`@DefaultValue` no código** (só para as chaves `linkpulse.*`).

Exemplo com `linkpulse.base-url`: o `application.yml` diz `http://localhost:8080`. No compose, no kind ou na AWS, a variável `LINKPULSE_BASE_URL` substitui esse valor sem mudar nenhum arquivo.

#### O arquivo, bloco por bloco

`app/src/main/resources/application.yml`

**`spring`: a infraestrutura**

```yaml
spring:
  application:
    name: link-pulse
  threads:
    virtual:
      enabled: true
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
    contexts: prod
    analytics-enabled: false
```

| Chave | Em palavras simples |
|-------|---------------------|
| `application.name` | O nome da aplicação. Aparece em logs e métricas. |
| `threads.virtual.enabled` | Cada requisição roda numa virtual thread (ver "Virtual threads e `open-in-view`" abaixo). |
| `jpa.hibernate.ddl-auto: validate` | O Hibernate **não cria nem altera** tabelas; só confere se as tabelas batem com as classes Java. Quem cria tabelas é o Liquibase (guia 03). |
| `jpa.open-in-view: false` | Explicado na seção "Virtual threads e `open-in-view`". |
| `liquibase.change-log` | Onde está o arquivo-mestre das migrações. `classpath:` significa "dentro do jar, a partir de `src/main/resources`". |
| `liquibase.contexts: prod` | Por padrão, roda só as migrações de produção. O profile dev troca para `dev` e liga o link de exemplo (seed). |
| `liquibase.analytics-enabled: false` | O Liquibase envia estatísticas anônimas por padrão; aqui isso é desligado. |

Note o que **falta**: não há URL, usuário nem senha do Postgres. Isso é proposital, como diz o comentário do arquivo: "prod usa SPRING_DATASOURCE_URL/USERNAME/PASSWORD, o dev usa application-dev.yml e os ITs usam @ServiceConnection". Senha de produção nunca entra no repositório.

```yaml
  data:
    redis:
      timeout: 150ms
      connect-timeout: 250ms
      repositories:
        enabled: false
    mongodb:
      repositories:
        type: none
  mongodb:
    database: linkpulse
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

| Chave | Em palavras simples |
|-------|---------------------|
| `data.redis.timeout: 150ms` | Se o Valkey (o cache) demorar mais que 150 ms para responder, a aplicação desiste do cache e busca no Postgres. Um cache lento não pode deixar o redirect lento. |
| `data.redis.connect-timeout: 250ms` | O mesmo raciocínio para abrir a conexão (o padrão seria 10 s). |
| `data.redis.repositories.enabled: false` e `data.mongodb.repositories.type: none` | Desligam um recurso automático do Spring Data que o projeto não usa: o Valkey é acessado pelo `StringRedisTemplate` e o MongoDB pelo `MongoTemplate`. |
| `mongodb.database: linkpulse` | Nome do banco do MongoDB onde ficam os cliques. O endereço também não está aqui: o padrão é `localhost:27017`, e fora do dev ele vem de `SPRING_MONGODB_URI`. |
| `lifecycle.timeout-per-shutdown-phase: 30s` | Ao desligar, a aplicação espera até 30 s por etapa para terminar o que estava fazendo (por exemplo, gravar o último lote de cliques). |

**`server`: o servidor web**

```yaml
server:
  port: 8080
  shutdown: graceful
  forward-headers-strategy: native
  tomcat:
    remoteip:
      internal-proxies: ""
```

| Chave | Em palavras simples |
|-------|---------------------|
| `port: 8080` | A porta da API. |
| `shutdown: graceful` | Ao desligar, o servidor para de aceitar requisições novas mas termina as que já estão em andamento, em vez de cortá-las no meio. |
| `forward-headers-strategy` e `internal-proxies: ""` | Controlam como descobrir o IP real de quem fez a requisição, usado pelo rate limit. Com a lista de proxies confiáveis **vazia**, o cabeçalho `X-Forwarded-For` é ignorado. Assim, um cliente não consegue fingir outro IP mandando esse cabeçalho e fugir do limite. No kind, a lista recebe a faixa de IPs do Traefik. |

**`linkpulse`: a configuração do próprio projeto**

```yaml
linkpulse:
  base-url: http://localhost:8080
  code:
    alphabet: 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz
    multiplier: 2176477521915
  alias:
    reserved:
      - links
      - actuator
      # ... (12 palavras ao todo)
```

São os valores que viram o `LinkPulseProperties`. Dois avisos dos comentários do arquivo:

- `base-url` é sobrescrita por variável de ambiente no compose, no kind e na AWS.
- `alphabet` e `multiplier` **nunca devem mudar** depois que existirem links publicados: trocar qualquer um deles muda o código de todos os links já criados.

Logo acima desse bloco, um comentário longo lista as chaves opcionais (`clicks.*`, `ratelimit.*`) com o padrão e a variável de ambiente de cada uma. Elas não aparecem como chaves de verdade porque os padrões já vêm do `@DefaultValue`.

**`management`: o Actuator**

```yaml
management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```

A porta separada e os endpoints estão na seção "Actuator na porta 8081" logo abaixo. A última chave liga o histograma do tempo de resposta das requisições, que permite ao Prometheus calcular p95 e p99 ("95% das requisições responderam em menos de X ms").

#### Os arquivos de profile

`application-dev.yml` e `application-prod.yml` só contêm as chaves que **mudam** naquele ambiente. Todo o resto vem do `application.yml`.

`app/src/main/resources/application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/linkpulse
    username: linkpulse
    password: linkpulse
  liquibase:
    contexts: dev

linkpulse:
  ip-hash-key: dev-only-ip-hash-key-not-a-secret
```

- O banco local do `compose.dev.yaml`. A senha `linkpulse` só vale na sua máquina.
- `contexts: dev` liga o seed (guia 03).
- Uma `ip-hash-key` fixa, que o comentário avisa "não é segredo". Fora do dev, a chave vem obrigatoriamente de `LINKPULSE_IP_HASH_KEY`, com pelo menos 16 caracteres; sem ela, a aplicação não sobe.

`app/src/main/resources/application-prod.yml` só repete `spring.liquibase.contexts: prod`, de forma explícita, para garantir que o seed de dev nunca rode em produção. Banco, Valkey e MongoDB de produção chegam por variáveis de ambiente (`SPRING_DATASOURCE_URL`, `SPRING_DATA_REDIS_HOST`, `SPRING_MONGODB_URI` etc.).

O `Makefile` ativa o profile dev com `-Dspring-boot.run.profiles=dev`. O que muda no Liquibase por profile está no guia 03.

### Actuator na porta 8081

O **Actuator** é um "painel de instrumentos" da aplicação: endpoints prontos que dizem se ela está saudável (`/actuator/health`) e expõem métricas (`/actuator/prometheus`).

`management.server.port: 8081` sobe esses endpoints num **segundo servidor**, separado da API (8080). `exposure.include: health,info,prometheus` publica só esses três. Na prática:

- <http://localhost:8081/actuator/health>: responde `{"status":"UP"}` quando a aplicação está bem;
- <http://localhost:8081/actuator/prometheus>: texto com todas as métricas, no formato que o Prometheus lê (guia 16).

Por que duas portas? Em produção, só a 8080 fica exposta à internet; a 8081 fica numa rede interna, onde só o monitoramento a alcança. Ninguém de fora consegue ver as métricas. O `OpenApiIT` confere que a documentação da API não mostra endpoints de gerenciamento (`apiDocsDoesNotExposeManagementEndpoints`).

> As probes de liveness/readiness para o Kubernetes ainda não estão configuradas no `application.yml`: chegam nas próximas fases (ver guia 10).

### Virtual threads e `open-in-view`

- `spring.threads.virtual.enabled: true` faz o Tomcat atender cada requisição numa virtual thread (veja "Conceitos" acima). O consumer de cliques também roda numa virtual thread própria (guia 13).
- `open-in-view: false` desliga um padrão antigo do Spring chamado "Open Session in View". Ligado, ele mantém a conexão com o banco aberta até o fim da resposta HTTP, mesmo depois de o service terminar. Isso deixa o controller disparar consultas "escondidas" sem perceber e segura conexões por mais tempo. Desligado, o acesso ao banco fica restrito ao service, dentro da transação: um acesso fora dela falha de forma explícita, e o problema aparece na hora.

### Records no projeto

O projeto usa records para tudo o que só carrega dados: `CreateLinkRequest`, `LinkResponse`, `StatsResponse`, `CacheEntry.Found`, `ClickDocument`, `RateLimiter.Decision` e `LinkPulseProperties` (com `Code`, `Alias`, `Cache`, `Clicks` e `RateLimit`). Não há Lombok no `app/pom.xml`.

`app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java`

```java
public record CreateLinkRequest(
        @Schema(description = "URL de destino do link curto",
                example = "https://example.com/artigo?id=42")
        @NotBlank @Size(max = 2048) @HttpUrl String url,
        // ...
        String alias,
        // ...
        @Future OffsetDateTime expiresAt) {
}
```

Cada componente do record é um campo do JSON do `POST /links`. As anotações dizem como validá-lo (guia 05) e como documentá-lo na Swagger UI (`@Schema`).

O Boot 4 usa o **Jackson 3** para converter JSON em objetos. A decisão 01-06 no `.planning/STATE.md` registra que o Jackson 3 recusa um `OffsetDateTime` sem fuso horário (`2026-12-31T23:59:59`, sem o `Z` no fim). Por isso `expiresAt` sem fuso vira `400 malformed-request` (guia 05).

### Swagger UI

O `springdoc-openapi-starter-webmvc-ui` lê as anotações `@Tag`, `@Operation`, `@ApiResponse` e `@Schema` dos controllers e gera duas coisas:

- a **especificação OpenAPI**, um JSON que descreve toda a API: <http://localhost:8080/v3/api-docs>;
- a **Swagger UI**, uma página onde você lê a documentação e testa cada endpoint pelo navegador: <http://localhost:8080/swagger-ui.html>.

`app/src/main/java/dev/linkpulse/config/OpenApiConfig.java` só define o cabeçalho (título "Link Pulse API", versão "0.1.0").

## Por que assim?

- **Boot 4.1.1 em vez de 3.5.x:** a tabela de decisões do `.planning/PROJECT.md` registra que a linha 3.x ficou sem suporte gratuito (correções de segurança) desde 2026-06-30, enquanto a 4.1 é suportada até 2027-07.
- **Configuração tipada e validada na subida:** uma `linkpulse.base-url` inválida, um multiplicador não coprimo ou uma `ip-hash-key` curta derrubam a aplicação na hora, em vez de gerar links quebrados ou dados inseguros em produção.
- **`Clock` como bean:** expiração, cache, rate limit e estatísticas dependem do tempo; com um `Clock` injetado, os testes controlam o "agora".
- **Sem senha no yml base:** segredos de produção chegam por variável de ambiente e nunca ficam no repositório.
- **Customizar em vez de substituir:** `RedisConfig` e `MongoConfig` só ajustam a auto-configuração (timeouts, comportamento com a conexão caída). Eles não criam a conexão do zero, para não desligar o que o Boot já faz, inclusive a ligação automática com os containers dos testes (guia 06).

## Experimente

1. Suba com o profile dev (guia 09) e acesse `http://localhost:8081/actuator/health`. Depois tente `http://localhost:8080/actuator/health` e explique a diferença.
2. Rode a API com `LINKPULSE_BASE_URL=ftp://x` (Git Bash: `LINKPULSE_BASE_URL=ftp://x make run`) e leia a mensagem de falha da subida.
3. Abra `app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java` e encontre o teste que prova o binding por variável de ambiente.
4. Procure no `app/pom.xml` qual dependência traz o Liquibase e qual comentário explica por quê.

## Perguntas para fixar

1. O que muda entre `spring-boot-starter-web` (Boot 3) e `spring-boot-starter-webmvc` (Boot 4)?
2. Por que `CodeGenerator` não tem `@Component` e mesmo assim é injetado no `LinkService`?
3. Qual variável de ambiente sobrescreve `linkpulse.self-hosts`, e em que formato?
4. O que a separação entre as portas 8080 e 8081 permite fazer em produção?
5. Qual o risco de virtual threads com `synchronized` no Java 21?
6. Num método `@Bean`, o que o Spring faz com os parâmetros? E com o tipo de retorno?
7. Por que o código usa um `Clock` injetado em vez de `Instant.now()`?
8. O `application.yml` não tem bloco `linkpulse.ratelimit`. De onde vem o limite de 20 criações por minuto?
9. Se `linkpulse.base-url` estiver no `application.yml` e também em `LINKPULSE_BASE_URL`, qual valor vale?
10. Para que serve o `@Valid` em `@NotNull @Valid Code code`?

<details>
<summary>Respostas</summary>

1. Só o nome e a modularidade: o Boot 4 dividiu os starters em módulos menores, e o de Spring MVC passou a se chamar `webmvc`.
2. Porque o `ApplicationConfig` tem um método `@Bean` que o cria com `new` e o coloca no contexto; o `LinkService` o recebe pelo construtor.
3. `LINKPULSE_SELF_HOSTS`, com os hosts separados por vírgula.
4. Expor só a API (8080) ao público e deixar saúde e métricas (8081) numa rede interna.
5. Uma espera dentro de `synchronized` prende a virtual thread à thread do sistema (pinning), e ela perde a vantagem.
6. Os parâmetros são buscados no contexto e passados ao método; o objeto retornado vira um bean daquele tipo.
7. Porque um `Clock` injetado pode ser trocado por um relógio fixo nos testes, e o "agora" vira um valor conhecido.
8. Do `@DefaultValue("20")` no record `RateLimit` do `LinkPulseProperties`.
9. A variável de ambiente: ela tem prioridade sobre os arquivos `.yml`.
10. Faz a validação "entrar" no record `Code` e conferir as anotações dos campos dele (`@NotBlank`, `@Positive`).

</details>

---

[← Anterior: 01 · Estrutura e fluxo](01-estrutura-e-fluxo-de-requisicoes.md) · [Índice](README.md) · [Próximo: 03 · Postgres, JPA e Liquibase →](03-postgres-jpa-e-liquibase.md)
