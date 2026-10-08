---
phase: 01-funda-o-e-n-cleo-de-links
plan: 03
subsystem: api
tags: [code-generator, base62, permutacao-multiplicativa, biginteger, configuration-properties, post-links, concorrencia, testcontainers]

requires:
  - phase: 01-02
    provides: "Link (Persistable), LinkRepository.nextId(), LinkService.resolve, ApplicationConfig (Clock), AbstractIT"
provides:
  - "CodeGenerator(alphabet, multiplier): permutação multiplicativa mod 62^7 em BigInteger + Base62 de 7 caracteres, encode/decode, LENGTH/SPACE"
  - "LinkPulseProperties (@ConfigurationProperties(\"linkpulse\") @Validated): baseUrl + Code(alphabet, multiplier)"
  - "ApplicationConfig: @EnableConfigurationProperties + @Bean CodeGenerator validado na subida (D-03)"
  - "POST /links → 201 + Location = shortUrl de linkpulse.base-url, corpo D-09 (LinkResponse)"
  - "CreateLinkRequest(url, alias, expiresAt) com @Schema; LinkService.create(request) com nextval → encode → saveAndFlush"
  - "Testes: CodeGeneratorTest (unit, sem Docker), LinkApiIT e ConcurrencyIT"
affects: [01-04, 01-05, 01-06, 01-07, phase-02-cache, phase-03-k6]

plan_head_before: e0057b18c6b4956a32eca79ff581c779bbed25a2
actuals:
  tokens: 7700
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Configuração da aplicação em record @ConfigurationProperties @Validated no prefixo linkpulse.*, com override por env via relaxed binding"
    - "Validação de configuração na subida: o @Bean lança IllegalArgumentException e derruba o contexto"
    - "Aritmética modular só em BigInteger + longValueExact (nunca id * M em long)"
    - "URL pública montada de linkpulse.base-url com UriComponentsBuilder.pathSegment; nunca de Host/X-Forwarded-*"
    - "Instantes truncados em micros antes de persistir (precisão do timestamptz)"
    - "ITs de criação extraem campos do corpo com JsonPath; testes de contexto isolado com ApplicationContextRunner"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
    - app/src/main/java/dev/linkpulse/link/CodeGenerator.java
    - app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java
    - app/src/main/java/dev/linkpulse/link/LinkResponse.java
    - app/src/main/java/dev/linkpulse/link/LinkController.java
    - app/src/test/java/dev/linkpulse/link/LinkApiIT.java
    - app/src/test/java/dev/linkpulse/link/CodeGeneratorTest.java
    - app/src/test/java/dev/linkpulse/link/ConcurrencyIT.java
  modified:
    - app/src/main/java/dev/linkpulse/config/ApplicationConfig.java
    - app/src/main/java/dev/linkpulse/link/LinkService.java
    - app/src/main/resources/application.yml

key-decisions:
  - "CodeGenerator relança a falha do modInverse como IllegalArgumentException('multiplicador deve ser coprimo de 62^7') sem encadear a ArithmeticException, para que a causa raiz da falha de subida seja a mensagem em pt-BR"
  - "Corpo 201 inclui expiresAt: null explícito quando o link não expira (Jackson 3 com inclusão padrão)"
  - "POST /links ainda ignora alias (sempre gera código); regras de alias ficam na 01-04 e allow-list http/https na 01-06"

patterns-established:
  - "linkpulse.code.alphabet e linkpulse.code.multiplier são contrato one-way: trocar quebra todos os links publicados"
  - "Testes de propriedade em laço usam AssertionError manual (sem AssertJ por iteração) para manter 2M+ checagens em ~3 s"

requirements-completed: [LINK-01, LINK-02, LINK-03, QUAL-01]

coverage:
  - id: D1
    description: "POST /links com URL válida → 201, Location == shortUrl (http://localhost:8080/<code>), corpo {code, shortUrl, targetUrl, expiresAt, createdAt} com code de 7 alfanuméricos e createdAt ISO-8601 em Z"
    requirement: LINK-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/link/LinkApiIT.java#createsLinkWithGeneratedCodeAndLocation"
        status: pass
    human_judgment: false
  - id: D2
    description: "Link criado redireciona na hora (302 com Location igual à URL); mesma URL duas vezes gera códigos diferentes (D-11); expiresAt -03:00 com nanos volta em UTC truncado em micros"
    requirement: LINK-01
    verification:
      - kind: integration
        ref: "LinkApiIT#createdLinkRedirectsImmediately, #sameUrlTwiceCreatesIndependentLinks, #expiresAtWithOffsetIsReturnedInUtcTruncatedToMicros"
        status: pass
    human_judgment: false
  - id: D3
    description: "Gerador bijetivo e não enumerável: vetores 1..5 → cJinsNv, EdRbklq, qxAPd9l, TGtDVXg, 5ac1Nvb; ida e volta em 1..1e6 e em 62^7-1e5..62^7-1; unicidade; 7 caracteres; prefixos de 6 distintos em 1..10000; lista 1..1000 fora de ordem; limites e entradas inválidas"
    requirement: QUAL-01
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/link/CodeGeneratorTest.java (19 casos)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Contexto não sobe com multiplier=62 (causa raiz IllegalArgumentException 'coprimo'); sobe com o M default e encode(1) == cJinsNv"
    requirement: LINK-03
    verification:
      - kind: unit
        ref: "CodeGeneratorTest#contextFailsOnStartupWhenMultiplierIsNotCoprime, #contextStartsWithDefaultMultiplier"
        status: pass
    human_judgment: false
  - id: D5
    description: "8 threads x 50 criações concorrentes → 400 códigos distintos, 400 linhas novas, nenhuma exceção"
    requirement: LINK-02
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/link/ConcurrencyIT.java#concurrentCreationsProduceDistinctCodes"
        status: pass
    human_judgment: false
  - id: D6
    description: "./mvnw -B -ntp verify completo verde: Checkstyle 0 violações, 19 unit + 14 ITs, SpotBugs 0"
    requirement: QUAL-01
    verification:
      - kind: integration
        ref: "cd app && ./mvnw -B -ntp verify"
        status: pass
    human_judgment: false

duration: 5min
completed: 2026-10-08
status: complete
---

# Phase 1 Plan 03: Criação de links com código gerado Summary

**`POST /links` responde 201 com `Location` e o corpo D-09. O código de 7 caracteres sai do `nextval('link_id_seq')` passado por uma permutação multiplicativa mod 62^7 (`BigInteger`) e por Base62, e a URL curta é montada só de `linkpulse.base-url`. O `CodeGenerator` tem testes de bijeção perto de 62^7−1, não enumerabilidade e rejeição de multiplicador ou alfabeto inválidos na subida. 400 criações concorrentes geram 400 códigos distintos.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-10-08T22:48:42Z
- **Completed:** 2026-10-08T22:54:00Z
- **Tasks:** 2/2
- **Files modified:** 11 (8 criados, 3 alterados)

## Accomplishments

- `POST /links {"url":"https://example.com/artigo?id=42"}` → 201, `Location: http://localhost:8080/<code>`, com `code` em `^[0-9A-Za-z]{7}$`, `targetUrl` igual à enviada, `createdAt` ISO-8601 em `Z` e `expiresAt: null`. `GET /<code>` responde 302 para a URL na hora.
- `expiresAt: 2099-12-31T23:59:59.123456789-03:00` volta como `2100-01-01T02:59:59.123456Z`, em UTC e truncado em micros. A mesma URL enviada duas vezes gera dois códigos (D-11).
- `CodeGenerator`: vetores conhecidos batem com o jshell da pesquisa (IDs 1..5 → `cJinsNv`, `EdRbklq`, `qxAPd9l`, `TGtDVXg`, `5ac1Nvb`; ID 0 → `0000000`). Ida e volta passa em 1,1 milhão de IDs, inclusive `62^7−1`. `encode(-1)` e `encode(62^7)` falham explicitamente (D-02).
- Validação na subida (D-03): com `linkpulse.code.multiplier=62`, o contexto falha com causa raiz `IllegalArgumentException: multiplicador deve ser coprimo de 62^7`.
- `ConcurrencyIT`: 8 threads × 50 → 400 códigos distintos e `count` +400, sem exceção.
- `./mvnw -B -ntp verify`: 0 violações de Checkstyle, 19 testes unitários, 14 ITs (`ConcurrencyIT` 1, `LinkApiIT` 4, `RedirectIT` 9), `BugInstance size is 0`, `BUILD SUCCESS`.

## Task Commits

1. **Task 1 (tracer): POST /links de ponta a ponta** - `81dc3a4` (feat). O gate do tracer (`end-of-phase`, verify só automatizado) foi feito re-rodando `-Dit.test=LinkApiIT verify`: 4/4 verde, e só então a expansão começou.
2. **Task 2: Gerador bijetivo/não enumerável + concorrência** - `6fc6122` (test)

**Plan metadata:** commit `docs(01-03)` logo após este SUMMARY

## Files Created/Modified

- `config/LinkPulseProperties.java`: record `@ConfigurationProperties("linkpulse") @Validated` com `baseUrl` (`@NotNull URI`) e `Code(alphabet, multiplier)` aninhado.
- `config/ApplicationConfig.java`: `@EnableConfigurationProperties(LinkPulseProperties.class)` e `@Bean CodeGenerator`.
- `link/CodeGenerator.java`: `final`; `LENGTH`, `SPACE`, `encode`/`decode`; valida alfabeto ASCII `[0-9A-Za-z]` (62 distintos), M em `(0, 62^7)` e coprimalidade via `modInverse`. Javadoc diz que é ofuscação, não segurança.
- `link/CreateLinkRequest.java`: `@NotBlank @Size(max = 2048) url`, `alias`, `OffsetDateTime expiresAt`, com `@Schema` em pt-BR.
- `link/LinkResponse.java`: corpo D-09 + `from(Link, URI)`.
- `link/LinkController.java`: `POST /links` → `ResponseEntity.created(shortUrl)`.
- `link/LinkService.java`: construtor `(LinkRepository, CodeGenerator, Clock)` e `@Transactional create(request)`, sem log de URL nem de request.
- `application.yml`: bloco `linkpulse` (`base-url`, `code.alphabet`, `code.multiplier`).
- Testes: `LinkApiIT` (4 cenários), `CodeGeneratorTest` (19 casos, 167 linhas) e `ConcurrencyIT`.

## Decisions Made

- A falha do `modInverse` é relançada sem a `ArithmeticException` como causa, para que a causa raiz da falha de subida seja a mensagem "coprimo" (é o que o teste (i) exige).
- `expiresAt` aparece como `null` no corpo quando o link não expira. O contrato D-09 lista o campo, e o IT confere que a chave existe.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Causa raiz da falha de subida era `ArithmeticException`**
- **Found during:** Task 2, ao escrever o teste (i)
- **Issue:** Na Task 1, o `IllegalArgumentException("multiplicador deve ser coprimo de 62^7", e)` encadeava a `ArithmeticException`. Assim, a causa raiz do `getStartupFailure()` seria `ArithmeticException: BigInteger not invertible.`, e não a mensagem pt-BR que o plano exige.
- **Fix:** Relançar sem encadear a causa, com um comentário explicando o porquê. O algoritmo, o alfabeto e o M não mudaram.
- **Files modified:** `app/src/main/java/dev/linkpulse/link/CodeGenerator.java`
- **Commit:** `6fc6122`

**2. [Rule 1 - Bug] Checkstyle `VariableDeclarationUsageDistance` no `ConcurrencyIT`**
- **Found during:** Task 2
- **Issue:** `long before = links.count()` ficava a 4 instruções do primeiro uso (máximo 3), o que derrubava o build em `validate`.
- **Fix:** Declarar como `final long before`. Ela precisa mesmo ser lida antes das criações.
- **Files modified:** `app/src/test/java/dev/linkpulse/link/ConcurrencyIT.java`
- **Commit:** `6fc6122`

---

**Total deviations:** 2 auto-fixed (1 de comportamento exigido pelo critério de aceite, 1 de estilo)
**Impact on plan:** Nenhum. Contratos, vetores e critérios de aceite seguem literais.

## Issues Encountered

- Nenhum. O Docker Desktop já estava ligado (Engine 29.8.0). `LinkApiIT` levou ~17 s isolado, e o `verify` completo ~1 min.

## Known Stubs

Nenhum. O componente `alias` do `CreateLinkRequest` é aceito e ignorado de propósito. O plano define isso: o slice de alias é da 01-04.

## Threat Flags

Nenhuma superfície nova fora do `<threat_model>`. O `POST /links` é o T-03-0x previsto. O T-03-05 (allow-list http/https) segue em aberto até a 01-06, como o plano registra; hoje uma URL `javascript:` ainda seria aceita no `POST`.

## Next Phase Readiness

- 01-04: `LinkService.create` já tem o ponto de entrada para `aliasPolicy.check` + `existsByCode` + tratamento de `DataIntegrityViolationException`. O `CodeGenerator.LENGTH` (7) serve para a disjunção com a regex de alias.
- 01-06: `CreateLinkRequest.url` recebe `@HttpUrl` e `expiresAt` recebe `@Future`. O 400 de `@Valid` hoje sai do `problemdetails` padrão, sem `errors[]`.
- 01-07: `@Schema` já está nos DTOs; faltam as anotações OpenAPI do controller.

---
*Phase: 01-funda-o-e-n-cleo-de-links*
*Completed: 2026-10-08*

## Self-Check: PASSED

- 8/8 arquivos criados encontrados; commits 81dc3a4 e 6fc6122 presentes no histórico.
