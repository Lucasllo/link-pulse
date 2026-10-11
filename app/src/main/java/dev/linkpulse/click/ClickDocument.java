package dev.linkpulse.click;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Um clique gravado na coleção normal {@code click_events} do MongoDB (D-05).
 *
 * <p>O {@code _id} é o ID da entrada no stream {@code lp:clicks} ({@code <ms>-<seq>}): a mesma
 * entrada entregue duas vezes bate no índice único do {@code _id} e vira duplicata ignorada, e
 * duas entradas com o mesmo conteúdo continuam sendo dois cliques (IDs diferentes).
 *
 * <p>Privacidade (CLICK-06, D-10): o documento guarda só o HMAC do IP ({@code ipHash}) e o host
 * do {@code Referer}. O IP em claro nunca chega ao stream, e o user-agent cru do stream
 * ({@code ua}) não vira campo: ele passa pelo {@link UserAgentClassifier} e sobra só a família
 * do navegador, a família do SO e a marca de bot ({@code isBot}), sem versões.
 *
 * <p>Formato final em {@code click_events}: {@code _id}, {@code _class}, {@code code},
 * {@code ts}, {@code referrer}, {@code browser}, {@code os}, {@code isBot} e {@code ipHash}.
 *
 * @param id ID da entrada do stream ({@code _id})
 * @param code código curto do link
 * @param ts instante do clique (BSON Date, usado pelo índice TTL e pelas agregações por dia)
 * @param referrer host do {@code Referer}, ou {@code (direct)}
 * @param browser família do navegador
 * @param os família do sistema operacional
 * @param bot se o clique veio de robô ({@code isBot} no MongoDB)
 * @param ipHash HMAC-SHA256 do IP em hexadecimal (64 caracteres)
 */
@Document(ClickDocument.COLLECTION)
public record ClickDocument(@Id String id, String code, Instant ts, String referrer,
        String browser, String os, @Field("isBot") boolean bot, String ipHash) {

    /** Coleção dos cliques. */
    public static final String COLLECTION = "click_events";

    /** Tamanho máximo aceito para o {@code referrer} (um host DNS tem até 253). */
    static final int MAX_REFERRER = 255;

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Pattern IP_HASH = Pattern.compile("[0-9a-f]{64}");

    /**
     * Converte uma entrada do stream no documento do clique.
     *
     * <p>Entrada inválida (versão antiga do formato ou escrita à mão no Valkey) devolve vazio em
     * vez de lançar, para não envenenar o lote: o consumer conta e confirma a entrada mesmo
     * assim. O campo {@code ua} é classificado e descartado; ausente vale como vazio, que conta
     * como bot.
     *
     * @param id ID da entrada no stream
     * @param fields campos da entrada ({@code code}, {@code ts}, {@code ref}, {@code ua},
     *     {@code iph})
     * @return o documento, ou vazio se algum campo obrigatório falta ou é inválido
     */
    public static Optional<ClickDocument> fromStream(String id, Map<?, ?> fields) {
        if (id == null || id.isBlank() || fields == null) {
            return Optional.empty();
        }
        String code = text(fields, ClickPublisher.FIELD_CODE);
        if (code == null || !CODE.matcher(code).matches()) {
            return Optional.empty();
        }
        String ipHash = text(fields, ClickPublisher.FIELD_IPH);
        if (ipHash == null || !IP_HASH.matcher(ipHash).matches()) {
            return Optional.empty();
        }
        String ref = text(fields, ClickPublisher.FIELD_REF);
        if (ref == null || ref.isBlank() || ref.length() > MAX_REFERRER) {
            return Optional.empty();
        }
        long epochMilli = parsePositiveLong(text(fields, ClickPublisher.FIELD_TS));
        if (epochMilli <= 0) {
            return Optional.empty();
        }
        String userAgent = text(fields, ClickPublisher.FIELD_UA);
        UserAgentClassifier.UserAgentInfo agent =
                UserAgentClassifier.classify(userAgent == null ? "" : userAgent);
        return Optional.of(new ClickDocument(id, code, Instant.ofEpochMilli(epochMilli), ref,
                agent.browser(), agent.os(), agent.bot(), ipHash));
    }

    private static String text(Map<?, ?> fields, String name) {
        Object value = fields.get(name);
        return value == null ? null : value.toString();
    }

    /**
     * Lê um {@code long} positivo.
     *
     * @param value texto com o epoch ms
     * @return o valor, ou {@code -1} se for nulo, não numérico, zero ou negativo
     */
    private static long parsePositiveLong(String value) {
        if (value == null) {
            return -1;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
