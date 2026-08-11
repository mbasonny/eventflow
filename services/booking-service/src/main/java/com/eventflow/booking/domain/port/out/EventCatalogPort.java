package com.eventflow.booking.domain.port.out;

import com.eventflow.booking.domain.model.CategoryId;
import com.eventflow.booking.domain.model.EventId;
import com.eventflow.booking.domain.model.Money;
import com.eventflow.booking.domain.model.Quantity;

/**
 * Port sortant : ce que {@code booking-service} attend du catalogue.
 *
 * <p>Le contrat est exprimé en termes métier — « donne-moi le prix », « retiens
 * ces places ». Ni HTTP, ni URL, ni code de statut n'apparaissent ici. En
 * phase 3, l'implémentation deviendra un producer Kafka sans que cette interface
 * ni la couche applicative ne changent.
 *
 * <p>L'interface distingue trois issues, et c'est le point important :
 * <ul>
 *   <li>{@code SeatsUnavailableException} — refus métier, le catalogue va bien
 *   <li>{@code CategoryUnavailableException} — erreur du client
 *   <li>{@code EventCatalogUnavailableException} — panne du catalogue
 * </ul>
 * Confondre les deux premières avec la troisième ferait ouvrir le disjoncteur
 * sur des refus parfaitement légitimes.
 */
public interface EventCatalogPort {

    /**
     * Prix et disponibilité d'une catégorie, sans rien réserver.
     *
     * @throws com.eventflow.booking.domain.exception.CategoryUnavailableException si inconnue
     * @throws com.eventflow.booking.domain.exception.EventCatalogUnavailableException si injoignable
     */
    CategorySnapshot findCategory(EventId eventId, CategoryId categoryId);

    /**
     * Retire des places du stock du catalogue.
     *
     * @throws com.eventflow.booking.domain.exception.SeatsUnavailableException si stock insuffisant
     * @throws com.eventflow.booking.domain.exception.CategoryUnavailableException si inconnue
     * @throws com.eventflow.booking.domain.exception.EventCatalogUnavailableException si injoignable
     */
    CategorySnapshot reserveSeats(EventId eventId, CategoryId categoryId, Quantity quantity);

    /** Photographie d'une catégorie à un instant donné. */
    record CategorySnapshot(
            CategoryId id, String name, Money price, int capacity, int availableSeats) {
    }
}
