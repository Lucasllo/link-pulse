---
phase: "01"
slug: "funda-o-e-n-cleo-de-links"
status: verified
# threats_open = count of OPEN threats at or above workflow.security_block_on severity (the blocking gate)
threats_open: 0
asvs_level: 1
created: "2026-10-09"
---

# Phase 01 — Security

> Contrato de segurança da fase: threat register, riscos aceitos e trilha de auditoria.
> Register montado a partir dos `<threat_model>` de 01-01 a 01-08 (autorado no plano). Verificação em profundidade ASVS L1 (grep no código e nos testes).

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Runner do CI → actions de terceiros | Código externo roda com o token do workflow | `GITHUB_TOKEN` (só `contents: read`) |
| Dev/CI → Maven Central | Download do Maven (wrapper) e das dependências | Binários de build |
| Rede → porta de management 8081 | Endpoints do Actuator | Health/info |
| Cliente → `GET /{code}` | Path variable não confiável chega ao lookup | Código curto |
| Cliente → `POST /links` | JSON não confiável (`url`, `alias`, `expiresAt`) | URL de destino, alias |
| App → cliente (erros) | Corpos de erro podem vazar detalhes internos | Problem Details |
| App → PostgreSQL | Consultas, `nextval`, INSERT concorrente, DDL só via Liquibase | Links |
| App → Liquibase (rede) | Liquibase OSS envia analytics por padrão | Metadados do changelog |
| Configuração (env) → app | `LINKPULSE_*` define base-url, self-hosts e o gerador | Config do operador |
| App → navegador (302) | O `Location` volta ao navegador, que resolve DNS (regras WHATWG) | URL de destino |
| Rede local → Postgres do `compose.dev.yaml` | Banco de dev com credenciais fixas | Credenciais de dev |
| Cliente → `/v3/api-docs` e Swagger UI | Documentação pública da API | Spec OpenAPI |

---

## Threat Register

| Threat ID | Category | Component | Severity | Disposition | Mitigation | Status |
|-----------|----------|-----------|----------|-------------|------------|--------|
| T-01-01 | Tampering | `.github/workflows/ci.yml` (actions) | high | mitigate | Todo `uses:` fixado por SHA de 40 hex (0 sem pin); Dependabot `github-actions` | closed |
| T-01-02 | Elevation of Privilege | Token do workflow | medium | mitigate | `permissions: contents: read` (`ci.yml:9`) | closed |
| T-01-03 | Tampering | Download do Maven pelo `mvnw` | high | mitigate | `distributionSha256Sum` em `maven-wrapper.properties:4` | closed |
| T-01-04 | Information Disclosure | Actuator | medium | mitigate | `management.server.port: 8081` e `include: health,info` (`application.yml:59,63`); UAT 1 confirmou 404 na 8080 | closed |
| T-01-05 | Denial of Service | Logs do CI | low | accept | Upload de relatórios só em `failure()` (`ci.yml:49`) | closed |
| T-01-SC | Tampering | Dependências Maven | high | mitigate | Versões do BOM/properties auditadas no RESEARCH; Dependabot `maven` | closed |
| T-02-01 | Tampering | `RedirectController` → `findByCode` | high | mitigate | `@GetMapping("/{code:[A-Za-z0-9_-]+}")` (`RedirectController.java:45`); query derivada com bind | closed |
| T-02-02 | Information Disclosure | Respostas 404/410 | medium | mitigate | `LinkProblems` monta o `detail` só com código/alias/expiração (`LinkProblems.java:14`) | closed |
| T-02-03 | Tampering | Schema do banco | medium | mitigate | `ddl-auto: validate` (`application.yml:12`) | closed |
| T-02-04 | Information Disclosure | Liquibase analytics | low | mitigate | `analytics-enabled: false` (`application.yml:19`) | closed |
| T-02-05 | Spoofing | Open redirect para a URL armazenada | medium | accept | Função do produto; mitigado na criação (T-06-01/02, T-08-*) e documentado no README | closed |
| T-02-06 | Denial of Service | URIs gigantes no path | low | accept | Limite da request line do Tomcat + regex do path | closed |
| T-02-SC | Tampering | Testcontainers/starters JPA/Liquibase | high | mitigate | Versões do BOM 4.1.1 auditadas; `postgres:18.6` com tag fixa (compose e `TestcontainersConfiguration`) | closed |
| T-03-01 | Information Disclosure | `CodeGenerator` (enumeração) | medium | mitigate | Permutação multiplicativa mod 62^7 com `BigInteger`; Javadoc/README dizem que é ofuscação | closed |
| T-03-02 | Tampering | `CodeGenerator.encode` (overflow) | high | mitigate | `BigInteger` + limites com `IllegalArgumentException` (`CodeGenerator.java:55-80`) | closed |
| T-03-03 | Tampering | `LinkService.create` sob concorrência | high | mitigate | `nextval` + `uk_links_code` (`001-create-links.yaml:32`); `ConcurrencyIT` | closed |
| T-03-04 | Denial of Service | Corpo do `POST /links` | medium | mitigate | `@Size(max = 2048)` (`CreateLinkRequest.java:21`) + `VARCHAR(2048)` | closed |
| T-03-05 | Tampering | Esquemas perigosos na url | high | mitigate | Fechado pela 01-06 (ver T-06-01) | closed |
| T-03-06 | Information Disclosure | Logs da criação | medium | mitigate | Nenhum logger em `LinkService`/`LinkController` (grep vazio); UAT 3 confirmou | closed |
| T-03-07 | Spoofing | `shortUrl` (host header injection) | medium | mitigate | `shortUrl` de `properties.baseUrl()` (`LinkController.java:70`), sem `Host`/`X-Forwarded-*` | closed |
| T-03-08 | Tampering | `linkpulse.code.multiplier` mal configurado | medium | mitigate | Construtor valida coprimalidade e alfabeto na subida (`CodeGenerator.java:47-80`) | closed |
| T-04-01 | Spoofing | `AliasPolicy` (sequestro de rotas) | high | mitigate | Regex com `-`/`_` obrigatório + reservados em `Locale.ROOT` (`AliasPolicy.java:24-39`); `AliasPolicyTest` | closed |
| T-04-05 | Tampering | Corrida no mesmo alias | high | mitigate | `uk_links_code` + `isCodeConflict` → 409 (`LinkService.java:91,105`) | closed |
| T-04-06 | Information Disclosure | Corpos de erro de alias | high | mitigate | Detail fixo; `LinkApiIT:142,334` confere ausência de `uk_links_code`/`SQL`/`Exception` | closed |
| T-04-07 | Denial of Service | Alias grande | low | mitigate | `{4,32}` em `ALIAS_REGEX` (`AliasPolicy.java:24`) | closed |
| T-05-01 | Information Disclosure | Credenciais de dev `linkpulse` | low | accept | Só para dev local; prod via `SPRING_DATASOURCE_*` | closed |
| T-05-02 | Information Disclosure | Porta do Postgres local | medium | mitigate | `"127.0.0.1:5432:5432"` (`compose.dev.yaml:13`) | closed |
| T-05-03 | Tampering | Seed `demo-link` em prod | medium | mitigate | `contextFilter: dev` + `contexts: prod` (`application.yml:17`, `application-prod.yml:5`); `LiquibaseContextsIT` | closed |
| T-06-01 | Tampering | `HttpUrlValidator` (`javascript:`, `data:`, `file:`) | high | mitigate | Allow-list http/https + `new URI` + host obrigatório (`HttpUrlValidator.java:25-35`); IT com `ftp://` | closed |
| T-06-02 | Spoofing | URL com credenciais | medium | mitigate | `uri.getUserInfo() == null` (`HttpUrlValidator.java:35`) | closed |
| T-06-03 | Denial of Service | Loop pelo próprio host | medium | mitigate | Substituído por T-08-01 (01-08) | closed |
| T-06-04 | Information Disclosure | Corpos de erro | high | mitigate | `handleHttpMessageNotReadable` com detail fixo; 500 genérico (`GlobalExceptionHandler.java:74-102`) | closed |
| T-06-05 | Information Disclosure | Dois handlers de Problem Details ativos | medium | mitigate | Chave `problemdetails` ausente de `application.yml`; `RedirectIT`/`LinkApiIT` conferem `application/problem+json` | closed |
| T-06-06 | Denial of Service | Payload grande | low | mitigate | IT com url de 2049 caracteres (`LinkApiIT:225`) | closed |
| T-06-07 | Spoofing | Open redirect inerente | medium | accept | Mitigações de criação + README "Riscos conhecidos" | closed |
| T-07-01 | Information Disclosure | Swagger UI / `/v3/api-docs` públicos | low | accept | API pública por decisão do projeto; Actuator fica na 8081 | closed |
| T-07-02 | Spoofing | Open redirect (documentação) | medium | accept | README "Riscos conhecidos" (`README.md:186`) | closed |
| T-07-03 | Information Disclosure | Credenciais de dev no README | low | accept | Só Postgres local em 127.0.0.1 | closed |
| T-08-01 | Denial of Service | Loop pelo próprio host (caixa, ponto final) | medium | mitigate | `targetHostPolicy.check` (`LinkService.java:74`) + `normalizeHost`; IT `trailingDotOnTheShortenerHost...` | closed |
| T-08-02 | Denial of Service | Destinos de loopback | medium | mitigate | `isLoopback` (`TargetHostPolicy.java:125-142`) | closed |
| T-08-03 | Spoofing | IP numérico ofuscado | medium | mitigate | `isNonCanonicalNumeric` (`TargetHostPolicy.java:109,159`) | closed |
| T-08-04 | Tampering | base-url inválida desliga a checagem | medium | mitigate | Construtor compacto lança `IllegalArgumentException` (`LinkPulseProperties.java:57`) | closed |
| T-08-05 | Information Disclosure | Mensagem da falha de subida | low | mitigate | Mensagem fixa; `startupFailureDoesNotEchoTheBaseUrl` | closed |
| T-08-06 | Denial of Service | Domínio de terceiros que resolve para o encurtador | medium | accept | `LINKPULSE_SELF_HOSTS`, limite de saltos do navegador, rate limit na Phase 2; README "mitigação parcial" (`README.md:187`) | closed |
| T-08-07 | Denial of Service | DNS disparado pelo cliente | medium | mitigate | `getByName` só para literal IPv6 entre colchetes (`TargetHostPolicy.java:132-135`); nomes nunca vão ao resolvedor; UAT 3 confirmou | closed |

*Status: open · closed · open — below high threshold (non-blocking)*
*Severity: critical > high > medium > low — only open threats at or above workflow.security_block_on count toward threats_open*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01-01 | T-01-05 | Relatórios de CI só sobem em falha e não contêm dados sensíveis | plano 01-01 | 2026-10-09 |
| AR-01-02 | T-02-05, T-06-07, T-07-02 | Open redirect é a função do produto; mitigações na criação e documentado no README | planos 01-02, 01-06, 01-07 | 2026-10-09 |
| AR-01-03 | T-02-06 | Tomcat limita a request line; regex rejeita caracteres fora do alfabeto | plano 01-02 | 2026-10-09 |
| AR-01-04 | T-05-01, T-07-03 | Credenciais `linkpulse` só para Postgres de dev em loopback; prod usa env | planos 01-05, 01-07 | 2026-10-09 |
| AR-01-05 | T-07-01 | API pública sem autenticação por decisão do projeto; spec sem segredos | plano 01-07 | 2026-10-09 |
| AR-01-06 | T-08-06 | Resolver DNS no POST custa latência e é contornável por rebinding; operador declara aliases em `LINKPULSE_SELF_HOSTS`; rate limit chega na Phase 2 | plano 01-08 | 2026-10-09 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-09 | 45 | 45 | 0 | /gsd-secure-phase (L1 grep, sem auditor: register autorado no plano) |

## Security Audit 2026-10-09
| Metric | Count |
|--------|-------|
| Threats found | 45 |
| Closed | 45 |
| Open | 0 |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-09
