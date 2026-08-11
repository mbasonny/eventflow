package com.eventflow.booking.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Demande de réservation.
 *
 * <p>Aucun champ de prix ni de montant : le serveur les détermine. En phase 6,
 * {@code userId} disparaîtra aussi de la charge utile — il viendra du jeton
 * Keycloak, car un client ne doit pas pouvoir réserver au nom d'un autre.
 */
@Schema(description = "Demande de réservation de places")
public record CreateBookingRequest(

        @Schema(example = "user-42")
        @NotBlank(message = "L'identifiant utilisateur est obligatoire")
        @Size(max = 128, message = "L'identifiant ne peut pas dépasser {max} caractères")
        String userId,

        @NotNull(message = "L'événement est obligatoire")
        UUID eventId,

        @NotNull(message = "La catégorie est obligatoire")
        UUID categoryId,

        @Schema(example = "2")
        @Positive(message = "La quantité doit être strictement positive")
        @Max(value = 50, message = "Au plus {value} places par réservation")
        int quantity) {
}
