package dev.linkpulse.link;

import dev.linkpulse.cache.CacheEntry;
import dev.linkpulse.cache.LinkCache;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolve o código curto do redirect: primeiro o cache no Valkey, depois o Postgres.
 *
 * <p>A classe não abre transação de propósito: um hit no cache não pode pegar conexão do pool do
 * Hikari (Pitfall P2). Só no miss o lookup vai ao {@link LinkService#find(String)}, que abre a
 * transação read-only.
 *
 * <p>Código inexistente e link expirado também saem do cache ({@code N} e {@code G}), com os
 * mesmos Problem Details 404 e 410 do lookup no Postgres.
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
        if (cached.isPresent()) {
            return fromCache(code, cached.get());
        }
        return fromDatabase(code);
    }

    /**
     * Responde a partir do valor do cache. Um {@code L} cujo prazo já passou pelo relógio da
     * aplicação vira {@code G} e 410.
     */
    private String fromCache(String code, CacheEntry entry) {
        return switch (entry) {
            case CacheEntry.Found found -> {
                if (found.expiresAt() != null && !clock.instant().isBefore(found.expiresAt())) {
                    linkCache.putGone(code, found.expiresAt());
                    throw LinkProblems.expired(code, found.expiresAt());
                }
                yield found.targetUrl();
            }
            case CacheEntry.Missing missing -> throw LinkProblems.notFound(code);
            case CacheEntry.Gone gone -> throw LinkProblems.expired(code, gone.expiredAt());
        };
    }

    /**
     * Miss: consulta o Postgres e grava o resultado no cache, inclusive 404 ({@code N}) e 410
     * ({@code G}).
     */
    private String fromDatabase(String code) {
        Optional<Link> found = linkService.find(code);
        if (found.isEmpty()) {
            linkCache.putMissing(code);
            throw LinkProblems.notFound(code);
        }
        Link link = found.get();
        if (link.isExpiredAt(clock.instant())) {
            linkCache.putGone(code, link.getExpiresAt());
            throw LinkProblems.expired(code, link.getExpiresAt());
        }
        linkCache.putFound(code, link.getTargetUrl(), link.getExpiresAt());
        return link.getTargetUrl();
    }
}
