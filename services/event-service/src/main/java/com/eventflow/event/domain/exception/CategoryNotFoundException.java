package com.eventflow.event.domain.exception;

import com.eventflow.event.domain.model.CategoryId;

/** Levée lorsqu'une catégorie référencée n'appartient pas à l'événement. */
public class CategoryNotFoundException extends DomainException {

    private final CategoryId categoryId;

    public CategoryNotFoundException(CategoryId categoryId) {
        super("Catégorie introuvable : " + categoryId.value());
        this.categoryId = categoryId;
    }

    public CategoryId categoryId() {
        return categoryId;
    }
}
