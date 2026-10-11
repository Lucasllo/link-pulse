package dev.linkpulse.click;

import java.util.List;

/**
 * Grava um lote de cliques de forma idempotente.
 *
 * <p>O consumer só confirma ({@code XACK}) as entradas do lote depois que {@link #write} volta.
 * Se ele lançar, nada é confirmado e o lote fica no PEL do grupo para ser entregue de novo.
 */
public interface ClickEventWriter {

    /**
     * Grava os documentos. Documento já gravado (mesmo {@code _id}) não é erro: é contado como
     * duplicata e o resto do lote continua.
     *
     * @param documents documentos do lote; pode ser vazio
     * @return quantos documentos foram ignorados por já existirem
     * @throws org.springframework.dao.DataAccessException em qualquer falha que não seja só
     *     duplicata (o lote não deve ser confirmado)
     */
    int write(List<ClickDocument> documents);
}
