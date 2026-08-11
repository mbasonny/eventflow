package com.eventflow.booking.domain.model;

/**
 * Un nombre de places demandé, toujours strictement positif.
 *
 * <p>Réserver zéro place n'a pas de sens métier, et une quantité négative
 * transformerait une réservation en libération. L'invariant est vérifié à la
 * construction : une {@code Quantity} invalide n'existe jamais, donc aucune
 * méthode en aval n'a besoin de la revalider.
 */
public record Quantity(int value) {

    public Quantity {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Une quantité doit être strictement positive, reçu : " + value);
        }
    }

    public static Quantity of(int value) {
        return new Quantity(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
