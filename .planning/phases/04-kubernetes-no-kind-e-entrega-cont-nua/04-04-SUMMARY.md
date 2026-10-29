---
phase: 04-kubernetes-no-kind-e-entrega-cont-nua
plan: 04
subsystem: infra
tags: [github-actions, ghcr, trivy, sbom, cyclonedx, slsa, attestations, kind, mongodb, traefik]
status: complete

requires:
  - phase: 04-03
    provides: "bash deploy/kind/lint.sh, up.sh e smoke.sh (interface sem make para o CI)"
  - phase: 03
    provides: "app/Dockerfile multi-stage (usuário 10001, HEALTHCHECK, LABEL org.opencontainers.image.source)"
provides:
  - "Job image: buildx com cache gha, Trivy v0.76.0 (trivy-action v0.36.0 por SHA) falhando em CRITICAL com correção, SBOM CycloneDX barrado abaixo de 50 componentes, artifacts image e sbom"
  - "Job k8s-smoke: kind v0.33.0 + Helm v4.3.1, obs-check, lint.sh, imagem do commit via artifact, up.sh e smoke.sh do avaliador, diagnóstico do cluster na falha"
  - "Job publish (só push na main): docker load do tar testado, push para ghcr.io/lucasllo/link-pulse com sha-{sha7} e latest, provenance SLSA e SBOM atestados no registry"
  - "Pacote ghcr.io/lucasllo/link-pulse público, verificável com gh attestation verify"
  - "up.sh espera a rota do Ingress chegar ao app antes de terminar"
  - "Mongo 8 sobe em kernel 6.19+ (GLIBC_TUNABLES=glibc.pthread.rseq=1) no compose, compose.dev, chart deps e ITs; imagem fixada em mongo:8.0.32"
affects: [05]

actuals:
  tokens: 8000
  tasks: 3
  commits: 6

tech-stack:
  added: [aquasecurity/trivy-action v0.36.0 (Trivy v0.76.0), actions/attest v4.2.2, helm/kind-action v1.15.1 (install_only), azure/setup-helm v5]
  patterns:
    - "Publica só o tar que passou no Trivy e no smoke (docker load, sem rebuild)"
    - "Escrita no token só no job publish; todas as actions por SHA com a versão em comentário"
    - "Pronto = rota do Ingress responde pelo app (plano de dados), não pod Ready (plano de controle)"

key-files:
  created: []
  modified:
    - .github/workflows/ci.yml
    - README.md
    - app/pom.xml
    - deploy/kind/up.sh
    - compose.yaml
    - compose.dev.yaml
    - deploy/helm/deps/values.yaml
    - deploy/helm/deps/templates/mongo.yaml
    - app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java

key-decisions:
  - "Spring Boot 4.1.2 (patch) em vez de sobrescrever o Tomcat no BOM, para zerar 3 CVEs CRITICAL do tomcat-embed-core 11.0.24"
  - "A espera da rota fica no up.sh (não no workflow), para valer igual para o CI e para o avaliador"
  - "Mongo: workaround rseq=1 por env, com a imagem fixada em tag de patch (8.0.32) pela reprodutibilidade; remover o env quando o Mongo corrigir SERVER-121912"
  - "Visibilidade do pacote: já estava public na verificação; nenhuma troca manual foi necessária"

patterns-established:
  - "Falha de container em CI que não reproduz local: procurar a linha \"s\":\"F\" no log antes de culpar a imagem (pode ser o kernel do runner)"

requirements-completed: [CI-02, CI-03, CI-04]

coverage:
  - id: D1
    description: "Todo push e PR builda a imagem, barra CVE CRITICAL corrigível e gera o SBOM CycloneDX"
    requirement: CI-03
    verification:
      - kind: e2e
        ref: "gh run view 40789218786 (job image)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Todo push e PR sobe kind e passa no smoke do avaliador com a imagem do commit"
    requirement: CI-04
    verification:
      - kind: e2e
        ref: "gh run view 40789218786 (job k8s-smoke: smoke OK nos 5 passos)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Na main, a imagem testada vai para o GHCR público com provenance e SBOM atestados"
    requirement: CI-02
    verification:
      - kind: e2e
        ref: "pull anônimo de ghcr.io/v2/lucasllo/link-pulse/manifests/latest = 200; gh attestation verify (provenance e https://cyclonedx.org/bom) para sha-7c9b868"
        status: pass
    human_judgment: false

duration: "Tasks 1-2 em 2026-10-24; checkpoint e correções do CI em 2026-10-28 (~1h40)"
completed: 2026-10-28
---

# Phase 4 Plan 04: CI com Trivy, SBOM, smoke em kind e publish no GHCR Summary

**Cada push e PR builda a imagem, barra CVE crítica, gera o SBOM e prova o deploy no kind com os scripts do avaliador. Na main, a mesma imagem vai para o GHCR público com provenance e SBOM atestados. O primeiro run verde exigiu corrigir uma corrida no `up.sh` e o Mongo 8 no kernel 6.19+ do runner.**

## Performance

- **Tasks:** 3 (Tasks 1 e 2 em 2026-10-24; checkpoint da Task 3 com as correções em 2026-10-28)
- **Arquivos:** 9 modificados
- **Run verde:** [40789218786](https://github.com/Lucasllo/link-pulse/actions/runs/40789218786)

| Job | Tempo |
|-----|-------|
| build-test | 2m15s |
| image | 3m26s |
| k8s-smoke | 4m08s (espera da rota 1,4 s; smoke 61 s) |
| publish | 38s |

## Accomplishments

- **Task 1 (`46bf034`):** jobs `image` e `k8s-smoke` no `ci.yml`, concorrência cancelando só PR. Antes, `31a584e` subiu o Boot para 4.1.2, porque o Trivy local reprovou 3 CVEs CRITICAL do Tomcat 11.0.24.
- **Task 2 (`5b912a3`):** job `publish` só em push na main, sem checkout nem build: `docker load` do tar testado, tags `sha-{sha7}` e `latest`, `actions/attest` duas vezes (provenance SLSA e SBOM CycloneDX). README com a seção "Imagem publicada (GHCR)".
- **Task 3 (checkpoint):** o primeiro push falhou no `k8s-smoke`. A correção passou por três commits (abaixo) até o run 40789218786 sair verde nos quatro jobs. Verificação do plano: pull anônimo do `latest` = 200, pacote `public`, `gh attestation verify` OK para provenance e SBOM de `sha-7c9b868` (equivalente a `GHCR-PUBLIC-OK`).

## Task Commits

1. **Pré-requisito: Boot 4.1.2** - `31a584e` (fix)
2. **Task 1: image + k8s-smoke** - `46bf034` (ci)
3. **Task 2: publish + README** - `5b912a3` (ci)
4. **Task 3 / desvio 1: espera da rota no up.sh** - `dd5ada9` (fix)
5. **Task 3 / desvio 2: mongo:8.0.32** - `c738f8b` (fix)
6. **Task 3 / desvio 3: GLIBC_TUNABLES rseq=1** - `7c9b868` (fix)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Smoke recebia 503 "no available server" logo após o up.sh**
- **Encontrado durante:** Task 3 (run 40107470284)
- **Problema:** o `helm --wait` termina no pod Ready, mas o Traefik só põe o pod no load balancer depois do EndpointSlice e da recarga (~2 s); com `allowEmptyServices=true` responde 503. O POST do smoke saiu 44 ms depois do up.sh.
- **Correção:** o `up.sh` espera até 60 s um GET pelo Ingress responder JSON (só o app responde JSON); sem convergir, sai com exit 1 e comandos de diagnóstico. Sessão: `.planning/debug/resolved/k8s-smoke-503-traefik.md`.
- **Commit:** dd5ada9

**2. [Rule 3 - Bloqueio] Mongo 8 recusado no kernel 6.19+ do runner**
- **Encontrado durante:** Task 3 (runs 40785701265 e 40787814614)
- **Problema:** `"MongoDB cannot start: Linux kernel versions 6.19 and newer has a known incompatibility"` (SERVER-121912). Os ITs falhavam com "MongoDBContainer should be started first" e o StatefulSet do kind ficava Failed.
- **Hipótese descartada:** a tag `mongo:8.0` ter passado para a 8.0.34. Fixar 8.0.32 (`c738f8b`) falhou do mesmo jeito; a tag fixa ficou pela reprodutibilidade.
- **Correção:** `GLIBC_TUNABLES=glibc.pthread.rseq=1` no compose, no compose.dev, no chart deps e nos ITs.
- **Commits:** c738f8b, 7c9b868

**Total de desvios:** 3 commits de correção para 2 problemas de ambiente do CI. **Impacto:** nenhum escopo novo; os dois valem também para o avaliador.

## Issues Encountered

- O pacote já estava público quando a verificação rodou, então não houve troca manual de visibilidade.
- A anotação "ubuntu-latest vai migrar para Ubuntu 26 a partir de 2026-10-19" segue nos runs; o runner atual ainda é 24.04, mas o kernel já é 6.19+.

## User Setup Required

None: o push foi autorizado na sessão e o pacote já estava público.

## Next Phase Readiness

- A Phase 5 (Terraform AWS e vitrine) pode usar `ghcr.io/lucasllo/link-pulse:sha-{sha7}` como imagem de referência e o CI como gate.
- Pendência: remover o `GLIBC_TUNABLES` quando o Mongo corrigir o tcmalloc/check do kernel.

## Self-Check: PASSED

- FOUND: .github/workflows/ci.yml (jobs build-test, image, k8s-smoke, publish), README.md (gh attestation verify)
- FOUND commits: 31a584e, 46bf034, 5b912a3, dd5ada9, c738f8b, 7c9b868
- Verify da Task 3: run verde com os 4 jobs, manifest 200 anônimo, attestations OK
