package com.eventflow.booking.application.service;

import com.eventflow.booking.domain.exception.BookingNotFoundException;
import com.eventflow.booking.domain.exception.SeatsUnavailableException;
import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.BookingId;
import com.eventflow.booking.domain.port.in.CancelBookingUseCase;
import com.eventflow.booking.domain.port.in.CreateBookingUseCase;
import com.eventflow.booking.domain.port.out.BookingRepository;
import com.eventflow.booking.domain.port.out.EventCatalogPort;
import com.eventflow.booking.domain.port.out.EventCatalogPort.CategorySnapshot;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestration d'une réservation, en mode synchrone (phase 2).
 *
 * <h2>Pourquoi cette classe n'est pas {@code @Transactional}</h2>
 *
 * <p>Elle appelle {@code event-service} par le réseau. Envelopper l'ensemble
 * dans une transaction maintiendrait une connexion PostgreSQL ouverte pendant
 * tout l'appel HTTP — jusqu'à 3 secondes de délai de lecture. Sous charge, le
 * pool de connexions se vide en attendant un service distant, et la base devient
 * le goulot d'étranglement d'une panne qui ne la concerne pas.
 *
 * <p>La règle : <strong>jamais d'appel réseau à l'intérieur d'une transaction
 * base.</strong> Chaque {@code save()} est atomique de lui-même — Spring Data
 * rend {@code SimpleJpaRepository.save} transactionnel — et c'est la seule
 * atomicité dont on a besoin ici.
 *
 * <p>À ne pas faire : déclarer une méthode {@code @Transactional} privée de
 * cette classe et l'appeler depuis {@code book()}. L'auto-invocation ne traverse
 * pas le proxy Spring : l'annotation serait purement décorative, et on croirait
 * à tort disposer d'une transaction.
 *
 * <h2>Ce que cette implémentation ne sait pas faire</h2>
 *
 * <p>Entre la réservation des places chez {@code event-service} et
 * l'enregistrement local du statut confirmé, il existe une fenêtre. Un crash au
 * mauvais moment laisse des places retenues pour une réservation restée
 * {@code PENDING} : personne ne les libérera. Il n'y a pas de transaction ACID
 * entre deux services — c'est précisément ce que la saga et le Transactional
 * Outbox traiteront en phase 4.
 */
@Service
@RequiredArgsConstructor
class BookingCommandService implements CreateBookingUseCase, CancelBookingUseCase {

    private static final Logger log = LoggerFactory.getLogger(BookingCommandService.class);

    private final BookingRepository bookingRepository;
    private final EventCatalogPort eventCatalog;
    private final Clock clock;

    @Override
    public Booking book(CreateBookingCommand command) {
        // 1. Prix officiel auprès du propriétaire du catalogue.
        CategorySnapshot category =
                eventCatalog.findCategory(command.eventId(), command.categoryId());

        // 2. Trace persistée AVANT l'appel qui modifie le stock : si la suite
        //    échoue, il reste une réservation PENDING exploitable pour un
        //    diagnostic ou une reprise, plutôt qu'aucune trace du tout.
        Booking booking = bookingRepository.save(Booking.create(
                command.userId(), command.eventId(), command.categoryId(),
                command.quantity(), category.price(), clock));

        // 3. Retenir les places, hors transaction.
        try {
            eventCatalog.reserveSeats(
                    command.eventId(), command.categoryId(), command.quantity());
            booking.confirm(clock);
        } catch (SeatsUnavailableException exception) {
            // Refus métier : la réservation est rejetée, pas en erreur.
            booking.reject(exception.getMessage(), clock);
            log.info("Réservation {} rejetée : {}", booking.reference(), exception.getMessage());
        }

        // 4. Enregistrer l'issue. Une panne du catalogue remonte telle quelle et
        //    laisse la réservation en PENDING — volontairement visible.
        return bookingRepository.save(booking);
    }

    @Override
    public Booking cancel(BookingId id, String reason) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException(id.toString()));

        boolean heldSeats = booking.holdsSeats();
        booking.cancel(reason, clock);

        if (heldSeats) {
            // Les places restent retenues chez event-service : aucun mécanisme de
            // compensation fiable n'existe encore. La phase 4 publiera ici
            // `seats.release`.
            log.warn("Places non libérées pour {} : compensation non implémentée",
                    booking.reference());
        }
        return bookingRepository.save(booking);
    }
}
