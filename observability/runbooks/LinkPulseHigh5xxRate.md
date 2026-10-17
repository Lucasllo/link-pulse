# LinkPulseHigh5xxRate

Severidade: **critical**. Regra em [`observability/prometheus/rules/link-pulse-alerts.yml`](../prometheus/rules/link-pulse-alerts.yml).

## Significado

Mais de 5% das respostas da API foram 5xx durante 5 min seguidos.

A taxa considera todas as rotas da API e deixa de fora as do actuator (`uri!~"/actuator.*"`).

## Impacto

Parte dos clientes recebe erro: links que não são criados, redirects que falham ou estatísticas que não carregam, conforme a rota afetada.

## Diagnóstico

Quais rotas e status estão falhando, no Prometheus (http://localhost:9090):

```promql
sum by (uri, method, status) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
```

O painel "Respostas 5xx/s" do dashboard "Link Pulse — Aplicação" mostra a mesma coisa ao longo do tempo.

Os logs do app. Um 500 inesperado sai como Problem Details com type `/problems/internal-error`:

```bash
docker compose logs --tail=500 app | grep -i 'internal-error\|ERROR\|WARN'
```

Cada rota depende de algo diferente:

- `GET /links/{code}/stats` responde **503** com type `/problems/stats-unavailable` quando o MongoDB está fora. É esperado, não é bug.
- `POST /links` depende do Postgres (a sequência gera o código curto).
- `GET /{code}` lê do Valkey e cai para o Postgres no miss. Com o Valkey fora, o redirect continua funcionando pelo Postgres.

Estado das dependências:

```bash
docker compose ps
docker compose logs --tail=200 postgres
docker compose logs --tail=200 mongo
```

## Mitigação

Religue a dependência que caiu e espere os healthchecks:

```bash
docker compose up -d --wait
```

Se todas as dependências estão healthy e o erro continua, o problema está no app: siga o `/problems/internal-error` nos logs até a exceção. Um restart (`docker compose restart app`) só ganha tempo, não corrige.

O alerta some quando a taxa fica abaixo de 5% na janela de 5 min.
