# 03 · Postgres, JPA e Liquibase

**Em uma frase:** o Postgres é a fonte da verdade dos links; o Liquibase cria o schema a partir de changelogs YAML versionados, e o Hibernate (via Spring Data JPA) só confere esse schema e lê e grava a entidade `Link`.

## Conceitos

### JPA, Hibernate e Spring Data JPA

- **JPA** é a especificação Java de mapeamento objeto-relacional: anotações como `@Entity`, `@Table`, `@Id` e `@Column` dizem como uma classe vira uma tabela.
- **Hibernate** é a implementação da JPA que roda por baixo.
- **Spring Data JPA** gera repositórios: você declara uma interface que estende `JpaRepository<Entidade, TipoDoId>` e ganha `save`, `findById`, `count` etc. Métodos com nomes como `findByCode` viram consultas sozinhos (*query derivation*), e `@Query` permite SQL próprio.

### Migrações de schema

Em vez de deixar o ORM criar tabelas ("ddl-auto"), o schema é descrito em arquivos versionados e aplicados em ordem. No Liquibase:

- **changelog:** arquivo que lista mudanças;
- **changeset:** uma mudança identificada por `id` + `author`, aplicada uma única vez. O Liquibase grava cada changeset aplicado na tabela `databasechangelog`, com um checksum, e usa `databasechangeloglock` para impedir que duas instâncias migrem ao mesmo tempo;
- **context:** rótulo que liga ou desliga changesets por ambiente.

### Sequência

Uma `SEQUENCE` do Postgres é um contador atômico fora de tabela: `nextval('nome')` devolve um número novo a cada chamada, mesmo com muitas conexões concorrentes, e nunca o repete.

## No Link Pulse

### Postgres 18.6 em vários lugares

- `compose.dev.yaml`: imagem `postgres:18.6` para rodar localmente (guia 09).
- `app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java`: o mesmo `postgres:18.6` nos testes de integração (guia 06).
- No futuro, RDS PostgreSQL na AWS (Phase 5, guia 10).

A mesma versão em todos os ambientes evita surpresas de "funciona no meu banco".

### A entidade `Link`

`app/src/main/java/dev/linkpulse/link/Link.java`

```java
/**
 * Link curto gravado na tabela {@code links}.
 *
 * <p>O ID vem da sequência {@code link_id_seq} antes do INSERT (não há {@code @GeneratedValue}).
 * Por isso a entidade implementa {@link Persistable}: sem isso o Spring Data trataria o ID
 * atribuído como entidade existente e faria {@code merge} (SELECT + INSERT).
 */
@Entity
@Table(name = "links")
public class Link implements Persistable<Long> {

    @Id
    private Long id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;
    // ...
    @Transient
    private boolean newEntity = true;
```

Pontos importantes:

- **`Persistable<Long>`:** o `save` do Spring Data decide entre `persist` (novo) e `merge` (existente). Por padrão, "ID preenchido" significa "existente". Como aqui o ID é preenchido antes, `isNew()` devolve o campo `newEntity`, que começa `true` e vira `false` em `@PostLoad`/`@PostPersist` (método `markNotNew`).
- **`Link.create(id, code, targetUrl, expiresAt, createdAt)`** é a fábrica de entidades novas; o construtor sem argumentos é `protected`, só para o JPA.
- **`isExpiredAt(Instant now)`** devolve `true` quando há expiração e `now` não é anterior a ela: o instante exato já conta como expirado.

### `LinkRepository`

`app/src/main/java/dev/linkpulse/link/LinkRepository.java`

```java
public interface LinkRepository extends JpaRepository<Link, Long> {

    /**
     * Próximo valor da sequência {@code link_id_seq}.
     *
     * <p>Transação de escrita explícita: o {@code nextval()} falha em transação read-only, e os
     * query methods herdam {@code readOnly = true} do {@code SimpleJpaRepository}. Quando chamado
     * dentro de uma transação do service, junta-se a ela.
     *
     * @return o próximo ID
     */
    @Transactional
    @Query(value = "select nextval('link_id_seq')", nativeQuery = true)
    long nextId();
    // ...
    Optional<Link> findByCode(String code);
    // ...
    boolean existsByCode(String code);
}
```

- `findByCode` e `existsByCode` são consultas derivadas do nome; nenhuma linha de SQL foi escrita para elas.
- `nextId()` é uma query nativa. O `@Transactional` de escrita é a decisão 01-02 do `.planning/STATE.md`: sem ele, a chamada herdaria `readOnly = true` e o `nextval` falharia.

### Por que sequência dedicada e `nextval` antes do INSERT

O código curto é derivado do ID (guia 04). Se o ID viesse de uma coluna `IDENTITY`, ele só existiria depois do INSERT, e seria preciso gravar e depois atualizar a linha com o código. Com `nextval` antes, o javadoc de `LinkService.create` resume: "o código existe antes do commit e um único INSERT grava `id` e `code`".

### `saveAndFlush` e a constraint `uk_links_code`

`saveAndFlush` envia o INSERT imediatamente, ainda dentro do `try` do `LinkService.create`. Se dois clientes pedem o mesmo alias ao mesmo tempo, o segundo INSERT viola `uk_links_code`. O service reconhece a violação pelo nome da constraint (via `ConstraintViolationException` do Hibernate) e, como fallback, pelo SQLSTATE `23505` com o nome na mensagem (decisão 01-04). Outras violações de integridade seguem como 500.

### Liquibase: os changelogs

`app/src/main/resources/db/changelog/db.changelog-master.yaml`

```yaml
databaseChangeLog:
  - include:
      file: changes/001-create-links.yaml
      relativeToChangelogFile: true
  - include:
      file: changes/002-seed-dev.yaml
      relativeToChangelogFile: true
```

O master só inclui os arquivos numerados, na ordem. `relativeToChangelogFile: true` resolve o caminho a partir da pasta do master.

`app/src/main/resources/db/changelog/changes/001-create-links.yaml`

```yaml
# DDL dos links. Nunca leva context: precisa rodar em todos os ambientes.
# Changesets aplicados são imutáveis (checksum); qualquer mudança vai num changeset novo.
databaseChangeLog:
  - changeSet:
      id: 001-create-link-id-seq
      author: link-pulse
      changes:
        - createSequence:
            sequenceName: link_id_seq
            startValue: 1
            incrementBy: 1
  # ...
              - column:
                  name: code
                  type: VARCHAR(32)
                  constraints:
                    nullable: false
                    unique: true
                    uniqueConstraintName: uk_links_code
```

A tabela `links` tem `id BIGINT` (PK `pk_links`), `code VARCHAR(32)` único, `target_url VARCHAR(2048)`, `expires_at` e `created_at` como `TIMESTAMP WITH TIME ZONE`.

`app/src/main/resources/db/changelog/changes/002-seed-dev.yaml`

```yaml
# Dados de demonstração. Só roda com o context dev (profile dev); prod usa contexts=prod.
databaseChangeLog:
  - changeSet:
      id: 002-seed-dev-demo-link
      author: link-pulse
      contextFilter: dev
      changes:
        - insert:
            tableName: links
            columns:
              - column:
                  name: id
                  valueComputed: nextval('link_id_seq')
              - column:
                  name: code
                  value: demo-link
              # ...
```

- `contextFilter: dev` faz o seed rodar só quando `spring.liquibase.contexts` inclui `dev`. (`contextFilter` é o nome atual do antigo atributo `context`.)
- `valueComputed: nextval('link_id_seq')` usa a mesma sequência da aplicação, então o seed não "rouba" um ID que a API usaria depois. Por isso, num banco de dev novo, o primeiro `POST` recebe o ID 2 (README).

### Contexts por profile

| Profile | `spring.liquibase.contexts` | Seed `demo-link`? |
|---------|-----------------------------|-------------------|
| nenhum | `prod` (default do `application.yml`) | Não |
| `prod` | `prod` (`application-prod.yml`) | Não |
| `dev` | `dev` (`application-dev.yml`) | Sim |

O `app/src/test/java/dev/linkpulse/LiquibaseContextsIT.java` prova as três linhas: a classe externa usa o default, e classes `@Nested` com `@ActiveProfiles("prod")` e `@ActiveProfiles("dev")` conferem a tabela `links` e a `databasechangelog`.

Ainda no `application.yml`: `analytics-enabled: false`, porque "o Liquibase OSS envia analytics anônimos por padrão".

### `ddl-auto: validate`

O Hibernate não cria nem altera tabelas: na subida, ele só compara as entidades com o schema que o Liquibase criou e falha se algo não bater. O `RedirectIT` confere isso em `hibernateOnlyValidatesSchema` e prova que o schema vem do changelog em `schemaComesFromLiquibaseChangelog`.

### Licença do Liquibase

O Boot 4.1.1 gerencia o Liquibase 5.0.3, com licença `FSL-1.1-ALv2` (Functional Source License, que vira Apache 2.0 depois de dois anos). A seção "Licenças de terceiros" do README explica que ela permite este uso de portfólio e que a alternativa Apache desde já é fixar `<liquibase.version>4.33.0</liquibase.version>` no `app/pom.xml`.

## Por que assim?

- **Schema só pelo Liquibase:** o comentário do `application.yml` diz "O schema vem só do Liquibase; o Hibernate apenas confere." Mudança de banco vira código revisável, com histórico.
- **001 sem context, 002 com `contextFilter: dev`:** DDL roda em todo lugar; dados de demonstração nunca chegam a produção (decisão 01-05).
- **Changesets imutáveis:** alterar um changeset aplicado muda o checksum e o Liquibase recusa subir. Correções vão num arquivo novo (`003-...`).
- **Sem `@GeneratedValue`:** o ID precisa existir antes do INSERT para virar código curto.

## Experimente

Com o Postgres de dev no ar (`make db-up`) e a API já iniciada uma vez com o profile dev (para o Liquibase rodar), abra o `psql` dentro do container:

```bash
docker compose -f compose.dev.yaml exec postgres psql -U linkpulse -d linkpulse
```

Dentro do `psql`:

```sql
\d links
select id, code, target_url from links;
select id, author, filename from databasechangelog;
select * from databasechangeloglock;
\q
```

Exercícios:

1. Encontre `uk_links_code` na saída de `\d links`.
2. Crie dois links pela API e veja os IDs crescendo na tabela, enquanto os códigos não seguem ordem visível.
3. Descreva o que aconteceria se você editasse o `001-create-links.yaml` e subisse a API de novo contra o mesmo banco.

## Perguntas para fixar

1. Por que `Link` implementa `Persistable<Long>`? O que aconteceria sem isso?
2. Por que `nextId()` tem `@Transactional` mesmo sendo só uma leitura de sequência?
3. Que configuração garante que o `demo-link` nunca aparece em produção? Qual teste prova isso?
4. Para que servem as tabelas `databasechangelog` e `databasechangeloglock`?
5. O que significa `ddl-auto: validate`, e por que não `update`?

---

[← Anterior: 02 · Spring Boot e Java 21](02-spring-boot-e-java-21.md) · [Índice](README.md) · [Próximo: 04 · Código curto Base62 →](04-codigo-curto-base62.md)
