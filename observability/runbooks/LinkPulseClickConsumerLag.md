# LinkPulseClickConsumerLag

Severidade: **warning**. Regra em [`observability/prometheus/rules/link-pulse-alerts.yml`](../prometheus/rules/link-pulse-alerts.yml).

## Significado

O consumer de cliques está atrasado: o lag ou os pendentes do grupo passaram de 1000 entradas durante 5 min seguidos.

Os dois números medem coisas diferentes:

- **lag**: entradas do stream que ainda não foram entregues ao grupo. Cresce quando o consumer não dá conta de ler (ou está parado).
- **pendentes**: entradas já entregues ao consumer, mas ainda sem `XACK`. Cresce quando o consumer lê e não consegue gravar.

Com o MongoDB fora, o consumer continua lendo: o lag fica perto de zero e os pendentes crescem. Por isso a regra olha os dois (`or`).

Lag `NaN` significa indefinido (o Valkey não soube calcular) e não dispara o alerta.

## Impacto

Os redirects continuam funcionando: o clique é publicado no stream e o redirect não espera o MongoDB.

O que atrasa são as estatísticas (`GET /links/{code}/stats`): os cliques ficam no stream até o consumer gravá-los.

Se o atraso passar do tamanho do stream (`MAXLEN ~ 100000`), as entradas mais antigas são aparadas antes de serem processadas. Esses cliques se perdem e aparecem em `linkpulse_clicks_lost_total`.

## Diagnóstico

No Prometheus (http://localhost:9090) ou no dashboard "Link Pulse — Pipeline de cliques" (painéis "Lag do consumer", "Pendentes (PEL)" e "Lag e pendentes"):

```promql
max(linkpulse_clicks_stream_lag)
max(linkpulse_clicks_stream_pending)
rate(linkpulse_clicks_consumer_errors_total[5m])
increase(linkpulse_clicks_lost_total[1h])
```

Erros do consumer subindo junto com os pendentes apontam para o MongoDB:

```bash
docker compose ps mongo
docker compose logs --tail=200 mongo
docker compose logs --tail=200 app | grep -i 'consumer de cliques'
```

O consumer registra um WARN na primeira falha de uma sequência e um INFO quando se recupera.

## Mitigação

Restaure o MongoDB:

```bash
docker compose up -d --wait mongo
```

Depois disso, acompanhe os pendentes voltarem a zero no dashboard do pipeline. Não precisa de ação manual: o reclaim reprocessa, a cada 30 s, as entradas pendentes paradas há mais de 60 s, e a gravação não duplica cliques.

**Nunca apague o stream nem o grupo** (`DEL`, `XGROUP DESTROY`): isso descarta os cliques que ainda não foram gravados.

O alerta some quando lag e pendentes ficam abaixo de 1000.
