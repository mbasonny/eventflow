package com.eventflow.booking.domain.port.in;

import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.CategoryId;
import com.eventflow.booking.domain.model.EventId;
import com.eventflow.booking.domain.model.Quantity;
import com.eventflow.booking.domain.model.UserId;

/**
 * Port entrant : réserver des places.
 *
 * <p>La commande ne contient <strong>aucun montant</strong> : le prix est
 * récupéré auprès du service propriétaire du catalogue. Laisser le client
 * proposer un prix serait lui laisser décider de ce qu'il paie.
 */
public interface CreateBookingUseCase {

    Booking book(CreateBookingCommand command);

    record CreateBookingCommand(
            UserId userId, EventId eventId, CategoryId categoryId, Quantity quantity) {
    }
}
