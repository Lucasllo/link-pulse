package dev.linkpulse.link;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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

    /** Constraint UNIQUE de {@code links.code} (changelog 001). */
    private static final String CODE_CONSTRAINT = "uk_links_code";

    /** SQLSTATE de {@code unique_violation} no PostgreSQL. */
    private static final String UNIQUE_VIOLATION = "23505";

    private final LinkRepository repository;
    private final CodeGenerator codeGenerator;
    private final AliasPolicy aliasPolicy;
    private final TargetHostPolicy targetHostPolicy;
    private final Clock clock;

    /**
     * Cria o service.
     *
     * @param repository repositório dos links
     * @param codeGenerator gerador do código curto a partir do ID da sequência
     * @param aliasPolicy regras do alias customizado (formato e reservados)
     * @param targetHostPolicy regras do host de destino (o próprio encurtador não pode ser
     *     destino de um link)
     * @param clock relógio da aplicação (base da expiração e do {@code createdAt})
     */
    public LinkService(LinkRepository repository, CodeGenerator codeGenerator,
            AliasPolicy aliasPolicy, TargetHostPolicy targetHostPolicy, Clock clock) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.aliasPolicy = aliasPolicy;
        this.targetHostPolicy = targetHostPolicy;
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
        try {
            return repository.saveAndFlush(Link.create(id, code, request.url(), expiresAt, now));
        } catch (DataIntegrityViolationException e) {
            if (alias != null && isCodeConflict(e)) {
                throw LinkProblems.aliasConflict(alias);
            }
            // Código gerado colidindo é impossível (D-05): se acontecer, é bug e vira 500.
            throw e;
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
