<!-- GSD:project-start source:PROJECT.md -->

## Project

**Link Pulse**

*O Que É*

Link Pulse é um encurtador de URLs com analytics de cliques, construído como projeto de portfólio backend com ênfase em DevOps/cloud: deploy, observabilidade e infraestrutura como código. A API (sem frontend, sem autenticação) cria links curtos, redireciona com cache Redis e registra cliques de forma assíncrona no MongoDB para estatísticas. O público é recrutadores/avaliadores técnicos que vão ler o repositório, rodar `make up`/`make k8s` e olhar os dashboards.

**Core Value:** *Valor Central*

Um avaliador clona o repositório, roda `make up` (compose) ou `make k8s` (kind + Prometheus/Grafana) e vê um encurtador funcionando de ponta a ponta, com redirect rápido via cache e métricas reais nos dashboards versionados.
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->

## Technology Stack

## LEIA PRIMEIRO: 4 decisões que mudam o roadmap

| # | Fato verificado | Impacto | Recomendação |
|---|-----------------|---------|--------------|
| 1 | O **suporte OSS do Spring Boot 3.5.x acabou em 2026-06-30**, segundo a API oficial `api.spring.io/projects/spring-boot/generations`. 3.5.16, de 2026-06-25, é a última versão OSS da linha 3.x. A linha 4.1.x é suportada até 2027-07-31. | Um portfólio lançado em out/2026 sobre um framework sem patches OSS vai acumular alertas do Dependabot ou de CVE. Um avaliador atento pode notar isso. | **Opinião do pesquisador: trocar a restrição para Spring Boot 4.1.1.** Se o autor mantiver "3.x", a versão é **3.5.16**, com uma nota no README explicando a escolha. Este documento usa 3.5.16 como baseline porque é a restrição declarada. A seção "Variante Spring Boot 4.1" lista tudo o que muda. |
| 2 | O **LocalStack exige conta e `LOCALSTACK_AUTH_TOKEN` desde 2026-03-23**, quando passou a ter imagem única. No plano gratuito (Hobby), **ECS, ECR, RDS e ElastiCache NÃO estão incluídos**. A documentação oficial marca esses serviços como "Included in Plans: Base, Ultimate". EC2/VPC está no Hobby. | A premissa "ECS Fargate tem melhor suporte no LocalStack" não vale no plano grátis. Um `apply` completo no LocalStack sem custo é impossível. | Validação em camadas: (a) `apply` no LocalStack Hobby só da parte suportada (VPC, subnets, SG, IAM, CloudWatch Logs, Secrets Manager/SSM, S3); (b) `terraform test` com `mock_provider "aws"` para ECS, RDS, ElastiCache e ECR, que não pede credencial nem gera custo; (c) `validate`, `tflint` e `trivy config`; (d) `plan` documentado. Veja a seção Terraform. |
| 3 | O **controller ingress-nginx foi aposentado em março de 2026**: sem releases nem patches de segurança. A API `networking.k8s.io/v1 Ingress` continua GA. | Instalar ingress-nginx no kind em 2026 é usar software abandonado. | Usar **Traefik v3** (chart `traefik/traefik` 41.6.1) como Ingress controller no kind. O recurso `Ingress` do chart continua igual. |
| 4 | O **catálogo gratuito da Bitnami acabou** (imagens movidas para `bitnamilegacy` em 2025-08-28, catálogo removido em 2025-09-29). | Os charts `bitnami/postgresql`, `bitnami/redis` e `bitnami/mongodb`, que antes eram o caminho padrão para dependências no kind, estão quebrados ou sem atualização. | Postgres, Mongo e Redis no kind entram como manifests próprios simples (StatefulSet + Service) com imagens oficiais, em um chart `deps` do repositório. |

## Stack Recomendada

### Tecnologias Centrais (aplicação)

| Tecnologia | Versão | Propósito | Por que recomendado | Confiança |
|------------|--------|-----------|---------------------|-----------|
| Java (Eclipse Temurin) | **21** (imagem `21.0.12.1_1`) | Runtime/JDK | Restrição do projeto. LTS com virtual threads (JEP 444), records e pattern matching. Temurin é a distribuição neutra mais usada em imagens oficiais. | ALTA |
| Spring Boot | **3.5.16** (baseline da restrição) / **4.1.1** (recomendado) | Framework da API | 3.5.16 é a última 3.x (OSS encerrado em 2026-06-30). 4.1.1 é a GA atual, suportada até 2027-07 e compatível com Java 17 a 26. | ALTA |
| Spring Framework | 6.2.19 (gerenciado pelo Boot 3.5.16) | Core | Vem do BOM; não declarar versão. | ALTA |
| Spring Data JPA + Hibernate | Hibernate 6.6.53.Final (BOM) | Persistência dos links no Postgres | Restrição do projeto. A sequência Postgres alimenta o Base62. | ALTA |
| PostgreSQL | **18** (imagem `postgres:18.6`) | Fonte da verdade dos links | Versão atual e GA no RDS desde nov/2025 (RDS já tem 18.3+). Usar a mesma major no local, no kind e no RDS. | ALTA |
| PostgreSQL JDBC | 42.7.11 (BOM) | Driver | Gerenciado pelo Boot. | ALTA |
| Liquibase | **4.31.1** (BOM do Boot 3.5.16) | Migrações YAML com contexts dev/prod | Restrição do projeto. **Não forçar a 5.x no Boot 3.5**: a Liquibase 5.0 trocou a licença para FSL (Functional Source License) e é a versão que o Boot 4 gerencia. A 4.31.1 é Apache 2.0. | ALTA |
| MongoDB | **8.0** (imagem `mongo:8.0`, hoje 8.0.32) | Eventos de clique + aggregation pipeline | 8.0 é a major estável e compatível com o driver 5.5.x do Boot 3.5. 8.3 é rapid release; não precisa. | ALTA |
| MongoDB Java Driver / Spring Data MongoDB | driver 5.5.2 (BOM), Spring Data 2025.0.13 | Acesso ao Mongo | Gerenciados pelo Boot. Usar `MongoTemplate` para `insertAll`/bulk e `Aggregation`. | ALTA |
| Redis-compatível | **Valkey 8.1** (`valkey/valkey:8.1`) — alternativa `redis:8.2` | Cache, Stream de cliques, rate limit | O ElastiCache só oferece Redis OSS até 7.1; as versões novas são **Valkey** (7.2+, inclusive 8.x e 9.0). Rodar Valkey local, no kind e no ElastiCache (`engine = "valkey"`) dá paridade total, licença BSD e o ElastiCache mais barato da AWS. Streams, Lua e INCR/EXPIRE são idênticos. No README, chamar de "Redis (Valkey)". | MÉDIA (paridade de versão com o ElastiCache 8.1/9.0 vem da documentação AWS via busca) |
| Spring Data Redis + Lettuce | Spring Data Redis 3.5.13, Lettuce 6.6.0.RELEASE (BOM) | Cliente Redis, `StreamOperations`, `RedisScript` | O Lettuce é o cliente padrão do Boot, thread-safe e com suporte completo a Streams (XADD, XREADGROUP, XACK, XPENDING, XCLAIM). Não trocar por Jedis. | ALTA |
| Micrometer + `micrometer-registry-prometheus` | 1.15.12 (BOM) | Métricas em `/actuator/prometheus` | Padrão do Spring Boot Actuator. Desde a 1.13 o registry usa o Prometheus client_java 1.x. | ALTA |
| Spring Boot Actuator | (BOM) | Health liveness/readiness, métricas, info | Probes do Kubernetes via `/actuator/health/liveness` e `/readiness`. | ALTA |

### Bibliotecas de Suporte

| Biblioteca | Versão | Propósito | Quando usar | Confiança |
|------------|--------|-----------|-------------|-----------|
| `spring-boot-starter-validation` | (BOM) | Validar URL, alias e expiração no `POST /links` | Sempre. Bean Validation nos DTOs (records). | ALTA |
| `spring-boot-testcontainers` | (BOM) | `@ServiceConnection` liga os containers ao contexto Spring | Sempre, nos testes de integração. | ALTA |
| Testcontainers (`org.testcontainers:postgresql`, `mongodb`, `junit-jupiter`) | **1.21.4** (BOM do Boot 3.5.16) | Containers reais nos ITs | Sempre. **A 1.21.4 é a versão mínima que funciona com Docker Engine 29**: a 1.21.3 falha com "client version 1.32 is too old". Não declarar versão; o BOM já traz a 1.21.4. | ALTA |
| Redis no Testcontainers | `GenericContainer("valkey/valkey:8.1")` + `@ServiceConnection(name = "redis")` | IT do cache, do stream e do rate limit | Dispensa dependência extra. O `name = "redis"` faz o Boot criar o `RedisConnectionDetails` com qualquer imagem. | MÉDIA |
| springdoc-openapi (`springdoc-openapi-starter-webmvc-ui`) | **2.9.1** (compilado contra o Boot 3.5.16) | Swagger UI / OpenAPI da API | Recomendado para o avaliador explorar a API. Usar a 3.1.1 só se migrar para o Boot 4. | ALTA |
| Rate limiting | **Script Lua próprio** via `RedisScript<Long>` (sem dependência) | 429 por IP no `POST /links` | Um contador fixed-window com `INCR` + `PEXPIRE` atômico em Lua fica em 10 a 15 linhas, sem lib nova, testável com Testcontainers, e mostra que o autor entende de atomicidade no Redis. | MÉDIA (design) |
| Bucket4j (`com.bucket4j:bucket4j_jdk17-lettuce`) | 8.21.0 | Alternativa de token bucket distribuído | Só se quiser token bucket/burst de verdade. Exige passar o `RedisClient` nativo do Lettuce para o `LettuceBasedProxyManager`. É exagero para "rate limit simples". | ALTA (versão) |
| JaCoCo (`jacoco-maven-plugin`) | 0.8.15 | Cobertura | Opcional, mas barato e fica bem no README/CI. | ALTA |
| find-sec-bugs (plugin do SpotBugs) | 1.14.0 | Regras de segurança no SpotBugs | Opcional e de baixo custo; reforça a narrativa de "qualidade". | ALTA |

### Build e Qualidade

| Ferramenta | Versão | Propósito | Notas | Confiança |
|------------|--------|-----------|-------|-----------|
| **Maven** (via Maven Wrapper) | **3.9.16** (wrapper `maven-wrapper-plugin` 3.3.4) | Build | A 3.10.0 saiu em 2026-10-01 e é recente demais; a 4.0 ainda está em RC. Fixar a 3.9.16 em `.mvn/wrapper/maven-wrapper.properties`. | ALTA |
| `spring-boot-starter-parent` | 3.5.16 | Parent POM | Gerencia surefire/failsafe 3.5.6, compiler 3.14.1 etc. | ALTA |
| maven-surefire / maven-failsafe | 3.5.6 (BOM) | Unit (`*Test`) e integração (`*IT`) | `./mvnw verify` roda os dois. Testcontainers fica só no failsafe. | ALTA |
| Checkstyle (`maven-checkstyle-plugin` + `com.puppycrawl.tools:checkstyle`) | plugin **3.6.0** + engine **14.3.0** | Estilo | **O plugin 3.6.0 usa Checkstyle 9.3 por padrão**, que é antigo e falha com sintaxe Java 21 (record patterns, switch patterns). É obrigatório sobrescrever a `<dependency>` do plugin para 14.3.0 (que roda em Java 21). Rodar na fase `validate`, com `failOnViolation=true`. | ALTA |
| SpotBugs (`com.github.spotbugs:spotbugs-maven-plugin`) | **4.10.4.1** (engine 4.10.4) | Análise estática de bugs | Goal `check` na fase `verify`, `effort=Max`, `threshold=Medium`, com um `spotbugs-exclude.xml` para falsos positivos típicos do Spring (`EI_EXPOSE_REP*` em beans injetados). | ALTA |

### Container e Kubernetes

| Ferramenta | Versão | Propósito | Notas | Confiança |
|------------|--------|-----------|-------|-----------|
| Imagem de build | `eclipse-temurin:21-jdk-noble` (usar o `./mvnw` do repo) | Stage de build | Usar o wrapper, sem precisar de imagem `maven:`; a versão do Maven fica idêntica entre local e CI. Cache com `RUN --mount=type=cache,target=/root/.m2`. | ALTA |
| Imagem de runtime | **`eclipse-temurin:21.0.12.1_1-jre-noble`** (ou `21-jre-noble`) | Stage final | Ubuntu 24.04 + glibc e **já traz `curl`**, o que viabiliza o `HEALTHCHECK`. Criar usuário não-root (`useradd -u 10001 app`, `USER 10001`). Não usar distroless: ele não tem shell nem curl, e o `HEALTHCHECK` exigido pelo projeto fica inviável. | ALTA |
| Extração em camadas | `java -Djarmode=tools -jar app.jar extract --layers --launcher` | Layered jar | **`-Djarmode=layertools` está deprecated desde o Boot 3.3.** ENTRYPOINT: `java org.springframework.boot.loader.launch.JarLauncher` (o pacote mudou no Boot 3.2). | ALTA |
| Docker Compose | v2 (CLI `docker compose`; release atual 5.6.0) | `make up` | Não usar `docker-compose` v1 (hífen). | ALTA |
| kind | **v0.33.0** (node padrão `kindest/node:v1.37.0`) | Cluster local | Fixar a imagem do node pelo digest do release notes em `kind-config.yaml`. | ALTA |
| kubectl | v1.37.x | CLI | Default do `helm/kind-action` 1.15.1 é v1.37.1. | ALTA |
| Helm | **v4.3.0** (v3.22.0 ainda mantida) | Charts | O Helm 4 faz server-side apply por padrão, o que evita o erro de annotations grandes nas CRDs do kube-prometheus-stack. Charts `apiVersion: v2` continuam valendo. | ALTA (versão) / MÉDIA (comportamento) |
| kube-prometheus-stack | **92.0.0** (Prometheus Operator v0.94.1, subchart grafana 13.2.7) | Prometheus + Grafana + Alertmanager no kind | A 92.0.0 saiu em 2026-10-06; a única mudança vinda da 91.x é o `nodeSelector` linux. Fixar com `--version 92.0.0`. Requer K8s >= 1.25. | ALTA |
| Traefik (chart `traefik/traefik`) | chart **41.6.1** (Traefik v3.7.x) | Ingress controller no kind | Substitui o ingress-nginx aposentado. Atende o recurso `Ingress` do chart e também a Gateway API, se quiser evoluir depois. | ALTA (versões) / MÉDIA (escolha) |
| metrics-server (chart) | chart **3.14.0** (app v0.9.0) | Necessário para o **HPA funcionar** no kind | Usar `args: [--kubelet-insecure-tls]` no kind. Sem ele o HPA fica em `<unknown>`. | ALTA |
| kubeconform | v0.8.0 | Valida o YAML renderizado do chart | `helm template ... | kubeconform -strict` no CI. | ALTA |

### Observabilidade (stack do `make up`)

| Ferramenta | Versão | Notas | Confiança |
|------------|--------|-------|-----------|
| Prometheus | `prom/prometheus:v3.15.0` | Regras de alerta em arquivo versionado (`alerts.yml`). Para receber o remote write do k6, ligar `--web.enable-remote-write-receiver`. | ALTA |
| Grafana | `grafana/grafana:13.2.3` | Provisioning de datasource e dashboards por arquivo (`/etc/grafana/provisioning`). Os mesmos JSONs viram ConfigMaps com o label `grafana_dashboard: "1"` no kind. | ALTA |
| Métricas de percentil | `management.metrics.distribution.percentiles-histogram.http.server.requests=true` | Sem isso não sai p95/p99 via `histogram_quantile`. | ALTA |

### Terraform / AWS / LocalStack

| Ferramenta | Versão | Propósito | Notas | Confiança |
|------------|--------|-----------|-------|-----------|
| Terraform CLI | **1.16.5** (`required_version = ">= 1.16, < 2.0"`) | IaC | Commitar o `.terraform.lock.hcl`. | ALTA |
| Provider `hashicorp/aws` | **6.67.0** (`version = "~> 6.67"`) | Recursos AWS | Linha 6.x atual. | ALTA |
| Módulo `terraform-aws-modules/vpc/aws` | 6.7.3 | VPC/subnets/NAT | Opcional. Para portfólio, recursos escritos à mão em módulos próprios (`network`, `ecs`, `rds`, `cache`, `ecr`) mostram mais domínio; usar o módulo só para a VPC se o prazo apertar. | ALTA (versão) |
| LocalStack | imagem **`localstack/localstack:2026.09.0`** (CalVer), CLI `localstack` 2026.8.2 | Emulação AWS | **Exige `LOCALSTACK_AUTH_TOKEN`** (conta Hobby gratuita, uso não comercial; portfólio pessoal se encaixa). **O Hobby NÃO cobre ECS, ECR, RDS nem ElastiCache.** Cobre EC2/VPC, IAM, Logs, Secrets Manager, SSM e S3. No CI, o token entra como secret do repositório. | ALTA |
| tflocal (`terraform-local`, PyPI) | **0.26.0** | Wrapper que aponta o provider para o LocalStack | `pip install terraform-local`. Alternativa sem Python: bloco `endpoints {}` ou variável `AWS_ENDPOINT_URL=http://localhost:4566` + `s3_use_path_style = true`. | ALTA |
| `terraform test` + `mock_provider "aws"` | nativo do Terraform (>= 1.7) | Valida ECS/RDS/ElastiCache/ECR **sem credencial e sem LocalStack pago** | Peça central da estratégia de custo zero: testes `command = plan` com asserts sobre os atributos (CPU/mem da task, engine `valkey`, `postgres` 18, SGs). | MÉDIA (padrão recomendado) |
| tflint + `tflint-ruleset-aws` | 0.64.0 + 0.49.0 | Lint de Terraform | Pega tipo de instância inválido, versão de engine etc. | ALTA |
| Trivy (`trivy config`) ou Checkov | trivy 0.75.0 / checkov 3.3.25 | Scan de misconfiguração de IaC e da imagem | Ver alerta de supply chain na seção CI. | ALTA |

### Teste de carga

| Ferramenta | Versão | Notas | Confiança |
|------------|--------|-------|-----------|
| k6 | **2.3.0** (imagem `grafana/k6:2.3.0`) | O k6 v2 (maio/2026) removeu APIs antigas. Usar só `k6 run` com `options.scenarios`. **Usar `redirects: 0` nas options do teste de `GET /{code}`**; sem isso o k6 segue o 302 e passa a testar a carga do site de destino. Saída opcional `-o experimental-prometheus-rw` para jogar as métricas no Prometheus e no dashboard. | ALTA (versão) / MÉDIA (nome do output no v2) |

### CI/CD (GitHub Actions)

| Action | Versão | Uso | Confiança |
|--------|--------|-----|-----------|
| `actions/checkout` | **v7** (7.0.1) | Checkout | ALTA |
| `actions/setup-java` | **v6** (6.0.1) | `distribution: temurin`, `java-version: 21`, `cache: maven` | ALTA |
| `docker/setup-buildx-action` | **v4** (4.4.1) | Buildx | ALTA |
| `docker/login-action` | **v4** (4.6.0) | Login no GHCR com `GITHUB_TOKEN` (`permissions: packages: write`) | ALTA |
| `docker/metadata-action` | **v6** (6.2.0) | Tags `sha-<short>`, `latest` na main e semver | ALTA |
| `docker/build-push-action` | **v7** (7.4.0) | Build + push, `cache-from/to: type=gha` | ALTA |
| `helm/kind-action` | **v1.15.1** | Cluster kind v0.33.0 no runner para o smoke test | ALTA |
| `azure/setup-helm` | **v5** (5.0.1) | Fixar `version: v4.3.0` (o default é `latest`) | ALTA |
| `hashicorp/setup-terraform` | **v4** (4.0.1) | `terraform_version: 1.16.5` | ALTA |
| `terraform-linters/setup-tflint` | **v6** (6.3.2) | tflint | ALTA |
| `grafana/setup-k6-action` + `grafana/run-k6-action` | v1.2.2 + v1.5.0 | Smoke de carga opcional no CI | ALTA |
| `actions/upload-artifact` | **v7** (7.0.1) | Relatórios de teste, SpotBugs e resumo do k6 | ALTA |
| `aquasecurity/trivy-action` | v0.36.0, **fixado por SHA** | Scan da imagem/IaC | ALTA |

## Maven vs Gradle: **Maven**

## Instalação

# Wrapper (fixa Maven 3.9.16)

# Ferramentas locais (Windows: winget/scoop; Linux CI: actions)

# - Docker Desktop (WSL2), kind v0.33.0, kubectl v1.37, helm v4.3.0, terraform 1.16.5, k6 2.3.0

# - GNU make: o Git Bash NÃO traz make -> `winget install ezwinports.make` ou `scoop install make`, ou rodar no WSL

# Repositórios Helm

## Padrões de uso recomendados (por componente)

- `src/main/resources/db/changelog/db.changelog-master.yaml` com `includeAll` ou `include` de arquivos numerados (`001-create-links.yaml`).
- `spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml`, com `spring.liquibase.contexts=dev` no profile dev e `prod` no profile prod. Changesets de seed levam `contextFilter: dev` (o atributo `context` ainda funciona, mas `contextFilter` é o nome atual desde a 4.16).
- Sequência dedicada (`CREATE SEQUENCE link_id_seq`) criada por changeset. O Base62 usa o `nextval`, e não o ID do `IDENTITY` depois do insert: assim o código existe antes do commit.
- No redirect: `redisTemplate.opsForStream().add(...)` com `XADD ... MAXLEN ~ N` para limitar a memória, sem esperar o processamento.
- Consumer: **um loop de polling próprio** (bean `SmartLifecycle` rodando em virtual thread) chama `opsForStream().read(Consumer.from(group, instanceId), StreamReadOptions.empty().count(500).block(Duration.ofSeconds(2)), StreamOffset.create(key, ReadOffset.lastConsumed()))`, depois `mongoTemplate.insertAll(lote)` e por fim `acknowledge(...)` dos IDs.
- **Não usar `StreamMessageListenerContainer` para o lote:** ele entrega uma mensagem por vez ao listener, e o requisito é gravar no Mongo em lote.
- Reprocessamento: `opsForStream().pending(...)` + `claim(...)` para mensagens presas. A métrica de lag (pendentes do grupo) vira gauge do Micrometer e alimenta o alerta "lag do consumer".
- Coleção de cliques como **time-series collection** (`@TimeSeries(timeField = "ts", metaField = "code")` no Spring Data MongoDB). Encaixa bem em eventos, reduz storage e acelera agregação por dia. TTL opcional via `expireAfter`.
- Índice `{code: 1, ts: -1}`. Stats via `Aggregation.newAggregation(match, group/dateTrunc, sort, limit)`.
- Cache-aside manual com `StringRedisTemplate` (`code` → URL), com TTL = `min(TTL padrão, expiresAt - now)`. Cachear também o negativo (404) com TTL curto. Contadores `cache.hits` e `cache.misses` via `Counter` do Micrometer com tag `result=hit|miss`.
- Para isso, preferir o template explícito a `@Cacheable`: com `@Cacheable` o TTL por entrada derivado da expiração e as métricas de hit/miss ficam opacos.
- `spring.threads.virtual.enabled=true`. No Java 21, cuidado com pinning em `synchronized` (a correção, JEP 491, só chega no Java 24). O pgjdbc 42.7.x e o Lettuce já evitam isso nos caminhos quentes.
- Porta de gerenciamento separada (`management.server.port=8081`). Expor `health,info,prometheus`. Probes `/actuator/health/liveness` e `/actuator/health/readiness`. Readiness inclui `db`, `redis` e `mongo`; liveness **não** inclui dependências externas.
- Logs estruturados nativos (Boot 3.4+): `logging.structured.format.console=ecs` no profile prod.

## Alternativas Consideradas

| Recomendado | Alternativa | Quando usar a alternativa |
|-------------|-------------|---------------------------|
| Spring Boot 3.5.16 (restrição) | **Spring Boot 4.1.1** | **Preferível**, se o autor aceitar relaxar "3.x". É a única linha com suporte OSS ativo em out/2026. |
| Maven | Gradle (Kotlin DSL) | Monorepo ou multi-módulo grande, ou time que já usa Gradle. |
| Script Lua (fixed window) | Bucket4j 8.21.0 + Lettuce | Quando precisar de token bucket com burst e refill contínuo. |
| Valkey 8.1 | `redis:8.2` (oficial, tri-licença com AGPLv3) | Se o autor fizer questão do binário "Redis". Funciona igual para este projeto, mas não bate com o ElastiCache (que não oferece Redis 8). |
| Traefik (Ingress) | Envoy Gateway v1.9.2 (Gateway API) | Para mostrar Gateway API em vez de Ingress. O requisito fala em "Ingress", então Traefik atende sem mudar o chart. |
| `terraform test` + mock + LocalStack Hobby parcial | LocalStack Base (US$39/mês), plano Student (GitHub Student Pack) ou programa OSS do LocalStack | Para `apply` completo de ECS/RDS/ElastiCache/ECR emulado. Vale tentar o plano Student/OSS se o autor for elegível. |
| `terraform test` + mock | Moto server (`motoserver/moto:5.2.3`, Apache-2.0) ou Floci (`floci/floci`, MIT, muito novo) | Emuladores grátis com ECS/RDS/ElastiCache/ECR em nível de API. O Moto é maduro mas só imita a API (não sobe Postgres de verdade). O Floci tem risco de adoção. Bom como "extra", não como base. |
| Terraform | OpenTofu | Se a licença BSL incomodar. Terraform tem mais reconhecimento em vaga. |
| `eclipse-temurin:21-jre-noble` | `21-jre-alpine` / distroless `java21-debian12:nonroot` | Alpine: imagem menor (musl, só wget). Distroless: superfície mínima, mas sem curl para o `HEALTHCHECK`. |
| Polling manual do Stream | `StreamMessageListenerContainer` | Quando o processamento é por mensagem, sem lote. |

## O Que NÃO Usar

| Evitar | Por quê | Usar no lugar |
|--------|---------|---------------|
| Testcontainers < 1.21.4 (ou forçar 2.x no Boot 3.5) | < 1.21.4 quebra no Docker Engine 29 ("client version 1.32 is too old"). A 2.x renomeia artefatos (`testcontainers-postgresql`) e pacotes, o que desalinha com as `ConnectionDetails` do Boot 3.5. | A 1.21.4 do BOM (ou a 2.0.5 só no Boot 4.x). |
| Liquibase 5.x no Boot 3.5 | Licença FSL; não é a versão testada pelo Boot 3.5. | 4.31.1 (BOM). |
| `-Djarmode=layertools` | Deprecated desde o Boot 3.3. | `-Djarmode=tools extract --layers --launcher`. |
| Checkstyle padrão do plugin (9.3) | Não entende a sintaxe moderna do Java 21. | Engine 14.3.0 como dependência do plugin. |
| ingress-nginx | Aposentado em mar/2026, sem patches de segurança. | Traefik (chart 41.6.1). |
| Charts/imagens Bitnami | Catálogo gratuito encerrado (`bitnamilegacy` sem atualização). | Manifests próprios com imagens oficiais (`postgres:18.6`, `mongo:8.0`, `valkey/valkey:8.1`). |
| `localstack/localstack:latest` sem token / pin em 4.x "community" | `latest` exige auth desde 2026-03-23. A community 4.x nunca teve ECS/RDS/ElastiCache/ECR (eram Pro) e está congelada. | `localstack/localstack:2026.09.0` + `LOCALSTACK_AUTH_TOKEN` (Hobby), limitado aos serviços suportados. |
| Jedis | Bloqueante; o Lettuce já é o default e suporta tudo que o projeto precisa. | Lettuce (BOM). |
| `@Cacheable` para o redirect | TTL por entrada derivado da expiração e métricas hit/miss ficam opacos. | Cache-aside explícito com `StringRedisTemplate`. |
| `micrometer-registry-prometheus-simpleclient` | Registry legado (client 0.x). | `micrometer-registry-prometheus`. |
| Lombok | Records do Java 21 cobrem DTOs e eventos. O Lombok gera ruído no SpotBugs (`EI_EXPOSE_REP`) e mais um processador de anotação. | Records + construtores explícitos. |
| Actions de terceiros por tag mutável | Incidente trivy-action (mar/2026). | Pin por SHA + Dependabot. |
| k6 com redirects habilitados no teste de redirect | Mede o site de destino, não a API. | `redirects: 0` e checagem `status === 302`. |
| Gerar o código curto com hash/aleatório | Colisão e retry; o projeto já decidiu Base62 da sequência. | `nextval` + Base62. |

## Padrões de Stack por Variante

- `spring-boot-starter-parent` **4.1.1**. Spring Framework 7.0.9, Hibernate 7.4.5.Final, Spring Data 2026.0.1, Lettuce 7.5.2, Micrometer 1.17.1, Mongo driver 5.8.1, JUnit 6.0.3, Jackson **3.1.5** (pacote `tools.jackson`).
- Starters modulares do Boot 4: `spring-boot-starter-webmvc` e **`spring-boot-starter-liquibase`** (no Boot 4 a autoconfig do Liquibase saiu do core). Testes: `spring-boot-starter-*-test` correspondentes.
- Liquibase **5.0.3** (licença FSL, aceitável para portfólio; mencionar no README) ou fixar 4.33.0 por propriedade.
- Testcontainers **2.0.5** com artefatos `org.testcontainers:testcontainers-postgresql`, `testcontainers-mongodb` e `testcontainers-junit-jupiter`.
- springdoc **3.1.1**.
- Java continua **21**: o Boot 4.1 suporta Java 17 a 26.
- Todo o resto (Docker, kind, Helm, Terraform, CI, k6) **não muda**.
- 3.5.16 com Dependabot ligado. Documentar no README que é "a última linha 3.x; migração para 4.x planejada", o que transforma a limitação em sinal de maturidade.
- Solicitar o plano Student/OSS do LocalStack ou usar um trial do Base. Manter `terraform test` com mock como gate do CI de qualquer forma, porque não depende de token.

## Compatibilidade de Versões

| Pacote A | Compatível com | Notas |
|----------|----------------|-------|
| Spring Boot 3.5.16 | Java 17 a 25 | Java 21 OK. |
| Spring Boot 4.1.1 | Java 17 a 26 | Java 21 OK. |
| Boot 3.5.16 | Testcontainers 1.21.4, Liquibase 4.31.1, Lettuce 6.6.0, Micrometer 1.15.12, Mongo driver 5.5.2, pgjdbc 42.7.11 | Versões gerenciadas pelo BOM; não sobrescrever. |
| springdoc 2.9.1 | Boot 3.5.16 | Compilado contra 3.5.16. |
| Testcontainers 1.21.4 | Docker Engine 29 | Versão mínima com negociação de API compatível. |
| Checkstyle 14.3.0 | Runtime Java 21 | Exige JDK 21 para rodar (OK). |
| Mongo driver 5.5.x | MongoDB 8.0 | Usar `mongo:8.0`, não 8.3. |
| Valkey 8.1 | Lettuce 6.6 / Spring Data Redis 3.5 | Protocolo compatível; Streams e Lua idênticos. |
| Valkey (ElastiCache) | `engine = "valkey"`, `engine_version = "8.1"` | O ElastiCache tem Redis OSS só até 7.1. |
| Postgres 18 | RDS (18.1+ GA desde nov/2025) | A imagem Docker 18 mudou o PGDATA para `/var/lib/postgresql/18/docker`; montar o volume em `/var/lib/postgresql`. |
| kube-prometheus-stack 92.0.0 | Kubernetes >= 1.25; kind v0.33.0 (K8s 1.37) | OK. |
| kind v0.33.0 | `helm/kind-action` v1.15.1 (default) | O mesmo kind no local e no CI. |
| Helm 4.3.0 | Charts `apiVersion: v2` | Charts Helm 3 funcionam. |
| LocalStack 2026.09.0 | tflocal 0.26.0, AWS provider 6.x | Precisa de `LOCALSTACK_AUTH_TOKEN`. |
| k6 2.3.0 | `grafana/run-k6-action` v1.5.0 | Scripts devem usar só APIs não removidas no v2. |

## Fontes

- API oficial spring.io, https://api.spring.io/projects/spring-boot/generations: datas de suporte OSS (3.5.x até 2026-06-30; 4.1.x até 2027-07-31). **ALTA**
- endoflife.date, https://endoflife.date/api/spring-boot.json: confirma 3.5.16 como último release 3.5 e 4.1.1 como atual. **ALTA**
- Maven Central (`maven-metadata.xml` e `spring-boot-dependencies-{3.5.16,4.1.1}.pom`): versões do Boot, Testcontainers, Liquibase, Micrometer, Lettuce, Checkstyle, plugins SpotBugs/Checkstyle/JaCoCo, springdoc, Bucket4j, Maven Wrapper. **ALTA**
- GitHub Releases API: kind v0.33.0, Helm v4.3.0/v3.22.0, kube-prometheus-stack 92.0.0 (Chart.yaml e UPGRADE.md), Terraform 1.16.5, AWS provider 6.67.0, k6 2.3.0 (notas do v2.0.0), Maven 3.9.16/3.10.0/4.0.0-rc-7, Traefik chart 41.6.1, metrics-server chart 3.14.0, tflint 0.64.0, trivy 0.75.0 e todas as GitHub Actions listadas (action.yml do `helm/kind-action` v1.15.1 para os defaults). **ALTA**
- Docker Hub tags API: `eclipse-temurin:21.0.12.1_1-*`, `postgres:18.6`, `mongo:8.0.32`, `redis:8.x`, `valkey/valkey`, `prom/prometheus:v3.15.0`, `grafana/grafana:13.2.x`, `localstack/localstack:2026.09.0`, `grafana/k6:2.3.0`. **ALTA**
- Adoptium Dockerfile (`21/jre/ubuntu/noble`): confirma `curl` e `wget` na imagem JRE. **ALTA**
- LocalStack: [Single image next steps](https://blog.localstack.cloud/localstack-single-image-next-steps/) (2026-03-05), [Pricing](https://www.localstack.cloud/pricing), páginas de serviço [ECS](https://docs.localstack.cloud/aws/services/ecs/), [ECR](https://docs.localstack.cloud/aws/services/ecr/), [RDS](https://docs.localstack.cloud/aws/services/rds/), [ElastiCache](https://docs.localstack.cloud/aws/services/elasticache/) ("Included in Plans: Base, Ultimate") e [EC2](https://docs.localstack.cloud/aws/services/ec2/) ("Hobby, Base, Ultimate"). [Issue testcontainers-java #11568](https://github.com/testcontainers/testcontainers-java/issues/11568). **ALTA**
- PyPI JSON: terraform-local 0.26.0, localstack CLI 2026.8.2, moto 5.2.3. **ALTA**
- Testcontainers + Docker 29: [issue #11235](https://github.com/testcontainers/testcontainers-java/issues/11235), [coffeesprout](https://www.coffeesprout.nl/en/testcontainers-docker29-api-too-old.html). **ALTA** (confirmado por múltiplas fontes)
- Aposentadoria do ingress-nginx: [Google Open Source Blog](https://opensource.googleblog.com/2026/02/the-end-of-an-era-transitioning-away-from-ingress-nginx.html), [chkk.io](https://www.chkk.io/blog/ingress-nginx-deprecation). **ALTA**
- Fim do catálogo Bitnami: [Northflank](https://northflank.com/blog/bitnami-deprecates-free-images-migration-steps-and-alternatives), [chkk.io](https://www.chkk.io/blog/bitnami-deprecation). **ALTA**
- Liquibase 5.0 / FSL: [Liquibase blog](https://www.liquibase.com/blog/liquibase-community-for-the-future-fsl), [spring-boot#47386](https://github.com/spring-projects/spring-boot/issues/47386). **ALTA**
- Incidente trivy-action: [GHSA-69fq-xp46-6x23](https://github.com/aquasecurity/trivy/security/advisories/GHSA-69fq-xp46-6x23), [Microsoft Security Blog](https://www.microsoft.com/en-us/security/blog/2026/03/24/detecting-investigating-defending-against-trivy-supply-chain-compromise/). **ALTA**
- AWS: [ElastiCache supported engines](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/supported-engine-versions.html) (Valkey 7.2+; Redis OSS até 7.1), [RDS PostgreSQL 18 GA](https://aws.amazon.com/about-aws/whats-new/2025/11/amazon-rds-postgresql-major-version-18). **MÉDIA a ALTA** (obtido via busca; conferir o `engine_version` exato do Valkey na fase de Terraform)
- Floci: [wavect.io review](https://wavect.io/blog/floci-vs-localstack-aws-emulator/) (jul/set 2026). **BAIXA** (fonte única, projeto novo; citado só como alternativa)

<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->

## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->

## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->

## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->

## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:

- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->

## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
