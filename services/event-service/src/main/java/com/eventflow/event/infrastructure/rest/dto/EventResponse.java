package com.eventflow.event.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Un événement du catalogue")
public record EventResponse(
        UUID id,
        String title,
        String venue,
        Instant startsAt,
        int totalCapacity,
        int totalAvailableSeats,
        boolean soldOut,
        List<TicketCategoryResponse> categories) {
}
