package com.eventflow.event.infrastructure.persistence;

import com.eventflow.event.domain.model.CategoryId;
import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.model.Money;
import com.eventflow.event.domain.model.TicketCategory;
import java.util.Currency;
import java.util.List;

/**
 * Traduction entre les objets de domaine et les entités JPA.
 *
 * <p>Ce mapping explicite est le prix de l'architecture hexagonale. Il achète
 * en échange que le domaine ne connaisse ni Hibernate, ni le schéma relationnel :
 * ajouter une colonne technique ou changer de stratégie de chargement ne touche
 * aucune ligne de logique métier.
 */
final class EventMapper {

    private EventMapper() {
    }

    static Event toDomain(EventJpaEntity entity) {
        List<TicketCategory> categories = entity.getCategories().stream()
                .map(EventMapper::toDomain)
                .toList();

        // rehydrate() et non create() : on restaure un état existant sans
        // rejouer les règles de création — un événement passé reste lisible.
        return Event.rehydrate(
                EventId.of(entity.getId()),
                entity.getTitle(),
                entity.getVenue(),
                entity.getStartsAt(),
                categories);
    }

    private static TicketCategory toDomain(TicketCategoryJpaEntity entity) {
        return TicketCategory.rehydrate(
                CategoryId.of(entity.getId()),
                entity.getName(),
                Money.of(entity.getPriceAmount(), Currency.getInstance(entity.getPriceCurrency())),
                entity.getCapacity(),
                entity.getAvailableSeats());
    }

    static EventJpaEntity toJpa(Event event) {
        EventJpaEntity entity = new EventJpaEntity(
                event.id().value(), event.title(), event.venue(), event.startsAt());
        event.categories().forEach(category -> entity.addCategory(toJpa(category)));
        return entity;
    }

    private static TicketCategoryJpaEntity toJpa(TicketCategory category) {
        return new TicketCategoryJpaEntity(
                category.id().value(),
                category.name(),
                category.price().amount(),
                category.price().currency().getCurrencyCode(),
                category.capacity(),
                category.availableSeats());
    }

    /**
     * Reporte l'état du domaine sur une entité déjà gérée par Hibernate.
     *
     * <p>On modifie l'entité attachée plutôt que d'en construire une nouvelle :
     * remplacer l'instance ferait perdre à Hibernate le suivi des modifications
     * et déclencherait des suppressions/insertions inutiles via
     * {@code orphanRemoval}.
     */
    static void applyTo(EventJpaEntity entity, Event event) {
        entity.setTitle(event.title());
        entity.setVenue(event.venue());
        entity.setStartsAt(event.startsAt());

        // Les catégories sont reconstruites intégralement : l'agrégat est la
        // source de vérité de sa composition. Suffisant tant qu'un événement
        // porte quelques catégories ; à revoir si le volume changeait d'ordre.
        entity.clearCategories();
        event.categories().forEach(category -> entity.addCategory(toJpa(category)));
    }
}
