package com.eventflow.event.domain.exception;

/**
 * Racine des exceptions métier.
 *
 * <p>Distinguer les exceptions métier des exceptions techniques permet au
 * {@code @RestControllerAdvice} de traduire les premières en réponses HTTP
 * porteuses de sens (409, 404…) et de traiter les secondes en 500 sans jamais
 * exposer de détail d'implémentation au client.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
