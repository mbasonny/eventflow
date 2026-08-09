package com.eventflow.event.infrastructure.rest;

import com.eventflow.event.domain.exception.CategoryNotFoundException;
import com.eventflow.event.domain.exception.EventNotFoundException;
import com.eventflow.event.domain.exception.InsufficientSeatsException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduction unique des exceptions en réponses HTTP, au format
 * <strong>ProblemDetail (RFC 7807)</strong>.
 *
 * <p>Un seul point de traduction par service : les contrôleurs ne contiennent
 * aucun {@code try/catch}, et le format d'erreur est homogène sur toute l'API.
 *
 * <p>Règle absolue : jamais de trace d'exécution ni de message technique dans la
 * réponse. Les exceptions inattendues sont journalisées côté serveur et rendues
 * au client sous une forme neutre.
 */
@RestControllerAdvice
@RequiredArgsConstructor
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BASE_TYPE = "https://eventflow.dev/problems/";

    private final Clock clock;

    @ExceptionHandler(EventNotFoundException.class)
    ProblemDetail handleEventNotFound(EventNotFoundException exception) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Événement introuvable",
                exception.getMessage(), "event-not-found");
        problem.setProperty("eventId", exception.eventId().value());
        return problem;
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ProblemDetail handleCategoryNotFound(CategoryNotFoundException exception) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Catégorie introuvable",
                exception.getMessage(), "category-not-found");
        problem.setProperty("categoryId", exception.categoryId().value());
        return problem;
    }

    /**
     * 409 et non 400 : la requête est bien formée, c'est l'état courant du stock
     * qui empêche de la satisfaire. Le client peut réessayer avec moins de
     * places, ou plus tard si des places se libèrent.
     */
    @ExceptionHandler(InsufficientSeatsException.class)
    ProblemDetail handleInsufficientSeats(InsufficientSeatsException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Places insuffisantes",
                exception.getMessage(), "insufficient-seats");
        problem.setProperty("categoryId", exception.categoryId().value());
        problem.setProperty("requested", exception.requested());
        problem.setProperty("available", exception.available());
        return problem;
    }

    /** Échec de validation d'un corps de requête annoté {@code @Valid}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.merge(error.getField(), error.getDefaultMessage(),
                        (first, second) -> first + " ; " + second));
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.put(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Requête invalide",
                "Un ou plusieurs champs sont invalides", "validation-error");
        // Le détail par champ permet au client d'afficher l'erreur au bon endroit
        // du formulaire, au lieu d'un message global inexploitable.
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Échec de validation d'un paramètre de requête ({@code @Min}, {@code @Max}…). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                errors.put(violation.getPropertyPath().toString(), violation.getMessage()));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Requête invalide",
                "Un ou plusieurs paramètres sont invalides", "validation-error");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Invariant du domaine violé par une entrée qui a passé la validation
     * déclarative — par exemple deux catégories de même nom.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête invalide",
                exception.getMessage(), "invalid-request");
    }

    /**
     * Corps de requête illisible : JSON malformé, type incompatible, corps vide.
     *
     * <p>C'est une erreur du client, donc 400. Sans ce gestionnaire l'exception
     * tomberait dans le catch-all et renverrait 500 — un serveur qui s'accuse
     * d'une faute qu'il n'a pas commise, et une alerte de supervision pour rien.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        // Le message d'origine expose le parseur Jackson et la position exacte :
        // information interne, sans valeur pour le client.
        log.debug("Corps de requête illisible", exception);
        return problem(HttpStatus.BAD_REQUEST, "Corps de requête illisible",
                "Le corps de la requête n'est pas un JSON valide ou ne correspond pas au contrat",
                "malformed-request");
    }

    /** Paramètre de chemin ou de requête du mauvais type — un UUID mal formé, par exemple. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Paramètre invalide",
                "La valeur fournie pour « %s » n'a pas le format attendu"
                        .formatted(exception.getName()),
                "invalid-parameter");
        problem.setProperty("parameter", exception.getName());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        // Journalisé avec la trace complète côté serveur, jamais renvoyé au client.
        log.error("Erreur inattendue", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne",
                "Une erreur inattendue est survenue", "internal-error");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(BASE_TYPE + type));
        problem.setProperty("timestamp", clock.instant());
        return problem;
    }
}
