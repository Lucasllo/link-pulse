.DEFAULT_GOAL := help

# Sem make? Os mesmos comandos rodam direto: cd app && ./mvnw -B -ntp <goal>
# Postgres local sem make: docker compose -f compose.dev.yaml up -d --wait (e ... down)
MVNW := cd app && ./mvnw -B -ntp
COMPOSE := docker compose -f compose.dev.yaml

.PHONY: help build test verify run db-up db-down

help: ## Lista os alvos disponíveis
	@grep -E '^[a-zA-Z_-]+:.*?## ' Makefile | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-10s %s\n", $$1, $$2}'

build: ## Empacota o jar sem rodar testes
	$(MVNW) -DskipTests package

test: ## Roda só os testes unitários (sem Docker)
	$(MVNW) test

verify: ## Checkstyle, testes unitários, testes de integração e SpotBugs
	$(MVNW) verify

run: db-up ## Sobe o Postgres local e a API com o profile dev
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev

db-up: ## Sobe o Postgres 18 local e espera o healthcheck
	$(COMPOSE) up -d --wait

db-down: ## Para o Postgres local (mantém o volume)
	$(COMPOSE) down
