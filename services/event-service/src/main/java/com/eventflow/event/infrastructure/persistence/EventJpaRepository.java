package com.eventflow.event.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository Spring Data, détail d'infrastructure.
 *
 * <p>Il n'est jamais exposé au domaine : seul {@link EventPersistenceAdapter}
 * s'en sert, pour implémenter le port
 * {@link com.eventflow.event.domain.port.out.EventRepository}.
 */
interface EventJpaRepository extends JpaRepository<EventJpaEntity, UUID> {

    /**
     * Charge l'événement et ses catégories en une seule requête.
     *
     * <p>Sans ce {@code JOIN FETCH}, la collection est chargée paresseusement et
     * chaque agrégat déclenche une requête supplémentaire — le problème N+1.
     */
    @Query("SELECT e FROM EventJpaEntity e LEFT JOIN FETCH e.categories WHERE e.id = :id")
    Optional<EventJpaEntity> findByIdWithCategories(UUID id);

    @Query(value = "SELECT DISTINCT e FROM EventJpaEntity e LEFT JOIN FETCH e.categories",
            countQuery = "SELECT count(e) FROM EventJpaEntity e")
    Page<EventJpaEntity> findAllWithCategories(Pageable pageable);
}
