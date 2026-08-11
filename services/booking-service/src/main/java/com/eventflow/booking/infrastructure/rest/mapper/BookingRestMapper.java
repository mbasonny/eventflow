package com.eventflow.booking.infrastructure.rest.mapper;

import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.CategoryId;
import com.eventflow.booking.domain.model.EventId;
import com.eventflow.booking.domain.model.Quantity;
import com.eventflow.booking.domain.model.UserId;
import com.eventflow.booking.domain.port.in.CreateBookingUseCase.CreateBookingCommand;
import com.eventflow.booking.domain.port.in.FindBookingsUseCase.BookingPage;
import com.eventflow.booking.infrastructure.rest.dto.BookingResponse;
import com.eventflow.booking.infrastructure.rest.dto.CreateBookingRequest;
import com.eventflow.booking.infrastructure.rest.dto.PagedResponse;
import org.springframework.stereotype.Component;

/** Traduction entre le contrat HTTP et les types du domaine. */
@Component
public class BookingRestMapper {

    public CreateBookingCommand toCommand(CreateBookingRequest request) {
        return new CreateBookingCommand(
                UserId.of(request.userId()),
                EventId.of(request.eventId()),
                CategoryId.of(request.categoryId()),
                Quantity.of(request.quantity()));
    }

    public BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.id().value(),
                booking.reference().value(),
                booking.userId().value(),
                booking.eventId().value(),
                booking.categoryId().value(),
                booking.quantity().value(),
                booking.unitPrice().amount(),
                booking.totalAmount().amount(),
                booking.totalAmount().currency().getCurrencyCode(),
                booking.status().name(),
                booking.statusReason().orElse(null),
                booking.createdAt(),
                booking.updatedAt());
    }

    public PagedResponse<BookingResponse> toResponse(BookingPage page) {
        return new PagedResponse<>(
                page.bookings().stream().map(this::toResponse).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
