package com.eventflow.event.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Représentation JPA d'une catégorie de places. */
@Entity
@Table(name = "ticket_categories")
@Getter(AccessLevel.PACKAGE)
class TicketCategoryJpaEntity {

    @Id
    private UUID id;

    /**
     * Aucun getter généré : exposer le parent inviterait à remonter
     * l'association depuis un enfant, ce qui déclenche un chargement paresseux
     * inattendu. On navigue toujours depuis la racine d'agrégat.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.PACKAGE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private EventJpaEntity event;

    @Column(nullable = false, length = 100)
    private String name;

    // Money est éclaté en deux colonnes : un montant sans sa devise n'a pas de
    // sens, et NUMERIC garantit l'exactitude décimale qu'un double ne donne pas.
    @Column(name = "price_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAmount;

    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    /** Requis par Hibernate. */
    protected TicketCategoryJpaEntity() {
    }

    TicketCategoryJpaEntity(UUID id, String name, BigDecimal priceAmount, String priceCurrency,
                            int capacity, int availableSeats) {
        this.id = id;
        this.name = name;
        this.priceAmount = priceAmount;
        this.priceCurrency = priceCurrency;
        this.capacity = capacity;
        this.availableSeats = availableSeats;
    }
}
