# 11 · Dependabot (para iniciantes)

**Em uma frase:** o Dependabot é um robô do GitHub que toda semana procura versões novas das dependências do projeto e abre um Pull Request propondo a atualização, que o CI testa antes de você decidir.

## Primeiro: o que são "dependências"

Quase nenhum programa é escrito do zero. O Link Pulse usa código pronto feito por outras pessoas: o Spring Boot, o driver do Postgres, o cliente do Valkey, as "actions" do GitHub que rodam o CI, e assim por diante. Esses pedaços de código pronto se chamam **dependências**, porque o projeto depende deles para funcionar.

Cada dependência tem uma **versão**, como `7.0.1`. Os autores corrigem bugs e falhas de segurança e lançam versões novas o tempo todo. Se ninguém atualizar, o projeto envelhece: acumula bugs já corrigidos e brechas de segurança conhecidas.

Acompanhar dezenas de bibliotecas à mão é chato e fácil de esquecer.

## O Dependabot é um robô que faz esse acompanhamento por você

O Dependabot é um robô do próprio GitHub. Toda semana ele:

1. **Olha** as dependências do projeto.
2. **Confere** se saiu alguma versão nova.
3. Se saiu, **abre um Pull Request** (PR), que é uma proposta de mudança: "troque a versão X pela Y".
4. Esse PR **dispara o CI** ([ci.yml](../.github/workflows/ci.yml)), que compila o projeto e roda todos os testes com a versão nova.
5. **Você decide**: se os testes passaram, aceita (merge); se quebrou algo, investiga ou recusa.

Ele nunca altera o código sozinho. Ele só sugere, e a decisão continua com você.

**Analogia:** é como um assistente que lê toda semana os recalls dos fabricantes das peças do seu carro. Quando sai um, ele separa a peça nova, faz um teste rápido e deixa um bilhete: "chegou uma peça melhor, já testei, quer trocar?"

## Um exemplo real deste repositório

Você já recebeu um PR desses. No histórico de commits aparece:

```
fcba346 chore(deps): bump actions/upload-artifact from 7.0.1 to 7.0.2
25b52c1 Merge pull request #5 from Lucasllo/dependabot/github_actions/actions/upload-artifact-7.0.2
```

O Dependabot viu que a action `upload-artifact` saiu na versão 7.0.2, abriu o PR #5, o CI passou e o PR foi aceito.

## Como ele está configurado aqui

A configuração fica em [.github/dependabot.yml](../.github/dependabot.yml):

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
    ignore:
      # Maven Wrapper fixado em 3.9.16: a 3.10.0 (2026-10-01) é recente demais
      # e a 4.0 ainda está em RC. Reavaliar quando a 3.10.x amadurecer.
      - dependency-name: "org.apache.maven:apache-maven"
        versions: [">= 3.10.0"]
```

Ela vigia dois tipos de dependência:

| O que vigia | Onde | Frequência |
|---|---|---|
| **GitHub Actions** (as ferramentas que rodam o CI) | raiz do repositório | semanal |
| **Maven** (as bibliotecas Java do `pom.xml`) | pasta `app/` | semanal |

Ela também define três detalhes:

- **`open-pull-requests-limit: 5`**: no máximo 5 PRs abertos ao mesmo tempo, para não virar uma enxurrada.
- **`ignore` do Maven 3.10+**: o projeto fixou o Maven na versão 3.9.16 porque a 3.10.0 saiu no começo de outubro e ainda é nova demais para confiar. Essa regra diz ao robô: "não me ofereça a 3.10 por enquanto".
- **Pin por SHA**: o comentário "Mantém os pins por SHA das actions atualizados" se refere a uma prática de segurança. As actions do CI são referenciadas por um código único e imutável (o hash do commit), e não por um nome de versão que alguém poderia trocar por código malicioso. A desvantagem é que esses códigos são difíceis de atualizar à mão, e o Dependabot faz isso automaticamente. O guia [08](08-ci-github-actions.md) explica o pin por SHA em detalhe.

## Por que isso importa num portfólio

1. **Segurança:** falhas descobertas nas bibliotecas são corrigidas rápido.
2. **Projeto "vivo":** quem avalia o repositório vê que ele é mantido, e não abandonado.
3. **Atualização segura:** como cada PR passa pelos testes do CI, você só atualiza quando tem evidência de que nada quebrou.

Resumindo: **o Dependabot mantém as peças do projeto em dia, e o CI garante que a peça nova serve.**

## Perguntas para fixar

1. O Dependabot altera o código da `main` sozinho? Quem decide se a atualização entra?
2. Por que é importante que o CI rode em cada PR aberto pelo Dependabot?
3. Para que serve a regra `ignore` no `dependabot.yml` deste projeto?
4. Por que atualizar à mão actions fixadas por SHA seria trabalhoso?

<details>
<summary>Respostas</summary>

1. Não. Ele só abre um Pull Request; quem decide se a atualização entra (merge) é você.
2. Porque o CI compila e testa o projeto com a versão nova: é a evidência de que a atualização não quebrou nada.
3. Para o robô não oferecer o Maven 3.10 ou mais novo, porque o projeto fixou a 3.9.16 de propósito.
4. Porque seria preciso descobrir, para cada action, o hash de 40 caracteres do commit da versão nova e trocá-lo junto com o comentário de versão.

</details>

---

[← Anterior: 09 · Ambiente local e Windows](09-ambiente-local-e-windows.md) · [Índice](README.md) · [Próximo: 12 · Cache no Valkey →](12-cache-valkey-e-fail-open.md)
