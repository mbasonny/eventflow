package com.eventflow.event.domain.port.in;

import com.eventflow.event.domain.model.CategoryId;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.model.Quantity;
import com.eventflow.event.domain.model.TicketCategory;

/**
 * Port entrant : retirer des places du stock.
 *
 * <p>C'est le cas d'usage que {@code booking-service} appellera en phase 2, puis
 * qui sera déclenché par un événement Kafka en phase 3. Le port ne change pas —
 * seul l'adaptateur entrant change. C'est tout l'intérêt de l'hexagonal.
 *
 * <p>À ce stade, aucune protection contre les accès concurrents : deux appels
 * simultanés peuvent lire le même stock et réserver chacun. C'est délibéré, la
 * phase 3 traitera le problème par verrouillage optimiste.
 */
public interface ReserveSeatsUseCase {

    /**
     * @throws com.eventflow.event.domain.exception.EventNotFoundException si l'événement est inconnu
     * @throws com.eventflow.event.domain.exception.CategoryNotFoundException si la catégorie n'appartient pas à l'événement
     * @throws com.eventflow.event.domain.exception.InsufficientSeatsException si le stock est insuffisant
     */
    TicketCategory reserve(ReserveSeatsCommand command);

    record ReserveSeatsCommand(EventId eventId, CategoryId categoryId, Quantity quantity) {
    }
}
