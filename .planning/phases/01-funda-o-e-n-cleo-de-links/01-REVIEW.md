---
phase: 01-funda-o-e-n-cleo-de-links
reviewed: 2026-10-09T22:54:30Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - README.md
  - app/src/main/java/dev/linkpulse/config/ApplicationConfig.java
  - app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java
  - app/src/main/java/dev/linkpulse/link/LinkService.java
  - app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java
  - app/src/main/resources/application.yml
  - app/src/test/java/dev/linkpulse/config/LinkPulsePropertiesTest.java
  - app/src/test/java/dev/linkpulse/link/LinkApiIT.java
  - app/src/test/java/dev/linkpulse/link/TargetHostPolicyTest.java
findings:
  critical: 0
  warning: 3
  info: 6
  total: 9
status: issues_found
---

# Phase 1: Code Review Report (incremental, plano 01-08)

**Reviewed:** 2026-10-09T22:54:30Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Narrative Findings (AI reviewer)

## Summary

Esta é uma revisão incremental do plano de fechamento de gaps 01-08 (diff `8fed18a..HEAD`). O plano extrai as regras de host de destino para `TargetHostPolicy`, valida `linkpulse.base-url` na subida, cria `linkpulse.self-hosts` e reescreve o README. Testei os casos de borda com o `java.net.URI` e o `InetAddress` do JDK 21.0.10, os mesmos que a aplicação usa.

**Status dos achados anteriores:**

- **CR-01: resolvido, com ressalvas.** Todos os vetores citados antes agora são recusados: `localhost.`, `127.0.0.1`, `[::1]`, `0x7f000001`, `2130706433` e `0177.0.0.1`. O caso `127.0.0.1.` (ponto final em IP) chega com `getHost() == null` e cai no `@HttpUrl`. A limitação de DNS ficou documentada como "mitigação parcial" no README (linhas 136 e 187), que era a alternativa aceita no achado original. Sobraram dois desvios que não dependem de DNS: literal IPv6 com zone ID passa pela regra de loopback (WR-01), e um IP do encurtador escrito em outra forma passa pela regra de "próprio encurtador" (WR-02).
- **WR-01: resolvido.** `LinkPulseProperties` derruba a subida quando a `base-url` não é http(s) absoluta com host, ou quando tem credenciais, query ou fragmento, e os testes cobrem isso. A nova `self-hosts` herdou o mesmo problema de configuração errada que desliga a proteção em silêncio (WR-03).
- Os outros achados anteriores (WR-02 a WR-04 e IN-01 a IN-10) estão fora do escopo do 01-08. Em dois deles, os arquivos revisados mostram que o problema continua: IN-06 (`cause.getCause() == cause`, código morto em `LinkService.java:117-119`) e WR-04 (exemplo `2026-12-31` em `LinkController.java:56`).

Não achei nenhum problema bloqueante. A classe nova é pequena, bem documentada e testada, e a regra "termina em número" segue de fato o WHATWG URL Standard.

## Warnings

### WR-01: Um literal IPv6 que o JDK não interpreta é aceito (fail-open), então `[::1%25lo]` passa pela regra de loopback

**File:** `app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java:132-140` (o teste fixa esse comportamento em `TargetHostPolicyTest.java:85`)
**Issue:** `isLoopback` devolve `false` quando `InetAddress.getByName` lança `UnknownHostException`. Para literais com zone ID (RFC 6874), o `URI.getHost()` devolve o texto cru, com `%25`. O JDK então lê o escopo como `25lo`, uma interface que não existe em nenhuma máquina. Resultado confirmado:

```
http://[::1%25lo]/x  -> host=[::1%25lo]  inetErr=UnknownHostException: no such interface 25lo  -> ACEITO
http://[::1%251]/x   -> host=[::1%251]   loop=true                                            -> recusado
```

O `@HttpUrl` também aceita, porque o host não é nulo e não há userinfo. Assim, `{"url":"http://[::1%25lo]:8080/loop-b","alias":"loop-b"}` cria o link. Navegadores rejeitam zone ID, mas clientes que seguem redirect e decodificam `%25` (o `curl -L`, por exemplo) vão para `::1`. Em dev, isso é o próprio encurtador, o que gera um loop. Em prod, o destino é a máquina de quem clica, e o README (linha 133) promete "não pode ser loopback, em qualquer perfil". Na Phase 2, cada salto vira um clique registrado. O problema de fundo é a política falhar aberta: qualquer literal entre colchetes que o JDK não entende passa como destino válido.
**Fix:** falhar fechado. Destino público nunca precisa de zone ID, então:

```java
if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
    if (normalizedHost.indexOf('%') >= 0) {
        return true; // zone ID: só faz sentido em link-local/loopback; nunca é destino público
    }
    try {
        InetAddress address = InetAddress.getByName(normalizedHost);
        return address.isLoopbackAddress() || address.isAnyLocalAddress();
    } catch (UnknownHostException e) {
        return true; // literal que o JDK não entende: recusar em vez de aceitar
    }
}
```

O melhor é criar uma regra própria ("literal IPv6 com zone ID não é aceito"), com mensagem dedicada. Troque o caso `http://[fe80::1%25en0]/x` de `acceptsOtherDestinations` para uma lista de recusados e acrescente `http://[::1%25lo]/x`.

### WR-02: A regra de "próprio encurtador" compara texto, e um IP do encurtador escrito em outra forma passa e gera loop

**File:** `app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java:162` (o conjunto é montado em `:71-83`)
**Issue:** `selfHosts.contains(host)` compara strings depois de só passar para minúsculas e tirar o ponto final. Quando o host da `base-url` ou de um item de `self-hosts` é um IP literal (cenário plausível em kind com NodePort, `http://172.18.0.2:30080`, ou em ECS com IP público), estas formas do mesmo endereço passam. Conferi com o JDK:

```
[::ffff:203.0.113.10]  -> InetAddress /203.0.113.10   (IPv4-mapped do IPv4 do encurtador)
[2001:DB8:0::1]        -> InetAddress /2001:db8::1     (mesma IPv6, outra grafia)
```

As duas não são loopback, então nenhuma regra recusa. O navegador aceita `http://[::ffff:203.0.113.10]/x`, normaliza para `[::ffff:cb00:710a]` e conecta no IPv4 pela pilha dual-stack. Com um alias, o loop sai num único POST, sem DNS. Esse caso não está coberto pela ressalva de DNS do README.
**Fix:** na construção, guardar à parte os endereços dos hosts que são IP literal (`InetAddress` dos itens canônicos `a.b.c.d` e dos itens entre colchetes, convertendo IPv4-mapped para IPv4). Em `check`, se o destino é literal entre colchetes ou IPv4 canônico, compare o `InetAddress`, não a string:

```java
private final Set<InetAddress> selfAddresses; // montado no construtor, só a partir de literais
...
InetAddress literal = parseLiteral(host); // null para nomes; nunca resolve DNS
if (selfHosts.contains(host) || (literal != null && selfAddresses.contains(literal))) {
    throw LinkProblems.invalidField("url", SELF_MESSAGE);
}
```

Em `Inet4Address`/`Inet6Address`, o `equals` compara bytes, e o JDK já devolve `Inet4Address` para IPv4-mapped. Acrescente casos de teste com base `http://203.0.113.10` e destinos `[::ffff:203.0.113.10]` e `[::ffff:cb00:710a]`.

### WR-03: Um item mal escrito em `linkpulse.self-hosts` nunca casa, e a proteção some em silêncio (o mesmo defeito do WR-01 anterior, agora na propriedade nova)

**File:** `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java:55-61`, `app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java:76-82`
**Issue:** os itens só passam por `strip`, minúsculas e remoção do ponto final. Estes erros comuns de configuração (em compose, Helm ou ECS) são aceitos e nunca casam com nada, porque o `check` compara contra `URI.getHost()`, que não tem esquema, porta nem path e que põe colchetes no IPv6:

- `LINKPULSE_SELF_HOSTS=https://lnk.example.com` (esquema)
- `lb.example.com:443` (porta)
- `lb.example.com/` (barra)
- `*.example.com` (curinga, que alguém pode tentar depois de ler a nota sobre DNS curinga no README)
- `2001:db8::1` (IPv6 sem colchetes)

A subida segue normal, e o operador acredita que o hostname do load balancer está protegido. É exatamente o defeito que o 01-08 corrigiu para a `base-url` ("configuração errada desliga a checagem em silêncio").
**Fix:** validar cada item no construtor compacto e derrubar a subida, sem ecoar o valor:

```java
private static final String SELF_HOSTS_ERROR =
        "linkpulse.self-hosts deve conter só hosts, sem esquema, porta, path ou curinga";

private static void requireBareHost(String entry) {
    try {
        URI probe = new URI("http://" + entry.strip() + "/");
        if (probe.getHost() == null || probe.getPort() != -1 || !"/".equals(probe.getRawPath())
                || probe.getUserInfo() != null) {
            throw new IllegalArgumentException(SELF_HOSTS_ERROR);
        }
    } catch (URISyntaxException e) {
        throw new IllegalArgumentException(SELF_HOSTS_ERROR);
    }
}
```

Itens em branco podem continuar sendo descartados, como hoje. `*.example.com`, `https://...`, `host:443`, `host/` e IPv6 sem colchetes caem na validação: o host vem nulo, a porta é diferente de -1 ou o path não é `/`. Acrescente um caso parametrizado ao `LinkPulsePropertiesTest`, no mesmo formato do `contextFailsOnStartupWhenBaseUrlIsInvalid`.

## Info

### IN-01: A promessa "a mensagem nunca ecoa o valor" da `base-url` só vale quando a URL é sintaticamente válida

**File:** `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java:44`
**Issue:** a mensagem do construtor compacto é fixa. Mas, quando a string nem vira `URI` (por exemplo, `http://user:senha@lnk.example.com/a b`, com espaço), a falha acontece na conversão do binder, antes do construtor. Nesse caminho, o `BindFailureAnalyzer` do Boot 4.1.1 imprime `Property: linkpulse.base-url` / `Value: "..."` (confirmei a string no jar). O teste `startupFailureDoesNotEchoTheBaseUrl` cobre só o caso parseável.
**Fix:** tirar o "nunca" do Javadoc ("o construtor não ecoa o valor") ou fazer o binding como `String` e converter para `URI` dentro do construtor, tratando a `URISyntaxException` com mensagem fixa.

### IN-02: A `base-url` padrão `http://localhost:8080` vale também em prod, então esquecer `LINKPULSE_BASE_URL` passa sem aviso

**File:** `app/src/main/resources/application.yml:30`
**Issue:** a validação nova aceita o default. Em prod, sem a env, a `shortUrl` e o `Location` saem com `http://localhost:8080/...`, e a regra de "próprio encurtador" protege só `localhost`, que já era recusado por loopback. O `application-prod.yml` não sobrescreve esse valor.
**Fix:** no `application-prod.yml`, usar `linkpulse.base-url: ${LINKPULSE_BASE_URL}` sem default, para a subida falhar quando a env não existe.

### IN-03: O IT de loopback e host ofuscado não confere qual regra recusou

**File:** `app/src/test/java/dev/linkpulse/link/LinkApiIT.java:261-275`
**Issue:** `assertValidationErrorOn(result, "url")` passa com qualquer 400 no campo `url`. Se a ordem das regras mudar, ou se o `@HttpUrl` passar a recusar algum desses casos antes, o teste continua verde sem exercitar o `TargetHostPolicy` no fluxo HTTP.
**Fix:** parametrizar com a mensagem esperada (`@CsvSource`) e conferir `$.errors[0].message`, como já faz `trailingDotOnTheShortenerHostIsRejectedAndNoLoopIsCreated`.

### IN-04: O `check` deixa passar host nulo e depende do `@HttpUrl` da borda HTTP

**File:** `app/src/main/java/dev/linkpulse/link/TargetHostPolicy.java:155-158`
**Issue:** `http://127.0.0.1./x` e `http://127.1/x` chegam com `getHost() == null` (confirmado) e só são barrados pelo `@HttpUrl`. Quem chama `LinkService.create` direto (como o próprio `LinkApiIT` faz na linha 176, ou um futuro consumer ou job) pula essa barreira. Com `url` nula ou mal formada, `URI.create` lança NPE ou `IllegalArgumentException`, o que vira 500.
**Fix:** falhar fechado também aqui. Se `host == null`, lance `LinkProblems.invalidField("url", "deve ser uma URL http ou https com host")`, e capture a `IllegalArgumentException` do `URI.create`.

### IN-05: Javadoc desatualizado: diz "o próprio encurtador", mas agora são três regras

**File:** `app/src/main/java/dev/linkpulse/link/LinkService.java:40-41,64-65`, `app/src/main/java/dev/linkpulse/config/ApplicationConfig.java:51-53`
**Issue:** os comentários ainda descrevem só a regra de "próprio encurtador". Loopback e host numérico não canônico não aparecem.
**Fix:** citar as três regras ou só apontar para o Javadoc de `TargetHostPolicy`.

### IN-06: Uma `base-url` com esquema ou host em maiúsculas é aceita e repetida assim na `shortUrl`

**File:** `app/src/main/java/dev/linkpulse/config/LinkPulseProperties.java:63-72` (o teste aceita isso de propósito em `LinkPulsePropertiesTest.java:79`)
**Issue:** `HTTPS://LNK.EXAMPLE.COM/` passa na validação, e o `LinkController` monta `HTTPS://LNK.EXAMPLE.COM/<code>` com `UriComponentsBuilder.fromUri`. A URL funciona, mas a resposta fica feia e inconsistente com os exemplos do README.
**Fix:** no construtor compacto, normalizar o esquema e o host para minúsculas, reconstruindo a `URI`.

---

_Reviewed: 2026-10-09T22:54:30Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
