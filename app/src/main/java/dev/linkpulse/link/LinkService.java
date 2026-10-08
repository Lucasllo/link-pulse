package dev.linkpulse.link;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio dos links.
 */
@Service
public class LinkService {

    private final LinkRepository repository;
    private final Clock clock;

    /**
     * Cria o service.
     *
     * @param repository repositório dos links
     * @param clock relógio da aplicação (base da expiração)
     */
    public LinkService(LinkRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
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
