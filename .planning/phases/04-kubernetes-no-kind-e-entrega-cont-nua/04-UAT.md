---
status: testing
phase: 04-kubernetes-no-kind-e-entrega-cont-nua
source: [04-VERIFICATION.md]
started: 2026-10-29T00:25:00Z
updated: 2026-10-29T00:25:00Z
---

## Current Test

number: 1
name: Navegador - shortUrl, Grafana e alertas no kind
expected: |
  A shortUrl abre o destino; o Grafana abre sem login direto no dashboard 'Link Pulse — Aplicação' com dados de requisições; o Prometheus lista os quatro alertas do Link Pulse, inativos com o app de pé
awaiting: user response

## Tests

### 1. Navegador - shortUrl, Grafana e alertas no kind
Abrir http://link-pulse.localtest.me/swagger-ui.html, criar um link pelo POST /links e clicar na shortUrl; depois abrir http://grafana.localtest.me e http://prometheus.localtest.me/alerts.
expected: A shortUrl abre o destino; o Grafana abre sem login direto no dashboard 'Link Pulse — Aplicação' com dados de requisições; o Prometheus lista os quatro alertas do Link Pulse, inativos com o app de pé
result: [pending]

### 2. Rate limit por IP real atrás do Traefik
21 POST /links pelo Ingress com X-Forwarded-For forjado; depois um POST de dentro de um pod do cluster.
expected: 20 x 201 e o 21º = 429 mesmo com XFF forjado; o pod do cluster (IP próprio) recebe 201
result: [pending]

### 3. Sequência do zero na porta 8080
make k8s-down; make k8s K8S_HTTP_PORT=8080; make k8s-smoke (e o mesmo na 80, que o CI já cobre).
expected: smoke OK nas duas portas
result: [pending]

### 4. Revisar as 8 proibições test-tier
Ler a seção 'Proibições' do 04-VERIFICATION.md.
expected: Aceitar a evidência estática/observada de cada uma ou pedir um teste de regressão
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
