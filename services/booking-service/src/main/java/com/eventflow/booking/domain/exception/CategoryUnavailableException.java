package com.eventflow.booking.domain.exception;

import com.eventflow.booking.domain.model.CategoryId;
import com.eventflow.booking.domain.model.EventId;

/**
 * L'événement ou la catégorie demandé n'existe pas dans le catalogue.
 *
 * <p>Erreur du client, pas panne du service : ignorée par le disjoncteur au même
 * titre que {@link SeatsUnavailableException}.
 */
public class CategoryUnavailableException extends DomainException {

    private final EventId eventId;
    private final CategoryId categoryId;

    public CategoryUnavailableException(EventId eventId, CategoryId categoryId) {
        super("Catégorie %s introuvable pour l'événement %s".formatted(categoryId, eventId));
        this.eventId = eventId;
        this.categoryId = categoryId;
    }

    public EventId eventId() {
        return eventId;
    }

    public CategoryId categoryId() {
        return categoryId;
    }
}
