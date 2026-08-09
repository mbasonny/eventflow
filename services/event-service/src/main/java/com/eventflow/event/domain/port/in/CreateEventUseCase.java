package com.eventflow.event.domain.port.in;

import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.Money;
import java.time.Instant;
import java.util.List;

/**
 * Port entrant : créer un événement et ses catégories de places.
 *
 * <p>La commande est exprimée avec les types du domaine ({@link Money}), pas
 * avec ceux du transport HTTP. C'est la couche REST qui traduit — le domaine
 * ignore qu'une API existe.
 */
public interface CreateEventUseCase {

    Event create(CreateEventCommand command);

    record CreateEventCommand(
            String title,
            String venue,
            Instant startsAt,
            List<NewCategory> categories) {

        public record NewCategory(String name, Money price, int capacity) {
        }
    }
}
