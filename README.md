# Link Pulse

## O que é

Link Pulse é uma API de encurtamento de URLs: cria links curtos (gerados ou com alias) e redireciona para a URL original com 302. Não tem frontend nem autenticação. Este README cobre a Phase 1 (núcleo de links); cache Redis, analytics de cliques, Kubernetes, observabilidade e Terraform entram nas próximas fases.

Stack: **Java 21**, **Spring Boot 4.1.1**, **PostgreSQL 18**, **Liquibase 5.0.3** e **Maven 3.9.16** via wrapper (`app/mvnw`, sem Maven instalado).

## Pré-requisitos

- **JDK 21** no `PATH` (Temurin, Oracle ou outro). O Maven não precisa estar instalado: o wrapper baixa a 3.9.16 e confere o SHA-256.
- **Docker Desktop ligado.** Os testes de integração (`make verify`, Testcontainers) e o Postgres local (`make db-up`, `make run`) precisam do daemon rodando. Com o Docker parado, os ITs falham com `Could not find a valid Docker environment`. Sem Docker, só `make test` (testes unitários) funciona.
- **Git Bash ou WSL no Windows.** O `Makefile` e os exemplos usam sintaxe POSIX (`cd app && ./mvnw ...`).
- **GNU make** (opcional). O **Git Bash não traz make**. No Windows, instale de uma destas formas:
  - `winget install ezwinports.make`
  - `scoop install make`
  - ou rode tudo de dentro do WSL, onde `sudo apt install make` resolve.

  Sem make, use a coluna "Sem make" da tabela abaixo. Os comandos são os mesmos.

Caminhos com espaço são suportados. O CI prova isso a cada push, clonando o repositório em `link pulse/`.

## Comandos

Rode da raiz do repositório.

| Com make | Sem make (Git Bash, WSL, Linux, macOS) | O que faz |
|----------|----------------------------------------|-----------|
| `make help` | — | Lista os alvos |
| `make build` | `cd app && ./mvnw -B -ntp -DskipTests package` | Empacota o jar sem rodar testes |
| `make test` | `cd app && ./mvnw -B -ntp test` | Só os testes unitários (não precisa de Docker) |
| `make verify` | `cd app && ./mvnw -B -ntp verify` | Checkstyle, unitários, ITs com Testcontainers e SpotBugs (precisa de Docker) |
| `make db-up` | `docker compose -f compose.dev.yaml up -d --wait` | Sobe o Postgres 18 local e espera o healthcheck |
| `make db-down` | `docker compose -f compose.dev.yaml down` | Para o Postgres local (mantém o volume `pgdata`) |
| `make run` | `docker compose -f compose.dev.yaml up -d --wait` e depois `cd app && ./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=dev` | Sobe o Postgres e a API com o profile `dev` |

No **PowerShell**, use o `mvnw.cmd`:

```powershell
cd app; .\mvnw.cmd -B -ntp verify
```

Para apagar também os dados locais: `docker compose -f compose.dev.yaml down -v`.

## Rodando local

```bash
make run
```

O profile `dev` aponta para o Postgres do `compose.dev.yaml` (`localhost:5432`, banco/usuário/senha `linkpulse`, só no loopback) e aplica o changeset de seed `demo-link`, que existe **só no profile dev**. Em outro terminal:

```bash
curl -i http://localhost:8080/demo-link
# HTTP/1.1 302
# Location: https://example.com/
# Cache-Control: no-store, private
```

- Swagger UI: <http://localhost:8080/swagger-ui.html> (spec em <http://localhost:8080/v3/api-docs>)
- Health: <http://localhost:8081/actuator/health>. O Actuator fica numa porta separada (8081) e expõe só `health` e `info`.

Fora do profile dev, o datasource vem por variável de ambiente (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`). As credenciais `linkpulse` valem só para o Postgres local de desenvolvimento.

## API

### Criar um link: `POST /links`

```bash
curl -i -X POST http://localhost:8080/links \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/artigo"}'
```

```http
HTTP/1.1 201
Location: http://localhost:8080/EdRbklq
Content-Type: application/json

{
  "code": "EdRbklq",
  "shortUrl": "http://localhost:8080/EdRbklq",
  "targetUrl": "https://example.com/artigo",
  "expiresAt": null,
  "createdAt": "2026-10-08T23:27:24.197641Z"
}
```

O `code` depende do próximo valor da sequência: num banco de dev novo, o ID 1 fica com o seed `demo-link` e o primeiro POST recebe o ID 2 (`EdRbklq`). `expiresAt` volta `null` quando o link não expira.

Com alias e expiração:

```bash
curl -i -X POST http://localhost:8080/links \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/promo","alias":"minha-promo","expiresAt":"2026-12-31T23:59:59Z"}'
```

```http
HTTP/1.1 201
Location: http://localhost:8080/minha-promo

{
  "code": "minha-promo",
  "shortUrl": "http://localhost:8080/minha-promo",
  "targetUrl": "https://example.com/promo",
  "expiresAt": "2026-12-31T23:59:59Z",
  "createdAt": "2026-10-08T23:28:10.512304Z"
}
```

`expiresAt` é devolvido em UTC, com precisão de microssegundos.

### Seguir um link: `GET /{code}`

```bash
curl -i http://localhost:8080/minha-promo
# HTTP/1.1 302
# Location: https://example.com/promo
# Cache-Control: no-store, private
```

O `Cache-Control: no-store, private` impede que navegador ou proxy guardem o redirect. Assim, um link expirado para de redirecionar na hora.

## Regras

- **Alias** (opcional): casa com `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$`, ou seja, de 4 a 32 caracteres entre letras, dígitos, `-` e `_`, com **pelo menos um `-` ou `_`**. Os códigos gerados são só alfanuméricos, então um alias nunca colide com um código que a sequência ainda vai gerar.
  - Palavras reservadas são comparadas **sem** diferenciar maiúsculas: `links`, `actuator`, `swagger-ui`, `v3`, `api-docs`, `health`, `favicon.ico`, `robots.txt`, `admin`, `api`, `static` e `login`. `Swagger-UI` é recusado.
  - Armazenamento e lookup **diferenciam** maiúsculas: `Minha-Promo` e `minha-promo` são links diferentes.
  - Alias repetido → 409.
- **URL**: absoluta, `http` ou `https`, com host, **sem credenciais** (`user:pass@` é recusado), com até 2048 caracteres e **sem apontar para o próprio encurtador** (o host de `LINKPULSE_BASE_URL`, o que evita loop de redirect). `ftp:`, `javascript:`, `data:`, `file:` e `mailto:` são recusados.
- **`expiresAt`** (opcional): ISO-8601 **com offset** (`2026-12-31T23:59:59Z` ou `2026-12-31T20:59:59-03:00`) e **no futuro**. Sem offset, a resposta é 400 `malformed-request`. No passado, 400 `validation-error`. A partir do instante de expiração, o redirect responde 410.
- A **mesma URL enviada duas vezes gera dois links** diferentes. Não há deduplicação.

## Erros (Problem Details, RFC 9457)

Todo erro sai como `application/problem+json`. O `type` é uma URI relativa no formato `/problems/<slug>`. O `detail` nunca traz SQL, stack trace nem nome de exceção.

| `type` | Status | Quando | Campos extras |
|--------|--------|--------|---------------|
| `/problems/validation-error` | 400 | Campo inválido: URL, alias (formato ou reservado) ou `expiresAt` no passado | `errors[{field, message}]` |
| `/problems/malformed-request` | 400 | Corpo que não é JSON válido ou campo em formato errado (por exemplo, `expiresAt` sem offset) | — |
| `/problems/alias-conflict` | 409 | O alias pedido já existe | `alias` |
| `/problems/link-not-found` | 404 | `GET /{code}` de um código inexistente | `code` |
| `/problems/link-expired` | 410 | `GET /{code}` de um link expirado | `code`, `expiredAt` |
| `/problems/internal-error` | 500 | Erro inesperado (detail genérico; o detalhe fica só no log) | — |

Exemplo:

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

## Código curto

O código gerado sai do próximo valor da sequência `link_id_seq` do Postgres (`nextval`), que passa por uma permutação multiplicativa `(id × M) mod 62^7` e é codificado em **Base62 com 7 caracteres**. Não há colisão nem retry, e códigos de IDs vizinhos não ficam parecidos a olho nu (ID 1 → `cJinsNv`, ID 2 → `EdRbklq`).

**Isso é ofuscação, não segurança.** Quem conhece dois códigos consecutivos recupera `M` (`c2 − c1 ≡ M mod 62^7`) e passa a enumerar os links. Não use o código curto como segredo: um link curto é público para quem o tiver.

Configuração (override por variável de ambiente, via relaxed binding do Spring):

| Variável | Padrão | Observação |
|----------|--------|------------|
| `LINKPULSE_BASE_URL` | `http://localhost:8080` | Base da `shortUrl` e do `Location`. Nunca é lida do header `Host` nem de `X-Forwarded-*` |
| `LINKPULSE_CODE_MULTIPLIER` | `2176477521915` | Precisa ser coprimo de `62^7`. Um valor inválido derruba a subida |
| `LINKPULSE_CODE_ALPHABET` | `0-9A-Za-z` (62 caracteres) | Alfabeto do Base62 |
| `LINKPULSE_ALIAS_RESERVED` | lista acima | Lista separada por vírgula |

**Trocar `M` ou o alfabeto muda o código de todos os links já publicados**, e os links antigos deixam de funcionar. Escolha uma vez, antes de publicar.

## Riscos conhecidos

- **Open redirect.** Todo encurtador é, por natureza, um open redirect: qualquer pessoa pode criar um link curto que leva a um site malicioso com cara de link confiável. As mitigações são as regras de URL acima: só `http`/`https`, sem credenciais embutidas (bloqueia `https://banco.com@evil.com`), sem esquemas perigosos (`javascript:`, `data:`, `file:`) e sem apontar para o próprio encurtador. Não há lista de domínios bloqueados nem verificação de reputação.
- **Enumeração.** Ver "Código curto": a ofuscação não impede quem quiser listar os links.
- **API sem autenticação nem rate limit nesta fase.** O rate limit por IP no `POST /links` entra na Phase 2.

## Qualidade e CI

- **Checkstyle** (Google Java Style adaptado para 4 espaços, engine 14.3.0) na fase `validate`. Uma violação quebra o build.
- **SpotBugs** (effort Max, threshold Medium) na fase `verify`.
- **Testes:** `*Test` no Surefire (unitários, sem Docker) e `*IT` no Failsafe, com **Testcontainers** (Postgres 18.6 real). `./mvnw -B -ntp verify` roda tudo.
- **CI** (`.github/workflows/ci.yml`, workflow `ci`): roda em todo push na `main` e em todo pull request, com Java 21 Temurin, checkout em caminho com espaço, `make -n verify` e `./mvnw -B -ntp verify`. As actions são **fixadas por SHA**, e o Dependabot cuida das atualizações.

## Licenças de terceiros

- **Liquibase 5.0.3**, gerenciado pelo BOM do Spring Boot 4.1.1, usa a licença **`FSL-1.1-ALv2`** (Functional Source License, que vira Apache 2.0 depois de dois anos). A FSL permite este uso de portfólio, sem fim comercial concorrente. Se precisar de licença Apache desde já, a alternativa é fixar o Liquibase 4.33.0 por property no `app/pom.xml` (`<liquibase.version>4.33.0</liquibase.version>`).
- As demais dependências (Spring, Hibernate, driver PostgreSQL, springdoc, Testcontainers) usam licenças permissivas (Apache 2.0, MIT ou BSD).
