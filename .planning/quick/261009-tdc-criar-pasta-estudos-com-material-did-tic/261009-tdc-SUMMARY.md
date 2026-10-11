---
phase: quick-261009-tdc
plan: 01
subsystem: docs
status: complete
tags: [docs, estudos, pt-BR, didatico]
requires: []
provides:
  - "estudos/README.md (índice com 10 guias, trilhas, convenções e glossário)"
  - "estudos/01..10 (guias didáticos ancorados no código real da Phase 1)"
affects: []
tech-stack:
  added: []
  patterns:
    - "Guias com estrutura fixa: Em uma frase / Conceitos / No Link Pulse / Por que assim? / Experimente / Perguntas para fixar / rodapé de navegação"
    - "Caminhos em crase relativos à raiz, links relativos a estudos/, trechos copiados literalmente"
key-files:
  created:
    - estudos/README.md
    - estudos/01-estrutura-e-fluxo-de-requisicoes.md
    - estudos/02-spring-boot-e-java-21.md
    - estudos/03-postgres-jpa-e-liquibase.md
    - estudos/04-codigo-curto-base62.md
    - estudos/05-validacao-e-erros.md
    - estudos/06-testes.md
    - estudos/07-build-e-qualidade.md
    - estudos/08-ci-github-actions.md
    - estudos/09-ambiente-local-e-windows.md
    - estudos/10-proximas-fases.md
  modified: []
decisions:
  - "Guia 09 recomenda .\\mvnw.cmd também como saída no cmd: com NoDefaultCurrentDirectoryInExePath definido, o cmd recusa mvnw.cmd sem .\\ (observado no smoke)"
metrics:
  duration: "11min"
  completed: 2026-10-10
  tasks: 3
  files: 11
plan_head_before: 182bcc9e4345c9a21fcbd4543bc85b3664db1001
actuals:
  tokens: 27800
  tasks: 3
  commits: 3
---

# Quick 261009-tdc Plan 01: Pasta estudos/ com material didático Summary

Pasta `estudos/` com índice e 10 guias em pt-BR (cerca de 2000 linhas) que explicam a estrutura e as tecnologias do Link Pulse no estado da Phase 1. Os guias citam só caminhos, classes, métodos, propriedades e alvos que existem no HEAD, e os gates automáticos de caminho e de link passaram.

## Tarefas

| # | Tarefa | Commit | Arquivos |
|---|--------|--------|----------|
| 1 | Índice + guia 01 (estrutura e fluxo, 2 `sequenceDiagram`) | 040c96f | `estudos/README.md`, `estudos/01-estrutura-e-fluxo-de-requisicoes.md` |
| 2 | Guias 02–05 (Boot/Java 21, Postgres/JPA/Liquibase, Base62, validação/erros) | 92aef46 | `estudos/02-*.md` a `estudos/05-*.md` |
| 3 | Guias 06–10 (testes, build, CI, ambiente local/Windows, próximas fases) + verificação final | 4d759c6 | `estudos/06-*.md` a `estudos/10-*.md` |

## Verificação

- O `<verify>` da Tarefa 1 passou (tracer gate: re-executado e verde antes de expandir).
- O `<verify>` da Tarefa 2 passou.
- O `<verify>` da Tarefa 3 (conjunto inteiro) passou: 11 arquivos, palavras-chave, heurística de pt-BR, nenhum caminho MISSING, nenhum link BROKEN, pelo menos 3 citações reais por guia de 01 a 09, nada alterado fora de `estudos/` desde 182bcc9.
- Todos os arquivos estão em UTF-8 e LF (0 CR).

## Execuções registradas (pedido do `<output>`)

- **Listagem ao vivo × planejamento:** `git ls-files app .github Makefile compose.dev.yaml .gitattributes` bateu exatamente com a listagem do plano. Sem divergências.
- **`./mvnw -B -ntp -Dtest=CodeGeneratorTest test` (a partir de `app/`, Git Bash):** BUILD SUCCESS, `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`. O comando foi mantido nos guias 04 e 06, com o resultado real.
- **Smoke PowerShell** `powershell.exe -NoProfile -Command "Set-Location app; .\mvnw.cmd -v"`: `Apache Maven 3.9.16`.
- **Smoke cmd** `MSYS_NO_PATHCONV=1 cmd.exe /d /c "cd app && mvnw.cmd -v"`: **falhou** com "'mvnw.cmd' não é reconhecido como um comando interno ou externo". A causa é a variável `NoDefaultCurrentDirectoryInExePath=1`, herdada do ambiente do Git Bash/harness. Com `.\mvnw.cmd`, ou sem essa variável (`env -u NoDefaultCurrentDirectoryInExePath`), o mesmo comando mostrou `Apache Maven 3.9.16`. O guia 09 mantém `mvnw.cmd` como comando principal do cmd, documenta o caso em "Problemas comuns" e usa `.\mvnw.cmd` no bloco de smoke.
- **`file app/mvnw app/mvnw.cmd`:** "POSIX shell script, ASCII text executable" e "ASCII text, with CRLF line terminators". Citado no guia 07.

## Deviations from Plan

### Ajustes de conteúdo para não inventar

**1. [Rule 1 - Bug] Exercício de Checkstyle usava uma regra inexistente**
- **Found during:** Tarefa 3 (guia 07)
- **Issue:** o rascunho sugeria provocar uma violação com um import não usado, mas `app/config/checkstyle/checkstyle.xml` não tem `UnusedImports`.
- **Fix:** o exercício passou a usar um import com `*`, que é barrado por `AvoidStarImport` (confirmado por grep).
- **Commit:** 4d759c6

**2. [Rule 1 - Bug] Descrição da regra do sufixo IT no Checkstyle**
- **Found during:** Tarefa 3 (guia 06)
- **Issue:** o rascunho dizia que o Checkstyle "confere" o sufixo `IT`. Na verdade, a regra `AbbreviationAsWordInName` só **libera** a sigla `IT` (`allowedAbbreviations`).
- **Fix:** o texto foi corrigido para refletir o arquivo.
- **Commit:** 4d759c6

**3. [Rule 3 - Blocking] Guia 09 com menos de 3 citações `app/`**
- **Found during:** Tarefa 3
- **Fix:** o guia passou a citar `app/mvnw`, `app/mvnw.cmd` e `app/.mvn/wrapper/maven-wrapper.properties` na introdução dos comandos por terminal.
- **Commit:** 4d759c6

### Observações (sem mudança de escopo)

- O guia 01 ficou com 277 linhas, acima da meta de 120 a 250, por causa dos dois diagramas Mermaid e da tabela de respostas. O guia 10 ficou com 96 linhas, abaixo da meta, mas com parágrafos longos e as 4 fases cobertas. As metas eram de tamanho-alvo, não gates.
- O comentário de `app/config/spotbugs/exclude.xml` já cita `StringRedisTemplate` e `MeterRegistry`, que pertencem à Phase 2. O guia 07 registra isso numa nota de uma linha, apontando para o guia 10.
- A justificativa de 302 × 301 no guia 01 está marcada como semântica HTTP geral e aponta para o `.planning/ROADMAP.md`, que deixa esse trade-off para o README da Phase 5.

## Known Stubs

Nenhum. Os guias são documentação completa. Os itens "planejados" estão concentrados no guia 10 e rotulados como tal.

## Threat Flags

Nenhuma superfície nova (só Markdown). As mitigações do threat model foram aplicadas:

- **T-01:** sem segredos. Só aparecem as credenciais de dev `linkpulse`, marcadas como locais, e o nome `LOCALSTACK_AUTH_TOKEN`.
- **T-02:** o guia 04 diz "ofuscação, não segurança", e o guia 05 traz a mitigação parcial por DNS.
- **T-03:** `down -v` aparece com aviso explícito de que apaga os dados.
- **T-04:** o gate de git confirmou zero alterações fora de `estudos/`.
- **T-05:** gates de caminho e link verdes.

## Self-Check: PASSED

- 11 arquivos em `estudos/` encontrados.
- Commits 040c96f, 92aef46 e 4d759c6 presentes em `git log`.
- `git rev-list --count 182bcc9..HEAD` = 3.
