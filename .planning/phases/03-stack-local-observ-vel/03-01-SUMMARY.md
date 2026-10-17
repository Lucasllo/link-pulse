---
phase: 03-stack-local-observ-vel
plan: 01
subsystem: infra
tags: [docker, compose, dockerfile, layered-jar, prometheus, grafana, valkey, postgres, mongo, dependabot]

requires:
  - phase: 02
    provides: app com actuator na 8081 (health, info, prometheus), env LINKPULSE_*/SPRING_* e pipeline de cliques Valkey -> Mongo
provides:
  - imagem link-pulse:local (multi-stage, layered jar, usuário 10001:10001, HEALTHCHECK no readiness)
  - compose.yaml (projeto linkpulse) com postgres, valkey, mongo, app, prometheus e grafana na ordem dos healthchecks
  - job de scrape link-pulse em app:8081/actuator/prometheus
  - datasource Grafana uid prometheus (url http://prometheus:9090)
  - alvos make up, down, clean, image
  - compose.dev.yaml isolado no projeto linkpulse-dev
affects: [03-02, 03-03, 03-04, phase-04-kind, phase-05-readme]

actuals:
  tokens: 4987
  tasks: 3
  commits: 3
plan_head_before: 491228b5247a34e07e6de3ffa0addb95196e8aef

tech-stack:
  added: [eclipse-temurin:21-jdk-noble, eclipse-temurin:21-jre-noble, prom/prometheus:v3.15.0, grafana/grafana:13.2.3]
  patterns:
    - "Dependências duplicadas entre compose.yaml e compose.dev.yaml (sem include/extends), com tags mantidas iguais"
    - "Config de observabilidade montada por diretório read-only de observability/ (fonte única)"
    - "Segredos de demo como ${VAR:-default} comentados 'não é segredo'"

key-files:
  created:
    - app/Dockerfile
    - app/.dockerignore
    - compose.yaml
    - observability/prometheus/prometheus.yml
    - observability/grafana/provisioning/datasources/prometheus.yml
  modified:
    - Makefile
    - compose.dev.yaml
    - .gitignore
    - README.md
    - .github/dependabot.yml

key-decisions:
  - "unzip instalado só no stage de build: sem ele o mvnw baixa o .tar.gz do Maven e a validação do distributionSha256Sum (do .zip) falha; a validação continua ligada"
  - "Readiness do Boot 4.1.1 responde UP na 8081 sem management.endpoint.health.probes.enabled; application.yml não mudou"
  - "Contrato para a Phase 4: tag link-pulse:local, job link-pulse e datasource uid prometheus"

patterns-established:
  - "make up = docker compose up -d --build --wait; o comando só sai com 0 com todos os serviços healthy"
  - "Só 8080, 3000 e 9090 publicadas, sempre em 127.0.0.1; actuator e bancos só na rede interna"

requirements-completed: [CONT-01, CONT-02]

coverage:
  - id: D1
    description: "Imagem link-pulse:local multi-stage, com layered jar, usuário 10001:10001 e HEALTHCHECK healthy"
    requirement: CONT-01
    verification:
      - kind: e2e
        ref: "docker inspect --format {{.Config.User}} link-pulse:local == 10001:10001; docker inspect linkpulse-app-1 Health.Status == healthy"
        status: pass
      - kind: e2e
        ref: "docker run --rm --entrypoint ls link-pulse:local -d /app/org/springframework/boot/loader/launch/JarLauncher.class /app/BOOT-INF/lib"
        status: pass
    human_judgment: false
  - id: D2
    description: "make up sobe os seis serviços na ordem dos healthchecks; POST /links 201, GET /{code} 302, up{job=link-pulse}=1"
    requirement: CONT-02
    verification:
      - kind: e2e
        ref: "make up (rc=0, 6 serviços Healthy); curl POST /links -> code cJinsNv; curl GET /cJinsNv -> 302; Prometheus query up{job=\"link-pulse\"} -> 1; promtool check config SUCCESS"
        status: pass
    human_judgment: false
  - id: D3
    description: "Só 127.0.0.1:8080, :3000 e :9090 publicadas; 8081, Postgres, Mongo e Valkey sem binding no host"
    requirement: CONT-02
    verification:
      - kind: e2e
        ref: "docker inspect HostConfig.PortBindings: app {8080/tcp 127.0.0.1}, prometheus {9090/tcp 127.0.0.1}, grafana {3000/tcp 127.0.0.1}, postgres/valkey/mongo {}"
        status: pass
    human_judgment: false
  - id: D4
    description: "Grafana com datasource uid prometheus consultável, leitura anônima e escrita anônima recusada"
    verification:
      - kind: e2e
        ref: "curl /api/health database ok; /api/datasources/uid/prometheus url http://prometheus:9090; /health status OK; GET /api/search anônimo 200; POST /api/folders anônimo 403"
        status: pass
    human_judgment: false
  - id: D5
    description: "compose.yaml (linkpulse) e compose.dev.yaml (linkpulse-dev) sobem juntos sem disputar portas, containers ou volumes"
    verification:
      - kind: e2e
        ref: "docker compose up -d --wait + docker compose -f compose.dev.yaml up -d --wait (rc=0); volumes linkpulse_pgdata e linkpulse-dev_pgdata; app segue running após o down do dev"
        status: pass
    human_judgment: false
  - id: D6
    description: "README com make up/down/clean/image e a seção Stack completa (make up); Dependabot docker-compose (/) e docker (/app)"
    verification:
      - kind: other
        ref: "grep do verify DOCS-OK (README, dependabot, make -n verify)"
        status: pass
    human_judgment: true
    rationale: "Clareza do README para o avaliador é julgamento de leitura; o grep só prova a presença dos trechos"

duration: 32min
completed: 2026-10-17
status: complete
---

# Phase 3 Plan 01: Imagem multi-stage e make up Summary

**Imagem `link-pulse:local` multi-stage (layered jar, usuário 10001:10001, HEALTHCHECK no readiness da 8081) e `make up` subindo Postgres, Valkey, Mongo, app em prod, Prometheus raspando `app:8081` e Grafana com datasource `uid: prometheus`, tudo atrás de healthchecks e só no loopback**

## Performance

- **Duration:** 32 min
- **Started:** 2026-10-17T12:48:11Z
- **Completed:** 2026-10-17T13:20:04Z
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments
- `make up` sai do clone para seis serviços healthy: o app só inicia depois de postgres, valkey e mongo, e o Grafana só depois do Prometheus. O Prometheus não depende do app.
- A imagem roda como `10001:10001`, com camadas `dependencies`, `spring-boot-loader`, `snapshot-dependencies` e `application` e `ENTRYPOINT` no `JarLauncher` (forma exec). O HEALTHCHECK no readiness chega a healthy.
- Prometheus com o job `link-pulse` (`up = 1`) e o Grafana com o datasource de UID fixo consultável. O anônimo só lê (escrita recebe 403).
- `compose.dev.yaml` virou o projeto `linkpulse-dev`, e as duas stacks sobem juntas sem conflito.
- README documenta `make up`/`down`/`clean`/`image` e a stack completa. O Dependabot passa a vigiar as tags de imagem do compose e do Dockerfile.

## Task Commits

1. **Task 1: make up builda a imagem e sobe o app atrás das dependências, raspado pelo Prometheus**: `6e6e48e` (feat)
2. **Task 2: Grafana sobe com o datasource de UID fixo, leitura anônima e segredos de demo sobrescrevíveis**: `298aece` (feat)
3. **Task 3: Stack completa e stack de dev convivem, documentadas no README, com imagens mantidas pelo Dependabot**: `1981497` (docs)

## Files Created/Modified
- `app/Dockerfile`: build com `./mvnw` e cache do `~/.m2`, extração em camadas, runtime JRE noble não-root com HEALTHCHECK
- `app/.dockerignore`: tira do contexto o `target/`, os arquivos de IDE, os logs e o arquivo de env local
- `compose.yaml`: stack completa (projeto `linkpulse`), defaults de demo e portas só no loopback
- `observability/prometheus/prometheus.yml`: jobs `link-pulse` (`app:8081/actuator/prometheus`) e `prometheus`, mais `rule_files` da pasta `rules/`
- `observability/grafana/provisioning/datasources/prometheus.yml`: datasource `Prometheus`, `uid: prometheus`
- `Makefile`: `STACK`, exports do MSYS e alvos `up`, `down`, `clean` e `image`
- `compose.dev.yaml`: `name: linkpulse-dev` e `pg_isready -h 127.0.0.1`
- `.gitignore`: arquivo de env local
- `README.md`: escopo, pré-requisitos, tabela de comandos e seção "Stack completa (make up)"
- `.github/dependabot.yml`: ecossistemas `docker-compose` (`/`) e `docker` (`/app`)

## Decisions Made
- **Readiness sem `probes.enabled`:** no Boot 4.1.1, o readiness respondeu `{"status":"UP"}` na 8081 sem `management.endpoint.health.probes.enabled`, como a pesquisa citava. O `application.yml` não mudou.
- **Grafana tem `wget` (A2):** o healthcheck com `wget` funcionou na `grafana/grafana:13.2.3`, sem precisar trocar por `curl`.
- **Tempos para o README da Phase 5:**
  - o primeiro `make up`, sem imagens nem cache, levou cerca de 17 min só para baixar as imagens base Temurin, numa rede lenta;
  - com as imagens base em cache, o primeiro build completo (Maven pelo wrapper, dependências e compilação) mais a subida levou 255 s;
  - o `make up` seguinte, depois de um `make down`, levou 32 s;
  - o primeiro `up` com o Grafana levou 224 s, quase todo no pull da imagem.
- **Contrato da Phase 4:** a tag `link-pulse:local`, o job `link-pulse` e o UID `prometheus` do datasource ficam fixos. O `kind load`, o ServiceMonitor ou `jobLabel`, o alerta TargetDown da 03-03 e os dashboards da 03-02 dependem deles.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `unzip` no stage de build do Dockerfile**
- **Found during:** Task 1
- **Issue:** o `./mvnw -B -ntp -DskipTests package` falhava sempre com "Failed to validate Maven distribution SHA-256". A causa está na linha 179 do `mvnw`: sem `unzip` no PATH, o wrapper troca a distribuição `.zip` pelo `.tar.gz`, e o SHA-256 fixado no `maven-wrapper.properties` é o do `.zip`. A imagem `eclipse-temurin:21-jdk-noble` não traz `unzip`; o host (Git Bash) e o runner do CI trazem. Para descartar download corrompido, conferi o `.zip` no host (SHA-256 igual ao fixado e SHA-512 igual ao oficial) e baixei o mesmo `.zip` dentro da imagem JDK, com o mesmo SHA-256.
- **Fix:** `apt-get install -y --no-install-recommends unzip` no stage `build`, com comentário pt-BR explicando o motivo. A validação de checksum continua ligada, e a imagem final (JRE) não muda.
- **Files modified:** `app/Dockerfile`
- **Verification:** `make up` com rc=0 e os seis serviços healthy, mais os dois `<verify>` da Task 1.
- **Committed in:** `6e6e48e` (commit da Task 1)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** sem essa correção a imagem não builda. Nenhuma mudança de escopo, e a validação de integridade do Maven foi mantida.

## Issues Encountered
- **Guarda de isolamento do worktree:** os comandos do `<verify>` não puderam rodar como uma linha única encadeada. A guarda recusa construções complexas com `docker`/`git` e `sh -c` dentro de containers. Cada asserção rodou como comando separado, com o mesmo resultado esperado.
  - Para o `test -f`/`test -d` dentro da imagem, usei `docker run --rm --entrypoint ls link-pulse:local -d <JarLauncher.class> <BOOT-INF/lib>` (rc=0).
- **Hook de leitura de segredo:** o hook bloqueia qualquer comando Bash que cite o nome do arquivo de env local, então `git check-ignore -q .env` não rodou. Conferi pela ferramenta Grep que a linha exata `.env` está no `.gitignore` e no `app/.dockerignore`, e o `.gitignore` não tem nenhuma regra de negação.
- O primeiro `make up` ficou cerca de 17 min preso no download das camadas Temurin, por lentidão da rede, não do projeto.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- 03-02 (dashboards) já tem o Grafana com o datasource `uid: prometheus` e o diretório `observability/grafana/provisioning/`. Falta montar `provisioning/dashboards` e `dashboards/` no serviço `grafana`.
- 03-03 (alertas) já tem o `rule_files: /etc/prometheus/rules/*.yml` e o job `link-pulse`. A pasta `rules/` entra pelo mesmo bind mount do diretório.
- 03-04 (seed e k6) pode acrescentar o serviço `k6` (profile `tools`) e os alvos `seed`/`load` ao Makefile, que já exporta o `MSYS_NO_PATHCONV`.
- A stack foi derrubada com `make down`. Os volumes `linkpulse_*` foram mantidos, e o banco já tem o link `cJinsNv` de teste.
- Os volumes antigos `projeto4_pgdata` e `projeto4_mongodata` continuam no disco do autor, órfãos, como o README documenta.

---
*Phase: 03-stack-local-observ-vel*
*Completed: 2026-10-17*

## Self-Check: PASSED
