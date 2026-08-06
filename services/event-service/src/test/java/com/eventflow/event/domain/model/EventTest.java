package com.eventflow.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.eventflow.event.domain.exception.CategoryNotFoundException;
import com.eventflow.event.domain.exception.InsufficientSeatsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Event")
class EventTest {

    // Horloge figée : la règle « date dans le futur » devient déterministe,
    // le test ne dépend plus de l'instant où on le lance.
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant TOMORROW = NOW.plus(Duration.ofDays(1));

    private static Event anEvent() {
        return Event.create("Concert de rentrée", "Zénith de Paris", TOMORROW, CLOCK);
    }

    @Nested
    @DisplayName("à la création")
    class Creation {

        @Test
        void should_expose_the_provided_details() {
            Event event = anEvent();

            assertThat(event.title()).isEqualTo("Concert de rentrée");
            assertThat(event.venue()).isEqualTo("Zénith de Paris");
            assertThat(event.startsAt()).isEqualTo(TOMORROW);
            assertThat(event.categories()).isEmpty();
        }

        @Test
        void should_reject_a_start_date_in_the_past() {
            Instant yesterday = NOW.minus(Duration.ofDays(1));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Event.create("Concert", "Zénith", yesterday, CLOCK))
                    .withMessageContaining("dans le futur");
        }

        @Test
        void should_reject_a_start_date_equal_to_now() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Event.create("Concert", "Zénith", NOW, CLOCK));
        }

        @Test
        void should_reject_a_blank_title() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Event.create("  ", "Zénith", TOMORROW, CLOCK));
        }
    }

    @Nested
    @DisplayName("avec des catégories")
    class Categories {

        @Test
        void should_aggregate_capacity_across_categories() {
            Event event = anEvent();
            event.addCategory(TicketCategory.create("Fosse", Money.euros("49.90"), 100));
            event.addCategory(TicketCategory.create("Balcon", Money.euros("79.00"), 50));

            assertThat(event.totalCapacity()).isEqualTo(150);
            assertThat(event.totalAvailableSeats()).isEqualTo(150);
        }

        @Test
        void should_reject_two_categories_with_the_same_name() {
            Event event = anEvent();
            event.addCategory(TicketCategory.create("Fosse", Money.euros("49.90"), 100));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> event.addCategory(
                            TicketCategory.create("fosse", Money.euros("59.00"), 20)))
                    .withMessageContaining("existe déjà");
        }

        @Test
        void should_not_allow_mutating_the_exposed_category_list() {
            Event event = anEvent();
            List<TicketCategory> categories = event.categories();
            TicketCategory intruder = TicketCategory.create("Pirate", Money.euros("1.00"), 1);

            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> categories.add(intruder));
        }
    }

    @Nested
    @DisplayName("lors d'une réservation")
    class Reservation {

        @Test
        void should_reserve_seats_in_the_targeted_category() {
            Event event = anEvent();
            TicketCategory fosse = TicketCategory.create("Fosse", Money.euros("49.90"), 100);
            event.addCategory(fosse);

            event.reserveSeats(fosse.id(), Quantity.of(4));

            assertThat(event.totalAvailableSeats()).isEqualTo(96);
        }

        @Test
        void should_reject_a_category_that_belongs_to_another_event() {
            Event event = anEvent();
            event.addCategory(TicketCategory.create("Fosse", Money.euros("49.90"), 100));
            CategoryId foreignCategory = CategoryId.newId();

            assertThatExceptionOfType(CategoryNotFoundException.class)
                    .isThrownBy(() -> event.reserveSeats(foreignCategory, Quantity.of(1)));
        }

        @Test
        void should_propagate_insufficient_seats() {
            Event event = anEvent();
            TicketCategory fosse = TicketCategory.create("Fosse", Money.euros("49.90"), 2);
            event.addCategory(fosse);

            assertThatExceptionOfType(InsufficientSeatsException.class)
                    .isThrownBy(() -> event.reserveSeats(fosse.id(), Quantity.of(3)));
        }

        @Test
        void should_be_sold_out_when_every_category_is_empty() {
            Event event = anEvent();
            TicketCategory fosse = TicketCategory.create("Fosse", Money.euros("49.90"), 2);
            TicketCategory balcon = TicketCategory.create("Balcon", Money.euros("79.00"), 1);
            event.addCategory(fosse);
            event.addCategory(balcon);

            event.reserveSeats(fosse.id(), Quantity.of(2));
            assertThat(event.isSoldOut()).isFalse();

            event.reserveSeats(balcon.id(), Quantity.of(1));
            assertThat(event.isSoldOut()).isTrue();
        }

        @Test
        void should_restore_seats_on_release() {
            Event event = anEvent();
            TicketCategory fosse = TicketCategory.create("Fosse", Money.euros("49.90"), 10);
            event.addCategory(fosse);
            event.reserveSeats(fosse.id(), Quantity.of(6));

            event.releaseSeats(fosse.id(), Quantity.of(6));

            assertThat(event.totalAvailableSeats()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("à la réhydratation depuis la base")
    class Rehydration {

        @Test
        void should_accept_a_past_event() {
            // Un événement terminé doit rester consultable : les règles de
            // création ne s'appliquent qu'à la création.
            Instant lastYear = NOW.minus(Duration.ofDays(365));

            Event event = Event.rehydrate(
                    EventId.newId(), "Concert 2025", "Zénith", lastYear, List.of());

            assertThat(event.startsAt()).isEqualTo(lastYear);
        }
    }
}
