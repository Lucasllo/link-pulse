# Phase 4: Kubernetes no kind e entrega contínua - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-20
**Phase:** 04-kubernetes-no-kind-e-entrega-contínua
**Areas discussed:** Acesso pelo host

---

## Acesso pelo host

### Entrada do tráfego

| Option | Description | Selected |
|--------|-------------|----------|
| extraPortMappings 80 | hostPort 80 → Traefik, bind 127.0.0.1, sem port-forward | ✓ |
| extraPortMappings 8088 | Porta alta, evita conflito com 80 e com o compose | |
| kubectl port-forward | Túnel manual via `make k8s-forward` | |

### Hostnames

| Option | Description | Selected |
|--------|-------------|----------|
| *.localtest.me | link-pulse/grafana/prometheus.localtest.me, sem editar hosts | ✓ |
| localhost com paths | /grafana e /prometheus por path; conflita com GET /{code} | |
| Só o app no Ingress | Grafana/Prometheus por port-forward | |

### Base URL

| Option | Description | Selected |
|--------|-------------|----------|
| http://link-pulse.localtest.me | shortUrl clicável no host | ✓ |
| Você decide | Planejador alinha | |

### Grafana

| Option | Description | Selected |
|--------|-------------|----------|
| Login anônimo Viewer | Igual ao compose; admin com senha de demo | ✓ |
| Só admin/senha de demo | Exige login | |

### Saída do make k8s

| Option | Description | Selected |
|--------|-------------|----------|
| URLs + exemplo curl | 3 URLs e curl de criar/redirect | ✓ |
| Só as URLs | Mínimo | |

### IP do cliente atrás do Traefik

| Option | Description | Selected |
|--------|-------------|----------|
| Confiar no Traefik | internal-proxies = CIDR de pods do kind; rate limit por IP real | ✓ |
| Manter vazio | Rate limit global no kind, documentado | |

## Claude's Discretion

- Imagem no cluster (kind load vs GHCR; imagem usada pelo smoke no CI)
- Pipeline GHCR/Trivy/SBOM (tags, política de falha, formato do SBOM, ordem smoke × push)
- Values dev/prod e Secret
- Estrutura dos charts, kind-config e alvos do Makefile
- Configuração do kube-prometheus-stack (seletores, Alertmanager, recursos)

## Deferred Ideas

Nenhuma.
