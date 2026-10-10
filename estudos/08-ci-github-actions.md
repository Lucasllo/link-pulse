# 08 · CI no GitHub Actions

**Em uma frase:** a cada push na `main` e a cada pull request, o workflow `ci` roda o mesmo `./mvnw -B -ntp verify` do seu computador num runner Linux, com token só de leitura, actions fixadas por SHA e um checkout proposital num caminho com espaço.

## Conceitos

### Anatomia de um workflow

Um workflow é um arquivo YAML em `.github/workflows/`. As chaves principais:

- **`on`**: os eventos que disparam o workflow (`push`, `pull_request`, agendamento...).
- **`permissions`**: o que o `GITHUB_TOKEN` automático pode fazer no repositório.
- **`concurrency`**: agrupa execuções e decide se uma nova cancela a anterior.
- **`jobs`**: trabalhos independentes; cada um roda numa máquina nova (`runs-on`).
- **`steps`**: passos de um job, em ordem. Um passo usa **`uses`** (executa uma action pronta, como `actions/checkout`) ou **`run`** (executa comandos de shell).

Documentação oficial: <https://docs.github.com/actions>.

### Pin por SHA

`uses: actions/checkout@v7` aponta para uma **tag**, e tags são móveis: quem controla o repositório da action pode apontá-la para outro código. `uses: actions/checkout@<sha de 40 caracteres>` aponta para um commit exato, que não muda. O comentário `# v7.0.1` ao lado mantém a leitura humana.

### Dependabot

O Dependabot abre pull requests automáticos quando sai uma versão nova de uma dependência. Ele entende pins por SHA: atualiza o hash e o comentário de versão juntos.

## No Link Pulse

### O `ci.yml` passo a passo

`.github/workflows/ci.yml`

```yaml
name: ci

on:
  push:
    branches: [main]
  pull_request:

# O token do workflow só lê o repositório; nenhum secret é usado nesta fase.
permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true
```

- **Gatilhos:** push na `main` e qualquer pull request.
- **`permissions: contents: read`:** menor privilégio. O token só consegue ler o código; mesmo que um passo fosse comprometido, não poderia criar releases, comentar ou publicar pacotes.
- **`concurrency`:** um grupo por ref (`ci-refs/heads/main`, `ci-refs/pull/N/merge`). Um push novo no mesmo PR cancela a execução antiga, economizando minutos de runner.

```yaml
jobs:
  build-test:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    defaults:
      run:
        # Checkout num caminho com espaço: prova a cada push que o build aguenta "projeto 4" no Windows.
        working-directory: "link pulse/app"
    steps:
      - name: Checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          path: "link pulse"
```

- **`timeout-minutes: 20`:** um teste travado não consome as 6 horas padrão do runner.
- **`path: "link pulse"` + `working-directory: "link pulse/app"`:** o repositório é clonado numa pasta com espaço. O autor desenvolve em `...\GSD\projeto 4` no Windows; se algum script não colocasse aspas num caminho, o CI quebraria aqui primeiro.
- **Pin por SHA:** o checkout está fixado em `3d3c42e5aac5ba805825da76410c181273ba90b1` (`# v7.0.1`). O `setup-java` e o `upload-artifact` seguem o mesmo padrão.

```yaml
      - name: Java 21 (Temurin)
        uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
          cache-dependency-path: "link pulse/app/pom.xml"

      - name: mvnw é executável e LF
        run: test -x mvnw && ! grep -q $'\r' mvnw

      - name: Makefile parseia no Linux
        working-directory: "link pulse"
        run: make -n verify && make help

      - name: Build, Checkstyle, testes unitários + Testcontainers, SpotBugs
        run: ./mvnw -B -ntp verify
```

- **`setup-java` com `cache: maven`:** instala o JDK 21 Temurin e guarda o `~/.m2` entre execuções, com a chave derivada do `pom.xml` (por isso o `cache-dependency-path` aponta para o pom dentro da pasta com espaço).
- **"mvnw é executável e LF":** `test -x` confere o bit de execução e `! grep -q $'\r'` falha se houver qualquer CR no arquivo. É a prova automática do `.gitattributes` (guia 07).
- **"Makefile parseia no Linux":** `make -n verify` imprime os comandos sem executá-los (*dry run*) e `make help` lista os alvos. Se o Makefile tivesse erro de sintaxe (por exemplo, espaços no lugar de TAB), o passo falharia.
- **`./mvnw -B -ntp verify`:** o mesmo comando de `make verify`. O runner `ubuntu-latest` já tem Docker, então os ITs com Testcontainers rodam normalmente. `-B` deixa o log sem cores e prompts; `-ntp` esconde o progresso dos downloads.

```yaml
      - name: Relatórios em caso de falha
        if: failure()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: reports
          path: |
            link pulse/app/target/surefire-reports
            link pulse/app/target/failsafe-reports
            link pulse/app/target/spotbugsXml.xml
            link pulse/app/target/checkstyle-result.xml
          if-no-files-found: ignore
```

- **`if: failure()`:** o passo só roda se algum anterior falhou. Num build verde, nada é enviado.
- Os relatórios ficam disponíveis como artefato `reports` na página da execução, para investigar sem reproduzir localmente.

### Dependabot

`.github/dependabot.yml`

```yaml
version: 2
updates:
  # Mantém os pins por SHA das actions atualizados.
  - package-ecosystem: github-actions
    directory: "/"
    schedule:
      interval: weekly
    open-pull-requests-limit: 5

  - package-ecosystem: maven
    directory: "/app"
    schedule:
      interval: weekly
    open-pull-requests-limit: 5
```

- `github-actions` olha os workflows a partir da raiz (`/`) e atualiza os SHAs.
- `maven` olha o `app/pom.xml` (diretório `/app`), incluindo o parent do Spring Boot e os plugins.
- Frequência semanal e no máximo 5 PRs abertos por ecossistema, para não inundar o repositório.

Pin por SHA sem Dependabot congelaria as actions para sempre (sem correções de segurança). Com ele, cada atualização vira um PR revisável, que passa pelo próprio CI.

> Publicação da imagem no GHCR, scan com Trivy, SBOM e smoke test em kind chegam na Phase 4 (ver guia 10).

## Por que assim?

- **Pin por SHA:** a seção "O Que NÃO Usar" do `.claude/CLAUDE.md` descarta "Actions de terceiros por tag mutável" e cita o incidente da `trivy-action` (março de 2026), um comprometimento de cadeia de suprimentos. Quem referencia uma action por tag recebe o que a tag apontar no dia; quem referencia por SHA recebe sempre o mesmo código. A decisão 01-01 no `.planning/STATE.md` registra que os SHAs foram conferidos com `git ls-remote`.
- **Token só de leitura:** o comentário do próprio `ci.yml` diz que "nenhum secret é usado nesta fase".
- **Mesmo comando local e no CI:** se `make verify` passa no seu computador, o CI roda exatamente o mesmo `./mvnw -B -ntp verify`, com o mesmo Maven (wrapper) e o mesmo Postgres (Testcontainers).
- **Caminho com espaço:** o critério 5 da Phase 1 no `.planning/ROADMAP.md` exige que o build rode "mesmo com espaço no caminho".

## Experimente

1. Na aba **Actions** do repositório no GitHub, abra uma execução do workflow `ci` e localize cada passo descrito acima.
2. Explique o que aconteceria se você trocasse o pin do checkout por `actions/checkout@v7`.
3. Em um branch de teste, salve o `app/mvnw` com CRLF (sem passar pelo `.gitattributes`) e preveja qual passo falha.
4. Calcule: se você fizer três pushes seguidos no mesmo PR, quantas execuções terminam e quantas são canceladas?

## Perguntas para fixar

1. O que `permissions: contents: read` impede um passo comprometido de fazer?
2. Por que o checkout usa `path: "link pulse"`?
3. Por que um SHA de 40 caracteres é mais seguro que uma tag como `v7`?
4. Quando o passo de upload de relatórios roda?
5. O que o Dependabot atualiza nos dois ecossistemas configurados?

---

[← Anterior: 07 · Build e qualidade](07-build-e-qualidade.md) · [Índice](README.md) · [Próximo: 09 · Ambiente local e Windows →](09-ambiente-local-e-windows.md)
