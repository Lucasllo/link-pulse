# 02 · Spring Boot e Java 21

**Em uma frase:** o Link Pulse roda em Spring Boot 4.1.1 sobre Java 21, e quase toda a infraestrutura (servidor web, DataSource, Liquibase, Actuator) vem de starters e auto-configuração, ajustada por arquivos `application*.yml` e por um record de propriedades próprio.

## Conceitos

### Starter

Um starter é uma dependência Maven "guarda-chuva": ela puxa as bibliotecas de um tema e a auto-configuração correspondente. Você declara `spring-boot-starter-data-jpa` e ganha Hibernate, Spring Data JPA e pool de conexões, sem escolher versão (quem define é o BOM do Boot, ver guia 07).

No Boot 4, os starters ficaram mais modulares. O antigo `spring-boot-starter-web` deu lugar a `spring-boot-starter-webmvc`, e a auto-configuração do Liquibase saiu do núcleo e passou a exigir `spring-boot-starter-liquibase`.

### Auto-configuração

Na subida, o Boot olha o classpath e as propriedades e cria beans por conta própria. Se há driver JDBC e `spring.datasource.url`, ele cria o `DataSource`. Se há Liquibase e `spring.liquibase.change-log`, ele roda as migrações antes de a aplicação aceitar requisições. Se há Actuator, ele sobe os endpoints de gerenciamento. Você só escreve configuração quando quer algo diferente do padrão.

### Profiles

`application.yml` é a base. `application-dev.yml` e `application-prod.yml` só valem quando o profile correspondente está ativo, e sobrescrevem as chaves que repetem. Formas comuns de ativar: a variável de ambiente `SPRING_PROFILES_ACTIVE=dev` ou, com o plugin Maven, `-Dspring-boot.run.profiles=dev`.

### Records e virtual threads (Java 21)

- **Record** (Java 16+): `record Ponto(int x, int y) {}` gera construtor, acessores `x()`/`y()`, `equals`, `hashCode` e `toString`. Os campos são finais. Serve para DTOs e configurações sem precisar de Lombok.
- **Virtual threads** (JEP 444, Java 21): threads leves gerenciadas pela JVM. Uma thread bloqueada em I/O (esperando o banco, por exemplo) libera a thread do sistema operacional, então dá para atender muitas requisições simultâneas no modelo "uma thread por requisição", sem programação reativa. Cuidado no Java 21: um bloqueio dentro de `synchronized` "prende" (pinning) a thread do sistema; a correção (JEP 491) só chega no Java 24.

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

Dependências de produção: `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webmvc-ui` (versão 3.1.1 por property), `spring-boot-starter-data-jpa`, `spring-boot-starter-liquibase` e o driver `postgresql` (escopo `runtime`). O próprio pom comenta: "No Boot 4 a autoconfig do Liquibase saiu do core e precisa do starter próprio."

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

`@SpringBootApplication` liga três coisas: configuração, auto-configuração e varredura de componentes a partir do pacote `dev.linkpulse`. É por isso que `@RestController`, `@Service` e `@RestControllerAdvice` nos subpacotes são encontrados sem registro manual.

### `ApplicationConfig`: os beans que o projeto declara

`app/src/main/java/dev/linkpulse/config/ApplicationConfig.java` é anotada com `@Configuration(proxyBeanMethods = false)` e `@EnableConfigurationProperties(LinkPulseProperties.class)`, e declara quatro beans:

| Bean | Por quê (javadoc) |
|------|-------------------|
| `Clock clock()` → `Clock.systemUTC()` | "Único ponto de obtenção do tempo; os testes podem trocá-lo por um relógio fixo." |
| `CodeGenerator codeGenerator(...)` | Alfabeto ou multiplicador inválido "derruba o contexto na subida" |
| `AliasPolicy aliasPolicy(...)` | Recebe os reservados de `linkpulse.alias.reserved` |
| `TargetHostPolicy targetHostPolicy(...)` | Recebe `linkpulse.base-url` e `linkpulse.self-hosts` |

Repare que `CodeGenerator`, `AliasPolicy` e `TargetHostPolicy` são classes comuns, sem anotação do Spring: quem decide como construí-las é a configuração. Isso deixa as classes testáveis com `new` nos testes unitários.

### `LinkPulseProperties`: configuração tipada

`app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java`

```java
@ConfigurationProperties("linkpulse")
@Validated
public record LinkPulseProperties(
        @NotNull URI baseUrl,
        @NotNull @Valid Code code,
        @DefaultValue Alias alias,
        List<String> selfHosts) {
    // ...
    public record Code(@NotBlank String alphabet, @Positive long multiplier) {
    }
    // ...
    public record Alias(List<String> reserved) {
```

- Um record com `@ConfigurationProperties("linkpulse")` recebe as chaves `linkpulse.*` pelo construtor. Os records aninhados `Code` e `Alias` mapeiam `linkpulse.code.*` e `linkpulse.alias.*`.
- `@Validated` + `@NotNull`/`@Positive` validam na subida: configuração errada derruba a aplicação antes de ela aceitar tráfego.
- O construtor compacto valida `baseUrl` com uma mensagem fixa, que "nunca ecoa o valor, que pode conter credenciais", e transforma `selfHosts` nulo em lista vazia.
- **Relaxed binding:** a mesma propriedade aceita variável de ambiente em maiúsculas com `_`. O comentário do `application.yml` lista: `LINKPULSE_BASE_URL`, `LINKPULSE_CODE_ALPHABET`, `LINKPULSE_CODE_MULTIPLIER`, `LINKPULSE_ALIAS_RESERVED` e `LINKPULSE_SELF_HOSTS` (listas separadas por vírgula).

### Os três arquivos de configuração

`app/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: link-pulse
  threads:
    virtual:
      enabled: true
  # ...
  jpa:
    hibernate:
      # O schema vem só do Liquibase; o Hibernate apenas confere.
      ddl-auto: validate
    open-in-view: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
    # Default explícito; o profile dev sobrescreve para "dev".
    contexts: prod
# ...
management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        include: health,info
```

- `app/src/main/resources/application-dev.yml` traz o datasource `jdbc:postgresql://localhost:5432/linkpulse` (credenciais `linkpulse`, só para desenvolvimento local) e `spring.liquibase.contexts: dev`.
- `app/src/main/resources/application-prod.yml` só fixa `contexts: prod`. O datasource de produção vem de `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` e `SPRING_DATASOURCE_PASSWORD`.
- O `application.yml` base não tem URL de banco de propósito: "prod usa SPRING_DATASOURCE_URL/USERNAME/PASSWORD, o dev usa application-dev.yml e os ITs usam @ServiceConnection".

O `Makefile` ativa o profile dev com `-Dspring-boot.run.profiles=dev`. O que muda no Liquibase por profile está no guia 03.

### Actuator na porta 8081

`management.server.port: 8081` sobe os endpoints do Actuator num servidor separado da API (8080), e `exposure.include: health,info` publica só esses dois. Na prática: `http://localhost:8081/actuator/health`. Separar portas permite que, em produção, só a 8080 fique exposta ao público, enquanto health e futuras métricas ficam numa porta interna. O `OpenApiIT` confere que a spec da API não expõe endpoints de gerenciamento (`apiDocsDoesNotExposeManagementEndpoints`).

> O endpoint de métricas do Prometheus e as probes de liveness/readiness ainda não estão configurados: chegam nas Phases 2 a 4 (ver guia 10).

### Virtual threads e `open-in-view`

- `spring.threads.virtual.enabled: true` faz o Tomcat atender cada requisição numa virtual thread.
- `open-in-view: false` desliga o padrão "Open Session in View", que mantém a sessão do Hibernate (e a conexão) aberta até o fim da resposta HTTP. Desligado, o acesso ao banco fica restrito à transação do service: carregamento preguiçoso fora dela falha de forma explícita, em vez de disparar consultas escondidas na camada web.

### Records no projeto

`CreateLinkRequest`, `LinkResponse` e `LinkPulseProperties` (com `Code` e `Alias`) são records. Não há Lombok no `app/pom.xml`.

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

O Jackson do Boot 4 é a versão 3. A decisão 01-06 em `.planning/STATE.md` registra que o Jackson 3 rejeita `OffsetDateTime` sem offset; por isso `expiresAt` sem fuso vira 400 `malformed-request` (guia 05).

### Swagger UI

`springdoc-openapi-starter-webmvc-ui` gera a especificação a partir das anotações `@Tag`, `@Operation` e `@ApiResponse` dos controllers. `app/src/main/java/dev/linkpulse/config/OpenApiConfig.java` só define o cabeçalho (título "Link Pulse API", versão "0.1.0"). Endereços, segundo o README: <http://localhost:8080/swagger-ui.html> e <http://localhost:8080/v3/api-docs>.

## Por que assim?

- **Boot 4.1.1 em vez de 3.5.x:** a tabela de decisões do `.planning/PROJECT.md` registra que a linha 3.x ficou sem suporte OSS desde 2026-06-30, enquanto a 4.1 é suportada até 2027-07.
- **Configuração tipada e validada na subida:** `linkpulse.base-url` inválida ou multiplicador não coprimo derrubam a aplicação na hora (decisões 01-03 e 01-08 no `.planning/STATE.md`), em vez de gerar links quebrados em produção.
- **`Clock` como bean:** a expiração depende do tempo; com um `Clock` injetado, os testes controlam o "agora".
- **Sem URL de banco no yml base:** segredo de produção nunca fica no repositório.

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

---

[← Anterior: 01 · Estrutura e fluxo](01-estrutura-e-fluxo-de-requisicoes.md) · [Índice](README.md) · [Próximo: 03 · Postgres, JPA e Liquibase →](03-postgres-jpa-e-liquibase.md)
