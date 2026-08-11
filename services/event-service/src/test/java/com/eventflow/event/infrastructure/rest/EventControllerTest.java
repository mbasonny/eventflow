package com.eventflow.event.infrastructure.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eventflow.event.domain.exception.EventNotFoundException;
import com.eventflow.event.domain.exception.InsufficientSeatsException;
import com.eventflow.event.domain.model.CategoryId;
import com.eventflow.event.domain.model.Event;
import com.eventflow.event.domain.model.EventId;
import com.eventflow.event.domain.model.Money;
import com.eventflow.event.domain.model.TicketCategory;
import com.eventflow.event.domain.port.in.CreateEventUseCase;
import com.eventflow.event.domain.port.in.DeleteEventUseCase;
import com.eventflow.event.domain.port.in.FindEventsUseCase;
import com.eventflow.event.domain.port.in.ReserveSeatsUseCase;
import com.eventflow.event.domain.port.in.UpdateEventUseCase;
import com.eventflow.event.infrastructure.rest.mapper.EventRestMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test du contrat HTTP seul : les cas d'usage sont simulés.
 *
 * <p>On vérifie ce que l'API promet — codes de statut, en-têtes, forme du JSON,
 * format d'erreur — sans démarrer la base ni la couche métier.
 */
@WebMvcTest(EventController.class)
@Import({EventRestMapper.class, EventControllerTest.FixedClockConfiguration.class})
@DisplayName("EventController")
class EventControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        Clock clock() {
            return CLOCK;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateEventUseCase createEvent;

    @MockitoBean
    private UpdateEventUseCase updateEvent;

    @MockitoBean
    private DeleteEventUseCase deleteEvent;

    @MockitoBean
    private FindEventsUseCase findEvents;

    @MockitoBean
    private ReserveSeatsUseCase reserveSeats;

    private static Event anEvent() {
        Event event = Event.rehydrate(
                EventId.newId(), "Concert de rentrée", "Zénith de Paris",
                NOW.plus(Duration.ofDays(30)), List.of());
        event.addCategory(TicketCategory.create("Fosse", Money.euros("49.90"), 100));
        return event;
    }

    private static final String VALID_BODY = """
            {
              "title": "Concert de rentrée",
              "venue": "Zénith de Paris",
              "startsAt": "2026-12-31T20:00:00Z",
              "categories": [
                { "name": "Fosse", "price": 49.90, "currency": "EUR", "capacity": 100 }
              ]
            }
            """;

    @Test
    void should_return_201_with_a_location_header_on_creation() throws Exception {
        Event created = anEvent();
        given(createEvent.create(any())).willReturn(created);

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/events/" + created.id().value()))
                .andExpect(jsonPath("$.title").value("Concert de rentrée"))
                .andExpect(jsonPath("$.totalCapacity").value(100))
                .andExpect(jsonPath("$.categories[0].name").value("Fosse"))
                .andExpect(jsonPath("$.categories[0].price").value(49.90));
    }

    @Test
    @DisplayName("un POST invalide renvoie un ProblemDetail détaillé par champ")
    void should_return_400_problem_detail_when_the_body_is_invalid() throws Exception {
        String invalidBody = """
                {
                  "title": "",
                  "venue": "Zénith",
                  "startsAt": "2020-01-01T20:00:00Z",
                  "categories": []
                }
                """;

        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://eventflow.dev/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Requête invalide"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.startsAt").exists())
                .andExpect(jsonPath("$.errors.categories").exists());
    }

    @Test
    void should_return_404_problem_detail_when_the_event_is_unknown() throws Exception {
        EventId missing = EventId.newId();
        given(findEvents.byId(any())).willThrow(new EventNotFoundException(missing));

        mockMvc.perform(get("/api/v1/events/{id}", missing.value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://eventflow.dev/problems/event-not-found"))
                .andExpect(jsonPath("$.eventId").value(missing.value().toString()));
    }

    @Test
    @DisplayName("un stock insuffisant renvoie 409, pas 400")
    void should_return_409_when_seats_are_insufficient() throws Exception {
        CategoryId categoryId = CategoryId.newId();
        given(findEvents.category(any(), any()))
                .willThrow(new InsufficientSeatsException(categoryId, 5, 2));

        mockMvc.perform(get("/api/v1/events/{id}/categories/{cid}/availability",
                        EventId.newId().value(), categoryId.value()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://eventflow.dev/problems/insufficient-seats"))
                .andExpect(jsonPath("$.requested").value(5))
                .andExpect(jsonPath("$.available").value(2));
    }

    @Test
    void should_return_a_paged_list() throws Exception {
        given(findEvents.all(0, 20))
                .willReturn(new FindEventsUseCase.EventPage(List.of(anEvent()), 0, 20, 1));

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void should_reject_an_out_of_range_page_size() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://eventflow.dev/problems/validation-error"));
    }

    @Test
    @DisplayName("un JSON malformé renvoie 400, pas 500")
    void should_return_400_when_the_body_is_not_valid_json() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ title: 'sans guillemets' }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://eventflow.dev/problems/malformed-request"));
    }

    @Test
    void should_return_400_when_the_identifier_is_not_a_uuid() throws Exception {
        mockMvc.perform(get("/api/v1/events/{id}", "pas-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://eventflow.dev/problems/invalid-parameter"))
                .andExpect(jsonPath("$.parameter").value("id"));
    }

    @Test
    void should_return_204_on_deletion() throws Exception {
        EventId id = EventId.newId();

        mockMvc.perform(delete("/api/v1/events/{id}", id.value()))
                .andExpect(status().isNoContent());

        verify(deleteEvent).delete(EventId.of(id.value()));
    }

    @Test
    void should_return_404_when_deleting_an_unknown_event() throws Exception {
        EventId missing = EventId.newId();
        willThrow(new EventNotFoundException(missing)).given(deleteEvent).delete(any());

        mockMvc.perform(delete("/api/v1/events/{id}", missing.value()))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_reserve_seats_and_return_the_updated_availability() throws Exception {
        TicketCategory category = TicketCategory.rehydrate(
                CategoryId.newId(), "Fosse", Money.euros("49.90"), 100, 97);
        given(reserveSeats.reserve(any())).willReturn(category);

        mockMvc.perform(post("/api/v1/events/{id}/categories/{cid}/reservations",
                        EventId.newId().value(), category.id().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(97))
                .andExpect(jsonPath("$.capacity").value(100));
    }

    @Test
    void should_return_409_when_reserving_more_seats_than_available() throws Exception {
        CategoryId categoryId = CategoryId.newId();
        given(reserveSeats.reserve(any()))
                .willThrow(new InsufficientSeatsException(categoryId, 40, 12));

        mockMvc.perform(post("/api/v1/events/{id}/categories/{cid}/reservations",
                        EventId.newId().value(), categoryId.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 40}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.requested").value(40))
                .andExpect(jsonPath("$.available").value(12));
    }

    @Test
    void should_reject_a_reservation_of_zero_seats() throws Exception {
        mockMvc.perform(post("/api/v1/events/{id}/categories/{cid}/reservations",
                        EventId.newId().value(), CategoryId.newId().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());
    }

    @Test
    void should_list_the_categories_of_an_event() throws Exception {
        Event event = anEvent();
        given(findEvents.byId(any())).willReturn(event);

        mockMvc.perform(get("/api/v1/events/{id}/categories", event.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Fosse"))
                .andExpect(jsonPath("$[0].availableSeats").value(100))
                .andExpect(jsonPath("$[0].soldOut").value(false));
    }
}
