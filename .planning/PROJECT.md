# Link Pulse

## What This Is

*O Que É*

Link Pulse é um encurtador de URLs com analytics de cliques, construído como projeto de portfólio backend com ênfase em DevOps/cloud: deploy, observabilidade e infraestrutura como código. A API (sem frontend, sem autenticação) cria links curtos, redireciona com cache Redis e registra cliques de forma assíncrona no MongoDB para estatísticas. O público é recrutadores/avaliadores técnicos que vão ler o repositório, rodar `make up`/`make k8s` e olhar os dashboards.

## Core Value

*Valor Central*

Um avaliador clona o repositório, roda `make up` (compose) ou `make k8s` (kind + Prometheus/Grafana) e vê um encurtador funcionando de ponta a ponta, com redirect rápido via cache e métricas reais nos dashboards versionados.

## Requirements

*Requisitos*

### Validados

- ✓ `POST /links` recebe URL longa, alias opcional e expiração opcional e retorna o código curto (com regras de URL/host e erros em Problem Details) — Phase 1
- ✓ Código curto gerado por Base62 a partir do ID da sequência do Postgres (permutação multiplicativa mod 62^7, sem colisão); alias customizado validado por unicidade — Phase 1
- ✓ Código inexistente → 404; link expirado → 410 Gone — Phase 1
- ✓ PostgreSQL com Liquibase: changelogs YAML versionados e contexts (dev/prod) — Phase 1
- ✓ Testes unitários do gerador de código (Base62) — Phase 1
- ✓ Análise estática com Checkstyle e SpotBugs no build — Phase 1
- ✓ `GET /{code}` responde 302 com cache-aside no Valkey (negative cache, hit/miss em métrica, fail-open com Valkey fora) — Phase 2
- ✓ TTL do cache respeita a expiração do link — Phase 2
- ✓ Clique assíncrono: redirect publica no stream `lp:clicks` (IP em HMAC diário, referrer reduzido ao host) e o consumer grava no MongoDB em lote, com reclaim, idempotência e shutdown ordenado — Phase 2
- ✓ `GET /links/{code}/stats` com total, cliques por dia, top referrers e famílias de navegador/SO (aggregation do MongoDB, bots excluídos por padrão) — Phase 2
- ✓ Rate limit por IP em `POST /links` (script Lua no Valkey, 429 + `Retry-After`, fail-open documentado) — Phase 2
- ✓ MongoDB para eventos de clique (TTL de retenção); Valkey para cache, stream e rate limit — Phase 2
- ✓ Testes de integração com Testcontainers (Postgres + Mongo + Valkey) — Phases 1-2
- ✓ Dockerfile multi-stage com layered jar, usuário não-root (10001) e HEALTHCHECK no readiness — Phase 3
- ✓ `make up` sobe a stack local via Docker Compose (app, Postgres, Mongo, Valkey, Prometheus, Grafana), portas só no loopback — Phase 3
- ✓ Micrometer + Prometheus: scrape de `/actuator/prometheus` (8081) pelo job `link-pulse` — Phases 2-3
- ✓ Dashboards Grafana versionados em JSON (aplicação e pipeline de cliques), anônimo Viewer, `make seed` com dados de demonstração pela API — Phase 3
- ✓ Alertas no Prometheus (5xx, p99 do redirect, lag/pendentes do consumer, TargetDown) com testes `promtool` (`make obs-check`) e runbooks — Phase 3
- ✓ Teste de carga com k6 (`make load`), thresholds por cenário e resumo versionado; resultados no README — Phase 3

### Ativos

**Container e Kubernetes**
- [ ] Helm chart do app com Deployment, Service, Ingress, ConfigMap, Secret, HPA, liveness/readiness via Actuator; values para dev/prod
- [ ] `make k8s` cria cluster kind, instala dependências e o app via Helm, com kube-prometheus-stack (Prometheus/Grafana)

**CI/CD**
- [ ] GitHub Actions: build + testes (Testcontainers) + Checkstyle/SpotBugs + build da imagem + publicação no GHCR
- [ ] Job de verificação que faz deploy em kind e roda smoke test

**AWS / Terraform**
- [ ] Terraform provisionando ECR, ECS Fargate, RDS (Postgres), ElastiCache (Redis) e rede necessária
- [ ] Terraform validado em camadas: `fmt`/`validate`/`tflint`; `terraform test` com `mock_provider "aws"` cobrindo todos os módulos; apply parcial no LocalStack Hobby (rede, IAM, logs, secrets) quando houver token; `plan` documentado (sem apply em conta real)

**Documentação**
- [ ] README em português com diagrama de arquitetura, instruções (`make up`, `make k8s`), resultados do k6 e prints dos dashboards

### Fora de Escopo

- Frontend — projeto é focado em backend/infra
- Autenticação de usuários — não agrega à vitrine DevOps e aumenta o escopo
- Apply real na AWS — custo; o Terraform fica validado via LocalStack + `plan`
- EKS — ECS Fargate é mais simples; Kubernetes já é demonstrado com kind
- Fila externa (Kafka/RabbitMQ/SQS) — Redis Streams resolve sem infra adicional
- Publicação da imagem no ECR pelo CI — exigiria credenciais AWS; ECR fica criado via Terraform e documentado

## Contexto

- Projeto de portfólio, escopo pequeno/moderado (~1-2 semanas)
- Toda documentação (README, docs, planejamento) em português do Brasil
- MongoDB na AWS não é provisionado como serviço gerenciado no escopo principal (DocumentDB/Atlas a documentar como opção)
- O ambiente de desenvolvimento é Windows; os scripts `make` devem funcionar em bash (Git Bash/WSL) e no runner Linux do CI

## Restrições

- **Stack**: Java 21, Spring Boot 4.1.x, Spring Data JPA, PostgreSQL + Liquibase, MongoDB, Valkey (protocolo Redis) — definido pelo autor; Boot 4.1 escolhido porque a linha 3.x perdeu suporte OSS em 2026-06-30
- **Infra local**: Docker Compose, kind (Helm + Traefik), LocalStack Hobby (token gratuito, opcional no CI) — tudo precisa rodar sem conta cloud paga
- **Custo**: zero custo de AWS
- **Prazo**: ~1-2 semanas
- **Idioma**: documentação em pt-BR

## Decisões Importantes

| Decisão | Justificativa | Resultado |
|---------|---------------|-----------|
| Base62 do ID da sequência do Postgres | Sem colisão, determinístico e simples de testar | ✓ Phase 1 (com permutação mod 62^7 para ofuscar a ordem) |
| Redis Streams para eventos de clique | Assíncrono e durável sem infra nova; demonstra backpressure/lag | ✓ Phase 2 (consumer próprio em lote, XCLAIM só confirma IDs ausentes do stream) |
| Helm chart (com values dev/prod) | Padrão de mercado; integra com kube-prometheus-stack | — Pendente |
| ECS Fargate como alvo AWS | Terraform mais leve que EKS (o LocalStack gratuito não cobre ECS nem EKS) | — Pendente |
| Terraform validado em camadas (lint + `terraform test` com mock + LocalStack Hobby parcial) | LocalStack gratuito não inclui ECS/RDS/ElastiCache/ECR desde 2026-03; custo zero | — Pendente |
| Imagem publicada no GHCR | Grátis, sem secrets AWS no CI | — Pendente |
| 404 para inexistente, 410 para expirado | Semântica HTTP correta | ✓ Phase 1 |
| Spring Boot 4.1.1 | Linha 3.x sem suporte OSS desde 2026-06-30; 4.1 suportado até 2027-07 | ✓ Phase 1 |
| Valkey 8.1 em todos os ambientes | Paridade com ElastiCache (Redis OSS parado na 7.1); protocolo compatível com Spring Data Redis | ✓ Phase 2 (compose e Testcontainers) |
| Traefik + manifests próprios no kind | ingress-nginx aposentado (2026-03) e catálogo gratuito Bitnami encerrado | — Pendente |
| Coleção Mongo normal `click_events` (não time-series) | Time-series não aceita índice único; idempotência usa `_id` = ID do stream | ✓ Phase 2 |
| Liquibase 5.0.3 (BOM do Boot 4.1.1), licença FSL-1.1-ALv2 | Versão gerenciada e testada pelo Boot 4.1; a FSL permite uso em portfólio; a alternativa Apache (4.33.0 por property) fica documentada no README | ✓ Registrada na Phase 1 |
| Checagem de host de destino sem DNS (`TargetHostPolicy`) + `LINKPULSE_SELF_HOSTS` | Resolver DNS no POST custa latência e é contornável por rebinding; loop via DNS fica como mitigação parcial documentada | ✓ Phase 1 |
| IP do clique como HMAC-SHA256 com chave + dia UTC | Sem PII crua no stream, no Mongo nem nas chaves de rate limit; rotação diária limita correlação | ✓ Phase 2 |
| Rate limit e cache em fail-open com Valkey fora (D-08) | Disponibilidade do redirect/criação vale mais que o limite; queda visível em métricas e no README | ✓ Phase 2 (limitação WR-01 aceita na UAT) |
| Sem limite de reentregas no consumer | Inválidas são confirmadas na conversão; falha de infraestrutura espera no PEL | ✓ Phase 2 |
| Grafana anônimo como Viewer, dashboards/datasource read-only por provisioning | Avaliador vê os painéis sem login e sem poder alterá-los | ✓ Phase 3 |
| Alertas sem Alertmanager, com runbook por `runbook_url` e testes `promtool` | Regras verificáveis no CI e no kind sem infra extra | ✓ Phase 3 |
| k6 com `redirects: 0` e `--summary-export` (sem jslib remoto) | Mede só a API e evita import remoto em runtime | ✓ Phase 3 (redirect p95 3,18 ms / p99 9,29 ms) |

## Evolução

Este documento evolui nas transições de fase e nos fechamentos de milestone.

**Após cada transição de fase** (via `/gsd-transition`):
1. Requisitos invalidados? → Mover para Fora de Escopo com o motivo
2. Requisitos validados? → Mover para Validados com referência da fase
3. Novos requisitos? → Adicionar em Ativos
4. Decisões a registrar? → Adicionar em Decisões Importantes
5. "O Que É" continua correto? → Atualizar se mudou

**Após cada milestone** (via `/gsd-complete-milestone`):
1. Revisão completa de todas as seções
2. Valor Central — continua sendo a prioridade certa?
3. Auditar Fora de Escopo — os motivos continuam válidos?
4. Atualizar Contexto com o estado atual

---
*Última atualização: 2026-10-17 após a Phase 3*
