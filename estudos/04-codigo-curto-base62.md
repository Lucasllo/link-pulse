# 04 · Código curto Base62

**Em uma frase:** o código curto gerado é `base62(pad7((id * M) mod 62^7))`. O número do link é embaralhado por uma multiplicação que pode ser desfeita e depois escrito com 7 letras e dígitos. Isso garante que dois links nunca tenham o mesmo código e esconde a ordem dos números, mas é ofuscação, não segurança.

## O que você vai aprender

- O que é uma base numérica e por que "Base62".
- O que é "resto da divisão" (`mod`) e por que ele embaralha números.
- Por que o multiplicador precisa ser "coprimo" e o que é um inverso modular.
- Por que o código curto **não** deve ser tratado como senha.

## Antes de começar: conceitos

### Bases numéricas

No dia a dia usamos a **base 10**: dez símbolos (`0` a `9`), e cada posição vale 10 vezes a posição à direita. `253` é `2×100 + 5×10 + 3×1`.

Nada obriga a usar dez símbolos. O computador usa a base 2 (só `0` e `1`); cores em HTML usam a base 16 (`0-9` e `a-f`). A **Base62** usa 62 símbolos:

```text
0123456789 ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz
  10 dígitos      26 maiúsculas             26 minúsculas
```

Cada posição vale 62 vezes a da direita. Com mais símbolos, cabem números maiores em menos caracteres. E esses 62 caracteres podem ir numa URL sem nenhum tratamento especial.

Quantos números cabem em 7 caracteres? `62^7` (62 multiplicado por ele mesmo 7 vezes) = **3.521.614.606.208**, mais de 3,5 trilhões. No código, esse número se chama `SPACE`.

**"pad7"** significa completar com o primeiro símbolo (`0`) à esquerda até dar 7 caracteres, como escrever `0000042` em vez de `42`. Assim todo código tem exatamente 7 caracteres.

### Resto da divisão (`mod`)

`a mod n` é o **resto** da divisão de `a` por `n`. Exemplos: `17 mod 5 = 2` (17 = 3×5 + 2); `10 mod 10 = 0`; `23 mod 10 = 3`.

Pense num relógio: depois das 12 vem a 1 de novo. As horas são "mod 12". O resultado de `mod n` sempre fica entre `0` e `n − 1`.

### O problema de usar o número direto

Se o código fosse só o número convertido para Base62, o link 1000 teria o código `00000G8` e o 1001 teria `00000G9`. Qualquer pessoa adivinharia o próximo link e poderia percorrer todos. A ideia é **embaralhar** o número antes:

```text
x = (id * M) mod N        com N = 62^7
```

### Coprimos, bijeção e inverso (com um brinquedo)

Para enxergar a ideia, troque os valores reais por números pequenos: `N = 10` e `M = 3`.

| id | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|----|---|---|---|---|---|---|---|---|---|---|
| (id × 3) mod 10 | 0 | 3 | 6 | 9 | 2 | 5 | 8 | 1 | 4 | 7 |

Observe:

- **Todos os resultados de 0 a 9 aparecem exatamente uma vez.** Ids diferentes nunca dão o mesmo resultado. Em matemática, isso se chama **bijeção**: uma troca de lugares, sem perder nem repetir nada.
- **Dá para desfazer.** Existe um número que "desmultiplica": o **inverso modular** de 3 mod 10 é 7, porque `3 × 7 = 21` e `21 mod 10 = 1`. Para voltar do resultado ao id, multiplique por 7: o resultado 3 vira `3 × 7 = 21 → 1`, que é o id original.
- **Isso só funciona porque 3 e 10 são coprimos**, ou seja, não têm nenhum divisor em comum além de 1. Tente `M = 4` (4 e 10 são ambos divisíveis por 2): o id 0 dá 0 e o id 5 dá `20 mod 10 = 0`. Dois ids, o mesmo código: **colisão**.
- **E a fraqueza:** quem vê os resultados do id 1 (3) e do id 2 (6) calcula `6 − 3 = 3`, que é o `M`. A partir daí, consegue gerar todos.

No projeto, `N = 62^7` e `M` é um número enorme, mas a lógica é exatamente esta.

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

O método `encode`, que transforma o número em código:

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

Linha a linha:

1. **`if (id < 0 || id >= SPACE)`**: recusa números que não cabem em 7 caracteres.
2. **`BigInteger...multiply(multiplier).mod(MODULUS)`**: calcula `(id × M) mod 62^7`. Por que `BigInteger`? O tipo `long` do Java guarda números até cerca de 9,2 × 10^18. Com `M` na casa de 2 × 10^12, a multiplicação passa desse limite a partir de id ≈ 4,2 milhões. E o `long` não avisa: ele "dá a volta" em silêncio e devolve um número errado, o que quebraria a bijeção. `BigInteger` não tem limite.
3. **`char[] out = new char[LENGTH]`**: reserva 7 posições para as letras.
4. **O `for` de trás para a frente**: é a conversão para Base62, igual a converter para qualquer base. `x % BASE` (o resto da divisão por 62) diz qual símbolo vai na posição; `x /= BASE` passa para a próxima posição. As posições que sobram ficam com `alphabet[0]`, que é o "pad7".

O método `decode` faz o caminho de volta: lê os 7 caracteres como número em Base62 e multiplica pelo inverso. Ele existe para os testes provarem que a conta é reversível; a API só usa `encode`.

### Validação na subida

O construtor confere o alfabeto (62 caracteres diferentes de `[0-9A-Za-z]`) e o multiplicador:

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

O truque está no `modInverse`: o inverso modular **só existe** quando `M` é coprimo de `62^7`. Se não for, o Java lança `ArithmeticException`, que vira uma mensagem clara em português. Como o `CodeGenerator` é criado como bean no `ApplicationConfig` (guia 02), um multiplicador ruim impede a aplicação de subir (decisão 01-03 no `.planning/STATE.md`).

`62^7 = 2^7 × 31^7`. Então "coprimo de 62^7" é o mesmo que "não divisível por 2 nem por 31": `M` precisa ser ímpar e não ser múltiplo de 31.

### Configuração

`app/src/main/resources/application.yml`

```yaml
  code:
    # Alfabeto e multiplicador definem todos os códigos publicados: trocar quebra links (D-01).
    # O multiplicador precisa ser coprimo de 62^7 (validado na subida). É ofuscação, não segurança.
    alphabet: 0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz
    multiplier: 2176477521915
```

Valores reais tirados do teste `knownVectors` (não calculados à mão): `encode(1)` = `cJinsNv` e `encode(2)` = `EdRbklq`. Números vizinhos, códigos sem nenhuma semelhança visível.

### Alias e código gerado nunca se cruzam

O alias exige pelo menos um `-` ou `_` (regex `^(?=.*[-_])[A-Za-z0-9_-]{4,32}$` no `AliasPolicy`), e o código gerado só tem letras e dígitos. O javadoc de `AliasPolicy` conclui: "os dois espaços nunca se cruzam, então um alias nunca ocupa um código que a sequência ainda vai gerar." O teste `generatedCodesNeverMatchTheAliasRegex` no `AliasPolicyTest` confere isso.

### Testes

- `app/src/test/java/dev/linkpulse/link/CodeGeneratorTest.java`: vetores conhecidos; ida e volta (`decode(encode(id)) == id`) com números baixos e altos; unicidade em amostras; formato de 7 caracteres; inícios diferentes para números vizinhos; recusa de multiplicadores inválidos (62, 31, 2, 0, -1 e `SPACE`); e falha de subida com a palavra "coprimo".
- `app/src/test/java/dev/linkpulse/link/ConcurrencyIT.java`: 8 threads criam 50 links cada contra o Postgres real, e o teste exige 400 códigos diferentes.

## Por que assim?

- **Sem colisão nem nova tentativa:** a sequence garante números únicos, e a bijeção garante códigos únicos. A seção "O Que NÃO Usar" do `.claude/CLAUDE.md` descarta gerar o código com hash ou sorteio por causa de "colisão e retry": um sorteio pode repetir, e aí é preciso sortear de novo e conferir no banco.
- **Não dá para adivinhar a olho nu:** o critério de sucesso 1 da Phase 1 no ROADMAP pede códigos "não consecutivos nem enumeráveis".
- **Mas é ofuscação, não segurança.** O README diz: "Quem conhece dois códigos consecutivos recupera `M` (`c2 − c1 ≡ M mod 62^7`) e passa a enumerar os links. Não use o código curto como segredo: um link curto é público para quem o tiver." Não use o código como senha, token ou controle de acesso.
- **Escolha definitiva:** o `.planning/STATE.md` registra que `linkpulse.code.alphabet` e `multiplier` "são contrato one-way: mudar depois altera os códigos já emitidos". Escolha uma vez, antes de publicar.

## Experimente

1. Refaça o brinquedo com `N = 12` e `M = 5`. Ache o inverso de 5 mod 12 (dica: `5 × 5 = 25`) e confira que todo resultado aparece uma vez.
2. Repita com `M = 4` e encontre a primeira colisão.
3. Rode só os testes do gerador, sem Docker, a partir da raiz do repositório (guia 06):

```bash
cd app && ./mvnw -B -ntp -Dtest=CodeGeneratorTest test
```

4. Leia `contextFailsOnStartupWhenMultiplierIsNotCoprime` no `CodeGeneratorTest` e explique por que `62` é um multiplicador inválido.

## Perguntas para fixar

1. Por que `M` precisa ser coprimo de `62^7`? O que `modInverse` tem a ver com isso?
2. Por que o código usa `BigInteger` em vez de multiplicar dois `long`?
3. Como alguém que conhece dois códigos consecutivos descobre `M`?
4. Por que um alias nunca colide com um código que a sequence ainda vai gerar?
5. O que acontece com os links já publicados se alguém trocar `LINKPULSE_CODE_MULTIPLIER`?

<details>
<summary>Respostas</summary>

1. Só com `M` coprimo a multiplicação é uma bijeção (sem colisões). O `modInverse` só existe nesse caso, então o construtor usa a falha dele para detectar um `M` inválido.
2. Porque `id × M` passa do limite do `long` e "dá a volta" em silêncio, gerando um número errado.
3. Subtraindo um código do outro (como números): a diferença é `M mod 62^7`.
4. O alias tem obrigatoriamente `-` ou `_`, e o código gerado nunca tem.
5. Todos os códigos gerados mudam: links publicados passariam a apontar para outros números, ou para nada.

</details>

---

[← Anterior: 03 · Postgres, JPA e Liquibase](03-postgres-jpa-e-liquibase.md) · [Índice](README.md) · [Próximo: 05 · Validação e erros →](05-validacao-e-erros.md)
