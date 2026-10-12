# 07 · Build e qualidade

**Em uma frase:** o build usa um Maven 3.9.16 fixo, baixado e conferido por SHA-256 pelo wrapper, e quebra com qualquer violação de estilo (Checkstyle, fase `validate`) ou bug provável (SpotBugs, fase `verify`). Os fins de linha são controlados pelo `.gitattributes`, para tudo funcionar igual no Windows e no Linux.

## O que você vai aprender

- O que é "build" e o que o Maven faz.
- O que é o ciclo de vida do Maven e o que cada comando `make` roda.
- Por que fixar e conferir a versão do Maven (wrapper + SHA-256).
- O que o Checkstyle e o SpotBugs verificam.
- Por que o fim de linha de um arquivo pode quebrar um script.

## Antes de começar: conceitos

### Build

**Build** é o processo de transformar o código-fonte num programa que roda: baixar as bibliotecas, compilar o Java (transformar `.java` em `.class`, o *bytecode* que a JVM executa), rodar os testes e empacotar tudo num arquivo `.jar`. Fazer isso à mão seria uma lista enorme de comandos; uma **ferramenta de build** faz sozinha.

### Maven e o ciclo de vida

O **Maven** é a ferramenta de build deste projeto. Ele lê o `pom.xml` (*Project Object Model*), onde estão as dependências e os plugins, e executa **fases** sempre na mesma ordem:

```text
validate → compile → test → package → integration-test → verify
```

| Fase | O que acontece aqui |
|------|---------------------|
| `validate` | Confere o projeto. Aqui roda o Checkstyle |
| `compile` | Compila o código principal |
| `test` | Roda os testes unitários (Surefire) |
| `package` | Gera o `.jar` |
| `integration-test` | Roda os ITs (Failsafe) |
| `verify` | Confere o resultado dos ITs e roda o SpotBugs |

**Pedir uma fase roda todas as anteriores.** `./mvnw verify` faz tudo da tabela. Os **plugins** se "penduram" nas fases: cada um faz seu trabalho quando a fase dele chega.

### Wrapper

O **Maven Wrapper** (`mvnw` para Git Bash/Linux, `mvnw.cmd` para Windows) é um script guardado no repositório que baixa uma versão **exata** do Maven na primeira execução e a reaproveita depois. Ninguém precisa instalar o Maven, e todo mundo (você, outra pessoa, o CI) usa a mesma versão.

### BOM e parent

Um **BOM** (*bill of materials*) é uma lista de versões testadas juntas. O `spring-boot-starter-parent` traz o BOM do Spring Boot e também configura os plugins mais comuns. Por isso as dependências do projeto quase nunca declaram `<version>`: quem decide é o BOM, e as versões nunca brigam entre si.

### Análise estática

"Estática" quer dizer **sem rodar o programa**: a ferramenta só lê o código.

- **Checkstyle** confere o **estilo**: indentação, nomes, imports, Javadoc, tamanho de linha. Código com estilo uniforme é mais fácil de ler e revisar.
- **SpotBugs** analisa o código compilado atrás de **padrões de bug**: um `null` que vai estourar, um recurso que nunca é fechado, uma representação interna exposta...

## No Link Pulse

### O wrapper e o SHA-256

`app/.mvn/wrapper/maven-wrapper.properties`

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce
```

- **`distributionUrl`**: de onde baixar o Maven 3.9.16.
- **`distributionType=only-script`**: não há um `maven-wrapper.jar` no repositório; os próprios scripts `app/mvnw` e `app/mvnw.cmd` fazem o download.
- **`distributionSha256Sum`**: a "impressão digital" do arquivo correto. Um **hash SHA-256** é um número calculado a partir do conteúdo do arquivo: se um único byte mudar, o hash muda completamente. O wrapper calcula o hash do zip baixado e o compara com este. Se não bater, ele se recusa a usar o arquivo.

Por que isso importa? É uma defesa de **cadeia de suprimentos** (*supply chain*): mesmo que alguém interceptasse o download e trocasse o Maven por uma versão adulterada, ela não rodaria. O README resume: "o wrapper baixa a 3.9.16 e confere o SHA-256".

A versão está fixada na 3.9.16 de propósito: a 3.10.0 saiu em 2026-10-01 e ainda é nova demais. O Dependabot foi configurado para não oferecê-la por enquanto (guia 11).

### `.gitattributes` e o fim de linha

Cada sistema operacional marca o fim de uma linha de texto de um jeito:

- Linux e macOS usam um caractere: **LF** (`\n`).
- Windows usa dois: **CRLF** (`\r\n`).

O Git pode converter um no outro sem avisar. O problema: um script shell com CRLF quebra no Linux/Git Bash com o erro "bad interpreter", porque o `\r` invisível vira parte do nome do programa (`/bin/sh\r`).

`.gitattributes`

```text
* text=auto eol=lf
mvnw text eol=lf
*.cmd text eol=crlf
*.bat text eol=crlf
*.jar binary
*.png binary
```

| Regra | Efeito |
|-------|--------|
| `* text=auto eol=lf` | Arquivos de texto ficam em LF |
| `mvnw text eol=lf` | O script do Git Bash/Linux fica sempre em LF |
| `*.cmd text eol=crlf` | Scripts do Windows ficam em CRLF, que é o que o `cmd.exe` espera |
| `*.jar binary`, `*.png binary` | Arquivos binários nunca são convertidos |

O CI confere o `mvnw` a cada push (guia 08).

### Anatomia do `app/pom.xml`

1. **`<parent>`**: `spring-boot-starter-parent` 4.1.1 (BOM + configuração de plugins).
2. **`<properties>`**: `java.version` 21, `springdoc.version` 3.1.1 e `checkstyle.version` 14.3.0.
3. **`<dependencies>`**: os starters, sem versão (vem do BOM). Só o springdoc usa `${springdoc.version}`, porque não faz parte do BOM do Boot. A lista completa, com o que cada uma faz, está no guia 02.
4. **Dependências de teste**: `spring-boot-starter-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `testcontainers-postgresql` e `testcontainers-mongodb` (guia 06). O Valkey não precisa de módulo próprio.
5. **`<build><plugins>`**: `spring-boot-maven-plugin` (gera o jar executável no `package`), `maven-failsafe-plugin` (ITs), `maven-checkstyle-plugin` e `spotbugs-maven-plugin`.

### Checkstyle

`app/pom.xml`

```xml
            <!-- Estilo: Google adaptado (4 espaços, severity error). Roda em validate e quebra o build. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.6.0</version>
                <dependencies>
                    <dependency>
                        <groupId>com.puppycrawl.tools</groupId>
                        <artifactId>checkstyle</artifactId>
                        <version>${checkstyle.version}</version>
                    </dependency>
                </dependencies>
                <configuration>
                    <configLocation>config/checkstyle/checkstyle.xml</configLocation>
                    <consoleOutput>true</consoleOutput>
                    <failOnViolation>true</failOnViolation>
                    <!-- ... -->
```

Leitura guiada:

- **O plugin e o "motor" são coisas diferentes.** O plugin 3.6.0 traz por padrão o Checkstyle 9.3, que, segundo o comentário do pom, "não entende Java 21" (records, *pattern matching*). Por isso o bloco `<dependencies>` **dentro do plugin** troca o motor pelo 14.3.0.
- **Roda na fase `validate`**: código fora do padrão quebra o build antes mesmo de compilar.
- **`failOnViolation`** + `violationSeverity` `warning` são uma "defesa": o build falha "mesmo que alguém volte a severity do checkstyle.xml para warning".
- **`includeTestSourceDirectory`** aplica as regras também aos testes.
- `app/config/checkstyle/checkstyle.xml` é a configuração do Google Java Style, com ajustes marcados "Link Pulse (D-15)": severidade fixa em `error`, indentação de 4 espaços (8 em continuação) em vez dos 2 do Google, e Javadoc cobrado por classe (`MissingJavadocType`) em vez de por método.

### SpotBugs

`app/pom.xml`

```xml
            <!-- Bugs: SpotBugs com esforço máximo; achados de prioridade Medium ou maior quebram o verify. -->
            <plugin>
                <groupId>com.github.spotbugs</groupId>
                <artifactId>spotbugs-maven-plugin</artifactId>
                <version>4.10.4.1</version>
                <configuration>
                    <effort>Max</effort>
                    <threshold>Medium</threshold>
                    <excludeFilterFile>config/spotbugs/exclude.xml</excludeFilterFile>
                </configuration>
```

- **`effort` Max**: a análise mais profunda (mais lenta, mais completa).
- **`threshold` Medium**: achados de prioridade média ou alta quebram o build.
- Roda na fase `verify`, depois dos testes. Ele precisa do código **compilado**, então não poderia rodar em `validate`.

As exceções ficam em `app/config/spotbugs/exclude.xml`, que abre com a regra: "toda exclusão precisa de um comentário com a justificativa. Nada de excluir por pacote inteiro." Hoje são duas, e as duas tratam do mesmo assunto, **"representação interna exposta"**: o SpotBugs avisa quando um objeto guarda ou devolve a referência de outro objeto que alguém de fora poderia alterar.

| Padrão | Justificativa (comentário do arquivo, resumida) |
|--------|-------------------------------------------------|
| `EI_EXPOSE_REP` | Getters de records e de beans Spring devolvem a referência guardada (um bean injetado, uma lista imutável). É intencional: singletons do container e records portadores de dados. |
| `EI_EXPOSE_REP2` | Construtores com injeção de dependência guardam a referência recebida do container (`StringRedisTemplate`, `MeterRegistry`, repositórios). Copiar esses objetos não faz sentido; injeção por construtor é o padrão do projeto. |

Exemplo concreto: o construtor do `LinkCache` recebe e guarda um `StringRedisTemplate` e um `MeterRegistry`. O SpotBugs veria isso como "guardou a referência de um objeto de fora", mas é exatamente o que a injeção de dependência faz (guia 02).

### Comandos e o que cada um roda

| Com make | Sem make (de `app/`) | Fases / verificações |
|----------|----------------------|----------------------|
| `make build` | `./mvnw -B -ntp -DskipTests package` | Checkstyle (`validate`), compilação, jar. Sem testes. |
| `make test` | `./mvnw -B -ntp test` | Checkstyle + unitários (Surefire). Sem Docker. |
| `make verify` | `./mvnw -B -ntp verify` | Checkstyle + unitários + jar + ITs com Testcontainers (Failsafe) + SpotBugs. Precisa de Docker. |

- **`-B`** (*batch mode*): sem cores nem perguntas interativas, bom para logs.
- **`-ntp`** (*no transfer progress*): esconde a barra de progresso de cada download.
- **`-DskipTests`**: pula os testes.

### Relatórios

Depois de um build, dentro de `app/`:

- `target/checkstyle-result.xml`: violações de estilo;
- `target/spotbugsXml.xml`: achados do SpotBugs;
- `target/surefire-reports` e `target/failsafe-reports`: resultados dos testes (guia 06).

A pasta `target` é criada pelo build e não vai para o Git.

## Por que assim?

- **Versão do Maven fixa e conferida:** a pesquisa de stack no `.claude/CLAUDE.md` recomenda fixar a 3.9.16 no wrapper (a 3.10.0 era recente demais e a 4.0 ainda estava em RC, "release candidate").
- **Verificações desde o primeiro commit:** a decisão 01-01 no `.planning/STATE.md` registra Checkstyle 14.3.0 (Google adaptado, severidade `error`) em `validate` e SpotBugs 4.10.4.1 Max/Medium em `verify`. Corrigir estilo e bugs aos poucos, desde o começo, é muito mais barato que no fim.
- **Exclusões justificadas, por padrão de bug:** excluir por pacote esconderia bugs de verdade; excluir por padrão, com o motivo escrito, deixa a decisão revisável.
- **Fins de linha explícitos:** o autor trabalha no Windows (com espaço no caminho: "projeto 4") e o CI roda em Linux. Sem `.gitattributes`, o mesmo repositório se comportaria diferente nos dois.

## Experimente

1. Rode `make build` e depois `ls app/target` (Git Bash): onde está o jar?
2. Troque temporariamente um import do `LinkService` por um com `*` (por exemplo, `import java.time.*;`), rode `make test` e leia a violação da regra `AvoidStarImport`. Desfaça.
3. No Git Bash, rode `file app/mvnw app/mvnw.cmd` e confira os fins de linha. Executado durante a escrita deste guia: o `mvnw` aparece como "POSIX shell script, ASCII text executable" e o `mvnw.cmd` como "ASCII text, with CRLF line terminators".
4. Abra `app/config/checkstyle/checkstyle.xml` e encontre todos os comentários "Link Pulse".

## Perguntas para fixar

1. O que o `distributionSha256Sum` protege, e o que aconteceria se o zip baixado tivesse outro hash?
2. Por que o motor do Checkstyle é trocado pelo 14.3.0?
3. Em que fase roda o Checkstyle e em que fase roda o SpotBugs? Por que o SpotBugs não pode rodar em `validate`?
4. Por que `mvnw` precisa estar em LF e `mvnw.cmd` em CRLF?
5. Qual a regra para adicionar uma exclusão no `exclude.xml`?

<details>
<summary>Respostas</summary>

1. Protege contra um Maven adulterado. Com outro hash, o wrapper recusa o arquivo e o build não roda.
2. Porque o 9.3, que vem com o plugin, não entende a sintaxe do Java 21.
3. Checkstyle em `validate`; SpotBugs em `verify`. O SpotBugs analisa o código compilado, que só existe depois do `compile`.
4. O `mvnw` roda no Git Bash/Linux, que não aceita o `\r` do CRLF ("bad interpreter"); o `mvnw.cmd` roda no `cmd.exe` do Windows, que espera CRLF.
5. Toda exclusão precisa de um comentário com a justificativa, e nunca pode excluir um pacote inteiro.

</details>

---

[← Anterior: 06 · Testes](06-testes.md) · [Índice](README.md) · [Próximo: 08 · CI no GitHub Actions →](08-ci-github-actions.md)
