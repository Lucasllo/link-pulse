# Phase 1: Fundação e núcleo de links - Research

**Researched:** 2026-10-06
**Domain:** Spring Boot 4.1.1 / Java 21 / Maven, JPA + Liquibase sobre PostgreSQL 18, qualidade estática (Checkstyle/SpotBugs), Testcontainers 2, GitHub Actions, portabilidade Windows/Linux
**Confidence:** ALTA (versões e APIs conferidas nesta sessão no Maven Central e no código-fonte do Hibernate; alguns comportamentos de runtime marcados como `[ASSUMED]` e cobertos por teste)

## Summary

A Phase 1 é um walking skeleton greenfield: repositório Maven com wrapper, `POST /links` e `GET /{code}` lendo do Postgres, schema via Liquibase, Problem Details, springdoc e CI verde. O PROJECT.md e o STATE.md fixaram **Spring Boot 4.1.1**. Por isso, o baseline 3.5.16 do CLAUDE.md não vale aqui; vale a "Variante Spring Boot 4.1" do STACK.md. Conferi o BOM 4.1.1 nesta sessão: Spring Framework 7.0.9, Hibernate 7.4.5.Final, Jackson 3.1.5, JUnit Jupiter 6.0.3, Testcontainers 2.0.5, Liquibase 5.0.3 (licença `FSL-1.1-ALv2`), pgjdbc 42.7.13 e surefire/failsafe 3.5.6. O Boot 4 trocou nomes de starters (`spring-boot-starter-webmvc`, `spring-boot-starter-liquibase`, `*-test`) e pacotes de teste (`@WebMvcTest`, `TestRestTemplate`), e removeu o `@MockBean`. São as primeiras coisas que quebram se o executor seguir exemplos do Boot 3.

Os riscos técnicos reais desta fase são pequenos e concretos, cada um com mitigação verificável:

1. **Overflow no `id * M`.** 62^7 = 3.521.614.606.208 e `Long.MAX_VALUE / 62^7 ≈ 2,6 milhões`, então a multiplicação estoura `long` para qualquer `M` útil. Confirmei com jshell: `Math.multiplyExact(N-1, M)` gera "long overflow". A saída é usar `BigInteger`.
2. **`nextval()` numa transação read-only.** Query methods do Spring Data rodam com `readOnly = true` por padrão.
3. **`save()` com ID atribuído.** O Spring Data trata a entidade como "não nova" e faz `merge` (SELECT + INSERT), a menos que ela implemente `Persistable`.
4. **`google_checks.xml` com severidade `warning` por padrão.** Com isso o build nunca quebra. Além disso, a indentação vem em 2 espaços.
5. **Ambiente Windows.** Esta máquina tem `core.autocrlf=true`, Maven e `make` não instalados, Docker daemon parado e espaço no caminho.

**Primary recommendation:** projeto Maven em `app/` com `spring-boot-starter-parent` 4.1.1 e `java.version=21`. Código atribuído via `nextval` explícito numa transação de escrita, entidade `Persistable<Long>` e `CodeGenerator` com `BigInteger`. Exceções num `@RestControllerAdvice extends ResponseEntityExceptionHandler`. ITs com containers declarados como `@Bean @ServiceConnection` numa `@TestConfiguration`. CI `./mvnw -B -ntp verify` com checkout num diretório com espaço.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Formato do código curto
- **D-01:** Embaralhamento por **permutação multiplicativa própria**, sem biblioteca: `code = base62(pad7((id * M) mod 62^7))`, com `M` coprimo de 62^7 (ímpar e não múltiplo de 31). A decodificação usa o inverso modular de `M`. Não usar Sqids. — **Reversibility:** one-way — trocar o algoritmo ou `M` muda todos os códigos já publicados (links quebram).
- **D-02:** Códigos gerados têm **comprimento fixo de 7 caracteres** (padding no alfabeto Base62), espaço de 62^7 ≈ 3,5 trilhões. O gerador deve falhar explicitamente se o ID ≥ 62^7. — **Reversibility:** one-way — o comprimento define a regex de código e a disjunção com o alias.
- **D-03:** Multiplicador (e, se houver, alfabeto) ficam em **propriedades de config** (`linkpulse.code.*`) com default no `application.yml` e override por variável de ambiente. Validar na inicialização que `M` é coprimo de 62^7. O README trata isso como ofuscação, não como segurança.
- **D-04:** Testes unitários (QUAL-01): ida e volta `decode(encode(id)) == id`, unicidade numa faixa grande de IDs, sempre 7 caracteres, e nenhum código gerado casando com a regex de alias.

#### Regras do alias
- **D-05:** Regex do alias: `^[a-zA-Z0-9_-]{4,32}$` com **pelo menos um `-` ou `_`**. Como códigos gerados são só alfanuméricos, os namespaces são disjuntos estruturalmente. Mesmo assim, manter `code UNIQUE` no banco. — **Reversibility:** costly — relaxar depois exige revalidar aliases existentes contra o espaço gerado.
- **D-06:** Alias **diferencia maiúsculas de minúsculas** (igual ao Base62); lookup por igualdade exata.
- **D-07:** Lista de reservados em config, comparação case-insensitive: rotas reais (`links`, `actuator`, `swagger-ui`, `v3`, `api-docs`, `health`, `favicon.ico`, `robots.txt`) + genéricas (`admin`, `api`, `static`, `login`). A regra do `-`/`_` já barra a maioria; a lista é defesa extra (também cobre variações como `api-docs`).
- **D-08:** Alias já existente → 409 em Problem Details (detectado também via violação de unique constraint, para cobrir corrida).

#### Contrato da API
- **D-09:** `POST /links` responde **201 Created** com header `Location` (URL curta) e corpo `{code, shortUrl, targetUrl, expiresAt, createdAt}`. — **Reversibility:** costly — contrato público usado por k6, smoke test e README.
- **D-10:** Expiração via campo `expiresAt` em **ISO-8601 com offset obrigatório** (ex.: `2026-12-31T23:59:59Z`), validado como futuro; persistido como `timestamptz`. Sem campo de duração relativa.
- **D-11:** A mesma URL longa enviada duas vezes **gera dois links independentes** (sem deduplicação, sem índice na URL).
- **D-12:** A base da `shortUrl` vem da propriedade **`linkpulse.base-url`** (default `http://localhost:8080`), sobrescrita por env no compose/kind/ECS. Não derivar de Host/X-Forwarded-*.

#### Estrutura do repo e build
- **D-13:** Pacote raiz **`dev.linkpulse`**, groupId `dev.linkpulse`, artifactId `link-pulse`. — **Reversibility:** costly — renomear pacote toca todos os arquivos, Checkstyle/SpotBugs excludes e configs.
- **D-14:** Organização **por feature**: `dev.linkpulse.link` (controller, service, repository, entidade, `CodeGenerator`), além de `dev.linkpulse.config` e `dev.linkpulse.common` (Problem Details, erros). Fases seguintes adicionam `.click`, `.stats`, `.ratelimit`, `.cache`.
- **D-15:** Checkstyle com base **Google (`google_checks.xml`) adaptada**: indentação de 4 espaços, severidade `error`, arquivo em `config/checkstyle/`. Engine 14.x sobrescrita no plugin (o default 9.3 não entende Java 21).
- **D-16:** Phase 1 entrega **Makefile base** (`help`, `build`, `test`, `verify`, `run`, `db-up`, `db-down`) e um **`compose.dev.yaml` só com Postgres 18** para rodar local. O `make up` completo é da Phase 3.

### Claude's Discretion
- Tamanho máximo da URL longa (sugestão: 2048) e validação de esquema `http`/`https` com host presente.
- Formato exato dos Problem Details (`type` URIs, campo `errors[]` por campo inválido, `code` no 404/410).
- Endpoint opcional `GET /links/{code}` de metadados: só se for barato; não é requisito.
- Estratégia de Testcontainers (classe base `AbstractIT` com containers compartilhados via `@ServiceConnection`).
- Escopo do workflow de CI (ubuntu-latest; matriz Windows não exigida — a compatibilidade Windows vem de `.gitattributes`, `mvnw` executável e testes de caminho com espaço).
- Obtenção do ID antes do INSERT (`nextval` explícito ou `@SequenceGenerator(allocationSize = 1)` alinhado ao `INCREMENT BY 1` do changelog).
- `Cache-Control` exato no 302 (ex.: `private, no-store` ou `no-cache, no-store, must-revalidate`).

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| LINK-01 | `POST /links` com URL longa (http/https, tamanho máximo validado) retorna código e URL curta | Validador `@HttpUrl` próprio (URI estrita + esquema + host + `@Size(max=2048)`); resposta 201 + `Location` (D-09); `linkpulse.base-url` (D-12). Ver Padrão 4 e o exemplo do controller |
| LINK-02 | Base62 do ID da sequência, obtido antes do INSERT, sem colisão | `select nextval('link_id_seq')` numa transação de escrita + entidade `Persistable<Long>` + `saveAndFlush`. Ver Armadilhas 2 e 3 |
| LINK-03 | Embaralhamento bijetivo (não enumerável) | `CodeGenerator` com `BigInteger` (Armadilha 1); `M` default verificado: `2176477521915`, inverso `3013170320947` |
| LINK-04 | Alias com regex disjunta, reservados e 409 | `AliasPolicy` com `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$` + lista case-insensitive + `existsByCode` + captura de `DataIntegrityViolationException` (corrida) |
| LINK-05 | Expiração opcional e futura | DTO com `OffsetDateTime expiresAt` + `@Future`; persistido como `Instant` → `timestamptz` (validate confirmado no código do Hibernate 7.4.5) |
| LINK-06 | Erros como Problem Details (RFC 9457) | `@RestControllerAdvice extends ResponseEntityExceptionHandler` + exceções `ErrorResponseException`; `errors[]` via `ProblemDetail.setProperty` |
| REDIR-01 | `GET /{code}` → 302 com `Cache-Control` sem cache | `ResponseEntity.status(FOUND).location(..).cacheControl(CacheControl.noStore().cachePrivate())`; mapping `/{code:[A-Za-z0-9_-]+}` |
| REDIR-02 | 404 inexistente, 410 expirado | `LinkNotFoundException`/`LinkExpiredException extends ErrorResponseException`; `Clock` injetável |
| DATA-01 | Liquibase YAML, contexts dev/prod, `ddl-auto=validate` | `spring-boot-starter-liquibase`; DDL sem context, seed com `contextFilter: dev`; `spring.liquibase.contexts` explícito por profile; `spring.liquibase.analytics-enabled=false` |
| QUAL-01 | Testes unitários do gerador | `CodeGeneratorTest` (ida e volta, unicidade, 7 caracteres, disjunção da regex, limites, validação de `M`) |
| QUAL-03 | Checkstyle e SpotBugs quebram o build | Checkstyle plugin 3.6.0 + engine 14.3.0 na fase `validate`, `severity=error`; SpotBugs plugin 4.10.4.1 `check` na fase `verify` + `exclude.xml` mínimo |
| QUAL-04 | OpenAPI/Swagger UI | `springdoc-openapi-starter-webmvc-ui` 3.1.1 (compilado contra Boot 4.1.0); `/swagger-ui.html`, `/v3/api-docs` |
| CONT-03 | Windows (Git Bash/WSL) + Linux, LF, `mvnw` executável, espaços, instrução do `make` | `.gitattributes` antes de qualquer arquivo; `git update-index --chmod=+x`; wrapper `only-script` 3.3.4 + Maven 3.9.16 com SHA-256; CI faz checkout em `link pulse/` |
| CI-01 | Actions roda build, testes (Testcontainers), Checkstyle e SpotBugs em push/PR | Um job `ubuntu-latest`: `./mvnw -B -ntp verify`; actions fixadas por SHA (resolvidos nesta sessão) |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

Diretivas acionáveis de `./.claude/CLAUDE.md` que valem nesta fase. Onde o PROJECT.md decidiu diferente, a variante Boot 4.1 prevalece, conforme a própria seção "Padrões de Stack por Variante" do CLAUDE.md.

- **Java 21 (Temurin no CI)**, **Maven via wrapper fixado em 3.9.16** (não usar 3.10.0 nem 4.0 RC), **Spring Boot 4.1.1** (decidido no PROJECT.md; o CLAUDE.md lista como "recomendado").
- **Não declarar versões gerenciadas pelo BOM** (Hibernate, pgjdbc, Liquibase, Testcontainers, Jackson). Não forçar Liquibase fora do BOM.
- **Checkstyle:** plugin 3.6.0 **com a dependência sobrescrita para `com.puppycrawl.tools:checkstyle` 14.3.0**, na fase `validate`, `failOnViolation=true`.
- **SpotBugs:** `spotbugs-maven-plugin` 4.10.4.1, goal `check` na fase `verify`, `effort=Max`, `threshold=Medium`, com arquivo de exclusões.
- **Testes:** `*Test` no surefire e `*IT` no failsafe; Testcontainers só no failsafe; `./mvnw verify` roda tudo.
- **PostgreSQL 18** (`postgres:18.6`), com volume montado em `/var/lib/postgresql` (o PGDATA mudou na imagem 18).
- **Liquibase:** master `db/changelog/db.changelog-master.yaml` com `include` de arquivos numerados; contexts dev/prod; seed com `contextFilter: dev`; sequência dedicada criada por changeset; `nextval` antes do insert.
- **O que NÃO usar:** Lombok (usar records), Jedis, `@Cacheable` no redirect, geração de código por hash/aleatório, actions de terceiros por tag mutável (fixar por SHA), Testcontainers < 1.21.4 (no Boot 4: 2.0.5 do BOM).
- **Actions:** `actions/checkout` v7, `actions/setup-java` v6 (`distribution: temurin`, `java-version: 21`, `cache: maven`), `actions/upload-artifact` v7.
- **Windows:** o Git Bash não traz `make` (`winget install ezwinports.make`, `scoop install make` ou WSL).
- **Porta de gerenciamento separada** (`management.server.port=8081`), expondo só o mínimo.
- **GSD Workflow Enforcement:** as edições do repositório acontecem dentro de `/gsd-execute-phase`.
- **Documentação em pt-BR.** Termos técnicos, código e caminhos ficam em inglês.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Validação de entrada (URL, alias, expiração) | API / Backend (Bean Validation + `AliasPolicy`) | Database (`NOT NULL`, `UNIQUE`, `varchar(2048)`) | A API dá o erro amigável (400/409); o banco é a rede de segurança contra corrida e bug |
| Geração do código curto | API / Backend (`CodeGenerator`, função pura) | Database (sequência `link_id_seq` fornece o ID) | A unicidade vem da sequência; a ofuscação é uma bijeção em memória, sem I/O |
| Unicidade do código/alias | Database (`uk_links_code`) | API (`existsByCode` + tradução de `DataIntegrityViolationException` → 409) | Só o banco garante unicidade sob concorrência |
| Redirect 302/404/410 | API / Backend (`RedirectController` + `LinkService`) | Database (lookup por `code`) | Sem cache nesta fase (o cache é da Phase 2) |
| Política de cache do navegador | API / Backend (header `Cache-Control`) | — | Só o servidor de origem controla isso; não há CDN |
| Schema | Database (Liquibase) | API (Hibernate `ddl-auto=validate` só confere) | Os changelogs são a única fonte do schema |
| Documentação da API | API / Backend (springdoc) | — | Gerada das anotações do controller |
| Build reproduzível e qualidade | Build tooling (Maven Wrapper, Checkstyle, SpotBugs) | CI (GitHub Actions) | O mesmo `./mvnw verify` roda local e no CI |
| Ambiente local | Dev tooling (Makefile + `compose.dev.yaml`) | — | Postgres local para `make run`; os ITs usam Testcontainers |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.springframework.boot:spring-boot-starter-parent` | **4.1.1** | Parent POM, BOM e pluginManagement | Decisão do PROJECT.md; suporte OSS até 2027-07 `[VERIFIED: Maven Central spring-boot-dependencies-4.1.1.pom]` |
| `spring-boot-starter-webmvc` | (BOM) | Spring MVC + Tomcat 11.0.24 + Jackson 3 | Nome novo no Boot 4; puxa `spring-boot-starter-jackson`, `-tomcat`, `spring-boot-webmvc` `[VERIFIED: spring-boot-starter-webmvc-4.1.1.pom]` |
| `spring-boot-starter-data-jpa` | (BOM) → Hibernate **7.4.5.Final** | Persistência dos links | `[VERIFIED: BOM 4.1.1]` |
| `spring-boot-starter-liquibase` | (BOM) → `liquibase-core` **5.0.3** | Migrações | No Boot 4 o Liquibase precisa do starter próprio. O `liquibase-core` 5.0.3 declara a licença `FSL-1.1-ALv2` e traz `snakeyaml` como dependência compile, então changelogs YAML funcionam `[VERIFIED: liquibase-core-5.0.3.pom, spring-boot-liquibase-4.1.1.pom]` |
| `spring-boot-starter-validation` | (BOM) → Hibernate Validator **9.1.3.Final** | Bean Validation nos DTOs | `[VERIFIED: BOM 4.1.1]` |
| `spring-boot-starter-actuator` | (BOM) | Health na porta 8081 (contrato de runtime das Phases 3–5) | Barato; liveness/readiness completos ficam para a Phase 4 |
| `org.postgresql:postgresql` | (BOM) **42.7.13**, `runtime` | Driver JDBC | `[VERIFIED: BOM 4.1.1]` |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | **3.1.1** | OpenAPI + Swagger UI | Linha 3.x para o Boot 4; o parent do springdoc 3.1.1 é `spring-boot-starter-parent` 4.1.0; swagger-ui 5.32.14 `[VERIFIED: springdoc-openapi-3.1.1.pom; CITED: springdoc.org]` |

### Supporting (test)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `spring-boot-starter-test` | (BOM) → JUnit Jupiter **6.0.3**, AssertJ 3.27.7, Mockito 5.23.0 | Testes unitários | Sempre |
| `spring-boot-starter-webmvc-test` | (BOM) | `@AutoConfigureMockMvc`, `@WebMvcTest`, `spring-boot-resttestclient` | ITs HTTP via MockMvc `[VERIFIED: pom 4.1.1 inclui spring-boot-webmvc-test e spring-boot-resttestclient]` |
| `spring-boot-testcontainers` | (BOM) | `@ServiceConnection` | ITs |
| `org.testcontainers:testcontainers-postgresql` | (BOM) **2.0.5** | `org.testcontainers.postgresql.PostgreSQLContainer` | ITs. **Artefato renomeado na 2.x**; o antigo `org.testcontainers:postgresql` não existe no BOM 2.0.5 `[VERIFIED: testcontainers-bom-2.0.5.pom]` |
| `org.testcontainers:testcontainers-junit-jupiter` | (BOM) 2.0.5 | Extensão JUnit (opcional) | Só se usar `@Testcontainers`/`@Container`; com o padrão `@Bean` não é necessário |

### Build e qualidade

| Tool | Version | Notes |
|------|---------|-------|
| Maven (wrapper `only-script`) | **3.9.16**, wrapper **3.3.4** | `distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`. Calculei nesta sessão sobre o zip oficial, e o SHA-512 bateu com o `.sha512` publicado no Maven Central `[VERIFIED]` |
| `maven-checkstyle-plugin` | **3.6.0** (default interno `checkstyleVersion` 9.3) | Sobrescrever a dependência para `com.puppycrawl.tools:checkstyle:14.3.0` `[VERIFIED: maven-checkstyle-plugin-3.6.0.pom linha 79: <checkstyleVersion>9.3</checkstyleVersion>]` |
| `com.puppycrawl.tools:checkstyle` | **14.3.0** (latest, 2026-09-27) | `[VERIFIED: maven-metadata.xml]` |
| `com.github.spotbugs:spotbugs-maven-plugin` | **4.10.4.1** (engine 4.10.4) | `[VERIFIED: pom do plugin, <spotbugs.version>4.10.4</spotbugs.version>]` |
| surefire / failsafe | 3.5.6 (BOM) | O parent já configura o failsafe com os goals `integration-test` + `verify`; basta declarar o plugin em `<build><plugins>` `[VERIFIED: spring-boot-starter-parent-4.1.1.pom]` |
| maven-compiler-plugin | 3.15.0 (BOM) | O parent usa `<java.version>17</java.version>` por padrão; **sobrescrever para 21** `[VERIFIED: parent pom linha 14]` |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `nextval` explícito + `Persistable` | `@GeneratedValue(SEQUENCE)` + `@SequenceGenerator(allocationSize=1)` | Com o gerador do Hibernate, o ID só existe no `persist()`, e o `code NOT NULL` precisa estar setado antes do flush. O `EntityInsertAction` captura o estado no `persist` `[ASSUMED]`, o que arrisca INSERT com `code` nulo ou INSERT + UPDATE. O `nextval` explícito é linear e legível |
| MockMvc nos ITs | `RestTestClient` / `TestRestTemplate` (Boot 4: `@AutoConfigureRestTestClient` / `@AutoConfigureTestRestTemplate`) | O MockMvc nunca segue redirect, então asserts de 302/`Location`/`Cache-Control` ficam triviais. Um cliente HTTP real pode seguir 302 conforme a configuração do request factory `[ASSUMED]` |
| Containers como `@Bean` numa `@TestConfiguration` | `static @Container` numa `AbstractIT` com `@Testcontainers` | O `static @Container` é parado pela extensão JUnit ao fim de cada classe, mas o contexto Spring fica no cache apontando para o container morto (Armadilha 9) |
| find-sec-bugs | — | Adiar: gera `SPRING_ENDPOINT` em todo endpoint e `UNVALIDATED_REDIRECT` no 302, que é o propósito do produto. Ruído sem ganho no MVP |

**Installation (trecho do `app/pom.xml`):**
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>4.1.1</version>
  <relativePath/>
</parent>
<groupId>dev.linkpulse</groupId>
<artifactId>link-pulse</artifactId>
<version>0.1.0-SNAPSHOT</version>
<properties>
  <java.version>21</java.version>                 <!-- parent default é 17 -->
  <springdoc.version>3.1.1</springdoc.version>
  <checkstyle.version>14.3.0</checkstyle.version>
</properties>
<dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-liquibase</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
  <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>${springdoc.version}</version></dependency>

  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-testcontainers</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers-postgresql</artifactId><scope>test</scope></dependency>
</dependencies>
```

## Package Legitimacy Audit

O seam `gsd-tools package-legitimacy check` só aceita `npm|pypi|crates` (a chamada com `--ecosystem maven` devolveu o erro de uso). Por isso, conferi cada artefato direto no Maven Central nesta sessão (`maven-metadata.xml` / `.pom`). Todos são de groupIds oficiais com histórico de anos. Nenhum tem `postinstall`, porque o Maven não executa scripts de instalação.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `org.springframework.boot:*` 4.1.1 | Maven Central | 12+ anos | massivo | github.com/spring-projects/spring-boot | OK (manual) | Approved |
| `org.testcontainers:testcontainers-postgresql` 2.0.5 | Maven Central | artefato 2.x novo; projeto 10+ anos | alto | github.com/testcontainers/testcontainers-java | OK (manual; gerenciado pelo BOM do Boot) | Approved |
| `org.liquibase:liquibase-core` 5.0.3 | Maven Central | 15+ anos | alto | github.com/liquibase/liquibase | OK (licença FSL-1.1-ALv2, registrar no README) | Approved |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` 3.1.1 | Maven Central | 6+ anos | alto | github.com/springdoc/springdoc-openapi | OK (manual; documentado em springdoc.org) | Approved |
| `com.puppycrawl.tools:checkstyle` 14.3.0 | Maven Central | 20+ anos | alto | github.com/checkstyle/checkstyle | OK (manual) | Approved |
| `org.apache.maven.plugins:maven-checkstyle-plugin` 3.6.0 | Maven Central | 15+ anos | alto | github.com/apache/maven-checkstyle-plugin | OK (manual) | Approved |
| `com.github.spotbugs:spotbugs-maven-plugin` 4.10.4.1 | Maven Central | 8+ anos | alto | github.com/spotbugs/spotbugs-maven-plugin | OK (manual) | Approved |
| `org.apache.maven.wrapper:maven-wrapper-distribution` 3.3.4 | Maven Central | 3+ anos | alto | github.com/apache/maven-wrapper | OK (manual) | Approved |

**Packages removed due to [SLOP] verdict:** nenhum
**Packages flagged as suspicious [SUS]:** nenhum

## Architecture Patterns

### System Architecture Diagram

```
                 cliente (curl / Swagger UI / testes)
                      │                         │
           POST /links│ JSON                    │ GET /{code}
                      ▼                         ▼
        ┌──────────────────────────┐   ┌─────────────────────────────┐
        │ LinkController           │   │ RedirectController          │
        │ @Valid CreateLinkRequest │   │ path {code:[A-Za-z0-9_-]+}  │
        └────────────┬─────────────┘   └──────────────┬──────────────┘
          400 ◄──────┤ Bean Validation falhou          │
                     ▼                                 ▼
        ┌──────────────────────────────────────────────────────────┐
        │ LinkService                                              │
        │  create():                         resolve(code):        │
        │   alias? ─sim─► AliasPolicy ─inválido/reservado─► 400    │
        │     │           existsByCode ─sim─► 409                  │
        │     ▼                              findByCode            │
        │   nextval('link_id_seq')            ├─ vazio ──► 404     │
        │     ▼                               ├─ expirado ► 410    │
        │   code = alias ?: CodeGenerator     └─ ok ──► URL        │
        │          .encode(id)  (BigInteger)                       │
        │     ▼                                                    │
        │   saveAndFlush(Link Persistable)                         │
        │     └─ DataIntegrityViolation (corrida) ─► 409           │
        └──────────────┬───────────────────────────────┬───────────┘
                       ▼                               ▼
               ┌──────────────────────────────────────────────┐
               │ PostgreSQL 18: link_id_seq, links            │
               │ (uk_links_code), schema só via Liquibase     │
               └──────────────────────────────────────────────┘
                       │
   201 + Location ◄────┘       302 + Location + Cache-Control: no-store, private
   (shortUrl = linkpulse.base-url + "/" + code)

   Erros ──► GlobalExceptionHandler (ResponseEntityExceptionHandler)
             ──► application/problem+json (RFC 9457)
```

### Recommended Project Structure

O ARCHITECTURE.md recomenda o monorepo com o Maven em `app/`. O CONTEXT.md não trava isso. A Phase 3 usa `docker build app/` como contexto autocontido, então começar em `app/` evita um `git mv` depois.

```
link-pulse/                         (raiz do repositório)
├── .gitattributes                  # PRIMEIRO arquivo commitado (LF)
├── .gitignore
├── .github/
│   ├── workflows/ci.yml
│   └── dependabot.yml              # opcional: github-actions + maven
├── Makefile                        # help build test verify run db-up db-down
├── compose.dev.yaml                # só postgres:18.6
├── README.md                       # pt-BR: requisitos, make no Windows, comandos sem make
└── app/
    ├── pom.xml
    ├── mvnw  mvnw.cmd              # mvnw com bit +x no índice do git
    ├── .mvn/wrapper/maven-wrapper.properties
    ├── config/
    │   ├── checkstyle/checkstyle.xml          # google_checks 14.3.0 adaptado
    │   └── spotbugs/exclude.xml
    └── src/
        ├── main/java/dev/linkpulse/
        │   ├── LinkPulseApplication.java      # @ConfigurationPropertiesScan
        │   ├── link/
        │   │   ├── LinkController.java   RedirectController.java
        │   │   ├── LinkService.java      LinkRepository.java   Link.java
        │   │   ├── CodeGenerator.java    AliasPolicy.java
        │   │   ├── CreateLinkRequest.java  LinkResponse.java   (records)
        │   │   └── LinkNotFoundException.java  LinkExpiredException.java  AliasConflictException.java ...
        │   ├── config/
        │   │   ├── LinkPulseProperties.java   # record @ConfigurationProperties("linkpulse") @Validated
        │   │   └── ClockConfig.java  OpenApiConfig.java
        │   └── common/
        │       ├── GlobalExceptionHandler.java
        │       └── validation/HttpUrl.java  HttpUrlValidator.java
        ├── main/resources/
        │   ├── application.yml  application-dev.yml  application-prod.yml
        │   └── db/changelog/
        │       ├── db.changelog-master.yaml
        │       └── changes/001-create-link-id-seq.yaml  002-create-links.yaml  003-seed-dev.yaml
        └── test/java/dev/linkpulse/
            ├── link/CodeGeneratorTest.java  AliasPolicyTest.java  HttpUrlValidatorTest.java
            ├── TestcontainersConfiguration.java  TestLinkPulseApplication.java
            ├── AbstractIT.java
            └── link/LinkApiIT.java  RedirectIT.java  ConcurrencyIT.java  LiquibaseProdContextIT.java
```

### Pattern 1: Código via `nextval` explícito + entidade `Persistable`

**What:** buscar o ID na sequência, calcular o código e fazer um único INSERT com `id` e `code`, numa transação de escrita.
**When to use:** sempre nesta fase (D-01/D-02, LINK-02).
**Por que `Persistable`:** "If the identifier property is `null`, then the entity is assumed to be new. Otherwise, it is assumed to be not new." e "If an entity implements `Persistable`, Spring Data JPA delegates the new detection to the `isNew(…)` method" `[CITED: docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html]`. Sem isso, `save()` chama `merge`, que faz SELECT + INSERT.

```java
// Source: padrão recomendado pela doc do Spring Data JPA (entidades com ID atribuído)
@Entity
@Table(name = "links")
public class Link implements Persistable<Long> {
    @Id
    private Long id;
    @Column(nullable = false, length = 32)
    private String code;
    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Transient
    private boolean isNew = true;

    protected Link() { }   // JPA

    // Fábrica estática: nada de validação/throw no construtor (SpotBugs CT_CONSTRUCTOR_THROW)
    public static Link create(long id, String code, String targetUrl, Instant expiresAt, Instant createdAt) {
        Link l = new Link();
        l.id = id; l.code = code; l.targetUrl = targetUrl; l.expiresAt = expiresAt; l.createdAt = createdAt;
        return l;
    }

    @PostLoad @PostPersist
    void markNotNew() { this.isNew = false; }

    @Override public Long getId() { return id; }
    @Override public boolean isNew() { return isNew; }
    public boolean isExpiredAt(Instant now) { return expiresAt != null && !now.isBefore(expiresAt); }
    // getters...
}

public interface LinkRepository extends JpaRepository<Link, Long> {
    @Query(value = "select nextval('link_id_seq')", nativeQuery = true)
    long nextId();
    Optional<Link> findByCode(String code);
    boolean existsByCode(String code);
}

@Service
public class LinkService {
    @Transactional   // transação de ESCRITA: nextval não roda em transação read-only (Armadilha 2)
    public Link create(CreateLinkRequest req) {
        String alias = req.alias();
        if (alias != null) {
            aliasPolicy.check(alias);                        // 400 se inválido/reservado
            if (repo.existsByCode(alias)) throw new AliasConflictException(alias);   // 409
        }
        long id = repo.nextId();
        String code = alias != null ? alias : codeGenerator.encode(id);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);   // precisão do timestamptz
        try {
            return repo.saveAndFlush(Link.create(id, code, req.url(), toInstant(req.expiresAt()), now));
        } catch (DataIntegrityViolationException e) {
            if (alias != null) throw new AliasConflictException(alias);   // corrida (D-08)
            throw e;   // código gerado colidindo é impossível por D-05: bug → 500
        }
    }
}
```

### Pattern 2: `CodeGenerator` com permutação multiplicativa em `BigInteger`

**What:** bijeção em `[0, 62^7)`, `x = id·M mod 62^7`, codificada em Base62 big-endian com padding para 7 caracteres.
**Valores verificados nesta sessão (jshell):** `N = 62^7 = 3521614606208`; `Long.MAX_VALUE / N = 2619074`; `M = 2176477521915` (≈ N·0,618, ímpar, não múltiplo de 31, `gcd = 1`); `M⁻¹ mod N = 3013170320947` (`M·M⁻¹ mod N = 1`). Com o alfabeto `0-9A-Za-z`, os IDs 1..5 viram `cJinsNv`, `EdRbklq`, `qxAPd9l`, `TGtDVXg` e `5ac1Nvb`. `BigInteger.valueOf(62).modInverse(N)` lança `ArithmeticException: BigInteger not invertible.`, que serve como validação natural de coprimalidade.

```java
// Source: implementação própria (D-01); valores conferidos com jshell nesta sessão
public final class CodeGenerator {       // final: evita CT_CONSTRUCTOR_THROW do SpotBugs
    public static final int LENGTH = 7;
    public static final long SPACE = 3_521_614_606_208L;   // 62^7
    private static final BigInteger N = BigInteger.valueOf(SPACE);

    private final char[] alphabet;
    private final BigInteger m;
    private final BigInteger mInverse;

    public CodeGenerator(String alphabet, long multiplier) {
        if (alphabet.length() != 62 || alphabet.chars().distinct().count() != 62
                || !alphabet.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("alfabeto deve ter 62 caracteres alfanuméricos distintos");
        }
        if (multiplier <= 0 || multiplier >= SPACE) {
            throw new IllegalArgumentException("multiplicador fora de (0, 62^7)");
        }
        this.alphabet = alphabet.toCharArray();
        this.m = BigInteger.valueOf(multiplier);
        this.mInverse = m.modInverse(N);   // ArithmeticException se gcd(M, 62^7) != 1
    }

    public String encode(long id) {
        if (id < 0 || id >= SPACE) {
            throw new IllegalArgumentException("id fora do espaço de 7 caracteres: " + id);   // D-02
        }
        long x = BigInteger.valueOf(id).multiply(m).mod(N).longValueExact();   // NUNCA id * M em long
        char[] out = new char[LENGTH];
        for (int i = LENGTH - 1; i >= 0; i--) {
            out[i] = alphabet[(int) (x % 62)];
            x /= 62;
        }
        return new String(out);
    }

    public long decode(String code) { /* Base62 → x; x·M⁻¹ mod N */ }
}
```

Para a validação na inicialização (D-03), a `LinkPulseProperties` (record, que é implicitamente `final`) e um `@Bean CodeGenerator` construído a partir dela fazem o contexto falhar ao subir se `M` for inválido. Isso é mais simples que um validador customizado.

### Pattern 3: Problem Details centralizado

**What:** um `@RestControllerAdvice` que estende `ResponseEntityExceptionHandler`, mais exceções de domínio que estendem `ErrorResponseException`.
**Fatos (Spring Framework 7.0.9):** o `ResponseEntityExceptionHandler` "Handles all Spring MVC exceptions" e "any `ErrorResponseException`". Campos extras entram no mapa `properties`, e o `ProblemDetailJacksonMixin` "Unwraps the 'properties' Map to render as top-level JSON properties" `[CITED: docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html]`. O Boot 4.1.1 tem `spring.mvc.problemdetails.enabled` e `ProblemDetailsExceptionHandler` `[VERIFIED: spring-boot-webmvc-4.1.1.jar]`. Com um advice próprio, essa propriedade é desnecessária: o handler do Boot recua quando já existe um `ResponseEntityExceptionHandler` `[ASSUMED: mesmo comportamento do Boot 3]`.

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ex.getBody();
        pd.setType(URI.create("/problems/validation-error"));
        pd.setTitle("Requisição inválida");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList());
        return handleExceptionInternal(ex, pd, headers, status, request);
    }
    // handleHttpMessageNotReadable: JSON inválido ou expiresAt sem offset → 400 com detail útil
}

public class LinkExpiredException extends ErrorResponseException {
    public LinkExpiredException(String code, Instant expiredAt) {
        super(HttpStatus.GONE, asProblem(code, expiredAt), null);
    }
    private static ProblemDetail asProblem(String code, Instant expiredAt) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, "O link '" + code + "' expirou.");
        pd.setType(URI.create("/problems/link-expired"));
        pd.setTitle("Link expirado");
        pd.setProperty("code", code);
        pd.setProperty("expiredAt", expiredAt.toString());
        return pd;
    }
}
```

`type`: a RFC 9457 diz que "When this member is not present, its value is assumed to be 'about:blank'", que "when relative URIs are used, they include the full path" e que "The type URI is allowed to be a non-resolvable URI" `[CITED: rfc-editor.org/rfc/rfc9457.html §3.1.1]`. Recomendação (Claude's Discretion): caminhos completos relativos `/problems/{validation-error|alias-conflict|alias-reserved|link-not-found|link-expired}`, com `code` como propriedade extra no 404/410/409. Os textos de `title`/`detail` ficam em pt-BR.

### Pattern 4: Validação de URL própria

```java
// @HttpUrl: URI estrita + esquema http/https + host presente. O tamanho fica com @Size(max = 2048)
public final class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {
    @Override public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true;                 // @NotBlank cuida do nulo
        try {
            URI uri = new URI(value);                   // rejeita espaços e caracteres ilegais
            String scheme = uri.getScheme();
            return scheme != null
                && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (URISyntaxException e) { return false; }
    }
}

public record CreateLinkRequest(
        @NotBlank @Size(max = 2048) @HttpUrl String url,
        String alias,                                   // validado pela AliasPolicy (regex + reservados)
        @Future OffsetDateTime expiresAt) { }          // offset obrigatório (D-10)
```

Recomendação extra (barata, Claude's Discretion): rejeitar URL cujo host seja o host de `linkpulse.base-url`. Isso evita loop de redirect e cadeias de ofuscação (PITFALLS, "Erros de Segurança").

### Pattern 5: Redirect

```java
@RestController
public class RedirectController {
    // Regex sem quantificador {m,n}: o '.' de favicon.ico / swagger-ui.html cai fora e vira 404 do Spring
    @GetMapping("/{code:[A-Za-z0-9_-]+}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = linkService.resolve(code);      // lança 404/410 (ErrorResponseException)
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .cacheControl(CacheControl.noStore().cachePrivate())   // "no-store, private"
                .build();
    }
}
```

### Pattern 6: Testcontainers como beans (padrão start.spring.io)

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {    // org.testcontainers.postgresql.PostgreSQLContainer (TC 2.x, sem generics)
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.6"));
    }
}

@SpringBootTest
@AutoConfigureMockMvc                    // org.springframework.boot.webmvc.test.autoconfigure (Boot 4)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIT { @Autowired protected MockMvcTester mvc; }

// Dev sem compose: ./mvnw spring-boot:test-run
public class TestLinkPulseApplication {
    public static void main(String[] args) {
        SpringApplication.from(LinkPulseApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
```

Fatos conferidos nos jars 4.1.1 e no Testcontainers 2.0.5:
- `@ServiceConnection` fica em `org.springframework.boot.testcontainers.service.connection`.
- `@AutoConfigureMockMvc` e `@WebMvcTest` ficam em `org.springframework.boot.webmvc.test.autoconfigure`.
- `@DataJpaTest` fica em `org.springframework.boot.data.jpa.test.autoconfigure`.
- `TestRestTemplate` fica em `org.springframework.boot.resttestclient`.
- O `JdbcContainerConnectionDetailsFactory` (`spring-boot-jdbc`) atende qualquer `JdbcDatabaseContainer`.
- A nova `org.testcontainers.postgresql.PostgreSQLContainer` estende `JdbcDatabaseContainer<PostgreSQLContainer>`. A antiga `org.testcontainers.containers.PostgreSQLContainer<SELF>` continua no jar, mas carrega o marcador `Deprecated`.

`[VERIFIED: listagem dos jars spring-boot-webmvc-test, spring-boot-testcontainers, spring-boot-resttestclient, spring-boot-data-jpa-test, spring-boot-jdbc 4.1.1 e testcontainers-postgresql 2.0.5]`

### Pattern 7: Liquibase YAML com contexts

```yaml
# db/changelog/db.changelog-master.yaml
databaseChangeLog:
  - include: { file: db/changelog/changes/001-create-link-id-seq.yaml }
  - include: { file: db/changelog/changes/002-create-links.yaml }
  - include: { file: db/changelog/changes/003-seed-dev.yaml }

# changes/002-create-links.yaml  (DDL: NUNCA com context)
databaseChangeLog:
  - changeSet:
      id: 002-create-links
      author: link-pulse
      changes:
        - createTable:
            tableName: links
            columns:
              - column: { name: id, type: BIGINT, constraints: { primaryKey: true, primaryKeyName: pk_links, nullable: false } }
              - column: { name: code, type: VARCHAR(32), constraints: { nullable: false, unique: true, uniqueConstraintName: uk_links_code } }
              - column: { name: target_url, type: VARCHAR(2048), constraints: { nullable: false } }
              - column: { name: expires_at, type: TIMESTAMP WITH TIME ZONE }
              - column: { name: created_at, type: TIMESTAMP WITH TIME ZONE, defaultValueComputed: now(), constraints: { nullable: false } }

# changes/001: createSequence { sequenceName: link_id_seq, startValue: 1, incrementBy: 1 }
# changes/003-seed-dev.yaml: changeSet com contextFilter: dev, insert de um link demo com alias "demo-link"
#   e id via valueComputed: nextval('link_id_seq')
```

```yaml
# application.yml (defaults seguros)
spring:
  application.name: link-pulse
  jpa:
    hibernate.ddl-auto: validate
    open-in-view: false
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
    contexts: prod                 # explícito; o profile dev sobrescreve para "dev"
    analytics-enabled: false       # Liquibase OSS envia analytics por padrão
  threads.virtual.enabled: true
server.port: 8080
management:
  server.port: 8081
  endpoints.web.exposure.include: health,info
linkpulse:
  base-url: http://localhost:8080
  code:
    alphabet: 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz
    multiplier: 2176477521915
  alias:
    reserved: [links, actuator, swagger-ui, v3, api-docs, health, favicon.ico, robots.txt, admin, api, static, login]
```

Propriedades conferidas no `spring-boot-liquibase-4.1.1.jar`: `spring.liquibase.contexts`, `spring.liquibase.change-log` (default `classpath:/db/changelog/db.changelog-master.yaml`) e `spring.liquibase.analytics-enabled` `[VERIFIED]`. Os nomes `linkpulse.code.alphabet`, `linkpulse.code.multiplier` e `linkpulse.alias.reserved` são proposta minha, dentro do prefixo `linkpulse.code.*` fixado pelo D-03 `[ASSUMED]`.

### Anti-Patterns to Avoid
- **`id * M` em `long`:** estoura silenciosamente (sem exceção) e quebra a bijeção. Usar `BigInteger` (ou `Math.multiplyHigh` com redução de 128 bits, que é menos legível).
- **`@GeneratedValue` com `allocationSize` default (50) contra `INCREMENT BY 1`:** PITFALLS, Armadilha 3. Com ID atribuído, o problema desaparece.
- **`spring.jpa.hibernate.ddl-auto=update` "só em dev":** mascara divergência do schema. O valor é sempre `validate`.
- **Context em changeset de DDL:** em prod a tabela não seria criada.
- **Mapear `GET /{code}` sem restrição de caracteres:** o controller passa a capturar `favicon.ico`, `swagger-ui.html` etc.
- **`@MockBean`:** foi removido no Boot 4. Usar `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`).
- **Lombok:** proibido pelo CLAUDE.md. Records cobrem DTOs.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Formato de erro HTTP | JSON de erro próprio | `ProblemDetail` + `ResponseEntityExceptionHandler` + `ErrorResponseException` | Já cobre todas as exceções do Spring MVC (415, 405, 400 de JSON malformado, `NoResourceFoundException`) |
| Unicidade sob concorrência | Lock em memória ou "check-then-insert" puro | Constraint `UNIQUE` + tradução de `DataIntegrityViolationException` | Só o banco serializa inserções concorrentes |
| Geração de IDs | Contador em memória/Redis | Sequência Postgres (`nextval`) | Atômica, transacional e sem colisão entre réplicas |
| Containers de teste | Scripts `docker run` nos testes | Testcontainers 2 + `@ServiceConnection` | Ciclo de vida e porta aleatória automáticos; o mesmo código roda no Windows e no CI |
| Parsing de data com offset | Regex de ISO-8601 | `OffsetDateTime` + Jackson 3 (java.time embutido em `tools.jackson.databind.ext.javatime`) | O parser padrão exige o offset `[VERIFIED: jackson-databind-3.1.5.jar contém ext/javatime]` |
| Wrapper do Maven | Script de download próprio | `maven-wrapper-distribution` 3.3.4 `only-script` | Já trata quoting de caminhos (`"$MAVEN_HOME/bin/$MVN_CMD"`) e verificação de SHA-256 |
| Documentação da API | YAML OpenAPI escrito à mão | springdoc 3.1.1 | Fica sincronizada com o código |

**Key insight:** o diferencial desta fase está em três peças pequenas e legíveis (permutação, regex disjunta, contexts do Liquibase). Todo o resto deve ser o caminho padrão do Spring, para o avaliador reconhecer de imediato.

## Common Pitfalls

### Pitfall 1: Overflow na permutação multiplicativa
**What goes wrong:** `(id * M) % N` em `long` dá valores errados (sem exceção) assim que `id * M > 9,2e18`. Com `M ≈ 2,2e12`, isso acontece a partir de `id ≈ 4,2 milhões`. Os códigos podem colidir ou o `decode` deixa de inverter.
**Why it happens:** `62^7 ≈ 3,5e12` e `Long.MAX_VALUE / 62^7 = 2619074` `[VERIFIED: jshell nesta sessão]`.
**How to avoid:** `BigInteger.valueOf(id).multiply(m).mod(N).longValueExact()`. Testar `encode`/`decode` com IDs perto de `62^7 - 1`, não só com IDs pequenos.
**Warning signs:** o teste de unicidade passa com 1..100.000 mas falha numa faixa alta.

### Pitfall 2: `nextval()` em transação read-only
**What goes wrong:** `ERROR: cannot execute nextval() in a read-only transaction`.
**Why it happens:** query methods do Spring Data herdam o `@Transactional(readOnly = true)` do `SimpleJpaRepository`, e o Spring propaga o read-only para a conexão JDBC, que o pgjdbc traduz em transação `READ ONLY` `[ASSUMED: comportamento conhecido; não reproduzido nesta sessão]`.
**How to avoid:** chamar `nextId()` dentro de `LinkService.create` anotado com `@Transactional` (escrita). Alternativa: anotar o próprio `nextId()` com `@Transactional`. O primeiro IT de criação detecta o problema.
**Warning signs:** o erro aparece logo no primeiro `POST /links` do IT.

### Pitfall 3: `save()` com ID atribuído faz `merge`
**What goes wrong:** cada criação gera um SELECT extra. Num cenário de bug (ID repetido), o `merge` viraria UPDATE silencioso em vez de erro.
**Why it happens:** com ID não nulo e sem `@Version`, o Spring Data considera a entidade "não nova" `[CITED: docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html]`.
**How to avoid:** `implements Persistable<Long>` com flag `@Transient isNew` alternada em `@PostPersist`/`@PostLoad`. Usar `saveAndFlush` para que a violação de unique saia dentro do método (D-08).
**Warning signs:** `select ... from links where id=?` antes de cada insert no log SQL.

### Pitfall 4: Checkstyle que nunca falha (ou que reprova o projeto inteiro)
**What goes wrong:** o `google_checks.xml` 14.3.0 declara `<property name="severity" value="${org.checkstyle.google.severity}" default="warning"/>`. Com o `violationSeverity` padrão do plugin (`error`), as violações viram warning e o build passa. Na direção oposta, `Indentation` vem com `basicOffset` 2, `caseIndent` 2, `lineWrappingIndentation` 4 e `arrayInitIndent` 2, e o código com 4 espaços (D-15) falha em toda linha `[VERIFIED: google_checks.xml no tag checkstyle-14.3.0, linhas 23 e 319-325]`.
**How to avoid:** copiar o `google_checks.xml` exatamente do tag `checkstyle-14.3.0` para `app/config/checkstyle/checkstyle.xml` e fazer estes ajustes:
- trocar a severidade para `value="error"`;
- em `Indentation`, usar `basicOffset=4`, `braceAdjustment=0`, `caseIndent=4`, `throwsIndent=8`, `lineWrappingIndentation=8` e `arrayInitIndent=4`;
- remover `MissingJavadocMethod`, que cobra Javadoc em todo método público, inclusive controllers. Manter `MissingJavadocType` para cobrar Javadoc só por tipo (recomendação).

Garantir também que o plugin rode na fase `validate` com `failOnViolation=true`. Atenção ao `CustomImportOrder` do Google: estáticos primeiro, depois todos os outros num bloco único em ordem ASCII. A ordenação padrão do IDE costuma divergir.
**Warning signs:** `[WARN]` no log do Checkstyle com o build `SUCCESS`; ou centenas de violações `Indentation` no primeiro build.

### Pitfall 5: SpotBugs em código Spring/JPA
**What goes wrong:**
- `CT_CONSTRUCTOR_THROW` (desde a 4.8) dispara em construtores que lançam exceção em classes não `final`, como um `CodeGenerator` que valida `M` no construtor.
- `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` disparam em records com `List` e em beans injetados mutáveis (`LinkPulseProperties`) `[VERIFIED: os padrões constam em spotbugs.readthedocs.io/bugDescriptions; CHANGELOG 4.10.4 menciona correções de FP do CT_CONSTRUCTOR_THROW]`.
**How to avoid:** classes de validação como `final` ou records; entidade JPA com fábrica estática sem `throw` no construtor; `exclude.xml` com `EI_EXPOSE_REP` e `EI_EXPOSE_REP2` (prática comum em projetos Spring) e mais nada. Toda exclusão nova leva comentário com a justificativa.
**Warning signs:** `verify` falha só no SpotBugs depois que os testes passaram.

### Pitfall 6: Exemplos do Boot 3 que não compilam no Boot 4
**What goes wrong:**
- `spring-boot-starter-web` está deprecated e foi substituído por `spring-boot-starter-webmvc`.
- O Liquibase não sobe sem `spring-boot-starter-liquibase`.
- `@MockBean` não existe mais.
- `TestRestTemplate` mudou de pacote e exige `@AutoConfigureTestRestTemplate`.
- `org.testcontainers:postgresql` virou `testcontainers-postgresql`.
- Jackson mudou de pacote: `com.fasterxml.jackson.databind` → `tools.jackson.databind`. **As anotações continuam em `com.fasterxml.jackson.annotation`**: o BOM do Jackson 3.1.5 gerencia `com.fasterxml.jackson.core:jackson-annotations` na versão `2.21`.
- `java.version` do parent é 17.

`[VERIFIED: Spring Boot 4.0 Migration Guide; jackson-bom-3.1.5.pom; parent pom]`
**How to avoid:** usar só os artefatos e pacotes listados neste documento. Na dúvida, abrir o jar no `~/.m2`.
**Warning signs:** `package ... does not exist` na compilação dos testes.

### Pitfall 7: Liquibase contexts e analytics
**What goes wrong:** sem `spring.liquibase.contexts`, **todos** os changesets rodam, inclusive o seed dev, e o prod sobe com dados de demo (PITFALLS, Armadilha 11). Além disso, o Liquibase OSS ≥ 4.30 envia analytics anônimos por padrão `[CITED: github.com/liquibase/liquibase/issues/6503; docs.liquibase.com/analytics]`.
**How to avoid:** deixar `contexts` explícito em cada profile (default `prod` no `application.yml`, `dev` no `application-dev.yml`) e ter um IT com `contexts=prod` afirmando que o seed não existe. Configurar `spring.liquibase.analytics-enabled: false`, que existe no Boot 4.1.1 e funciona com a autoconfig padrão `[VERIFIED: metadata do jar; o bug do JHipster #32993 era só da LiquibaseConfiguration própria do JHipster]`. Changesets são imutáveis: toda mudança vai num changeset novo.
**Warning signs:** o link `demo-link` aparece com o profile prod; `Validation Failed: 1 changesets check sum`.

### Pitfall 8: Hibernate `validate` × tipos do changelog
**Fato verificado:** no Hibernate 7.4.5, `ColumnDefinitions.hasMatchingType` compara o type code e depois o nome normalizado. Como fallback, chama `dialect.resolveSqlTypeDescriptor`, e o `PostgreSQLDialect` mapeia `timestamptz` para `TIMESTAMP_UTC` ("The PostgreSQL JDBC driver reports TIMESTAMP for timestamptz, but we use it only for mapping Instant"). Logo, **campo `Instant` + coluna `TIMESTAMP WITH TIME ZONE` passa no validate** `[VERIFIED: hibernate-orm tag 7.4.5, PostgreSQLDialect.java:377-381 e ColumnDefinitions.java:28-45]`. A validação de unique keys e índices tem default `NONE` (`ConstraintValidationType.interpret` devolve `NONE` sem a setting) `[VERIFIED: ConstraintValidationType.java]`.
**How to avoid:** usar `Instant` na entidade (não `LocalDateTime`), `VARCHAR(n)` para `String` e `BIGINT` para `Long`. Qualquer IT que suba o contexto prova o validate.
**Warning signs:** `Schema validation: wrong column type encountered in column [...]` no startup.

### Pitfall 9: Containers compartilhados que morrem entre classes de IT
**What goes wrong:** com `static @Container` numa `AbstractIT` + `@Testcontainers`, a extensão JUnit para o container ao fim de cada classe. O contexto Spring em cache continua apontando para a porta antiga, e a segunda classe de IT falha com `Connection refused`.
**How to avoid:** declarar os containers como `@Bean @ServiceConnection` numa `@TestConfiguration` importada pela `AbstractIT` (Pattern 6), para que o ciclo de vida acompanhe o contexto. Manter as mesmas propriedades e anotações em todos os ITs para maximizar o reuso do cache de contexto. Os ITs com propriedades diferentes, como `contexts=prod`, ganham contexto e banco próprios, o que é desejável aqui.
**Warning signs:** o primeiro IT passa e os seguintes falham com erro de conexão.

### Pitfall 10: Windows nesta máquina (CRLF, bit executável, espaço no caminho)
**Fatos do ambiente:** `git config core.autocrlf` = `true`; o caminho do repositório contém espaço (`projeto 4`); Git 2.53.0.windows.1 `[VERIFIED: probe nesta sessão]`.
**What goes wrong:** `mvnw` com CRLF falha com `$'\r': command not found` no Git Bash e `bad interpreter` no Linux. Um `mvnw` commitado sem o modo 100755 dá `Permission denied` no Actions. Caminhos sem aspas quebram em pedaços.
**How to avoid:**
- Commitar o `.gitattributes` **antes** do `mvnw`, com `* text=auto eol=lf`, `*.cmd text eol=crlf`, `*.bat text eol=crlf`, `*.jar binary` e `*.png binary`. Se algum arquivo já tiver sido adicionado antes, rodar `git add --renormalize .`.
- `git update-index --chmod=+x app/mvnw` e conferir com `git ls-files -s app/mvnw` (deve mostrar `100755`).
- No Makefile, usar só caminhos relativos (`cd app && ./mvnw ...`); nunca `$(CURDIR)` sem aspas.
- No CI, fazer o checkout em `path: link pulse` e rodar tudo de lá. Isso prova "caminho com espaço" no Linux a cada push.
- O script `mvnw` 3.3.4 já cita os caminhos (`exec "$MAVEN_HOME/bin/$MVN_CMD" "$@"`) `[VERIFIED: mvnw do zip 3.3.4]`. O `mvnw.cmd` com espaço no caminho fica `[ASSUMED]`: verificar rodando `.\mvnw.cmd -v` no PowerShell neste repositório.
**Warning signs:** `^M` em mensagens de erro; o CI falha em `./mvnw` com `Permission denied`.

### Pitfall 11: Precisão de `Instant` × `timestamptz`
**What goes wrong:** `Instant.now()` tem precisão de nanos no Windows/Linux modernos e o Postgres guarda micros. O `createdAt` da resposta 201 difere do valor relido do banco, e testes de igualdade ficam intermitentes.
**How to avoid:** `clock.instant().truncatedTo(ChronoUnit.MICROS)`; também truncar o `expiresAt` recebido.

### Pitfall 12: Permutação multiplicativa é ofuscação, não segurança
**What goes wrong:** quem conhece o esquema recupera `M` com dois códigos consecutivos (`c2 - c1 ≡ M mod 62^7`). Além disso, o último caractere Base62 de IDs consecutivos segue uma progressão aritmética mod 62: `v, q, l, g, b` nos IDs 1..5 com o `M` acima `[VERIFIED: jshell]`.
**How to avoid:** nada a corrigir dentro do D-01. Registrar no README (D-03) e nos comentários do `CodeGenerator`. O critério de sucesso (códigos sequenciais "não consecutivos nem enumeráveis" a olho nu) é atendido.

### Pitfall 13: `expiresAt` sem offset
**What goes wrong:** se o Jackson aceitasse `2026-12-31T23:59:59` sem offset, ele assumiria um fuso, violando o D-10.
**How to avoid:** tipar o DTO com `OffsetDateTime`. O deserializador do Jackson usa `ISO_OFFSET_DATE_TIME` e deve rejeitar entrada sem offset com `HttpMessageNotReadableException`, que vira 400 `[ASSUMED]`. **Cobrir com IT** (`expiresAt` sem offset → 400 Problem Details). Se o Jackson 3 aceitar, trocar por `String` + `@Pattern` + parse explícito.

### Pitfall 14: Docker parado ou Testcontainers sem Docker
**What goes wrong:** os ITs falham localmente com `Could not find a valid Docker environment`. **O Docker Desktop está instalado mas o daemon está parado nesta máquina** (`open //./pipe/dockerDesktopLinuxEngine: O sistema não pode encontrar o arquivo especificado`) `[VERIFIED: probe]`.
**How to avoid:** o README e o `make help` dizem que os ITs exigem o Docker Desktop rodando. `make test` roda só os unitários (surefire) e funciona sem Docker; `make verify` roda tudo.

## Code Examples

### `.mvn/wrapper/maven-wrapper.properties` (gerado sem Maven instalado)
```properties
# Source: o mvnw 3.3.4 lê distributionUrl e distributionSha256Sum (linhas 111-118 do script)
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce
```
Como obter o `mvnw`/`mvnw.cmd` sem Maven local: baixar `https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper-distribution/3.3.4/maven-wrapper-distribution-3.3.4-only-script.zip`, que contém `mvnw`, `mvnw.cmd`, `mvnwDebug` e `mvnwDebug.cmd` `[VERIFIED: unzip -l]`, e copiar `mvnw` + `mvnw.cmd` para `app/`. Depois, para regenerar pelo caminho oficial: `./mvnw -N wrapper:wrapper -Dmaven=3.9.16 -Dtype=only-script`. As linhas `wrapperVersion` e `distributionType` só documentam, porque o script as ignora `[ASSUMED: formato gerado pelo plugin]`.

### Plugins de qualidade (`app/pom.xml`)
```xml
<build>
  <plugins>
    <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
    <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-failsafe-plugin</artifactId></plugin> <!-- goals vêm do parent -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId><artifactId>maven-checkstyle-plugin</artifactId><version>3.6.0</version>
      <dependencies>
        <dependency><groupId>com.puppycrawl.tools</groupId><artifactId>checkstyle</artifactId><version>${checkstyle.version}</version></dependency>
      </dependencies>
      <configuration>
        <configLocation>config/checkstyle/checkstyle.xml</configLocation>
        <consoleOutput>true</consoleOutput>
        <failOnViolation>true</failOnViolation>
        <violationSeverity>warning</violationSeverity>   <!-- defesa extra: falha mesmo se alguém voltar a severity para warning -->
        <includeTestSourceDirectory>true</includeTestSourceDirectory>
      </configuration>
      <executions><execution><id>checkstyle</id><phase>validate</phase><goals><goal>check</goal></goals></execution></executions>
    </plugin>
    <plugin>
      <groupId>com.github.spotbugs</groupId><artifactId>spotbugs-maven-plugin</artifactId><version>4.10.4.1</version>
      <configuration>
        <effort>Max</effort><threshold>Medium</threshold>
        <excludeFilterFile>config/spotbugs/exclude.xml</excludeFilterFile>
      </configuration>
      <executions><execution><id>spotbugs</id><phase>verify</phase><goals><goal>check</goal></goals></execution></executions>
    </plugin>
  </plugins>
</build>
```

### `.github/workflows/ci.yml`
```yaml
# SHAs resolvidos via `git ls-remote --tags` em 2026-10-06 (tags lightweight → SHA do commit)
name: ci
on:
  push: { branches: [main] }
  pull_request:
permissions: { contents: read }
concurrency: { group: ci-${{ github.ref }}, cancel-in-progress: true }
jobs:
  build-test:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    defaults:
      run: { working-directory: "link pulse/app" }   # prova caminho com espaço no Linux
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1   # v7.0.1
        with: { path: "link pulse" }
      - uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6  # v6.0.1
        with: { distribution: temurin, java-version: "21", cache: maven }
      - name: mvnw é executável e LF
        run: test -x mvnw && ! grep -q $'\r' mvnw
      - name: Build, Checkstyle, testes unitários + Testcontainers, SpotBugs
        run: ./mvnw -B -ntp verify
      - if: failure()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: reports
          path: |
            link pulse/app/target/surefire-reports
            link pulse/app/target/failsafe-reports
            link pulse/app/target/spotbugsXml.xml
            link pulse/app/target/checkstyle-result.xml
```
O runner `ubuntu-latest` já tem Docker, então o Testcontainers funciona sem configuração. Se o `cache: maven` não achar o `pom.xml` por causa do `path`, adicionar `cache-dependency-path: "link pulse/app/pom.xml"` `[ASSUMED]`.

### Makefile (fino; roda no Git Bash/WSL/Linux)
```make
.DEFAULT_GOAL := help
MVNW := cd app && ./mvnw -B -ntp
COMPOSE := docker compose -f compose.dev.yaml

help: ## Lista os alvos
	@grep -E '^[a-z-]+:.*## ' Makefile | awk 'BEGIN{FS=":.*## "}{printf "  %-10s %s\n",$$1,$$2}'
build: ## Compila e empacota sem testes
	$(MVNW) -DskipTests package
test: ## Testes unitários (não precisa de Docker)
	$(MVNW) test
verify: ## Build completo: Checkstyle, unit, IT (Testcontainers/Docker), SpotBugs
	$(MVNW) verify
run: db-up ## Sobe a API com profile dev contra o Postgres do compose
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev
db-up: ## Sobe o Postgres 18 local e espera ficar saudável
	$(COMPOSE) up -d --wait
db-down: ## Para o Postgres local (mantém o volume)
	$(COMPOSE) down
.PHONY: help build test verify run db-up db-down
```
No Windows: `winget install ezwinports.make` (ou `scoop install make`) e rodar o `make` **dentro do Git Bash**. Sem `make`, o README lista os comandos equivalentes (`cd app && ./mvnw verify` etc.).

### `compose.dev.yaml`
```yaml
services:
  postgres:
    image: postgres:18.6
    environment: { POSTGRES_DB: linkpulse, POSTGRES_USER: linkpulse, POSTGRES_PASSWORD: linkpulse }
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql"]      # Postgres 18: montar o diretório pai (CLAUDE.md)
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U linkpulse -d linkpulse"]
      interval: 5s
      timeout: 3s
      retries: 10
volumes: { pgdata: {} }
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` (+ `-test`) | Boot 4.0 | Trocar o artefato |
| Liquibase autoconfig no core do Boot | `spring-boot-starter-liquibase` | Boot 4.0 | Sem o starter, o Liquibase não roda e o validate falha |
| `@MockBean`/`@SpyBean` | `@MockitoBean`/`@MockitoSpyBean` | Removidos no Boot 4.0 | — |
| `TestRestTemplate` em `boot.test.web.client` | `org.springframework.boot.resttestclient.TestRestTemplate` + `@AutoConfigureTestRestTemplate`; novo `RestTestClient` | Boot 4.0 | Preferir MockMvc nesta fase |
| `org.testcontainers:postgresql` / `containers.PostgreSQLContainer<SELF>` | `testcontainers-postgresql` / `org.testcontainers.postgresql.PostgreSQLContainer` | Testcontainers 2.0 | Classe nova sem generics |
| Jackson 2 (`com.fasterxml.jackson.databind`, `JavaTimeModule`) | Jackson 3 (`tools.jackson.databind`, java.time embutido; anotações ainda `com.fasterxml.jackson.annotation`) | Boot 4.0 | Não adicionar `jackson-datatype-jsr310` |
| Liquibase Apache-2.0 | Liquibase 5.x `FSL-1.1-ALv2` | Liquibase 5.0 | Registrar no README (Phase 5) e no PROJECT.md |
| Checkstyle 9.3 (default do plugin) | Checkstyle 14.3.0 | — | Obrigatório sobrescrever |

**Deprecated/outdated:**
- `org.testcontainers.containers.PostgreSQLContainer`: ainda no jar 2.0.5, marcada como deprecated.
- `spring-boot-starter-web`: substituído por `-webmvc` (o migration guide o descreve como deprecated).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `nextval()` falha dentro de query method read-only do Spring Data (o read-only chega à conexão pgjdbc) | Pitfall 2 | Baixo: se não falhar, o `@Transactional` recomendado continua correto |
| A2 | O Jackson 3 rejeita `OffsetDateTime` sem offset | Pitfall 13 | Médio: violaria o D-10 em silêncio. Mitigado por IT obrigatório |
| A3 | O `ProblemDetailsExceptionHandler` do Boot 4 recua quando existe um `ResponseEntityExceptionHandler` próprio | Pattern 3 | Baixo: dois advices; resolver com `@Order` ou desligando a propriedade |
| A4 | O `mvnw.cmd` 3.3.4 funciona com espaço no caminho no PowerShell | Pitfall 10 | Médio para avaliadores Windows sem Git Bash; verificar com `.\mvnw.cmd -v` |
| A5 | O `actions/setup-java` com `cache: maven` acha o `pom.xml` dentro de `link pulse/app` | CI | Baixo: só o cache deixa de funcionar; corrigir com `cache-dependency-path` |
| A6 | Os nomes `linkpulse.code.alphabet`, `linkpulse.code.multiplier` e `linkpulse.alias.reserved` | Pattern 7 | Baixo: nomes internos; o prefixo `linkpulse.code.*` é decisão do D-03 |
| A7 | Com `@GeneratedValue(SEQUENCE)`, o estado é capturado no `persist()` e `code` setado depois geraria INSERT + UPDATE ou INSERT nulo | Alternatives | Baixo: a recomendação evita esse caminho |
| A8 | Um cliente HTTP real nos testes pode seguir o 302 por padrão | Alternatives | Baixo: a recomendação é MockMvc |
| A9 | Linhas `wrapperVersion`/`distributionType` no properties seguem o formato do plugin | Code Examples | Nulo: o script as ignora |

## Open Questions

1. **Layout `app/` ou raiz?**
   - What we know: o ARCHITECTURE.md recomenda `app/` (contexto Docker autocontido na Phase 3); o CONTEXT.md não decidiu.
   - What's unclear: preferência do autor.
   - Recommendation: `app/`. Mudar depois custa um `git mv` e ajustes no CI/Makefile.
2. **Licença FSL do Liquibase 5.0.3.**
   - What we know: o BOM do Boot 4.1.1 gerencia a 5.0.3 com licença `FSL-1.1-ALv2` `[VERIFIED]`. O STACK.md cita a 4.33.0 (Apache) como opção, fixada por propriedade.
   - Recommendation: manter a 5.0.3 do BOM (o CLAUDE.md manda não sobrescrever o BOM; uso em portfólio é permitido pela FSL). Registrar no PROJECT.md agora e no README na Phase 5. Isso resolve o blocker do STATE.md.
3. **Actuator já na Phase 1?**
   - Recommendation: sim, mínimo (`health,info` na 8081). É barato e fixa o contrato de portas citado no CONTEXT.md. Os grupos liveness/readiness ficam para a Phase 4.
4. **Endpoint `GET /links/{code}` de metadados.**
   - Recommendation: não implementar nesta fase. Não é requisito e entraria em conflito de forma com `/links/{code}/stats` só na Phase 2.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | build | ✓ | Oracle 21.0.10 (javac e jshell presentes) | — (o CI usa Temurin 21) |
| Maven | gerar o wrapper | ✗ | — | Baixar o zip `maven-wrapper-distribution` 3.3.4 `only-script` e escrever o `.properties` à mão. Depois disso, `./mvnw` baixa o Maven 3.9.16 |
| Docker Engine | ITs com Testcontainers, `make db-up` | CLI ✓, **daemon parado** | — | Iniciar o Docker Desktop antes de `make verify`/`db-up`. O CI tem Docker nativo |
| GNU make | Makefile | ✗ | — | `winget install ezwinports.make` ou rodar os comandos equivalentes do README |
| git | repositório | ✓ | 2.53.0.windows.1, `core.autocrlf=true` | `.gitattributes` neutraliza o autocrlf |
| curl | wrapper (download) | ✓ | — | O mvnw também usa wget ou um downloader Java |
| gh CLI | — | ✗ | — | Não é necessário nesta fase |

**Missing dependencies with no fallback:** nenhuma.
**Missing dependencies with fallback:** Maven (wrapper manual), make (comandos diretos), Docker daemon (ligar o Docker Desktop; sem ele, só `make test` roda localmente).

## Validation Architecture

> `workflow.nyquist_validation` está `false` no `.planning/config.json`, mas o orquestrador pediu esta seção explicitamente.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 6.0.3 + AssertJ 3.27.7 + Spring Test (MockMvcTester) + Testcontainers 2.0.5 (BOM do Boot 4.1.1) |
| Config file | nenhum — Wave 0 cria `app/pom.xml` (surefire `*Test`, failsafe `*IT` pelo parent) |
| Quick run command | `cd app && ./mvnw -B -ntp test` (unitários, sem Docker, < 30 s depois do primeiro download) |
| Single test | `cd app && ./mvnw -B -ntp -Dtest=CodeGeneratorTest test` |
| Single IT | `cd app && ./mvnw -B -ntp -Dtest=NoUnit -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RedirectIT verify` |
| Full suite command | `cd app && ./mvnw -B -ntp verify` (Checkstyle → unit → IT → SpotBugs) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| LINK-01 | URL válida → 201, `Location` = `shortUrl`, corpo `{code, shortUrl, targetUrl, expiresAt, createdAt}`; `ftp://`, `javascript:`, sem host, > 2048 → 400 | IT + unit (`HttpUrlValidatorTest`) | `-Dit.test=LinkApiIT verify` | ❌ Wave 0 |
| LINK-02 | ID vem da sequência antes do INSERT; N criações concorrentes (ex.: 8 threads × 50 via `LinkService`) → códigos todos distintos | IT | `-Dit.test=ConcurrencyIT verify` | ❌ Wave 0 |
| LINK-03 | Códigos consecutivos não consecutivos; ida e volta; sempre 7 caracteres; unicidade em faixa grande e perto de 62^7−1; `M` não coprimo → exceção; `id ≥ 62^7` → exceção | unit | `-Dtest=CodeGeneratorTest test` | ❌ Wave 0 |
| LINK-04 | Regex (`minha-promo`, `black_friday` ok; `abc`, `abcd`, `a b-c` falham); reservados case-insensitive (`Swagger-UI`); nenhum código gerado casa com a regex; alias repetido → 409; duas criações concorrentes do mesmo alias → um 201 + um 409 | unit (`AliasPolicyTest`, `CodeGeneratorTest`) + IT | `-Dtest=AliasPolicyTest test` / `-Dit.test=LinkApiIT verify` | ❌ Wave 0 |
| LINK-05 | `expiresAt` passado → 400; sem offset → 400; futuro com offset → 201 e persistido como `timestamptz` | IT | `-Dit.test=LinkApiIT verify` | ❌ Wave 0 |
| LINK-06 | Todo erro tem `Content-Type: application/problem+json`, `status`, `title`, `type`; validação tem `errors[]` | IT | `-Dit.test=LinkApiIT verify` | ❌ Wave 0 |
| REDIR-01 | `GET /{code}` → 302, `Location` = URL original, `Cache-Control` contém `no-store` | IT | `-Dit.test=RedirectIT verify` | ❌ Wave 0 |
| REDIR-02 | Inexistente → 404 Problem; expirado (linha inserida com `expires_at` no passado) → 410 Problem com `code`; `/favicon.ico` → 404 | IT | `-Dit.test=RedirectIT verify` | ❌ Wave 0 |
| DATA-01 | O contexto sobe com `ddl-auto=validate` (todo IT); com `contexts=prod` o seed `demo-link` não existe; com `dev` existe | IT | `-Dit.test=LiquibaseContextsIT verify` | ❌ Wave 0 |
| QUAL-01 | (= LINK-03) | unit | `-Dtest=CodeGeneratorTest test` | ❌ Wave 0 |
| QUAL-03 | Uma violação proposital quebra o build | manual uma vez (inserir um tab ou `import *` → `./mvnw validate` falha → reverter) + o `verify` no CI | `./mvnw -B validate` | — |
| QUAL-04 | `/v3/api-docs` retorna 200 e contém `/links`; `/swagger-ui.html` → 302/200 | IT | `-Dit.test=OpenApiIT verify` | ❌ Wave 0 |
| CONT-03 | `git ls-files -s app/mvnw` → 100755; `git ls-files --eol` sem `crlf` em `w/` para `.sh`/`mvnw`; build a partir de caminho com espaço | CI (checkout em `link pulse/`) + manual (Git Bash e PowerShell neste repo) | job `build-test` | ❌ Wave 0 |
| CI-01 | Push/PR roda `verify` e falha em violação | CI | workflow `ci.yml` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `cd app && ./mvnw -B -ntp test` (mais o IT da feature tocada, com o Docker ligado)
- **Per wave merge:** `cd app && ./mvnw -B -ntp verify`
- **Phase gate:** `verify` verde local (Git Bash, caminho com espaço) **e** no GitHub Actions antes do `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `.gitattributes` (primeiro commit), `.gitignore`
- [ ] `app/pom.xml`, `app/mvnw`, `app/mvnw.cmd`, `app/.mvn/wrapper/maven-wrapper.properties`
- [ ] `app/config/checkstyle/checkstyle.xml`, `app/config/spotbugs/exclude.xml`
- [ ] `app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java`, `AbstractIT.java`, `TestLinkPulseApplication.java`
- [ ] `.github/workflows/ci.yml` verde com um teste trivial (walking skeleton: o CI fica verde antes das features)

## Security Domain

> `security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: high`.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | não | Fora de escopo (sem auth, decisão do projeto) |
| V3 Session Management | não | API stateless, sem sessão |
| V4 Access Control | não | Sem usuários; não há PATCH/DELETE (fora de escopo de propósito) |
| V5 Input Validation | **sim** | Bean Validation (Hibernate Validator 9.1.3) + `@HttpUrl` com allow-list `http`/`https`, host obrigatório e `@Size(max=2048)`; `AliasPolicy` com regex de allow-list; `@Future`; path var restrita a `[A-Za-z0-9_-]+`; SQL só via JPA/parâmetros (o `nextval` é literal fixo) |
| V6 Cryptography | não | A permutação **não é** criptografia (D-03); não usar o termo "seguro" no README |
| V7 Error Handling and Logging | **sim** | Problem Details sem stack trace, sem SQL e sem nome de constraint no `detail`; `server.error.include-stacktrace` fica no default (`never`); não logar o corpo inteiro das requisições |
| V14 Configuration | **sim** | Actuator na porta 8081 com só `health,info`; versões fixadas (BOM, wrapper com SHA-256, actions por SHA); `permissions: contents: read` no workflow |

### Known Threat Patterns for Spring MVC + Postgres (encurtador)

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Esquemas perigosos como destino (`javascript:`, `data:`, `file:`) | Tampering / Elevation (XSS via domínio) | Allow-list `http`/`https` + `new URI()` estrito |
| Open redirect | Spoofing | Inerente ao produto. Mitigar loops rejeitando o host do próprio `linkpulse.base-url` e documentar no README |
| Enumeração de links | Information Disclosure | Permutação multiplicativa (ofuscação; Pitfall 12 documenta o limite) |
| Alias sequestrando rotas (`swagger-ui`, `actuator`) | Spoofing | Lista de reservados case-insensitive + regex que exige `-`/`_` |
| Corrida na criação de alias | Tampering | `UNIQUE` no banco + 409 |
| Vazamento de detalhes internos em erro | Information Disclosure | Problem Details com mensagens próprias; `DataIntegrityViolationException` nunca vai crua para o cliente |
| Supply chain no CI | Tampering | Actions fixadas por SHA; `distributionSha256Sum` no wrapper; Dependabot (opcional) |
| Payload gigante | DoS | `@Size(max=2048)` na URL e `@Size(max=32)` no alias; o Tomcat limita o POST por padrão |

## Sources

### Primary (HIGH confidence)
- Maven Central: `spring-boot-dependencies-4.1.1.pom` (versões gerenciadas), `spring-boot-starter-parent-4.1.1.pom` (java.version 17, failsafe), poms dos starters `webmvc`, `webmvc-test`, `data-jpa`, `liquibase` e `validation` 4.1.1, `spring-boot-liquibase-4.1.1.pom`/jar (metadata de propriedades), jars `spring-boot-webmvc`, `-webmvc-test`, `-testcontainers`, `-resttestclient`, `-data-jpa-test` e `-jdbc` 4.1.1 (pacotes de classes), `testcontainers-bom-2.0.5.pom`, `testcontainers-postgresql-2.0.5.jar`, `liquibase-core-5.0.3.pom` (licença FSL-1.1-ALv2, snakeyaml), `jackson-bom-3.1.5.pom`, `jackson-databind-3.1.5.jar`, `springdoc-openapi-3.1.1.pom`, metadata de checkstyle, maven-checkstyle-plugin 3.6.0, spotbugs-maven-plugin 4.10.4.1, maven-wrapper 3.3.4, apache-maven 3.9.16 (SHA-512 conferido)
- Código-fonte do Hibernate ORM, tag 7.4.5: `ColumnDefinitions.java`, `AbstractSchemaValidator.java`, `Dialect.java`, `PostgreSQLDialect.java`, `ConstraintValidationType.java`
- Checkstyle `google_checks.xml`, tag `checkstyle-14.3.0`
- Spring Framework 7.0.9 reference, Error Responses: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Spring Data JPA reference, Entity State Detection: https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html
- Spring Boot 4.0 Migration Guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
- RFC 9457 §3.1.1: https://www.rfc-editor.org/rfc/rfc9457.html
- springdoc: https://springdoc.org/ (Boot 4 / v3, paths default)
- SpotBugs bug descriptions + CHANGELOG 4.10.4
- `git ls-remote` de actions/checkout, actions/setup-java e actions/upload-artifact (SHAs)
- Probes locais: java, git, docker, make, mvn e jshell (matemática da permutação)

### Secondary (MEDIUM confidence)
- Liquibase analytics: https://github.com/liquibase/liquibase/issues/6503, https://docs.liquibase.com/analytics/home.html, https://github.com/jhipster/generator-jhipster/issues/32993

### Tertiary (LOW confidence)
- Nenhuma afirmação crítica depende só de fonte terciária. Os itens não verificados estão no Assumptions Log.

## Metadata

**Confidence breakdown:**
- Standard stack: ALTA. Todas as versões e artefatos foram conferidos no Maven Central nesta sessão.
- Architecture: ALTA. Os padrões são canônicos do Spring, e o validate de `timestamptz` foi conferido no código do Hibernate.
- Pitfalls: ALTA/MÉDIA. Overflow, Checkstyle, Boot 4 e ambiente foram verificados. Read-only `nextval`, Jackson sem offset e `mvnw.cmd` com espaço ficam `[ASSUMED]`, cada um com teste ou verificação indicada.

**Research date:** 2026-10-06
**Valid until:** 2026-11-05 (30 dias; stack estável, mas Boot 4.1.x e Testcontainers 2.0.x têm patches frequentes)
