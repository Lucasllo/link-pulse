# Phase 4: Kubernetes no kind e entrega contínua - Research

**Researched:** 2026-10-20
**Domain:** kind + Helm 4 (Traefik v3, metrics-server, kube-prometheus-stack), Helm charts próprios (app e deps), health groups do Spring Boot 4.1, GitHub Actions (GHCR, Trivy, SBOM, attestations, smoke em kind)
**Confidence:** HIGH em versões, valores de charts e comportamento de Traefik/Operator (lidos na fonte nesta sessão); MEDIUM no fluxo Windows/Git Bash (nada rodou: Docker Desktop estava desligado, kind e helm não estão instalados) e no desenho do pipeline de publicação

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Acesso pelo host (discutido)
- **D-01:** O tráfego entra por **`extraPortMappings` do kind na porta 80 do host**, com bind em `127.0.0.1` e apontando para o entrypoint web do Traefik (hostPort/nodePort no nó control-plane). Não precisa de port-forward. Como a porta 80 pode estar ocupada no Windows (IIS e similares), documentar a causa e um override (variável no `make k8s`/kind-config ou nota no README).
- **D-02:** O roteamento é **por hostname em `*.localtest.me`**, que resolve para 127.0.0.1 por DNS público sem editar o arquivo hosts: `link-pulse.localtest.me` (app), `grafana.localtest.me` e `prometheus.localtest.me`. Cada host tem seu Ingress, sem subpaths, para não conflitar com o `GET /{code}` na raiz.
- **D-03:** `LINKPULSE_BASE_URL=http://link-pulse.localtest.me` no values do kind, para o `shortUrl` do `POST /links` ser clicável no navegador do host. — **Reversibility:** reversible
- **D-04:** O Grafana do kind tem **login anônimo com papel Viewer**, como no compose: o avaliador abre `grafana.localtest.me` e vê os dashboards. O admin usa uma senha de demonstração no values, comentada como "não é segredo".
- **D-05:** No fim, o `make k8s` **imprime as 3 URLs** (app, Grafana, Prometheus) e um exemplo de `curl` para criar um link e seguir o redirect. Se existir um alvo de seed para o kind, ele também é sugerido.
- **D-06:** **O app confia no Traefik como proxy**. No values do kind, `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` recebe o CIDR de pods do kind (o default do kind é `10.244.0.0/16`; confirmar contra o `podSubnet` do kind-config, que convém fixar explicitamente) em formato CIDR. O app passa a ler o `X-Forwarded-For` e o rate limit volta a ser por IP real, e não global. O default de `application.yml` continua vazio (decisão 02-09/Pitfall P5). O smoke test e qualquer seed precisam continuar abaixo de 20 criações/min por IP.

#### Herdado de decisões anteriores (não rediscutir)
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

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| K8S-01 | Helm chart do app com Deployment, Service, Ingress, ConfigMap, Secret e HPA, com values dev/prod | Padrão 6 (templates, values dev/prod, `existingSecret`, checksum de config, HPA sem `spec.replicas`, tag única da imagem); Pitfalls 6, 7, 12 |
| K8S-02 | Liveness e readiness via Actuator (liveness sem dependências externas; readiness com Postgres e Valkey) | Padrão 7 (`management.endpoint.health.group.readiness.include: readinessState,db,redis`, chaves `db`/`redis` conferidas na doc do Boot 4.1.1; probes na 8081; startupProbe); Pitfalls 9, 10 |
| K8S-03 | `make k8s` cria cluster kind e instala Traefik, metrics-server, kube-prometheus-stack, dependências e o app | Padrões 1 a 5, 8 e 9 (kind-config com digest e `podSubnet`, Traefik NodePort 30080 ← hostPort 80, metrics-server com `--kubelet-insecure-tls`, kps enxuto, chart `deps`, script `up.sh` com contexto fixo); Pitfalls 1 a 5, 11, 13 |
| K8S-04 | Prometheus faz scrape via ServiceMonitor; dashboards e regras carregados automaticamente | Padrão 4 (seletores `*NilUsesHelmValues: false` verificados no template) e Padrão 6 (ServiceMonitor com `jobLabel`, PrometheusRule e ConfigMaps a partir de `observability/` por `--set-file`); Pitfalls 6, 7, 8 |
| CI-02 | Build da imagem e publicação no GHCR (na main) | Padrão 11 (jobs `image` → `k8s-smoke` → `publish`, metadata-action minusculiza, push da imagem testada, permissões por job); Pitfalls 14, 15, 17 |
| CI-03 | Scan do Trivy e SBOM | Padrão 11 (trivy-action v0.36.0 por SHA com `version: v0.76.0`, gate CRITICAL com fix, SBOM CycloneDX + `actions/attest`); Pitfall 16 |
| CI-04 | Job de verificação faz deploy em kind e roda smoke test (criar link → redirect → stats) | Padrões 10 e 11 (`helm/kind-action` com `install_only`, mesmo `make k8s` do avaliador, smoke em bash com `--resolve`, UA de navegador e polling do `/stats`); Pitfalls 4, 18 |
</phase_requirements>

## Summary

A fase é quase toda infraestrutura declarativa: um `kind-config.yaml`, três arquivos de values para charts de terceiros (Traefik, metrics-server, kube-prometheus-stack), dois charts próprios (`link-pulse` e `deps`), um script de subida, um script de smoke e três jobs novos no CI. A única mudança no código do app é de configuração: hoje o `application.yml` não declara health groups, e no Boot 4.1.1 a readiness tem só o `readinessState` por padrão ("By default, Spring Boot does not add other health indicators to these groups"). Por isso o K8S-02 precisa de `management.endpoint.health.group.readiness.include: readinessState,db,redis`. As chaves `db` e `redis` foram conferidas na tabela da doc do Boot 4.1.1. O Mongo agora se chama `mongodb`, e de qualquer forma fica fora dos dois grupos.

Várias versões do CLAUDE.md já envelheceram. Helm **4.3.1** (a 4.3.1 corrige a espera por CRDs no modo `hookOnly`, que é o default do Helm 4 sem `--wait`). Traefik chart **42.1.0**: traz o Traefik v3.7.15, que corrige três CVEs; a 42.0.0 só quebra para Traefik Hub e K8s < 1.30. kube-prometheus-stack **92.10.1**: traz o subchart grafana 14.0.1, com o Grafana 13.2.4, que corrige quatro CVEs; ele adiciona um `values.schema.json` e mantém o Prometheus v3.15.0 e o Operator v0.94.1. A 92.0.0 do CLAUDE.md trazia o Grafana 13.2.3, igual ao compose, mas sem os fixes. Trivy **v0.76.0**. kind v0.33.0, metrics-server 3.14.0, kubeconform v0.8.0, `helm/kind-action` v1.15.1 e as actions da Docker continuam atuais. Todos os SHAs foram resolvidos pela API do GitHub nesta sessão (tabela no Padrão 11).

Três descobertas mudam o desenho. (1) **`.Files.Get` não sai do chart, e symlinks quebram no Windows.** A melhor saída é o `--set-file` do Helm 4: o `up.sh` passa `observability/prometheus/rules/link-pulse-alerts.yml` e cada JSON de `observability/grafana/dashboards/` como values. O chart renderiza o PrometheusRule (com `fromYaml`, porque `spec.groups` tem o mesmo formato do arquivo, conferido no tipo Go do Operator) e um ConfigMap por dashboard. Sem cópia e sem arquivo gerado. Os JSONs têm `{{method}}`, `{{uri}}` e outras legendas do Grafana, então o conteúdo nunca pode passar por `tpl`. (2) **O Traefik apaga os `X-Forwarded-*` vindos de IP não confiável** (lido em `forwarded_header.go` do v3.7.14: `if !x.insecure && !x.isTrustedIP(r.RemoteAddr) { DeleteXForwardedHeaders(r.Header) }`) e depois acrescenta o RemoteAddr. Com `trustedIPs` vazio (o default do chart) e o app confiando só em `10.244.0.0/16`, o cliente não consegue forjar o IP do rate limit. (3) **O Docker 29 com containerd image store quebra o `kind load docker-image`** (`ctr: content digest ... not found`; a doc de known issues do kind ganhou em set/2026 o contorno `docker image save --platform` + `kind load image-archive`). O Docker local é o 29.8.0.

**Primary recommendation:** a lógica fica em `deploy/kind/up.sh` (bash, roda igual no Git Bash e no CI), com `--context kind-link-pulse` em todos os comandos. A ordem é: kind (digest fixado, `podSubnet` explícito, hostPort 80 em 127.0.0.1 → nodePort 30080) → imagem com tag única via `image-archive` → metrics-server → Traefik (NodePort, `externalTrafficPolicy: Local`) → kps enxuto (seletores abertos, Alertmanager e regras/dashboards default desligados) → chart `deps` → chart do app com `--set-file` da `observability/`. No CI, o mesmo `make k8s` roda num job que recebe a imagem como artifact do job do Trivy, e só a imagem que passou no scan e no smoke é publicada no GHCR.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Entrada HTTP do host (`*.localtest.me:80`) | Container runtime (Docker port mapping do kind, só 127.0.0.1) | Ingress controller (Traefik) | O kind só expõe o que está em `extraPortMappings`; o Traefik roteia por Host |
| Roteamento por hostname e IP real do cliente | Ingress (Traefik, `X-Forwarded-For`) | API (RemoteIpValve do Tomcat com CIDR dos pods) | O proxy define o XFF; o app decide em quem confiar |
| Encurtar, redirecionar, stats, rate limit | API (pod do app) | Valkey/Postgres/Mongo (chart `deps`) | Sem mudança de código; só config por env |
| Saúde e ciclo de vida do pod | Kubelet (probes na 8081) | API (health groups do Actuator) | Liveness só estado interno; readiness com db+redis |
| Escala horizontal | Control plane (HPA autoscaling/v2) | metrics-server | O HPA de CPU precisa do metrics-server e de `requests.cpu` |
| Persistência dos dados | StatefulSets do chart `deps` (PVC no local-path do kind) | — | Dev-only, imagens oficiais |
| Scrape, regras e alertas | Prometheus Operator (ServiceMonitor/PrometheusRule) | Chart do app (gera os objetos) | O app declara o que é raspado; o operator transforma em config |
| Dashboards | Sidecar do Grafana (ConfigMaps rotulados) | Chart do app (ConfigMaps a partir de `observability/`) | Fonte única no repositório |
| Build, scan, SBOM, publicação | CI (GitHub Actions) | GHCR | Imagem testada é a publicada |

## Project Constraints (from CLAUDE.md)

- Traefik v3 como Ingress controller; **nunca ingress-nginx** (aposentado em mar/2026).
- **Nada de charts/imagens Bitnami**: Postgres, Mongo e Valkey como manifests próprios (StatefulSet + Service) com imagens oficiais (`postgres:18.6`, `mongo:8.0`, `valkey/valkey:8.1`) num chart `deps`.
- metrics-server com `args: [--kubelet-insecure-tls]` no kind (sem ele o HPA fica `<unknown>`).
- kube-prometheus-stack fixado por versão; dashboards como ConfigMaps com label `grafana_dashboard: "1"`.
- `kubeconform -strict` sobre o `helm template` no CI.
- Liveness **não** inclui dependências externas; readiness inclui `db` e `redis`; porta de gerenciamento 8081 com `health,info,prometheus`.
- Logs estruturados `logging.structured.format.console=ecs` no profile prod (values-prod).
- Actions de terceiros **fixadas por SHA** (incidente trivy-action mar/2026) + Dependabot.
- Imagem não-root numérica (10001) — o Helm usa `runAsUser: 10001`/`runAsNonRoot`.
- Sem Lombok, sem `@Cacheable`, sem Jedis (sem impacto nesta fase).
- Windows: GNU make via winget/scoop; Git Bash não traz make; o Makefile usa `NOCONV` para o docker.
- Comentários e documentação em pt-BR; segredos de demonstração como defaults comentados "não é segredo".
- Commits sem co-autoria do Claude (memória do usuário).
- Fluxo GSD: edições só dentro de `/gsd-execute-phase`.

## Standard Stack

Versões conferidas nesta sessão pela API de releases do GitHub, pelos `Chart.yaml`/`values.yaml` nas tags e por manifest OCI anônimo no GHCR.

### Core (cluster local)

| Componente | Versão | Propósito | Por que / delta vs CLAUDE.md | Fonte |
|------------|--------|-----------|------------------------------|-------|
| kind | **v0.33.0** (2026-08-26) | Cluster local | Igual ao CLAUDE.md. Node padrão `kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5` | [VERIFIED: GitHub release kubernetes-sigs/kind v0.33.0] |
| Helm | **v4.3.1** (2026-10-15) | Charts | **Delta:** CLAUDE.md diz 4.3.0. A 4.3.1 traz "fix(action): wait for CRD establishment under HookOnlyStrategy #32672" | [VERIFIED: GitHub release helm/helm v4.3.1] |
| kubectl | v1.37.1 (CI) / v1.36.1 (local, skew de 1 minor é suportado) | CLI | Default do `helm/kind-action` v1.15.1 e já instalado no runner | [VERIFIED: action.yml kind-action v1.15.1; runner-images Ubuntu2404-Readme] |
| Traefik chart | **42.1.0** (2026-10-20, Traefik v3.7.15) | Ingress controller | **Delta:** CLAUDE.md diz 41.6.1 (v3.7.13). O v3.7.15 corrige GHSA-p8wx-ph99-xgcr, GHSA-ww7c-q3mv-xx22 e GHSA-28hp-c82p-8vqj. A 42.0.0 só quebra para Traefik Hub e exige K8s ≥ 1.30 (o kind roda 1.37) | [VERIFIED: GitHub releases traefik-helm-chart v42.0.0/v42.1.0, traefik v3.7.15] |
| metrics-server chart | **3.14.0** (app v0.9.0) | Métricas do HPA | Igual ao CLAUDE.md. Repo HTTP `https://kubernetes-sigs.github.io/metrics-server/` (o index tem a 3.14.0) | [VERIFIED: Chart.yaml na tag metrics-server-helm-chart-3.14.0] |
| kube-prometheus-stack | **92.10.1** (2026-10-16) | Prometheus Operator, Prometheus, Grafana | **Delta:** CLAUDE.md diz 92.0.0. A 92.10.1 traz o subchart grafana 14.0.1 (appVersion 13.2.4, que corrige 4 CVEs) e mantém o Operator v0.94.1 e o Prometheus `v3.15.0-distroless`. A 92.11.0 (de hoje) só muda o CI do repositório | [VERIFIED: Chart.yaml/values.yaml nas tags 92.0.0, 92.10.1 e 92.11.0; release grafana v13.2.4] |
| kubeconform | **v0.8.0** (imagem `ghcr.io/yannh/kubeconform:v0.8.0`) | Validar o `helm template` | Os schemas `v1.37.1-standalone-strict` existem no `yannh/kubernetes-json-schema`; o catálogo `datreeio/CRDs-catalog` tem `monitoring.coreos.com/servicemonitor_v1.json` e `prometheusrule_v1.json` | [VERIFIED: GitHub API (releases, git tree e contents)] |
| Imagens do chart `deps` | `postgres:18.6`, `valkey/valkey:8.1`, `mongo:8.0` | Dependências | As mesmas do compose (compose.yaml:13, :31, :41) | [VERIFIED: compose.yaml:13-43; Docker Hub tags API] |

### Supporting (CI)

| Action / ferramenta | Versão | SHA (commit) | Uso |
|---------------------|--------|--------------|-----|
| `actions/checkout` | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | já no ci.yml |
| `actions/upload-artifact` | v7.0.2 | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` | já no ci.yml |
| `actions/download-artifact` | v8.0.2 | `9000827ccba6bdab643e8b6fd33ac0654aef8333` | imagem/SBOM entre jobs (v8 falha em hash divergente por padrão) |
| `docker/setup-buildx-action` | v4.4.1 | `f87e5991a6d7451dcb8d9637bfbc97413f497069` | buildx |
| `docker/build-push-action` | v7.4.0 | `c3c9e263c25d99ce0380d002d59b67737d91b0dc` | build com `load: true` + cache gha |
| `docker/login-action` | v4.6.0 | `dbcb813823bdd20940b903addbd779551569679f` | GHCR |
| `docker/metadata-action` | v6.2.0 | `dc802804100637a589fabce1cb79ff13a1411302` | tags `sha-<7>` e `latest`; **minusculiza o nome da imagem** |
| `aquasecurity/trivy-action` | v0.36.0 | `ed142fd0673e97e23eac54620cfb913e5ce36c25` | scan + SBOM; usa por dentro `setup-trivy@3fb12ec…` (v0.2.6, já corrigido); default `version: v0.70.0` → **passar `version: v0.76.0`** |
| `actions/attest` | v4.2.2 | `1e69f48acb82d1966a394da916b4c1698aa569d6` | provenance SLSA e SBOM attestation (o `attest-sbom` está em depreciação em favor dele) |
| `helm/kind-action` | v1.15.1 | `1544676c07bb3570fcb70166beaa07c4528fd4aa` | `install_only: true` (kind v0.33.0 + kubectl v1.37.1) |
| `azure/setup-helm` | v5.0.1 | `9bc31f4ebc9c6b171d7bfbaa5d006ae7abdb4310` | `version: v4.3.1` (o runner traz Helm 3.22.0) |
| `github/codeql-action/upload-sarif` (opcional) | v4.39.0 | `6f2ac81f5cd81fe351a197ba0b00d91d03cd2aee` | SARIF do Trivy na aba Security |

[VERIFIED: GitHub API `git/ref/tags/<tag>`, com tags anotadas desreferenciadas até o commit nesta sessão]

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `--set-file` da `observability/` | `scripts/sync-observability.sh` copiando para `files/` (gitignored) + `.Files.Get` (ARCHITECTURE.md) | Funciona, mas cria uma segunda cópia que pode envelhecer e exige o sync antes de qualquer `helm template`. O `--set-file` é nativo e o mesmo comando serve no CI |
| `--set-file` | `kubectl create configmap --from-file … \| kubectl label --local … \| kubectl apply` | Os objetos ficam fora do release do Helm (`helm uninstall` não os remove), e o PrometheusRule teria de ser montado com `sed` |
| Symlink `deploy/helm/link-pulse/files → observability` | — | **Rejeitado:** o Git no Windows sem `core.symlinks`/modo desenvolvedor faz checkout do symlink como arquivo-texto |
| Traefik por NodePort 30080 | `ports.web.hostPort: 80` no pod (portmap CNI) | Os dois funcionam no nó único. NodePort desacopla do agendamento do pod e é o exemplo da doc do kind ("the kind node `containerPort` and the service `nodePort` needs to be equal") |
| kps 92.10.1 | 92.0.0 (Grafana 13.2.3, igual ao compose) | A 92.0.0 dá paridade exata de versão, mas sem os fixes de CVE do 13.2.4. Recomendado: 92.10.1 e subir o compose para `grafana/grafana:13.2.4` |
| Publicar a imagem testada (load do tar + push) | Rebuild com `build-push-action push: true` usando o cache gha | O rebuild é mais simples, mas o digest publicado não é o que passou no Trivy e no smoke (o timestamp do config muda sem `SOURCE_DATE_EPOCH`) |
| SBOM pelo Trivy (CycloneDX) | `anchore/sbom-action` (syft) ou `sbom: true` no build-push-action | O Trivy já está fixado e auditado no pipeline; outra action de terceiro aumenta a superfície. O SBOM do BuildKit só é anexado no push |

**Instalação local (Windows):**
```bash
winget install Kubernetes.kind Helm.Helm   # kind 0.33.0 e Helm 4.3.1 no winget hoje
# kubectl já vem com o Docker Desktop (v1.36.1 aqui; skew de 1 minor é suportado)
```
[VERIFIED: `winget show` nesta sessão → kind 0.33.0, Helm 4.3.1]

## Package Legitimacy Audit

Nenhum pacote de registry (npm/PyPI/crates/Maven novo) é instalado nesta fase; o gate `package-legitimacy` não se aplica. Os artefatos externos são binários, charts e actions, todos conferidos na origem:

| Artefato | Origem | Verificação | Disposition |
|----------|--------|-------------|-------------|
| kind v0.33.0 | github.com/kubernetes-sigs/kind | release + digest do node image | Approved |
| Helm v4.3.1 | github.com/helm/helm, get.helm.sh (sha256 nas notas) | release | Approved |
| Traefik chart 42.1.0 | `oci://ghcr.io/traefik/helm/traefik` | manifest OCI anônimo 200 + Chart.yaml na tag | Approved |
| kube-prometheus-stack 92.10.1 | `oci://ghcr.io/prometheus-community/charts/kube-prometheus-stack` | manifest OCI anônimo 200 + Chart.yaml na tag | Approved |
| metrics-server chart 3.14.0 | `https://kubernetes-sigs.github.io/metrics-server/` | index.yaml contém 3.14.0 | Approved |
| kubeconform v0.8.0 | `ghcr.io/yannh/kubeconform:v0.8.0` | manifest OCI 200 | Approved |
| Actions (tabela acima) | GitHub | SHA resolvido por `git/ref/tags` | Approved (pin por SHA) |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```text
                 Avaliador (navegador/curl no host)            GitHub Actions (push/PR)
                          │ http://*.localtest.me (DNS → 127.0.0.1)        │
                          ▼                                                 ▼
  ┌── Docker port mapping 127.0.0.1:80 ──┐                  build-test (já existe)
  │   (extraPortMappings do kind)        │                  image: build(load) → Trivy gate → SBOM → tar
  └───────────────┬──────────────────────┘                          │ artifact
                  ▼ nodePort 30080 (eTP Local)                       ▼
  ┌───────────── kind node (K8s 1.37) ───────────────────┐   k8s-smoke: kind-action(install_only)
  │  Traefik (ns traefik)                                │     → docker load → make k8s → make k8s-smoke
  │   • apaga X-Forwarded-* de IP não confiável          │            │ (só na main, se tudo passou)
  │   • acrescenta RemoteAddr ao XFF                     │            ▼
  │   • Host() → Ingress                                 │   publish: docker load → tag/push GHCR
  │      ├─ link-pulse.localtest.me ─► Service link-pulse:8080      → attest provenance + SBOM
  │      ├─ grafana.localtest.me ───► Grafana (kps)      │
  │      └─ prometheus.localtest.me ► Prometheus (kps)   │
  │                                                      │
  │  ns link-pulse                                       │
  │   app pod(s) ─ HPA(CPU) ◄─ metrics-server            │
  │    ├ 8080 API  (RemoteIpValve confia em 10.244/16)   │
  │    └ 8081 actuator: liveness(livenessState)          │
  │                     readiness(readinessState,db,redis)
  │        │ JDBC        │ Lettuce        │ driver        │
  │        ▼             ▼                ▼               │
  │     postgres      valkey(volatile-lru)  mongo         │  (chart deps, StatefulSets)
  │                                                      │
  │  ns monitoring (kps)                                 │
  │   Operator ─ ServiceMonitor(job link-pulse) ─► scrape 8081/actuator/prometheus
  │            ─ PrometheusRule(4 alertas) ─► /alerts    │
  │   Grafana sidecar ◄─ ConfigMaps grafana_dashboard="1"│
  └──────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```text
deploy/
├── kind/
│   ├── kind-config.yaml            # digest do node, podSubnet, 127.0.0.1:80 → 30080
│   ├── up.sh                       # make k8s: lógica toda em bash (Git Bash e CI)
│   ├── smoke.sh                    # make k8s-smoke: link → 302 → stats (+ checagens de observabilidade)
│   └── values/
│       ├── traefik.yaml
│       ├── metrics-server.yaml
│       └── kube-prometheus-stack.yaml
└── helm/
    ├── link-pulse/                 # chart do app
    │   ├── Chart.yaml  values.yaml  values-dev.yaml  values-prod.yaml
    │   └── templates/ _helpers.tpl deployment.yaml service.yaml ingress.yaml configmap.yaml
    │                  secret.yaml hpa.yaml servicemonitor.yaml prometheusrule.yaml
    │                  dashboards-configmap.yaml NOTES.txt
    └── deps/                       # dev-only: postgres, valkey, mongo (imagens oficiais)
        ├── Chart.yaml  values.yaml
        └── templates/ postgres.yaml valkey.yaml mongo.yaml secret.yaml
.kind/                              # gitignored: tar da imagem para o kind load
```

`deploy/` segue a sugestão do CONTEXT. O ARCHITECTURE.md propunha `helm/` e `k8s/` na raiz; qualquer dos dois serve, mas a escolha precisa ser uma só e entrar no `observability/README.md` (a tabela "kind (Phase 4)" de lá tem de ser atualizada com o mecanismo real).

### Pattern 1: kind-config com digest, podSubnet explícito e porta configurável

**What:** um nó control-plane, imagem fixada por digest, `podSubnet` declarado (é o CIDR que o app vai confiar) e a porta do host em 127.0.0.1.
**When to use:** sempre. O override da porta vem do `up.sh` por `sed` + `--config=-` (o kind lê config do stdin: "handle config flag, we might need to read from stdin", em `createcluster.go` da v0.33.0).

```yaml
# deploy/kind/kind-config.yaml
# Cluster do make k8s. A porta 80 do host (só 127.0.0.1) cai no nodePort 30080 do Traefik.
# Porta 80 ocupada (IIS, http.sys)? make k8s K8S_HTTP_PORT=8080 troca o hostPort abaixo.
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
networking:
  # Explícito porque o app confia neste CIDR (SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES).
  podSubnet: "10.244.0.0/16"
  serviceSubnet: "10.96.0.0/16"
nodes:
  - role: control-plane
    image: kindest/node:v1.37.0@sha256:a1ed56cfb0e7b93589bdf97c8cd566405a265939e3620fc4f5de89adff580ae5
    extraPortMappings:
      - containerPort: 30080
        hostPort: 80
        listenAddress: "127.0.0.1"
        protocol: TCP
```
Fontes: defaults "`10.244.0.0/16` pod subnet" e "`10.96.0.0/16` service subnet", `listenAddress` e o exemplo NodePort [CITED: kind.sigs.k8s.io/docs/user/configuration]; digest [VERIFIED: release kind v0.33.0].

### Pattern 2: Traefik no kind (NodePort 30080, IP do cliente preservado)

```yaml
# deploy/kind/values/traefik.yaml (chart 42.1.0)
ingressClass:
  enabled: true
  isDefaultClass: true
  name: traefik            # ingressClassName dos Ingress do app, do Grafana e do Prometheus
service:
  spec:                    # no chart 42.x o tipo fica em service.spec
    type: NodePort
    # Sem SNAT do kube-proxy: o Traefik vê o IP de origem do port mapping, não o do nó.
    externalTrafficPolicy: Local
ports:
  web:
    nodePort: 30080        # = containerPort do extraPortMappings
    # forwardedHeaders.trustedIPs fica vazio (default): o Traefik apaga X-Forwarded-* do cliente.
  websecure:
    expose:
      default: false       # sem TLS no kind
```
[VERIFIED: traefik-helm-chart v42.0.0/v42.1.0 values.yaml: `service.spec.type: LoadBalancer` como default, `ports.web.nodePort`, `ports.web.forwardedHeaders.trustedIPs: []`, `insecure: false`, `ingressClass.name: ""` → `default (include "traefik.fullname" .)`]. O dashboard do Traefik não é exposto por padrão (`ingressRoute.dashboard.enabled: false`, porta `traefik` com `expose.default: false`). O `Host()` do Traefik ignora a porta do header (o `requestdecorator.parseHost` usa `net.SplitHostPort`), então `K8S_HTTP_PORT=8080` não quebra o roteamento [VERIFIED: traefik v3.7.15 `pkg/middlewares/requestdecorator/request_decorator.go`].

### Pattern 3: metrics-server

```yaml
# deploy/kind/values/metrics-server.yaml (chart 3.14.0)
# Os kubelets do kind usam certificado autoassinado.
args:
  - --kubelet-insecure-tls
```
`defaultArgs` já trazem `--kubelet-preferred-address-types=InternalIP,ExternalIP,Hostname` e `--metric-resolution=15s`; `args: []` é somado a eles [VERIFIED: values.yaml metrics-server-helm-chart-3.14.0]. O README do metrics-server: "disable certificate validation by passing `--kubelet-insecure-tls`… For testing purposes only" [VERIFIED: README v0.9.0]. Depois do install: `kubectl wait --for=condition=Available apiservice/v1beta1.metrics.k8s.io --timeout=180s`.

### Pattern 4: kube-prometheus-stack enxuto e com seletores abertos

**What:** sem os seletores abertos o Prometheus só pega `ServiceMonitor`/`PrometheusRule` com `release: <release>`. No template da 92.x: `{{ else if .Values.prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues }} serviceMonitorSelector: matchLabels: release: {{ $.Release.Name | quote }}`, e o mesmo vale para o `ruleSelector`. Com `false` e seletor `{}`, ele seleciona tudo em todos os namespaces (`serviceMonitorNamespaceSelector: {}`) [VERIFIED: templates/prometheus/prometheus.yaml, kube-prometheus-stack-92.0.0, linhas 203-217 e 287-296].

```yaml
# deploy/kind/values/kube-prometheus-stack.yaml (chart 92.10.1)
# Enxuto para laptop: só o que o Link Pulse usa. Sem Alertmanager: o firing aparece em /alerts.
alertmanager:
  enabled: false
defaultRules:
  create: false            # /alerts mostra só os 4 alertas do Link Pulse, como no compose
nodeExporter:
  enabled: false
kubeStateMetrics:
  enabled: false
# Componentes do control plane que o kind não expõe: viraria target vermelho em /targets.
kubeControllerManager: { enabled: false }
kubeScheduler: { enabled: false }
kubeEtcd: { enabled: false }
kubeProxy: { enabled: false }

prometheus:
  ingress:
    enabled: true
    ingressClassName: traefik
    hosts: [prometheus.localtest.me]   # externalUrl sai daqui (template usa index hosts 0)
    paths: [/]
    pathType: Prefix
  prometheusSpec:
    scrapeInterval: 15s      # igual ao compose; também vira o timeInterval do datasource
    evaluationInterval: 15s
    retention: 2d
    retentionSize: 1GB
    serviceMonitorSelectorNilUsesHelmValues: false
    podMonitorSelectorNilUsesHelmValues: false
    ruleSelectorNilUsesHelmValues: false
    resources:
      requests: { cpu: 200m, memory: 512Mi }
      limits: { memory: 1Gi }

grafana:
  defaultDashboardsEnabled: false
  # Não é segredo: senha de demonstração do admin (o anônimo é só Viewer).
  adminPassword: admin
  ingress:
    enabled: true
    ingressClassName: traefik
    hosts: [grafana.localtest.me]
    path: /
    pathType: Prefix
  grafana.ini:
    server:
      root_url: http://grafana.localtest.me
    auth.anonymous:
      enabled: true
      org_role: Viewer
    users:
      home_page: /d/link-pulse-overview   # não usar default_home_dashboard_path (403 no Grafana 13)
    analytics:
      reporting_enabled: false
      check_for_updates: false
  sidecar:
    dashboards:
      # Só dashboards do Link Pulse passam pelo sidecar: a pasta e o UID ficam iguais aos do compose.
      provider:
        folder: Link Pulse
        folderUid: link-pulse
        disableDelete: true
        allowUiUpdates: false
    datasources:
      alertmanager:
        enabled: false     # o template renderiza o datasource do Alertmanager mesmo com ele desligado
```

Fatos verificados nos values da 92.0.0 e do subchart grafana 13.2.7/14.0.1: `sidecar.dashboards.label: grafana_dashboard`, `labelValue: "1"`, `searchNamespace: ALL`; `sidecar.datasources.uid: prometheus`, `defaultDatasourceEnabled: true`, `isDefaultDatasource: true`; `provider.folder: ''`, `provider.folderUid: ''` ("will be automatically generated if not specified"); `adminUser: admin` com `# adminPassword: strongpassword` comentado (sem senha, o chart gera uma aleatória); `assertNoLeakedSecrets: true` (a senha não pode ir no `grafana.ini`). A imagem é `grafana/grafana:{{ .Chart.AppVersion }}-distroless` e o Prometheus `quay.io/prometheus/prometheus:v3.15.0-distroless` [VERIFIED: values.yaml nas tags]. O datasource do Alertmanager entra se `grafana.sidecar.datasources.alertmanager.enabled` for verdadeiro, sem olhar `alertmanager.enabled` [VERIFIED: templates/grafana/configmaps-datasources.yaml].

O UID `prometheus` do datasource padrão é o que os dashboards usam: `"datasource": { "type": "prometheus", "uid": "prometheus" }` e o provisioning do compose declara `uid: prometheus` [VERIFIED: observability/grafana/provisioning/datasources/prometheus.yml:7]. A pasta e o UID do compose: `folder: Link Pulse` e `folderUid: link-pulse` [VERIFIED: observability/grafana/provisioning/dashboards/link-pulse.yml:8-10].

Se o planejador quiser os dashboards de cluster do kps, a troca é: `defaultDashboardsEnabled: true` + `defaultRules.create: true` + `kubeStateMetrics`/`nodeExporter` ligados, e a pasta passa a vir de `sidecar.dashboards.folderAnnotation: grafana_folder` com `provider.foldersFromFilesStructure: true` (aí o UID da pasta não fica fixo). No Docker Desktop, o node-exporter costuma exigir `prometheus-node-exporter.hostRootFsMount.enabled: false` [ASSUMED].

### Pattern 5: chart `deps` (StatefulSets com imagens oficiais)

Espelho do compose (compose.yaml:12-50): Services com os mesmos nomes (`postgres`, `valkey`, `mongo`), para as env vars do app serem as mesmas do compose.

```yaml
# deploy/helm/deps/templates/postgres.yaml (trecho)
apiVersion: apps/v1
kind: StatefulSet
metadata: { name: postgres }
spec:
  serviceName: postgres
  replicas: 1
  selector: { matchLabels: { app.kubernetes.io/name: postgres } }
  template:
    metadata: { labels: { app.kubernetes.io/name: postgres } }
    spec:
      containers:
        - name: postgres
          image: postgres:18.6
          env:
            - { name: POSTGRES_DB, value: linkpulse }
            - { name: POSTGRES_USER, value: linkpulse }
            - name: POSTGRES_PASSWORD
              valueFrom: { secretKeyRef: { name: postgres-credentials, key: password } }
          ports: [{ name: postgres, containerPort: 5432 }]
          readinessProbe:
            # -h 127.0.0.1: por TCP, para não dar "pronto" no servidor temporário do initdb.
            exec: { command: ["pg_isready", "-h", "127.0.0.1", "-U", "linkpulse", "-d", "linkpulse"] }
            periodSeconds: 5
          volumeMounts:
            # Postgres 18: PGDATA em /var/lib/postgresql/18/docker; monta-se o diretório pai.
            - { name: data, mountPath: /var/lib/postgresql }
  volumeClaimTemplates:
    - metadata: { name: data }
      spec: { accessModes: [ReadWriteOnce], resources: { requests: { storage: 1Gi } } }
```
- Valkey: `args: ["--maxmemory", "256mb", "--maxmemory-policy", "volatile-lru"]` (o comando do compose é `["valkey-server", "--maxmemory", "256mb", "--maxmemory-policy", "volatile-lru"]` [VERIFIED: compose.yaml:32]), limite de memória ≥ 384Mi, sem PVC (o compose também não persiste o Valkey), probe `valkey-cli ping`.
- Mongo: `args: ["--wiredTigerCacheSizeGB", "0.25"]` [VERIFIED: compose.yaml:43], PVC em `/data/db`. Readiness `mongosh --quiet --eval "db.adminCommand('ping')"` com `periodSeconds: 10` e `timeoutSeconds: 5` (o mongosh é pesado), liveness `tcpSocket: 27017`.
- O StorageClass default do kind é o `standard` (local-path-provisioner) [ASSUMED].

### Pattern 6: chart do app

**Deployment** (pontos não óbvios):

```yaml
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}      # com HPA, omitir: cada upgrade resetaria a contagem
  {{- end }}
  template:
    metadata:
      annotations:
        # Rollout automático quando ConfigMap/Secret mudam.
        checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
        checksum/secret: {{ include (print $.Template.BasePath "/secret.yaml") . | sha256sum }}
    spec:
      automountServiceAccountToken: false   # o app não fala com a API do Kubernetes
      enableServiceLinks: false             # sem POSTGRES_PORT=tcp://... e afins no env do Spring
      terminationGracePeriodSeconds: 60     # preStop 10 s + fases de shutdown de até 30 s (compose: 45 s)
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        seccompProfile: { type: RuntimeDefault }
      containers:
        - name: link-pulse
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}   # dev: Never (imagem veio do kind load)
          envFrom:
            - configMapRef: { name: {{ include "link-pulse.fullname" . }} }
            - secretRef: { name: {{ .Values.secret.existingSecret | default (include "link-pulse.fullname" .) }} }
          ports:
            - { name: http, containerPort: 8080 }
            - { name: management, containerPort: 8081 }
          startupProbe:          # JVM + Liquibase: até 180 s antes de a liveness valer
            httpGet: { path: /actuator/health/liveness, port: management }
            periodSeconds: 2
            failureThreshold: 90
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: management }
            periodSeconds: 10
            timeoutSeconds: 2
            failureThreshold: 3
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: management }
            periodSeconds: 5
            timeoutSeconds: 2
            failureThreshold: 3
          lifecycle:
            preStop:
              sleep: { seconds: 10 }   # tempo para o pod sair dos endpoints antes do SIGTERM
          resources:
            requests: { cpu: 250m, memory: 512Mi }   # requests.cpu é a base do HPA
            limits: { memory: 1Gi }                  # sem limite de CPU (throttling na subida da JVM)
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: { drop: [ALL] }
          volumeMounts:
            - { name: tmp, mountPath: /tmp }   # java.io.tmpdir do Tomcat
      volumes:
        - { name: tmp, emptyDir: {} }
```
A imagem já é `USER 10001:10001`, `EXPOSE 8080 8081` e `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"` [VERIFIED: app/Dockerfile:37-40]: com `limits.memory: 1Gi` o heap máximo fica em ~768Mi. O `sleep` nativo do preStop é beta e ligado por padrão desde o 1.30 [CITED: kubernetes.dev/resources/keps/3960; v1-33 feature gates], ok no 1.37. Se o kubeconform reclamar, a alternativa é `exec: { command: ["sleep", "10"] }` (a imagem é Ubuntu e tem `sleep`).

**ConfigMap (values-dev = kind):** as mesmas chaves do compose (compose.yaml:57-68), com o kind no lugar do compose:

| Chave | Valor no kind | Origem no compose (verbatim) |
|-------|---------------|------------------------------|
| `SPRING_PROFILES_ACTIVE` | `prod` | `SPRING_PROFILES_ACTIVE: prod` (compose.yaml:57) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/linkpulse` | idem (:58) |
| `SPRING_DATASOURCE_USERNAME` | `linkpulse` | idem (:59) |
| `SPRING_DATA_REDIS_HOST` / `_PORT` | `valkey` / `"6379"` | idem (:61-62) |
| `SPRING_MONGODB_URI` | `mongodb://mongo:27017/linkpulse` | idem (:63) |
| `LINKPULSE_BASE_URL` | `http://link-pulse.localtest.me` (D-03; com porta se `K8S_HTTP_PORT≠80`) | `LINKPULSE_BASE_URL: http://localhost:8080` (:66) |
| `LINKPULSE_SELF_HOSTS` | `link-pulse,link-pulse.link-pulse,link-pulse.link-pulse.svc,link-pulse.link-pulse.svc.cluster.local` | `LINKPULSE_SELF_HOSTS: app` (:68) |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | `10.244.0.0/16` (= `podSubnet`) | ausente no compose (default `internal-proxies: ""`, application.yml:62) |

**Secret:** `SPRING_DATASOURCE_PASSWORD` e `LINKPULSE_IP_HASH_KEY`. No values-dev, defaults de demonstração comentados ("não é segredo"; chave ≥ 16 caracteres, como a do compose `compose-demo-ip-hash-key-not-a-secret` [VERIFIED: compose.yaml:65]). No values-prod, `secret.create: false` + `secret.existingSecret`. A senha do Postgres do `deps` e a do app precisam bater (o mesmo default `linkpulse` do compose).

**values-prod:** `autoscaling` 2–6, `resources` maiores, `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`, `image.repository: ghcr.io/lucasllo/link-pulse`, `pullPolicy: IfNotPresent`, Ingress com host e TLS de exemplo, `topologySpreadConstraints`. Ele só é renderizado (`helm lint`/`template`/kubeconform), nunca instalado. Atenção ao nome: "values-dev" é o ambiente kind, mas o **profile Spring continua `prod`** (D-08), sem seed Liquibase. Isso precisa de um comentário no arquivo.

**Service** (nome = `link-pulse`, portas nomeadas):
```yaml
ports:
  - { name: http, port: 8080, targetPort: http }
  - { name: management, port: 8081, targetPort: management }   # o Ingress não roteia esta porta
```

**ServiceMonitor** (job `link-pulse` garantido): o Operator define que "If the value of this field is empty or if the label doesn't exist for the given Service, the `job` label of the metrics defaults to the name of the associated Kubernetes `Service`" [VERIFIED: prometheus-operator v0.94.1 `servicemonitor_types.go`]. Para não depender do fullname:
```yaml
{{- if .Values.metrics.serviceMonitor.enabled }}
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: {{ include "link-pulse.fullname" . }}
  labels: {{- include "link-pulse.labels" . | nindent 4 }}
spec:
  jobLabel: app.kubernetes.io/name        # o Service tem app.kubernetes.io/name: link-pulse
  selector:
    matchLabels: {{- include "link-pulse.selectorLabels" . | nindent 6 }}
  endpoints:
    - port: management                    # NOME da porta do Service
      path: /actuator/prometheus
      interval: 15s
{{- end }}
```
O alerta depende disso: `expr: up{job="link-pulse"} == 0 or absent(up{job="link-pulse"})` [VERIFIED: observability/prometheus/rules/link-pulse-alerts.yml:55]. O compose usa `job_name: link-pulse`, `metrics_path: /actuator/prometheus`, `targets: ["app:8081"]` [VERIFIED: observability/prometheus/prometheus.yml:13-16]. A flag `metrics.serviceMonitor.enabled` (e não `.Capabilities.APIVersions.Has`) mantém o `helm template` do CI determinístico.

**PrometheusRule e dashboards a partir de `observability/` (`--set-file`):** o Helm 4.3.1 documenta "`--set-file` set values from respective files specified via the command line (can specify multiple or separate values with commas: key1=path1,key2=path2)" [CITED: helm.sh/docs/helm/helm_upgrade, versão 4.3.1]. Os campos do `PrometheusRuleSpec` são `Groups []RuleGroup json:"groups"` com `name`, `interval`, `rules[]` (`alert`, `expr`, `for`, `labels`, `annotations`), idênticos ao arquivo de regras [VERIFIED: prometheus-operator v0.94.1 `prometheusrule_types.go`].

```yaml
# templates/prometheusrule.yaml
{{- if .Values.observability.enabled }}
{{- $src := required "observability.rules vazio: rode make k8s ou passe --set-file observability.rules=observability/prometheus/rules/link-pulse-alerts.yml" .Values.observability.rules }}
{{- $rules := $src | fromYaml }}
{{- if not $rules.groups }}{{ fail "observability.rules não tem groups: (YAML inválido?)" }}{{ end }}
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: {{ include "link-pulse.fullname" . }}
  labels: {{- include "link-pulse.labels" . | nindent 4 }}
spec:
  groups:
    {{- toYaml $rules.groups | nindent 4 }}
{{- end }}
```
```yaml
# templates/dashboards-configmap.yaml — um ConfigMap por JSON; o conteúdo NUNCA passa por tpl
{{- range $name, $json := .Values.observability.dashboards }}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ printf "%s-dashboard-%s" (include "link-pulse.fullname" $) $name | trunc 63 | trimSuffix "-" }}
  labels:
    grafana_dashboard: "1"
    {{- include "link-pulse.labels" $ | nindent 4 }}
data:
  {{ $name }}.json: |-
    {{- $json | nindent 4 }}
{{- end }}
```
Os JSONs não têm tabs e têm 14 KB e 11,5 KB (o limite do ConfigMap é 1 MiB). As legendas `"legendFormat": "{{method}} {{uri}} {{status}}"`, `"{{result}}/s"`, `"{{result}}"` e `"{{outcome}}"` aparecem em link-pulse-overview.json:129 e :282 e em link-pulse-pipeline.json:178 e :238 [VERIFIED: grep nesta sessão]. Como values não são renderizados como template, elas passam intactas.

**Image tag única:** com `pullPolicy: Never` e a tag fixa `local`, um segundo `make k8s` depois de mudar o código carrega a imagem nova, mas o Deployment não muda e o pod continua com a antiga. O `up.sh` deve criar a tag `link-pulse:local-<12 primeiros hex do .Id>`, carregar essa e passar `--set image.tag=…`.

### Pattern 7: health groups (única mudança no app)

Hoje o `application.yml` não tem grupos. Ele tem só:
```yaml
management:
  server:
    port: 8081
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```
[VERIFIED: app/src/main/resources/application.yml:118-125]

Doc do Boot 4.1.1: "Actuator configures the "liveness" and "readiness" probes as Health Groups… `management.endpoint.health.group.readiness.include=readinessState,customCheck`… By default, Spring Boot does not add other health indicators to these groups." Chaves da tabela: `db` (DataSourceHealthIndicator), `redis` (DataRedisHealthIndicator), `mongodb` (MongoHealthIndicator) [CITED: docs.spring.io/spring-boot/reference/actuator/endpoints.html, v4.1.1].

```yaml
management:
  endpoint:
    health:
      group:
        # Liveness só com o estado interno: Postgres, Valkey ou Mongo fora nunca reiniciam o pod.
        liveness:
          include: livenessState
        # Readiness com Postgres e Valkey (K8S-02). Mongo fora de propósito: só o consumer e o
        # /stats dependem dele; os cliques esperam no stream.
        readiness:
          include: readinessState,db,redis
```
O `HEALTHCHECK` do Dockerfile consulta `/actuator/health/readiness` [VERIFIED: app/Dockerfile:41-42]. Depois da mudança, o container do compose só fica healthy com Postgres e Valkey de pé, o que o `depends_on: service_healthy` já garante. Um IT novo pode provar a composição com `management.endpoint.health.group.readiness.show-components=always` no `@SpringBootTest` [ASSUMED: nome da propriedade por grupo]. O nome do consumer do stream vem do `HOSTNAME` (`String hostname = environment.getProperty("HOSTNAME");` [VERIFIED: ClickConfig.java:115-118]). No Kubernetes é o nome do pod, único por réplica, e o HPA não cria conflito no grupo.

### Pattern 8: imagem no kind sem quebrar no containerd image store

```bash
# No up.sh. A doc de known issues do kind (PR #4247, set/2026) recomenda save single-platform +
# image-archive quando o Docker usa containerd image store (Docker 29+).
IMG_ID=$(docker image inspect --format '{{.Id}}' link-pulse:local | sed 's/^sha256://' | cut -c1-12)
TAG="local-$IMG_ID"
docker tag link-pulse:local "link-pulse:$TAG"
PLATFORM=$(docker version --format '{{.Server.Os}}/{{.Server.Arch}}')
mkdir -p .kind
docker image save --platform "$PLATFORM" -o .kind/link-pulse.tar "link-pulse:$TAG" \
  || docker image save -o .kind/link-pulse.tar "link-pulse:$TAG"   # daemons sem --platform
kind load image-archive .kind/link-pulse.tar --name "$CLUSTER"
```
"`--platform` API 1.48+ Save only the given platform(s)" [CITED: docs.docker.com/reference/cli/docker/image/save]. O runner tem Docker 28.0.4 e o local, 29.8.0 [VERIFIED: runner-images Ubuntu2404-Readme; `docker --version`]. O fallback cobre o caso de a flag ser recusada [ASSUMED].

### Pattern 9: `make k8s` delega para `deploy/kind/up.sh`

O fluxo tem estado (cluster já existe? qual tag?), e num Makefile isso vira `$(shell)` avaliado na hora errada. O padrão "lógica em bash; o Makefile só delega" já está no ARCHITECTURE.md.

```bash
#!/usr/bin/env bash
# deploy/kind/up.sh — make k8s. Roda no Git Bash (Windows) e no Linux/CI.
set -euo pipefail
# Git Bash: sem conversão de /caminho para os .exe (docker, kind, kubectl, helm). Seguro aqui:
# este script não chama o ./mvnw.
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
cd "$(dirname "$0")/../.."                      # raiz do repo (aguenta espaço no caminho)

CLUSTER=${KIND_CLUSTER:-link-pulse}
CTX="kind-$CLUSTER"
PORT=${K8S_HTTP_PORT:-80}
K="kubectl --context $CTX"
H="helm --kube-context $CTX"
need() { command -v "$1" >/dev/null 2>&1 || { echo "Falta '$1'. Windows: winget install $2" >&2; exit 1; }; }
need docker "Docker.DockerDesktop"; need kind "Kubernetes.kind"; need kubectl "Kubernetes.kubectl"; need helm "Helm.Helm"

if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  sed "s/hostPort: 80\$/hostPort: $PORT/" deploy/kind/kind-config.yaml \
    | kind create cluster --name "$CLUSTER" --config=- --wait 120s
fi
# [Pattern 8: tag única + image-archive]
$H upgrade --install metrics-server metrics-server --repo https://kubernetes-sigs.github.io/metrics-server/ \
  --version 3.14.0 -n kube-system -f deploy/kind/values/metrics-server.yaml --wait --timeout 5m
$H upgrade --install traefik oci://ghcr.io/traefik/helm/traefik --version 42.1.0 \
  -n traefik --create-namespace -f deploy/kind/values/traefik.yaml --wait --timeout 5m
$H upgrade --install monitoring oci://ghcr.io/prometheus-community/charts/kube-prometheus-stack \
  --version 92.10.1 -n monitoring --create-namespace -f deploy/kind/values/kube-prometheus-stack.yaml \
  --wait --timeout 15m
$H upgrade --install deps deploy/helm/deps -n link-pulse --create-namespace --wait --timeout 5m
OBS=(--set-file observability.rules=observability/prometheus/rules/link-pulse-alerts.yml)
for f in observability/grafana/dashboards/*.json; do
  n=$(basename "$f" .json); OBS+=(--set-file "observability.dashboards.$n=$f")
done
$H upgrade --install link-pulse deploy/helm/link-pulse -n link-pulse \
  -f deploy/helm/link-pulse/values-dev.yaml --set image.tag="$TAG" "${OBS[@]}" --wait --timeout 10m
# [imprime as 3 URLs e o curl de exemplo (D-05)]
```
Flags do Helm 4.3.1: "`--wait` … Use '--wait' alone for 'watcher' strategy… Default when flag is omitted: 'hookOnly'"; `--rollback-on-failure` substitui o `--atomic` e `--force-replace` substitui o `--force` [CITED: helm.sh/docs/helm/helm_upgrade, 4.3.1]. OCI anônimo: manifest 200 para `traefik/helm/traefik:42.1.0` e `prometheus-community/charts/kube-prometheus-stack:92.x` [VERIFIED: GHCR token anônimo nesta sessão].

Makefile (padrão do `help` com `## `, `.PHONY` atualizado):
```make
k8s: image ## Cria o cluster kind e instala Traefik, metrics-server, Prometheus/Grafana, deps e o app
	bash deploy/kind/up.sh
k8s-smoke: ## Smoke no kind: cria link, segue o 302 e espera o clique no /stats
	bash deploy/kind/smoke.sh
k8s-down: ## Apaga o cluster kind (dados incluídos)
	$(NOCONV) kind delete cluster --name link-pulse
k8s-lint: ## helm lint + kubeconform dos charts (values dev e prod)
	bash deploy/kind/lint.sh
```
No CI, `make k8s` não pode depender de `image` (a imagem vem do artifact). Uma saída é uma variável `K8S_SKIP_BUILD=1` com um alvo intermediário, outra é o CI chamar `bash deploy/kind/up.sh` direto. O `help` usa `printf "  %-10s"`, e `k8s-smoke` cabe.

### Pattern 10: smoke test (`deploy/kind/smoke.sh`)

Contratos conferidos: request `@NotBlank @Size(max = 2048) @HttpUrl String url` [VERIFIED: CreateLinkRequest.java:18-21]; resposta `String code`, `URI shortUrl` [VERIFIED: LinkResponse.java:16-20]; stats `long total` [VERIFIED: StatsResponse.java:24-36].

```bash
#!/usr/bin/env bash
set -euo pipefail
PORT=${K8S_HTTP_PORT:-80}
HOST=link-pulse.localtest.me
BASE="http://$HOST"; [ "$PORT" = 80 ] || BASE="$BASE:$PORT"
# --resolve: não depende do DNS público (CI, roteadores com proteção a DNS rebinding) nem do ::1.
R=(--resolve "$HOST:$PORT:127.0.0.1" --max-time 10)
# UA de navegador (o mesmo de load/lib/common.js): curl conta como bot e sumiria do /stats padrão.
UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'
TARGET="https://example.com/smoke-$(date +%s)"

body=$(curl -fsS "${R[@]}" -A "$UA" -H 'Content-Type: application/json' \
  -d "{\"url\":\"$TARGET\"}" "$BASE/links")
code=$(printf '%s' "$body" | sed -n 's/.*"code":"\([^"]*\)".*/\1/p')
[ -n "$code" ] || { echo "POST /links sem code: $body" >&2; exit 1; }

read -r status location < <(curl -sS "${R[@]}" -A "$UA" -o /dev/null \
  -w '%{http_code} %{redirect_url}\n' "$BASE/$code")
[ "$status" = 302 ] && [ "$location" = "$TARGET" ] || { echo "redirect: $status $location" >&2; exit 1; }

for _ in $(seq 1 45); do       # pipeline assíncrono: stream → consumer → Mongo
  total=$(curl -fsS "${R[@]}" -A "$UA" "$BASE/links/$code/stats" | sed -n 's/.*"total":\([0-9]*\).*/\1/p')
  [ "${total:-0}" -ge 1 ] && { echo "smoke OK: $code"; exit 0; }
  sleep 2
done
echo "clique não apareceu no /stats em 90 s" >&2; exit 1
```
Sem `jq`: o host não tem jq (o runner tem jq 1.7). Uma criação só, bem abaixo do limite de 20 por minuto. Extensão recomendada para o critério 3, ainda no smoke e com polling de até 120 s:
- `curl -G --data-urlencode 'query=min(up{job="link-pulse"})' http://prometheus.localtest.me/api/v1/query` → valor `"1"`;
- `/api/v1/rules?type=alert` contém `LinkPulseTargetDown`;
- `http://grafana.localtest.me/api/dashboards/uid/link-pulse-overview` (anônimo) → 200 com `"folderUid":"link-pulse"`.

### Pattern 11: pipeline do CI

```text
build-test (existe) ─┐
image ───────────────┼─► k8s-smoke ──► publish (só push na main; needs: build-test, image, k8s-smoke)
```

- **Concorrência:** hoje está `cancel-in-progress: true` para todo ref [VERIFIED: .github/workflows/ci.yml:12-14]. Dois pushes seguidos na main cancelariam um publish no meio. Usar `cancel-in-progress: ${{ github.event_name == 'pull_request' }}`.
- **Permissões:** o topo continua `contents: read` [VERIFIED: ci.yml:9-10]; só o `publish` ganha `packages: write`, `id-token: write`, `attestations: write` e `artifact-metadata: write`. O README do `actions/attest` v4.2.2 lista os três últimos ("The `artifact-metadata` permission is necessary to create the artifact storage record") [VERIFIED: README na tag v4.2.2]. O comentário "nenhum secret é usado nesta fase" (ci.yml:8) precisa ser atualizado.
- **Caminho com espaço:** manter o checkout em `path: "link pulse"` (ci.yml:28) e `context: "link pulse/app"` no build.

```yaml
  image:
    runs-on: ubuntu-latest
    timeout-minutes: 25
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with: { path: "link pulse" }
      - uses: docker/setup-buildx-action@f87e5991a6d7451dcb8d9637bfbc97413f497069 # v4.4.1
      - uses: docker/build-push-action@c3c9e263c25d99ce0380d002d59b67737d91b0dc # v7.4.0
        with:
          context: "link pulse/app"
          load: true
          tags: link-pulse:local
          provenance: false        # attestations vêm do actions/attest no publish
          sbom: false
          cache-from: type=gha
          cache-to: type=gha,mode=max
      - name: Trivy (falha em CRITICAL com correção disponível)
        uses: aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25 # v0.36.0
        with:
          version: v0.76.0
          scan-type: image
          image-ref: link-pulse:local
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"
      - name: SBOM (CycloneDX)
        uses: aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25 # v0.36.0
        with:
          skip-setup-trivy: true
          scan-type: image
          image-ref: link-pulse:local
          format: cyclonedx
          output: sbom.cdx.json
      - run: docker image save -o link-pulse.tar link-pulse:local
      - uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with: { name: image, path: link-pulse.tar, retention-days: 1 }
      - uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with: { name: sbom, path: sbom.cdx.json }

  k8s-smoke:
    needs: image
    runs-on: ubuntu-latest
    timeout-minutes: 30
    defaults: { run: { working-directory: "link pulse" } }
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with: { path: "link pulse" }
      - uses: helm/kind-action@1544676c07bb3570fcb70166beaa07c4528fd4aa # v1.15.1
        with: { install_only: true, cluster_name: link-pulse, ignore_failed_clean: true }
      - uses: azure/setup-helm@9bc31f4ebc9c6b171d7bfbaa5d006ae7abdb4310 # v5.0.1
        with: { version: v4.3.1 }
      - run: bash deploy/kind/lint.sh            # falha rápido antes de subir o cluster
      - uses: actions/download-artifact@9000827ccba6bdab643e8b6fd33ac0654aef8333 # v8.0.2
        with: { name: image, path: "link pulse" }
      - run: docker load -i link-pulse.tar
      - run: bash deploy/kind/up.sh              # mesmo caminho do avaliador
      - run: bash deploy/kind/smoke.sh
      - if: failure()
        run: kubectl --context kind-link-pulse get pods -A -o wide; kubectl --context kind-link-pulse -n link-pulse describe pods; kubectl --context kind-link-pulse -n link-pulse logs -l app.kubernetes.io/name=link-pulse --tail=300

  publish:
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    needs: [build-test, image, k8s-smoke]
    runs-on: ubuntu-latest
    permissions: { contents: read, packages: write, id-token: write, attestations: write, artifact-metadata: write }
    steps:
      - uses: actions/download-artifact@9000827ccba6bdab643e8b6fd33ac0654aef8333 # v8.0.2
        with: { name: image }
      - uses: actions/download-artifact@9000827ccba6bdab643e8b6fd33ac0654aef8333 # v8.0.2
        with: { name: sbom }
      - run: docker load -i link-pulse.tar
      - uses: docker/login-action@dbcb813823bdd20940b903addbd779551569679f # v4.6.0
        with: { registry: ghcr.io, username: "${{ github.actor }}", password: "${{ secrets.GITHUB_TOKEN }}" }
      - id: meta
        uses: docker/metadata-action@dc802804100637a589fabce1cb79ff13a1411302 # v6.2.0
        with:
          images: ghcr.io/${{ github.repository }}     # Lucasllo → lucasllo (a action minusculiza)
          tags: |
            type=sha
            type=raw,value=latest,enable={{is_default_branch}}
      - id: push
        env: { TAGS: "${{ steps.meta.outputs.tags }}" }
        run: |
          for t in $TAGS; do docker tag link-pulse:local "$t"; docker push "$t"; done
          ref=$(docker image inspect --format '{{index .RepoDigests 0}}' "$(printf '%s\n' "$TAGS" | head -n1)")
          echo "name=${ref%@*}" >> "$GITHUB_OUTPUT"; echo "digest=${ref#*@}" >> "$GITHUB_OUTPUT"
      - uses: actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6 # v4.2.2
        with: { subject-name: "${{ steps.push.outputs.name }}", subject-digest: "${{ steps.push.outputs.digest }}", push-to-registry: true }
      - uses: actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6 # v4.2.2
        with: { subject-name: "${{ steps.push.outputs.name }}", subject-digest: "${{ steps.push.outputs.digest }}", sbom-path: sbom.cdx.json, push-to-registry: true }
```
O metadata-action garante "this action will automatically: Lowercase the image name", com `type=sha` gerando `sha-860c190` (7 caracteres) [VERIFIED: README docker/metadata-action v6.2.0]. Modos do `actions/attest`: "No `sbom-path` … Auto-generates SLSA build provenance"; "`sbom-path` is provided → Creates attestation from SPDX or CycloneDX SBOM" [VERIFIED: README v4.2.2]. Para o avaliador, a verificação é `gh attestation verify oci://ghcr.io/lucasllo/link-pulse:latest --owner Lucasllo`.

**Visibilidade do pacote:** "When you first publish a package, the default visibility is private"; o pacote ligado ao repositório "automatically inherits the access permissions (but not the visibility)"; "Once you make a package public, you cannot make it private again" [CITED: docs.github.com/en/packages/learn-github-packages/configuring-a-packages-access-control-and-visibility]. Depois do primeiro publish, há um passo manual único: *Package settings → Change visibility → Public*. É um checkpoint humano no plano. O `LABEL org.opencontainers.image.source="https://github.com/Lucasllo/link-pulse"` já liga o pacote ao repositório [VERIFIED: app/Dockerfile:28]. O repositório é PUBLIC [VERIFIED: `gh repo view`].

### Pattern 12: lint dos charts (`deploy/kind/lint.sh`)

```bash
helm lint --strict deploy/helm/link-pulse -f deploy/helm/link-pulse/values-dev.yaml "${OBS[@]}"
helm lint --strict deploy/helm/link-pulse -f deploy/helm/link-pulse/values-prod.yaml "${OBS[@]}"
helm lint --strict deploy/helm/deps
for v in dev prod; do
  helm template link-pulse deploy/helm/link-pulse -n link-pulse -f "deploy/helm/link-pulse/values-$v.yaml" --set image.tag=lint "${OBS[@]}"
done | docker run --rm -i ghcr.io/yannh/kubeconform:v0.8.0 -strict -summary \
  -kubernetes-version 1.37.1 -schema-location default \
  -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json'
helm template deps deploy/helm/deps | docker run --rm -i ghcr.io/yannh/kubeconform:v0.8.0 -strict -summary -kubernetes-version 1.37.1
```
No Git Bash, `docker run` precisa do `MSYS_NO_PATHCONV=1` (o script exporta). Rodar o kubeconform em container dispensa instalá-lo no Windows.

### Anti-Patterns to Avoid
- **`tpl` sobre o conteúdo dos dashboards** ou copiar os JSONs para `templates/`: as legendas `{{method}}` viram template do Helm e quebram o render.
- **`forwardedHeaders.insecure: true` ou `trustedIPs` largo no Traefik:** o cliente passa a injetar `X-Forwarded-For` e burla o rate limit (Pitfall P5 da Phase 2, agora no Ingress).
- **kubectl/helm sem `--context`:** se o contexto atual do avaliador apontar para um cluster real, o `make k8s` instala lá.
- **Imagem `:local` fixa com `pullPolicy: Never`:** o rollout nunca acontece depois de um rebuild.
- **`spec.replicas` com HPA ligado:** cada `helm upgrade` reseta a contagem.
- **Mongo na readiness:** derruba o encurtador inteiro por causa do analytics (ARCHITECTURE.md, Anti-padrão 2).
- **Rotular o ServiceMonitor com `release: monitoring`** em vez de abrir os seletores: acopla o chart do app ao nome do release do kps.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Ingress controller | nginx/haproxy próprio em Deployment | Traefik chart 42.1.0 | Atualização de CVE, IngressClass, forwarded headers |
| Prometheus + Grafana no cluster | manifests próprios de Prometheus/Grafana | kube-prometheus-stack | Operator, CRDs, sidecar, datasource com UID fixo |
| Descoberta de targets | `additionalScrapeConfigs` estático | ServiceMonitor | Endpoints dinâmicos com HPA; job pelo Service |
| Leitura de arquivos fora do chart | symlink, cópia gitignored | `helm --set-file` | Nativo, multiplataforma, sem estado |
| Nome minúsculo da imagem | `${GITHUB_REPOSITORY,,}` espalhado | `docker/metadata-action` | Já minusculiza e gera as tags |
| SBOM / assinatura | gerador próprio, cosign manual | Trivy CycloneDX + `actions/attest` | Sigstore público, verificável por `gh attestation verify` |
| Instalar kind/kubectl no runner | curl + checksum à mão | `helm/kind-action` (`install_only`) + `azure/setup-helm` | Versões fixadas e testadas |
| Validação de schema | yamllint ou checagem manual | kubeconform `-strict` + CRDs-catalog | Pega campos desconhecidos e tipos errados |
| Espera por readiness | `sleep 60` | `helm --wait` + `kubectl wait`/polling com timeout | Determinístico e falha com mensagem |

**Key insight:** quase tudo nesta fase já tem uma peça mantida pela comunidade. O valor do portfólio está em configurar essas peças certo e com segurança (seletores, proxies confiáveis, permissões, pins), e não em escrevê-las.

## Common Pitfalls

### Pitfall 1: porta 80 ocupada no Windows
**What goes wrong:** `kind create cluster` falha com "port is already allocated" ou bind negado (IIS, http.sys, Skype antigo).
**How to avoid:** `K8S_HTTP_PORT` (`make k8s K8S_HTTP_PORT=8080`). O `up.sh` ajusta o kind-config, o `LINKPULSE_BASE_URL` (`--set`), o `root_url` do Grafana (`--set-string 'grafana.grafana\.ini.server.root_url=…'`), o `externalUrl` do Prometheus e as URLs impressas. Diagnóstico: `netstat -ano | findstr :80` (aqui, nesta sessão, a porta 80 estava livre).
**Warning signs:** erro do Docker na criação do nó.

### Pitfall 2: `localtest.me` resolve também para `::1`
**What goes wrong:** o DNS devolve `::1` e `127.0.0.1` (conferido com `nslookup` nesta sessão), mas o kind só escuta em 127.0.0.1. O cliente pode tentar o IPv6 primeiro e esperar o fallback. Alguns roteadores com proteção a DNS rebinding bloqueiam respostas 127.0.0.1.
**How to avoid:** o smoke usa `--resolve`. O README documenta `curl -4` e o fallback de editar o hosts.

### Pitfall 3: `kind load docker-image` com containerd image store
**What goes wrong:** `ctr: content digest sha256:…: not found` (kind#3795).
**How to avoid:** Pattern 8 (`docker image save --platform` + `kind load image-archive`).

### Pitfall 4: comandos sem contexto explícito
**What goes wrong:** helm/kubectl agem no contexto atual do usuário.
**How to avoid:** `--context kind-link-pulse`/`--kube-context kind-link-pulse` em tudo. O kind cria o contexto `kind-<nome>` [ASSUMED: convenção do kind].

### Pitfall 5: conversão de caminhos do Git Bash
**What goes wrong:** `kubectl get --raw /apis/metrics.k8s.io/...` ou `docker run ... /etc/...` viram `C:/Program Files/Git/...`.
**How to avoid:** `export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'` no topo do `up.sh`, `smoke.sh` e `lint.sh`, que não chamam o mvnw. É o mesmo par do Makefile: `NOCONV := MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'` [VERIFIED: Makefile:10].

### Pitfall 6: ServiceMonitor/PrometheusRule invisíveis
**What goes wrong:** sem `*SelectorNilUsesHelmValues: false`, o Prometheus filtra por `release: monitoring`. Porta referenciada por número, ou não nomeada, no Service também impede o scrape.
**How to avoid:** Pattern 4 + `port: management` nomeada.
**Warning signs:** `/targets` sem `link-pulse`; `/rules` vazio.

### Pitfall 7: job diferente de `link-pulse`
**What goes wrong:** o `LinkPulseTargetDown` nunca casa (fica em firing eterno pelo `absent()`).
**How to avoid:** `jobLabel: app.kubernetes.io/name` + label no Service; o smoke confere `up{job="link-pulse"}`.

### Pitfall 8: dashboards fora da pasta ou duplicados
**What goes wrong:** com `defaultDashboardsEnabled: true` e `provider.folder: Link Pulse`, os dashboards do cluster também caem na pasta do Link Pulse.
**How to avoid:** escolher um dos dois modos do Pattern 4.

### Pitfall 9: readiness com Valkey derruba o redirect inteiro
**What goes wrong:** é o trade-off aceito (D-10). Com o Valkey fora, o pod fica NotReady, o Traefik responde 503 (`allowEmptyServices: true`) e o fail-open do REDIR-05 não chega a ser usado no kind.
**How to avoid:** nada a evitar; documentar no README (Phase 5) e mostrar no UAT que as restarts ficam em 0.
**Warning signs:** se `RESTARTS` subir, a liveness está dependendo de algo externo.

### Pitfall 10: probe de readiness lenta com o Postgres fora
**What goes wrong:** o `DataSourceHealthIndicator` espera uma conexão do Hikari (connectionTimeout padrão de 30 s) [ASSUMED]. A probe estoura o timeout de 2 s e conta como falha, o que dá o resultado certo (NotReady), mas segura uma thread por tentativa.
**How to avoid:** aceitável com virtual threads; opcional `spring.datasource.hikari.connection-timeout` menor.

### Pitfall 11: `--wait` do Helm 4 e o CR `Prometheus`
**What goes wrong:** a estratégia `watcher` (kstatus) pode não reconhecer as conditions do CR (`Available`/`Reconciled`) ou terminar antes de o StatefulSet existir [ASSUMED].
**How to avoid:** depois do kps, `kubectl -n monitoring rollout status statefulset/prometheus-monitoring-kube-prometheus-prometheus --timeout=10m` ou um `kubectl wait --for=condition=Available prometheus --all` [ASSUMED: nomes]. Usar Helm 4.3.1.

### Pitfall 12: env vars injetadas pelos Services
**What goes wrong:** o Kubernetes injeta `POSTGRES_PORT=tcp://…`, `VALKEY_PORT=…` e `LINK_PULSE_PORT=…` para cada Service do namespace. O relaxed binding do Spring pode ligar algumas delas a propriedades por acidente.
**How to avoid:** `enableServiceLinks: false`.

### Pitfall 13: primeiro `make k8s` lento ou sem memória
**What goes wrong:** download de ~1 GB de imagens (kps, Grafana, Traefik, deps) dentro do nó. O Docker Desktop/WSL2 com pouca RAM deixa pods `Pending`/OOMKilled.
**How to avoid:** kps enxuto, timeouts de 15 min, requests modestos (soma estimada de ~1,2 CPU e ~2,5 GiB). O host tem 16 GB de RAM e i7-4790; o default do WSL2 é 50% da RAM [ASSUMED].

### Pitfall 14: imagem com maiúsculas
**What goes wrong:** `ghcr.io/Lucasllo/link-pulse` → "repository name must be lowercase".
**How to avoid:** metadata-action (minusculiza) e `docker inspect … RepoDigests` (o nome já sai minúsculo).

### Pitfall 15: pacote nasce privado
**What goes wrong:** quem tenta `docker pull` anônimo recebe 401/denied.
**How to avoid:** checkpoint humano depois do primeiro publish. Verificação anônima: `curl -s "https://ghcr.io/token?scope=repository:lucasllo/link-pulse:pull"` + manifest HEAD → 200.

### Pitfall 16: Trivy reprova a imagem atual ou estoura rate limit
**What goes wrong:** a base noble ou alguma lib Java pode ter CRITICAL com fix, e o primeiro run falha (estado atual desconhecido). O download do trivy-db no GHCR pode dar 429.
**How to avoid:** rodar o scan localmente antes (`docker run aquasec/trivy:0.76.0 image link-pulse:local`, por digest) e criar `.trivyignore.yaml` com justificativa e expiração, se preciso. O `cache: true` da action (default) guarda o DB. Se der 429: `TRIVY_DB_REPOSITORY=public.ecr.aws/aquasecurity/trivy-db` [ASSUMED].

### Pitfall 17: publish cancelado pela concorrência
**What goes wrong:** `cancel-in-progress: true` em `ci-${{ github.ref }}` mata um push no meio, na main.
**How to avoid:** cancelar só em PR.

### Pitfall 18: cleanup do kind-action com `install_only`
**What goes wrong:** o post step roda `docker rm -f kind-registry || "${INPUT_IGNORE_FAILED_CLEAN}"` e `kind delete cluster --name chart-testing` com `set -o errexit` [VERIFIED: cleanup.sh na tag v1.15.1]. Sem `ignore_failed_clean: true`, o job pode falhar no fim.
**How to avoid:** `cluster_name: link-pulse` + `ignore_failed_clean: true`.

## Code Examples

### Ingress do app
```yaml
# templates/ingress.yaml (values-dev: className traefik, host link-pulse.localtest.me)
{{- if .Values.ingress.enabled }}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "link-pulse.fullname" . }}
spec:
  ingressClassName: {{ .Values.ingress.className }}
  rules:
    - host: {{ .Values.ingress.host | quote }}
      http:
        paths:
          - path: /                 # sem subpath: GET /{code} fica na raiz (D-02)
            pathType: Prefix
            backend:
              service:
                name: {{ include "link-pulse.fullname" . }}
                port: { name: http }
{{- end }}
```

### HPA
```yaml
{{- if .Values.autoscaling.enabled }}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata: { name: {{ include "link-pulse.fullname" . }} }
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: {{ include "link-pulse.fullname" . }} }
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource: { name: cpu, target: { type: Utilization, averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }} } }
  behavior:
    scaleUp: { stabilizationWindowSeconds: 60 }
    scaleDown: { stabilizationWindowSeconds: 300 }
{{- end }}
```
Pool do Hikari (padrão 10) × `maxReplicas` precisa ficar abaixo do `max_connections` do Postgres (100): dev 3 e prod 6 passam.

### Resiliência (K8S-02) — roteiro de UAT/verificação
```bash
K="kubectl --context kind-link-pulse -n link-pulse"
r0=$($K get pod -l app.kubernetes.io/name=link-pulse -o jsonpath='{.items[0].status.containerStatuses[0].restartCount}')
$K scale statefulset valkey --replicas=0     # ou deployment, conforme o chart deps
$K wait --for=condition=Ready=false pod -l app.kubernetes.io/name=link-pulse --timeout=60s
$K scale statefulset valkey --replicas=1
$K wait --for=condition=Ready pod -l app.kubernetes.io/name=link-pulse --timeout=120s
$K scale statefulset mongo --replicas=0      # o app continua Ready; /stats responde 503
sleep 30; $K get pod -l app.kubernetes.io/name=link-pulse   # READY 1/1
$K scale statefulset mongo --replicas=1
r1=$($K get pod -l app.kubernetes.io/name=link-pulse -o jsonpath='{.items[0].status.containerStatuses[0].restartCount}')
[ "$r0" = "$r1" ]                              # nenhuma reinicialização
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ingress-nginx no kind | Traefik / cloud-provider-kind (a doc do kind agora diz "No third-party ingress controllers are required by default" com cloud-provider-kind ≥ v0.9.0) | mar/2026 (EOL do ingress-nginx) | D-07 mantém o Traefik; cloud-provider-kind fica como nota |
| `helm --atomic`, `--force` | `--rollback-on-failure`, `--force-replace`; `--wait=watcher` | Helm 4 | Não usar as flags antigas |
| `kind load docker-image` direto | `docker image save --platform` + `kind load image-archive` | Docker 29 (containerd store) | Pattern 8 |
| `actions/attest-sbom`, `attest-build-provenance` | `actions/attest` (modos por input) | 2026 | O `attest-sbom` está em depreciação |
| `trivy-action` por tag | por SHA + `setup-trivy` ≥ v0.2.6 | GHSA-69fq-xp46-6x23 (mar/2026: 76 de 77 tags da trivy-action reescritas) | D-12 |
| `service.type` no chart do Traefik | `service.spec.type` | chart 4x | Values novos |
| Grafana chart sem schema | `values.schema.json` (tipos estritos) | grafana chart 14.0.0 (out/2026) | Valores numéricos/booleanos sem aspas |

**Deprecated/outdated:**
- `kube-prometheus-stack` 92.0.0 do CLAUDE.md: Grafana 13.2.3 sem os fixes de CVE do 13.2.4.
- Traefik chart 41.6.1 do CLAUDE.md: Traefik v3.7.13, anterior aos fixes de CVE do v3.7.15.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | O `--wait` (watcher) do Helm 4 pode não esperar o StatefulSet do Prometheus; espera explícita depois do kps | Pitfall 11 | O app sobe antes do CRD/Prometheus pronto; o smoke de observabilidade falha cedo (mitigado por polling) |
| A2 | `docker image save --platform` funciona no Docker 28 com store clássico (fallback sem a flag) | Pattern 8 | Fallback cobre |
| A3 | O nome do StatefulSet do kps é `prometheus-monitoring-kube-prometheus-prometheus` | Pitfall 11 | Ajustar o nome no `rollout status` |
| A4 | O IP de origem que o Traefik vê no kind não está em 10.244/16 (com eTP Local); se estiver, o rate limit continua não forjável, só agrupa por esse IP | Pattern 2 | Baixo |
| A5 | `docker image inspect … RepoDigests` traz o digest do GHCR logo depois do push no runner | Pattern 11 | Trocar por `docker buildx imagetools inspect` ou `skopeo` (o runner tem skopeo 1.13.3) |
| A6 | `management.endpoint.health.group.readiness.show-components=always` vale por grupo no IT | Pattern 7 | Usar o `show-components` global no teste |
| A7 | O StorageClass default do kind (`standard`) atende os PVCs do `deps` | Pattern 5 | Sem PVC (emptyDir) também serve no dev |
| A8 | O estado atual de CVEs da imagem passa no gate CRITICAL+fix | Pitfall 16 | Plano precisa de tarefa de triagem/`.trivyignore` |
| A9 | Mirror do trivy-db no ECR como contorno de 429 | Pitfall 16 | Só re-run |
| A10 | O node-exporter no Docker Desktop precisa de `hostRootFsMount.enabled: false` (só se o modo completo do kps for escolhido) | Pattern 4 | Pod em erro de mount |
| A11 | O default de memória do WSL2 é 50% da RAM (~8 GB aqui) | Pitfall 13 | Pods `Pending`; documentar `.wslconfig` |
| A12 | O Hikari espera até 30 s por conexão na health de `db` | Pitfall 10 | Só performance |
| A13 | O contexto do kubeconfig é `kind-<nome do cluster>` | Pitfall 4 | Ajustar a variável |
| A14 | O cluster kind pode não sobreviver a um restart do Docker Desktop (recriar com `make k8s-down && make k8s`) | Environment | Documentação |

## Open Questions (RESOLVED)

1. **O smoke bloqueia a publicação?**
   - Recomendação: sim. O `publish` depende de `build-test`, `image` e `k8s-smoke` e empurra exatamente o tar testado.
2. **Subir o compose para `grafana/grafana:13.2.4`?**
   - Recomendação: sim (fix de CVE e paridade com o kind). É uma linha no compose.yaml:104 e uma menção no observability/README; o Dependabot faria o mesmo.
3. **kps enxuto ou completo (dashboards de cluster)?**
   - Recomendação: enxuto por padrão (Pattern 4), com o modo completo descrito em comentário no values.
4. **Visibilidade do GHCR:** passo manual único, depois do primeiro publish na main. O plano deve ter um `checkpoint:human-verify` e um teste de pull anônimo.
5. **SARIF na aba Security:** opcional. Exige `security-events: write`, e PRs de fork não recebem token de escrita; se entrar, só no push da main.
6. **`k8s-seed`:** opcional. O caminho mais simples é um Pod k6 no cluster (`grafana/k6:2.3.0`) com `load/` num ConfigMap, `BASE_URL=http://link-pulse.link-pulse.svc:8080` e `PUBLIC_BASE_URL=http://link-pulse.localtest.me`. Sem `X-Forwarded-For`, o IP contado é o do pod do k6 (o seed já fica abaixo de 15 criações). Rodar o k6 do host contra o kind esbarra no bind em 127.0.0.1.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker (Desktop) | kind, build, kubeconform em container | ✓ CLI / ✗ daemon parado na pesquisa | 29.8.0 | Iniciar o Docker Desktop antes de executar |
| kind | `make k8s` | ✗ | — | `winget install Kubernetes.kind` (0.33.0 disponível) |
| Helm | `make k8s` | ✗ | — | `winget install Helm.Helm` (4.3.1 disponível) |
| kubectl | `make k8s` | ✓ | v1.36.1 (skew de 1 minor com o 1.37) | `winget install Kubernetes.kubectl` |
| GNU make | Makefile | ✓ | 4.4.1 (Windows32) | `bash deploy/kind/up.sh` direto |
| curl | smoke | ✓ | 8.18.0 (Schannel) | — |
| jq | — | ✗ | — | Não depender (sed/grep) |
| gh | verificação de attestations/pacote | ✓ | 2.102.0 (logado como Lucasllo) | — |
| Porta 80 em 127.0.0.1 | extraPortMappings | ✓ livre na pesquisa | — | `K8S_HTTP_PORT` |
| RAM/CPU do host | kind + kps | 16 GB / i7-4790 | — | kps enxuto |
| Runner ubuntu-latest | CI | Docker 28.0.4, kind 0.33.0, kubectl 1.37.1, Helm 3.22.0, jq 1.7, skopeo 1.13.3, yq 4.54.1 | — | `azure/setup-helm` para o Helm 4.3.1 |

**Missing dependencies with no fallback:** nenhuma (todas instaláveis por winget).
**Missing dependencies with fallback:** kind e helm (winget); jq (não usar).

## Security Domain

`security_enforcement: true`, ASVS nível 1.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | não (API sem auth por escopo) | Grafana: anônimo só Viewer; admin com senha de demo comentada |
| V3 Session Management | não | — |
| V4 Access Control | parcial | RBAC do K8s via charts; `automountServiceAccountToken: false` no app; permissões mínimas por job no CI |
| V5 Input Validation | sem mudança | Bean Validation existente; o IP do cliente só via RemoteIpValve com CIDR |
| V6 Cryptography | não | HMAC do IP já existe; a chave vem do Secret |
| V9 Communications | parcial | Sem TLS no kind (loopback); values-prod com TLS no Ingress |
| V14 Configuration | sim | Pins por SHA/digest/versão, Trivy gate, SBOM + provenance, contêiner endurecido, segredos fora da imagem |

### Known Threat Patterns for kind + Traefik + GitHub Actions

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Cliente forja `X-Forwarded-For` para burlar o rate limit | Spoofing/Tampering | Traefik com `trustedIPs` vazio (apaga XFF de não confiáveis, verificado na fonte); app confia só em `10.244.0.0/16` |
| Pod do cluster chama o Service do app com XFF forjado | Spoofing | Aceito no kind (todo o cluster é do avaliador). Mitigação opcional: NetworkPolicy permitindo a 8080 só a partir do namespace `traefik` [ASSUMED: suporte do kindnet] |
| Action comprometida por tag reescrita | Tampering | SHA de commit em todas as actions; trivy-action ≥ v0.35.0 e setup-trivy ≥ v0.2.6 (patched no GHSA-69fq-xp46-6x23) |
| Token do CI com escopo demais / PR de fork publicando | Elevation | `contents: read` no topo; escrita só no job `publish`, que só roda em push na main; `pull_request` (nunca `pull_request_target`) |
| Imagem pública com CVE crítica ou segredo embutido | Info disclosure | Gate do Trivy (o scanner de secret é default no `trivy image`) [ASSUMED: scanners default `vuln,secret`]; `.dockerignore` já exclui `.env` |
| Exposição de Prometheus/Grafana/actuator | Info disclosure | Bind só em 127.0.0.1; actuator 8081 fora do Ingress; Prometheus com `enableAdminAPI: false` (default verificado); Traefik dashboard não exposto |
| DNS rebinding contra serviços locais | Spoofing | Roteamento por Host: um domínio atacante não casa nenhum Ingress (404) |
| Contêiner escapando privilégios | Elevation | `runAsNonRoot`, `runAsUser: 10001`, `readOnlyRootFilesystem`, `drop: [ALL]`, `allowPrivilegeEscalation: false`, `seccompProfile: RuntimeDefault` |
| Supply chain da imagem sem rastreabilidade | Repudiation | `actions/attest` (provenance SLSA + SBOM) com push para o registry; verificação por `gh attestation verify` |

## Sources

### Primary (HIGH confidence)
- GitHub Releases/Tags API (nesta sessão): kind v0.33.0 (digests dos nodes), helm v4.3.1, traefik-helm-chart v41.6.1..v42.1.0, traefik v3.7.15, metrics-server chart 3.14.0, kubeconform v0.8.0, trivy v0.76.0, trivy-action v0.36.0, actions/attest v4.2.2, attest-sbom v4.1.0, docker/* actions, helm/kind-action v1.15.1, azure/setup-helm v5.0.1, download-artifact v8.0.2, codeql-action v4.39.0
- prometheus-community/helm-charts, tags kube-prometheus-stack-92.0.0, 92.8.0, 92.10.1 e 92.11.0: `Chart.yaml`, `values.yaml`, `templates/prometheus/prometheus.yaml`, `templates/grafana/configmaps-datasources.yaml`
- grafana-community/helm-charts, grafana-13.2.7 e grafana-14.0.1: `Chart.yaml`, `values.yaml`, `values.schema.json`, README "To 14.0.0"
- prometheus-operator v0.94.1: `servicemonitor_types.go` (jobLabel), `prometheusrule_types.go`
- traefik v3.7.14/v3.7.15: `forwarded_header.go`, `request_decorator.go`; traefik-helm-chart v42.x: `values.yaml`, `_podtemplate.tpl`, `ingressclass.yaml`, `_helpers.tpl`
- helm/kind-action v1.15.1: `action.yml`, `cleanup.sh`
- kind PR #4247 (known-issues: `kind load` + containerd image store)
- GHSA-69fq-xp46-6x23 (incidente Trivy, versões afetadas e corrigidas)
- actions/runner-images `Ubuntu2404-Readme.md`
- Repositório local (lido nesta sessão): application.yml, compose.yaml, Makefile, ci.yml, Dockerfile, observability/*, ClickConfig.java, DTOs da API
- https://docs.spring.io/spring-boot/reference/actuator/endpoints.html (Boot 4.1.1): probes, health groups, chaves dos indicators
- https://helm.sh/docs/helm/helm_upgrade/ (Helm 4.3.1): `--wait`, `--set-file`, `--rollback-on-failure`, `--force-replace`
- https://kind.sigs.k8s.io/docs/user/configuration/: podSubnet/serviceSubnet default, extraPortMappings, NodePort
- https://docs.github.com/en/packages/learn-github-packages/configuring-a-packages-access-control-and-visibility
- https://docs.docker.com/reference/cli/docker/image/save/ (`--platform`, API 1.48+)

### Secondary (MEDIUM confidence)
- https://doc.traefik.io/traefik/reference/install-configuration/entrypoints/ (forwardedHeaders; o comportamento com IP não confiável foi confirmado no código-fonte)
- https://kind.sigs.k8s.io/docs/user/ingress/ (cloud-provider-kind ≥ v0.9.0)
- KEP-3960 e tabelas de feature gates v1.30/v1.33 (PodLifecycleSleepAction beta, ligado por padrão desde o 1.30): https://www.kubernetes.dev/resources/keps/3960/ , https://v1-33.docs.kubernetes.io/docs/reference/command-line-tools-reference/feature-gates/
- Busca sobre `kind load` + containerd: https://gitea.com/gitea/gitea.com/issues/83 , https://buildpacks.io/docs/for-app-developers/how-to/build-outputs/understand-failures/

### Tertiary (LOW confidence)
- Itens do Assumptions Log (A1-A14)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, com versões, SHAs, digests e manifests OCI conferidos nesta sessão
- Architecture: HIGH nos charts de terceiros (values/templates lidos nas tags), MEDIUM no fluxo do `up.sh` no Windows (não executado: Docker parado, kind e helm ausentes)
- Pitfalls: HIGH nos de seletores, forwarded headers, containerd e cleanup do kind-action (lidos na fonte); MEDIUM em `--wait`, memória e Trivy

**Research date:** 2026-10-20
**Valid until:** ~2026-11-03 (charts do kps e do Traefik saem quase todo dia; reconferir versões no início da execução)
