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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * Représentation JPA d'un événement.
 *
 * <p>Volontairement distincte de {@code Event} : cette classe sert la base de
 * données (annotations, constructeur sans argument, mutabilité exigée par
 * Hibernate), pas le métier. Le prix à payer est un mapping explicite, assumé
 * dans {@link EventMapper}.
 *
 * <p>Lombok se limite ici aux accesseurs, en visibilité <em>package</em> : rien
 * ne sort de {@code infrastructure.persistence}. Pas de {@code @Data} — son
 * {@code equals}/{@code hashCode} couvrirait la collection {@code LAZY} et
 * lèverait une {@code LazyInitializationException} dès qu'on placerait
 * l'entité dans un {@code HashSet}.
 */
@Entity
@Table(name = "events")
@Getter(AccessLevel.PACKAGE)
class EventJpaEntity {

    @Id
    private UUID id;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false, length = 200)
    private String title;

    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false, length = 200)
    private String venue;

    @Setter(AccessLevel.PACKAGE)
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

    // Les deux méthodes ci-dessous restent écrites à la main : elles portent une
    // règle (maintenir les deux côtés de l'association), pas un simple accès.

    void addCategory(TicketCategoryJpaEntity category) {
        categories.add(category);
        category.setEvent(this);
    }

    void clearCategories() {
        categories.clear();
    }
}
