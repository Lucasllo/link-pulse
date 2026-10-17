.DEFAULT_GOAL := help

# Sem make? Os mesmos comandos rodam direto: cd app && ./mvnw -B -ntp <goal>
# Dependências locais sem make: docker compose -f compose.dev.yaml up -d --wait (e ... down)
MVNW := cd app && ./mvnw -B -ntp
COMPOSE := docker compose -f compose.dev.yaml
# Stack completa (compose.yaml). Sem make: docker compose up -d --build --wait (e down, down -v, build app)
# Git Bash: NOCONV impede a conversão de argumentos /caminho quando o sh chama o docker.exe.
# Vale só para o docker: exportada para o make inteiro, quebra o classpath do ./mvnw.
NOCONV := MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
STACK := $(NOCONV) docker compose

.PHONY: help build test verify run db-up db-down up down clean image seed obs-check

help: ## Lista os alvos disponíveis
	@grep -E '^[a-zA-Z_-]+:.*?## ' Makefile | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-10s %s\n", $$1, $$2}'

build: ## Empacota o jar sem rodar testes
	$(MVNW) -DskipTests package

test: ## Roda só os testes unitários (sem Docker)
	$(MVNW) test

verify: ## Checkstyle, testes unitários, testes de integração e SpotBugs
	$(MVNW) verify

run: db-up ## Sobe as dependências locais e a API com o profile dev
	$(MVNW) spring-boot:run -Dspring-boot.run.profiles=dev

db-up: ## Sobe as dependências locais do compose.dev.yaml e espera os healthchecks
	$(COMPOSE) up -d --wait

db-down: ## Para as dependências locais (mantém os volumes)
	$(COMPOSE) down

up: ## Builda a imagem e sobe a stack completa, esperando os healthchecks
	$(STACK) up -d --build --wait

down: ## Para a stack completa (mantém os volumes)
	$(STACK) down

clean: ## Para a stack completa e apaga os volumes
	$(STACK) down -v --remove-orphans

image: ## Builda só a imagem local do app (link-pulse:local)
	$(STACK) build app

# -T: sem TTY (funciona no Git Bash/mintty e no CI); --quiet: sem barra de progresso, o resumo e
# os logs do seed continuam.
seed: ## Popula links e cliques de demonstração (k6 em container)
	$(STACK) run --rm -T k6 run --quiet seed.js

# promtool da própria imagem do Prometheus: nenhuma ferramenta nova no host. --no-deps: só o
# container efêmero, sem subir a stack. Os caminhos absolutos chegam intactos ao docker.exe no
# Git Bash por causa do NOCONV dentro de STACK.
obs-check: ## Valida a config e as regras do Prometheus (promtool da imagem oficial)
	$(STACK) run --rm --no-deps -T --entrypoint promtool prometheus check config /etc/prometheus/prometheus.yml
	$(STACK) run --rm --no-deps -T --entrypoint promtool prometheus test rules /etc/prometheus/tests/link-pulse-alerts-test.yml
