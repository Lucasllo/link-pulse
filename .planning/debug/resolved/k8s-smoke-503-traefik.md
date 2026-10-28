---
status: resolved
trigger: "CI job k8s-smoke falha: POST /links respondeu 503: no available server (run 40107470284)"
created: 2026-10-28
updated: 2026-10-28
---

# Debug: k8s-smoke 503 "no available server" no CI

## Symptoms

- **Expected:** No job `k8s-smoke` do workflow `ci` (push da main, commit 5b912a3), `bash deploy/kind/smoke.sh` roda logo após `make k8s` (deploy/kind/up.sh) e o passo 1/5 `POST /links` via Ingress (http://link-pulse.localtest.me) responde 201; o job passa e libera o job `publish` (GHCR).
- **Actual:** `1/5 POST /links -> https://example.com/smoke-1792856369` → `smoke FALHOU: POST /links respondeu 503: no available server`. Job falha, `publish` é pulado.
- **Error messages:** `503: no available server` (corpo típico do Traefik quando o router existe mas o service não tem endpoint/servidor saudável registrado).
- **Timeline:** Primeiro run do CI com o job k8s-smoke (2026-10-24, run https://github.com/Lucasllo/link-pulse/actions/runs/40107470284). Na máquina local do autor, o mesmo caminho (up.sh + smoke.sh) passou durante a execução do plano 04-04.
- **Reproduction:** Push na main → workflow `ci` → job `k8s-smoke`. Diagnóstico pós-falha (`kubectl get pods -A`) mostra todos os pods Running 1/1; o pod `link-pulse-78dc676749-8dl48` tinha apenas 11s de idade, deps (postgres/mongo/valkey) 25s. Charts: traefik-42.1.0 (v3.7.15), kube-prometheus-stack-92.10.1, metrics-server-3.14.0. Runner ubuntu-24.04, kind v0.33.0, Helm v4.3.1.

## Initial hypothesis (orchestrator)

Race: o smoke envia o primeiro POST antes de o Traefik registrar o endpoint do pod recém-Ready no Service/Ingress (propagação EndpointSlice → provider kubernetesIngress do Traefik). Possível também que up.sh espere o rollout mas não espere a rota do Ingress responder. Verificar ainda se o pod de 11s indica rollout novo (ex.: reinício/upgrade do Deployment no fim do up.sh) e se o Service selector/porta batem.

Relevant files: deploy/kind/up.sh, deploy/kind/smoke.sh, .github/workflows/ci.yml, charts do app em deploy/ (Ingress/Service/Deployment).

Constraint: a correção deve valer para o avaliador também (mesmos scripts no CI e local), não só um sleep no workflow. Não fazer push sem autorização do usuário. Commits sem co-autoria do Claude (sem Co-Authored-By / "Generated with Claude Code").

## Current Focus

bug_class: Bohrbug condicionado a timing (corrida determinística: falha sempre que a 1a requisição cai na janela entre o Ready do pod e a recarga do Traefik)
hypothesis: CONFIRMADA. up.sh termina quando o helm --wait vê o pod Ready, mas o Traefik só põe o pod no load balancer do router depois do evento do EndpointSlice + recarga com throttle; o smoke dispara o POST nessa janela e recebe o 503 do load balancer vazio (allowEmptyServices=true no chart).

reasoning_checkpoint:
  hypothesis: "O POST /links do smoke recebe 503 'no available server' porque up.sh devolve o controle assim que o helm --wait vê o pod Ready, e o Traefik leva até ~2 s (evento do EndpointSlice + recarga com providersThrottleDuration) para pôr o pod no load balancer do router do Ingress; nesse intervalo o router existe com load balancer vazio (allowEmptyServices=true) e o Traefik responde 503 sem encaminhar ao app."
  confirming_evidence:
    - "CI: helm deployed 15:39:29.567, POST 15:39:29.611, 503 em 15:39:29.619; o pod tinha acabado de passar o startupProbe (15:39:29.341)."
    - "CI: o DispatcherServlet da porta 8080 nunca inicializou (o POST não chegou ao app); o corpo text/plain 'no available server' é do Traefik."
    - "Repro local run 1: 3 respostas 503 'no available server' nos ~0,8 s seguintes ao rollout status OK; app só respondeu ~2,2 s depois. Com 0 réplicas o 503 e o corpo são idênticos ao do CI."
    - "Chart traefik 42.1.0: providers.kubernetesIngress.allowEmptyServices=true (explica 503 em vez de 404)."
  falsification_test: "Se, com a espera pela rota no up.sh, o smoke ainda recebesse 503 'no available server' no passo 1, ou se a espera nunca terminasse com a rota de fato funcionando, a hipótese estaria errada."
  fix_rationale: "O defeito é o up.sh declarar o app pronto pelo plano de controle (pod Ready) e não pelo plano de dados (rota do Ingress). A correção faz o up.sh esperar, depois do helm --wait, até um GET pelo próprio Ingress chegar ao app (resposta JSON do app, inclusive o 404 de código inexistente, e não o text/plain do Traefik). Vale para o CI e para o avaliador porque ambos rodam o mesmo up.sh, e garante que a URL que o up.sh imprime já funciona. Não é sleep: termina assim que a rota converge e falha com mensagem clara se não convergir em 60 s."
  blind_spots: "Não medi o tempo exato de recarga do Traefik v3.7.15 no runner do GitHub (só o local). A espera cobre só o up.sh: um smoke.sh rodado logo após um restart de pod fora do up.sh (ex.: Docker Desktop reiniciado) ainda pode pegar a janela; está fora do escopo deste bug."
  candidate_causes:
    - "code: up.sh sem espera pelo plano de dados (Traefik) após o helm --wait; smoke.sh com POST único sem retry (por causa do rate limit)"
    - "config: chart do Traefik com allowEmptyServices=true e providersThrottleDuration padrão de 2 s (molda o erro e o tamanho da janela)"
    - "environment: runner do CI mais rápido (44 ms entre o fim do up.sh e o POST) do que o Git Bash/Docker Desktop local"
    - "data: descartado (o app nunca recebeu a requisição; rate limit não participa)"
  and_gate: "sim: a falha exige (a) nenhum passo esperar a rota do Ingress depois do helm --wait E (b) a primeira requisição cair na janela de propagação do Traefik (runner rápido). allowEmptyServices só troca o 404 por 503; não é causa. A correção remove (a), que é a condição sob controle do repositório."

fix_applied: espera pela rota do Ingress no fim de deploy/kind/up.sh (+ need curl). bash -n e shellcheck (koalaman/shellcheck:stable) limpos.
guardrail: aceito (ver Resolution.verification). Correção NÃO commitada; aguardando confirmação humana.
next_action: aguardar o usuário confirmar (rodar make k8s + make k8s-smoke local e, com autorização, um push para o CI verde no job k8s-smoke); depois commitar só deploy/kind/up.sh, arquivar a sessão em resolved/ e criar o knowledge-base.md

## Evidence

- timestamp: 2026-10-28
  checked: knowledge base (.planning/debug/knowledge-base.md)
  found: arquivo não existe; nenhuma sessão resolvida anterior
  implication: sem padrão conhecido; investigação do zero

- timestamp: 2026-10-28
  checked: log completo do job k8s-smoke (run 40107470284, job 120912891784), timestamps
  found: |
    15:39:18.68 helm install link-pulse começa (Ingress + Service + Deployment criados juntos)
    15:39:19    container do app inicia
    15:39:28.119 Tomcat da management (8081) sobe; app "Started in 8.2 s"
    15:39:29.341 primeira requisição na management (DispatcherServlet do Tomcat-1 inicializa) = startupProbe passa, readiness começa
    15:39:29.567 helm "STATUS: deployed" (--wait viu o Deployment disponível)
    15:39:29.611 smoke envia o POST /links (44 ms depois do fim do up.sh)
    15:39:29.619 resposta 503 "no available server"
    Diagnóstico pós-falha: todos os pods Running 1/1; link-pulse Ready=True, 0 restarts; nenhum erro no log do app (nenhuma requisição na porta 8080 chegou ao app).
  implication: o POST chegou ao Traefik ~100-300 ms depois de o pod ficar Ready. O router do Ingress existia desde 15:39:18 com o Service sem endpoint pronto; o 503 vem do load balancer vazio do Traefik, não do app. Consistente com a hipótese de corrida readiness -> EndpointSlice -> provider do Traefik.

- timestamp: 2026-10-28
  checked: deploy/kind/up.sh e deploy/kind/smoke.sh
  found: up.sh termina no `helm upgrade --install link-pulse ... --wait` e não espera a rota do Ingress responder; smoke.sh faz um único POST /links sem retry (de propósito: limite de 20 criações/min por IP). Os passos 3-5 já têm loop de espera; os passos 1-2 não.
  implication: nada entre "pod Ready" e "primeira requisição pelo Ingress" absorve a latência de propagação do Traefik.

- timestamp: 2026-10-28
  checked: helm show values oci://ghcr.io/traefik/helm/traefik --version 42.1.0 (values do projeto não sobrescrevem providers)
  found: providers.kubernetesIngress.allowEmptyServices = true ("Allows to return 503 when there are no endpoints available"). Traefik tem providers.providersThrottleDuration padrão de 2 s entre recargas de configuração.
  implication: com Ingress criado e Service sem endpoint pronto, o Traefik monta o router com load balancer vazio e responde exatamente "503 no available server" (sem isso seria 404). A troca para o servidor do pod só vale depois do evento do EndpointSlice + recarga (com throttle), uma janela de até alguns segundos após o Ready.

- timestamp: 2026-10-28
  checked: reprodução no kind local (scratchpad/repro.sh): scale 0 -> probe -> scale 1 -> rollout status -> GET /links/zz-probe-inexistente/stats pelo Ingress em loop a cada 50 ms
  found: |
    Com 0 réplicas: "503 text/plain; charset=utf-8", corpo "no available server" (idêntico ao CI).
    Run 1: rollout status OK 19:42:19.661; respostas 503 "no available server" em 19:42:19.898, 20.223, 20.486; primeira resposta do app (404 application/problem+json) em 19:42:21.904, ~2,2 s depois do rollout status.
    Run 2: rollout status OK 19:43:02.111; primeira sondagem (19:43:03.435) já chegou ao app.
  implication: hipótese confirmada por observação direta. Existe uma janela de até ~2 s após o pod ficar Ready (o que o helm --wait/rollout status enxerga) em que o Traefik ainda responde 503 pelo load balancer vazio. Intermitente conforme a fase do throttle de recarga do Traefik; no CI o smoke chegou 44 ms após o up.sh e caiu na janela. Localmente, o overhead do Git Bash/Docker Desktop (docker port no início do smoke.sh) tende a empurrar o POST para fora da janela, o que explica o "passou na máquina do autor".

- timestamp: 2026-10-28
  checked: curl nativo do Git Bash (/mingw64/bin/curl) com MSYS_NO_PATHCONV=1 e `-o /dev/null`
  found: retorna exit 23 (falha ao escrever em "/dev/null", que vira um caminho Windows inexistente), embora o -w ainda imprima o status
  implication: a espera no up.sh não pode usar `-o /dev/null`; deve seguir o padrão do smoke.sh (corpo e -w pelo stdout, última linha = metadado).

- timestamp: 2026-10-28
  checked: caminho real com a correção (scratchpad/e2e.sh fixed-1 e fixed-2): helm uninstall link-pulse -> bash deploy/kind/up.sh -> sondagem imediata -> bash deploy/kind/smoke.sh
  found: |
    fixed-1: helm deployed 19:48:32.755; "Esperando a rota..." 32.815; up.sh segue 33.929 (espera 1,1 s); 1a sondagem pós-up.sh = 404 application/problem+json; smoke OK (5/5).
    fixed-2: helm deployed 20:09:16.965; espera 1,3 s; 1a sondagem pós-up.sh = app; smoke OK (5/5).
  implication: a espera absorve a janela (1,1-1,3 s) e o smoke passa logo em seguida.

- timestamp: 2026-10-28
  checked: caminho real SEM a correção (git checkout de up.sh): e2e.sh reverted-1 e watch-e2e.sh reverted-2 (observador sondando a rota a cada 100 ms durante o install)
  found: |
    reverted-1: 1a sondagem pós-up.sh = 000 (1a requisição fria do app > 2 s), depois app; smoke OK.
    reverted-2: último 503 do Traefik 0,28 s ANTES do fim do helm --wait (0,6 s antes do fim do up.sh); a requisição seguinte ficou ~2,5 s presa na 1a requisição fria do app.
  implication: no Windows/Docker Desktop o helm --wait volta mais tarde em relação ao Ready do que no runner do CI, e o 503 raramente passa do fim do up.sh: mesma explicação do "passa na máquina do autor". O e2e local não serve de gatilho confiável para a reversão; usado o harness com gatilho controlado abaixo.

- timestamp: 2026-10-28
  checked: harness A/B com gatilho controlado (scratchpad/race-ab.sh): scale 0 -> scale 1 -> rollout status (volta logo após o Ready, como o helm --wait no CI) -> 1a requisição. A = sem espera (up.sh revertido); B = bloco de espera extraído literalmente do up.sh corrigido (sed entre o echo "Esperando a rota..." e o done)
  found: |
    A: 1 de 5 com a 1a requisição = 503 text/plain do Traefik (somado ao repro run 1: 2 de 7).
    B: 0 de 5; espera de 1164 a 3742 ms; 1a requisição depois da espera sempre 404 application/problem+json do app.
    Caminho de falha do bloco (host sem rota, prazo reduzido para 3 s): sai com exit 1 e a mensagem com os comandos de diagnóstico; não fica pendurado.
  implication: revert-and-reconfirm: sem a espera, o 503 volta quando a 1a requisição sai logo após o Ready; com a espera, não volta. O critério "resposta JSON = app" não sai antes da hora (nenhum 503 depois da espera).

- timestamp: 2026-10-28
  checked: verificações adjacentes
  found: bash deploy/kind/lint.sh -> "lint OK"; shellcheck limpo; bash -n ok; arquivo continua LF e 100755; smoke passos 2-5 verdes nas rodadas fixed-1/fixed-2. Nenhum código Java ou chart alterado.
  implication: sem regressão nos vizinhos do arquivo alterado.

## Eliminated

- hypothesis: Service/Ingress com selector ou porta errados (503 permanente)
  evidence: no kind local, com o mesmo chart, a mesma rota chega ao app ~2 s após o Ready (repro run 1) ou de imediato (run 2); o CI usa o mesmo chart. Um erro de selector/porta nunca convergiria.
  timestamp: 2026-10-28

- hypothesis: pod recriado/rollout novo no fim do up.sh (o pod de 11 s seria um segundo pod)
  evidence: describe do CI: Start Time 15:39:18 = início do helm install link-pulse (REVISION 1, release novo), Restart Count 0, um único ReplicaSet (78dc676749). Os 11 s de idade são só o tempo desde a criação pelo próprio install.
  timestamp: 2026-10-28

- hypothesis: o próprio app respondeu 503 (dependência indisponível, rate limit)
  evidence: corpo "no available server" em text/plain é do Traefik (o app responde application/problem+json). No log do app só o DispatcherServlet do Tomcat-1 (management, porta 8081, probes) inicializou; o DispatcherServlet da porta 8080 nunca foi inicializado, ou seja, o POST nunca chegou ao app.
  timestamp: 2026-10-28

## Resolution

root_cause: "deploy/kind/up.sh considera o app pronto quando o helm --wait vê o pod Ready (plano de controle) e devolve o controle sem esperar o Traefik pôr o pod no load balancer da rota do Ingress (plano de dados, até ~2 s depois: evento do EndpointSlice + recarga com throttle). O smoke.sh, rodado logo em seguida no CI (44 ms), fez o POST /links nessa janela e recebeu o 503 'no available server' do load balancer vazio (allowEmptyServices=true no chart do Traefik)."
fix: "deploy/kind/up.sh: depois do helm --wait do app, espera até 60 s a rota do Ingress chegar ao app (GET $BASE/links/up-probe/stats com --resolve para 127.0.0.1; pronto quando o Content-Type é JSON, que só o app responde; o Traefik responde text/plain). Sonda a cada 1 s, curl com --max-time 2, corpo e -w pelo stdout (sem -o /dev/null, que quebra no curl nativo do Git Bash). Sem convergir em 60 s, sai com exit 1 e comandos de diagnóstico. Acrescenta need curl. Vale para o CI e para o avaliador (mesmo up.sh), e a URL impressa pelo make k8s já funciona quando ele termina."
oracle_type: specified (o smoke exige 201 no POST /links; a espera exige uma resposta do app, não do Traefik)
verification:
  target_test: { result: pass, detail: "e2e real fixed-1 e fixed-2: smoke OK; harness B 0/5 com 503 depois da espera" }
  mutation_check: { result: skipped, reason_if_skipped: "sem Stryker/ferramenta de mutação para bash; o equivalente manual é a variante A (remover a espera): o 503 volta em 1 de 5 (2 de 7 somando o repro)" }
  no_op_deletion: { result: pass, deletion_justified_by_rca: false, detail: "diff só acrescenta (24 linhas, um arquivo)" }
  adjacent_tests: { result: pass, suites_run: ["deploy/kind/lint.sh", "shellcheck", "bash -n", "smoke.sh passos 1-5"] }
  revert_and_reconfirm: { result: pass, bug_returned_on_revert: true, fixed_on_reapply: true, detail: "gatilho controlado (race-ab.sh): A 1/5 com 503, B 0/5. No e2e local revertido o 503 não passou do fim do up.sh (helm --wait volta mais tarde no Windows), o mesmo motivo de o bug só aparecer no CI" }
  guardrail_verdict: accepted
files_changed: [deploy/kind/up.sh]
commit: dd5ada9

## Follow-up: Mongo 8 recusado no kernel 6.19+ do runner

Com o up.sh corrigido, o run 40785701265 caiu antes, em build-test (ITs com "MongoDBContainer should be started first") e no kind (StatefulSet mongo Failed). O mongod morria ao subir; o log do run 40787814614 trouxe a causa:
"MongoDB cannot start: Linux kernel versions 6.19 and newer has a known incompatibility with this version of MongoDB" (SERVER-121912, tcmalloc x ABI do rseq).

- Hipótese descartada: a tag mongo:8.0 ter passado para 8.0.34 (2026-10-27). Fixar 8.0.32 (c738f8b) falhou igual. A tag fixa ficou, pela reprodutibilidade.
- Correção (7c9b868): GLIBC_TUNABLES=glibc.pthread.rseq=1 no Mongo do compose, do compose.dev, do chart deps e dos ITs (a imagem traz rseq=0).
- Verificação: run 40789218786 com build-test, image, k8s-smoke ("Esperando a rota..." 1,4 s, smoke OK nos 5 passos) e publish verdes; pull anônimo do latest = 200; pacote public; gh attestation verify (provenance e CycloneDX) OK para sha-7c9b868.
