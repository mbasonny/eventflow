package com.eventflow.booking.domain.port.out;

import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.BookingId;
import com.eventflow.booking.domain.model.BookingReference;
import com.eventflow.booking.domain.model.UserId;
import java.util.List;
import java.util.Optional;

/** Port sortant : persistance des réservations. */
public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(BookingId id);

    Optional<Booking> findByReference(BookingReference reference);

    /** @param page index de page, à partir de 0 */
    List<Booking> findByUser(UserId userId, int page, int size);

    long countByUser(UserId userId);
}
