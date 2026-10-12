# 15 · Rate limit por IP

**Em uma frase:** o `POST /links` aceita até 20 criações por minuto por IP. A contagem fica no Valkey, num script Lua que conta e expira a chave de uma vez só. Acima do limite, a resposta é `429` com o header `Retry-After`; e o IP do cliente só é corrigido pelo `X-Forwarded-For` quando a requisição vem de um proxy confiável.

## O que você vai aprender

- O que é rate limit e o algoritmo de "janela fixa".
- Por que contar e expirar precisa ser atômico, e como um script Lua resolve isso.
- O que é um interceptor do Spring MVC.
- Como descobrir o IP real do cliente sem deixar ele mentir.
- Por que o rate limit também é fail-open.

## Antes de começar: conceitos

### Rate limit

**Rate limit** (limite de taxa) é limitar quantas vezes alguém pode fazer algo num intervalo de tempo. Protege contra abuso: um robô que cria milhares de links de spam por segundo, ou que tenta derrubar o banco.

### Janela fixa

O algoritmo mais simples é a **janela fixa**: o tempo é dividido em blocos iguais (de 1 minuto, por exemplo), e cada IP tem um contador por bloco.

**Analogia:** uma catraca que libera 20 entradas por minuto. No começo de cada minuto, o contador volta a zero.

```text
minuto 10:00 → contador do IP 1.2.3.4: 1, 2, ..., 20 ✓, 21 ✗ (429), 22 ✗ ...
minuto 10:01 → contador zera: 1 ✓ ...
```

O número do bloco é `floor(agora_em_ms / tamanho_da_janela_em_ms)` (a divisão arredondada para baixo). Todo mundo dentro do mesmo minuto cai no mesmo bloco.

### Atomicidade

Para contar, o código precisa fazer duas coisas no Valkey: **somar 1** (`INCR`) e, se a chave é nova, **dar um prazo de validade** (`PEXPIRE`), para ela sumir quando a janela acabar.

Se forem dois comandos separados, aparecem dois problemas:

- **Corrida:** duas requisições ao mesmo tempo podem se atrapalhar entre um comando e outro.
- **Chave eterna:** se a aplicação cair entre o `INCR` e o `PEXPIRE`, a chave fica sem prazo e o IP fica bloqueado para sempre.

**Atômico** quer dizer "tudo de uma vez, sem ninguém no meio". O Valkey executa um **script Lua** de forma atômica: enquanto o script roda, nenhum outro comando entra. Lua é uma linguagem pequena, embutida no Valkey e no Redis justamente para isso.

### Interceptor

Um **interceptor** do Spring MVC é um "porteiro" que roda **antes** do controller. Ele pode deixar a requisição seguir (`return true`) ou barrá-la (lançando uma exceção). É o lugar natural para regras que valem para um endpoint inteiro, como o rate limit.

### IP real e proxies

O IP que o servidor vê é o do **último** computador que falou com ele. Se há um **proxy** no caminho (um balanceador de carga, o Traefik do Kubernetes), o servidor vê o IP do proxy, e não o do cliente. Por isso os proxies acrescentam o header `X-Forwarded-For: <ip do cliente>`.

O problema: **qualquer um** pode mandar esse header. Um cliente que fala direto com o servidor pode escrever `X-Forwarded-For: 9.9.9.9` e fingir ser outra pessoa a cada requisição, escapando do limite. A regra segura: só acreditar no header quando a conexão vem de um proxy **confiável**.

## No Link Pulse

### Onde o limite vale

`app/src/main/java/dev/linkpulse/config/WebMvcConfig.java`

```java
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/links");
    }
```

O padrão `/links` é **exato**: não pega `/links/{code}/stats` nem o redirect `/{code}`. E o interceptor ainda confere o método:

`app/src/main/java/dev/linkpulse/ratelimit/RateLimitInterceptor.java`

```java
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        if (!enabled || !"POST".equals(request.getMethod())) {
            return true;
        }
        RateLimiter.Decision decision = rateLimiter.check(request.getRemoteAddr());
        if (!decision.allowed()) {
            throw RateLimitProblems.tooManyRequests(decision.retryAfterSeconds());
        }
        return true;
    }
```

- `preHandle` roda antes do controller.
- Só o `POST` conta. O redirect, o `HEAD` e o `/stats` **nunca** recebem `429`: limitar o redirect quebraria links legítimos muito acessados.
- O IP vem **só** de `request.getRemoteAddr()`. O código da aplicação nunca lê o `X-Forwarded-For` (veja "IP real" abaixo).
- Acima do limite, lança o erro `429`, que o `GlobalExceptionHandler` transforma em Problem Details (guia 05).

### O contador: `RateLimiter`

`app/src/main/java/dev/linkpulse/ratelimit/RateLimiter.java`

O script Lua:

```java
    private static final RedisScript<Long> FIXED_WINDOW = RedisScript.of(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 or redis.call('PTTL', KEYS[1]) < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class);
```

Linha a linha:

1. `INCR` soma 1 na chave (e cria a chave com 1, se ela não existe). O resultado é o número de criações deste IP nesta janela.
2. Se é a primeira (`current == 1`) **ou** se a chave está sem prazo (`PTTL < 0`), aplica o prazo (`PEXPIRE`) com o tamanho da janela, em milissegundos (`ARGV[1]`).
3. Devolve a contagem.

A segunda condição (`PTTL < 0`) é uma rede de segurança: garante que nenhuma chave fique eterna.

A checagem:

```java
    public Decision check(String clientIp) {
        if (!availability.allowRequest()) {
            errors.increment();
            return new Decision(true, 0);
        }
        long now = clock.millis();
        String key = KEY_PREFIX + ipHasher.hash(clientIp, clock.instant()) + ":"
                + windowIndex(now, windowMillis);
        Long count;
        try {
            count = redis.execute(FIXED_WINDOW, List.of(key), Long.toString(windowMillis));
        } catch (RuntimeException e) {
            // Sem log: a exceção do Lettuce pode citar a chave. A queda já conta no gate.
            availability.recordFailure();
            errors.increment();
            return new Decision(true, 0);
        }
        if (count != null && count > limit) {
            rejected.increment();
            return new Decision(false, retryAfterSeconds(now, windowMillis));
        }
        allowed.increment();
        return new Decision(true, 0);
    }
```

- **A chave** é `lp:rl:{hmac do ip}:{número da janela}`. O IP **nunca** vai em claro para o Valkey: usa o mesmo HMAC dos cliques (guia 13). Como o número da janela está na chave, cada minuto tem uma chave nova, e a anterior expira sozinha.
- **`redis.execute(FIXED_WINDOW, ...)`** manda o script ao Valkey.
- **Acima do limite**, calcula o `Retry-After`: os segundos que faltam até a próxima janela, arredondados para cima e nunca menos de 1.
- **`Decision`** é um record com a resposta: pode passar? Se não, em quantos segundos?

### Fail-open, por decisão consciente

Repare nos dois `return new Decision(true, 0)` dos casos de erro: com o Valkey fora (disjuntor aberto, guia 12, ou erro no comando), **a criação passa sem limite**. O javadoc chama isso de "decisão consciente": a disponibilidade da criação de links vale mais que o limite. O risco fica:

- **visível**, em `linkpulse_ratelimit_requests_total{result="error"}` e em `linkpulse_valkey_unavailable_total`;
- **documentado**, na seção "Riscos conhecidos" do README.

O `RateLimitIT` mediu: com o Valkey pausado, o `POST` respondeu `201` em 334 ms (resumo do plano 02-09).

### A resposta 429

`app/src/main/java/dev/linkpulse/ratelimit/RateLimitProblems.java`

```java
    public static ErrorResponseException tooManyRequests(long retryAfterSeconds) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Limite de criação de links por IP atingido. Tente de novo em "
                        + retryAfterSeconds + " s.");
        problem.setType(URI.create("/problems/rate-limited"));
        problem.setTitle("Muitas requisições");
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);
        ErrorResponseException ex =
                new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS, problem, null);
        ex.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return ex;
    }
```

`Retry-After` é um header HTTP padrão que diz ao cliente "tente de novo daqui a N segundos". O mesmo número vai no corpo (`retryAfterSeconds`). O `detail` nunca leva o IP, a chave nem o limite configurado.

### IP real: quem decide é o Tomcat

O código só lê `getRemoteAddr()`. Quem decide se esse endereço deve ser trocado pelo do `X-Forwarded-For` é uma peça do Tomcat, o `RemoteIpValve`, configurada no `application.yml` (guia 02):

```yaml
server:
  # ...
  forward-headers-strategy: native
  tomcat:
    remoteip:
      # ...
      internal-proxies: ""
```

- `forward-headers-strategy: native` liga o `RemoteIpValve`.
- `internal-proxies` é a lista de proxies confiáveis. **Vazia** = nenhum proxy é confiável = o `X-Forwarded-For` é sempre ignorado, e o IP vem da conexão.

Por que vazio de propósito? O comentário do `application.yml` explica: o padrão do Boot confiaria em toda a faixa de rede privada (inclusive a rede interna do Docker), e "qualquer cliente burlaria o rate limit com um header forjado". Quando houver um proxy de verdade (o Traefik do Kubernetes, na Phase 4), a faixa de IPs dele entra na lista pela variável `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`.

O `TrustedProxyRateLimitIT` prova os dois lados: um `X-Forwarded-For` forjado por quem não é proxy é ignorado; vindo de um proxy listado, o IP real do cliente é usado.

### Configuração

Todas com valor padrão no `LinkPulseProperties` (guia 02), sem precisar aparecer no `application.yml`:

| Propriedade | Variável de ambiente | Padrão |
|-------------|----------------------|--------|
| `linkpulse.ratelimit.enabled` | `LINKPULSE_RATELIMIT_ENABLED` | `true` |
| `linkpulse.ratelimit.limit` | `LINKPULSE_RATELIMIT_LIMIT` | `20` |
| `linkpulse.ratelimit.window` | `LINKPULSE_RATELIMIT_WINDOW` | `1m` |

### Métrica

`linkpulse_ratelimit_requests_total{result="allowed"|"rejected"|"error"}` conta cada decisão. Nenhuma tag leva o IP (guia 16).

## Por que assim?

- **Script Lua em vez de biblioteca:** a pesquisa de stack (`.claude/CLAUDE.md`) recomenda um script próprio de 10 a 15 linhas, sem dependência nova, testável com Testcontainers, e que mostra domínio de atomicidade no Redis. Bibliotecas como o Bucket4j só valeriam para algoritmos mais sofisticados (*token bucket*).
- **Janela fixa:** simples e suficiente para "não deixar criar milhares de links". A desvantagem conhecida: alguém pode fazer 20 criações no fim de um minuto e mais 20 no começo do seguinte.
- **Só no `POST /links`:** criar link é a operação que pode ser abusada para spam; o redirect precisa atender qualquer volume.
- **Fail-open:** é melhor ficar alguns minutos sem limite do que deixar ninguém criar links enquanto o Valkey está fora.
- **`internal-proxies` vazio:** seguro por padrão; confiar num proxy é uma decisão explícita de cada ambiente.

## Experimente

Com a API rodando, no Git Bash:

```bash
for i in $(seq 1 22); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/links \
    -H 'Content-Type: application/json' -d "{\"url\":\"https://example.com/$i\"}"
done
```

1. Quantos `201` e quantos `429` aparecem? (Se o minuto virar no meio, o contador zera.)
2. Repita a última requisição com `curl -i` e leia o header `Retry-After` e o corpo.
3. No `valkey-cli`, rode `KEYS lp:rl:*` e `PTTL` numa das chaves. O IP aparece na chave?
4. Mande `-H 'X-Forwarded-For: 9.9.9.9'` nas requisições e confirme que o limite continua valendo.

## Perguntas para fixar

1. Por que o `INCR` e o `PEXPIRE` ficam juntos num script Lua?
2. Para que serve a condição `PTTL < 0` no script?
3. Por que o rate limit não vale para o redirect?
4. O que acontece com o limite se o Valkey cair, e onde isso aparece?
5. Por que `internal-proxies` é vazio por padrão?

<details>
<summary>Respostas</summary>

1. Para serem atômicos: sem corrida entre requisições simultâneas e sem chave eterna se a aplicação cair entre os dois comandos.
2. É uma rede de segurança: se uma chave ficou sem prazo por qualquer motivo, ele é reaplicado.
3. Porque o redirect precisa atender links legítimos muito acessados; o abuso que importa é a criação em massa de links.
4. O limite é desligado (fail-open): a criação passa. Aparece em `linkpulse_ratelimit_requests_total{result="error"}` e em `linkpulse_valkey_unavailable_total`.
5. Porque, se o servidor confiasse no `X-Forwarded-For` de qualquer um, um cliente poderia forjar o header e fingir um IP novo a cada requisição.

</details>

---

[← Anterior: 14 · Estatísticas](14-estatisticas-no-mongodb.md) · [Índice](README.md) · [Próximo: 16 · Métricas →](16-metricas-com-micrometer.md)
