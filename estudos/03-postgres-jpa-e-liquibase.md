# 03 · Postgres, JPA e Liquibase

**Em uma frase:** o Postgres é a fonte da verdade dos links. O Liquibase cria as tabelas a partir de arquivos YAML versionados, e o Hibernate (através do Spring Data JPA) só confere essas tabelas e lê e grava a entidade `Link`.

## O que você vai aprender

- O que é um banco relacional e por que os links moram no Postgres.
- O que são JPA, Hibernate e Spring Data JPA, e como uma classe Java vira uma tabela.
- O que é uma *sequence* e por que o número do link é pedido antes de gravar.
- Como o Liquibase versiona o banco e como o profile decide se o link de demonstração é criado.

## Antes de começar: conceitos

### Banco relacional e "fonte da verdade"

Um **banco relacional** guarda dados em **tabelas**, como uma planilha bem rígida: cada coluna tem um tipo fixo (número, texto, data), e regras (*constraints*) impedem dados inválidos, como dois links com o mesmo código. A linguagem para conversar com ele é o **SQL** (`SELECT`, `INSERT`...).

O Link Pulse usa três bancos, cada um com um papel (guia 00). O **Postgres** é a **fonte da verdade**: se o cache (Valkey) e os cliques (MongoDB) sumissem, os links continuariam existindo aqui. O cache sempre pode ser reconstruído a partir do Postgres; o contrário não.

### ORM: JPA, Hibernate e Spring Data JPA

Java trabalha com **objetos**; o banco trabalha com **linhas de tabela**. Um **ORM** (*Object-Relational Mapping*) faz a tradução entre os dois.

- **JPA** é a especificação (o "contrato"): anotações como `@Entity`, `@Table`, `@Id` e `@Column` dizem como uma classe vira uma tabela.
- **Hibernate** é a implementação que roda por baixo e gera o SQL.
- **Spring Data JPA** gera **repositórios** prontos: você declara uma interface que estende `JpaRepository<Entidade, TipoDoId>` e ganha `save`, `findById`, `count` e outros, sem escrever implementação. Métodos com nomes como `findByCode` viram consultas sozinhos (*query derivation*: o Spring lê o nome do método e monta o SQL). `@Query` permite escrever um SQL próprio quando preciso.

### Transação

Uma **transação** é um pacote de operações no banco que vale "tudo ou nada". Se uma parte falha, o banco desfaz as outras (*rollback*). Quando tudo dá certo, o banco confirma (*commit*). No Spring, `@Transactional` num método abre uma transação no começo e faz o commit no fim.

### Migrações de schema

O **schema** é a estrutura do banco: tabelas, colunas, índices, sequences. Ele muda com o tempo (uma coluna nova, um índice). Em vez de alguém rodar SQL à mão em cada ambiente, as mudanças ficam em arquivos versionados no Git e são aplicadas em ordem. Pense num "git do banco de dados".

No **Liquibase**:

- **changelog:** arquivo que lista mudanças;
- **changeset:** uma mudança identificada por `id` + `author`, aplicada **uma única vez**. O Liquibase anota cada changeset aplicado na tabela `databasechangelog`, com um *checksum* (uma "impressão digital" do conteúdo), e usa a tabela `databasechangeloglock` para impedir que duas instâncias da aplicação migrem ao mesmo tempo;
- **context:** um rótulo que liga ou desliga changesets por ambiente (`dev`, `prod`).

### Sequence

Uma `SEQUENCE` do Postgres é um **contador** fora de qualquer tabela. Cada chamada a `nextval('nome')` devolve um número novo (1, 2, 3...), mesmo com centenas de conexões pedindo ao mesmo tempo, e nunca repete um número.

## No Link Pulse

### Postgres 18.6 em todos os lugares

- `compose.dev.yaml`: imagem `postgres:18.6` para rodar localmente (guia 09).
- `app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java`: o mesmo `postgres:18.6` nos testes de integração (guia 06).
- No futuro, RDS PostgreSQL na AWS (Phase 5, guia 10).

A mesma versão em todos os ambientes evita o clássico "funciona no meu banco".

### A entidade `Link`

Uma **entidade** é uma classe Java que representa uma linha de tabela.

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

Leitura guiada:

- **`@Entity` + `@Table(name = "links")`**: "esta classe corresponde à tabela `links`".
- **`@Id`**: o campo `id` é a chave primária, o identificador único de cada linha.
- **`@Column(...)`**: detalhes da coluna. `nullable = false` quer dizer obrigatória; `length = 2048` é o tamanho máximo do texto; `name = "target_url"` liga o campo Java `targetUrl` à coluna `target_url` do banco.
- **`@Transient`**: "este campo não vai para o banco". O `newEntity` só existe na memória.

**Por que `Persistable<Long>`?** O `save` do Spring Data precisa decidir entre **inserir** (objeto novo) e **atualizar** (objeto que já existe). A regra padrão é: "se o ID está preenchido, o objeto já existe". Só que aqui o ID é preenchido **antes** de gravar (vem da sequence). Sem ajuda, o Spring acharia que o link já existe e faria um `SELECT` inútil antes do `INSERT`. Implementando `Persistable`, a entidade responde ela mesma à pergunta `isNew()`: o campo `newEntity` começa `true` e vira `false` depois de gravado ou lido (método `markNotNew`, chamado nos eventos `@PostPersist` e `@PostLoad`).

Outros detalhes:

- **`Link.create(id, code, targetUrl, expiresAt, createdAt)`** é a fábrica de links novos. O construtor sem argumentos é `protected` e existe só para o Hibernate.
- **`isExpiredAt(Instant now)`** devolve `true` quando o link tem expiração e `now` não é anterior a ela. O instante exato da expiração já conta como expirado.

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

- Repare que é uma **interface**: não há implementação escrita. O Spring Data cria a implementação na subida.
- `findByCode` e `existsByCode` são consultas derivadas do nome. O Spring lê "find by code" e gera `SELECT ... WHERE code = ?`.
- `Optional<Link>` é uma "caixa que pode estar vazia": se o código não existe, a caixa vem vazia em vez de `null`. Isso obriga quem chama a tratar o caso "não achou".
- `nextId()` é uma consulta nativa (SQL do próprio Postgres). O `@Transactional` explícito é a decisão 01-02 do `.planning/STATE.md`: sem ele, a chamada herdaria o modo "somente leitura", e o `nextval` (que altera o contador) falharia.

Quem usa o repositório:

- `LinkService.create`: `existsByCode`, `nextId` e `saveAndFlush` (guia 01).
- `LinkService.find`: `findByCode`, numa transação somente leitura. Só é chamado quando o cache não tem a resposta (guia 12).
- `StatsService`: `existsByCode`, para responder `404` antes de consultar o MongoDB (guia 14).

### Por que pedir o número antes de gravar

O código curto é calculado a partir do número (guia 04). Se o número viesse de uma coluna automática (`IDENTITY`), ele só existiria **depois** do INSERT, e seria preciso gravar a linha, ler o número, calcular o código e atualizar a linha: duas escritas. Pedindo `nextval` antes, o javadoc de `LinkService.create` resume: "o código existe antes do commit e um único INSERT grava `id` e `code`".

### `saveAndFlush` e a constraint `uk_links_code`

Normalmente o Hibernate junta as escritas e só as envia ao banco no fim da transação. `saveAndFlush` força o envio **na hora**, ainda dentro do `try` do `LinkService.create`. Isso importa por causa da corrida de alias (guia 01): se dois clientes pedem o mesmo alias juntos, o segundo INSERT viola a regra de unicidade `uk_links_code`, e o erro aparece dentro do `try`, onde o service consegue transformá-lo em `409`. O service reconhece a violação pelo nome da constraint e, como plano B, pelo código de erro `23505` do Postgres (decisão 01-04).

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

O arquivo-mestre só inclui os arquivos numerados, na ordem. `relativeToChangelogFile: true` diz "procure o arquivo a partir da pasta deste master".

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

**DDL** (*Data Definition Language*) é a parte do SQL que cria estruturas (tabelas, sequences). O primeiro changeset cria a sequence; o seguinte cria a tabela `links` com estas colunas:

| Coluna | Tipo | Regra |
|--------|------|-------|
| `id` | `BIGINT` | chave primária (`pk_links`) |
| `code` | `VARCHAR(32)` | obrigatório e único (`uk_links_code`) |
| `target_url` | `VARCHAR(2048)` | obrigatório |
| `expires_at` | `TIMESTAMP WITH TIME ZONE` | opcional |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | obrigatório |

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

- Um **seed** é um dado inicial de exemplo. Este cria o link `demo-link`, que aponta para `https://example.com/`.
- `contextFilter: dev` faz o seed rodar só quando `spring.liquibase.contexts` inclui `dev`. (`contextFilter` é o nome atual do antigo atributo `context`.)
- `valueComputed: nextval('link_id_seq')` usa a mesma sequence da aplicação, então o seed não "rouba" um número que a API usaria depois. Por isso, num banco de dev novo, o primeiro `POST` recebe o número 2 (README).

### Qual profile cria o link de demonstração

| Profile ativo | `spring.liquibase.contexts` | Seed `demo-link`? |
|---------------|-----------------------------|-------------------|
| nenhum | `prod` (padrão do `application.yml`) | Não |
| `prod` | `prod` (`application-prod.yml`) | Não |
| `dev` | `dev` (`application-dev.yml`) | Sim |

O `app/src/test/java/dev/linkpulse/LiquibaseContextsIT.java` prova as três linhas. A classe externa usa o padrão, e classes internas (`@Nested`) com `@ActiveProfiles("prod")` e `@ActiveProfiles("dev")` conferem a tabela `links` e a `databasechangelog`.

### `ddl-auto: validate`

O Hibernate sabe criar tabelas sozinho, mas aqui ele é proibido: com `ddl-auto: validate`, na subida ele só **compara** as entidades Java com as tabelas que o Liquibase criou e falha se algo não bater (uma coluna faltando, um tipo diferente). O `RedirectIT` confere isso em `hibernateOnlyValidatesSchema` e prova que o schema vem do changelog em `schemaComesFromLiquibaseChangelog`.

### Licença do Liquibase

O Boot 4.1.1 traz o Liquibase 5.0.3, com licença `FSL-1.1-ALv2` (*Functional Source License*, que vira Apache 2.0 depois de dois anos). A seção "Licenças de terceiros" do README explica que ela permite este uso de portfólio, e que a alternativa Apache desde já é fixar `<liquibase.version>4.33.0</liquibase.version>` no `app/pom.xml`.

## Por que assim?

- **Schema só pelo Liquibase:** o comentário do `application.yml` diz "O schema vem só do Liquibase; o Hibernate apenas confere." Toda mudança de banco vira código revisável, com histórico no Git.
- **001 sem context, 002 só em dev:** a estrutura roda em todo lugar; dados de demonstração nunca chegam à produção (decisão 01-05).
- **Changesets imutáveis:** alterar um changeset já aplicado muda o checksum, e o Liquibase se recusa a subir. Correções vão num arquivo novo (`003-...`).
- **Sem `@GeneratedValue`:** o número precisa existir antes do INSERT para virar código curto.
- **Postgres para links, MongoDB para cliques:** links são poucos, precisam de unicidade garantida e são a verdade do sistema; cliques são muitos, só crescem e são somados (guia 13).

## Experimente

Com as dependências de dev no ar (`make db-up`) e a API já iniciada uma vez com o profile dev (para o Liquibase rodar), abra o `psql`, o terminal do Postgres, dentro do container:

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
2. Crie dois links pela API e veja os números crescendo na tabela, enquanto os códigos não seguem ordem visível.
3. Descreva o que aconteceria se você editasse o `001-create-links.yaml` e subisse a API de novo contra o mesmo banco.

## Perguntas para fixar

1. Por que `Link` implementa `Persistable<Long>`? O que aconteceria sem isso?
2. Por que `nextId()` tem `@Transactional` mesmo sendo só uma leitura da sequence?
3. Que configuração garante que o `demo-link` nunca aparece em produção? Qual teste prova isso?
4. Para que servem as tabelas `databasechangelog` e `databasechangeloglock`?
5. O que significa `ddl-auto: validate`, e por que não deixar o Hibernate criar as tabelas?

<details>
<summary>Respostas</summary>

1. Porque o ID é preenchido antes de gravar. Sem `Persistable`, o Spring Data acharia que o link já existe e faria um `merge` (SELECT + INSERT) em vez de um INSERT direto.
2. Porque os métodos do repositório herdam o modo somente leitura, e o `nextval` altera o contador da sequence, o que falha nesse modo.
3. O `contextFilter: dev` do changeset de seed, junto com `contexts: prod` como padrão. O `LiquibaseContextsIT` prova.
4. `databasechangelog` registra quais changesets já rodaram (com checksum); `databasechangeloglock` impede duas instâncias de migrar ao mesmo tempo.
5. O Hibernate só confere se as tabelas batem com as classes. Deixá-lo criar tabelas tiraria o schema do controle de versão e poderia alterar o banco de produção sem revisão.

</details>

---

[← Anterior: 02 · Spring Boot e Java 21](02-spring-boot-e-java-21.md) · [Índice](README.md) · [Próximo: 04 · Código curto Base62 →](04-codigo-curto-base62.md)
