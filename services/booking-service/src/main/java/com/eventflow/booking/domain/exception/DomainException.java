package com.eventflow.booking.domain.exception;

/** Racine des exceptions métier de booking-service. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
