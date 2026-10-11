package dev.linkpulse.link;

import dev.linkpulse.cache.LinkCache;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Regras de negócio dos links.
 *
 * <p>Não registra em log a URL de destino nem o corpo das criações: query strings podem carregar
 * tokens e dados pessoais.
 */
@Service
public class LinkService {

    /** Constraint UNIQUE de {@code links.code} (changelog 001). */
    private static final String CODE_CONSTRAINT = "uk_links_code";

    /** SQLSTATE de {@code unique_violation} no PostgreSQL. */
    private static final String UNIQUE_VIOLATION = "23505";

    private final LinkRepository repository;
    private final CodeGenerator codeGenerator;
    private final AliasPolicy aliasPolicy;
    private final TargetHostPolicy targetHostPolicy;
    private final LinkCache linkCache;
    private final Clock clock;

    /**
     * Cria o service.
     *
     * @param repository repositório dos links
     * @param codeGenerator gerador do código curto a partir do ID da sequência
     * @param aliasPolicy regras do alias customizado (formato e reservados)
     * @param targetHostPolicy regras do host de destino (o próprio encurtador não pode ser
     *     destino de um link)
     * @param linkCache cache do redirect; a criação apaga a chave do código depois do commit
     * @param clock relógio da aplicação (base da expiração e do {@code createdAt})
     */
    public LinkService(LinkRepository repository, CodeGenerator codeGenerator,
            AliasPolicy aliasPolicy, TargetHostPolicy targetHostPolicy, LinkCache linkCache,
            Clock clock) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.aliasPolicy = aliasPolicy;
        this.targetHostPolicy = targetHostPolicy;
        this.linkCache = linkCache;
        this.clock = clock;
    }

    /**
     * Cria um link com o alias pedido ou, sem alias, com código gerado.
     *
     * <p>O ID sai do {@code nextval('link_id_seq')} antes do INSERT, então o código existe antes
     * do commit e um único INSERT grava {@code id} e {@code code}. A transação é de escrita porque
     * o {@code nextval} não roda em transação read-only. Os instantes são truncados em
     * microssegundos, a precisão do {@code timestamptz}.
     *
     * <p>Alias duplicado vira 409 em dois pontos: na checagem prévia e, se duas criações correm
     * ao mesmo tempo, na violação de {@code uk_links_code} (D-08).
     *
     * <p>Antes de tudo, o host da URL de destino passa pelo {@link TargetHostPolicy}, que concentra
     * as regras de destino (o próprio encurtador não pode ser destino).
     *
     * <p>Depois do commit, a chave {@code lp:link:{code}} é apagada do cache: um GET anterior pode
     * ter gravado o negative cache ({@code N}) desse código. O {@code DEL} roda sempre, inclusive
     * para código gerado. Ele fica no {@code afterCommit}, e não dentro da transação, porque um
     * {@code DEL} antes do commit deixaria um GET concorrente ler o Postgres sem a linha e regravar
     * {@code N} (Pitfall P3). Sobra uma janela residual: um GET que leia o Postgres antes do
     * commit e grave {@code N} depois do {@code DEL}; ela fica limitada ao
     * {@code linkpulse.cache.negative-ttl}.
     *
     * @param request URL de destino e opcionais
     * @return o link persistido
     * @throws org.springframework.web.ErrorResponseException 400 se o {@link TargetHostPolicy}
     *     recusa o host da URL ou se o alias é inválido ou reservado, 409 se o alias já existe
     */
    @Transactional
    public Link create(CreateLinkRequest request) {
        targetHostPolicy.check(request.url());
        String alias = request.alias();
        if (alias != null) {
            aliasPolicy.check(alias);
            if (repository.existsByCode(alias)) {
                throw LinkProblems.aliasConflict(alias);
            }
        }
        long id = repository.nextId();
        String code = alias != null ? alias : codeGenerator.encode(id);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Instant expiresAt = request.expiresAt() == null
                ? null
                : request.expiresAt().toInstant().truncatedTo(ChronoUnit.MICROS);
        Link saved;
        try {
            saved = repository.saveAndFlush(Link.create(id, code, request.url(), expiresAt, now));
        } catch (DataIntegrityViolationException e) {
            if (alias != null && isCodeConflict(e)) {
                throw LinkProblems.aliasConflict(alias);
            }
            // Código gerado colidindo é impossível (D-05): se acontecer, é bug e vira 500.
            throw e;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new EvictAfterCommit(linkCache, code));
        return saved;
    }

    /**
     * Apaga a chave do código no cache depois do commit da criação (Pitfall P3).
     *
     * @param linkCache cache do redirect
     * @param code código criado
     */
    private record EvictAfterCommit(LinkCache linkCache, String code)
            implements TransactionSynchronization {

        @Override
        public void afterCommit() {
            linkCache.evict(code);
        }
    }

    /**
     * Indica se a violação veio de {@code uk_links_code}, percorrendo a cadeia de causas.
     *
     * @param e exceção traduzida pelo Spring
     * @return {@code true} se a causa é a unicidade do código
     */
    private static boolean isCodeConflict(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && CODE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            if (cause instanceof SQLException sql
                    && UNIQUE_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().toLowerCase(Locale.ROOT).contains(CODE_CONSTRAINT)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * Busca o link pelo código curto, numa transação read-only.
     *
     * <p>Só o miss do cache chega aqui; as respostas 404/410 do redirect ficam no
     * {@link RedirectService}.
     *
     * @param code código curto (case-sensitive)
     * @return o link, ou vazio se o código não existe
     */
    @Transactional(readOnly = true)
    public Optional<Link> find(String code) {
        return repository.findByCode(code);
    }
}
