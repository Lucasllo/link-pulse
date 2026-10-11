---
phase: "02"
slug: "hot-path-no-valkey-e-pipeline-de-cliques"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-11"
---

# Phase 02 — Security

> Contrato de segurança da fase: registro de ameaças, riscos aceitos e trilha de auditoria.
> Registro consolidado dos blocos `<threat_model>` dos planos 02-01 a 02-09 (escrito no planejamento).

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Cliente → `GET /{code}` | Código do path (`[A-Za-z0-9_-]+`); qualquer cliente pode enumerar códigos | código curto, `Referer`, `User-Agent`, IP do socket |
| Cliente → `GET /links/{code}/stats` | Endpoint público sem autenticação | `from`, `to`, `includeBots` (entrada não confiável) |
| Cliente → Tomcat | O cliente controla qualquer header, inclusive `X-Forwarded-For` | headers HTTP |
| Proxy confiável (Traefik) → Tomcat | Só proxies em `internal-proxies` informam o IP do cliente | IP do cliente |
| Request thread → executor do publisher | Fila em memória com o IP cru | IP cru (PII, efêmero) |
| App → Valkey (cache, stream, contador) | Descartável; Valkey lento/fora não pode atrasar o redirect | URL de destino, cliques com IP já em HMAC, contadores |
| Stream `lp:clicks` → consumer | Entradas podem vir malformadas | eventos de clique |
| Réplicas → grupo `click-writers` | Consumers disputam os mesmos pendentes | IDs do stream |
| Consumer → MongoDB | Persistência de longo prazo; sem PII crua | `ipHash`, host do referrer, família de navegador/SO, `isBot` |
| App → Postgres | Fonte da verdade; consultado só no miss | links |
| Prometheus → `/actuator/prometheus` (8081) | Porta de gerenciamento; scrape não depende de Valkey/Mongo | métricas agregadas (baixa cardinalidade) |
| Orquestrador → SIGTERM | Shutdown em rolling deploy | lote em andamento |
| Operador → `LINKPULSE_IP_HASH_KEY` / `linkpulse.clicks.retention` | Segredo e configuração | chave do HMAC (segredo) |
| Host → Mongo do `compose.dev.yaml` | Porta publicada só em `127.0.0.1` | — |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-02-01 | Denial of Service | `LinkCache`/Lettuce: fail-slow com Valkey fora | high | mitigate | timeouts 250/150 ms, `REJECT_COMMANDS`, `ValkeyAvailability`; `ValkeyOutageIT#redirectKeepsWorkingFastWhileValkeyIsPaused` | closed |
| T-02-02 | Denial of Service | Enumeração de códigos martelando o Postgres | medium | mitigate | Negative cache `N` (60 s); `CacheIT#negativeCacheIsInvalidatedWhenAliasIsCreated` | closed |
| T-02-03 | Tampering | Codec do cache com valor corrompido | medium | mitigate | Prefixo de tipo + `split` limitado, fora do formato = miss; `LinkCacheCodecTest` | closed |
| T-02-04 | Tampering | Link expirado servido pelo cache | medium | mitigate | TTL = min(padrão, até expirar) + `Clock` na leitura; `CacheIT#ttlNeverExceedsTimeUntilExpiration` | closed |
| T-02-05 | Information Disclosure | Tags de métrica/logs com código ou URL | low | mitigate | Só a tag `result`; `MetricsCardinalityIT` | closed |
| T-02-06 | Information Disclosure | `/actuator/prometheus` exposto | low | accept | Porta 8081 separada, fora do Ingress; métricas sem dados pessoais | closed |
| T-02-07 | Information Disclosure | IP cru no stream | high | mitigate | `IpHasher` (HmacSHA256 + dia UTC) antes do XADD; `ClickPublishIT#redirectPublishesClickWithHashedIp` | closed |
| T-02-08 | Information Disclosure | Referer com path/query no stream | high | mitigate | `ReferrerNormalizer.toHost`; `ClickPublishIT#refererIsReducedToHost` | closed |
| T-02-09 | Information Disclosure | Chave do HMAC em mensagem de exceção | medium | mitigate | Mensagem fixa no `IpHasher`; `IpHasherTest` | closed |
| T-02-10 | Denial of Service | Fila do publisher ilimitada sob rajada | medium | mitigate | `ArrayBlockingQueue` com capacidade + descarte contado; `ClickPublisherTest` | closed |
| T-02-11 | Denial of Service | Stream crescendo até o OOM do Valkey | medium | mitigate | `XADD MAXLEN ~` com `approximateTrimming`; `volatile-lru` no compose | closed |
| T-02-12 | Denial of Service | Redirect esperando o XADD com Valkey fora | high | mitigate | Publicação assíncrona + gate; `ValkeyOutageIT#clickPublishFailureIsCountedWhileValkeyIsPaused` | closed |
| T-02-13 | Repudiation | HEAD/prefetch inflando cliques | low | mitigate | Guard `HttpMethod.GET`; `ClickPublishIT#headRequestDoesNotPublishClick` | closed |
| T-02-14 | Information Disclosure | Reidentificação pelo hash (força bruta IPv4) | medium | mitigate | HMAC com chave ≥ 16 caracteres (`IpHasher.MIN_KEY_LENGTH`), rotação diária | closed |
| T-02-15 | Information Disclosure | IP ou user-agent cru persistidos | high | mitigate | `ClickDocument` sem IP/UA cru; `ClickPipelineIT#redirectBecomesClickDocumentInMongo` | closed |
| T-02-16 | Denial of Service | Poison message travando o loop | medium | mitigate | Inválido confirmado e contado; `ClickPipelineIT#invalidEntryIsAcknowledgedWithoutDocument` | closed |
| T-02-17 | Denial of Service | Consumer com timeout de 150 ms no XREADGROUP BLOCK | medium | mitigate | Fábrica Lettuce dedicada com `commandTimeout(block + 3 s)` | closed |
| T-02-18 | Denial of Service | Mongo lento travando consumer e shutdown | medium | mitigate | `MongoConfig`: server selection/connect/read timeouts | closed |
| T-02-19 | Tampering | Duplicata derrubando o lote inteiro | medium | mitigate | Bulk `UNORDERED` tolerante a 11000 (`MongoClickEventWriter`) | closed |
| T-02-20 | Information Disclosure | Retenção sem limite dos cliques | medium | mitigate | Índice TTL `ts_ttl` com `collMod`; `#clickEventsHasCodeTsAndTtlIndexes`, `#retentionChangeUpdatesTtlWithCollMod` | closed |
| T-02-21 | Tampering | Reprocessamento duplicando cliques | medium | mitigate | `_id` = ID do stream; `ClickRedeliveryIT#deadConsumerPendingIsReprocessedWithoutDuplicates` | closed |
| T-02-22 | Repudiation | Cliques perdidos por ACK antes da escrita | high | mitigate | ACK só após `write`; `ClickRedeliveryIT#batchIsAcknowledgedOnlyAfterSuccessfulWrite` | closed |
| T-02-23 | Denial of Service | PEL crescendo com IDs aparados | low | mitigate | ACK + contador `lost`; `ClickRedeliveryIT#trimmedPendingEntriesAreAcknowledgedAndCountedAsLost` | closed |
| T-02-24 | Repudiation | Reclaim confirmando entrada de outra réplica | medium | mitigate | Só confirma ID ausente do stream (`range`); confirmado na UAT 02 teste 1 | closed |
| T-02-25 | Denial of Service | Mensagem que sempre falha reentregue para sempre | low | accept | Inválidas já confirmadas na conversão; sem `deliveryCount` por decisão (UAT 02 teste 3) | closed |
| T-02-26 | Denial of Service | Período enorme forçando aggregation pesada | medium | mitigate | Limite de 366 dias + índice `code_ts`; `StatsPeriodTest`, `StatsIT#invalidPeriodIsProblem` | closed |
| T-02-27 | Information Disclosure | `ipHash` na resposta de stats | medium | mitigate | `StatsResponse` só agregados; `StatsIT#responseNeverExposesIpHash` | closed |
| T-02-28 | Information Disclosure | Stats públicas (sem auth) | low | accept | Decisão do projeto; só agregados, referrer já é host | closed |
| T-02-29 | Tampering | Bots inflando as estatísticas | low | mitigate | `isBot` excluído por padrão; `StatsIT#botsAreExcludedByDefaultAndIncludedOnRequest` | closed |
| T-02-30 | Information Disclosure | Erro do parser/Mongo vazando detalhes | low | mitigate | Problem com detail fixo; `StatsIT#malformedParameterIsProblem` | closed |
| T-02-31 | Denial of Service | Mongo fora segurando requests do /stats | medium | mitigate | Timeouts do driver + 503; `StatsIT#mongoOutageIsServiceUnavailable` | closed |
| T-02-32 | Information Disclosure | Tags com código/URL/IP/UA no `/actuator/prometheus` | medium | mitigate | Allowlist de chaves; `MetricsCardinalityIT` | closed |
| T-02-33 | Denial of Service | Explosão de séries por alta cardinalidade | medium | mitigate | `uri` = template da rota; `MetricsCardinalityIT` | closed |
| T-02-34 | Denial of Service | Scrape travando com Valkey fora | medium | mitigate | Gauges leem `AtomicLong` atualizados no loop (UAT 02 teste 3) | closed |
| T-02-35 | Repudiation | Cliques perdidos/duplicados no shutdown | medium | mitigate | `stop(callback)` após lote e ACK; `ClickConsumerLifecycleIT#stopWaitsForInFlightBatch` | closed |
| T-02-36 | Denial of Service | Consumer morto em silêncio após erro do Mongo | high | mitigate | `catch` no loop com backoff e contador; `ClickResilienceIT#consumerSurvivesMongoOutageAndCatchesUp` | closed |
| T-02-37 | Information Disclosure | Log do consumer com dados de clique | low | mitigate | Logs só com stream e classe da exceção; `getMessage()` usado apenas para detectar `NOGROUP`, não logado | closed |
| T-02-38 | Spoofing | `X-Forwarded-For` forjado | high | mitigate | `RemoteIpValve` com `internal-proxies` vazio por padrão; `RateLimitIT#spoofedForwardedForFromUntrustedPeerIsIgnored`, `TrustedProxyRateLimitIT#spoofedLeftmostEntriesAreIgnoredBehindTrustedProxy` | closed |
| T-02-39 | Denial of Service | Criação em massa de links | medium | mitigate | 20 POST/min por IP, 429 + `Retry-After`; `RateLimitIT#fourthCreationInTheWindowIsRateLimited`, `#concurrentRequestsNeverExceedTheLimit` | closed |
| T-02-40 | Tampering | Corrida INCR/EXPIRE deixando contador eterno | medium | mitigate | Script Lua atômico; `RateLimitIT#rateLimitKeysAlwaysExpireAndHideTheIp` | closed |
| T-02-41 | Information Disclosure | IP cru como chave no Valkey | medium | mitigate | Chave com HMAC do `IpHasher`; mesmo IT | closed |
| T-02-42 | Denial of Service | Valkey fora bloqueando criação de links | medium | mitigate | Fail-open com gate; `RateLimitIT#valkeyOutageFailsOpen` | closed |
| T-02-43 | Elevation of Privilege | Limite desligado com Valkey fora (fail-open) | low | accept | Decisão D-08; visível em métricas, registrado no README | closed |
| T-02-44 | Information Disclosure | Chave do HMAC no `toString` das propriedades | medium | mitigate | `ipHashKey=****`; `LinkPulsePropertiesTest#toStringDoesNotExposeIpHashKey` | closed |
| T-02-45 | Information Disclosure | Mongo local exposto na rede | low | mitigate | `127.0.0.1:27017:27017` no `compose.dev.yaml` | closed |
| T-02-46 | Information Disclosure | UA cru/versões detalhadas (fingerprinting) | medium | mitigate | Só família de navegador/SO e `isBot`; `ClickPipelineIT#documentCarriesBrowserOsAndBotFlag` | closed |
| T-02-47 | Denial of Service | Criação de índices no startup derrubando a subida | medium | mitigate | `ClickIndexes.ensure()` só no loop com backoff (UAT 02 teste 3) | closed |
| T-02-SC1 | Tampering | Dependências novas (data-redis, registry-prometheus) | low | mitigate | Versões só do BOM do Boot 4.1.1; Package Legitimacy Audit aprovado | closed |
| T-02-SC3 | Tampering | Dependências novas (data-mongodb, testcontainers-mongodb) | low | mitigate | Versões só dos BOMs; Package Legitimacy Audit aprovado | closed |
| T-02-SC6 | Tampering | Dependência de teste nova (micrometer-metrics-test) | low | mitigate | Escopo `test`, versão do BOM | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02-01 | T-02-06 | `/actuator/prometheus` na porta 8081, fora do Ingress; só métricas agregadas sem PII | plano 02-03 | 2026-10-11 |
| AR-02-02 | T-02-25 | Sem limite de reentregas: inválidas são confirmadas na conversão; falha de infraestrutura deve esperar no PEL | plano 02-06 | 2026-10-11 |
| AR-02-03 | T-02-28 | Projeto sem autenticação; stats expõem só agregados e o referrer já é reduzido ao host | plano 02-07 | 2026-10-11 |
| AR-02-04 | T-02-43 | Fail-open do rate limit (D-08): disponibilidade vale mais que o limite; queda visível em métricas e documentada no README | plano 02-09 | 2026-10-11 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-11 | 50 | 50 | 0 | gsd-secure-phase (ASVS L1, verificação por grep; atalho sem auditor) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-11
