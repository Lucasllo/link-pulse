# Phase 3: Stack local observável - Pattern Map

**Mapped:** 2026-10-14
**Files analyzed:** 25 (21 novos, 4 modificados)
**Analogs found:** 6 / 25. É uma fase de configuração: a maior parte dos arquivos (Dockerfile, Prometheus, Grafana, k6, runbooks) não tem análogo no repositório e deve seguir os Padrões 1 a 7 do `03-RESEARCH.md`.

Todos os caminhos de análogo abaixo foram conferidos com `git ls-files` (rastreados). O `.gsd/` na raiz não é rastreado e nunca deve ser citado.

## File Classification

| Arquivo novo/modificado | Role | Data Flow | Análogo mais próximo | Qualidade |
|---|---|---|---|---|
| `compose.yaml` (novo) | config (orquestração) | request-response / startup ordering | `compose.dev.yaml` | exact (serviços de dependência) |
| `compose.dev.yaml` (modificar: `name: linkpulse-dev`, `pg_isready -h 127.0.0.1`) | config | startup ordering | ele mesmo | exact |
| `Makefile` (modificar: `up down clean image seed load` + `obs-check` opcional) | config (build) | batch | `Makefile` | exact |
| `.gitignore` (modificar: `.env`) | config | — | `.gitignore` | exact |
| `README.md` (modificar: tabela de alvos, volumes do dev) | docs | — | `README.md` linhas 27-43 | exact |
| `.github/dependabot.yml` (opcional: `docker-compose` + `docker` em `/app`) | config | — | `.github/dependabot.yml` | exact |
| `app/Dockerfile` (novo) | config (build) | batch | nenhum | no analog -> RESEARCH Padrão 1 |
| `app/.dockerignore` (novo) | config | — | `.gitignore` (lista de exclusões) | partial |
| `observability/prometheus/prometheus.yml` | config | pull/scrape | `application.yml` 118-130 (lado do app) | partial |
| `observability/prometheus/rules/link-pulse-alerts.yml` | config (regras) | event-driven | nenhum | RESEARCH Padrão 3 |
| `observability/prometheus/tests/link-pulse-alerts-test.yml` | test | batch | nenhum | RESEARCH Code Examples |
| `observability/grafana/provisioning/datasources/prometheus.yml` | config | — | nenhum | RESEARCH Padrão 4 |
| `observability/grafana/provisioning/dashboards/link-pulse.yml` | config | file-I/O | nenhum | RESEARCH Padrão 4 |
| `observability/grafana/dashboards/link-pulse-overview.json` | config (dashboard) | transform (PromQL) | nenhum | RESEARCH Padrão 4 + tabela de painéis |
| `observability/grafana/dashboards/link-pulse-pipeline.json` | config (dashboard) | transform (PromQL) | nenhum | idem |
| `observability/runbooks/LinkPulseHigh5xxRate.md` (+3: `HighP99Latency`, `ClickConsumerLag`, `TargetDown`) | docs | — | `README.md` (tom/idioma pt-BR, blocos de comando) | partial |
| `load/lib/common.js` | utility | request-response | `UserAgentClassifier.java` (restrição de UA), `AliasPolicy.java` (regex do alias) | partial (restrições, não código) |
| `load/seed.js` | script (seed) | batch / request-response | nenhum | RESEARCH Padrão 6 |
| `load/load.js` | test (carga) | request-response | nenhum | RESEARCH Padrão 5 |
| `load/results/.gitkeep` | — | file-I/O | — | — |

## Pattern Assignments

### `compose.yaml` (config, startup ordering)

**Análogo:** `compose.dev.yaml` (55 linhas, lido inteiro)

**Cabeçalho comentado em pt-BR com o comando "sem make"** (linhas 1-4):
```yaml
# Dependências para rodar a API localmente (make db-up / make run): Postgres 18, Valkey 8.1 e
# MongoDB 8.0. Sem make: docker compose -f compose.dev.yaml up -d --wait
# Credenciais fixas só para desenvolvimento local; prod recebe SPRING_DATASOURCE_*,
# SPRING_DATA_REDIS_HOST/SPRING_DATA_REDIS_PORT e SPRING_MONGODB_URI por env.
```
Copiar o estilo, trocando por: `name: linkpulse`, "Sem make: docker compose up -d --build --wait" e o aviso "defaults de demonstração, não são segredos; sobrescreva por env ou .env".

**Serviços de dependência a duplicar** (linhas 6-50). Ajustes obrigatórios no `compose.yaml`: **remover todos os `ports:`** (D-06), `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-linkpulse}` (D-07), `pg_isready -h 127.0.0.1` (Pitfall 5), retries 20.
```yaml
  postgres:
    image: postgres:18.6
    environment:
      POSTGRES_DB: linkpulse
      POSTGRES_USER: linkpulse
      POSTGRES_PASSWORD: linkpulse
    # Postgres 18 mudou o PGDATA para /var/lib/postgresql/18/docker; monta-se o diretório pai.
    volumes:
      - pgdata:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U linkpulse -d linkpulse"]
      interval: 5s
      timeout: 3s
      retries: 10

  # Cache do redirect (Redis-compatível). volatile-lru só despeja chaves com TTL, como no ElastiCache.
  valkey:
    image: valkey/valkey:8.1
    command: ["valkey-server", "--maxmemory", "256mb", "--maxmemory-policy", "volatile-lru"]
    healthcheck:
      test: ["CMD", "valkey-cli", "ping"]
      ...
  mongo:
    image: mongo:8.0
    volumes:
      - mongodata:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping')"]
```
**Volumes nomeados** (linhas 52-54): `pgdata: {}`, `mongodata: {}`; acrescentar `promdata: {}`.

**Comentário de convenção:** cada bloco tem uma linha de comentário pt-BR explicando o porquê (ex.: linha 12 "Só no loopback..."). No `compose.yaml`, comentar: portas publicadas só em `127.0.0.1`, 8081 não publicada, `stop_grace_period: 45s`, "para trocar a senha, make clean" (Pitfall 17), e "manter as tags iguais às do compose.dev.yaml".

**Serviços novos** (`app`, `prometheus`, `grafana`, `k6` com `profiles: ["tools"]`): sem análogo; copiar o RESEARCH Padrão 2 (linhas 332-402). Env do app conforme D-15, e nomes de env conferidos no catálogo do `app/src/main/resources/application.yml` (comentários `(LINKPULSE_RATELIMIT_LIMIT, ...)`, linhas 84-87). Não sobrescrever `LINKPULSE_RATELIMIT_*` (D-03).

---

### `compose.dev.yaml` (modificação)

Duas mudanças mínimas: inserir `name: linkpulse-dev` antes de `services:` (linha 5) e trocar a linha 19 por `test: ["CMD-SHELL", "pg_isready -h 127.0.0.1 -U linkpulse -d linkpulse"]`. Atualizar o README linhas 34 e 43, que citam os volumes `pgdata`/`mongodata` (passam a ser `linkpulse-dev_pgdata`; os antigos `projeto4_*` ficam órfãos).

---

### `Makefile` (config, batch)

**Análogo:** `Makefile` (30 linhas)

**Variáveis + comentário "sem make?"** (linhas 3-6):
```make
# Sem make? Os mesmos comandos rodam direto: cd app && ./mvnw -B -ntp <goal>
# Dependências locais sem make: docker compose -f compose.dev.yaml up -d --wait (e ... down)
MVNW := cd app && ./mvnw -B -ntp
COMPOSE := docker compose -f compose.dev.yaml
```
Acrescentar `STACK := docker compose` com o comentário "Stack completa sem make: docker compose up -d --build --wait", e `export MSYS_NO_PATHCONV := 1` / `export MSYS2_ARG_CONV_EXCL := *` (Pitfall 12). Não renomear `COMPOSE`.

**`.PHONY` único** (linha 8): estender para `help build test verify run db-up db-down up down clean image seed load` (e `obs-check`, se entrar).

**Formato de alvo com `## descrição` em pt-BR** (linhas 25-29); o `help` (linha 11) usa `%-10s`, então os nomes devem ter até 10 caracteres (`obs-check` = 9, ok):
```make
db-up: ## Sobe as dependências locais do compose.dev.yaml e espera os healthchecks
	$(COMPOSE) up -d --wait

db-down: ## Para as dependências locais (mantém os volumes)
	$(COMPOSE) down
```
Receitas com TAB. Corpo dos alvos novos: RESEARCH Padrão 7. O CI roda `make -n verify && make help` (`.github/workflows/ci.yml:43`), então o Makefile precisa parsear no Linux.

---

### `.gitignore` (modificação)

Lista simples, um padrão por linha, sem comentários (linhas 1-6). Acrescentar `.env`. Não ignorar `load/results/` (o `load-summary.json` é versionado, D-11).

### `app/.dockerignore` (novo)

**Análogo parcial:** `.gitignore` (mesmas exclusões: `target/`, `.idea/`, `*.iml`, `.vscode/`, `*.log`, `.DS_Store`). **Não** excluir `.mvn/`, `config/` (Checkstyle roda no `package`, `app/pom.xml:137-144`), `src/`, `pom.xml`, `mvnw`.

---

### `README.md` (modificação)

**Análogo:** tabela de alvos, linhas 27-35. Formato a seguir em cada alvo novo:
```markdown
| Com make | Sem make (Git Bash, WSL, Linux, macOS) | O que faz |
| `make db-up` | `docker compose -f compose.dev.yaml up -d --wait` | Sobe as dependências locais ... e espera os healthchecks |
```
Nota de pré-requisito no estilo da linha 12 ("**Docker Desktop ligado.** ..."). Documentar: conflito de 8080/8081 entre `make run` e `make up` simultâneos, D-04 (cliques por dia só mostra hoje), `COMPOSE_PROJECT_NAME` sobrescreve `name:`.

---

### `.github/dependabot.yml` (opcional)

**Análogo:** o próprio arquivo; copiar o bloco `maven` (ecosystem + `directory` + `schedule.interval: weekly` + `open-pull-requests-limit: 5`) para `docker-compose` (`/`) e `docker` (`/app`). Comentário pt-BR acima de cada bloco, como o "# Mantém os pins por SHA das actions atualizados."

---

### `observability/prometheus/prometheus.yml` (config, scrape)

**Análogo parcial (lado do app):** `app/src/main/resources/application.yml` linhas 118-130:
```yaml
management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        # prometheus: /actuator/prometheus na porta de gerenciamento (8081), raspado pelo Prometheus.
        include: health,info,prometheus
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true
```
Daí: `metrics_path: /actuator/prometheus`, target `app:8081`, `job_name: link-pulse`. Não existe `management.metrics.tags` (sem tag `application`): queries não filtram por `application`/`instance`/`job` (exceto o alerta TargetDown). Corpo: RESEARCH Padrão 3.

### `observability/prometheus/rules/link-pulse-alerts.yml` e `tests/link-pulse-alerts-test.yml`

Sem análogo. Copiar RESEARCH Padrão 3 (linhas 438-481) e Code Examples (linhas 792-811). Nomes de métrica: tabela "Métricas reais" do RESEARCH (verificada contra `LinkCache.java`, `ClickPublisher.java`, `ClickStreamConsumer.java`, `RateLimiter.java`). Matcher do redirect: `uri="/{code:[A-Za-z0-9_-]+}"` (template de `RedirectController.java:60`); criação: `uri="/links", method="POST"` (`LinkController.java:24,51`). `runbook_url` com base `https://github.com/Lucasllo/link-pulse/blob/main/observability/runbooks/`.

### `observability/grafana/**` (provisioning e dashboards)

Sem análogo. Copiar RESEARCH Padrão 4 (datasource linhas 491-505, provider 507-523, esqueleto JSON 533-561, tabela de painéis 565-581). Regras fixas: JSON clássico escrito à mão (nunca export v2), datasource sempre `{"type":"prometheus","uid":"prometheus"}`, sem `__inputs`, `$__rate_interval`, `max()` nas gauges `linkpulse_clicks_stream_*` com value mapping NaN -> "indefinido", `editable: false`, UIDs `link-pulse-overview` / `link-pulse-pipeline`, pasta `folderUid: link-pulse`.

### `observability/runbooks/*.md`

**Análogo parcial:** `README.md` (pt-BR, frases curtas, comandos em blocos `bash`, ex. linha 63 `docker compose -f compose.dev.yaml exec mongo mongosh linkpulse --eval ...` e linha 74 `docker compose -f compose.dev.yaml stop valkey`). Estrutura por runbook: Significado, Impacto, Diagnóstico (PromQL + `docker compose logs app`/`ps`; nota de `kubectl` para a Phase 4), Mitigação. No runbook de lag, explicar lag x pending (`ClickStreamConsumer.java:310-339`).

---

### `load/lib/common.js`, `load/seed.js`, `load/load.js`

Sem análogo de código (não existe JS no repositório). Copiar RESEARCH Padrão 5 (linhas 606-652) e Padrão 6. Restrições extraídas do código Java que o JS precisa respeitar:

**User-Agent** - nenhum UA de navegador pode conter um dos tokens de `app/src/main/java/dev/linkpulse/click/UserAgentClassifier.java` linhas 36-43:
```java
static final List<String> BOT_TOKENS = List.of(
        "googlebot", "bingbot", ... "headlesschrome", "lighthouse",
        "crawler", "spider", "curl/", "wget/", "python-requests", ...
        "java-http-client", "java/", "apache-httpclient",
        "libwww-perl", "httpie", "postmanruntime", "insomnia", "k6/");
```
Por isso `options.userAgent` é obrigatório (o default `Grafana k6/x` contém `k6/`). O seed pode mandar ~5% de `Googlebot` de propósito.

**Alias** - todo alias do seed deve casar com o regex de `app/src/main/java/dev/linkpulse/link/AliasPolicy.java:24-26` (`^(?=.*[-_])[A-Za-z0-9_-]{4,32}$`: precisa de `-` ou `_`, 4-32 chars).

**Rate limit** - `application.yml:84-87`: 20 criações por janela fixa de 1 min por IP; 409/400 também contam. Orçamento: seed <= 12 POSTs (HEAD antes para pular aliases existentes), setup do load 5 POSTs com retry por `Retry-After`, cenário `create` 12/min com `startTime: '60s'`.

**Destinos** - só hosts públicos (a `TargetHostPolicy` recusa loopback, `localhost` e `LINKPULSE_SELF_HOSTS`).

**k6:** `maxRedirects: 0` global + `redirects: 0` por requisição, `summaryTrendStats` com `p(99)`, `--summary-export=results/load-summary.json` (sem `handleSummary` nem jslib remota), thresholds com `{scenario:redirect}` / `{scenario:create}`.

## Shared Patterns

### Comentários e idioma
**Fonte:** `compose.dev.yaml`, `Makefile`, `application.yml`
**Aplicar a:** todos os arquivos novos. Comentários em pt-BR, curtos, explicando o porquê (não o quê). Identificadores, chaves e PromQL em inglês.

### Portas só no loopback
**Fonte:** `compose.dev.yaml` linhas 12-14 (`"127.0.0.1:5432:5432"` + comentário "Só no loopback...")
**Aplicar a:** `compose.yaml` (somente 8080, 3000, 9090; nunca 8081 nem bancos).

### Healthcheck + espera
**Fonte:** `compose.dev.yaml` (interval 5s / timeout 3s) e `Makefile:26` (`up -d --wait`)
**Aplicar a:** todos os serviços do `compose.yaml` (`depends_on: condition: service_healthy`; `make up` = `up -d --build --wait`).

### Alvo de make com equivalente "sem make"
**Fonte:** `Makefile` linhas 3-4 e README linhas 27-35
**Aplicar a:** cada alvo novo (comentário no Makefile + linha na tabela do README).

### Fim de linha
**Fonte:** `.gitattributes` (`* text=auto eol=lf`, `mvnw text eol=lf`)
**Aplicar a:** Dockerfile, scripts k6, YAML: LF já garantido; no Dockerfile ainda usar `COPY --chmod=0755 mvnw ./`.

## No Analog Found

| Arquivo | Role | Data Flow | Motivo |
|---|---|---|---|
| `app/Dockerfile` | build config | batch | Primeiro Dockerfile do projeto -> RESEARCH Padrão 1 |
| `observability/prometheus/rules/*.yml`, `tests/*.yml` | regras/teste | event-driven | Sem Prometheus no repo -> Padrão 3 |
| `observability/grafana/**` | provisioning/dashboards | transform | Sem Grafana no repo -> Padrão 4 |
| `load/*.js` | seed/carga | request-response | Sem JS/k6 no repo -> Padrões 5 e 6 |

## Metadata

**Escopo da busca:** raiz do repositório (arquivos rastreados por `git ls-files`), `app/src/main/resources`, `app/src/main/java/dev/linkpulse/{click,link}`, `.github/`
**Arquivos lidos:** `compose.dev.yaml`, `Makefile`, `.gitignore`, `.gitattributes`, `.github/dependabot.yml`, trechos de `README.md`, `application.yml`, `UserAgentClassifier.java`, `AliasPolicy.java`, `ci.yml`
**Data:** 2026-10-14
