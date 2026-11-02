# Phase 5: Terraform AWS e vitrine - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-02
**Phase:** 05-terraform-aws-e-vitrine
**Areas discussed:** Desenho da infra AWS, LocalStack e camadas, Formato do README, Evidências (prints/k6)

---

## Desenho da infra AWS

| Pergunta | Opções | Escolha |
|----------|--------|---------|
| Saída das tasks para a internet | Privadas + 1 NAT / Fargate em subnet pública / Privadas + VPC endpoints | ✓ Privadas + 1 NAT |
| Origem da imagem | ECR com GHCR por variável / GHCR direto / ECR pull-through cache | ✓ ECR com GHCR por variável |
| TLS no ALB | HTTPS opcional por variável / Só HTTP / HTTPS obrigatório | ✓ HTTPS opcional |
| Layout e state | Módulos + envs/prod / envs/dev + envs/prod / Workspaces | ✓ Módulos + envs/prod |
| URI do Mongo | Secret por variável / DocumentDB opcional / Secret vazio | ✓ Secret por variável |
| Senha do RDS | manage_master_user_password / random_password + secret | ✓ manage_master_user_password |
| Escala do ECS | 2 tasks + autoscaling CPU / 1 task / Você decide | ✓ 2 tasks + autoscaling |
| Health do ALB | TG 8080 com health 8081 / Você decide | ✓ TG 8080 com health 8081 |

## LocalStack e camadas

| Pergunta | Opções | Escolha |
|----------|--------|---------|
| Restringir ao subset do Hobby | Root envs/localstack / envs/prod com flags / -target | ✓ Root envs/localstack |
| Endpoint do provider | Bloco endpoints / tflocal | ✓ Bloco endpoints |
| Alvos make | compose.localstack.yaml + tf-* / docker run em script / Você decide | ✓ compose + tf-* |
| CI | terraform + localstack condicional / com paths filter / sem trivy config | ✓ terraform + localstack condicional, sem paths filter |
| Trivy IaC | Falhar em HIGH/CRITICAL com ignores justificados / Só reportar | ✓ Falhar com ignores |
| terraform test | Por módulo + composição / Só por módulo / Você decide | ✓ Por módulo + composição |

## Formato do README

| Pergunta | Opções | Escolha |
|----------|--------|---------|
| Estrutura | README vitrine + docs/ / README único estendido / Vitrine no topo, manual abaixo | ✓ Vitrine + docs/ |
| Diagrama | Mermaid / SVG desenhado / Mermaid + SVG AWS | ✓ Mermaid |
| Trade-offs | Tabela + docs/decisoes.md / ADRs numerados / Só README | ✓ Tabela + decisoes.md |
| Topo (multi) | Badges / Em 2 minutos / Destaques técnicos | ✓ Todos |
| Licença | MIT / Apache-2.0 / Sem licença | ✓ MIT |
| estudos/ | Link discreto / Não mencionar / Mover para docs/ | ✓ Link discreto |

## Evidências (prints/k6)

| Pergunta | Opções | Escolha |
|----------|--------|---------|
| Captura dos prints | Checkpoint manual / Grafana render / Playwright | ✓ Checkpoint manual |
| Stack dos prints | Compose + um print do kind / Só kind / Só compose | ✓ Compose + um print do kind |
| k6 no README | Tabela do JSON + contexto / Tabela + print / Rodar load de novo | ✓ Tabela do JSON + contexto |

## Claude's Discretion

- Nomes de módulos, variáveis e outputs; VPC escrita à mão (preferência)
- Região, CIDRs, classes de instância, retenção de logs
- TLS do Valkey no ElastiCache e a env correspondente do app
- Mecânica de verificação do tf-localstack
- Ordem da migração README → docs/

## Deferred Ideas

Nenhuma.
