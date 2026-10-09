---
phase: 01-funda-o-e-n-cleo-de-links
verified: 2026-10-09T23:41:48Z
status: human_needed
score: 53/55 must-haves verified (SC do roadmap 5/5; truths dos planos 01-01 a 01-07 41/43; truths da 01-08 7/7)
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
  previous_score: 48/55
  gaps_closed:
    - "URL que aponta para o host de linkpulse.base-url responde 400 com errors[] no campo url (01-06, mitigação T-06-03 contra loop de redirect) — fechado pela 01-08 (rodada de 2026-10-09T23:00:29Z)"
    - "SC5 / CI-01 / truth da 01-01: cada push na branch principal e cada PR disparam o workflow ci no GitHub Actions — fechado pela opção (a): branch padrão renomeada de master para main"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Com o Docker ligado, no Windows: instalar o GNU make (winget install ezwinports.make ou scoop install make) e rodar make run no Git Bash (ou, sem make, docker compose -f compose.dev.yaml up -d --wait e depois cd app && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev). Depois: curl -i http://localhost:8080/demo-link; curl http://localhost:8081/actuator/health; curl -i http://localhost:8080/actuator/health; make db-down"
    expected: "demo-link responde 302 para https://example.com/ com Cache-Control no-store, private; o health na 8081 responde UP; a 8080 não expõe /actuator (404); db-down para o container e mantém o volume pgdata"
    why_human: "Exige subir o servidor e o Postgres do compose (truth 2 da 01-01, truth 1 da 01-05). O CI prova o build e o make help no Linux, mas não o runtime nem a porta de management, e o make no Git Bash nunca foi observado"
  - test: "Seguir o README do zero e abrir http://localhost:8080/swagger-ui.html; criar links com e sem alias pelo 'Try it out'; ler a regra de URL e o bullet 'Loop de redirect via DNS (mitigação parcial)'"
    expected: "Swagger UI carrega, cria links (201) e mostra erros em Problem Details; o README basta sem conhecimento prévio e descreve com fidelidade o que a regra de host bloqueia"
    why_human: "Clareza editorial e experiência na UI (human-check da 01-07 e item D5 da 01-08). Atenção ao WR-04: o exemplo de expiresAt 2026-12-31 passa a dar 400 a partir de 2027-01-01"
  - test: "Revisar as proibições de nível judgment: 01-03 (código curto não é segredo; nenhum log da URL de destino ou do corpo) e 01-08 (a checagem de host não resolve DNS)"
    expected: "Confirmar o veredito não autoritativo deste verificador: as três respeitadas"
    why_human: "unverified-prohibition, revisão humana recomendada (proibições judgment-tier nunca passam em silêncio)"
---

# Phase 1: Fundação e núcleo de links — Relatório de Verificação

**Objetivo da fase:** Um cliente cria links curtos (gerados ou com alias) e é redirecionado para a URL original a partir do Postgres, num repositório que builda igual no Windows e no Linux, com CI verde desde o início
**Verificado em:** 2026-10-09T23:41:48Z
**Status:** human_needed
**Re-verificação:** Sim. Segunda rodada, focada em SC5/CI-01, depois da correção do filtro de branch pela opção (a). A rodada anterior (2026-10-09T23:00:29Z) já tinha confirmado o fechamento do gap da 01-08.

> **Nota sobre o modo MVP.** O ROADMAP marca a fase com `Mode: mvp`, mas o objetivo não está no formato de user story. Como nas rodadas anteriores, segui a verificação goal-backward padrão a pedido do orquestrador. Recomendação mantida: reescrever o objetivo com `/gsd mvp-phase 1` ou tirar `mode: mvp` da fase.

## Resumo da re-verificação

| Item | Verificação inicial | Rodada 23:00 | Agora |
|------|---------------------|--------------|-------|
| Gap CR-01 (checagem do próprio host contornável) | ✗ FAILED (parcial) | ✓ VERIFIED | ✓ VERIFIED (os ITs agora também rodaram no CI) |
| WR-01 (base-url sem host desliga a checagem) | ⚠️ Warning | ✓ resolvido | ✓ resolvido |
| SC5 / CI-01 (CI no GitHub) | ? humano (sem remote) | ✗ FAILED (filtro `main` × branch `master`) | ✓ VERIFIED: branch padrão `main`, CI verde no PR e no push |
| Build no Linux, com espaço no caminho | ? humano | ? humano | ✓ VERIFIED (CI no `ubuntu-latest`, checkout em `link pulse/`) |
| Verify completo com ITs | verde local | ? humano (Docker desligado) | ✓ VERIFIED (CI: 103 unitários + 50 ITs, BUILD SUCCESS) |

### Evidência do SC5/CI-01, conferida por este verificador

Não confiei na mensagem do orquestrador. Conferi cada fato:

| Fato | Comando | Resultado |
|------|---------|-----------|
| Branch padrão renomeada | `git ls-remote --symref origin HEAD` | `ref: refs/heads/main HEAD` → `8b57425`. As heads do remoto são `main`, o branch da fase e um branch do Dependabot. Não existe mais `master` |
| PR #1 rodou o `ci.yml` em `pull_request` | `GET /repos/Lucasllo/link-pulse/actions/runs?per_page=5` | `38005181081 ci pull_request gsd/phase-01-... bef3cf3 completed success` |
| PR #1 foi mergeado em `main` | `GET /repos/Lucasllo/link-pulse/pulls/1` | `closed`, `merged: true`, `merge_commit_sha 8b57425`, `gsd/phase-01-... -> main` |
| O push na `main` rodou o `ci.yml` | mesma API | `38005385906 ci push main 8b57425 completed success` |
| Steps do job | `GET .../actions/runs/{id}/jobs` (nas duas execuções) | `build-test` success em `ubuntu-latest`: Checkout, Java 21 (Temurin), "mvnw é executável e LF", "Makefile parseia no Linux" e "Build, Checkstyle, testes unitários + Testcontainers, SpotBugs", todos success |
| Conteúdo do log (push na main) | `gh run view 38005385906 --log` | Checkout em `.../link pulse` (caminho com espaço); `make -n verify` imprime `cd app && ./mvnw -B -ntp verify`; `make help` lista help/build/test/verify/run/db-up/db-down; `0 Checkstyle violations`; `Tests run: 103, Failures: 0` (TargetHostPolicyTest 37, LinkPulsePropertiesTest 14…); `Connected to docker`; `Tests run: 50, Failures: 0` (LinkApiIT 27, RedirectIT 9, ConcurrencyIT 1, OpenApiIT 6, LiquibaseContextsIT 5+2); `BugInstance size is 0`; `BUILD SUCCESS` |
| O código testado no CI é o mesmo verificado aqui | `git diff --quiet 76b698b origin/main -- app .github Makefile README.md compose.dev.yaml` | Sem diferença. O único arquivo novo em `main` é este relatório |

O `ci.yml` não mudou: o filtro continua `push: branches: [main]`, e agora casa com a branch padrão. O README (linha 196, "todo push na `main`") passou a estar correto sem edição.

## Cobertura do fluxo do usuário (resumo)

| Passo | Esperado | Evidência | Status |
|-------|----------|-----------|--------|
| Cliente cria link com código gerado | 201, código de 7 caracteres, shortUrl e Location | `LinkApiIT` e `ConcurrencyIT` verdes no CI | ✓ |
| Cliente cria link com alias | 201; repetido → 409 | `LinkApiIT` verde no CI | ✓ |
| Cliente não consegue criar link em loop pelo próprio host | 400 no campo `url`; o código não passa a existir | `TargetHostPolicyTest` (37) e `LinkApiIT#trailingDotOnTheShortenerHostIsRejectedAndNoLoopIsCreated` verdes no CI; probe próprio sobre as classes compiladas | ✓ |
| Cliente acessa `/{code}` | 302/404/410 a partir do Postgres | `RedirectIT` (9) verde no CI | ✓ |
| Builda igual no Windows e no Linux | `./mvnw verify` verde nos dois | Windows: verde no Git Bash e no PowerShell com espaço no caminho (verificação inicial). Linux: CI verde com checkout em `link pulse/` | ✓ |
| CI verde desde o início | Workflow verde no GitHub | Run `38005181081` (PR) e `38005385906` (push na `main`), ambos success | ✓ |

## Atingimento do objetivo

### Critérios de sucesso do roadmap (contrato)

| # | Critério | Status | Evidência |
|---|----------|--------|-----------|
| SC1 | `POST /links` retorna código e URL curta; não enumerável; concorrência sem duplicata | ✓ VERIFIED | `CodeGeneratorTest` (19) e `ConcurrencyIT` verdes no CI |
| SC2 | Alias válido; 409; reservado/regex/URL inválida/expiração passada → erro de validação em Problem Details | ✓ VERIFIED | Sem a ressalva do host próprio: `TargetHostPolicy.check` lança o mesmo `validation-error` com `errors[]`; `LinkApiIT` (27) verde no CI |
| SC3 | `GET /{code}` → 302 sem cache; 404; 410 | ✓ VERIFIED | `RedirectIT` (9) verde no CI |
| SC4 | Liquibase YAML com contexts, `validate`, Swagger UI | ✓ VERIFIED | `LiquibaseContextsIT` (dev 5, prod 2) e `OpenApiIT` (6) verdes no CI |
| SC5 | Cada push/PR roda build, testes, Checkstyle e SpotBugs no GitHub Actions, falhando em violação; mesmo build no Git Bash/WSL e no Linux, com espaço no caminho | ✓ VERIFIED | Ver "Evidência do SC5/CI-01". Os gates quebram o build: provado por probe na verificação inicial, e a configuração não mudou. `mvnw` 100755/LF conferido no CI pelo step dedicado. Git Bash: verde na verificação inicial |

### Truths da 01-08 (gap closure)

| # | Truth (resumo) | Status | Evidência |
|---|----------------|--------|-----------|
| 1 | `http://localhost.:8080/loop-a` com alias → 400 `url` "não pode apontar para o próprio encurtador"; `GET /loop-a` → 404 | ✓ VERIFIED | Probe próprio + `TargetHostPolicyTest`; o IT `trailingDotOnTheShortenerHostIsRejectedAndNoLoopIsCreated` rodou verde no CI (LinkApiIT 27/27) |
| 2 | Loopback recusado sem DNS e para qualquer base-url | ✓ VERIFIED | `rejectsLoopbackDestinations` (11); IT parametrizado (7) verde no CI. Ressalva WR-A (`[::1%25lo]`) fora da lista da truth |
| 3 | Host numérico fora de `a.b.c.d` → 400; IPv4 público canônico → 201 | ✓ VERIFIED | Unitários + `publicIpv4LiteralIsAccepted` verde no CI |
| 4 | `self-hosts` / `LINKPULSE_SELF_HOSTS` com a mesma normalização; vazio por padrão | ✓ VERIFIED | `LinkPulsePropertiesTest` (14), verde localmente e no CI |
| 5 | base-url inválida derruba a subida, sem ecoar o valor | ✓ VERIFIED | `#contextFailsOnStartupWhenBaseUrlIsInvalid` (7), `#startupFailureDoesNotEchoTheBaseUrl` e probe próprio (`EchoProbe`) |
| 6 | README fiel ao código, com a mitigação parcial por DNS | ✓ VERIFIED | `README.md:131-136`, `:176-177`, `:187`; gates de grep. A clareza editorial fica no item humano |
| 7 | Nada regride; `./mvnw -B -ntp verify` BUILD SUCCESS | ✓ VERIFIED | Local: `-DskipITs verify` verde (103 testes, 0 violações, 0 BugInstance). CI (run `38005385906`, código idêntico): verify completo com 103 unitários + 50 ITs, BUILD SUCCESS |

### Truths dos planos 01-01 a 01-07

Os arquivos dessas truths não mudaram desde a verificação inicial. Mudanças de status nesta rodada:

| Plano | Truth | Antes | Agora | Evidência |
|-------|-------|-------|-------|-----------|
| 01-01 | Clone em caminho com espaço builda com `./mvnw verify` no Git Bash **e no Linux**, wrapper 3.9.16 com SHA-256 | ? humano | ✓ VERIFIED | Git Bash na verificação inicial; Linux no CI (`link pulse/`, sem Maven instalado, via wrapper) |
| 01-01 | Todo push na main e todo PR disparam o `ci` com checkout em `link pulse/`, check do mvnw, `make -n` e `./mvnw verify` com Java 21 Temurin | ✗ FAILED (parcial) | ✓ VERIFIED | Runs `38005181081` (pull_request) e `38005385906` (push main), steps conferidos |
| 01-01 | `make help` lista help, build, test, verify e run; sem make, os mesmos comandos via `cd app && ./mvnw` | ? humano | ✓ VERIFIED | Log do CI: `make help` lista os 7 alvos e `make -n verify` mostra `cd app && ./mvnw -B -ntp verify`. A forma sem make está no README (verificação inicial) |
| 01-06 | Host de `linkpulse.base-url` → 400 | ✗ FAILED (parcial) | ✓ VERIFIED | Fechado na rodada anterior (01-08); ITs agora também verdes no CI |

Continuam humanas 2 truths: o jar sobe e o `GET :8081/actuator/health` responde UP (01-01), e `make run` + `curl /demo-link` → 302 (01-05). As outras 39 seguem VERIFIED como na verificação inicial.

**Placar:** 53/55 truths verificadas (SC 5/5, planos 01-01 a 01-07 41/43, 01-08 7/7). 0 falhas, 2 dependem de verificação humana (runtime local) e 0 estão presentes sem comportamento comprovado.

### Proibições (`must_haves.prohibitions`)

| Plano | Proibição | Nível | Disposição |
|-------|-----------|-------|------------|
| 01-03 | Não apresentar o código curto como segredo | judgment | Veredito LLM não autoritativo: respeitada. **unverified-prohibition: revisão humana recomendada** |
| 01-03 | Não logar a URL de destino nem o corpo das criações | judgment | Veredito LLM não autoritativo: respeitada. **unverified-prohibition: revisão humana recomendada** |
| 01-04 | Não aceitar alias que sombreie rota ou reservado | test | ✓ Com enforcement (verde no CI) |
| 01-05 | Não aplicar o seed no context prod | test | ✓ Com enforcement (`LiquibaseContextsIT$ProdProfile` verde no CI) |
| 01-08 | Não resolver DNS ao checar o host de destino | judgment | Veredito LLM não autoritativo: respeitada. Uma única chamada `InetAddress.getByName(`, só para literal entre colchetes; `getAllByName` = 0. **unverified-prohibition: revisão humana recomendada** |
| 01-08 | Não ecoar o valor de `linkpulse.base-url` na exceção de subida | test | ✓ Com enforcement: `#startupFailureDoesNotEchoTheBaseUrl` verde (local e CI) + probe próprio. Ressalva IN-01 da revisão: o `BindFailureAnalyzer` pode logar `Value:` em subida real |

### Artefatos obrigatórios

Os 6 artefatos da 01-08 passam (`gsd query verify.artifacts` 6/6). `LinkApiIT` deixou de ser só estático: a execução foi observada no CI. Os artefatos das 01-01 a 01-07 seguem como na verificação inicial. `.github/workflows/ci.yml` agora está verificado em execução, e não só no conteúdo.

### Ligações-chave (wiring)

| De | Para | Via | Status |
|----|------|-----|--------|
| `LinkService.create` | `TargetHostPolicy.check` | primeira linha do método | ✓ WIRED |
| `ApplicationConfig` | `LinkPulseProperties` → `TargetHostPolicy` | `new TargetHostPolicy(properties.baseUrl(), properties.selfHosts())` | ✓ WIRED |
| `LinkPulseProperties` | `TargetHostPolicy` | base-url validada no binding | ✓ WIRED |
| `ci.yml` | branch principal do remoto | `on.push.branches: [main]` | ✓ WIRED (antes NOT_WIRED; `refs/heads/main` é a HEAD do remoto, run `38005385906`) |
| `ci.yml` | `app/mvnw` | `./mvnw -B -ntp verify` em `link pulse/app` | ✓ WIRED (execução observada) |

### Fluxo de dados (nível 4)

| Artefato | Dado | Fonte | Dado real | Status |
|----------|------|-------|-----------|--------|
| `TargetHostPolicy.selfHosts` | hosts próprios | `base-url` + `self-hosts` (yml/env) | Sim | ✓ FLOWING |
| `RedirectController` | `Location` | `LinkRepository.findByCode` (Postgres) | Sim (RedirectIT no CI) | ✓ FLOWING |
| `LinkController` | `code`/`shortUrl` | `nextval` + `CodeGenerator` + `base-url` | Sim | ✓ FLOWING |

### Verificações comportamentais

| Comportamento | Comando | Resultado | Status |
|---------------|---------|-----------|--------|
| Unitários + Checkstyle + SpotBugs (local, rodada 23:00) | `./mvnw -B -ntp -DskipITs verify` | BUILD SUCCESS, 103 testes, 0 violações, 0 BugInstance | ✓ PASS |
| Probe do CR-01 (rodada 23:00) | `java HostProbe.java` | Vetores do gap → 400; destinos comuns aceitos | ✓ PASS |
| base-url sem eco (rodada 23:00) | `java EchoProbe.java` | Sem `senha` na cadeia | ✓ PASS |
| Branch padrão do remoto | `git ls-remote --symref origin HEAD` | `refs/heads/main` | ✓ PASS |
| CI em pull_request | API do GitHub, run `38005181081` | completed / success | ✓ PASS |
| CI em push na main | API do GitHub, run `38005385906` | completed / success; log: 103 + 50 testes, BUILD SUCCESS | ✓ PASS |
| Desvios fora do contrato | `HostProbe` | `[::1%25lo]` aceito; com base IP, `[::ffff:a00:5]` aceito | ⚠️ Warning |

### Execução de probes

Nenhum `scripts/*/tests/probe-*.sh` existe, e nenhum plano declara probes. Os probes acima foram escritos e rodados por este verificador no scratchpad.

### Cobertura de requisitos

| Requisito | Plano | Status | Evidência |
|-----------|-------|--------|-----------|
| LINK-01 | 01-03, 01-06, 01-08 | ✓ SATISFIED | Validação de URL completa, com a regra de host |
| LINK-02 | 01-03 | ✓ SATISFIED | `ConcurrencyIT` no CI |
| LINK-03 | 01-03 | ✓ SATISFIED | `CodeGeneratorTest` |
| LINK-04 | 01-04 | ✓ SATISFIED | `AliasPolicyTest` + ITs de alias |
| LINK-05 | 01-06 | ✓ SATISFIED | Ressalva WR-02 antiga (ano > 294276 → 500) |
| LINK-06 | 01-04, 01-06, 01-08 | ✓ SATISFIED | Problem Details com `errors[]` |
| REDIR-01 | 01-02 | ✓ SATISFIED | `RedirectIT` |
| REDIR-02 | 01-02 | ✓ SATISFIED | `RedirectIT` |
| DATA-01 | 01-02, 01-05 | ✓ SATISFIED | `LiquibaseContextsIT` |
| QUAL-01 | 01-03 | ✓ SATISFIED | 19 verdes |
| QUAL-03 | 01-01 | ✓ SATISFIED | Checkstyle e SpotBugs no CI (gates de quebra provados por probe) |
| QUAL-04 | 01-07 | ✓ SATISFIED | `OpenApiIT` |
| CONT-03 | 01-01, 01-05, 01-07 | ✓ SATISFIED | Windows (Git Bash e PowerShell com espaço no caminho) e Linux (CI com `link pulse/`); `mvnw` LF/executável conferido no CI; instrução do make no README. O `make` no Git Bash ainda não foi executado (fica no item humano do runtime) |
| CI-01 | 01-01 | ✓ SATISFIED | `ci.yml` roda build, unitários, ITs, Checkstyle e SpotBugs em push na `main` e em PR (runs `38005385906` e `38005181081`, success) |

Requisitos órfãos: nenhum.

### Anti-padrões encontrados

| Arquivo | Linha | Padrão | Severidade | Impacto |
|---------|-------|--------|------------|---------|
| arquivos da 01-08 | — | `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` | — | Nenhum |
| `TargetHostPolicy.java` | 132-140 | **WR-A:** fail-open em `UnknownHostException` | ⚠️ Warning | `http://[::1%25lo]/x` é aceito; `curl -L` segue para `::1` (navegadores rejeitam zone ID). Correção no 01-REVIEW WR-01 |
| `TargetHostPolicy.java` | 162 | **WR-B:** "próprio encurtador" compara texto | ⚠️ Warning | Com base-url em IP literal, a forma IPv4-mapped do mesmo IP passa. Nenhuma config atual usa IP. Correção no 01-REVIEW WR-02 |
| `LinkPulseProperties.java` / `TargetHostPolicy.java` | 60 / 76-82 | **WR-C:** itens de `self-hosts` sem validação | ⚠️ Warning | Item mal escrito desliga a proteção sem aviso (01-REVIEW WR-03) |
| `CreateLinkRequest.java`, `LinkController.java`, `README.md` | — | Exemplo `expiresAt` 2026-12-31 (WR-04) | ⚠️ Warning | Passa a dar 400 em 2027-01-01 |
| `application.yml` | 30 | base-url default `localhost` também em prod | ℹ️ Info | 01-REVIEW IN-02 |
| `LinkApiIT.java` | 261-275 | IT parametrizado não confere a mensagem | ℹ️ Info | 01-REVIEW IN-03 |
| `.github/workflows/ci.yml` | 12-14 | `cancel-in-progress: true` também na main (IN-08) | ℹ️ Info | Um commit da main pode ficar sem resultado de CI |
| clone local | — | O git local ainda tem a branch `master` e `init.defaultBranch = master` | ℹ️ Info | Higiene: `git branch -m master main`, `git fetch origin` e `git branch -u origin/main main`, para não empurrar uma `master` nova por engano |
| Antigos WR-02, WR-03, IN-01 | — | Fora do escopo | ⚠️/ℹ️ | Continuam abertos |

### Advisory (escopo novo, sem evidência determinística)

| # | Achado | Categoria | Por que advisory |
|---|--------|-----------|------------------|
| 1 | `[::127.0.0.1]` (IPv4-compatible, obsoleto) é aceito | security | Nenhum SO moderno roteia `::7f00:1` para loopback; nenhum caminho de loop foi demonstrado |
| 2 | `localhost.localdomain` e `127.0.0.1.nip.io` são aceitos | security | Dependem de `/etc/hosts` ou DNS; cobertos pela ressalva "mitigação parcial" (T-08-06, aceito) |

### Verificação humana necessária

1. **Runtime local no Windows (com make).** Instalar o make, rodar `make run` no Git Bash, `curl -i :8080/demo-link` (302 para `https://example.com/`, sem cache), `curl :8081/actuator/health` (UP), a 8080 sem `/actuator` e `make db-down` (volume preservado).
2. **README e Swagger UI.** Seguir o README do zero, criar links pela Swagger UI e julgar a fidelidade da regra de host e da mitigação parcial.
3. **Proibições judgment.** 01-03 (código não é segredo; sem log de URL ou corpo) e 01-08 (sem DNS).

Dois itens da rodada anterior saíram desta lista. O "CI no GitHub" e o "verify completo com Docker" foram cobertos pelos runs `38005181081` e `38005385906`. O segundo rodou os 50 ITs com Testcontainers sobre o mesmo código.

### Resumo

Não há gaps. O bloqueio do SC5/CI-01 foi resolvido pela opção (a): a branch padrão do remoto agora é `main`, e o `ci.yml` (sem mudança) casa com ela. Conferi pela API pública do GitHub e pelo `gh`:
- o PR #1 rodou o CI em `pull_request` (run `38005181081`, success);
- o PR foi mergeado como `8b57425`;
- o push na `main` rodou o CI (run `38005385906`, success).

O log mostra checkout em `link pulse/`, `make help` no Linux, 103 unitários, 50 ITs com Testcontainers, Checkstyle e SpotBugs limpos e BUILD SUCCESS. O código testado é o mesmo verificado aqui. Com isso também ficam verificados o build no Linux, o `make help` e o verify completo da 01-08.

O status é `human_needed` por três itens que não dá para verificar automaticamente: o runtime local (jar + health na 8081, `make run` + `demo-link`), a clareza do README e da Swagger UI, e as proibições judgment. WR-A, WR-B e WR-C continuam como warnings não bloqueantes, com correções descritas no 01-REVIEW.md.

---

_Verificado em: 2026-10-09T23:41:48Z_
_Verificador: Claude (gsd-verifier)_
