---
phase: 01-funda-o-e-n-cleo-de-links
verified: 2026-10-08T23:39:35Z
status: gaps_found
score: 41/48 must-haves verified (SC do roadmap 4/5; truths dos planos 37/43)
covered_files:
  - .gitattributes
  - .github/dependabot.yml
  - .github/workflows/ci.yml
  - .gitignore
  - .planning/REQUIREMENTS.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-01-PLAN.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-01-SUMMARY.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-02-PLAN.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-02-SUMMARY.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-03-PLAN.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-03-SUMMARY.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-04-PLAN.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-04-SUMMARY.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-05-PLAN.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-05-SUMMARY.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-06-PLAN.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-06-SUMMARY.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-07-PLAN.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-07-SUMMARY.md
  - Makefile
  - README.md
  - app/.mvn/wrapper/maven-wrapper.properties
  - app/config/checkstyle/checkstyle.xml
  - app/config/spotbugs/exclude.xml
  - app/mvnw
  - app/mvnw.cmd
  - app/pom.xml
  - app/src/main/java/dev/linkpulse/LinkPulseApplication.java
  - app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java
  - app/src/main/java/dev/linkpulse/common/validation/HttpUrl.java
  - app/src/main/java/dev/linkpulse/common/validation/HttpUrlValidator.java
  - app/src/main/java/dev/linkpulse/config/ApplicationConfig.java
  - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
  - app/src/main/java/dev/linkpulse/config/OpenApiConfig.java
  - app/src/main/java/dev/linkpulse/link/AliasPolicy.java
  - app/src/main/java/dev/linkpulse/link/CodeGenerator.java
  - app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java
  - app/src/main/java/dev/linkpulse/link/Link.java
  - app/src/main/java/dev/linkpulse/link/LinkController.java
  - app/src/main/java/dev/linkpulse/link/LinkProblems.java
  - app/src/main/java/dev/linkpulse/link/LinkRepository.java
  - app/src/main/java/dev/linkpulse/link/LinkResponse.java
  - app/src/main/java/dev/linkpulse/link/LinkService.java
  - app/src/main/java/dev/linkpulse/link/RedirectController.java
  - app/src/main/resources/application-dev.yml
  - app/src/main/resources/application-prod.yml
  - app/src/main/resources/application.yml
  - app/src/main/resources/db/changelog/changes/001-create-links.yaml
  - app/src/main/resources/db/changelog/changes/002-seed-dev.yaml
  - app/src/main/resources/db/changelog/db.changelog-master.yaml
  - app/src/test/java/dev/linkpulse/AbstractIT.java
  - app/src/test/java/dev/linkpulse/LiquibaseContextsIT.java
  - app/src/test/java/dev/linkpulse/OpenApiIT.java
  - app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java
  - app/src/test/java/dev/linkpulse/common/validation/HttpUrlValidatorTest.java
  - app/src/test/java/dev/linkpulse/link/AliasPolicyTest.java
  - app/src/test/java/dev/linkpulse/link/CodeGeneratorTest.java
  - app/src/test/java/dev/linkpulse/link/ConcurrencyIT.java
  - app/src/test/java/dev/linkpulse/link/LinkApiIT.java
  - app/src/test/java/dev/linkpulse/link/RedirectIT.java
  - compose.dev.yaml
covered_digest: "v1:sha256:a5a26f2b8e0b3473f733c529b2c851603d54d1f5821c85c8a49147861d60288c"
behavior_unverified: 0
overrides_applied: 0
gaps:
  - truth: "URL que aponta para o host de linkpulse.base-url responde 400 com errors[] no campo url (01-06, mitigação T-06-03 contra loop de redirect)"
    status: partial
    reason: "LinkService.pointsToShortener compara só URI.getHost() com baseUrl().getHost() via equalsIgnoreCase. A mesma máquina com o FQDN com ponto final (http://localhost.:8080/x), ou por IP de loopback (127.0.0.1, [::1]), passa pelo @HttpUrl e pela checagem. Reproduzido com java.net.URI do JDK 21: localhost. -> selfHostBlocked=false. Um único POST {\"url\":\"http://localhost.:8080/loop-a\",\"alias\":\"loop-a\"} cria um link que redireciona para si mesmo. O README (linhas 131 e 180) e o 01-06-SUMMARY afirmam que o loop está fechado."
    artifacts:
      - path: "app/src/main/java/dev/linkpulse/link/LinkService.java"
        issue: "pointsToShortener (linhas 112-115) não normaliza o host (ponto final, caixa já tratada) nem considera aliases do próprio serviço (loopback, hostname do LB)"
      - path: "app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java"
        issue: "base-url sem host (ex.: sem esquema) faz getHost() devolver null e desliga a checagem em silêncio (WR-01, mesma raiz)"
      - path: "app/src/test/java/dev/linkpulse/link/LinkApiIT.java"
        issue: "urlPointingToTheShortenerItselfIsValidationErrorOnUrl cobre só a variação de caixa (LOCALHOST)"
      - path: "README.md"
        issue: "Linhas 131 e 180 afirmam que a regra evita loop de redirect, garantia que o código não entrega"
    missing:
      - "Normalizar o host antes de comparar (minúsculas com Locale.ROOT, remover pontos finais)"
      - "Comparar com um conjunto de hosts do próprio serviço (baseUrl normalizado + linkpulse.self-hosts opcional; em dev incluir 127.0.0.1 e [::1]) e/ou recusar IP literal de loopback via InetAddress sem resolver DNS"
      - "Validar linkpulse.base-url na subida (absoluta, http/https, com host), derrubando o contexto se inválida"
      - "Casos de IT com localhost., 127.0.0.1 e [::1] esperando 400 no campo url"
      - "Se a cobertura total ficar fora de escopo: ajustar README e threat model para 'mitigação parcial' e registrar override"
human_verification:
  - test: "Criar o repositório no GitHub, adicionar o remote origin, fazer push da main (e abrir um PR) e conferir a aba Actions"
    expected: "O workflow ci roda o job build-test e termina verde: checkout em 'link pulse/', step 'mvnw é executável e LF', 'make -n verify && make help' e './mvnw -B -ntp verify' passam no ubuntu-latest com Java 21 Temurin"
    why_human: "O repositório não tem remote; a execução no GitHub Actions nunca foi observada. Também é a única prova do build no Linux (SC5, CI-01, truth 1 e 6 da 01-01)"
  - test: "Instalar o GNU make (winget install ezwinports.make ou scoop install make) e, no Git Bash, rodar make help e make verify na raiz"
    expected: "make help lista help/build/test/verify/run/db-up/db-down; make verify termina com BUILD SUCCESS"
    why_human: "make não está instalado nesta máquina (truth 8 da 01-01, CONT-03)"
  - test: "Com Docker Desktop ligado: make run (ou docker compose -f compose.dev.yaml up -d --wait seguido de cd app && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev); depois curl -i http://localhost:8080/demo-link e curl http://localhost:8081/actuator/health; depois make db-down"
    expected: "demo-link responde 302 para https://example.com/ com Cache-Control no-store, private; o health na 8081 responde UP; a 8080 não expõe /actuator; db-down mantém o volume pgdata"
    why_human: "Exige subir o servidor e o Postgres do compose. Os ITs provam o redirect do seed via MockMvc, mas não o runtime real nem a porta de management (truth 2 da 01-01, truth 1 da 01-05). Nota: o jar já não sobe 'sem configuração extra' desde a 01-02, porque precisa de datasource"
  - test: "Seguir o README do zero e abrir http://localhost:8080/swagger-ui.html; criar um link com e sem alias pelo 'Try it out'"
    expected: "Swagger UI carrega, cria links (201) e mostra erros em Problem Details; o README basta sem conhecimento prévio"
    why_human: "Clareza da documentação e experiência na UI dependem de julgamento humano (human-check da 01-07). Atenção ao WR-04: o exemplo de expiresAt 2026-12-31 passa a dar 400 a partir de 2027-01-01"
  - test: "Revisar as duas proibições de nível judgment da 01-03 (código curto não é segredo; nenhum log da URL de destino ou do corpo)"
    expected: "Confirmar o veredito não autoritativo deste verificador: ambas respeitadas (Javadoc do CodeGenerator e README tratam como ofuscação; o único log é o LOG.error do handler de 500, sem request nem corpo)"
    why_human: "unverified-prohibition, revisão humana recomendada (proibições judgment-tier nunca passam em silêncio)"
---

# Phase 1: Fundação e núcleo de links — Relatório de Verificação

**Objetivo da fase:** Um cliente cria links curtos (gerados ou com alias) e é redirecionado para a URL original a partir do Postgres, num repositório que builda igual no Windows e no Linux, com CI verde desde o início
**Verificado em:** 2026-10-08T23:39:35Z
**Status:** gaps_found
**Re-verificação:** Não, é a verificação inicial

> **Nota sobre o modo MVP.** O ROADMAP marca a fase com `Mode: mvp`, mas o objetivo não está no formato de user story (`gsd query user-story.validate` deu `valid: false`). A regra do modo MVP manda recusar a verificação nesse caso. Mesmo assim, segui com a verificação goal-backward padrão porque o orquestrador pediu a verificação explicitamente e os critérios de sucesso do roadmap são concretos. Recomendação: reescrever o objetivo como user story com `/gsd mvp-phase 1` ou tirar `mode: mvp` da fase.

## Cobertura do fluxo do usuário (resumo)

| Passo | Esperado | Evidência | Status |
|-------|----------|-----------|--------|
| Cliente cria link com código gerado | 201, código de 7 caracteres, shortUrl e Location | `LinkApiIT#createsLinkWithGeneratedCodeAndLocation` (verde) | ✓ |
| Cliente cria link com alias | 201, code = alias; repetido → 409 | `LinkApiIT#aliasBecomesTheCodeAndRedirects`, `#repeatedAliasIsConflict...`, corrida 1×9 | ✓ |
| Cliente acessa `/{code}` | 302 do Postgres, sem cache; 404/410 em Problem Details | `RedirectIT` (9 testes verdes, Postgres 18.6 real) | ✓ |
| Repositório builda igual no Windows e no Linux | `./mvnw verify` verde nos dois | Git Bash + PowerShell com espaço no caminho: verde. Linux: só via CI, não observado | ? humano |
| CI verde desde o início | Workflow verde no GitHub | Não há remote; nunca rodou | ? humano |

## Atingimento do objetivo

### Critérios de sucesso do roadmap (contrato)

| # | Critério | Status | Evidência |
|---|----------|--------|-----------|
| SC1 | `POST /links` retorna código e URL curta; códigos em sequência não são consecutivos nem enumeráveis; criações concorrentes não duplicam | ✓ VERIFIED | `LinkController` → `LinkService.create` → `codeGenerator.encode(repository.nextId())` (nextval antes do INSERT). `CodeGenerator` faz permutação multiplicativa com `BigInteger` e `modInverse`. `CodeGeneratorTest#consecutiveIdsAreNotEnumerable` e `ConcurrencyIT` (8×50 → 400 códigos distintos, 400 linhas, zero exceções) passaram nesta execução |
| SC2 | Alias válido aceito; repetido → 409; reservado, fora da regex, URL inválida (não http/https ou longa) e expiração passada → erro de validação, sempre em Problem Details | ✓ VERIFIED | `AliasPolicy` (regex disjunta, reservados case-insensitive), `HttpUrlValidator`, `@Size(max=2048)`, `@Future`, `GlobalExceptionHandler` + `LinkProblems`. ITs verdes: ftp → 400 `url`, 2049 → 400, 2048 → 201, `Swagger-UI` → 400 `alias`, `abcd` → 400, passado → 400 `expiresAt`, 409 sem SQL nem constraint no corpo |
| SC3 | `GET /{code}` → 302 com `Cache-Control` sem cache; inexistente → 404; expirado → 410 | ✓ VERIFIED | `RedirectController` usa `CacheControl.noStore().cachePrivate()`. `RedirectIT#redirectsToTargetWithoutCache`, `#unknownCodeIsNotFoundProblem`, `#expiredLinkIsGoneProblem` verdes |
| SC4 | Schema pelo Liquibase (YAML, contexts dev/prod), `ddl-auto=validate`, Swagger UI documenta endpoints | ✓ VERIFIED | `application.yml` com `ddl-auto: validate`, `contexts: prod` e `analytics-enabled: false`. `RedirectIT#schemaComesFromLiquibaseChangelog`, `LiquibaseContextsIT` (dev com seed, prod sem seed) e `OpenApiIT` (6 testes: título, 201/400/409, 302/404/410, `/swagger-ui/index.html` 200) verdes |
| SC5 | Cada push/PR roda no GitHub Actions build, unitários, ITs, Checkstyle e SpotBugs, falhando em violação; o mesmo build roda no Git Bash/WSL e no Linux com espaço no caminho | ? UNCERTAIN (humano) | Parte local verificada: `ci.yml` correto (push main + PR, checkout em `link pulse/`, actions fixadas por SHA, `contents: read`). `./mvnw -B -ntp verify` verde no Git Bash em caminho com espaço. Gates provados por probe num clone em `scratchpad/link pulse/`: import curinga + tab → `validate` EXIT=1 (`AvoidStarImport`, `FileTabCharacter`); null deref → `verify` EXIT=1 (`NP_ALWAYS_NULL`). `mvnw` 100755/LF no índice, `mvnw.cmd` CRLF, `.gitattributes` com `* text=auto eol=lf`. **Não observado:** execução no GitHub Actions e build no Linux (sem remote) |

### Truths dos planos (frontmatter `must_haves`)

| Plano | Truth (resumo) | Status | Evidência |
|-------|----------------|--------|-----------|
| 01-01 | Clone em caminho com espaço builda no Git Bash **e no Linux**, wrapper 3.9.16 com SHA-256 | ? humano | Git Bash: verde (repo e clone em `link pulse/`); `maven-wrapper.properties` com `distributionSha256Sum`. Linux só via CI |
| 01-01 | Jar sobe e `GET :8081/actuator/health` → UP; API na 8080; management só health,info | ? humano | Config presente (`management.server.port: 8081`, `include: health,info`); nenhum teste cobre a porta 8081. Desde a 01-02 o jar precisa de datasource |
| 01-01 | `mvnw` 100755 + LF; `mvnw.cmd` CRLF com autocrlf=true | ✓ VERIFIED | `git ls-files -s`: `100755 app/mvnw`; `--eol`: `i/lf w/lf` (mvnw), `w/crlf` (mvnw.cmd); `core.autocrlf=true` nesta máquina |
| 01-01 | `mvnw.cmd -v` no PowerShell com espaço no caminho | ✓ VERIFIED | `powershell.exe ... .\mvnw.cmd -v` → `Apache Maven 3.9.16` |
| 01-01 | Violação de estilo quebra `validate`; null deref quebra `verify` | ✓ VERIFIED | Probes executados por este verificador num clone descartável (EXIT=1 nos dois) |
| 01-01 | Push na main e PR disparam o workflow `ci` com os steps exigidos | ? humano | Conteúdo do `ci.yml` confere; execução não observada |
| 01-01 | Actions fixadas por SHA de 40 caracteres, `contents: read`, Dependabot github-actions e maven | ✓ VERIFIED | 3 `uses:` com SHA de 40 hex; `permissions: contents: read`; `dependabot.yml` com os dois ecossistemas |
| 01-01 | `make help` lista os alvos; sem make, os mesmos comandos via `cd app && ./mvnw` | ? humano | Alvos com `##` presentes no Makefile; make não instalado |
| 01-02 | 302 com Location e `Cache-Control` no-store, private | ✓ VERIFIED | `RedirectIT#redirectsToTargetWithoutCache` |
| 01-02 | 404 `problem+json`, `/problems/link-not-found`, propriedade `code` | ✓ VERIFIED | `RedirectIT#unknownCodeIsNotFoundProblem` |
| 01-02 | Expirado (inclusive no limite exato) → 410 com `expiredAt`; futuro continua redirecionando | ✓ VERIFIED | `Link.isExpiredAt` usa `!now.isBefore(expiresAt)`; `RedirectIT#expiredLinkIsGoneProblem`, `#linkExpiringInTheFutureStillRedirects` |
| 01-02 | Lookup case-sensitive (`AbCd-123` vs `abcd-123`) | ✓ VERIFIED | `RedirectIT#lookupIsCaseSensitive` |
| 01-02 | `favicon.ico` e multi-segmento não caem no redirect | ✓ VERIFIED | Regex `[A-Za-z0-9_-]+`; `RedirectIT#pathWithDot...`, `#multiSegment...` |
| 01-02 | Schema só pelo Liquibase, com changesets registrados, e `validate` | ✓ VERIFIED | `001-create-links.yaml` (sequência + `uk_links_code`); `RedirectIT#schemaComesFromLiquibaseChangelog`, `#hibernateOnlyValidatesSchema` |
| 01-02 | `contexts` default prod e `analytics-enabled=false` | ✓ VERIFIED | `application.yml` |
| 01-02 | ITs contra Postgres 18.6 via `@Bean @ServiceConnection` | ✓ VERIFIED | `TestcontainersConfiguration`; 41 ITs verdes com Docker Desktop |
| 01-03 | 201 + Location = shortUrl + campos D-09 | ✓ VERIFIED | `LinkApiIT#createsLinkWithGeneratedCodeAndLocation` |
| 01-03 | shortUrl de `linkpulse.base-url`, nunca de Host/X-Forwarded | ✓ VERIFIED | `LinkController` usa `properties.baseUrl()` |
| 01-03 | Código de 7 alfanuméricos a partir do nextval antes do INSERT, numa transação de escrita | ✓ VERIFIED | `LinkService.create` `@Transactional`; `LinkRepository.nextId` com `nextval('link_id_seq')` |
| 01-03 | Link criado redireciona logo depois | ✓ VERIFIED | `LinkApiIT#createdLinkRedirectsImmediately` |
| 01-03 | Consecutivos sem prefixo comum de 6; lista 1..1000 fora de ordem | ✓ VERIFIED | `CodeGeneratorTest#consecutiveIdsAreNotEnumerable` |
| 01-03 | Ida e volta até 62^7−1; injetivo; limites lançam IAE | ✓ VERIFIED | `CodeGeneratorTest` (19 testes verdes) |
| 01-03 | Contexto não sobe com multiplicador não coprimo ou alfabeto inválido | ✓ VERIFIED | `#contextFailsOnStartupWhenMultiplierIsNotCoprime`, `#constructorRejectsInvalidAlphabets` |
| 01-03 | 8×50 concorrentes → 400 códigos e 400 linhas, sem exceção | ✓ VERIFIED | `ConcurrencyIT` verde (24,8 s) |
| 01-03 | Mesma URL duas vezes → dois links | ✓ VERIFIED | `LinkApiIT#sameUrlTwiceCreatesIndependentLinks` |
| 01-03 | expiresAt com offset devolvido em UTC, truncado em µs | ✓ VERIFIED | `LinkApiIT#expiresAtWithOffsetIsReturnedInUtcTruncatedToMicros` |
| 01-04 | Alias válido → 201 com code = alias, redireciona | ✓ VERIFIED | `LinkApiIT#aliasBecomesTheCodeAndRedirects` |
| 01-04 | Repetido → 409 `/problems/alias-conflict`; 10 simultâneos → 1 sucesso e 9 conflitos | ✓ VERIFIED | `#repeatedAliasIsConflict...`, `#concurrentCreationsOfTheSameAlias...` |
| 01-04 | Fora da regex ou reservado em qualquer caixa → 400 `errors[]` em `alias` | ✓ VERIFIED | `AliasPolicyTest` (17), `LinkApiIT#reservedAlias...`, `#aliasWithoutHyphen...` |
| 01-04 | Alias case-sensitive (`Minha-Promo` ≠ `minha-promo`) | ✓ VERIFIED | `LinkApiIT#aliasIsCaseSensitive` |
| 01-04 | Códigos gerados nunca casam com a regex de alias | ✓ VERIFIED | `AliasPolicyTest#generatedCodesNeverMatchTheAliasRegex` |
| 01-04 | Erros de alias sem SQL, stack, classe ou constraint | ✓ VERIFIED | Assert `doesNotContain("uk_links_code", "SQL", "Exception")` |
| 01-05 | `make run` / comandos sem make sobem tudo; `curl /demo-link` → 302 | ? humano | `LiquibaseContextsIT$DevProfile#demoLinkRedirects` prova via MockMvc; runtime real com compose não observado |
| 01-05 | `db-up` espera healthcheck; Postgres em 127.0.0.1:5432; volume em `/var/lib/postgresql` | ✓ VERIFIED | `compose.dev.yaml` (healthcheck `pg_isready`, porta `127.0.0.1:5432`, volume pai); Makefile `up -d --wait` |
| 01-05 | prod sem seed nem changeset 002; dev com os dois, provado por IT | ✓ VERIFIED | `LiquibaseContextsIT` (default, prod e dev verdes) |
| 01-06 | `ftp://` → 400 `validation-error` com `errors[].field = url` | ✓ VERIFIED | `LinkApiIT#urlOutsideTheAllowList...`, `HttpUrlValidatorTest` (16) |
| 01-06 | Não http/https, com credenciais, > 2048 **ou apontando para o host de `linkpulse.base-url`** → 400; 2048 aceita | ✗ FAILED (parcial) | Allow-list, credenciais e tamanho OK. Host próprio: só bloqueia o mesmo texto de host ignorando caixa. `localhost.` (mesmo FQDN), `127.0.0.1` e `[::1]` passam (reproduzido). Ver Gaps |
| 01-06 | Expiração passada → 400 `expiresAt`; sem offset ou JSON malformado → `malformed-request` | ✓ VERIFIED | `#expiresAtInThePast...`, `#expiresAtWithoutOffset...`, `#malformedJson...` |
| 01-06 | Todo erro em `problem+json` sem vazar internals; inesperado → 500 `internal-error` | ✓ VERIFIED | `GlobalExceptionHandler`; `LinkApiIT$UnexpectedFailure` verde. Ressalvas: WR-02 (entrada do cliente gerando 500) e IN-02 (erros do framework com `about:blank`), ambos ainda em Problem Details |
| 01-06 | Erros anteriores continuam iguais (404/410/409/400 de alias) | ✓ VERIFIED | `RedirectIT` e `LinkApiIT` verdes juntos |
| 01-07 | `/v3/api-docs` com título, paths e respostas; `/swagger-ui/index.html` 200 | ✓ VERIFIED | `OpenApiIT` (6 verdes) |
| 01-07 | README pt-BR com pré-requisitos, make no Windows, comandos sem make, `mvnw.cmd`, curl, erros, ofuscação | ✓ VERIFIED | `README.md` linhas 11-48, 131, 165 (conteúdo presente; clareza fica como item humano) |
| 01-07 | Licença FSL-1.1-ALv2 do Liquibase no README e no PROJECT.md | ✓ VERIFIED | `README.md:193`, `PROJECT.md:105` |

**Placar:** 41/48 truths verificadas (1 falhou parcialmente, 6 dependem de verificação humana, 0 presentes sem comportamento comprovado)

### Proibições (`must_haves.prohibitions`)

| Plano | Proibição | Nível | Disposição |
|-------|-----------|-------|------------|
| 01-03 | Não apresentar o código curto como segredo | judgment | Veredito LLM não autoritativo: respeitada (Javadoc do `CodeGenerator` e `README.md:165`). **unverified-prohibition: revisão humana recomendada** |
| 01-03 | Não logar a URL de destino nem o corpo das criações | judgment | Veredito LLM não autoritativo: respeitada (único log é `LOG.error(..., ex)` no handler de 500, sem request nem corpo; nenhum logging de request configurado). **unverified-prohibition: revisão humana recomendada** |
| 01-04 | Não aceitar alias que sombreie rota ou palavra reservada, em qualquer caixa | test | ✓ Com enforcement: `AliasPolicyTest#rejectsReservedWordsIgnoringCase` e `LinkApiIT#reservedAliasIsValidationErrorIgnoringCase` verdes. Os reservados sem `-`/`_` caem antes na regex, o que também cumpre a proibição (IN-01) |
| 01-05 | Não aplicar o seed `demo-link` no context prod | test | ✓ Com enforcement: `LiquibaseContextsIT$ProdProfile` e o caso default verdes |

### Artefatos obrigatórios

| Artefato | Status | Detalhes |
|----------|--------|----------|
| `.gitattributes` | ✓ VERIFIED | `* text=auto eol=lf`, `mvnw` LF, `*.cmd` CRLF |
| `app/.mvn/wrapper/maven-wrapper.properties` | ✓ VERIFIED | 3.9.16 com `distributionSha256Sum` |
| `app/pom.xml` | ✓ VERIFIED | Boot 4.1.1, Java 21, Checkstyle 14.3.0 em validate, SpotBugs em verify |
| `app/config/checkstyle/checkstyle.xml`, `app/config/spotbugs/exclude.xml` | ✓ VERIFIED | Ligados ao pom; gates provados por probe |
| `.github/workflows/ci.yml` | ✓ VERIFIED (estático) | Execução no GitHub pendente (humano) |
| `Makefile` | ✓ VERIFIED (estático) | Alvos help/build/test/verify/run/db-up/db-down; `cd app && ./mvnw` |
| `001-create-links.yaml`, `002-seed-dev.yaml`, master | ✓ VERIFIED | Sequência, `uk_links_code`, `contextFilter: dev` |
| `Link.java`, `LinkRepository.java`, `LinkService.java`, controllers | ✓ VERIFIED | `Persistable<Long>`, `nextval`, `findByCode`, `resolve(code)` |
| `CodeGenerator.java` | ✓ VERIFIED | `modInverse`, `BigInteger`, ofuscação documentada |
| `AliasPolicy.java` | ✓ VERIFIED | Regex `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$` |
| `GlobalExceptionHandler.java`, `HttpUrl*.java` | ✓ VERIFIED | `extends ResponseEntityExceptionHandler`; allow-list |
| `OpenApiConfig.java`, `README.md` | ✓ VERIFIED | Título `Link Pulse API`; README com os tópicos exigidos |
| Testes (`RedirectIT` 127 linhas, `LinkApiIT` 342, `CodeGeneratorTest` 167, `ConcurrencyIT`, `AliasPolicyTest`, `HttpUrlValidatorTest` 59, `LiquibaseContextsIT`, `OpenApiIT`) | ✓ VERIFIED | Todos acima do `min_lines` e verdes |

### Ligações-chave (wiring)

| De | Para | Via | Status |
|----|------|-----|--------|
| `pom.xml` | `checkstyle.xml` / `exclude.xml` | `configLocation` / `excludeFilterFile` | ✓ WIRED (probes falham o build) |
| `ci.yml` | `app/mvnw` | `./mvnw -B -ntp verify` em `link pulse/app` | ✓ WIRED (estático) |
| `Makefile` | `app/mvnw`, `compose.dev.yaml` | `MVNW`, `COMPOSE`; `run: db-up` | ✓ WIRED |
| `RedirectController` | `LinkService.resolve(code)` → `findByCode` | chamada direta | ✓ WIRED |
| `LinkController` | `LinkService.create` → `codeGenerator.encode(repository.nextId())` | chamada direta | ✓ WIRED |
| `ApplicationConfig` | `LinkPulseProperties` → `new CodeGenerator(` / `new AliasPolicy(` | `@Bean` | ✓ WIRED |
| `LinkService` | `AliasPolicy.check`, `uk_links_code` → `aliasConflict` | chamada + tradução da violação | ✓ WIRED |
| `LinkService` | `properties.baseUrl().getHost()` | `pointsToShortener` | ⚠️ PARTIAL: ligado, mas a comparação é contornável (gap) |
| `application.yml` / `application-dev.yml` | changelog master / `contextFilter: dev` | `change-log`, `contexts: dev` | ✓ WIRED |
| `CreateLinkRequest` | `HttpUrlValidator` | `@HttpUrl` | ✓ WIRED |
| `AbstractIT` | `TestcontainersConfiguration` | `@Import` | ✓ WIRED |

### Fluxo de dados (nível 4)

| Artefato | Dado | Fonte | Dado real | Status |
|----------|------|-------|-----------|--------|
| `RedirectController` | `Location` | `LinkRepository.findByCode` (Postgres) | Sim | ✓ FLOWING |
| `LinkController` | `code`/`shortUrl` | `nextval('link_id_seq')` + `CodeGenerator` + `base-url` | Sim | ✓ FLOWING |
| seed `demo-link` | linha em `links` | Liquibase (só context dev) | Sim | ✓ FLOWING |

### Verificações comportamentais

| Comportamento | Comando | Resultado | Status |
|---------------|---------|-----------|--------|
| Build completo com testes e gates num caminho com espaço (Git Bash) | `cd app && ./mvnw -B -ntp verify` (execução única) | BUILD SUCCESS; 52 unitários + 41 ITs, 0 falhas; 0 violações de Checkstyle; 0 BugInstance | ✓ PASS |
| Checkstyle quebra em violação | Clone em `scratchpad/link pulse/` + `StyleProbe` (import `*` e tab) + `./mvnw validate` | EXIT=1, `AvoidStarImport`, `FileTabCharacter` | ✓ PASS |
| SpotBugs quebra em violação | Mesmo clone + `BugProbe` (null deref) + `./mvnw -DskipTests verify` | EXIT=1, `NP_ALWAYS_NULL` (High) | ✓ PASS |
| `mvnw` executável e LF num clone novo | `test -x mvnw && ! grep -q $'\r' mvnw` | OK | ✓ PASS |
| `mvnw.cmd` no PowerShell com espaço no caminho | `.\mvnw.cmd -v` | Apache Maven 3.9.16 | ✓ PASS |
| Checagem de host próprio (CR-01) | `java Cr01.java` (mesma lógica de `pointsToShortener`) | `LOCALHOST` bloqueado; `localhost.`, `127.0.0.1` e `[::1]` passam no `@HttpUrl` e não são bloqueados | ✗ FAIL |
| Workflow no GitHub Actions | — | Sem remote | ? SKIP (humano) |
| `make help` / `make verify` | — | make não instalado | ? SKIP (humano) |

### Execução de probes

Nenhum `scripts/*/tests/probe-*.sh` existe no repositório, e nenhum plano declara probes. Os "probes" de gate das SUMMARYs (`StyleProbe`/`BugProbe`) foram reexecutados por este verificador (tabela acima), sem confiar na narrativa.

### Cobertura de requisitos

| Requisito | Plano | Descrição | Status | Evidência |
|-----------|-------|-----------|--------|-----------|
| LINK-01 | 01-03, 01-06 | `POST /links` com URL http/https e tamanho máximo, devolve código e URL curta | ✓ SATISFIED | `LinkApiIT` (201, ftp → 400, 2049 → 400, 2048 → 201). O gap do host próprio não faz parte do texto do requisito |
| LINK-02 | 01-03 | Base62 a partir do nextval antes do INSERT, sem colisão | ✓ SATISFIED | `LinkService.create`, `ConcurrencyIT` |
| LINK-03 | 01-03 | Embaralhamento bijetivo | ✓ SATISFIED | `CodeGenerator` + `CodeGeneratorTest` |
| LINK-04 | 01-04 | Alias com regex disjunta, reservados e 409 | ✓ SATISFIED | `AliasPolicy`, ITs de alias e corrida |
| LINK-05 | 01-06 | Expiração opcional, obrigatoriamente futura | ✓ SATISFIED | `@Future`, `#expiresAtInThePast...` (ressalva WR-02: ano > 294276 → 500) |
| LINK-06 | 01-04, 01-06 | Validação e conflito em Problem Details | ✓ SATISFIED | `GlobalExceptionHandler`, `LinkProblems` |
| REDIR-01 | 01-02 | 302 com `Cache-Control` sem cache | ✓ SATISFIED | `RedirectIT#redirectsToTargetWithoutCache` |
| REDIR-02 | 01-02 | 404 e 410 | ✓ SATISFIED | `RedirectIT` |
| DATA-01 | 01-02, 01-05 | Liquibase YAML com contexts dev/prod; `validate` | ✓ SATISFIED | Changelogs, `LiquibaseContextsIT`, `RedirectIT` |
| QUAL-01 | 01-03 | Unitários do gerador (ida e volta, unicidade) | ✓ SATISFIED | `CodeGeneratorTest` (19 verdes) |
| QUAL-03 | 01-01 | Checkstyle e SpotBugs quebram em violação | ✓ SATISFIED | Probes reexecutados (EXIT=1 nos dois) |
| QUAL-04 | 01-07 | OpenAPI/Swagger UI | ✓ SATISFIED | `OpenApiIT` |
| CONT-03 | 01-01, 01-05, 01-07 | Windows (Git Bash/WSL) e Linux, LF, `mvnw` executável, espaço no caminho, instrução do make | ? NEEDS HUMAN | Windows verificado (Git Bash + PowerShell com espaço); LF/100755 verificados; README com winget/scoop/WSL. Linux só via CI, e `make` não foi testado |
| CI-01 | 01-01 | GitHub Actions roda build, testes, Checkstyle e SpotBugs em cada push/PR | ? NEEDS HUMAN | Workflow correto no repositório; nunca rodou (sem remote). O REQUIREMENTS.md marca `Complete`, o que antecipa uma evidência ainda inexistente |

Requisitos órfãos: nenhum. Os 14 IDs mapeados para a Phase 1 no REQUIREMENTS.md aparecem no campo `requirements` de algum plano.

### Anti-padrões encontrados

| Arquivo | Linha | Padrão | Severidade | Impacto |
|---------|-------|--------|------------|---------|
| — | — | `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` | — | Nenhum marcador de dívida nos arquivos da fase |
| `app/src/main/java/dev/linkpulse/link/LinkService.java` | 112-115 | Checagem de segurança contornável (CR-01) | 🛑 Blocker | Quebra a truth de host próprio da 01-06 e a garantia afirmada no README |
| `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java` | 28 | `base-url` sem validação de host/esquema (WR-01) | ⚠️ Warning | Configuração errada desliga a checagem de host e gera shortUrl relativa |
| `app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java` | 27 | `expiresAt` fora do intervalo do `timestamptz` → 500 (WR-02) | ⚠️ Warning | Entrada do cliente vira 500 + log ERROR (ainda em Problem Details) |
| `CreateLinkRequest.java` / `GlobalExceptionHandler.java` | 21, 27 / 55-61 | `errors[].message` depende do `Accept-Language` (WR-03) | ⚠️ Warning | Contrato com idiomas misturados |
| `CreateLinkRequest.java`, `LinkController.java`, `README.md` | 26, 56, 96, 132 | Exemplo `expiresAt` 2026-12-31 (WR-04) | ⚠️ Warning | "Try it out" e curl do README dão 400 a partir de 2027-01-01, antes do público-alvo ler |
| `application.yml` / `README.md` | 38-50 / 128 | 10 de 12 reservados inalcançáveis pela regex (IN-01) | ℹ️ Info | README superestima a lista |
| `.github/workflows/ci.yml` | 12-14 | `cancel-in-progress: true` também na main (IN-08) | ℹ️ Info | Commit da main pode ficar sem resultado de CI |

### Verificação humana necessária

1. **CI no GitHub (CI-01, SC5, CONT-03 Linux).** Criar o repositório, `git remote add origin`, push da main e um PR. Esperado: o job `build-test` fica verde com checkout em `link pulse/`. Por que humano: não há remote; é também a única prova do build no Linux.
2. **make no Git Bash.** Instalar o make e rodar `make help` e `make verify`. Esperado: alvos listados e BUILD SUCCESS. Por que humano: make não está instalado.
3. **Runtime local.** `make run` (ou os comandos sem make), `curl -i :8080/demo-link`, `curl :8081/actuator/health`, `make db-down`. Esperado: 302 para `https://example.com/`, health UP na 8081, volume preservado. Por que humano: exige subir servidor e compose.
4. **README e Swagger UI.** Seguir o README do zero e criar links pela Swagger UI. Esperado: o README basta e os erros aparecem em Problem Details. Por que humano: clareza e UI. Atenção ao WR-04.
5. **Proibições judgment da 01-03.** Confirmar o veredito não autoritativo (código não é segredo; sem log de URL ou corpo).

### Resumo dos gaps

O núcleo do objetivo foi entregue e comprovado pela execução real da suíte: criação com código gerado não enumerável e sem colisão, alias com 409 e corrida, redirect 302/404/410 a partir do Postgres, Liquibase com contexts, `validate`, Swagger UI e gates de qualidade que de fato quebram o build. Os 4 primeiros critérios de sucesso do roadmap estão verificados.

**Gap bloqueante (1, parcial): checagem de host próprio contornável (CR-01).** A truth da 01-06 exige 400 para URL que aponta para o host de `linkpulse.base-url`, e o threat model (T-06-03), o 01-06-SUMMARY e o README (linhas 131 e 180) afirmam que isso fecha o loop de redirect. Reproduzi com `java.net.URI`: `http://localhost.:8080/...` (o mesmo host em forma FQDN), `127.0.0.1` e `[::1]` passam. Em prod vale o mesmo para `https://<base>./x`. Nenhuma fase futura do roadmap cobre o tema, então o gap não foi adiado. Mesma raiz do WR-01 (`base-url` sem validação desliga a checagem em silêncio). A correção é pequena: normalizar o host, usar um conjunto de hosts próprios, validar `base-url` na subida e acrescentar ITs com essas variações.

**Isto parece uma escolha aceitável se o escopo for reduzido.** Se o desenvolvedor decidir que mitigação parcial basta nesta fase, ajuste antes o README e o threat model para "mitigação parcial" e depois registre um override no frontmatter:

```yaml
overrides:
  - must_have: "URL que aponta para o host de linkpulse.base-url responde 400 com errors[] no campo url"
    reason: "Mitigação parcial aceita na Phase 1: bloqueia o host exato do base-url (sem diferenciar caixa); FQDN com ponto final, IPs de loopback e hostnames alternativos ficam para depois. README e threat model ajustados para 'mitigação parcial'."
    accepted_by: "<nome>"
    accepted_at: "<ISO timestamp>"
```

**Pendências humanas (não bloqueiam por si, mas impedem `passed`):** o SC5/CI-01 depende de um push para o GitHub, que nunca aconteceu. O REQUIREMENTS.md marca CI-01 e CONT-03 como `Complete` antes dessa evidência existir. O `make` e o runtime local com compose também não foram observados.

---

_Verificado em: 2026-10-08T23:39:35Z_
_Verificador: Claude (gsd-verifier)_
