package com.eventflow.booking.infrastructure.persistence;

import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.BookingId;
import com.eventflow.booking.domain.model.BookingReference;
import com.eventflow.booking.domain.model.CategoryId;
import com.eventflow.booking.domain.model.EventId;
import com.eventflow.booking.domain.model.Money;
import com.eventflow.booking.domain.model.Quantity;
import com.eventflow.booking.domain.model.UserId;
import java.util.Currency;

/** Traduction entre l'agrégat {@link Booking} et son entité JPA. */
final class BookingMapper {

    private BookingMapper() {
    }

    static Booking toDomain(BookingJpaEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return Booking.rehydrate(
                BookingId.of(entity.getId()),
                BookingReference.of(entity.getReference()),
                UserId.of(entity.getUserId()),
                EventId.of(entity.getEventId()),
                CategoryId.of(entity.getCategoryId()),
                Quantity.of(entity.getQuantity()),
                Money.of(entity.getUnitAmount(), currency),
                Money.of(entity.getTotalAmount(), currency),
                entity.getStatus(),
                entity.getStatusReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static BookingJpaEntity toJpa(Booking booking) {
        return new BookingJpaEntity(
                booking.id().value(),
                booking.reference().value(),
                booking.userId().value(),
                booking.eventId().value(),
                booking.categoryId().value(),
                booking.quantity().value(),
                booking.unitPrice().amount(),
                booking.totalAmount().amount(),
                booking.totalAmount().currency().getCurrencyCode(),
                booking.status(),
                booking.statusReason().orElse(null),
                booking.createdAt(),
                booking.updatedAt());
    }

    /**
     * Reporte l'état du domaine sur une entité déjà gérée par Hibernate.
     *
     * <p>Seuls le statut, son motif et l'horodatage sont modifiables : tout le
     * reste d'une réservation est immuable une fois créée. Le mapper le reflète
     * en n'exposant que ces trois setters.
     */
    static void applyTo(BookingJpaEntity entity, Booking booking) {
        entity.setStatus(booking.status());
        entity.setStatusReason(booking.statusReason().orElse(null));
        entity.setUpdatedAt(booking.updatedAt());
    }
}
