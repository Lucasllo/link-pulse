package dev.linkpulse.link;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio dos links.
 *
 * <p>Não registra em log a URL de destino nem o corpo das criações: query strings podem carregar
 * tokens e dados pessoais.
 */
@Service
public class LinkService {

    private final LinkRepository repository;
    private final CodeGenerator codeGenerator;
    private final Clock clock;

    /**
     * Cria o service.
     *
     * @param repository repositório dos links
     * @param codeGenerator gerador do código curto a partir do ID da sequência
     * @param clock relógio da aplicação (base da expiração e do {@code createdAt})
     */
    public LinkService(LinkRepository repository, CodeGenerator codeGenerator, Clock clock) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    /**
     * Cria um link com código gerado.
     *
     * <p>O ID sai do {@code nextval('link_id_seq')} antes do INSERT, então o código existe antes
     * do commit e um único INSERT grava {@code id} e {@code code}. A transação é de escrita porque
     * o {@code nextval} não roda em transação read-only. Os instantes são truncados em
     * microssegundos, a precisão do {@code timestamptz}.
     *
     * @param request URL de destino e opcionais
     * @return o link persistido
     */
    @Transactional
    public Link create(CreateLinkRequest request) {
        long id = repository.nextId();
        String code = codeGenerator.encode(id);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = request.expiresAt() == null
                ? null
                : request.expiresAt().toInstant().truncatedTo(ChronoUnit.MICROS);
        return repository.saveAndFlush(Link.create(id, code, request.url(), expiresAt, now));
    }

    /**
     * Resolve o código curto na URL de destino.
     *
     * @param code código curto (case-sensitive)
     * @return a URL de destino
     * @throws org.springframework.web.ErrorResponseException 404 se o código não existe, 410 se
     *     o link expirou
     */
    @Transactional(readOnly = true)
    public String resolve(String code) {
        Link link = repository.findByCode(code).orElseThrow(() -> LinkProblems.notFound(code));
        if (link.isExpiredAt(clock.instant())) {
            throw LinkProblems.expired(code, link.getExpiresAt());
        }
        return link.getTargetUrl();
    }
}
