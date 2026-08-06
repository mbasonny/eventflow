package com.eventflow.event.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identifiant d'une catégorie de places. */
public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "L'identifiant de catégorie est obligatoire");
    }

    public static CategoryId newId() {
        return new CategoryId(UUID.randomUUID());
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
