# 16 · Métricas com Micrometer

**Em uma frase:** a aplicação conta o que acontece por dentro (acertos do cache, cliques publicados, decisões do rate limit, tempo de cada requisição) com o Micrometer e expõe tudo em `http://localhost:8081/actuator/prometheus`, no formato que o Prometheus coleta, sem nenhuma métrica carregando código, URL ou IP.

## O que você vai aprender

- O que é observabilidade e para que servem métricas.
- Os tipos de métrica: counter, gauge, timer e histograma.
- O que são tags e por que "cardinalidade alta" é um problema.
- Como ler o `/actuator/prometheus` e o catálogo de métricas do projeto.
- Como a Phase 3 vai usar essas métricas.

## Antes de começar: conceitos

### Observabilidade

Quando a aplicação está rodando num servidor, você não consegue "olhar dentro" dela. **Observabilidade** é a capacidade de entender o que está acontecendo a partir do que ela expõe. Há três pilares:

- **logs:** mensagens de texto sobre eventos ("falha no consumer");
- **métricas:** números ao longo do tempo ("requisições por segundo");
- **traces:** o caminho de uma requisição entre serviços.

Este guia trata de **métricas**. **Analogia:** o painel de um carro. Você não abre o motor para saber a velocidade; olha o velocímetro.

### Tipos de métrica

| Tipo | Analogia | Exemplo no projeto |
|------|----------|--------------------|
| **Counter** (contador) | o hodômetro: só sobe | `linkpulse.cache.requests`: quantas consultas ao cache |
| **Gauge** (medidor) | o marcador de combustível: sobe e desce | `linkpulse.clicks.publish.queue`: cliques esperando na fila agora |
| **Timer** | um cronômetro que soma todas as medições | `linkpulse.clicks.consumer.write`: quanto tempo leva gravar um lote |
| **Histograma** | separar medições em faixas ("até 10 ms", "até 50 ms"...) | `http.server.requests`: tempo das requisições HTTP |

Um counter isolado diz pouco ("1.234.567 hits desde que subiu"). O valor está na **taxa**: quantos por segundo, agora. Quem calcula a taxa é o Prometheus, a partir de duas leituras seguidas.

### Tags (labels)

Uma **tag** divide uma métrica em fatias. `linkpulse.cache.requests` com a tag `result` vira várias séries: `result=hit`, `result=miss`, `result=error`... Assim, um único nome responde "quantos hits?" e "quantos erros?".

### Cardinalidade: o perigo das tags

**Cardinalidade** é o número de valores diferentes que uma tag pode ter. Cada combinação de tags vira uma **série** separada no Prometheus, guardada em memória.

- `result` tem 5 valores possíveis: 5 séries. Ótimo.
- Se alguém usasse o **código do link** como tag, cada link novo criaria uma série nova: milhões de séries, e o Prometheus ficaria sem memória.
- Se usasse o **IP**, além disso, vazaria dado pessoal para o monitoramento.

Regra do projeto: **tags só com valores de um conjunto pequeno e fixo**. Para dados por link existe o `/stats` (guia 14).

### Prometheus e percentis

O **Prometheus** é um banco de séries temporais que, de tempos em tempos (a cada 15 s, por exemplo), visita um endereço HTTP e lê todas as métricas. Isso se chama **scrape** ("raspar"). O **Grafana** desenha gráficos a partir dele. Os dois chegam na Phase 3 (guia 10).

**Percentil:** "p95 = 40 ms" quer dizer que 95% das requisições levaram até 40 ms. A média esconde os casos lentos; o p95 e o p99 mostram o que os usuários mais azarados sentem. Para calcular percentis, o Prometheus precisa do **histograma**.

## No Link Pulse

### Micrometer e o endpoint

O **Micrometer** é a biblioteca de métricas do Spring Boot: uma "fachada" em que o código registra counters e gauges sem saber para onde eles vão. O `micrometer-registry-prometheus` (guia 02) traduz tudo para o formato do Prometheus.

`app/src/main/resources/application.yml`

```yaml
management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        # prometheus: /actuator/prometheus na porta de gerenciamento (8081), raspado pelo Prometheus.
        include: health,info,prometheus
  metrics:
    distribution:
      percentiles-histogram:
        # Histograma de http.server.requests: p95/p99 via histogram_quantile no Prometheus.
        http.server.requests: true
```

- O endpoint fica na porta **8081**, separada da API (guia 02).
- `percentiles-histogram` liga o histograma do tempo das requisições HTTP. Sem ele, o Prometheus não conseguiria calcular p95 e p99.

### Como uma métrica é registrada

`app/src/main/java/dev/linkpulse/cache/LinkCache.java`

```java
    private static Counter requests(MeterRegistry registry, String result) {
        return Counter.builder(REQUESTS_METRIC)
                .description("Consultas ao cache do redirect por resultado")
                .tag("result", result)
                .register(registry);
    }
```

- O **`MeterRegistry`** é o "cartório" das métricas; ele é um bean e chega pelo construtor (guia 02).
- `Counter.builder(nome).tag(...).register(registry)` cria (uma vez só, no construtor) um contador com aquela tag.
- Depois, no código, basta `hits.increment()`.

Um gauge é registrado de outro jeito: em vez de alguém incrementar, o Micrometer **pergunta** o valor na hora do scrape:

`app/src/main/java/dev/linkpulse/click/ClickPublisher.java`

```java
        Gauge.builder(QUEUE_METRIC, this, publisher -> publisher.executor.getQueue().size())
                .description("Cliques na fila do publisher esperando o XADD")
                .register(registry);
```

### Nomes: do Micrometer ao Prometheus

O Micrometer usa pontos (`linkpulse.cache.requests`); o Prometheus usa sublinhados e acrescenta sufixos conforme o tipo:

| No código | No `/actuator/prometheus` |
|-----------|---------------------------|
| counter `linkpulse.cache.requests` | `linkpulse_cache_requests_total` |
| gauge `linkpulse.clicks.publish.queue` | `linkpulse_clicks_publish_queue` |
| timer `http.server.requests` com histograma | `http_server_requests_seconds_bucket`, `_count` e `_sum` |

### O catálogo de métricas do projeto

Lista final registrada no resumo do plano 02-08, mais a do rate limit (02-09):

| Métrica (Prometheus) | Tipo | Tags | Guia |
|----------------------|------|------|------|
| `linkpulse_cache_requests_total` | counter | `result` = hit, miss, negative_hit, error, bypass | 12 |
| `linkpulse_cache_errors_total` | counter | `operation` = get, put, evict | 12 |
| `linkpulse_valkey_unavailable_total` | counter | — | 12 |
| `linkpulse_clicks_publish_total` | counter | `result` = published, rejected, failed | 13 |
| `linkpulse_clicks_publish_queue` | gauge | — | 13 |
| `linkpulse_clicks_persisted_total`, `_duplicates_total`, `_invalid_total`, `_claimed_total`, `_lost_total` | counter | — | 13 |
| `linkpulse_clicks_consumer_errors_total` | counter | — | 13 |
| `linkpulse_clicks_consumer_batch_count` / `_sum` / `_max` | summary | — | 13 |
| `linkpulse_clicks_consumer_write_seconds_count` / `_sum` / `_max` | timer | `outcome` = success, failure | 13 |
| `linkpulse_clicks_stream_pending` | gauge | — | 13 |
| `linkpulse_clicks_stream_lag` | gauge (`NaN` = indefinido) | — | 13 |
| `linkpulse_clicks_stream_length` | gauge | — | 13 |
| `linkpulse_ratelimit_requests_total` | counter | `result` = allowed, rejected, error | 15 |
| `http_server_requests_seconds_bucket` / `_count` / `_sum` | histograma | `uri` (o **modelo** da rota), `method`, `status`, `outcome`, `exception`, `error` | — |

Repare na tag `uri` do `http.server.requests`: ela guarda o **modelo** da rota (`/{code:[A-Za-z0-9_-]+}`), e não o caminho pedido (`/demo-link`). Se fosse o caminho, cada código viraria uma série nova: cardinalidade alta.

As gauges do stream (`pending`, `lag`, `length`) são atualizadas pelo próprio laço do consumer, no máximo uma vez a cada 2 s. Na hora do scrape, o Micrometer só lê um número já guardado em memória, sem ir ao Valkey: o scrape fica barato e não sofre se o Valkey estiver lento.

### O teste que protege a regra: `MetricsCardinalityIT`

`app/src/test/java/dev/linkpulse/MetricsCardinalityIT.java` sobe a aplicação com um servidor de verdade, gera tráfego e lê o `/actuator/prometheus` pela porta de gerenciamento. O javadoc resume a regra que ele confere:

> nenhuma linha `linkpulse_*` ou `http_server_requests*` contém código curto, URL de destino, host ou query do Referer, user-agent ou o IP do cliente, e as chaves de tag das métricas `linkpulse_*` ficam em `result`, `operation` e `outcome`.

Se alguém, no futuro, acrescentar uma tag com o código do link, este teste quebra.

### Para que tudo isso vai servir (Phase 3)

Com o Prometheus e o Grafana da Phase 3 (guia 10), estas métricas viram painéis e alertas, por exemplo:

| Pergunta | Métrica de base |
|----------|-----------------|
| O cache está ajudando? (hit ratio) | `linkpulse_cache_requests_total` por `result` |
| O redirect está rápido? (p95/p99) | `http_server_requests_seconds_bucket` |
| Os cliques estão chegando? | `linkpulse_clicks_persisted_total` |
| O consumer está atrasado? | `linkpulse_clicks_stream_lag` e `_pending` |
| Estamos perdendo cliques? | `rejected` + `failed` + `linkpulse_clicks_lost_total` |
| O Valkey está caindo? | `linkpulse_valkey_unavailable_total` |

## Por que assim?

- **Contadores próprios, com nomes `linkpulse.*`:** o que interessa ao negócio (cache, cliques, rate limit) não existe nas métricas automáticas do Spring.
- **Só tags de baixa cardinalidade:** o Prometheus continua leve, e nenhum dado pessoal vai para o monitoramento.
- **Porta separada:** as métricas não ficam expostas ao público (guia 02).
- **Gauges baratas:** o scrape nunca depende do Valkey estar rápido.

## Experimente

Com a API rodando:

1. Abra <http://localhost:8081/actuator/prometheus> no navegador e procure (Ctrl+F) `linkpulse_cache_requests_total`.
2. No Git Bash, rode `curl -s http://localhost:8081/actuator/prometheus | grep '^linkpulse_'`, chame o `/demo-link` cinco vezes e rode de novo. Quais números mudaram?
3. Procure `http_server_requests_seconds_count` e encontre a linha com a `uri` do redirect. Ela mostra `demo-link`?
4. Pare o Valkey (`docker compose -f compose.dev.yaml stop valkey`), chame o `/demo-link` e veja `linkpulse_valkey_unavailable_total` subir. Suba o Valkey de novo.

## Perguntas para fixar

1. Qual a diferença entre um counter e um gauge? Dê um exemplo de cada no projeto.
2. O que é cardinalidade, e por que o código do link não pode ser tag?
3. Por que o projeto liga o `percentiles-histogram` do `http.server.requests`?
4. O que o `MetricsCardinalityIT` impede que aconteça no futuro?
5. Por que a tag `uri` guarda o modelo da rota e não o caminho pedido?

<details>
<summary>Respostas</summary>

1. O counter só sobe (`linkpulse_cache_requests_total`); o gauge sobe e desce (`linkpulse_clicks_publish_queue`).
2. É a quantidade de valores diferentes de uma tag; cada valor vira uma série em memória no Prometheus. Com o código como tag, cada link novo criaria uma série, sem limite.
3. Para o Prometheus conseguir calcular p95 e p99 do tempo das requisições.
4. Que alguém acrescente uma tag com dado de alta cardinalidade ou pessoal (código, URL, IP, user-agent) sem perceber.
5. Para não criar uma série por código pedido (cardinalidade alta) e não expor os códigos.

</details>

---

[← Anterior: 15 · Rate limit](15-rate-limit-por-ip.md) · [Índice](README.md) · [Próximo: 10 · Próximas fases →](10-proximas-fases.md)
