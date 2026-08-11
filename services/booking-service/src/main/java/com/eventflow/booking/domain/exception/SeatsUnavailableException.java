package com.eventflow.booking.domain.exception;

import com.eventflow.booking.domain.model.CategoryId;

/**
 * Le catalogue a refusé la réservation faute de places.
 *
 * <p>C'est un résultat métier <strong>normal</strong>, pas une panne. Elle est
 * donc explicitement ignorée par le disjoncteur : un concert complet génère des
 * milliers de refus légitimes, et compter ces refus comme des échecs ouvrirait
 * le circuit alors qu'{@code event-service} se porte parfaitement bien.
 */
public class SeatsUnavailableException extends DomainException {

    private final CategoryId categoryId;
    private final int requested;
    private final int available;

    public SeatsUnavailableException(CategoryId categoryId, int requested, int available) {
        super("Places insuffisantes dans la catégorie %s : %d demandée(s), %d disponible(s)"
                .formatted(categoryId, requested, available));
        this.categoryId = categoryId;
        this.requested = requested;
        this.available = available;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public int requested() {
        return requested;
    }

    public int available() {
        return available;
    }
}
