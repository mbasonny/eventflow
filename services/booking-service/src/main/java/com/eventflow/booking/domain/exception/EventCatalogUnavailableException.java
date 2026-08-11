package com.eventflow.booking.domain.exception;

/**
 * Le catalogue est injoignable : délai dépassé, erreur serveur, ou disjoncteur
 * ouvert.
 *
 * <p>C'est le symptôme du <strong>couplage de disponibilité</strong> que la
 * phase 2 cherche justement à faire constater : {@code booking-service} est en
 * parfait état de marche, mais ne peut plus rien faire parce qu'un autre service
 * est tombé. La phase 3 supprimera ce couplage en passant à l'asynchrone.
 *
 * <p>Contrairement aux exceptions métier, celle-ci <em>compte</em> comme un
 * échec pour le disjoncteur.
 */
public class EventCatalogUnavailableException extends DomainException {

    public EventCatalogUnavailableException(String message) {
        super(message);
    }
}
