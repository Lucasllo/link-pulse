# 09 · Ambiente local e Windows

**Em uma frase:** para rodar o Link Pulse localmente você precisa de JDK 21 e Docker; o `compose.dev.yaml` sobe só o Postgres, o `Makefile` encurta os comandos, e há equivalentes para Git Bash/WSL, cmd e PowerShell.

## Conceitos

### Docker Compose

O Docker Compose descreve serviços (containers), portas, volumes e healthchecks num arquivo YAML e os sobe com um comando: `docker compose -f arquivo.yaml up -d`. A flag `--wait` faz o comando esperar até os healthchecks ficarem saudáveis. Use sempre `docker compose` (v2, com espaço), não o antigo `docker-compose`.

### Make

O GNU make lê um `Makefile` com **alvos** (`build:`, `test:`...) e os comandos de cada um. Um alvo pode depender de outro (`run: db-up`), e o make roda a dependência antes. No Windows, o make não vem com o Git Bash.

### Três terminais no Windows

- **Git Bash / WSL:** shell POSIX (bash). Roda `./mvnw`, `make` e a sintaxe do README.
- **cmd:** o prompt clássico do Windows. Roda `mvnw.cmd`.
- **PowerShell:** shell moderno do Windows, com regras próprias de parsing de argumentos. Roda `.\mvnw.cmd`.

## No Link Pulse

### Pré-requisitos (README)

- **JDK 21** no `PATH`. O Maven não precisa estar instalado: o wrapper baixa a 3.9.16 (guia 07).
- **Docker Desktop ligado**, para o Postgres local e para os testes de integração.
- **Git Bash ou WSL** para os comandos POSIX.
- **GNU make**, opcional. O Git Bash não traz make. Formas de instalar, do README: `winget install ezwinports.make`, `scoop install make`, ou rodar de dentro do WSL com `sudo apt install make`.

### `compose.dev.yaml` comentado

`compose.dev.yaml`

```yaml
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
# ...
```

- **Só o Postgres.** Valkey, MongoDB, Prometheus e Grafana chegam nas Phases 2 e 3 (ver guia 10).
- **`127.0.0.1:5432:5432`:** a porta é publicada só no loopback. Sem o `127.0.0.1`, o Docker publicaria em todas as interfaces e qualquer máquina da rede (um Wi-Fi público, por exemplo) alcançaria um banco com senha conhecida.
- **Volume em `/var/lib/postgresql`:** a imagem do Postgres 18 guarda os dados em `/var/lib/postgresql/18/docker`; montar o diretório pai mantém os dados no volume `pgdata`.
- **Healthcheck `pg_isready`:** com `--wait`, o `docker compose up` só retorna quando o banco aceita conexões, então a API não sobe antes do banco.
- **Credenciais `linkpulse`/`linkpulse`:** só para desenvolvimento local. Produção recebe `SPRING_DATASOURCE_*` por variável de ambiente.

### O `Makefile`

`Makefile`

```makefile
MVNW := cd app && ./mvnw -B -ntp
COMPOSE := docker compose -f compose.dev.yaml
# ...
run: db-up ## Sobe o Postgres local e a API com o profile dev
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev
```

Alvos reais, listados com `grep -E '^[a-zA-Z_-]+:.*?## ' Makefile` (o mesmo filtro que o alvo `help` usa):

| Alvo | O que faz |
|------|-----------|
| `help` | Lista os alvos disponíveis (é o alvo padrão) |
| `build` | Empacota o jar sem rodar testes |
| `test` | Roda só os testes unitários (sem Docker) |
| `verify` | Checkstyle, testes unitários, testes de integração e SpotBugs |
| `run` | Sobe o Postgres local e a API com o profile dev (depende de `db-up`) |
| `db-up` | Sobe o Postgres 18 local e espera o healthcheck |
| `db-down` | Para o Postgres local (mantém o volume) |

A variável `MVNW` concentra `cd app && ./mvnw -B -ntp`, e `COMPOSE` concentra o `docker compose -f compose.dev.yaml`. Como `run` depende de `db-up`, `make run` sempre garante o banco antes da API.

> `make up` e `make k8s` ainda não existem: chegam nas Phases 3 e 4 (ver guia 10).

### Comandos por terminal

Todos a partir da raiz do repositório. O Git Bash usa o script POSIX `app/mvnw`; o cmd e o PowerShell usam `app/mvnw.cmd`. Os dois leem a mesma versão do Maven em `app/.mvn/wrapper/maven-wrapper.properties`.

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

- O PowerShell não executa arquivos da pasta atual pelo nome: é preciso `.\mvnw.cmd`.
- O argumento `-D...` vai entre aspas. Sem elas, o PowerShell pode quebrar `-Dspring-boot.run.profiles=dev` no ponto e entregar ao Maven dois argumentos, e o profile não é ativado.

Para rodar a suíte completa no PowerShell (igual ao README):

```powershell
cd app; .\mvnw.cmd -B -ntp verify
```

### Testando a API no PowerShell

No Windows PowerShell 5.1, `curl` é um apelido de `Invoke-WebRequest`, que tem outra sintaxe. Chame o curl de verdade pelo nome completo, `curl.exe`:

```powershell
curl.exe -i http://localhost:8080/demo-link
curl.exe http://localhost:8081/actuator/health
```

Para o `POST /links`, o jeito mais simples é o cmdlet nativo (o campo `url` vem de `CreateLinkRequest`):

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/links -ContentType 'application/json' -Body '{"url":"https://example.com/artigo"}'
```

No Git Bash, use os `curl` do guia 01.

### Parando

```bash
make db-down
# ou, sem make:
docker compose -f compose.dev.yaml down
```

Os dois param o container e **mantêm** o volume `pgdata`, com os links criados. Atenção: `docker compose -f compose.dev.yaml down -v` **apaga os dados** (remove o volume). Use só quando quiser recomeçar do zero.

### Problemas comuns

- **Porta 5432 ocupada:** outro Postgres (instalado no Windows ou de outro projeto) já usa a porta. Pare o outro serviço antes do `make db-up`.
- **Docker parado:** `docker compose` falha ao conectar no daemon e os ITs falham com `Could not find a valid Docker environment` (README). Abra o Docker Desktop e espere ele ficar pronto.
- **`mvnw` com CRLF:** aparece "bad interpreter" no Git Bash. O `.gitattributes` evita isso; se aconteceu, o arquivo foi salvo por um editor que trocou os fins de linha (guia 07).
- **Caminhos com espaço:** sempre entre aspas (`cd "...\projeto 4"`). O CI prova a cada push que o build funciona assim (guia 08).
- **`mvnw.cmd` "não é reconhecido" no cmd:** quando a variável de ambiente `NoDefaultCurrentDirectoryInExePath` está definida, o cmd deixa de procurar executáveis na pasta atual. `.\mvnw.cmd` funciona sempre. Isso foi observado ao escrever este guia: um `cmd.exe` aberto a partir do Git Bash com essa variável recusou `mvnw.cmd` e aceitou `.\mvnw.cmd`.

### Smoke dos comandos Windows (executado)

A partir da raiz do repositório, no Git Bash:

```bash
powershell.exe -NoProfile -Command "Set-Location app; .\mvnw.cmd -v"
MSYS_NO_PATHCONV=1 cmd.exe /d /c "cd app && .\mvnw.cmd -v"
```

Os dois mostraram `Apache Maven 3.9.16` na primeira linha.

## Por que assim?

- **Só o banco no compose de dev:** a API roda pelo Maven, com recarga rápida e depuração na IDE. A stack completa em container (`make up`) é tema da Phase 3.
- **Loopback e volume nomeado:** decisão 01-05 no `.planning/STATE.md`: "Postgres de dev publicado só em 127.0.0.1:5432 com volume em /var/lib/postgresql; Makefile ganha db-up/db-down e run depende de db-up".
- **Make opcional:** o README traz a coluna "Sem make" para cada alvo; ninguém fica bloqueado por não ter make no Windows.
- **Ponto de partida comum:** `application-dev.yml` (`app/src/main/resources/application-dev.yml`) aponta para exatamente o banco que o compose sobe.

## Experimente

1. Rode `make db-up` (ou o comando sem make) e depois `docker compose -f compose.dev.yaml ps`: o estado deve aparecer como saudável.
2. Suba a API no terminal que você mais usa e, em outro, chame `/demo-link` e `/actuator/health`.
3. No PowerShell, rode a API sem as aspas no `-D...` e observe se o seed `demo-link` existe (ele só é criado com o profile dev).
4. Pare com `make db-down`, suba de novo e confirme que o link criado antes continua lá.

## Perguntas para fixar

1. Por que a porta do Postgres é publicada em `127.0.0.1` e não em todas as interfaces?
2. O que a flag `--wait` faz no `docker compose up`, e qual configuração do compose ela usa?
3. Por que, no PowerShell, o argumento `-Dspring-boot.run.profiles=dev` vai entre aspas?
4. Qual a diferença entre `docker compose down` e `docker compose down -v`?
5. Por que `curl.exe` e não `curl` no Windows PowerShell 5.1?

---

[← Anterior: 08 · CI no GitHub Actions](08-ci-github-actions.md) · [Índice](README.md) · [Próximo: 10 · Próximas fases →](10-proximas-fases.md)
