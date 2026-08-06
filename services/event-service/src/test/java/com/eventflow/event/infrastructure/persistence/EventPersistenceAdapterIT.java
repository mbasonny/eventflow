package com.eventflow.event.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;

import com.eventflow.event.TestcontainersConfiguration;
import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.model.Money;
import com.eventflow.event.domain.model.Quantity;
import com.eventflow.event.domain.model.TicketCategory;
import com.eventflow.event.domain.port.out.EventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * Test d'intégration contre un vrai PostgreSQL 16 démarré par Testcontainers.
 *
 * <p>Pourquoi pas H2 : les types, les contraintes et le comportement
 * transactionnel diffèrent. Un test vert sur H2 ne dit rien de la production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, EventPersistenceAdapter.class})
@DisplayName("EventPersistenceAdapter")
class EventPersistenceAdapterIT {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Autowired
    private EventRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static Event anEventWithCategories() {
        Event event = Event.create(
                "Concert de rentrée", "Zénith de Paris", NOW.plus(Duration.ofDays(30)), CLOCK);
        event.addCategory(TicketCategory.create("Fosse", Money.euros("49.90"), 100));
        event.addCategory(TicketCategory.create("Balcon", Money.euros("79.00"), 50));
        return event;
    }

    /** Force l'écriture SQL puis vide le cache de premier niveau. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void should_persist_and_reload_an_event_with_its_categories() {
        // Given
        Event event = anEventWithCategories();

        // When
        repository.save(event);
        flushAndClear();

        // Then
        Event reloaded = repository.findById(event.id()).orElseThrow();
        assertThat(reloaded.title()).isEqualTo("Concert de rentrée");
        assertThat(reloaded.venue()).isEqualTo("Zénith de Paris");
        assertThat(reloaded.categories()).hasSize(2);
        assertThat(reloaded.totalCapacity()).isEqualTo(150);
    }

    @Test
    void should_preserve_amount_and_currency() {
        Event event = anEventWithCategories();
        repository.save(event);
        flushAndClear();

        Event reloaded = repository.findById(event.id()).orElseThrow();
        Money price = reloaded.category(event.categories().getFirst().id()).price();

        assertThat(price).isEqualTo(Money.euros("49.90"));
        assertThat(price.currency().getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    void should_persist_the_updated_stock_after_a_reservation() {
        // Given
        Event event = anEventWithCategories();
        repository.save(event);
        flushAndClear();

        // When
        Event loaded = repository.findById(event.id()).orElseThrow();
        loaded.reserveSeats(loaded.categories().getFirst().id(), Quantity.of(12));
        repository.save(loaded);
        flushAndClear();

        // Then
        Event reloaded = repository.findById(event.id()).orElseThrow();
        assertThat(reloaded.totalAvailableSeats()).isEqualTo(138);
    }

    @Test
    void should_return_empty_when_the_event_does_not_exist() {
        assertThat(repository.findById(EventId.newId())).isEmpty();
    }

    @Test
    void should_delete_categories_along_with_the_event() {
        Event event = anEventWithCategories();
        repository.save(event);
        flushAndClear();

        repository.deleteById(event.id());
        flushAndClear();

        assertThat(repository.existsById(event.id())).isFalse();
        Long remaining = (Long) entityManager.getEntityManager()
                .createNativeQuery(
                        "SELECT count(*) FROM ticket_categories WHERE event_id = :id", Long.class)
                .setParameter("id", event.id().value())
                .getSingleResult();
        assertThat(remaining).isZero();
    }

    @Test
    void should_list_events_ordered_by_start_date() {
        Event later = Event.create("Match", "Stade", NOW.plus(Duration.ofDays(60)), CLOCK);
        Event sooner = Event.create("Concert", "Zénith", NOW.plus(Duration.ofDays(10)), CLOCK);
        repository.save(later);
        repository.save(sooner);
        flushAndClear();

        List<Event> events = repository.findAll(0, 10);

        assertThat(events).extracting(Event::title).containsExactly("Concert", "Match");
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("la contrainte CHECK de la base refuse un stock hors bornes")
    void should_enforce_the_seats_check_constraint_at_database_level() {
        // Le domaine rend ce cas inatteignable ; on écrit en SQL brut pour
        // vérifier que la base protège quand même la donnée.
        Event event = anEventWithCategories();
        repository.save(event);
        flushAndClear();

        assertThatException().isThrownBy(() -> {
            entityManager.getEntityManager()
                    .createNativeQuery("""
                            INSERT INTO ticket_categories
                                (id, event_id, name, price_amount, price_currency,
                                 capacity, available_seats)
                            VALUES (:id, :eventId, 'Corrompue', 10.00, 'EUR', 10, 11)
                            """)
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("eventId", event.id().value())
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("l'index unique est insensible à la casse, comme la règle du domaine")
    void should_reject_a_duplicate_category_name_ignoring_case() {
        Event event = anEventWithCategories();
        repository.save(event);
        flushAndClear();

        assertThatException().isThrownBy(() -> {
            entityManager.getEntityManager()
                    .createNativeQuery("""
                            INSERT INTO ticket_categories
                                (id, event_id, name, price_amount, price_currency,
                                 capacity, available_seats)
                            VALUES (:id, :eventId, 'FOSSE', 10.00, 'EUR', 10, 10)
                            """)
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("eventId", event.id().value())
                    .executeUpdate();
            entityManager.flush();
        });
    }
}
