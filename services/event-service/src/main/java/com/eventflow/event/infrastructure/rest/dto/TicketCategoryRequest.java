package com.eventflow.event.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(description = "Une catégorie de places à créer")
public record TicketCategoryRequest(

        @Schema(example = "Fosse")
        @NotBlank(message = "Le nom de la catégorie est obligatoire")
        @Size(max = 100, message = "Le nom ne peut pas dépasser {max} caractères")
        String name,

        @Schema(example = "49.90")
        @NotNull(message = "Le prix est obligatoire")
        @PositiveOrZero(message = "Le prix ne peut pas être négatif")
        @Digits(integer = 10, fraction = 2,
                message = "Le prix accepte au plus {fraction} décimales")
        BigDecimal price,

        @Schema(example = "EUR", defaultValue = "EUR")
        @Pattern(regexp = "[A-Z]{3}", message = "La devise doit être un code ISO 4217")
        String currency,

        @Schema(example = "500")
        @Positive(message = "La capacité doit être strictement positive")
        int capacity) {

    /** La devise est facultative dans la requête ; l'euro est le défaut. */
    public String currencyOrDefault() {
        return currency == null || currency.isBlank() ? "EUR" : currency;
    }
}
