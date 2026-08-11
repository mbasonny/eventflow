package com.eventflow.booking.domain.model;

import java.util.Objects;

/**
 * Identifiant de l'utilisateur qui réserve.
 *
 * <p>Une chaîne et non un UUID : en phase 6, cette valeur viendra du
 * {@code sub} du jeton Keycloak, dont le format est imposé par le fournisseur
 * d'identité.
 */
public record UserId(String value) {

    public UserId {
        Objects.requireNonNull(value, "L'identifiant utilisateur est obligatoire");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("L'identifiant utilisateur ne peut pas être vide");
        }
    }

    public static UserId of(String value) {
        return new UserId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
