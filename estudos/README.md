# Estudos do Link Pulse

Material didático em português para quem está **começando na programação** e quer aprender as tecnologias do Link Pulse lendo o código real do repositório. Cada guia explica um tema do zero, com analogias, e depois mostra como ele aparece aqui, com caminhos de arquivo e trechos copiados do código.

## Para quem é e como usar

- Para quem está aprendendo Java/Spring, bancos de dados, testes, build, CI e observabilidade e quer um projeto pequeno, mas completo, como referência.
- Para o próprio autor, como revisão do que foi construído e do porquê de cada decisão.
- Leia com o código aberto ao lado. Quando o guia citar `app/src/main/java/dev/linkpulse/link/LinkService.java`, abra o arquivo e confira.
- Todo guia tem a mesma estrutura:
  1. **O que você vai aprender**;
  2. **Antes de começar: conceitos**, o tema explicado do zero, sem depender do projeto;
  3. **No Link Pulse**, o tema no código real, com leitura guiada dos trechos;
  4. **Por que assim?**, as decisões e o motivo de cada uma;
  5. **Experimente**, comandos e exercícios;
  6. **Perguntas para fixar**, com as respostas escondidas num bloco "Respostas" que abre com um clique. Tente responder antes de abrir.

## Estado do projeto

As Phases 1 e 2 estão concluídas:

- **Phase 1:** a API cria links curtos e redireciona a partir do Postgres, com Liquibase, Checkstyle, SpotBugs, testes com Testcontainers e CI no GitHub Actions.
- **Phase 2:** o redirect sai do cache Valkey (e continua funcionando se ele cair), cada clique vira um evento gravado no MongoDB, há estatísticas por link, rate limit na criação e métricas para o Prometheus.

A Phase 3 (stack local observável, com Prometheus, Grafana e k6) está em planejamento. A posição atual fica em [STATE.md](../.planning/STATE.md).

Os guias descrevem só o que existe hoje no repositório. O que ainda vai ser construído aparece apenas no guia 10.

## Guias

| Nº | Guia | O que você aprende |
|----|------|--------------------|
| 00 | [00 · Visão geral do projeto](00-visao-geral-do-projeto.md) | O que o Link Pulse faz e qual tecnologia entra em cada momento: criar, redirecionar, gravar cliques, estatísticas |
| 01 | [01 · Estrutura e fluxo](01-estrutura-e-fluxo-de-requisicoes.md) | O que é uma API, os pacotes `dev.linkpulse.*` e o caminho de cada endpoint, do HTTP aos bancos |
| 02 | [02 · Spring Boot e Java 21](02-spring-boot-e-java-21.md) | Starters, auto-configuração, profiles, beans (`ApplicationConfig`), `LinkPulseProperties`, `application.yml`, Actuator, records e virtual threads |
| 03 | [03 · Postgres, JPA e Liquibase](03-postgres-jpa-e-liquibase.md) | Banco relacional, entidade `Link`, Spring Data JPA, sequence, transação e migrações com contexts dev/prod |
| 04 | [04 · Código curto Base62](04-codigo-curto-base62.md) | Bases numéricas, resto da divisão, coprimos e por que o código é ofuscação, não segurança |
| 05 | [05 · Validação e erros](05-validacao-e-erros.md) | Bean Validation, regra própria `@HttpUrl`, políticas de alias e host e todos os erros em Problem Details (RFC 9457) |
| 06 | [06 · Testes](06-testes.md) | Unitários × integração, Testcontainers com Postgres, Valkey e MongoDB, testes assíncronos e simulação de queda |
| 07 | [07 · Build e qualidade](07-build-e-qualidade.md) | Maven e o ciclo de vida, wrapper com SHA-256, Checkstyle, SpotBugs e fins de linha |
| 08 | [08 · CI no GitHub Actions](08-ci-github-actions.md) | O que é CI, o `ci.yml` passo a passo, pin por SHA e menor privilégio |
| 09 | [09 · Ambiente local e Windows](09-ambiente-local-e-windows.md) | Docker, o `compose.dev.yaml` com os três bancos, o Makefile e os comandos para Git Bash, cmd e PowerShell |
| 10 | [10 · Próximas fases](10-proximas-fases.md) | O que as Phases 3 a 5 vão trazer: imagem Docker, Prometheus, Grafana, k6, Kubernetes, Helm e Terraform |
| 11 | [11 · Dependabot](11-dependabot.md) | O que são dependências e como o Dependabot abre PRs de atualização testados pelo CI |
| 12 | [12 · Cache no Valkey e fail-open](12-cache-valkey-e-fail-open.md) | Cache-aside, TTL, negative cache, limpeza depois do commit e como sobreviver à queda do Valkey sem ficar lento |
| 13 | [13 · Pipeline de cliques](13-pipeline-de-cliques.md) | Processamento assíncrono, Redis Streams, consumer group, at-least-once sem duplicata, privacidade e MongoDB |
| 14 | [14 · Estatísticas no MongoDB](14-estatisticas-no-mongodb.md) | Aggregation pipeline (`$match`, `$group`, `$facet`), período em UTC, série diária e filtro de robôs |
| 15 | [15 · Rate limit por IP](15-rate-limit-por-ip.md) | Janela fixa, script Lua atômico, interceptor, 429 com `Retry-After` e como descobrir o IP real sem ser enganado |
| 16 | [16 · Métricas com Micrometer](16-metricas-com-micrometer.md) | Counter, gauge, timer e histograma, tags e cardinalidade, `/actuator/prometheus` e o catálogo de métricas |

## Trilhas de leitura

- **Estou começando:** leia o 00 antes de qualquer trilha.
- **Quero entender a aplicação:** 01 → 02 → 03 → 04 → 05.
- **Quero entender o caminho quente e os cliques (Phase 2):** 12 → 13 → 14 → 15 → 16.
- **Quero rodar e entregar:** 09 → 07 → 06 → 08 → 11.
- Depois de qualquer trilha, o 10 mostra para onde o projeto vai.

A navegação no rodapé de cada guia segue a ordem 00 → 01 → … → 09 → 11 → 12 → … → 16 → 10.

## Convenções deste material

- Caminhos em crase são relativos à raiz do repositório, por exemplo `app/pom.xml`. Links clicáveis são relativos a esta pasta (`../app/pom.xml`).
- Relatórios de build aparecem como `target/...`: a pasta `target` fica dentro de `app/` e só existe depois de um build.
- Trechos de código são copiados dos arquivos. Partes omitidas aparecem como `// ...` ou `# ...`.
- Saídas de comando e medições só aparecem quando vêm do README do projeto, dos resumos dos planos em `.planning/phases/` ou de um comando executado de verdade.
- O que é planejado (e ainda não existe) só aparece no guia 10.

## Glossário rápido

- **API:** programa que recebe pedidos pela rede e responde com dados (aqui, JSON e redirects), sem telas.
- **Requisição / resposta HTTP:** o pedido do cliente (método, caminho, corpo) e o retorno do servidor (status, headers, corpo).
- **Status HTTP:** número de três dígitos da resposta: 2xx deu certo, 3xx vá para outro lugar, 4xx o cliente errou, 5xx o servidor falhou.
- **Framework:** esqueleto pronto de aplicação; aqui, o Spring Boot.
- **Starter:** dependência do Spring Boot que agrupa bibliotecas e auto-configuração de um tema (web, JPA, Redis).
- **Auto-configuração:** o Boot cria objetos sozinho a partir das bibliotecas presentes e das propriedades.
- **Bean:** objeto criado e guardado pelo Spring e entregue (injetado) a quem o pede no construtor.
- **Injeção de dependência:** a classe recebe pronto o que precisa, em vez de criar com `new`.
- **Profile:** conjunto nomeado de configurações (`dev`, `prod`) ativado na subida.
- **Record:** tipo do Java (16+) para carregar dados imutáveis, com construtor, leitura, `equals` e `hashCode` gerados.
- **Virtual thread:** thread leve do Java 21 que libera a thread do sistema enquanto espera.
- **Transação:** pacote de operações no banco que vale "tudo ou nada".
- **Changeset:** unidade de mudança do Liquibase, aplicada uma vez e registrada com checksum.
- **Context do Liquibase:** rótulo que liga ou desliga changesets conforme o ambiente (`dev`, `prod`).
- **Cache:** memória rápida que guarda respostas para não recalculá-las. **Hit** = estava lá; **miss** = não estava.
- **TTL:** tempo de vida de uma chave no Valkey; depois dele, a chave some.
- **Fail-open:** quando uma peça de apoio cai, o sistema segue funcionando sem ela.
- **Assíncrono:** deixar o trabalho para ser feito depois, sem esperar.
- **Stream / consumer group:** fila de eventos do Valkey/Redis e o grupo de leitores que divide o trabalho.
- **At-least-once / idempotente:** "nenhum evento se perde, mas pode chegar duas vezes" / "repetir a operação não muda o resultado".
- **HMAC:** hash com chave secreta; transforma o IP num código do qual não dá para voltar.
- **Aggregation pipeline:** sequência de estágios do MongoDB que filtra, agrupa e conta documentos.
- **Rate limit:** limite de quantas vezes algo pode ser feito num intervalo de tempo.
- **Métrica / tag / cardinalidade:** número acompanhado ao longo do tempo / fatia da métrica / quantos valores diferentes a tag pode ter.
- **Problem Details:** formato padrão de erro HTTP em JSON definido pela RFC 9457.
- **IT:** teste de integração (classes `*IT`), que sobe a aplicação contra serviços reais.
- **Testcontainers:** biblioteca que sobe containers Docker de verdade durante os testes.
- **Container / imagem:** programa rodando isolado numa "caixa" / o molde da caixa.
- **Wrapper:** script (`mvnw`) que baixa e roda uma versão fixa do Maven, sem instalação prévia.
- **CI:** compilar e testar automaticamente a cada mudança enviada ao repositório.
- **Pin por SHA:** referenciar uma GitHub Action pelo hash do commit, que não muda, em vez de uma tag, que pode ser movida.
