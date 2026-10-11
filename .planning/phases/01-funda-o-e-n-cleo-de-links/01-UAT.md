---
status: complete
phase: 01-funda-o-e-n-cleo-de-links
source: [01-VERIFICATION.md]
started: 2026-10-09T23:44:08Z
updated: 2026-10-09T23:49:10Z
---

## Current Test

[testing complete]

## Tests

### 1. Runtime local no Windows (make run, demo-link, health na 8081, db-down)
Com o Docker ligado: instalar o GNU make (winget install ezwinports.make) e rodar make run no Git Bash (ou, sem make: docker compose -f compose.dev.yaml up -d --wait e depois cd app && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev). Depois: curl -i http://localhost:8080/demo-link; curl http://localhost:8081/actuator/health; curl -i http://localhost:8080/actuator/health; make db-down
expected: demo-link responde 302 para https://example.com/ com Cache-Control no-store, private; o health na 8081 responde UP; a 8080 não expõe /actuator (404); db-down para o container e mantém o volume pgdata
result: pass

### 2. README e Swagger UI
Seguir o README do zero e abrir http://localhost:8080/swagger-ui.html; criar links com e sem alias pelo "Try it out"; ler a regra de URL e o bullet "Loop de redirect via DNS (mitigação parcial)"
expected: Swagger UI carrega, cria links (201) e mostra erros em Problem Details; o README basta sem conhecimento prévio e descreve com fidelidade o que a regra de host bloqueia (atenção ao WR-04: exemplo de expiresAt 2026-12-31)
result: pass

### 3. Proibições de nível judgment
Revisar 01-03 (código curto não é segredo; nenhum log da URL de destino ou do corpo) e 01-08 (a checagem de host não resolve DNS)
expected: Confirmar o veredito não autoritativo do verificador: as três respeitadas
result: pass

## Summary

total: 3
passed: 3
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps
