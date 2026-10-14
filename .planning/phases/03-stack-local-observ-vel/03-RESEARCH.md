# Phase 3: Stack local observável - Research

**Researched:** 2026-10-14
**Domain:** Docker (multi-stage/layered jar), Docker Compose, Prometheus v3, Grafana 13 (provisioning por arquivo), k6 v2
**Confidence:** HIGH no container, no compose e nas métricas reais; MEDIUM no Grafana 13 (formato de dashboard) e nos detalhes do k6 v2

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Seed/demo (discutido)
- **D-01:** O seed é um **script k6** executado pelo container `grafana/k6:2.3.0` na rede do compose, pelo alvo `make seed`. Ele reaproveita a ferramenta do teste de carga e dispensa bash e curl no host.
- **D-02:** O volume é **pequeno e rápido**: cerca de 10 a 15 links, alguns com alias legível, e de algumas centenas a poucos milhares de cliques em 30 a 60 s. Referrers, navegadores e SO variam para os tops do `/stats` e dos painéis ficarem interessantes. Os cliques usam **User-Agent de navegador**, porque clientes de linha de comando contam como bot desde a decisão 02-05.
- **D-03:** O seed **fica abaixo do rate limit**, com no máximo 15 criações. O compose mantém o default do app (`linkpulse.ratelimit.limit=20` por minuto) e o seed nunca deve receber 429.
- **D-04:** **Sem backfill**. Os cliques só têm o timestamp do momento (o `ts` é definido pelo servidor), então "cliques por dia" no `/stats` mostra só o dia atual. Nada é inserido direto no Mongo. A limitação vai documentada (README e saída do seed).

#### Compose e `make up` (discutido)
- **D-05:** Um **`compose.yaml` novo na raiz** traz a stack completa do `make up`, de modo que `docker compose up` funcione sem `-f`. O `compose.dev.yaml` continua existindo para o `make run` (app no host). Reaproveitar definições entre os dois (`include`/`extends`/anchors) fica a critério do planejador. **As stacks não podem disputar portas nem volumes** ao rodar uma depois da outra.
- **D-06:** Ficam publicadas **só as portas de uso, todas em `127.0.0.1`**: app 8080, Grafana 3000 e Prometheus 9090. Postgres, Mongo, Valkey e a porta de gerenciamento 8081 ficam só na rede interna. O Prometheus raspa `app:8081/actuator/prometheus` por dentro da rede.
- **D-07:** Os segredos de demonstração são **defaults no próprio `compose.yaml`** no formato `${VAR:-default}` (`LINKPULSE_IP_HASH_KEY` com pelo menos 16 caracteres, senha do Postgres, admin do Grafana), comentados como "não é segredo". Quem quiser sobrescreve por env ou `.env`, e o `.env` entra no `.gitignore`. O `make up` funciona logo depois do clone, sem passo extra.
- **D-08:** Os alvos de make são `up`, `down` (mantém os volumes), `clean` (`down -v`), `image` (builda só a imagem local do app, reaproveitável no `kind load` da Phase 4), `seed` e `load` (os dois com k6 em container). `logs` e `ps` não foram pedidos.

#### k6 e rate limit (discutido)
- **D-09:** O cenário de **criação roda abaixo do limite**: `constant-arrival-rate` de cerca de 15 por minuto, medindo a latência do `POST /links` sem mudar a config do app. A maior parte da carga vai para o **redirect**, o caminho quente sem rate limit. **Cuidado:** seed e load criam a partir do mesmo IP, e rodar `make load` menos de 1 minuto depois do `make seed` soma as criações na mesma janela. O planejador deve tratar isso (margem na taxa, espera inicial ou outra solução) para o teste não falhar de forma intermitente.
- **D-10:** O perfil é **moderado e conservador**: cerca de 2 a 3 min, redirect em torno de 200 req/s. Thresholds: redirect com **p95 < 50 ms e p99 < 100 ms**, criação com **p95 < 200 ms**, **taxa de erro < 1%**. O teste precisa passar num laptop com Docker Desktop. O redirect usa `redirects: 0` e checa `status === 302`.
- **D-11:** A saída do k6 é **só o resumo no terminal mais um JSON** (`handleSummary`/`--summary-export`) gravado num caminho versionável, para o README da Phase 5. **Sem remote write** no Prometheus: os dashboards já mostram a carga pelo lado do servidor.
- **D-12:** O redirect do k6 manda **User-Agent de navegador**. Os cliques da carga aparecem no `/stats` padrão e no painel de cliques/min, o que mostra o pipeline funcionando sob carga.

#### Herdado de decisões anteriores (não rediscutir)
- **D-13:** A imagem de runtime é `eclipse-temurin:21-jre-noble` (traz curl para o `HEALTHCHECK`). O build usa `eclipse-temurin:21-jdk-noble` com o `./mvnw` do repositório e cache em `/root/.m2`. A extração usa `-Djarmode=tools extract --layers --launcher` e o ENTRYPOINT é `org.springframework.boot.loader.launch.JarLauncher`. O usuário é numérico (`10001`) (CLAUDE.md).
- **D-14:** O Valkey roda com `maxmemory-policy volatile-lru` e Postgres 18 com o volume em `/var/lib/postgresql`, como no `compose.dev.yaml` (STATE.md).
- **D-15:** O app no compose roda no profile **prod** (sem seed Liquibase de dev) e recebe `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_HOST`/`PORT`, `SPRING_MONGODB_URI`, `LINKPULSE_IP_HASH_KEY` e `LINKPULSE_BASE_URL` por env. `internal-proxies` continua vazio no compose (decisão 02-09).
- **D-16:** Os dashboards e o alerta de lag usam `max()` sobre os gauges `linkpulse_clicks_stream_pending/lag/length`, e lag NaN significa indefinido (decisão 02-08). p95/p99 saem de `histogram_quantile` sobre `http_server_requests_seconds_bucket`. Nenhuma métrica tem tag por código, URL ou IP (OBS-01).
- **D-17:** O Prometheus é `prom/prometheus:v3.15.0` e o Grafana é `grafana/grafana:13.2.3`, com provisioning por arquivo. Os mesmos JSONs viram ConfigMaps com o label `grafana_dashboard: "1"` no kind (CLAUDE.md).

### Claude's Discretion
Áreas não discutidas. O pesquisador e o planejador decidem a partir dos defaults da pesquisa:
- **Alertas e runbooks:** limiares (ex.: 5xx > 5% por 5 min, p99 > 500 ms por 5 min, lag/pendentes acima de um teto por 5 min, `up == 0` por 1 min), local dos runbooks (sugestão: `observability/runbooks/*.md` ou `docs/runbooks/`, vinculados pela annotation `runbook_url`). Sem Alertmanager: o requisito lista 6 serviços e o alerta só precisa aparecer como firing no Prometheus. Parar o container do app precisa disparar o target down.
- **Desenho dos dashboards:** dois dashboards (aplicação e pipeline) com painéis extras de baixo custo, se ajudarem (Valkey indisponível, 429 do rate limit, cliques perdidos/duplicados), UID fixo do datasource e da pasta, e refresh automático.
- **Estrutura de `observability/`:** subpastas para `prometheus/` (config, regras), `grafana/` (provisioning, dashboards) e k6 (ou `load/` na raiz). Uma fonte só, montada pelo compose sem cópias.
- **Detalhes do compose:** pin das imagens por tag ou digest, acesso anônimo de leitura ao Grafana, `depends_on: condition: service_healthy`, intervalos de scrape e de avaliação, retenção do Prometheus.
- **Saída do seed:** lista dos links criados e URL do Grafana no fim; comportamento ao rodar o seed duas vezes (aliases repetidos dão 409, que deve ser tolerado).

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CONT-01 | Dockerfile multi-stage com layered jar, usuário não-root numérico e HEALTHCHECK | Padrão 1 (Dockerfile verificado contra o jar real do Boot 4.1.1: `--launcher` existe, `layertools` foi removido, `JarLauncher` em `org/springframework/boot/loader/launch/`); Pitfalls 1, 2, 3 |
| CONT-02 | `make up` sobe app, Postgres, Mongo, Valkey, Prometheus e Grafana com dependências por healthcheck | Padrão 2 (compose com `name:` explícito, `depends_on: service_healthy`, `up -d --build --wait`); Pitfalls 4, 5, 6, 7 |
| CONT-04 | Script de seed/demo gera links e cliques para popular os dashboards | Padrão 6 (seed k6 com HEAD para conferir alias, UA de navegador, orçamento do rate limit); Pitfalls 9, 10, 11 |
| OBS-02 | Dashboard com taxa de requisições, p95/p99, cache hit ratio e cliques/min | Padrão 4 + catálogo de métricas verificado no código (seção "Métricas reais"); Pitfall 13 |
| OBS-03 | Dashboard de saúde do pipeline (lag, pendentes, descartados) | Padrão 4 (queries com `max()`, mapeamento NaN → "indefinido"); Pitfall 14 |
| OBS-04 | Alertas de 5xx, p99 alto, lag do consumer e target down, cada um com runbook | Padrão 3 (regras + `runbook_url` para o GitHub); Pitfalls 14, 15; `promtool test rules` |
| OBS-05 | Fonte única em `observability/`, usada pelo compose e pelo kind | Estrutura recomendada; UID `prometheus` igual ao default do kube-prometheus-stack; Pitfall 12 |
| LOAD-01 | k6 (redirect e criação) sem seguir redirects, com thresholds de p95/p99 e taxa de erro | Padrão 5 (`maxRedirects: 0`, `userAgent`, thresholds por `scenario`, aquecimento, orçamento do rate limit); Pitfalls 8, 9, 10, 11 |
</phase_requirements>

## Summary

A fase é quase toda configuração: um Dockerfile, um `compose.yaml`, arquivos do Prometheus e do Grafana, dois scripts k6, runbooks e alvos de make. Nenhum código Java precisa mudar. As métricas já existem (verificadas no código e no catálogo da 02-08), e os health groups `liveness`/`readiness` do Actuator ficam habilitados automaticamente no Boot 4.1.1, mesmo fora do Kubernetes. [CITED: docs.spring.io/spring-boot/reference/actuator/endpoints.html]

Os riscos ficam na integração, não na tecnologia. São seis: (1) o Grafana 13 passou a exportar dashboards no formato v2, que o provisioning por arquivo rejeita, então os JSONs devem ser escritos à mão no formato clássico; (2) os dois arquivos compose estão no mesmo diretório e por isso caem no mesmo projeto, a menos que cada um declare `name:`; (3) o Git Bash converte argumentos que começam com `/` quando chama o `docker.exe`; (4) o `pg_isready` sem `-h` dá "pronto" durante o servidor temporário do initdb; (5) o User-Agent padrão do k6 (`Grafana k6/<versão>`) contém `k6/`, que o app classifica como bot; (6) as criações do seed e do load somam na mesma janela fixa do rate limit.

**Primary recommendation:** Use um `compose.yaml` com `name: linkpulse` e um serviço `k6` sob `profiles: ["tools"]` (com `working_dir` e caminhos relativos, o que elimina os problemas de caminho no Windows). Escreva os dashboards à mão em JSON clássico com datasource `{"type":"prometheus","uid":"prometheus"}`. Valide regras com `promtool check config` e `promtool test rules` dentro da própria imagem do Prometheus.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Build e empacotamento da imagem | Build (Docker multi-stage) | — | O `./mvnw` roda dentro do stage JDK; o runtime JRE só recebe as camadas extraídas |
| Saúde do container | Container (HEALTHCHECK → Actuator 8081) | Compose (`depends_on: service_healthy`) | O HEALTHCHECK só tem efeito no compose (o Kubernetes ignora e usa probes na Phase 4) |
| Orquestração local e ordem de subida | Docker Compose | Makefile (invólucro fino) | O compose resolve os caminhos relativos e a rede; o make só nomeia os comandos |
| Coleta de métricas | Prometheus (pull de `app:8081`) | App (Micrometer, já pronto) | Nenhuma métrica nova é necessária |
| Regras de alerta | Prometheus (rule files) | Runbooks em Markdown no repositório | Sem Alertmanager: o estado firing aparece em `/alerts` |
| Visualização | Grafana (provisioning por arquivo) | — | Datasource e dashboards vêm do disco, sem clique manual |
| Dados de demonstração | k6 (container na rede do compose) | API pública (`POST /links`, `GET /{code}`, `HEAD /{code}`) | Nada é escrito direto nos bancos (D-04) |
| Teste de carga | k6 (container) | Dashboards (visão do servidor) | Sem remote write (D-11) |

## Project Constraints (from CLAUDE.md)

- Imagem de runtime `eclipse-temurin:21-jre-noble` (tem curl para o `HEALTHCHECK`). **Não usar distroless.** Usuário não-root numérico: `useradd -u 10001`, `USER 10001`.
- Build com `eclipse-temurin:21-jdk-noble` e o `./mvnw` do repositório, com `RUN --mount=type=cache,target=/root/.m2`.
- Extração com `java -Djarmode=tools -jar app.jar extract --layers --launcher` e ENTRYPOINT `java org.springframework.boot.loader.launch.JarLauncher`. `-Djarmode=layertools` é proibido.
- Docker Compose v2 (`docker compose`), nunca `docker-compose` v1.
- Prometheus `prom/prometheus:v3.15.0` com alertas em arquivo versionado; Grafana `grafana/grafana:13.2.3` com provisioning por arquivo; os mesmos JSONs viram ConfigMaps com o label `grafana_dashboard: "1"` no kind.
- `management.metrics.distribution.percentiles-histogram.http.server.requests=true` (já está no `application.yml`).
- k6 `grafana/k6:2.3.0`: usar só `k6 run` com `options.scenarios`; não seguir redirects no teste de `GET /{code}` e checar `status === 302`.
- Porta de gerenciamento separada (8081), exposição `health,info,prometheus`; liveness sem dependências externas.
- Imagens Bitnami são proibidas: usar imagens oficiais (`postgres:18.6`, `mongo:8.0`, `valkey/valkey:8.1`).
- Ações do GitHub fixadas por SHA (se esta fase tocar no CI).
- Lombok proibido (irrelevante aqui: não há código Java novo).
- Fluxo GSD: edições só dentro de `/gsd-execute-phase`.
- Memória do usuário: **nunca** adicionar `Co-Authored-By` nem "Generated with Claude Code" em commits.
- Documentação e comentários em pt-BR.

## Métricas reais (fonte das queries)

O catálogo abaixo foi conferido no código-fonte nesta sessão. Os nomes no formato Prometheus vêm do catálogo publicado pela 02-08, que fez scrape real. [VERIFIED: .planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-08-SUMMARY.md:135-149]

| Prometheus (verbatim do 02-08-SUMMARY) | Tags | Fonte no código |
|---|---|---|
| `linkpulse_cache_requests_total` | `result` = hit, miss, negative_hit, error, bypass | `LinkCache.java:53` `"linkpulse.cache.requests"`; `:93-97` `requests(registry, "hit")`, `"negative_hit"`, `"miss"`, `"error"`, `"bypass"` |
| `linkpulse_cache_errors_total` | `operation` = get, put, evict | `LinkCache.java:56` `"linkpulse.cache.errors"`; `:98-100` |
| `linkpulse_valkey_unavailable_total` | — | `ValkeyAvailability.java:29` `"linkpulse.valkey.unavailable"` |
| `linkpulse_clicks_publish_total` | `result` = published, rejected, failed | `ClickPublisher.java:79` `"linkpulse.clicks.publish"`; `:116-118` `publishCounter(registry, "published")`, `"rejected"`, `"failed"` |
| `linkpulse_clicks_publish_queue` | — (gauge) | `ClickPublisher.java:82` `"linkpulse.clicks.publish.queue"` |
| `linkpulse_clicks_persisted_total`, `_duplicates_total`, `_invalid_total`, `_claimed_total`, `_lost_total` | — | `ClickStreamConsumer.java:193-210` `"linkpulse.clicks.persisted"`, `"linkpulse.clicks.duplicates"`, `"linkpulse.clicks.invalid"`, `"linkpulse.clicks.claimed"`, `"linkpulse.clicks.lost"` |
| `linkpulse_clicks_consumer_errors_total` | — | `ClickStreamConsumer.java:202` `"linkpulse.clicks.consumer.errors"` |
| `linkpulse_clicks_consumer_batch_count` / `_sum` / `_max` | — | `ClickStreamConsumer.java:211` `"linkpulse.clicks.consumer.batch"` |
| `linkpulse_clicks_consumer_write_seconds_count` / `_sum` / `_max` | `outcome` = success, failure | `ClickStreamConsumer.java:229-231` `"linkpulse.clicks.consumer.write"`, `.tag("outcome", outcome)` |
| `linkpulse_clicks_stream_pending` | — (gauge do grupo: usar `max()`) | `ClickStreamConsumer.java:216` `"linkpulse.clicks.stream.pending"` |
| `linkpulse_clicks_stream_lag` | — (gauge do grupo: `max()`; `NaN` = indefinido) | `ClickStreamConsumer.java:222` `"linkpulse.clicks.stream.lag"` |
| `linkpulse_clicks_stream_length` | — (gauge: `max()`) | `ClickStreamConsumer.java:219` `"linkpulse.clicks.stream.length"` |
| `linkpulse_ratelimit_requests_total` | `result` = allowed, rejected, error | `RateLimiter.java:56` `"linkpulse.ratelimit.requests"`; `:97-99` `requests(registry, "allowed")`, `"rejected"`, `"error"` |
| `http_server_requests_seconds_bucket` / `_count` / `_sum` | `uri` (template), `method`, `status`, `outcome`, `exception`, `error` | `application.yml:126-130` `percentiles-histogram: http.server.requests: true` |

**Valores de `uri` para os filtros PromQL:**
- Redirect: `@GetMapping("/{code:[A-Za-z0-9_-]+}")` [VERIFIED: app/src/main/java/dev/linkpulse/link/RedirectController.java:60]. A 02-08 confirmou no scrape que o `uri` sai exatamente como esse template: "o `uri` do `http.server.requests` é o template da rota (`/{code:[A-Za-z0-9_-]+}`)" [VERIFIED: 02-08-SUMMARY.md:130]. Matcher PromQL: `uri="/{code:[A-Za-z0-9_-]+}"`.
- Criação: `@RequestMapping("/links")` + `@PostMapping` [VERIFIED: app/src/main/java/dev/linkpulse/link/LinkController.java:24,51]. Matcher: `uri="/links", method="POST"`.
- Stats: `@GetMapping("/links/{code:[A-Za-z0-9_-]+}/stats")` (vista por grep no `StatsController.java:47`; o arquivo não foi aberto) [ASSUMED no valor exato].

**Não existe tag `application`** nas métricas (o `application.yml` não define `management.metrics.tags`) [VERIFIED: app/src/main/resources/application.yml:118-130]. As queries não devem filtrar por `application`, `instance` nem `namespace`, para continuarem portáveis para o kind.

## Standard Stack

### Core (imagens; versões travadas por D-13/D-14/D-17)

| Imagem | Tag | Propósito | Verificação |
|---|---|---|---|
| `eclipse-temurin` | `21-jdk-noble` (build) / `21-jre-noble` ou `21.0.12.1_1-jre-noble` (runtime) | Build com o mvnw e runtime com curl | Tags existem no Docker Hub; `21-jre-noble` e `21.0.12.1_1-jre-noble` têm o mesmo digest `sha256:000fd431958b…` (atualizado em 2026-10-04) [VERIFIED: Docker Hub API] |
| `postgres` | `18.6` | Fonte da verdade dos links | Existe (2026-10-07) [VERIFIED: Docker Hub API] |
| `valkey/valkey` | `8.1` | Cache, stream, rate limit | Existe (2026-09-21) [VERIFIED: Docker Hub API] |
| `mongo` | `8.0` | `click_events` | Existe (2026-10-14) [VERIFIED: Docker Hub API] |
| `prom/prometheus` | `v3.15.0` | Scrape e regras | Existe (2026-09-25); o digest de `v3.15.0` é igual ao de `v3.15.0-busybox` (`sha256:efd719c99d83b060`), então a imagem padrão é a variante busybox (tem `wget`) [VERIFIED: Docker Hub API] |
| `grafana/grafana` | `13.2.3` | Dashboards | Existe (2026-09-29). Já saiu a **13.2.4** (2026-10-13), um patch dentro da mesma linha [VERIFIED: Docker Hub API] |
| `grafana/k6` | `2.3.0` | Seed e carga | Existe (2026-09-21); é a `latest` da linha v2 [VERIFIED: Docker Hub API] |

### Ferramentas do host

| Ferramenta | Uso | Notas |
|---|---|---|
| Docker Desktop (Engine ≥ 25) | Tudo | `--start-interval` do HEALTHCHECK exige Engine 25.0+ [CITED: docs.docker.com/reference/dockerfile/#healthcheck] |
| Docker Compose v2 (CLI do host: v5.5.1) | `make up` e os outros alvos | `--wait`, `profiles` e `start_interval` disponíveis |
| GNU make 4.4.1 (ezwinports) | Alvos | Já usado nas Phases 1 e 2 |

**Sem dependências novas de Maven, npm ou PyPI.** O k6 não precisa de jslib remota (veja Pitfall 11).

**Instalação:** nenhuma; todas as imagens são puxadas pelo `docker compose`.

## Package Legitimacy Audit

Esta fase não instala pacotes de registry de linguagem (npm/PyPI/Maven/crates). Os únicos artefatos externos são imagens de container, todas oficiais ou de organizações verificadas, e todas conferidas na API do Docker Hub nesta sessão. O seam `package-legitimacy` só cobre npm/pypi/crates e não se aplica.

| Artefato | Registry | Publicado | Fonte | Verdict | Disposition |
|---|---|---|---|---|---|
| `eclipse-temurin:21-jdk-noble`, `21-jre-noble` | Docker Hub (Official Image) | 2026-10-04 | github.com/adoptium/containers | OK | Approved |
| `postgres:18.6` | Docker Hub (Official Image) | 2026-10-07 | github.com/docker-library/postgres | OK | Approved (já usado) |
| `mongo:8.0` | Docker Hub (Official Image) | 2026-10-14 | github.com/docker-library/mongo | OK | Approved (já usado) |
| `valkey/valkey:8.1` | Docker Hub (org valkey) | 2026-09-21 | github.com/valkey-io/valkey-container | OK | Approved (já usado) |
| `prom/prometheus:v3.15.0` | Docker Hub (org prom) | 2026-09-25 | github.com/prometheus/prometheus | OK | Approved |
| `grafana/grafana:13.2.3` | Docker Hub (org grafana) | 2026-09-29 | github.com/grafana/grafana | OK | Approved |
| `grafana/k6:2.3.0` | Docker Hub (org grafana) | 2026-09-21 | github.com/grafana/k6 | OK | Approved |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                        host (127.0.0.1)
   navegador / avaliador ─────────┬───────────────┬────────────────┐
                                  │ :8080         │ :3000          │ :9090
                                  ▼               ▼                ▼
 make seed / make load ──► [k6 container] ─HTTP─► [app] ◄─scrape─ [prometheus] ◄─query─ [grafana]
 (docker compose run,       profile "tools"     │  :8080 API        app:8081         │  rules/*.yml   provisioning:
  working_dir /work)        BASE_URL=app:8080   │  :8081 actuator   /actuator/       │  (alertas)     datasource uid
                                                │  (só rede interna) prometheus      │                "prometheus" +
                         depends_on healthy ────┤                                   │                dashboards JSON
                                                ├─ JDBC ──► [postgres] (healthy: pg_isready -h 127.0.0.1)
                                                ├─ RESP ──► [valkey]   (healthy: valkey-cli ping)
                                                └─ Mongo ─► [mongo]    (healthy: mongosh ping)

 Fonte única (bind mounts read-only, sem cópia):
   observability/prometheus/{prometheus.yml, rules/}  ──► /etc/prometheus/...
   observability/grafana/provisioning/{datasources,dashboards}/ ──► /etc/grafana/provisioning/...
   observability/grafana/dashboards/*.json            ──► /etc/grafana/dashboards  (Phase 4: ConfigMaps)
   load/*.js, load/results/                           ──► /work (k6)
```

Fluxo principal da demonstração: `make up` (build + subida na ordem dos healthchecks) → `make seed` (k6 cria cerca de 12 links e cerca de 1.500 cliques) → o app publica no stream → o consumer grava no Mongo → os contadores sobem → o Prometheus raspa a cada 15 s → o Grafana mostra os painéis.

### Recommended Project Structure

```
.
├── compose.yaml                         # NOVO: stack completa (name: linkpulse)
├── compose.dev.yaml                     # existente (+ name: linkpulse-dev)
├── Makefile                             # + up down clean image seed load (+ obs-check opcional)
├── .gitignore                           # + .env
├── app/
│   ├── Dockerfile                       # NOVO: multi-stage
│   └── .dockerignore                    # NOVO: target/, IDE; NÃO excluir .mvn/ nem config/
├── observability/
│   ├── prometheus/
│   │   ├── prometheus.yml               # só compose (no kind entra o ServiceMonitor)
│   │   ├── rules/link-pulse-alerts.yml  # formato groups: (igual ao spec do PrometheusRule)
│   │   └── tests/link-pulse-alerts-test.yml  # promtool test rules
│   ├── grafana/
│   │   ├── provisioning/datasources/prometheus.yml   # só compose (kind usa o datasource do chart)
│   │   ├── provisioning/dashboards/link-pulse.yml    # provider de arquivo (só compose)
│   │   └── dashboards/
│   │       ├── link-pulse-overview.json # OBS-02 (compose e kind)
│   │       └── link-pulse-pipeline.json # OBS-03 (compose e kind)
│   └── runbooks/
│       ├── LinkPulseHigh5xxRate.md
│       ├── LinkPulseHighP99Latency.md
│       ├── LinkPulseClickConsumerLag.md
│       └── LinkPulseTargetDown.md
└── load/
    ├── lib/common.js                    # BASE_URL, UAs de navegador, referrers, helpers
    ├── seed.js                          # CONT-04
    ├── load.js                          # LOAD-01
    └── results/.gitkeep                 # load-summary.json versionável (D-11)
```

### Pattern 1: Dockerfile multi-stage (CONT-01)

**O que é verificado:** no jar real do projeto (Boot 4.1.1), `extract` aceita `--launcher` e `--layers`, as camadas são `dependencies`, `spring-boot-loader`, `snapshot-dependencies` e `application`, e `-Djarmode=layertools` foi removido. Saída colada desta sessão:

```
$ java -Djarmode=tools -jar target/link-pulse-0.1.0-SNAPSHOT.jar help extract
Options:
  --launcher                     Whether to extract the Spring Boot launcher
  --layers string list           Layers to extract
  --destination string           Directory to extract files to. ...
$ java -Djarmode=tools -jar target/link-pulse-0.1.0-SNAPSHOT.jar list-layers
dependencies
spring-boot-loader
snapshot-dependencies
application
$ java -Djarmode=layertools -jar target/link-pulse-0.1.0-SNAPSHOT.jar list
Error: Unsupported jarmode 'layertools'
```

A extração com `--layers --launcher` gerou `spring-boot-loader/org/springframework/boot/loader/launch/JarLauncher.class`, e `snapshot-dependencies/` ficou **vazio** (um `COPY` de diretório vazio funciona). [VERIFIED: execução local do jar `link-pulse-0.1.0-SNAPSHOT.jar`]

A documentação oficial 4.1.1 mostra a variante **sem** `--launcher` (`java -jar application.jar`) [CITED: docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html]. As duas funcionam. D-13 trava a variante com `--launcher` e `JarLauncher`.

```dockerfile
# syntax=docker/dockerfile:1
# ---------- build: compila com o mvnw do repositório (mesmo Maven do CI) ----------
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY --chmod=0755 mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY config/ config/
COPY src/ src/
# Cache do ~/.m2 (dependências e a distribuição do Maven baixada pelo wrapper) entre builds.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package \
 && cp target/link-pulse-*.jar /src/app.jar
RUN java -Djarmode=tools -jar /src/app.jar extract --layers --launcher --destination /src/extracted

# ---------- runtime: JRE com curl, usuário numérico ----------
FROM eclipse-temurin:21-jre-noble
LABEL org.opencontainers.image.source="https://github.com/Lucasllo/link-pulse"
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
# Da camada que menos muda para a que mais muda.
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
USER 10001:10001
EXPOSE 8080 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --start-interval=2s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health/readiness || exit 1
# Forma exec: o java é o PID 1 e recebe o SIGTERM (graceful shutdown do Boot).
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

- O jar sai como `target/link-pulse-0.1.0-SNAPSHOT.jar`, ao lado do `link-pulse-0.1.0-SNAPSHOT.jar.original` [VERIFIED: `ls app/target`]. O glob `link-pulse-*.jar` não casa o `.jar.original`.
- O `package` roda o Checkstyle (fase `validate`) [VERIFIED: app/pom.xml:137-144 `<phase>validate</phase>`], por isso o `config/` precisa estar no contexto. O SpotBugs só roda no `verify` e não entra no build da imagem.
- `curl` está na imagem JRE noble, segundo o Dockerfile da Adoptium [CITED: CLAUDE.md §Fontes, "Adoptium Dockerfile (21/jre/ubuntu/noble): confirma curl e wget"].
- Health groups `liveness`/`readiness`: "These health groups are automatically enabled." [CITED: docs.spring.io/spring-boot/reference/actuator/endpoints.html, Boot 4.1.1]. Nada no `application.yml` os desabilita (grep por `probes`/`liveness`/`readiness` em `app/` não achou nada). Por padrão, o readiness contém só o `readinessState` ("Spring Boot does not add other health indicators to these groups"), o que basta para a ordem de subida. O K8S-02 (Phase 4) acrescenta db/redis.

### Pattern 2: compose.yaml (CONT-02)

```yaml
# Stack completa do `make up`. Segredos abaixo são DEFAULTS DE DEMONSTRAÇÃO, não são segredos:
# sobrescreva por variável de ambiente ou .env (ignorado pelo git).
name: linkpulse

services:
  postgres:
    image: postgres:18.6
    environment:
      POSTGRES_DB: linkpulse
      POSTGRES_USER: linkpulse
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-linkpulse}   # não é segredo
    volumes: [pgdata:/var/lib/postgresql]                  # PGDATA do 18 fica em /var/lib/postgresql/18/docker
    healthcheck:
      # -h 127.0.0.1: por TCP, para não dar "pronto" no servidor temporário do initdb (só socket).
      test: ["CMD-SHELL", "pg_isready -h 127.0.0.1 -U linkpulse -d linkpulse"]
      interval: 5s
      timeout: 3s
      retries: 20
  valkey:
    image: valkey/valkey:8.1
    command: ["valkey-server", "--maxmemory", "256mb", "--maxmemory-policy", "volatile-lru"]
    healthcheck: { test: ["CMD", "valkey-cli", "ping"], interval: 5s, timeout: 3s, retries: 20 }
  mongo:
    image: mongo:8.0
    command: ["mongod", "--wiredTigerCacheSizeGB", "0.25"]   # opcional: amigável a laptop
    volumes: [mongodata:/data/db]
    healthcheck:
      test: ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping')"]
      interval: 5s
      timeout: 3s
      retries: 20

  app:
    build: ./app
    image: link-pulse:local          # tag fixa sem "latest" (a Phase 4 faz `kind load` dela)
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/linkpulse
      SPRING_DATASOURCE_USERNAME: linkpulse
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-linkpulse}
      SPRING_DATA_REDIS_HOST: valkey
      SPRING_DATA_REDIS_PORT: "6379"
      SPRING_MONGODB_URI: mongodb://mongo:27017/linkpulse
      LINKPULSE_IP_HASH_KEY: ${LINKPULSE_IP_HASH_KEY:-compose-demo-ip-hash-key-not-a-secret}
      LINKPULSE_BASE_URL: http://localhost:8080
      LINKPULSE_SELF_HOSTS: app       # opcional: impede link apontando para o próprio app na rede
    ports: ["127.0.0.1:8080:8080"]    # 8081 (actuator) NÃO é publicada
    stop_grace_period: 45s            # lifecycle 30s por fase; o default de 10s mata o lote em andamento
    depends_on:
      postgres: { condition: service_healthy }
      valkey: { condition: service_healthy }
      mongo: { condition: service_healthy }

  prometheus:
    image: prom/prometheus:v3.15.0
    # Substituir o command apaga o CMD da imagem: --config.file e --storage.tsdb.path precisam voltar.
    command:
      - --config.file=/etc/prometheus/prometheus.yml
      - --storage.tsdb.path=/prometheus
      - --storage.tsdb.retention.time=7d
    volumes:
      - ./observability/prometheus:/etc/prometheus:ro   # diretório (não arquivo único: ver Pitfall 7)
      - promdata:/prometheus
    ports: ["127.0.0.1:9090:9090"]
    healthcheck:
      test: ["CMD", "wget", "-q", "-O", "/dev/null", "http://localhost:9090/-/ready"]
      interval: 5s
      timeout: 3s
      retries: 20

  grafana:
    image: grafana/grafana:13.2.3
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:-admin}   # não é segredo
      GF_AUTH_ANONYMOUS_ENABLED: "true"
      GF_AUTH_ANONYMOUS_ORG_ROLE: Viewer
      GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH: /etc/grafana/dashboards/link-pulse-overview.json
      GF_ANALYTICS_REPORTING_ENABLED: "false"
      GF_ANALYTICS_CHECK_FOR_UPDATES: "false"
    volumes:
      - ./observability/grafana/provisioning/datasources:/etc/grafana/provisioning/datasources:ro
      - ./observability/grafana/provisioning/dashboards:/etc/grafana/provisioning/dashboards:ro
      - ./observability/grafana/dashboards:/etc/grafana/dashboards:ro
    ports: ["127.0.0.1:3000:3000"]
    depends_on:
      prometheus: { condition: service_healthy }
    healthcheck:
      test: ["CMD", "wget", "-q", "-O", "/dev/null", "http://localhost:3000/api/health"]
      interval: 5s
      timeout: 3s
      retries: 30

  # Ferramenta (não sobe no `up`): `docker compose run --rm k6 run seed.js`.
  k6:
    image: grafana/k6:2.3.0
    profiles: ["tools"]
    working_dir: /work
    volumes: ["./load:/work"]
    environment:
      BASE_URL: http://app:8080
    depends_on:
      app: { condition: service_healthy }

volumes:
  pgdata: {}
  mongodata: {}
  promdata: {}
```

- Precedência do nome do projeto: `-p` > `COMPOSE_PROJECT_NAME` > `name:` no arquivo > nome do diretório. Nomes válidos só têm "lowercase letters, decimal digits, dashes, and underscores" [CITED: docs.docker.com/compose/how-tos/project-name/]. "projeto 4" tem espaço, e os dois arquivos estão no mesmo diretório, então hoje caem no mesmo projeto. **Declare `name: linkpulse` no `compose.yaml` e `name: linkpulse-dev` no `compose.dev.yaml`.** Efeito colateral: os volumes do dev criados antes (`projeto4_pgdata` e `projeto4_mongodata`) ficam órfãos. São só dados de dev; a nota de execução pode citar `docker volume ls`.
- Portas: o dev publica 5432, 6379 e 27017 no loopback; a stack completa publica 8080, 3000 e 9090. Não há conflito entre as duas stacks. **Mas** `make run` (app no host) usa 8080/8081 e conflita com o app do compose se os dois estiverem rodando ao mesmo tempo. Basta documentar isso; D-05 só exige que funcionem em sequência.
- Serviço com profile alvo de `run`: "Compose runs that service regardless of whether its profile is activated", e "Only the targeted service (and any of its declared dependencies via `depends_on`) is started." [CITED: docs.docker.com/compose/how-tos/profiles/]. Por isso `make seed` com a stack parada sobe o que precisa e espera o app ficar healthy.
- Reaproveitar definições: **recomendo duplicar** os três serviços de dependência (cerca de 30 linhas) em vez de usar `extends` + `ports: !reset []`. `include` não serve, porque traria as portas publicadas do dev (contra D-06) e não permite trocar o `name:`. A duplicação é mais legível para o avaliador; um comentário pede que as tags fiquem iguais nos dois arquivos.
- Grafana sem volume de dados: ele é reconstruído a partir do provisioning a cada recriação (stateless). O Prometheus tem volume (`promdata`), então o histórico sobrevive ao `make down`, e o `make clean` apaga tudo.

### Pattern 3: Prometheus — config, regras e runbooks (OBS-04, OBS-05)

`observability/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s
rule_files:
  - /etc/prometheus/rules/*.yml
scrape_configs:
  - job_name: link-pulse          # o alerta TargetDown filtra por este job
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["app:8081"]
  - job_name: prometheus
    static_configs:
      - targets: ["localhost:9090"]
```

`observability/prometheus/rules/link-pulse-alerts.yml` (formato `groups:`, idêntico ao `spec` do `PrometheusRule`):

```yaml
groups:
  - name: link-pulse
    rules:
      - alert: LinkPulseHigh5xxRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5..", uri!~"/actuator.*"}[5m]))
            / sum(rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[5m])) > 0.05
        for: 5m
        labels: { severity: critical }
        annotations:
          summary: "Mais de 5% das respostas da API são 5xx"
          description: "Taxa de 5xx em {{ $value | humanizePercentage }} nos últimos 5 min."
          runbook_url: https://github.com/Lucasllo/link-pulse/blob/main/observability/runbooks/LinkPulseHigh5xxRate.md
      - alert: LinkPulseHighP99Latency
        expr: |
          histogram_quantile(0.99,
            sum by (le) (rate(http_server_requests_seconds_bucket{uri="/{code:[A-Za-z0-9_-]+}"}[5m]))
          ) > 0.5
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "p99 do redirect acima de 500 ms"
          description: "p99 do GET /{code} em {{ $value | humanizeDuration }}."
          runbook_url: https://github.com/Lucasllo/link-pulse/blob/main/observability/runbooks/LinkPulseHighP99Latency.md
      - alert: LinkPulseClickConsumerLag
        # Lag sozinho não basta: com o Mongo fora, o consumer continua lendo (lag ~0) e o PEL cresce.
        expr: max(linkpulse_clicks_stream_lag) > 1000 or max(linkpulse_clicks_stream_pending) > 1000
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "Consumer de cliques atrasado (lag ou pendentes acima de 1000)"
          description: "Valor atual: {{ $value }}."
          runbook_url: https://github.com/Lucasllo/link-pulse/blob/main/observability/runbooks/LinkPulseClickConsumerLag.md
      - alert: LinkPulseTargetDown
        # absent(): no kind a série `up` some quando o pod some; no compose ela vai a 0.
        expr: up{job="link-pulse"} == 0 or absent(up{job="link-pulse"})
        for: 1m
        labels: { severity: critical }
        annotations:
          summary: "Prometheus não consegue raspar o Link Pulse"
          description: "O target do job link-pulse está fora há mais de 1 min."
          runbook_url: https://github.com/Lucasllo/link-pulse/blob/main/observability/runbooks/LinkPulseTargetDown.md
```

- O remote do repositório é `https://github.com/Lucasllo/link-pulse.git` [VERIFIED: `git remote -v`]. O `runbook_url` aponta para o arquivo na `main`, que é clicável na UI do Prometheus e do Grafana.
- **Por que o alerta de lag usa `or` com pending:** o loop lê o lote (`readBatch`) e o processa dentro do mesmo `try`; uma falha de gravação vai para o backoff sem XACK [VERIFIED: app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java:310-339]. O ciclo seguinte lê entradas novas, então o lag (não lidas) fica baixo enquanto o pending (lidas sem ACK) cresce. As gauges são atualizadas no próprio loop (`refreshGaugesIfDue()`, linha 311), inclusive durante o backoff.
- `NaN > 1000` é falso, então lag indefinido não dispara alerta (coerente com D-16).
- Teste do critério 3: `docker compose stop app` faz a resolução DNS de `app` falhar, o scrape falha e `up{job="link-pulse"}=0`. Com 15 s de scrape, 15 s de avaliação e `for: 1m`, o alerta fica **firing em cerca de 1,5 min**. Para conferir: `curl -s http://127.0.0.1:9090/api/v1/alerts`. [ASSUMED: comportamento padrão do static_config com DNS falhando]
- Runbooks em pt-BR, um por alerta: significado, impacto, diagnóstico (queries, `docker compose logs app`, `docker compose ps`; e `kubectl` na Phase 4) e mitigação.

### Pattern 4: Grafana — provisioning e dashboards (OBS-02, OBS-03, OBS-05)

`observability/grafana/provisioning/datasources/prometheus.yml`:

```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    uid: prometheus            # MESMO UID do datasource padrão do kube-prometheus-stack (Phase 4)
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: false
    jsonData:
      timeInterval: 15s        # base do $__rate_interval (4 × scrape)
```

`observability/grafana/provisioning/dashboards/link-pulse.yml`:

```yaml
apiVersion: 1
providers:
  - name: link-pulse
    orgId: 1
    folder: Link Pulse
    folderUid: link-pulse
    type: file
    disableDeletion: true
    allowUiUpdates: false
    updateIntervalSeconds: 30
    options:
      path: /etc/grafana/dashboards
      foldersFromFilesStructure: false
```

- Campos do provider (`name`, `orgId`, `folder`, `folderUid`, `type: file`, `disableDeletion`, `updateIntervalSeconds`, `allowUiUpdates`, `options.path`, `foldersFromFilesStructure`) conferidos na documentação [CITED: grafana.com/docs/grafana/latest/administration/provisioning/]. Diretório padrão de provisioning na imagem: `GF_PATHS_PROVISIONING` → `/etc/grafana/provisioning` [CITED: grafana.com/docs/grafana/latest/setup-grafana/configure-docker/].
- O kube-prometheus-stack (main) usa `grafana.sidecar.datasources.uid: prometheus`, `name: Prometheus`, e o sidecar de dashboards usa `label: grafana_dashboard`, `labelValue: "1"` [CITED: raw.githubusercontent.com/prometheus-community/helm-charts/main/charts/kube-prometheus-stack/values.yaml]. Conferir de novo na versão 92.0.0 na Phase 4.
- Variáveis `GF_*`: o padrão é `GF_<SectionName>_<KeyName>` [CITED: configure-docker]. `GF_AUTH_ANONYMOUS_ENABLED` / `_ORG_ROLE` vêm da seção `[auth.anonymous]`, e `GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH` vem de `[dashboards] default_home_dashboard_path`. [ASSUMED: nomes de chave do grafana.ini; conferir subindo o container]

**Formato dos dashboards: JSON clássico escrito à mão.** No Grafana 13 o export pela UI passou a gerar o formato v2 por padrão, e o provisioning por arquivo rejeita esse formato com "dashboard appears to be in v2 format" (issue grafana/grafana#123607, reportada na 13.0.1). O formato clássico (objeto com `panels` e `schemaVersion`) é o que funcionava de forma confiável. [CITED: github.com/grafana/grafana/issues/123607] [ASSUMED: que o JSON clássico carrega na 13.2.3 sem feature toggles; conferir no log do Grafana e em `/api/search`]

Esqueleto mínimo de dashboard (repetir o padrão em cada painel):

```json
{
  "uid": "link-pulse-overview",
  "title": "Link Pulse — Aplicação",
  "schemaVersion": 41,
  "version": 1,
  "editable": false,
  "refresh": "10s",
  "time": { "from": "now-15m", "to": "now" },
  "tags": ["link-pulse"],
  "templating": { "list": [] },
  "panels": [
    {
      "id": 1, "type": "timeseries", "title": "Latência do redirect (p95/p99)",
      "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 },
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": { "defaults": { "unit": "s" }, "overrides": [] },
      "targets": [
        { "refId": "A", "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{uri=\"/{code:[A-Za-z0-9_-]+}\"}[$__rate_interval])))",
          "legendFormat": "p95" },
        { "refId": "B", "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri=\"/{code:[A-Za-z0-9_-]+}\"}[$__rate_interval])))",
          "legendFormat": "p99" }
      ]
    }
  ]
}
```

**Painéis recomendados (queries prontas):**

| Dashboard | Painel | PromQL |
|---|---|---|
| Aplicação | Requisições/s por rota e status | `sum by (method, uri, status) (rate(http_server_requests_seconds_count{uri!~"/actuator.*"}[$__rate_interval]))` |
| Aplicação | p95/p99 do redirect | `histogram_quantile(0.95\|0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/{code:[A-Za-z0-9_-]+}"}[$__rate_interval])))` |
| Aplicação | p95/p99 da criação | idem com `{uri="/links", method="POST"}` |
| Aplicação | Cache hit ratio (stat + série) | `sum(rate(linkpulse_cache_requests_total{result=~"hit\|negative_hit"}[$__rate_interval])) / sum(rate(linkpulse_cache_requests_total[$__rate_interval]))` (unidade `percentunit`) |
| Aplicação | Cliques/min | `sum(rate(linkpulse_clicks_persisted_total[$__rate_interval])) * 60` |
| Aplicação (extra) | 5xx/s | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[$__rate_interval]))` |
| Aplicação (extra) | 429 do rate limit | `sum(rate(linkpulse_ratelimit_requests_total{result="rejected"}[$__rate_interval]))` |
| Aplicação (extra) | Valkey indisponível | `sum(increase(linkpulse_valkey_unavailable_total[$__rate_interval]))` + `sum by (result) (rate(linkpulse_cache_requests_total{result=~"error\|bypass"}[$__rate_interval]))` |
| Pipeline | Lag (stat + série) | `max(linkpulse_clicks_stream_lag)`; value mapping especial `NaN` → "indefinido" |
| Pipeline | Pendentes | `max(linkpulse_clicks_stream_pending)` |
| Pipeline | Tamanho do stream / fila do publisher | `max(linkpulse_clicks_stream_length)`, `max(linkpulse_clicks_publish_queue)` |
| Pipeline | Descartados | `sum by (result) (increase(linkpulse_clicks_publish_total{result=~"rejected\|failed"}[$__rate_interval]))`, `sum(increase(linkpulse_clicks_lost_total[$__rate_interval]))`, `sum(increase(linkpulse_clicks_invalid_total[$__rate_interval]))` |
| Pipeline | Publicados vs gravados/s | `sum(rate(linkpulse_clicks_publish_total{result="published"}[$__rate_interval]))` vs `sum(rate(linkpulse_clicks_persisted_total[$__rate_interval]))` |
| Pipeline | Duplicados / reclaim / erros | `sum(increase(linkpulse_clicks_duplicates_total[…]))`, `…_claimed_total`, `…_consumer_errors_total` |
| Pipeline | Gravação de lote (média) | `sum by (outcome) (rate(linkpulse_clicks_consumer_write_seconds_sum[…])) / sum by (outcome) (rate(linkpulse_clicks_consumer_write_seconds_count[…]))` |

Decisão de modelagem: o hit ratio conta `negative_hit` como hit, porque é resposta servida pelo cache. Documentar essa escolha na descrição do painel.

### Pattern 5: k6 — teste de carga (LOAD-01)

Opções e fatos verificados na documentação do k6 (era v2):
- A opção global é **`maxRedirects`** (default `10`, env `K6_MAX_REDIRECTS`). Por requisição, o parâmetro é `redirects`: "The number of redirects to follow for this request. Overrides the global test option". `redirects: 0` **dentro de `options`** não é uma opção válida. [CITED: grafana.com/docs/k6/latest/using-k6/k6-options/reference/, grafana.com/docs/k6/latest/javascript-api/k6-http/params/]
- `userAgent` padrão: `Grafana k6/<K6_VERSION>` [CITED: k6-options/reference]. Ele contém `k6/`, que está nos `BOT_TOKENS` do app: `"libwww-perl", "httpie", "postmanruntime", "insomnia", "k6/"` [VERIFIED: app/src/main/java/dev/linkpulse/click/UserAgentClassifier.java:37-45]. **É obrigatório definir `userAgent` de navegador.**
- `summaryTrendStats` padrão: `avg,min,med,max,p(90),p(95)`, **sem p(99)**. Acrescente `p(99)` para o resumo e o JSON mostrarem o p99 [CITED: k6-options/reference].
- `setupTimeout` padrão: `60s` [CITED].
- `--summary-export`: "While this feature is not deprecated yet", a documentação recomenda `handleSummary()` [CITED]. Na v2.0.0, `--no-summary` virou `--summary-mode=disabled` e `--summary-mode=legacy` foi removido [CITED: github.com/grafana/k6/releases/tag/v2.0.0].
- Executor `externally-controlled` removido na v2; `constant-arrival-rate` continua [CITED: release v2.0.0].

**Orçamento do rate limit (resolve D-09):** a janela é fixa e alinhada ao epoch: a chave é `KEY_PREFIX + ipHasher.hash(clientIp, clock.instant()) + ":" + windowIndex(now, windowMillis)`, com `windowIndex = floor(nowMillis / windowMillis)`, e a recusa acontece só quando `count > limit` [VERIFIED: app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java:121-136,141-146]. O limite padrão é 20 por janela de 1 min (`linkpulse.ratelimit.limit=20`, `linkpulse.ratelimit.window=1m`) [VERIFIED: app/src/main/resources/application.yml:84-87]. Toda chamada a `POST /links` conta, inclusive as que terminam em 409 ou 400 (o interceptor roda antes do controller).

| Origem | Criações | Janela |
|---|---|---|
| Seed (1ª execução) | ≤ 12 (D-03: ≤ 15) | W0 |
| `setup()` do load | 5 | W0 no pior caso → total ≤ 17-20 ≤ 20: **passa** |
| Cenário `create` do load | 12/min, `startTime: '60s'` | sempre uma janela posterior a W0 (60 s depois do setup) → ≤ 13 por janela |

- **Por que `12/min` e não `15/min`:** 15/min com `constant-arrival-rate` pode dar 16 iterações numa janela desalinhada. 12 deixa margem e ainda mede a latência do POST.
- **Rede de segurança no `setup()`:** se um POST do setup receber 429 (por exemplo, `make seed` duas vezes seguidas e logo `make load`), dormir o `Retry-After` e tentar uma vez de novo. Use `setupTimeout: '90s'`. O 429 do setup não entra nos thresholds com tag de cenário.

```js
// load/load.js — esqueleto
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, BROWSER_UA, createLink } from './lib/common.js';

export const options = {
  maxRedirects: 0,                 // nunca seguir o 302 (mede a API, não o destino)
  userAgent: BROWSER_UA,           // o UA padrão "Grafana k6/x" conta como bot no app
  discardResponseBodies: true,     // o setup pede o corpo por requisição (responseType: 'text')
  setupTimeout: '90s',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    warmup:   { executor: 'constant-arrival-rate', exec: 'redirect', rate: 50,  timeUnit: '1s',
                duration: '30s', preAllocatedVUs: 10, maxVUs: 50 },
    redirect: { executor: 'constant-arrival-rate', exec: 'redirect', rate: 200, timeUnit: '1s',
                duration: '2m', startTime: '30s', preAllocatedVUs: 50, maxVUs: 200 },
    create:   { executor: 'constant-arrival-rate', exec: 'create', rate: 12, timeUnit: '1m',
                duration: '90s', startTime: '60s', preAllocatedVUs: 2, maxVUs: 5 },
  },
  thresholds: {
    'http_req_duration{scenario:redirect}': ['p(95)<50', 'p(99)<100'],
    'http_req_duration{scenario:create}':   ['p(95)<200'],
    'http_req_failed{scenario:redirect}':   ['rate<0.01'],
    'http_req_failed{scenario:create}':     ['rate<0.01'],
    'checks': ['rate>0.99'],
  },
};

export function setup() {
  // 5 links de destino público; createLink trata 429 com Retry-After + 1 nova tentativa.
  return { codes: [1, 2, 3, 4, 5].map((i) => createLink(`https://example.com/load/${i}`)) };
}

export function redirect(data) {
  const code = data.codes[Math.floor(Math.random() * data.codes.length)];
  const res = http.get(`${BASE_URL}/${code}`, { redirects: 0 });
  check(res, { 'redirect 302': (r) => r.status === 302 });
}

export function create() {
  const res = http.post(`${BASE_URL}/links`,
    JSON.stringify({ url: `https://example.com/k6/${Date.now()}-${__VU}-${__ITER}` }),
    { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'create 201': (r) => r.status === 201 });
}
```

- **Aquecimento:** o cenário `warmup` absorve o JIT da JVM. Os thresholds usam `{scenario:redirect}`, então o aquecimento não entra no p99. A tag `scenario` é uma tag de sistema que o k6 aplica sozinho. [ASSUMED: comportamento padrão das system tags; conferir no JSON do resumo]
- `http_req_failed` considera 302 e 201 como sucesso (faixa padrão de status esperados: 200-399) [ASSUMED: default de `expectedStatuses`].
- Total: cerca de 2,5 min (D-10). Cerca de 30 mil cliques de navegador entram no pipeline (D-12).
- Execução: `docker compose run --rm k6 run --summary-export=results/load-summary.json load.js`, gravando em `load/results/load-summary.json`, que é versionado (D-11). Prefira `--summary-export` a `handleSummary`, porque mantém o resumo nativo no terminal sem a jslib remota (Pitfall 11).

### Pattern 6: k6 — seed (CONT-04)

- `setup()`: para cada alias (cerca de 8, todos casando com `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$` [VERIFIED: app/src/main/java/dev/linkpulse/link/AliasPolicy.java:24]), faz **`HEAD /{alias}`** com `redirects: 0`. Se vier 302, o link já existe e o seed pula o POST; se vier 404, cria. HEAD não conta clique (REDIR-06) nem passa pelo rate limit. Depois cria cerca de 4 links com código gerado. Total ≤ 12 POSTs na primeira execução e ≤ 4 nas seguintes. Para o POST, use `responseCallback: http.expectedStatuses(201, 409)`, para tolerar a corrida no alias.
- Destinos **públicos** (`https://github.com/...`, `https://docs.spring.io/...`, `https://valkey.io/...`). Loopback, `localhost` (host da `base-url`) e os `self-hosts` são recusados pela `TargetHostPolicy` (decisão 01-08). Um link com `expiresAt` daqui a 30 dias mostra a funcionalidade.
- Cenário `clicks`: `constant-arrival-rate` de cerca de 30/s por 45 s (cerca de 1.350 cliques). A escolha do link é ponderada (estilo Zipf, para os tops ficarem visíveis). O `User-Agent` sai de uma lista de navegadores (Chrome/Windows, Safari/iPhone, Firefox/Linux, Edge/Windows, Chrome/Android, Safari/macOS), com cerca de 5% de `Googlebot` para mostrar a diferença com `?includeBots=true`. O `Referer` varia (news.ycombinator.com, google.com, t.co, linkedin.com, ou ausente → `(direct)`).
- `teardown(data)`: espera cerca de 3 s (o consumer grava em lote) e consulta `GET /links/{alias}/stats` de um link, que não tem rate limit. Depois faz `console.log` da lista de `shortUrl`, do total contado, de `http://localhost:3000` (Grafana) e da limitação D-04 ("cliques por dia mostra só hoje").
- Os UAs da lista **não podem** conter nenhum dos `BOT_TOKENS` (por exemplo `headlesschrome`, `java/`, `k6/`) [VERIFIED: UserAgentClassifier.java:37-45].

### Pattern 7: Makefile

```make
# Stack completa (compose.yaml). Sem make: docker compose up -d --build --wait
STACK := docker compose
# Git Bash: impede a conversão de argumentos /caminho quando o sh chama o docker.exe.
export MSYS_NO_PATHCONV := 1
export MSYS2_ARG_CONV_EXCL := *

up: ## Builda a imagem e sobe a stack completa, esperando os healthchecks
	$(STACK) up -d --build --wait
down: ## Para a stack completa (mantém os volumes)
	$(STACK) down
clean: ## Para a stack completa e apaga os volumes
	$(STACK) down -v --remove-orphans
image: ## Builda só a imagem local do app (link-pulse:local)
	$(STACK) build app
seed: ## Popula links e cliques de demonstração (k6 em container)
	$(STACK) run --rm k6 run seed.js
load: ## Teste de carga k6 (resumo em load/results/load-summary.json)
	$(STACK) run --rm k6 run --summary-export=results/load-summary.json load.js
```

- O `help` atual usa `%-10s`, e todos os alvos novos cabem [VERIFIED: Makefile:10-11].
- O CI já roda `make -n verify && make help` no Linux; os alvos novos precisam parsear lá.
- Alvo opcional `obs-check`: `$(STACK) run --rm --no-deps --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml` e `... test rules /etc/prometheus/tests/link-pulse-alerts-test.yml`. Os caminhos absolutos funcionam por causa do `MSYS_NO_PATHCONV`. [ASSUMED: `promtool` em `/bin` na imagem; conferir]

### Anti-Patterns to Avoid
- **Exportar o dashboard pela UI do Grafana 13 e commitar:** o export padrão é v2, rejeitado pelo provisioning por arquivo. Escreva e mantenha o JSON clássico à mão.
- **`${DS_PROMETHEUS}` / `__inputs` no JSON:** só funciona em import manual. Use o UID fixo `prometheus`.
- **Filtrar por `instance`, `job` ou `namespace` nos dashboards:** quebra no kind. Use agregações (`sum`, `max`).
- **`docker run -v "$(CURDIR)/load:/scripts"` no Makefile:** o caminho tem espaço e passa pela conversão do MSYS. Use o serviço `k6` do compose, que resolve caminhos relativos.
- **Prometheus dependendo do app (`depends_on`):** se o app não subir, a observabilidade também não sobe. Só o Grafana depende do Prometheus.
- **`image: link-pulse:latest`:** na Phase 4, a tag `latest` faz o Kubernetes usar `imagePullPolicy: Always` e falhar com imagem carregada via `kind load`. Use `link-pulse:local`.
- **Percentil no cliente (`management.metrics.distribution.percentiles`):** não agrega. Os histogramas já existem.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Esperar dependências subirem | Script `wait-for-it.sh` ou loop com sleep | `depends_on: condition: service_healthy` + `docker compose up --wait` | Nativo, com timeouts e código de saída |
| Gerar carga e dados de demo | bash + curl em loop | k6 (`constant-arrival-rate`, `setup`/`teardown`) | Taxa controlada, thresholds e resumo; sem depender do shell do host (D-01) |
| Validar regras/config do Prometheus | Subir a stack e olhar a UI | `promtool check config` + `promtool test rules` (na imagem do Prometheus) | Determinístico, roda em segundos, pega erro de PromQL e de YAML |
| Extrair camadas do jar | `unzip` + cópia manual de `BOOT-INF` | `-Djarmode=tools extract --layers --launcher` | Layout correto do loader e das camadas |
| Copiar dashboards para o compose | Script de sync/cópia | Bind mount read-only do diretório `observability/` | Fonte única (OBS-05) |
| Esperar o alerta para provar o critério 3 | Verificação visual | `curl -s 127.0.0.1:9090/api/v1/alerts` + checagem de `"state":"firing"` | Evidência reprodutível |

**Key insight:** quase tudo nesta fase é declarativo. Cada script próprio de espera, cópia ou carga vira mais um ponto de quebra no Windows (Git Bash, espaços no caminho, CRLF).

## Common Pitfalls

### Pitfall 1: `layertools` / `JarLauncher` no pacote antigo
**O que dá errado:** `Error: Unsupported jarmode 'layertools'` no build, ou `ClassNotFoundException` no start.
**Por que acontece:** Dockerfiles de tutorial anteriores ao Boot 3.2/3.3.
**Como evitar:** Padrão 1 (verificado no jar real nesta sessão).
**Sinais de alerta:** o build falha no stage de extração.

### Pitfall 2: `.dockerignore` mal feito
**O que dá errado:** sem `.dockerignore`, os 84 MB do `target/` vão para o contexto. Se ele excluir `.mvn/` ou `config/`, o wrapper ou o Checkstyle quebram o build.
**Como evitar:** excluir `target/`, `.idea/`, `*.iml`, `.vscode/` e logs; manter `.mvn/`, `config/`, `src/`, `pom.xml` e `mvnw`.

### Pitfall 3: bit executável e CRLF do `mvnw` no build a partir do Windows
**O que dá errado:** `Permission denied` ou `/bin/sh^M` no `RUN ./mvnw`.
**Como evitar:** `COPY --chmod=0755 mvnw ./`. O `.gitattributes` já força LF (`mvnw text eol=lf`) [VERIFIED: .gitattributes].

### Pitfall 4: projeto compose compartilhado entre `compose.yaml` e `compose.dev.yaml`
**O que dá errado:** sem `name:`, os dois arquivos no mesmo diretório viram o mesmo projeto. Os serviços `postgres`, `valkey` e `mongo` são **os mesmos containers e volumes**, e aparecem avisos de "orphan containers". Isso viola D-05.
**Como evitar:** `name: linkpulse` e `name: linkpulse-dev`. Um `COMPOSE_PROJECT_NAME` definido no ambiente sobrescreve os dois; documentar.

### Pitfall 5: `pg_isready` passa durante o initdb
**O que dá errado:** no primeiro `make up` (volume vazio), o entrypoint do Postgres sobe um servidor temporário só em socket Unix para rodar o init, e o `pg_isready` sem `-h` responde OK. O app (`depends_on: healthy`) tenta TCP, toma "connection refused" e o Liquibase derruba a subida.
**Como evitar:** `pg_isready -h 127.0.0.1 ...` (TCP). O `compose.dev.yaml` atual não tem `-h`; vale corrigir lá também. [ASSUMED: comportamento do entrypoint oficial; risco baixo, correção barata]

### Pitfall 6: `stop_grace_period` padrão de 10 s
**O que dá errado:** `docker compose down/stop` manda SIGKILL antes de o lote em andamento terminar. A reentrega via PEL recupera os cliques, mas o shutdown limpo (CLICK-05) não é demonstrado.
**Como evitar:** `stop_grace_period: 45s` no app e ENTRYPOINT em forma exec (java como PID 1).

### Pitfall 7: bind mount de arquivo único
**O que dá errado:** editores que salvam por "substituir arquivo" trocam o inode, e o container continua vendo a versão antiga do `prometheus.yml`.
**Como evitar:** montar **diretórios** (`./observability/prometheus:/etc/prometheus:ro`).

### Pitfall 8: `redirects: 0` no lugar errado e UA padrão do k6
**O que dá errado:** com `redirects: 0` em `options`, nada acontece (não é uma opção), e o k6 segue o 302 até o site de destino. Com o UA padrão `Grafana k6/x`, todos os cliques viram bot e somem do `/stats` padrão e da narrativa do D-12.
**Como evitar:** `maxRedirects: 0` global + `redirects: 0` por requisição + `userAgent` de navegador.

### Pitfall 9: rate limit somando seed + setup + create
**O que dá errado:** o load falha de forma intermitente com 429 no `create`.
**Como evitar:** o orçamento do Padrão 5 (`create` a 12/min com `startTime: '60s'`; setup com 5 POSTs e retry com `Retry-After`; seed ≤ 12 POSTs com o HEAD que evita POST repetido de alias).

### Pitfall 10: p99 do redirect contaminado pelo JIT
**O que dá errado:** os primeiros segundos com a JVM fria estouram `p(99)<100`.
**Como evitar:** cenário `warmup` separado e thresholds com `{scenario:redirect}`.

### Pitfall 11: `handleSummary` + jslib remota
**O que dá errado:** com `handleSummary` exportado, o k6 **não imprime** o resumo padrão. Para recuperar o texto, seria preciso importar `textSummary` de `https://jslib.k6.io/k6-summary/0.0.2/index.js` (rede em tempo de execução). O formato do objeto de resumo também mudou ("new machine-readable summary" introduzido na 1.5.0, anunciado como default na v2), e não está claro se a jslib 0.0.2 acompanha. [CITED: grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/]
**Como evitar:** `--summary-export=results/load-summary.json` (caminho relativo ao `working_dir`).

### Pitfall 12: conversão de caminhos do Git Bash (MSYS)
**O que dá errado:** um argumento como `/etc/prometheus/prometheus.yml` passado do sh do Git Bash para o `docker.exe` vira `C:/Program Files/Git/etc/...`. O make da ezwinports executa receitas pelo `sh` quando há metacaracteres, então o comportamento varia de alvo para alvo. [ASSUMED: comportamento conhecido do MSYS; conferir rodando os alvos no Git Bash]
**Como evitar:** caminhos relativos ao `working_dir` do serviço (seed/load) e `export MSYS_NO_PATHCONV := 1` / `MSYS2_ARG_CONV_EXCL := *` no Makefile.

### Pitfall 13: dashboard "No data" por UID, intervalo ou filtro
**O que dá errado:** datasource com UID gerado, `rate(...[1m])` com scrape de 30 s no kind, ou filtro por `job`/`instance`.
**Como evitar:** UID `prometheus`, `$__rate_interval` com `timeInterval: 15s` no datasource e nenhum filtro de alvo. Lint opcional: `grep` confirmando que todo `"uid"` de datasource nos JSONs é `prometheus` e que não existe `__inputs`.

### Pitfall 14: gauges do pipeline com semântica especial
**O que dá errado:** usar `sum()` em vez de `max()` (com várias réplicas no kind, o valor do grupo seria multiplicado) ou mostrar `NaN` cru no stat.
**Como evitar:** `max()` sempre (D-16) e value mapping `special: nan` → "indefinido".

### Pitfall 15: `up == 0` não dispara no Kubernetes
**O que dá errado:** quando o pod some, os endpoints do ServiceMonitor somem e a série `up` deixa de existir; `up == 0` nunca fica verdadeiro.
**Como evitar:** `up{job="link-pulse"} == 0 or absent(up{job="link-pulse"})`. Na Phase 4, o `job` do ServiceMonitor é o nome do Service por padrão, então o Service do chart deve se chamar `link-pulse` (ou o ServiceMonitor deve fixar `jobLabel`). Registrar como nota para a Phase 4.

### Pitfall 16: `docker compose run` sem TTY no Git Bash/mintty
**O que dá errado:** "the input device is not a TTY" ou barra de progresso do k6 inundando o terminal.
**Como evitar:** o compose detecta TTY automaticamente; se o problema aparecer, usar `run --rm -T`. [ASSUMED: conferir no terminal do autor]

### Pitfall 17: senha do Postgres alterada depois do primeiro `up`
**O que dá errado:** `POSTGRES_PASSWORD` só vale na inicialização do volume; mudar no `.env` depois quebra a autenticação do app.
**Como evitar:** documentar no comentário do compose: "para trocar a senha, `make clean`".

## Code Examples

Os exemplos completos estão nos Padrões 1 a 7 acima. Um exemplo adicional, o teste de unidade de regra (`observability/prometheus/tests/link-pulse-alerts-test.yml`):

```yaml
# Source: formato padrão do `promtool test rules` [ASSUMED: sintaxe; validar rodando o promtool]
rule_files:
  - ../rules/link-pulse-alerts.yml
evaluation_interval: 15s
tests:
  - interval: 15s
    input_series:
      - series: 'up{job="link-pulse", instance="app:8081"}'
        values: '1 1 0 0 0 0 0 0 0'
    alert_rule_test:
      - eval_time: 2m
        alertname: LinkPulseTargetDown
        exp_alerts:
          - exp_labels: { severity: critical, job: link-pulse, instance: "app:8081" }
            exp_annotations:
              summary: "Prometheus não consegue raspar o Link Pulse"
              description: "O target do job link-pulse está fora há mais de 1 min."
              runbook_url: https://github.com/Lucasllo/link-pulse/blob/main/observability/runbooks/LinkPulseTargetDown.md
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `-Djarmode=layertools` | `-Djarmode=tools extract --layers [--launcher]` | Deprecated no Boot 3.3; **removido** (verificado no 4.1.1) | O Dockerfile antigo quebra no build |
| `org.springframework.boot.loader.JarLauncher` | `org.springframework.boot.loader.launch.JarLauncher` | Boot 3.2 | `ClassNotFoundException` |
| Export "Classic" de dashboard no Grafana | Export v2 (resource) por padrão no Grafana 13; o provisioning por arquivo não aceita v2 sem apiVersion/metadata | Grafana 13.0 | JSON clássico escrito à mão |
| `--no-summary` / `--summary-mode=legacy` (k6) | `--summary-mode=disabled` / `compact` / `full` | k6 v2.0.0 | Scripts e flags antigos falham |
| `docker-compose` v1, chave `version:` | `docker compose` v2+; `version:` obsoleta | — | Não declarar `version:` |
| Distroless sem shell para Java | Temurin JRE noble (curl incluso) quando há `HEALTHCHECK` | Decisão do projeto | `HEALTHCHECK` viável |

**Deprecated/outdated:**
- ingress-nginx e charts Bitnami (CLAUDE.md): irrelevantes nesta fase, mas não devem aparecer no compose.
- `--summary-export` do k6: "not deprecated yet", mas desencorajado. Serve para esta fase; pode ser revisto se for removido numa v2.x futura.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | O Grafana 13.2.3 carrega JSON clássico (com `schemaVersion`, sem `apiVersion`) pelo provisioning por arquivo, com as feature toggles padrão | Padrão 4 | Dashboards não aparecem. Contingência: desligar as toggles `kubernetesDashboards`/`dashboardNewLayouts` por `GF_FEATURE_TOGGLES_DISABLE`, ou escrever no formato v2 com `apiVersion: dashboard.grafana.app/v2…` |
| A2 | A imagem `grafana/grafana:13.2.3` (padrão, diferente da `-ubuntu` e da `-distroless`) tem `wget` para o healthcheck | Padrão 2 | O healthcheck do Grafana falha. Contingência: `curl`, ou nenhum healthcheck no Grafana |
| A3 | Nomes `GF_AUTH_ANONYMOUS_ENABLED`, `GF_AUTH_ANONYMOUS_ORG_ROLE`, `GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH`, `GF_ANALYTICS_*` | Padrão 2 | Sem acesso anônimo ou sem home dashboard (não bloqueia a demo: login admin) |
| A4 | O k6 aplica a tag de sistema `scenario` a todas as métricas HTTP dos cenários (thresholds `{scenario:redirect}`), e `http_req_failed` trata 2xx/3xx como sucesso | Padrão 5 | Threshold que não casa nada é ignorado ou falha. Conferir no `load-summary.json` |
| A5 | O `--summary-export` continua existindo no k6 2.3.0 e grava em caminho relativo ao `working_dir`; o diretório `results/` precisa existir | Padrão 5 | O JSON não é gerado. Contingência: `handleSummary` só com o arquivo JSON |
| A6 | `docker compose stop app` → `up{job="link-pulse"}=0` (DNS falha) → firing em cerca de 1,5 min | Padrão 3 | O critério 3 exigiria outro desenho (pouco provável) |
| A7 | `pg_isready` sem `-h` dá falso positivo durante o initdb | Pitfall 5 | Só custo: `-h 127.0.0.1` é inofensivo de qualquer forma |
| A8 | Conversão de argumentos do MSYS e o uso do `sh` pelo make da ezwinports | Pitfall 12 | Alvos com caminho absoluto quebram no Git Bash. Mitigado pelos caminhos relativos e pelo `MSYS_NO_PATHCONV` |
| A9 | `promtool` em `/bin/promtool` na imagem do Prometheus; sintaxe do arquivo de `test rules` | Padrão 7 / Code Examples | O alvo `obs-check` precisa de ajuste |
| A10 | As requisições à porta de gerenciamento (8081) podem aparecer em `http_server_requests` com `uri="/actuator/…"`; por isso o filtro `uri!~"/actuator.*"` | Padrão 3/4 | Sem efeito se não aparecerem (o filtro é defensivo) |
| A11 | `uri` do `/stats` é `/links/{code:[A-Za-z0-9_-]+}/stats` (visto por grep, arquivo não aberto) | Métricas reais | Só afeta um painel opcional |

## Open Questions

1. **Grafana 13.2.3 ou 13.2.4?**
   - O que se sabe: D-17 trava a 13.2.3; a 13.2.4 saiu em 2026-10-13 (patch).
   - Recomendação: manter 13.2.3 (decisão travada). Se A1 falhar na 13.2.3, testar a 13.2.4 antes de mudar de formato.
2. **Digest ou tag nas imagens?**
   - O que se sabe: as tags existem; os digests foram coletados nesta sessão (tabela Standard Stack).
   - Recomendação: tag exata (legível) e Dependabot `docker-compose` + `docker` (`/app`) em `.github/dependabot.yml` para manter as versões atualizadas. Digest só se o planejador quiser reforçar supply chain.
3. **Mexer no `compose.dev.yaml` (`name:` e `pg_isready -h`)?**
   - Recomendação: sim, as duas mudanças são pequenas e cumprem D-05. Atualizar o README onde ele cita os volumes, se necessário.
4. **Validação no CI nesta fase?**
   - O que se sabe: CI-02..04 são da Phase 4.
   - Recomendação: opcional e barato, um job `docker compose config -q` + `promtool` (pela imagem). Se entrar, actions fixadas por SHA.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker CLI + Compose | Todos os alvos | ✓ | Compose v5.5.1 | — |
| Docker daemon (Docker Desktop) | `make up/seed/load/image` | ✗ **no momento da pesquisa** (`failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`) | — | Iniciar o Docker Desktop antes da execução; não há alternativa |
| GNU make | Alvos | ✓ | 4.4.1 (ezwinports) | Comandos `docker compose ...` equivalentes no README/Makefile |
| Java 21 (host) | Só verificações locais de jar (opcional) | ✓ | 21.0.10 | O build da imagem não precisa do Java do host |
| k6 (host) | — | ✗ | — | Desnecessário: roda no container `grafana/k6:2.3.0` (D-01) |
| Acesso à internet no build | Wrapper do Maven + dependências + pull de imagens | ✓ (Docker Hub respondeu) | — | Cache do BuildKit depois do primeiro build |

**Missing dependencies with no fallback:**
- Docker daemon parado: o executor precisa iniciar o Docker Desktop antes de qualquer tarefa de verificação.

**Missing dependencies with fallback:**
- k6 no host: usar o container (já é a decisão).

## Validation Architecture

Seção omitida: `workflow.nyquist_validation` é `false` em `.planning/config.json` [VERIFIED: .planning/config.json]. Verificações práticas recomendadas para o planejador usar como critérios das tarefas:
- `docker compose config -q` (sintaxe e interpolação do compose).
- `docker compose up -d --build --wait` com código de saída 0; `docker inspect --format '{{.Config.User}}' link-pulse:local` → `10001:10001`; `docker compose ps` mostra o app `(healthy)`.
- `promtool check config` e `promtool test rules` (alvo `obs-check`).
- `curl -s 127.0.0.1:3000/api/search?query=Link%20Pulse` lista os 2 dashboards (sem login, pelo acesso anônimo); `curl -s 127.0.0.1:3000/api/datasources/uid/prometheus` (precisa de admin) confirma o UID.
- `curl -s 127.0.0.1:9090/api/v1/targets` mostra `link-pulse` `up`; `/api/v1/rules` lista as 4 regras.
- `make seed` sem 429 → `/stats` com total > 0; `make load` com exit 0 (thresholds) e `load/results/load-summary.json` gerado.
- `docker compose stop app` → em cerca de 2 min, `/api/v1/alerts` contém `LinkPulseTargetDown` com `"state":"firing"`.

## Security Domain

`security_enforcement: true`, ASVS nível 1 [VERIFIED: .planning/config.json].

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Parcial (Grafana) | Admin do Grafana com senha por env (default de demo documentado); anônimo só `Viewer` |
| V3 Session Management | Não | — |
| V4 Access Control | Sim | Anônimo `Viewer` (nunca `Editor`/`Admin`); actuator 8081 sem porta publicada; bancos sem porta publicada |
| V5 Input Validation | Não (sem endpoint novo) | — |
| V6 Cryptography | Parcial | `LINKPULSE_IP_HASH_KEY` default de demo ≥ 16 caracteres, sobrescrevível; nunca uma chave "real" no repositório |
| V14 Configuration | Sim | Usuário não-root numérico; imagens oficiais com tag exata; portas só em `127.0.0.1`; `.env` no `.gitignore`; admin API e lifecycle do Prometheus **desligados** (sem `--web.enable-admin-api` / `--web.enable-lifecycle`) |

### Known Threat Patterns for stack local (compose)

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Serviços expostos na LAN (0.0.0.0) | Information Disclosure | `127.0.0.1:` em todas as portas publicadas; Postgres/Mongo/Valkey/8081 sem `ports` |
| Credenciais default confundidas com segredo real | Spoofing | Comentário "não é segredo" + override por `.env` ignorado pelo git |
| Container como root | Elevation of Privilege | `USER 10001:10001` |
| Prometheus `/-/quit`, `/api/v1/admin/*` sem autenticação | Tampering / DoS | Não habilitar `--web.enable-lifecycle` nem `--web.enable-admin-api` |
| Grafana anônimo com permissão de edição | Tampering | `GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer`, dashboards `editable: false`, provider `allowUiUpdates: false` |
| Supply chain (imagem/jslib) | Tampering | Imagens oficiais com tag exata (digest opcional); nenhuma jslib remota no k6 |
| Link de demo apontando para o próprio app na rede interna | Tampering (loop) | `LINKPULSE_SELF_HOSTS=app` (a `TargetHostPolicy` já recusa loopback/localhost) |
| Logs/painéis com dados pessoais | Information Disclosure | Métricas sem código/URL/IP (OBS-01, provado na 02-08); IP só como HMAC |

## Sources

### Primary (HIGH confidence)
- Código do repositório lido nesta sessão: `compose.dev.yaml`, `Makefile`, `app/pom.xml`, `application.yml`/`-prod`/`-dev`, `LinkCache.java`, `ValkeyAvailability.java`, `ClickPublisher.java`, `ClickStreamConsumer.java`, `RateLimiter.java`, `RedirectController.java`, `LinkController.java`, `LinkResponse.java`, `AliasPolicy.java`, `UserAgentClassifier.java`, `CreateLinkRequest.java`, `.planning/config.json`
- `.planning/phases/02-…/02-08-SUMMARY.md` §"Nomes finais das métricas" (scrape real)
- Execução local de `java -Djarmode=tools` sobre `app/target/link-pulse-0.1.0-SNAPSHOT.jar` (saída colada no Padrão 1)
- Docker Hub API (`hub.docker.com/v2/repositories/...`): existência, data e digest de todas as tags
- https://docs.spring.io/spring-boot/reference/actuator/endpoints.html (4.1.1): health groups e `add-additional-paths`
- https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html (4.1.1)
- https://raw.githubusercontent.com/prometheus/prometheus/main/Dockerfile: base busybox, `USER nobody`, CMD padrão
- https://docs.docker.com/reference/dockerfile/ (HEALTHCHECK, `--start-interval` exige Engine 25.0)
- https://docs.docker.com/compose/how-tos/profiles/ e https://docs.docker.com/compose/how-tos/project-name/

### Secondary (MEDIUM confidence)
- https://grafana.com/docs/grafana/latest/administration/provisioning/ e https://grafana.com/docs/grafana/latest/setup-grafana/configure-docker/
- https://github.com/grafana/grafana/issues/123607 (provisioning × formato v2 no Grafana 13)
- https://grafana.com/docs/grafana/latest/upgrade-guide/upgrade-v13.0/
- https://raw.githubusercontent.com/prometheus-community/helm-charts/main/charts/kube-prometheus-stack/values.yaml (UID `prometheus`, label do sidecar)
- https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/, https://grafana.com/docs/k6/latest/javascript-api/k6-http/params/, https://grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/
- https://github.com/grafana/k6/releases/tag/v2.0.0

### Tertiary (LOW confidence)
- Busca web sobre mudanças do k6 v2 em resumos (qaskills.sh, yrkan.com): não usadas como base de recomendação

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH. Todas as tags foram verificadas no registry; as versões são travadas por decisões anteriores.
- Container (Dockerfile): HIGH. As opções de extração e o layout foram verificados executando o jar real.
- Compose/Makefile: MEDIUM-HIGH. A documentação do Compose foi conferida; o comportamento do Git Bash/MSYS é ASSUMED (mitigado).
- Prometheus/alertas: HIGH nos nomes de métricas (código + scrape da 02-08); MEDIUM nos limiares (discrição, valores conservadores).
- Grafana: MEDIUM. O provisioning foi conferido, mas o Grafana 13 com JSON clássico precisa de confirmação na execução (A1).
- k6: MEDIUM-HIGH. As opções vêm da documentação da era v2; as system tags e o `--summary-export` na 2.3.0 são ASSUMED com verificação simples.
- Pitfalls: HIGH nos itens verificados no código (orçamento do rate limit, UA bot, lag × pending); MEDIUM nos de ambiente Windows.

**Research date:** 2026-10-14
**Valid until:** 2026-11-13 (30 dias; Grafana e k6 lançam patches com frequência)
