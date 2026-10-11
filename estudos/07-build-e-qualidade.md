# 07 · Build e qualidade

**Em uma frase:** o build usa um Maven 3.9.16 fixo, baixado e conferido por SHA-256 pelo wrapper, e quebra em qualquer violação de estilo (Checkstyle, fase `validate`) ou bug provável (SpotBugs, fase `verify`), com fins de linha controlados pelo `.gitattributes` para funcionar igual no Windows e no Linux.

## Conceitos

### Maven e o ciclo de vida

O Maven descreve o projeto num `pom.xml` e executa **fases** em ordem. As que importam aqui:

```text
validate → compile → test → package → integration-test → verify
```

Pedir uma fase roda todas as anteriores. Plugins se "penduram" em fases: `./mvnw verify` roda tudo da lista acima.

### Wrapper

O Maven Wrapper (`mvnw` / `mvnw.cmd`) é um script versionado no repositório que baixa uma versão exata do Maven na primeira execução e a reutiliza. Ninguém precisa instalar Maven, e todo mundo (inclusive o CI) usa a mesma versão.

### BOM e parent

Um **BOM** (*bill of materials*) é uma lista de versões testadas juntas. O `spring-boot-starter-parent` herda o BOM do Spring Boot e também configura plugins comuns. Por isso as dependências do projeto quase nunca declaram `<version>`.

### Análise estática

- **Checkstyle** verifica estilo: indentação, nomes, imports, Javadoc, tamanho de linha.
- **SpotBugs** analisa o bytecode compilado atrás de padrões de bug: null pointer provável, recurso não fechado, representação interna exposta, e assim por diante.

## No Link Pulse

### O wrapper e o SHA-256

`app/.mvn/wrapper/maven-wrapper.properties`

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
distributionSha256Sum=5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce
```

- `distributionType=only-script`: não há `maven-wrapper.jar` no repositório; os próprios scripts `app/mvnw` e `app/mvnw.cmd` fazem o download.
- `distributionSha256Sum`: o wrapper confere o hash do zip baixado e recusa um arquivo adulterado. É uma defesa de cadeia de suprimentos (*supply chain*): mesmo que o download fosse interceptado, o Maven alterado não rodaria.
- O README diz: "o wrapper baixa a 3.9.16 e confere o SHA-256". O mesmo Maven roda no seu PC e no CI.

### `.gitattributes`

`.gitattributes`

```text
* text=auto eol=lf
mvnw text eol=lf
*.cmd text eol=crlf
*.bat text eol=crlf
*.jar binary
*.png binary
```

No Windows, o Git pode converter fins de linha para CRLF no checkout. Um script shell com CRLF quebra no Linux/Git Bash com "bad interpreter" (o `\r` vira parte do nome do interpretador). As regras forçam `mvnw` em LF, enquanto `*.cmd` fica em CRLF, que é o que o `cmd.exe` espera. O CI confere o `mvnw` a cada push (guia 08).

### Anatomia do `app/pom.xml`

1. **`<parent>`**: `spring-boot-starter-parent` 4.1.1 (BOM + configuração de plugins).
2. **`<properties>`**: `java.version` 21, `springdoc.version` 3.1.1 e `checkstyle.version` 14.3.0.
3. **`<dependencies>`**: starters sem versão (vem do BOM); só o springdoc usa `${springdoc.version}`, porque não faz parte do BOM do Boot.
4. **`<build><plugins>`**: `spring-boot-maven-plugin` (gera o jar executável no `package`), `maven-failsafe-plugin` (ITs), `maven-checkstyle-plugin` e `spotbugs-maven-plugin`.

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

- O plugin 3.6.0 traz por padrão o Checkstyle 9.3, que, segundo o comentário do pom, "não entende Java 21". Por isso a dependência do plugin é sobrescrita para o engine 14.3.0.
- Roda na fase `validate`: código fora do padrão quebra o build antes de compilar.
- `failOnViolation` + `violationSeverity` `warning` são uma "defesa": falha "mesmo que alguém volte a severity do checkstyle.xml para warning". `includeTestSourceDirectory` aplica as regras também aos testes.
- `app/config/checkstyle/checkstyle.xml` é a configuração do Google Java Style (o cabeçalho cita o guia de estilo do Google), com ajustes marcados "Link Pulse (D-15)": `severity` fixa em `error`, indentação de 4 espaços (8 em continuação) no lugar dos 2 do Google, e Javadoc cobrado por tipo (`MissingJavadocType`) em vez de por método.

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

`effort` Max faz a análise mais profunda; `threshold` Medium quebra o build com achados de prioridade média ou alta. O goal `check` roda na fase `verify`, depois dos ITs.

As exclusões ficam em `app/config/spotbugs/exclude.xml`, que abre com a regra: "toda exclusão precisa de um comentário com a justificativa. Nada de excluir por pacote inteiro." Hoje são duas:

| Padrão | Justificativa (comentário do arquivo, resumida) |
|--------|-------------------------------------------------|
| `EI_EXPOSE_REP` | Getters de records e de beans Spring devolvem a referência guardada (um bean injetado, uma lista imutável). É intencional: singletons do container e records portadores de dados. |
| `EI_EXPOSE_REP2` | Construtores com injeção de dependência guardam a referência recebida do container. Copiar esses objetos não faz sentido; injeção por construtor é o padrão do projeto. |

O comentário do `EI_EXPOSE_REP2` já cita `StringRedisTemplate` e `MeterRegistry`, que só entram na Phase 2 (ver guia 10); hoje, o caso real são os repositórios e políticas injetados no `LinkService`.

### Comandos e o que cada um roda

| Com make | Sem make (de `app/`) | Fases / gates |
|----------|----------------------|---------------|
| `make build` | `./mvnw -B -ntp -DskipTests package` | Checkstyle (`validate`), compilação, jar. Sem testes. |
| `make test` | `./mvnw -B -ntp test` | Checkstyle + unitários (Surefire). Sem Docker. |
| `make verify` | `./mvnw -B -ntp verify` | Checkstyle + unitários + jar + ITs com Testcontainers (Failsafe) + SpotBugs. Precisa de Docker. |

`-B` é *batch mode* (sem cores nem prompts, bom para logs) e `-ntp` (*no transfer progress*) esconde o progresso de cada download.

### Relatórios

Depois de um build, dentro de `app/`:

- `target/checkstyle-result.xml`: violações de estilo;
- `target/spotbugsXml.xml`: achados do SpotBugs;
- `target/surefire-reports` e `target/failsafe-reports`: resultados dos testes (guia 06).

## Por que assim?

- **Versão do Maven fixa e conferida:** a pesquisa de stack no `.claude/CLAUDE.md` recomenda fixar a 3.9.16 no wrapper (a 3.10.0 era recente demais e a 4.0 ainda estava em RC).
- **Gates desde o primeiro commit:** a decisão 01-01 no `.planning/STATE.md` registra Checkstyle 14.3.0 (Google adaptado, `severity error`) em `validate` e SpotBugs 4.10.4.1 Max/Medium em `verify`.
- **Exclusões justificadas, por padrão de bug:** excluir por pacote esconderia bugs reais; excluir por padrão, com justificativa escrita, deixa o motivo revisável.
- **Fins de linha explícitos:** o autor trabalha no Windows ("projeto 4", com espaço no caminho) e o CI roda em Linux; sem `.gitattributes`, o mesmo repositório se comportaria diferente nos dois.

## Experimente

1. Rode `make build` e depois `ls app/target` (Git Bash): onde está o jar?
2. Troque temporariamente um import de `LinkService` por um import com `*` (por exemplo, `import java.time.*;`), rode `make test` e leia a violação da regra `AvoidStarImport`. Desfaça.
3. No Git Bash, rode `file app/mvnw app/mvnw.cmd` e confira os fins de linha. Executado durante a escrita deste guia: o `mvnw` aparece como "POSIX shell script, ASCII text executable" e o `mvnw.cmd` como "ASCII text, with CRLF line terminators".
4. Abra `app/config/checkstyle/checkstyle.xml` e encontre todos os comentários "Link Pulse".

## Perguntas para fixar

1. O que o `distributionSha256Sum` protege, e o que aconteceria se o zip baixado tivesse outro hash?
2. Por que o engine do Checkstyle é sobrescrito para 14.3.0?
3. Em que fase roda o Checkstyle e em que fase roda o SpotBugs? Por que o SpotBugs não pode rodar em `validate`?
4. Por que `mvnw` precisa estar em LF e `mvnw.cmd` em CRLF?
5. Qual a regra para adicionar uma exclusão no `exclude.xml`?

---

[← Anterior: 06 · Testes](06-testes.md) · [Índice](README.md) · [Próximo: 08 · CI no GitHub Actions →](08-ci-github-actions.md)
