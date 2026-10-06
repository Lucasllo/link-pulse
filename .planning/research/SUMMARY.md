# Resumo da Pesquisa do Projeto

**Projeto:** Link Pulse (encurtador de URLs com analytics de cliques, portfólio backend/DevOps)
**Domínio:** API Java/Spring + Postgres + MongoDB + Redis, com entrega via Docker Compose, kind/Helm, Terraform/AWS, CI e observabilidade
**Pesquisado em:** 2026-10-06
**Confiança:** ALTA para versões e restrições de licenciamento; MÉDIA para padrões de design

---

## Decisões pendentes do autor (resolver antes da Fase 1)

A pesquisa achou fatos de 2026 que contradizem premissas do PROJECT.md. O roadmap precisa registrar cada decisão.

| # | Decisão | Fato | Recomendação |
|---|---------|------|--------------|
| 1 | **Spring Boot 3.5.16 vs 4.1.x** | O suporte OSS da linha 3.5 acabou em 2026-06-30, e a 3.5.16 é a última versão. A 4.1.x tem suporte até 2027-07-31. | **Migrar para 4.1.1.** Se o autor mantiver "3.x", fixar 3.5.16 e explicar a escolha no README. Essa decisão afeta Jackson, Testcontainers 2, starters e Liquibase: o Boot 4 gerencia a Liquibase 5.x, que usa licença FSL; o Boot 3.5 usa a 4.31.1, com licença Apache. |
| 2 | **Escopo de "Terraform validado contra LocalStack"** | Desde 2026-03 o LocalStack exige conta e `LOCALSTACK_AUTH_TOKEN`. O plano gratuito (Hobby) **não inclui ECS, ECR, RDS nem ElastiCache**. | **Reescrever o requisito como validação em camadas:** (a) `fmt`/`validate`/`tflint` em todo PR; (b) `terraform test` com `mock_provider "aws"` cobrindo todos os módulos, com `assert`; (c) `apply` parcial no LocalStack Hobby (rede, Secrets Manager, IAM e logs), que exige um token gratuito e roda no CI só se o secret existir. O `plan` contra a AWS real exige credenciais, então deve ser documentado como passo manual ou gerado com endpoints locais. Plano B: Moto server, com confiança BAIXA. |
| 3 | **Ingress e dependências no kind** | O controller ingress-nginx foi aposentado em mar/2026. O catálogo gratuito da Bitnami acabou em 2025-09. | Usar **Traefik v3** (chart `traefik/traefik`) como Ingress controller. O recurso `Ingress` continua GA. Postgres, Mongo e Redis/Valkey no kind entram como **manifests próprios** (StatefulSet + Service), sem charts Bitnami. |
| 4 | **Valkey vs Redis** | O ElastiCache só oferece Redis OSS até a 7.1. As versões novas são Valkey (7.2+ e 8.x), com licença BSD e preço menor. | **Valkey 8.1** em todos os ambientes (local, kind e ElastiCache com `engine = "valkey"`) para ter paridade. A aplicação continua usando Spring Data Redis/Lettuce, porque o protocolo é compatível. No README, explicar "Redis" como protocolo e Valkey como implementação. |

**Conflito entre documentos resolvido: coleção de cliques no Mongo.** O STACK.md sugere uma time-series collection. O ARCHITECTURE.md recomenda uma coleção normal. **Decisão: coleção normal `click_events`.** Time-series não aceita índice único, e a idempotência do consumer depende de `_id` = ID da mensagem do stream (`insertMany` com `ordered: false`, ignorando duplicate key). No volume de um portfólio, a vantagem de storage da time-series não importa. Para retenção, usar um índice TTL em `ts` (ex.: 90 dias) e índices compostos `{code, ts}` para as agregações. No README, citar time-series como alternativa descartada e explicar o motivo.

---

## Resumo Executivo

O Link Pulse é um encurtador de URLs cujo valor real para o avaliador está na operação, não no produto. Ele precisa mostrar um caminho quente rápido (cache-aside no Redis), um pipeline assíncrono robusto (Redis Streams → consumer group → Mongo) e uma entrega reproduzível (`make up` e `make k8s`) com métricas reais. Especialistas constroem esse tipo de sistema com o Postgres como fonte da verdade (sequência + Base62), o redirect desacoplado da escrita do clique, entrega at-least-once com escrita idempotente e degradação graciosa: se o Redis cair, o redirect segue via Postgres e o clique é descartado e contado em métrica.

A abordagem recomendada constrói de dentro para fora. Primeiro vêm o núcleo de links no Postgres com CI verde, depois a camada Redis (cache e rate limit), em seguida o pipeline de cliques (a parte de maior risco técnico), depois container, compose e observabilidade, depois Kubernetes no kind e, por fim, a documentação. O Terraform depende só do contrato de runtime (portas, env vars, health paths) e pode correr em paralelo a partir da fase de container.

Os principais riscos são quatro. (1) O requisito de LocalStack é inviável como está escrito (ver decisão 2). (2) O consumer de Streams pode parar em silêncio ou nunca dar ACK e recuperar pendentes. (3) O clique "assíncrono" pode acabar bloqueando o redirect. (4) Há armadilhas de ambiente: Windows (CRLF, `make` ausente, caminho com espaço, já que o próprio projeto fica em "projeto 4"), seletores do kube-prometheus-stack e Ingress no kind. Todos têm mitigação conhecida e verificável, listada abaixo.

---

## Principais Descobertas

### Stack Recomendada

Ver STACK.md para detalhes. Versões conferidas em fontes oficiais.

**Tecnologias centrais:**
- **Java 21 (Temurin)**: restrição do projeto; LTS com virtual threads.
- **Spring Boot 4.1.1 (recomendado) / 3.5.16 (baseline da restrição)**: ver decisão 1.
- **PostgreSQL 18 + Liquibase** (4.31.1 no Boot 3.5): fonte da verdade e sequência do Base62. Usar a mesma major no local, no kind e no RDS.
- **MongoDB 8.0**: eventos de clique e aggregation pipeline, com `MongoTemplate` para `insertAll` e `Aggregation`.
- **Valkey 8.1** via Spring Data Redis + Lettuce: cache, Stream e rate limit. Não usar Jedis.
- **Micrometer + Prometheus + Actuator**: métricas, health groups de liveness/readiness.
- **Maven** (com wrapper), Checkstyle, SpotBugs e Testcontainers.
- **Infra:** kind, Helm, Traefik v3, kube-prometheus-stack, metrics-server, Terraform ≥ 1.7 (por causa do `mock_provider`), k6 e GitHub Actions publicando no GHCR.

### Funcionalidades Esperadas

**Obrigatórias (table stakes):**
- `POST /links` com validação (http/https, tamanho), alias (regex, lista de reservados, 409) e expiração futura. Erros em Problem Details.
- `GET /{code}` → **302** (nunca 301), com `Cache-Control` sem cache, 404 e 410. HEAD não conta clique.
- `/links/{code}/stats` com `from`/`to`, total, cliques por dia (UTC), top referrers e top navegadores/SO.
- Rate limit por IP → 429 + `Retry-After`, usando o IP real via forward headers confiáveis.
- Métricas, dashboards versionados, 4 alertas (5xx, p99, lag do consumer, `up`), logs JSON, OpenAPI e README pt-BR.

**Diferenciais (alto sinal, baixo custo):**
- Degradação graciosa demonstrável (`docker stop redis`).
- Consumer idempotente e painel "saúde do pipeline de cliques".
- Filtragem de bots e crawlers de preview.
- IP hasheado com salt (citar a LGPD).
- `MAXLEN ~` no `XADD` e `XAUTOCLAIM` de pendentes.
- Negative caching (invalidar ao criar o link), códigos não enumeráveis (Sqids/permutação) e runbooks por alerta.
- Thresholds do k6 e Trivy + SBOM no CI.

**Adiar (v2+):** tracing OTel atravessando o stream, SLOs com burn-rate, GeoIP, bulk create e dead-letter stream.

**Anti-features (não fazer):** `PATCH`/`DELETE` sem auth, 301, IP cru, expiração por número de cliques, dedupe de URL, stats em tempo real, exactly-once e tags de métrica por `code`/URL/IP.

### Abordagem de Arquitetura

Monorepo (`app/`, `helm/`, `observability/`, `terraform/`, `scripts/`, `k6/`), com a aplicação organizada em pacotes por feature.

**Componentes principais:**
1. **Links (Postgres):** sequência + `CodeGenerator` Base62 com atribuição explícita do ID e regra anti-colisão com alias.
2. **Redirect:** cache-aside com TTL limitado pela expiração, negative caching e fail-open para o Postgres.
3. **Produtor de cliques:** `XADD` não bloqueante, fail-open, com timeout curto, `MAXLEN ~` e métrica de falhas.
4. **Consumer:** consumer group com leitura em lote → `insertMany` → `XACK`, recuperação de pendentes e flush no shutdown. Não usar `autoAcknowledge` nem insert por mensagem.
5. **Stats:** aggregation pipeline sobre `click_events`, que é uma coleção normal (ver o conflito resolvido no topo).
6. **Rate limiter:** interceptor só em `POST /links`, com script atômico (INCR + EXPIRE).
7. **Observabilidade:** uma fonte só (`observability/`) para compose e kind, com dashboards, regras e UID de datasource fixo.

### Armadilhas Críticas

1. **LocalStack Hobby sem ECS/RDS/ElastiCache/ECR.** Mitigação: validação em camadas (decisão 2) e ajuste do requisito no roadmap.
2. **Consumer de Streams que para em silêncio, sem ACK ou sem recuperar pendentes.** Mitigação: ACK só depois do insert, `XAUTOCLAIM`, tratar o `cancelOnError` e testes de integração "Mongo cai e volta" e "pod morre com pendentes".
3. **Clique assíncrono que bloqueia ou derruba o redirect.** Mitigação: timeout curto, fail-open e p99 estável no k6 com o stream lento.
4. **Alias que colide com código gerado ou com rotas, e `allocationSize` desalinhado do `INCREMENT BY`.** Mitigação: alias com prefixo/regex disjunto do Base62, lista de reservados, `ddl-auto=validate` e teste concorrente sem duplicatas.
5. **Eviction do Redis apagando stream e rate limit.** Mitigação: `maxmemory-policy volatile-lru` em todos os ambientes.
6. **Ambiente e plataforma.** No kind: seletores do kube-prometheus-stack (`*SelectorNilUsesHelmValues`), `extraPortMappings` e `kind load` da imagem. Probes: a liveness não pode depender do Mongo, do Redis ou do Postgres. Shutdown: SIGTERM, `preStop` e flush do lote. Imagem: `USER` numérico. Windows: `.gitattributes` com LF e `make` via winget/scoop/WSL. GHCR: nome em minúsculas e pacote público.

---

## Implicações para o Roadmap

Estrutura sugerida: 7 fases, seguindo a ordem de dependência do ARCHITECTURE.md.

### Fase 1: Fundação + núcleo de links (Postgres)
**Justificativa:** tudo depende da verdade do link e do contrato de runtime. Um CI verde desde cedo evita dívida de build. Esta fase também fixa as decisões 1 e 4.
**Entrega:** skeleton do repositório, Makefile, `.gitattributes`, `mvnw`, Checkstyle/SpotBugs, Liquibase (`links` + sequência, contexts dev/prod), `CodeGenerator` com testes, `POST /links` e `GET /{code}` sem cache (302/404/410), validação de alias, Problem Details, health groups, `AbstractIT` com Testcontainers, workflow `build-test` e compose só com Postgres.
**Evita as armadilhas:** 2, 3, 10 (health groups), 11 e 18.

### Fase 2: Camada Redis (cache-aside, negative caching, rate limit, métricas do hot path)
**Justificativa:** depende da Fase 1 e pode ser testada isoladamente com Testcontainers.
**Entrega:** cache com TTL limitado pela expiração, fail-open, negative cache invalidado na criação, rate limit atômico com 429 + `Retry-After` e IP real, métricas de cache hit/miss, redirect por resultado e rejeições, todas sem tags de alta cardinalidade.
**Evita as armadilhas:** 6, 8 e 15.

### Fase 3: Pipeline de cliques (Stream → consumer → Mongo → `/stats`)
**Justificativa:** é a parte de maior risco técnico (PEL, idempotência, lag) e depende das Fases 1 e 2.
**Entrega:** produtor fail-open, consumer group com lote, ACK e `XAUTOCLAIM`, coleção normal `click_events` com `_id` = ID do stream, índice TTL, IP hasheado, filtragem de bots, parsing de UA, `/stats`, gauges de pending/lag, flush no shutdown e testes de crash/reentrega.
**Evita as armadilhas:** 4, 5, 7 e 9 (lógica do consumer).

### Fase 4: Container + compose completo + observabilidade
**Justificativa:** os dashboards só fazem sentido quando as métricas já existem.
**Entrega:** Dockerfile multi-stage com layered jar, non-root numérico e HEALTHCHECK; compose com os 6 serviços e `service_healthy`; `observability/` com dashboards JSON, painel do pipeline e 4 alertas; k6 contra o compose.
**Evita as armadilhas:** 9 (entrypoint), 14, 15, 16 e 18.

### Fase 5: Kubernetes (kind + Helm + kube-prometheus-stack) + CI e2e + GHCR
**Justificativa:** reaproveita a imagem, o `observability/` e o `smoke.sh` da Fase 4.
**Entrega:** chart do app (Deployment, Service, Ingress, ConfigMap, Secret, HPA, probes, values dev/prod), manifests próprios para as dependências, Traefik, metrics-server, ServiceMonitor e PrometheusRule, `make k8s`, job `kind-e2e` com smoke test e publicação no GHCR.
**Evita as armadilhas:** 8 (revalidar atrás do Ingress), 9 (`preStop`/grace), 10, 12, 13, 17 e 19.

### Fase 6: Terraform AWS + validação em camadas (pode correr em paralelo às Fases 4 e 5)
**Justificativa:** depende só do contrato de runtime, mas precisa da decisão 2 tomada antes.
**Entrega:** módulos network, ecr, rds-postgres, elasticache (Valkey), ecs-service (+ ALB) e secrets; `envs/aws` e `envs/localstack`; `terraform test` com mock; `tflint`; `apply` parcial no LocalStack condicionado ao token; `plan` documentado.
**Evita as armadilhas:** 1 e 7 (parameter group com `volatile-lru`).

### Fase 7: Documentação e vitrine
**Justificativa:** os prints e os resultados vêm do compose e do kind.
**Entrega:** README pt-BR com diagrama, `make up`/`make k8s`, resultados do k6 com thresholds, prints dos dashboards, trade-offs (302, time-series descartada, Valkey, LocalStack em camadas, Spring Boot) e runbooks.

### Justificativa da Ordem
- O fluxo de dados segue a dependência real: link → cache → clique → operação → orquestração.
- O risco técnico concentrado na Fase 3 é atacado cedo, antes da camada de infraestrutura.
- O Terraform fica desacoplado e serve de "folga paralela" no prazo de 1 a 2 semanas.

### Flags de Pesquisa
Precisam de `/gsd-plan-phase --research-phase`:
- **Fase 3:** API exata do Spring Data Redis na versão escolhida (`cancelOnError`, `BLOCK` com Lettuce, `XAUTOCLAIM`) e desenho dos testes de reentrega.
- **Fase 5:** versões atuais dos charts Traefik, kube-prometheus-stack e metrics-server, seletores e sidecar de dashboards, manifests que substituem a Bitnami.
- **Fase 6:** spike do token Hobby, do `mock_provider` e de como produzir um `plan` documentável sem credenciais.
- **Fase 1 (leve):** impacto da escolha Boot 3.5 vs 4.1 (Jackson 3, Testcontainers 2, Liquibase 4 vs 5).

Padrões consolidados (pular a pesquisa): Fases 2, 4 e 7.

---

## Avaliação de Confiança

| Área | Confiança | Notas |
|------|-----------|-------|
| Stack | ALTA | Versões e EOLs conferidos no Maven Central, GitHub, Docker Hub, spring.io e na documentação do LocalStack |
| Funcionalidades | MÉDIA-ALTA | Baseadas em concorrentes (Shlink, Kutt, Dub, Bitly) e no contexto de portfólio |
| Arquitetura | MÉDIA-ALTA | Padrões consolidados. Detalhes de seletores e sidecar do kube-prometheus-stack vêm de conhecimento não reverificado |
| Armadilhas | ALTA | Específicas, com verificação objetiva para cada uma |

**Confiança geral:** ALTA

### Lacunas a Resolver
- **Decisões 1 a 4:** precisam da confirmação do autor antes do roadmap final.
- **Token do LocalStack:** se o autor não quiser criar conta, a validação fica restrita às camadas (a) e (b), com o Moto como plano B ainda não testado.
- **`plan` real para AWS:** sem credenciais não há `plan` contra a AWS de verdade. Definir se o requisito aceita um `plan` gerado com endpoints locais ou um passo manual documentado.
- **Ofuscação de código (Sqids):** v1 ou trade-off documentado? Decidir na Fase 1, porque mudar depois altera os códigos já gerados.
- **MongoDB na AWS:** fora do Terraform. Documentar DocumentDB/Atlas como opção.

---

## Fontes

### Primárias (confiança ALTA)
- API `api.spring.io/projects/spring-boot/generations`: EOL da linha 3.5
- Documentação do LocalStack: cobertura de serviços por plano e auth token
- Anúncio de aposentadoria do ingress-nginx e anúncio da Bitnami sobre o catálogo legacy
- Maven Central, GitHub Releases e Docker Hub: versões
- Documentação do MongoDB (restrições de índice em time-series), dos comandos de Redis Streams, do Terraform `test`/`mock_provider` e do AWS ElastiCache (engines Valkey)

### Secundárias (confiança MÉDIA)
- Shlink, Kutt, Dub e Bitly: referência de funcionalidades
- Conhecimento de treinamento sobre kube-prometheus-stack (seletores, sidecar `grafana_dashboard`)

---
*Pesquisa concluída em: 2026-10-06*
*Pronto para o roadmap: sim (depois das decisões pendentes do autor)*
