# Pesquisa de Arquitetura

**Domínio:** Encurtador de URLs com analytics de cliques (backend + DevOps/cloud de portfólio)
**Pesquisado em:** 2026-10-06
**Confiança geral:** MÉDIA-ALTA (padrões de aplicação = ALTA; limites do LocalStack e ecossistema k8s 2026 = MÉDIA, verificados em docs oficiais via WebFetch)

> **Três descobertas que mudam o plano (ler primeiro):**
> 1. **LocalStack gratuito NÃO emula ECS, RDS, ElastiCache, ECR nem ELB.** Desde 23/03/2026 a imagem `localstack/localstack` exige `LOCALSTACK_AUTH_TOKEN`. O plano gratuito "Hobby" cobre VPC/subnets/SG (EC2 CRUD), IAM, CloudWatch Logs, Secrets Manager, S3 e KMS. ECS, RDS, ElastiCache, ECR e ELBv2 ficam **somente no plano Base (pago)**. O requisito "Terraform validado contra LocalStack" precisa ser reformulado (ver seção Terraform).
> 2. **ingress-nginx foi aposentado** (sem releases nem patches de segurança desde março/2026), e o **catálogo gratuito da Bitnami acabou** (agosto/2025). No kind, não use ingress-nginx nem charts Bitnami para Postgres/Mongo/Redis.
> 3. **Toda a linha Spring Boot 3.x está fora do suporte OSS** (a 3.5 perdeu o suporte em 30/06/2026). A arquitetura abaixo vale igual para Boot 3.5 e 4.x. A decisão de versão fica para o STACK.md, mas o roadmap precisa registrá-la.

## Arquitetura Padrão

### Visão Geral do Sistema

```
                         ┌──────────────────────────────────────────────┐
  Cliente HTTP ────────► │ Borda: Ingress (kind) / ALB (ECS) / :8080    │
  (curl, k6, browser)    └──────────────────────┬───────────────────────┘
                                                │
┌───────────────────────────────────────────────▼─────────────────────────────────┐
│                     link-pulse (um único artefato Spring Boot)                  │
│                                                                                 │
│  ┌──────────────┐  ┌───────────────┐  ┌──────────────┐  ┌───────────────────┐   │
│  │ RateLimit    │  │ LinkController│  │ Redirect     │  │ StatsController   │   │
│  │ Interceptor  │─►│ POST /links   │  │ Controller   │  │ GET /links/{c}/   │   │
│  │ (só POST)    │  └──────┬────────┘  │ GET /{code}  │  │      stats        │   │
│  └──────┬───────┘         │           └──┬────────┬──┘  └────────┬──────────┘   │
│         │          ┌──────▼──────┐  ┌────▼─────┐  │              │              │
│         │          │ LinkService │  │ Redirect │  │       ┌──────▼──────┐       │
│         │          │ + CodeGen   │  │ Service  │  │       │ StatsService│       │
│         │          │  (Base62)   │  └─┬──────┬─┘  │       └──────┬──────┘       │
│         │          └──┬───────┬──┘    │      │    │              │              │
│         │             │  ┌────▼───────▼┐     │  ┌─▼────────────┐ │              │
│         │             │  │ LinkCache   │     │  │ ClickPublish │ │              │
│         │             │  │(cache-aside)│     │  │ (async XADD) │ │              │
│         │             │  └────┬────────┘     │  └─┬────────────┘ │              │
│         │             │       │              │    │              │              │
│         │             │       │   ┌──────────┴────┼─────────────┐│              │
│         │             │       │   │ ClickConsumer (thread de    ││              │
│         │             │       │   │ background, liga/desliga    ││              │
│         │             │       │   │ por property) XREADGROUP →  ││              │
│         │             │       │   │ insertMany → XACK           ││              │
│         │             │       │   └──────────┬───────────┬──────┘│              │
│  Actuator :8081 (health, prometheus) ─ gauges de lag/pending do stream          │
└─────────┼─────────────┼───────┼──────────────┼───────────┼───────┼──────────────┘
          │             │       │              │           │       │
   ┌──────▼─────────────┼───────▼──────────────▼──┐  ┌─────▼───────▼──────┐
   │ Redis                │                         │  │ MongoDB            │
   │  lp:rl:*   (rate limit)                        │  │  click_events      │
   │  lp:link:* (cache + tombstones)                │  │  (bruto + índices) │
   │  lp:clicks (Stream, grupo click-writers)       │  └────────────────────┘
   └──────────────────────────────────────────────┘
                 ┌───────▼────────┐
                 │ PostgreSQL     │  fonte da verdade dos links
                 │ links + seq    │  (Liquibase)
                 └────────────────┘

   Prometheus ──scrape :8081/actuator/prometheus──► link-pulse
   Grafana ◄── Prometheus (dashboards JSON versionados)
```

**Regra de ouro dos dados:** Postgres é a fonte da verdade dos links. Redis guarda só dados derivados ou efêmeros (cache, contador de rate limit, buffer de eventos). Mongo é dono dos eventos de clique. Nenhum componente lê a "verdade" de um link no Mongo, e nenhum grava cliques no Postgres.

### Responsabilidades dos Componentes

| Componente | Responsabilidade | Implementação típica | Fala com |
|---|---|---|---|
| `RateLimitInterceptor` | Limitar `POST /links` por IP e responder 429 | `HandlerInterceptor` registrado só para `POST /links`, script Lua (INCR+EXPIRE atômico) em janela fixa | Redis (`lp:rl:{ip}:{janela}`) |
| `LinkController` / `LinkService` | Validar URL, alias e expiração; gerar o código; persistir; invalidar cache | Spring MVC + Bean Validation; `@Transactional` | Postgres, `CodeGenerator`, `LinkCache` (DEL) |
| `CodeGenerator` | `id → Base62` e ofuscação opcional | Classe pura e sem Spring (fácil de testar unitariamente) | — |
| `RedirectController` / `RedirectService` | Resolver o código: 302, 404 ou 410; disparar o evento de clique | MVC; regex no path para não colidir com rotas da aplicação | `LinkCache` → Postgres (miss), `ClickPublisher` |
| `LinkCache` | Cache-aside com TTL ligado à expiração, tombstones de 404/410, métricas de hit/miss | `StringRedisTemplate` + JSON pequeno; **não** usar `@Cacheable` | Redis |
| `ClickPublisher` | Publicar o clique sem atrasar o redirect | Executor limitado (fila bounded e descarte com métrica) → `XADD lp:clicks MAXLEN ~ N` | Redis Stream |
| `ClickConsumer` | Ler em lote, gravar no Mongo e dar ACK; recuperar pendentes | Thread dedicada (`SmartLifecycle`) com loop `XREADGROUP COUNT 200 BLOCK 2000` + `insertMany(ordered=false)` + `XACK` em lote; varredor de PEL agendado | Redis Stream, MongoDB |
| `StatsService` | Total, cliques por dia, top referrers e user-agents | `MongoTemplate.aggregate` com `$match` + `$facet` | Postgres (existência → 404), MongoDB |
| `StreamMetrics` | Gauges de lag e pending do consumer group | `XINFO GROUPS` periódico → `Gauge` do Micrometer | Redis, Micrometer |
| Actuator | Probes, `/actuator/prometheus` | Porta de management separada (8081) e `/livez` + `/readyz` na 8080 | Prometheus, kubelet, ALB |

## Estrutura de Projeto Recomendada

### Layout do repositório (monorepo)

```
link-pulse/
├── app/                              # Projeto Maven (o único código Java)
│   ├── pom.xml  mvnw  .mvn/
│   ├── Dockerfile                    # multi-stage, layered jar, usuário não-root
│   ├── config/
│   │   ├── checkstyle/checkstyle.xml
│   │   └── spotbugs/exclude.xml
│   └── src/
│       ├── main/java/dev/linkpulse/... (ver abaixo)
│       ├── main/resources/
│       │   ├── application.yml       # defaults + leitura de env vars
│       │   └── db/changelog/
│       │       ├── db.changelog-master.yaml
│       │       └── changes/001-create-links.yaml, 002-seed-dev.yaml (context: dev)
│       └── test/java/...             # unit (*Test) + integração (*IT, Testcontainers)
├── compose.yaml                      # `make up`: app + postgres + mongo + redis + prometheus + grafana
├── observability/                    # FONTE ÚNICA de observabilidade
│   ├── prometheus/
│   │   ├── prometheus.yml            # config de scrape para o compose
│   │   └── rules/link-pulse-alerts.yml   # formato `groups:` (igual ao spec do PrometheusRule)
│   └── grafana/
│       ├── provisioning/{datasources,dashboards}/*.yml   # compose
│       └── dashboards/link-pulse-overview.json
├── helm/
│   ├── link-pulse/                   # chart do app
│   │   ├── Chart.yaml  values.yaml  values-dev.yaml  values-prod.yaml
│   │   ├── files/                    # gerado: cópia de observability/ (gitignored, via make)
│   │   └── templates/ deployment, service, ingress, configmap, secret, hpa,
│   │                  servicemonitor, prometheusrule, dashboards-configmap, _helpers.tpl
│   └── link-pulse-deps/              # chart dev-only: postgres/mongo/redis com imagens OFICIAIS
├── k8s/
│   ├── kind-config.yaml              # extraPortMappings 80/443 + label ingress-ready
│   └── kube-prometheus-stack-values.yaml
├── terraform/                        # ver seção Terraform
├── k6/
│   ├── redirect-load.js  create-links.js
│   └── results/                      # resumos versionados que o README cita
├── scripts/                          # lógica real em bash; o Makefile só delega
│   ├── k8s-up.sh  k8s-down.sh  smoke.sh  sync-observability.sh  tf-localstack.sh
├── docs/                             # diagramas, ADRs curtos, prints dos dashboards
├── Makefile                          # up, down, test, k8s, k8s-down, smoke, load, tf-*
└── .github/workflows/
    ├── ci.yml                        # build/test → imagem → kind-e2e (+ helm/terraform lint)
    └── load-test.yml                 # workflow_dispatch: k6 (não roda em todo PR)
```

### Layout de pacotes da aplicação (package-by-feature)

```
dev.linkpulse
├── LinkPulseApplication.java
├── link/                      # criação e consulta da verdade do link
│   ├── api/                   # LinkController, CreateLinkRequest/Response (records)
│   ├── domain/                # Link (entidade JPA), LinkStatus, regras de expiração/alias
│   ├── code/                  # CodeGenerator, Base62, (CodeObfuscator opcional)
│   ├── persistence/           # LinkRepository (JPA) + query nativa nextval
│   └── LinkService.java
├── redirect/                  # hot path
│   ├── RedirectController.java
│   ├── RedirectService.java
│   └── cache/                 # LinkCache, CachedLink (record), CacheMetrics
├── click/                     # pipeline assíncrono de cliques
│   ├── ClickEvent.java        # record: code, ts, referrer, userAgent, ipHash
│   ├── publish/               # ClickPublisher (executor bounded + XADD)
│   ├── consume/               # ClickStreamConsumer (SmartLifecycle), PendingReclaimer, ClickDocument
│   └── stats/                 # StatsController, StatsService, StatsResponse
├── ratelimit/                 # RateLimitInterceptor, RedisRateLimiter, script Lua
├── observability/             # StreamMetrics, common tags, (ObservationConfig)
└── common/
    ├── config/                # RedisConfig, MongoConfig (índices), WebConfig, ExecutorConfig
    ├── web/                   # GlobalExceptionHandler (ProblemDetail RFC 9457), ClientIpResolver
    └── properties/            # @ConfigurationProperties LinkPulseProperties (validado)
```

### Justificativa da Estrutura

- **`app/` isolado:** o build Maven, o Dockerfile e o contexto Docker ficam autocontidos (`docker build app/`). O CI usa `working-directory: app` e o contexto da imagem não arrasta `terraform/` nem `helm/`.
- **package-by-feature em vez de camadas globais (`controllers/`, `services/`):** quem avalia lê `redirect/` e entende o hot path inteiro num lugar só. Hexagonal/ports-and-adapters completo é exagero para cerca de 15 classes. Spring Modulith seria um "plus" de vitrine, mas é opcional e não deve atrasar o MVP.
- **`observability/` como fonte única:** compose e Helm consomem os mesmos JSON e as mesmas regras (ver Padrão 8). Dashboards duplicados à mão divergem.
- **`scripts/` + Makefile fino:** o desenvolvimento é em Windows/Git Bash. Se `make` faltar, `bash scripts/k8s-up.sh` funciona igual, e o CI chama os mesmos scripts. Assim, a verificação do kind no CI exercita exatamente o caminho que o avaliador roda.
- **`helm/link-pulse-deps` próprio:** os charts Bitnami deixaram de ser opção viável (imagens movidas para `bitnamilegacy`, sem updates). Um chart mínimo com StatefulSets de `postgres:17`, `mongo:8` e `redis:8` (ou Valkey) é curto, controlável e explicitamente dev-only.

## Padrões Arquiteturais

### Padrão 1: Geração de código via sequência Postgres + Base62 (atribuição explícita do ID)

**O quê:** pegar `nextval('links_id_seq')` **antes** do INSERT, calcular `code = base62(obfuscate(id))` e inserir `id` e `code` juntos na mesma transação. A coluna `code` é `UNIQUE` e compartilhada entre códigos gerados e aliases.
**Quando:** sempre. Evita INSERT seguido de UPDATE e evita o otimizador pooled do Hibernate.
**Trade-offs:** um round-trip a mais (barato). Códigos sequenciais puros são enumeráveis, o que se mitiga com ofuscação opcional.

```java
// LinkRepository
@Query(value = "select nextval('links_id_seq')", nativeQuery = true)
long nextId();

// LinkService.create (@Transactional)
for (int attempt = 0; attempt < 3; attempt++) {
    long id = repo.nextId();
    String code = req.alias() != null ? req.alias() : codeGen.encode(id);
    try { return repo.saveAndFlush(new Link(id, code, req.url(), req.expiresAt())); }
    catch (DataIntegrityViolationException e) {
        if (req.alias() != null) throw new AliasTakenException(code);   // 409
        // código gerado colidiu com um alias customizado existente: tenta o próximo id
    }
}
linkCache.evict(code);   // apaga um tombstone "não existe" que possa estar no cache
```

- **Entidade:** `@Id` **sem** `@GeneratedValue` (o id é atribuído). Se usar `@SequenceGenerator`, `allocationSize` tem que bater com o `INCREMENT BY` do changelog Liquibase (o padrão do Hibernate é 50, o que gera IDs "pulados" e erros de validação).
- **Comprimento:** `START WITH 14776336` (62⁴) faz os códigos começarem com 5 caracteres, sem a cara de "link nº 1".
- **Ofuscação (opcional, recomendada por ser barata):** permutação bijetiva em `[0, 62^7)`, multiplicando por um primo coprimo módulo 62^7 (com o inverso guardado para decodificar, se precisar). É determinística, sem colisão e testável por propriedade (encode→decode). Alternativa pronta: Sqids (`org.sqids:sqids`). O README deve deixar claro que **não é segurança**.
- **Alias:** regex `^[A-Za-z0-9_-]{3,32}$` mais uma lista de palavras reservadas (`links`, `actuator`, `livez`, `readyz`, `swagger-ui`, `v3`, `favicon.ico`, `health`). Confiança ALTA.

### Padrão 2: Cache-aside com TTL ligado à expiração e negative caching (hot path)

**O quê:** `RedirectService` consulta `LinkCache`. No miss, lê o Postgres e popula o cache. O valor cacheado carrega `expiresAt` para que a decisão 302/410 seja exata, mesmo com TTL arredondado.

```
GET /{code}
  └─ LinkCache.get("lp:link:{code}")
       ├─ HIT {url, expiresAt}      → se expiresAt <= now → 410 (e grava tombstone GONE) ; senão 302
       ├─ HIT "NF"  (tombstone)     → 404
       ├─ HIT "GONE" (tombstone)    → 410
       └─ MISS → Postgres findByCode
              ├─ não existe  → SET "NF"   EX 60     → 404
              ├─ expirado    → SET "GONE" EX 86400  → 410
              └─ ativo       → SET {url,expiresAt} EX min(24h, expiresAt-now) → 302
  └─ (só no 302) ClickPublisher.publish(event)  ← fire-and-forget
```

**Regras:**
- **TTL** = `min(ttlPadrão, expiresAt − now)`, com piso de 1 s. Sem expiração, usa `ttlPadrão` (24 h). Como não existe API de update/delete de link, invalidar só importa no **create** (apagar o tombstone `NF`).
- **Negative TTL curto (30–60 s):** limita a janela em que um alias recém-criado apareceria como 404 se a invalidação falhar. Também protege o Postgres contra varredura de códigos inexistentes.
- **Tombstone GONE pode ser longo:** um link expirado nunca volta a valer.
- **Redis fora do ar:** `LinkCache` captura a exceção, incrementa `linkpulse.cache.errors` e segue para o Postgres (degrada, não quebra). O redirect continua funcionando.
- **Por que não `@Cacheable`:** TTL por entrada, tombstones e métricas por resultado ficam explícitos e legíveis, e o avaliador vê o padrão. O `RedisCacheManager` até suporta `TtlFunction`, mas esconde justamente o que o portfólio quer mostrar.
- **Resposta:** `302` com `Location` e `Cache-Control: private, max-age=0`. Navegadores não devem cachear o redirect, senão cliques repetidos somem do analytics. Use 302, nunca 301, pelo mesmo motivo.

Confiança: ALTA (padrão consolidado).

### Padrão 3: Produtor de cliques não bloqueante (Redis Stream)

**O quê:** o redirect monta `ClickEvent(code, ts, referrer, userAgent, ipHash)` e entrega a um `ThreadPoolTaskExecutor` **limitado** (ex.: 2–4 threads, fila de 10 000). A política de rejeição é **descartar e contar** (`linkpulse.clicks.dropped`). A thread do executor faz `XADD lp:clicks MAXLEN ~ 1000000 * code … ts … ref … ua … iph …`.
**Por quê:** XADD costuma levar menos de 1 ms, mas é I/O de rede síncrono. Com Redis lento, o redirect não pode esperar. Perder clique sob falha é aceitável e mensurável. Atrasar redirect não é.
**Detalhes:**
- **IP:** gravar só `ipHash = SHA-256(salt + ip)`, por LGPD e porque o IP cru não é usado nas estatísticas.
- **Referrer:** normalizar para host (`https://t.co/abc` → `t.co`) já no produtor ou no consumer. O parsing de UA (browser, SO, dispositivo) fica **no consumer**, fora do hot path.
- **Trimming:** `MAXLEN ~` apaga entradas antigas **mesmo que ainda não tenham ACK**. Dimensione N bem acima do backlog esperado e alerte sobre o lag. Com Redis ≥ 8.2, use `XADD … ACKED` para só aparar entradas já confirmadas por todos os grupos (confiança MÉDIA; confirmar a versão do Redis/ElastiCache/Valkey antes de depender disso).
- **Virtual threads (Java 21):** `spring.threads.virtual.enabled=true` ajuda o Tomcat, mas **não** substitui o executor limitado do publisher. Virtual threads são ilimitadas e não dão backpressure.

### Padrão 4: Consumer group com escrita em lote, ACK e recuperação de pendentes

**O quê:** um loop explícito de polling, **não** `StreamMessageListenerContainer`. O container do Spring entrega mensagem a mensagem, o que atrapalha o `insertMany` em lote e o controle de ACK.

```java
// ClickStreamConsumer implements SmartLifecycle — roda numa thread dedicada
// Consumer name = HOSTNAME (nome do pod/task) → estável por réplica
void loop() {
    drainOwnPending();                       // na partida: ler a partir de "0" o próprio PEL
    while (running) {
        var records = ops.read(Consumer.from("click-writers", consumerName),
            StreamReadOptions.empty().count(200).block(Duration.ofSeconds(2)),
            StreamOffset.create("lp:clicks", ReadOffset.lastConsumed()));   // ">"
        if (records.isEmpty()) continue;
        var docs = records.stream().map(ClickDocument::from).toList();   // _id = stream record id
        try {
            mongo.bulkOps(BulkMode.UNORDERED, ClickDocument.class).insert(docs).execute();
        } catch (BulkOperationException e) {
            if (!onlyDuplicateKeyErrors(e)) { metrics.batchFailed(); continue; } // sem ACK → fica no PEL
        }
        ops.acknowledge("lp:clicks", "click-writers", ids(records));          // XACK em lote
        metrics.persisted(records.size());
    }
}
```

- **Idempotência:** `_id` do documento = ID da entrada no stream (`1728212345678-0`). Reentrega após crash gera duplicate key, que é ignorado, e o ACK sai normalmente. Essa é a garantia "efetivamente uma vez" do pipeline.
- **Criação do grupo:** na partida, `XGROUP CREATE lp:clicks click-writers $ MKSTREAM`, tratando `BUSYGROUP` como sucesso.
- **Pendentes de consumidores mortos** (pods removidos pelo HPA ou tasks substituídas): um `PendingReclaimer` agendado (a cada 30 s) roda `XPENDING` filtrando idle > 60 s e chama `claim(...)` para si. O Spring Data Redis expõe XCLAIM mas **não** XAUTOCLAIM (issue #3434 aberta). Para usar XAUTOCLAIM, só via `connection.execute(...)` nativo. Mensagens com `deliveryCount > 5` vão para `lp:clicks:dlq` (stream ou log) e recebem ACK, para não envenenar o loop.
- **Shutdown gracioso:** `SmartLifecycle.stop()` sinaliza `running=false` e espera o lote corrente (< `BLOCK` + tempo de escrita). Combine com `server.shutdown=graceful` e `terminationGracePeriodSeconds` acima do timeout.
- **Conexão:** `XREADGROUP BLOCK` segura a conexão. Use uma `LettuceConnectionFactory` dedicada (ou pool) para o consumer, para não bloquear a conexão compartilhada usada pelo cache no hot path. Confiança MÉDIA-ALTA: o Lettuce multiplexa uma conexão nativa e um comando bloqueante nela atrasa os demais.
- **Liga/desliga:** `linkpulse.clicks.consumer.enabled=true` (padrão). Assim, a mesma imagem pode rodar como "web-only" e "worker" se um dia o Helm separar Deployments. No MVP, todas as réplicas fazem as duas coisas, e o consumer group distribui as mensagens entre elas.

### Padrão 5: Schema Mongo + índices para as agregações

**Coleção `click_events`** (coleção normal, **não** time-series):

```json
{ "_id": "1728212345678-0", "code": "aZ3kP", "ts": ISODate(...),
  "referrer": "t.co", "browser": "Chrome", "os": "Android", "device": "mobile",
  "ipHash": "9f2c…" }
```

- **Índice principal:** `{ code: 1, ts: -1 }`. Atende ao `$match {code, ts >= now-30d}` de todas as agregações. Os índices são criados no startup via `MongoTemplate.indexOps` (ou `@CompoundIndex` com `spring.data.mongodb.auto-index-creation=true`, explícito).
- **Por que não time-series:** coleções time-series não aceitam índice único, e perderíamos a idempotência por `_id = stream id`. Para o volume de portfólio, a coleção normal com índice composto basta. Confiança MÉDIA-ALTA.
- **TTL opcional:** `{ ts: 1 }, expireAfterSeconds: 31536000` (retenção de 1 ano), como demonstração de política de retenção.
- **Uma ida ao banco por request de stats** com `$facet`:

```js
[{ $match: { code: "aZ3kP", ts: { $gte: since } } },
 { $facet: {
     total:     [{ $count: "n" }],
     daily:     [{ $group: { _id: { $dateTrunc: { date: "$ts", unit: "day", timezone: "America/Sao_Paulo" } }, n: { $sum: 1 } } }, { $sort: { _id: 1 } }],
     referrers: [{ $group: { _id: "$referrer", n: { $sum: 1 } } }, { $sort: { n: -1 } }, { $limit: 10 }],
     browsers:  [{ $group: { _id: "$browser",  n: { $sum: 1 } } }, { $sort: { n: -1 } }, { $limit: 10 }] } }]
```

- **Total:** o "total de cliques" de todo o período requer um `$match` sem filtro de data (ou um segundo facet). Defina no requisito se `stats` é "últimos 30 dias" ou "desde a criação" (sugestão: parâmetro `?days=30`, com teto de 365).
- **Fuso:** use `$dateTrunc` com timezone explícito. Dias em UTC "erram" a contagem diária vista do Brasil.
- **Evolução (fora do MVP):** rollup `click_daily {code, day, n}` via `$inc` em bulk upsert no próprio consumer. Só vale com volume alto.

### Padrão 6: Rate limiter como interceptor só da rota de escrita

- **Posição:** um `HandlerInterceptor` mapeado em `/links` que age só em `POST`. Um filtro servlet global pegaria o redirect, e o redirect **não** deve ter rate limit nem custo extra.
- **Algoritmo:** janela fixa com script Lua (`INCR` + `EXPIRE` na primeira vez), chave `lp:rl:{ip}:{epochMinute}`, limite configurável (ex.: 20/min). Responde `429` com `Retry-After` e `ProblemDetail`. Bucket4j + Lettuce é a alternativa "biblioteca", mas o script de 6 linhas é mais didático.
- **IP do cliente:** `ClientIpResolver` usa `X-Forwarded-For` **somente** atrás de proxy confiável (`server.forward-headers-strategy=native` com `RemoteIpValve` e proxies internos confiáveis). No compose não há proxy; no kind o Ingress e no ECS o ALB injetam XFF. Sem isso, todo mundo vira o IP do Ingress e um único cliente esgota o limite global.
- **Redis indisponível:** *fail-open* (permite e conta `linkpulse.ratelimit.errors`). Rate limit não é controle de segurança crítico aqui.

### Padrão 7: Actuator, probes e contrato de runtime

```yaml
server: { port: 8080, shutdown: graceful }
management:
  server.port: 8081                      # /actuator/** fora do Ingress/ALB público
  endpoints.web.exposure.include: health,info,prometheus
  endpoint.health:
    probes: { enabled: true, add-additional-paths: true }   # /livez e /readyz também na 8080
    group:
      liveness.include:  livenessState                      # NUNCA dependências externas
      readiness.include: readinessState,db,redis            # Mongo fora: só o consumer depende dele
  metrics:
    tags.application: link-pulse
    distribution.percentiles-histogram.http.server.requests: true
```

- **Mongo fora da readiness:** com o Mongo fora do ar, redirect e criação continuam funcionando. Os cliques acumulam no stream (lag sobe e o alerta dispara), e as stats respondem 503. Pôr o Mongo na readiness derrubaria o encurtador inteiro por causa do analytics.
- **Redis na readiness: decisão consciente.** O redirect degrada sem Redis, mas o pipeline de cliques para. Para portfólio, incluir é aceitável e mais simples de explicar. A alternativa é excluir e confiar em alerta.
- **Kubernetes:** `startupProbe` em `/actuator/health/liveness` (8081, `failureThreshold` de 30 × 2 s, porque a JVM + Liquibase demoram), `livenessProbe` e `readinessProbe` na 8081.
- **ECS/ALB:** health check do target group em `/readyz` na **porta de tráfego** (8080), graças a `add-additional-paths`. O ALB não precisa de acesso à 8081.
- **Dockerfile `HEALTHCHECK`:** exige `curl` ou `wget` na imagem (as imagens JRE slim e distroless podem não ter). Escolha a base pensando nisso (ex.: `eclipse-temurin:21-jre-alpine` traz `wget` do busybox) ou rode o health check em Java. O Kubernetes **ignora** o HEALTHCHECK do Dockerfile, que só vale para compose e ECS.

**Contrato de runtime (documentar cedo, consumido por compose, Helm e ECS):**

| Variável | Exemplo |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `jdbc:postgresql://postgres:5432/linkpulse` |
| `SPRING_DATA_REDIS_HOST` / `_PORT` | `redis` / `6379` |
| `SPRING_DATA_MONGODB_URI` | `mongodb://mongo:27017/linkpulse` |
| `SPRING_LIQUIBASE_CONTEXTS` | `dev` (compose/kind) ou `prod` (ECS) |
| `LINKPULSE_BASE_URL` | `http://localhost:8080` (monta o `shortUrl` da resposta) |
| `LINKPULSE_CLICKS_CONSUMER_ENABLED` | `true` |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` |
| Portas | 8080 (app, `/livez`, `/readyz`), 8081 (actuator) |

Sem profiles por ambiente (`application-k8s.yml` etc.): tudo vem por env var, no estilo 12-factor. Um único profile `test` é aceitável.

### Padrão 8: Observabilidade com uma fonte só para compose e kind

**Nomes de métricas** (Micrometer em notação de ponto; Prometheus converte para snake_case e acrescenta `_total`/`_seconds`):

| Micrometer | Prometheus | Tags | Uso |
|---|---|---|---|
| `http.server.requests` (auto) | `http_server_requests_seconds_bucket` | `uri`, `method`, `status`, `outcome` | taxa de requests, 5xx, p95/p99 via `histogram_quantile` |
| `linkpulse.cache.requests` | `linkpulse_cache_requests_total` | `result=hit\|miss\|negative_hit` | hit ratio |
| `linkpulse.cache.errors` | `linkpulse_cache_errors_total` | `op` | degradação |
| `linkpulse.redirects` | `linkpulse_redirects_total` | `outcome=found\|not_found\|gone` | negócio |
| `linkpulse.links.created` | `linkpulse_links_created_total` | `type=generated\|alias` | negócio |
| `linkpulse.clicks.published` / `.dropped` | `linkpulse_clicks_published_total` / `_dropped_total` | — | produtor |
| `linkpulse.clicks.persisted` | `linkpulse_clicks_persisted_total` | — | cliques/min (`rate(...[1m])*60`) |
| `linkpulse.clicks.batch.duration` | `linkpulse_clicks_batch_duration_seconds` | `outcome` | consumer |
| `linkpulse.clicks.stream.lag` / `.pending` | `linkpulse_clicks_stream_lag` / `_pending` | `group` | **alerta de lag** (`XINFO GROUPS`; o campo `lag` existe a partir do Redis 7) |
| `linkpulse.ratelimit.rejected` | `linkpulse_ratelimit_rejected_total` | — | 429s |

- **Cardinalidade:** **nunca** use `code` como tag. O `uri` do `http.server.requests` já sai como template `/{code}`, o que está correto. Rotas inexistentes viram `uri="/**"` ou `NOT_FOUND`, também correto.
- **Alertas** (`observability/prometheus/rules/link-pulse-alerts.yml`): `LinkPulseHigh5xxRate` (> 5 % em 5 min), `LinkPulseHighP99Latency` (p99 do `/{code}` > 250 ms em 10 min), `LinkPulseClickConsumerLag` (lag > 1000 por 5 min), `LinkPulseClicksDropped` (`increase > 0`).
- **Fonte única → dois consumidores:**
  - Compose: monta `observability/prometheus/*` e `observability/grafana/*` como volumes, com provisioning do Grafana.
  - Helm: o chart **não consegue** ler arquivos fora do diretório dele (`.Files.Get` é restrito ao chart). Então `scripts/sync-observability.sh` (chamado por `make k8s` e pelo CI) copia para `helm/link-pulse/files/` (gitignored). Depois, `templates/prometheusrule.yaml` usa `spec: {{ .Files.Get "files/link-pulse-alerts.yml" | fromYaml | toYaml | nindent 2 }}` (o formato `groups:` é idêntico) e `templates/dashboards-configmap.yaml` gera um ConfigMap com label `grafana_dashboard: "1"`, que o sidecar do Grafana do kube-prometheus-stack carrega.
  - `ServiceMonitor` aponta para a porta nomeada `management` (8081), path `/actuator/prometheus`.
  - **Pegadinha:** o kube-prometheus-stack, por padrão, só seleciona ServiceMonitor e PrometheusRule com label `release: <nome-do-release>`. Em `k8s/kube-prometheus-stack-values.yaml`, defina `prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues: false` e `ruleSelectorNilUsesHelmValues: false`.
  - **Datasource UID:** os JSON dos dashboards devem referenciar o datasource por variável `${datasource}` (ou um UID fixo provisionado igual nos dois ambientes). Senão, o dashboard que funciona no compose aparece "No data" no kind.

## Fluxo de Dados

### Fluxo de requisições

```
POST /links {url, alias?, expiresAt?}
  → RateLimitInterceptor (Redis INCR) ──429?
  → LinkController (Bean Validation) ──400?
  → LinkService: nextval → Base62/alias → INSERT links (Postgres) ──409 alias?
  → LinkCache.evict(code) (Redis DEL)
  ← 201 {code, shortUrl, expiresAt}  + Location

GET /{code}
  → RedirectService → LinkCache (Redis GET) ─miss→ Postgres SELECT → Redis SET EX
  ← 302 Location | 404 | 410
  ⇢ (assíncrono, só no 302) ClickPublisher → executor bounded → XADD lp:clicks

[background] ClickConsumer
  XREADGROUP lp:clicks click-writers COUNT 200 BLOCK 2000
  → parse UA/referrer → Mongo insertMany (unordered, _id = stream id)
  → XACK (lote)
  [a cada 30 s] XPENDING idle>60 s → XCLAIM → reprocessa | deliveryCount>5 → DLQ + XACK
  [a cada 15 s] XINFO GROUPS → gauges de lag/pending

GET /links/{code}/stats?days=30
  → Postgres existsByCode ──404?
  → Mongo aggregate($match code+ts → $facet total/daily/referrers/browsers)
  ← 200 {total, daily[], topReferrers[], topUserAgents[]}
```

### Fluxos-chave

1. **Criação:** síncrona e transacional no Postgres. O Redis só é tocado para invalidar.
2. **Redirect:** leitura via Redis com fallback para o Postgres. A única escrita é o XADD assíncrono. É o caminho medido pelo k6 e pelos alertas de p99.
3. **Ingestão de cliques:** Redis Stream → Mongo, em lote, at-least-once com escrita idempotente. O lag é a métrica de saúde.
4. **Leitura de stats:** Postgres (existência) + Mongo (agregação). Sem cache no MVP. Opcionalmente, cache de 30 s no Redis.
5. **Telemetria:** Prometheus faz pull da 8081, Grafana lê o Prometheus e as regras de alerta são avaliadas no Prometheus. Alertmanager sem receiver real (só a UI), o que basta para a vitrine.

## Topologias de Deploy

### Docker Compose (`make up`)
`postgres`, `mongo` e `redis` com `healthcheck`. O `app` usa `depends_on: condition: service_healthy` e build de `./app`. O `prometheus` monta as regras e o `grafana` monta o provisioning. Só a 8080 do app, a 3000 do Grafana e a 9090 do Prometheus ficam expostas ao host.

### kind + Helm (`make k8s`)
```
scripts/k8s-up.sh:
  kind create cluster --config k8s/kind-config.yaml         (extraPortMappings 80→80)
  helm upgrade --install kps prometheus-community/kube-prometheus-stack -n monitoring -f k8s/kube-prometheus-stack-values.yaml --wait
  helm upgrade --install traefik traefik/traefik -n ingress ...   (controller de Ingress; NÃO ingress-nginx)
  docker build -t link-pulse:dev app/ && kind load docker-image link-pulse:dev
  helm upgrade --install deps helm/link-pulse-deps -n link-pulse --wait
  scripts/sync-observability.sh
  helm upgrade --install link-pulse helm/link-pulse -n link-pulse -f helm/link-pulse/values-dev.yaml --set image.tag=dev --wait
  scripts/smoke.sh http://localhost
```
- **Imagem:** use tag fixa (`dev` ou o SHA) e `imagePullPolicy: IfNotPresent`. A tag `latest` força `Always` e o kind tenta puxar de fora.
- **Controller de Ingress:** Traefik (aceita a API `Ingress`, chart oficial, sobe rápido no kind). O template de Ingress do chart deve ter `ingressClassName` configurável. Gateway API é o caminho "moderno", mas o requisito pede `Ingress`. Pode ser citado no README como evolução. Confiança MÉDIA na escolha específica.
- **HPA:** exige `metrics-server`, que o kind não traz. Instale com `--kubelet-insecure-tls` ou o HPA ficará `<unknown>`.

### AWS (Terraform, só `plan`)
`ALB (público)` → `ECS Fargate service` (subnets privadas) → `RDS Postgres` + `ElastiCache Redis` (subnets privadas, SGs que só aceitam tráfego do SG do service). A imagem vem do `ECR`. Os segredos vêm do `Secrets Manager` via `secrets[].valueFrom` na task definition, e os logs vão para o `CloudWatch Logs` (`awslogs`). **Mongo:** não provisionado. `SPRING_DATA_MONGODB_URI` entra como secret, documentando DocumentDB/Atlas como opção. Atenção: DocumentDB não implementa todo o pipeline de agregação (`$dateTrunc` tem suporte limitado; verificar se for usar).

## Terraform: layout e a realidade do LocalStack

```
terraform/
├── modules/
│   ├── network/          # VPC, subnets públicas/privadas, IGW, route tables, SGs (NAT opcional via var)
│   ├── ecr/              # repositório + lifecycle policy
│   ├── rds-postgres/     # db subnet group, parameter group, instância, secret da senha
│   ├── elasticache-redis/# subnet group, replication group (1 nó)
│   ├── ecs-service/      # cluster, task def, service, IAM (execution/task roles), log group, ALB+TG+listener
│   └── secrets/          # Secrets Manager (DB, Mongo URI)
├── envs/
│   ├── aws/              # provider real, backend S3 comentado/documentado, terraform.tfvars.example → `plan`
│   └── localstack/       # provider com endpoints → localhost:4566, flags skip_*; aplica SÓ módulos suportados
├── tests/
│   └── *.tftest.hcl      # `terraform test` com mock_provider "aws" para TODOS os módulos
└── .tflint.hcl
```

**Matriz de suporte do LocalStack (plano gratuito Hobby, com auth token):**

| Módulo | Hobby (grátis) | Base (pago) | Estratégia |
|---|---|---|---|
| network (VPC/subnets/SG/IGW) | ✅ CRUD | ✅ | `apply` no LocalStack |
| secrets (Secrets Manager) | ✅ | ✅ | `apply` no LocalStack |
| IAM roles/policies, CloudWatch log group | ✅ | ✅ | `apply` no LocalStack |
| ecr | ❌ | ✅ | `terraform test` com mock + `plan` |
| rds-postgres | ❌ | ✅ (PG 13–17; versão real pode divergir) | idem |
| elasticache-redis | ❌ | ✅ (só Redis/Valkey, sem auth/TLS) | idem |
| ecs-service (+ ALB) | ❌ | ✅ (Fargate roda via Docker local) | idem |

**Arquitetura recomendada da validação (em três camadas, todas sem custo):**
1. **Estática (CI em todo PR):** `terraform fmt -check`, `terraform validate` e `tflint` em `envs/aws` e `envs/localstack`.
2. **`terraform test` com `mock_provider "aws"`** (Terraform ≥ 1.7): roda `plan`/`apply` simulados de **todos** os módulos e verifica com `assert` (ex.: SG do RDS só aceita o SG do ECS, task def tem os health checks e secrets, ALB health check em `/readyz`). É isso que "valida" ECS/RDS/ElastiCache/ECR sem conta e sem plano pago. Confiança ALTA no recurso; o desenho em camadas é recomendação.
3. **LocalStack Hobby (`make tf-localstack`):** `apply` real de network + secrets + IAM + logs. No CI, roda só se o secret `LOCALSTACK_AUTH_TOKEN` existir (`if: env.LOCALSTACK_AUTH_TOKEN != ''`). Módulos pagos ficam atrás de `var.enable_compute = false` em `envs/localstack`. Quem tiver plano Base (ou licença de estudante/OSS, se concedida) liga `enable_compute=true` e roda tudo.
- **`terraform plan` para AWS documentado:** o `plan` com o provider real **exige credenciais AWS válidas** (o provider chama STS). Sem conta, gere o plano contra o LocalStack com `envs/aws` apontando para endpoints locais, ou documente o `plan` como passo manual do autor (saída salva em `docs/terraform-plan.txt`). Tratar como gap.
- **Provider LocalStack:** blocos `endpoints {}` explícitos com `skip_credentials_validation`, `skip_requesting_account_id`, `skip_metadata_api_check` e `s3_use_path_style`. Preferível ao wrapper `tflocal`, que é uma dependência Python a mais no Windows.
- **Alternativa sem token:** o Moto server (Apache-2.0) mocka APIs de ECS/RDS/ElastiCache/ECR/ELBv2 o bastante para `apply` de CRUD. Vale como plano B se o token for um bloqueio. Confiança BAIXA na cobertura do provider Terraform contra Moto; precisaria de spike.

## Topologia do CI (GitHub Actions)

```
ci.yml (push/PR)
┌────────────────────┐   ┌──────────────────┐   ┌───────────────────────┐
│ build-test         │   │ helm-lint        │   │ terraform-check       │
│ setup-java 21      │   │ sync-observ.     │   │ fmt/validate/tflint   │
│ ./mvnw verify      │   │ helm lint        │   │ terraform test (mock) │
│ (unit + IT via     │   │ helm template |  │   │ [opcional] localstack │
│  Testcontainers,   │   │  kubeconform     │   │  se houver token      │
│  checkstyle,       │   └────────┬─────────┘   └───────────────────────┘
│  spotbugs)         │            │
└────────┬───────────┘            │
         ▼                        │
┌────────────────────┐            │
│ image              │            │
│ buildx (cache gha) │            │
│ push GHCR só em    │            │
│ main (sha + latest)│            │
│ docker save → art. │            │
└────────┬───────────┘            │
         ▼                        ▼
┌─────────────────────────────────────────────┐
│ kind-e2e (needs: image, helm-lint)          │
│ helm/kind-action → download artifact →      │
│ docker load → kind load → deps chart →      │
│ app chart (serviceMonitor/prometheusRule    │
│ desligados OU só CRDs do prometheus-operator│
│ instaladas) → scripts/smoke.sh             │
│ if: failure() → kubectl describe/logs       │
└─────────────────────────────────────────────┘
load-test.yml (workflow_dispatch): compose up → k6 → artifact com o resumo
```

- **Smoke test** (`scripts/smoke.sh`, o mesmo do `make k8s`): `POST /links` → 201; `GET /{code}` → 302 com `Location` certo; código inexistente → 404; link com `expiresAt` no passado (ou poucos segundos) → 410; espera até 15 s e `GET /stats` → `total >= 1` (prova o pipeline Stream → Mongo de ponta a ponta).
- **kube-prometheus-stack no CI:** instalar o stack completo custa minutos e memória do runner. Recomendado: no CI, aplicar só os CRDs (`ServiceMonitor` e `PrometheusRule`) para validar os templates, ou desligá-los via values. O stack completo fica para o `make k8s` local.
- **Testcontainers no CI:** os runners `ubuntu-latest` têm Docker, então funciona direto. Use `@ServiceConnection` (Boot ≥ 3.1) e o padrão de containers singleton (classe base `AbstractIT` com containers `static`) para não subir três containers por classe de teste.
- **GHCR:** `permissions: packages: write` e login com `GITHUB_TOKEN`. O pacote nasce **privado**: torne-o público nas configurações para o avaliador conseguir fazer `docker pull`.

## Considerações de Escala

| Escala | Ajustes |
|---|---|
| Portfólio / k6 local (≤ 1k req/s) | Um processo faz tudo. Pool Hikari 10, Lettuce com conexão dedicada ao consumer. Hit ratio > 95 % após o aquecimento. |
| 1k–10k req/s de redirect | Separar o Deployment web do worker (mesma imagem, `consumer.enabled`). HPA no web por CPU/RPS. Réplica de leitura do Postgres para os misses. Rollup `click_daily` no consumer. |
| 100k+ req/s | Cache L1 in-process (Caffeine, TTL curto) na frente do Redis. Stream particionado por hash do código (`lp:clicks:{0..N}`) ou Kafka. Stats servidas de pré-agregados. |

### Prioridades de escala
1. **Primeiro gargalo:** misses do cache martelando o Postgres em um pico de links novos ou numa varredura de 404. O negative caching e o pool Hikari resolvem.
2. **Segundo gargalo:** o consumer atrás do produtor (o lag sobe). Mais réplicas consumidoras e lotes maiores resolvem. O alerta de lag é o sinal.

## Anti-Padrões

### Anti-padrão 1: Gravar o clique de forma síncrona no redirect
**O que fazem:** `mongo.insert(click)` ou `XADD` bloqueante dentro do controller.
**Por que é errado:** a latência do redirect passa a depender do Mongo/Redis, e um Mongo lento derruba o p99 e a vitrine.
**Faça assim:** executor limitado + XADD + consumer em lote (Padrões 3 e 4).

### Anti-padrão 2: Dependências externas na liveness
**O que fazem:** `liveness.include=db,redis,mongo`.
**Por que é errado:** um Postgres instável faz o kubelet reiniciar todos os pods em cascata.
**Faça assim:** liveness = `livenessState`; readiness = `readinessState,db,redis`; Mongo fora de ambas.

### Anti-padrão 3: `StreamMessageListenerContainer` com `autoAcknowledge` e insert por mensagem
**O que fazem:** o exemplo "hello world" do Spring Data Redis.
**Por que é errado:** o ACK sai antes de gravar (perde evento em crash), as escritas no Mongo são unitárias e não há tratamento de pendentes.
**Faça assim:** loop explícito, `insertMany` idempotente, ACK depois da escrita e reclaimer.

### Anti-padrão 4: `code` (ou URL) como tag de métrica
**Por que é errado:** explosão de cardinalidade no Prometheus.
**Faça assim:** tags de baixa cardinalidade (`result`, `outcome`). O detalhe por link fica no endpoint `/stats`.

### Anti-padrão 5: `GET /{code}` engolindo outras rotas
**O que fazem:** `@GetMapping("/{code}")` sem restrição, alias sem lista de reservados.
**Por que é errado:** `/favicon.ico`, `/swagger-ui` e `/livez` viram lookups no cache e 404s, ou um alias "livez" sequestra a probe.
**Faça assim:** `@GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")`, palavras reservadas, actuator na 8081.

### Anti-padrão 6: Charts Bitnami / ingress-nginx no kind
**Por que é errado:** as imagens Bitnami gratuitas foram movidas para `bitnamilegacy` sem updates, e o ingress-nginx está aposentado sem patches de segurança. Ambos passam impressão de projeto desatualizado para quem avalia DevOps.
**Faça assim:** chart próprio com imagens oficiais e Traefik (ou Gateway API).

### Anti-padrão 7: Dashboards e regras duplicados à mão para compose e k8s
**Faça assim:** fonte única em `observability/` + script de sync + checagem no CI.

## Pontos de Integração

### Serviços externos

| Serviço | Padrão de integração | Observações |
|---|---|---|
| PostgreSQL | Spring Data JPA + Hikari; Liquibase no startup do app | Com várias réplicas, o lock do Liquibase serializa. Um pod morto durante a migração deixa `DATABASECHANGELOGLOCK` preso (documentar o `liquibase releaseLocks`). Alternativa: rodar a migração num Job de pre-upgrade do Helm (fora do MVP). |
| Redis | `StringRedisTemplate` (cache, rate limit) + `StreamOperations`; Lettuce | Conexão dedicada para o `XREADGROUP BLOCK`. ElastiCache com TLS/AUTH em prod vs. sem TLS local (parametrizar `spring.data.redis.ssl.enabled`). |
| MongoDB | `MongoTemplate` (bulk + aggregate) | Índices criados explicitamente no startup. Indisponível ⇒ só stats e consumer afetados. |
| Prometheus | Pull da 8081 (`/actuator/prometheus`) | Compose: static config. k8s: ServiceMonitor com seletor corrigido. |
| GHCR | `docker/login-action` + `GITHUB_TOKEN` | Tornar o pacote público. |
| LocalStack | Provider Terraform com endpoints locais + `LOCALSTACK_AUTH_TOKEN` | Hobby não cobre ECS/RDS/ElastiCache/ECR/ELB. |

### Fronteiras internas

| Fronteira | Comunicação | Observações |
|---|---|---|
| `link` ↔ `redirect` | Chamada direta (`LinkRepository` de leitura, `LinkCache.evict`) | `redirect` depende de `link.persistence` só para leitura. |
| `redirect` → `click.publish` | Chamada direta, fire-and-forget (executor) | O único acoplamento é o record `ClickEvent`. |
| `click.publish` → `click.consume` | **Redis Stream** (fronteira assíncrona e durável) | Contrato = campos do stream; versionar com o campo `v=1`. |
| `click.stats` → `link` | Leitura de existência | O 404 das stats segue a mesma semântica do redirect. |
| Todos → `observability` | `MeterRegistry` injetado | Nomes centralizados em constantes. |

## Ordem de Construção Sugerida (implicações para o roadmap)

Dependências reais entre componentes, já em granularidade grossa:

1. **Fundação + núcleo de links (Postgres).** Repo skeleton (`app/`, Makefile, `scripts/`), Maven com Checkstyle/SpotBugs, Liquibase (`links` + sequência, contexts), `CodeGenerator` + testes unitários, `POST /links` e `GET /{code}` **sem cache** (302/404/410), `AbstractIT` com Testcontainers, workflow `build-test`, `compose.yaml` só com Postgres.
   *Por quê primeiro:* tudo depende da verdade do link e do contrato de runtime. O CI verde cedo evita dívida de build.
2. **Redis: cache-aside, negative caching e rate limit + métricas do hot path.** Depende do (1). Testável isoladamente com Testcontainers Redis.
3. **Pipeline de cliques: Stream → consumer → Mongo → `/stats`.** Depende do (2) (Redis já integrado) e do (1). É a parte com maior risco técnico (PEL, idempotência, lag) e precisa de testes de integração de crash/reentrega.
4. **Container + compose completo + observabilidade.** Dockerfile, compose com os 6 serviços, `observability/` (dashboards, regras), gauges de lag, k6 contra o compose. Depende de (1)–(3), porque as métricas precisam existir para os dashboards fazerem sentido.
5. **Kubernetes: Helm (app + deps), kind, kube-prometheus-stack, job `kind-e2e`, publicação no GHCR.** Depende de (4): reaproveita a imagem, o `observability/` e o `smoke.sh`.
6. **Terraform AWS + LocalStack.** Depende só do **contrato de runtime** (portas, env vars, health paths) e da imagem existir conceitualmente. Pode correr **em paralelo** a (4)/(5) se houver folga. Precisa de decisão prévia sobre o token do LocalStack e o escopo de "validado".
7. **Documentação/vitrine.** README pt-BR, diagrama, resultados do k6, prints. Fecha depois de (5), porque os prints vêm do kind/compose.

**Flags de pesquisa por fase:**
- Fase 3: precisa de pesquisa mais funda (API exata do Spring Data Redis da versão escolhida, comportamento do `BLOCK` com Lettuce, testes de reentrega).
- Fase 5: verificar as versões atuais dos charts kube-prometheus-stack/Traefik/metrics-server e o comportamento do sidecar de dashboards.
- Fase 6: precisa de spike (token Hobby, `mock_provider`, como produzir um `plan` "AWS" documentável sem credenciais).
- Fases 1, 2 e 4: padrões consolidados, pesquisa adicional improvável.

## Fontes

- Spring Boot Actuator, probes e health groups (oficial, ALTA): https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
- LocalStack ECS, plano Base (MÉDIA, doc oficial via WebFetch): https://docs.localstack.cloud/aws/services/ecs/
- LocalStack RDS, plano Base, PG 13–17 (MÉDIA): https://docs.localstack.cloud/aws/services/rds/
- LocalStack ElastiCache, plano Base, sem auth/TLS (MÉDIA): https://docs.localstack.cloud/aws/services/elasticache/
- LocalStack ECR, plano Base (MÉDIA): https://docs.localstack.cloud/aws/services/ecr/
- LocalStack ELB/ELBv2, plano Base (MÉDIA): https://docs.localstack.cloud/aws/services/elb/
- LocalStack EC2/VPC CRUD no Hobby (MÉDIA): https://docs.localstack.cloud/aws/services/ec2/
- LocalStack licenciamento/planos (MÉDIA): https://docs.localstack.cloud/references/licensing/ e https://www.localstack.cloud/pricing
- Auth token obrigatório desde 23/03/2026 (MÉDIA, várias fontes concordam): https://github.com/testcontainers/testcontainers-java/issues/11568 , https://dev.to/peytongreen_dev/your-localstack-ci-is-broken-here-are-your-three-options-41o8
- Aposentadoria do ingress-nginx (ALTA, blog oficial do Kubernetes): https://www.kubernetes.io/blog/2026/01/29/ingress-nginx-statement/
- Mudanças no catálogo Bitnami (ALTA, issue oficial): https://github.com/bitnami/charts/issues/35164
- Fim do OSS do Spring Boot 3.5 em 30/06/2026 (MÉDIA): https://www.osseva.io/eol/spring-boot-3-5 , https://www.danvega.dev/blog/spring-boot-end-of-life
- Redis 8.2: XADD/XTRIM KEEPREF|DELREF|ACKED, XACKDEL (ALTA, oficial): https://redis.io/blog/redis-82-streams-bitmap/ , https://redis.io/docs/latest/commands/xadd/
- Spring Data Redis sem XAUTOCLAIM (MÉDIA): https://github.com/spring-projects/spring-data-redis/issues/3434
- XAUTOCLAIM (oficial): https://redis.io/docs/latest/commands/xautoclaim/
- Conhecimento de treinamento (MÉDIA, não reverificado nesta sessão): seletores `*SelectorNilUsesHelmValues` do kube-prometheus-stack, sidecar `grafana_dashboard`, restrição de `.Files` do Helm, ausência de índice único em time-series do MongoDB, `mock_provider` do Terraform ≥ 1.7, `@ServiceConnection` do Testcontainers.

---
*Pesquisa de arquitetura para: Link Pulse (encurtador de URLs com analytics)*
*Pesquisado em: 2026-10-06*
