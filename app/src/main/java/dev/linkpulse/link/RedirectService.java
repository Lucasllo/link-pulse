package dev.linkpulse.link;

import dev.linkpulse.cache.CacheEntry;
import dev.linkpulse.cache.LinkCache;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolve o código curto do redirect: primeiro o cache no Valkey, depois o Postgres.
 *
 * <p>A classe não abre transação de propósito: um hit no cache não pode pegar conexão do pool do
 * Hikari (Pitfall P2). Só no miss o lookup vai ao {@link LinkService#find(String)}, que abre a
 * transação read-only.
 */
@Service
public class RedirectService {

    private final LinkCache linkCache;
    private final LinkService linkService;
    private final Clock clock;

    /**
     * Cria o service.
     *
     * @param linkCache cache do redirect
     * @param linkService acesso transacional aos links (só no miss)
     * @param clock relógio da aplicação (expiração inclusiva)
     */
    public RedirectService(LinkCache linkCache, LinkService linkService, Clock clock) {
        this.linkCache = linkCache;
        this.linkService = linkService;
        this.clock = clock;
    }

    /**
     * Resolve o código curto na URL de destino.
     *
     * <p>A expiração é inclusiva pelo relógio da aplicação: com {@code now == expiresAt} o link
     * já está expirado, venha ele do cache ou do Postgres.
     *
     * @param code código curto (case-sensitive)
     * @return a URL de destino
     * @throws org.springframework.web.ErrorResponseException 404 se o código não existe, 410 se
     *     o link expirou
     */
    public String resolve(String code) {
        Optional<CacheEntry> cached = linkCache.get(code);
        if (cached.isPresent() && cached.get() instanceof CacheEntry.Found found) {
            if (isExpired(found.expiresAt())) {
                throw LinkProblems.expired(code, found.expiresAt());
            }
            return found.targetUrl();
        }
        Link link = linkService.find(code).orElseThrow(() -> LinkProblems.notFound(code));
        if (link.isExpiredAt(clock.instant())) {
            throw LinkProblems.expired(code, link.getExpiresAt());
        }
        linkCache.putFound(code, link.getTargetUrl(), link.getExpiresAt());
        return link.getTargetUrl();
    }

    private boolean isExpired(Instant expiresAt) {
        return expiresAt != null && !clock.instant().isBefore(expiresAt);
    }
}
