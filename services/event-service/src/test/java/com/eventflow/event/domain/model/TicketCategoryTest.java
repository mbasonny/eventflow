package com.eventflow.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.eventflow.event.domain.exception.InsufficientSeatsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TicketCategory")
class TicketCategoryTest {

    private static final Money PRICE = Money.euros("49.90");

    @Nested
    @DisplayName("à la création")
    class Creation {

        @Test
        void should_start_with_all_seats_available() {
            TicketCategory category = TicketCategory.create("Fosse", PRICE, 100);

            assertThat(category.capacity()).isEqualTo(100);
            assertThat(category.availableSeats()).isEqualTo(100);
            assertThat(category.reservedSeats()).isZero();
            assertThat(category.isSoldOut()).isFalse();
        }

        @Test
        void should_trim_the_name() {
            TicketCategory category = TicketCategory.create("  Carré or  ", PRICE, 10);

            assertThat(category.name()).isEqualTo("Carré or");
        }

        @Test
        void should_reject_a_blank_name() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TicketCategory.create("   ", PRICE, 10))
                    .withMessageContaining("ne peut pas être vide");
        }

        @Test
        void should_reject_a_non_positive_capacity() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TicketCategory.create("Fosse", PRICE, 0))
                    .withMessageContaining("strictement positive");
        }
    }

    @Nested
    @DisplayName("lors d'une réservation")
    class Reservation {

        @Test
        void should_decrease_available_seats() {
            // Given
            TicketCategory category = TicketCategory.create("Fosse", PRICE, 100);

            // When
            category.reserve(Quantity.of(3));

            // Then
            assertThat(category.availableSeats()).isEqualTo(97);
            assertThat(category.reservedSeats()).isEqualTo(3);
        }

        @Test
        void should_allow_reserving_exactly_the_last_seats() {
            TicketCategory category = TicketCategory.create("Fosse", PRICE, 10);

            category.reserve(Quantity.of(10));

            assertThat(category.availableSeats()).isZero();
            assertThat(category.isSoldOut()).isTrue();
        }

        @Test
        void should_reject_booking_when_not_enough_seats() {
            // Given
            TicketCategory category = TicketCategory.create("Fosse", PRICE, 10);
            category.reserve(Quantity.of(8));

            // When / Then
            assertThatExceptionOfType(InsufficientSeatsException.class)
                    .isThrownBy(() -> category.reserve(Quantity.of(3)))
                    .satisfies(exception -> {
                        assertThat(exception.requested()).isEqualTo(3);
                        assertThat(exception.available()).isEqualTo(2);
                    });
        }

        @Test
        void should_leave_the_stock_untouched_when_reservation_fails() {
            TicketCategory category = TicketCategory.create("Fosse", PRICE, 10);

            assertThatExceptionOfType(InsufficientSeatsException.class)
                    .isThrownBy(() -> category.reserve(Quantity.of(11)));

            assertThat(category.availableSeats()).isEqualTo(10);
        }

        @Test
        void should_report_availability_without_reserving() {
            TicketCategory category = TicketCategory.create("Fosse", PRICE, 5);

            assertThat(category.hasAvailability(Quantity.of(5))).isTrue();
            assertThat(category.hasAvailability(Quantity.of(6))).isFalse();
            assertThat(category.availableSeats()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("lors d'une libération")
    class Release {

        @Test
        void should_restore_seats_to_the_stock() {
            TicketCategory category = TicketCategory.create("Fosse", PRICE, 10);
            category.reserve(Quantity.of(4));

            category.release(Quantity.of(4));

            assertThat(category.availableSeats()).isEqualTo(10);
        }

        @Test
        void should_reject_releasing_more_than_capacity() {
            TicketCategory category = TicketCategory.create("Fosse", PRICE, 10);
            category.reserve(Quantity.of(2));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> category.release(Quantity.of(3)))
                    .withMessageContaining("capacité");
        }
    }

    @Nested
    @DisplayName("à la réhydratation depuis la base")
    class Rehydration {

        @Test
        void should_restore_the_persisted_state() {
            CategoryId id = CategoryId.newId();

            TicketCategory category = TicketCategory.rehydrate(id, "Fosse", PRICE, 100, 42);

            assertThat(category.id()).isEqualTo(id);
            assertThat(category.availableSeats()).isEqualTo(42);
        }

        @Test
        void should_reject_a_corrupted_stock() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TicketCategory.rehydrate(
                            CategoryId.newId(), "Fosse", PRICE, 10, 11))
                    .withMessageContaining("hors bornes");
        }
    }
}
