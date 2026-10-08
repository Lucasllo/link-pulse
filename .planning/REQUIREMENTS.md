# Requisitos: Link Pulse

**Definido em:** 2026-10-06
**Valor Central:** Um avaliador clona o repositório, roda `make up` (compose) ou `make k8s` (kind + Prometheus/Grafana) e vê um encurtador funcionando de ponta a ponta, com redirect rápido via cache e métricas reais nos dashboards versionados.

## Requisitos v1

Requisitos da entrega inicial. Cada um é mapeado para uma fase do roadmap.

### Links (criação)

- [x] **LINK-01**: Cliente cria link via `POST /links` com URL longa (http/https, tamanho máximo validado) e recebe o código curto e a URL curta
- [x] **LINK-02**: Código gerado por Base62 a partir do ID da sequência do Postgres (obtido antes do INSERT), sem colisão
- [x] **LINK-03**: O ID é embaralhado de forma bijetiva (Sqids/permutação) para que códigos consecutivos não sejam enumeráveis
- [x] **LINK-04**: Cliente pode informar alias customizado; alias segue regex disjunta dos códigos gerados, respeita lista de palavras reservadas (`links`, `actuator`, etc.) e retorna 409 se já existir
- [x] **LINK-05**: Cliente pode informar expiração opcional (data futura obrigatória)
- [x] **LINK-06**: Erros de validação e conflito são retornados como Problem Details (RFC 9457)

### Redirect

- [x] **REDIR-01**: `GET /{code}` responde 302 para a URL original, com `Cache-Control` que impede cache no navegador
- [x] **REDIR-02**: Código inexistente retorna 404; link expirado retorna 410 Gone
- [ ] **REDIR-03**: Lookup usa cache-aside no Valkey com TTL = min(padrão, tempo até expirar), com negative caching (404/410) invalidado na criação do link
- [ ] **REDIR-04**: Hit/miss do cache é medido em métrica
- [ ] **REDIR-05**: Com o Valkey indisponível, o redirect continua funcionando via Postgres (fail-open) e o problema aparece em métrica
- [ ] **REDIR-06**: Requisições HEAD não contam clique

### Cliques (pipeline assíncrono)

- [ ] **CLICK-01**: Cada redirect publica um evento de clique em Redis Stream sem bloquear nem derrubar o redirect (timeout curto, fail-open, descarte contado em métrica, `MAXLEN ~`)
- [ ] **CLICK-02**: Consumer group lê em lote, grava no MongoDB (`click_events`, coleção normal) e faz XACK só após a escrita
- [ ] **CLICK-03**: Gravação é idempotente (`_id` = ID da mensagem do stream; duplicatas ignoradas)
- [ ] **CLICK-04**: Mensagens pendentes de consumers mortos são recuperadas (XPENDING/XCLAIM ou XAUTOCLAIM); o consumer não para após um erro
- [ ] **CLICK-05**: No shutdown, o lote em andamento é concluído antes de encerrar (graceful shutdown)
- [ ] **CLICK-06**: IP é armazenado apenas como hash com salt (nunca em claro)
- [ ] **CLICK-07**: Eventos de bots/crawlers são marcados com `isBot`; user-agent é parseado em navegador/SO
- [ ] **CLICK-08**: Lag e pendentes do consumer são expostos como métricas

### Estatísticas

- [ ] **STAT-01**: `GET /links/{code}/stats` retorna total de cliques (com filtro opcional `from`/`to`)
- [ ] **STAT-02**: Stats retornam cliques por dia (aggregation pipeline do MongoDB)
- [ ] **STAT-03**: Stats retornam top referrers e top navegadores/SO
- [ ] **STAT-04**: Bots ficam fora das stats por padrão; `?includeBots=true` os inclui

### Rate limiting

- [ ] **RATE-01**: `POST /links` é limitado por IP via Valkey (operação atômica), retornando 429 com `Retry-After`
- [ ] **RATE-02**: O IP real do cliente é obtido via forward headers apenas de proxies confiáveis (funciona atrás do Traefik)

### Dados e qualidade

- [x] **DATA-01**: Schema do Postgres gerenciado por Liquibase com changelogs YAML versionados e contexts dev/prod; Hibernate em `ddl-auto=validate`
- [ ] **DATA-02**: Índices do MongoDB (`{code, ts}` e TTL de retenção) criados explicitamente
- [x] **QUAL-01**: Testes unitários do gerador de código (Base62 + embaralhamento, ida e volta, unicidade)
- [ ] **QUAL-02**: Testes de integração com Testcontainers (Postgres + Mongo + Valkey), cobrindo redirect, cache, rate limit, pipeline de cliques (incluindo reentrega) e stats
- [x] **QUAL-03**: Checkstyle e SpotBugs rodam no build e quebram em violação
- [ ] **QUAL-04**: API documentada com OpenAPI/Swagger UI (springdoc)

### Container e ambiente local

- [ ] **CONT-01**: Dockerfile multi-stage com layered jar, usuário não-root numérico e HEALTHCHECK
- [ ] **CONT-02**: `make up` sobe via Docker Compose: app, Postgres, Mongo, Valkey, Prometheus e Grafana, com dependências por healthcheck
- [x] **CONT-03**: Repositório funciona em Windows (Git Bash/WSL) e Linux: `.gitattributes` com LF, `mvnw` executável, caminhos com espaço tratados, instrução de instalação do `make`
- [ ] **CONT-04**: Script de seed/demo gera links e cliques para popular os dashboards

### Kubernetes

- [ ] **K8S-01**: Helm chart do app com Deployment, Service, Ingress, ConfigMap, Secret e HPA, com values dev/prod
- [ ] **K8S-02**: Liveness e readiness via Actuator (liveness sem dependências externas; readiness com Postgres e Valkey)
- [ ] **K8S-03**: `make k8s` cria cluster kind e instala Traefik, metrics-server, kube-prometheus-stack, dependências (manifests próprios com imagens oficiais) e o app
- [ ] **K8S-04**: Prometheus faz scrape do app via ServiceMonitor; dashboards e regras são carregados automaticamente

### CI/CD

- [x] **CI-01**: GitHub Actions roda build, testes (Testcontainers), Checkstyle e SpotBugs em cada push/PR
- [ ] **CI-02**: Pipeline faz build da imagem e publica no GHCR (na main)
- [ ] **CI-03**: Imagem passa por scan do Trivy e é gerado um SBOM
- [ ] **CI-04**: Job de verificação faz deploy em kind e roda smoke test (criar link → redirect → stats)

### Observabilidade

- [ ] **OBS-01**: App expõe métricas Micrometer/Prometheus (requisições, latência, cache hit/miss, cliques, pipeline) sem tags de alta cardinalidade
- [ ] **OBS-02**: Dashboard Grafana versionado em JSON com taxa de requisições, latência p95/p99, cache hit ratio e cliques/min
- [ ] **OBS-03**: Dashboard de saúde do pipeline de cliques (lag, pendentes, descartados)
- [ ] **OBS-04**: Alertas Prometheus: taxa de 5xx, latência p99 alta, lag do consumer e target down, cada um com runbook
- [ ] **OBS-05**: Dashboards e regras vêm de uma fonte única (`observability/`), usada pelo compose e pelo kind

### AWS / Terraform

- [ ] **IAC-01**: Módulos Terraform para rede, ECR, ECS Fargate (+ ALB), RDS Postgres, ElastiCache Valkey e secrets
- [ ] **IAC-02**: `fmt`/`validate`/`tflint` passam e rodam no CI
- [ ] **IAC-03**: `terraform test` com `mock_provider "aws"` cobre todos os módulos com asserts
- [ ] **IAC-04**: Apply parcial no LocalStack Hobby (rede, IAM, logs, secrets) via `make`, rodando no CI quando o secret do token existir
- [ ] **IAC-05**: Procedimento de `plan`/apply na AWS real documentado (sem executar)

### Carga e documentação

- [ ] **LOAD-01**: Teste de carga k6 (redirect e criação) sem seguir redirects, com thresholds de p95/p99 e taxa de erro
- [ ] **DOC-01**: README em pt-BR com diagrama de arquitetura, instruções `make up`/`make k8s`, resultados do k6 e prints dos dashboards
- [ ] **DOC-02**: README documenta os trade-offs (302 vs 301, Valkey, coleção normal vs time-series, validação do Terraform em camadas, Boot 4.1, Mongo na AWS via DocumentDB/Atlas)

## Requisitos v2

Adiados. Registrados, mas fora do roadmap atual.

### Observabilidade

- **OBS2-01**: Logs JSON estruturados com ID de correlação
- **OBS2-02**: Tracing OpenTelemetry atravessando o stream
- **OBS2-03**: SLOs com alertas de burn-rate

### Produto

- **PROD-01**: GeoIP nos cliques
- **PROD-02**: Criação de links em lote
- **PROD-03**: Dead-letter stream para eventos inválidos

## Fora de Escopo

| Funcionalidade | Motivo |
|----------------|--------|
| Frontend | Projeto focado em backend/infra |
| Autenticação de usuários | Não agrega à vitrine DevOps; aumenta o escopo |
| PATCH/DELETE de links | Sem auth, qualquer um poderia sequestrar links alheios |
| Redirect 301 | Navegador cacheia e cliques repetidos não são contados |
| Expiração por número de cliques | Conflita com a contagem assíncrona |
| IP em claro | Privacidade/LGPD |
| Deduplicação de URL | Links diferentes para a mesma URL precisam de stats separadas |
| Stats em tempo real / exactly-once | Consistência eventual com at-least-once idempotente é suficiente |
| Apply real na AWS | Custo; validação em camadas substitui |
| EKS | ECS Fargate é mais simples; K8s já é demonstrado com kind |
| Fila externa (Kafka/RabbitMQ/SQS) | Redis Streams resolve sem infra adicional |
| Push da imagem para o ECR no CI | Exigiria credenciais AWS; ECR criado via Terraform e documentado |
| MongoDB gerenciado na AWS via Terraform | Fora do escopo; DocumentDB/Atlas apenas documentados |

## Traceability

Rastreabilidade: quais fases cobrem quais requisitos. Preenchido na criação do roadmap. O título da seção, os nomes das colunas e os valores de status ficam em inglês porque as ferramentas do GSD os leem literalmente (`Pending` → `In Progress` → `Complete`).

| Requirement | Phase | Status |
|-------------|-------|--------|
| LINK-01 | Phase 1 | Complete |
| LINK-02 | Phase 1 | Complete |
| LINK-03 | Phase 1 | Complete |
| LINK-04 | Phase 1 | Complete |
| LINK-05 | Phase 1 | Complete |
| LINK-06 | Phase 1 | Complete |
| REDIR-01 | Phase 1 | Complete |
| REDIR-02 | Phase 1 | Complete |
| REDIR-03 | Phase 2 | Pending |
| REDIR-04 | Phase 2 | Pending |
| REDIR-05 | Phase 2 | Pending |
| REDIR-06 | Phase 2 | Pending |
| CLICK-01 | Phase 2 | Pending |
| CLICK-02 | Phase 2 | Pending |
| CLICK-03 | Phase 2 | Pending |
| CLICK-04 | Phase 2 | Pending |
| CLICK-05 | Phase 2 | Pending |
| CLICK-06 | Phase 2 | Pending |
| CLICK-07 | Phase 2 | Pending |
| CLICK-08 | Phase 2 | Pending |
| STAT-01 | Phase 2 | Pending |
| STAT-02 | Phase 2 | Pending |
| STAT-03 | Phase 2 | Pending |
| STAT-04 | Phase 2 | Pending |
| RATE-01 | Phase 2 | Pending |
| RATE-02 | Phase 2 | Pending |
| DATA-01 | Phase 1 | Complete |
| DATA-02 | Phase 2 | Pending |
| QUAL-01 | Phase 1 | Complete |
| QUAL-02 | Phase 2 | Pending |
| QUAL-03 | Phase 1 | Complete |
| QUAL-04 | Phase 1 | Pending |
| CONT-01 | Phase 3 | Pending |
| CONT-02 | Phase 3 | Pending |
| CONT-03 | Phase 1 | Complete |
| CONT-04 | Phase 3 | Pending |
| K8S-01 | Phase 4 | Pending |
| K8S-02 | Phase 4 | Pending |
| K8S-03 | Phase 4 | Pending |
| K8S-04 | Phase 4 | Pending |
| CI-01 | Phase 1 | Complete |
| CI-02 | Phase 4 | Pending |
| CI-03 | Phase 4 | Pending |
| CI-04 | Phase 4 | Pending |
| OBS-01 | Phase 2 | Pending |
| OBS-02 | Phase 3 | Pending |
| OBS-03 | Phase 3 | Pending |
| OBS-04 | Phase 3 | Pending |
| OBS-05 | Phase 3 | Pending |
| IAC-01 | Phase 5 | Pending |
| IAC-02 | Phase 5 | Pending |
| IAC-03 | Phase 5 | Pending |
| IAC-04 | Phase 5 | Pending |
| IAC-05 | Phase 5 | Pending |
| LOAD-01 | Phase 3 | Pending |
| DOC-01 | Phase 5 | Pending |
| DOC-02 | Phase 5 | Pending |

**Cobertura:**

- Requisitos v1: 57 no total
- Mapeados em fases: 57
- Não mapeados: 0 ✓

**Por fase:**

- Phase 1: 14 requisitos
- Phase 2: 21 requisitos
- Phase 3: 8 requisitos
- Phase 4: 7 requisitos
- Phase 5: 7 requisitos

---
*Requisitos definidos em: 2026-10-06*
*Última atualização: 2026-10-06 após a criação do roadmap (rastreabilidade preenchida)*
