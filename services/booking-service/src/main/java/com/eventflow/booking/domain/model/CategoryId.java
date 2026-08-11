package com.eventflow.booking.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Référence d'une catégorie de places appartenant à {@code event-service}.
 *
 * <p>Identifiant étranger, comme {@link EventId} : désigné, jamais généré ici.
 */
public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "L'identifiant de catégorie est obligatoire");
    }

    public static CategoryId of(UUID value) {
        return new CategoryId(value);
    }

    public static CategoryId of(String value) {
        return new CategoryId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
