package com.eventflow.booking.infrastructure.client;

import com.eventflow.booking.domain.exception.CategoryUnavailableException;
import com.eventflow.booking.domain.exception.EventCatalogUnavailableException;
import com.eventflow.booking.domain.exception.SeatsUnavailableException;
import com.eventflow.booking.domain.model.CategoryId;
import com.eventflow.booking.domain.model.EventId;
import com.eventflow.booking.domain.model.Money;
import com.eventflow.booking.domain.model.Quantity;
import com.eventflow.booking.domain.port.out.EventCatalogPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.Currency;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adaptateur HTTP synchrone vers {@code event-service}.
 *
 * <p><strong>C'est le point sensible de la phase 2.</strong> Cet appel couple la
 * disponibilité des deux services : si le catalogue tombe, plus aucune
 * réservation n'est possible, alors que {@code booking-service} va parfaitement
 * bien. Resilience4j limite les dégâts, il ne supprime pas le couplage — seul le
 * passage à l'asynchrone (phase 3) le fera.
 *
 * <h2>Traduire les codes HTTP en langage métier</h2>
 *
 * <pre>
 *   404  →  CategoryUnavailableException     erreur du client
 *   409  →  SeatsUnavailableException        refus métier légitime
 *   5xx  →  EventCatalogUnavailableException panne
 *   timeout / connexion refusée  →  EventCatalogUnavailableException
 * </pre>
 *
 * <p>Cette distinction n'est pas cosmétique : les deux premières sont déclarées
 * en {@code ignore-exceptions} dans la configuration du disjoncteur. Sans ça, un
 * concert complet — des milliers de 409 parfaitement normaux — ferait grimper le
 * taux d'échec et ouvrirait le circuit sur un service en pleine santé.
 */
@Component
@RequiredArgsConstructor
class EventServiceClient implements EventCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(EventServiceClient.class);
    private static final String INSTANCE = "event-service";

    private final RestClient restClient;

    @Override
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE, fallbackMethod = "findCategoryFallback")
    public CategorySnapshot findCategory(EventId eventId, CategoryId categoryId) {
        CategoryPayload payload = restClient.get()
                .uri("/api/v1/events/{eventId}/categories/{categoryId}/availability",
                        eventId.value(), categoryId.value())
                .exchange((request, response) -> switch (response.getStatusCode()) {
                    case HttpStatus.OK -> response.bodyTo(CategoryPayload.class);
                    case HttpStatus.NOT_FOUND ->
                            throw new CategoryUnavailableException(eventId, categoryId);
                    default -> throw new EventCatalogUnavailableException(
                            "Le catalogue a répondu " + response.getStatusCode());
                });

        return toSnapshot(payload);
    }

    @Override
    @Retry(name = INSTANCE)
    @CircuitBreaker(name = INSTANCE, fallbackMethod = "reserveSeatsFallback")
    public CategorySnapshot reserveSeats(EventId eventId, CategoryId categoryId,
                                         Quantity quantity) {
        CategoryPayload payload = restClient.post()
                .uri("/api/v1/events/{eventId}/categories/{categoryId}/reservations",
                        eventId.value(), categoryId.value())
                .body(new ReservationPayload(quantity.value()))
                .exchange((request, response) -> switch (response.getStatusCode()) {
                    case HttpStatus.OK -> response.bodyTo(CategoryPayload.class);
                    case HttpStatus.NOT_FOUND ->
                            throw new CategoryUnavailableException(eventId, categoryId);
                    case HttpStatus.CONFLICT -> {
                        // Le ProblemDetail d'event-service porte requested/available :
                        // on les propage pour que le client final sache combien de
                        // places restent, au lieu d'un « c'est complet » opaque.
                        ProblemPayload problem = response.bodyTo(ProblemPayload.class);
                        throw new SeatsUnavailableException(
                                categoryId,
                                problem == null ? quantity.value() : problem.requested(),
                                problem == null ? 0 : problem.available());
                    }
                    default -> throw new EventCatalogUnavailableException(
                            "Le catalogue a répondu " + response.getStatusCode());
                });

        return toSnapshot(payload);
    }

    /**
     * Repli déclenché par le disjoncteur ou par un échec technique.
     *
     * <p>Les exceptions métier <strong>doivent être relancées telles quelles</strong> :
     * Resilience4j route vers le repli toute exception non ignorée, et un repli
     * qui avalerait un 409 transformerait « il ne reste plus de place » en
     * « service indisponible ». Le client recevrait 503 au lieu de 409, et
     * réessaierait indéfiniment une requête qui ne peut pas aboutir.
     */
    @SuppressWarnings("unused")
    private CategorySnapshot findCategoryFallback(
            EventId eventId, CategoryId categoryId, Throwable cause) {
        return rethrowOrDegrade(cause);
    }

    @SuppressWarnings("unused")
    private CategorySnapshot reserveSeatsFallback(
            EventId eventId, CategoryId categoryId, Quantity quantity, Throwable cause) {
        return rethrowOrDegrade(cause);
    }

    private CategorySnapshot rethrowOrDegrade(Throwable cause) {
        if (cause instanceof SeatsUnavailableException seatsUnavailable) {
            throw seatsUnavailable;
        }
        if (cause instanceof CategoryUnavailableException categoryUnavailable) {
            throw categoryUnavailable;
        }
        log.warn("Catalogue injoignable, repli activé : {}", cause.toString());
        throw new EventCatalogUnavailableException(
                "Le catalogue est momentanément indisponible, réessayez plus tard");
    }

    private static CategorySnapshot toSnapshot(CategoryPayload payload) {
        return new CategorySnapshot(
                CategoryId.of(payload.id()),
                payload.name(),
                Money.of(payload.price(), Currency.getInstance(payload.currency())),
                payload.capacity(),
                payload.availableSeats());
    }

    /** Miroir du contrat d'event-service, volontairement local à l'adaptateur. */
    private record CategoryPayload(
            String id, String name, BigDecimal price, String currency,
            int capacity, int availableSeats) {
    }

    private record ReservationPayload(int quantity) {
    }

    /** Extensions du ProblemDetail renvoyé par event-service en 409. */
    private record ProblemPayload(int requested, int available) {
    }
}
