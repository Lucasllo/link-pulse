# Phase 2: Hot path no Valkey e pipeline de cliques - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-10
**Phase:** 02-hot-path-no-valkey-e-pipeline-de-cliques
**Areas discussed:** Contrato do /stats

Áreas oferecidas e não escolhidas (ficaram a critério do Claude): Privacidade e retenção, Rate limit e IP real, Bots e user-agent.

---

## Contrato do /stats

### Período padrão sem from/to

| Option | Description | Selected |
|--------|-------------|----------|
| Últimos 30 dias | Custo da agregação limitado; a resposta ecoa o período efetivo | ✓ |
| Todo o histórico | Mais intuitivo, mas cresce com o tempo | |
| Total histórico + por dia de 30 dias | Dois períodos na mesma resposta | |

### Dias sem clique na série

| Option | Description | Selected |
|--------|-------------|----------|
| Preencher com 0 | Série contínua, com intervalo máximo limitado | ✓ |
| Só dias com clique | Saída direta do $group | |

### Tops

| Option | Description | Selected |
|--------|-------------|----------|
| Top 10, com "(direct)" | 10 fixo; referrer normalizado para o host | ✓ |
| Top N via ?limit= | Parâmetro 1..50 | |
| Referrer completo | URL inteira, com risco de vazar dados | |

### Erros

| Option | Description | Selected |
|--------|-------------|----------|
| 404 para inexistente, 200 para expirado | Link expirado mantém o histórico consultável | ✓ |
| 404 e 410, igual ao redirect | Esconde as stats de links expirados | |

**User's choice:** todas as opções recomendadas; depois, "Pronto para o contexto" (formato de from/to, fuso e momento da normalização do referrer ficaram a critério do Claude).

---

## Claude's Discretion

- Formato de from/to e fuso (UTC), formato do JSON do /stats e momento da normalização do referrer
- Salt do hash de IP e retenção TTL
- Limite do rate limit, chave, Retry-After e proxies confiáveis
- Detecção de bots e parsing do user-agent
- Timeouts, executor, MAXLEN, lote, XAUTOCLAIM, TTLs de cache e graceful shutdown

## Deferred Ideas

Nenhuma.
