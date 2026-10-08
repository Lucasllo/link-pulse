package dev.linkpulse.link;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Regras de negócio dos links.
 */
@Service
public class LinkService {

    private final LinkRepository repository;

    /**
     * Cria o service.
     *
     * @param repository repositório dos links
     */
    public LinkService(LinkRepository repository) {
        this.repository = repository;
    }

    /**
     * Resolve o código curto na URL de destino.
     *
     * @param code código curto (case-sensitive)
     * @return a URL de destino
     */
    @Transactional(readOnly = true)
    public String resolve(String code) {
        return repository.findByCode(code)
                .map(Link::getTargetUrl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
