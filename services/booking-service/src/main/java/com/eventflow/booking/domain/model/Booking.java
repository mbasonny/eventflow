package com.eventflow.booking.domain.model;

import com.eventflow.booking.domain.exception.InvalidBookingTransitionException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Une réservation, et sa machine à états.
 *
 * <p>C'est l'agrégat central de {@code booking-service}. Toute la valeur de
 * cette classe tient dans un point : <strong>aucun changement d'état ne peut
 * avoir lieu sans passer par une transition déclarée valide</strong>. Il n'existe
 * pas de {@code setStatus()} ; confirmer, rejeter et annuler sont des opérations
 * métier qui vérifient d'où l'on part.
 *
 * <p>Ce n'est pas une précaution théorique. En phase 3, Kafka livrera les
 * messages <em>au moins une fois</em> : un {@code payment.succeeded} rejoué
 * tentera de confirmer une réservation déjà annulée. Ici, cette tentative lève
 * une exception au lieu de corrompre l'état.
 *
 * <p>Le montant total est <strong>calculé</strong> à la création, jamais fourni
 * de l'extérieur : un client ne décide pas de ce qu'il paie.
 */
public final class Booking {

    private final BookingId id;
    private final BookingReference reference;
    private final UserId userId;
    private final EventId eventId;
    private final CategoryId categoryId;
    private final Quantity quantity;
    private final Money unitPrice;
    private final Money totalAmount;
    private final Instant createdAt;

    private BookingStatus status;
    private String statusReason;
    private Instant updatedAt;

    private Booking(BookingId id, BookingReference reference, UserId userId, EventId eventId,
                    CategoryId categoryId, Quantity quantity, Money unitPrice, Money totalAmount,
                    BookingStatus status, String statusReason, Instant createdAt,
                    Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "L'identifiant est obligatoire");
        this.reference = Objects.requireNonNull(reference, "La référence est obligatoire");
        this.userId = Objects.requireNonNull(userId, "L'utilisateur est obligatoire");
        this.eventId = Objects.requireNonNull(eventId, "L'événement est obligatoire");
        this.categoryId = Objects.requireNonNull(categoryId, "La catégorie est obligatoire");
        this.quantity = Objects.requireNonNull(quantity, "La quantité est obligatoire");
        this.unitPrice = Objects.requireNonNull(unitPrice, "Le prix unitaire est obligatoire");
        this.totalAmount = Objects.requireNonNull(totalAmount, "Le montant total est obligatoire");
        this.status = Objects.requireNonNull(status, "Le statut est obligatoire");
        this.statusReason = statusReason;
        this.createdAt = Objects.requireNonNull(createdAt, "La date de création est obligatoire");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La date de mise à jour est obligatoire");
    }

    /** Crée une réservation en attente de confirmation. */
    public static Booking create(UserId userId, EventId eventId, CategoryId categoryId,
                                 Quantity quantity, Money unitPrice, Clock clock) {
        Objects.requireNonNull(clock, "L'horloge est obligatoire");
        Objects.requireNonNull(quantity, "La quantité est obligatoire");
        Objects.requireNonNull(unitPrice, "Le prix unitaire est obligatoire");

        Instant now = clock.instant();
        return new Booking(
                BookingId.newId(),
                BookingReference.generate(),
                userId,
                eventId,
                categoryId,
                quantity,
                unitPrice,
                unitPrice.times(quantity),
                BookingStatus.PENDING,
                null,
                now,
                now);
    }

    /** Reconstruit une réservation persistée, sans rejouer les règles de création. */
    public static Booking rehydrate(BookingId id, BookingReference reference, UserId userId,
                                    EventId eventId, CategoryId categoryId, Quantity quantity,
                                    Money unitPrice, Money totalAmount, BookingStatus status,
                                    String statusReason, Instant createdAt, Instant updatedAt) {
        return new Booking(id, reference, userId, eventId, categoryId, quantity, unitPrice,
                totalAmount, status, statusReason, createdAt, updatedAt);
    }

    /** Les places ont été obtenues auprès d'event-service. */
    public void confirm(Clock clock) {
        transitionTo(BookingStatus.CONFIRMED, null, clock);
    }

    /** Les places n'étaient pas disponibles : rien n'a été retenu. */
    public void reject(String reason, Clock clock) {
        transitionTo(BookingStatus.REJECTED, requireReason(reason), clock);
    }

    /** Annulation ; les places déjà retenues doivent être rendues au stock. */
    public void cancel(String reason, Clock clock) {
        transitionTo(BookingStatus.CANCELLED, requireReason(reason), clock);
    }

    /**
     * Point de passage unique de tout changement d'état.
     *
     * <p>Concentrer la vérification ici garantit qu'aucune transition future ne
     * pourra l'oublier : ajouter un état revient à compléter le graphe de
     * {@link BookingStatus}, pas à réécrire des contrôles.
     */
    private void transitionTo(BookingStatus target, String reason, Clock clock) {
        Objects.requireNonNull(clock, "L'horloge est obligatoire");
        if (!status.canTransitionTo(target)) {
            throw new InvalidBookingTransitionException(status, target);
        }
        this.status = target;
        this.statusReason = reason;
        this.updatedAt = clock.instant();
    }

    /** Les places de cette réservation sont-elles retenues sur le stock ? */
    public boolean holdsSeats() {
        return status == BookingStatus.CONFIRMED;
    }

    public boolean isPending() {
        return status == BookingStatus.PENDING;
    }

    public BookingId id() {
        return id;
    }

    public BookingReference reference() {
        return reference;
    }

    public UserId userId() {
        return userId;
    }

    public EventId eventId() {
        return eventId;
    }

    public CategoryId categoryId() {
        return categoryId;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Money unitPrice() {
        return unitPrice;
    }

    public Money totalAmount() {
        return totalAmount;
    }

    public BookingStatus status() {
        return status;
    }

    public Optional<String> statusReason() {
        return Optional.ofNullable(statusReason);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String requireReason(String reason) {
        Objects.requireNonNull(reason, "Le motif est obligatoire");
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Le motif ne peut pas être vide");
        }
        return trimmed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Booking booking && id.equals(booking.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Booking[%s, %s, %s x%s, %s]"
                .formatted(reference, status, categoryId, quantity, totalAmount);
    }
}
