# Debug Knowledge Base

## k8s-smoke-503-traefik (2026-10-28) — resolved
- **Sintoma:** primeira requisição pelo Ingress logo após `make k8s` → `503 no available server` (Traefik).
- **Causa:** `helm --wait` termina no pod Ready; o Traefik só põe o pod no load balancer depois do EndpointSlice + recarga (~2 s). `allowEmptyServices=true` vira 503.
- **Padrão:** "pronto" deve ser medido no plano de dados (a rota responde pelo app), não no plano de controle.
- **Correção:** espera da rota no fim de `deploy/kind/up.sh` (dd5ada9).

## Mongo 8 no kernel 6.19+ (2026-10-28) — resolved
- **Sintoma:** mongod morre ao subir no runner do GitHub (exit 137 no check do Testcontainers, "MongoDBContainer should be started first", StatefulSet Failed); local funciona.
- **Causa:** kernel 6.19+ x tcmalloc/rseq (SERVER-121912); o Mongo 8.0 recusa subir.
- **Padrão:** antes de culpar a imagem, procurar a linha `"s":"F"` no log do container; diferença de comportamento CI x local pode ser o kernel do runner.
- **Correção:** `GLIBC_TUNABLES=glibc.pthread.rseq=1` em compose, compose.dev, chart deps e ITs (7c9b868). Remover quando o Mongo corrigir o tcmalloc/check.
