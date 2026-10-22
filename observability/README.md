# observability/

Fonte única da observabilidade do Link Pulse: config do Prometheus, regras de alerta e seus testes, provisioning e dashboards do Grafana, e runbooks.

O `compose.yaml` monta esta pasta direto (bind mount, sem cópia). O `make k8s` (kind + kube-prometheus-stack 92.10.1, com Grafana 13.2.4 e Prometheus v3.15.0) leva os mesmos arquivos ao cluster por `--set-file`, também sem cópia. Nenhum dashboard nem regra existe em outro lugar do repositório.

| | Compose (`make up`) | kind (`make k8s`) |
|---|---|---|
| Grafana (anônimo, só leitura) | <http://localhost:3000> | <http://grafana.localtest.me> |
| Alertas do Prometheus | <http://localhost:9090/alerts> | <http://prometheus.localtest.me/alerts> |

No kind, com `K8S_HTTP_PORT` diferente de 80, as URLs ganham `:{porta}` (o `make k8s` imprime as certas). O admin do Grafana é `admin`/`admin` nos dois (senha de demonstração, não é segredo).

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
| `prometheus/prometheus.yml` | Scrape do app (`app:8081/actuator/prometheus`, job `link-pulse`) e `rule_files` | Diretório `prometheus/` montado em `/etc/prometheus` (read-only) | Não é usado. O scrape vem do **ServiceMonitor** do chart `deploy/helm/link-pulse` (`templates/servicemonitor.yaml`), na porta `management` (`/actuator/prometheus`, 15 s), com `jobLabel: app.kubernetes.io/name`: o job é `link-pulse` qualquer que seja o nome do Service, e o `LinkPulseTargetDown` casa. |
| `prometheus/rules/link-pulse-alerts.yml` | Os quatro alertas, grupo `link-pulse` | Carregado por `rule_files: /etc/prometheus/rules/*.yml` | Vira o **PrometheusRule** do chart `deploy/helm/link-pulse` (`templates/prometheusrule.yaml`): o `deploy/kind/up.sh` passa o arquivo por `--set-file observability.rules=observability/prometheus/rules/link-pulse-alerts.yml` e o `groups:` vira o `spec`. Sem label de release: os seletores do kube-prometheus-stack estão abertos (`ruleSelectorNilUsesHelmValues: false`). |
| `prometheus/tests/` | Testes de unidade das regras | Só `promtool` (`make obs-check`) | Só `promtool` (o CI da Phase 4 pode reaproveitar o `make obs-check`). |
| `grafana/provisioning/datasources/` | Datasource Prometheus com UID `prometheus` | Montado em `/etc/grafana/provisioning/datasources` | Não é usado. O datasource é o padrão do kube-prometheus-stack, com o UID `prometheus`, o mesmo que os dashboards referenciam. |
| `grafana/provisioning/dashboards/` | Provider que lê os JSONs da pasta | Montado em `/etc/grafana/provisioning/dashboards` | Não é usado. Quem carrega os dashboards é o sidecar do Grafana do kube-prometheus-stack, na pasta `Link Pulse` (UID `link-pulse`), read-only como no compose (`deploy/kind/values/kube-prometheus-stack.yaml`). |
| `grafana/dashboards/*.json` | Os dois dashboards | Montado em `/etc/grafana/dashboards` | Um **ConfigMap** com o label `grafana_dashboard: "1"` por JSON (`templates/dashboards-configmap.yaml`), passado por `--set-file observability.dashboards.{nome}=observability/grafana/dashboards/{nome}.json` (um por arquivo, num loop do `up.sh`) e lido pelo sidecar de dashboards na pasta `Link Pulse` (UID `link-pulse`). |
| `runbooks/` | Um runbook por alerta | Linkados pela annotation `runbook_url` (arquivo na `main` do GitHub) | Os mesmos links: as annotations não mudam. |

Por que `--set-file`: o Helm só lê arquivos de dentro do diretório do chart (`.Files.Get` não sai dele), um symlink do chart para cá quebra no checkout do Windows, e copiar os arquivos para o chart criaria uma segunda fonte que diverge. Com `--set-file`, o `deploy/kind/up.sh` passa o conteúdo destes arquivos como value na hora do deploy, e o PrometheusRule e os ConfigMaps continuam objetos do release `link-pulse` (somem com ele). O conteúdo entra como valor, nunca como template: as legendas `{{method}}` dos painéis chegam intactas. Se as regras ou os dashboards vierem vazios, o render do chart falha com a mensagem do que passar.

Dashboard novo: basta criar o JSON nesta pasta; o próximo make k8s o publica.

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

Não há Alertmanager (nem no compose nem no kind): o estado firing aparece em <http://localhost:9090/alerts> no compose e em <http://prometheus.localtest.me/alerts> no kind.

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

No kind, qualquer mudança em regra ou dashboard entra com um novo `make k8s`: o Operator recarrega o Prometheus e o sidecar relê os ConfigMaps sozinhos.
