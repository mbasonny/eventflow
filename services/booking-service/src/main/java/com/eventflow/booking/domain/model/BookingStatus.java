package com.eventflow.booking.domain.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * États possibles d'une réservation, et transitions autorisées.
 *
 * <pre>
 *   PENDING ──confirm()──▶ CONFIRMED ──cancel()──▶ CANCELLED
 *      │
 *      ├──reject()──▶ REJECTED        (places indisponibles)
 *      └──cancel()──▶ CANCELLED       (abandon avant confirmation)
 * </pre>
 *
 * <p>Les transitions valides sont déclarées ici plutôt que dispersées en
 * {@code if} dans le code appelant : le graphe est lisible d'un coup d'œil, et
 * toute transition non déclarée est refusée par défaut — le comportement sûr.
 *
 * <p>La phase 4 insérera {@code SEATS_RESERVED} et {@code PAID} entre
 * {@code PENDING} et {@code CONFIRMED}, quand la saga deviendra asynchrone.
 */
public enum BookingStatus {

    /** Créée, en attente de la réponse d'event-service. */
    PENDING,

    /** Places obtenues : la réservation est ferme. */
    CONFIRMED,

    /** Places indisponibles : rien n'a été retenu. */
    REJECTED,

    /** Annulée après coup ; les places ont été rendues au stock. */
    CANCELLED;

    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED =
            new EnumMap<>(BookingStatus.class);

    static {
        ALLOWED.put(PENDING, EnumSet.of(CONFIRMED, REJECTED, CANCELLED));
        ALLOWED.put(CONFIRMED, EnumSet.of(CANCELLED));
        ALLOWED.put(REJECTED, EnumSet.noneOf(BookingStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(BookingStatus.class));
    }

    public boolean canTransitionTo(BookingStatus target) {
        return ALLOWED.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    /** Un état final n'accepte plus aucune transition. */
    public boolean isFinal() {
        return ALLOWED.getOrDefault(this, Collections.emptySet()).isEmpty();
    }
}
