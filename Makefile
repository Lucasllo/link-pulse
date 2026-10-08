.DEFAULT_GOAL := help

# Sem make? Os mesmos comandos rodam direto: cd app && ./mvnw -B -ntp <goal>
MVNW := cd app && ./mvnw -B -ntp

.PHONY: help build test verify run

help: ## Lista os alvos disponíveis
	@grep -E '^[a-zA-Z_-]+:.*?## ' Makefile | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-10s %s\n", $$1, $$2}'

build: ## Empacota o jar sem rodar testes
	$(MVNW) -DskipTests package

test: ## Roda só os testes unitários (sem Docker)
	$(MVNW) test

verify: ## Checkstyle, testes unitários, testes de integração e SpotBugs
	$(MVNW) verify

run: ## Sobe a API com o profile dev
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev
