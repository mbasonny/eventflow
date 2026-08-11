package com.eventflow.booking.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Motif d'annulation")
public record CancelBookingRequest(

        @Schema(example = "Empêchement de dernière minute")
        @NotBlank(message = "Le motif est obligatoire")
        @Size(max = 500, message = "Le motif ne peut pas dépasser {max} caractères")
        String reason) {
}
