---
phase: 04-kubernetes-no-kind-e-entrega-cont-nua
reviewed: 2026-10-28T12:00:00Z
depth: standard
files_reviewed: 38
files_reviewed_list:
  - .github/workflows/ci.yml
  - .gitignore
  - app/pom.xml
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
  - deploy/helm/link-pulse/values.yaml
  - deploy/helm/link-pulse/values-dev.yaml
  - deploy/helm/link-pulse/values-prod.yaml
  - deploy/kind/kind-config.yaml
  - deploy/kind/lint.sh
  - deploy/kind/smoke.sh
  - deploy/kind/up.sh
  - deploy/kind/values/kube-prometheus-stack.yaml
  - deploy/kind/values/metrics-server.yaml
  - deploy/kind/values/traefik.yaml
  - Makefile
  - observability/README.md
  - README.md
findings:
  critical: 0
  warning: 9
  info: 8
  total: 17
status: issues_found
---

# Phase 4: Relatório de Code Review

**Revisado em:** 2026-10-28T12:00:00Z
**Profundidade:** standard
**Arquivos revisados:** 38
**Status:** issues_found

## Resumo

Revisei o workflow de CI (build-test, image com Trivy e SBOM, k8s-smoke, publish com attestations), os dois charts Helm (`link-pulse` e `deps`), os scripts do kind (`up.sh`, `smoke.sh`, `lint.sh`), os values do Traefik, do metrics-server e do kube-prometheus-stack, o `Makefile`, os arquivos compose, os grupos de health do `application.yml`, o `HealthGroupsIT` e os dois READMEs.

O trabalho é sólido: pin por SHA nas actions, o publish envia o tar que já foi testado, as guardas fail-closed do chart são testadas, `--kube-context` aparece em todas as chamadas e há pod security no Deployment. Não achei nenhum defeito que quebre o caminho feliz, o que bate com o CI verde. Os achados se concentram em:

1. **Uma afirmação falsa sobre o gate de segurança.** O README diz que HIGH e vulnerabilidades sem correção "aparecem no log" do Trivy, mas com `severity: CRITICAL` e `ignore-unfixed: true` elas nem aparecem.
2. **Readiness e HEALTHCHECK acoplados a dependências compartilhadas.** No kind, uma queda do Valkey ou do Postgres derruba todas as réplicas de uma vez e nenhum alerta dispara. O HEALTHCHECK da imagem passou a consultar o mesmo grupo, o que vira reinício em loop em orquestradores que agem sobre a saúde do container (o ECS da Phase 5).
3. **Lacunas no perfil de produção de referência.** Não há NetworkPolicy, mas o X-Forwarded-For é aceito de um /16 inteiro. Também faltam PDB e TLS/auth para Mongo e Valkey.
4. **Erro de UX no `make k8s`.** O Makefile sempre exporta `K8S_HTTP_PORT=80`, e o `up.sh` emite um aviso falso com uma sugestão destrutiva.

## Narrative Findings (AI reviewer)

## Warnings

### WR-01: README descreve errado o gate do Trivy: HIGH e sem correção NÃO aparecem no log

**File:** `README.md:451` (configuração em `.github/workflows/ci.yml:95-96`)
**Issue:** O README diz que o Trivy "reprova o job se houver vulnerabilidade CRITICAL com correção disponível (HIGH e sem correção só aparecem no log)". O passo, porém, roda com `severity: CRITICAL` e `ignore-unfixed: true`. O `--severity` do Trivy filtra o relatório, e não só o exit code, então vulnerabilidades HIGH nunca são listadas. O `--ignore-unfixed` também esconde todas as vulnerabilidades sem correção. O log de um run verde não mostra nada além de CRITICAL corrigível. Isso é uma afirmação falsa sobre um controle de segurança, num projeto cujo público são avaliadores que leem o README.
**Fix:** Separe "reportar" de "reprovar" com dois passos. Ou corrija o texto.
```yaml
      - name: Trivy (relatório HIGH/CRITICAL, inclusive sem correção)
        uses: aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25 # v0.36.0
        with:
          version: v0.76.0
          scan-type: image
          image-ref: link-pulse:local
          severity: HIGH,CRITICAL
          ignore-unfixed: false
          exit-code: "0"
          format: table
          timeout: 10m
      - name: Trivy (gate: falha em CRITICAL com correção)
        uses: aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25 # v0.36.0
        with:
          skip-setup-trivy: true
          scan-type: image
          image-ref: link-pulse:local
          severity: CRITICAL
          ignore-unfixed: true
          exit-code: "1"
          timeout: 10m
```

### WR-02: `make k8s` sempre exporta `K8S_HTTP_PORT=80` e gera aviso falso com sugestão destrutiva

**File:** `Makefile:21,78` e `deploy/kind/up.sh:54-57`
**Issue:** `K8S_HTTP_PORT ?= 80` no Makefile, junto com `K8S_HTTP_PORT=$(K8S_HTTP_PORT) bash deploy/kind/up.sh`, faz a variável chegar ao `up.sh` sempre definida. Suponha que o cluster tenha sido criado com `make k8s K8S_HTTP_PORT=8080`, o caminho documentado para quem tem a 80 ocupada. A partir daí, todo `make k8s` sem argumento cai em `[ -n "${K8S_HTTP_PORT:-}" ] && [ "$K8S_HTTP_PORT" != "$PORT" ]` e imprime "K8S_HTTP_PORT=80 foi ignorada", recomendando `make k8s-down e make k8s K8S_HTTP_PORT=80`. Ou seja, o script sugere apagar o cluster, com os dados, e recriá-lo justamente na porta que o usuário evitou. A checagem do `up.sh` não distingue "o usuário pediu" de "default do Makefile".
**Fix:** Deixe a variável vazia por padrão e só repasse quando houver valor. O default 80 já está no `up.sh` (`PORT=${K8S_HTTP_PORT:-80}`).
```make
K8S_HTTP_PORT ?=
k8s: image
	$(if $(K8S_HTTP_PORT),K8S_HTTP_PORT=$(K8S_HTTP_PORT) )bash deploy/kind/up.sh
```

### WR-03: Readiness com `db,redis` derruba todas as réplicas de uma vez, e nenhum alerta cobre a queda

**File:** `app/src/main/resources/application.yml:132-133`, `deploy/helm/link-pulse/templates/deployment.yaml:76-83`, `observability/prometheus/rules/link-pulse-alerts.yml:55`
**Issue:** Postgres e Valkey são dependências compartilhadas por todas as réplicas. Se um deles cai ou fica lento (o Valkey do kind é réplica única, sem PVC, com limite de 384Mi), todos os pods ficam NotReady ao mesmo tempo, o Service fica sem endpoints e o Traefik responde 503 para tudo. Isso inclui o redirect, que a aplicação foi desenhada para servir pelo Postgres com o Valkey fora (fail-open, Pitfall P1), e o redirect de links quentes que o cache serviria com o Postgres fora. O README documenta a troca, mas há uma lacuna de observabilidade que não está documentada:
- os 503 vêm do Traefik e não entram em `http_server_requests_seconds_count`, então o `LinkPulseHigh5xxRate` não dispara;
- o Prometheus Operator raspa endpoints NotReady (só descarta pods em fase Failed/Succeeded), e o processo segue vivo na 8081, então `up{job="link-pulse"}` continua 1 e o `LinkPulseTargetDown` também não dispara.

Resultado: uma indisponibilidade total do serviço no kind passa sem nenhum alerta.
**Fix:** Pode-se tirar `redis` (e de preferência `db`) da readiness e deixar o app degradar sozinho, que é o próprio desenho fail-open. Se a troca for mantida, falta um alerta que veja a queda pelo lado de fora. Exemplos: um alerta sobre `kube_deployment_status_replicas_available == 0` (exige kube-state-metrics, hoje desligado) ou o ServiceMonitor do Traefik com um alerta sobre `traefik_service_requests_total{code="503"}`. Uma alternativa mínima é documentar a lacuna no runbook e no README.

### WR-04: O HEALTHCHECK da imagem agora consulta a readiness com dependências e vira liveness em orquestradores como o ECS

**File:** `app/src/main/resources/application.yml:128-133` (o HEALTHCHECK está em `app/Dockerfile:41-42`)
**Issue:** Esta fase passou a incluir `db,redis` no grupo de readiness, e o comentário registra que "O HEALTHCHECK da imagem também consulta este grupo". Antes, o grupo padrão do Boot só olhava o `readinessState`. O Docker/compose não age sobre `unhealthy`, mas o ECS, alvo da próxima fase, substitui a task quando o health check do container falha. Uma queda do ElastiCache ou do RDS passaria a matar e recriar todas as tasks em loop, que é exatamente o que o K8S-02 proíbe ("dependência fora nunca reinicia"). A mudança também alterou o `make up` sem aviso: agora parar o Valkey deixa o container `app` unhealthy.
**Fix:** Aponte o HEALTHCHECK da imagem para a liveness, que só olha o estado interno, e deixe a readiness para quem roteia tráfego (o kubelet/Service e o target group do ALB).
```dockerfile
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --start-interval=2s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health/liveness || exit 1
```
Depois disso, atualize o comentário do `application.yml:130-131`.

### WR-05: values-prod confia no X-Forwarded-For de um /16 inteiro e o chart não tem NetworkPolicy

**File:** `deploy/helm/link-pulse/values-prod.yaml:37-39` (e `values-dev.yaml:26-28`)
**Issue:** `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES: 10.0.0.0/16` aparece descrito como "CIDR dos pods do ingress controller". Na prática, um /16 cobre toda a rede de pods (ou a VPC), não só o controller. O chart não cria NetworkPolicy, então qualquer workload do cluster alcança `link-pulse:8080` direto pelo ClusterIP, manda `X-Forwarded-For: <IP qualquer>` e escolhe a chave do rate limit, que é o bypass descrito no Pitfall P5. O README aceita isso no kind ("um avaliador só"), mas o values-prod se apresenta como perfil de produção de referência e herda o mesmo furo, sem nenhuma ressalva.
**Fix:** Adicione um `templates/networkpolicy.yaml` opcional (por exemplo, `networkPolicy.enabled: true` no values-prod). A porta `http` só deve aceitar tráfego do namespace/pods do ingress controller, e a `management` só do Prometheus. Em paralelo, restrinja o CIDR ao das subnets dos pods do controller, ou documente o risco no values-prod.
```yaml
{{- if .Values.networkPolicy.enabled }}
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: {{ include "link-pulse.fullname" . }}
spec:
  podSelector:
    matchLabels: {{- include "link-pulse.selectorLabels" . | nindent 6 }}
  policyTypes: [Ingress]
  ingress:
    - from: {{- toYaml .Values.networkPolicy.ingressFrom | nindent 8 }}
      ports: [{port: http}]
    - from: {{- toYaml .Values.networkPolicy.metricsFrom | nindent 8 }}
      ports: [{port: management}]
{{- end }}
```

### WR-06: Perfil de produção de referência sem TLS e sem credenciais para Mongo e Valkey (e Postgres sem sslmode)

**File:** `deploy/helm/link-pulse/values-prod.yaml:29-33,43-48`
**Issue:** O values-prod aponta para `mongodb://link-pulse-clicks:27017/linkpulse` (sem usuário, sem `tls=true`), `link-pulse-cache:6379` (sem senha e sem `SPRING_DATA_REDIS_SSL_ENABLED`; um ElastiCache com transit encryption recusa conexão sem TLS) e `jdbc:postgresql://link-pulse-db:5432/linkpulse` (sem `sslmode=require`, então o tráfego para o RDS sai em claro se o servidor aceitar). O contrato do `existingSecret` lista só `SPRING_DATASOURCE_PASSWORD` e `LINKPULSE_IP_HASH_KEY`. Um leitor que copie o perfil sobe a aplicação falando em texto claro e sem autenticação com dois dos três bancos.
**Fix:** Mova as URIs com credenciais para o Secret externo. O `secretRef` vem depois do `configMapRef` no `envFrom`, então ele prevalece. Ligue também o TLS:
```yaml
env:
  SPRING_DATASOURCE_URL: jdbc:postgresql://link-pulse-db:5432/linkpulse?sslmode=require
  SPRING_DATA_REDIS_SSL_ENABLED: "true"
# link-pulse-secrets: SPRING_DATASOURCE_PASSWORD, LINKPULSE_IP_HASH_KEY,
#   SPRING_DATA_REDIS_PASSWORD e SPRING_MONGODB_URI (mongodb+srv://user:pass@.../linkpulse?tls=true)
```
Remova `SPRING_MONGODB_URI` do `env` do values-prod e documente as chaves extras no comentário do `secret`.

### WR-07: Sem PodDisruptionBudget: um drain pode despejar todas as réplicas de produção juntas

**File:** `deploy/helm/link-pulse/values-prod.yaml:11-15,59-62` (nenhum template de PDB em `deploy/helm/link-pulse/templates/`)
**Issue:** O values-prod pede no mínimo 2 réplicas espalhadas por zona para ter disponibilidade, mas não há PDB. Um `kubectl drain`, um upgrade de node group ou o Karpenter/Cluster Autoscaler podem despejar as duas réplicas ao mesmo tempo, e a disponibilidade prometida pelo `minReplicas: 2` some justamente na manutenção.
**Fix:**
```yaml
{{- if .Values.podDisruptionBudget.enabled }}
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {{ include "link-pulse.fullname" . }}
spec:
  maxUnavailable: 1
  selector:
    matchLabels: {{- include "link-pulse.selectorLabels" . | nindent 6 }}
{{- end }}
```
Use `enabled: false` no values.yaml e `true` no values-prod. No kind, com 1 réplica, um PDB bloquearia o drain.

### WR-08: O `scaleUp.stabilizationWindowSeconds: 60` do HPA faz o contrário do comentário ("sobe rápido")

**File:** `deploy/helm/link-pulse/templates/hpa.yaml:24-26`
**Issue:** O default do HPA para scale-up é uma janela de estabilização de 0 s, que reage imediatamente. Com 60 s, o controlador usa a *menor* recomendação do último minuto, o que atrasa a subida num pico em até 60 s. O comentário "Sobe rápido num pico" descreve o default que a configuração está removendo.
**Fix:** Remova a janela de scale-up (`scaleUp: {stabilizationWindowSeconds: 0}`, ou omita o bloco) e mantenha só o `scaleDown: 300`. Se o atraso for intencional, corrija o comentário.

### WR-09: Gate do `lint.sh` depende de schemas remotos mutáveis (branch `main`) e de imagem por tag

**File:** `deploy/kind/lint.sh:22-25`
**Issue:** O kubeconform baixa os schemas dos CRDs de `raw.githubusercontent.com/datreeio/CRDs-catalog/main/...`, uma branch mutável, e os do Kubernetes do `-schema-location default`, também remoto, em todo run. A imagem `ghcr.io/yannh/kubeconform:v0.8.0` vem por tag mutável. Uma mudança no catálogo (ou indisponibilidade do GitHub raw) pode reprovar ou aprovar o job sem nenhum commit no repositório. Isso destoa da política do próprio projeto de fixar tudo por SHA, adotada depois do incidente do trivy-action.
**Fix:** Fixe o catálogo por commit (`.../datreeio/CRDs-catalog/<sha>/{{.Group}}/...`) e a imagem por digest (`ghcr.io/yannh/kubeconform:v0.8.0@sha256:...`). Melhor ainda: versione no repositório os schemas JSON de `monitoring.coreos.com` para ServiceMonitor e PrometheusRule e aponte `-schema-location` para o diretório local.

## Info

### IN-01: `waitUntilReady()` no `finally` mascara a falha original do teste

**File:** `app/src/test/java/dev/linkpulse/HealthGroupsIT.java:151-154,170-173`
**Issue:** Se uma asserção do `try` falha e o container não volta em 30 s, o `ConditionTimeoutException` do `finally` substitui a exceção original, e o relatório do Failsafe mostra só "não ficou ready", escondendo a causa real.
**Fix:** Faça só o `unpause` no `finally` e chame `waitUntilReady()` depois do bloco, ou capture a exceção do `finally` e use `addSuppressed` na original.

### IN-02: `make k8s-down` fixa o nome do cluster, mas `up.sh` e `smoke.sh` honram `KIND_CLUSTER`

**File:** `Makefile:81`
**Issue:** `KIND_CLUSTER=foo make k8s` cria `foo`, mas `make k8s-down` apaga `link-pulse`.
**Fix:** `KIND_CLUSTER ?= link-pulse` no Makefile e `kind delete cluster --name $(KIND_CLUSTER)`.

### IN-03: `GLIBC_TUNABLES=glibc.pthread.rseq=1` é incondicional e substitui a variável inteira da imagem

**File:** `compose.yaml:42-45`, `compose.dev.yaml:43-46`, `deploy/helm/deps/templates/mongo.yaml:47-51`, `app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java:47-50`
**Issue:** O comentário fala em "Kernel 6.19+", mas o override vale para qualquer kernel. Ele desliga os caches per-CPU do tcmalloc em todo ambiente, e o Mongo perde desempenho mesmo onde o problema não existe. Como a variável é substituída, e não acrescentada, qualquer outro tunable que uma tag futura da imagem colocar em `GLIBC_TUNABLES` se perde em silêncio. Para o propósito dev/portfólio é aceitável.
**Fix:** Registre no comentário que o efeito vale para todos os kernels e anote o upgrade que permite remover o workaround (a correção do SERVER-121912). Em todo bump da tag, confira o `GLIBC_TUNABLES` da imagem.

### IN-04: "Dashboard novo: basta criar o JSON" quebra com nomes que têm `.`, `_` ou maiúsculas

**File:** `deploy/kind/up.sh:127-129`, `deploy/kind/lint.sh:44-46`, `deploy/helm/link-pulse/templates/dashboards-configmap.yaml:16`
**Issue:** `--set-file observability.dashboards.$(basename .json)` trata `.` como separador de chave. Um `app.v2.json` vira o mapa aninhado `{app: {v2: ...}}`, e o template escreve um map no lugar do JSON. Nomes com `_` ou maiúsculas geram um nome de ConfigMap inválido (RFC 1123), e o install falha. O `observability/README.md:44` promete que qualquer JSON entra.
**Fix:** No loop, valide `[[ $name =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || fail "nome de dashboard inválido: $f"`, ou documente a regra de nome.

### IN-05: `observability/README.md` com texto desatualizado sobre o CI

**File:** `observability/README.md:36`
**Issue:** O texto diz "o CI da Phase 4 pode reaproveitar o `make obs-check`", mas o job `k8s-smoke` já roda o `make obs-check` (`ci.yml:175-176`).
**Fix:** Troque por "o job `k8s-smoke` do CI roda o `make obs-check`".

### IN-06: `checksum/secret` faz hash do segredo em claro e não reage a Secret externo

**File:** `deploy/helm/link-pulse/templates/deployment.yaml:22`
**Issue:** A annotation guarda o SHA-256 do Secret renderizado, com os valores em claro. Quem lê o pod pode testar senhas de baixa entropia offline. Com `secret.create=false` (values-prod), o include sai vazio, e a rotação do `existingSecret` não dispara rollout.
**Fix:** Aceitável para o kind. No prod, documente o uso de um reloader (por exemplo, stakater/Reloader) ou de uma annotation com a versão do ExternalSecret.

### IN-07: O diagnóstico de falha do CI não coleta logs das dependências nem do Traefik

**File:** `.github/workflows/ci.yml:197-204`
**Issue:** Os logs coletados se restringem a `-l app.kubernetes.io/name=link-pulse`. A falha recente do Mongo no kernel 6.19 (rseq) e o 503 do Traefik só aparecem nos logs de `mongo`/`traefik`, que não são coletados.
**Fix:** Acrescente `kubectl -n link-pulse logs -l app.kubernetes.io/part-of=link-pulse --all-containers --tail=200 || true` e `kubectl -n traefik logs deploy/traefik --tail=200 || true`.

### IN-08: `up.sh` não valida `K8S_HTTP_PORT` e sai em silêncio se o nó do kind estiver parado

**File:** `deploy/kind/up.sh:52,60-62`
**Issue:** `K8S_HTTP_PORT` vai direto para o `sed` e para o YAML do kind sem checar se é numérica. Um valor com `/` quebra o `sed`, e um valor com `\n` injeta YAML. Com o cluster listado por `kind get clusters` mas o container do nó parado (Docker Desktop reiniciado), o `docker port ... | head -1` falha sob `pipefail` e o script morre sem a mensagem amigável de "recrie o cluster".
**Fix:** `[[ $PORT =~ ^[0-9]{1,5}$ ]] || { echo "K8S_HTTP_PORT inválida: $PORT" >&2; exit 1; }`. Na linha 52, use `MAPPED=$(docker port ... 2>/dev/null | head -1 || true)` e mostre uma mensagem quando vier vazio, como o `smoke.sh` já faz.

---

_Revisado em: 2026-10-28T12:00:00Z_
_Revisor: Claude (gsd-code-reviewer)_
_Profundidade: standard_
