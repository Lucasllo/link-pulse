---
phase: 01-funda-o-e-n-cleo-de-links
verified: 2026-10-09T23:00:29Z
status: gaps_found
score: 48/55 must-haves verified (SC do roadmap 4/5; truths dos planos 01-01 a 01-07 38/43; truths da 01-08 6/7)
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
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-08-PLAN.md
  - .planning/phases/01-funda-o-e-n-cleo-de-links/01-08-SUMMARY.md
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
  - app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java
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
  - app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java
  - app/src/test/java/dev/linkpulse/link/AliasPolicyTest.java
  - app/src/test/java/dev/linkpulse/link/CodeGeneratorTest.java
  - app/src/test/java/dev/linkpulse/link/ConcurrencyIT.java
  - app/src/test/java/dev/linkpulse/link/LinkApiIT.java
  - app/src/test/java/dev/linkpulse/link/RedirectIT.java
  - app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java
  - compose.dev.yaml
covered_digest: "v1:sha256:d77a2903f9b56419481b55031d7b833c7c78fbff51e2df6c3133ed8379548653"
behavior_unverified: 0
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 41/48
  gaps_closed:
    - "URL que aponta para o host de linkpulse.base-url responde 400 com errors[] no campo url (01-06, mitigação T-06-03 contra loop de redirect)"
  gaps_remaining: []
  regressions: []
gaps:
  - truth: "SC5 / CI-01 / truth da 01-01: cada push na branch principal e cada PR disparam o workflow ci no GitHub Actions"
    status: partial
    reason: "O repositório agora tem remote (origin = github.com/Lucasllo/link-pulse), e a branch principal do remoto é master (git ls-remote --symref origin HEAD -> refs/heads/master; não existe refs/heads/main). O ci.yml dispara em push só para branches: [main], então nenhum push na branch principal roda o CI. Só os pull requests disparam. A API pública do GitHub (GET /repos/Lucasllo/link-pulse/actions/runs) devolve total_count 0: o workflow nunca rodou. O README (linha 196) promete 'roda em todo push na main', uma branch que não existe. Nenhuma fase posterior cobre o tema (o CI-02 da fase de imagem também assume 'main')."
    artifacts:
      - path: ".github/workflows/ci.yml"
        issue: "on.push.branches = [main]; a branch padrão do remoto e do git local (init.defaultBranch) é master"
      - path: "README.md"
        issue: "Linha 196: 'roda em todo push na main'"
    missing:
      - "Decisão do desenvolvedor (escalonamento): (a) renomear a branch padrão para main no GitHub e no clone local, sem mudar código; ou (b) trocar o filtro para branches: [master] (ou [main, master]) e ajustar o README"
      - "Depois disso, observar uma execução verde do workflow ci (push na branch principal ou PR) na aba Actions. Essa execução é também a única prova do build no Linux"
human_verification:
  - test: "Depois de corrigir o gap do filtro de branch: push na branch principal e abrir um PR do branch da fase para ela; conferir a aba Actions"
    expected: "O workflow ci roda nos dois eventos e o job build-test termina verde: checkout em 'link pulse/', step 'mvnw é executável e LF', 'make -n verify && make help' e './mvnw -B -ntp verify' (com ITs Testcontainers) no ubuntu-latest com Java 21 Temurin"
    why_human: "Nunca houve execução no GitHub (0 runs). É a única prova do build no Linux (SC5, CI-01, CONT-03)"
  - test: "Com o Docker Desktop ligado, rodar na raiz: cd app && ./mvnw -B -ntp verify"
    expected: "BUILD SUCCESS com 103 unitários e 50 ITs (LinkApiIT com trailingDotOnTheShortenerHostIsRejectedAndNoLoopIsCreated, loopbackOrObfuscatedHostIsValidationErrorOnUrl [7] e publicIpv4LiteralIsAccepted; RedirectIT, ConcurrencyIT, LiquibaseContextsIT e OpenApiIT sem regressão)"
    why_human: "O Docker estava desligado durante esta verificação e o verificador não sobe serviços. Unitários, Checkstyle e SpotBugs foram rodados por este verificador (verdes). O verde dos ITs vem só dos relatórios do Failsafe do executor (50/50, 2026-10-08 22:22), que não valem como evidência própria"
  - test: "Instalar o GNU make (winget install ezwinports.make ou scoop install make) e, no Git Bash, rodar make help e make verify na raiz"
    expected: "make help lista help/build/test/verify/run/db-up/db-down; make verify termina com BUILD SUCCESS"
    why_human: "make continua ausente nesta máquina (truth 8 da 01-01, CONT-03)"
  - test: "Com o Docker ligado: make run (ou docker compose -f compose.dev.yaml up -d --wait e depois cd app && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev); curl -i http://localhost:8080/demo-link; curl http://localhost:8081/actuator/health; make db-down"
    expected: "demo-link responde 302 para https://example.com/ com Cache-Control no-store, private; o health na 8081 responde UP; a 8080 não expõe /actuator; db-down mantém o volume pgdata"
    why_human: "Exige subir o servidor e o Postgres do compose (truth 2 da 01-01, truth 1 da 01-05)"
  - test: "Seguir o README do zero e abrir http://localhost:8080/swagger-ui.html; criar links com e sem alias pelo 'Try it out'; ler a regra de URL e o bullet 'Loop de redirect via DNS (mitigação parcial)'"
    expected: "Swagger UI carrega, cria links (201) e mostra erros em Problem Details; o README basta sem conhecimento prévio e descreve com fidelidade o que a regra de host bloqueia"
    why_human: "Clareza editorial e experiência na UI (human-check da 01-07 e item D5 da 01-08). Atenção ao WR-04: o exemplo de expiresAt 2026-12-31 passa a dar 400 a partir de 2027-01-01"
  - test: "Revisar as proibições de nível judgment: 01-03 (código curto não é segredo; nenhum log da URL de destino ou do corpo) e 01-08 (a checagem de host não resolve DNS)"
    expected: "Confirmar o veredito não autoritativo deste verificador: as três respeitadas"
    why_human: "unverified-prohibition, revisão humana recomendada (proibições judgment-tier nunca passam em silêncio)"
---

# Phase 1: Fundação e núcleo de links — Relatório de Verificação

**Objetivo da fase:** Um cliente cria links curtos (gerados ou com alias) e é redirecionado para a URL original a partir do Postgres, num repositório que builda igual no Windows e no Linux, com CI verde desde o início
**Verificado em:** 2026-10-09T23:00:29Z
**Status:** gaps_found
**Re-verificação:** Sim, depois do plano de fechamento de gap 01-08

> **Nota sobre o modo MVP.** O ROADMAP marca a fase com `Mode: mvp`, mas o objetivo não está no formato de user story. Como na verificação inicial, segui a verificação goal-backward padrão a pedido do orquestrador. Recomendação mantida: reescrever o objetivo com `/gsd mvp-phase 1` ou tirar `mode: mvp` da fase.

## Resumo da re-verificação

| Item | Antes | Agora |
|------|-------|-------|
| Gap CR-01 (checagem do próprio host contornável) | ✗ FAILED (parcial) | ✓ VERIFIED (fechado) |
| WR-01 (base-url sem host desliga a checagem em silêncio) | ⚠️ Warning | ✓ resolvido (a subida falha) |
| SC5 / CI-01 (CI no GitHub) | ? humano (sem remote) | ✗ FAILED (parcial): o remote existe, a branch principal é `master` e o `ci.yml` só dispara push em `main`; 0 execuções |
| Regressões nos arquivos tocados pela 01-08 | — | Nenhuma nos unitários, Checkstyle e SpotBugs (rodados aqui). ITs não reexecutados (Docker desligado) |

## Cobertura do fluxo do usuário (resumo)

| Passo | Esperado | Evidência | Status |
|-------|----------|-----------|--------|
| Cliente cria link com código gerado | 201, código de 7 caracteres, shortUrl e Location | Sem mudança desde a verificação inicial (`LinkApiIT`, `ConcurrencyIT`); `LinkService.create` só ganhou a chamada `targetHostPolicy.check` na primeira linha | ✓ |
| Cliente cria link com alias | 201; repetido → 409 | Sem mudança no caminho do alias | ✓ |
| Cliente não consegue criar link em loop pelo próprio host | 400 no campo `url` | `TargetHostPolicyTest` (37 verdes, rodado aqui) e probe próprio sobre as classes compiladas | ✓ |
| Cliente acessa `/{code}` | 302/404/410 a partir do Postgres | `RedirectController`/`resolve` intocados pela 01-08 | ✓ |
| Builda igual no Windows e no Linux | `./mvnw verify` verde nos dois | Windows: verde (Git Bash, caminho com espaço). Linux: só via CI, que nunca rodou | ? humano |
| CI verde desde o início | Workflow verde no GitHub | Remote existe, mas o filtro `branches: [main]` não casa com `master`; 0 execuções | ✗ gap |

## Atingimento do objetivo

### Critérios de sucesso do roadmap (contrato)

| # | Critério | Status | Evidência |
|---|----------|--------|-----------|
| SC1 | `POST /links` retorna código e URL curta; não enumerável; concorrência sem duplicata | ✓ VERIFIED | Inalterado. `CodeGeneratorTest` (19) verde nesta execução; `ConcurrencyIT` verde na verificação inicial e o caminho do gerador não mudou |
| SC2 | Alias válido; 409; reservado/regex/URL inválida/expiração passada → erro de validação em Problem Details | ✓ VERIFIED | Agora sem a ressalva do host próprio: `TargetHostPolicy.check` lança `LinkProblems.invalidField("url", ...)`, o mesmo `validation-error` com `errors[]` dos demais campos (`TargetHostPolicyTest#assertRejected` confere status 400, type, `field = url` e mensagem) |
| SC3 | `GET /{code}` → 302 sem cache; 404; 410 | ✓ VERIFIED | Inalterado (`RedirectController`, `LinkService.resolve`) |
| SC4 | Liquibase YAML com contexts, `validate`, Swagger UI | ✓ VERIFIED | Inalterado; `application.yml` só ganhou comentários |
| SC5 | Cada push/PR roda build, testes, Checkstyle e SpotBugs no GitHub Actions; mesmo build no Git Bash/WSL e no Linux | ✗ FAILED (parcial) | Parte local continua verificada (gates quebram o build, `mvnw` 100755/LF, caminho com espaço). Fato novo: `git ls-remote --symref origin HEAD` → `refs/heads/master`, sem `main`; `ci.yml` linha 5 `branches: [main]`; `GET /repos/Lucasllo/link-pulse/actions/runs` → `total_count: 0`. Push na branch principal não roda CI. Ver Gaps |

### Truths da 01-08 (gap closure)

| # | Truth (resumo) | Status | Evidência |
|---|----------------|--------|-----------|
| 1 | `http://localhost.:8080/loop-a` com alias → 400 `url` "não pode apontar para o próprio encurtador"; `GET /loop-a` → 404 | ✓ VERIFIED | `normalizeHost` (Locale.ROOT, remove pontos finais); probe próprio: `localhost.`, `LOCALHOST`, `HTTP://LOCALHOST./x` e `localhost:80` → 400 com `SELF_MESSAGE`. `targetHostPolicy.check(request.url())` é a primeira linha de `create`, antes de `existsByCode`, `nextId` e `saveAndFlush`, então nenhum link é gravado. IT `trailingDotOnTheShortenerHostIsRejectedAndNoLoopIsCreated` existe e está verde no relatório do executor; não reexecutado aqui (ver humano) |
| 2 | Loopback recusado sem DNS e para qualquer base-url: 127.0.0.0/8, 0.0.0.0, `[::1]`, `[::]`, `[::ffff:127.0.0.1]`, `localhost`, `*.localhost` | ✓ VERIFIED | `rejectsLoopbackDestinations` (11 vetores) verde aqui; probe com base `http://localhost:8080` e `http://10.0.0.5:8080`. Ressalva WR-A: `[::1%25lo]` passa (fail-open em zone ID), forma fora da lista da truth |
| 3 | Host numérico fora de `a.b.c.d` → 400; IPv4 público canônico → 201 | ✓ VERIFIED | `rejectsNonCanonicalNumericHosts` (5) e `acceptsOtherDestinations` (11) verdes; probe: `0`, `0x`, `017700000001`, `127.0.0.01` recusados; `1.1.1.1`, `1e100.net`, `123.com` aceitos |
| 4 | `linkpulse.self-hosts` / `LINKPULSE_SELF_HOSTS` com a mesma normalização; vazio por padrão | ✓ VERIFIED | `LinkPulsePropertiesTest#selfHostsBindFromCommaSeparatedProperty`, `#selfHostsDefaultToEmpty`, `#environmentVariablesBindBaseUrlAndSelfHosts` verdes aqui; `TargetHostPolicyTest` usa `ALIAS.example.com.` e item em branco |
| 5 | base-url inválida derruba a subida com `IllegalArgumentException` citando `linkpulse.base-url` sem ecoar o valor | ✓ VERIFIED | `#contextFailsOnStartupWhenBaseUrlIsInvalid` (7) e `#startupFailureDoesNotEchoTheBaseUrl` verdes aqui. Probe próprio com `ApplicationContextRunner`: `http://user:senha@lnk.example.com`, `.../a b` e `lnk example.com` falham, e nenhuma mensagem da cadeia contém `senha` |
| 6 | README descreve exatamente o que é bloqueado e declara a mitigação parcial por DNS | ✓ VERIFIED | `README.md:131-136` (três regras + "não resolve DNS"), `:176-177` (base-url e `LINKPULSE_SELF_HOSTS`), `:187` ("Loop de redirect via DNS (mitigação parcial)"); `grep -c 'o que evita loop de redirect'` = 0; `2026-12-31` = 3 (WR-04 intocado). Clareza editorial fica no item humano do README |
| 7 | Nada regride: `LOCALHOST` → 400, `example.com` → 201, `./mvnw -B -ntp verify` BUILD SUCCESS | ? UNCERTAIN (humano) | Rodado aqui: `./mvnw -B -ntp -DskipITs verify` → BUILD SUCCESS, 103 unitários, 0 violações de Checkstyle, `BugInstance size is 0`. Os ITs não rodaram (Docker desligado); o Failsafe do executor mostra 50/50 sem falha, mas é evidência do executor. Nenhum IT usa destino que a nova política recusaria (só `example.com` e o IPv4 público) e nenhuma config sobrescreve `base-url` com valor inválido |

### Truths dos planos 01-01 a 01-07 (regressão rápida)

Os arquivos de main e teste dessas truths não mudaram desde a verificação inicial (`git diff --stat f1c10b2 HEAD` só lista os 9 arquivos da 01-08). Mudanças no status:

| Plano | Truth | Antes | Agora | Evidência |
|-------|-------|-------|-------|-----------|
| 01-06 | Não http/https, credenciais, > 2048 **ou host de `linkpulse.base-url`** → 400 | ✗ FAILED (parcial) | ✓ VERIFIED | Gap fechado (truths 1 a 5 da 01-08). `pointsToShortener` e `baseUrl().getHost()` não existem mais em `app/src/main` |
| 01-01 | Push na main e PR disparam o workflow `ci` | ? humano | ✗ FAILED (parcial) | Mesma raiz do SC5: não existe `main` no remoto |

As demais continuam como na verificação inicial: 37 VERIFIED (agora 38 com a 01-06) e 4 humanas (build no Linux, jar + health na 8081, `make help`, `make run` + curl do seed).

**Placar:** 48/55 truths verificadas (SC 4/5, planos 01-01 a 01-07 38/43, 01-08 6/7). 2 falharam pela mesma raiz (filtro de branch do CI), 5 dependem de verificação humana, 0 presentes sem comportamento comprovado.

### Proibições (`must_haves.prohibitions`)

| Plano | Proibição | Nível | Disposição |
|-------|-----------|-------|------------|
| 01-03 | Não apresentar o código curto como segredo | judgment | Veredito LLM não autoritativo: respeitada. **unverified-prohibition: revisão humana recomendada** |
| 01-03 | Não logar a URL de destino nem o corpo das criações | judgment | Veredito LLM não autoritativo: respeitada (a 01-08 não acrescentou log). **unverified-prohibition: revisão humana recomendada** |
| 01-04 | Não aceitar alias que sombreie rota ou reservado | test | ✓ Com enforcement (inalterado) |
| 01-05 | Não aplicar o seed no context prod | test | ✓ Com enforcement (inalterado) |
| 01-08 | Não resolver DNS ao checar o host de destino | judgment | Veredito LLM não autoritativo: respeitada. Uma única chamada `InetAddress.getByName(` fora de comentários (`TargetHostPolicy.java:135`), só para host entre colchetes; `getAllByName` = 0. Escopo com nome (`%25lo`) cai em busca de interface local, não em DNS. **unverified-prohibition: revisão humana recomendada** |
| 01-08 | Não ecoar o valor de `linkpulse.base-url` na exceção de subida | test | ✓ Com enforcement: `LinkPulsePropertiesTest#startupFailureDoesNotEchoTheBaseUrl` verde aqui (percorre a cadeia de causas) + probe próprio com valores não parseáveis. Ressalva (IN-01 da revisão): o `BindFailureAnalyzer` pode imprimir `Value:` no log de uma subida real em caminho de conversão; fora da letra da proibição (mensagem da exceção) |

### Artefatos obrigatórios (01-08)

| Artefato | Status | Detalhes |
|----------|--------|----------|
| `app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java` | ✓ VERIFIED | 169 linhas; `public final class`, três mensagens, `CANONICAL_IPV4`, `normalizeHost`, `isLoopback`, `isNonCanonicalNumeric`, `check` |
| `app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java` | ✓ VERIFIED | 127 linhas (min 70); 37 casos verdes aqui |
| `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java` | ✓ VERIFIED | `selfHosts` + construtor compacto com mensagem fixa `linkpulse.base-url deve ser ...` |
| `app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java` | ✓ VERIFIED | 127 linhas (min 50); 14 casos verdes aqui |
| `app/src/test/java/dev/linkpulse/link/LinkApiIT.java` | ✓ VERIFIED (estático) | Contém `localhost.:8080/loop-a`, o parametrizado com 7 URLs e `publicIpv4LiteralIsAccepted`; execução pendente (humano) |
| `README.md` | ✓ VERIFIED | Contém `mitigação parcial`, `não resolve DNS` e 3 menções a `LINKPULSE_SELF_HOSTS` |

`gsd query verify.artifacts 01-08-PLAN.md` → 6/6 passed.

### Ligações-chave (wiring)

| De | Para | Via | Status |
|----|------|-----|--------|
| `LinkService.create` | `TargetHostPolicy.check` | `targetHostPolicy.check(request.url())` na linha 74, primeira do método | ✓ WIRED |
| `ApplicationConfig` | `LinkPulseProperties` → `TargetHostPolicy` | `new TargetHostPolicy(properties.baseUrl(), properties.selfHosts())` | ✓ WIRED |
| `LinkPulseProperties` | `TargetHostPolicy` | base-url validada no binding antes do bean (o contexto falha antes de criar a política) | ✓ WIRED |
| `LinkController` | `LinkService.create` | `@Valid` (`@HttpUrl`, `@Size`) antes do service, então `URI.create` em `check` só recebe URL parseável | ✓ WIRED |
| `ci.yml` | branch principal do remoto | `on.push.branches: [main]` | ✗ NOT_WIRED: o remoto só tem `master` |

`gsd query verify.key-links 01-08-PLAN.md` → 3/3 verified. O key_link antigo da 01-06 (`baseUrl().getHost()`) foi substituído; nenhum resquício em `app/src/main`.

### Fluxo de dados (nível 4)

| Artefato | Dado | Fonte | Dado real | Status |
|----------|------|-------|-----------|--------|
| `TargetHostPolicy.selfHosts` | conjunto de hosts próprios | `linkpulse.base-url` + `linkpulse.self-hosts` (yml/env) via `LinkPulseProperties` | Sim (binding por env provado em `#environmentVariablesBindBaseUrlAndSelfHosts`) | ✓ FLOWING |
| `LinkController` | `shortUrl` | `properties.baseUrl()` validada na subida | Sim | ✓ FLOWING |

### Verificações comportamentais

| Comportamento | Comando | Resultado | Status |
|---------------|---------|-----------|--------|
| Unitários + Checkstyle + SpotBugs (execução única) | `cd app && ./mvnw -B -ntp -DskipITs verify` | BUILD SUCCESS; 103 testes (TargetHostPolicyTest 37, LinkPulsePropertiesTest 14, CodeGeneratorTest 19, AliasPolicyTest 17, HttpUrlValidatorTest 16); 0 violações; `BugInstance size is 0` | ✓ PASS |
| Probe do CR-01 refeito (`@HttpUrl` + `TargetHostPolicy` compilados, base `http://localhost:8080`) | `java HostProbe.java http://localhost:8080` | `localhost.`, `127.0.0.1`, `[::1]`, `0x7f000001`, `[0:0:0:0:0:ffff:127.0.0.1]`, `[0000::1]`, `0`, `0x` → 400; `127.1`, `0.0.0.0.`, `loc%61lhost`, `ⓛocalhost` → 400 `@HttpUrl`; `example.com`, `1.1.1.1` → aceitos | ✓ PASS |
| Desvios fora do contrato (mesmo probe) | idem, e base `http://10.0.0.5:8080` | `[::1%25lo]` aceito (fail-open); `[::127.0.0.1]` aceito; com base IP literal, `[::ffff:a00:5]` aceito | ⚠️ Warning |
| base-url com credenciais não é ecoada | `java EchoProbe.java` (`ApplicationContextRunner`) | 3 valores falham na subida; nenhuma mensagem da cadeia contém `senha` | ✓ PASS |
| Execução do CI no GitHub | `curl api.github.com/repos/Lucasllo/link-pulse/actions/runs` | `total_count: 0` | ✗ FAIL (gap) |
| ITs com Testcontainers | — | Docker desligado; o verificador não sobe serviços | ? SKIP (humano) |
| `make help` / `make verify` | `command -v make` | ausente | ? SKIP (humano) |

### Execução de probes

Nenhum `scripts/*/tests/probe-*.sh` existe no repositório, e nenhum plano declara probes. Os probes acima foram escritos e rodados por este verificador no scratchpad, fora do repositório.

### Cobertura de requisitos

| Requisito | Plano | Status | Evidência |
|-----------|-------|--------|-----------|
| LINK-01 | 01-03, 01-06, 01-08 | ✓ SATISFIED | Validação de URL completa, agora com a regra de host (truths 1 a 5 da 01-08) |
| LINK-02 | 01-03 | ✓ SATISFIED | Inalterado |
| LINK-03 | 01-03 | ✓ SATISFIED | Inalterado; `CodeGeneratorTest` verde aqui |
| LINK-04 | 01-04 | ✓ SATISFIED | Inalterado; `AliasPolicyTest` verde aqui |
| LINK-05 | 01-06 | ✓ SATISFIED | Inalterado (ressalva WR-02 antiga: ano > 294276 → 500) |
| LINK-06 | 01-04, 01-06, 01-08 | ✓ SATISFIED | Erros de host no mesmo `validation-error` com `errors[]` |
| REDIR-01 | 01-02 | ✓ SATISFIED | Inalterado |
| REDIR-02 | 01-02 | ✓ SATISFIED | Inalterado |
| DATA-01 | 01-02, 01-05 | ✓ SATISFIED | Inalterado |
| QUAL-01 | 01-03 | ✓ SATISFIED | 19 verdes aqui |
| QUAL-03 | 01-01 | ✓ SATISFIED | Checkstyle e SpotBugs rodaram nesta execução; probes de quebra provados na verificação inicial e configuração inalterada |
| QUAL-04 | 01-07 | ✓ SATISFIED | Inalterado |
| CONT-03 | 01-01, 01-05, 01-07 | ? NEEDS HUMAN | Windows verificado; Linux só via CI (nunca rodou); `make` não testado |
| CI-01 | 01-01 | ✗ BLOCKED | Workflow nunca rodou; o filtro `push: [main]` não casa com a branch principal `master` |

Requisitos órfãos: nenhum. Os 14 IDs da Phase 1 aparecem no campo `requirements` de algum plano.

### Anti-padrões encontrados

| Arquivo | Linha | Padrão | Severidade | Impacto |
|---------|-------|--------|------------|---------|
| arquivos da 01-08 | — | `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` | — | Nenhum marcador |
| `.github/workflows/ci.yml` | 5 | `branches: [main]` com branch principal `master` | 🛑 Blocker | Push na branch principal nunca roda CI (SC5, CI-01). Evidência determinística: `git ls-remote`, API do GitHub com 0 runs |
| `app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java` | 132-140 | **WR-A:** fail-open em `UnknownHostException` | ⚠️ Warning | `http://[::1%25lo]/x` é aceito. Navegadores rejeitam zone ID, mas `curl -L` segue para `::1`; em dev é loop. Correção: recusar literal com `%` e tratar exceção como recusa (01-REVIEW WR-01) |
| `TargetHostPolicy.java` | 162 | **WR-B:** "próprio encurtador" compara texto | ⚠️ Warning | Com `base-url` em IP literal (`http://10.0.0.5:8080`), `[::ffff:a00:5]` passa e o navegador faz loop por dual-stack. Nenhuma config do repositório usa base-url em IP hoje; relevante para kind/ECS se alguém usar IP. Correção: comparar `InetAddress` dos literais (01-REVIEW WR-02) |
| `LinkPulseProperties.java` / `TargetHostPolicy.java` | 60 / 76-82 | **WR-C:** itens de `self-hosts` sem validação | ⚠️ Warning | `https://lb...`, `lb:443`, `*.example.com` nunca casam e desligam a proteção em silêncio, o mesmo defeito que a 01-08 corrigiu na base-url (01-REVIEW WR-03; por leitura de código) |
| `app/src/main/resources/application.yml` | 30 | base-url default `localhost` também em prod | ℹ️ Info | Esquecer `LINKPULSE_BASE_URL` em prod passa sem aviso (01-REVIEW IN-02) |
| `LinkApiIT.java` | 261-275 | IT parametrizado não confere a mensagem | ℹ️ Info | Não prova qual regra recusou (01-REVIEW IN-03) |
| `CreateLinkRequest.java`, `LinkController.java`, `README.md` | — | Exemplo `expiresAt` 2026-12-31 (WR-04 antigo) | ⚠️ Warning | Passa a dar 400 em 2027-01-01, menos de 3 meses a partir de hoje |
| Antigos WR-02, WR-03, IN-01, IN-08 | — | Fora do escopo da 01-08 | ⚠️/ℹ️ | Continuam abertos, como na verificação inicial |

### Advisory (escopo novo, sem evidência determinística)

| # | Achado | Categoria | Por que advisory |
|---|--------|-----------|------------------|
| 1 | `[::127.0.0.1]` (IPv4-compatible, obsoleto) é aceito | security | Reproduzido no probe, mas nenhum SO moderno roteia `::7f00:1` para loopback; sem caminho de loop demonstrado |
| 2 | `localhost.localdomain` e `127.0.0.1.nip.io` são aceitos | security | Dependem de `/etc/hosts` ou DNS; já cobertos pela ressalva "mitigação parcial" do README (T-08-06, aceito) |

### Verificação humana necessária

1. **CI no GitHub (depois de fechar o gap).** Push na branch principal e um PR; o job `build-test` precisa ficar verde. É a única prova do build no Linux.
2. **Verify completo com Docker.** `cd app && ./mvnw -B -ntp verify`; esperado 103 unitários + 50 ITs, BUILD SUCCESS. Os ITs da 01-08 não foram reexecutados por este verificador.
3. **make no Git Bash.** Instalar o make e rodar `make help` e `make verify`.
4. **Runtime local.** `make run`, `curl -i :8080/demo-link` (302), `curl :8081/actuator/health` (UP), `make db-down` (volume preservado).
5. **README e Swagger UI.** Seguir o README do zero, criar links pela Swagger UI e julgar a fidelidade da regra de host e do bullet de mitigação parcial.
6. **Proibições judgment.** 01-03 (código não é segredo; sem log de URL ou corpo) e 01-08 (sem DNS).

### Resumo dos gaps

**O gap da verificação anterior fechou.** O `TargetHostPolicy` normaliza o host, compara com `base-url` + `LINKPULSE_SELF_HOSTS`, recusa loopback e IP numérico ofuscado sem DNS, e está ligado na primeira linha de `LinkService.create`. Refiz o probe do CR-01 contra as classes compiladas: `localhost.`, `127.0.0.1`, `[::1]` e `0x7f000001` agora dão 400 no campo `url`. Uma `base-url` inválida derruba a subida sem ecoar o valor, e o README não promete mais o fechamento do loop. Unitários, Checkstyle e SpotBugs estão verdes nesta execução. Não houve regressão nos arquivos que não foram tocados.

**Gap novo e bloqueante (1, parcial): o CI não dispara na branch principal.** Desde a verificação inicial, o repositório ganhou remote (`github.com/Lucasllo/link-pulse`), e a branch padrão é `master`. O `ci.yml` dispara push só em `main`, e o README promete "todo push na `main`". A API do GitHub mostra 0 execuções do workflow. O critério 5 do roadmap ("cada push/PR roda...") e o "CI verde desde o início" do objetivo não se cumprem como está. **Esta decisão é do desenvolvedor:**
- (a) renomear a branch padrão para `main` (no GitHub e no clone). Não muda código e mantém o README e o CI-02, que também falam em `main`;
- (b) trocar o filtro para `[master]` (ou `[main, master]`) e ajustar o README.

Nos dois casos, a fase só fecha depois de uma execução verde observada na aba Actions.

**Warnings para decidir (não bloqueiam):** WR-A (zone ID `[::1%25lo]` passa por fail-open) e WR-B (base-url em IP literal aceita a forma IPv4-mapped do mesmo IP) são desvios que reproduzi e que ficam fora das formas enumeradas nas truths. WR-C (`self-hosts` mal escrito desliga a proteção em silêncio) repete o defeito que a 01-08 corrigiu na base-url. As três correções são pequenas e estão descritas no 01-REVIEW.md.

---

_Verificado em: 2026-10-09T23:00:29Z_
_Verificador: Claude (gsd-verifier)_
