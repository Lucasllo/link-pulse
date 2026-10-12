# 05 · Validação e erros

**Em uma frase:** a entrada do `POST /links` passa por Bean Validation (com uma regra própria, `@HttpUrl`) e por duas políticas de domínio (`AliasPolicy` e `TargetHostPolicy`). Todo erro da API sai num formato padrão, o Problem Details (RFC 9457), sem vazar detalhes internos.

## O que você vai aprender

- Por que nunca confiar no que chega do cliente.
- Como as anotações de validação funcionam e como criar uma regra própria.
- A diferença entre validar o formato e validar uma regra de negócio.
- O formato de erro padrão (Problem Details) e quem produz cada erro da API.

## Antes de começar: conceitos

### Nunca confie na entrada

Tudo o que chega numa requisição foi escrito por alguém de fora: pode vir errado por engano ou de propósito. Um link para `javascript:alert(1)`, um alias de 5 mil caracteres, uma data no passado. Validar é conferir cada dado **antes** de usá-lo e recusar com uma mensagem clara o que não serve.

### Bean Validation

Bean Validation (Jakarta Validation) é um padrão de **anotações** que descrevem regras de campo:

| Anotação | Regra |
|----------|-------|
| `@NotBlank` | texto obrigatório, e não pode ser só espaços |
| `@Size(max = 2048)` | no máximo 2048 caracteres |
| `@Future` | a data precisa estar no futuro |

O Spring roda essas regras quando um parâmetro do controller está marcado com `@Valid`. Se alguma falha, o método do controller **nem é chamado**: o Spring lança `MethodArgumentNotValidException`, que vira `400`.

Para criar uma regra própria, você escreve duas peças:

1. uma **anotação** marcada com `@Constraint(validatedBy = ...)`;
2. uma classe que implementa **`ConstraintValidator<Anotacao, Tipo>`**, com o método `isValid`, que devolve `true` (válido) ou `false`.

### Formato × regra de domínio

Algumas regras só olham o valor ("é uma URL http?"). Outras dependem de configuração ou do banco ("o alias está na lista de proibidos?", "já existe?"). As primeiras cabem bem em anotações; as segundas ficam em classes de domínio chamadas pelo service.

### Exceção

Uma **exceção** é a forma do Java avisar "algo deu errado aqui". Quando o código faz `throw`, a execução para e a exceção "sobe" pelos métodos que o chamaram até alguém tratá-la (`catch`). No Spring, um **`@RestControllerAdvice`** é esse "alguém" central: ele pega as exceções que escapam dos controllers e as transforma em respostas HTTP.

### Problem Details (RFC 9457)

Uma **RFC** é um documento que padroniza algo na internet. A RFC 9457 define um formato JSON padrão para erros HTTP, com o header `Content-Type: application/problem+json`. Os campos básicos:

| Campo | Significado | Exemplo |
|-------|-------------|---------|
| `type` | identificador do tipo de problema | `/problems/link-not-found` |
| `title` | resumo legível do tipo | `Link não encontrado` |
| `status` | o código HTTP | `404` |
| `detail` | explicação desta ocorrência | `Nenhum link com o código 'abc'.` |
| `instance` | o caminho pedido | `/abc` |

Campos extras são permitidos, como uma lista `errors`. Referência: <https://www.rfc-editor.org/rfc/rfc9457>. No Spring, a classe `ProblemDetail` representa esse corpo, e `ErrorResponseException` é uma exceção que já carrega um `ProblemDetail` pronto.

A vantagem: o cliente trata **qualquer** erro da mesma forma, lendo `type` e `status`.

## No Link Pulse

### As anotações do `CreateLinkRequest`

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

E o gatilho, no `LinkController`: `create(@Valid @RequestBody CreateLinkRequest request)`.

- **`url`**: obrigatória, até 2048 caracteres (o mesmo tamanho da coluna `target_url`) e `@HttpUrl`.
- **`alias`**: sem anotação. As regras dele ficam no `AliasPolicy`, chamado pelo service.
- **`expiresAt`**: `@Future`. Uma data no passado vira `400 validation-error`. Já uma data **sem fuso** (`2026-12-31T23:59:59`, sem o `Z`) nem chega à validação: o conversor de JSON (Jackson 3) recusa e a resposta é `400 malformed-request` (decisão 01-06 no `.planning/STATE.md`).
- **`@Schema`** não valida nada: só documenta o campo na Swagger UI.

### A regra própria `@HttpUrl`

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

- `@Constraint(validatedBy = HttpUrlValidator.class)` liga a anotação à classe que faz a checagem.
- `@Retention(RUNTIME)` mantém a anotação visível enquanto o programa roda (senão o validador não a veria).
- `@Target` só em `FIELD` é proposital: segundo o javadoc, assim "a anotação num componente de record vá apenas para o campo e não seja validada duas vezes".

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

Leitura guiada:

1. **`null` passa.** Quem cobra a obrigatoriedade é o `@NotBlank`; cada anotação cuida de uma coisa só.
2. **`new URI(value)`**: o Java tenta interpretar o texto como URL. Se não conseguir, é inválido.
3. **Esquema só `http` ou `https`.** Isso barra `javascript:`, `data:` e `file:`, que podem executar código ou ler arquivos do computador de quem clica.
4. **Precisa ter host e não pode ter usuário/senha.** O javadoc explica o perigo: em `https://banco.com@evil.com`, "o leitor vê o primeiro host, mas o navegador vai para o segundo". Quem lê acha que vai ao banco e cai num site falso.

### `AliasPolicy`

`app/src/main/java/dev/linkpulse/link/AliasPolicy.java` tem três métodos:

- **`matchesFormat(alias)`**: confere a regex `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$`. Traduzindo: de 4 a 32 caracteres, só letras, dígitos, `_` e `-`, e **pelo menos um** `-` ou `_`. O trecho `(?=.*[-_])` é um *lookahead*, uma "espiada à frente" que exige o hífen ou o sublinhado sem consumir caracteres.
- **`isReserved(alias)`**: compara, em minúsculas, com a lista `linkpulse.alias.reserved` (`links`, `actuator`, `swagger-ui`, `v3`, `api-docs`...). Um alias `actuator` brigaria com as rotas da própria aplicação.
- **`check(alias)`**: chama os dois e, se algo falha, lança `LinkProblems.invalidField("alias", ...)`.

Só a comparação com os reservados ignora maiúsculas. O link em si diferencia: `Minha-Promo` e `minha-promo` são links diferentes (decisão 01-04). A conversão usa `Locale.ROOT` para evitar surpresas de idioma, como o "I" maiúsculo do turco, que vira "ı" (sem ponto) em minúscula.

### `TargetHostPolicy`

`app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java` olha o host do destino (já em minúsculas e sem ponto final) e recusa, nesta ordem:

1. **IP escrito de forma "disfarçada":** `0x7f000001`, `2130706433` e `0177.0.0.1` parecem estranhos, mas o navegador lê todos como `127.0.0.1`. Só a forma normal `a.b.c.d` passa.
2. **O próprio encurtador:** o host de `linkpulse.base-url` e os de `linkpulse.self-hosts`, em qualquer porta. Um link para si mesmo criaria um loop de redirect.
3. **Loopback, sempre:** `localhost`, `*.localhost`, 127.0.0.0/8, `0.0.0.0`, `[::1]`... Esses endereços apontam para a própria máquina de quem clica.

Cada recusa vira `400` no campo `url`. Um detalhe importante do javadoc: "A classe **não resolve DNS**." Um domínio qualquer que aponte para o encurtador só é barrado se estiver em `LINKPULSE_SELF_HOSTS`. O README, em "Riscos conhecidos", chama isso de **mitigação parcial**: consultar o DNS no `POST` deixaria a criação mais lenta e ainda seria contornável (o dono do domínio pode mudar o DNS depois). O rate limit (guia 15) completa a mitigação.

### `LinkProblems`: a fábrica de erros dos links

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

O método **cria** a exceção, mas não a lança: quem a usa escreve `throw LinkProblems.notFound(code)`. Os métodos da classe: `notFound` (404), `expired` (410, com `expiredAt`), `aliasConflict` (409, com `alias`) e `invalidField` (400, com `errors[{field, message}]`). A classe é `final` e tem construtor privado: ninguém a instancia, ela só tem métodos estáticos.

A Phase 2 trouxe duas fábricas no mesmo estilo:

- **`StatsProblems`** (`app/src/main/java/dev/linkpulse/stats/StatsProblems.java`): `invalidPeriod` (400, com `from`, `to` e `maxDays`) e `unavailable` (503, quando o MongoDB está fora). Guia 14.
- **`RateLimitProblems`** (`app/src/main/java/dev/linkpulse/ratelimit/RateLimitProblems.java`): `tooManyRequests` (429, com `retryAfterSeconds` e o header `Retry-After`). Guia 15.

### `GlobalExceptionHandler`: o tradutor central

`app/src/main/java/dev/linkpulse/common/GlobalExceptionHandler.java` é um `@RestControllerAdvice` que estende `ResponseEntityExceptionHandler`, uma classe do Spring que já trata toda `ErrorResponseException` (as de `LinkProblems`, `StatsProblems` e `RateLimitProblems`). Ele sobrescreve quatro casos:

| Método | Quando | Resposta |
|--------|--------|----------|
| `handleMethodArgumentNotValid` | Uma anotação de validação falhou | `400 /problems/validation-error`, com `errors[]` ordenado por campo |
| `handleHttpMessageNotReadable` | JSON quebrado ou campo em formato errado | `400 /problems/malformed-request`, com `detail` fixo |
| `handleTypeMismatch` | Parâmetro de query ou caminho em formato errado (`from=2026-13-01`, `includeBots=talvez`) | `400 /problems/invalid-parameter`, com a propriedade `parameter` |
| `handleUnexpected` | Qualquer outra exceção | `500 /problems/internal-error` |

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

**Por que o 500 é tão vago?** A *stack trace* (o rastro completo do erro) vai só para o log do servidor. O cliente recebe uma frase genérica, porque a mensagem de uma exceção pode revelar SQL, nomes de tabela, versões de biblioteca... informação útil para um atacante. O mesmo vale para os outros casos:

- o `handleHttpMessageNotReadable` não repassa a mensagem do parser, que "expõe detalhes do parser (classe, linha, coluna, tipo Java)";
- o `handleTypeMismatch` cita só o **nome** do parâmetro, nunca o valor recebido. O padrão do Spring repetiria o valor junto com o tipo Java esperado.

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

### Todos os tipos de erro

| `type` | Status | Quando | Quem lança |
|--------|--------|--------|------------|
| `/problems/validation-error` | 400 | Campo inválido (URL, alias, `expiresAt` no passado, host recusado) | `GlobalExceptionHandler` e `LinkProblems.invalidField` |
| `/problems/malformed-request` | 400 | Corpo que não é JSON válido ou campo em formato errado | `GlobalExceptionHandler` |
| `/problems/invalid-parameter` | 400 | Parâmetro de query/caminho em formato errado | `GlobalExceptionHandler` |
| `/problems/invalid-period` | 400 | `/stats` com `from` depois de `to` ou mais de 366 dias | `StatsProblems.invalidPeriod` |
| `/problems/link-not-found` | 404 | Código inexistente (redirect ou stats) | `LinkProblems.notFound` |
| `/problems/alias-conflict` | 409 | O alias já existe | `LinkProblems.aliasConflict` |
| `/problems/link-expired` | 410 | Redirect de um link expirado | `LinkProblems.expired` |
| `/problems/rate-limited` | 429 | Criações demais do mesmo IP | `RateLimitProblems.tooManyRequests` |
| `/problems/internal-error` | 500 | Erro inesperado | `GlobalExceptionHandler.handleUnexpected` |
| `/problems/stats-unavailable` | 503 | MongoDB fora ao pedir estatísticas | `StatsProblems.unavailable` |

Os `type` são caminhos relativos no formato `/problems/<nome>`. Eles identificam o erro, mas não são páginas que abrem no navegador; o javadoc do handler cita a seção 3.1.1 da RFC 9457.

### Testes

- `HttpUrlValidatorTest`: esquemas permitidos, URLs sem host ou malformadas, URLs com credenciais.
- `AliasPolicyTest`: formatos válidos e inválidos, reservados sem diferenciar maiúsculas, `errors[]` com o campo `alias`.
- `TargetHostPolicyTest`: o próprio encurtador, loopback, IPs disfarçados e normalização.
- `LinkApiIT`: os mesmos casos via HTTP, inclusive `expiresAtWithoutOffsetIsMalformedRequest` e o 500 genérico em `becomesGenericInternalErrorWithoutLeakingDetails`, que confere que o corpo não contém "SQL", "uk_links_code" nem "RuntimeException".
- `StatsIT` e `RateLimitIT`: os erros 400, 404, 429 e 503 dos endpoints novos (guias 14 e 15).

## Por que assim?

- **Um formato só para todo erro:** o cliente trata qualquer falha lendo `type` e `status`. A decisão 01-06 registra que o `GlobalExceptionHandler` próprio substitui o tratamento padrão do Boot.
- **Regras de domínio fora das anotações:** reservados e hosts dependem de configuração (`linkpulse.*`), então ficam em classes construídas pelo `ApplicationConfig` (guia 02).
- **Nada de valor do cliente ecoado:** mensagens fixas evitam vazar dados internos e evitam devolver ao cliente o que ele mesmo mandou (que pode conter dados pessoais).
- **Sem vender proteção que não existe:** a checagem de host não consulta o DNS, e o README documenta isso como risco conhecido.

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
curl -i 'http://localhost:8080/links/demo-link/stats?from=2026-13-01'
```

Para cada um, preveja o `status`, o `type` e o campo em `errors[]` antes de rodar.

## Perguntas para fixar

1. Por que `@HttpUrl` aceita `null`?
2. Por que `expiresAt` sem fuso gera `malformed-request` e não `validation-error`?
3. Qual a diferença entre a comparação de reservados e a busca do link quanto a maiúsculas?
4. Por que `TargetHostPolicy` não consulta o DNS, e qual risco fica?
5. Por que o 500 nunca devolve a mensagem da exceção?
6. O que o `invalid-parameter` devolve sobre o parâmetro errado, e o que ele **não** devolve?

<details>
<summary>Respostas</summary>

1. Porque quem cobra a obrigatoriedade é o `@NotBlank`; cada anotação cuida de uma regra só.
2. Porque o Jackson 3 não consegue nem montar o `OffsetDateTime` sem fuso: o erro acontece na leitura do JSON, antes de a validação rodar.
3. Os reservados são comparados sem diferenciar maiúsculas; a busca do link diferencia (`Minha-Promo` ≠ `minha-promo`).
4. Seria mais lento e ainda contornável (o DNS pode mudar depois). Fica o risco de um domínio de terceiros apontar para o encurtador e criar um loop; a mitigação é `LINKPULSE_SELF_HOSTS` mais o rate limit.
5. Porque a mensagem pode revelar SQL, tabelas e versões. O detalhe fica só no log do servidor.
6. Devolve o nome do parâmetro (`parameter`); não devolve o valor recebido.

</details>

---

[← Anterior: 04 · Código curto Base62](04-codigo-curto-base62.md) · [Índice](README.md) · [Próximo: 06 · Testes →](06-testes.md)
