package com.eventflow.booking.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Référence d'un événement appartenant à {@code event-service}.
 *
 * <p>Identifiant <strong>étranger</strong> : booking-service ne crée jamais
 * d'événement, il ne fait que le désigner. Pas de fabrique {@code newId()} —
 * générer une référence vers une ressource dont on n'est pas propriétaire n'a
 * aucun sens.
 *
 * <p>Le type est redéfini ici plutôt que partagé via une bibliothèque commune :
 * un module partagé recoupleraient deux services qu'on a justement séparés, et
 * imposerait de redéployer les deux à chaque changement.
 */
public record EventId(UUID value) {

    public EventId {
        Objects.requireNonNull(value, "L'identifiant d'événement est obligatoire");
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
