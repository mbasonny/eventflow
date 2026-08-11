package com.eventflow.booking.infrastructure.rest;

import com.eventflow.booking.domain.exception.BookingNotFoundException;
import com.eventflow.booking.domain.exception.CategoryUnavailableException;
import com.eventflow.booking.domain.exception.EventCatalogUnavailableException;
import com.eventflow.booking.domain.exception.InvalidBookingTransitionException;
import com.eventflow.booking.domain.exception.SeatsUnavailableException;
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

/** Traduction unique des exceptions en réponses HTTP, au format RFC 7807. */
@RestControllerAdvice
@RequiredArgsConstructor
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BASE_TYPE = "https://eventflow.dev/problems/";

    private final Clock clock;

    @ExceptionHandler(BookingNotFoundException.class)
    ProblemDetail handleBookingNotFound(BookingNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Réservation introuvable",
                exception.getMessage(), "booking-not-found");
    }

    @ExceptionHandler(CategoryUnavailableException.class)
    ProblemDetail handleCategoryUnavailable(CategoryUnavailableException exception) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Catégorie introuvable",
                exception.getMessage(), "category-not-found");
        problem.setProperty("eventId", exception.eventId().value());
        problem.setProperty("categoryId", exception.categoryId().value());
        return problem;
    }

    /** Refus métier : la requête est valide, le stock ne suffit pas. */
    @ExceptionHandler(SeatsUnavailableException.class)
    ProblemDetail handleSeatsUnavailable(SeatsUnavailableException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Places insuffisantes",
                exception.getMessage(), "insufficient-seats");
        problem.setProperty("requested", exception.requested());
        problem.setProperty("available", exception.available());
        return problem;
    }

    /**
     * 503 avec {@code Retry-After} : le service est momentanément incapable de
     * traiter la demande, mais la requête était correcte. Répondre 500 laisserait
     * croire à un bug de booking-service alors que la panne est ailleurs.
     *
     * <p>C'est le couplage de disponibilité rendu visible dans le contrat d'API —
     * exactement ce que la phase 3 supprimera.
     */
    @ExceptionHandler(EventCatalogUnavailableException.class)
    ProblemDetail handleCatalogUnavailable(EventCatalogUnavailableException exception) {
        log.warn("Catalogue indisponible : {}", exception.getMessage());
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "Service indisponible",
                exception.getMessage(), "catalog-unavailable");
        problem.setProperty("retryAfterSeconds", 10);
        return problem;
    }

    /** Transition d'état interdite : confirmer une réservation déjà annulée. */
    @ExceptionHandler(InvalidBookingTransitionException.class)
    ProblemDetail handleInvalidTransition(InvalidBookingTransitionException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Opération impossible",
                exception.getMessage(), "invalid-transition");
        problem.setProperty("currentStatus", exception.from().name());
        problem.setProperty("requestedStatus", exception.to().name());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.merge(error.getField(), error.getDefaultMessage(),
                        (first, second) -> first + " ; " + second));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Requête invalide",
                "Un ou plusieurs champs sont invalides", "validation-error");
        problem.setProperty("errors", errors);
        return problem;
    }

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.debug("Corps de requête illisible", exception);
        return problem(HttpStatus.BAD_REQUEST, "Corps de requête illisible",
                "Le corps de la requête n'est pas un JSON valide ou ne correspond pas au contrat",
                "malformed-request");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Paramètre invalide",
                "La valeur fournie pour « %s » n'a pas le format attendu"
                        .formatted(exception.getName()), "invalid-parameter");
        problem.setProperty("parameter", exception.getName());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête invalide",
                exception.getMessage(), "invalid-request");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
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
