## Deferred Items

- Id da imagem `link-pulse:local` muda a cada `make image` sem mudança de código
  status: open
  **Found during:** 04-02 (Task 3). **What:** quatro `make k8s` seguidos geraram quatro tags (`local-0dbf77f2041f`, `local-84ba116a12ca`, `local-5afc2c5b7707`, `local-54f94117255f`), todas com o mesmo `CreatedAt`; provável attestation/provenance do BuildKit no containerd image store do Docker 29. Efeito: todo `make k8s` faz rollout do app e acumula tags e o `.kind/link-pulse.tar`. **Onde:** `Makefile` (alvo `image`) e o cálculo da tag em `deploy/kind/up.sh` (04-01). Candidatos: `--provenance=false` no build do compose ou tag pelo digest do conteúdo (`docker image inspect --format '{{.RootFS.Layers}}'`). Fora do escopo da 04-02.
