# 04 · Código curto Base62

**Em uma frase:** o código curto gerado é `base62(pad7((id * M) mod 62^7))`: o ID da sequência é embaralhado por uma multiplicação modular reversível e escrito com 7 caracteres alfanuméricos. Isso evita colisões e esconde a ordem dos IDs, mas é ofuscação, não segurança.

## Conceitos

### Base62

Base62 é escrever um número usando 62 símbolos: os dígitos `0-9`, as maiúsculas `A-Z` e as minúsculas `a-z`. Funciona como a base 10, só que cada posição vale 62 vezes a anterior. Esses 62 caracteres não precisam de escape numa URL, então o código fica limpo no link.

Com 7 posições, cabem `62^7` = 3.521.614.606.208 valores distintos (o mesmo número aparece como `SPACE` no código). "pad7" significa completar com o primeiro símbolo do alfabeto à esquerda, para que todo código tenha exatamente 7 caracteres.

### Permutação multiplicativa

Se você só convertesse o ID para Base62, IDs vizinhos dariam códigos vizinhos e qualquer um adivinharia o próximo link. A ideia é embaralhar o ID antes:

```text
x = (id * M) mod N        com N = 62^7
```

Se `M` e `N` forem **coprimos** (o único divisor comum é 1), essa conta é uma **bijeção** em `[0, N)`: cada ID vai para um `x` diferente, e todo `x` vem de algum ID. Além disso, existe o **inverso modular** `M⁻¹`, com `M * M⁻¹ ≡ 1 (mod N)`, que desfaz a conta: `id = (x * M⁻¹) mod N`.

### Modelo de brinquedo (não são valores do projeto)

Para enxergar a ideia, use `N = 10` e `M = 3` (3 e 10 são coprimos):

| id | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|----|---|---|---|---|---|---|---|---|---|---|
| (id × 3) mod 10 | 0 | 3 | 6 | 9 | 2 | 5 | 8 | 1 | 4 | 7 |

- Todos os resultados de 0 a 9 aparecem uma vez: é uma bijeção.
- O inverso de 3 mod 10 é 7 (3 × 7 = 21 ≡ 1). Desfazendo o id 1: 3 × 7 = 21 → 21 mod 10 = 1.
- Com `M = 4` (que tem o fator 2 em comum com 10), id 0 e id 5 dariam 0: colisão. Por isso o multiplicador precisa ser coprimo do módulo.
- E o problema de segurança: quem vê os códigos de id 1 (3) e id 2 (6) calcula 6 − 3 = 3 = `M`. A partir daí, gera todos.

## No Link Pulse

### O gerador

`app/src/main/java/dev/linkpulse/link/CodeGenerator.java`

```java
/**
 * Gera o código curto de 7 caracteres a partir do ID da sequência (D-01, D-02).
 *
 * <p>O código é {@code base62(pad7((id * M) mod 62^7))}. Como {@code M} é coprimo de 62^7, a
 * multiplicação é uma bijeção em {@code [0, 62^7)}: IDs distintos sempre dão códigos distintos, e
 * {@link #decode(String)} desfaz a conta com o inverso modular de {@code M}. O efeito prático é
 * que IDs consecutivos viram códigos sem relação visível entre si.
 *
 * <p>A multiplicação usa {@link BigInteger}: com {@code M} na casa de 2e12, {@code id * M} em
 * {@code long} estoura a partir de {@code id} perto de 4,2 milhões, sem exceção, e quebraria a
 * bijeção.
 *
 * <p><strong>Isto é ofuscação, não segurança.</strong> Quem conhece dois códigos de IDs
 * consecutivos recupera {@code M} (a diferença entre eles é {@code M mod 62^7}) e passa a
 * enumerar todos os links. O código curto não deve ser tratado como segredo nem como controle de
 * acesso.
 */
public final class CodeGenerator {

    /** Comprimento fixo de todo código gerado. */
    public static final int LENGTH = 7;
```

O `encode`:

```java
    public String encode(long id) {
        if (id < 0 || id >= SPACE) {
            throw new IllegalArgumentException("id fora do espaço de 7 caracteres: " + id);
        }
        long x = BigInteger.valueOf(id).multiply(multiplier).mod(MODULUS).longValueExact();
        char[] out = new char[LENGTH];
        for (int i = LENGTH - 1; i >= 0; i--) {
            out[i] = alphabet[(int) (x % BASE)];
            x /= BASE;
        }
        return new String(out);
    }
```

1. Recusa IDs fora de `[0, 62^7)`: não há como representá-los em 7 caracteres.
2. Multiplica e reduz com `BigInteger` (o `long` estouraria em silêncio, conforme o javadoc).
3. Preenche o array da direita para a esquerda com os restos da divisão por 62. Posições que sobram ficam com `alphabet[0]`, que é o "pad7".

`decode` faz o caminho de volta: lê os 7 caracteres como número em base 62 e multiplica pelo `inverse`. Ele existe para testes e para provar a bijeção; o fluxo da API usa só `encode`.

### Validação na subida

O construtor valida o alfabeto (62 caracteres distintos de `[0-9A-Za-z]`) e o multiplicador:

```java
        if (multiplier <= 0 || multiplier >= SPACE) {
            throw new IllegalArgumentException(
                    "multiplicador deve estar em (0, 62^7), recebido: " + multiplier);
        }
        this.multiplier = BigInteger.valueOf(multiplier);
        try {
            this.inverse = this.multiplier.modInverse(MODULUS);
        } catch (ArithmeticException e) {
            // Sem encadear a causa: "BigInteger not invertible" não acrescenta nada, e a
            // mensagem em pt-BR fica como causa raiz da falha de subida do contexto (D-03).
            throw new IllegalArgumentException("multiplicador deve ser coprimo de 62^7");
        }
```

`modInverse` só existe quando `M` é coprimo do módulo. Se não for, o `BigInteger` lança `ArithmeticException`, que vira uma mensagem clara em pt-BR. Como o `CodeGenerator` é criado como bean em `app/src/main/java/dev/linkpulse/config/ApplicationConfig.java`, um multiplicador ruim derruba a aplicação na subida (decisão 01-03 no `.planning/STATE.md`).

### Configuração

`app/src/main/resources/application.yml`

```yaml
  code:
    # Alfabeto e multiplicador definem todos os códigos publicados: trocar quebra links (D-01).
    # O multiplicador precisa ser coprimo de 62^7 (validado na subida). É ofuscação, não segurança.
    alphabet: 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz
    multiplier: 2176477521915
```

Valores reais tirados do teste `knownVectors` (não calculados à mão): `encode(1)` = `cJinsNv` e `encode(2)` = `EdRbklq`. São códigos sem semelhança visível entre si, embora venham de IDs vizinhos.

### Alias e código gerado nunca se cruzam

O alias exige pelo menos um `-` ou `_` (regex `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$` em `AliasPolicy`), e o código gerado é só alfanumérico. O javadoc de `AliasPolicy` conclui: "os dois espaços nunca se cruzam, então um alias nunca ocupa um código que a sequência ainda vai gerar." O teste `generatedCodesNeverMatchTheAliasRegex` em `AliasPolicyTest` confere isso.

### Testes

- `app/src/test/java/dev/linkpulse/link/CodeGeneratorTest.java`: vetores conhecidos, ida e volta (`decode(encode(id)) == id`) em faixas baixas e altas, unicidade em amostras, formato de 7 caracteres, prefixos diferentes entre IDs consecutivos, rejeição de multiplicadores inválidos (62, 31, 2, 0, -1 e `SPACE`) e falha de subida com a palavra "coprimo".
- `app/src/test/java/dev/linkpulse/link/ConcurrencyIT.java`: 8 threads criam 50 links cada contra o Postgres real e o teste exige 400 códigos distintos.

## Por que assim?

- **Sem colisão nem retry:** a sequência garante IDs únicos e a bijeção garante códigos únicos. A seção "O Que NÃO Usar" do `.claude/CLAUDE.md` descarta gerar o código com hash ou aleatório por causa de "colisão e retry".
- **Não enumerável a olho nu:** o critério de sucesso 1 da Phase 1 no ROADMAP pede códigos "não consecutivos nem enumeráveis".
- **Mas é ofuscação, não segurança.** O README diz: "Quem conhece dois códigos consecutivos recupera `M` (`c2 − c1 ≡ M mod 62^7`) e passa a enumerar os links. Não use o código curto como segredo: um link curto é público para quem o tiver." Não use o código como token, senha ou controle de acesso.
- **Contrato one-way:** o `.planning/STATE.md` (Blockers/Concerns) registra que `linkpulse.code.alphabet` e `multiplier` "são contrato one-way: mudar depois altera os códigos já emitidos". Escolha uma vez, antes de publicar.

## Experimente

1. Refaça o modelo de brinquedo com `N = 12` e `M = 5`. Ache o inverso de 5 mod 12 e confira que todo resultado aparece uma vez.
2. Repita com `M = 4` e encontre a primeira colisão.
3. Rode só os testes do gerador, sem Docker, a partir de `app/` (comando explicado no guia 06):

```bash
cd app && ./mvnw -B -ntp -Dtest=CodeGeneratorTest test
```

4. Leia `contextFailsOnStartupWhenMultiplierIsNotCoprime` em `CodeGeneratorTest` e explique por que `62` é um multiplicador inválido.

## Perguntas para fixar

1. Por que `M` precisa ser coprimo de `62^7`? O que `modInverse` tem a ver com isso?
2. Por que o código usa `BigInteger` em vez de multiplicar dois `long`?
3. Como alguém que conhece dois códigos consecutivos descobre `M`?
4. Por que um alias nunca colide com um código que a sequência ainda vai gerar?
5. O que acontece com os links já publicados se alguém trocar `LINKPULSE_CODE_MULTIPLIER`?

---

[← Anterior: 03 · Postgres, JPA e Liquibase](03-postgres-jpa-e-liquibase.md) · [Índice](README.md) · [Próximo: 05 · Validação e erros →](05-validacao-e-erros.md)
