---
phase: 01-funda-o-e-n-cleo-de-links
plan: 01
subsystem: infra
tags: [maven-wrapper, spring-boot-4.1.1, java-21, checkstyle, spotbugs, github-actions, dependabot, makefile, gitattributes]

requires: []
provides:
  - "app/ Maven (dev.linkpulse:link-pulse:0.1.0-SNAPSHOT) sobre spring-boot-starter-parent 4.1.1 / Java 21"
  - "Maven Wrapper 3.3.4 only-script com Maven 3.9.16 e distributionSha256Sum"
  - "LinkPulseApplication + application.yml (API 8080, management 8081 só com health e info)"
  - "Gate de Checkstyle (google_checks 14.3.0 adaptado) em validate e SpotBugs (Max/Medium) em verify"
  - "Workflow ci (push main + PR) com checkout em caminho com espaço e actions fixadas por SHA"
  - "Dependabot (github-actions e maven) e Makefile base (help, build, test, verify, run)"
affects: [01-02, 01-03, 01-04, 01-05, phase-03-containers, ci]

plan_head_before: 171fd8253ef884b0daa1219d698d1254ebfa0104
actuals:
  tokens: 14400
  tasks: 3
  commits: 3

tech-stack:
  added: [spring-boot 4.1.1, springdoc-openapi 3.1.1, maven-wrapper 3.3.4, maven 3.9.16, maven-checkstyle-plugin 3.6.0, checkstyle 14.3.0, spotbugs-maven-plugin 4.10.4.1]
  patterns:
    - "Build sempre via cd app && ./mvnw -B -ntp (nunca Maven do sistema)"
    - "LF no repositório inteiro via .gitattributes; CRLF só em *.cmd/*.bat"
    - "Toda exclusão do SpotBugs leva comentário com justificativa"
    - "Actions de CI fixadas por SHA de 40 hex com comentário da tag"

key-files:
  created:
    - .gitattributes
    - .gitignore
    - app/mvnw
    - app/mvnw.cmd
    - app/.mvn/wrapper/maven-wrapper.properties
    - app/pom.xml
    - app/src/main/java/dev/linkpulse/LinkPulseApplication.java
    - app/src/main/resources/application.yml
    - app/config/checkstyle/checkstyle.xml
    - app/config/spotbugs/exclude.xml
    - .github/workflows/ci.yml
    - .github/dependabot.yml
    - Makefile
  modified: []

key-decisions:
  - "Spring Boot 4.1.1 (variante recomendada) com spring-boot-starter-webmvc; JPA/Liquibase/Postgres/Testcontainers entram só na 01-02"
  - "Checkstyle: severity fixa em error no checkstyle.xml e violationSeverity=warning no pom como defesa dupla"
  - "SpotBugs: só EI_EXPOSE_REP e EI_EXPOSE_REP2 excluídos, ambos comentados; find-sec-bugs adiado"
  - "Actions do CI: checkout 3d3c42e5 (v7.0.1), setup-java de7274f0 (v6.0.1), upload-artifact 043fb46d (v7.0.1), conferidos por git ls-remote"

patterns-established:
  - "Gates de qualidade: Checkstyle em validate e SpotBugs em verify, ambos quebram o build"
  - "Classes de teste de integração terminam em IT (allowedAbbreviations=IT no Checkstyle)"

requirements-completed: [CONT-03, QUAL-03, CI-01]

coverage:
  - id: D1
    description: "Jar empacotado com o wrapper (sem Maven instalado, caminho com espaço) sobe e responde health UP na 8081"
    requirement: CONT-03
    verification:
      - kind: integration
        ref: "cd app && ./mvnw -B -ntp -DskipTests package && java -jar target/link-pulse-0.1.0-SNAPSHOT.jar; curl http://localhost:8081/actuator/health"
        status: pass
    human_judgment: false
  - id: D2
    description: "mvnw no índice com modo 100755 e EOL lf; mvnw.cmd em CRLF no checkout; mvnw.cmd -v no PowerShell mostra Maven 3.9.16"
    requirement: CONT-03
    verification:
      - kind: other
        ref: "git ls-files --format='%(objectmode) %(eolinfo:index)' app/mvnw; powershell.exe -NoProfile -Command \"& .\\mvnw.cmd -v\""
        status: pass
    human_judgment: false
  - id: D3
    description: "Checkstyle bloqueia import curinga + tab em validate; SpotBugs bloqueia NP_ALWAYS_NULL em verify"
    requirement: QUAL-03
    verification:
      - kind: other
        ref: "provas StyleProbe.java / BugProbe.java do <verify> da Task 2 (OK: checkstyle bloqueou / OK: spotbugs bloqueou)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Ensaio local do CI: clone do HEAD em <tmp>/link pulse roda ./mvnw -B -ntp verify com BUILD SUCCESS"
    requirement: CI-01
    verification:
      - kind: integration
        ref: "git clone ... \"$D/link pulse\" && cd app && test -x mvnw && ./mvnw -B -ntp verify"
        status: pass
    human_judgment: false
  - id: D5
    description: "Workflow ci roda verde no GitHub Actions e make help/make verify funcionam no Git Bash"
    requirement: CI-01
    verification: []
    human_judgment: true
    rationale: "Não há remote nem gh CLI, e o make não está instalado nesta máquina; depende do user_setup (criar repo + push, instalar make)"

duration: 21min
completed: 2026-10-08
status: complete
---

# Phase 1 Plan 01: Fundação do build Summary

**App Spring Boot 4.1.1 / Java 21 em `app/` com Maven Wrapper 3.3.4 (Maven 3.9.16 + SHA-256), health UP na porta 8081, Checkstyle 14.3.0 e SpotBugs 4.10.4.1 quebrando o build, e workflow de CI com actions fixadas por SHA e checkout em caminho com espaço**

## Performance

- **Duration:** ~21 min (inclui o executor anterior, que parou no checkpoint do `maven-wrapper.properties`)
- **Started:** 2026-10-08T22:12:21Z
- **Completed:** 2026-10-08T22:33:18Z
- **Tasks:** 3/3
- **Files modified:** 13 (todos criados)

## Accomplishments

- Repositório builda com `cd app && ./mvnw -B -ntp verify` no Git Bash do Windows, a partir de `projeto 4` (caminho com espaço), sem Maven instalado. O wrapper baixou o Maven 3.9.16 e conferiu o SHA-256.
- O jar sobe sem configuração extra: API na 8080, management na 8081, `GET /actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`. `/actuator/env` devolve 404 e a 8080 não serve o actuator.
- `app/mvnw` no índice com `100755` e `i/lf`. `app/mvnw.cmd` tem `w/crlf` no checkout. `mvnw.cmd -v` no PowerShell imprime `Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)` com Java 21.0.10.
- Os gates têm efeito real: `StyleProbe.java` (import curinga + tab) faz o `validate` falhar, e `BugProbe.java` faz o `verify` falhar com `[ERROR] High: Null pointer dereference ... NP_ALWAYS_NULL`. Os dois arquivos de prova foram removidos e nunca entraram no git.
- `ci.yml`, `dependabot.yml` e `Makefile` estão versionados. O ensaio do CI num clone em `<tmp>/link pulse` terminou com `BUILD SUCCESS` (0 violações de Checkstyle, `BugInstance size is 0`).

## Task Commits

1. **Task 1: Do clone ao jar rodando (wrapper, pom e app mínima com health)** - `247f826` (feat)
2. **Task 2: O build quebra em violação (Checkstyle em validate, SpotBugs em verify)** - `e0cf8e6` (feat)
3. **Task 3: CI em cada push/PR, Dependabot e Makefile base** - `e4a987e` (feat)

**Plan metadata:** commit `docs(01-01)` logo após este SUMMARY

## Files Created/Modified

- `.gitattributes`: `* text=auto eol=lf`, `mvnw` LF, `*.cmd`/`*.bat` CRLF, `*.jar`/`*.png` binary
- `.gitignore`: `target/`, IDEs, `*.log`, `.DS_Store`
- `app/mvnw`, `app/mvnw.cmd`: scripts oficiais do `maven-wrapper-distribution-3.3.4-only-script.zip` (sha1 `640f7c39…` conferido; byte a byte idênticos)
- `app/.mvn/wrapper/maven-wrapper.properties`: Maven 3.9.16 com `distributionSha256Sum`
- `app/pom.xml`: parent Boot 4.1.1, Java 21, webmvc/validation/actuator/springdoc 3.1.1, failsafe, Checkstyle e SpotBugs
- `app/src/main/java/dev/linkpulse/LinkPulseApplication.java`: bootstrap `@SpringBootApplication`
- `app/src/main/resources/application.yml`: portas 8080/8081, virtual threads, exposure `health,info`
- `app/config/checkstyle/checkstyle.xml`: google_checks 14.3.0 baixado e adaptado (severity error, 4/8, sem MissingJavadocMethod, `allowedAbbreviations=IT`)
- `app/config/spotbugs/exclude.xml`: `EI_EXPOSE_REP` e `EI_EXPOSE_REP2`, cada um com comentário
- `.github/workflows/ci.yml`: job `build-test` com checkout em `link pulse/`
- `.github/dependabot.yml`: `github-actions` (`/`) e `maven` (`/app`), semanal
- `Makefile`: `help`, `build`, `test`, `verify`, `run` sobre `MVNW := cd app && ./mvnw -B -ntp`

## SHAs efetivos das actions

| Action | Tag | SHA (git ls-remote) | Igual ao RESEARCH? |
|---|---|---|---|
| actions/checkout | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` | Sim (tag leve) |
| actions/setup-java | v6.0.1 | `de7274f081f381c8f8158605e0321c36c376e2e6` | Sim (tag leve) |
| actions/upload-artifact | v7.0.1 | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` | Sim (tag leve) |

## Saída de `mvnw.cmd -v` (PowerShell, em `projeto 4\app`)

```text
Apache Maven 3.9.16 (2bdd9fddda4b155ebf8000e807eb73fd829a51d5)
Maven home: C:\Users\Lucas Lopes\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be...
Java version: 21.0.10, vendor: Oracle Corporation, runtime: C:\Program Files\Java\jdk-21.0.10
Default locale: pt_BR, platform encoding: UTF-8
OS name: "windows 11", version: "10.0", arch: "amd64", family: "windows"
```

## Decisions Made

- O plano foi seguido. A escolha do Boot 4.1.1 já vinha do plano (D-13) e foi mantida.
- A defesa dupla de severity (`severity=error` no XML e `violationSeverity=warning` no pom) ficou como o plano especificou.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removido também o `SuppressionXpathSingleFilter` que só servia ao `MissingJavadocMethod`**
- **Found during:** Task 2
- **Issue:** O google_checks 14.3.0 tem um filtro com `checks=MissingJavadocMethod` logo depois do módulo. Remover só o módulo deixaria uma supressão morta apontando para uma check inexistente.
- **Fix:** Removi o módulo e o filtro, e deixei no lugar um comentário explicando a remoção. `grep -c 'name="MissingJavadocMethod"'` dá 0.
- **Files modified:** `app/config/checkstyle/checkstyle.xml`
- **Committed in:** `e0cf8e6`

**2. [Rule 2 - Missing Critical] `if-no-files-found: ignore` no upload de relatórios**
- **Found during:** Task 3
- **Issue:** Quando o build falha no Checkstyle (fase validate), surefire, failsafe e spotbugs ainda não geraram relatórios. Sem essa opção, o step de upload emitiria um warning por arquivo ausente.
- **Fix:** Adicionei `if-no-files-found: ignore` ao step `if: failure()`.
- **Files modified:** `.github/workflows/ci.yml`
- **Committed in:** `e4a987e`

---

**Total deviations:** 2 auto-fixed (1 bug de configuração, 1 robustez do CI)
**Impact on plan:** Nenhum aumento de escopo. Os critérios de aceite continuam valendo literalmente.

## Issues Encountered

- **Checkpoint human-action na Task 1 (executor anterior):** o arquivo `app/.mvn/wrapper/maven-wrapper.properties` precisou de aprovação do usuário. O orquestrador o criou com o conteúdo exato do plano, e este executor conferiu as 4 linhas antes de seguir.
- **`kill $PID` no Git Bash não encerra o `java.exe` nativo:** depois do comando de verify da Task 1, as portas 8080/8081 continuaram em LISTEN. Encerrei o processo com `taskkill //PID <pid> //F`. Quando o comando for reusado no Windows (por exemplo, no UAT), é preciso conferir as portas depois. No Linux/CI o `kill` funciona normalmente.
- **JDK local:** a máquina usa Oracle JDK 21.0.10, não Temurin. O CI usa Temurin 21. Não há diferença para este build.
- **Docker:** nenhuma verificação deste plano precisou de Docker.

## User Setup Required

O plano declara `user_setup`, mas nenhum USER-SETUP.md foi gerado. Os dois itens pendentes ficam como human-check de fim de fase:
- **GitHub:** criar o repositório, rodar `git remote add origin <url>`, fazer push e conferir o workflow `ci` verde na aba Actions.
- **GNU make:** `winget install ezwinports.make` (ou scoop/WSL); depois rodar `make help` e `make verify` no Git Bash. Sem make, `make help` foi simulado com o mesmo grep/awk e lista os 5 alvos.

## Next Phase Readiness

- A 01-02 pode adicionar JPA, Liquibase, o driver Postgres e o Testcontainers ao `app/pom.xml`. O build já tem os gates de estilo e de bugs, e o failsafe está pronto para `*IT`.
- Os ITs com Testcontainers vão exigir o daemon do Docker ligado localmente. No CI, o runner ubuntu já tem Docker.

---
*Phase: 01-funda-o-e-n-cleo-de-links*
*Completed: 2026-10-08*

## Self-Check: PASSED

- 13/13 arquivos encontrados; commits 247f826, e0cf8e6 e e4a987e presentes no histórico.
