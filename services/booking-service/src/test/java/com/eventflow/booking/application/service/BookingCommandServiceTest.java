package com.eventflow.booking.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.eventflow.booking.domain.exception.EventCatalogUnavailableException;
import com.eventflow.booking.domain.exception.SeatsUnavailableException;
import com.eventflow.booking.domain.model.Booking;
import com.eventflow.booking.domain.model.BookingStatus;
import com.eventflow.booking.domain.model.CategoryId;
import com.eventflow.booking.domain.model.EventId;
import com.eventflow.booking.domain.model.Money;
import com.eventflow.booking.domain.model.Quantity;
import com.eventflow.booking.domain.model.UserId;
import com.eventflow.booking.domain.port.in.CreateBookingUseCase.CreateBookingCommand;
import com.eventflow.booking.domain.port.out.BookingRepository;
import com.eventflow.booking.domain.port.out.EventCatalogPort;
import com.eventflow.booking.domain.port.out.EventCatalogPort.CategorySnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test de l'orchestration seule : les deux ports sortants sont simulés.
 *
 * <p>Aucun contexte Spring, aucune base, aucun serveur HTTP — c'est possible
 * précisément parce que le service ne dépend que d'interfaces déclarées par le
 * domaine.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BookingCommandService")
class BookingCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final EventId EVENT_ID = EventId.of(UUID.randomUUID());
    private static final CategoryId CATEGORY_ID = CategoryId.of(UUID.randomUUID());

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private EventCatalogPort eventCatalog;

    private BookingCommandService service;

    /** Statut de chaque réservation au moment précis où save() est appelé. */
    private final List<BookingStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new BookingCommandService(bookingRepository, eventCatalog, CLOCK);
    }

    /**
     * Le repository renvoie ce qu'on lui donne, et note le statut au passage.
     *
     * <p>Un {@code ArgumentCaptor} ne conviendrait pas : le service mute la même
     * instance de {@link Booking} entre les deux sauvegardes, si bien que les
     * deux valeurs capturées désignent le même objet dans son état final.
     */
    private void stubSaveEchoingArgument() {
        given(bookingRepository.save(any())).willAnswer(call -> {
            Booking booking = call.getArgument(0);
            savedStatuses.add(booking.status());
            return booking;
        });
    }

    private static CreateBookingCommand aCommand() {
        return new CreateBookingCommand(
                UserId.of("user-42"), EVENT_ID, CATEGORY_ID, Quantity.of(3));
    }

    private static CategorySnapshot aCategory(int availableSeats) {
        return new CategorySnapshot(
                CATEGORY_ID, "Fosse", Money.euros("49.90"), 100, availableSeats);
    }

    @Test
    void should_confirm_the_booking_when_seats_are_granted() {
        // Given
        stubSaveEchoingArgument();
        given(eventCatalog.findCategory(EVENT_ID, CATEGORY_ID)).willReturn(aCategory(100));
        given(eventCatalog.reserveSeats(EVENT_ID, CATEGORY_ID, Quantity.of(3)))
                .willReturn(aCategory(97));

        // When
        Booking booking = service.book(aCommand());

        // Then
        assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.totalAmount()).isEqualTo(Money.euros("149.70"));
        then(eventCatalog).should().reserveSeats(EVENT_ID, CATEGORY_ID, Quantity.of(3));
    }

    @Test
    @DisplayName("le prix vient du catalogue, jamais du client")
    void should_price_the_booking_from_the_catalog() {
        stubSaveEchoingArgument();
        given(eventCatalog.findCategory(EVENT_ID, CATEGORY_ID))
                .willReturn(new CategorySnapshot(
                        CATEGORY_ID, "Carré or", Money.euros("120.00"), 50, 50));
        given(eventCatalog.reserveSeats(any(), any(), any()))
                .willReturn(aCategory(47));

        Booking booking = service.book(aCommand());

        assertThat(booking.unitPrice()).isEqualTo(Money.euros("120.00"));
        assertThat(booking.totalAmount()).isEqualTo(Money.euros("360.00"));
    }

    @Test
    void should_reject_the_booking_when_seats_are_insufficient() {
        stubSaveEchoingArgument();
        given(eventCatalog.findCategory(EVENT_ID, CATEGORY_ID)).willReturn(aCategory(2));
        willThrow(new SeatsUnavailableException(CATEGORY_ID, 3, 2))
                .given(eventCatalog).reserveSeats(any(), any(), any());

        Booking booking = service.book(aCommand());

        assertThat(booking.status()).isEqualTo(BookingStatus.REJECTED);
        assertThat(booking.statusReason()).isPresent();
    }

    @Test
    @DisplayName("une réservation rejetée reste consultable : elle n'est pas une erreur")
    void should_persist_a_rejected_booking() {
        stubSaveEchoingArgument();
        given(eventCatalog.findCategory(EVENT_ID, CATEGORY_ID)).willReturn(aCategory(2));
        willThrow(new SeatsUnavailableException(CATEGORY_ID, 3, 2))
                .given(eventCatalog).reserveSeats(any(), any(), any());

        service.book(aCommand());

        // Deux sauvegardes : la trace PENDING avant l'appel, puis l'issue.
        then(bookingRepository).should(times(2)).save(any());
        assertThat(savedStatuses)
                .containsExactly(BookingStatus.PENDING, BookingStatus.REJECTED);
    }

    @Test
    @DisplayName("le couplage de disponibilité : catalogue HS, plus aucune réservation possible")
    void should_propagate_the_failure_when_the_catalog_is_down() {
        // C'est le symptôme que la phase 2 doit rendre visible. booking-service
        // se porte bien, mais ne peut rien faire.
        willThrow(new EventCatalogUnavailableException("Le catalogue est indisponible"))
                .given(eventCatalog).findCategory(any(), any());

        assertThatExceptionOfType(EventCatalogUnavailableException.class)
                .isThrownBy(() -> service.book(aCommand()));

        // Rien n'est écrit : on n'a même pas pu connaître le prix.
        then(bookingRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("une panne après la trace PENDING laisse la réservation en attente")
    void should_leave_the_booking_pending_when_reservation_call_fails() {
        stubSaveEchoingArgument();
        given(eventCatalog.findCategory(EVENT_ID, CATEGORY_ID)).willReturn(aCategory(100));
        willThrow(new EventCatalogUnavailableException("Délai dépassé"))
                .given(eventCatalog).reserveSeats(any(), any(), any());

        assertThatExceptionOfType(EventCatalogUnavailableException.class)
                .isThrownBy(() -> service.book(aCommand()));

        // Une seule sauvegarde, et la réservation reste PENDING : les places
        // ont peut-être été retenues, personne ne le sait. C'est le trou que
        // l'Outbox comblera en phase 4.
        then(bookingRepository).should(times(1)).save(any());
        assertThat(savedStatuses).containsExactly(BookingStatus.PENDING);
    }
}
