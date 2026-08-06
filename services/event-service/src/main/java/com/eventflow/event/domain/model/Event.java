package com.eventflow.event.domain.model;

import com.eventflow.event.domain.exception.CategoryNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Un événement et ses catégories de places.
 *
 * <p><strong>Racine d'agrégat</strong> : on ne manipule jamais une
 * {@link TicketCategory} directement depuis l'extérieur, on passe toujours par
 * l'événement qui la contient. C'est ce qui garantit qu'une réservation ne peut
 * pas viser une catégorie appartenant à un autre événement.
 */
public final class Event {

    private final EventId id;
    private String title;
    private String venue;
    private Instant startsAt;
    private final List<TicketCategory> categories;

    private Event(EventId id, String title, String venue, Instant startsAt,
                  List<TicketCategory> categories) {
        this.id = Objects.requireNonNull(id, "L'identifiant de l'événement est obligatoire");
        this.title = requireText(title, "Le titre est obligatoire");
        this.venue = requireText(venue, "Le lieu est obligatoire");
        this.startsAt = Objects.requireNonNull(startsAt, "La date de début est obligatoire");
        this.categories = new ArrayList<>(Objects.requireNonNull(categories));
    }

    /**
     * Crée un événement neuf.
     *
     * <p>L'horloge est un paramètre plutôt qu'un appel à {@code Instant.now()} :
     * la règle « la date de début est dans le futur » devient testable de façon
     * déterministe, sans {@code Thread.sleep} ni date calculée à la volée.
     */
    public static Event create(String title, String venue, Instant startsAt, Clock clock) {
        Objects.requireNonNull(clock, "L'horloge est obligatoire");
        Objects.requireNonNull(startsAt, "La date de début est obligatoire");
        if (!startsAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException(
                    "La date de début doit être dans le futur, reçu : " + startsAt);
        }
        return new Event(EventId.newId(), title, venue, startsAt, List.of());
    }

    /**
     * Reconstruit un événement déjà persisté, sans rejouer les règles de
     * création : un événement passé reste chargeable et consultable.
     */
    public static Event rehydrate(EventId id, String title, String venue, Instant startsAt,
                                  List<TicketCategory> categories) {
        return new Event(id, title, venue, startsAt, categories);
    }

    public void addCategory(TicketCategory category) {
        Objects.requireNonNull(category, "La catégorie est obligatoire");
        boolean duplicateName = categories.stream()
                .anyMatch(existing -> existing.name().equalsIgnoreCase(category.name()));
        if (duplicateName) {
            throw new IllegalArgumentException(
                    "Une catégorie nommée « %s » existe déjà pour cet événement"
                            .formatted(category.name()));
        }
        categories.add(category);
    }

    /** Réserve des places dans une catégorie de <em>cet</em> événement. */
    public void reserveSeats(CategoryId categoryId, Quantity quantity) {
        category(categoryId).reserve(quantity);
    }

    /** Libère des places précédemment réservées — compensation de la saga. */
    public void releaseSeats(CategoryId categoryId, Quantity quantity) {
        category(categoryId).release(quantity);
    }

    public TicketCategory category(CategoryId categoryId) {
        Objects.requireNonNull(categoryId, "L'identifiant de catégorie est obligatoire");
        return categories.stream()
                .filter(category -> category.id().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    public void rename(String newTitle) {
        this.title = requireText(newTitle, "Le titre est obligatoire");
    }

    public void relocate(String newVenue) {
        this.venue = requireText(newVenue, "Le lieu est obligatoire");
    }

    public void reschedule(Instant newStartsAt, Clock clock) {
        Objects.requireNonNull(clock, "L'horloge est obligatoire");
        Objects.requireNonNull(newStartsAt, "La date de début est obligatoire");
        if (!newStartsAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException(
                    "La nouvelle date doit être dans le futur, reçu : " + newStartsAt);
        }
        this.startsAt = newStartsAt;
    }

    public int totalCapacity() {
        return categories.stream().mapToInt(TicketCategory::capacity).sum();
    }

    public int totalAvailableSeats() {
        return categories.stream().mapToInt(TicketCategory::availableSeats).sum();
    }

    public boolean isSoldOut() {
        return !categories.isEmpty() && categories.stream().allMatch(TicketCategory::isSoldOut);
    }

    public EventId id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String venue() {
        return venue;
    }

    public Instant startsAt() {
        return startsAt;
    }

    /** Vue non modifiable : la liste ne se manipule que par les méthodes de l'agrégat. */
    public List<TicketCategory> categories() {
        return Collections.unmodifiableList(categories);
    }

    private static String requireText(String value, String message) {
        Objects.requireNonNull(value, message);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Event event && id.equals(event.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Event[%s, %s, %s, %d catégorie(s)]".formatted(title, venue, startsAt, categories.size());
    }
}
