---
phase: 01-funda-o-e-n-cleo-de-links
plan: 08
subsystem: api
tags: [seguranca, validacao, url, loopback, ssrf-lite, configuration-properties, readme, gap-closure]
status: complete

requires:
  - phase: 01-06
    provides: "Regra de URL (@HttpUrl), LinkProblems.invalidField e a checagem original do próprio encurtador (T-06-03), agora substituída"
  - phase: 01-07
    provides: "README pt-BR com as seções Regras, Código curto (tabela de configuração) e Riscos conhecidos"
provides:
  - "TargetHostPolicy (dev.linkpulse.link): host normalizado (Locale.ROOT, sem pontos finais) recusado se for o próprio encurtador (base-url + self-hosts), loopback em qualquer forma literal ou IP numérico fora de a.b.c.d, sem DNS"
  - "LinkPulseProperties.selfHosts (linkpulse.self-hosts / LINKPULSE_SELF_HOSTS), vazio por padrão"
  - "linkpulse.base-url inválida derruba a subida com IllegalArgumentException de mensagem fixa (WR-01)"
  - "LinkService(LinkRepository, CodeGenerator, AliasPolicy, TargetHostPolicy, Clock); create começa com targetHostPolicy.check(request.url())"
  - "README: regra de URL exata e bullet 'Loop de redirect via DNS (mitigação parcial)'"
affects: [phase-02-rate-limit, phase-03-compose, phase-04-helm, phase-05-terraform-ecs, verify-work]

plan_head_before: 2e233afb40c0f29e55c020319601392d97ecb869
actuals:
  tokens: 9316
  tasks: 3
  commits: 7

tech-stack:
  added: []
  patterns:
    - "Política de domínio pura (public final class + @Bean no ApplicationConfig), no mesmo molde do AliasPolicy"
    - "Validação de @ConfigurationProperties no construtor compacto do record, com mensagem fixa que não ecoa o valor"
    - "Checagem de host sem DNS: InetAddress.getByName só recebe literal IPv6 entre colchetes"

key-files:
  created:
    - app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java
    - app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java
    - app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java
  modified:
    - app/src/main/java/dev/linkpulse/link/LinkService.java
    - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
    - app/src/main/java/dev/linkpulse/config/ApplicationConfig.java
    - app/src/main/resources/application.yml
    - app/src/test/java/dev/linkpulse/link/LinkApiIT.java
    - README.md

key-decisions:
  - "Loopback é recusado em qualquer perfil e para qualquer base-url, não só por uma lista de dev: em dev é o próprio encurtador, em prod aponta para a máquina de quem clica, e o compose da Phase 3 pode rodar prod com base http://localhost:8080"
  - "Host cujo último rótulo termina em número (regra WHATWG) só é aceito na forma canônica a.b.c.d; 0x7f000001, 2130706433, 0177.0.0.1 e 127.000.000.001 viram 400"
  - "LINKPULSE_SELF_HOSTS (com underscore) faz binding no Spring Boot 4.1.1; a contingência da forma canônica LINKPULSE_SELFHOSTS não foi necessária"
  - "T-06-03 da 01-06-PLAN e a afirmação de fechamento do loop na 01-06-SUMMARY ficam substituídos por T-08-01 (mitigado) e T-08-06 (aceito, mitigação parcial por DNS); os arquivos da 01-06 não foram editados"
  - "O key_link baseUrl().getHost() da 01-06 foi substituído pelos key_links targetHostPolicy.check(request.url()) (LinkService) e new TargetHostPolicy(properties.baseUrl(), properties.selfHosts()) (ApplicationConfig)"

patterns-established:
  - "Regras de destino de link ficam no TargetHostPolicy; o LinkService só delega"
  - "Toda propriedade da qual a URL pública depende é validada na subida (falha visível, nunca desligamento silencioso)"

requirements-completed: [LINK-01, LINK-06]

coverage:
  - id: D1
    description: "Loop pelo próprio host com ponto final ou outra caixa: POST com http://localhost.:8080/loop-a e alias loop-a responde 400 no campo url com 'não pode apontar para o próprio encurtador', e GET /loop-a responde 404"
    requirement: LINK-01
    verification:
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/link/LinkApiIT.java#trailingDotOnTheShortenerHostIsRejectedAndNoLoopIsCreated"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/link/LinkApiIT.java#urlPointingToTheShortenerItselfIsValidationErrorOnUrl"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java#rejectsTheShortenerItself"
        status: pass
    human_judgment: false
  - id: D2
    description: "Loopback (127.0.0.0/8, 0.0.0.0, [::1], [::], IPv4-mapped, localhost, *.localhost) e IP numérico ofuscado recusados sem DNS; IPv4 público canônico aceito"
    requirement: LINK-01
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java#rejectsLoopbackDestinations"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java#rejectsNonCanonicalNumericHosts"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java#acceptsOtherDestinations"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/link/LinkApiIT.java#loopbackOrObfuscatedHostIsValidationErrorOnUrl"
        status: pass
      - kind: integration
        ref: "app/src/test/java/dev/linkpulse/link/LinkApiIT.java#publicIpv4LiteralIsAccepted"
        status: pass
    human_judgment: false
  - id: D3
    description: "Erros de host saem como 400 /problems/validation-error com errors[0].field = url (Problem Details)"
    requirement: LINK-06
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java#assertRejected (status, type, field, message)"
        status: pass
    human_judgment: false
  - id: D4
    description: "linkpulse.base-url inválida derruba a subida sem ecoar o valor; self-hosts por propriedade e por LINKPULSE_SELF_HOSTS"
    requirement: LINK-01
    verification:
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java#contextFailsOnStartupWhenBaseUrlIsInvalid"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java#startupFailureDoesNotEchoTheBaseUrl"
        status: pass
      - kind: unit
        ref: "app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java#environmentVariablesBindBaseUrlAndSelfHosts"
        status: pass
    human_judgment: false
  - id: D5
    description: "README descreve exatamente o que a regra de URL bloqueia e registra o loop via DNS de terceiros como mitigação parcial"
    verification:
      - kind: other
        ref: "gate de texto da Task 3 (grep) imprime README-OK"
        status: pass
    human_judgment: true
    rationale: "A fidelidade e a clareza do texto do README para o avaliador é julgamento editorial; o grep só prova a presença das frases-chave e a ausência da afirmação antiga"

duration: 14min
completed: 2026-10-09
---

# Phase 1 Plan 08: Fechamento do loop de redirect (TargetHostPolicy) Summary

**`TargetHostPolicy` normaliza o host de destino (minúsculas com `Locale.ROOT`, sem pontos finais) e recusa, sem DNS, o próprio encurtador (`base-url` + `LINKPULSE_SELF_HOSTS`), loopback em qualquer forma literal e IP numérico ofuscado. Uma `linkpulse.base-url` inválida agora derruba a subida, e o README registra o loop via DNS de terceiros como mitigação parcial.**

## Performance

- **Duration:** 14 min
- **Started:** 2026-10-09T01:09:56Z
- **Completed:** 2026-10-09T01:24:16Z
- **Tasks:** 3
- **Files modified:** 9 (3 criados, 6 alterados)

## Accomplishments

- O CR-01 fechou: `http://localhost.:8080/loop-a` com alias `loop-a` responde 400 no campo `url`, e o `GET /loop-a` responde 404 (o link em loop não é criado). As variações que o verificador reproduziu (`127.0.0.1`, `[::1]`, `0x7f000001`) também respondem 400.
- Loopback em qualquer perfil (127.0.0.0/8, 0.0.0.0, `[::1]`, `[::]`, `[::ffff:127.0.0.1]`, `localhost`, `*.localhost`) e hosts numéricos fora de `a.b.c.d` são recusados. `InetAddress.getByName` só recebe literal IPv6 entre colchetes, então nenhum nome vai ao resolvedor.
- O WR-01 fechou: `linkpulse.base-url` sem esquema, com outro esquema, sem host, com credenciais, query ou fragmento derruba o contexto com `IllegalArgumentException`. A mensagem é fixa e não ecoa o valor (nenhuma mensagem da cadeia de causas contém a senha).
- `linkpulse.self-hosts` / `LINKPULSE_SELF_HOSTS` virou contrato de configuração para o compose (Phase 3), o Helm (Phase 4) e o ECS (Phase 5).
- O README não promete mais o fechamento do loop: a regra de URL lista exatamente o que é bloqueado, e "Riscos conhecidos" ganhou o bullet "Loop de redirect via DNS (mitigação parcial)". O bullet "Open redirect" (orientação do plan checker) passou a remeter às regras de host e à ressalva de DNS.

## Task Commits

1. **Task 1: Loop com ponto final fechado de ponta a ponta (tracer)**
   - `b05d90e` test(01-08): reproduz loop de redirect com ponto final no host (RED: esperado 400, recebido 201)
   - `5540855` fix(01-08): normaliza o host de destino e centraliza a regra do próprio encurtador no TargetHostPolicy (GREEN)
2. **Task 2: Loopback e IP numérico ofuscado recusados sem DNS (TDD)**
   - `a7ea96c` test(01-08): loopback e IP ofuscado como destino (RED: 11 + 5 vetores unitários e 7 ITs falhando)
   - `a1889cd` feat(01-08): recusa loopback e IP numérico ofuscado como destino, sem DNS (GREEN)
3. **Task 3: base-url validada, self-hosts por env, README fiel (TDD)**
   - `fa36a93` test(01-08): base-url inválida derruba a subida e self-hosts por env (RED)
   - `dd4fd09` fix(01-08): valida linkpulse.base-url na subida e expõe linkpulse.self-hosts (GREEN)
   - `884bb6f` docs(01-08): regra de URL e riscos fiéis ao código (mitigação parcial por DNS)

**Plan metadata:** commit `docs(01-08)` deste SUMMARY

## Files Created/Modified

- `app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java`: política do host de destino (`check`, `normalizeHost`, `isLoopback`, `isNonCanonicalNumeric`, `CANONICAL_IPV4` e as três mensagens).
- `app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java`: 37 casos unitários (4 grupos parametrizados e 3 `@Test`), sem Docker.
- `app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java`: 14 casos com `ApplicationContextRunner` (base-url inválida e válida, sem eco da senha, self-hosts por propriedade e por env).
- `app/src/main/java/dev/linkpulse/link/LinkService.java`: recebe o `TargetHostPolicy` no lugar de `LinkPulseProperties`; o `pointsToShortener` privado saiu.
- `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java`: componente `selfHosts` e construtor compacto com a validação da `baseUrl`.
- `app/src/main/java/dev/linkpulse/config/ApplicationConfig.java`: `@Bean TargetHostPolicy targetHostPolicy(LinkPulseProperties)`.
- `app/src/main/resources/application.yml`: só comentários (`LINKPULSE_SELF_HOSTS`, regra da base-url e de self-hosts).
- `app/src/test/java/dev/linkpulse/link/LinkApiIT.java`: `trailingDotOnTheShortenerHostIsRejectedAndNoLoopIsCreated`, `loopbackOrObfuscatedHostIsValidationErrorOnUrl` (7 URLs) e `publicIpv4LiteralIsAccepted`.
- `README.md`: bullet URL reescrito, linha `LINKPULSE_SELF_HOSTS` e regra da `LINKPULSE_BASE_URL` na tabela, bullet de mitigação parcial em "Riscos conhecidos".

## Decisions Made

- **Loopback recusado em qualquer perfil** (decisão de discricionariedade do plano). A verificação sugeria uma lista só de dev, mas em prod loopback aponta para a máquina de quem clica e nunca é destino público, e o compose da Phase 3 pode rodar prod com base `http://localhost:8080`.
- **Host numérico só na forma canônica.** O último rótulo "termina em número" (só dígitos ou `0x` + hex) e o host inteiro não casa com `CANONICAL_IPV4`: a resposta é 400 com `host numérico deve estar na forma a.b.c.d`. IPv4 público canônico (`93.184.215.14`) continua aceito.
- **Binding por env:** `LINKPULSE_SELF_HOSTS` (com underscore) faz binding no Spring Boot 4.1.1 (`environmentVariablesBindBaseUrlAndSelfHosts` verde já no RED). A contingência (`LINKPULSE_SELFHOSTS`) não foi aplicada.
- **Threat model:** o T-06-03 da 01-06-PLAN e a afirmação de fechamento do loop na 01-06-SUMMARY ficam substituídos por T-08-01 (mitigado) e T-08-06 (aceito, mitigação parcial por DNS). Os arquivos da 01-06 não foram editados.
- **Key links:** o `baseUrl().getHost()` da 01-06 foi substituído por `targetHostPolicy.check(request.url())` no `LinkService` e por `new TargetHostPolicy(properties.baseUrl(), properties.selfHosts())` no `ApplicationConfig`.
- **Ordem do `check`:** numérico não canônico, depois próprio encurtador, depois loopback. Assim, uma base `http://localhost.:8080` recusa `http://localhost:8080/x` com a mensagem do próprio encurtador, e não com a de loopback.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] As constantes das mensagens entraram no commit RED da Task 2**
- **Found during:** Task 2 (RED)
- **Issue:** o `TargetHostPolicyTest` referencia `TargetHostPolicy.LOOPBACK_MESSAGE` e `NUMERIC_HOST_MESSAGE`. Sem elas, o RED seria um erro de compilação (INVALID_RED), e não uma falha de asserção.
- **Fix:** o commit `test(01-08)` declara só as duas constantes `static final String`, sem comportamento. A falha do RED veio das asserções: nenhuma exceção nos 11 vetores de loopback e nos 5 numéricos (`check tdd-red-evidence` deu `RED_EVIDENCE_OK` para os dois grupos).
- **Files modified:** `app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java`
- **Verification:** RED com 16 falhas de asserção nos unitários e 7 nos ITs; GREEN com 37/37 unitários e 27/27 no `LinkApiIT`.
- **Committed in:** `a7ea96c`

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** só reorganiza onde as constantes nascem; o comportamento entrou inteiro no GREEN. Sem aumento de escopo.

## TDD Gate Compliance

- Task 1 (tracer): RED `b05d90e` (o IT falhou com "expected: 400 but was: 201") → GREEN `5540855` (tipo `fix`, conforme o plano).
- Task 2: RED `a7ea96c` → GREEN `a1889cd`. A evidência de RED foi validada por `gsd-tools check tdd-red-evidence` (`RED_EVIDENCE_OK` para `rejectsLoopbackDestinations` e `rejectsNonCanonicalNumericHosts`), a partir do XML do Surefire transcrito para TAP.
- Task 3: RED `fa36a93` → GREEN `dd4fd09` (tipo `fix`, conforme o plano). `RED_EVIDENCE_OK` para `contextFailsOnStartupWhenBaseUrlIsInvalid` e `startupFailureDoesNotEchoTheBaseUrl`. No RED, 4 dos 7 vetores inválidos falharam (ftp, credenciais, query e fragmento). Os outros 3 (`lnk.example.com`, `/caminho`, `mailto:a@b.com`, todos sem host) já derrubavam a subida pela defesa em profundidade que a Task 1 pôs no construtor do `TargetHostPolicy`, cuja mensagem também cita `linkpulse.base-url`. Isso era esperado depois da Task 1, e não é um teste errado.
- Não houve REFACTOR separado.

## Issues Encountered

- Na Task 1, o Checkstyle (`LineLength` de 100) recusou a linha de Javadoc do `LinkPulseProperties` que listava as variáveis de ambiente. A linha foi quebrada antes do commit do GREEN.
- O primeiro GREEN da Task 2 deixou de fora o literal `".localhost"` que o critério de aceite procura (ele usava `"." + LOCALHOST`). O código foi trocado pelo literal e reverificado antes do commit. O comportamento não mudou.
- No relatório do Failsafe, a linha "Tests run: 0 ... in LinkApiIT" vem do agrupamento da classe `@Nested`, e não é ausência de testes. O resumo do Failsafe mostra 27 testes no `LinkApiIT`, e o XML lista os cenários novos sem falha.

## Verification

- `./mvnw -B -ntp -Dtest='TargetHostPolicyTest,LinkPulsePropertiesTest,HttpUrlValidatorTest,AliasPolicyTest,CodeGeneratorTest' test`: 103 testes, BUILD SUCCESS, sem Docker.
- `./mvnw -B -ntp verify`: 103 unitários e 50 ITs (`LinkApiIT`, `RedirectIT`, `ConcurrencyIT`, `LiquibaseContextsIT`, `OpenApiIT`), Checkstyle sem violação, SpotBugs `BugInstance size is 0`, BUILD SUCCESS.
- Gate de texto do README: imprime `README-OK`. `grep -c '2026-12-31' README.md` continua `3` (WR-04 não foi tocado).
- `InetAddress.getByName(` aparece 1 vez fora de comentários; `getAllByName` aparece 0 vezes.

## User Setup Required

None - no external service configuration required. (Os ambientes que expõem o encurtador por outro hostname devem preencher `LINKPULSE_SELF_HOSTS` nas Phases 3 a 5.)

## Next Phase Readiness

- O gap da 01-VERIFICATION.md está coberto. Falta reverificar a fase com `/gsd-verify-work`, que deve repetir o probe do CR-01.
- Ficam fora deste plano, como estava previsto: WR-02, WR-03, WR-04 e IN-01 a IN-10.

## Self-Check: PASSED

- FOUND: `TargetHostPolicy.java`, `TargetHostPolicyTest.java`, `LinkPulsePropertiesTest.java`
- FOUND: commits `b05d90e`, `5540855`, `a7ea96c`, `a1889cd`, `fa36a93`, `dd4fd09`, `884bb6f`

---
*Phase: 01-funda-o-e-n-cleo-de-links*
*Completed: 2026-10-09*
