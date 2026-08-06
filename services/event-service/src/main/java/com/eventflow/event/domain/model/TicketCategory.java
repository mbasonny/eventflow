package com.eventflow.event.domain.model;

import com.eventflow.event.domain.exception.InsufficientSeatsException;
import java.util.Objects;

/**
 * Une catégorie de places : un nom, un prix, une capacité, et le stock restant.
 *
 * <p>C'est ici que vit la seule règle réellement critique du système :
 * <strong>on ne vend jamais plus de places qu'il n'en existe</strong>. La règle
 * est portée par l'objet lui-même, pas par un service applicatif — personne ne
 * peut modifier {@code availableSeats} sans passer par {@link #reserve} ou
 * {@link #release}.
 *
 * <p>Invariant maintenu en permanence : {@code 0 <= availableSeats <= capacity}.
 *
 * <p>Cette classe ne protège <em>pas</em> contre les accès concurrents : deux
 * threads peuvent lire le même stock et réserver chacun. C'est volontaire à ce
 * stade du projet — la phase 3 traitera le problème par verrouillage optimiste.
 */
public final class TicketCategory {

    private final CategoryId id;
    private final String name;
    private final Money price;
    private final int capacity;
    private int availableSeats;

    private TicketCategory(CategoryId id, String name, Money price, int capacity, int availableSeats) {
        this.id = Objects.requireNonNull(id, "L'identifiant de catégorie est obligatoire");
        this.name = requireValidName(name);
        this.price = Objects.requireNonNull(price, "Le prix est obligatoire");
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "La capacité doit être strictement positive, reçu : " + capacity);
        }
        if (availableSeats < 0 || availableSeats > capacity) {
            throw new IllegalArgumentException(
                    "Places disponibles hors bornes : %d pour une capacité de %d"
                            .formatted(availableSeats, capacity));
        }
        this.capacity = capacity;
        this.availableSeats = availableSeats;
    }

    /** Crée une catégorie neuve : toutes les places sont disponibles. */
    public static TicketCategory create(String name, Money price, int capacity) {
        return new TicketCategory(CategoryId.newId(), name, price, capacity, capacity);
    }

    /**
     * Reconstruit une catégorie déjà persistée.
     *
     * <p>Distinct de {@link #create} : on restaure un état existant, sans
     * régénérer d'identifiant ni forcer le stock à la capacité. Les invariants
     * sont malgré tout revérifiés — une donnée corrompue en base doit échouer
     * au chargement, pas plus tard au milieu d'une réservation.
     */
    public static TicketCategory rehydrate(
            CategoryId id, String name, Money price, int capacity, int availableSeats) {
        return new TicketCategory(id, name, price, capacity, availableSeats);
    }

    /**
     * Retire {@code quantity} places du stock.
     *
     * @throws InsufficientSeatsException si le stock restant est insuffisant ;
     *     l'état de l'objet est alors inchangé.
     */
    public void reserve(Quantity quantity) {
        Objects.requireNonNull(quantity, "La quantité est obligatoire");
        if (quantity.value() > availableSeats) {
            throw new InsufficientSeatsException(id, quantity.value(), availableSeats);
        }
        availableSeats -= quantity.value();
    }

    /**
     * Remet {@code quantity} places dans le stock — la compensation d'un
     * paiement échoué (phase 4).
     *
     * @throws IllegalArgumentException si la libération dépasserait la capacité,
     *     ce qui signalerait un bug de compensation plutôt qu'une erreur métier.
     */
    public void release(Quantity quantity) {
        Objects.requireNonNull(quantity, "La quantité est obligatoire");
        int restored = availableSeats + quantity.value();
        if (restored > capacity) {
            throw new IllegalArgumentException(
                    "Libérer %d place(s) porterait le stock à %d pour une capacité de %d"
                            .formatted(quantity.value(), restored, capacity));
        }
        availableSeats = restored;
    }

    public boolean hasAvailability(Quantity quantity) {
        Objects.requireNonNull(quantity, "La quantité est obligatoire");
        return quantity.value() <= availableSeats;
    }

    public boolean isSoldOut() {
        return availableSeats == 0;
    }

    public int reservedSeats() {
        return capacity - availableSeats;
    }

    public CategoryId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Money price() {
        return price;
    }

    public int capacity() {
        return capacity;
    }

    public int availableSeats() {
        return availableSeats;
    }

    private static String requireValidName(String name) {
        Objects.requireNonNull(name, "Le nom de la catégorie est obligatoire");
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Le nom de la catégorie ne peut pas être vide");
        }
        return trimmed;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TicketCategory category && id.equals(category.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "TicketCategory[%s, %s, %d/%d disponibles]"
                .formatted(name, price, availableSeats, capacity);
    }
}
