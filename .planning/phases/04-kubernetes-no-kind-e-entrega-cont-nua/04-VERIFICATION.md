---
phase: 04-kubernetes-no-kind-e-entrega-cont-nua
verified: 2026-10-29T00:21:30Z
status: human_needed
score: 31/33 must-haves verificados (5/5 critérios do ROADMAP; 26/28 truths dos planos)
covered_files:
  - .github/workflows/ci.yml
  - .planning/REQUIREMENTS.md
  - .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-01-PLAN.md
  - .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-01-SUMMARY.md
  - .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-02-PLAN.md
  - .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-02-SUMMARY.md
  - .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-03-PLAN.md
  - .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-03-SUMMARY.md
  - .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-04-PLAN.md
  - .planning/phases/04-kubernetes-no-kind-e-entrega-cont-nua/04-04-SUMMARY.md
  - Makefile
  - README.md
  - app/Dockerfile
  - app/src/main/resources/application.yml
  - app/src/test/java/dev/linkpulse/HealthGroupsIT.java
  - app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java
  - compose.dev.yaml
  - compose.yaml
  - deploy/helm/deps/Chart.yaml
  - deploy/helm/deps/templates/mongo.yaml
  - deploy/helm/deps/templates/postgres.yaml
  - deploy/helm/deps/templates/secret.yaml
  - deploy/helm/deps/templates/valkey.yaml
  - deploy/helm/deps/values.yaml
  - deploy/helm/link-pulse/Chart.yaml
  - deploy/helm/link-pulse/templates/_helpers.tpl
  - deploy/helm/link-pulse/templates/configmap.yaml
  - deploy/helm/link-pulse/templates/dashboards-configmap.yaml
  - deploy/helm/link-pulse/templates/deployment.yaml
  - deploy/helm/link-pulse/templates/hpa.yaml
  - deploy/helm/link-pulse/templates/ingress.yaml
  - deploy/helm/link-pulse/templates/prometheusrule.yaml
  - deploy/helm/link-pulse/templates/secret.yaml
  - deploy/helm/link-pulse/templates/service.yaml
  - deploy/helm/link-pulse/templates/servicemonitor.yaml
  - deploy/helm/link-pulse/values-dev.yaml
  - deploy/helm/link-pulse/values-prod.yaml
  - deploy/helm/link-pulse/values.yaml
  - deploy/kind/kind-config.yaml
  - deploy/kind/lint.sh
  - deploy/kind/smoke.sh
  - deploy/kind/up.sh
  - deploy/kind/values/kube-prometheus-stack.yaml
  - deploy/kind/values/metrics-server.yaml
  - deploy/kind/values/traefik.yaml
  - observability/README.md
covered_digest: "v1:sha256:be1d163671080d8773a5ff165c4f8982dab9cc8b98e18e1516cb720617797b36"
behavior_unverified: 2
overrides_applied: 0
behavior_unverified_items:
  - truth: "Um X-Forwarded-For forjado pelo cliente não muda a identidade do rate limit pelo Traefik: 20 criações com XFF diferentes recebem 201 e a 21ª recebe 429; um pod do cluster com IP real diferente segue recebendo 201 (04-01, D-06)"
    test: "Com o cluster de pé e numa janela nova de 1 min, mandar do host 21 POST /links para http://link-pulse.localtest.me, cada um com um X-Forwarded-For diferente; em seguida, de um pod (kubectl --context kind-link-pulse run ... curl), mandar um POST pelo Service do Traefik"
    expected: "201 nos 20 primeiros, 429 no 21º; o POST do pod recebe 201 na mesma janela"
    why_human: "O lado do app está coberto pelo TrustedProxyRateLimitIT (verde no CI), mas a parte do Traefik (forwardedHeaders.trustedIPs: [] + externalTrafficPolicy: Local) só é configuração; nenhum teste automatizado exercita a cadeia host -> Traefik -> app. A execução que o 04-01-SUMMARY relata foi manual e intermitente (2 de 4 perderam a saída). O verificador não roda 21 POSTs para não mutar o cluster nem gastar o rate limit."
  - truth: "Do zero: make k8s-down -> make k8s K8S_HTTP_PORT=8080 -> make k8s-smoke (shortUrl com :8080) -> make k8s-down -> make k8s -> make k8s-smoke passa inteiro (04-03, D-01)"
    test: "Rodar a sequência completa no host Windows (Git Bash)"
    expected: "Os dois smokes terminam em 'smoke OK: {code}'; na 8080 a shortUrl, o Grafana (root_url) e o Prometheus (externalUrl) levam :8080"
    why_human: "O caminho da porta 80 do zero é provado pelo job k8s-smoke do CI (cluster novo a cada run). O caminho 8080 (sed do hostPort, KPS_SET, env.LINKPULSE_BASE_URL) só tem a evidência narrada no 04-03-SUMMARY; nenhum teste automatizado o executa e o verificador não recria o cluster."
human_verification:
  - test: "Abrir no navegador http://link-pulse.localtest.me/swagger-ui.html, criar um link pelo POST /links e clicar na shortUrl; depois abrir http://grafana.localtest.me e http://prometheus.localtest.me/alerts (human-check adiado do 04-02-PLAN, Task 3)"
    expected: "A shortUrl abre o destino; o Grafana abre sem login direto no dashboard 'Link Pulse — Aplicação' com dados de requisições; o Prometheus lista os quatro alertas do Link Pulse, inativos com o app de pé"
    why_human: "Clicar na shortUrl no navegador do host (D-03) e ver os painéis com dados é verificação visual. As partes de API já foram conferidas pelo verificador (dashboards na pasta link-pulse via anônimo, 4 regras com runbook_url, up=1)."
  - test: "Rate limit por IP real atrás do Traefik (ver behavior_unverified_items[0])"
    expected: "20 x 201, 21º = 429 com XFF forjado; pod do cluster recebe 201"
    why_human: "Cadeia Traefik -> app sem teste automatizado"
  - test: "Sequência do zero na porta 8080 (ver behavior_unverified_items[1])"
    expected: "smoke OK nas duas portas"
    why_human: "Caminho 8080 sem teste automatizado"
  - test: "Revisar as 8 proibições test-tier sinalizadas (unverified-prohibition — revisão humana recomendada), listadas na seção 'Proibições' do relatório"
    expected: "Aceitar a evidência estática/observada registrada para cada uma ou pedir um teste de regressão"
    why_human: "Proibições com verification: test sem teste automatizado que falharia se a proibição fosse violada ficam fail-closed (ADR-550 D4); a evidência coletada pelo verificador está limpa, mas é inspeção estática ou observação pontual"
---

# Phase 4: Kubernetes no kind e entrega contínua — Relatório de Verificação

**Objetivo da fase:** Um avaliador roda `make k8s` e obtém o mesmo encurtador observável num cluster kind (Helm, Traefik, kube-prometheus-stack), enquanto o CI publica uma imagem escaneada no GHCR e comprova o deploy com smoke test.
**Verificado em:** 2026-10-29T00:21:30Z
**Status:** human_needed
**Re-verificação:** Não (verificação inicial)

## Resumo

O objetivo foi atingido no código e em execução real. Conferi de forma independente, sem confiar nos SUMMARYs:

- **CI:** o run 40789218786 (push na `main`, commit `7c9b868`) terminou com `build-test`, `image`, `k8s-smoke` e `publish` em success. O log do `k8s-smoke` mostra kubeconform 10/9/7 válidos, `code=cJinsNv`, `302 -> https://example.com/smoke-...`, `total=1`, `up=1, LinkPulseTargetDown carregada`, `link-pulse-overview ok` e `smoke OK: cJinsNv`. O log do `image` mostra o Trivy v0.76.0 rodando e o `jq -e '(.components | length) >= 50'` devolvendo `true`. Depois de `7c9b868` só mudaram arquivos de `.planning/`, então o run cobre o código atual.
- **GHCR:** pull anônimo (token anônimo do ghcr.io) de `manifests/latest` e de `manifests/sha-7c9b868` responde 200; `gh api users/Lucasllo/packages/container/link-pulse` = `public`; `gh attestation verify` da tag `sha-7c9b868` sai com 0 para a provenance e para `--predicate-type https://cyclonedx.org/bom`.
- **Lint local:** `bash deploy/kind/lint.sh` no Windows/Git Bash terminou em `lint OK` (helm lint strict nos 3 renders, kubeconform 10/9/7 válidos com 0 inválidos/erros/pulados, 4 guardas fail-closed recusadas pela mensagem certa, paridade `postgres:18.6`, `valkey/valkey:8.1` e `mongo:8.0.32`).
- **Cluster kind local (`kind-link-pulse`, leitura apenas):** Deployment 1/1, HPA `cpu: 3%/70%` (nunca `<unknown>`), ServiceMonitor e PrometheusRule presentes, `docker port link-pulse-control-plane 30080/tcp` = `127.0.0.1:80`, `GET /links/zzzzzzz/stats` pelo Ingress devolve 404 `application/problem+json` (resposta do app), `/actuator/health` pelo Ingress devolve 404 (a 8081 não sai pelo Ingress), `min(up{job="link-pulse"})` = 1, as 4 regras com o `runbook_url` da main, os dois dashboards na pasta `link-pulse` com `canEdit:false`/`canSave:false`, a legenda `{{method}} {{uri}} {{status}}` intacta e `POST /api/folders` anônimo = 403.

O status é `human_needed`, e não `passed`, por quatro motivos: um human-check visual adiado pelo próprio plano 04-02; duas truths de comportamento sem teste automatizado (cadeia de rate limit através do Traefik e a sequência do zero na 8080); e 8 proibições test-tier que, pelo contrato fail-closed, não podem virar verde só com inspeção estática. Nenhum must-have FALHOU e não há bloqueador.

## Goal Achievement

### Critérios de sucesso do ROADMAP (contrato)

| # | Critério | Status | Evidência |
|---|----------|--------|-----------|
| SC1 | `make k8s` cria o cluster kind e instala Traefik, metrics-server, kube-prometheus-stack, Postgres/Mongo/Valkey (manifests próprios, imagens oficiais) e o app via Helm; criar link e ser redirecionado funciona pelo Ingress a partir do host | ✓ VERIFICADO | O alvo `k8s: image` do `Makefile` delega para `deploy/kind/up.sh`, que cria/reaproveita o cluster e instala metrics-server 3.14.0, Traefik 42.1.0 (OCI), kube-prometheus-stack 92.10.1, o chart `deps` (StatefulSet + Service com `postgres:18.6`, `valkey/valkey:8.1` e `mongo:8.0.32`, sem Bitnami) e o chart `link-pulse`, sempre com `--kube-context`/`--context kind-link-pulse`. O CI roda o mesmo `up.sh` num cluster novo e o smoke prova POST 201 -> 302 com `Location` igual ao destino pelo Ingress. O cluster local responde pelo Ingress em 127.0.0.1:80. |
| SC2 | O chart traz Deployment, Service, Ingress, ConfigMap, Secret e HPA, com values dev/prod; o pod só fica Ready com Postgres e Valkey acessíveis; derrubar Valkey ou Mongo não reinicia o pod | ✓ VERIFICADO | Os 6 templates existem e renderizam (kubeconform dev 10 e prod 9 recursos válidos); `values-dev.yaml` e `values-prod.yaml` existem. `application.yml`: `liveness.include: livenessState` e `readiness.include: readinessState,db,redis`. O `deployment.yaml` aponta startup/liveness para `/actuator/health/liveness` e readiness para `/actuator/health/readiness` na porta `management`. O `HealthGroupsIT` (4 testes, verde no CI: `Tests run: 4, Failures: 0` em `dev.linkpulse.HealthGroupsIT`) pausa o Valkey -> readiness 503 e liveness 200; pausa o Mongo -> readiness e liveness 200. Como a liveness é o único gatilho de restart depois do startup, nenhuma dessas quedas reinicia o pod. O pod local está com `restartCount` 0. |
| SC3 | O Prometheus do cluster raspa o app via ServiceMonitor, e os mesmos dashboards e alertas de `observability/` aparecem no Grafana e no Prometheus do kind sem passo manual | ✓ VERIFICADO | `servicemonitor.yaml` com `jobLabel: app.kubernetes.io/name`, porta `management`, `/actuator/prometheus`. O `up.sh` passa `--set-file observability.rules=observability/prometheus/rules/link-pulse-alerts.yml` e um `--set-file observability.dashboards.{nome}` por JSON, então o conteúdo não é copiado (nenhum JSON de dashboard nem `groups:` fora de `observability/` no git). Seletores abertos (`*SelectorNilUsesHelmValues: false`) e sidecar `grafana_dashboard=1` com `searchNamespace: ALL`. Ao vivo: up=1, 4 regras, 2 dashboards na pasta `link-pulse`. |
| SC4 | Na main, o GitHub Actions publica a imagem no GHCR (nome em minúsculas, pacote público), roda o Trivy e gera o SBOM | ✓ VERIFICADO | `ci.yml`: job `image` (Trivy v0.76.0 com `severity: CRITICAL`, `ignore-unfixed: true`, `exit-code: "1"`; SBOM CycloneDX com inputs próprios e barreira de 50 componentes); job `publish` só em push na main, `needs: [build-test, image, k8s-smoke]`, push de `ghcr.io/lucasllo/link-pulse` com `sha-{sha7}` e `latest` a partir do tar testado, mais attestations de provenance e de SBOM. Run 40789218786 verde, pull anônimo 200, pacote `public`, attestations verificadas. |
| SC5 | O job de verificação do CI sobe um cluster kind, faz o deploy e passa no smoke criar link -> redirect -> stats | ✓ VERIFICADO | Job `k8s-smoke`: kind v0.33.0 + Helm v4.3.1, `make obs-check`, `lint.sh`, imagem do artifact do commit, `bash deploy/kind/up.sh` e `bash deploy/kind/smoke.sh`. O log do run mostra os 5 passos e `smoke OK: cJinsNv`. |

### Truths dos planos (must_haves)

| # | Plano | Truth (resumo) | Status | Evidência |
|---|-------|----------------|--------|-----------|
| 1 | 04-01 | `make k8s` builda, cria/reaproveita o cluster com o nó fixado por digest e `podSubnet` 10.244.0.0/16, carrega a tag `local-{12 hex}` por image-archive e instala Traefik, deps e app com `--wait` no contexto fixo | ✓ VERIFICADO | `kind-config.yaml` (digest `sha256:a1ed56cf...`, `podSubnet`), `up.sh` linhas 77-86 e 94-131; CI e cluster local. Valkey com `--maxmemory-policy volatile-lru`. |
| 2 | 04-01 | Do host, POST 201 com `shortUrl` `http://link-pulse.localtest.me/{code}` e GET 302 pelo nodePort 30080, sem port-forward | ✓ VERIFICADO | Smoke do CI (POST 201, 302 com Location); `LINKPULSE_BASE_URL` no values-dev; `extraPortMappings` 30080 = `nodePort: 30080` do Traefik. |
| 3 | 04-01 | Porta só em 127.0.0.1; Ingress só roteia a porta `http` | ✓ VERIFICADO | `docker port` = `127.0.0.1:80`; `ingress.yaml` usa `port.name: http`; `/actuator/health` pelo Ingress = 404. |
| 4 | 04-01 | XFF forjado não muda a identidade do rate limit (20 x 201, 21º 429; pod com IP real recebe 201) | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | Configuração presente (Traefik `trustedIPs: []`, `externalTrafficPolicy: Local`; app com `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES: 10.244.0.0/16`). O lado do app passa no `TrustedProxyRateLimitIT` (3 testes, verde no CI). A cadeia Traefik -> app não tem teste automatizado. Ver Human Verification. |
| 5 | 04-01 | Liveness interna e readiness com `readinessState,db,redis`; Valkey/Postgres fora -> NotReady e volta; Mongo fora -> Ready; `restartCount` inalterado | ✓ VERIFICADO | `HealthGroupsIT` (Valkey e Mongo pausados) verde no CI + mapeamento das probes no `deployment.yaml`. O Postgres pausado fica fora do IT; a composição `db` na readiness é assertada pelo IT (`containsExactlyInAnyOrder("readinessState","db","redis")`). |
| 6 | 04-01 | Profile prod, env pelo ConfigMap, segredos pelo Secret; o chart recusa render sem `image.tag`, com chave HMAC < 16 ou `secret.create: false` sem `existingSecret` | ✓ VERIFICADO | `configmap.yaml`/`secret.yaml`/`envFrom`; as 3 guardas recusadas no `lint.sh` local e no CI. |
| 7 | 04-01 | Rodar `make k8s` de novo não recria o cluster; tag nova dispara rollout | ✓ VERIFICADO | `up.sh` linhas 50-58 (`kind get clusters \| grep -qx`) e tag pelo Id da imagem. Ao vivo: cluster com 4d10h, Deployment com 69 min e release `deps` na v6 (reaproveitamento repetido). |
| 8 | 04-02 | ServiceMonitor com `jobLabel`; `min(up{job="link-pulse"})` = 1 sem passo manual | ✓ VERIFICADO | Consulta ao vivo devolveu `"1"`; smoke do CI passo 4. |
| 9 | 04-02 | `/api/v1/rules` lista os 4 alertas com `runbook_url` da main, vindos de `observability/` por `--set-file` | ✓ VERIFICADO | Consulta ao vivo: 4 nomes e 4 `runbook_url` `.../blob/main/observability/runbooks/*.md`; `prometheusrule.yaml` usa `fromYaml` do value. |
| 10 | 04-02 | Grafana sem login mostra `link-pulse-overview` e `link-pulse-pipeline` na pasta `link-pulse`, datasource `prometheus`, legendas intactas, anônimo só Viewer e sem criar pasta | ✓ VERIFICADO | Ao vivo: os dois dashboards com `folderUid: link-pulse`, `canEdit:false`, legenda `{{method}} {{uri}} {{status}}`, `POST /api/folders` = 403; `org_role: Viewer`. |
| 11 | 04-02 | Com o app em 0, `LinkPulseTargetDown` dispara pelo `absent()` e volta | ✓ VERIFICADO | Expressão `up{job="link-pulse"} == 0 or absent(up{job="link-pulse"})`; o teste de unidade do promtool (`observability/prometheus/tests/link-pulse-alerts-test.yml`) cobre o ramo `absent()` e roda no `make obs-check` do job `k8s-smoke` (verde). |
| 12 | 04-02 | metrics-server em `metrics.k8s.io`; HPA autoscaling/v2 1-3 com utilização atual; sem `spec.replicas` com HPA | ✓ VERIFICADO | HPA ao vivo `cpu: 3%/70%`, min 1 max 3; `up.sh` espera `apiservice/v1beta1.metrics.k8s.io`; `deployment.yaml` omite `replicas` com `autoscaling.enabled`. |
| 13 | 04-02 | `make k8s` imprime as 3 URLs e um `curl`, com `:{porta}` fora da 80, e `root_url`/`externalUrl` acompanham | ✓ VERIFICADO | `up.sh` linhas 100-104 e 156-177; log do CI mostra o bloco final. |
| 14 | 04-02 | Nenhum JSON de dashboard nem `groups:` fora de `observability/` | ✓ VERIFICADO | `git ls-files` fora de `observability/` e `.planning/` sem `"panels"` nem `^groups:`. |
| 15 | 04-03 | `make k8s-smoke`: UA de navegador, 302 sem seguir, espera até 90 s o `/stats`, confere up, regra e dashboard; termina em `smoke OK` | ✓ VERIFICADO | `smoke.sh` (UA `Chrome/141.0.0.0`, curl sem `-L`, 45 x 2 s); log do CI. |
| 16 | 04-03 | Uma criação de link por execução | ✓ VERIFICADO | Um único `POST "$APP/links"` em `smoke.sh`. |
| 17 | 04-03 | App em 0 -> smoke sai com código diferente de 0 | ✓ VERIFICADO | `set -euo pipefail`; o passo 1 chama `fail` (exit 1) em qualquer status diferente de 201. |
| 18 | 04-03 | `make k8s-lint`: helm lint strict + kubeconform strict (1.37.1 + CRDs) dev/prod/deps com 0 inválidos/erros/pulados e as 4 guardas | ✓ VERIFICADO | Execução local em `lint OK` (saída acima). |
| 19 | 04-03 | O lint falha com tags divergentes entre compose, compose.dev, deps e Testcontainers | ✓ VERIFICADO | Bloco de paridade do `lint.sh` (distintas != 1 ou ausente -> `fail`); execução local mostra uma tag por imagem. |
| 20 | 04-03 | values-prod: GHCR `IfNotPresent`, HPA 2-6, resources maiores, ECS, `existingSecret`, TLS, topologySpread | ✓ VERIFICADO | `values-prod.yaml` lido; render prod válido no kubeconform (9 recursos, sem Secret). |
| 21 | 04-03 | Sequência do zero 8080 -> 80 com smoke nas duas | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | Porta 80 do zero provada pelo CI; caminho 8080 só narrado no 04-03-SUMMARY. Ver Human Verification. |
| 22 | 04-03 | README ensina `make k8s`/`k8s-smoke`/`k8s-lint`/`k8s-down` com equivalentes sem make, pré-requisitos, porta 80, `localtest.me`, readiness com Valkey e rate limit atrás do Traefik | ✓ VERIFICADO | `README.md` seção `## Kubernetes local (make k8s)` e subseções (linhas 195-266). |
| 23 | 04-04 | Job `image`: buildx com cache gha e label de revisão, Trivy v0.76.0 por SHA falhando em CRITICAL corrigível, SBOM CycloneDX com inputs próprios e barreira de 50, artifacts `image` e `sbom` | ✓ VERIFICADO | `ci.yml` linhas 62-138; log do run. |
| 24 | 04-04 | Job `k8s-smoke` (needs `image`): kind v0.33.0 + Helm v4.3.1, obs-check, lint, imagem do artifact, `up.sh` e `smoke.sh`; diagnóstico na falha | ✓ VERIFICADO | `ci.yml` linhas 140-204; log do run. |
| 25 | 04-04 | Job `publish` só em push na main, needs dos três, `docker load` sem rebuild, tags `sha-{sha7}`/`latest`, attestations | ✓ VERIFICADO | `ci.yml` linhas 206-298; tags `latest` e `sha-7c9b868` respondem 200; attestations verificadas. |
| 26 | 04-04 | Token `contents: read` no topo; só o `publish` ganha escrita; actions por SHA | ✓ VERIFICADO | `ci.yml` linhas 10-11 e 213-219; nenhum `uses:` sem SHA de 40 hex. |
| 27 | 04-04 | Concorrência cancela só PR | ✓ VERIFICADO | `cancel-in-progress: ${{ github.event_name == 'pull_request' }}`. |
| 28 | 04-04 | Run da main com os 4 jobs em success, `latest` com pull anônimo 200 e attestations válidas | ✓ VERIFICADO | Conferido de forma independente (ver Resumo). |

**Placar:** 31/33 truths verificadas (2 presentes, comportamento não verificado).

### Proibições (must_haves.prohibitions)

Todas são `verification: test`. Pelo contrato fail-closed (ADR-550 D4), sem teste automatizado que falharia diante da violação, a proibição fica sinalizada mesmo com evidência limpa.

| Plano | Proibição | Disposição | Evidência coletada |
|-------|-----------|------------|--------------------|
| 04-01 | Não publicar a porta fora de 127.0.0.1 nem rotear 8081/bancos pelo Ingress | sinalizada (unverified-prohibition — revisão humana recomendada) | `listenAddress: "127.0.0.1"`; `docker port` = `127.0.0.1:80`; Ingress só `port.name: http`; `/actuator/health` pelo Ingress = 404. Nenhum teste de regressão. |
| 04-01 | Não deixar o cliente escolher o IP do rate limit | sinalizada | `TrustedProxyRateLimitIT` cobre o lado do app; o lado do Traefik é só configuração (ver truth 4). |
| 04-01 | Não executar kubectl/helm fora de `kind-link-pulse` | sinalizada | Em `up.sh` e `smoke.sh` não há chamada a `kubectl`/`helm` fora de `k()`/`h()` (além de `need`); o diagnóstico do CI usa `--context`/`--kube-context`. Inspeção estática apenas. |
| 04-02 | Não copiar dashboards/regras para fora de `observability/` | sinalizada | Varredura do git limpa; a guarda "observability.rules vazio" do `lint.sh` exige o `--set-file`, mas não detectaria uma cópia. |
| 04-02 | Anônimo do Grafana no máximo Viewer | sinalizada | `org_role: Viewer`; ao vivo `canEdit:false` e `POST /api/folders` = 403. Nenhum teste de regressão (o smoke não confere o papel). |
| 04-03 | Nenhum segredo nem default de demonstração no values-prod | sinalizada | `values-prod.yaml` só tem `secret.create: false` + `existingSecret`; o render prod não contém Secret. Nenhuma assertiva automatizada. |
| 04-03 | Smoke cria no máximo um link e não segue o 302 | sinalizada | Um só POST; nenhum `-L`/`--location` em `smoke.sh`. Inspeção estática. |
| 04-04 | Não publicar imagem diferente da testada nem a partir de PR | ✓ verificada | Aplicada pela própria plataforma: `if: github.event_name == 'push' && github.ref == 'refs/heads/main'` e `needs: [build-test, image, k8s-smoke]`, sem checkout nem build no `publish` (só `docker load` do artifact). O run 40789218786 mostra a ordem executada. |
| 04-04 | Nenhuma action por tag mutável | sinalizada | Todos os `uses:` de `ci.yml` têm SHA de 40 hex com a versão em comentário. Nenhum gate automatizado impede a regressão. Ressalva: o `lint.sh` puxa `ghcr.io/yannh/kubeconform:v0.8.0` por tag e schemas da branch `main` do datreeio (WR-09 da revisão); não é `uses:` de action, mas destoa da mesma política. |

**Resultado:** completa com 8 proibições sinalizadas.

### Required Artifacts

| Artefato | Esperado | Status | Detalhes |
|----------|----------|--------|----------|
| `deploy/kind/kind-config.yaml` | Nó com digest, podSubnet, 127.0.0.1:80 -> 30080 | ✓ VERIFICADO | Contém `listenAddress: "127.0.0.1"`, consumido pelo `up.sh` |
| `deploy/kind/values/traefik.yaml` | NodePort 30080, `externalTrafficPolicy: Local`, IngressClass padrão | ✓ VERIFICADO | Usado no `h upgrade --install traefik` |
| `deploy/kind/up.sh` | Lógica do `make k8s` | ✓ VERIFICADO | `image-archive`, contexto fixo; chamado pelo Makefile e pelo CI |
| `deploy/helm/deps/templates/valkey.yaml` | Valkey 8.1 com volatile-lru | ✓ VERIFICADO | Instalado no cluster (`valkey-0` Running) |
| `deploy/helm/link-pulse/templates/deployment.yaml` | Probes na porta management | ✓ VERIFICADO | `/actuator/health/readiness` e `/liveness` |
| `deploy/helm/link-pulse/values-dev.yaml` | Env do kind, proxies confiáveis | ✓ VERIFICADO | `10.244.0.0/16` |
| `app/src/test/java/dev/linkpulse/HealthGroupsIT.java` | IT dos grupos | ✓ VERIFICADO | 4 testes verdes no CI |
| `deploy/kind/values/kube-prometheus-stack.yaml` | Stack enxuta, seletores abertos, anônimo Viewer | ✓ VERIFICADO | `serviceMonitorSelectorNilUsesHelmValues: false` |
| `deploy/helm/link-pulse/templates/servicemonitor.yaml` | job link-pulse | ✓ VERIFICADO | `jobLabel: app.kubernetes.io/name`; ServiceMonitor no cluster |
| `deploy/helm/link-pulse/templates/prometheusrule.yaml` | PrometheusRule de observability/ | ✓ VERIFICADO | `fromYaml`; regras carregadas ao vivo |
| `deploy/helm/link-pulse/templates/dashboards-configmap.yaml` | Um ConfigMap por JSON | ✓ VERIFICADO | 2 ConfigMaps `grafana_dashboard` no cluster |
| `deploy/helm/link-pulse/templates/hpa.yaml` | HPA autoscaling/v2 | ✓ VERIFICADO | HPA ao vivo 3%/70% |
| `deploy/kind/values/metrics-server.yaml` | `--kubelet-insecure-tls` | ✓ VERIFICADO | metrics-server Running |
| `deploy/kind/smoke.sh` | Smoke link -> 302 -> stats + observabilidade | ✓ VERIFICADO | `smoke OK` no CI |
| `deploy/kind/lint.sh` | helm lint, kubeconform, guardas, paridade | ✓ VERIFICADO | `lint OK` local e no CI |
| `deploy/helm/link-pulse/values-prod.yaml` | Perfil prod sem segredo | ✓ VERIFICADO | `existingSecret` |
| `README.md` | Seção kind e seção do pipeline de imagem | ✓ VERIFICADO | `## Kubernetes local (make k8s)` e `gh attestation verify` (com a ressalva do WR-01) |
| `.github/workflows/ci.yml` | Jobs build-test, image, k8s-smoke, publish | ✓ VERIFICADO | trivy-action `@ed142fd0...` por SHA |

### Key Link Verification

| De | Para | Via | Status | Detalhes |
|----|------|-----|--------|----------|
| `kind-config.yaml` | `traefik.yaml` | 30080 | ✓ LIGADO | containerPort = nodePort |
| `kind-config.yaml` | `values-dev.yaml` | 10.244.0.0/16 | ✓ LIGADO | podSubnet = internal-proxies |
| `deployment.yaml` | `application.yml` | probes nos grupos liveness/readiness | ✓ LIGADO | Grupos definidos em `management.endpoint.health.group` |
| `values-dev.yaml` | `deps/templates/postgres.yaml` | Services postgres/valkey/mongo e mesma senha | ✓ LIGADO | `jdbc:postgresql://postgres:5432/linkpulse`; app Ready ao vivo |
| `Makefile` | `up.sh` | `bash deploy/kind/up.sh` | ✓ LIGADO | Alvo `k8s: image` |
| `up.sh` | `link-pulse-alerts.yml` | `--set-file observability.rules` | ✓ LIGADO | Linha 126 |
| `up.sh` | `observability/grafana/dashboards` | `observability.dashboards.{nome}` | ✓ LIGADO | Linhas 127-129 |
| `servicemonitor.yaml` | regra TargetDown | `job="link-pulse"` | ✓ LIGADO | up=1 com esse job ao vivo |
| `kube-prometheus-stack.yaml` | `dashboards-configmap.yaml` | `grafana_dashboard=1` | ✓ LIGADO | Dashboards carregados ao vivo |
| `hpa.yaml` | `deployment.yaml` | scaleTargetRef / sem replicas | ✓ LIGADO | HPA referencia `Deployment/link-pulse` |
| `smoke.sh` | `load/lib/common.js` | UA `Chrome/141.0.0.0` | ✓ LIGADO | Mesmo UA |
| `lint.sh` | `values-prod.yaml` / `compose.yaml` | render prod e paridade | ✓ LIGADO | Execução local |
| `Makefile` | `smoke.sh` | `bash deploy/kind/smoke.sh` | ✓ LIGADO | Alvo `k8s-smoke` |
| `ci.yml` | `up.sh` / `smoke.sh` | job k8s-smoke | ✓ LIGADO | Linhas 192 e 195 |
| `ci.yml` | `app/Dockerfile` | `context: "link pulse/app"` | ✓ LIGADO | Linha 77 |
| `app/Dockerfile` | GHCR | `LABEL org.opencontainers.image.source` | ✓ LIGADO | Linha 28 |

### Data-Flow Trace (Level 4)

| Artefato | Dado | Fonte | Dado real | Status |
|----------|------|-------|-----------|--------|
| Dashboards do Grafana no kind | JSON dos painéis | `observability/grafana/dashboards/*.json` -> `--set-file` -> ConfigMap -> sidecar | Sim (dashboards ao vivo com a legenda original) | ✓ FLUINDO |
| PrometheusRule | grupos de alerta | `observability/prometheus/rules/link-pulse-alerts.yml` -> `fromYaml` | Sim (4 regras ao vivo) | ✓ FLUINDO |
| `/stats` no smoke | `total` | redirect -> stream Valkey -> consumer -> Mongo | Sim (`total=1` no CI) | ✓ FLUINDO |
| Imagem publicada | tar testado | job `image` -> artifact -> `publish` | Sim (digest único entre as tags, attestations válidas) | ✓ FLUINDO |

### Behavioral Spot-Checks

| Comportamento | Comando | Resultado | Status |
|---------------|---------|-----------|--------|
| Lint dos charts sem cluster | `bash deploy/kind/lint.sh` | `lint OK`, exit 0 | ✓ PASS |
| Ingress chega ao app | `curl --resolve ... /links/zzzzzzz/stats` | 404 `application/problem+json` | ✓ PASS |
| Actuator fora do Ingress | `curl --resolve ... /actuator/health` | 404 | ✓ PASS |
| Scrape do app | `query=min(up{job="link-pulse"})` | `"1"` | ✓ PASS |
| Regras carregadas | `/api/v1/rules?type=alert` | 4 alertas com runbook_url | ✓ PASS |
| Grafana anônimo só leitura | `/api/dashboards/uid/...` e `POST /api/folders` | `folderUid: link-pulse`, `canEdit:false`; 403 | ✓ PASS |
| HPA com métrica | `kubectl get hpa` | `cpu: 3%/70%` | ✓ PASS |
| Imagem pública | pull anônimo de `latest` e `sha-7c9b868` | 200 / 200 | ✓ PASS |
| Attestations | `gh attestation verify` (provenance e cyclonedx) | exit 0 / exit 0 | ✓ PASS |
| CI da main | `gh run view 40789218786` | 4 jobs success, `smoke OK: cJinsNv` | ✓ PASS |
| Rate limit pela cadeia Traefik | 21 POSTs com XFF | não executado (muta o cluster) | ? SKIP -> humano |

### Probe Execution

Step 7c: não se aplica. A fase não declara probes `scripts/*/tests/probe-*.sh` e não existe nenhum no repositório. Os scripts executáveis da fase (`lint.sh`, `smoke.sh`, `up.sh`) foram cobertos pelos spot-checks e pelo run do CI.

### Requirements Coverage

| Requisito | Plano | Descrição | Status | Evidência |
|-----------|-------|-----------|--------|-----------|
| K8S-01 | 04-01, 04-02, 04-03 | Chart com Deployment, Service, Ingress, ConfigMap, Secret e HPA, values dev/prod | ✓ SATISFEITO | Templates + values-dev/prod; kubeconform válido; HPA ao vivo |
| K8S-02 | 04-01 | Liveness sem dependências externas; readiness com Postgres e Valkey | ✓ SATISFEITO | `application.yml` + probes + `HealthGroupsIT` verde |
| K8S-03 | 04-01, 04-02, 04-03 | `make k8s` cria o kind e instala Traefik, metrics-server, kube-prometheus-stack, deps e app | ✓ SATISFEITO | `up.sh`; CI e cluster local |
| K8S-04 | 04-02 | Scrape via ServiceMonitor; dashboards e regras automáticos | ✓ SATISFEITO | Conferido ao vivo e no smoke do CI |
| CI-02 | 04-04 | Build da imagem e publicação no GHCR na main | ✓ SATISFEITO | Job `publish`, pull anônimo 200, pacote público |
| CI-03 | 04-04 | Scan do Trivy e SBOM | ✓ SATISFEITO | Trivy gate + SBOM CycloneDX (>= 50 componentes) + attestation de SBOM verificada |
| CI-04 | 04-03, 04-04 | Deploy em kind e smoke criar link -> redirect -> stats | ✓ SATISFEITO | Job `k8s-smoke` verde |

Os 7 IDs da fase aparecem no frontmatter de algum plano; não há requisito órfão mapeado para a Phase 4 no REQUIREMENTS.md. Pendência de registro: o `REQUIREMENTS.md` ainda marca **CI-02** e **CI-03** como `[ ]`/`Pending`, embora as evidências os satisfaçam; o orquestrador deve atualizar.

### Anti-Patterns e achados da revisão (04-REVIEW.md)

Nenhum marcador `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` nos arquivos da fase. Confrontei os warnings da revisão com os requisitos e critérios:

| Arquivo | Linha | Achado | Severidade | Impacto no objetivo |
|---------|-------|--------|------------|---------------------|
| `README.md` | 451 | WR-01: o README diz que "HIGH e sem correção só aparecem no log" do Trivy, mas `severity: CRITICAL` + `ignore-unfixed: true` filtram o relatório e essas vulnerabilidades não aparecem. Confirmado no `ci.yml` (linhas 95-96). | ⚠️ Warning | Não contradiz o CI-03 (o scan roda e barra CRITICAL corrigível), mas é uma afirmação falsa sobre um controle de segurança num README lido por avaliadores. Corrigir o texto ou separar relatório e gate (DOC-01 é da Phase 5, mas a frase é desta fase). |
| `app/Dockerfile` | 41-42 | WR-04: o HEALTHCHECK consulta `/actuator/health/readiness`, que agora inclui `db,redis`. | ⚠️ Warning (risco para a Phase 5) | **Não contradiz o K8S-02 nem o SC2**: o kubelet/containerd ignora o HEALTHCHECK da imagem, e o Deployment define as próprias probes (liveness só com `livenessState`). No compose não há `restart` no serviço `app`, então `unhealthy` não reinicia nada, e a demonstração de fail-open do README usa o `compose.dev.yaml` com o app via `mvnw`. O risco é real para o ECS da Phase 5 (IAC-01): se a task definition herdar este health check, uma queda do ElastiCache/RDS recicla as tasks, o que viola o espírito do K8S-02. Recomendo levar para o planejamento da Phase 5 (apontar o HEALTHCHECK para a liveness ou definir o `healthCheck` da task explicitamente). |
| `application.yml` / alertas | 132-133 | WR-03: readiness com `db,redis` tira todas as réplicas de uma vez, e os 503 do Traefik não disparam nenhum alerta (`up` segue 1, 5xx do app não conta). | ⚠️ Warning (documentação/observabilidade) | O SC2 **exige** essa readiness, e o README registra a troca. O OBS-04 está atendido (os 4 alertas existem). A lacuna de alerta para indisponibilidade vista de fora não é requisito, mas vale um parágrafo no runbook/README. |
| `Makefile` / `up.sh` | 21, 78 / 54-57 | WR-02: o Makefile sempre passa `K8S_HTTP_PORT=80`; num cluster criado na 8080, todo `make k8s` sem argumento imprime aviso falso e sugere `make k8s-down` (destrutivo). | ⚠️ Warning | Atinge justamente o caminho documentado de porta 80 ocupada. Não quebra o deploy (o script reaproveita o cluster), mas induz o avaliador a apagar dados. |
| `deploy/kind/lint.sh` | 22-25 | WR-09: imagem do kubeconform por tag e schemas da branch `main`. | ⚠️ Warning | Gate não reproduzível; não afeta o resultado atual. |
| `values-prod.yaml` / `hpa.yaml` | — | WR-05 a WR-08: sem NetworkPolicy, sem TLS/credenciais para Mongo/Valkey, sem PDB, janela de scale-up contrária ao comentário. | ℹ️ Info | O values-prod é perfil de referência, só renderizado; nenhum requisito pede esses recursos. |
| `deferred-items.md` | — | O Id de `link-pulse:local` muda a cada `make image` sem mudança de código, então todo `make k8s` faz rollout. | ℹ️ Info | Não viola a truth 7 (tag nova -> rollout); só gera rollouts e tags desnecessários. |
| `up.sh` / `ci.yml` | — | Versões acima do baseline do CLAUDE.md (Traefik chart 42.1.0, kube-prometheus-stack 92.10.1, Helm 4.3.1). | ℹ️ Info | Decididas nos planos e documentadas no README; sem impacto. |

### Human Verification Required

### 1. Navegador: shortUrl, Grafana e /alerts (adiado do 04-02)

**Teste:** Abrir http://link-pulse.localtest.me/swagger-ui.html, criar um link pelo `POST /links` e clicar na `shortUrl`; depois abrir http://grafana.localtest.me e http://prometheus.localtest.me/alerts.
**Esperado:** A shortUrl abre o destino; o Grafana abre sem login no dashboard "Link Pulse — Aplicação" com dados de requisições; o `/alerts` lista os quatro alertas, inativos.
**Por que humano:** Verificação visual no navegador do host. As partes de API já foram conferidas.

### 2. Rate limit por IP real atrás do Traefik

**Teste:** Numa janela nova, 21 `POST /links` do host com `X-Forwarded-For` diferentes; depois um POST a partir de um pod do cluster passando pelo Traefik.
**Esperado:** 20 x 201 e 429 no 21º; o POST do pod recebe 201.
**Por que humano:** A cadeia Traefik -> app não tem teste automatizado; a execução relatada foi manual e intermitente.

### 3. Sequência do zero na porta 8080

**Teste:** `make k8s-down` -> `make k8s K8S_HTTP_PORT=8080` -> `make k8s-smoke` -> `make k8s-down` -> `make k8s` -> `make k8s-smoke`.
**Esperado:** `smoke OK` nas duas portas; na 8080, a shortUrl, o Grafana e o Prometheus levam `:8080`.
**Por que humano:** Só a porta 80 do zero é provada pelo CI.

### 4. Proibições sinalizadas

**Teste:** Revisar as 8 proibições sinalizadas na tabela "Proibições".
**Esperado:** Aceitar a evidência estática/observada ou pedir teste de regressão (os candidatos mais baratos são uma checagem no `lint.sh` de que todo `uses:` tem SHA, uma de que o values-prod não renderiza Secret e uma no smoke de que a 8081 não responde pelo Ingress).
**Por que humano:** Proibições test-tier sem teste automatizado ficam fail-closed.

### Gaps Summary

Não há gaps bloqueadores. Os 5 critérios do ROADMAP e os 7 requisitos (K8S-01..04, CI-02..04) estão satisfeitos com evidência independente: run da main verde com o smoke em kind, imagem pública no GHCR com attestations de provenance e SBOM verificadas, lint local verde e o cluster kind local respondendo com scrape, regras e dashboards carregados sem passo manual.

O que segura o `passed`: um human-check visual adiado pelo plano, duas truths de comportamento sem teste automatizado (rate limit pela cadeia do Traefik; sequência na 8080) e 8 proibições test-tier sem teste de regressão. Dos warnings da revisão, nenhum contradiz requisito ou critério desta fase. Dois merecem ação: o WR-01 (frase falsa sobre o Trivy no README) e o WR-04 (HEALTHCHECK na readiness), que deve entrar no planejamento do ECS na Phase 5. O WR-02 é um defeito de UX no caminho de porta ocupada e vale uma correção rápida.

---

_Verificado em: 2026-10-29T00:21:30Z_
_Verificador: Claude (gsd-verifier)_
