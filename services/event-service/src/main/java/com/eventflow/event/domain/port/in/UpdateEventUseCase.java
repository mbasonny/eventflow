package com.eventflow.event.domain.port.in;

import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import java.time.Instant;

/**
 * Port entrant : modifier les informations générales d'un événement.
 *
 * <p>Les catégories ne sont volontairement pas modifiables ici : toucher à une
 * capacité déjà partiellement vendue est une opération métier à part entière,
 * qui aura son propre cas d'usage.
 */
public interface UpdateEventUseCase {

    Event update(UpdateEventCommand command);

    record UpdateEventCommand(EventId id, String title, String venue, Instant startsAt) {
    }
}
