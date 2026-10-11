---
status: complete
phase: 02-hot-path-no-valkey-e-pipeline-de-cliques
source: [02-VERIFICATION.md]
started: 2026-10-11T21:19:59Z
updated: 2026-10-11T21:55:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Revisar a corrida XPENDING/XCLAIM entre duas réplicas (ClickStreamConsumer.reclaimIdle e acknowledgeVanished, linhas ~470-535)
expected: Um ID pedido ao XCLAIM que não voltou e que ainda existe no stream nunca recebe XACK; só IDs ausentes do stream viram lost
result: pass

### 2. Decidir sobre o WR-01: com o Valkey pausado ou particionado (não parado), todas as requisições concorrentes que chegam quando a janela de bypass vence pagam o timeout de ~150 ms
expected: Aceitar como limitação conhecida e corrigir o Javadoc do ValkeyAvailability e o README ("só a primeira chamada de cada janela paga"), ou implementar half-open com sonda única
result: pass

### 3. Confirmar as três proibições de julgamento sem teste próprio: (a) índices do Mongo nunca criados na inicialização dos beans; (b) sem limite de reentregas; (c) scrape das gauges nunca consulta Valkey/Mongo
expected: As três se sustentam no código (veredito não autoritativo do verificador: ClickIndexes só roda no loop; não há deliveryCount; gauges leem AtomicLong atualizados no loop)
result: pass

### 4. Ler o README como avaliador (Valkey fora, Ver estatísticas, rate limit, erros e riscos conhecidos)
expected: Texto claro e correto; incluir browser/os/isBot na descrição do documento de clique (IN-13) e corrigir a frase do gate (WR-01)
result: pass

## Summary

total: 4
passed: 4
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none]
