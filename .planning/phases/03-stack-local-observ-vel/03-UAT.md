---
status: testing
phase: 03-stack-local-observ-vel
source: [03-VERIFICATION.md]
started: 2026-10-17T14:45:00Z
updated: 2026-10-17T14:45:00Z
---

## Current Test

number: 1
name: Painéis do Grafana no navegador
expected: |
  Com a stack de pé e depois de `make seed`, abrir http://localhost:3000 sem login. A home redireciona para o dashboard da aplicação; os painéis de requisições/s, p95/p99 do redirect e da criação, cache hit ratio e cliques/min mostram séries; o pipeline mostra lag, pendentes, tamanho do stream, publicados x gravados e descartados; sem tráfego o hit ratio exibe "sem tráfego" e o lag NaN exibe "indefinido"; nenhum painel em erro.
awaiting: user response

## Tests

### 1. Painéis do Grafana no navegador
expected: Com a stack de pé e depois de `make seed`, abrir http://localhost:3000 sem login e percorrer os dashboards "Link Pulse — Aplicação" e "Link Pulse — Pipeline de cliques". A home redireciona para o dashboard da aplicação; os painéis mostram séries; sem tráfego o hit ratio exibe "sem tráfego" e o lag NaN exibe "indefinido"; nenhum painel em erro.
result: [pending]

### 2. Proibições marcadas como não verificadas
expected: Confirmar o veredito não autoritativo do verificador (todas respeitadas) ou apontar violação: portas só no loopback, sem segredo real versionado, dashboards sem filtro por código/URL/IP, seed só pela API pública, carga sem mexer em config/thresholds/resumo. Ver a tabela de proibições em 03-VERIFICATION.md.
result: [pending]

### 3. Clareza dos runbooks
expected: Ler os quatro runbooks em observability/runbooks/ do ponto de vista de um avaliador/operador. Texto claro e útil; aceitar ou corrigir a afirmação imprecisa do runbook de lag sobre linkpulse_clicks_lost_total (WR-04 do 03-REVIEW.md).
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps
