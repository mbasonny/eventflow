package com.eventflow.event.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Représentation JPA d'un événement.
 *
 * <p>Volontairement distincte de {@code Event} : cette classe sert la base de
 * données (annotations, constructeur sans argument, mutabilité exigée par
 * Hibernate), pas le métier. Le prix à payer est un mapping explicite, assumé
 * dans {@link EventMapper}.
 */
@Entity
@Table(name = "events")
class EventJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    /**
     * {@code orphanRemoval} supprime en base toute catégorie retirée de la
     * liste : l'agrégat reste la seule source de vérité de sa composition.
     */
    @OneToMany(
            mappedBy = "event",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<TicketCategoryJpaEntity> categories = new ArrayList<>();

    /** Requis par Hibernate. */
    protected EventJpaEntity() {
    }

    EventJpaEntity(UUID id, String title, String venue, Instant startsAt) {
        this.id = id;
        this.title = title;
        this.venue = venue;
        this.startsAt = startsAt;
    }

    void addCategory(TicketCategoryJpaEntity category) {
        categories.add(category);
        category.setEvent(this);
    }

    void clearCategories() {
        categories.clear();
    }

    UUID getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    String getVenue() {
        return venue;
    }

    void setVenue(String venue) {
        this.venue = venue;
    }

    Instant getStartsAt() {
        return startsAt;
    }

    void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    List<TicketCategoryJpaEntity> getCategories() {
        return categories;
    }
}
