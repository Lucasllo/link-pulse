# 12 · Cache no Valkey e fail-open

**Em uma frase:** o redirect pergunta primeiro ao Valkey (um banco em memória) qual é o destino do código. Só quando ele não sabe, a pergunta vai ao Postgres, e a resposta é guardada para a próxima vez. Se o Valkey cair, o redirect continua funcionando pelo Postgres, sem ficar lento.

## O que você vai aprender

- O que é um cache e por que ele deixa o redirect rápido.
- O que é o Valkey e o que significa "TTL".
- Como funciona o padrão *cache-aside*, inclusive para respostas negativas (404 e 410).
- Por que o cache é apagado **depois** do commit.
- O que é *fail-open* e como o projeto evita que ele vire *fail-slow*.

## Antes de começar: conceitos

### Cache

Um **cache** é uma memória rápida que guarda respostas já calculadas para não ter de calculá-las de novo.

**Analogia:** você pergunta a um bibliotecário onde fica um livro. Ele vai ao arquivo (lento), descobre e anota num post-it na mesa. Na próxima vez que alguém pedir o mesmo livro, ele olha o post-it (rápido). O arquivo é o Postgres; o post-it é o cache.

Termos:

- **hit (acerto):** a resposta estava no cache.
- **miss (falta):** não estava; foi preciso buscar na fonte.
- **hit ratio:** a fração de acertos. Quanto maior, mais rápido o sistema e menos carga no banco.

### Valkey e TTL

O **Valkey** é um banco que guarda tudo **na memória RAM**, por isso responde em menos de um milissegundo. Ele nasceu como uma cópia de código aberto do **Redis** e fala exatamente a mesma língua: qualquer cliente de Redis funciona com ele. No projeto, o cliente é o **Lettuce**, usado pelo Spring Data Redis através do `StringRedisTemplate`.

O Valkey guarda pares **chave → valor**, como um dicionário gigante. Cada chave pode ter um **TTL** (*time to live*, "tempo de vida"): quando o tempo acaba, o Valkey apaga a chave sozinho. É assim que o cache "esquece" respostas antigas.

### Cache-aside

**Cache-aside** ("cache ao lado") é o padrão mais comum de uso de cache. A **aplicação** controla tudo:

1. Pergunta ao cache.
2. **Hit:** usa a resposta.
3. **Miss:** busca na fonte (o Postgres) e grava a resposta no cache, com TTL.

O cache nunca fala com o banco sozinho; quem faz a ponte é o código.

### Negative cache

E se alguém pedir um código que **não existe**? Sem cuidado, cada pedido iria ao Postgres, e um robô pedindo milhares de códigos inventados sobrecarregaria o banco. O **negative cache** guarda também as respostas negativas: "este código não existe" (404) e "este link expirou" (410).

### Fail-open × fail-closed × fail-slow

Quando uma peça de apoio quebra, o sistema pode:

- **fail-closed (falhar fechado):** parar de funcionar. Se o cache cai, o redirect devolve erro.
- **fail-open (falhar aberto):** seguir funcionando sem a peça. Se o cache cai, o redirect vai direto ao Postgres.

O cache é **descartável** (o Postgres tem a verdade), então o projeto escolhe **fail-open**. Mas há uma armadilha, o **fail-slow**: se o Valkey estiver travado (ligado, mas sem responder), cada redirect esperaria o tempo-limite inteiro antes de desistir. O sistema "funciona", mas lentíssimo. O projeto tem três defesas contra isso, descritas abaixo.

## No Link Pulse

### As chaves e o formato do valor

Cada código tem uma chave `lp:link:{code}` (por exemplo, `lp:link:demo-link`). O valor é um texto que começa com uma letra dizendo o tipo:

| Valor | Significa | TTL |
|-------|-----------|-----|
| `L\|<expiresAt ou ->\|<url>` | Link válido (o `-` indica "não expira") | o menor entre 24 h e o tempo que falta até expirar |
| `N` | Código inexistente (404) | 60 s |
| `G\|<expiredAt>` | Link expirado (410) | 24 h |

Exemplo real do `demo-link`, registrado no resumo do plano 02-01: `L|-|https://example.com/`.

**Por que o TTL de um link válido nunca passa da expiração?** Se um link expira em 10 minutos, o cache não pode guardá-lo por 24 h, ou ele continuaria redirecionando depois de expirar. Mesmo assim, quem lê confere a expiração de novo contra o relógio da aplicação, porque o relógio do Valkey e o da aplicação podem divergir um pouco.

**Por que o negative cache dura só 60 s?** Porque um código que não existe pode passar a existir (alguém cria o alias). O TTL curto limita quanto tempo uma resposta "não existe" errada poderia durar.

No Java, esses três tipos viram um tipo "selado" (`sealed`), que só permite essas três formas:

`app/src/main/java/dev/linkpulse/cache/CacheEntry.java`

```java
public sealed interface CacheEntry permits CacheEntry.Found, CacheEntry.Missing, CacheEntry.Gone {
```

`Found`, `Missing` e `Gone` são records (guia 02). Com `sealed`, o compilador sabe que não existe uma quarta opção, e o `switch` que trata os três casos fica completo e seguro.

### O fluxo do redirect

`app/src/main/java/dev/linkpulse/link/RedirectService.java`

```java
    public String resolve(String code) {
        Optional<CacheEntry> cached = linkCache.get(code);
        if (cached.isPresent()) {
            return fromCache(code, cached.get());
        }
        return fromDatabase(code);
    }
```

**Hit:** a resposta vem do cache.

```java
    private String fromCache(String code, CacheEntry entry) {
        return switch (entry) {
            case CacheEntry.Found found -> {
                if (found.expiresAt() != null && !clock.instant().isBefore(found.expiresAt())) {
                    linkCache.putGone(code, found.expiresAt());
                    throw LinkProblems.expired(code, found.expiresAt());
                }
                yield found.targetUrl();
            }
            case CacheEntry.Missing missing -> throw LinkProblems.notFound(code);
            case CacheEntry.Gone gone -> throw LinkProblems.expired(code, gone.expiredAt());
        };
    }
```

O `switch` com `case CacheEntry.Found found ->` é *pattern matching* do Java 21: ele testa o tipo e já cria a variável `found` daquele tipo. Repare que um `Found` cujo prazo já passou vira `Gone` na hora (410), mesmo que o TTL do Valkey ainda não tenha vencido.

**Miss:** busca no Postgres e grava no cache, **inclusive o resultado negativo**.

```java
    private String fromDatabase(String code) {
        Optional<Link> found = linkService.find(code);
        if (found.isEmpty()) {
            linkCache.putMissing(code);
            throw LinkProblems.notFound(code);
        }
        Link link = found.get();
        if (link.isExpiredAt(clock.instant())) {
            linkCache.putGone(code, link.getExpiresAt());
            throw LinkProblems.expired(code, link.getExpiresAt());
        }
        linkCache.putFound(code, link.getTargetUrl(), link.getExpiresAt());
        return link.getTargetUrl();
    }
```

Um detalhe de desempenho: o `RedirectService` **não** tem `@Transactional`. Uma transação pega uma conexão do pool do Postgres, e um hit no cache não precisa do banco. Só o miss chama `linkService.find(code)`, que abre uma transação somente leitura.

### `LinkCache`: o cache em si

`app/src/main/java/dev/linkpulse/cache/LinkCache.java`

```java
    public Optional<CacheEntry> get(String code) {
        if (!availability.allowRequest()) {
            bypasses.increment();
            return Optional.empty();
        }
        String raw;
        try {
            raw = redis.opsForValue().get(key(code));
        } catch (RuntimeException e) {
            availability.recordFailure();
            getErrors.increment();
            errors.increment();
            return Optional.empty();
        }
        Optional<CacheEntry> entry = decode(raw);
        if (entry.isEmpty()) {
            misses.increment();
        } else if (entry.get() instanceof CacheEntry.Found) {
            hits.increment();
        } else {
            negativeHits.increment();
        }
        return entry;
    }
```

Leitura guiada:

1. **`availability.allowRequest()`**: pergunta ao "disjuntor" (veja abaixo) se pode chamar o Valkey. Se não pode, conta um `bypass` e devolve vazio, o que faz o `RedirectService` ir ao Postgres.
2. **`redis.opsForValue().get(...)`**: o comando `GET` do Valkey.
3. **`catch (RuntimeException e)`**: qualquer falha do Valkey é **engolida**: conta um erro, avisa o disjuntor e devolve vazio. Nenhuma exceção do Valkey sai desta classe. É isso que torna o cache descartável.
4. **`decode(raw)`**: transforma o texto (`L|...`, `N`, `G|...`) em `CacheEntry`. Um valor fora do formato vira miss, **nunca** um destino diferente.
5. **Contadores**: cada consulta conta em `linkpulse.cache.requests` com `result` = `hit`, `negative_hit`, `miss`, `error` ou `bypass`. Esses números alimentam o painel de *hit ratio* (guia 16).

As escritas (`putFound`, `putMissing`, `putGone`) seguem a mesma regra: falhou, conta e segue. O `putFound` calcula o TTL com o método `foundTtl`, que escolhe o menor entre `linkpulse.cache.default-ttl` (24 h) e o tempo até expirar.

### Criar um link apaga o cache, depois do commit

Cenário: alguém pede `/minha-promo` antes de o alias existir. O cache guarda `N` (não existe) por 60 s. Logo depois, alguém cria o alias `minha-promo`. Sem cuidado, o redirect responderia 404 por até 60 s, mesmo com o link criado.

Por isso o `LinkService.create` apaga a chave `lp:link:{code}` depois de gravar (guia 01):

`app/src/main/java/dev/linkpulse/link/LinkService.java`

```java
    private record EvictAfterCommit(LinkCache linkCache, String code)
            implements TransactionSynchronization {

        @Override
        public void afterCommit() {
            linkCache.evict(code);
        }
    }
```

**Por que depois do commit, e não antes?** Enquanto a transação não termina, o link ainda **não é visível** para as outras conexões do Postgres. Se o `DEL` acontecesse antes do commit, um `GET` simultâneo poderia ler o banco (ainda sem o link) e gravar `N` de novo, depois do `DEL`. Com o `afterCommit`, quando o `DEL` acontece, o link já está visível para todos. O javadoc do `LinkService` admite uma janela residual muito pequena, limitada aos 60 s do `negative-ttl`.

O `CacheIT` prova o caso em `negativeCacheIsInvalidatedWhenAliasIsCreated`.

### Fail-open sem fail-slow: as três defesas

**1. Tempos-limite curtos** (`application.yml`, guia 02):

```yaml
  data:
    redis:
      # Timeout de comando curto: com o Valkey lento ou sem resposta, o cache falha em até 150 ms
      # e o redirect segue pelo Postgres (fail-open sem fail-slow, Pitfall P1).
      timeout: 150ms
      # Timeout de conexão curto: uma (re)conexão não segura o request pelo default de 10 s.
      connect-timeout: 250ms
```

Um comando que não responde em 150 ms desiste.

**2. Rejeitar na hora quando a conexão caiu** (`RedisConfig`):

`app/src/main/java/dev/linkpulse/config/RedisConfig.java`

```java
    @Bean
    LettuceClientOptionsBuilderCustomizer failFastClientOptions() {
        return builder -> builder.disconnectedBehavior(
                ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
```

Por padrão, com a conexão caída, o Lettuce **enfileira** os comandos esperando reconectar. Com `REJECT_COMMANDS`, ele recusa na hora, e o cache cai no `catch` imediatamente. Repare que a classe só **customiza** a configuração automática do Boot, sem criar a conexão do zero (o javadoc explica que isso quebraria a ligação com os containers dos testes).

**3. O "disjuntor" `ValkeyAvailability`**

As duas primeiras defesas não cobrem o pior caso: o Valkey **travado** (conectado, mas sem responder). Cada redirect esperaria os 150 ms inteiros. A terceira defesa funciona como o **disjuntor** da sua casa: deu problema, ele desarma por um tempo.

`app/src/main/java/dev/linkpulse/cache/ValkeyAvailability.java`

```java
    public boolean allowRequest() {
        return clock.millis() >= openUntil.get();
    }

    public void recordFailure() {
        long now = clock.millis();
        // max: uma thread com leitura de relógio mais antiga nunca encurta a janela.
        long previous = openUntil.getAndAccumulate(now + cooldownMillis, Math::max);
        if (now >= previous) {
            unavailable.increment();
        }
    }
```

- Depois de uma falha, `recordFailure` marca "ninguém chama o Valkey até agora + 2 s" (`linkpulse.cache.bypass-cooldown`).
- Durante essa janela, `allowRequest()` devolve `false`, e todo mundo vai direto ao Postgres, sem pagar timeout.
- Passada a janela, a próxima chamada tenta o Valkey de novo. Se ele voltou, tudo segue normal; se não, a janela reabre.
- O estado é um `AtomicLong`, um número que várias threads podem atualizar ao mesmo tempo com segurança, sem `synchronized` (que prenderia as virtual threads no Java 21, guia 02).

Resultado medido no `ValkeyOutageIT` (resumo do plano 02-03), com o Valkey pausado: 5 redirects em **[161, 6, 5, 4, 4] ms**. O primeiro paga o timeout de 150 ms; os seguintes passam direto pelo Postgres.

O mesmo disjuntor é compartilhado pela publicação de cliques (guia 13) e pelo rate limit (guia 15).

### Métricas do cache

| Métrica (Prometheus) | O que conta |
|----------------------|-------------|
| `linkpulse_cache_requests_total{result=...}` | Consultas: `hit`, `negative_hit`, `miss`, `error`, `bypass` |
| `linkpulse_cache_errors_total{operation=...}` | Falhas do Valkey por operação: `get`, `put`, `evict` |
| `linkpulse_valkey_unavailable_total` | Quantas vezes o disjuntor desarmou |

Nenhuma métrica leva o código, a URL ou o IP (guia 16).

## Por que assim?

- **Cache-aside explícito em vez de `@Cacheable`:** a seção "O Que NÃO Usar" do `.claude/CLAUDE.md` descarta o `@Cacheable` do Spring para o redirect, porque com ele o TTL por link (derivado da expiração) e as métricas de hit/miss ficariam escondidos.
- **Valkey em vez de Redis:** o ElastiCache da AWS só oferece Redis até a versão 7.1; as versões novas são Valkey. Usar Valkey em todos os ambientes dá paridade com a nuvem (decisão no `.planning/PROJECT.md`).
- **Fail-open:** o Postgres é a fonte da verdade; perder o cache deixa o sistema um pouco mais lento, nunca fora do ar.
- **Negative cache:** protege o Postgres de enxurradas de códigos inventados.

## Experimente

Com a API rodando (`make run`):

1. Chame `curl -i http://localhost:8080/demo-link` e depois, no `valkey-cli` (guia 09), `GET lp:link:demo-link` e `PTTL lp:link:demo-link`. O que é o número do `PTTL`?
2. Peça um código inexistente (`curl -i http://localhost:8080/nao-existe-1`) e confira `GET lp:link:nao-existe-1` no `valkey-cli`.
3. Faça a demonstração do README: `docker compose -f compose.dev.yaml stop valkey`, chame `/demo-link` de novo e veja o 302. Depois rode `curl -s http://localhost:8081/actuator/prometheus | grep linkpulse_cache` e encontre `result="error"` e `result="bypass"`. Para voltar: `docker compose -f compose.dev.yaml start valkey`.

## Perguntas para fixar

1. Qual a diferença entre hit, miss e negative hit?
2. Por que o TTL de um link válido é o menor entre 24 h e o tempo até a expiração?
3. Por que a chave do cache é apagada depois do commit, e não antes?
4. O que é fail-slow, e quais são as três defesas do projeto contra ele?
5. Por que o `RedirectService` não tem `@Transactional`?

<details>
<summary>Respostas</summary>

1. Hit: o cache tinha o link válido. Miss: o cache não tinha nada (foi ao Postgres). Negative hit: o cache tinha a resposta "não existe" (`N`) ou "expirou" (`G`).
2. Para o cache nunca guardar um link além do prazo dele; senão o link continuaria redirecionando depois de expirar.
3. Antes do commit, o link ainda não é visível para outras conexões. Um `GET` simultâneo poderia ler "não existe" e regravar `N` depois do `DEL`.
4. É quando o sistema continua funcionando, mas lentíssimo, porque cada chamada espera o tempo-limite de uma peça travada. Defesas: timeouts curtos (150/250 ms), `REJECT_COMMANDS` com a conexão caída e o disjuntor `ValkeyAvailability` (2 s sem chamar o Valkey depois de uma falha).
5. Porque uma transação pega uma conexão do Postgres, e um hit no cache não precisa do banco. Só o miss abre transação, no `LinkService.find`.

</details>

---

[← Anterior: 11 · Dependabot](11-dependabot.md) · [Índice](README.md) · [Próximo: 13 · Pipeline de cliques →](13-pipeline-de-cliques.md)
