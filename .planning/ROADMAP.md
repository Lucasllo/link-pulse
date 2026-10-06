# Roadmap: Link Pulse

## Visão Geral

O Link Pulse é construído de dentro para fora. Primeiro vem a verdade do link no Postgres, com build, análise estática e CI verdes desde o primeiro commit. Em seguida, o caminho quente no Valkey (cache-aside, fail-open, rate limit) é entregue junto com o pipeline assíncrono de cliques (Redis Streams → consumer group → MongoDB → `/stats`), a parte de maior risco técnico, atacada cedo. Depois vêm a stack local observável (`make up`, dashboards e alertas versionados, seed e k6) e a orquestração em Kubernetes com entrega contínua (`make k8s`, GHCR, Trivy/SBOM e smoke test em kind no CI). Por fim, o Terraform AWS validado em camadas e o README pt-BR fecham a vitrine. Os módulos Terraform dependem só do contrato de runtime da Phase 3 (imagem, portas, env vars, health paths) e podem avançar em paralelo à Phase 4 se sobrar folga no prazo.

## Phases

**Numeração das fases:**
- Fases inteiras (1, 2, 3): trabalho planejado do milestone
- Fases decimais (2.1, 2.2): inserções urgentes (marcadas com INSERTED)

As fases decimais aparecem entre as inteiras vizinhas, em ordem numérica.

- [ ] **Phase 1: Fundação e núcleo de links** - Criar e redirecionar links a partir do Postgres, com Liquibase, qualidade estática e CI verdes desde o início
- [ ] **Phase 2: Hot path no Valkey e pipeline de cliques** - Cache-aside com fail-open, rate limit por IP, cliques assíncronos via Redis Streams → MongoDB e endpoint de estatísticas
- [ ] **Phase 3: Stack local observável** - Imagem do app, `make up` com os 6 serviços, dashboards e alertas versionados, seed de demonstração e teste de carga k6
- [ ] **Phase 4: Kubernetes no kind e entrega contínua** - Helm chart, `make k8s` com Traefik e kube-prometheus-stack, publicação no GHCR com Trivy/SBOM e smoke test em kind no CI
- [ ] **Phase 5: Terraform AWS e vitrine** - Módulos AWS validados em camadas (lint, `terraform test` com mock, LocalStack Hobby) e README pt-BR com evidências

## Phase Details

### Phase 1: Fundação e núcleo de links
**Goal**: Um cliente cria links curtos (gerados ou com alias) e é redirecionado para a URL original a partir do Postgres, num repositório que builda igual no Windows e no Linux, com CI verde desde o início
**Mode:** mvp
**Depends on**: Nada (primeira fase)
**Requirements**: LINK-01, LINK-02, LINK-03, LINK-04, LINK-05, LINK-06, REDIR-01, REDIR-02, DATA-01, QUAL-01, QUAL-03, QUAL-04, CONT-03, CI-01
**Success Criteria** (what must be TRUE):
  1. `POST /links` com uma URL válida retorna o código curto e a URL curta; códigos criados em sequência não são consecutivos nem enumeráveis, e criações concorrentes nunca geram código duplicado
  2. Um alias customizado válido é aceito; alias repetido retorna 409, e alias reservado (`links`, `actuator`) ou fora da regex, URL inválida (não http/https ou longa demais) e expiração no passado retornam erro de validação, sempre em Problem Details (RFC 9457)
  3. `GET /{code}` redireciona com 302 e `Cache-Control` que impede cache no navegador; código inexistente retorna 404 e link expirado retorna 410 Gone
  4. A aplicação sobe com o schema criado pelo Liquibase (changelogs YAML, contexts dev/prod) e com o Hibernate em `ddl-auto=validate`, e a Swagger UI (springdoc) documenta os endpoints
  5. Cada push/PR roda no GitHub Actions o build, os testes unitários do gerador (ida e volta, unicidade), os testes de integração com Testcontainers, o Checkstyle e o SpotBugs, falhando em qualquer violação; o mesmo build roda no Git Bash/WSL e no Linux, mesmo com espaço no caminho (LF via `.gitattributes`, `mvnw` executável, instrução de instalação do `make`)
**Plans**: TBD

### Phase 2: Hot path no Valkey e pipeline de cliques
**Goal**: Redirects saem do cache Valkey e continuam funcionando se ele cair; cada clique vira um evento processado de forma assíncrona e idempotente no MongoDB, e o cliente consulta estatísticas confiáveis de cada link
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: REDIR-03, REDIR-04, REDIR-05, REDIR-06, RATE-01, RATE-02, CLICK-01, CLICK-02, CLICK-03, CLICK-04, CLICK-05, CLICK-06, CLICK-07, CLICK-08, STAT-01, STAT-02, STAT-03, STAT-04, DATA-02, QUAL-02, OBS-01
**Success Criteria** (what must be TRUE):
  1. Redirects repetidos do mesmo código são servidos do cache Valkey, com TTL que nunca passa da expiração do link; um código que retornou 404 passa a redirecionar logo após ser criado (negative cache invalidado); `/actuator/prometheus` expõe hit/miss do cache e métricas de requisições, latência, cliques e pipeline, sem tags por código, URL ou IP
  2. Com o Valkey parado (`docker stop`), `GET /{code}` continua respondendo 302 via Postgres sem atraso perceptível; a falha do cache e os cliques descartados aparecem em métricas, e requisições HEAD nunca geram clique
  3. Depois de uma rajada de redirects, `GET /links/{code}/stats` retorna o total (com filtro `from`/`to`), os cliques por dia, os top referrers e os top navegadores/SO, deixando bots de fora por padrão e incluindo-os com `?includeBots=true`; no MongoDB, `click_events` tem os índices `{code, ts}` e TTL de retenção, e nenhum documento guarda IP em claro
  4. O pipeline não perde nem duplica cliques: a suíte de integração com Testcontainers (Postgres + Mongo + Valkey) cobre redirect, cache, rate limit e stats, e prova que o XACK só acontece após a gravação, que pendentes de um consumer morto são reprocessados sem duplicata, que o consumer segue vivo após uma queda do Mongo e que o shutdown conclui o lote em andamento; lag e pendentes aparecem como métricas
  5. `POST /links` acima do limite por IP retorna 429 com `Retry-After`, contando o IP real do cliente quando a requisição chega por um proxy confiável e ignorando `X-Forwarded-For` vindo de origem não confiável
**Plans**: TBD

### Phase 3: Stack local observável
**Goal**: Um avaliador roda `make up`, popula dados de demonstração e vê o encurtador funcionando com métricas reais em dashboards e alertas versionados, com o desempenho comprovado por teste de carga
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: CONT-01, CONT-02, CONT-04, OBS-02, OBS-03, OBS-04, OBS-05, LOAD-01
**Success Criteria** (what must be TRUE):
  1. `make up` sobe app, Postgres, Mongo, Valkey, Prometheus e Grafana na ordem dos healthchecks; a imagem do app é multi-stage com layered jar, roda com usuário não-root numérico e reporta HEALTHCHECK saudável
  2. Depois do script de seed/demo, o Grafana mostra, sem configuração manual, o dashboard com taxa de requisições, latência p95/p99, cache hit ratio e cliques/min, e o dashboard de saúde do pipeline com lag, pendentes e descartados
  3. O Prometheus carrega os alertas de taxa de 5xx, latência p99 alta, lag do consumer e target down, cada um com runbook vinculado; parar o container do app faz o alerta de target down disparar
  4. Dashboards, regras de alerta e datasource (UID fixo) vivem só em `observability/`, e o compose os carrega de lá, sem cópias, prontos para o kind reaproveitar
  5. O teste k6 (redirect e criação, sem seguir redirects) roda contra o compose e passa nos thresholds de p95/p99 e de taxa de erro
**Plans**: TBD

### Phase 4: Kubernetes no kind e entrega contínua
**Goal**: Um avaliador roda `make k8s` e obtém o mesmo encurtador observável num cluster kind (Helm, Traefik, kube-prometheus-stack), enquanto o CI publica uma imagem escaneada no GHCR e comprova o deploy com smoke test
**Mode:** mvp
**Depends on**: Phase 3
**Requirements**: K8S-01, K8S-02, K8S-03, K8S-04, CI-02, CI-03, CI-04
**Success Criteria** (what must be TRUE):
  1. `make k8s` cria o cluster kind e instala Traefik, metrics-server, kube-prometheus-stack, Postgres/Mongo/Valkey (manifests próprios com imagens oficiais) e o app via Helm; criar link e ser redirecionado funciona pelo Ingress a partir do host
  2. O Helm chart traz Deployment, Service, Ingress, ConfigMap, Secret e HPA, com values dev/prod; o pod só fica Ready com Postgres e Valkey acessíveis, e derrubar o Valkey ou o Mongo não reinicia o pod (liveness sem dependências externas)
  3. O Prometheus do cluster faz scrape do app via ServiceMonitor, e os mesmos dashboards e alertas de `observability/` aparecem no Grafana e no Prometheus do kind sem passo manual
  4. Na main, o GitHub Actions publica a imagem no GHCR (nome em minúsculas, pacote público), roda o scan do Trivy e gera o SBOM
  5. O job de verificação do CI sobe um cluster kind, faz o deploy e passa no smoke test criar link → redirect → stats
**Plans**: TBD

### Phase 5: Terraform AWS e vitrine
**Goal**: O avaliador encontra a infraestrutura AWS do app descrita em Terraform e validada em camadas a custo zero, e um README pt-BR que conta a história do projeto com evidências (diagrama, k6, dashboards e trade-offs)
**Mode:** mvp
**Depends on**: Phase 3, Phase 4 (os módulos Terraform dependem só do contrato de runtime da Phase 3 e podem avançar em paralelo à Phase 4; o README precisa do `make k8s` pronto)
**Requirements**: IAC-01, IAC-02, IAC-03, IAC-04, IAC-05, DOC-01, DOC-02
**Success Criteria** (what must be TRUE):
  1. Os módulos Terraform de rede, ECR, ECS Fargate + ALB, RDS Postgres, ElastiCache Valkey e secrets passam em `fmt`, `validate` e `tflint`, executados no CI a cada PR
  2. `terraform test` com `mock_provider "aws"` passa com asserts em todos os módulos
  3. Com um token do LocalStack Hobby, um alvo `make` aplica a parte suportada (rede, IAM, logs, secrets); no CI esse job roda só quando o secret existe e é pulado sem falhar quando não existe; o procedimento de `plan`/apply na AWS real está documentado passo a passo, sem ser executado
  4. O README em pt-BR traz diagrama de arquitetura, instruções de `make up` e `make k8s`, resultados do k6 e prints dos dashboards, e explica os trade-offs (302 vs 301, Valkey, coleção normal vs time-series, Terraform em camadas, escolha do Spring Boot, Mongo na AWS via DocumentDB/Atlas)
**Plans**: TBD

## Progress

**Ordem de execução:**
As fases seguem a ordem numérica: 1 → 2 → 3 → 4 → 5 (a parte Terraform da Phase 5 pode começar logo após a Phase 3)

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Fundação e núcleo de links | 0/TBD | Not started | - |
| 2. Hot path no Valkey e pipeline de cliques | 0/TBD | Not started | - |
| 3. Stack local observável | 0/TBD | Not started | - |
| 4. Kubernetes no kind e entrega contínua | 0/TBD | Not started | - |
| 5. Terraform AWS e vitrine | 0/TBD | Not started | - |
