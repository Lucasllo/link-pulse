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
| `make db-up` | `docker compose -f compose.dev.yaml up -d --wait` | Sobe as dependências locais do `compose.dev.yaml` (Postgres, Valkey e Mongo) e espera os healthchecks |
| `make db-down` | `docker compose -f compose.dev.yaml down` | Para as dependências locais do `compose.dev.yaml` (Postgres, Valkey e Mongo; mantém os volumes `pgdata` e `mongodata`) |
| `make run` | `docker compose -f compose.dev.yaml up -d --wait` e depois `cd app && ./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=dev` | Sobe as dependências locais do `compose.dev.yaml` (Postgres, Valkey e Mongo) e a API com o profile `dev` |

No **PowerShell**, use o `mvnw.cmd`:

```powershell
cd app; .\mvnw.cmd -B -ntp verify
```

Para apagar também os dados locais: `docker compose -f compose.dev.yaml down -v`.

## Rodando local

```bash
make run
```

O profile `dev` aponta para o Postgres do `compose.dev.yaml` (`localhost:5432`, banco/usuário/senha `linkpulse`, só no loopback) e para o Valkey do mesmo arquivo (`127.0.0.1:6379`), e aplica o changeset de seed `demo-link`, que existe **só no profile dev**. Em outro terminal:

```bash
curl -i http://localhost:8080/demo-link
# HTTP/1.1 302
# Location: https://example.com/
# Cache-Control: no-store, private
```

Cada redirect vira um clique: a API publica o evento no stream `lp:clicks` do Valkey sem esperar, e um consumer group lê o stream em lote e grava cada clique como um documento na coleção `click_events` do Mongo do `compose.dev.yaml` (`127.0.0.1:27017`, banco `linkpulse`). O documento guarda o código, o instante, o host do `Referer` e o hash do IP; o IP em claro e o user-agent cru não são gravados. Para ver os cliques chegando:

```bash
docker compose -f compose.dev.yaml exec mongo mongosh linkpulse --eval 'db.click_events.find().limit(3)'
```

- Swagger UI: <http://localhost:8080/swagger-ui.html> (spec em <http://localhost:8080/v3/api-docs>)
- Health: <http://localhost:8081/actuator/health>. O Actuator fica numa porta separada (8081) e expõe `health`, `info` e `prometheus` (<http://localhost:8081/actuator/prometheus>, com hit/miss do cache em `linkpulse_cache_requests_total` e o histograma de latência em `http_server_requests_seconds_bucket`).

### Demonstração: o redirect sem o Valkey

O Valkey é só cache e fila de cliques; o Postgres continua sendo a fonte da verdade. Com a API rodando, pare o Valkey:

```bash
docker compose -f compose.dev.yaml stop valkey
curl -i http://localhost:8080/demo-link
# HTTP/1.1 302
# Location: https://example.com/
```

O redirect continua respondendo 302 pelo Postgres, sem atraso perceptível: o Lettuce tem timeout de comando de 150 ms e de conexão de 250 ms, rejeita comandos na hora com a conexão caída e, depois de uma falha, o cache fica desviado por 2 s (`linkpulse.cache.bypass-cooldown`). A falha aparece em `curl -s http://localhost:8081/actuator/prometheus | grep linkpulse_cache` (`result="error"` e `result="bypass"`) e em `linkpulse_valkey_unavailable_total`. Para voltar: `docker compose -f compose.dev.yaml start valkey`.

Fora do profile dev, as conexões vêm por variável de ambiente: o datasource (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`), o Valkey (`SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`) e o MongoDB (`SPRING_MONGODB_URI`; o banco continua `linkpulse`, fixado em `spring.mongodb.database`). As credenciais `linkpulse` valem só para o Postgres local de desenvolvimento.

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

### Ver estatísticas: `GET /links/{code}/stats`

```bash
curl -s 'http://localhost:8080/links/minha-promo/stats?from=2026-10-01&to=2026-10-03&includeBots=true'
```

```json
{
  "code": "minha-promo",
  "from": "2026-10-01",
  "to": "2026-10-03",
  "includeBots": true,
  "total": 5,
  "daily": [
    { "date": "2026-10-01", "clicks": 3 },
    { "date": "2026-10-02", "clicks": 0 },
    { "date": "2026-10-03", "clicks": 2 }
  ],
  "topReferrers": [
    { "value": "news.example.com", "clicks": 3 },
    { "value": "(direct)", "clicks": 2 }
  ],
  "topBrowsers": [
    { "value": "Firefox", "clicks": 3 },
    { "value": "Other", "clicks": 2 }
  ],
  "topOs": [
    { "value": "Linux", "clicks": 3 },
    { "value": "Other", "clicks": 2 }
  ]
}
```

- **Período**: `from` e `to` são datas `YYYY-MM-DD`, inclusivas e agrupadas em **UTC**. Sem os dois, o período é os últimos 30 dias (`to` = hoje em UTC). Com só `from`, `to` é hoje; com só `to`, `from` é `to` menos 29 dias. O máximo é 366 dias. A resposta sempre ecoa o `from` e o `to` efetivos, e total, série e tops usam o mesmo período.
- **Série diária**: contínua do `from` ao `to`, com `0` nos dias sem clique. Um link sem cliques devolve `total: 0` e tops vazios.
- **Tops**: até 10 itens por dimensão, ordenados por cliques e, no empate, pelo valor. O referrer é o host de origem, ou `(direct)` quando não houve `Referer`. Navegador e SO são famílias, sem versão (`Other` quando não reconhecidos).
- **Bots**: crawlers, previews de link, navegadores headless e clientes de linha de comando ou de biblioteca (`curl`, `wget`, `k6`, `python-requests`...) ficam fora por padrão. **O próprio `curl` conta como bot**: quem testa pelo terminal deve consultar com `includeBots=true` (ou mandar um `User-Agent` de navegador no redirect).
- Os cliques chegam ao MongoDB de forma assíncrona: logo depois do redirect, o total pode levar alguns instantes para subir.
- Link expirado continua com estatísticas (o 410 é só do redirect). A resposta só traz agregados, nunca o hash do IP nem outro dado por visitante.

## Regras

- **Alias** (opcional): casa com `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$`, ou seja, de 4 a 32 caracteres entre letras, dígitos, `-` e `_`, com **pelo menos um `-` ou `_`**. Os códigos gerados são só alfanuméricos, então um alias nunca colide com um código que a sequência ainda vai gerar.
  - Palavras reservadas são comparadas **sem** diferenciar maiúsculas: `links`, `actuator`, `swagger-ui`, `v3`, `api-docs`, `health`, `favicon.ico`, `robots.txt`, `admin`, `api`, `static` e `login`. `Swagger-UI` é recusado.
  - Armazenamento e lookup **diferenciam** maiúsculas: `Minha-Promo` e `minha-promo` são links diferentes.
  - Alias repetido → 409.
- **URL**: absoluta, `http` ou `https`, com host, **sem credenciais** (`user:pass@` é recusado) e com até 2048 caracteres. `ftp:`, `javascript:`, `data:`, `file:` e `mailto:` são recusados. O host de destino passa por três regras, todas com resposta 400 no campo `url`:
  - **Não pode ser o próprio encurtador**: o host de `LINKPULSE_BASE_URL` e os de `LINKPULSE_SELF_HOSTS`, comparados sem diferenciar maiúsculas, sem o ponto final (`localhost.` é igual a `localhost`) e em qualquer porta.
  - **Não pode ser loopback**, em qualquer perfil: `localhost`, `*.localhost`, `127.0.0.0/8`, `0.0.0.0`, `[::1]`, `[::]` e `[::ffff:127.0.0.1]`.
  - **Host numérico só na forma `a.b.c.d`**: `0x7f000001`, `2130706433` e `0177.0.0.1`, que o navegador lê como 127.0.0.1, são recusados.

  A checagem **não resolve DNS**: um domínio de terceiros que aponte para o encurtador só é barrado se estiver em `LINKPULSE_SELF_HOSTS` (ver "Riscos conhecidos").
- **`expiresAt`** (opcional): ISO-8601 **com offset** (`2026-12-31T23:59:59Z` ou `2026-12-31T20:59:59-03:00`) e **no futuro**. Sem offset, a resposta é 400 `malformed-request`. No passado, 400 `validation-error`. A partir do instante de expiração, o redirect responde 410.
- A **mesma URL enviada duas vezes gera dois links** diferentes. Não há deduplicação.

## Erros (Problem Details, RFC 9457)

Todo erro sai como `application/problem+json`. O `type` é uma URI relativa no formato `/problems/<slug>`. O `detail` nunca traz SQL, stack trace nem nome de exceção.

| `type` | Status | Quando | Campos extras |
|--------|--------|--------|---------------|
| `/problems/validation-error` | 400 | Campo inválido: URL, alias (formato ou reservado) ou `expiresAt` no passado | `errors[{field, message}]` |
| `/problems/malformed-request` | 400 | Corpo que não é JSON válido ou campo em formato errado (por exemplo, `expiresAt` sem offset) | — |
| `/problems/alias-conflict` | 409 | O alias pedido já existe | `alias` |
| `/problems/invalid-period` | 400 | `/stats` com `from` depois de `to` ou período acima de 366 dias | `from`, `to`, `maxDays` |
| `/problems/invalid-parameter` | 400 | Parâmetro em formato inválido, como `from=2026-13-01` ou `includeBots=talvez` (o valor recebido não é ecoado) | `parameter` |
| `/problems/link-not-found` | 404 | `GET /{code}` ou `GET /links/{code}/stats` de um código inexistente | `code` |
| `/problems/link-expired` | 410 | `GET /{code}` de um link expirado | `code`, `expiredAt` |
| `/problems/internal-error` | 500 | Erro inesperado (detail genérico; o detalhe fica só no log) | — |
| `/problems/stats-unavailable` | 503 | `/stats` com o MongoDB indisponível; tente de novo em instantes | — |

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
| `LINKPULSE_BASE_URL` | `http://localhost:8080` | Base da `shortUrl` e do `Location`. Nunca é lida do header `Host` nem de `X-Forwarded-*`. Precisa ser uma URL `http` ou `https` absoluta, com host e sem credenciais, query ou fragmento. Um valor inválido derruba a subida |
| `LINKPULSE_SELF_HOSTS` | vazio | Hosts extras que também são o encurtador (DNS do load balancer ou do Ingress) e por isso não podem ser destino de um link. Lista separada por vírgula |
| `LINKPULSE_CODE_MULTIPLIER` | `2176477521915` | Precisa ser coprimo de `62^7`. Um valor inválido derruba a subida |
| `LINKPULSE_CODE_ALPHABET` | `0-9A-Za-z` (62 caracteres) | Alfabeto do Base62 |
| `LINKPULSE_ALIAS_RESERVED` | lista acima | Lista separada por vírgula |
| `LINKPULSE_IP_HASH_KEY` | nenhum fora do profile `dev` | **Obrigatória**, com pelo menos 16 caracteres. Chave do HMAC que transforma o IP do cliente em hash antes de publicar o clique; o IP nunca é guardado em claro. Sem ela a aplicação não sobe. O valor nunca aparece no `toString` das propriedades nem em mensagem de erro |

**Trocar `M` ou o alfabeto muda o código de todos os links já publicados**, e os links antigos deixam de funcionar. Escolha uma vez, antes de publicar.

## Riscos conhecidos

- **Open redirect.** Todo encurtador é, por natureza, um open redirect: qualquer pessoa pode criar um link curto que leva a um site malicioso com cara de link confiável. As mitigações são as regras de URL acima: só `http`/`https`, sem credenciais embutidas (bloqueia `https://banco.com@evil.com`), sem esquemas perigosos (`javascript:`, `data:`, `file:`) e as regras de host da seção "Regras" (próprio encurtador, loopback e IP numérico ofuscado), com a ressalva de DNS do item seguinte. Não há lista de domínios bloqueados nem verificação de reputação.
- **Loop de redirect via DNS (mitigação parcial).** A checagem de host não resolve DNS: resolver no `POST` adicionaria latência e ainda seria contornável por DNS rebinding (o nome pode mudar entre a criação e o clique). Por isso, um domínio de terceiros que resolva para o encurtador (um DNS curinga que devolve 127.0.0.1, um CNAME para o load balancer) e não esteja em `LINKPULSE_SELF_HOSTS` ainda cria um link em loop; o navegador desiste depois de cerca de 20 saltos. Recomendação: declarar em `LINKPULSE_SELF_HOSTS` todos os hostnames conhecidos do encurtador.
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
