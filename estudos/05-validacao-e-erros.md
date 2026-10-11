# 05 · Validação e erros

**Em uma frase:** a entrada do `POST /links` passa por Bean Validation (com uma constraint própria, `@HttpUrl`) e por duas políticas de domínio (`AliasPolicy` e `TargetHostPolicy`), e todo erro sai no formato Problem Details da RFC 9457, sem vazar detalhes internos.

## Conceitos

### Bean Validation

Bean Validation (Jakarta Validation) é um padrão de anotações que descrevem regras de campo: `@NotBlank`, `@Size(max = ...)`, `@Future` e muitas outras. O Spring roda essas regras quando um parâmetro do controller está marcado com `@Valid`. Se alguma falhar, o método do controller nem é chamado: o Spring lança `MethodArgumentNotValidException`.

Para criar uma regra própria, você escreve:

1. uma **anotação** marcada com `@Constraint(validatedBy = ...)`, com os atributos obrigatórios `message`, `groups` e `payload`;
2. uma classe que implementa **`ConstraintValidator<Anotacao, Tipo>`** com o método `isValid`.

### Validação de formato × regra de domínio

Algumas regras olham só o valor ("é uma URL http?"). Outras dependem de configuração ou de estado ("o alias está na lista de reservados?", "já existe no banco?"). As primeiras cabem bem em anotações; as segundas costumam ficar em classes de domínio chamadas pelo service.

### Problem Details (RFC 9457)

A RFC 9457 define um formato JSON padrão para erros HTTP, com `Content-Type: application/problem+json`. Os campos básicos são:

- `type`: URI que identifica o tipo de problema;
- `title`: resumo legível do tipo;
- `status`: o código HTTP;
- `detail`: explicação desta ocorrência;
- `instance`: URI da ocorrência (normalmente o caminho pedido).

Campos extras (extensões) são permitidos, como uma lista `errors`. Referência: <https://www.rfc-editor.org/rfc/rfc9457>. No Spring, a classe `ProblemDetail` representa esse corpo, e `ErrorResponseException` é uma exceção que já carrega um `ProblemDetail`.

## No Link Pulse

### Anotações em `CreateLinkRequest`

`app/src/main/java/dev/linkpulse/link/CreateLinkRequest.java`

```java
public record CreateLinkRequest(
        @Schema(description = "URL de destino do link curto",
                example = "https://example.com/artigo?id=42")
        @NotBlank @Size(max = 2048) @HttpUrl String url,
        @Schema(description = "Código escolhido pelo cliente no lugar do gerado (opcional)",
                example = "minha-promo")
        String alias,
        @Schema(description = "Expiração em ISO-8601 com offset obrigatório (opcional)",
                example = "2026-12-31T23:59:59Z")
        @Future OffsetDateTime expiresAt) {
}
```

E o gatilho, em `app/src/main/java/dev/linkpulse/link/LinkController.java`: `create(@Valid @RequestBody CreateLinkRequest request)`.

- `url`: obrigatória, no máximo 2048 caracteres (o mesmo tamanho da coluna `target_url`) e `@HttpUrl`.
- `alias`: sem anotação; as regras ficam em `AliasPolicy`, chamada pelo service.
- `expiresAt`: `OffsetDateTime` com `@Future`. Um instante no passado vira 400 `validation-error`. Já um valor **sem offset** nem chega à validação: o Jackson 3 recusa a conversão e a resposta é 400 `malformed-request` (decisão 01-06 no `.planning/STATE.md`).

### A constraint própria `@HttpUrl`

`app/src/main/java/dev/linkpulse/common/validation/HttpUrl.java`

```java
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {
    // ...
    String message() default "deve ser uma URL http ou https absoluta, com host e sem credenciais";
```

O `@Target` só em `FIELD` é proposital: segundo o javadoc, assim "a anotação num componente de record vá apenas para o campo e não seja validada duas vezes".

`app/src/main/java/dev/linkpulse/common/validation/HttpUrlValidator.java`

```java
public final class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return false;
        }
        String host = uri.getHost();
        return host != null && !host.isBlank() && uri.getUserInfo() == null;
    }
}
```

Regras: parse estrito pelo `URI` do JDK, esquema só `http`/`https` (barra `javascript:`, `data:`, `file:`), host obrigatório e nada de *userinfo*. O javadoc explica o último ponto: em `https://banco.com@evil.com`, "o leitor vê o primeiro host, mas o navegador vai para o segundo". `null` passa porque a obrigatoriedade é do `@NotBlank`.

### `AliasPolicy`

`app/src/main/java/dev/linkpulse/link/AliasPolicy.java` expõe três métodos:

- `matchesFormat(alias)`: casa com `ALIAS_REGEX = "^(?=.*[-_])[A-Za-z0-9_-]{4,32}$"`, ou seja, 4 a 32 caracteres de `[A-Za-z0-9_-]` com pelo menos um `-` ou `_` (o `(?=.*[-_])` é um *lookahead* que exige isso sem consumir caracteres);
- `isReserved(alias)`: compara em minúsculas com `Locale.ROOT` contra `linkpulse.alias.reserved` (`links`, `actuator`, `swagger-ui`, `v3`, `api-docs` etc.);
- `check(alias)`: chama os dois e lança `LinkProblems.invalidField("alias", ...)`.

Só a comparação com os reservados ignora a caixa. O armazenamento e o lookup diferenciam maiúsculas: `Minha-Promo` e `minha-promo` são links diferentes (decisão 01-04). `Locale.ROOT` evita surpresas de idioma, como a conversão do "I" em turco.

### `TargetHostPolicy`

`app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java` olha o host já normalizado (minúsculas, sem ponto final) e recusa, nesta ordem:

1. **Host numérico fora da forma canônica:** `0x7f000001`, `2130706433` e `0177.0.0.1` são lidos pelo navegador como 127.0.0.1. Só `a.b.c.d` decimal sem zero à esquerda passa (`CANONICAL_IPV4`).
2. **O próprio encurtador:** o host de `linkpulse.base-url` e os de `linkpulse.self-hosts`, em qualquer porta.
3. **Loopback, em qualquer perfil:** `localhost`, `*.localhost`, 127.0.0.0/8, 0.0.0.0, `[::1]`, `[::]` e IPv4-mapped.

Cada recusa vira `LinkProblems.invalidField("url", ...)`, com 400 no campo `url`. Um detalhe essencial do javadoc: "A classe **não resolve DNS**." Um domínio de terceiros que aponte para o encurtador só é barrado se estiver em `LINKPULSE_SELF_HOSTS`. O README, em "Riscos conhecidos", chama isso de **mitigação parcial**: resolver DNS no `POST` custaria latência e ainda seria contornável por DNS rebinding (o nome pode mudar entre a criação e o clique). O rate limit da Phase 2 entra como parte da mitigação (ver guia 10).

### `LinkProblems`: fábrica de erros

`app/src/main/java/dev/linkpulse/link/LinkProblems.java`

```java
    public static ErrorResponseException notFound(String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Nenhum link com o código '" + code + "'.");
        problem.setType(URI.create("/problems/link-not-found"));
        problem.setTitle("Link não encontrado");
        problem.setProperty("code", code);
        return new ErrorResponseException(HttpStatus.NOT_FOUND, problem, null);
    }
```

Métodos: `notFound` (404), `expired` (410, com `expiredAt`), `aliasConflict` (409, com `alias`) e `invalidField` (400, com `errors[{field, message}]`). A classe é `final` com construtor privado: só métodos estáticos.

### `GlobalExceptionHandler`

`app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java` é um `@RestControllerAdvice` que estende `ResponseEntityExceptionHandler`. Os handlers herdados já tratam toda `ErrorResponseException` (as de `LinkProblems`). A classe sobrescreve três casos:

- `handleMethodArgumentNotValid`: erro de Bean Validation vira `/problems/validation-error` com `errors[]` ordenado por campo, no mesmo formato de `LinkProblems.invalidField`;
- `handleHttpMessageNotReadable`: JSON inválido ou campo em formato errado vira `/problems/malformed-request`, com `detail` fixo;
- `handleUnexpected` (`@ExceptionHandler(Exception.class)`): qualquer outra exceção vira 500 `/problems/internal-error`.

```java
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        LOG.error("Erro inesperado ao processar a requisição", ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Erro inesperado. Tente novamente mais tarde.");
        body.setType(INTERNAL_ERROR);
        body.setTitle("Erro interno");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
```

A stack trace vai só para o log do servidor. O cliente recebe uma mensagem genérica, porque mensagens de exceção podem revelar SQL, nomes de tabela, constraints ou versões de biblioteca (vazamento de informação). O javadoc do `handleHttpMessageNotReadable` diz o mesmo sobre o parser: a mensagem "expõe detalhes do parser (classe, linha, coluna, tipo Java)".

### Exemplo de resposta (README)

```json
{
  "type": "/problems/validation-error",
  "title": "Requisição inválida",
  "status": 400,
  "detail": "Um ou mais campos são inválidos.",
  "instance": "/links",
  "errors": [{ "field": "url", "message": "deve ser uma URL http ou https absoluta, com host e sem credenciais" }]
}
```

### Tabela de problem types

| `type` | Status | Quem lança |
|--------|--------|------------|
| `/problems/validation-error` | 400 | `GlobalExceptionHandler.handleMethodArgumentNotValid` (Bean Validation) e `LinkProblems.invalidField` (`AliasPolicy`, `TargetHostPolicy`) |
| `/problems/malformed-request` | 400 | `GlobalExceptionHandler.handleHttpMessageNotReadable` |
| `/problems/link-not-found` | 404 | `LinkProblems.notFound` (via `LinkService.resolve`) |
| `/problems/alias-conflict` | 409 | `LinkProblems.aliasConflict` (via `LinkService.create`) |
| `/problems/link-expired` | 410 | `LinkProblems.expired` (via `LinkService.resolve`) |
| `/problems/internal-error` | 500 | `GlobalExceptionHandler.handleUnexpected` |

Os `type` são URIs relativas e não resolvíveis, no formato `/problems/<slug>`; o javadoc do handler cita a seção 3.1.1 da RFC 9457.

### Testes

- `app/src/test/java/dev/linkpulse/common/validation/HttpUrlValidatorTest.java`: allow-list de esquemas, URLs sem host ou com sintaxe inválida, URLs com credenciais.
- `app/src/test/java/dev/linkpulse/link/AliasPolicyTest.java`: formatos válidos e inválidos, reservados sem caixa, `errors[]` com o campo `alias`.
- `app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java`: próprio encurtador, loopback, hosts numéricos ofuscados e normalização.
- `app/src/test/java/dev/linkpulse/link/LinkApiIT.java`: os mesmos casos via HTTP, incluindo `expiresAtWithoutOffsetIsMalformedRequest` e o 500 genérico em `becomesGenericInternalErrorWithoutLeakingDetails`, que confere que o corpo não contém "SQL", "uk_links_code" nem "RuntimeException".

## Por que assim?

- **Um formato só para todo erro:** o cliente trata qualquer falha lendo `type` e `status`. A decisão 01-06 registra que o `GlobalExceptionHandler` próprio substitui o handler de Problem Details do Boot.
- **Regras de domínio fora das anotações:** reservados e hosts dependem de configuração (`linkpulse.*`), então ficam em classes construídas pelo `ApplicationConfig`.
- **Sem vender proteção que não existe:** a checagem de host não resolve DNS, e o README documenta o loop via DNS como risco conhecido.

## Experimente

Com a API no ar (guia 09):

```bash
curl -i -X POST http://localhost:8080/links -H 'Content-Type: application/json' \
  -d '{"url":"ftp://example.com/x"}'
curl -i -X POST http://localhost:8080/links -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/","alias":"Swagger-UI"}'
curl -i -X POST http://localhost:8080/links -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/","expiresAt":"2026-12-31T23:59:59"}'
curl -i -X POST http://localhost:8080/links -H 'Content-Type: application/json' \
  -d '{"url":"http://0x7f000001/"}'
```

Para cada um, preveja o `type` e o campo em `errors[]` antes de rodar.

## Perguntas para fixar

1. Por que `@HttpUrl` aceita `null`?
2. Por que `expiresAt` sem offset gera `malformed-request` e não `validation-error`?
3. Qual a diferença entre a comparação de reservados e o lookup do alias quanto a maiúsculas?
4. Por que `TargetHostPolicy` não resolve DNS, e qual o risco que fica?
5. Por que o 500 nunca devolve a mensagem da exceção?

---

[← Anterior: 04 · Código curto Base62](04-codigo-curto-base62.md) · [Índice](README.md) · [Próximo: 06 · Testes →](06-testes.md)
