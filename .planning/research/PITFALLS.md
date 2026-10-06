# Pesquisa de Armadilhas (Pitfalls)

**Domínio:** Encurtador de URLs com analytics de cliques — projeto de portfólio backend com ênfase em DevOps/cloud (Spring Boot / Java 21, Postgres + Liquibase, MongoDB, Redis Cache/Streams/rate limit, Docker, kind + Helm + kube-prometheus-stack, GitHub Actions, Terraform AWS validado em LocalStack, k6)
**Pesquisado em:** 2026-10-06
**Confiança geral:** ALTA para armadilhas de mecanismo (Redis, JPA, Kubernetes, Docker, Micrometer). Confirmadas em fontes oficiais ou em issues públicas. ALTA para a mudança de licenciamento do LocalStack (tabela oficial de planos). MÉDIA para detalhes que dependem da versão escolhida (default do Spring Data Redis 4.x, UID do datasource no kube-prometheus-stack).

> **Convenção de fases:** o roadmap ainda não existe. As fases abaixo são referenciadas pelo **tema**, e o roadmapper deve mapeá-las para os números finais:
> **F-Fundação** (build, Postgres/Liquibase, Base62, qualidade estática) ·
> **F-Redirect** (redirect + cache Redis) ·
> **F-Cliques** (Streams + consumer + Mongo + stats) ·
> **F-RateLimit** ·
> **F-Container** (Dockerfile + Compose + Makefile) ·
> **F-Observabilidade** (Micrometer, dashboards, alertas) ·
> **F-K8s** (kind + Helm + kube-prometheus-stack + HPA) ·
> **F-CI** (GitHub Actions + GHCR + job kind) ·
> **F-Terraform** (AWS via LocalStack + plan) ·
> **F-Carga/Docs** (k6 + README).

---

## Armadilhas Críticas

### Armadilha 1: "Terraform validado contra LocalStack" é inviável no plano gratuito para ECS/RDS/ElastiCache/ECR/ALB

**O que dá errado:**
Desde a release 2026.03.0, a imagem `localstack/localstack` exige `LOCALSTACK_AUTH_TOKEN`. O plano gratuito (**Hobby**, uso não comercial) **não inclui ECS, RDS, ElastiCache, ECR nem ELBv2 (ALB)**. Esses serviços aparecem só a partir do plano Base, que é pago. Existe também um plano **Student**. O Hobby cobre EC2/VPC, IAM, CloudWatch Logs e Secrets Manager. Um `terraform apply` com `tflocal` vai falhar justamente nos recursos centrais do requisito. Tutoriais anteriores a 2026, que mostram `docker run localstack/localstack` sem token, não funcionam mais. O bypass temporário (`LOCALSTACK_ACKNOWLEDGE_ACCOUNT_REQUIREMENT=1`) expirou em 2026-04-06.

**Por que acontece:**
O requisito foi escrito com base no LocalStack Community "antigo". Mesmo antes de 2026, ECS, RDS e ElastiCache já eram recursos Pro. O PROJECT.md registra "ECS Fargate... melhor suporte no LocalStack" como justificativa, e essa premissa está desatualizada.

**Como evitar:**
- Redefinir o requisito de validação do Terraform em **três camadas, todas sem conta AWS e sem token pago**:
  1. `terraform fmt -check`, `terraform validate` e `tflint` (com o plugin `tflint-ruleset-aws`) no CI.
  2. **`terraform test` com `mock_provider "aws"`** (Terraform ≥ 1.7). Isso valida a lógica dos módulos (subnets, SGs, task definition, variáveis) offline, sem nenhuma chamada de API. É a validação real do ECS/RDS/ElastiCache.
  3. LocalStack Hobby (token gratuito, guardado como secret do CI) para **apply real só da camada de rede/IAM/logs/secrets** (VPC, subnets, SGs, IAM roles, log groups, Secrets Manager). Os recursos pagos ficam atrás de uma flag (`var.enable_compute = false`) no perfil LocalStack.
- Documentar no README, com transparência, o que foi aplicado no LocalStack e o que foi validado por mock/plan. Um avaliador valoriza mais isso do que uma alegação vaga.
- Alternativas sem token (Floci, MiniStack, fakecloud) surgiram em 2026, mas têm confiança BAIXA (projetos novos e cobertura não verificada). Ficam como spike opcional, nunca no caminho crítico.
- `terraform plan` "para AWS" exige credenciais. Sem conta, use `plan` com o provider apontando para o LocalStack, com `skip_credentials_validation`, `skip_requesting_account_id` e `skip_metadata_api_check`, e sem data sources que consultem ECS/RDS. Na prática, o mock provider é mais confiável.

**Sinais de alerta:**
`tflocal apply` retornando `501`, `not included within your LocalStack license` ou `InternalFailure` em `ecs:CreateCluster`/`rds:CreateDBInstance`. Container LocalStack encerrando com erro de auth token.

**Fase:** **F-Terraform**, e o requisito precisa ser ajustado já na definição do roadmap. Sinalizar a fase para pesquisa aprofundada.

---

### Armadilha 2: Alias customizado colide com um código Base62 gerado no futuro (ou com rotas da API)

**O que dá errado:**
Códigos gerados (Base62 do ID da sequência) e aliases customizados compartilham o mesmo namespace `/{code}`. Um usuário cria o alias `b` hoje. Quando a sequência chegar ao ID 37, o Base62 gera `b`. O resultado é uma violação de unicidade no insert (500) ou, pior, a sobrescrita do mapeamento no cache. Também existem aliases que colidem com rotas: `links`, `actuator`, `favicon.ico`, `health`, `swagger-ui`.

**Por que acontece:**
"Sem colisão" vale só entre códigos gerados. A unicidade do alias é verificada contra o que já existe, mas não contra o que a sequência **vai** gerar.

**Como evitar:**
- Separar os namespaces de forma estrutural. Opções: (a) alias exige comprimento mínimo maior que o dos códigos gerados num horizonte realista (ex.: alias ≥ 8 caracteres e código gerado ≤ 7) ou (b) alias aceita um caractere fora do alfabeto Base62, como `-` ou `_`. Recomendação: **(b), alias com regex `^[a-zA-Z0-9_-]{4,32}$` e pelo menos um `-`/`_`**, ou então (a) documentada. Mesmo assim, manter uma unique constraint em `code` e, ao gerar, fazer retry com o próximo `nextval` se ocorrer violação.
- Lista de palavras reservadas (`links`, `actuator`, `api`, `health`, `metrics`, `favicon.ico`, `robots.txt`) validada no POST.
- Teste de propriedade/unitário: gerar código para IDs 1..N e verificar que nenhum casa com a regex de alias.

**Sinais de alerta:**
`DataIntegrityViolationException` esporádica no `POST /links` sem alias. Testes de alias usando strings curtas.

**Fase:** **F-Fundação** (modelo de dados + gerador Base62).

---

### Armadilha 3: Desalinhamento entre `allocationSize` do Hibernate e o `INCREMENT BY` da sequência Liquibase (e o "ovo e galinha" do código)

**O que dá errado:**
O Hibernate 6 usa `allocationSize = 50` por padrão com o otimizador *pooled*. Se o changelog Liquibase cria a sequência com `incrementBy: 1`, o Hibernate falha na validação ou gera IDs que se sobrepõem entre instâncias, com chave duplicada sob carga e réplicas múltiplas. Há um segundo problema: o código depende do ID, que só existe após o insert. Isso leva a `code` nullable, dois writes (insert e depois update) e uma janela em que o link existe sem código.

**Por que acontece:**
Os defaults do JPA e o DDL escrito à mão no Liquibase não conversam. `spring.jpa.hibernate.ddl-auto=update` às vezes "conserta" localmente e mascara o problema.

**Como evitar:**
- Buscar o ID **antes** do insert: `SELECT nextval('link_id_seq')` num repositório próprio (ou `@GeneratedValue` com `@SequenceGenerator(allocationSize = 1)`), calcular o Base62 e fazer um único `INSERT` com `code NOT NULL UNIQUE`.
- Se usar o otimizador pooled, o `incrementBy` da sequência no Liquibase **tem que ser igual** ao `allocationSize`.
- `spring.jpa.hibernate.ddl-auto=validate` (nunca `update`/`create`) com Liquibase, para que o esquema venha só dos changelogs.
- Ofuscação opcional: Base62 de ID sequencial é **enumerável** (qualquer pessoa itera `/1`, `/2`... e descobre todas as URLs). Um mapeamento bijetivo barato, como multiplicar por um primo módulo 62^7 ou usar Sqids, resolve sem perder a ausência de colisão. Vale um parágrafo no README.

**Sinais de alerta:**
`SchemaManagementException` sobre o increment size da sequência. IDs saltando de 50 em 50. Coluna `code` nullable no changelog.

**Fase:** **F-Fundação**.

---

### Armadilha 4: Consumer do Redis Streams que para em silêncio, nunca dá ACK ou nunca recupera pendentes

**O que dá errado:**
Este é o conjunto de falhas mais comum com Streams:
1. **A assinatura é cancelada no primeiro erro.** No Spring Data Redis, por padrão, uma exceção cancela a `Subscription` do `StreamMessageListenerContainer`. Por exemplo, o Mongo fica fora por 2 s e o consumer **para para sempre** sem derrubar a aplicação. O lag cresce e o health continua UP. Há uma issue (#2919) para mudar esse default na versão 4.x; verifique o comportamento da versão escolhida.
2. **ACK antes de persistir** (auto-ack ou ack ao colocar no buffer em memória). O pod morre e os cliques do lote se perdem.
3. **Pending Entries List (PEL) órfã.** Com HPA, uma réplica é removida com mensagens entregues e sem ACK. Ninguém faz `XAUTOCLAIM`/`XCLAIM` e elas ficam pendentes eternamente.
4. **Nome de consumer fixo** (`consumer-1`) em todas as réplicas, o que mistura PELs e confunde métricas.
5. **Stream sem limite.** Sem `MAXLEN`/`XTRIM`, o stream cresce até estourar a memória do Redis, o mesmo Redis do cache e do rate limit.
6. **Grupo não criado.** `XREADGROUP` falha com `NOGROUP` no primeiro boot se ninguém executou `XGROUP CREATE ... MKSTREAM`, e o erro do item 1 cancela a assinatura.

**Por que acontece:**
Tutoriais mostram o "caminho feliz" com `autoAcknowledge(true)` e uma única instância.

**Como evitar:**
- `StreamReadRequest.builder(...).cancelOnError(t -> false)` e um `errorHandler` que loga e incrementa uma métrica de erro.
- ACK manual (`XACK`) **somente após** o `insertMany` no Mongo ter sucesso. A semântica é *at-least-once*.
- **Idempotência:** usar o ID da mensagem do stream como `_id` no Mongo, com `insertMany(..., ordered=false)`. Redeliveries geram duplicate key e o código ignora o erro 11000.
- Job agendado (`@Scheduled`, a cada 30 a 60 s) com `XAUTOCLAIM` de mensagens pendentes há mais de N segundos para o consumer atual.
- Nome de consumer = hostname do pod (`HOSTNAME`), que é estável por pod.
- `XADD ... MAXLEN ~ 100000` (trim aproximado) no producer.
- Criar o grupo na inicialização com `MKSTREAM`, tratando `BUSYGROUP` como sucesso.
- Expor gauges de **lag** (`XINFO GROUPS` → `lag`, Redis ≥ 7) e **pending**, e alertar sobre eles. Isso já é requisito ("lag do consumer").

**Sinais de alerta:**
Gauge de pending crescendo monotonicamente. Contagem de cliques no Mongo divergindo da métrica de redirects. Log com `Subscription cancelled`. Teste de integração que derruba o container Mongo e o consumer não volta.

**Fase:** **F-Cliques**. Os testes de integração devem incluir o cenário "Mongo indisponível → volta → nada perdido".

---

### Armadilha 5: O registro "assíncrono" do clique bloqueia ou derruba o redirect

**O que dá errado:**
- O `XADD` é feito de forma síncrona na thread do request. Com o Redis lento ou fora, o redirect fica lento ou retorna 500, e o redirect (valor central) passa a depender do pipeline de analytics.
- `@Async` em método da própria classe (auto-invocação) **não é assíncrono**, porque o proxy do Spring é contornado.
- `@Async` com o executor padrão sem limite, ou fila sem limite, gera OOM sob carga do k6.
- Se o cache e o stream estão no mesmo Redis e o Redis cai, o lookup também falha. Sem fallback para o Postgres, todo redirect quebra.

**Por que acontece:**
"Assíncrono" fica só no nome. Não existe um contrato explícito de degradação.

**Como evitar:**
- Definir o contrato: **o redirect nunca falha por causa do analytics**. A publicação do clique é *best-effort* com timeout curto (Lettuce `commandTimeout` de ~100 ms para o caminho do clique) e o erro vira métrica (`clicks_publish_failed_total`), não exceção.
- Publicar via `ThreadPoolTaskExecutor` com fila limitada e `DiscardPolicy` contabilizada, ou usar Lettuce async/reactive sem aguardar o futuro. Evitar auto-invocação de `@Async`.
- Leitura do cache com fallback: erro no Redis → consultar o Postgres → responder (com métrica de `cache_errors`).
- Medir: o p99 do redirect deve ser praticamente igual com e sem o consumer rodando. Isso entra no k6.

**Sinais de alerta:**
O p99 do `GET /{code}` sobe quando o Mongo ou o consumer estão lentos. Thread dumps com threads do Tomcat em `XADD`.

**Fase:** **F-Cliques** (contrato), validado em **F-Carga/Docs**.

---

### Armadilha 6: Cache Redis sem negative caching, com TTL errado ou serialização frágil

**O que dá errado:**
- **Cache penetration:** códigos inexistentes nunca são cacheados, então qualquer varredura (`/aaaa`, `/aaab`...) ou bot bate 100% no Postgres. Isso piora com códigos sequenciais enumeráveis.
- **Negative cache sem invalidação:** o 404 de `meu-alias` fica cacheado e, logo depois, alguém cria `meu-alias`. O link novo devolve 404 até o TTL expirar.
- **TTL ignora a expiração:** com TTL fixo de 24 h, um link que expira em 5 min continua redirecionando (302) em vez de 410.
- `@Cacheable` do Spring **não permite TTL por entrada**, e o default de `RedisCacheConfiguration` usa serialização JDK (binária, ilegível, quebra ao mudar a classe). Com Jackson, `Instant`/records sem `JavaTimeModule` e type info corretos geram `SerializationException` no primeiro hit.
- Hit e miss medidos errado: incrementar "hit" quando o valor é o sentinela de 404 distorce o hit ratio do dashboard.

**Como evitar:**
- Usar `StringRedisTemplate` diretamente (não `@Cacheable`) com valor simples (`url|expiresAtEpoch` ou JSON pequeno). TTL = `min(TTL_PADRÃO, expiresAt - agora)` e verificação de expiração **também na leitura**, porque o relógio do app e o TTL do Redis divergem.
- Negative caching com sentinela (`__NF__`) e TTL curto (30 a 60 s). **O `POST /links` faz `DEL` da chave** ao criar.
- Tags de métrica `result=hit|miss|negative_hit|error`.
- Jitter de ±10% no TTL para evitar expiração sincronizada (stampede). Com o volume deste projeto, um single-flight completo é exagero; o jitter basta.
- Link expirado: cachear um sentinela `__GONE__` para responder 410 sem tocar no banco.

**Sinais de alerta:**
Queries no Postgres por segundo ≈ requests por segundo durante o k6 com códigos aleatórios. Teste "criar link expirando em 2 s → aguardar 3 s → esperar 410" falhando.

**Fase:** **F-Redirect**.

---

### Armadilha 7: Redis compartilhado com política de eviction errada apaga o stream e o rate limit

**O que dá errado:**
Cache, stream e contadores de rate limit vivem no mesmo Redis. Com `maxmemory-policy allkeys-lru`, que é comum em templates de cache, o Redis **pode despejar a chave do stream** (perde cliques não consumidos e o consumer group) ou os contadores de rate limit. Com `noeviction`, quando a memória enche, até a escrita de cache falha.

**Como evitar:**
- `maxmemory-policy volatile-lru` (ou `volatile-ttl`): só chaves **com TTL** são candidatas a eviction. Cache e rate limit têm TTL; o stream não tem TTL, e seu limite vem do `MAXLEN`.
- Configurar o mesmo no Compose (`command: redis-server --maxmemory 256mb --maxmemory-policy volatile-lru`), no Helm e no **parameter group do ElastiCache** no Terraform.
- Alternativa documentável: databases/instâncias separadas. Para o portfólio, uma instância com `volatile-lru` e uma nota no README basta.

**Sinais de alerta:**
`evicted_keys > 0` em `INFO stats` e `NOGROUP` logo em seguida.

**Fase:** **F-Cliques** (decisão), replicado em **F-Container**, **F-K8s** e **F-Terraform**.

---

### Armadilha 8: Rate limit por IP que limita o proxy (ou que se burla com `X-Forwarded-For`)

**O que dá errado:**
- No kind/Ingress (e no ALB/ECS), `request.getRemoteAddr()` é o IP do ingress controller. **Todos os clientes compartilham um balde**, e um único usuário bloqueia todo mundo.
- O efeito contrário: confiar cegamente em `X-Forwarded-For` (pegar o primeiro valor) permite que qualquer um mande `X-Forwarded-For: 1.2.3.<random>` e escape do limite.
- `INCR` seguido de `EXPIRE` em dois comandos: se o processo morrer entre eles, a chave fica **sem TTL** e o IP fica bloqueado para sempre.
- O k6 roda tudo de um IP, então o teste de carga do `POST /links` vira um teste de 429.

**Como evitar:**
- `server.forward-headers-strategy=native` (o Tomcat `RemoteIpValve` usa o IP **mais à direita não confiável**) com `server.tomcat.remoteip.internal-proxies` restrito ao CIDR do cluster/pod network. Não ler `X-Forwarded-For` manualmente.
- Operação atômica: script Lua (`INCR` + `EXPIRE` se `== 1`) ou janela fixa com `SET key 0 EX 60 NX` + `INCR`. Também existe o Bucket4j com backend Lettuce, se preferir uma biblioteca.
- Headers `Retry-After` e `X-RateLimit-Remaining` no 429.
- Perfil/valor de limite configurável por env, e o cenário k6 de criação com o limite elevado (ou um IP por VU, simulado de forma confiável só em teste) e documentado.
- Teste de integração: enviar `X-Forwarded-For` falso de um IP não confiável e verificar que o header é ignorado.

**Sinais de alerta:**
No kind, um `curl` em loop gera 429 para outro terminal. Chaves `rl:*` com `TTL -1`.

**Fase:** **F-RateLimit**, revalidado em **F-K8s** (atrás do Ingress).

---

### Armadilha 9: Perda de cliques e de requisições no shutdown (SIGTERM ignorado, sem preStop, lote em memória)

**O que dá errado:**
- `ENTRYPOINT java ...` em **forma shell** (`sh -c`) faz o Java não ser PID 1 e não receber SIGTERM. O Kubernetes espera 30 s e manda SIGKILL, então não há graceful shutdown e o buffer do consumer é perdido.
- Sem `server.shutdown=graceful` e `spring.lifecycle.timeout-per-shutdown-phase`, requisições em voo são cortadas.
- Race clássica: o pod recebe SIGTERM **antes** de ser removido dos endpoints do Service/Ingress. Por alguns segundos ainda chega tráfego e dá 502/connection refused durante rollout e scale-down do HPA.
- O consumer acumula lote em memória e o container de listener é parado depois do Mongo client (ordem errada de shutdown dos beans).

**Como evitar:**
- `ENTRYPOINT ["java", ...]` em forma exec (JSON array). Se precisar de script, usar `exec java ...`.
- `server.shutdown=graceful` e `timeout-per-shutdown-phase=20s`. `terminationGracePeriodSeconds` maior que preStop + timeout (ex.: 45 s).
- `lifecycle.preStop` com sleep de ~5 a 10 s. O Kubernetes ≥ 1.30 tem a ação nativa `sleep` no preStop, sem depender de shell na imagem.
- Consumer implementando `SmartLifecycle` com fase adequada: parar a leitura, fazer flush do lote, `XACK` e só depois deixar o Mongo client fechar. Com ACK pós-persistência (Armadilha 4), o pior caso vira redelivery, não perda.

**Sinais de alerta:**
`kubectl rollout restart` durante o k6 gera erros. O pod leva exatamente 30 s para terminar (sinal de SIGKILL).

**Fase:** **F-Container** (entrypoint) e **F-K8s** (preStop/grace). A lógica do consumer fica em **F-Cliques**.

---

### Armadilha 10: Probes que causam reinícios em cascata (liveness dependente do Mongo/Redis/Postgres)

**O que dá errado:**
Colocar `db`, `redis` e `mongo` no grupo de **liveness**: se o Mongo cai, o Kubernetes reinicia **todos** os pods do app em loop, inclusive os que poderiam servir redirects pelo cache. Outro caso: uma migração Liquibase longa no startup, com liveness agressiva, mata o pod no meio da migração e deixa o `DATABASECHANGELOGLOCK` travado. Os próximos pods ficam presos em `Waiting for changelog lock....`

**Como evitar:**
- Liveness = `/actuator/health/liveness` (só `livenessState`, o default). **Nunca** incluir dependências externas.
- Readiness = `readinessState` + **apenas o Postgres**, que é a dependência sem a qual não existe redirect em cache miss. Mongo **não** deve tirar o pod de serviço, porque o analytics é assíncrono. Redis: avaliar; com fallback para o Postgres (Armadilha 5), também fica fora.
- `startupProbe` com `failureThreshold * periodSeconds` ≥ 120 s para cobrir JVM + Liquibase.
- Documentar como liberar o lock (`liquibase releaseLocks` ou `UPDATE databasechangeloglock SET locked=false`) no README de troubleshooting.
- Actuator na porta de management separada (ex.: 8081), não exposta no Ingress.

**Sinais de alerta:**
`kubectl get pods` com `RESTARTS` subindo ao parar o Mongo. Log `Waiting for changelog lock` após um crash.

**Fase:** **F-K8s** (probes/Helm), com a configuração dos health groups em **F-Fundação**.

---

### Armadilha 11: Liquibase contexts mal usados (seed de dev indo para prod, ou nada rodando)

**O que dá errado:**
- **Changesets sem `context` rodam em TODOS os contextos**, e se a aplicação não define nenhum contexto, **todos os changesets rodam, inclusive os marcados `context: dev`**. O profile prod sobe com dados de seed.
- O efeito contrário: marcar o DDL com `context: dev` faz com que, em prod, a tabela não seja criada.
- Editar um changeset já aplicado provoca falha de checksum (`Validation Failed: 1 changesets check sum`) em quem já tinha banco, como o volume do Compose do avaliador.
- Liquibase 5.0 (set/2025) mudou para licença **FSL** e passou a ser distribuído sem drivers/extensões. Subir manualmente para 5.x fora do BOM do Spring Boot pode quebrar a integração.

**Como evitar:**
- Regra: **DDL nunca tem context**. Só dados de seed/demo têm `context: dev`. `spring.liquibase.contexts` é **explícito em todos os profiles** (`dev` no Compose/kind dev, `prod` no values prod e no ECS).
- Changesets imutáveis: mudanças sempre em novo changeset. `make clean` remove volumes para dev.
- Usar a versão do Liquibase gerenciada pelo BOM do Spring Boot escolhido. Não sobrescrever.
- Teste de integração rodando com `contexts=prod` e verificando que a tabela de seed está vazia.

**Sinais de alerta:**
Os links de demo aparecem no ambiente "prod" do kind. Arquivo de changelog com `context` no `createTable`.

**Fase:** **F-Fundação**.

---

### Armadilha 12: Ingress no kind (controller retirado, falta de `extraPortMappings`, imagem não encontrada)

**O que dá errado:**
- **O ingress-nginx (kubernetes/ingress-nginx) chegou ao EOL em 2026-03-24**: repositório read-only, sem patches de CVE. Muitos tutoriais de kind (e a própria doc histórica do kind) ainda o usam. Um avaliador DevOps em 2026 vai notar.
- Cluster kind criado sem `extraPortMappings` (80/443) e sem o label `ingress-ready=true`: o Ingress existe, mas `curl localhost` não chega. No Windows, a porta 80 pode estar ocupada (IIS, Skype antigo, outros serviços), gerando o erro "port is already allocated".
- `kind load docker-image app:latest` + `imagePullPolicy: Always` (o default para `:latest`) faz o kubelet tentar puxar do Docker Hub, resultando em `ErrImagePull`.

**Como evitar:**
- Escolher um controller mantido: **Traefik** (Helm, suporta o recurso `Ingress` padrão, e o requisito pede Ingress) ou **Envoy Gateway/Contour** se quiser mostrar Gateway API. Recomendação: **Traefik com `Ingress` padrão** (menor esforço e cumpre o requisito). Mencionar Gateway API como evolução no README.
- `kind-config.yaml` versionado com `extraPortMappings` (hostPort configurável, ex.: 8080, para evitar conflito no Windows) e `nodeSelector` do controller.
- Tag de imagem explícita (SHA do git ou `dev`) + `imagePullPolicy: IfNotPresent` no values dev.

**Sinais de alerta:**
`curl http://localhost` com timeout e o controller `Pending` por nodeSelector. Pod em `ErrImagePull` logo após `kind load`.

**Fase:** **F-K8s**. Sinalizar para pesquisa (escolha do controller).

---

### Armadilha 13: kube-prometheus-stack não "enxerga" o ServiceMonitor, a PrometheusRule e os dashboards

**O que dá errado:**
- Por padrão, o Prometheus do kube-prometheus-stack só seleciona `ServiceMonitor`/`PrometheusRule` com o label `release: <nome-do-release>` (`serviceMonitorSelectorNilUsesHelmValues: true`). O ServiceMonitor do app existe, mas o target nunca aparece em `/targets`, e os alertas "existem" mas nunca carregam.
- O `ServiceMonitor` referencia a porta pelo **nome** (`port: http-management`). Se o Service não nomeia a porta, ou a porta de management não está no Service, não há scrape.
- O chart do app com `ServiceMonitor` falha no `helm install` em clusters sem a CRD (`no matches for kind "ServiceMonitor"`), por exemplo no job de CI se o kube-prometheus-stack não for instalado.
- Os dashboards só são importados pelo sidecar do Grafana se o ConfigMap tiver o label `grafana_dashboard: "1"` e estiver em um namespace observado.

**Como evitar:**
- Em `values` do kube-prometheus-stack: `prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false`, `ruleSelectorNilUsesHelmValues=false` e `podMonitorSelectorNilUsesHelmValues=false`. Alternativamente, o label `release` parametrizado no chart do app.
- Template condicional: `{{- if and .Values.metrics.serviceMonitor.enabled (.Capabilities.APIVersions.Has "monitoring.coreos.com/v1") }}`.
- `sidecar.dashboards.searchNamespace: ALL`, ou dashboards no mesmo namespace do Grafana.
- O smoke test do `make k8s` consulta a API do Prometheus (`/api/v1/targets`) e falha se o target do app não estiver `up`.

**Sinais de alerta:**
`up{job=~".*link-pulse.*"}` vazio. Dashboards com "No data" mesmo com tráfego.

**Fase:** **F-K8s** e **F-Observabilidade**.

---

### Armadilha 14: Dashboards Grafana JSON não portáveis entre Compose e kind

**O que dá errado:**
O dashboard exportado pelo Grafana referencia o datasource por **UID gerado** (`"uid": "P1809F7CD0C75ACF3"`) ou por `${DS_PROMETHEUS}` (export "for sharing externally", que só funciona no import manual pela UI, **não** em provisioning). No Compose, o UID é um; no kube-prometheus-stack, outro. Resultado: "Datasource not found" em um dos dois ambientes.

**Como evitar:**
- Fixar o UID do datasource nos dois ambientes. No Compose, `grafana/provisioning/datasources/*.yaml` com `uid: prometheus`. No kube-prometheus-stack, o datasource padrão do chart já usa `uid: prometheus` (configurável via `grafana.sidecar.datasources`; **verificar na versão do chart**, confiança MÉDIA).
- No JSON, `"datasource": {"type": "prometheus", "uid": "prometheus"}` em todos os painéis, ou uma variável de template `datasource` do tipo `prometheus`.
- Uma única fonte de verdade: `observability/dashboards/*.json`, consumida pelo provisioning do Compose (volume) e por um ConfigMap gerado no Helm (`.Files.Glob`). Nada de cópias divergentes.
- Lint no CI: `jq` verificando que nenhum `uid` de datasource difere de `prometheus`.

**Sinais de alerta:**
Painéis com triângulo vermelho em um ambiente só. `__inputs` no topo do JSON.

**Fase:** **F-Observabilidade**.

---

### Armadilha 15: Explosão de cardinalidade e percentis que não agregam

**O que dá errado:**
- Usar o código curto, a URL de destino, o IP ou o user-agent como **tag** do Micrometer (`clicks_total{code="abc"}`). Cada link vira uma série temporal e o Prometheus degrada rapidamente com o k6 criando milhares de links.
- Um filtro/métrica customizada usando `request.getRequestURI()` em vez do padrão da rota cria uma série por código.
- `management.metrics.distribution.percentiles=0.95,0.99` calcula percentis **no cliente**, por pod, e **não é agregável** entre réplicas. O p99 "do serviço" no dashboard fica matematicamente errado com o HPA escalando.
- `rate(x[1m])` com scrape interval de 30 s (o default do kube-prometheus-stack) dá painéis vazios ou serrilhados. A janela precisa ser ≥ 4× o scrape interval.

**Como evitar:**
- Tags só de baixa cardinalidade: `result`, `outcome`, `status`, `method`, `uri` (padrão `/{code}`, como o Spring MVC já faz). Analytics por código fica **no Mongo**, nunca no Prometheus.
- `management.metrics.distribution.percentiles-histogram.http.server.requests=true` + SLOs (`slo=50ms,100ms,250ms,500ms`) e, no PromQL, `histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{uri="/{code}"}[5m])))`.
- Usar `$__rate_interval` nos painéis do Grafana.
- Alertas com `for: 5m` e janelas adequadas. O alerta de latência p99 usa o histogram.

**Sinais de alerta:**
`prometheus_tsdb_head_series` crescendo linearmente durante o k6. Painel de p99 mostrando várias linhas (uma por pod) ou valores impossíveis.

**Fase:** **F-Observabilidade** (no desenho das métricas em **F-Redirect**/**F-Cliques**, ao criá-las).

---

### Armadilha 16: Imagem non-root com USER por nome, layered jar no formato antigo e HEALTHCHECK sem ferramenta

**O que dá errado:**
- `USER spring` (nome) + `securityContext.runAsNonRoot: true` no Helm gera `CreateContainerConfigError: container has runAsNonRoot and image has non-numeric user (spring), cannot verify user is non-root`.
- Dockerfile copiado de tutorial com `-Djarmode=layertools` (depreciado no Boot 3.3, **removido no 4.1**) e `ENTRYPOINT org.springframework.boot.loader.JarLauncher` (classe que mudou para `org.springframework.boot.loader.launch.JarLauncher` no Boot 3.2) gera `ClassNotFoundException` no start.
- `HEALTHCHECK CMD curl ...` numa imagem JRE sem curl (distroless, ou slim sem o pacote) deixa o container eternamente `unhealthy`. Além disso, **o Kubernetes ignora o HEALTHCHECK do Docker**, que só tem efeito no Compose.
- `readOnlyRootFilesystem: true` sem `emptyDir` em `/tmp` faz o Tomcat falhar ao criar o diretório de trabalho.
- JVM sem `-XX:MaxRAMPercentage`: o default de 25% do limite de memória desperdiça RAM, ou, com limite baixo, causa OOMKill por metaspace/threads acima do heap.

**Como evitar:**
- `RUN useradd -u 10001 ...` / `USER 10001:10001` e `runAsUser: 10001` no Helm.
- `java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted` e `ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]` (ou o modo `--launcher` documentado na versão escolhida). Conferir em `spring-boot/reference/packaging/container-images/dockerfiles`.
- HEALTHCHECK sem dependências externas: usar `wget`/`curl` só se a imagem base os tiver (verificar). Em distroless, não usar HEALTHCHECK e delegar a saúde ao `depends_on: condition` do Compose via outro mecanismo, ou escolher base `eclipse-temurin:21-jre` com curl instalado. Explicar no README que no k8s quem vale são as probes.
- `emptyDir` para `/tmp`. `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"`.
- Verificação no CI: `docker run --rm --user 10001 img id -u` e `docker inspect` checando `Config.User`, mais `trivy`/`hadolint` (bônus de portfólio).

**Sinais de alerta:**
Pod em `CreateContainerConfigError`. `docker ps` mostrando `(unhealthy)` com o app funcionando.

**Fase:** **F-Container** (e `securityContext` em **F-K8s**).

---

### Armadilha 17: HPA no kind que nunca escala (ou escala no startup da JVM)

**O que dá errado:**
- O kind não vem com **metrics-server**. Instalado sem `--kubelet-insecure-tls`, ele fica `CrashLoop`/sem métricas e o HPA mostra `<unknown>/70%` para sempre.
- Deployment sem `resources.requests.cpu` deixa o HPA de CPU sem base de cálculo, gerando `missing request for cpu`.
- O pico de CPU do startup da JVM (JIT + Liquibase + contexto Spring) dispara scale-up a cada novo pod, causando *flapping*.
- `replicas:` fixo no Deployment junto com HPA faz cada `helm upgrade` resetar a contagem.

**Como evitar:**
- `make k8s` instala o metrics-server via Helm com `args: [--kubelet-insecure-tls]` e espera `kubectl top nodes` responder.
- `requests` (CPU e memória) obrigatórios no values. `limits` de memória sim; `limits` de CPU com cautela (o throttling piora a latência da JVM).
- HPA `autoscaling/v2` com `behavior.scaleUp.stabilizationWindowSeconds` (~60 s) e `scaleDown` (~300 s). O readiness/startup probe segura o tráfego até a JVM aquecer.
- Omitir `spec.replicas` no Deployment quando `hpa.enabled=true`.
- Não usar prometheus-adapter/métricas customizadas no HPA neste escopo: é complexo e frágil. CPU basta para a demonstração, e o k6 pode demonstrar o HPA escalando (print no README).

**Sinais de alerta:**
`kubectl get hpa` com `<unknown>`. Réplicas subindo a cada rollout.

**Fase:** **F-K8s**, demonstrado em **F-Carga/Docs**.

---

### Armadilha 18: Ambiente Windows quebra o "clone e `make up`" (CRLF, make ausente, caminho com espaços)

**O que dá errado:**
- **O Git Bash não inclui GNU make.** `make up` dá "command not found" para avaliadores no Windows.
- **O caminho do projeto tem espaços** (`C:/Users/Lucas Lopes/.../projeto 4`). `$(PWD)`/`$(CURDIR)` sem aspas em Makefile, em volumes do Docker (`-v $(PWD)/x:/x`) e em scripts quebram em pedaços.
- `core.autocrlf=true` converte `.sh`, `mvnw` e arquivos de entrypoint para CRLF: `/bin/sh^M: bad interpreter` no container e no runner Linux.
- `mvnw` commitado do Windows **sem bit executável** gera `Permission denied` no GitHub Actions.
- Docker Desktop/WSL2 com memória padrão é insuficiente para kind + kube-prometheus-stack + Postgres + Mongo + Redis + app (precisa de ~6 a 8 GB). Pods ficam `Pending`/OOMKilled.
- Testcontainers no Windows: **Docker Engine 29 elevou a versão mínima da API**, e Testcontainers < 1.21.4 / < 2.0.2 falha com `client version 1.32 is too old`.

**Como evitar:**
- `.gitattributes` com `* text=auto eol=lf` (e `*.cmd text eol=crlf`). `git update-index --chmod=+x mvnw scripts/*.sh`.
- Makefile como **fino invólucro**: cada alvo chama um script `scripts/*.sh` com `set -euo pipefail` e caminhos sempre entre aspas. Documentar a instalação do make (`winget install ezwinports.make` / `choco install make` / usar WSL) e oferecer o comando equivalente sem make no README.
- Testar `make up` a partir de um diretório **com espaço no nome** (o seu próprio caminho já serve como teste).
- README com requisitos mínimos de memória e um `.wslconfig` de exemplo. Perfil "lite" do `make k8s` sem Alertmanager/node-exporter, se necessário.
- Testcontainers ≥ 2.0.2 (ou ≥ 1.21.4 na linha 1.x). Atenção: a 2.x renomeou artefatos (`testcontainers-postgresql` etc.).

**Sinais de alerta:**
O CI falha em passos que passam localmente, ou vice-versa. `^M` em mensagens de erro.

**Fase:** **F-Fundação** (gitattributes, mvnw, Testcontainers) e **F-Container** (Makefile/scripts).

---

### Armadilha 19: Pipeline do GitHub Actions que publica no GHCR mas o avaliador não consegue puxar

**O que dá errado:**
- **O nome da imagem precisa ser minúsculo.** `ghcr.io/${{ github.repository }}` com dono/repo contendo maiúsculas gera `repository name must be lowercase`.
- Falta `permissions: packages: write` no job, resultando em `denied: installation not allowed to Create organization package`/403.
- **Pacotes no GHCR nascem privados.** O values do Helm aponta para o GHCR e o `make k8s` do avaliador falha com `ImagePullBackOff`.
- O job de kind no CI puxa a imagem do GHCR, criando uma dependência de ordem e de credencial. Em PRs de forks não há push nem secret.
- O kube-prometheus-stack completo no runner deixa o job lento e instável (os runners públicos têm 4 vCPU / 16 GB, mas o tempo de instalação + CRDs grandes aumentam o flakiness).

**Como evitar:**
- Usar `docker/metadata-action` (que normaliza para minúsculas) ou `${GITHUB_REPOSITORY,,}`.
- `permissions: { contents: read, packages: write }` só no job de publish. Publicar apenas em `push` na `main`/tags.
- Tornar o pacote público manualmente uma vez (Package settings → Change visibility) e documentar isso.
- Job kind: `docker build` local + `kind load docker-image` (não depende do GHCR). Instalar **somente as CRDs** do prometheus-operator, ou desligar o ServiceMonitor via values. Smoke test: `POST /links` → `GET /{code}` com `curl -s -o /dev/null -w '%{http_code}'` esperando **302 sem seguir redirect**, e `GET /stats` com retry até o consumer processar.
- Usar `helm/kind-action` e fixar a versão do node image do kind.

**Sinais de alerta:**
Push funcionando no seu fork mas não em PR. `ImagePullBackOff` em máquina limpa.

**Fase:** **F-CI**.

---

## Padrões de Dívida Técnica

Atalhos que parecem razoáveis mas criam problemas.

| Atalho | Benefício imediato | Custo de longo prazo | Quando é aceitável |
|--------|-------------------|----------------------|--------------------|
| `ddl-auto=update` "só em dev" | Itera sem escrever changelog | Esquema real diverge do Liquibase; prod quebra | Nunca (o Liquibase é requisito) |
| `@Cacheable` em vez de `RedisTemplate` | Menos código | Sem TTL por entrada, sem negative cache controlado, serialização frágil | Nunca para o lookup do redirect |
| Auto-ack no consumer | Menos código | Perda de cliques no crash/shutdown | Nunca |
| Imagens Bitnami (`bitnami/postgresql`, `bitnami/redis`) no Compose/kind | Charts prontos | Desde 28/08/2025 as imagens foram movidas para `bitnamilegacy` (congeladas, CVEs acumulando) | Nunca; usar as imagens oficiais (`postgres`, `redis`/`valkey`, `mongo`) e manifests/charts simples próprios para dependências no kind |
| Uma única `values.yaml` com `if env == prod` | Menos arquivos | Values ilegíveis; o avaliador não vê a diferença dev/prod | Nunca; usar `values-dev.yaml`/`values-prod.yaml` sobre um base |
| Secrets em texto no `values.yaml` / `terraform.tfvars` commitado | Funciona de primeira | Credencial no histórico do git; o avaliador nota | Só senhas de dev óbvias (`dev/dev`) para Compose/kind; em prod usar `existingSecret` + `manage_master_user_password` no RDS |
| `terraform.tfstate` local commitado | Simplicidade | Vaza dados; demonstra má prática | Nunca; `.gitignore` + backend S3 documentado (comentado) |
| Testcontainers com um container novo por classe de teste | Isolamento | Suíte lenta (minutos) no CI e muitos contextos Spring | Usar o padrão singleton container + `@ServiceConnection` |
| Pular o SpotBugs/Checkstyle até o final | Velocidade inicial | Centenas de violações no fim (`EI_EXPOSE_REP` em records/entidades) | Nunca; ligar na F-Fundação com o filtro de exclusões definido |
| Escolher o Spring Boot 3.5 sem nota | Segue a restrição "3.x" | **O suporte OSS da 3.5 terminou em 30/06/2026**; um avaliador atento vê "EOL" | Aceitável se o README justificar; sinalizar ao autor a opção 4.x (decisão do STACK) |

## Pegadinhas de Integração

| Integração | Erro comum | Abordagem correta |
|------------|-----------|-------------------|
| Spring Data MongoDB | `@Indexed`/`@CompoundIndex` nas entidades, assumindo que os índices são criados (**`auto-index-creation` é `false` por padrão** desde o Spring Data MongoDB 3.0) | Criar os índices explicitamente na inicialização (`MongoTemplate.indexOps`) ou via script: `{code:1, ts:1}` para as aggregations |
| MongoDB aggregation "cliques por dia" | `$dateToString` em UTC e o avaliador brasileiro vê o dia "errado" após as 21h | `$dateTrunc: {date: "$ts", unit: "day", timezone: <param>}`, com timezone configurável (default `America/Sao_Paulo`) e documentado |
| MongoDB crescimento | Coleção de cliques cresce sem limite | Índice TTL (ex.: 90 dias) ou time-series collection; citar no README |
| Redis Streams + Spring | `XREADGROUP` antes do `XGROUP CREATE` | Criar o grupo com `MKSTREAM` no startup, ignorando `BUSYGROUP` |
| Compose `depends_on` | Sem `condition: service_healthy`, o app sobe antes do Postgres e falha no Liquibase | Healthchecks em postgres (`pg_isready`), mongo (`mongosh --eval "db.adminCommand('ping')"`) e redis (`redis-cli ping`), com `condition: service_healthy` |
| Testcontainers + Spring Boot | `@DynamicPropertySource` repetido e containers por classe | `@ServiceConnection` (Boot ≥ 3.1) em uma classe base/`@TestConfiguration` compartilhada |
| ElastiCache (Terraform) | Ativar `transit_encryption_enabled` e esquecer `spring.data.redis.ssl.enabled=true` no app | Variável de ambiente na task definition alinhada com o recurso |
| ECS Fargate (Terraform) | Task em subnet privada sem NAT/VPC endpoints não puxa a imagem do ECR nem envia logs | Documentar a escolha: NAT gateway (custo) ou VPC endpoints (ECR api/dkr, S3, Logs); a execution role precisa de `AmazonECSTaskExecutionRolePolicy` |
| ECS + ALB | Health check do target group em `/` (que é `/{code}` e retorna 404) | `/actuator/health/readiness` na porta de management, ou path dedicado |
| AWS sem MongoDB | Arquitetura AWS "completa" sem o banco de cliques: a task não fica saudável se depender do Mongo | Documentar DocumentDB/Atlas como opção e garantir que o app sobe sem Mongo (readiness não depende dele; Armadilha 10). A compatibilidade do DocumentDB com `$dateTrunc` deve ser verificada se for proposta |
| Terraform + LocalStack | Endpoints do LocalStack hardcoded no `provider "aws"` principal | Arquivo `providers_localstack.tf` via override, ou o wrapper `tflocal`; a configuração "AWS real" permanece limpa |

## Armadilhas de Performance

| Armadilha | Sintomas | Prevenção | Quando quebra |
|-----------|----------|-----------|---------------|
| k6 seguindo redirects | O k6 mede a latência do **site de destino** (e faz carga num terceiro, ex.: example.com) | `options.maxRedirects = 0` e `check(r => r.status === 302)` | Imediatamente, em qualquer teste |
| k6 via `kubectl port-forward` | Throughput limitado a centenas de req/s e erros de stream; os números não refletem o app | Bater no Ingress (`localhost:<hostPort>`) ou rodar o k6 como Job no cluster | > ~200 req/s |
| k6 na mesma máquina que kind + toda a stack | CPU disputada; p99 inflado | Documentar o hardware; reportar números relativos (hit vs miss, 1 vs N réplicas) em vez de absolutos | Sempre em laptop; tratar como contexto, não como bug |
| Cache frio vs quente misturado | p99 alto e enganoso | Cenários separados: warm-up + cenário "hot set" (Zipf) + cenário "códigos aleatórios" (miss/negative) | Na interpretação dos resultados |
| Lookup no Postgres sem índice único em `code` | Seq scan em todo cache miss | `UNIQUE` no changelog (que já cria o índice) | Dezenas de milhares de links |
| Pool Hikari padrão (10) com HPA escalando | `too many connections` no Postgres (max 100) com N réplicas | `maximum-pool-size` explícito × `maxReplicas` < `max_connections` | ~10 réplicas |
| `insertMany` de 1 documento por vez | Throughput do consumer menor que a taxa de cliques; o lag cresce indefinidamente | Lote por `XREADGROUP COUNT 100..500` + `BLOCK` | Algumas centenas de cliques/s |
| Virtual threads (Java 21) + `synchronized` em drivers | Pinning de carrier threads e latência travada | Se ativar `spring.threads.virtual.enabled`, medir com o k6; no Java 21 o pinning em `synchronized` ainda existe (resolvido no JDK 24) | Carga alta com I/O bloqueante |

## Erros de Segurança

| Erro | Risco | Prevenção |
|------|-------|-----------|
| Aceitar qualquer string como URL de destino | `javascript:`, `data:`, `file:` viram um vetor de XSS/phishing via o seu domínio | Validar `URI` com scheme ∈ {http, https}, host presente e tamanho máximo (ex.: 2048) |
| Encurtar URLs do próprio domínio | Loops de redirect e cadeias de ofuscação | Rejeitar o host do próprio serviço (configurável) |
| Armazenar o IP cru do clicador no Mongo | Dado pessoal sob a **LGPD**; em um projeto pt-BR o avaliador pode questionar | Gravar só o hash do IP com salt rotativo ou o IP truncado (/24, /48), e não persistir o IP no rate limit além do TTL. Documentar no README |
| Confiar em `X-Forwarded-For` | Burlar o rate limit e falsificar o analytics | Armadilha 8: `RemoteIpValve` com proxies confiáveis |
| Expor `/actuator/**` pelo Ingress/ALB | `env`, `heapdump` e `configprops` vazam segredos | Porta de management separada, `exposure.include=health,info,prometheus` e sem rota de Ingress para ela |
| Códigos sequenciais enumeráveis | Raspagem de todas as URLs encurtadas | Ofuscação bijetiva (Armadilha 3) ou documentar como limitação conhecida |
| Stats públicas sem autenticação | Qualquer um vê os referrers/UAs de qualquer link | Aceitável no escopo (sem auth); declarar no README como decisão consciente |
| Secrets do LocalStack/GHCR em logs do CI | Vazamento de token | Usar `secrets.*` e nunca `echo`; o token do LocalStack Hobby como secret |

## Armadilhas de UX (da API)

| Armadilha | Impacto | Abordagem melhor |
|-----------|---------|------------------|
| Usar 301 "porque é mais rápido" | Navegadores cacheiam o 301 indefinidamente: cliques seguintes não chegam ao servidor (analytics subestimado) e a expiração/410 nunca é vista | 302 (já decidido) **+ `Cache-Control: private, max-age=0, no-cache`**, e documentar o trade-off 301 vs 302 no README (bom ponto de discussão) |
| Contar HEAD, bots e pré-visualizações (Slack/WhatsApp unfurl, prefetch) como cliques | Analytics inflado | O Spring mapeia HEAD para o handler GET; não publicar clique em HEAD e marcar UAs de bot conhecidos (campo `bot: true`) em vez de descartar |
| 410 sem corpo explicativo | O cliente não distingue expirado de removido | Corpo `application/problem+json` (RFC 9457; o Spring Boot 3 tem `spring.mvc.problemdetails.enabled`) |
| `POST /links` retorna só o código | O cliente precisa montar a URL | Retornar `shortUrl` completa (base URL configurável via `app.base-url`, diferente em Compose/kind/ECS) + `Location` header + 201 |
| Stats inconsistentes logo após o clique | O avaliador clica e as stats mostram 0 | Documentar a consistência eventual (segundos); o smoke test usa retry |
| Erros de validação genéricos (400 sem detalhe) | Difícil de usar via curl | Problem Details com `errors[]` por campo |

## Checklist "Parece Pronto, Mas Não Está"

- [ ] **Redirect:** verificar `Cache-Control` no 302, 410 para expirado **mesmo com cache quente**, e 404 com negative cache invalidado na criação.
- [ ] **Consumer de cliques:** derrubar o Mongo por 30 s durante o tráfego, religar e confirmar que **contagem no Mongo = redirects publicados** (sem perda, sem duplicata).
- [ ] **Consumer:** matar o pod com `kubectl delete pod` no meio do lote; as mensagens pendentes são reclamadas via `XAUTOCLAIM` por outra réplica.
- [ ] **Rate limit:** funciona atrás do Ingress (dois clientes distintos não compartilham o balde) e `X-Forwarded-For` forjado é ignorado.
- [ ] **Liquibase:** subir com `contexts=prod` e confirmar a ausência de dados de seed; subir duas vezes sem erro de checksum.
- [ ] **Mongo:** `db.clicks.getIndexes()` mostra o índice composto (não apenas `_id`).
- [ ] **Imagem:** `docker inspect` mostra `User` numérico; o container responde a SIGTERM em menos de 30 s; o layered jar sobe com a classe de launcher correta.
- [ ] **Helm:** `helm template` sem a CRD do Prometheus não falha; `helm lint` com `values-dev` e `values-prod`.
- [ ] **Observabilidade:** o target do app está `up` em `/targets` do Prometheus do kube-prometheus-stack; os dashboards mostram dados **no Compose e no kind** com o mesmo JSON.
- [ ] **Alertas:** a PrometheusRule aparece em `/rules` e um alerta foi disparado de propósito (ex.: parar o consumer → alerta de lag) e capturado em print.
- [ ] **HPA:** `kubectl get hpa` mostra uma porcentagem real (não `<unknown>`) e há evidência de scale-up sob k6.
- [ ] **CI:** a imagem no GHCR é pública e puxável sem login; o job kind passa em PR sem secrets.
- [ ] **Terraform:** `terraform test` (mock) passa no CI; o README diz claramente o que foi aplicado no LocalStack e o que só foi validado.
- [ ] **k6:** `maxRedirects: 0`; cenários de hit e miss separados; hardware documentado.
- [ ] **Windows:** `make up` funciona a partir de um caminho com espaços e de um clone limpo com `autocrlf=true`.

## Estratégias de Recuperação

| Armadilha | Custo de recuperação | Passos |
|-----------|---------------------|--------|
| LocalStack sem ECS/RDS/ElastiCache | BAIXO se detectado cedo | Adotar `terraform test` + mock provider e restringir o apply do LocalStack à rede/IAM; reescrever o critério de aceite |
| Colisão alias × código gerado em produção | MÉDIO | Migration adicionando uma regra de alias; renomear aliases conflitantes; retry na geração |
| `allocationSize` × sequência | BAIXO | Ajustar `@SequenceGenerator(allocationSize=1)` ou um novo changeset alterando o `INCREMENT BY`; nunca editar o changeset antigo |
| Consumer cancelado / PEL órfã | BAIXO | `cancelOnError(t -> false)`; `XAUTOCLAIM` manual uma vez; os cliques continuam no stream (se não houve trim) |
| Stream despejado pela eviction | ALTO (dados perdidos) | Mudar para `volatile-lru`; recriar o grupo; aceitar a perda e documentar |
| Lock do Liquibase travado | BAIXO | `UPDATE databasechangeloglock SET locked=false, lockgranted=null, lockedby=null WHERE id=1` + ajustar o startupProbe |
| Cardinalidade explodida | MÉDIO | Remover a tag; `DELETE` de séries via API admin ou recriar o volume do Prometheus |
| Dashboards com UID quebrado | BAIXO | `jq` para substituir o UID por `prometheus` em todos os JSONs |
| ingress-nginx já adotado | BAIXO | Trocar o `ingressClassName` para traefik; sem anotações específicas do nginx não há mais nada a mudar |

## Mapeamento Armadilha → Fase

| Armadilha | Fase de prevenção | Verificação |
|-----------|-------------------|-------------|
| 1. LocalStack Hobby sem ECS/RDS/ElastiCache | F-Terraform (ajustar o requisito no roadmap) | `terraform test` com mock verde no CI; apply LocalStack da rede verde |
| 2. Alias × código gerado / rotas | F-Fundação | Teste unitário regex × Base62(1..N); lista de reservados testada |
| 3. Sequência/allocationSize, enumeração | F-Fundação | `ddl-auto=validate` sobe; teste concorrente com N threads sem duplicata |
| 4. Consumer Streams (ACK, PEL, cancel) | F-Cliques | Teste de integração "Mongo cai e volta" e "pod morre com pendentes" |
| 5. Assíncrono bloqueando o redirect | F-Cliques / F-Carga | Teste com Redis do stream lento; p99 estável no k6 |
| 6. Cache (negativo, TTL, serialização) | F-Redirect | Testes 404→criar→302; expira→410 com cache quente |
| 7. Eviction do Redis | F-Cliques (+ Container/K8s/Terraform) | `CONFIG GET maxmemory-policy` = `volatile-lru` em todos os ambientes |
| 8. Rate limit atrás de proxy / atomicidade | F-RateLimit / F-K8s | Teste com XFF forjado; nenhuma chave `rl:*` com TTL -1 |
| 9. Shutdown (PID 1, preStop, flush) | F-Container / F-K8s / F-Cliques | `rollout restart` durante o k6 sem 5xx; término < 30 s |
| 10. Probes e lock do Liquibase | F-Fundação / F-K8s | Parar o Mongo → zero restarts; readiness só cai com o Postgres |
| 11. Liquibase contexts | F-Fundação | Teste com `contexts=prod` sem seed |
| 12. Ingress no kind (EOL nginx, portas, imagem) | F-K8s | `curl localhost:<porta>/<code>` → 302 após `make k8s` limpo |
| 13. Seletores do kube-prometheus-stack | F-K8s / F-Observabilidade | Smoke test consulta `/api/v1/targets` |
| 14. UID do datasource Grafana | F-Observabilidade | Lint `jq` no CI; dashboards com dados nos dois ambientes |
| 15. Cardinalidade e percentis | F-Observabilidade (desenho em F-Redirect/F-Cliques) | `count by (__name__)` estável após o k6; p99 via `histogram_quantile` |
| 16. Imagem non-root, layered jar, healthcheck | F-Container | `USER` numérico; pod sobe com `runAsNonRoot`; `docker ps` healthy |
| 17. HPA no kind | F-K8s / F-Carga | `kubectl get hpa` com valores reais; print de scale-up |
| 18. Windows (make, CRLF, espaços, Docker 29) | F-Fundação / F-Container | Clone limpo no Windows com caminho com espaço → `make up` verde |
| 19. GHCR (minúsculas, permissions, visibilidade) | F-CI | `docker pull` anônimo funciona; o job kind passa em PR |

**Fases que merecem pesquisa aprofundada:**
- **F-Terraform:** o licenciamento do LocalStack muda o critério de aceite. Pesquisar `terraform test`/mock provider e o escopo do Hobby.
- **F-K8s:** escolha do ingress controller pós-EOL do ingress-nginx (Traefik vs Envoy Gateway/Gateway API) e substituição de charts Bitnami para dependências no kind.
- **F-Cliques:** o comportamento default de `cancelOnError` na versão do Spring Data Redis efetivamente usada (3.5.x vs 4.x).
- **F-Fundação:** a escolha entre Spring Boot 3.5 (EOL OSS em 30/06/2026) e 4.x afeta Jackson, Testcontainers 2, os starters e a classe do launcher. Coordenar com o STACK.md.

**Fases com padrões consolidados (pouca pesquisa extra):** F-Redirect, F-RateLimit, F-Container, F-Observabilidade, F-CI e F-Carga/Docs. As armadilhas acima bastam como checklist.

## Fontes

- LocalStack — planos e serviços por plano (ECS/RDS/ElastiCache/ECR/ELBv2 ausentes no Hobby): https://docs.localstack.cloud/aws/licensing/ — ALTA
- LocalStack 2026.03.0 (auth token obrigatório, plano Hobby, fim dos limites de CI credits): https://blog.localstack.cloud/localstack-for-aws-release-2026-03-0/ — ALTA
- Issue testcontainers-java #11568 (LocalStack latest exige token): https://github.com/testcontainers/testcontainers-java/issues/11568 — MÉDIA
- "Your LocalStack CI Is Broken" (opções e alternativas Floci/Moto): https://dev.to/peytongreen_dev/your-localstack-ci-is-broken-here-are-your-three-options-41o8 — BAIXA/MÉDIA
- Kubernetes — Ingress NGINX Retirement: https://www.kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/ — ALTA
- Kubernetes — Statement on Ingress NGINX (jan/2026): https://www.kubernetes.io/blog/2026/01/29/ingress-nginx-statement/ — ALTA
- Ingress2Gateway 1.0: https://kubernetes.io/blog/2026/03/20/ingress2gateway-1-0-release — ALTA
- Bitnami deprecation (bitnamilegacy): https://www.chkk.io/blog/bitnami-deprecation e https://northflank.com/blog/bitnami-deprecates-free-images-migration-steps-and-alternatives — MÉDIA
- Testcontainers × Docker Engine 29: https://github.com/testcontainers/testcontainers-java/issues/11235 e https://www.coffeesprout.nl/en/testcontainers-docker29-api-too-old.html — ALTA
- Spring Boot 3.5 EOL OSS (30/06/2026): https://www.osseva.io/eol/spring-boot-3-5 e https://www.danvega.dev/blog/spring-boot-end-of-life — MÉDIA (confirmar em spring.io/projects/spring-boot#support)
- Spring Data Redis — cancelamento da subscription em erro: https://github.com/spring-projects/spring-data-redis/issues/2919 e Javadoc de `StreamMessageListenerContainer` (https://docs.spring.io/spring-data/redis/reference/3.5/api/java/org/springframework/data/redis/stream/StreamMessageListenerContainer.html) — ALTA (o default na 4.x precisa ser verificado)
- Spring Boot — Dockerfiles / jarmode tools: https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html — ALTA
- Migrar layertools → tools (removido no 4.1): https://docs.moderne.io/user-documentation/recipes/recipe-catalog/java/spring/boot4/migratelayertoolstotools_4_1/ — MÉDIA
- Liquibase 5.0 / FSL: https://www.liquibase.com/blog/liquibase-community-for-the-future-fsl e https://github.com/spring-projects/spring-boot/issues/47386 — ALTA
- runAsNonRoot com usuário não numérico: https://github.com/helm/helm/issues/4818 — ALTA
- Conhecimento consolidado (Micrometer histograms, seletores do kube-prometheus-stack, metrics-server no kind, `auto-index-creation` do Spring Data Mongo, `RemoteIpValve`, eviction do Redis, semântica 301/302): documentação oficial dos respectivos projetos — ALTA/MÉDIA (pontos marcados como "verificar" no texto)
