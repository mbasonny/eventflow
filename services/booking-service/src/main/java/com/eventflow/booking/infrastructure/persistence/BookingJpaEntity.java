package com.eventflow.booking.infrastructure.persistence;

import com.eventflow.booking.domain.model.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Représentation JPA d'une réservation. */
@Entity
@Table(name = "bookings")
@Getter(AccessLevel.PACKAGE)
class BookingJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 16)
    private String reference;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * {@code EnumType.STRING} et jamais {@code ORDINAL} : avec l'ordinal, insérer
     * un état au milieu de l'énumération Java réinterpréterait silencieusement
     * toutes les lignes déjà écrites — une corruption de données invisible.
     */
    @Setter(AccessLevel.PACKAGE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Setter(AccessLevel.PACKAGE)
    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter(AccessLevel.PACKAGE)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Requis par Hibernate. */
    protected BookingJpaEntity() {
    }

    BookingJpaEntity(UUID id, String reference, String userId, UUID eventId, UUID categoryId,
                     int quantity, BigDecimal unitAmount, BigDecimal totalAmount, String currency,
                     BookingStatus status, String statusReason, Instant createdAt,
                     Instant updatedAt) {
        this.id = id;
        this.reference = reference;
        this.userId = userId;
        this.eventId = eventId;
        this.categoryId = categoryId;
        this.quantity = quantity;
        this.unitAmount = unitAmount;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = status;
        this.statusReason = statusReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
