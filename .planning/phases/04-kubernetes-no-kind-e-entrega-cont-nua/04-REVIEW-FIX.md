---
phase: 04-kubernetes-no-kind-e-entrega-cont-nua
fixed_at: 2026-10-30T01:10:00Z
review_path: .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-REVIEW.md
iteration: 1
findings_in_scope: 9
fixed: 9
skipped: 0
status: all_fixed
---

# Phase 4: Relatório de correções do code review

**Corrigido em:** 2026-10-30T01:10:00Z
**Review de origem:** .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-REVIEW.md
**Iteração:** 1

**Resumo:**
- Achados no escopo: 9 (WR-01 a WR-09; 0 critical; os 8 Info ficaram fora do escopo `critical_warning`)
- Corrigidos: 9 (WR-03 só com documentação, mantendo a troca aceita; WR-06 em parte, com a lacuna do TLS do Valkey documentada)
- Pulados: 0

## Verificação

Todas as correções foram feitas e verificadas no worktree isolado (`.claude/worktrees/rf-04-*`, branch temporária `gsd-reviewfix/04-*`), depois levadas para a `main` por fast-forward. Os comandos abaixo rodaram no worktree, num commit idêntico ao HEAD final da `main`, então dá para reproduzir na checkout principal.

- `bash deploy/kind/lint.sh` no HEAD final: **lint OK**. helm lint --strict (dev, prod, deps) sem falhas; kubeconform -strict: dev 10/10 válidos, prod 11/11 (antes eram 9: entraram PDB e NetworkPolicy), deps 7/7; as 5 guardas fail-closed recusadas, incluindo a nova `networkPolicy.enabled sem ingressFrom`; paridade das tags OK.
- `./mvnw -B -ntp -q verify -Dit.test=HealthGroupsIT` (em `app/`): **exit 0**. Checkstyle e SpotBugs passaram, 225 testes unitários, e o HealthGroupsIT teve 4 testes, 0 falhas. Os WARN "Redis health check failed" no log são esperados: o teste pausa o Valkey.
- `docker compose config`: válido, com o novo `healthcheck` do `app` resolvido. `docker build --check app`: sem avisos.
- `actionlint` 1.7.7 no `ci.yml`: o único erro é anterior a esta correção e não tem relação com ela (o escopo de permissão `artifact-metadata`, que o actionlint 1.7.7 não conhece).
- Teste negativo do kubeconform com os schemas fixados por commit: um campo inválido num Deployment foi recusado, o que mostra que os schemas fixados resolvem e o `-strict` continua valendo.
- **Não rodei:** `make up` de ponta a ponta (sobe a stack inteira nas portas 8080, 3000 e 9090) nem `make k8s`, que foi vetado. O comportamento do HEALTHCHECK e do healthcheck do compose em runtime fica para verificação humana (WR-04).

## Problemas corrigidos

### WR-04: O HEALTHCHECK da imagem agora consulta a readiness com dependências e vira liveness em orquestradores como o ECS

**Arquivos alterados:** `app/Dockerfile`, `app/src/main/resources/application.yml`, `compose.yaml`, `README.md`
**Commit:** 65ecd44
**Status:** fixed: requires human verification (runtime do `make up` não exercitado)
**Correção aplicada:** o `HEALTHCHECK` da imagem passou a consultar `/actuator/health/liveness`, sem dependências externas, com um comentário explicando o motivo (o ECS substitui a task unhealthy, K8S-02). Para o `make up` não mudar de comportamento, o serviço `app` do `compose.yaml` ganhou um `healthcheck:` próprio na readiness (mesmos interval, timeout, retries, start_period e start_interval da imagem). Assim o `--wait` e o `depends_on: service_healthy` do k6 continuam esperando o app pronto e ligado ao Postgres e ao Valkey. Atualizei os comentários dos dois grupos no `application.yml` e o README da seção "Stack completa".

### WR-01: README descreve errado o gate do Trivy: HIGH e sem correção NÃO aparecem no log

**Arquivos alterados:** `.github/workflows/ci.yml`, `README.md`
**Commit:** 20bb9ad
**Correção aplicada:** o scan virou dois passos. O primeiro é um relatório `HIGH,CRITICAL` com `ignore-unfixed: false` e `exit-code: "0"`, que instala o Trivy v0.76.0. O segundo é o gate `CRITICAL` com `ignore-unfixed: true` e `exit-code: "1"`, com `skip-setup-trivy` e todos os filtros explícitos, por causa do repasse de `TRIVY_*` entre passos. O nome do passo de gate usa vírgula em vez de "gate:", porque `: ` num escalar YAML sem aspas quebraria o arquivo. O README agora descreve os dois passos.

### WR-02: `make k8s` sempre exporta `K8S_HTTP_PORT=80` e gera aviso falso com sugestão destrutiva

**Arquivos alterados:** `Makefile`
**Commit:** 54916d0
**Correção aplicada:** `K8S_HTTP_PORT ?=` fica vazio por padrão, e a receita usa `$(if $(K8S_HTTP_PORT),K8S_HTTP_PORT=$(K8S_HTTP_PORT) )bash deploy/kind/up.sh`. O default 80 continua no `up.sh`. Conferido com `make -n`: sem argumento sai `bash deploy/kind/up.sh`; com `K8S_HTTP_PORT=8080`, como argumento ou como variável de ambiente, a porta é repassada.

### WR-08: O `scaleUp.stabilizationWindowSeconds: 60` do HPA faz o contrário do comentário ("sobe rápido")

**Arquivos alterados:** `deploy/helm/link-pulse/templates/hpa.yaml`
**Commit:** 2847232
**Correção aplicada:** a janela de scale-up foi para `0`, de forma explícita, e o comentário agora explica que esse é o default do HPA. O `scaleDown: 300` continua igual.

### WR-09: Gate do `lint.sh` depende de schemas remotos mutáveis (branch `main`) e de imagem por tag

**Arquivos alterados:** `deploy/kind/lint.sh`
**Commit:** 3813845
**Correção aplicada:** a imagem agora é `ghcr.io/yannh/kubeconform:v0.8.0@sha256:faffaf43…f64e`, fixada pelo digest do índice multi-arch. O catálogo `datreeio/CRDs-catalog` está fixado no commit `3eb90d47…`. Os schemas do Kubernetes, que antes vinham do `-schema-location default` (branch `master`), agora usam o mesmo template com o commit `47163ead…` de `yannh/kubernetes-json-schema`. Um comentário mostra como atualizar (`git ls-remote` e `docker buildx imagetools inspect`). Fica uma dependência: os schemas continuam vindo do GitHub raw a cada run. Não versionei os schemas no repositório (a opção "melhor ainda" do review) para não criar arquivos além do necessário.

### WR-07: Sem PodDisruptionBudget: um drain pode despejar todas as réplicas de produção juntas

**Arquivos alterados:** `deploy/helm/link-pulse/templates/pdb.yaml` (novo), `deploy/helm/link-pulse/values.yaml`, `deploy/helm/link-pulse/values-prod.yaml`, `README.md`
**Commit:** 9a1d079
**Correção aplicada:** novo template `policy/v1` PodDisruptionBudget, ligado por `podDisruptionBudget.enabled`: `false` no `values.yaml` e `true` no values-prod, com `maxUnavailable: 1`. Ele inclui `unhealthyPodEvictionPolicy: AlwaysAllow`. Sem isso, com a readiness presa ao Postgres e ao Valkey, uma queda deles deixaria todas as réplicas NotReady, e o default `IfHealthyBudget` travaria o drain. No kind nada muda: o render do dev continua com 0 PDB.

### WR-05: values-prod confia no X-Forwarded-For de um /16 inteiro e o chart não tem NetworkPolicy

**Arquivos alterados:** `deploy/helm/link-pulse/templates/networkpolicy.yaml` (novo), `deploy/helm/link-pulse/values.yaml`, `deploy/helm/link-pulse/values-prod.yaml`, `deploy/kind/lint.sh`, `README.md`
**Commit:** d967e93
**Correção aplicada:** nova NetworkPolicy de entrada, ligada por `networkPolicy.enabled`: `false` por padrão e `true` no values-prod. A porta `http` só aceita `ingressFrom`, que no values-prod é o Traefik no namespace `traefik`. A porta `management` só aceita `metricsFrom`, que é o Prometheus no namespace `monitoring`. O template falha no render se alguma das listas vier vazia, porque um `from` vazio liberaria qualquer origem. O `lint.sh` ganhou uma guarda nova para esse caso. O comentário do `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES: 10.0.0.0/16` no values-prod agora deixa claro que o /16 cobre a rede de pods inteira e que quem fecha o bypass é a NetworkPolicy; sem ela, o CIDR deve ser restringido. O README cita a NetworkPolicy na seção do rate limit e na lista do chart. O `values-dev.yaml` (CIDR do kind) ficou como está, porque o próprio README aceita esse risco no kind.

### WR-06: Perfil de produção de referência sem TLS e sem credenciais para Mongo e Valkey (e Postgres sem sslmode)

**Arquivos alterados:** `deploy/helm/link-pulse/values-prod.yaml`, `README.md`
**Commit:** 9f9e455
**Correção aplicada:** o `SPRING_DATASOURCE_URL` ganhou `?sslmode=require`. O comentário avisa que `verify-full` exigiria o bundle de CA do RDS na imagem. O `SPRING_MONGODB_URI` saiu do ConfigMap e entrou no contrato do Secret, como `mongodb+srv://usuario:senha@.../linkpulse?tls=true`. O comentário do `secret` lista as quatro chaves (`SPRING_DATASOURCE_PASSWORD`, `LINKPULSE_IP_HASH_KEY`, `SPRING_DATA_REDIS_PASSWORD` e `SPRING_MONGODB_URI`), explica que o `secretRef` prevalece sobre o `configMapRef` e diz o que acontece sem a URI do Mongo.
**Divergência da sugestão do review:** **não** liguei o `SPRING_DATA_REDIS_SSL_ENABLED: "true"`. O `ClickStreamConsumer.dedicatedConnectionFactory` (`app/src/main/java/dev/linkpulse/click/ClickStreamConsumer.java:252`) monta uma `LettuceClientConfiguration` própria, sem `useSsl()`, e ignora o `spring.data.redis.ssl`. Ligar a propriedade criaria um perfil meio quebrado: cache e rate limit em TLS, e o consumer sem conseguir conectar a um ElastiCache com transit encryption. A lacuna está documentada no values-prod e no README como pendência para o deploy na AWS (Phase 5). Para fechá-la, o consumer precisa passar o SSL do `spring.data.redis.ssl` (ou o `SslBundle` do `DataRedisConnectionDetails`) para a conexão dedicada.

### WR-03: Readiness com `db,redis` derruba todas as réplicas de uma vez, e nenhum alerta cobre a queda

**Arquivos alterados:** `README.md`, `observability/runbooks/LinkPulseHigh5xxRate.md`, `observability/runbooks/LinkPulseTargetDown.md`
**Commit:** e44f053
**Status:** fixed: só documentação (troca de readiness mantida)
**Correção aplicada:** a troca é consciente e está aceita no threat model (T-04-08, AR-04-02). Por isso `db` e `redis` continuam na readiness e o `application.yml` não mudou. Apliquei a alternativa mínima do próprio review, que é documentar a lacuna de observabilidade. Na seção "Probes e resiliência" do README entrou um bullet novo. Ele explica que os 503 do Traefik não entram em `http_server_requests_seconds_count` e que um pod NotReady continua sendo raspado (`up` = 1), então nenhum dos dois alertas dispara. Também diz o que fecharia a lacuna: um alerta em `kube_deployment_status_replicas_available == 0` (exige o kube-state-metrics, desligado no kind) ou nas métricas do Traefik com um ServiceMonitor. Os runbooks `LinkPulseHigh5xxRate` e `LinkPulseTargetDown` ganharam a ressalva e o comando de diagnóstico (`kubectl ... get pods,endpointslices`). Não criei um alerta novo, porque isso exigiria ligar o kube-state-metrics ou as métricas e o ServiceMonitor do Traefik no kind, além de regra e teste do promtool. Fica como melhoria futura.

---

_Corrigido em: 2026-10-30T01:10:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteração: 1_
