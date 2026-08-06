package com.eventflow.event.domain.port.out;

import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import java.util.List;
import java.util.Optional;

/**
 * Port sortant : ce dont le domaine a besoin pour persister ses agrégats.
 *
 * <p>L'interface est déclarée <strong>par le domaine</strong> et implémentée par
 * l'infrastructure — c'est l'inversion de dépendance. Le domaine ignore
 * l'existence de JPA, de PostgreSQL et de Spring Data.
 *
 * <p>La pagination est exprimée en {@code int} plutôt qu'avec un
 * {@code Pageable} : ce type appartient à Spring Data, l'importer ici ferait
 * entrer le framework dans le domaine.
 */
public interface EventRepository {

    /** Crée ou met à jour l'agrégat, et renvoie son état persisté. */
    Event save(Event event);

    Optional<Event> findById(EventId id);

    /** @param page index de page, à partir de 0 */
    List<Event> findAll(int page, int size);

    long count();

    boolean existsById(EventId id);

    void deleteById(EventId id);
}
