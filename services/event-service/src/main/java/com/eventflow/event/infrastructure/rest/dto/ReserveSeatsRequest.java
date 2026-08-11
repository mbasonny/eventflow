package com.eventflow.event.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

@Schema(description = "Demande de réservation de places dans une catégorie")
public record ReserveSeatsRequest(

        @Schema(example = "2")
        @Positive(message = "La quantité doit être strictement positive")
        // Garde-fou contre une saisie aberrante ou un client hostile : personne
        // ne réserve 10 000 places en une requête.
        @Max(value = 50, message = "Au plus {value} places par réservation")
        int quantity) {
}
