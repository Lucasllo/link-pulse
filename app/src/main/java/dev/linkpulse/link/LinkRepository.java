package dev.linkpulse.link;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso à tabela {@code links}.
 */
public interface LinkRepository extends JpaRepository<Link, Long> {

    /**
     * Próximo valor da sequência {@code link_id_seq}.
     *
     * <p>Transação de escrita explícita: o {@code nextval()} falha em transação read-only, e os
     * query methods herdam {@code readOnly = true} do {@code SimpleJpaRepository}. Quando chamado
     * dentro de uma transação do service, junta-se a ela.
     *
     * @return o próximo ID
     */
    @Transactional
    @Query(value = "select nextval('link_id_seq')", nativeQuery = true)
    long nextId();

    /**
     * Busca por igualdade exata (case-sensitive).
     *
     * @param code código curto
     * @return o link, se existir
     */
    Optional<Link> findByCode(String code);

    /**
     * Diz se já existe link com o código informado.
     *
     * @param code código curto
     * @return {@code true} se existir
     */
    boolean existsByCode(String code);
}
