package com.eventflow.event.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@Schema(description = "Modification des informations générales d'un événement")
public record UpdateEventRequest(

        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "Le titre ne peut pas dépasser {max} caractères")
        String title,

        @NotBlank(message = "Le lieu est obligatoire")
        @Size(max = 200, message = "Le lieu ne peut pas dépasser {max} caractères")
        String venue,

        @NotNull(message = "La date de début est obligatoire")
        @Future(message = "La date de début doit être dans le futur")
        Instant startsAt) {
}
