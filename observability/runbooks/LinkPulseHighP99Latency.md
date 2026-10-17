# LinkPulseHighP99Latency

Severidade: **warning**. Regra em [`observability/prometheus/rules/link-pulse-alerts.yml`](../prometheus/rules/link-pulse-alerts.yml).

## Significado

O p99 do redirect (`GET /{code}`) ficou acima de 500 ms durante 5 min seguidos.

O caminho quente é: Valkey (cache-aside) e, no miss, Postgres. O clique é publicado no stream em segundo plano e não entra na latência do redirect.

## Impacto

1% dos redirects demora mais de meio segundo. O usuário final sente o atraso antes de chegar ao destino.

## Diagnóstico

O p99 atual, no Prometheus (http://localhost:9090):

```promql
histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/{code:[A-Za-z0-9_-]+}"}[5m])))
```

No dashboard "Link Pulse — Aplicação", confira:

- "Cache hit ratio": um hit ratio baixo manda o redirect para o Postgres;
- "Valkey indisponível": o Valkey fora faz todo redirect ir ao Postgres.

O cache está falhando ou sendo contornado?

```promql
sum by (result) (rate(linkpulse_cache_requests_total{result=~"error|bypass"}[5m]))
```

A fila do publisher (painel "Fila do publisher" no dashboard "Link Pulse — Pipeline de cliques"). Uma fila cheia indica que o Valkey não está aceitando os cliques:

```promql
max(linkpulse_clicks_publish_queue)
```

CPU e memória do app e do Postgres:

```bash
docker stats --no-stream
docker compose ps valkey postgres
docker compose logs --tail=200 valkey
```

## Mitigação

- Valkey fora ou falhando: `docker compose up -d --wait valkey`. O cache volta a encher a cada miss.
- Carga acima do que a máquina aguenta (CPU do app ou do Postgres no limite em `docker stats`): reduza a carga, por exemplo interrompendo um teste de carga em andamento.

O alerta some quando o p99 fica abaixo de 500 ms na janela de 5 min.
