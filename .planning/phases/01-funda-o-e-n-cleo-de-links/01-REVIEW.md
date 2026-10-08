---
phase: 01-funda-o-e-n-cleo-de-links
reviewed: 2026-10-08T23:34:12Z
depth: standard
files_reviewed: 44
files_reviewed_list:
  - .gitattributes
  - .github/dependabot.yml
  - .github/workflows/ci.yml
  - .gitignore
  - app/.mvn/wrapper/maven-wrapper.properties
  - app/config/checkstyle/checkstyle.xml
  - app/config/spotbugs/exclude.xml
  - app/pom.xml
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
  - app/src/main/java/dev/linkpulse/LinkPulseApplication.java
  - app/src/main/resources/application.yml
  - app/src/main/resources/application-dev.yml
  - app/src/main/resources/application-prod.yml
  - app/src/main/resources/db/changelog/changes/001-create-links.yaml
  - app/src/main/resources/db/changelog/changes/002-seed-dev.yaml
  - app/src/main/resources/db/changelog/db.changelog-master.yaml
  - app/src/test/java/dev/linkpulse/AbstractIT.java
  - app/src/test/java/dev/linkpulse/common/validation/HttpUrlValidatorTest.java
  - app/src/test/java/dev/linkpulse/link/AliasPolicyTest.java
  - app/src/test/java/dev/linkpulse/link/CodeGeneratorTest.java
  - app/src/test/java/dev/linkpulse/link/ConcurrencyIT.java
  - app/src/test/java/dev/linkpulse/link/LinkApiIT.java
  - app/src/test/java/dev/linkpulse/link/RedirectIT.java
  - app/src/test/java/dev/linkpulse/LiquibaseContextsIT.java
  - app/src/test/java/dev/linkpulse/OpenApiIT.java
  - app/src/test/java/dev/linkpulse/TestcontainersConfiguration.java
  - compose.dev.yaml
  - Makefile
  - README.md
findings:
  critical: 1
  warning: 4
  info: 10
  total: 15
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-10-08T23:34:12Z
**Depth:** standard
**Files Reviewed:** 44
**Status:** issues_found

## Narrative Findings (AI reviewer)

## Summary

Revisei os 44 arquivos da Phase 1: código de produção, testes, build, CI, compose, Makefile e README. O núcleo está correto. A bijeção do `CodeGenerator` usa `BigInteger`, a corrida de alias é traduzida em 409 pelo nome da constraint, a entidade com ID atribuído implementa `Persistable`, e o handler central não vaza mensagem de exceção. Os testes cobrem bem esses pontos.

O problema principal é que a mitigação "URL não pode apontar para o próprio encurtador" (T-06-03) dá para contornar de forma trivial. O README e o threat model dizem que ela está fechada. Testei com `java.net.URI` real: `http://localhost.:8080/...`, com ponto no final do host, e `http://127.0.0.1:8080/...` passam na checagem. Com isso, dá para criar um link com alias que redireciona para ele mesmo, em loop.

Também achei estes problemas:
- `linkpulse.base-url` não é validada na subida. Uma configuração errada desliga a checagem de host em silêncio e gera `shortUrl` relativa.
- Um `expiresAt` muito distante, mas sintaticamente válido, devolve 500 em vez de 400.
- `errors[].message` muda de idioma conforme o `Accept-Language` do cliente.
- Os exemplos de `expiresAt` na Swagger UI e no README usam `2026-12-31` e começam a falhar com 400 daqui a menos de 3 meses.

## Critical Issues

### CR-01: A checagem "não apontar para o próprio encurtador" dá para contornar com ponto no final do host, IP ou outro hostname, o que permite criar um link em loop

**File:** `app/src/main/java/dev/linkpulse/link/LinkService.java:112-115` (consumido em `:76-78`; afirmação em `README.md:131` e `README.md:180`)
**Issue:** `pointsToShortener` compara só `URI.getHost()` com `properties.baseUrl().getHost()` usando `equalsIgnoreCase`. Confirmei com o `java.net.URI` do JDK 21:

```
http://localhost.:8080/loop-a -> host=localhost.
http://127.0.0.1:8080/x       -> host=127.0.0.1
http://[::1]:8080/x           -> host=[::1]
http://0x7f000001:8080/x      -> host=0x7f000001
```

Nenhum desses hosts é igual a `localhost`, então todos são aceitos. O `@HttpUrl` também aceita todos. O alias é escolhido pelo cliente, então o loop sai num único POST:

```json
{"url":"http://localhost.:8080/loop-a","alias":"loop-a"}
```

`GET /loop-a` → 302 para `/loop-a` → 302... até o navegador desistir, perto de 20 saltos. Em prod, com `LINKPULSE_BASE_URL=https://lnk.example`, o mesmo acontece com `https://lnk.example./x` (o FQDN com ponto final resolve igual) ou com o hostname do load balancer. O impacto direto é limitado: links quebrados e cerca de 20 requisições por clique. Mas a Phase 2 vai registrar cada salto como clique, o que infla as estatísticas e multiplica a carga no Stream. E o README (linha 131) e o threat model (T-06-03 marcado como "mitigado") afirmam uma garantia que o código não entrega. O único teste (`LinkApiIT#urlPointingToTheShortenerItselfIsValidationErrorOnUrl`) cobre só a variação de caixa.
**Fix:** normalizar o host antes de comparar e aceitar uma lista de hosts do próprio serviço, em vez de um só:

```java
private boolean pointsToShortener(String url) {
    String host = normalizeHost(URI.create(url).getHost());
    return host != null && properties.selfHosts().contains(host);
}

private static String normalizeHost(String host) {
    if (host == null) {
        return null;
    }
    String h = host.toLowerCase(Locale.ROOT);
    while (h.endsWith(".")) {
        h = h.substring(0, h.length() - 1);
    }
    return h;
}
```

`selfHosts()` deve ser derivado de `baseUrl` (normalizado) mais uma lista opcional `linkpulse.self-hosts` (hostname do ALB, IPs). Em dev, `localhost`, `127.0.0.1` e `[::1]` também entram. Também vale bloquear hosts IP literais em loopback com `InetAddress`, sem resolver DNS. Acrescente casos de teste com `localhost.`, `127.0.0.1` e `[::1]`. Se a cobertura completa (outros DNS que apontam para o serviço) ficar fora de escopo, ajuste o README e o threat model para "mitigação parcial".

## Warnings

### WR-01: `linkpulse.base-url` não é validada na subida: configuração errada desliga a checagem de host e gera `shortUrl` relativa

**File:** `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java:28`
**Issue:** a única restrição é `@NotNull URI baseUrl`. Com `LINKPULSE_BASE_URL=lnk.example.com`, sem esquema (erro comum em compose, Helm ou ECS), `URI.getHost()` devolve `null` (confirmado). Daí:
1. `LinkService.pointsToShortener` (linha 114) compara com `null` e sempre devolve `false`, o que desliga a proteção de CR-01 sem aviso.
2. `LinkController` (linhas 70-73) monta `shortUrl = "lnk.example.com/<code>"`, uma URL relativa, e devolve isso no `Location` do 201.

O projeto já valida alfabeto e multiplicador na subida (D-03), mas não valida o parâmetro do qual a URL pública inteira depende.
**Fix:** validar no construtor compacto do record, ou num `@Bean`, e derrubar o contexto:

```java
public LinkPulseProperties {
    if (baseUrl != null && (!baseUrl.isAbsolute() || baseUrl.getHost() == null
            || !("http".equalsIgnoreCase(baseUrl.getScheme())
                 || "https".equalsIgnoreCase(baseUrl.getScheme()))
            || baseUrl.getQuery() != null || baseUrl.getFragment() != null)) {
        throw new IllegalArgumentException(
                "linkpulse.base-url deve ser uma URL http(s) absoluta com host: " + baseUrl);
    }
}
```

Acrescente um caso `ApplicationContextRunner` como o do `CodeGeneratorTest#contextFailsOnStartupWhenMultiplierIsNotCoprime`.

### WR-02: `expiresAt` válido para o Jackson e para `@Future`, mas fora do intervalo do `timestamptz`, devolve 500 com stack trace em ERROR

**File:** `app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java:27`, `app/src/main/java/dev/linkpulse/link/LinkService.java:89-99`
**Issue:** `OffsetDateTime.parse("+300000-01-01T00:00:00Z")` é aceito (confirmado), e `@Future` também passa. O PostgreSQL só guarda `timestamptz` até o ano 294276, então o `saveAndFlush` falha com `22008 timestamp out of range`. O Spring traduz isso como `DataIntegrityViolationException`. Como `alias == null` (ou `isCodeConflict` dá `false`), o `catch` da linha 94 relança, e o `GlobalExceptionHandler.handleUnexpected` responde 500 `internal-error` e grava a stack completa em ERROR. É erro de entrada do cliente virando 500. Isso polui alertas (a Phase 4 terá alerta de taxa de 5xx) e deixa qualquer cliente anônimo gerar logs ERROR à vontade.
**Fix:** limitar a expiração a um teto de domínio e responder 400 no campo:

```java
private static final Instant MAX_EXPIRES_AT = Instant.parse("9999-12-31T23:59:59Z");
...
if (expiresAt != null && expiresAt.isAfter(MAX_EXPIRES_AT)) {
    throw LinkProblems.invalidField("expiresAt", "deve ser anterior a 9999-12-31T23:59:59Z");
}
```

Também dá para criar uma constraint própria (`@FutureBefore`) no DTO, para o erro entrar no mesmo `errors[]` do Bean Validation. Acrescente um IT com `+300000-01-01T00:00:00Z` esperando 400.

### WR-03: `errors[].message` muda de idioma conforme o `Accept-Language`, e o contrato fica misturando pt-BR e outros idiomas

**File:** `app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java:55-61`, `app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java:21,27`
**Issue:** `@NotBlank`, `@Size` e `@Future` usam as mensagens padrão do Hibernate Validator. Não existe `ValidationMessages.properties` em `src/main/resources`. O `LocalValidatorFactoryBean` do Spring interpola com `LocaleContextHolder`, ou seja, com o locale da requisição. Conferi no jar do HV 9.1.3: com `Accept-Language: en`, o retorno é `"must not be blank"` / `"size must be between 0 and 2048"`; com `pt-BR`, `"não deve estar em branco"`; com `de`, alemão. Já `@HttpUrl` (mensagem literal) e as regras de alias e de host próprio (`LinkProblems.invalidField`) respondem sempre em pt-BR. Assim, uma mesma resposta 400 pode trazer `errors[]` com dois idiomas, e o texto depende do cliente e do locale da JVM (CI em Linux `en` contra máquina Windows `pt-BR`). O resto do contrato é fixo em pt-BR (detail e title).
**Fix:** fixar as mensagens no próprio DTO, ou criar `src/main/resources/ValidationMessages.properties` com as chaves usadas:

```java
@NotBlank(message = "é obrigatória")
@Size(max = 2048, message = "deve ter no máximo 2048 caracteres")
@HttpUrl String url,
...
@Future(message = "deve estar no futuro") OffsetDateTime expiresAt
```

Acrescente um IT com `Accept-Language: en` que confira a mensagem em pt-BR.

### WR-04: Exemplos de `expiresAt` com data fixa `2026-12-31` vão falhar com 400 em menos de 3 meses (Swagger UI "Try it out" e README)

**File:** `app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java:26`, `app/src/main/java/dev/linkpulse/link/LinkController.java:56`, `README.md:96`, `README.md:132`
**Issue:** o `@Schema(example = "2026-12-31T23:59:59Z")` vira o corpo padrão do "Try it out" na Swagger UI. A partir de 2027-01-01, o avaliador que clicar em "Execute" com o exemplo recebe 400 `validation-error` em `expiresAt` (`@Future`). O curl do README (linha 96) quebra da mesma forma. O público-alvo do projeto (recrutadores) vai abrir o repositório meses depois da entrega, e o primeiro contato com a API seria um erro. O exemplo também usa `alias: "minha-promo"`, então a segunda execução do exemplo padrão responde 409.
**Fix:** usar uma data distante nos exemplos (`2099-12-31T23:59:59Z`, a mesma do `LinkApiIT`) e, no `@Schema` do alias, um texto que deixe claro que o valor é ilustrativo. O formato citado no detail do `GlobalExceptionHandler.java:79` pode continuar com qualquer data, porque ali ela só mostra o formato.

## Info

### IN-01: 10 das 12 palavras reservadas nunca são alcançáveis pela regex do alias

**File:** `app/src/main/resources/application.yml:38-50`, `README.md:128`
**Issue:** `links`, `actuator`, `v3`, `health`, `admin`, `api`, `static` e `login` não têm `-` nem `_`. `favicon.ico` e `robots.txt` têm `.`. A `ALIAS_REGEX` rejeita todas antes da checagem de reservados, então só `swagger-ui` e `api-docs` têm efeito. O README apresenta a lista inteira como regra ativa.
**Fix:** deixar na lista só nomes que casam com a regex (`swagger-ui`, `api-docs` e eventuais rotas futuras com hífen) ou documentar que a lista vale como defesa contra uma mudança futura da regex.

### IN-02: README promete `type` `/problems/<slug>` para "todo erro", mas os erros do framework saem com `about:blank`

**File:** `README.md:137`, `app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java:25-28`
**Issue:** 404 de rota inexistente (`/favicon.ico`, `/a/b`), 405 (`PUT /links`), 415 (Content-Type errado) e 406 passam pelos handlers herdados do `ResponseEntityExceptionHandler`, com `type: about:blank`. O comportamento é aceitável pela RFC 9457, mas contradiz a frase do README.
**Fix:** ajustar o README ("erros de domínio usam `/problems/<slug>`; erros genéricos de HTTP usam `about:blank`") ou sobrescrever `createProblemDetail`/`handleExceptionInternal` para preencher um `type` próprio.

### IN-03: `GET /links` cai no redirect (404 `link-not-found` com `code: "links"`) e o `{code}` não tem limite de tamanho

**File:** `app/src/main/java/dev/linkpulse/link/RedirectController.java:45`
**Issue:** `/{code:[A-Za-z0-9_-]+}` casa com `links`. Num GET, o mapeamento POST de `/links` não serve, então o Spring escolhe o redirect e responde 404 "Nenhum link com o código 'links'", quando o esperado seria 405. A regex também não limita o tamanho, e qualquer segmento de até cerca de 8 KB (limite do Tomcat) vira consulta ao banco, apesar de a coluna ter só 32 caracteres.
**Fix:** `@GetMapping("/{code:[A-Za-z0-9_-]{1,32}}")`. Para `/links`, tanto faz aceitar o 404 quanto declarar um `@GetMapping("/links")` que responda 405.

### IN-04: Alias validado só no service, então `errors[]` não junta erros de URL e de alias, e a spec não publica pattern nem maxLength

**File:** `app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java:22-24`, `app/src/main/java/dev/linkpulse/link/LinkService.java:79-81`
**Issue:** com URL e alias inválidos ao mesmo tempo, o cliente recebe só o erro de `url`. O de `alias` aparece só depois que a URL é corrigida. Na OpenAPI, `alias` sai como string livre, sem `pattern` nem `maxLength`.
**Fix:** `@Pattern(regexp = AliasPolicy.ALIAS_REGEX, message = ...)` no componente `alias`. A checagem de reservados continua no service. Acrescente `@Schema(pattern = ..., maxLength = 32)`.

### IN-05: `@Future` usa o relógio do sistema, não o bean `Clock` declarado como "único ponto de obtenção do tempo"

**File:** `app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java:27`, `app/src/main/java/dev/linkpulse/config/ApplicationConfig.java:17-25`
**Issue:** o Hibernate Validator usa o `ClockProvider` padrão (system clock). Um teste que troque o `Clock` por um relógio fixo vê `@Future` e `LinkService.resolve` divergirem.
**Fix:** registrar um `ClockProvider` com o bean `Clock` via `ValidationConfigurationCustomizer` (`configuration.clockProvider(() -> clock)`).

### IN-06: Checagem `cause.getCause() == cause` é código morto, e um ciclo maior na cadeia de causas não é detectado

**File:** `app/src/main/java/dev/linkpulse/link/LinkService.java:135-137`
**Issue:** `Throwable.getCause()` nunca devolve `this` (devolve `null` quando a causa é ela mesma), então o `break` nunca roda. Uma cadeia cíclica A→B→A deixaria o laço infinito.
**Fix:** guardar as causas já vistas num `Set` (`Collections.newSetFromMap(new IdentityHashMap<>())`) ou limitar a profundidade, por exemplo a 16.

### IN-07: SpotBugs desliga EI_EXPOSE_REP/EI_EXPOSE_REP2 no projeto inteiro, com justificativa que cita classes inexistentes

**File:** `app/config/spotbugs/exclude.xml:16-27`
**Issue:** o próprio arquivo proíbe "excluir por pacote inteiro", mas exclui os dois padrões globalmente, o que é ainda mais amplo. A justificativa cita `StringRedisTemplate` e `MeterRegistry`, que não existem na base. Um array ou coleção mutável exposto por engano em código de domínio passaria sem alerta.
**Fix:** restringir com `<Or><Class name="~.*Properties.*"/>...</Or>` ou `<Field type=...>` aos casos reais (records de configuração, beans injetados) e atualizar o comentário.

### IN-08: `cancel-in-progress: true` também vale para pushes na `main`

**File:** `.github/workflows/ci.yml:12-14`
**Issue:** dois pushes seguidos na `main` cancelam o build do primeiro commit, que fica sem resultado de CI.
**Fix:** `cancel-in-progress: ${{ github.event_name == 'pull_request' }}`.

### IN-09: Dependabot não acompanha as imagens Docker fixadas

**File:** `.github/dependabot.yml:1-14`
**Issue:** `postgres:18.6`, em `compose.dev.yaml:6` e `TestcontainersConfiguration.java:21`, não recebe PRs de atualização (patches de segurança do Postgres).
**Fix:** acrescentar `package-ecosystem: docker-compose` (diretório `/`). A string no Testcontainers continua manual, então vale pô-la numa constante comentada.

### IN-10: `ConcurrencyIT` usa o número mágico `8` em vez de `THREADS`

**File:** `app/src/test/java/dev/linkpulse/link/ConcurrencyIT.java:39`
**Issue:** `Executors.newFixedThreadPool(8)` duplica a constante. Se `THREADS` subir, o pool fica menor que o número de tarefas, e o latch não libera todas ao mesmo tempo, o que enfraquece o teste de concorrência sem nenhum aviso.
**Fix:** `Executors.newFixedThreadPool(THREADS)`.

---

_Reviewed: 2026-10-08T23:34:12Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
