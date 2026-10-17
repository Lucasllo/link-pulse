# observability/

Fonte única da observabilidade do Link Pulse: config do Prometheus, regras de alerta e seus testes, provisioning e dashboards do Grafana, e runbooks.

O `compose.yaml` monta esta pasta direto (bind mount, sem cópia). A Phase 4 (kind + kube-prometheus-stack) reaproveita os mesmos arquivos. Nenhum dashboard nem regra existe em outro lugar do repositório.

```text
observability/
├── prometheus/
│   ├── prometheus.yml                  # scrape e rule_files (só compose)
│   ├── rules/link-pulse-alerts.yml     # os 4 alertas, formato groups:
│   └── tests/link-pulse-alerts-test.yml  # testes de unidade das regras (promtool)
├── grafana/
│   ├── provisioning/
│   │   ├── datasources/prometheus.yml  # datasource com UID prometheus (só compose)
│   │   └── dashboards/link-pulse.yml   # provider de arquivos (só compose)
│   └── dashboards/
│       ├── link-pulse-overview.json    # Link Pulse — Aplicação (home)
│       └── link-pulse-pipeline.json    # Link Pulse — Pipeline de cliques
└── runbooks/                           # um por alerta, linkado pelo runbook_url
```

## Compose (Phase 3) x kind (Phase 4)

| Arquivo | O que é | Compose (Phase 3) | kind (Phase 4) |
|---------|---------|-------------------|----------------|
| `prometheus/prometheus.yml` | Scrape do app (`app:8081/actuator/prometheus`, job `link-pulse`) e `rule_files` | Diretório `prometheus/` montado em `/etc/prometheus` (read-only) | Não é usado. O scrape vem do **ServiceMonitor** do chart. O job precisa continuar se chamando `link-pulse`: por padrão o job é o nome do Service, então o Service deve se chamar `link-pulse`, ou o ServiceMonitor deve fixar `jobLabel`. Senão o `LinkPulseTargetDown` não casa com nada. |
| `prometheus/rules/link-pulse-alerts.yml` | Os quatro alertas, grupo `link-pulse` | Carregado por `rule_files: /etc/prometheus/rules/*.yml` | O conteúdo de `groups:` vira o `spec` de um **PrometheusRule** (com o label que o `ruleSelector` do kube-prometheus-stack espera). |
| `prometheus/tests/` | Testes de unidade das regras | Só `promtool` (`make obs-check`) | Só `promtool` (o CI da Phase 4 pode reaproveitar o `make obs-check`). |
| `grafana/provisioning/datasources/` | Datasource Prometheus com UID `prometheus` | Montado em `/etc/grafana/provisioning/datasources` | Não é usado. O datasource padrão do kube-prometheus-stack já tem o UID `prometheus`, o mesmo que os dashboards referenciam. |
| `grafana/provisioning/dashboards/` | Provider que lê os JSONs da pasta | Montado em `/etc/grafana/provisioning/dashboards` | Não é usado. Quem carrega os dashboards é o sidecar do chart. |
| `grafana/dashboards/*.json` | Os dois dashboards | Montado em `/etc/grafana/dashboards` | Viram **ConfigMaps** com o label `grafana_dashboard: "1"`, lidos pelo sidecar de dashboards do Grafana. |
| `runbooks/` | Um runbook por alerta | Linkados pela annotation `runbook_url` (arquivo na `main` do GitHub) | Os mesmos links: as annotations não mudam. |

O Helm só lê arquivos de dentro do diretório do chart (`.Files.Get` não sai dele). Então a Phase 4 decide o mecanismo para levar estes arquivos ao cluster sem copiá-los para outro lugar do repositório, por exemplo `kubectl create configmap --from-file` (com `kubectl label`) no `make k8s`, ou gerar o PrometheusRule a partir de `rules/link-pulse-alerts.yml` na hora do deploy.

As configurações do Grafana que o compose passa por variável de ambiente viram `grafana.ini` nos values do chart:

| Compose (`GF_*`) | `grafana.ini` (kind) | Por quê |
|------------------|----------------------|---------|
| `GF_AUTH_ANONYMOUS_ENABLED=true`, `GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer` | `auth.anonymous.enabled`, `auth.anonymous.org_role` | Leitura sem login, só Viewer |
| `GF_USERS_HOME_PAGE=/d/link-pulse-overview` | `users.home_page` | Home no dashboard da aplicação. Não usar `default_home_dashboard_path`: no Grafana 13 ele grava uma cópia do JSON que o anônimo não lê (403) |
| `GF_ANALYTICS_REPORTING_ENABLED=false`, `GF_ANALYTICS_CHECK_FOR_UPDATES=false` | `analytics.reporting_enabled`, `analytics.check_for_updates` | Sem chamadas para fora |

## Regras de edição

- **Dashboards em JSON clássico, escritos à mão.** O export da UI do Grafana 13 gera o formato v2 (resource com `apiVersion`), que o provisioning por arquivo recusa. O provider tem `allowUiUpdates: false`: mudanças feitas na UI não ficam. Edite o JSON.
- **Datasource sempre pelo UID `prometheus`** (`{"type": "prometheus", "uid": "prometheus"}`), sem `__inputs` nem UID gerado.
- **Janelas com `$__rate_interval`**, nunca um `[1m]` fixo: o intervalo de scrape pode mudar no kind.
- **`max()` nas gauges do grupo** (`linkpulse_clicks_stream_lag`, `_pending`, `_length`): todas as réplicas reportam o mesmo valor do grupo, então `sum()` multiplicaria pelo número de réplicas. Lag `NaN` é "indefinido", não zero.
- **Nenhum filtro por `job`, `instance` ou `namespace`** nos painéis: os nomes mudam entre compose e kind. A única exceção é o `LinkPulseTargetDown`, que filtra `up{job="link-pulse"}` de propósito.
- **Nenhuma label por código curto, URL ou IP**: cardinalidade ilimitada e dado pessoal. As rotas aparecem pelo template (`uri="/{code:[A-Za-z0-9_-]+}"`).
- **Annotations dos alertas sem `$value`**: os testes do `promtool` comparam o texto exato; o valor atual aparece na UI do Prometheus.

## Alertas

| Alerta | Condição | `for` | Severidade | Runbook |
|--------|----------|-------|------------|---------|
| `LinkPulseHigh5xxRate` | Mais de 5% das respostas da API são 5xx (rotas do actuator fora) | 5m | critical | [LinkPulseHigh5xxRate.md](runbooks/LinkPulseHigh5xxRate.md) |
| `LinkPulseHighP99Latency` | p99 do `GET /{code}` acima de 500 ms | 5m | warning | [LinkPulseHighP99Latency.md](runbooks/LinkPulseHighP99Latency.md) |
| `LinkPulseClickConsumerLag` | `max()` do lag ou dos pendentes do grupo acima de 1000 | 5m | warning | [LinkPulseClickConsumerLag.md](runbooks/LinkPulseClickConsumerLag.md) |
| `LinkPulseTargetDown` | `up{job="link-pulse"} == 0` ou série ausente | 1m | critical | [LinkPulseTargetDown.md](runbooks/LinkPulseTargetDown.md) |

Não há Alertmanager: o estado firing aparece em <http://localhost:9090/alerts>.

## Validação

```bash
make obs-check
```

Sem make (no Git Bash, o `MSYS_NO_PATHCONV=1` impede que `/etc/...` vire um caminho do Windows):

```bash
MSYS_NO_PATHCONV=1 docker compose run --rm --no-deps -T --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml
MSYS_NO_PATHCONV=1 docker compose run --rm --no-deps -T --entrypoint promtool prometheus test rules /etc/prometheus/tests/link-pulse-alerts-test.yml
```

Os dois rodam o `promtool` da própria imagem `prom/prometheus:v3.15.0`, sem nada instalado no host e sem subir a stack.

Depois de editar, com a stack de pé:

- **Regra ou `prometheus.yml`**: o Prometheus só relê com `SIGHUP` (a API de lifecycle fica desligada de propósito):

  ```bash
  docker compose kill -s SIGHUP prometheus
  ```

- **Dashboard**: nada a fazer. O provider relê a pasta a cada 30 s.
- **Provisioning do Grafana** (datasource ou provider): `docker compose restart grafana`.
