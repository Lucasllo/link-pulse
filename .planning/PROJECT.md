# Link Pulse

## O Que É

Link Pulse é um encurtador de URLs com analytics de cliques, construído como projeto de portfólio backend com ênfase em DevOps/cloud: deploy, observabilidade e infraestrutura como código. A API (sem frontend, sem autenticação) cria links curtos, redireciona com cache Redis e registra cliques de forma assíncrona no MongoDB para estatísticas. O público é recrutadores/avaliadores técnicos que vão ler o repositório, rodar `make up`/`make k8s` e olhar os dashboards.

## Valor Central

Um avaliador clona o repositório, roda `make up` (compose) ou `make k8s` (kind + Prometheus/Grafana) e vê um encurtador funcionando de ponta a ponta, com redirect rápido via cache e métricas reais nos dashboards versionados.

## Requisitos

### Validados

(Nenhum ainda — entregar para validar)

### Ativos

**API**
- [ ] `POST /links` recebe URL longa, alias opcional e expiração opcional e retorna o código curto
- [ ] Código curto gerado por Base62 a partir do ID da sequência do Postgres (sem colisão); alias customizado validado por unicidade
- [ ] `GET /{code}` responde 302 para a URL original, com lookup via cache Redis (hit/miss medido em métrica)
- [ ] Código inexistente → 404; link expirado → 410 Gone; TTL do cache respeita a expiração
- [ ] Clique registrado de forma assíncrona: o redirect publica evento em Redis Stream e um consumer grava no MongoDB em lote, sem atrasar o redirect
- [ ] `GET /links/{code}/stats` retorna total de cliques, cliques por dia e top referrers/user-agents (aggregation pipeline do MongoDB)
- [ ] Rate limiting simples por IP em `POST /links` (Redis), retornando 429

**Dados**
- [ ] PostgreSQL com Liquibase: changelogs YAML versionados e contexts (dev/prod)
- [ ] MongoDB para eventos de clique; Redis para cache, stream e rate limit

**Qualidade**
- [ ] Testes unitários do gerador de código (Base62)
- [ ] Testes de integração com Testcontainers (Postgres + Mongo + Redis)
- [ ] Análise estática com Checkstyle e SpotBugs no build
- [ ] Teste de carga com k6 e resultados documentados no README

**Container e Kubernetes**
- [ ] Dockerfile multi-stage com layered jar, usuário não-root e HEALTHCHECK
- [ ] `make up` sobe a stack local via Docker Compose (app, Postgres, Mongo, Redis, Prometheus, Grafana)
- [ ] Helm chart do app com Deployment, Service, Ingress, ConfigMap, Secret, HPA, liveness/readiness via Actuator; values para dev/prod
- [ ] `make k8s` cria cluster kind, instala dependências e o app via Helm, com kube-prometheus-stack (Prometheus/Grafana)

**CI/CD**
- [ ] GitHub Actions: build + testes (Testcontainers) + Checkstyle/SpotBugs + build da imagem + publicação no GHCR
- [ ] Job de verificação que faz deploy em kind e roda smoke test

**Observabilidade**
- [ ] Micrometer + Prometheus expondo métricas da aplicação (requisições, latência, cache hit/miss, cliques)
- [ ] Dashboards Grafana versionados em JSON: taxa de requisições, latência p95/p99, cache hit ratio, cliques/min
- [ ] Alertas básicos no Prometheus (ex.: taxa de erro 5xx, latência p99 alta, lag do consumer)

**AWS / Terraform**
- [ ] Terraform provisionando ECR, ECS Fargate, RDS (Postgres), ElastiCache (Redis) e rede necessária
- [ ] Terraform validado contra LocalStack e com `terraform plan` para AWS documentado (sem apply em conta real)

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

- **Stack**: Java 21, Spring Boot 3.x, Spring Data JPA, PostgreSQL + Liquibase, MongoDB, Redis — definido pelo autor
- **Infra local**: Docker Compose, kind (Helm), LocalStack — tudo precisa rodar sem conta cloud
- **Custo**: zero custo de AWS
- **Prazo**: ~1-2 semanas
- **Idioma**: documentação em pt-BR

## Decisões Importantes

| Decisão | Justificativa | Resultado |
|---------|---------------|-----------|
| Base62 do ID da sequência do Postgres | Sem colisão, determinístico e simples de testar | — Pendente |
| Redis Streams para eventos de clique | Assíncrono e durável sem infra nova; demonstra backpressure/lag | — Pendente |
| Helm chart (com values dev/prod) | Padrão de mercado; integra com kube-prometheus-stack | — Pendente |
| ECS Fargate como alvo AWS | Terraform mais leve que EKS e melhor suporte no LocalStack | — Pendente |
| Terraform via LocalStack + plan, sem apply | Custo zero mantendo IaC demonstrável | — Pendente |
| Imagem publicada no GHCR | Grátis, sem secrets AWS no CI | — Pendente |
| 404 para inexistente, 410 para expirado | Semântica HTTP correta | — Pendente |

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
*Última atualização: 2026-10-06 após a inicialização*
