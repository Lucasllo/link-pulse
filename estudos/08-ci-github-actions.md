# 08 · CI no GitHub Actions

**Em uma frase:** a cada push na `main` e a cada pull request, o workflow `ci` roda o mesmo `./mvnw -B -ntp verify` do seu computador numa máquina Linux do GitHub, com um token só de leitura, actions fixadas por SHA e um checkout proposital num caminho com espaço.

## O que você vai aprender

- O que é CI e por que ele existe.
- Como ler um workflow do GitHub Actions, chave por chave.
- O que é "pin por SHA" e por que ele protege o projeto.
- O que cada passo do `ci.yml` deste projeto faz.

## Antes de começar: conceitos

### CI (Integração Contínua)

**CI** (*Continuous Integration*) é a prática de compilar e testar o projeto **automaticamente** a cada mudança enviada ao repositório. Em vez de confiar que cada pessoa rodou os testes na própria máquina, um "inspetor" independente roda tudo do zero, num ambiente limpo, e marca a mudança com ✅ ou ❌.

Benefícios:

- Erros aparecem minutos depois do push, e não semanas depois.
- "Funciona na minha máquina" deixa de ser desculpa: o CI usa uma máquina limpa.
- Um pull request só é aceito com o ✅, então a `main` está sempre funcionando.

### GitHub Actions: as peças

O **GitHub Actions** é o serviço de CI do próprio GitHub. Um **workflow** é um arquivo YAML em `.github/workflows/`. As chaves principais:

| Chave | O que define |
|-------|--------------|
| `on` | Os eventos que disparam o workflow (`push`, `pull_request`, um horário...) |
| `permissions` | O que o token automático (`GITHUB_TOKEN`) pode fazer no repositório |
| `concurrency` | Se uma execução nova cancela a anterior |
| `jobs` | Trabalhos independentes; cada um roda numa máquina nova (`runs-on`), chamada **runner** |
| `steps` | Os passos de um job, em ordem |

Cada passo usa uma de duas formas:

- **`uses`**: executa uma **action** pronta, um "plugin" publicado por alguém (por exemplo, `actions/checkout`, que baixa o código);
- **`run`**: executa comandos de terminal.

Documentação oficial: <https://docs.github.com/actions>.

### Pin por SHA

Uma action é referenciada pelo repositório dela e por uma versão. Há dois jeitos:

- **Por tag:** `uses: actions/checkout@v7`. Uma **tag** é só um apelido, e quem controla o repositório da action pode mudá-lo para apontar para outro código a qualquer momento. Se o repositório da action for invadido, o atacante move a tag, e todo mundo que usa `@v7` passa a rodar o código dele.
- **Por SHA:** `uses: actions/checkout@3d3c42e5...`. O **SHA** é o hash do commit (40 caracteres), a "impressão digital" de um código exato. Ele **não pode** ser movido: aponta para sempre para o mesmo código.

O comentário `# v7.0.1` ao lado do SHA mantém a leitura fácil para humanos.

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

- **Quando roda:** em push na `main` e em qualquer pull request.
- **`permissions: contents: read`:** **menor privilégio**. O token só consegue ler o código. Mesmo que um passo fosse comprometido, ele não conseguiria publicar pacotes, criar releases nem alterar o repositório.
- **`concurrency`:** cria um grupo por branch ou PR (`${{ github.ref }}` é uma variável que o GitHub preenche). Se você fizer um push novo no mesmo PR enquanto o anterior ainda roda, o antigo é cancelado, e minutos de máquina não são gastos à toa.

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

- **`runs-on: ubuntu-latest`:** uma máquina Linux nova, criada só para esta execução e apagada no fim.
- **`timeout-minutes: 20`:** um teste travado é derrubado em 20 minutos, em vez de consumir as 6 horas do limite padrão.
- **`path: "link pulse"` + `working-directory: "link pulse/app"`:** o código é baixado numa pasta **com espaço** no nome. O autor desenvolve em `...\GSD\projeto 4` no Windows; se algum script esquecesse aspas em volta de um caminho, o CI quebraria aqui primeiro.
- **Checkout por SHA:** `3d3c42e5aac5ba805825da76410c181273ba90b1` (`# v7.0.1`).

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

| Passo | O que faz |
|-------|-----------|
| **Java 21 (Temurin)** | Instala o JDK 21. `cache: maven` guarda as bibliotecas baixadas (`~/.m2`) entre execuções; a chave do cache é derivada do `pom.xml`, então o cache é renovado quando as dependências mudam. |
| **mvnw é executável e LF** | `test -x mvnw` confere se o arquivo tem permissão de execução; `! grep -q $'\r' mvnw` falha se houver qualquer `\r` (CRLF) no arquivo. É a prova automática do `.gitattributes` (guia 07). |
| **Makefile parseia no Linux** | `make -n verify` mostra os comandos sem executá-los (*dry run*), e `make help` lista os alvos. Se o Makefile tivesse um erro de sintaxe (como espaços no lugar de TAB), o passo falharia. |
| **Build, Checkstyle, testes...** | O mesmo comando de `make verify`. A máquina `ubuntu-latest` já vem com Docker, então os ITs sobem Postgres, Valkey e MongoDB com o Testcontainers normalmente (guia 06). |

```yaml
      - name: Relatórios em caso de falha
        if: failure()
        uses: actions/upload-artifact@cf430e030ddbb5b0abf93d22962f4752f3646cd9 # v7.0.2
        with:
          name: reports
          path: |
            link pulse/app/target/surefire-reports
            link pulse/app/target/failsafe-reports
            link pulse/app/target/spotbugsXml.xml
            link pulse/app/target/checkstyle-result.xml
          if-no-files-found: ignore
```

- **`if: failure()`:** o passo só roda se algum passo anterior falhou. Num build verde, nada é enviado.
- Os relatórios ficam disponíveis para download como o **artefato** `reports` na página da execução. Dá para investigar a falha sem reproduzi-la na sua máquina.
- Repare no SHA com o comentário `# v7.0.2`: essa linha foi atualizada pelo Dependabot no PR #5 (guia 11).

### Dependabot

`.github/dependabot.yml` configura o robô que mantém as versões em dia: ele confere toda semana as actions (`github-actions`, na raiz) e as bibliotecas Java (`maven`, em `/app`) e abre PRs de atualização. Ele entende o pin por SHA: atualiza o hash e o comentário de versão juntos. Sem ele, fixar por SHA congelaria as actions para sempre, sem correções de segurança. A explicação completa está no guia 11.

> Publicação da imagem Docker no GHCR, scan com Trivy, SBOM e smoke test num cluster kind chegam na Phase 4 (guia 10).

## Por que assim?

- **Pin por SHA:** a seção "O Que NÃO Usar" do `.claude/CLAUDE.md` descarta "Actions de terceiros por tag mutável" e cita o incidente da `trivy-action` (março de 2026), um ataque de cadeia de suprimentos em que tags foram movidas para código malicioso. A decisão 01-01 no `.planning/STATE.md` registra que os SHAs foram conferidos com `git ls-remote`.
- **Token só de leitura:** o comentário do próprio `ci.yml` diz que "nenhum secret é usado nesta fase". Se não precisa de mais permissão, não pede.
- **Mesmo comando no seu computador e no CI:** se `make verify` passa na sua máquina, o CI roda exatamente o mesmo `./mvnw -B -ntp verify`, com o mesmo Maven (wrapper) e os mesmos bancos (Testcontainers).
- **Caminho com espaço:** o critério 5 da Phase 1 no `.planning/ROADMAP.md` exige que o build rode "mesmo com espaço no caminho".

## Experimente

1. Na aba **Actions** do repositório no GitHub, abra uma execução do workflow `ci` e encontre cada passo descrito acima.
2. Explique o que aconteceria se você trocasse o pin do checkout por `actions/checkout@v7`.
3. Num branch de teste, salve o `app/mvnw` com CRLF (sem passar pelo `.gitattributes`) e preveja qual passo falha.
4. Se você fizer três pushes seguidos no mesmo PR, quantas execuções terminam e quantas são canceladas?

## Perguntas para fixar

1. O que `permissions: contents: read` impede um passo comprometido de fazer?
2. Por que o checkout usa `path: "link pulse"`?
3. Por que um SHA de 40 caracteres é mais seguro que uma tag como `v7`?
4. Quando o passo de upload de relatórios roda?
5. Como os ITs conseguem subir Postgres, Valkey e MongoDB na máquina do CI?

<details>
<summary>Respostas</summary>

1. Escrever no repositório: publicar pacotes, criar releases, comentar, alterar código.
2. Para o código ficar numa pasta com espaço e provar, a cada push, que nenhum script quebra com espaço no caminho.
3. Porque a tag pode ser movida para outro código; o SHA aponta sempre para o mesmo commit.
4. Só quando algum passo anterior falhou (`if: failure()`).
5. A máquina `ubuntu-latest` já tem Docker, e o Testcontainers sobe os containers durante o `verify`.

</details>

---

[← Anterior: 07 · Build e qualidade](07-build-e-qualidade.md) · [Índice](README.md) · [Próximo: 09 · Ambiente local e Windows →](09-ambiente-local-e-windows.md)
