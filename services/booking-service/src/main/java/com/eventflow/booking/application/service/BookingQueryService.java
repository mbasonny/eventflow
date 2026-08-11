package com.eventflow.booking.application.service;

import com.eventflow.booking.domain.exception.BookingNotFoundException;
import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.BookingId;
import com.eventflow.booking.domain.model.BookingReference;
import com.eventflow.booking.domain.model.UserId;
import com.eventflow.booking.domain.port.in.FindBookingsUseCase;
import com.eventflow.booking.domain.port.out.BookingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectures des réservations. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class BookingQueryService implements FindBookingsUseCase {

    private final BookingRepository bookingRepository;

    @Override
    public Booking byId(BookingId id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id.toString()));
    }

    @Override
    public Booking byReference(BookingReference reference) {
        return bookingRepository.findByReference(reference)
                .orElseThrow(() -> new BookingNotFoundException(reference.value()));
    }

    @Override
    public BookingPage ofUser(UserId userId, int page, int size) {
        List<Booking> bookings = bookingRepository.findByUser(userId, page, size);
        return new BookingPage(bookings, page, size, bookingRepository.countByUser(userId));
    }
}
