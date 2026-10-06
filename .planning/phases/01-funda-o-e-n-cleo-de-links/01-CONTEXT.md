# Phase 1: Fundação e núcleo de links - Context

**Gathered:** 2026-10-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Um cliente cria links curtos (gerados ou com alias) via `POST /links` e é redirecionado (`GET /{code}` → 302) a partir do Postgres, sem cache. O schema vem do Liquibase, a API está documentada no springdoc, e o repositório builda igual no Windows (Git Bash/WSL) e no Linux, com CI verde (build, testes unitários + Testcontainers, Checkstyle, SpotBugs) desde o primeiro commit.

Requisitos: LINK-01..06, REDIR-01, REDIR-02, DATA-01, QUAL-01, QUAL-03, QUAL-04, CONT-03, CI-01.

Fora desta fase: cache Valkey, rate limit, cliques/stats, Mongo (Phase 2); imagem do app, `make up` completo, dashboards (Phase 3).

</domain>

<decisions>
## Implementation Decisions

### Formato do código curto
- **D-01:** Embaralhamento por **permutação multiplicativa própria**, sem biblioteca: `code = base62(pad7((id * M) mod 62^7))`, com `M` coprimo de 62^7 (ímpar e não múltiplo de 31). A decodificação usa o inverso modular de `M`. Não usar Sqids. — **Reversibility:** one-way — trocar o algoritmo ou `M` muda todos os códigos já publicados (links quebram).
- **D-02:** Códigos gerados têm **comprimento fixo de 7 caracteres** (padding no alfabeto Base62), espaço de 62^7 ≈ 3,5 trilhões. O gerador deve falhar explicitamente se o ID ≥ 62^7. — **Reversibility:** one-way — o comprimento define a regex de código e a disjunção com o alias.
- **D-03:** Multiplicador (e, se houver, alfabeto) ficam em **propriedades de config** (`linkpulse.code.*`) com default no `application.yml` e override por variável de ambiente. Validar na inicialização que `M` é coprimo de 62^7. O README trata isso como ofuscação, não como segurança.
- **D-04:** Testes unitários (QUAL-01): ida e volta `decode(encode(id)) == id`, unicidade numa faixa grande de IDs, sempre 7 caracteres, e nenhum código gerado casando com a regex de alias.

### Regras do alias
- **D-05:** Regex do alias: `^[a-zA-Z0-9_-]{4,32}$` com **pelo menos um `-` ou `_`**. Como códigos gerados são só alfanuméricos, os namespaces são disjuntos estruturalmente. Mesmo assim, manter `code UNIQUE` no banco. — **Reversibility:** costly — relaxar depois exige revalidar aliases existentes contra o espaço gerado.
- **D-06:** Alias **diferencia maiúsculas de minúsculas** (igual ao Base62); lookup por igualdade exata.
- **D-07:** Lista de reservados em config, comparação case-insensitive: rotas reais (`links`, `actuator`, `swagger-ui`, `v3`, `api-docs`, `health`, `favicon.ico`, `robots.txt`) + genéricas (`admin`, `api`, `static`, `login`). A regra do `-`/`_` já barra a maioria; a lista é defesa extra (também cobre variações como `api-docs`).
- **D-08:** Alias já existente → 409 em Problem Details (detectado também via violação de unique constraint, para cobrir corrida).

### Contrato da API
- **D-09:** `POST /links` responde **201 Created** com header `Location` (URL curta) e corpo `{code, shortUrl, targetUrl, expiresAt, createdAt}`. — **Reversibility:** costly — contrato público usado por k6, smoke test e README.
- **D-10:** Expiração via campo `expiresAt` em **ISO-8601 com offset obrigatório** (ex.: `2026-12-31T23:59:59Z`), validado como futuro; persistido como `timestamptz`. Sem campo de duração relativa.
- **D-11:** A mesma URL longa enviada duas vezes **gera dois links independentes** (sem deduplicação, sem índice na URL).
- **D-12:** A base da `shortUrl` vem da propriedade **`linkpulse.base-url`** (default `http://localhost:8080`), sobrescrita por env no compose/kind/ECS. Não derivar de Host/X-Forwarded-*.

### Estrutura do repo e build
- **D-13:** Pacote raiz **`dev.linkpulse`**, groupId `dev.linkpulse`, artifactId `link-pulse`. — **Reversibility:** costly — renomear pacote toca todos os arquivos, Checkstyle/SpotBugs excludes e configs.
- **D-14:** Organização **por feature**: `dev.linkpulse.link` (controller, service, repository, entidade, `CodeGenerator`), além de `dev.linkpulse.config` e `dev.linkpulse.common` (Problem Details, erros). Fases seguintes adicionam `.click`, `.stats`, `.ratelimit`, `.cache`.
- **D-15:** Checkstyle com base **Google (`google_checks.xml`) adaptada**: indentação de 4 espaços, severidade `error`, arquivo em `config/checkstyle/`. Engine 14.x sobrescrita no plugin (o default 9.3 não entende Java 21).
- **D-16:** Phase 1 entrega **Makefile base** (`help`, `build`, `test`, `verify`, `run`, `db-up`, `db-down`) e um **`compose.dev.yaml` só com Postgres 18** para rodar local. O `make up` completo é da Phase 3.

### Claude's Discretion
- Tamanho máximo da URL longa (sugestão: 2048) e validação de esquema `http`/`https` com host presente.
- Formato exato dos Problem Details (`type` URIs, campo `errors[]` por campo inválido, `code` no 404/410).
- Endpoint opcional `GET /links/{code}` de metadados: só se for barato; não é requisito.
- Estratégia de Testcontainers (classe base `AbstractIT` com containers compartilhados via `@ServiceConnection`).
- Escopo do workflow de CI (ubuntu-latest; matriz Windows não exigida — a compatibilidade Windows vem de `.gitattributes`, `mvnw` executável e testes de caminho com espaço).
- Obtenção do ID antes do INSERT (`nextval` explícito ou `@SequenceGenerator(allocationSize = 1)` alinhado ao `INCREMENT BY 1` do changelog).
- `Cache-Control` exato no 302 (ex.: `private, no-store` ou `no-cache, no-store, must-revalidate`).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Escopo e requisitos
- `.planning/ROADMAP.md` §Phase 1 — objetivo e critérios de sucesso da fase
- `.planning/REQUIREMENTS.md` — LINK-01..06, REDIR-01/02, DATA-01, QUAL-01/03/04, CONT-03, CI-01
- `.planning/PROJECT.md` — restrições (Spring Boot 4.1.1, Java 21, Maven, pt-BR) e tabela de decisões

### Pesquisa
- `.planning/research/STACK.md` — versões (Boot 4.1.1, Testcontainers 2.x, Liquibase 5.x/FSL, springdoc 3.1.1, Checkstyle 14.x, SpotBugs) e variante Boot 4
- `.planning/research/PITFALLS.md` — Armadilha 2 (alias × código gerado/rotas) e a do `allocationSize` × `INCREMENT BY`; tabelas de UX de erro (Problem Details, 410 com corpo)
- `.planning/research/ARCHITECTURE.md` — componentes e fluxo do link
- `.planning/research/SUMMARY.md` — entregáveis sugeridos para a fundação
- `.claude/CLAUDE.md` — stack recomendada e "O que NÃO usar"

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Nenhum: projeto greenfield (o repositório só tem `.planning/` e `.claude/`).

### Established Patterns
- Nenhum padrão de código ainda; esta fase estabelece pacote, layout por feature, Checkstyle e estrutura de testes (`*Test` no surefire, `*IT` no failsafe).

### Integration Points
- O `CodeGenerator` e o `LinkRepository` desta fase são consumidos pelo cache-aside e pelo pipeline de cliques da Phase 2.
- `linkpulse.base-url` e as portas (app 8080, management 8081) viram contrato de runtime das Phases 3–5.

</code_context>

<specifics>
## Specific Ideas

- A permutação própria é escolhida também como vitrine: o avaliador lê ~20 linhas e entende por que não há colisão nem enumeração.
- Alias exemplo nos docs/testes: `minha-promo`, `black_friday`.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-funda-o-e-n-cleo-de-links*
*Context gathered: 2026-10-06*
