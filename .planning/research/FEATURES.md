# Pesquisa de Funcionalidades

**Domínio:** Encurtador de URLs com analytics de cliques (somente API, sem auth, sem frontend), como vitrine de portfólio DevOps/cloud
**Pesquisado em:** 2026-10-06
**Confiança:** MEDIUM no geral (semântica HTTP e Spring Boot Actuator: HIGH; comportamento de concorrentes e números de tráfego de bots: MEDIUM/LOW. Detalhes em "Fontes")

## Leitura de contexto

O "usuário" aqui não é alguém encurtando links em produção. É o **avaliador técnico** que clona o repositório, roda `make up` / `make k8s`, faz alguns `curl` e abre o Grafana. Por isso a classificação segue esta regra:

- **Table stakes** = o que, se faltar, faz o avaliador concluir que "não é um encurtador de verdade" ou que "não é um projeto DevOps de verdade". Funcionalidades de API **e** operacionais entram aqui.
- **Diferenciais** = o que mostra maturidade de produção (degradação graciosa, privacidade, alertas com runbook, consumer idempotente) sem estourar o prazo de 1-2 semanas.
- **Anti-features** = o que os concorrentes (Bitly, Dub, Shlink, Kutt, YOURLS) têm, mas que aqui só consumiria tempo sem mostrar nada de infra, ou que **fica perigoso sem autenticação**.

A complexidade (LOW/MEDIUM/HIGH) considera a stack já decidida: Java 21, Spring Boot 3.x, Postgres + Liquibase, Redis, MongoDB, Helm/kind e Terraform/LocalStack.

---

## Panorama de Funcionalidades

### Table Stakes (o avaliador espera ver)

#### A. API de Links

| Funcionalidade | Por que é esperada | Complexidade | Notas |
|---|---|---|---|
| `POST /links` com `url`, `alias?` e `expiresAt?`, respondendo `201 Created` + header `Location` + corpo com `code`, `shortUrl`, `targetUrl`, `expiresAt` e `createdAt` | É o contrato mínimo de todo encurtador (Bitly `POST /v4/shorten`, Kutt, Shlink) | LOW | Retornar a `shortUrl` absoluta, montada a partir de uma `app.base-url` configurável (muda entre compose, kind e ECS) |
| Validação da URL de destino: apenas `http`/`https`, `URI` parseável e tamanho máximo (~2048) | Sem isso o encurtador vira vetor de `javascript:`, `data:` e `file:`, um erro clássico | LOW | Use `java.net.URI` e não regex. Rejeite com 400 |
| Validação de alias: regex (ex.: `^[A-Za-z0-9_-]{3,32}$`), unicidade (409 Conflict) e **lista de palavras reservadas** (`links`, `actuator`, `swagger-ui`, `v3`, `api-docs`, `favicon.ico`, `health`, `metrics`) | Um alias `actuator` ou `links` sequestra uma rota da própria aplicação | LOW | A unicidade é garantida pela constraint `UNIQUE` no Postgres, não por check-then-insert |
| Geração de código Base62 a partir da sequência do Postgres | Já decidida. Não colide, é determinística e fácil de testar | LOW | Ver **pitfall de colisão alias × código gerado** em Dependências |
| `expiresAt` precisa estar no futuro e ter um limite razoável | Evita links "já expirados" e datas absurdas | LOW | Use ISO-8601 em UTC (`Instant`) |
| Erros padronizados em **RFC 9457 Problem Details** (`application/problem+json`) | Spring 6 / Boot 3 trazem `ProblemDetail` nativo, então não usar parece amador | LOW | `spring.mvc.problemdetails.enabled=true` + `@RestControllerAdvice` para 400/404/409/410/429 |
| `GET /links/{code}` com metadados do link (sem stats) | Permite ao avaliador conferir o que foi criado sem passar pelo redirect | LOW | Barato e útil no smoke test |

#### B. Redirect

| Funcionalidade | Por que é esperada | Complexidade | Notas |
|---|---|---|---|
| `GET /{code}` → **302** + `Location` | É o núcleo do produto. Com 302 o navegador volta ao servidor a cada clique, e por isso **todo clique é contabilizado**. 301 é cacheado pelo navegador e "some" com cliques | LOW | Envie também `Cache-Control: private, max-age=0` (ou `no-store`) para impedir cache intermediário de redirects com analytics |
| Lookup cache-aside no Redis (hit → Redis, miss → Postgres → popula o Redis) | É o "redirect rápido via cache" do Valor Central | LOW | Chave `link:{code}` com o valor `targetUrl` + `expiresAt` |
| **TTL do cache = `min(TTL padrão, expiresAt − agora)`** | Sem isso um link expirado continua redirecionando a partir do cache | LOW | Mesmo com o TTL, faça a checagem defensiva de `expiresAt` no hit, porque o relógio do Redis e o da app podem divergir |
| Inexistente → **404**, expirado → **410 Gone** | Semântica HTTP correta (RFC 9110). O Shlink, por exemplo, trata expirado como "não encontrado", então 410 é um detalhe que o avaliador nota | LOW | Corpo em Problem Details. Expirado **não** deve popular cache positivo |
| Clique publicado de forma assíncrona (Redis Stream), sem bloquear o redirect | Já decidido. Latência do redirect independente do Mongo | MEDIUM | Se `XADD` falhar, o redirect **segue** (fail-open) e incrementa uma métrica de erro |
| `HEAD /{code}` responde igual ao GET, mas **não registra clique** | Verificadores de link e alguns crawlers usam HEAD. Contar isso infla métricas | LOW | O Spring trata HEAD via GET por padrão. Basta checar o método antes de publicar o evento |

#### C. Analytics

| Funcionalidade | Por que é esperada | Complexidade | Notas |
|---|---|---|---|
| Evento de clique com `code`, `timestamp` (UTC), `referrer`, `userAgent` e IP **anonimizado/hasheado** | São os dados mínimos para as stats pedidas | LOW | Ver "Privacidade" abaixo: nunca persistir o IP cru |
| Consumer group no Redis Stream gravando no Mongo **em lote**, com `XACK` só depois do `insertMany` | Entrega at-least-once real. Sem ACK pós-escrita, um crash perde cliques | MEDIUM | `XREADGROUP COUNT N BLOCK ms`. Lote por tamanho **ou** tempo |
| `GET /links/{code}/stats`: total, cliques por dia, top referrers e top user-agents | Já especificado. É o "analytics" do nome do produto | MEDIUM | Aggregation pipeline com `$match` por `code` + `$group`. Índice `{code: 1, ts: -1}` é obrigatório |
| Stats de link inexistente → 404 (consultando o Postgres, não o Mongo) | Link sem cliques ≠ link inexistente | LOW | Link existente sem cliques → `total: 0` e listas vazias |
| Filtro de período `from`/`to` (default: últimos 30 dias) | Sem filtro, a agregação varre todo o histórico e cresce sem limite | LOW | Agrupar por dia em UTC (`$dateTrunc` com `timezone: "UTC"`) e documentar isso |
| Top user-agents **parseados** (navegador/SO/tipo de dispositivo), não a string crua | Cada UA cru é praticamente único, então um "top 5" de strings cruas não diz nada | LOW–MEDIUM | Classificação simples por substring é suficiente. Uma lib (Yauaa / uap-java) é opcional |
| Stats documentadas como **eventualmente consistentes** | O clique passa por stream e lote, então não aparece na hora | LOW | Uma frase no README e no OpenAPI evita "bug report" do avaliador |

#### D. Rate limiting

| Funcionalidade | Por que é esperada | Complexidade | Notas |
|---|---|---|---|
| Limite por IP em `POST /links` via Redis → **429** + `Retry-After` | Já especificado. É a proteção básica de uma API pública sem auth | LOW–MEDIUM | Janela fixa com `INCR` + `EXPIRE` atômico (script Lua ou `SET NX EX` + `INCR`) basta. Bucket4j + Redis é a alternativa "de mercado" |
| **IP real do cliente atrás de proxy/ingress** (`X-Forwarded-For` confiável) | Atrás do ingress do kind ou de um ALB, todos os clientes "têm o mesmo IP" e o rate limit vira global | LOW | `server.forward-headers-strategy=native` (ou `framework`) **e** confiar só no proxy conhecido. Sem isso o limitador também fica falsificável |
| Fail-open se o Redis cair (loga + métrica, não bloqueia criação) | O limitador não pode virar ponto único de falha da API | LOW | Decisão explícita, documentada |
| Métrica de rejeições (`ratelimit_rejected_total`) | Permite mostrar o 429 num painel durante o k6 | LOW | |

#### E. Observabilidade (funcionalidades operacionais)

| Funcionalidade | Por que é esperada | Complexidade | Notas |
|---|---|---|---|
| `/actuator/health/liveness` e `/actuator/health/readiness` como **grupos separados** | O Helm usa liveness/readiness. É o padrão Spring Boot + Kubernetes | LOW | **Liveness só com `livenessState`**. Readiness com `readinessState`, `db` e `redis`. Mongo fora da readiness (ver Pitfalls) |
| `/actuator/prometheus` com métricas RED do HTTP (`http.server.requests`) e **histograma habilitado** | Sem `percentiles-histogram` não dá para calcular p95/p99 no PromQL | LOW | `management.metrics.distribution.percentiles-histogram.http.server.requests=true` |
| Métricas de domínio: redirects por resultado (`hit`/`miss`/`not_found`/`expired`), cache hit/miss, cliques publicados/persistidos, falhas de publicação, tamanho de lote, **lag/pending do consumer** | São exatamente os painéis que o PROJECT.md promete | MEDIUM | **Nunca** usar `code` ou URL como tag (explosão de cardinalidade) |
| Dashboards Grafana versionados (JSON) com taxa de req., p95/p99, hit ratio, cliques/min, erros e lag | É o "olhar os dashboards" do Valor Central | MEDIUM | Provisionados por arquivo no compose e por ConfigMap com label do sidecar no kube-prometheus-stack, **o mesmo JSON** nos dois |
| Alertas Prometheus: taxa de 5xx, p99 alto, lag do consumer, `up == 0` | Já especificado. Mostra que a observabilidade é acionável, não decorativa | LOW–MEDIUM | No k8s, como `PrometheusRule`. No compose, como `rules.yml` |
| Logs estruturados em JSON com trace/correlation id | Avaliador de DevOps procura isso. O Spring Boot 3.4+ tem structured logging nativo (`logging.structured.format.console=ecs`/`logstash`) | LOW | Não logar IP cru nem a URL completa com query string (pode conter tokens) |
| Graceful shutdown (HTTP + flush do lote do consumer) | Sem isso, rolling update do Helm/HPA perde cliques em memória | LOW–MEDIUM | `server.shutdown=graceful`, `terminationGracePeriodSeconds` > timeout de shutdown e, se precisar, um `preStop` sleep curto |

#### F. Entrega / Infra (funcionalidades que o avaliador "usa")

| Funcionalidade | Por que é esperada | Complexidade | Notas |
|---|---|---|---|
| `make up` → stack completa saudável, com `depends_on: condition: service_healthy` | É a primeira coisa que o avaliador roda | LOW–MEDIUM | Healthchecks em Postgres, Redis e Mongo. A app só sobe depois deles |
| `make k8s` → kind + Helm + kube-prometheus-stack + app | Segundo caminho do Valor Central | MEDIUM–HIGH | O kube-prometheus-stack é pesado. Documentar os recursos mínimos de Docker Desktop |
| Helm com requests/limits, `securityContext` (non-root, `readOnlyRootFilesystem`), HPA, probes e `ServiceMonitor` | Mostra que sabe empacotar para produção | MEDIUM | `ServiceMonitor` habilitado por flag em `values` (só existe com o CRD do operator) |
| Seed/demo: `make demo` (ou script) que cria links e gera cliques | Sem tráfego os dashboards ficam vazios e o avaliador não vê nada | LOW | Pode reaproveitar o script do k6 em modo "leve" |
| Smoke test no CI (deploy no kind → cria link → segue redirect → checa stats) | Já especificado. Prova que o pipeline entrega algo funcional | MEDIUM | O smoke deve tolerar a consistência eventual das stats (retry curto) |

#### G. Documentação

| Funcionalidade | Por que é esperada | Complexidade | Notas |
|---|---|---|---|
| OpenAPI 3 gerado (springdoc-openapi) + Swagger UI | API sem frontend precisa de uma forma clicável de explorar | LOW | Os paths do Swagger (`/swagger-ui`, `/v3/api-docs`) entram na lista de aliases reservados |
| Exemplos `curl` (ou arquivo `.http`) no README, em pt-BR | O avaliador copia e cola. Precisa funcionar de primeira | LOW | |
| Diagrama de arquitetura + resultados do k6 + prints dos dashboards | Já especificado | LOW | |
| Seção "Decisões e trade-offs" (302 vs 301, 404 vs 410, Streams vs Kafka, IP hasheado, fail-open) | É a parte que o avaliador **lê**. Transforma escolhas em sinal de senioridade | LOW | Pode reaproveitar a tabela de Decisões do PROJECT.md |

---

### Diferenciais (vantagem na vitrine)

Ordenados por **sinal para o avaliador ÷ custo**. Os primeiros são quase de graça.

| Funcionalidade | Proposta de valor | Complexidade | Notas |
|---|---|---|---|
| **Degradação graciosa demonstrável** ("Redis caiu? redirect continua via Postgres; clique é descartado e contado em métrica; rate limit fail-open") | Mostra raciocínio de produção. Dá para demonstrar ao vivo com `docker stop redis` e mostrar no dashboard | MEDIUM | Timeouts curtos no Lettuce (ex.: 100–200 ms) são pré-requisito. Sem eles o "fail-open" vira "fail-slow" |
| **Filtragem de bots/preview crawlers** (`facebookexternalhit`, `Slackbot-LinkExpanding`, `Twitterbot`, `WhatsApp`, `TelegramBot`, `Googlebot`, `bingbot`, `curl`?) | Crawlers de preview inflam contagens (relatos de 5–25% do tráfego em links públicos). O Dub deixou de contar Googlebot/Twitterbot e o Shlink tem opção para excluir bots | LOW | **Recomendação:** gravar o clique com `isBot: true` e **excluir das stats por padrão** (`?includeBots=true` para ver). Melhor que descartar, porque mantém transparência, coisa que o Dub não oferece. Lista de substrings versionada no código + teste unitário |
| **IP nunca armazenado em claro**: hash `SHA-256(ip + salt)` com salt rotativo diário, ou truncamento (/24 IPv4, /48 IPv6) | Shlink anonimiza por padrão e Kutt não loga IP. Para um projeto brasileiro, citar LGPD no README é sinal de maturidade | LOW | O hash com salt diário ainda permite "visitantes únicos por dia" (modelo do Plausible) sem identificar ninguém |
| **Visitantes únicos por dia** nas stats (contagem distinta do hash do dia) | Métrica a mais nas stats, sem custo de privacidade | LOW | Depende do item anterior |
| **Consumer idempotente**: usar o ID da mensagem do stream como `_id` no Mongo (`insertMany` com `ordered: false`, ignorando duplicate key) | Redelivery (crash entre insert e `XACK`) não duplica cliques. Responde ao "e se cair no meio?" | LOW | Combina com o `XAUTOCLAIM` abaixo |
| **Recuperação de pendentes** (`XAUTOCLAIM` de mensagens paradas no PEL) + **`MAXLEN ~` no `XADD`** | Sem isso, o stream cresce até estourar a memória do Redis e mensagens de um pod morto ficam presas para sempre | MEDIUM | O gauge de `pending` alimenta o alerta de lag. Dead-letter é opcional (v1.x) |
| **Códigos não enumeráveis**: ofuscar o ID antes do Base62 (Sqids / permutação bijetiva / sequência começando em valor alto) | Base62 de sequência pura gera `1`, `2`, `3`…: qualquer um varre todos os links e as stats públicas deles | LOW–MEDIUM | Mantém a garantia de "sem colisão", porque é bijeção. Teste unitário do gerador cobre os dois sentidos |
| **Negative caching** de códigos inexistentes (TTL curto, ex.: 30–60 s) | Protege o Postgres de varredura/enumeração em 404 e mostra que o cache foi pensado para os dois caminhos | LOW | **Cuidado:** invalidar (`DEL`) a chave negativa ao criar um link com aquele alias |
| **Headers de rate limit** (`RateLimit` / `RateLimit-Policy` do draft IETF, ou `X-RateLimit-*`) além do `Retry-After` | Clientes conseguem se autorregular. Mostra conhecimento de padrões HTTP | LOW | O draft IETF ainda não virou RFC. `Retry-After` é o único padronizado. Documente isso |
| **Alertas com `runbook_url`/annotation** + um `docs/runbooks/*.md` curto por alerta | Separa "configurei alertas" de "operei sistemas". Avaliador de SRE nota | LOW | 4 alertas → 4 runbooks de meia página |
| **Painel "saúde do pipeline de cliques"**: publicados/s vs persistidos/s, pending, tamanho de lote, falhas | Torna o assíncrono **visível**. É a diferença entre "usei Redis Streams" e "operei Redis Streams" | LOW–MEDIUM | Reaproveita as métricas de domínio |
| **Retenção de eventos de clique** via TTL index no Mongo (ex.: 90 dias, configurável) | Dado de analytics cresce para sempre. Retenção é decisão operacional e também de privacidade | LOW | Um índice TTL em `ts` |
| **Thresholds do k6 como gate** (ex.: `p(95)<50ms` no redirect com cache quente, `http_req_failed<1%`) | Teste de carga com critério de aprovação, não só um número no README | LOW | Rodar no CI só um k6 "smoke" curto. O teste completo fica local |
| **Scan de imagem (Trivy) + SBOM** no CI | Supply chain é tema quente em vagas de DevOps. Custo de 1 step | LOW | Falhar só em CRITICAL para não travar o pipeline |
| **Tracing distribuído** (Micrometer Tracing + OTel → Tempo/Jaeger), com trace atravessando redirect → stream → consumer | Sinal forte de observabilidade completa (métricas + logs + traces) | HIGH | Propagar contexto por stream exige header manual no evento. **Melhor como v1.x.** No v1 basta o trace id nos logs |
| SLO + alertas de burn-rate (multiwindow) para o redirect | Nível SRE "de livro" | MEDIUM–HIGH | Só se sobrar tempo. Alertas simples já cumprem o requisito |
| `PodDisruptionBudget` + `NetworkPolicy` no chart | Detalhes de produção no Helm que poucos portfólios têm | LOW | `NetworkPolicy` só é aplicada se o CNI do kind suportar (o kindnet padrão **não** aplica). Documentar |

---

### Anti-Features (parecem boas, mas atrapalham este projeto)

| Funcionalidade | Por que é pedida | Por que é problemática aqui | Alternativa |
|---|---|---|---|
| **Editar destino (`PATCH`) / apagar link (`DELETE`) sem auth** | Bitly, Kutt e Dub permitem editar o destino | Sem autenticação, **qualquer pessoa** altera ou apaga qualquer link, o que transforma o encurtador em ferramenta de sequestro de links. Também força invalidação de cache, que hoje é desnecessária porque links são imutáveis | Links **imutáveis** e expiração como único "fim de vida". Se quiser mostrar invalidação: devolver um `managementToken` na criação (v1.x, opcional) |
| Redirect **301** | "Melhor para SEO", "mais rápido" | O navegador cacheia e cliques repetidos não chegam ao servidor, o que contradiz o produto de analytics | 302 + `Cache-Control` sem cache. Documentar o trade-off |
| **Geolocalização por IP** (MaxMind GeoLite2) | Todo concorrente mostra país/cidade | Exige conta e license key MaxMind, download de base no build/container, atualização periódica, além de questões de LGPD. Muito custo para pouco sinal DevOps | Referrer + dispositivo/navegador. Citar GeoIP como evolução no README |
| Armazenar **IP cru** | "Pode ser útil depois" | Dado pessoal (LGPD/GDPR) sem finalidade. Avaliador atento marca isso como problema | Hash com salt rotativo ou truncamento |
| **Dedupe de URL longa** (mesma URL → mesmo código, como o Bitly por conta) | Economiza códigos | Sem contas, dedupe mistura stats de pessoas diferentes e conflita com expiração/alias distintos. Exige lookup por URL (índice em texto longo) | Cada `POST` cria um link novo. Documentar a decisão |
| **Expiração por número de cliques** ("expira após N cliques") | Kutt e outros oferecem | **Conflita com a contagem assíncrona**: o redirect precisaria de um contador síncrono e atômico (`INCR` no caminho quente) e a regra de 410 ficaria dividida entre duas fontes | Só expiração por data |
| Links com senha / página intermediária / preview | Kutt e Shlink têm | Exige página HTML (formulário), ou seja, frontend, que está fora de escopo | — |
| QR code, custom domains, multi-tenant, UTM builder, link-in-bio, A/B e roteamento por dispositivo/geo | Recursos "de produto" de Bitly/Dub | Puro produto, zero sinal de infra, e cada um puxa outros (domínios → TLS/DNS, tenants → auth) | Fora de escopo, listado no README como "não objetivos" |
| Endpoint de **bulk create** / import CSV | Comum em APIs comerciais | Complica rate limit e validação (falha parcial) sem mostrar nada novo | v2, se houver |
| Stats em **tempo real** (WebSocket/SSE) ou contadores pré-agregados duplicados no Redis | "Analytics ao vivo" | Duas fontes de verdade para o mesmo número. O Grafana já mostra cliques/min em tempo quase real via Prometheus | Prometheus para tempo real (operacional) e Mongo para histórico (produto). Deixar essa separação explícita |
| **Exactly-once** na contagem de cliques | "Precisão" | Caro e desnecessário. Cliques são métrica estatística | At-least-once + escrita idempotente (`_id` = ID do stream) |
| Verificação de malware/phishing com APIs externas (Google Safe Browsing) | Encurtadores públicos são vetor de phishing | Exige API key, chamada externa no caminho de criação e quebra o "roda sem conta cloud" | Validar esquema/host e bloquear self-loop (destino no próprio host). Citar Safe Browsing no README como mitigação real |
| Auth, API keys, usuários, frontend, Kafka, EKS, service mesh, multi-região | — | Já estão fora de escopo no PROJECT.md | Manter fora |
| Tags de métrica por `code`/URL/IP | "Ver cliques por link no Grafana" | Explosão de cardinalidade no Prometheus, um anti-padrão clássico que avaliador de observabilidade pega na hora | Por-link fica no Mongo (`/stats`). Prometheus só com agregados |

---

## Dependências entre Funcionalidades

```
[Postgres + Liquibase (tabela links, sequência, UNIQUE(code))]
    └──requer──> [Gerador Base62 (+ ofuscação opcional)]
                     └──requer──> [POST /links (validação URL/alias/expiração, reservados)]
                                      └──requer──> [GET /{code} redirect 302 / 404 / 410]
                                                       ├──requer──> [Cache Redis cache-aside, TTL = min(padrão, expiração)]
                                                       │                └──aprimorado por──> [Negative caching 404]
                                                       └──requer──> [Publicação no Redis Stream (fail-open)]
                                                                        └──requer──> [Consumer group → Mongo em lote + XACK]
                                                                                         ├──aprimorado por──> [_id = ID do stream (idempotência)]
                                                                                         ├──aprimorado por──> [XAUTOCLAIM + MAXLEN]
                                                                                         └──requer──> [GET /links/{code}/stats (aggregation + índice)]
                                                                                                          ├──aprimorado por──> [Flag isBot + filtro padrão]
                                                                                                          └──aprimorado por──> [IP hasheado → únicos/dia]

[server.forward-headers-strategy (IP real)] ──requer-se antes de──> [Rate limit por IP → 429]
[server.forward-headers-strategy (IP real)] ──requer-se antes de──> [Hash de IP / únicos por dia]

[Micrometer + /actuator/prometheus + histogramas]
    └──requer──> [Métricas de domínio (cache, redirect, cliques, lag)]
                     └──requer──> [Dashboards Grafana JSON] ──e──> [Alertas Prometheus] ──aprimorado por──> [Runbooks]

[Health groups liveness/readiness] ──requer-se antes de──> [Helm probes] ──e──> [HEALTHCHECK do Dockerfile] ──e──> [Compose depends_on healthy]

[Seed/demo de tráfego] ──requer-se antes de──> [Prints dos dashboards] ──e──> [Resultados do k6 no README]

[PATCH/DELETE de links] ──conflita──> [Sem autenticação]
[Expiração por nº de cliques] ──conflita──> [Contagem assíncrona via Stream]
[Redirect 301] ──conflita──> [Contagem de todos os cliques]
[Mongo na readiness] ──conflita──> [Redirect independente do analytics]
```

### Notas de dependência

- **Gerador Base62 × alias customizado (pitfall crítico):** um alias como `abc` pode coincidir com o Base62 de um ID futuro da sequência. A `UNIQUE(code)` faz o insert falhar **depois**, quando um usuário inocente cria um link. Resolva **antes** de implementar o `POST /links`. Opções, da mais simples para a mais complexa: (1) aliases exigem um caractere fora do alfabeto gerado (ex.: `-`) ou um tamanho mínimo maior que o dos códigos gerados; (2) em caso de violação da `UNIQUE` com código gerado, consumir o próximo valor da sequência e tentar de novo (com teste). Recomendado: (1) + a constraint como rede de segurança.
- **Ofuscação de ID precisa ser decidida junto do gerador:** trocar depois muda todos os códigos existentes. Isso cabe na fase do gerador e dos testes unitários.
- **Cache-aside depende das regras de expiração:** o TTL do cache só fica correto se `expiresAt` estiver disponível no valor cacheado. Cachear só a URL obriga um lookup extra para decidir entre 302 e 410.
- **Negative caching depende do fluxo de criação:** ao criar um link (sobretudo com alias), a chave negativa precisa ser apagada. Senão o link novo responde 404 até o TTL negativo vencer.
- **Stats dependem do consumer, e o smoke test depende das stats:** o smoke test do CI precisa esperar o consumer (poll com timeout). Um assert imediato fica intermitente.
- **IP real (`forward-headers-strategy`) é pré-requisito do rate limit e do hash de IP:** sem ele, no kind/ALB, todos compartilham o IP do proxy.
- **Readiness não deve depender do Mongo:** o redirect não usa Mongo. Se o Mongo cair, o pod deve continuar recebendo tráfego enquanto o consumer acumula pending (e o alerta de lag dispara). Readiness = Postgres + Redis. Liveness = só o estado interno.
- **Dashboards e prints dependem de tráfego:** o script de seed/demo e o k6 precisam existir antes da fase de documentação.
- **Prometheus no compose × kube-prometheus-stack no k8s:** as regras de alerta e os dashboards devem ter **uma única fonte** (JSON/YAML) consumida pelos dois ambientes, ou vão divergir.

---

## Definição de MVP

### Entregar no v1 (escopo de 1-2 semanas)

**API / domínio**
- [ ] `POST /links` com validação de URL (esquema http/https, tamanho), alias (regex, reservados, 409) e expiração futura. Erros em Problem Details
- [ ] Gerador Base62 da sequência com **regra anti-colisão com alias** e testes unitários
- [ ] `GET /{code}` → 302 com `Cache-Control` sem cache, 404 e 410. HEAD não conta clique
- [ ] Cache-aside Redis com TTL limitado pela expiração. Fail-open para o Postgres se o Redis cair
- [ ] Publicação de clique no Stream (fail-open) → consumer group → `insertMany` no Mongo → `XACK`, com `_id` = ID do stream
- [ ] `GET /links/{code}/stats` com `from`/`to`, total, por dia (UTC), top referrers e top navegadores/SO (UA parseado). Bots excluídos por padrão
- [ ] IP hasheado com salt (nunca em claro)
- [ ] Rate limit por IP no `POST /links` → 429 + `Retry-After`, com IP real via forward headers

**Operacional**
- [ ] Health groups liveness/readiness corretos. HEALTHCHECK no Dockerfile. `depends_on: service_healthy`
- [ ] Métricas: HTTP com histograma, cache hit/miss, redirects por resultado, cliques publicados/persistidos/falhos, pending do consumer, rejeições de rate limit
- [ ] Dashboards JSON versionados (incluindo o painel do pipeline de cliques) e 4 alertas (5xx, p99, lag, `up`)
- [ ] Logs JSON estruturados. Graceful shutdown com flush do lote
- [ ] OpenAPI/Swagger UI, `curl` no README e script de demo/seed

### Adicionar após o núcleo funcionar (v1.x, se sobrar tempo)

- [ ] Ofuscação de ID (Sqids/permutação). **Gatilho:** antes do primeiro "release". Se não entrar no v1, documentar como trade-off conhecido
- [ ] Negative caching de 404
- [ ] `XAUTOCLAIM` de pendentes + `MAXLEN ~` no stream (o `MAXLEN` é barato e pode ir para o v1)
- [ ] TTL index de retenção no Mongo + visitantes únicos/dia
- [ ] Runbooks por alerta. Headers `RateLimit-*`
- [ ] Trivy + SBOM no CI. Thresholds do k6. PDB no chart
- [ ] `managementToken` na criação para habilitar `DELETE` seguro (só se quiser demonstrar invalidação de cache)

### Considerar no futuro (v2+)

- [ ] Tracing distribuído atravessando o stream (OTel + Tempo). Alto sinal, mas custo alto
- [ ] SLOs com burn-rate alerts
- [ ] GeoIP, bulk create e dead-letter stream. Dependem de mais infra ou de mais decisões de produto

---

## Matriz de Priorização

| Funcionalidade | Valor para o avaliador | Custo de implementação | Prioridade |
|---|---|---|---|
| POST /links + validações + Problem Details | HIGH | LOW | P1 |
| Base62 + regra alias × gerado | HIGH | LOW | P1 |
| Redirect 302/404/410 + cache-aside com TTL | HIGH | LOW | P1 |
| Stream → consumer em lote → Mongo (ACK pós-escrita) | HIGH | MEDIUM | P1 |
| Stats com período, UA parseado e bots excluídos | HIGH | MEDIUM | P1 |
| Rate limit 429 + IP real | MEDIUM | LOW | P1 |
| Health groups liveness/readiness | HIGH | LOW | P1 |
| Métricas de domínio + histogramas | HIGH | MEDIUM | P1 |
| Dashboards + alertas versionados | HIGH | MEDIUM | P1 |
| IP hasheado | MEDIUM | LOW | P1 |
| Fail-open do Redis (redirect, stream, rate limit) | HIGH | MEDIUM | P1 |
| Consumer idempotente (`_id` = ID do stream) | MEDIUM | LOW | P1 |
| Logs JSON + graceful shutdown | MEDIUM | LOW | P1 |
| Script de demo/seed | HIGH | LOW | P1 |
| OpenAPI/Swagger | MEDIUM | LOW | P1 |
| Ofuscação de ID | MEDIUM | LOW | P2 |
| Negative caching | MEDIUM | LOW | P2 |
| XAUTOCLAIM + MAXLEN | MEDIUM | MEDIUM | P2 (MAXLEN: P1) |
| Retenção TTL + únicos/dia | LOW | LOW | P2 |
| Runbooks, headers RateLimit, Trivy/SBOM, thresholds k6, PDB | MEDIUM | LOW | P2 |
| Tracing distribuído | HIGH | HIGH | P3 |
| SLO burn-rate | MEDIUM | MEDIUM | P3 |
| GeoIP, bulk, edição/remoção | LOW | MEDIUM–HIGH | Fora |

**Legenda:** P1 = obrigatório no v1. P2 = deveria entrar se houver folga. P3 = futuro.

---

## Análise de Concorrentes (referência de funcionalidades)

| Funcionalidade | Bitly (API v4) | Dub | Shlink (self-hosted) | Kutt (self-hosted) | Link Pulse (plano) |
|---|---|---|---|---|---|
| Status de redirect | 301 com cache curto (MEDIUM, não verificado nesta pesquisa) | 302 recomendado no próprio blog | 302 padrão, configurável (MEDIUM) | 302 | **302 + `Cache-Control` sem cache** |
| Mesma URL longa → mesmo link | Sim, por conta (`force_new_link` para forçar outro) | Não | Opcional ("find if exists") | Não | **Não** (sem contas, dedupe mistura stats) |
| Link expirado | — | Expiração com URL de fallback | Tratado como inválido/404 | Expira por tempo | **410 Gone** |
| Edição de destino | Sim (autenticado) | Sim (autenticado) | Sim (API key) | Sim (autenticado) | **Não** (sem auth, links imutáveis) |
| Bots | — | Exclui Googlebot/Twitterbot das contagens, de forma silenciosa | Pode excluir bots das stats | — | **Grava `isBot` e exclui por padrão, com `includeBots=true` opcional** |
| IP | — | — | Anonimizado por padrão (`ANONYMIZE_REMOTE_ADDR=true`) | Não loga IP | **Hash com salt rotativo** |
| Orphan visits (404 rastreados) | — | — | Sim (`TRACK_ORPHAN_VISITS`) | — | Só métrica Prometheus `redirect{result="not_found"}`, sem persistir |
| GeoIP | Sim | Sim | Sim (GeoLite2) | Sim | **Não** (anti-feature no escopo) |
| Observabilidade operacional exposta | N/A (SaaS) | N/A (SaaS) | Métricas e health básicos | Básica | **Diferencial central:** métricas de domínio, dashboards, alertas, lag do pipeline |

Leitura: em **funcionalidades de produto** o Link Pulse fica deliberadamente abaixo dos concorrentes. O diferencial está em tornar a **operação visível e defensável** (cache, pipeline assíncrono, degradação, privacidade e alertas). É exatamente isso que o público-alvo avalia.

---

## Fontes

- Shlink, rastreamento de visitas (orphan visits, anonimização de IP, desativação granular): https://shlink.io/documentation/tracking-visits/ (oficial, MEDIUM)
- Shlink, variáveis de ambiente (`ANONYMIZE_REMOTE_ADDR`, `TRACK_ORPHAN_VISITS`, `DISABLE_TRACKING_FROM`): https://shlink.io/documentation/environment-variables/ (oficial, MEDIUM)
- Dub, 301 vs 302 para encurtadores: https://dub.co/blog/301-vs-302-redirect (MEDIUM)
- Dub, analytics sem bots e com dedupe: https://dub.co/blog/introducing-new-analytics (MEDIUM)
- 301 vs 302 em encurtadores, efeito no cache e na contagem: https://url-shortening.com/blog/301-vs-302-redirects-in-shorteners-speed-seo-and-caching (LOW, corroborado pela fonte do Dub → MEDIUM)
- Inflação de cliques por crawlers de preview: https://linklyhq.com/support/inflated-click-figures , https://www.flyn.to/blog/best-url-shorteners-bot-filtering (LOW. Percentuais são estimativas de fornecedores, tratar como ordem de grandeza)
- Bitly API v4 (`/v4/shorten`, mesma URL → mesmo bitlink, `force_new_link`): https://dev.bitly.com/api-reference/ , https://dev.bitly.com/docs/getting-started/troubleshooting-tips/ (oficial, MEDIUM)
- Kutt, funcionalidades (senha, expiração, stats sem IP): https://noted.lol/kutt/ , https://alternateoss.com/kutt/ (LOW)
- IETF RateLimit header fields (ainda draft) e `Retry-After`: https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-ratelimit-headers (padrão em andamento, MEDIUM)
- Spring Boot, liveness/readiness e health groups: https://spring.io/blog/2020/03/25/liveness-and-readiness-probes-with-spring-boot/ , https://www.trinitylogic.co.uk/blog/kubernetes-health-probes-spring-boot/ (oficial + comunidade, MEDIUM–HIGH)
- Validação de URL e SSRF (esquemas permitidos, parse em vez de regex): https://developer.mozilla.org/en-US/docs/Web/Security/Attacks/SSRF , https://snyk.io/blog/secure-javascript-url-validation/ (MEDIUM)
- Semântica HTTP de 302/404/410/429 e Problem Details: RFC 9110, RFC 6585 e RFC 9457 (conhecimento de padrão, HIGH)
- Structured logging nativo (Spring Boot 3.4+), `ProblemDetail` (Spring 6) e graceful shutdown: conhecimento de treinamento, **não verificado nesta sessão** (MEDIUM. Confirmar a versão exata do Boot na fase de stack)

---
*Pesquisa de funcionalidades para: encurtador de URLs com analytics (vitrine DevOps)*
*Pesquisado em: 2026-10-06*
