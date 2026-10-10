---
phase: quick-261009-tdc
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - estudos/README.md
  - estudos/01-estrutura-e-fluxo-de-requisicoes.md
  - estudos/02-spring-boot-e-java-21.md
  - estudos/03-postgres-jpa-e-liquibase.md
  - estudos/04-codigo-curto-base62.md
  - estudos/05-validacao-e-erros.md
  - estudos/06-testes.md
  - estudos/07-build-e-qualidade.md
  - estudos/08-ci-github-actions.md
  - estudos/09-ambiente-local-e-windows.md
  - estudos/10-proximas-fases.md
autonomous: true
requirements:
  - quick-261009-tdc

estimate:
  tokens: 90000
  raw_tokens: 90000
  tasks: 3
  confidence: low

must_haves:
  truths:
    - "Quem estuda abre estudos/README.md e chega, por links relativos que funcionam, aos 10 guias numerados (01 a 10), na ordem de leitura sugerida"
    - "Cada guia de 01 a 09 explica a tecnologia de forma didática em pt-BR e mostra como ela aparece no Link Pulse, citando caminhos reais do repositório (todos existem no HEAD) e só classes, métodos, propriedades, alvos e endpoints que existem"
    - "O guia 01 traça POST /links e GET /{code} do controller até o Postgres com os nomes reais: LinkController.create, LinkService.create, TargetHostPolicy.check, AliasPolicy.check, LinkRepository.existsByCode/nextId/saveAndFlush, CodeGenerator.encode, RedirectController.redirect, LinkService.resolve, LinkRepository.findByCode e LinkProblems"
    - "Os guias descrevem o estado atual (Phase 1 concluída): Spring Boot 4.1.1, Actuator na porta 8081 expondo só health e info, compose.dev.yaml só com Postgres e alvos do Makefile help/build/test/verify/run/db-up/db-down. Valkey, Redis Streams, MongoDB, k6, kind/Helm e Terraform aparecem só no guia 10, como planejados, com link para .planning/ROADMAP.md"
    - "O guia 09 traz comandos para Git Bash/WSL, cmd (mvnw.cmd) e PowerShell (.\\mvnw.cmd com \"-Dspring-boot.run.profiles=dev\" entre aspas e curl.exe)"
    - "Nenhum arquivo fora de estudos/ é alterado: código, pom, Makefile, compose, CI e README da raiz ficam como estão"
  artifacts:
    - path: "estudos/README.md"
      provides: "Índice: ordem de leitura, uma linha por guia, estado do projeto e convenções de leitura"
      contains: "(01-estrutura-e-fluxo-de-requisicoes.md)"
    - path: "estudos/01-estrutura-e-fluxo-de-requisicoes.md"
      provides: "Mapa do repositório, pacotes dev.linkpulse.* e fluxo POST /links e GET /{code} com diagramas Mermaid"
      contains: "sequenceDiagram"
    - path: "estudos/02-spring-boot-e-java-21.md"
      provides: "Starters, auto-configuração, profiles, application*.yml, LinkPulseProperties, Actuator 8081, records, virtual threads"
      contains: "8081"
    - path: "estudos/03-postgres-jpa-e-liquibase.md"
      provides: "Entidade Link, LinkRepository, link_id_seq, changelogs YAML, contexts dev/prod, seed demo-link, ddl-auto validate"
      contains: "link_id_seq"
    - path: "estudos/04-codigo-curto-base62.md"
      provides: "Base62 + permutação multiplicativa mod 62^7 e por que é ofuscação, não segurança"
      contains: "62^7"
    - path: "estudos/05-validacao-e-erros.md"
      provides: "Bean Validation, @HttpUrl, AliasPolicy, TargetHostPolicy, LinkProblems, GlobalExceptionHandler, RFC 9457"
      contains: "RFC 9457"
    - path: "estudos/06-testes.md"
      provides: "JUnit, *Test vs *IT, surefire/failsafe, Testcontainers com @ServiceConnection"
      contains: "@ServiceConnection"
    - path: "estudos/07-build-e-qualidade.md"
      provides: "Maven wrapper com SHA-256, pom.xml, Checkstyle, SpotBugs, .gitattributes"
      contains: "distributionSha256Sum"
    - path: "estudos/08-ci-github-actions.md"
      provides: "ci.yml passo a passo, pin por SHA, permissions, concurrency, Dependabot"
      contains: "contents: read"
    - path: "estudos/09-ambiente-local-e-windows.md"
      provides: "compose.dev.yaml, Makefile e comandos para Git Bash, cmd e PowerShell"
      contains: "mvnw.cmd"
    - path: "estudos/10-proximas-fases.md"
      provides: "Phases 2 a 5 (Valkey, Streams, MongoDB, observabilidade, k6, kind/Helm, Terraform) como planejado"
      contains: "../.planning/ROADMAP.md"
  key_links:
    - from: "estudos/README.md"
      to: "estudos/01-*.md ... estudos/10-proximas-fases.md"
      via: "links Markdown relativos"
      pattern: "\\]\\((0[1-9]|10)-[a-z0-9-]+\\.md\\)"
    - from: "estudos/0*.md"
      to: "app/, .github/, Makefile, compose.dev.yaml"
      via: "caminhos em crase relativos à raiz e links ../app/..."
      pattern: "(app|\\.github)/[A-Za-z0-9_./-]+"
    - from: "estudos/10-proximas-fases.md"
      to: ".planning/ROADMAP.md"
      via: "link relativo ../.planning/ROADMAP.md"
      pattern: "\\.\\./\\.planning/ROADMAP\\.md"
---

<objective>
Criar a pasta `estudos/` na raiz do repositório com material didático em pt-BR que explica a estrutura e as tecnologias do Link Pulse no estado atual (Phase 1 concluída). São 11 arquivos Markdown: o índice `estudos/README.md`, os guias 01 a 09 (um por tema pedido) e o guia 10, sobre as próximas fases.

Purpose: quem estuda as tecnologias (e o próprio autor) entende o projeto lendo o código real, com explicações ancoradas em arquivos que existem, sem classes ou configurações inventadas.
Output: 11 arquivos em `estudos/`. É só documentação: nenhuma alteração em código, build, CI ou no README da raiz.
</objective>

<execution_context>
@~/.claude/gsd-core/workflows/execute-plan.md
@~/.claude/gsd-core/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/PROJECT.md
@.planning/ROADMAP.md
@README.md
@.claude/CLAUDE.md

## Estado observado no planejamento (git HEAD 182bcc9, 2026-10-09)

O escopo foi autorizado pela listagem real feita no planejamento (`git ls-files`). Antes de escrever, rode `git ls-files app .github Makefile compose.dev.yaml .gitattributes` de novo. Se a listagem divergir desta, vale a listagem ao vivo: escreva sobre o que existe e registre a diferença no SUMMARY.

- Código principal em `app/src/main/java/dev/linkpulse/`:
  - `LinkPulseApplication.java`
  - `config/`: `ApplicationConfig`, `LinkPulseProperties` (record com `Code` e `Alias` aninhados), `OpenApiConfig`
  - `common/`: `GlobalExceptionHandler`
  - `common/validation/`: `HttpUrl`, `HttpUrlValidator`
  - `link/`: `AliasPolicy`, `CodeGenerator`, `CreateLinkRequest`, `Link`, `LinkController`, `LinkProblems`, `LinkRepository`, `LinkResponse`, `LinkService`, `RedirectController`, `TargetHostPolicy`
- Recursos: `app/src/main/resources/application.yml`, `application-dev.yml`, `application-prod.yml`, `db/changelog/db.changelog-master.yaml`, `db/changelog/changes/001-create-links.yaml`, `db/changelog/changes/002-seed-dev.yaml`.
- Testes em `app/src/test/java/dev/linkpulse/`:
  - base: `AbstractIT`, `TestcontainersConfiguration`
  - ITs: `LiquibaseContextsIT`, `OpenApiIT`, `link/ConcurrencyIT`, `link/LinkApiIT`, `link/RedirectIT`
  - unitários: `common/validation/HttpUrlValidatorTest`, `config/LinkPulsePropertiesTest`, `link/AliasPolicyTest`, `link/CodeGeneratorTest`, `link/TargetHostPolicyTest`
- Build: `app/pom.xml`, `app/mvnw`, `app/mvnw.cmd`, `app/.mvn/wrapper/maven-wrapper.properties`, `app/config/checkstyle/checkstyle.xml`, `app/config/spotbugs/exclude.xml`.
- Raiz: `Makefile`, `compose.dev.yaml`, `.gitattributes`, `.gitignore`, `README.md`, `.github/workflows/ci.yml`, `.github/dependabot.yml`.
- `estudos/` ainda não existe.

## Fatos que os guias NÃO podem contradizer (conferidos nos arquivos durante o planejamento)

- Spring Boot **4.1.1** (`spring-boot-starter-parent` em `app/pom.xml`) e Java 21. O CLAUDE.md usa 3.5.16 como baseline de pesquisa, mas o projeto escolheu 4.1.1: veja as decisões em PROJECT.md e STATE.md. Starters do Boot 4 em uso: `spring-boot-starter-webmvc`, `-validation`, `-actuator`, `-data-jpa`, `-liquibase`, além de `springdoc-openapi-starter-webmvc-ui` 3.1.1 e do driver `postgresql`. Nos testes: `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers` e `org.testcontainers:testcontainers-postgresql` (nome de artefato do Testcontainers 2.x, com a versão vinda do BOM).
- Liquibase **5.0.3**, gerenciado pelo BOM, com licença FSL-1.1-ALv2. A alternativa Apache 4.33.0 está na seção "Licenças de terceiros" do README.
- `application.yml`: `spring.threads.virtual.enabled: true`, `jpa.hibernate.ddl-auto: validate`, `jpa.open-in-view: false`, `liquibase.contexts: prod` como default, `liquibase.analytics-enabled: false`, `server.port: 8080`, `management.server.port: 8081` e `management.endpoints.web.exposure.include: health,info`. Isso quer dizer que o endpoint de métricas do Prometheus AINDA NÃO existe, e não há probes, logs ECS nem configuração de readiness/liveness nos yml. `application-dev.yml` traz o datasource `localhost:5432/linkpulse` e `contexts: dev`. `application-prod.yml` só traz `contexts: prod`; o datasource de prod vem de `SPRING_DATASOURCE_*`.
- Propriedades `linkpulse.*`: `base-url`, `self-hosts`, `code.alphabet`, `code.multiplier`, `alias.reserved`, com override por env `LINKPULSE_*` (comentário no `application.yml`).
- `LinkRepository`: `nextId()` (native `select nextval('link_id_seq')`, com `@Transactional`), `findByCode`, `existsByCode`. `Link implements Persistable<Long>`. A tabela `links` tem a constraint `uk_links_code`. O seed `demo-link` → `https://example.com/` está em `002-seed-dev.yaml`, com `contextFilter: dev`.
- `CodeGenerator`: `base62(pad7((id * M) mod 62^7))`, `LENGTH = 7`, `BigInteger`, `modInverse` validado na subida (o multiplicador precisa ser coprimo de 62^7), `encode`/`decode`. O comentário do `application.yml` diz "É ofuscação, não segurança".
- `RedirectController`: `@GetMapping("/{code:[A-Za-z0-9_-]+}")`, responde 302 com `CacheControl.noStore().cachePrivate()`. `LinkController.create` responde `201 Created` com `Location`.
- Problem types: `/problems/validation-error`, `/problems/malformed-request` e `/problems/internal-error` (GlobalExceptionHandler, que estende `ResponseEntityExceptionHandler` e é anotado com `@RestControllerAdvice`); `/problems/link-not-found`, `/problems/link-expired` e `/problems/alias-conflict` (LinkProblems).
- `CreateLinkRequest(url, alias, expiresAt)`: `url` com `@NotBlank @Size(max = 2048) @HttpUrl`; `expiresAt` é `OffsetDateTime` com `@Future`.
- `TestcontainersConfiguration` publica um bean `PostgreSQLContainer` (`postgres:18.6`) com `@ServiceConnection`. `AbstractIT` usa `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
- Wrapper: `wrapperVersion=3.3.4`, `distributionType=only-script`, Maven 3.9.16, `distributionSha256Sum`. `.gitattributes` força `mvnw` em LF e `*.cmd` em CRLF.
- Checkstyle: plugin 3.6.0 + engine 14.3.0, fase `validate`, `failOnViolation`. SpotBugs: 4.10.4.1, `effort` Max, `threshold` Medium, fase `verify`, `excludeFilterFile` `config/spotbugs/exclude.xml`.
- `ci.yml`: gatilhos `push` em main e `pull_request`; `permissions: contents: read`; `concurrency` com `cancel-in-progress`; job `build-test` com `timeout-minutes: 20`; checkout em `path: "link pulse"` (caminho com espaço, de propósito); actions fixadas por SHA completo com comentário de versão (checkout `3d3c42e5aac5ba805825da76410c181273ba90b1` # v7.0.1, setup-java, upload-artifact); passo que confere se o `mvnw` é executável e está em LF; `make -n verify && make help`; `./mvnw -B -ntp verify`; upload dos relatórios só em caso de falha. `dependabot.yml`: ecossistemas `github-actions` (`/`) e `maven` (`/app`), semanal, limite de 5 PRs.
- Makefile: alvos `help`, `build`, `test`, `verify`, `run` (depende de `db-up`), `db-up` e `db-down`. `make up` e `make k8s` AINDA NÃO existem: chegam nas Phases 3 e 4. `compose.dev.yaml` tem só `postgres:18.6`, publicado em `127.0.0.1:5432`, com o volume em `/var/lib/postgresql` e healthcheck `pg_isready`.
</context>

## Cobertura das fontes (descrição da quick task)

| Item pedido | Tarefa |
|---|---|
| `estudos/README.md` índice | 1 |
| (1) estrutura + pacotes `dev.linkpulse.*` + fluxo POST /links e GET /{code} | 1 |
| (2) Spring Boot 4.1 / Java 21 | 2 |
| (3) Postgres + JPA + Liquibase | 2 |
| (4) código curto Base62 + permutação mod 62^7 | 2 |
| (5) validação e erros, RFC 9457 | 2 |
| (6) testes | 3 |
| (7) build e qualidade | 3 |
| (8) CI | 3 |
| (9) ambiente local + Windows cmd/PowerShell/Git Bash | 3 |
| "próximas fases" com referência a `.planning/ROADMAP.md` | 3 |
| pt-BR, didático, ancorado no código real, sem invenção | 1, 2, 3 (regras comuns + gates de caminho) |
| somente documentação, nada fora de `estudos/` | 3 (gate de git) |

## Regras comuns a TODAS as tarefas (o executor aplica em cada arquivo)

1. **Leia antes de escrever.** Para cada guia, leia uma vez os arquivos do `<read_first>` da tarefa. Antes de citar qualquer classe, método, propriedade, anotação, alvo do Makefile, endpoint ou problem type, confirme com Grep que ele existe. Se não existir, não cite. Nada de exemplos "ilustrativos" com nomes que pareçam do projeto.
2. **Estrutura de cada guia 01–10:** título `# NN · Tema`; um parágrafo "Em uma frase"; "Conceitos" (a tecnologia explicada de forma genérica para quem está aprendendo, em 2 a 5 subseções curtas); "No Link Pulse" (como o repositório usa a tecnologia, com caminhos e trechos); "Por que assim?" (trade-offs tirados de comentários e javadoc do código, das decisões em STATE.md/PROJECT.md ou do README; sem justificativa inventada); "Experimente" (comandos ou exercícios); "Perguntas para fixar" (3 a 5 perguntas); rodapé de navegação `← Anterior · Índice · Próximo →` com links relativos. Tamanho-alvo: 120 a 250 linhas por guia e 50 a 90 linhas no README.
3. **Caminhos:** em crase, sempre relativos à raiz do repositório (ex.: `app/src/main/java/dev/linkpulse/link/LinkService.java`). Em links Markdown, relativos à pasta `estudos/` (ex.: `[LinkService.java](../app/src/main/java/dev/linkpulse/link/LinkService.java)`). Não use globs, chaves (`{dev,prod}`), `:linha` nem `#L` em caminhos citados. Para relatórios de build, escreva `target/...` sem o prefixo `app/`. Cada guia de 01 a 09 cita pelo menos 3 caminhos distintos sob `app/` ou `.github/`.
4. **Trechos de código:** copie literalmente do arquivo (não reescreva), com no máximo cerca de 20 linhas por trecho, o caminho do arquivo logo acima e omissões marcadas com `// ...` ou `# ...`. Use blocos cercados com a linguagem (`java`, `yaml`, `xml`, `bash`, `powershell`, `bat`).
5. **Números e saídas:** não calcule à mão códigos Base62 reais, nem saídas de comandos que você não executou. Exemplo numérico de matemática deve usar um "modelo de brinquedo" (módulo pequeno), rotulado como tal. Valores reais só entram se forem copiados de arquivo ou de um comando que você realmente rodou.
6. **Escopo temporal:** descreva o que existe no HEAD. Tecnologias das Phases 2 a 5 só aparecem no guia 10, ou numa nota de uma linha do tipo "chega na Phase N (ver guia 10)".
7. **Idioma e formato:** pt-BR com acentuação correta, UTF-8 e LF. Nomes de arquivo sem acento. Links externos só para documentação oficial de que você tenha certeza (por exemplo, `https://www.rfc-editor.org/rfc/rfc9457`, `https://docs.spring.io/spring-boot/`, `https://docs.github.com/actions`); na dúvida, omita.
8. **Segredos:** não copie tokens nem segredos. As credenciais `linkpulse`/`linkpulse` de dev já estão públicas em `compose.dev.yaml` e podem aparecer, marcadas como "só para desenvolvimento local". `LOCALSTACK_AUTH_TOKEN` só pode aparecer pelo nome.
9. **Somente `estudos/`:** não edite nenhum outro arquivo, nem o README da raiz.

<tasks>

<task type="tracer">
  <name>Tarefa 1: Índice + guia 01 (estrutura e fluxo de requisições) — fatia de ponta a ponta</name>
  <files>estudos/README.md, estudos/01-estrutura-e-fluxo-de-requisicoes.md</files>
  <read_first>
    - README.md (seções "O que é", "Rodando local", "API", "Regras", "Erros", "Código curto")
    - app/src/main/java/dev/linkpulse/LinkPulseApplication.java
    - app/src/main/java/dev/linkpulse/link/LinkController.java
    - app/src/main/java/dev/linkpulse/link/RedirectController.java
    - app/src/main/java/dev/linkpulse/link/LinkService.java
    - app/src/main/java/dev/linkpulse/link/LinkRepository.java
    - app/src/main/java/dev/linkpulse/link/Link.java
    - app/src/main/java/dev/linkpulse/link/LinkProblems.java
    - app/src/main/java/dev/linkpulse/link/LinkResponse.java
    - app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java
  </read_first>
  <action>
Esta fatia cria o esqueleto completo (índice + primeiro guia + convenções de link e caminho) e prova as convenções com o check automático antes de escrever os outros guias.

(a) `estudos/README.md`, o índice:
- título "Estudos do Link Pulse";
- para quem é o material e como usar (ler com o código aberto ao lado);
- estado do projeto: Phase 1 concluída, Phase 2 em planejamento; aponte para `.planning/STATE.md` por link relativo `../.planning/STATE.md`;
- tabela com os 10 guias. Colunas: número, link relativo (`[01 · Estrutura e fluxo](01-estrutura-e-fluxo-de-requisicoes.md)`) e o que se aprende. Nomes exatos dos arquivos: `01-estrutura-e-fluxo-de-requisicoes.md`, `02-spring-boot-e-java-21.md`, `03-postgres-jpa-e-liquibase.md`, `04-codigo-curto-base62.md`, `05-validacao-e-erros.md`, `06-testes.md`, `07-build-e-qualidade.md`, `08-ci-github-actions.md`, `09-ambiente-local-e-windows.md` e `10-proximas-fases.md`;
- duas trilhas de leitura sugeridas: "quero entender a aplicação" (01→05) e "quero rodar e entregar" (09→07→06→08);
- convenções do material: caminhos em crase relativos à raiz; trechos copiados literalmente; o que é planejado só aparece no guia 10;
- glossário curto, de 8 a 12 termos, com uma linha cada: starter, auto-configuração, profile, bean, record, changeset, context do Liquibase, Problem Details, IT, Testcontainers, wrapper, pin por SHA.

Os links para os guias 02 a 10 ficam pendentes até as tarefas 2 e 3 e são conferidos no fim da tarefa 3.

(b) `estudos/01-estrutura-e-fluxo-de-requisicoes.md`:
- Mapa do repositório: árvore de diretórios do primeiro nível e de `app/`, gerada a partir de `git ls-files` (não da memória), com uma linha por item explicando o papel. Diga que não há frontend nem autenticação (README "O que é").
- Pacotes `dev.linkpulse`, `dev.linkpulse.config`, `dev.linkpulse.common`, `dev.linkpulse.common.validation` e `dev.linkpulse.link`: o que cada um contém e por que a organização é por funcionalidade (`link/`), e não por camada técnica.
- Fluxo de `POST /links`, com um diagrama Mermaid `sequenceDiagram` e uma lista numerada correspondente. A sequência é: `LinkController.create` (com `@Valid`) → `LinkService.create` → `TargetHostPolicy.check` → no caso de alias, `AliasPolicy.check` e `LinkRepository.existsByCode`, que leva a `LinkProblems.aliasConflict` → `LinkRepository.nextId` → `CodeGenerator.encode` (ou o alias) → `saveAndFlush(Link.create(...))` → `201 Created` com `Location` e corpo `LinkResponse`. Explique também a corrida de alias pela constraint `uk_links_code`. Confirme cada passo na leitura do `LinkService` e corrija esta ordem se o código divergir.
- Fluxo de `GET /{code}`, com outro `sequenceDiagram`: `RedirectController.redirect` → `LinkService.resolve` → `LinkRepository.findByCode`. Daí sai `LinkProblems.notFound` (404), `LinkProblems.expired` (410) ou `302` com `Location` e `Cache-Control: no-store, private`. Explique a regex do `@GetMapping` e o que ela barra antes de chegar ao controller.
- Tabela de respostas HTTP com status, quando ocorre e problem type: 201, 302, 400, 404, 409, 410 e 500.
- "Experimente": os `curl` do README (seção "Rodando local" e "API"), apontando para o guia 09 para os equivalentes em cmd e PowerShell.
- Notas de uma linha dizendo que cache, cliques e estatísticas chegam na Phase 2 (ver guia 10).

Ao terminar, rode o `<verify>` e corrija qualquer caminho MISSING ou link BROKEN antes de fazer o commit.
  </action>
  <verify>
    <automated>cd "$(git rev-parse --show-toplevel)" && test -s estudos/README.md && test -s estudos/01-estrutura-e-fluxo-de-requisicoes.md && for k in LinkController RedirectController LinkService LinkRepository CodeGenerator TargetHostPolicy AliasPolicy dev.linkpulse.link Cache-Control sequenceDiagram uk_links_code; do grep -qF -- "$k" estudos/01-estrutura-e-fluxo-de-requisicoes.md || { echo "SEM $k"; exit 1; }; done && for g in 01-estrutura-e-fluxo-de-requisicoes 02-spring-boot-e-java-21 03-postgres-jpa-e-liquibase 04-codigo-curto-base62 05-validacao-e-erros 06-testes 07-build-e-qualidade 08-ci-github-actions 09-ambiente-local-e-windows 10-proximas-fases; do grep -qF "($g.md)" estudos/README.md || { echo "INDICE SEM $g"; exit 1; }; done || exit 1; bad=$( grep -ohE '`(app|\.github|\.planning)/[^`[:space:]]*`' estudos/README.md estudos/01-*.md | tr -d '`' | grep -vE '[*{]|/target/' | sort -u | while read -r p; do [ -e "$p" ] || echo "MISSING $p"; done; cd estudos && for f in README.md 01-estrutura-e-fluxo-de-requisicoes.md; do grep -oE '\]\([^)]+\)' "$f" | grep -v '://' | sed -E 's/^\]\(//; s/\)$//; s/#.*//; s/ .*//' | grep -vE '^(0[2-9]|10)-' | while read -r p; do [ -z "$p" ] || [ -e "$p" ] || echo "BROKEN $f -> $p"; done; done ); echo "$bad"; test -z "$bad"</automated>
  </verify>
  <done>O índice lista os 10 guias com os nomes exatos. O guia 01 tem os dois diagramas `sequenceDiagram` com os métodos reais e a tabela de respostas. Todo caminho citado em crase existe, e todos os links relativos do README e do 01 resolvem, exceto os guias 02 a 10, que ainda serão criados. Commit feito só com os 2 arquivos.</done>
</task>

<task type="auto">
  <name>Tarefa 2: Guias 02–05 (Spring Boot/Java 21, Postgres/JPA/Liquibase, código curto, validação e erros)</name>
  <files>estudos/02-spring-boot-e-java-21.md, estudos/03-postgres-jpa-e-liquibase.md, estudos/04-codigo-curto-base62.md, estudos/05-validacao-e-erros.md</files>
  <read_first>
    - app/pom.xml (só a seção de dependências e o parent)
    - app/src/main/resources/application.yml, application-dev.yml, application-prod.yml
    - app/src/main/java/dev/linkpulse/config/ApplicationConfig.java, LinkPulseProperties.java, OpenApiConfig.java
    - app/src/main/resources/db/changelog/db.changelog-master.yaml, changes/001-create-links.yaml, changes/002-seed-dev.yaml
    - app/src/main/java/dev/linkpulse/link/CodeGenerator.java (javadoc completo)
    - app/src/main/java/dev/linkpulse/link/AliasPolicy.java, TargetHostPolicy.java (javadoc e regras; o corpo só até entender)
    - app/src/main/java/dev/linkpulse/common/validation/HttpUrl.java, HttpUrlValidator.java
    - app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java
    - README.md (seções "Regras", "Erros (Problem Details, RFC 9457)", "Código curto", "Riscos conhecidos", "Licenças de terceiros")
    - .planning/STATE.md (Decisions [Phase 01] e Blockers) e .planning/PROJECT.md (tabela "Decisões Importantes")
  </read_first>
  <action>
Escreva os 4 guias seguindo as "Regras comuns" e o rodapé de navegação: 01↔02↔03↔04↔05↔06. O link do 05 para o 06 fica pendente até a tarefa 3.

`02-spring-boot-e-java-21.md`:
- O que é um starter e por que o Boot 4 tem starters modulares (`spring-boot-starter-webmvc` em vez do antigo `-web`; `spring-boot-starter-liquibase` separado). Liste os starters do `app/pom.xml`.
- Auto-configuração: o DataSource sai de `spring.datasource.*`; o Liquibase roda na subida a partir de `spring.liquibase.change-log`; o Actuator sobe na porta de gerenciamento.
- `LinkPulseApplication` e as anotações reais dela.
- `ApplicationConfig`: quais beans ela declara e por quê (confira no arquivo).
- `LinkPulseProperties` como `record` com `@ConfigurationProperties("linkpulse")`, os records aninhados `Code` e `Alias`, validação na subida e relaxed binding (`LINKPULSE_BASE_URL`, `LINKPULSE_SELF_HOSTS` etc., conforme o comentário do `application.yml`).
- Profiles: `application.yml` como base; `application-dev.yml` e `application-prod.yml` sobrescrevem. Como ativar: `-Dspring-boot.run.profiles=dev` (do Makefile) e a variável `SPRING_PROFILES_ACTIVE`. O `contexts` do Liquibase muda por profile; aponte para o guia 03.
- Actuator na porta 8081 separada da 8080, expondo só `health,info`, e por que separar portas. Nota de uma linha: métricas Prometheus chegam na Phase 3 (guia 10).
- `spring.threads.virtual.enabled: true`: o que são virtual threads (JEP 444) e um cuidado com pinning em `synchronized` no Java 21.
- `open-in-view: false` e por quê.
- Java 21 no projeto: `record` em `CreateLinkRequest`, `LinkResponse` e `LinkPulseProperties`; o que o record substitui (sem Lombok).
- Por que Boot 4.1.1 e não 3.5.x: o suporte OSS da 3.5 acabou em 2026-06-30 (PROJECT.md/CLAUDE.md).
- Jackson 3 só entra se for confirmado em STATE.md (a decisão 01-06 sobre `OffsetDateTime` sem offset), citado como tal.
- Swagger UI: as URLs do README e a classe `OpenApiConfig`.

`03-postgres-jpa-e-liquibase.md`:
- Postgres 18.6 em três lugares: `compose.dev.yaml`, `TestcontainersConfiguration` e (no futuro) RDS.
- A entidade `Link`: as anotações reais, por que ela implementa `Persistable<Long>` (o ID é atribuído antes, vindo da sequência, e `isNew` evita um `merge` com SELECT; confirme no javadoc ou no código), `Link.create` e `isExpiredAt`.
- `LinkRepository`: o que o Spring Data gera a partir de `JpaRepository`; consultas derivadas (`findByCode`, `existsByCode`); a query nativa `nextId()` com `@Transactional` e o motivo (decisão 01-02 no STATE: evitar `nextval` em transação read-only).
- Por que a sequência é dedicada e por que se usa `nextval` antes do insert (o código existe antes do commit).
- `saveAndFlush` e a detecção da corrida de alias pela constraint `uk_links_code` com fallback SQLSTATE 23505 (decisão 01-04).
- Liquibase: o que é changelog e changeset; o master com `include` + `relativeToChangelogFile`; o `001` (sequência + tabela, sem context, imutável por checksum, conforme o comentário do arquivo); o `002` (seed `demo-link` com `contextFilter: dev` e `valueComputed: nextval(...)`); `spring.liquibase.contexts` por profile; a prova em `LiquibaseContextsIT`; `analytics-enabled: false`; a licença do Liquibase 5.0.3 (README).
- `ddl-auto: validate`: o Hibernate só confere o schema e nunca o cria.
- "Experimente": `docker compose -f compose.dev.yaml exec postgres psql -U linkpulse -d linkpulse`, seguido de `\d links` e `select id, code, target_url from links;`. Também as tabelas `databasechangelog` e `databasechangeloglock`, apresentadas como tabelas de controle do Liquibase.

`04-codigo-curto-base62.md`:
- O que é Base62 e por que o alfabeto `0-9A-Za-z`.
- O espaço de 62^7 códigos (3.521.614.606.208, valor que você pode conferir).
- A permutação multiplicativa: o código é `base62(pad7((id * M) mod 62^7))`. Por que `M` precisa ser coprimo de 62^7 (bijeção, existe inverso: `modInverse`, validado na subida e com mensagem própria; decisão 01-03). Por que `BigInteger` (overflow de `id * M`). Os métodos `encode`/`decode` e o `LENGTH = 7`.
- Um modelo de brinquedo com módulo pequeno, para mostrar a bijeção e a inversão, rotulado como brinquedo.
- Por que isso é ofuscação e não segurança: o próprio javadoc mostra como dois códigos consecutivos revelam `M`; o comentário do `application.yml` diz isso com todas as letras. Não use o código como segredo nem como token.
- Por que não hash ou aleatório: colisão e retry (CLAUDE.md, "O Que NÃO Usar").
- Separação dos espaços: alias exige `-` ou `_`; o código gerado é só alfanumérico.
- Contrato one-way: trocar `alphabet` ou `multiplier` quebra todos os links já emitidos (STATE, Blockers).
- Testes que cobrem o gerador: `CodeGeneratorTest` e `ConcurrencyIT`.
- Use a notação `62^7` de forma literal e consistente, igual à do javadoc.

`05-validacao-e-erros.md`:
- Bean Validation em records: as anotações reais de `CreateLinkRequest` e o `@Valid` em `LinkController.create`.
- Como se escreve uma constraint própria: `@HttpUrl` (meta-anotações) + `HttpUrlValidator implements ConstraintValidator`, com as regras reais (http/https, host obrigatório, recusa de userinfo `user:senha@host`, decisão 01-06).
- `@Future` em `OffsetDateTime`.
- `AliasPolicy`: regex, lista `linkpulse.alias.reserved` comparada sem caixa via `Locale.ROOT`, alias case-sensitive no armazenamento; `matchesFormat`, `isReserved`, `check`.
- `TargetHostPolicy`: recusa o próprio host (`base-url`, `self-hosts`), loopback em qualquer perfil e IP numérico fora da forma canônica; nunca resolve DNS. Explique a mitigação parcial por DNS (README "Riscos conhecidos"; decisões 01-08).
- `LinkProblems` como fábrica de `ErrorResponseException`.
- `GlobalExceptionHandler`: `@RestControllerAdvice`, estende `ResponseEntityExceptionHandler`, `errors[]` na validação, `malformed-request`, e `internal-error` genérico sem detalhe interno (por quê: vazamento de informação).
- RFC 9457: os campos `type`, `title`, `status`, `detail`, `instance` e extensões. Exemplo de resposta copiado da seção "Erros" do README.
- Tabela problem type → status → quem lança.
- Testes: `HttpUrlValidatorTest`, `AliasPolicyTest`, `TargetHostPolicyTest`, `LinkApiIT`.

Ao terminar, rode o `<verify>` e corrija tudo o que ele apontar antes de fazer o commit.
  </action>
  <verify>
    <automated>cd "$(git rev-parse --show-toplevel)" && chk(){ f=$1; shift; test -s "estudos/$f" || { echo "FALTA $f"; return 1; }; for k in "$@"; do grep -qF -- "$k" "estudos/$f" || { echo "SEM '$k' em $f"; return 1; }; done; } && chk 02-spring-boot-e-java-21.md 4.1.1 8081 'health' application-dev.yml application-prod.yml LinkPulseProperties virtual record && chk 03-postgres-jpa-e-liquibase.md ddl-auto validate link_id_seq contextFilter demo-link Persistable uk_links_code nextId && chk 04-codigo-curto-base62.md '62^7' coprimo modInverse ofusca CodeGeneratorTest && chk 05-validacao-e-erros.md 'RFC 9457' '@HttpUrl' HttpUrlValidator AliasPolicy TargetHostPolicy GlobalExceptionHandler LinkProblems '/problems/' || exit 1; bad=$( grep -ohE '`(app|\.github|\.planning)/[^`[:space:]]*`' estudos/0[2-5]-*.md | tr -d '`' | grep -vE '[*{]|/target/' | sort -u | while read -r p; do [ -e "$p" ] || echo "MISSING $p"; done; cd estudos && for f in 0[2-5]-*.md; do grep -oE '\]\([^)]+\)' "$f" | grep -v '://' | sed -E 's/^\]\(//; s/\)$//; s/#.*//; s/ .*//' | grep -vE '^(0[6-9]|10)-' | while read -r p; do [ -z "$p" ] || [ -e "$p" ] || echo "BROKEN $f -> $p"; done; done ); echo "$bad"; test -z "$bad"</automated>
  </verify>
  <done>Os 4 guias existem, seguem a estrutura comum, citam só nomes que existem no código e passam no check de palavras-chave, caminhos e links (os links para 06–10 ficam pendentes). O guia 04 diz com todas as letras que é ofuscação e não segurança. Commit feito só com os 4 arquivos.</done>
</task>

<task type="auto">
  <name>Tarefa 3: Guias 06–09 + 10 (próximas fases) e verificação final do conjunto</name>
  <files>estudos/06-testes.md, estudos/07-build-e-qualidade.md, estudos/08-ci-github-actions.md, estudos/09-ambiente-local-e-windows.md, estudos/10-proximas-fases.md</files>
  <read_first>
    - app/pom.xml (plugins: spring-boot-maven-plugin, failsafe, checkstyle, spotbugs)
    - app/src/test/java/dev/linkpulse/AbstractIT.java, TestcontainersConfiguration.java (javadoc), LiquibaseContextsIT.java (só a ideia)
    - app/src/test/java/dev/linkpulse/link/CodeGeneratorTest.java (um exemplo de teste unitário)
    - app/src/test/java/dev/linkpulse/link/RedirectIT.java (um exemplo de IT)
    - app/.mvn/wrapper/maven-wrapper.properties, .gitattributes, app/config/spotbugs/exclude.xml, app/config/checkstyle/checkstyle.xml (só o cabeçalho e os comentários)
    - .github/workflows/ci.yml, .github/dependabot.yml
    - Makefile, compose.dev.yaml, README.md (seções "Pré-requisitos", "Comandos", "Rodando local", "Qualidade e CI")
    - .planning/ROADMAP.md (seções das Phases 2 a 5: Goal e Success Criteria)
  </read_first>
  <action>
Escreva os 5 arquivos seguindo as "Regras comuns" e o rodapé de navegação: 05↔06↔07↔08↔09↔10. O 10 termina com "Voltar ao índice".

`06-testes.md`:
- Pirâmide de testes aplicada aqui: unitários `*Test`, rodados pelo surefire na fase `test`, sem Docker (`make test`); integração `*IT`, rodados pelo failsafe nas fases `integration-test`/`verify`, com Docker (`make verify`).
- JUnit Jupiter (versão vinda do BOM do Boot 4.1.1; não informe número de versão se não conseguir confirmar).
- Um teste unitário real comentado (trecho de `CodeGeneratorTest`).
- `AbstractIT` com `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`.
- `TestcontainersConfiguration`: bean `PostgreSQLContainer` `postgres:18.6` com `@ServiceConnection`. Explique o que o `@ServiceConnection` faz (gera as `ConnectionDetails`, dispensando a URL no yml) e por que o container é um bean único reaproveitado entre classes de IT (javadoc do arquivo).
- O artefato `testcontainers-postgresql` (nome do Testcontainers 2.x, comentário no pom). A exigência de Docker em execução e a compatibilidade com o Docker Engine 29 (CLAUDE.md).
- Um IT real comentado (trecho de `RedirectIT`).
- O que cada IT prova: uma linha para cada um de `LinkApiIT`, `RedirectIT`, `ConcurrencyIT`, `LiquibaseContextsIT` e `OpenApiIT`.
- Como rodar um único teste unitário: `./mvnw -Dtest=CodeGeneratorTest test`, a partir de `app/`. Inclua só se você rodar o comando com sucesso; senão, mostre só os alvos do Makefile.
- Onde ficam os relatórios: `target/surefire-reports` e `target/failsafe-reports`.

`07-build-e-qualidade.md`:
- Maven wrapper: `app/mvnw`, `app/mvnw.cmd` e `maven-wrapper.properties` (`wrapperVersion=3.3.4`, `distributionType=only-script`, Maven 3.9.16 e `distributionSha256Sum`). Por que fixar a versão e conferir o SHA-256 (supply chain; o mesmo Maven no local e no CI).
- `.gitattributes`: `mvnw` em LF, `*.cmd` em CRLF, e o problema que isso evita no Windows.
- Anatomia do `pom.xml`: parent, `properties`, dependências sem versão (BOM) e plugins.
- Ciclo de vida: `validate` → `compile` → `test` → `package` → `integration-test` → `verify`, e em que fase cada gate roda.
- Checkstyle: plugin 3.6.0 com engine 14.3.0 sobrescrita (o padrão 9.3 não entende Java 21, segundo o CLAUDE.md), `failOnViolation`, configuração `app/config/checkstyle/checkstyle.xml` (Google adaptado; confirme no cabeçalho do arquivo).
- SpotBugs 4.10.4.1, `effort` Max, `threshold` Medium, fase `verify`, e as exclusões reais de `app/config/spotbugs/exclude.xml` com a justificativa de cada uma, conforme os comentários do arquivo.
- Relatórios: `target/checkstyle-result.xml` e `target/spotbugsXml.xml`.
- Tabela "comando → o que roda": `make build`, `make test` e `make verify`, com os equivalentes `./mvnw`.

`08-ci-github-actions.md`:
- Anatomia de um workflow (`on`, `permissions`, `concurrency`, `jobs`, `steps`, `uses` × `run`).
- O `ci.yml` passo a passo:
  - gatilhos;
  - `permissions: contents: read` (menor privilégio do `GITHUB_TOKEN`);
  - `concurrency` com `cancel-in-progress`;
  - `timeout-minutes`;
  - o checkout em `path: "link pulse"` e o `working-directory`, que provam a cada push que o build aguenta caminho com espaço (o autor usa "projeto 4" no Windows);
  - pin por SHA completo com comentário de versão (cite o SHA real do checkout, `3d3c42e5aac5ba805825da76410c181273ba90b1`) e por que tags são mutáveis (incidente da trivy-action, mar/2026, no CLAUDE.md);
  - `setup-java` com `cache: maven`;
  - o passo que confere o `mvnw` executável e em LF;
  - `make -n verify && make help` (prova que o Makefile parseia no Linux);
  - `./mvnw -B -ntp verify` (significado de `-B` e `-ntp`);
  - upload de relatórios só com `if: failure()`.
- `dependabot.yml`: os dois ecossistemas, a frequência semanal e por que ele mantém os pins por SHA atualizados.
- Nota de uma linha: publicação no GHCR, Trivy e smoke em kind chegam na Phase 4 (guia 10).

`09-ambiente-local-e-windows.md`:
- Pré-requisitos (README): JDK 21, Docker Desktop e, opcionalmente, GNU make (o Git Bash não traz make; formas de instalar no README).
- `compose.dev.yaml` comentado: só Postgres 18.6; porta publicada só em `127.0.0.1` (por quê); volume em `/var/lib/postgresql` (mudança do PGDATA no PG18); healthcheck `pg_isready` + `--wait`; credenciais só de dev.
- Makefile: tabela com os alvos reais (use `grep -E '^[a-zA-Z_-]+:.*?## ' Makefile`, sem depender do make), a variável `MVNW` e `run` dependendo de `db-up`.
- Tabela de comandos por terminal:
  - Git Bash/WSL: `make run`, ou sem make `docker compose -f compose.dev.yaml up -d --wait` seguido de `cd app && ./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=dev`.
  - cmd: `docker compose -f compose.dev.yaml up -d --wait`, depois `cd app` e `mvnw.cmd -B -ntp spring-boot:run -Dspring-boot.run.profiles=dev`. Use `cd "...\projeto 4"` com aspas por causa do espaço.
  - PowerShell: `docker compose -f compose.dev.yaml up -d --wait`, depois `cd app; .\mvnw.cmd -B -ntp spring-boot:run "-Dspring-boot.run.profiles=dev"`. Explique as aspas: sem elas, o PowerShell quebra o argumento `-D...` no ponto. Mostre também `.\mvnw.cmd -B -ntp verify`, igual ao README.
  - Para testar a API no PowerShell: `curl.exe` (no Windows PowerShell 5.1, `curl` é alias de `Invoke-WebRequest`). Por exemplo, `curl.exe -i http://localhost:8080/demo-link` e `curl.exe http://localhost:8081/actuator/health`. Para o `POST /links`, use `Invoke-RestMethod -Method Post -Uri http://localhost:8080/links -ContentType 'application/json' -Body` com o JSON `{"url":"https://example.com/artigo"}` (o campo `url` vem de `CreateLinkRequest`).
- Parar: `make db-down` ou `docker compose -f compose.dev.yaml down`, que mantêm o volume. Avise que `down -v` apaga os dados.
- Problemas comuns: porta 5432 ocupada, Docker parado, `mvnw` com CRLF ("bad interpreter", evitado pelo `.gitattributes`) e caminhos com espaço.
- Smoke opcional da sintaxe, a partir da raiz do repo no Git Bash: `powershell.exe -NoProfile -Command "Set-Location app; .\mvnw.cmd -v"` e `MSYS_NO_PATHCONV=1 cmd.exe /d /c "cd app && mvnw.cmd -v"`; os dois devem mostrar Apache Maven 3.9.16. Se falharem por causa do ambiente, registre no SUMMARY e mantenha os comandos copiados do README e do Makefile, sem inventar outra sintaxe.

`10-proximas-fases.md`:
- Abra deixando claro que nada aqui existe ainda e que este é o plano de `[ROADMAP](../.planning/ROADMAP.md)`.
- Para cada Phase de 2 a 5: o Goal resumido do ROADMAP; as tecnologias, cada uma com "o que é" em 2 a 4 frases e "como vai entrar no Link Pulse"; e "o que estudar antes".
  - Phase 2: Valkey (Redis-compatível), cache-aside com fail-open, rate limit por IP com script Lua, Redis Streams com consumer group e gravação em lote no MongoDB, endpoint de estatísticas com aggregation.
  - Phase 3: imagem Docker multi-stage, `make up` com a stack local, Prometheus, Grafana, alertas e k6.
  - Phase 4: Helm chart, kind, Traefik, kube-prometheus-stack, GHCR, Trivy/SBOM e smoke em CI.
  - Phase 5: Terraform AWS (ECS, RDS, ElastiCache Valkey, ECR) validado em camadas: tflint, `terraform test` com `mock_provider`, LocalStack Hobby com `LOCALSTACK_AUTH_TOKEN` só pelo nome.
- Use só o que está no ROADMAP, no PROJECT.md e no CLAUDE.md, sem prometer detalhes que esses documentos não trazem.

Por fim, rode o `<verify>`. Ele confere o conjunto inteiro: todos os links (inclusive os pendentes das tarefas 1 e 2), todos os caminhos, as citações mínimas, o pt-BR e que nada fora de `estudos/` mudou. Corrija o que for apontado e faça o commit.
  </action>
  <verify>
    <automated>cd "$(git rev-parse --show-toplevel)" && chk(){ f=$1; shift; test -s "estudos/$f" || { echo "FALTA $f"; return 1; }; for k in "$@"; do grep -qF -- "$k" "estudos/$f" || { echo "SEM '$k' em $f"; return 1; }; done; } && chk 06-testes.md '@ServiceConnection' surefire failsafe AbstractIT TestcontainersConfiguration testcontainers-postgresql && chk 07-build-e-qualidade.md distributionSha256Sum 3.9.16 Checkstyle 14.3.0 SpotBugs exclude.xml .gitattributes && chk 08-ci-github-actions.md 'contents: read' dependabot.yml 3d3c42e5aac5ba805825da76410c181273ba90b1 concurrency && chk 09-ambiente-local-e-windows.md mvnw.cmd '"-Dspring-boot.run.profiles=dev"' PowerShell 'Git Bash' compose.dev.yaml db-up curl.exe && chk 10-proximas-fases.md '../.planning/ROADMAP.md' Valkey 'Redis Streams' MongoDB k6 kind Helm Terraform && test "$(ls estudos/*.md | wc -l)" -eq 11 && test -z "$(grep -L -E ' (não|é|são) ' estudos/*.md)" || { echo "FALHOU checagem de arquivos/palavras-chave/pt-BR"; exit 1; }; bad=$( grep -ohE '`(app|\.github|\.planning)/[^`[:space:]]*`' estudos/*.md | tr -d '`' | grep -vE '[*{]|/target/' | sort -u | while read -r p; do [ -e "$p" ] || echo "MISSING $p"; done; for f in estudos/0*.md; do n=$(grep -oE '(app|\.github)/[A-Za-z0-9_./-]+' "$f" | sort -u | wc -l); [ "$n" -ge 3 ] || echo "POUCAS-CITACOES $f $n"; done; cd estudos && for f in *.md; do grep -oE '\]\([^)]+\)' "$f" | grep -v '://' | sed -E 's/^\]\(//; s/\)$//; s/#.*//; s/ .*//' | while read -r p; do [ -z "$p" ] || [ -e "$p" ] || echo "BROKEN $f -> $p"; done; done ); echo "$bad"; test -z "$bad" && test -z "$(git status --porcelain -- . ':(exclude)estudos' ':(exclude).planning' ':(exclude).gsd')" && test -z "$(git diff --name-only 182bcc9 HEAD -- . ':(exclude)estudos' ':(exclude).planning')"</automated>
  </verify>
  <done>Os 11 arquivos existem. Todos os links relativos de `estudos/*.md` resolvem e todo caminho em crase existe. Cada guia de 01 a 09 cita pelo menos 3 caminhos reais. O guia 09 tem comandos para Git Bash, cmd e PowerShell. O guia 10 aponta para `../.planning/ROADMAP.md` e trata tudo como planejado. `git status` e `git diff` desde 182bcc9 não mostram mudança fora de `estudos/` e `.planning/`. Commit feito só com os 5 arquivos.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| repositório → leitor público | Os guias são publicados junto com o repo de portfólio; quem lê copia comandos e aprende conceitos a partir deles |
| guia → terminal do leitor | Comandos copiados dos guias rodam na máquina de quem estuda (Docker, Maven, PowerShell) |

## STRIDE Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation Plan |
|-----------|----------|-----------|----------|-------------|-----------------|
| T-q261009-01 | Information Disclosure | estudos/*.md | low | mitigate | Regra comum 8: nenhum token ou segredo nos guias. Só as credenciais de dev `linkpulse`/`linkpulse`, já públicas em `compose.dev.yaml` e marcadas como "só desenvolvimento local". `LOCALSTACK_AUTH_TOKEN` só aparece pelo nome |
| T-q261009-02 | Tampering (integridade do ensino) | estudos/04-codigo-curto-base62.md, 05-validacao-e-erros.md | low | mitigate | O guia 04 afirma que a permutação é ofuscação e não segurança (gate `ofusca`). O guia 05 reproduz as limitações reais (mitigação parcial por DNS do README "Riscos conhecidos"), sem vender proteção que o código não dá |
| T-q261009-03 | Denial of Service (dados locais do leitor) | estudos/09-ambiente-local-e-windows.md | low | mitigate | Os comandos de parada documentados mantêm o volume (`make db-down`, `down`). `down -v` só aparece com aviso explícito de que apaga os dados |
| T-q261009-04 | Tampering (escopo) | app/, .github/, Makefile, compose.dev.yaml, README.md | low | mitigate | O gate final da tarefa 3 exige `git status` e `git diff 182bcc9..HEAD` vazios fora de `estudos/` e `.planning/` |
| T-q261009-05 | Spoofing (conteúdo inventado) | estudos/*.md | low | mitigate | Regras comuns 1, 3 e 4 (Grep antes de citar, trechos literais), gate automático de caminhos MISSING e links BROKEN e mínimo de 3 citações reais por guia |
</threat_model>

<verification>
O `<automated>` da Tarefa 3 é a verificação do conjunto. Ele cobre:
- 11 arquivos;
- palavras-chave dos guias 06 a 10;
- heurística de pt-BR;
- todos os caminhos em crase existem;
- todos os links relativos resolvem;
- pelo menos 3 citações reais por guia de 01 a 09;
- nada alterado fora de `estudos/` (base 182bcc9, observada no planejamento).

Os `<automated>` das Tarefas 1 e 2 cobrem as palavras-chave dos guias 01 a 05.

Revisão manual rápida, sem bloquear: abrir `estudos/README.md` e `estudos/01-estrutura-e-fluxo-de-requisicoes.md` no GitHub ou num preview Markdown e conferir se os diagramas Mermaid renderizam.
</verification>

<success_criteria>
- A pasta `estudos/` tem `README.md` + 10 guias numerados em pt-BR, navegáveis por links relativos.
- Os 9 temas pedidos estão cobertos, cada um no seu guia, e há um guia "próximas fases" com link para `.planning/ROADMAP.md`.
- O conteúdo está ancorado no código real: caminhos verificados automaticamente, trechos literais e nenhum nome inventado. Os guias refletem o estado da Phase 1 (Boot 4.1.1, Actuator 8081 só com health/info, compose só com Postgres).
- O guia 09 traz comandos corretos para Git Bash/WSL, cmd e PowerShell.
- Zero alterações em código, build, CI ou no README da raiz.
</success_criteria>

<output>
Crie `.planning/quick/261009-tdc-criar-pasta-estudos-com-material-did-tic/261009-tdc-SUMMARY.md` ao concluir. O SUMMARY deve registrar se o smoke opcional do `mvnw.cmd` (cmd e PowerShell) e o `-Dtest=CodeGeneratorTest` foram executados e com que resultado, além de qualquer divergência entre a listagem de arquivos ao vivo e a do planejamento.
</output>
