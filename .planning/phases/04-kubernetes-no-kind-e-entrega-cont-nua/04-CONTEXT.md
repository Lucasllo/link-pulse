# Phase 4: Kubernetes no kind e entrega contínua - Context

**Gathered:** 2026-10-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Um avaliador roda `make k8s` e obtém, num cluster kind, o mesmo encurtador observável do `make up`: Traefik como Ingress controller, metrics-server (para o HPA), kube-prometheus-stack (Prometheus e Grafana), Postgres, Mongo e Valkey como manifests próprios com imagens oficiais, e o app via Helm chart (Deployment, Service, Ingress, ConfigMap, Secret, HPA, values dev/prod). O Prometheus do cluster raspa o app por ServiceMonitor, e os dashboards e alertas de `observability/` aparecem sem passo manual. No CI, a main publica a imagem no GHCR (nome em minúsculas, pacote público), com scan do Trivy e SBOM, e um job de verificação sobe um kind, faz o deploy e passa no smoke test criar link → redirect → stats.

Requisitos: K8S-01, K8S-02, K8S-03, K8S-04, CI-02, CI-03, CI-04.

Fora desta fase: Terraform AWS e o README final com prints (Phase 5).

</domain>

<decisions>
## Implementation Decisions

### Acesso pelo host (discutido)
- **D-01:** O tráfego entra por **`extraPortMappings` do kind na porta 80 do host**, com bind em `127.0.0.1` e apontando para o entrypoint web do Traefik (hostPort/nodePort no nó control-plane). Não precisa de port-forward. Como a porta 80 pode estar ocupada no Windows (IIS e similares), documentar a causa e um override (variável no `make k8s`/kind-config ou nota no README).
- **D-02:** O roteamento é **por hostname em `*.localtest.me`**, que resolve para 127.0.0.1 por DNS público sem editar o arquivo hosts: `link-pulse.localtest.me` (app), `grafana.localtest.me` e `prometheus.localtest.me`. Cada host tem seu Ingress, sem subpaths, para não conflitar com o `GET /{code}` na raiz.
- **D-03:** `LINKPULSE_BASE_URL=http://link-pulse.localtest.me` no values do kind, para o `shortUrl` do `POST /links` ser clicável no navegador do host. — **Reversibility:** reversible
- **D-04:** O Grafana do kind tem **login anônimo com papel Viewer**, como no compose: o avaliador abre `grafana.localtest.me` e vê os dashboards. O admin usa uma senha de demonstração no values, comentada como "não é segredo".
- **D-05:** No fim, o `make k8s` **imprime as 3 URLs** (app, Grafana, Prometheus) e um exemplo de `curl` para criar um link e seguir o redirect. Se existir um alvo de seed para o kind, ele também é sugerido.
- **D-06:** **O app confia no Traefik como proxy**. No values do kind, `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` recebe o CIDR de pods do kind (o default do kind é `10.244.0.0/16`; confirmar contra o `podSubnet` do kind-config, que convém fixar explicitamente) em formato CIDR. O app passa a ler o `X-Forwarded-For` e o rate limit volta a ser por IP real, e não global. O default de `application.yml` continua vazio (decisão 02-09/Pitfall P5). O smoke test e qualquer seed precisam continuar abaixo de 20 criações/min por IP.

### Herdado de decisões anteriores (não rediscutir)
- **D-07:** Traefik v3 (chart `traefik/traefik`) no lugar do ingress-nginx, que foi aposentado. Postgres 18.6, Mongo 8.0 e Valkey 8.1 entram como manifests próprios (StatefulSet + Service) com imagens oficiais, sem Bitnami (CLAUDE.md).
- **D-08:** O Valkey roda com `maxmemory-policy volatile-lru`. `LINKPULSE_IP_HASH_KEY` é obrigatória (pelo menos 16 caracteres) e o app roda no profile prod (STATE.md, 03-CONTEXT D-14/D-15).
- **D-09:** Os dashboards e regras saem da fonte única `observability/`. Os dashboards viram ConfigMaps com o label `grafana_dashboard: "1"` (sidecar do Grafana) e as regras viram PrometheusRule. O datasource tem UID fixo e o ServiceMonitor gera o job `link-pulse`, que é o nome que as queries e os alertas esperam (03-CONTEXT D-16/D-17, STATE.md).
- **D-10:** A liveness não depende de nada externo e a readiness inclui Postgres e Valkey. Derrubar o Valkey ou o Mongo não pode reiniciar o pod (K8S-02, CLAUDE.md).
- **D-11:** O smoke test usa User-Agent de navegador ou `includeBots=true` para o clique aparecer no `/stats` (STATE.md).
- **D-12:** Actions de terceiros são fixadas por SHA, inclusive o `aquasecurity/trivy-action` (incidente de mar/2026). As versões das actions, do kind, do Helm e dos charts são as de CLAUDE.md, e o researcher confere se continuam atuais.

### Claude's Discretion
Áreas não discutidas. O pesquisador e o planejador decidem a partir dos defaults da pesquisa:
- **Imagem no cluster:** o `make k8s` reaproveita o `make image` (`link-pulse:local`) e faz `kind load docker-image`, sem depender do GHCR nem de conta. No CI, o smoke deve validar a imagem do próprio commit (build + `kind load` ou pull da imagem recém-publicada). Fica a critério do planejador se o smoke bloqueia a publicação.
- **Pipeline GHCR/Trivy/SBOM:** tags via `docker/metadata-action` (`sha-<short>` e `latest` na main), nome em minúsculas e pacote público (a visibilidade do pacote pode exigir um passo manual único, que deve ser documentado). Política do Trivy (sugestão: falhar em CRITICAL com fix disponível, SARIF opcional), formato do SBOM (SPDX ou CycloneDX, como artifact e/ou attestation) e permissões mínimas por job.
- **Values dev/prod e Secret:** o dev é o values do kind (1 réplica, HPA com min baixo, resources modestos). O prod mostra um perfil realista (mais réplicas, resources, logs ECS). O Secret pode ser renderizado pelo chart a partir de defaults de demonstração no values dev, seguindo D-07 da Phase 3, ou receber um `existingSecret` em prod.
- **Estrutura de charts e Makefile:** chart do app e chart `deps` no repositório (ex.: `deploy/helm/link-pulse`, `deploy/helm/deps`), `kind-config.yaml` com imagem do node fixada, alvos como `k8s`, `k8s-down` e talvez `k8s-seed`, além de `helm template | kubeconform -strict` no CI.
- **kube-prometheus-stack:** seletores (`*SelectorNilUsesHelmValues: false`), Alertmanager desligado ou mínimo (os alertas só precisam aparecer como firing, como na Phase 3), retenção e recursos enxutos para o laptop.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Escopo e requisitos
- `.planning/ROADMAP.md` §Phase 4: objetivo e os 5 critérios de sucesso
- `.planning/REQUIREMENTS.md`: K8S-01..04, CI-02..04
- `.planning/PROJECT.md`: restrições e tabela de decisões (Helm, Traefik, manifests próprios)
- `.planning/STATE.md` §Blockers/Concerns: itens que vão da Phase 3 para a 4 (volatile-lru, IP hash key, job `link-pulse`, UA no smoke) e a pesquisa de charts e seletores

### Pesquisa e stack
- `.claude/CLAUDE.md`: versões fixadas (kind v0.33.0, Helm v4.3.0, kube-prometheus-stack 92.0.0, Traefik chart 41.6.1, metrics-server 3.14.0, kubeconform, actions por SHA) e os 4 alertas (ingress-nginx, Bitnami)
- `.planning/research/STACK.md`, `.planning/research/ARCHITECTURE.md`, `.planning/research/PITFALLS.md`: layout do repositório, armadilhas de Kubernetes e CI

### Fases anteriores
- `.planning/phases/03-stack-local-observ-vel/03-CONTEXT.md`: compose, secrets de demo, `make image`, observability como fonte única
- `.planning/phases/02-hot-path-no-valkey-e-pipeline-de-cliques/02-CONTEXT.md`: métricas, decisão de proxies confiáveis (02-09)
- `.planning/phases/01-funda-o-e-n-cleo-de-links/01-CONTEXT.md`: contrato da API e config `linkpulse.*`

### Código e config existentes
- `app/src/main/resources/application.yml` (linhas ~52-62): `forward-headers-strategy: native` e `server.tomcat.remoteip.internal-proxies: ""`, que no kind recebe override por `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`
- `compose.yaml`: env vars do app, healthchecks e imagens que o kind deve espelhar
- `observability/`: dashboards, provisioning, regras, testes promtool e runbooks a reaproveitar
- `.github/workflows/ci.yml`: job `build-test` existente (checkout no caminho com espaço, actions por SHA)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `compose.yaml`: definições do app (`link-pulse:local`, profile prod, env vars), de Postgres 18.6, Valkey 8.1 (`volatile-lru`), Mongo 8.0, Prometheus e Grafana. É a referência para os manifests do chart `deps` e para os values.
- `observability/grafana/dashboards/*.json`, `observability/prometheus/rules/link-pulse-alerts.yml`, `observability/runbooks/*.md`: viram ConfigMaps e PrometheusRule sem cópia manual (gerados por `.Files.Get` ou `kubectl create configmap --from-file`).
- `observability/prometheus/tests/link-pulse-alerts-test.yml` e o alvo `make obs-check`: as regras continuam validadas pelo promtool.
- `Makefile`: padrão `## comentário` para o `help`, `NOCONV` para o docker no Git Bash e alvo `image` que gera `link-pulse:local`.
- `load/lib/common.js`: helpers do k6 (UA de navegador), que um seed ou smoke no kind podem reaproveitar.

### Established Patterns
- Comentários e documentação em pt-BR. Segredos de demonstração vão como defaults comentados ("não é segredo").
- Health na porta de gerenciamento 8081 (`/actuator/health/liveness` e `/readiness`) e métricas em `/actuator/prometheus`, também na 8081: o ServiceMonitor aponta para a porta de gerenciamento.
- No CI, actions fixadas por SHA com a versão em comentário, `permissions` mínimas e checkout num caminho com espaço.

### Integration Points
- `.github/workflows/ci.yml` ganha os jobs de imagem/GHCR/Trivy/SBOM e de smoke em kind.
- `Makefile` ganha `k8s` e os alvos relacionados.
- O README da Phase 5 consome as URLs `*.localtest.me` e o fluxo do `make k8s`.

</code_context>

<specifics>
## Specific Ideas

- A experiência do avaliador no kind deve espelhar a do compose: `make k8s`, abrir `http://link-pulse.localtest.me` e `http://grafana.localtest.me`, e ver os dashboards sem login nem port-forward.
- No kind, o rate limit por IP tem que funcionar de verdade atrás do Traefik, porque isso demonstra a configuração de proxy confiável.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-kubernetes-no-kind-e-entrega-contínua*
*Context gathered: 2026-10-20*
