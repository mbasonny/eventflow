package com.eventflow.booking.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.eventflow.booking.domain.exception.InvalidBookingTransitionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Booking")
class BookingTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Clock LATER =
            Clock.fixed(NOW.plus(Duration.ofMinutes(5)), ZoneOffset.UTC);

    private static Booking aBooking() {
        return Booking.create(
                UserId.of("user-42"),
                EventId.of(UUID.randomUUID()),
                CategoryId.of(UUID.randomUUID()),
                Quantity.of(3),
                Money.euros("49.90"),
                CLOCK);
    }

    @Nested
    @DisplayName("à la création")
    class Creation {

        @Test
        void should_start_pending() {
            Booking booking = aBooking();

            assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
            assertThat(booking.isPending()).isTrue();
            assertThat(booking.holdsSeats()).isFalse();
            assertThat(booking.statusReason()).isEmpty();
        }

        @Test
        void should_compute_the_total_from_unit_price_and_quantity() {
            // Le montant n'est jamais fourni par l'appelant : un client ne
            // décide pas de ce qu'il paie.
            Booking booking = aBooking();

            assertThat(booking.totalAmount()).isEqualTo(Money.euros("149.70"));
        }

        @Test
        void should_generate_a_human_readable_reference() {
            Booking booking = aBooking();

            assertThat(booking.reference().value()).matches("EF-[A-HJ-NP-Z2-9]{8}");
        }

        @Test
        void should_generate_distinct_references() {
            assertThat(aBooking().reference()).isNotEqualTo(aBooking().reference());
        }

        @Test
        void should_timestamp_creation_from_the_clock() {
            Booking booking = aBooking();

            assertThat(booking.createdAt()).isEqualTo(NOW);
            assertThat(booking.updatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("transitions autorisées")
    class AllowedTransitions {

        @Test
        void should_confirm_a_pending_booking() {
            Booking booking = aBooking();

            booking.confirm(LATER);

            assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.holdsSeats()).isTrue();
            assertThat(booking.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        }

        @Test
        void should_reject_a_pending_booking_with_a_reason() {
            Booking booking = aBooking();

            booking.reject("Places insuffisantes", LATER);

            assertThat(booking.status()).isEqualTo(BookingStatus.REJECTED);
            assertThat(booking.statusReason()).contains("Places insuffisantes");
        }

        @Test
        void should_cancel_a_confirmed_booking() {
            Booking booking = aBooking();
            booking.confirm(LATER);

            booking.cancel("Demande du client", LATER);

            assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.holdsSeats()).isFalse();
        }

        @Test
        void should_cancel_a_pending_booking() {
            Booking booking = aBooking();

            booking.cancel("Abandon avant confirmation", LATER);

            assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("transitions interdites")
    class ForbiddenTransitions {

        @Test
        @DisplayName("rejouer une confirmation sur une réservation annulée échoue")
        void should_reject_confirming_a_cancelled_booking() {
            // Kafka livre au moins une fois : ce scénario se produira réellement.
            Booking booking = aBooking();
            booking.cancel("Annulée", LATER);

            assertThatExceptionOfType(InvalidBookingTransitionException.class)
                    .isThrownBy(() -> booking.confirm(LATER))
                    .satisfies(exception -> {
                        assertThat(exception.from()).isEqualTo(BookingStatus.CANCELLED);
                        assertThat(exception.to()).isEqualTo(BookingStatus.CONFIRMED);
                    });
        }

        @Test
        void should_reject_confirming_twice() {
            Booking booking = aBooking();
            booking.confirm(LATER);

            assertThatExceptionOfType(InvalidBookingTransitionException.class)
                    .isThrownBy(() -> booking.confirm(LATER));
        }

        @Test
        void should_reject_any_transition_from_a_rejected_booking() {
            Booking booking = aBooking();
            booking.reject("Complet", LATER);

            assertThatExceptionOfType(InvalidBookingTransitionException.class)
                    .isThrownBy(() -> booking.confirm(LATER));
            assertThatExceptionOfType(InvalidBookingTransitionException.class)
                    .isThrownBy(() -> booking.cancel("Trop tard", LATER));
        }

        @Test
        void should_leave_the_state_untouched_when_a_transition_fails() {
            Booking booking = aBooking();
            booking.reject("Complet", LATER);

            assertThatExceptionOfType(InvalidBookingTransitionException.class)
                    .isThrownBy(() -> booking.confirm(LATER));

            assertThat(booking.status()).isEqualTo(BookingStatus.REJECTED);
            assertThat(booking.statusReason()).contains("Complet");
        }

        @Test
        void should_require_a_non_blank_reason() {
            Booking booking = aBooking();

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> booking.reject("   ", LATER));
        }
    }
}
