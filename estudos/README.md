# Estudos do Link Pulse

Material didático em pt-BR para quem quer aprender as tecnologias do Link Pulse lendo o código real do repositório. Cada guia explica um tema de forma genérica e depois mostra como ele aparece aqui, com caminhos de arquivo e trechos copiados literalmente.

## Para quem é e como usar

- Para quem estuda Java/Spring, banco de dados, testes, build e CI e quer um projeto pequeno, mas completo, como referência.
- Para o próprio autor, como revisão do que foi construído e do porquê de cada decisão.
- Leia com o código aberto ao lado. Quando o guia citar `app/src/main/java/dev/linkpulse/link/LinkService.java`, abra o arquivo e confira.
- Cada guia termina com "Experimente" (comandos ou exercícios) e "Perguntas para fixar". Tente responder antes de seguir.

## Estado do projeto

A Phase 1 (fundação e núcleo de links) está concluída: a API cria links curtos e redireciona a partir do Postgres, com Liquibase, Checkstyle, SpotBugs, testes com Testcontainers e CI no GitHub Actions. A Phase 2 (cache Valkey e pipeline de cliques) está em planejamento. A posição atual fica em [STATE.md](../.planning/STATE.md).

Os guias 01 a 09 descrevem só o que existe hoje no repositório. O que ainda vai ser construído aparece apenas no guia 10.

## Guias

| Nº | Guia | O que você aprende |
|----|------|--------------------|
| 01 | [01 · Estrutura e fluxo](01-estrutura-e-fluxo-de-requisicoes.md) | Mapa do repositório, pacotes `dev.linkpulse.*` e o caminho de `POST /links` e `GET /{code}` até o Postgres |
| 02 | [02 · Spring Boot e Java 21](02-spring-boot-e-java-21.md) | Starters do Boot 4, auto-configuração, profiles, `@ConfigurationProperties`, Actuator na 8081, records e virtual threads |
| 03 | [03 · Postgres, JPA e Liquibase](03-postgres-jpa-e-liquibase.md) | Entidade `Link`, Spring Data JPA, sequência `link_id_seq`, changelogs YAML e contexts dev/prod |
| 04 | [04 · Código curto Base62](04-codigo-curto-base62.md) | Base62, permutação multiplicativa mod `62^7` e por que isso é ofuscação, não segurança |
| 05 | [05 · Validação e erros](05-validacao-e-erros.md) | Bean Validation, constraint própria, políticas de alias e host, Problem Details (RFC 9457) |
| 06 | [06 · Testes](06-testes.md) | Unitários × integração, Surefire × Failsafe, Testcontainers com `@ServiceConnection` |
| 07 | [07 · Build e qualidade](07-build-e-qualidade.md) | Maven wrapper com SHA-256, anatomia do `pom.xml`, Checkstyle, SpotBugs e `.gitattributes` |
| 08 | [08 · CI no GitHub Actions](08-ci-github-actions.md) | O `ci.yml` passo a passo, pin por SHA, menor privilégio e Dependabot |
| 09 | [09 · Ambiente local e Windows](09-ambiente-local-e-windows.md) | `compose.dev.yaml`, Makefile e os comandos para Git Bash, cmd e PowerShell |
| 10 | [10 · Próximas fases](10-proximas-fases.md) | O que as Phases 2 a 5 vão trazer: Valkey, Redis Streams, MongoDB, observabilidade, k6, kind/Helm e Terraform |

## Trilhas de leitura

- **Quero entender a aplicação:** 01 → 02 → 03 → 04 → 05.
- **Quero rodar e entregar:** 09 → 07 → 06 → 08.
- Depois de qualquer trilha, o 10 mostra para onde o projeto vai.

## Convenções deste material

- Caminhos em crase são relativos à raiz do repositório, por exemplo `app/pom.xml`. Links clicáveis são relativos a esta pasta (`../app/pom.xml`).
- Relatórios de build aparecem como `target/...`: a pasta `target` fica dentro de `app/` e só existe depois de um build.
- Trechos de código são copiados literalmente dos arquivos. Partes omitidas aparecem como `// ...` ou `# ...`.
- Saídas de comando só aparecem quando vêm do README do projeto ou de um comando executado de verdade.
- O que é planejado (e ainda não existe) só aparece no guia 10.

## Glossário rápido

- **Starter:** dependência do Spring Boot que agrupa bibliotecas e auto-configuração de um tema (web, JPA, validação).
- **Auto-configuração:** o Boot cria beans sozinho a partir do classpath e das propriedades, sem código de configuração.
- **Profile:** conjunto nomeado de configurações (`dev`, `prod`) ativado na subida.
- **Bean:** objeto criado e gerenciado pelo container do Spring e injetado onde for pedido.
- **Record:** tipo do Java (16+) para portadores de dados imutáveis, com construtor, getters, `equals` e `hashCode` gerados.
- **Changeset:** unidade de mudança do Liquibase, aplicada uma vez e registrada com checksum.
- **Context do Liquibase:** rótulo que liga ou desliga changesets conforme o ambiente (`dev`, `prod`).
- **Problem Details:** formato padrão de erro HTTP em JSON definido pela RFC 9457.
- **IT:** teste de integração (classes `*IT`), que sobe o contexto Spring contra serviços reais.
- **Testcontainers:** biblioteca que sobe containers Docker de verdade durante os testes.
- **Wrapper:** script (`mvnw`) que baixa e roda uma versão fixa do Maven, sem instalação prévia.
- **Pin por SHA:** referenciar uma GitHub Action pelo hash completo do commit, que não muda, em vez de uma tag, que pode ser movida.
