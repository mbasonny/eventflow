package com.eventflow.event.domain.exception;

import com.eventflow.event.domain.model.EventId;

/** Levée lorsqu'aucun événement ne correspond à l'identifiant demandé. */
public class EventNotFoundException extends DomainException {

    private final EventId eventId;

    public EventNotFoundException(EventId eventId) {
        super("Événement introuvable : " + eventId.value());
        this.eventId = eventId;
    }

    public EventId eventId() {
        return eventId;
    }
}
