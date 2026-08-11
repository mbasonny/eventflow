package com.eventflow.booking.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identifiant technique d'une réservation — propriété de booking-service. */
public record BookingId(UUID value) {

    public BookingId {
        Objects.requireNonNull(value, "L'identifiant de réservation est obligatoire");
    }

    public static BookingId newId() {
        return new BookingId(UUID.randomUUID());
    }

    public static BookingId of(UUID value) {
        return new BookingId(value);
    }

    public static BookingId of(String value) {
        return new BookingId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
