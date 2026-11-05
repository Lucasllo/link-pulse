# Phase 5: Terraform AWS e vitrine - Context

**Gathered:** 2026-11-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Entregar (1) a infraestrutura AWS do app descrita em Terraform — rede, ECR, ECS Fargate + ALB, RDS Postgres, ElastiCache Valkey e secrets — validada em camadas a custo zero (fmt/validate/tflint, `terraform test` com `mock_provider "aws"`, apply parcial no LocalStack Hobby quando houver token, `plan`/apply real só documentado), e (2) a vitrine do repositório: README pt-BR reorganizado com diagrama, quickstart `make up`/`make k8s`, resultados do k6, prints dos dashboards e trade-offs. Requisitos: IAC-01..05, DOC-01, DOC-02.

Fora desta fase: apply real na AWS, push de imagem para o ECR pelo CI, Mongo gerenciado provisionado (DocumentDB/Atlas só documentados), mudanças no código da aplicação.

</domain>

<decisions>
## Implementation Decisions

### Desenho da infra AWS
- **D-01:** Tasks Fargate, RDS e ElastiCache em **subnets privadas**; saída para a internet por **um NAT Gateway** (variável para 1 NAT por AZ). Subnets públicas só para o ALB e o NAT. O custo do NAT (~US$32/mês + tráfego) é documentado no README/docs.
- **D-02:** A task definition puxa a imagem do **ECR por padrão**; uma variável (ex.: `image_uri`/`image_repository`) permite apontar para `ghcr.io/lucasllo/link-pulse:sha-{sha7}`. A doc descreve o espelhamento GHCR → ECR (ex.: `crane copy` ou `docker pull/tag/push`) — sem push pelo CI.
- **D-03:** ALB com **listener HTTP 80 por padrão**; se `certificate_arn` (ou domínio) for informado, cria listener **443 com ACM** e redirect 80→443. Os testes mock cobrem os dois caminhos.
- **D-04:** Layout `infra/terraform/modules/{network,ecr,ecs,rds,cache,secrets}` (nomes finais a critério do planejador) + root **`envs/prod`** que compõe tudo. Backend **S3 com `use_lockfile`** declarado de forma parcial (`-backend-config`), **state local por padrão**; `.terraform.lock.hcl` commitado. — **Reversibility:** costly — mover módulos/roots depois exige reescrever testes, CI e `moved` blocks.
- **D-05:** URI do MongoDB entra como **variável sensível gravada num secret do Secrets Manager** e injetada na task via `secrets` (env `SPRING_MONGODB_URI`). Docs explicam Atlas (PrivateLink/peering) vs DocumentDB (compatibilidade parcial com aggregation/time-series).
- **D-06:** RDS com **`manage_master_user_password = true`** (senha gerada/rotacionada no Secrets Manager, nada no state); a task referencia o secret gerenciado (chave `password`). Postgres 18 (mesma major do local/kind).
- **D-07:** Serviço ECS com **desired 2, autoscaling min 2 / max 4, target tracking CPU ~60%**, deployment circuit breaker com rollback, sizing **0.5 vCPU / 1 GB** (espelhando o HPA do `values-prod`).
- **D-08 (revisada em 2026-11-05, após a pesquisa):** Target group na **8080**, health check do ALB na **8081** `/actuator/health/liveness` (não a readiness: no ECS, task que falha no health check do ALB é substituída, e a readiness inclui `db,redis`, o que transformaria queda de dependência em churn de tasks). O path fica como variável `health_check_path`. O SG das tasks aceita 8080 e 8081 **somente do SG do ALB**; o listener do ALB nunca expõe a 8081.

### LocalStack e camadas de validação
- **D-09:** Root separado **`envs/localstack`** que reaproveita os mesmos módulos e compõe só o subset do Hobby: network (sem NAT), secrets, IAM (roles de execução/task) e CloudWatch log group. Os módulos precisam permitir isso (ex.: `enable_nat_gateway = false`).
- **D-10:** Provider do `envs/localstack` com **bloco `endpoints`** (ou `AWS_ENDPOINT_URL`), credenciais fake e `skip_*` — **sem tflocal/Python**.
- **D-11:** **`compose.localstack.yaml`** (imagem `localstack/localstack:2026.09.0`, `LOCALSTACK_AUTH_TOKEN` do ambiente) + alvos `make`: **`tf-check`** (fmt -check, validate, tflint, `terraform test`; sem Docker nem token), **`tf-localstack`** (sobe LocalStack, apply, verificação dos recursos, destroy), **`tf-plan`** (procedimento do plan real, documentado). Tabela "com make / sem make" do README segue o padrão atual.
- **D-12:** CI: job **`terraform`** sempre (fmt -check, validate com `-backend=false`, tflint + ruleset aws, `terraform test`, `trivy config`), **sem paths filter**; job **`localstack`** só quando o secret `LOCALSTACK_AUTH_TOKEN` existir — quando não existe, é pulado com aviso no job summary e **nunca falha**. Actions fixadas por SHA.
- **D-13:** `trivy config` **falha em HIGH/CRITICAL**; exceções aceitas ficam em `.trivyignore` (ou `#trivy:ignore`) **com um comentário justificando cada uma** (ex.: HTTP default sem certificado, chave AWS-managed em vez de CMK).
- **D-14:** `terraform test` **por módulo** (`modules/*/tests/*.tftest.hcl`, `mock_provider "aws"`, asserts de contrato: engine `valkey` 8.x, Postgres 18, CPU/mem, health 8081, SGs mínimos, HTTPS liga/desliga, NAT) **+ um teste de composição** do `envs/prod` checando a ligação entre módulos; inclui `expect_failures` para validações de variáveis.

### README e documentação
- **D-15:** README vira **vitrine (~150-250 linhas)**; o conteúdo operacional atual (482 linhas) migra para `docs/` (sugestão: `api.md`, `operacao-local.md`, `kubernetes.md`, `observabilidade.md`, `terraform-aws.md`, `decisoes.md`). Nada do conteúdo atual se perde — só muda de lugar; links internos e âncoras citadas por outros arquivos (runbooks, scripts que imprimem URLs, `observability/README.md`) precisam ser atualizados.
- **D-16:** Diagramas em **Mermaid** no Markdown: um do fluxo do app (POST/GET → cache Valkey → Postgres; XADD → stream → consumer → Mongo → /stats; Prometheus/Grafana) e um da **topologia AWS** (VPC, subnets, ALB, ECS, RDS, ElastiCache, NAT, Secrets Manager, ECR, Atlas externo).
- **D-17:** Trade-offs: **tabela curta no README** (escolha → motivo, com link) + **`docs/decisoes.md`** estilo ADR leve (contexto, opções, escolha, consequência) cobrindo 302 vs 301, Valkey, coleção normal vs time-series, Terraform em camadas, Boot 4.1, Mongo na AWS via DocumentDB/Atlas, e as limitações aceitas (fail-open do rate limit, Valkey sem TLS no kind, Liquibase FSL).
- **D-18:** Topo do README: **badges** (status do `ci.yml`, pacote GHCR, Java 21, Spring Boot 4.1, licença MIT), bloco **"Em 2 minutos"** (clone → `make up` → `curl` e o que esperar) e **5-7 destaques técnicos**.
- **D-19:** Criar **`LICENSE` MIT** na raiz (Lucas Lopes, 2026).
- **D-20:** `estudos/` recebe **um link discreto** no README; a pasta não é alterada.

### Evidências (prints e k6)
- **D-21:** Prints via **checkpoint humano**: o plano entrega o roteiro (make clean → up → seed → load, quais dashboards/painéis, janela de tempo, tema, nomes de arquivo fixos em `docs/img/`), o executor pausa e o autor captura e salva os PNGs.
- **D-22:** Prints saem da **stack compose** (mesma origem do `load-summary.json`), mais **um print do kind** (Grafana em `grafana.localtest.me` ou `kubectl get hpa,pods`) provando o caminho K8s.
- **D-23:** k6 no README como **tabela gerada a partir do `load/results/load-summary.json` versionado** (por cenário redirect/create: RPS, p95, p99, taxa de erro, threshold ok), com linha de contexto da máquina (laptop, Docker Desktop/WSL2) e aviso de que não é benchmark. O plano confere que os números citados batem com o JSON.

### Herdado de decisões anteriores (não rediscutir)
- **D-24:** Versões do CLAUDE.md (Terraform 1.16.5 `>= 1.16, < 2.0`, provider `hashicorp/aws ~> 6.67`, tflint 0.64 + ruleset aws 0.49, LocalStack 2026.09.0, trivy, actions por SHA); o researcher confere se continuam atuais.
- **D-25:** ElastiCache com `engine = "valkey"` (8.x); Postgres 18; sem apply real; Mongo não provisionado; imagem `ghcr.io/lucasllo/link-pulse:sha-{sha7}` é o contrato publicado (PROJECT.md).
- **D-26:** Contrato de runtime: API 8080, management 8081, profile `prod`, `LINKPULSE_IP_HASH_KEY` obrigatória (>= 16 chars, vai para secret), `LINKPULSE_BASE_URL`, `LINKPULSE_SELF_HOSTS`, `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (CIDR da VPC, atrás do ALB), `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`, `SPRING_DATASOURCE_*` com `sslmode=require`, `SPRING_DATA_REDIS_*` — ver `deploy/helm/link-pulse/values-prod.yaml`.
- **D-27:** Toda documentação em pt-BR; commits sem co-autoria do Claude.

### Claude's Discretion
- Nomes exatos dos módulos, variáveis e outputs; uso ou não de `terraform-aws-modules/vpc` (preferência: escrito à mão).
- Região default (sugestão `us-east-1` ou `sa-east-1`, documentada), CIDRs, classe da instância RDS (ex.: `db.t4g.micro`) e do ElastiCache (`cache.t4g.micro` ou serverless), retenção de logs.
- **TLS do Valkey no ElastiCache (decidido em 2026-11-05, após a pesquisa):** `transit_encryption_enabled = false` como default do módulo, sem `auth_token`, acesso só pelo SG das tasks e sem mudança no app (`ClickStreamConsumer.dedicatedConnectionFactory` conecta sem SSL). A variável não é exposta em `envs/prod`. A lacuna entra nos trade-offs (D-17), junto com a WR-06 da Phase 4.
- Como o `tf-localstack` verifica os recursos (aws cli via container, `terraform output`, ou `check` blocks).
- Ordem/forma de migrar o README para `docs/` e onde fica o procedimento do plan real (provavelmente `docs/terraform-aws.md`).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Escopo e requisitos
- `.planning/ROADMAP.md` §Phase 5 — goal e success criteria
- `.planning/REQUIREMENTS.md` — IAC-01..05, DOC-01, DOC-02
- `.planning/PROJECT.md` — Fora de Escopo (apply real, push ECR, Mongo gerenciado) e Decisões Importantes
- `.claude/CLAUDE.md` — versões fixadas, seção Terraform/LocalStack (limites do plano Hobby), alerta de supply chain do trivy-action

### Contrato de runtime e infraestrutura existente
- `deploy/helm/link-pulse/values-prod.yaml` — env vars de prod, sslmode, proxies confiáveis, logs ECS, lacuna TLS do Valkey (WR-06)
- `compose.yaml` — env vars completas do app (Mongo URI, IP hash key)
- `.planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-CONTEXT.md` — decisões de imagem/GHCR, probes, proxies
- `.planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-REVIEW.md` — WR-03/WR-06 (lacunas documentadas)
- `.github/workflows/ci.yml` — jobs build-test, image, k8s-smoke, publish; padrão de pin por SHA

### Vitrine
- `README.md` — conteúdo atual a migrar para `docs/`
- `load/results/load-summary.json` — fonte dos números do k6
- `observability/README.md`, `observability/runbooks/` — links para o README que podem quebrar
- `Makefile` — padrão de alvos com `## descrição` e tabela com/sem make

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Makefile` com `help` autodocumentado: novos alvos `tf-*` seguem o mesmo formato `alvo: ## descrição`.
- `compose.dev.yaml`: modelo de compose auxiliar isolado para o `compose.localstack.yaml`.
- `deploy/kind/lint.sh` / `smoke.sh`: padrão de scripts bash chamados pelo make e pelo CI (os mesmos comandos local e CI).
- `values-prod.yaml`: lista pronta das env vars de prod para a task definition.

### Established Patterns
- Mesmos scripts no laptop e no CI (CI roda `up.sh`/`smoke.sh` do avaliador).
- Actions por SHA; versões fixas; Dependabot ativo.
- Documentação pt-BR com tabela "Com make / Sem make" e suporte a caminho com espaço (CI clona em `link pulse/`).
- Limitações aceitas são documentadas explicitamente (README "Riscos conhecidos", REVIEW WR-*).

### Integration Points
- `.github/workflows/ci.yml`: novos jobs `terraform` e `localstack`.
- `Makefile`: alvos `tf-check`, `tf-localstack`, `tf-plan`.
- Raiz: `infra/terraform/`, `compose.localstack.yaml`, `LICENSE`, `docs/`, `docs/img/`.

</code_context>

<specifics>
## Specific Ideas

- Público é recrutador/avaliador: o README precisa ser lido em ~2 minutos; o detalhe vai para `docs/`.
- Evidências reais (prints tirados pelo autor, números do JSON versionado), nunca inventadas.
- Exceções de segurança (trivy) sempre com justificativa escrita — triagem consciente é parte da narrativa.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-terraform-aws-e-vitrine*
*Context gathered: 2026-11-02*
