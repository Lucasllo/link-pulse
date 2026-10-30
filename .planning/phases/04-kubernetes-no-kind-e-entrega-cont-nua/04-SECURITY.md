---
phase: "04"
slug: "kubernetes-no-kind-e-entrega-cont-nua"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-30"
---

# Phase 04 — Security

> Contrato de segurança da fase: registro de ameaças, riscos aceitos e trilha de auditoria.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| LAN → porta do host | Um bind em todas as interfaces exporia o cluster para a rede | Tráfego HTTP da API e dos painéis |
| Cliente → Traefik | Headers do cliente (inclusive `X-Forwarded-For`) são entrada não confiável | IP de origem usado no rate limit |
| Traefik → app | O app só confia em proxies do CIDR dos pods (`10.244.0.0/16`) | IP real do cliente |
| Script → kubeconfig do avaliador | O contexto atual pode apontar para um cluster real | Comandos kubectl/helm com escrita |
| Registry → cluster | Charts e imagens de terceiros entram no nó | Código de terceiros |
| Navegador do host → Grafana/Prometheus | Acesso pelo loopback, sem autenticação para leitura | Métricas e dashboards |
| Chart do app → Prometheus Operator | ServiceMonitor/PrometheusRule viram config do Prometheus | Regras e alvos de scrape |
| metrics-server → kubelet | TLS do kubelet sem validação no kind | Métricas de recurso |
| Repositório → values-prod | Arquivo público versionado que descreve produção | Configuração (sem segredo) |
| CI/host → schemas remotos | kubeconform baixa schemas do GitHub | Schemas JSON (só validação) |
| Actions de terceiros → runner | Código de terceiros roda com o token do job | `GITHUB_TOKEN` |
| Pull request (fork, Dependabot) → workflow | Código não revisado executa no CI | Token somente leitura |
| Job publish → GHCR / imagem → mundo | Escrita no registry público e distribuição da imagem | Imagem, SBOM, provenance |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-04-01 | Information Disclosure | `extraPortMappings` do kind | high | mitigate | `deploy/kind/kind-config.yaml:21` `listenAddress: "127.0.0.1"` | closed |
| T-04-02 | Spoofing | Rate limit atrás do Traefik | high | mitigate | `deploy/kind/values/traefik.yaml:23-24` `trustedIPs: []`, `insecure: false`; `values-dev.yaml:28` `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES: 10.244.0.0/16`; UAT teste 2 (21º POST com XFF forjado = 429) | closed |
| T-04-03 | Elevation of Privilege | `up.sh` no contexto atual | high | mitigate | `deploy/kind/up.sh:21-22` `k()`/`h()` com `--context`/`--kube-context`; os `helm lint/template` do `lint.sh` são offline | closed |
| T-04-04 | Elevation of Privilege | Container do app | medium | mitigate | `deployment.yaml:25,32-36,92-95` non-root 10001, RO rootfs, `drop: [ALL]`, sem escalada, seccomp RuntimeDefault, sem token de SA | closed |
| T-04-05 | Information Disclosure | Secrets do app e do deps | medium | mitigate | `templates/secret.yaml:3-7` `required` + `fail` (<16 chars); `_helpers.tpl:60` `existingSecret`; defaults só em `values-dev.yaml` com "Não é segredo" | closed |
| T-04-06 | Information Disclosure | Actuator 8081 e bancos | medium | mitigate | `ingress.yaml:24-26` só a porta `http`; deps sem Ingress/NodePort/LoadBalancer | closed |
| T-04-07 | Spoofing | Pod do cluster com XFF forjado | low | accept | Ver Accepted Risks (AR-04-01) | closed |
| T-04-08 | Denial of Service | Readiness com Valkey → 503 | low | accept | `application.yml:132-133`; README:246-247; ver AR-04-02 | closed |
| T-04-SC | Tampering | Traefik, nó kind, imagens dos deps, kube-prometheus-stack, metrics-server | medium | mitigate | `kind-config.yaml:16` nó por digest; `up.sh:89,94,105` `--version` 3.14.0 / 42.1.0 / 92.10.1; `deps/values.yaml` tags exatas (`postgres:18.6`, `valkey/valkey:8.1`, `mongo:8.0.32`) | closed |
| T-04-09 | Tampering | Grafana anônimo | medium | mitigate | `kube-prometheus-stack.yaml:83` `org_role: Viewer`; `:103-104` `disableDelete: true`, `allowUiUpdates: false` | closed |
| T-04-10 | Information Disclosure / Tampering | API admin do Prometheus | medium | mitigate | `kube-prometheus-stack.yaml:55` `enableAdminAPI` no default (false); porta do kind só em loopback (T-04-01) | closed |
| T-04-11 | Tampering | Deriva de dashboards/regras | low | mitigate | `up.sh:126-128` `--set-file` de `observability/`; `prometheusrule.yaml:7,10` e `dashboards-configmap.yaml:9` com `required`/`fail` | closed |
| T-04-12 | Spoofing | metrics-server `--kubelet-insecure-tls` | low | accept | Só em `deploy/kind/values/metrics-server.yaml:6`; ver AR-04-03 | closed |
| T-04-13 | Denial of Service | Disco do laptop | low | mitigate | `kube-prometheus-stack.yaml:48-49,59-61` `retention: 2d`, `retentionSize: 1GB`, limit 1Gi | closed |
| T-04-14 | Information Disclosure | `values-prod.yaml` com segredo | medium | mitigate | `values-prod.yaml:47-48` `create: false`, `existingSecret: link-pulse-secrets` | closed |
| T-04-15 | Tampering | Manifests inválidos | low | mitigate | `lint.sh:22,51-53` kubeconform `-strict` e `helm lint --strict` | closed |
| T-04-16 | Repudiation | Smoke vazio | low | mitigate | `smoke.sh:68,79-89` confere o `Location` sem seguir e exige clique no `/stats` | closed |
| T-04-17 | Denial of Service | Smoke/CI no rate limit | low | mitigate | Uma criação por execução do `smoke.sh` (passo 1/5), abaixo de 20/min | closed |
| T-04-18 | Tampering | Schemas remotos do kubeconform | low | accept | `lint.sh:22,25`; ver AR-04-04 | closed |
| T-04-19 | Tampering | Imagem diferente do compose | low | mitigate | `lint.sh:89-106` paridade de tags (compose, compose.dev, deps, Testcontainers) | closed |
| T-04-20 | Tampering | Actions por tag reescrita | high | mitigate | Todas as `uses:` de `.github/workflows/*.yml` por SHA de 40 caracteres; `.github/dependabot.yml:4` `github-actions` | closed |
| T-04-21 | Elevation of Privilege | Token de escrita em PR/fork | high | mitigate | `ci.yml:10-11` topo `contents: read`; `packages: write` só no `publish` (`:208-215`, push na main) | closed |
| T-04-22 | Information Disclosure | Imagem pública com CVE crítica ou segredo | high | mitigate | `ci.yml:88-97` Trivy image (scanners default `vuln,secret`), `severity: CRITICAL`, `ignore-unfixed`, `exit-code: "1"`; `app/.dockerignore` exclui `target/` e `.env` | closed |
| T-04-23 | Tampering | Publicar imagem diferente da testada | high | mitigate | Job `publish` sem checkout/build, só `docker load -i link-pulse.tar` | closed |
| T-04-24 | Repudiation | Origem da imagem sem rastreio | medium | mitigate | `actions/attest` (provenance + SBOM CycloneDX) no `publish`; `gh attestation verify` no README:465-471 | closed |
| T-04-25 | Tampering | Injeção por outputs no `run:` | medium | mitigate | Tags da metadata-action via `env: TAGS`; `${{ }}` só em `with:` | closed |
| T-04-26 | Denial of Service | Publish cancelado | low | mitigate | `ci.yml:16` `cancel-in-progress` só em `pull_request` | closed |
| T-04-27 | Information Disclosure | Pacote público | low | accept | README:455-471; ver AR-04-05 | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-04-01 | T-04-07 | O kind é de um avaliador só e o CIDR confiável é o dos pods; um pod do próprio cluster pode forjar XFF. Sem NetworkPolicy nesta fase. No `values-prod` de referência a mesma confiança no /16 continua sem NetworkPolicy (WR do 04-REVIEW); a correção fica para o fix do review. | Plano 04-01 (threat model) | 2026-10-30 |
| AR-04-02 | T-04-08 | Troca consciente (D-10/K8S-02): com o Valkey fora, a readiness tira o pod de circulação em vez de servir só pelo Postgres. Documentado no `application.yml` e no README. O acoplamento de todas as réplicas a dependências compartilhadas é um WR do 04-REVIEW. | Plano 04-01 (threat model) | 2026-10-30 |
| AR-04-03 | T-04-12 | Certificado autoassinado do kubelet só no kind local; a flag não entra no `values-prod`. | Plano 04-02 (threat model) | 2026-10-30 |
| AR-04-04 | T-04-18 | Schemas da comunidade (yannh, datreeio) só validam e nunca são aplicados; a imagem do kubeconform é fixa em v0.8.0. | Plano 04-03 (threat model) | 2026-10-30 |
| AR-04-05 | T-04-27 | Critério 4 da ROADMAP exige o pacote público; a imagem não carrega segredo (T-04-22) e o repositório já é público. | Plano 04-04 (threat model) | 2026-10-30 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-30 | 29 | 29 | 0 | /gsd-secure-phase (ASVS L1, verificação grep dos 4 threat models; auditor dispensado pela regra de short-circuit) |

Observação: o 04-REVIEW (9 warnings, 0 critical) aponta que o README afirma que vulnerabilidades HIGH e sem correção "aparecem no log" do Trivy, o que é falso com `severity: CRITICAL` e `ignore-unfixed: true`. É um erro de documentação. O gate de T-04-22 continua como foi planejado.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-30
