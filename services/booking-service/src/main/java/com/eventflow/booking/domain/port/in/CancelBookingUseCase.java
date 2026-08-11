package com.eventflow.booking.domain.port.in;

import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.BookingId;

/**
 * Port entrant : annuler une réservation.
 *
 * <p>En phase 2, l'annulation change l'état local mais <strong>ne rend pas les
 * places au stock</strong> : il n'existe pas encore de mécanisme de
 * compensation fiable. La phase 4 branchera {@code seats.release} sur cette
 * transition, et c'est là que la saga prendra son sens.
 */
public interface CancelBookingUseCase {

    Booking cancel(BookingId id, String reason);
}
