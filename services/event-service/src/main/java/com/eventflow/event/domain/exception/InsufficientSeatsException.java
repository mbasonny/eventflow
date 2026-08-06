package com.eventflow.event.domain.exception;

import com.eventflow.event.domain.model.CategoryId;

/** Levée lorsqu'une réservation demande plus de places qu'il n'en reste. */
public class InsufficientSeatsException extends DomainException {

    private final CategoryId categoryId;
    private final int requested;
    private final int available;

    public InsufficientSeatsException(CategoryId categoryId, int requested, int available) {
        super("La catégorie %s n'a que %d place(s) disponible(s), %d demandée(s)"
                .formatted(categoryId.value(), available, requested));
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
