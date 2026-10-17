---
phase: 03-stack-local-observ-vel
reviewed: 2026-10-17T14:17:58Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - .github/dependabot.yml
  - .gitignore
  - Makefile
  - README.md
  - app/.dockerignore
  - app/Dockerfile
  - compose.dev.yaml
  - compose.yaml
  - load/lib/common.js
  - load/load.js
  - load/seed.js
  - observability/README.md
  - observability/grafana/dashboards/link-pulse-overview.json
  - observability/grafana/dashboards/link-pulse-pipeline.json
  - observability/grafana/provisioning/dashboards/link-pulse.yml
  - observability/grafana/provisioning/datasources/prometheus.yml
  - observability/prometheus/prometheus.yml
  - observability/prometheus/rules/link-pulse-alerts.yml
  - observability/prometheus/tests/link-pulse-alerts-test.yml
  - observability/runbooks/LinkPulseClickConsumerLag.md
  - observability/runbooks/LinkPulseHigh5xxRate.md
  - observability/runbooks/LinkPulseHighP99Latency.md
  - observability/runbooks/LinkPulseTargetDown.md
findings:
  critical: 0
  warning: 4
  info: 9
  total: 13
status: issues_found
---

# Phase 03: Relatório de Code Review

**Revisado em:** 2026-10-17T14:17:58Z
**Profundidade:** standard
**Arquivos revisados:** 23
**Status:** issues_found

## Resumo

Revisei a imagem Docker, as duas stacks compose, o Makefile, os scripts k6 (seed e carga), a config do Prometheus, as regras de alerta e os testes do promtool, o provisioning e os dois dashboards do Grafana, os runbooks, o Dependabot e o README.

Para conferir as referências cruzadas, li o código do app. Batem com o código os nomes das métricas e das tags (`linkpulse_cache_requests_total{result=hit|negative_hit|miss|error|bypass}`, `linkpulse_clicks_publish_total{result=published|rejected|failed}`, `linkpulse_clicks_consumer_write_seconds{outcome}`, `linkpulse_ratelimit_requests_total{result=rejected}` e as gauges do stream). Também batem os templates de URI (`/{code:[A-Za-z0-9_-]+}`, `/links`) e o alinhamento da janela do rate limit (`floor(epochMillis / window)`), de que depende o orçamento de POSTs do `load.js`.

O Makefile segue a restrição do Git Bash: o `NOCONV` só entra no `STACK` e não é exportado. Nada quebra o `make up` hoje e não há falha de segurança. As portas ficam no loopback, o Grafana anônimo entra só como Viewer e os defaults estão marcados como de demonstração.

Os problemas reais são quatro:

- O seed tem uma bomba-relógio de 30 dias: o alias com expiração passa a reprovar os thresholds.
- O Dependabot vai propor saltos de major que quebram os volumes e as restrições do projeto.
- Os testes do promtool e o build da imagem não rodam no CI.
- O runbook de lag afirma que todo clique aparado aparece em `linkpulse_clicks_lost_total`, o que não é verdade.

## Warnings

### WR-01: `make seed` passa a falhar 30 dias depois do primeiro seed (alias `promo-30-dias` expira)

**Arquivo:** `load/seed.js:57`, `load/seed.js:292-310`, `load/lib/common.js:62-69`
**Problema:** O alias `promo-30-dias` é criado com `expiresAt = agora + 30 dias`. Como o `make down` mantém o volume `pgdata`, o alias continua no banco depois desse prazo. A partir do 31º dia, cada `make seed` faz o seguinte:
1. O `HEAD /promo-30-dias` responde **410**. O `linkExists` só aceita 302/404 (`expectedStatuses(302, 404)`), então a requisição conta como falha em `http_req_failed` e a função devolve `false`.
2. O `POST` responde 409 (o alias já existe), e o alias entra em `codes` com peso 4.
3. No cenário `clicks`, cerca de 4/97 (≈4%) dos GETs vão para o link expirado e recebem 410. Isso reprova o check `redirect 302` (o threshold é `checks: rate>0.99`) e o `http_req_failed: rate<0.01`.

O k6 sai com o código 99 e o `make seed` falha. Isso contradiz o README ("Pode rodar de novo"). Pelo código do `RedirectController`, o `HEAD` de um link expirado passa por `redirectService.resolve`, que lança o 410.

**Correção:** trate o 410 como "existe, mas não serve para cliques": deixe o alias de fora dos códigos sorteados e não tente recriá-lo.
```js
// common.js
export function linkStatus(code) {
  const res = http.head(`${BASE_URL}/${code}`, {
    redirects: 0,
    responseCallback: http.expectedStatuses(302, 404, 410),
    tags: { name: 'HEAD /{code}' },
  });
  return res.status; // 302 existe, 404 não existe, 410 expirado
}
// seed.js setup()
const status = linkStatus(a.alias);
if (status === 410) { expired++; continue; } // não entra em codes/weights
if (status === 302) { existing++; } else { /* createLink ... */ }
```
Outra saída é usar um alias sem expiração fixa: gere um alias com sufixo de data (`promo-${yyyymm}`) ou crie esse link sempre sem alias.

### WR-02: Dependabot sem regras de `ignore` para majors de imagens com estado e da JDK

**Arquivo:** `.github/dependabot.yml:81-92`
**Problema:** Os ecossistemas `docker-compose` e `docker` não têm nenhum `ignore`. O Dependabot propõe tags com o mesmo sufixo e versão maior, e várias dessas propostas quebram restrições declaradas no CLAUDE.md ou os volumes existentes:
- `eclipse-temurin:21-jdk-noble` e `21-jre-noble` passariam para `25-*-noble`. A restrição do projeto é Java 21.
- `postgres:18.6` passaria para `19.x` quando a 19 estiver no Docker Hub. O volume `pgdata` inicializado na 18 deixa de subir ("database files are incompatible with server") e a paridade com o RDS 18 se perde.
- `mongo:8.0` passaria para minors de rapid release, mas o CLAUDE.md diz "Usar mongo:8.0, não 8.3", por causa do driver 5.5.x.
- `valkey/valkey:8.1` passaria para `9.x`, o que exige reavaliar a paridade com o ElastiCache.

Como o CI não sobe o compose nem builda a imagem (ver WR-03), um PR desses fica verde e é mesclável.
**Correção:**
```yaml
  - package-ecosystem: docker-compose
    directory: "/"
    schedule: { interval: weekly }
    open-pull-requests-limit: 5
    ignore:
      - dependency-name: "postgres"
        update-types: ["version-update:semver-major"]
      - dependency-name: "mongo"
        update-types: ["version-update:semver-major", "version-update:semver-minor"]
      - dependency-name: "valkey/valkey"
        update-types: ["version-update:semver-major"]
  - package-ecosystem: docker
    directory: "/app"
    schedule: { interval: weekly }
    open-pull-requests-limit: 5
    ignore:
      - dependency-name: "eclipse-temurin"
        update-types: ["version-update:semver-major"]
```

### WR-03: Os testes de alerta (`make obs-check`) e o build da imagem não rodam no CI

**Arquivo:** `Makefile:67-69`, `README.md:178`, `observability/README.md:29` (o gate ausente fica em `.github/workflows/ci.yml:39-46`)
**Problema:** Esta fase criou testes de unidade das regras (`link-pulse-alerts-test.yml`), e o README os apresenta como garantia ("As regras têm testes de unidade (`make obs-check`)"). O workflow `ci`, porém, só roda `make -n verify`, `make help` e `./mvnw verify`. Ficam sem verificação a cada push:
- uma regra quebrada (PromQL inválido, annotation alterada sem atualizar o teste);
- um `prometheus.yml` inválido;
- um `Dockerfile` que não builda.

Isso pesa mais porque o Dependabot vai abrir PRs que mexem justamente nesses arquivos (WR-02).
**Correção:** adicione ao `ci.yml` um job com `make obs-check` (o runner Linux tem Docker e o `promtool` vem da imagem) e um `docker build app` (ou `docker compose build app`). Se possível, inclua também um `docker compose config -q` para validar a interpolação do `compose.yaml`.

### WR-04: O runbook de lag afirma que todo clique aparado aparece em `linkpulse_clicks_lost_total`, o que é falso para entradas nunca entregues

**Arquivo:** `observability/runbooks/LinkPulseClickConsumerLag.md:24`
**Problema:** O runbook diz: "Se o atraso passar do tamanho do stream (`MAXLEN ~ 100000`), as entradas mais antigas são aparadas antes de serem processadas. Esses cliques se perdem e aparecem em `linkpulse_clicks_lost_total`."

No código (`ClickStreamConsumer.acknowledgeVanished`, linhas 504-537), `lost` só conta **entradas pendentes (PEL)** que o `XCLAIM` não devolveu e que não existem mais no stream. Uma entrada aparada que **nunca foi entregue ao grupo** é justamente o caso do lag alto (consumer lento ou parado). Ela some sem passar pelo PEL e não é contada em lugar nenhum. Um operador que seguir o runbook vê `increase(linkpulse_clicks_lost_total[1h]) == 0` e conclui que não houve perda, quando houve.
**Correção:** corrija o texto e dê uma query que detecte a perda por lag. Com trim por `MAXLEN` (sem `XDEL`), o `lag` do `XINFO GROUPS` continua válido e pode passar do `XLEN`:
```markdown
- Pendentes aparados antes da gravação: `linkpulse_clicks_lost_total`.
- Entradas nunca entregues e aparadas pelo MAXLEN NÃO são contadas. Sinal de perda:
  `max(linkpulse_clicks_stream_lag) > max(linkpulse_clicks_stream_length)`
  (o grupo está atrás da entrada mais antiga que ainda existe no stream).
```
Outra opção é adicionar uma regra de alerta para essa condição.

## Info

### IN-01: O painel "Respostas 5xx/s" não exclui o actuator, ao contrário do alerta

**Arquivo:** `observability/grafana/dashboards/link-pulse-overview.json` (painel id 10)
**Problema:** A query do painel é `sum(rate(http_server_requests_seconds_count{status=~"5.."}[$__rate_interval]))`. O `LinkPulseHigh5xxRate` e o painel "Requisições/s" filtram `uri!~"/actuator.*"`. Se a porta de gerenciamento registrar `http.server.requests`, o 503 do `/actuator/health/readiness` durante a subida (o `HEALTHCHECK` consulta a cada 2 s no start-interval) aparece como 5xx da API no painel, e o alerta não dispara.
**Correção:** use o mesmo filtro do alerta: `{status=~"5..", uri!~"/actuator.*"}`.

### IN-02: Painéis misturam `increase()` por janela e `rate()` por segundo, com unidades inconsistentes

**Arquivo:** `observability/grafana/dashboards/link-pulse-overview.json` (painel 12), `link-pulse-pipeline.json` (painéis 6, 7 e 8)
**Problema:**
- O painel "Valkey indisponível" põe no mesmo eixo `increase(...[$__rate_interval])` (eventos por janela) e `rate(...)` com legenda `{{result}}/s` (eventos por segundo).
- "Descartados" e "Duplicados, reclaim e erros" usam `increase` sobre `$__rate_interval`. O valor muda com o zoom (o step e a janela mudam), e janelas sobrepostas contam o mesmo evento em pontos vizinhos.
- "Publicados x gravados por segundo" usa a unidade `short`, quando o valor é uma taxa.

**Correção:** padronize em `rate()` com a unidade `ops` ou `reqps`. Se a ideia for mostrar contagens, use `increase(...[$__interval])` com `interval` mínimo igual ao do scrape.

### IN-03: `LinkPulseHigh5xxRate` sem guarda de tráfego mínimo

**Arquivo:** `observability/prometheus/rules/link-pulse-alerts.yml:33-36`
**Problema:** Com o tráfego de demonstração, quase nulo, poucas requisições com 5xx (por exemplo, 2 ou 3 `/stats` com 503 enquanto o avaliador testa o Mongo parado) bastam para a razão ficar acima de 5% durante quase toda a janela de 5 min. Isso gera um alerta **critical** por ruído estatístico.
**Correção:** acrescente `and sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[5m])) > 0.1` (ou outro mínimo de req/s) e um caso de teste com baixo volume.

### IN-04: Os testes não cobrem o ramo `lag` de `LinkPulseClickConsumerLag`

**Arquivo:** `observability/prometheus/tests/link-pulse-alerts-test.yml:198-228`
**Problema:** Só há o caso "pendentes > 1000 com lag 0" (dispara) e o caso "lag NaN com pendentes 0" (não dispara). Nenhum caso faz o primeiro operando do `or` (`max(lag) > 1000`) disparar sozinho. Uma regressão nesse lado da expressão passaria nos testes.
**Correção:** adicione um caso com `lag` em `0+500x40` e `pending` em `0x40`, esperando o alerta em `eval_time: 10m`, e um caso abaixo do limiar (por exemplo `lag` constante em 900).

### IN-05: O runbook de 5xx manda procurar `/problems/internal-error` nos logs, mas o log não tem esse texto

**Arquivo:** `observability/runbooks/LinkPulseHigh5xxRate.md:25-28`, `:53`
**Problema:** O `GlobalExceptionHandler` registra `LOG.error("Erro inesperado ao processar a requisição", ex)`. O type `/problems/internal-error` só aparece no corpo da resposta. O grep acaba casando por `ERROR`, mas a instrução "siga o `/problems/internal-error` nos logs até a exceção" engana quem lê.
**Correção:** troque por `docker compose logs --tail=500 app | grep -A20 'Erro inesperado ao processar a requisição'`.

### IN-06: App sem `restart` e sem limite de memória, com `-XX:+ExitOnOutOfMemoryError`

**Arquivo:** `compose.yaml:52-80`, `app/Dockerfile:40`
**Problema:** A JVM sai no primeiro OOM, e o serviço `app` não tem `restart:`, então fica parado até alguém intervir. Sem `mem_limit`, o `MaxRAMPercentage=75` é calculado sobre toda a memória da VM do Docker Desktop, não sobre um orçamento do container.
**Correção:** use `restart: unless-stopped` e `mem_limit: 768m` (ou `deploy.resources.limits.memory`) no serviço `app`. Na Phase 4, o Kubernetes cobre os dois.

### IN-07: Volumes anônimos do Valkey (`/data`) e do Mongo (`/data/configdb`) vazam a cada `down`/`up`

**Arquivo:** `compose.yaml:30-50`
**Problema:** As imagens `valkey/valkey` e `mongo` declaram `VOLUME`. O `make down` (sem `-v`) não remove volumes anônimos, e o próximo `up` cria outros, então volumes órfãos se acumulam. Além disso, as entradas do stream `lp:clicks` ainda não consumidas somem num ciclo `down`/`up`, e o README (linha 113) não menciona isso.
**Correção:** declare volumes nomeados (`valkeydata:/data`, `mongoconfig:/data/configdb`), ou documente que o `make clean` limpa os anônimos e que o stream não sobrevive ao `down`.

### IN-08: `load/results/load-summary.json` é versionado e regravado a cada `make load`

**Arquivo:** `Makefile:61-62`, `README.md:152`
**Problema:** Cada execução deixa a árvore suja, com números que dependem da máquina, e convida a commits acidentais do resultado de outra máquina.
**Correção:** grave em `results/load-summary.local.json` (ignorado no `.gitignore`) e versione só um `load-summary.json` de referência, atualizado de propósito. Outra opção é documentar `git checkout -- load/results` no README.

### IN-09: Imagens base com tags flutuantes e `apt-get` sem versão fixa

**Arquivo:** `app/Dockerfile:5`, `:8-10`, `:27`
**Problema:** `eclipse-temurin:21-jdk-noble` e `21-jre-noble` mudam de conteúdo a cada patch (o CLAUDE.md sugere a tag completa `21.0.12.1_1-jre-noble`), e o `unzip` é instalado sem versão. Isso tira a reprodutibilidade do build e torna inútil o Dependabot `docker`, que não tem patch para propor numa tag flutuante.
**Correção:** fixe `eclipse-temurin:21.0.12.1_1-jdk-noble` e `21.0.12.1_1-jre-noble` (de preferência com `@sha256:`) e deixe o Dependabot atualizar os patches.

---

_Revisado em: 2026-10-17T14:17:58Z_
_Revisor: Claude (gsd-code-reviewer)_
_Profundidade: standard_
