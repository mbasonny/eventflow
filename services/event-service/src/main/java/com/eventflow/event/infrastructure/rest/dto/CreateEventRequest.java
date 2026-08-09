package com.eventflow.event.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Charge utile de création d'un événement.
 *
 * <p>Un {@code record} dédié, jamais l'entité JPA : exposer une entité couple
 * le contrat public au schéma de la base et ouvre la porte au
 * <em>mass assignment</em>. La validation est déclarative — le contrôleur ne
 * contient aucun {@code if}.
 */
@Schema(description = "Création d'un événement et de ses catégories de places")
public record CreateEventRequest(

        @Schema(example = "Concert de rentrée")
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "Le titre ne peut pas dépasser {max} caractères")
        String title,

        @Schema(example = "Zénith de Paris")
        @NotBlank(message = "Le lieu est obligatoire")
        @Size(max = 200, message = "Le lieu ne peut pas dépasser {max} caractères")
        String venue,

        @Schema(example = "2026-12-31T20:00:00Z")
        @NotNull(message = "La date de début est obligatoire")
        @Future(message = "La date de début doit être dans le futur")
        Instant startsAt,

        // @Valid déclenche la validation de chaque élément : sans lui, les
        // contraintes portées par TicketCategoryRequest seraient ignorées.
        @NotEmpty(message = "Au moins une catégorie de places est requise")
        @Valid
        List<TicketCategoryRequest> categories) {
}
