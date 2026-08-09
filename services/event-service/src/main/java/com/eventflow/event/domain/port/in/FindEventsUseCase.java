package com.eventflow.event.domain.port.in;

import com.eventflow.event.domain.model.CategoryId;
import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.model.TicketCategory;
import java.util.List;

/**
 * Port entrant : les lectures du catalogue.
 *
 * <p>Séparé des cas d'usage d'écriture : les lectures s'exécutent en
 * transaction {@code readOnly}, et évolueront différemment (projections, cache)
 * sans impacter les règles d'écriture.
 */
public interface FindEventsUseCase {

    /** @throws com.eventflow.event.domain.exception.EventNotFoundException si absent */
    Event byId(EventId id);

    EventPage all(int page, int size);

    /** @throws com.eventflow.event.domain.exception.CategoryNotFoundException si absente */
    TicketCategory category(EventId eventId, CategoryId categoryId);

    record EventPage(List<Event> events, int page, int size, long totalElements) {

        public int totalPages() {
            return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        }
    }
}
