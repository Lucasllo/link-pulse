# 00 · Visão geral do projeto (para iniciantes)

**Em uma frase:** o Link Pulse é um encurtador de links com estatísticas de cliques, e cada tecnologia dele entra num momento específico do caminho que um pedido percorre.

## 1. O que é

O Link Pulse é um **encurtador de links**, do mesmo tipo que o bit.ly. Você entrega um endereço comprido, por exemplo:

```
https://www.minhaloja.com.br/produtos/categoria/tenis/modelo-x?cor=azul&tamanho=42
```

e recebe um link curto, por exemplo `http://localhost:8080/aZ3k9Qp`. Quem abre o link curto é levado automaticamente para o endereço comprido.

O projeto também faz **analytics**: conta quantas vezes cada link foi clicado, em que dias, de quais sites as pessoas vieram e com qual navegador.

O projeto não tem telas. É só uma **API**, ou seja, um programa que recebe pedidos pela internet e responde com dados. Ele existe para o portfólio: mostrar a um recrutador que você sabe construir e operar um sistema de backend "de verdade".

## 2. Como funciona: as 3 operações

| Ação | Pedido (HTTP) | O que acontece |
|---|---|---|
| Criar link | `POST /links` | Salva a URL e devolve o código curto |
| Usar link | `GET /aZ3k9Qp` | Responde "vá para tal endereço" (código **302**) |
| Ver estatísticas | `GET /links/aZ3k9Qp/stats` | Devolve os cliques dos últimos 30 dias |

## 3. Cada tecnologia no momento em que ela entra

### Momento A: você cria um link (`POST /links`)

1. **Java 21 + Spring Boot 4.1**. O Java é a linguagem do programa. O Spring Boot é um framework, uma "caixa de ferramentas" pronta que já sabe receber pedidos HTTP, ligar o programa a bancos de dados e assim por diante. Sem ele, você escreveria milhares de linhas só de infraestrutura.

2. **Bean Validation**. Confere se o que chegou faz sentido: se a URL é válida, se o apelido personalizado ("alias") não usa caracteres proibidos, se a data de expiração não está no passado. Quando algo está errado, a resposta é um erro padronizado (*Problem Details*).

3. **Rate limit com Valkey + Lua**. Impede que uma pessoa crie milhares de links por segundo. Cada IP tem um contador no Valkey (veja abaixo); quem passa do limite recebe o erro **429 "Too Many Requests"**. O contador é um pequeno script na linguagem **Lua**, que o Valkey executa de uma vez só, sem que dois pedidos simultâneos atrapalhem a contagem um do outro.

4. **PostgreSQL**. Um banco de dados relacional, organizado em tabelas como uma planilha bem rígida, e a **fonte da verdade** dos links: se o resto todo sumir, os links continuam salvos aqui. O Postgres tem um "contador automático" (a *sequence*) que entrega um número novo para cada link: 1, 2, 3...

5. **Gerador de código (Base62)**. O número do link é transformado em um texto de 7 caracteres usando 62 símbolos (0–9, A–Z, a–z). Antes disso, o número passa por uma conta matemática que embaralha o resultado. Assim, o link 1 e o link 2 não viram `0000001` e `0000002`, que qualquer pessoa conseguiria adivinhar. O código explica que isso é **ofuscação, não segurança** (detalhes no guia [04](04-codigo-curto-base62.md)).

6. **JPA/Hibernate**. Fazem a ponte entre o Java e o Postgres. Você trabalha com objetos Java (`Link`) e eles cuidam do SQL.

7. **Liquibase**. Cria e atualiza a estrutura do banco (tabelas, sequence) a partir de arquivos versionados em [db/changelog/](../app/src/main/resources/db/changelog/). Ele funciona como um "git do banco de dados": qualquer pessoa que rodar o projeto recebe exatamente as mesmas tabelas.

### Momento B: alguém clica no link (`GET /aZ3k9Qp`)

Este é o **caminho quente** (*hot path*): acontece muitas vezes e precisa ser muito rápido.

1. **Valkey como cache**. O Valkey é uma versão livre do Redis, um banco que guarda tudo **na memória RAM** e por isso é extremamente rápido. O programa pergunta primeiro ao Valkey qual é o destino do código `aZ3k9Qp`:
   - **Achou (hit)**: responde na hora, sem tocar no Postgres.
   - **Não achou (miss)**: busca no Postgres e guarda a resposta no Valkey para a próxima vez.

   O nome dessa técnica é **cache-aside**. Você pode ver o fluxo em [RedirectService.java](../app/src/main/java/dev/linkpulse/link/RedirectService.java). O cache também guarda respostas negativas, como "esse código não existe" (404) ou "esse link expirou" (410). Isso evita que alguém sobrecarregue o Postgres pedindo códigos inventados.

2. **Fail-open**. Se o Valkey cair, o redirect **continua funcionando**, só que mais devagar, indo direto ao Postgres. Um pedaço quebrado não derruba o sistema inteiro.

3. **Registro do clique com Redis Streams**. Gravar o clique no banco de estatísticas na hora deixaria o redirect lento. Por isso o programa só joga um recado ("houve um clique em `aZ3k9Qp`, com tal navegador, vindo de tal site") numa **fila** dentro do Valkey (o *Stream*) e responde 302 imediatamente. O clique é gravado de verdade depois, sem o usuário esperar. Isso é processamento **assíncrono**.
   - Por privacidade, o IP de quem clicou não é guardado como está: ele vira um *hash* (HMAC), um código que não permite descobrir o IP original.

### Momento C: os cliques são gravados (em segundo plano)

1. **Consumer (o "leitor da fila")**. Um processo dentro da aplicação, que roda em *virtual threads* (threads leves do Java 21), lê a fila em **lotes de até 500 cliques** e grava todos de uma vez. Gravar em lote é muito mais eficiente do que um por um.

2. **MongoDB**. Um banco de **documentos**: guarda registros flexíveis, parecidos com JSON. Ele combina com eventos de clique, que são muitos, simples e só crescem. Também é bom para "agregações" do tipo "some os cliques por dia".

3. **Garantia "at-least-once"**. O clique só é marcado como processado (*ACK*) **depois** de salvo no Mongo. Se o programa travar no meio, o clique continua na fila e é reprocessado, sem se perder e sem ficar duplicado.

### Momento D: alguém pede as estatísticas (`GET /links/{code}/stats`)

O MongoDB executa uma **aggregation pipeline**, uma sequência de etapas como "filtre este código → agrupe por dia → ordene". A resposta traz os cliques por dia, os principais sites de origem e os navegadores. Por padrão, acessos de robôs (bots) ficam de fora.

## 4. Tecnologias que cuidam da qualidade e da operação

Nada disso aparece para o usuário final, mas é o que um avaliador de DevOps procura:

| Tecnologia | Para que serve | Analogia |
|---|---|---|
| **Maven** | Baixa as bibliotecas, compila e roda os testes | O "gerente de obra" |
| **JUnit + Testcontainers** | Testes automáticos. O Testcontainers sobe um Postgres, um Mongo e um Valkey **de verdade** em containers durante os testes | Testar o carro na estrada, não só no simulador |
| **Checkstyle / SpotBugs** | Verificam o estilo do código e procuram bugs comuns automaticamente | Corretor ortográfico e revisor |
| **GitHub Actions** ([ci.yml](../.github/workflows/ci.yml)) | Roda build e testes a cada envio de código | Um inspetor que confere tudo sozinho |
| **Dependabot** | Avisa quando sai uma versão nova de alguma biblioteca | Lembrete de atualização |
| **Micrometer + Actuator** | Expõem métricas (cache hits/misses, tamanho da fila...) e a "saúde" da aplicação | O painel de instrumentos do carro |
| **Swagger (springdoc)** | Gera uma página que documenta a API e permite testá-la no navegador | O manual interativo |
| **Docker** | Empacota a aplicação com tudo de que ela precisa num "container" que roda igual em qualquer máquina | Um contêiner de navio padronizado |
| **Makefile** | Atalhos como `make up` | Botões prontos |

## 5. O que já existe e o que vem depois

O projeto está dividido em 5 fases ([ROADMAP.md](../.planning/ROADMAP.md)):

- ✅ **Fase 1**: criar e redirecionar links com Postgres, Liquibase, testes e CI.
- ✅ **Fase 2**: cache no Valkey, rate limit, fila de cliques → MongoDB e estatísticas.
- ⏳ **Fase 3**: `make up` sobe tudo com Docker Compose, mais **Prometheus** (coleta as métricas), **Grafana** (desenha os gráficos), alertas e o **k6** (simula milhares de usuários para medir o desempenho).
- ⏳ **Fase 4**: rodar no **Kubernetes** (um "orquestrador" que gerencia muitos containers, reinicia os que caem e cria mais cópias quando a carga sobe) usando o **kind** (um Kubernetes de teste dentro do Docker), **Helm** (o "instalador" de aplicações no Kubernetes) e **Traefik** (a porta de entrada que direciona o tráfego). As imagens serão publicadas no GHCR e verificadas pelo **Trivy**, um scanner de vulnerabilidades.
- ⏳ **Fase 5**: **Terraform**, que descreve em código a infraestrutura na nuvem da AWS (rede, ECS, RDS, ElastiCache) sem precisar clicar em nada. O código será validado com testes e com o **LocalStack** (um simulador da AWS), sem gastar dinheiro.

## 6. Resumo em uma frase por banco

- **Postgres**: guarda os links com segurança (o "cofre").
- **Valkey**: responde rápido (cache), segura os abusos (rate limit) e serve de fila temporária dos cliques.
- **MongoDB**: guarda a montanha de cliques e calcula as estatísticas.

Cada um faz o que faz de melhor, e o sistema continua funcionando mesmo quando um deles tem problema.

## Perguntas para fixar

1. Por que o redirect consulta o Valkey antes do Postgres?
2. O que acontece com o redirect se o Valkey cair?
3. Por que o clique vai para uma fila em vez de ser gravado direto no MongoDB?
4. Qual banco é a "fonte da verdade" dos links, e por quê?
5. Por que o código curto não deve ser tratado como segredo?

<details>
<summary>Respostas</summary>

1. Porque o Valkey guarda tudo na memória e responde muito mais rápido; o Postgres só é consultado quando o Valkey não sabe a resposta.
2. Continua funcionando (fail-open), indo direto ao Postgres; fica só um pouco mais lento.
3. Para o redirect responder na hora, sem esperar o MongoDB, e continuar funcionando mesmo se o MongoDB cair.
4. O Postgres: os links nascem lá, com regras que impedem códigos repetidos; o cache e os cliques podem ser reconstruídos ou perdidos sem perder os links.
5. Porque é ofuscação, não segurança: quem conhece dois códigos seguidos consegue calcular os outros.

</details>

## Onde aprofundar

| Momento | Guias |
|---------|-------|
| A: criar um link | [01](01-estrutura-e-fluxo-de-requisicoes.md) (fluxo), [03](03-postgres-jpa-e-liquibase.md) (Postgres), [04](04-codigo-curto-base62.md) (código curto), [05](05-validacao-e-erros.md) (validação), [15](15-rate-limit-por-ip.md) (rate limit) |
| B: alguém clica | [12](12-cache-valkey-e-fail-open.md) (cache e fail-open), [13](13-pipeline-de-cliques.md) (publicação do clique) |
| C: cliques gravados | [13](13-pipeline-de-cliques.md) (consumer e MongoDB) |
| D: estatísticas | [14](14-estatisticas-no-mongodb.md) |
| Qualidade e operação | [02](02-spring-boot-e-java-21.md), [06](06-testes.md), [07](07-build-e-qualidade.md), [08](08-ci-github-actions.md), [09](09-ambiente-local-e-windows.md), [11](11-dependabot.md), [16](16-metricas-com-micrometer.md) |
| O que vem depois | [10](10-proximas-fases.md) |

---

[Índice](README.md) · [Próximo: 01 · Estrutura e fluxo →](01-estrutura-e-fluxo-de-requisicoes.md)
