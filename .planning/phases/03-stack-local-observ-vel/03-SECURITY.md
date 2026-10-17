---
phase: "03"
slug: "stack-local-observ-vel"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-17"
---

# Phase 03 — Security

> Contrato de segurança da fase: registro de ameaças, riscos aceitos e trilha de auditoria.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| LAN → portas do host | Portas publicadas em todas as interfaces seriam alcançáveis pela rede | Tráfego HTTP da API, Grafana e Prometheus |
| Host → rede interna do compose | Só 8080, 3000 e 9090 atravessam; bancos e actuator ficam dentro | Requisições da API e leitura de métricas |
| Registry → build/runtime | Imagens base e de serviços vêm do Docker Hub | Binários de terceiros |
| Repositório → imagem | O contexto de build (`app/`) vira camadas da imagem | Código-fonte; risco de `.env` e artefatos locais |
| k6 → API do app | Seed e carga falam só com a API pública | Links e cliques de demonstração |
| API → sites de destino | Um 302 seguido levaria tráfego a terceiros | Requisições HTTP de saída |
| Navegador anônimo → Grafana | Leitura sem login dos dashboards provisionados | Métricas agregadas (sem código, URL ou IP) |
| Repositório → Grafana/Prometheus | Dashboards, regras e config montados read-only | Configuração versionada |
| Runbook (GitHub) → operador | Texto público | Nomes de variáveis, nunca valores |
| Registry/Internet → k6 | Import remoto executaria código baixado em runtime | Scripts JS |
| Container k6 → diretório do host | Bind mount `./load` com escrita em `results/` | Resumo JSON da carga |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-03-01 | Information Disclosure | Portas publicadas do `compose.yaml` | high | mitigate | Só `127.0.0.1:8080`, `127.0.0.1:9090` e `127.0.0.1:3000` (`compose.yaml:71,95,125`); Postgres, Valkey, Mongo e 8081 sem `ports` | closed |
| T-03-02 | Elevation of Privilege | Container do app como root | medium | mitigate | `useradd --system --uid 10001 ... --shell /usr/sbin/nologin` e `USER 10001:10001` (`app/Dockerfile:30,37`) | closed |
| T-03-03 | Spoofing | Defaults de demo confundidos com segredo real | medium | mitigate | Comentário "Não é segredo" em cada default (`compose.yaml:3,17,64,107`); `.env` em `.gitignore:7` e `app/.dockerignore:7` | closed |
| T-03-04 | Tampering | Admin/lifecycle do Prometheus sem autenticação | medium | mitigate | Nenhuma flag `web.enable*` fora de comentários (gate = 0) | closed |
| T-03-05 | Tampering | Grafana anônimo alterando datasource ou pastas | medium | mitigate | `GF_AUTH_ANONYMOUS_ORG_ROLE: Viewer` (`compose.yaml:111`); datasource `editable: false` | closed |
| T-03-06 | Tampering | Link de demo apontando para o próprio app (loop) | low | mitigate | `LINKPULSE_SELF_HOSTS: app` (`compose.yaml:68`) + `TargetHostPolicy` (decisão 01-08) | closed |
| T-03-07 | Information Disclosure | `.env`, `target/` ou IDE copiados para a imagem | low | mitigate | `app/.dockerignore`; o build copia só `mvnw`, `.mvn/`, `pom.xml`, `config/`, `src/` (`app/Dockerfile:13-18`) | closed |
| T-03-SC | Tampering | Imagens do Docker Hub (supply chain) | medium | mitigate | Imagens oficiais/verificadas por tag exata; Dependabot `docker-compose` e `docker` (`.github/dependabot.yml:23,30`) | closed |
| T-03-08 | Denial of Service | Seed seguindo o 302 até terceiros | medium | mitigate | `maxRedirects: 0` e `redirects: 0` + `check` `status === 302` (`load/seed.js:21,116,120`; `load/lib/common.js:64`) | closed |
| T-03-09 | Information Disclosure | Dashboards com filtro por código, URL ou IP | low | mitigate | `"templating": { "list": [] }` nos dois JSONs; só agregações | closed |
| T-03-10 | Tampering | Anônimo alterando ou apagando dashboards | medium | mitigate | `editable: false` nos JSONs; provider `allowUiUpdates: false`, `disableDeletion: true`; anônimo Viewer | closed |
| T-03-11 | Tampering | Dados de demo inseridos direto nos bancos | low | mitigate | Seed só via API pública (`createLink`/`linkExists` em `load/lib/common.js`); sem backfill (D-04) | closed |
| T-03-12 | Denial of Service | Seed esbarrando no rate limit | low | mitigate | Retry único por `Retry-After` e contagem `429 recebidos` (`load/lib/common.js:89-111`; `load/seed.js:148`) | closed |
| T-03-13 | Spoofing | UA de navegador/Googlebot forjados pelo seed | low | accept | Ver Accepted Risks Log (AR-03-01) | closed |
| T-03-14 | Repudiation | Queda do app sem alerta no kind | medium | mitigate | `up{job="link-pulse"} == 0 or absent(up{job="link-pulse"})` (`link-pulse-alerts.yml:55`) + caso de teste no promtool | closed |
| T-03-15 | Denial of Service | Pipeline parado com lag ~0 sem alerta | medium | mitigate | `... or max(linkpulse_clicks_stream_pending) > 1000` (`link-pulse-alerts.yml:43`) + caso "Mongo fora" no teste | closed |
| T-03-16 | Tampering | Alerta falso com lag NaN | low | mitigate | `NaN > 1000` é falso; caso de teste `NaN+0x40` (`link-pulse-alerts-test.yml:135-140`) | closed |
| T-03-17 | Information Disclosure | Runbooks públicos com valor de segredo | low | mitigate | Runbooks citam só nomes (`LINKPULSE_IP_HASH_KEY`), nenhum valor | closed |
| T-03-18 | Tampering | Recarga das regras por HTTP sem autenticação | low | mitigate | Lifecycle desligado (T-03-04); config montada `:ro` (`compose.yaml:92`) | closed |
| T-03-19 | Elevation of Privilege | `obs-check` rodando `promtool` em container | low | accept | Ver Accepted Risks Log (AR-03-02) | closed |
| T-03-20 | Denial of Service | Carga seguindo o 302 até `example.com` | high | mitigate | `maxRedirects: 0`, `redirects: 0` e `check` `status === 302` (`load/load.js:24,88,91`) | closed |
| T-03-21 | Tampering | Import remoto (jslib) executado pelo k6 | medium | mitigate | Gate `from 'http…'` = 0 em `load.js`, `seed.js` e `lib/common.js`; `--summary-export` (`Makefile:62`) | closed |
| T-03-22 | Repudiation | Evidência de desempenho manipulada | low | mitigate | Thresholds fixados em D-10; JSON gerado por `make load` e commitado como saiu; confirmado no UAT (teste 2) | closed |
| T-03-23 | Denial of Service | Carga derrubando o laptop do avaliador | low | mitigate | `maxVUs` 50/200/5 (`load/load.js:41,51,62`); perfil moderado D-10 | closed |
| T-03-24 | Elevation of Privilege | Container k6 gravando no host com outro uid | low | accept | Ver Accepted Risks Log (AR-03-03) | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-03-01 | T-03-13 | Dados declaradamente de demonstração; os UAs forjados exercitam o classificador de bots e o `includeBots`; documentado no README | plano 03-02 | 2026-10-17 |
| AR-03-02 | T-03-19 | Container efêmero (`--rm --no-deps`), diretório montado read-only, sem portas publicadas (`Makefile:68-69`) | plano 03-03 | 2026-10-17 |
| AR-03-03 | T-03-24 | Montagem restrita a `./load`; `K6_USER` usa o uid:gid do próprio usuário (`Makefile:15-16`, `compose.yaml:142`); container `--rm` | plano 03-04 | 2026-10-17 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-17 | 25 | 25 | 0 | /gsd-secure-phase (L1 grep, orquestrador; auditor dispensado pelo short-circuit) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-17
