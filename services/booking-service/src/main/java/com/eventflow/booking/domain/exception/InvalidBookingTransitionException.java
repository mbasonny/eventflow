package com.eventflow.booking.domain.exception;

import com.eventflow.booking.domain.model.BookingStatus;

/**
 * Transition d'état interdite — confirmer une réservation déjà annulée, par
 * exemple.
 *
 * <p>Ce cas se produira réellement : Kafka livre <em>au moins une fois</em>, et
 * un message rejoué tentera de refaire une transition déjà effectuée. L'exception
 * porte les deux états pour que la trace soit exploitable.
 */
public class InvalidBookingTransitionException extends DomainException {

    private final BookingStatus from;
    private final BookingStatus to;

    public InvalidBookingTransitionException(BookingStatus from, BookingStatus to) {
        super("Transition interdite : %s ne peut pas passer à %s".formatted(from, to));
        this.from = from;
        this.to = to;
    }

    public BookingStatus from() {
        return from;
    }

    public BookingStatus to() {
        return to;
    }
}
