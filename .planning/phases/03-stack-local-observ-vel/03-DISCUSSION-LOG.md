# Phase 3: Stack local observável - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-14
**Phase:** 03-stack-local-observ-vel
**Areas discussed:** Seed/demo, Compose e make up, k6 e rate limit

---

## Seed/demo

| Option | Description | Selected |
|--------|-------------|----------|
| Script k6 | Container grafana/k6 na rede do compose (`make seed`) | ✓ |
| Bash + curl | scripts/seed.sh; depende do Git Bash no Windows | |
| Container próprio | Serviço 'seed' no compose com imagem curl/alpine | |

**User's choice:** Script k6

| Option | Description | Selected |
|--------|-------------|----------|
| Pequeno e rápido | ~10–15 links, centenas a poucos milhares de cliques em 30–60 s | ✓ |
| Tráfego contínuo | Manter tráfego leve por alguns minutos | |
| Os dois modos | `make seed` rápido + `make demo` contínuo | |

**User's choice:** Pequeno e rápido

| Option | Description | Selected |
|--------|-------------|----------|
| Ficar abaixo do limite | No máximo ~15 links, rate limit default no compose | ✓ |
| Subir o limite no compose | LINKPULSE_RATELIMIT_LIMIT alto | |
| Respeitar o Retry-After | Esperar o Retry-After ao receber 429 | |

**User's choice:** Ficar abaixo do limite

| Option | Description | Selected |
|--------|-------------|----------|
| Basta, só 'hoje' | Sem backfill; documentar no README | ✓ |
| Backfill direto no Mongo | Inserir 30 dias de eventos via mongosh | |

**User's choice:** Basta, só 'hoje'

---

## Compose e make up

| Option | Description | Selected |
|--------|-------------|----------|
| compose.yaml novo + dev separado | compose.yaml para `make up`, compose.dev.yaml mantido para `make run` | ✓ |
| Um arquivo só com profiles | compose.yaml único com profile 'full' | |

**User's choice:** compose.yaml novo + dev separado

| Option | Description | Selected |
|--------|-------------|----------|
| Só as de uso | App 8080, Grafana 3000, Prometheus 9090 em 127.0.0.1 | ✓ |
| Tudo publicado | Também bancos e 8081 | |

**User's choice:** Só as de uso

| Option | Description | Selected |
|--------|-------------|----------|
| Defaults no compose | `${VAR:-default}` comentados como não-segredo | ✓ |
| .env.example obrigatório | `make up` copia para .env | |

**User's choice:** Defaults no compose

| Option | Description | Selected |
|--------|-------------|----------|
| down / clean | Parar mantendo volumes / remover volumes | ✓ |
| logs / ps | Atalhos de logs e estado | |
| seed / load | k6 do seed e da carga via container | ✓ |
| image | Build só da imagem local | ✓ |

**User's choice:** down / clean, seed / load, image (multiSelect)

---

## k6 e rate limit

| Option | Description | Selected |
|--------|-------------|----------|
| Taxa abaixo do limite | constant-arrival-rate ~15/min na criação; carga no redirect | ✓ |
| Limite alto só no teste | Override do rate limit no `make load` | |
| 429 como esperado | Criar acima do limite e aceitar 429 | |

**User's choice:** Taxa abaixo do limite
**Notes:** Claude apontou o risco de seed + load na mesma janela de 1 min somarem criações; o planejador deve tratar.

| Option | Description | Selected |
|--------|-------------|----------|
| Moderado e conservador | ~200 req/s; redirect p95<50ms p99<100ms; criação p95<200ms; erro<1% | ✓ |
| Agressivo | ~1000 req/s; p95<20ms p99<50ms | |
| Você decide | Calibrar medindo | |

**User's choice:** Moderado e conservador

| Option | Description | Selected |
|--------|-------------|----------|
| Só resumo no terminal | Resumo + JSON para o README | ✓ |
| Remote write no Prometheus | experimental-prometheus-rw | |

**User's choice:** Só resumo no terminal

| Option | Description | Selected |
|--------|-------------|----------|
| UA de navegador | Cliques da carga aparecem no /stats padrão | ✓ |
| UA padrão do k6 (bot) | Cliques marcados isBot | |

**User's choice:** UA de navegador

---

## Claude's Discretion

- Alertas e runbooks (limiares, local dos runbooks, sem Alertmanager)
- Desenho dos dashboards e painéis extras
- Estrutura interna de `observability/`
- Detalhes do compose (pins, acesso anônimo ao Grafana, scrape/retention)
- Saída e reexecução do seed

## Deferred Ideas

None
