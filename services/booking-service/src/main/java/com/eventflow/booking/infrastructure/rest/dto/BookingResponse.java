package com.eventflow.booking.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Une réservation")
public record BookingResponse(
        UUID id,
        String reference,
        String userId,
        UUID eventId,
        UUID categoryId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String currency,
        String status,
        String statusReason,
        Instant createdAt,
        Instant updatedAt) {
}
