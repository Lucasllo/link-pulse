# Phase 1: Fundação e núcleo de links - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-06
**Phase:** 01-Fundação e núcleo de links
**Areas discussed:** Formato do código curto, Regras do alias, Contrato da API, Estrutura do repo e build

---

## Formato do código curto

| Option | Description | Selected |
|--------|-------------|----------|
| Permutação própria | Multiplicador coprimo mod 62^7 + Base62, sem dependência | ✓ |
| Sqids | Biblioteca sqids-java | |
| Você decide | — | |

| Option | Description | Selected |
|--------|-------------|----------|
| Fixo em 7 | 62^7 ≈ 3,5 trilhões | ✓ |
| Fixo em 6 | 62^6 ≈ 56 bilhões | |
| Crescente | Cresce com o ID; vaza ordem | |

| Option | Description | Selected |
|--------|-------------|----------|
| Config com default | `linkpulse.code.*` + override por env | ✓ |
| Constante no código | — | |
| Você decide | — | |

**User's choice:** opções recomendadas.

---

## Regras do alias

| Option | Description | Selected |
|--------|-------------|----------|
| Exigir '-' ou '_' | `^[a-zA-Z0-9_-]{4,32}$` com ao menos um `-`/`_` | ✓ |
| Qualquer tamanho ≠ 7 | Mais livre, regra menos óbvia | |
| Comprimento ≥ 8 | Proíbe aliases curtos | |

| Option | Description | Selected |
|--------|-------------|----------|
| Diferencia maiúsculas | Igual ao Base62 | ✓ |
| Normaliza para minúsculas | — | |

| Option | Description | Selected |
|--------|-------------|----------|
| Rotas + genéricas | Lista em config, case-insensitive | ✓ |
| Só rotas reais | — | |
| Você decide | — | |

**Notes:** a regra do `-`/`_` já barra `links`/`actuator`; reservados ficam como defesa extra.

---

## Contrato da API

| Option | Description | Selected |
|--------|-------------|----------|
| 201 + Location + corpo | `{code, shortUrl, targetUrl, expiresAt, createdAt}` | ✓ |
| 200 com corpo | — | |

| Option | Description | Selected |
|--------|-------------|----------|
| Instante ISO-8601 | `expiresAt` com offset | ✓ |
| Duração relativa | `ttl: P7D` | |
| Aceitar os dois | Mutuamente exclusivos | |

| Option | Description | Selected |
|--------|-------------|----------|
| Gera link novo | Sem deduplicação | ✓ |
| Reaproveita existente | Índice/hash da URL | |

| Option | Description | Selected |
|--------|-------------|----------|
| Propriedade configurável | `linkpulse.base-url` | ✓ |
| Derivada da requisição | Host/X-Forwarded-* | |

---

## Estrutura do repo e build

| Option | Description | Selected |
|--------|-------------|----------|
| Por feature | `dev.linkpulse.link`, `.config`, `.common` | ✓ |
| Por camada | — | |
| Hexagonal | — | |

| Option | Description | Selected |
|--------|-------------|----------|
| Google adaptado | 4 espaços, severidade error | ✓ |
| Sun | — | |
| Próprio enxuto | — | |

| Option | Description | Selected |
|--------|-------------|----------|
| Makefile base + compose dev só Postgres | `make up` completo fica na Phase 3 | ✓ |
| Só Makefile | — | |
| Você decide | — | |

| Option | Description | Selected |
|--------|-------------|----------|
| dev.linkpulse | Neutro, artifactId `link-pulse` | ✓ |
| io.github.<usuário> | — | |
| br.com.lucaslopes | — | |

---

## Claude's Discretion

- Tamanho máximo da URL, formato detalhado dos Problem Details, `GET /links/{code}` opcional, `AbstractIT`, escopo do CI (sem matriz Windows), obtenção do ID antes do INSERT, valor exato do `Cache-Control`.

## Deferred Ideas

Nenhuma.
