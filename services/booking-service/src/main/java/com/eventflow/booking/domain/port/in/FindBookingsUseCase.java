package com.eventflow.booking.domain.port.in;

import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.BookingId;
import com.eventflow.booking.domain.model.BookingReference;
import com.eventflow.booking.domain.model.UserId;
import java.util.List;

/** Port entrant : consulter les réservations. */
public interface FindBookingsUseCase {

    /** @throws com.eventflow.booking.domain.exception.BookingNotFoundException si absente */
    Booking byId(BookingId id);

    /** @throws com.eventflow.booking.domain.exception.BookingNotFoundException si absente */
    Booking byReference(BookingReference reference);

    BookingPage ofUser(UserId userId, int page, int size);

    record BookingPage(List<Booking> bookings, int page, int size, long totalElements) {

        public int totalPages() {
            return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        }
    }
}
