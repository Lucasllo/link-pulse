# 14 · Estatísticas no MongoDB

**Em uma frase:** `GET /links/{code}/stats` confere no Postgres se o código existe e faz uma única consulta ao MongoDB (uma *aggregation pipeline* com `$match` e `$facet`), que devolve o total de cliques, a série por dia e os "tops" de origem, navegador e sistema operacional, deixando os robôs de fora por padrão.

## O que você vai aprender

- O que é uma *aggregation pipeline* e como os estágios `$match`, `$group`, `$sort`, `$limit` e `$facet` se encaixam.
- Como o período padrão é calculado e por que as datas são em UTC.
- Por que a série diária é completada em Java.
- Como os robôs são filtrados e os erros 400, 404 e 503 do endpoint.

## Antes de começar: conceitos

### Agregação

**Agregar** é resumir muitos registros em poucos números: "quantos cliques no total?", "quantos por dia?", "quais os 10 sites que mais mandaram visitas?". Fazer isso dentro do banco é muito mais rápido do que trazer todos os cliques para o Java e contar lá.

### Aggregation pipeline

No MongoDB, uma agregação é uma **pipeline** (linha de montagem): uma lista de **estágios**, em que a saída de um é a entrada do próximo.

**Analogia:** uma linha de triagem de cartas. A primeira pessoa separa só as cartas de uma cidade; a segunda agrupa por bairro e conta; a terceira ordena da pilha maior para a menor; a quarta fica só com as 10 primeiras.

| Estágio | Analogia | O que faz |
|---------|----------|-----------|
| `$match` | separar as cartas da cidade | Filtra os documentos (como o `WHERE` do SQL) |
| `$group` | agrupar por bairro e contar | Junta documentos por um campo e calcula (ex.: `$sum: 1` conta) |
| `$sort` | ordenar as pilhas | Ordena o resultado |
| `$limit` | ficar com as 10 primeiras | Corta a lista |
| `$count` | contar tudo | Conta os documentos que chegaram |
| `$facet` | várias pessoas trabalhando em paralelo | Roda várias sub-pipelines sobre os **mesmos** documentos e devolve todos os resultados juntos |

### UTC

**UTC** é o horário de referência mundial, sem fuso e sem horário de verão. "Cliques do dia 10" depende do fuso: o dia 10 em São Paulo começa às 03:00 UTC. Para a resposta ser a mesma em qualquer servidor, todas as datas do endpoint são em UTC.

## No Link Pulse

### O endpoint

```bash
curl -s 'http://localhost:8080/links/minha-promo/stats?from=2026-10-01&to=2026-10-03&includeBots=true'
```

Resposta (exemplo do README):

```json
{
  "code": "minha-promo",
  "from": "2026-10-01",
  "to": "2026-10-03",
  "includeBots": true,
  "total": 5,
  "daily": [
    { "date": "2026-10-01", "clicks": 3 },
    { "date": "2026-10-02", "clicks": 0 },
    { "date": "2026-10-03", "clicks": 2 }
  ],
  "topReferrers": [
    { "value": "news.example.com", "clicks": 3 },
    { "value": "(direct)", "clicks": 2 }
  ],
  "topBrowsers": [
    { "value": "Firefox", "clicks": 3 },
    { "value": "Other", "clicks": 2 }
  ],
  "topOs": [
    { "value": "Linux", "clicks": 3 },
    { "value": "Other", "clicks": 2 }
  ]
}
```

Os três parâmetros são opcionais:

| Parâmetro | Formato | Padrão |
|-----------|---------|--------|
| `from` | `YYYY-MM-DD` (primeiro dia, inclusivo, UTC) | `to` menos 29 dias |
| `to` | `YYYY-MM-DD` (último dia, inclusivo, UTC) | hoje em UTC |
| `includeBots` | `true` ou `false` | `false` |

O controller declara isso com anotações: `@RequestParam(required = false)` torna o parâmetro opcional; `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` converte o texto `2026-10-01` numa data (`LocalDate`); `@RequestParam(defaultValue = "false")` dá o padrão do `includeBots`. Um valor que não converte (`from=2026-13-01`) vira `400 invalid-parameter` (guia 05).

### O período: `StatsPeriod`

`app/src/main/java/dev/linkpulse/stats/StatsPeriod.java`

```java
    public static StatsPeriod resolve(LocalDate from, LocalDate to, LocalDate todayUtc) {
        LocalDate end = to != null ? to : todayUtc;
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        StatsPeriod period = new StatsPeriod(start, end);
        if (start.isAfter(end) || period.days() > MAX_DAYS) {
            throw StatsProblems.invalidPeriod(start, end);
        }
        return period;
    }
```

- Sem `to`, o fim é **hoje**. Sem `from`, o começo é o fim menos 29 dias (30 dias contando as duas pontas).
- `from` depois de `to`, ou mais de 366 dias, vira `400 invalid-period`.
- 30 e 366 são **constantes do contrato**, não configuração: o javadoc explica que o mesmo endpoint não pode responder diferente em cada ambiente.

Para o banco, o período vira um intervalo de instantes: `ts >= from às 00:00Z` e `ts < (to + 1 dia) às 00:00Z` (métodos `startInclusive` e `endExclusive`). Usar "menor que o dia seguinte" pega o último dia inteiro, até o último milissegundo.

### O serviço: `StatsService`

`app/src/main/java/dev/linkpulse/stats/StatsService.java`

```java
    public StatsResponse stats(String code, LocalDate from, LocalDate to, boolean includeBots) {
        if (!links.existsByCode(code)) {
            throw LinkProblems.notFound(code);
        }
        StatsPeriod period = StatsPeriod.resolve(from, to,
                LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));

        Document facets = aggregate(match(code, period, includeBots));

        return new StatsResponse(code, period.from(), period.to(), includeBots,
                total(facets), daily(facets, period),
                top(facets, "referrers"), top(facets, "browsers"), top(facets, "os"));
    }
```

1. **Existe?** Pergunta ao **Postgres** (a fonte da verdade, guia 03). Código inexistente → `404`. Um link **expirado** continua tendo estatísticas: o `410` vale só para o redirect.
2. **Período:** calcula com o "hoje" do `Clock` da aplicação (guia 02), para os testes poderem fixar a data.
3. **Uma consulta ao MongoDB:** o `$match` + `$facet`.
4. **Monta a resposta.**

### O filtro: `$match`

```java
    private static Document match(String code, StatsPeriod period, boolean includeBots) {
        Document match = new Document("code", code)
                .append("ts", new Document("$gte", Date.from(period.startInclusive()))
                        .append("$lt", Date.from(period.endExclusive())));
        if (!includeBots) {
            match.append("isBot", new Document("$ne", true));
        }
        return match;
    }
```

Em JSON, esse filtro fica assim (com `includeBots=false`):

```json
{ "code": "minha-promo",
  "ts": { "$gte": "<from 00:00Z>", "$lt": "<to+1 00:00Z>" },
  "isBot": { "$ne": true } }
```

- `$gte` = maior ou igual; `$lt` = menor que; `$ne` = diferente de.
- `isBot: { $ne: true }` em vez de `isBot: false`: assim, documentos antigos **sem** o campo `isBot` contam como humanos.
- Igualdade em `code` + intervalo em `ts` é exatamente o formato que o índice `code_ts` atende (guia 13). O banco vai direto aos cliques do código, sem ler a coleção inteira.
- **Segurança:** o filtro só recebe valores já tipados (o código validado pela regex da rota, datas e um booleano). Nenhum texto do cliente vira operador do MongoDB, o que evita **injeção** (um atacante mandar `{"$ne": null}` no lugar de um código).

### As quatro contas de uma vez: `$facet`

```java
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.stage(new Document("$match", match)),
                Aggregation.stage(new Document("$facet", new Document()
                        .append("total", List.of(new Document("$count", "n")))
                        .append("daily", List.of(new Document("$group",
                                new Document("_id", new Document("$dateToString",
                                        new Document("format", "%Y-%m-%d")
                                                .append("date", "$ts")
                                                .append("timezone", "UTC")))
                                        .append("n", new Document("$sum", 1)))))
                        .append("referrers", topPipeline("$referrer"))
                        .append("browsers", topPipeline("$browser"))
                        .append("os", topPipeline("$os")))));
```

Depois do `$match`, o `$facet` roda **cinco sub-pipelines** sobre os mesmos cliques filtrados:

| Faceta | Pipeline | Resultado |
|--------|----------|-----------|
| `total` | `$count` | quantos cliques ao todo |
| `daily` | `$group` pelo dia (`$dateToString` com `%Y-%m-%d` em UTC) e `$sum: 1` | cliques por dia (só os dias que tiveram clique) |
| `referrers` | `topPipeline("$referrer")` | top 10 de origem |
| `browsers` | `topPipeline("$browser")` | top 10 de navegador |
| `os` | `topPipeline("$os")` | top 10 de sistema operacional |

E o "top" é uma pipeline de três estágios:

```java
    private static List<Document> topPipeline(String field) {
        return List.of(
                new Document("$group", new Document("_id", field)
                        .append("n", new Document("$sum", 1))),
                new Document("$sort", new Document("n", -1).append("_id", 1)),
                new Document("$limit", TOP_SIZE));
    }
```

Agrupa pelo valor e conta, ordena por contagem decrescente (`-1`) e, no empate, pelo valor em ordem alfabética (`1`), e fica com os 10 primeiros. O desempate deixa a ordem **estável**: a mesma consulta sempre devolve a mesma ordem.

Tudo isso numa **única ida ao banco**, em vez de cinco.

### A série diária contínua

O `$group` do `daily` só devolve os dias que **tiveram** clique. Um gráfico com buracos engana, então o Java completa os dias vazios com zero:

```java
        return period.from().datesUntil(period.to().plusDays(1))
                .map(date -> new StatsResponse.DailyCount(date,
                        byDay.getOrDefault(date.toString(), 0L)))
                .toList();
```

`datesUntil` gera todas as datas do período, uma por uma; `getOrDefault(..., 0L)` pega a contagem do dia ou zero. Por isso o exemplo do README mostra `{ "date": "2026-10-02", "clicks": 0 }`.

### Robôs ficam fora por padrão

Robôs (crawlers de busca, previews de link do WhatsApp e do Slack, `curl`, `wget`, `k6`...) inflariam os números. O `UserAgentClassifier` marca esses cliques com `isBot: true` na gravação (guia 13), e o `$match` os exclui, a menos que `includeBots=true`.

**Atenção ao testar pelo terminal:** o próprio `curl` conta como robô. Se você clicar com `curl` e consultar sem `includeBots=true`, o total vem zero. O README avisa exatamente isso.

### Erros do endpoint

| Situação | Resposta |
|----------|----------|
| Código não existe no Postgres | `404 /problems/link-not-found` |
| `from` depois de `to`, ou mais de 366 dias | `400 /problems/invalid-period` (com `from`, `to` e `maxDays`) |
| Parâmetro em formato errado | `400 /problems/invalid-parameter` (com `parameter`) |
| MongoDB fora | `503 /problems/stats-unavailable` |

O `503` vem deste trecho:

```java
        try {
            return mongo.aggregate(aggregation, ClickDocument.COLLECTION, Document.class)
                    .getUniqueMappedResult();
        } catch (DataAccessException e) {
            throw StatsProblems.unavailable();
        }
```

`503 Service Unavailable` diz ao cliente "não é culpa sua, tente de novo em instantes", o que é mais honesto que um `500`. Os timeouts curtos do `MongoConfig` (guia 13) garantem que essa resposta chega em segundos, e não depois de 30 s.

### Assíncrono: o total pode demorar um instante

O clique chega ao MongoDB alguns instantes depois do redirect (guia 13). Logo após clicar, o total pode ainda não ter subido. O README avisa isso.

## Por que assim?

- **Uma consulta só (`$facet`):** total, série e tops saem dos mesmos documentos filtrados, numa ida ao banco.
- **Existência no Postgres:** quem decide se o link existe é a fonte da verdade, não a coleção de cliques (um link sem nenhum clique ainda existe).
- **UTC e constantes fixas:** a mesma pergunta tem a mesma resposta em qualquer servidor e ambiente.
- **Só agregados:** a resposta nunca traz o hash do IP nem dados por visitante.

## Experimente

Com a API rodando:

1. Clique três vezes em `/demo-link` com `curl` e consulte `curl -s 'http://localhost:8080/links/demo-link/stats'`. Depois repita com `?includeBots=true`. Explique a diferença.
2. Clique simulando um navegador e uma origem:

```bash
curl -s -o /dev/null -A 'Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0' \
  -e 'https://news.example.com/artigo?id=1' http://localhost:8080/demo-link
```

   (`-A` define o user-agent; `-e` define o Referer.) Consulte as estatísticas sem `includeBots` e encontre `news.example.com`, `Firefox` e `Linux`.
3. Peça um período inválido (`?from=2026-10-10&to=2026-10-01`) e leia o `invalid-period`.
4. Pare o MongoDB (`docker compose -f compose.dev.yaml stop mongo`), peça as estatísticas e veja o `503`. Suba o MongoDB de novo.

## Perguntas para fixar

1. O que o estágio `$facet` permite fazer numa única consulta?
2. Por que o filtro usa `isBot: { $ne: true }` e não `isBot: false`?
3. Por que a série diária é completada em Java?
4. Por que um link expirado continua tendo estatísticas?
5. Por que o MongoDB fora gera `503` e não `500`?

<details>
<summary>Respostas</summary>

1. Rodar várias sub-pipelines (total, por dia e os três tops) sobre os mesmos documentos filtrados, numa ida ao banco.
2. Para os documentos antigos, sem o campo `isBot`, contarem como humanos.
3. Porque o `$group` só devolve os dias que tiveram clique; o Java preenche os dias vazios com zero, para a série não ter buracos.
4. Porque a expiração só afeta o redirect (`410`); os cliques feitos antes continuam valendo como histórico.
5. Porque `503` diz que o serviço está temporariamente indisponível, e não que houve um bug: o cliente pode tentar de novo.

</details>

---

[← Anterior: 13 · Pipeline de cliques](13-pipeline-de-cliques.md) · [Índice](README.md) · [Próximo: 15 · Rate limit →](15-rate-limit-por-ip.md)
