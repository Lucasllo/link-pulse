# Phase 3: Stack local observável - Context

**Gathered:** 2026-10-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Um avaliador roda `make up` e sobe, via Docker Compose, a imagem do app (multi-stage, layered jar, usuário não-root numérico, `HEALTHCHECK`) junto com Postgres, MongoDB, Valkey, Prometheus e Grafana, na ordem dos healthchecks. Um seed popula links e cliques de demonstração. O Grafana mostra, sem configuração manual, o dashboard da aplicação (taxa de requisições, p95/p99, cache hit ratio, cliques/min) e o de saúde do pipeline (lag, pendentes, descartados). O Prometheus carrega alertas de taxa de 5xx, p99 alto, lag do consumer e target down, cada um com runbook. Dashboards, regras e datasource (UID fixo) vivem só em `observability/`, prontos para o kind reaproveitar. Um teste k6 (redirect e criação, sem seguir redirects) roda contra o compose e passa em thresholds de p95/p99 e taxa de erro.

Requisitos: CONT-01, CONT-02, CONT-04, OBS-02, OBS-03, OBS-04, OBS-05, LOAD-01.

Fora desta fase: Helm/kind, GHCR, Trivy/SBOM e smoke test no CI (Phase 4); Terraform e README final com prints e resultados do k6 (Phase 5).

</domain>

<decisions>
## Implementation Decisions

### Seed/demo (discutido)
- **D-01:** O seed é um **script k6** executado pelo container `grafana/k6:2.3.0` na rede do compose, pelo alvo `make seed`. Ele reaproveita a ferramenta do teste de carga e dispensa bash e curl no host.
- **D-02:** O volume é **pequeno e rápido**: cerca de 10 a 15 links, alguns com alias legível, e de algumas centenas a poucos milhares de cliques em 30 a 60 s. Referrers, navegadores e SO variam para os tops do `/stats` e dos painéis ficarem interessantes. Os cliques usam **User-Agent de navegador**, porque clientes de linha de comando contam como bot desde a decisão 02-05.
- **D-03:** O seed **fica abaixo do rate limit**, com no máximo 15 criações. O compose mantém o default do app (`linkpulse.ratelimit.limit=20` por minuto) e o seed nunca deve receber 429.
- **D-04:** **Sem backfill**. Os cliques só têm o timestamp do momento (o `ts` é definido pelo servidor), então "cliques por dia" no `/stats` mostra só o dia atual. Nada é inserido direto no Mongo. A limitação vai documentada (README e saída do seed).

### Compose e `make up` (discutido)
- **D-05:** Um **`compose.yaml` novo na raiz** traz a stack completa do `make up`, de modo que `docker compose up` funcione sem `-f`. O `compose.dev.yaml` continua existindo para o `make run` (app no host). Reaproveitar definições entre os dois (`include`/`extends`/anchors) fica a critério do planejador. **As stacks não podem disputar portas nem volumes** ao rodar uma depois da outra.
- **D-06:** Ficam publicadas **só as portas de uso, todas em `127.0.0.1`**: app 8080, Grafana 3000 e Prometheus 9090. Postgres, Mongo, Valkey e a porta de gerenciamento 8081 ficam só na rede interna. O Prometheus raspa `app:8081/actuator/prometheus` por dentro da rede.
- **D-07:** Os segredos de demonstração são **defaults no próprio `compose.yaml`** no formato `${VAR:-default}` (`LINKPULSE_IP_HASH_KEY` com pelo menos 16 caracteres, senha do Postgres, admin do Grafana), comentados como "não é segredo". Quem quiser sobrescreve por env ou `.env`, e o `.env` entra no `.gitignore`. O `make up` funciona logo depois do clone, sem passo extra.
- **D-08:** Os alvos de make são `up`, `down` (mantém os volumes), `clean` (`down -v`), `image` (builda só a imagem local do app, reaproveitável no `kind load` da Phase 4), `seed` e `load` (os dois com k6 em container). `logs` e `ps` não foram pedidos.

### k6 e rate limit (discutido)
- **D-09:** O cenário de **criação roda abaixo do limite**: `constant-arrival-rate` de cerca de 15 por minuto, medindo a latência do `POST /links` sem mudar a config do app. A maior parte da carga vai para o **redirect**, o caminho quente sem rate limit. **Cuidado:** seed e load criam a partir do mesmo IP, e rodar `make load` menos de 1 minuto depois do `make seed` soma as criações na mesma janela. O planejador deve tratar isso (margem na taxa, espera inicial ou outra solução) para o teste não falhar de forma intermitente.
- **D-10:** O perfil é **moderado e conservador**: cerca de 2 a 3 min, redirect em torno de 200 req/s. Thresholds: redirect com **p95 < 50 ms e p99 < 100 ms**, criação com **p95 < 200 ms**, **taxa de erro < 1%**. O teste precisa passar num laptop com Docker Desktop. O redirect usa `redirects: 0` e checa `status === 302`.
- **D-11:** A saída do k6 é **só o resumo no terminal mais um JSON** (`handleSummary`/`--summary-export`) gravado num caminho versionável, para o README da Phase 5. **Sem remote write** no Prometheus: os dashboards já mostram a carga pelo lado do servidor.
- **D-12:** O redirect do k6 manda **User-Agent de navegador**. Os cliques da carga aparecem no `/stats` padrão e no painel de cliques/min, o que mostra o pipeline funcionando sob carga.

### Herdado de decisões anteriores (não rediscutir)
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Escopo e requisitos
- `.planning/ROADMAP.md` §Phase 3: objetivo e os 5 critérios de sucesso
- `.planning/REQUIREMENTS.md`: CONT-01, CONT-02, CONT-04, OBS-02..05, LOAD-01
- `.planning/PROJECT.md`: restrições (Boot 4.1.1, Valkey 8.1, pt-BR) e tabela de decisões
- `.planning/STATE.md` §Blockers/Concerns: `volatile-lru`, `LINKPULSE_IP_HASH_KEY` obrigatória, UA de navegador no seed e no k6

### Pesquisa e stack
- `.claude/CLAUDE.md`: versões fixadas (Temurin, Prometheus v3.15.0, Grafana 13.2.3, k6 2.3.0) e padrões do Dockerfile, do actuator e do k6 (`redirects: 0`)
- `.planning/research/STACK.md`: versões e padrões de container e observabilidade
- `.planning/research/ARCHITECTURE.md`: layout do repositório e fonte única de `observability/`
- `.planning/research/PITFALLS.md`: armadilhas de compose, healthcheck, Postgres 18 PGDATA, cardinalidade de métricas e k6
- `.planning/research/FEATURES.md`: dashboards, alertas e seed esperados por um avaliador

### Fases anteriores
- `.planning/phases/01-funda-o-e-n-cleo-de-links/01-CONTEXT.md`: contrato do POST /links, config `linkpulse.*`, Problem Details
- `.planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-CONTEXT.md`: contrato do `/stats`, métricas e decisões do pipeline

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `compose.dev.yaml`: definições prontas de Postgres 18.6, Valkey 8.1 (`volatile-lru`, 256mb) e Mongo 8.0 com healthchecks (`pg_isready`, `valkey-cli ping`, `mongosh ping`), que servem de base para o `compose.yaml`.
- `Makefile`: padrão `## comentário` para o `help`, variáveis `MVNW` e `COMPOSE`, comentário "sem make?" com os comandos equivalentes. Os novos alvos seguem esse padrão.
- `app/src/main/resources/application.yml`: catálogo das env vars (`LINKPULSE_*`, `SPRING_*`), `management.server.port=8081`, exposição `health,info,prometheus` e histograma de `http.server.requests`.
- `app/mvnw` e `app/.mvn/wrapper`: o build da imagem usa o wrapper (contexto de build em `app/`).

### Established Patterns
- Métricas expostas (prefixo `linkpulse_` no Prometheus): `cache.requests{result}`, `cache.errors`, `valkey.unavailable`, `clicks.publish`, `clicks.publish.queue`, `clicks.persisted`, `clicks.duplicates`, `clicks.invalid`, `clicks.lost`, `clicks.claimed`, `clicks.consumer.*`, `clicks.stream.pending/lag/length`, `ratelimit.requests{result}`, além de `http.server.requests`. Conferir os nomes exatos e os sufixos em `/actuator/prometheus` antes de escrever as queries.
- Comentários e documentação em pt-BR; configs inválidas derrubam a subida (ex.: `LINKPULSE_IP_HASH_KEY` com menos de 16 caracteres).
- Health: liveness e readiness do actuator na 8081 (`/actuator/health/liveness`, `/readiness`); o `HEALTHCHECK` da imagem usa curl na 8081.

### Integration Points
- O CI (`.github/workflows/ci.yml`) ainda não builda imagem; a Phase 4 acrescenta GHCR. O `make image` desta fase define a tag local usada depois.
- A estrutura de `observability/` é consumida na Phase 4 (ConfigMaps do kube-prometheus-stack) e precisa de datasource com UID fixo.
- O contrato do `/stats` (02-07) é consumido pelo seed para conferir os cliques, opcionalmente.

</code_context>

<specifics>
## Specific Ideas

- A demonstração ao vivo para o avaliador é `make up` → `make seed` → abrir o Grafana em `localhost:3000` e ver os painéis com dados, sem nenhum clique de configuração.
- Os thresholds do k6 precisam ser folgados o bastante para passar numa máquina comum: o teste prova a arquitetura, não um benchmark.
- A taxa de criação do k6 (~15/min) e o seed (≤ 15 links) existem para nunca encostar no rate limit padrão. O compose mostra o comportamento "de produção".

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-stack-local-observ-vel*
*Context gathered: 2026-10-14*
