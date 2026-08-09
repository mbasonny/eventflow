package com.eventflow.event.domain.port.in;

import com.eventflow.event.domain.model.EventId;

/** Port entrant : supprimer un événement et, en cascade, ses catégories. */
public interface DeleteEventUseCase {

    void delete(EventId id);
}
