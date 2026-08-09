package com.eventflow.event.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Une catégorie de places et sa disponibilité")
public record TicketCategoryResponse(
        UUID id,
        String name,
        BigDecimal price,
        String currency,
        int capacity,
        int availableSeats,
        boolean soldOut) {
}
