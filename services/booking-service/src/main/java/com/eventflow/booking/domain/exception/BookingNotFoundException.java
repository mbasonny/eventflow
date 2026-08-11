package com.eventflow.booking.domain.exception;

/** Aucune réservation ne correspond à l'identifiant ou à la référence demandé. */
public class BookingNotFoundException extends DomainException {

    private final String identifier;

    public BookingNotFoundException(String identifier) {
        super("Réservation introuvable : " + identifier);
        this.identifier = identifier;
    }

    public String identifier() {
        return identifier;
    }
}
