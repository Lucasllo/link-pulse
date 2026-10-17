# LinkPulseTargetDown

Severidade: **critical**. Regra em [`observability/prometheus/rules/link-pulse-alerts.yml`](../prometheus/rules/link-pulse-alerts.yml).

## Significado

O Prometheus não consegue raspar `app:8081/actuator/prometheus` há mais de 1 min.

A regra é `up{job="link-pulse"} == 0 or absent(up{job="link-pulse"})`:

- no compose, o container do app parou ou não responde, e `up` vai a 0;
- no kind (Phase 4), o pod sumiu e a série `up` deixa de existir; o `absent()` cobre esse caso.

## Impacto

Sem métricas, o resto da observabilidade fica cego: os dashboards param e os outros alertas não avaliam nada.

Normalmente a API também está fora: `POST /links`, o redirect e o `/stats` não respondem.

## Diagnóstico

O container do app está de pé e healthy?

```bash
docker compose ps
docker compose logs --tail=200 app
```

O que o Prometheus vê do target (procure `"job":"link-pulse"`, `health` e `lastError`):

```bash
curl -s http://127.0.0.1:9090/api/v1/targets
```

No Prometheus (http://localhost:9090), a query:

```promql
up{job="link-pulse"}
```

O health do app por dentro da rede do compose (a porta 8081 não é publicada no host):

```bash
docker compose exec app curl -s http://localhost:8081/actuator/health
```

No kind (Phase 4), os equivalentes são `kubectl get pods`, `kubectl logs` e o ServiceMonitor do chart. O job do ServiceMonitor precisa continuar `link-pulse`, senão esta regra não casa.

## Mitigação

Religar o app e esperar o healthcheck:

```bash
docker compose up -d --wait app
```

Se o app não sobe, confira as dependências e a configuração:

- `docker compose ps postgres valkey mongo`: o app só sobe com as três healthy;
- `docker compose logs --tail=200 app`: erros de conexão ou de configuração na subida;
- variáveis de ambiente do serviço `app` no `compose.yaml` ou no `.env`. Por exemplo, `LINKPULSE_IP_HASH_KEY` com menos de 16 caracteres derruba a subida.

O alerta some sozinho quando o scrape volta a funcionar.
