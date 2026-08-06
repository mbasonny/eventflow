package com.eventflow.event.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifiant d'un événement.
 *
 * <p>Un type dédié plutôt qu'un {@code UUID} nu : le compilateur refuse alors
 * de passer un {@link CategoryId} là où un {@code EventId} est attendu, ce
 * qu'aucune relecture humaine ne garantit.
 */
public record EventId(UUID value) {

    public EventId {
        Objects.requireNonNull(value, "L'identifiant d'événement est obligatoire");
    }

    /** Génère un identifiant côté domaine, sans attendre la base de données. */
    public static EventId newId() {
        return new EventId(UUID.randomUUID());
    }

    public static EventId of(UUID value) {
        return new EventId(value);
    }

    public static EventId of(String value) {
        return new EventId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
