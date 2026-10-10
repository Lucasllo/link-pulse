# Phase 2: Hot path no Valkey e pipeline de cliques - Context

**Gathered:** 2026-10-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Os redirects passam a ser servidos do cache Valkey (cache-aside, TTL limitado pela expiração, negative cache) e continuam funcionando via Postgres se o Valkey cair. Cada 302 (nunca HEAD) publica um evento de clique num Redis Stream sem atrasar o redirect; um consumer group grava os eventos em lote, de forma idempotente, na coleção `click_events` do MongoDB. O cliente consulta `GET /links/{code}/stats` (total, cliques por dia, top referrers, top navegadores/SO, bots excluídos por padrão). `POST /links` ganha rate limit por IP com 429 + `Retry-After`, usando o IP real só quando vem de proxy confiável. Métricas Micrometer do cache, dos cliques e do pipeline, sem tags de alta cardinalidade.

Requisitos: REDIR-03..06, RATE-01, RATE-02, CLICK-01..08, STAT-01..04, DATA-02, QUAL-02, OBS-01.

Fora desta fase: imagem do app, `make up` completo, dashboards, alertas e k6 (Phase 3); Helm/kind (Phase 4).

</domain>

<decisions>
## Implementation Decisions

### Contrato do `GET /links/{code}/stats` (discutido)
- **D-01:** Sem `from`/`to`, o período padrão é **os últimos 30 dias**. A resposta sempre ecoa o período efetivo (`from`, `to`) aplicado, e total, série diária e tops usam todos o mesmo período. — **Reversibility:** costly — contrato público consumido pelo smoke test do CI (Phase 4), pelo seed/demo e pelo README.
- **D-02:** A série de cliques por dia é **contínua do `from` ao `to`, com dias sem clique preenchidos com 0**. Há um limite máximo de intervalo (sugestão: 366 dias), e acima dele a resposta é 400 em Problem Details.
- **D-03:** Os tops são **10 itens fixos por dimensão** (sem parâmetro `limit`). O referrer é **normalizado para o host** (sem path nem query, que podem levar tokens e dados pessoais), e requisição sem `Referer` entra no bucket `"(direct)"`. Os tops de navegador e de SO saem do user-agent parseado (CLICK-07).
- **D-04:** Código inexistente → **404** em Problem Details (existência conferida no Postgres, podendo passar pelo cache). Link **expirado → 200** com o histórico normal: o 410 é só do redirect.

### Herdado de decisões anteriores (não rediscutir)
- **D-05:** Coleção Mongo **normal** `click_events` (não time-series), com `_id` = ID da mensagem do stream; `insertMany` não ordenado ignorando duplicate key (PROJECT.md, SUMMARY.md).
- **D-06:** Cache-aside **explícito** com `StringRedisTemplate` (sem `@Cacheable`), TTL = min(padrão, tempo até expirar), negative cache para 404/410 invalidado na criação do link e contadores de hit/miss no Micrometer (CLAUDE.md).
- **D-07:** Consumer em **loop próprio** (`SmartLifecycle`, virtual thread) com `XREADGROUP COUNT/BLOCK` → `insertAll` → `XACK`. Não usar `StreamMessageListenerContainer` (CLAUDE.md).
- **D-08:** Rate limit por **script Lua de janela fixa** (`INCR` + `PEXPIRE`), aplicado só no `POST /links`, fail-open se o Valkey cair. O IP real vem do `server.forward-headers-strategy=native` (`RemoteIpValve`) com `internal-proxies` restrito; o código nunca lê `X-Forwarded-For` diretamente (ARCHITECTURE.md Padrão 6, PITFALLS.md Armadilha 8).
- **D-09:** Valkey 8.1 (`valkey/valkey:8.1`) nos ITs via `GenericContainer` + `@ServiceConnection(name = "redis")`; `maxmemory-policy volatile-lru` (STATE.md, PITFALLS.md).
- **D-10:** Nenhuma tag de métrica com código, URL, IP ou user-agent (OBS-01, PITFALLS.md).

### Claude's Discretion
Áreas não discutidas. O pesquisador e o planejador decidem, partindo destes defaults da pesquisa:
- **Formato de `from`/`to`:** datas ISO (`YYYY-MM-DD`, inclusivas) ou instantes ISO-8601 com offset; agrupamento diário em **UTC** (`$dateTrunc` com `timezone: "UTC"`), documentado no OpenAPI.
- **Formato do JSON do /stats:** nomes dos campos e estrutura (ex.: `{code, from, to, includeBots, total, daily[{date, clicks}], topReferrers[{value, clicks}], topBrowsers[...], topOs[...]}`).
- **Momento da normalização do referrer:** a recomendação é guardar só o host já na ingestão (privacidade).
- **Privacidade e retenção:** hash `SHA-256(salt + ip)` com salt vindo de config/secret (fixo ou rotativo diário, a critério); TTL de retenção configurável em `ts` (default sugerido: 90 dias).
- **Rate limit:** limite padrão configurável por env (sugestão: 20 req/min por IP); chave `lp:rl:{ip}:{janela}`; `Retry-After` = segundos até o fim da janela; proxies confiáveis configuráveis por env (vazio no compose, CIDR do cluster no kind).
- **Bots e user-agent:** lista própria de substrings versionada com teste unitário (preview crawlers, Googlebot/bingbot etc.) **ou** uma lib leve; decidir se `curl`/`wget`/clientes HTTP contam como bot; granularidade navegador + SO.
- **Ajustes de infraestrutura:** timeouts do Lettuce (curtos, ~100–200 ms, para o fail-open não virar fail-slow), executor limitado para o publish com descarte contado, `MAXLEN ~`, tamanho do lote e `BLOCK`, `XAUTOCLAIM` com min-idle, TTL padrão do cache e do negative cache, `server.shutdown=graceful` e timeout de shutdown.
- **Desenho dos ITs de reentrega:** consumer morto, queda do Mongo e shutdown com lote em andamento (pesquisar a API do Spring Data Redis no Boot 4.1, como pede o STATE.md).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Escopo e requisitos
- `.planning/ROADMAP.md` §Phase 2 — objetivo e 5 critérios de sucesso
- `.planning/REQUIREMENTS.md` — REDIR-03..06, RATE-01/02, CLICK-01..08, STAT-01..04, DATA-02, QUAL-02, OBS-01
- `.planning/PROJECT.md` — restrições (Boot 4.1.1, Valkey 8.1, pt-BR) e tabela de decisões (coleção normal, Redis Streams)
- `.planning/STATE.md` §Blockers/Concerns — itens de pesquisa da Phase 2 (`cancelOnError`, `BLOCK` com Lettuce, `XAUTOCLAIM`, `volatile-lru`) e T-08-06 (rate limit como mitigação parcial)

### Pesquisa
- `.planning/research/ARCHITECTURE.md` — `ClickPublisher` (executor limitado, descarte contado), consumer/XACK/shutdown, Padrão 6 (rate limiter), índices e TTL
- `.planning/research/PITFALLS.md` — stream sem limite, fail-slow, `volatile-lru`, `X-Forwarded-For` (Armadilha 8), graceful shutdown, cardinalidade de métricas, IP cru/LGPD
- `.planning/research/FEATURES.md` — stats (período, UTC, UA parseado), bots (`isBot` + `includeBots`), IP com salt, retenção
- `.planning/research/SUMMARY.md` — resolução do conflito time-series × coleção normal
- `.planning/research/STACK.md` e `.claude/CLAUDE.md` — versões e padrões de uso (Lettuce, Streams, Micrometer, Testcontainers)

### Fase anterior
- `.planning/phases/01-funda-o-e-n-cleo-de-links/01-CONTEXT.md` — D-01..D-16 (pacotes por feature, Problem Details, `linkpulse.*` config, contrato do POST)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `app/src/main/java/dev/linkpulse/link/LinkService.java`: `resolve(code)` (404/410 via `LinkProblems`) é o ponto onde entra o cache-aside; `create(...)` é onde o negative cache é invalidado.
- `app/src/main/java/dev/linkpulse/link/RedirectController.java`: `GET /{code:[A-Za-z0-9_-]+}` responde 302 com `no-store, private`; é aqui que o clique é publicado (só GET, nunca HEAD).
- `app/src/main/java/dev/linkpulse/link/LinkProblems.java` e `common/GlobalExceptionHandler.java`: fábrica de Problem Details, reutilizada para 429 e para o 400 do /stats.
- `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java`: record `@ConfigurationProperties("linkpulse")` validado; ganha os sub-records `cache`, `clicks`, `ratelimit` e `stats`.
- `app/src/test/java/dev/linkpulse/AbstractIT.java` + `TestcontainersConfiguration.java`: base dos ITs; ganha os containers Mongo e Valkey.

### Established Patterns
- Pacotes por feature (`dev.linkpulse.link`, `.config`, `.common`); a Phase 2 adiciona `.cache`, `.click`, `.stats` e `.ratelimit`.
- Clock UTC injetado (`Clock`) para expiração e para os testes; records para DTOs; Javadoc em pt-BR; Checkstyle Google adaptado com severidade error; SpotBugs Max/Medium.
- Env por relaxed binding (`LINKPULSE_*`); valores inválidos de config derrubam a subida.
- Logs nunca contêm URL de destino nem corpo de criação.
- `management.server.port=8081`, expondo hoje só `health,info`; precisa passar a expor `prometheus` e a configurar o histograma de `http.server.requests`.

### Integration Points
- `pom.xml` ganha os starters de data-redis e data-mongodb, `micrometer-registry-prometheus`, e os Testcontainers mongodb (artefatos 2.x `testcontainers-mongodb`) e valkey via `GenericContainer`.
- `compose.dev.yaml` ganha Valkey e Mongo para o `make run` local.
- O contrato do /stats (D-01..D-04) é consumido pelo seed/demo (Phase 3) e pelo smoke test do CI (Phase 4), que precisa fazer poll esperando o consumer.

</code_context>

<specifics>
## Specific Ideas

- O cenário "`docker stop` no Valkey e o redirect continua" é uma demonstração ao vivo para o avaliador; o fail-open precisa ser rápido, sem atraso perceptível.
- `"(direct)"` é o rótulo literal do bucket sem referrer.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-hot-path-no-valkey-e-pipeline-de-cliques*
*Context gathered: 2026-10-10*
