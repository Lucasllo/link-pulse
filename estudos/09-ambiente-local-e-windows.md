# 09 · Ambiente local e Windows

**Em uma frase:** para rodar o Link Pulse na sua máquina você precisa de JDK 21 e Docker. O `compose.dev.yaml` sobe Postgres, Valkey e MongoDB; o `Makefile` encurta os comandos; e há equivalentes para Git Bash/WSL, cmd e PowerShell.

## O que você vai aprender

- O que são Docker, container, imagem, porta e volume.
- Como o `compose.dev.yaml` sobe os três bancos de que a API precisa.
- O que o `Makefile` faz e como rodar tudo sem ele.
- Os comandos certos em cada terminal do Windows e os problemas mais comuns.

## Antes de começar: conceitos

### Docker, imagem e container

- Uma **imagem** é um "molde" pronto de um programa, com tudo de que ele precisa (sistema, bibliotecas, configuração). Ex.: `postgres:18.6`. A parte depois dos dois-pontos é a **tag** (a versão).
- Um **container** é uma imagem **rodando**: uma caixa isolada, como um mini-computador dentro do seu.
- O **Docker** é o programa que baixa imagens e roda containers. No Windows, ele vem no **Docker Desktop**.

A vantagem: você não instala Postgres, Valkey nem MongoDB no Windows. Cada um roda na sua caixa, na versão exata do projeto, e some quando você quiser.

### Porta e volume

- **Porta** é o "número da porta de entrada" de um programa na rede. O Postgres atende na 5432, o Valkey na 6379, o MongoDB na 27017, a API na 8080. "Publicar uma porta" liga uma porta do seu computador à porta do container.
- **Volume** é uma pasta guardada pelo Docker fora do container. Sem volume, os dados somem quando o container é apagado; com volume, sobrevivem.

### Docker Compose

O **Docker Compose** descreve vários containers num único arquivo YAML (serviços, portas, volumes, healthchecks) e sobe todos com um comando:

```bash
docker compose -f compose.dev.yaml up -d --wait
```

- `-f compose.dev.yaml`: qual arquivo usar.
- `up`: subir os serviços.
- `-d` (*detached*): rodar em segundo plano e devolver o terminal.
- `--wait`: só devolver o terminal quando todos os **healthchecks** passarem (veja abaixo).

Use sempre `docker compose` (v2, com espaço), nunca o antigo `docker-compose` (com hífen).

### Healthcheck

Um container "ligado" não quer dizer "pronto". O Postgres leva alguns segundos para aceitar conexões depois de iniciar. Um **healthcheck** é um comando que o Docker repete até o serviço responder que está saudável. Com `--wait`, o compose espera todos ficarem saudáveis antes de liberar o terminal, então a API nunca sobe antes dos bancos.

### Make

O **GNU make** lê um `Makefile` com **alvos** (`build:`, `test:`...) e os comandos de cada um. É um jeito de dar nomes curtos a comandos longos. Um alvo pode depender de outro (`run: db-up`), e o make roda a dependência antes. O Git Bash **não** traz o make; instalá-lo é opcional.

### Três terminais no Windows

| Terminal | O que é | Como chama o Maven |
|----------|---------|--------------------|
| **Git Bash / WSL** | Terminal no estilo Linux (bash) | `./mvnw`, e o `make` se instalado |
| **cmd** | O prompt clássico do Windows | `mvnw.cmd` |
| **PowerShell** | O terminal moderno do Windows, com regras próprias | `.\mvnw.cmd` |

## No Link Pulse

### Pré-requisitos (README)

- **JDK 21** no `PATH` (o `PATH` é a lista de pastas onde o sistema procura programas). O Maven não precisa ser instalado: o wrapper baixa a 3.9.16 (guia 07).
- **Docker Desktop ligado**, para os bancos locais e para os testes de integração.
- **Git Bash ou WSL**, para os comandos no estilo Linux.
- **GNU make**, opcional. Formas de instalar, segundo o README: `winget install ezwinports.make`, `scoop install make`, ou de dentro do WSL com `sudo apt install make`.

### O `compose.dev.yaml`, serviço por serviço

`compose.dev.yaml`

```yaml
# Dependências para rodar a API localmente (make db-up / make run): Postgres 18, Valkey 8.1 e
# MongoDB 8.0. Sem make: docker compose -f compose.dev.yaml up -d --wait
# Credenciais fixas só para desenvolvimento local; prod recebe SPRING_DATASOURCE_*,
# SPRING_DATA_REDIS_HOST/SPRING_DATA_REDIS_PORT e SPRING_MONGODB_URI por env.
services:
  postgres:
    image: postgres:18.6
    environment:
      POSTGRES_DB: linkpulse
      POSTGRES_USER: linkpulse
      POSTGRES_PASSWORD: linkpulse
    # Só no loopback: o banco de dev não fica exposto na rede.
    ports:
      - "127.0.0.1:5432:5432"
    # Postgres 18 mudou o PGDATA para /var/lib/postgresql/18/docker; monta-se o diretório pai.
    volumes:
      - pgdata:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U linkpulse -d linkpulse"]
      interval: 5s
      timeout: 3s
      retries: 10
```

**Postgres** (os links, guia 03):

- `environment`: cria o banco `linkpulse` com usuário e senha `linkpulse`. São credenciais **só de desenvolvimento**; em produção elas chegam por variáveis de ambiente.
- `"127.0.0.1:5432:5432"`: publica a porta **só no loopback**, o endereço que significa "este computador". Sem o `127.0.0.1`, o Docker publicaria a porta para a rede inteira, e qualquer pessoa no mesmo Wi-Fi (de uma cafeteria, por exemplo) alcançaria um banco com senha conhecida.
- `pgdata:/var/lib/postgresql`: guarda os dados no volume `pgdata`. O Postgres 18 guarda os dados numa subpasta (`/var/lib/postgresql/18/docker`), por isso o volume é montado na pasta "mãe".
- `pg_isready`: o comando de healthcheck que pergunta ao Postgres se ele aceita conexões.

```yaml
  # Cache do redirect (Redis-compatível). volatile-lru só despeja chaves com TTL, como no ElastiCache.
  valkey:
    image: valkey/valkey:8.1
    command: ["valkey-server", "--maxmemory", "256mb", "--maxmemory-policy", "volatile-lru"]
    # Só no loopback, como o Postgres.
    ports:
      - "127.0.0.1:6379:6379"
    healthcheck:
      test: ["CMD", "valkey-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
```

**Valkey** (cache, stream de cliques e rate limit; guias 12, 13 e 15):

- `--maxmemory 256mb`: o Valkey guarda tudo na RAM; este é o teto.
- `--maxmemory-policy volatile-lru`: o que fazer quando a memória enche. `volatile` = só pode apagar chaves que têm prazo de validade (TTL); `lru` = começa pelas usadas há mais tempo (*least recently used*). Os dados com prazo (o cache) podem ser descartados; os sem prazo (o stream de cliques) ficam. É a mesma política do ElastiCache, o Valkey gerenciado da AWS.
- Sem volume: o Valkey de dev é descartável.
- `valkey-cli ping`: o healthcheck; o Valkey responde `PONG`.

```yaml
  # Eventos de clique (coleção click_events do banco linkpulse), gravados pelo consumer do stream.
  # Sem autenticação: só para desenvolvimento local.
  mongo:
    image: mongo:8.0
    # Só no loopback, como o Postgres e o Valkey.
    ports:
      - "127.0.0.1:27017:27017"
    volumes:
      - mongodata:/data/db
    healthcheck:
      test: ["CMD", "mongosh", "--quiet", "--eval", "db.adminCommand('ping')"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  pgdata: {}
  mongodata: {}
```

**MongoDB** (os cliques, guias 13 e 14):

- Sem usuário e senha, só para desenvolvimento local, e por isso também preso ao loopback.
- O volume `mongodata` guarda os cliques entre reinícios.
- O healthcheck usa o `mongosh`, o terminal do MongoDB, para mandar um `ping`.

O bloco `volumes:` no fim declara os dois volumes nomeados.

### O `Makefile`

`Makefile`

```makefile
# Sem make? Os mesmos comandos rodam direto: cd app && ./mvnw -B -ntp <goal>
# Dependências locais sem make: docker compose -f compose.dev.yaml up -d --wait (e ... down)
MVNW := cd app && ./mvnw -B -ntp
COMPOSE := docker compose -f compose.dev.yaml
# ...
run: db-up ## Sobe as dependências locais e a API com o profile dev
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev
```

- `MVNW` e `COMPOSE` são **variáveis**: apelidos para comandos longos. `$(MVNW)` é trocado por `cd app && ./mvnw -B -ntp`.
- `run: db-up` quer dizer: "antes de `run`, rode `db-up`". Então `make run` sempre sobe os bancos antes da API.
- O texto depois de `##` é a descrição que o `make help` mostra.

Todos os alvos:

| Alvo | O que faz |
|------|-----------|
| `help` | Lista os alvos disponíveis (é o alvo padrão: `make` sozinho roda ele) |
| `build` | Empacota o jar sem rodar testes |
| `test` | Roda só os testes unitários (sem Docker) |
| `verify` | Checkstyle, testes unitários, testes de integração e SpotBugs |
| `run` | Sobe as dependências locais e a API com o profile dev (depende de `db-up`) |
| `db-up` | Sobe as dependências locais do `compose.dev.yaml` e espera os healthchecks |
| `db-down` | Para as dependências locais (mantém os volumes) |

> `make up` (tudo em containers, com Prometheus e Grafana) e `make k8s` ainda não existem: chegam nas Phases 3 e 4 (guia 10).

### Por que o profile dev

O `make run` ativa o profile `dev`, que traz exatamente o que o compose sobe (guia 02):

- o endereço do Postgres local com as credenciais `linkpulse`;
- o seed do link `demo-link` (guia 03);
- uma `ip-hash-key` fixa de desenvolvimento. **Sem o profile dev, a API não sobe**: essa chave é obrigatória e não tem valor padrão fora do dev (guia 13).

O Valkey e o MongoDB não precisam de configuração no profile: o padrão do Boot já é `localhost:6379` e `localhost:27017`.

### Comandos por terminal

Todos a partir da raiz do repositório. O Git Bash usa o script `app/mvnw`; o cmd e o PowerShell usam o `app/mvnw.cmd`. Os dois leem a mesma versão do Maven.

**Git Bash / WSL**

```bash
make run
```

Sem make:

```bash
docker compose -f compose.dev.yaml up -d --wait
cd app && ./mvnw -B -ntp spring-boot:run -Dspring-boot.run.profiles=dev
```

**cmd**

```bat
cd "C:\caminho\para\projeto 4"
docker compose -f compose.dev.yaml up -d --wait
cd app
mvnw.cmd -B -ntp spring-boot:run -Dspring-boot.run.profiles=dev
```

As aspas no `cd` são necessárias por causa do espaço em "projeto 4". Se o cmd responder que `mvnw.cmd` "não é reconhecido como um comando interno ou externo", use `.\mvnw.cmd` (veja "Problemas comuns").

**PowerShell**

```powershell
docker compose -f compose.dev.yaml up -d --wait
cd app; .\mvnw.cmd -B -ntp spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Duas diferenças em relação ao cmd:

- O PowerShell não executa um arquivo da pasta atual só pelo nome, por segurança: é preciso o `.\` na frente.
- O argumento `-D...` vai **entre aspas**. Sem elas, o PowerShell pode quebrar `-Dspring-boot.run.profiles=dev` no ponto e entregar ao Maven dois pedaços; o profile não é ativado e a API não sobe (falta a `ip-hash-key`).

Para rodar a suíte completa no PowerShell (igual ao README):

```powershell
cd app; .\mvnw.cmd -B -ntp verify
```

### Testando a API no PowerShell

No Windows PowerShell 5.1, `curl` é um **apelido** de outro comando (`Invoke-WebRequest`), com outra sintaxe. Chame o curl de verdade pelo nome completo, `curl.exe`:

```powershell
curl.exe -i http://localhost:8080/demo-link
curl.exe http://localhost:8081/actuator/health
```

Para o `POST /links`, o jeito mais simples é o comando nativo do PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/links -ContentType 'application/json' -Body '{"url":"https://example.com/artigo"}'
```

No Git Bash, use os `curl` do guia 01.

### Olhando dentro dos bancos

```bash
# Postgres: os links (guia 03)
docker compose -f compose.dev.yaml exec postgres psql -U linkpulse -d linkpulse

# Valkey: as chaves do cache e do stream (guias 12 e 13)
docker compose -f compose.dev.yaml exec valkey valkey-cli

# MongoDB: os cliques (comando do README)
docker compose -f compose.dev.yaml exec mongo mongosh linkpulse --eval 'db.click_events.find().limit(3)'
```

`docker compose exec <serviço> <comando>` roda um comando **dentro** de um container que já está rodando.

### Parando

```bash
make db-down
# ou, sem make:
docker compose -f compose.dev.yaml down
```

Os dois param os containers e **mantêm** os volumes `pgdata` e `mongodata`, com os links e os cliques. Atenção: `docker compose -f compose.dev.yaml down -v` **apaga os dados** (remove os volumes). Use só quando quiser recomeçar do zero.

### Problemas comuns

- **Porta ocupada (5432, 6379 ou 27017):** outro Postgres, Redis ou MongoDB (instalado no Windows ou de outro projeto) já usa a porta. Pare o outro serviço antes do `make db-up`.
- **Docker parado:** o `docker compose` não consegue falar com o Docker, e os ITs falham com `Could not find a valid Docker environment` (README). Abra o Docker Desktop e espere ele ficar pronto.
- **A API não sobe e reclama de `linkpulse.ip-hash-key`:** o profile dev não foi ativado. Confira o `-Dspring-boot.run.profiles=dev` (com aspas no PowerShell).
- **`mvnw` com CRLF:** aparece "bad interpreter" no Git Bash. O `.gitattributes` evita isso; se aconteceu, o arquivo foi salvo por um editor que trocou os fins de linha (guia 07).
- **Caminhos com espaço:** sempre entre aspas (`cd "...\projeto 4"`). O CI prova a cada push que o build funciona assim (guia 08).
- **`mvnw.cmd` "não é reconhecido" no cmd:** quando a variável de ambiente `NoDefaultCurrentDirectoryInExePath` está definida, o cmd deixa de procurar programas na pasta atual. `.\mvnw.cmd` funciona sempre. Isso foi observado ao escrever este guia: um `cmd.exe` aberto a partir do Git Bash com essa variável recusou `mvnw.cmd` e aceitou `.\mvnw.cmd`.

### Smoke dos comandos Windows (executado)

A partir da raiz do repositório, no Git Bash:

```bash
powershell.exe -NoProfile -Command "Set-Location app; .\mvnw.cmd -v"
MSYS_NO_PATHCONV=1 cmd.exe /d /c "cd app && .\mvnw.cmd -v"
```

Os dois mostraram `Apache Maven 3.9.16` na primeira linha.

## Por que assim?

- **Só os bancos no compose de dev:** a API roda pelo Maven, o que dá recarga rápida e depuração na IDE. A stack completa em containers (`make up`) é tema da Phase 3.
- **Loopback e volumes nomeados:** decisão 01-05 no `.planning/STATE.md`: "Postgres de dev publicado só em 127.0.0.1:5432 com volume em /var/lib/postgresql". A Phase 2 repetiu a regra para o Valkey e o MongoDB.
- **Mesmas versões em todo lugar:** `postgres:18.6`, `valkey/valkey:8.1` e `mongo:8.0` no compose e no Testcontainers (guia 06).
- **Make opcional:** o README traz a versão "Sem make" de cada alvo; ninguém fica bloqueado por não ter make no Windows.

## Experimente

1. Rode `make db-up` (ou o comando sem make) e depois `docker compose -f compose.dev.yaml ps`: os três serviços devem aparecer como saudáveis (`healthy`).
2. Suba a API no terminal que você mais usa e, em outro, chame `/demo-link` e `/actuator/health`.
3. Abra o `valkey-cli`, digite `KEYS lp:*`, chame o `/demo-link` de novo e repita o `KEYS`. Que chaves apareceram? (Guias 12 e 13.)
4. Pare tudo com `make db-down`, suba de novo e confirme que o link criado antes continua lá.

## Perguntas para fixar

1. Por que as portas são publicadas em `127.0.0.1` e não em todas as interfaces?
2. O que a flag `--wait` faz no `docker compose up`, e qual parte do compose ela usa?
3. Por que, no PowerShell, o argumento `-Dspring-boot.run.profiles=dev` vai entre aspas?
4. Qual a diferença entre `docker compose down` e `docker compose down -v`?
5. Por que a API não sobe sem o profile dev?

<details>
<summary>Respostas</summary>

1. Para os bancos (com senhas conhecidas ou sem senha) não ficarem acessíveis para outras máquinas da rede.
2. Faz o comando esperar todos os serviços ficarem saudáveis. Ele usa os `healthcheck` de cada serviço.
3. Porque, sem aspas, o PowerShell pode quebrar o argumento no ponto, e o Maven não recebe o profile.
4. `down` para e remove os containers, mas mantém os volumes (os dados); `down -v` também apaga os volumes.
5. Porque a `linkpulse.ip-hash-key` é obrigatória e só o profile dev tem um valor para ela (e o endereço do Postgres local).

</details>

---

[← Anterior: 08 · CI no GitHub Actions](08-ci-github-actions.md) · [Índice](README.md) · [Próximo: 11 · Dependabot →](11-dependabot.md)
