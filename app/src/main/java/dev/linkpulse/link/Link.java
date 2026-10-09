package dev.linkpulse.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

/**
 * Link curto gravado na tabela {@code links}.
 *
 * <p>O ID vem da sequência {@code link_id_seq} antes do INSERT (não há {@code @GeneratedValue}).
 * Por isso a entidade implementa {@link Persistable}: sem isso o Spring Data trataria o ID
 * atribuído como entidade existente e faria {@code merge} (SELECT + INSERT).
 */
@Entity
@Table(name = "links")
public class Link implements Persistable<Long> {

    @Id
    private Long id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    /** Construtor exigido pelo JPA. */
    protected Link() {
    }

    /**
     * Cria um link novo, ainda não persistido.
     *
     * @param id ID obtido da sequência {@code link_id_seq}
     * @param code código curto (gerado ou alias), case-sensitive
     * @param targetUrl URL de destino do redirect
     * @param expiresAt instante de expiração, ou {@code null} se o link não expira
     * @param createdAt instante de criação
     * @return a entidade marcada como nova
     */
    public static Link create(
            long id, String code, String targetUrl, Instant expiresAt, Instant createdAt) {
        Link link = new Link();
        link.id = id;
        link.code = code;
        link.targetUrl = targetUrl;
        link.expiresAt = expiresAt;
        link.createdAt = createdAt;
        return link;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public String getCode() {
        return code;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Diz se o link já expirou no instante informado. O limite exato conta como expirado.
     *
     * @param now instante de referência (vem do {@code Clock} da aplicação)
     * @return {@code true} se há expiração e {@code now} não é anterior a ela
     */
    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
