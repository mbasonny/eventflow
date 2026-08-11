package com.eventflow.booking.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.eventflow.booking.domain.exception.CategoryUnavailableException;
import com.eventflow.booking.domain.exception.EventCatalogUnavailableException;
import com.eventflow.booking.domain.exception.SeatsUnavailableException;
import com.eventflow.booking.domain.model.CategoryId;
import com.eventflow.booking.domain.model.EventId;
import com.eventflow.booking.domain.model.Money;
import com.eventflow.booking.domain.model.Quantity;
import com.eventflow.booking.domain.port.out.EventCatalogPort.CategorySnapshot;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Vérifie la traduction des réponses HTTP en langage métier.
 *
 * <p>C'est le contrat le plus important de cet adaptateur : confondre un refus
 * métier (409) avec une panne ouvrirait le disjoncteur sur un service en
 * parfaite santé.
 */
@DisplayName("EventServiceClient")
class EventServiceClientTest {

    private static final EventId EVENT_ID = EventId.of(UUID.randomUUID());
    private static final CategoryId CATEGORY_ID = CategoryId.of(UUID.randomUUID());

    private MockRestServiceServer server;
    private EventServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://event-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EventServiceClient(builder.build());
    }

    private String categoryJson(int availableSeats) {
        return """
                {
                  "id": "%s",
                  "name": "Fosse",
                  "price": 49.90,
                  "currency": "EUR",
                  "capacity": 100,
                  "availableSeats": %d
                }
                """.formatted(CATEGORY_ID.value(), availableSeats);
    }

    @Test
    void should_map_a_successful_response_to_a_snapshot() {
        server.expect(requestTo("http://event-service/api/v1/events/%s/categories/%s/availability"
                        .formatted(EVENT_ID.value(), CATEGORY_ID.value())))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(categoryJson(87), MediaType.APPLICATION_JSON));

        CategorySnapshot snapshot = client.findCategory(EVENT_ID, CATEGORY_ID);

        assertThat(snapshot.price()).isEqualTo(Money.euros("49.90"));
        assertThat(snapshot.availableSeats()).isEqualTo(87);
        server.verify();
    }

    @Test
    void should_send_the_quantity_when_reserving() {
        server.expect(requestTo("http://event-service/api/v1/events/%s/categories/%s/reservations"
                        .formatted(EVENT_ID.value(), CATEGORY_ID.value())))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.quantity").value(3))
                .andRespond(withSuccess(categoryJson(97), MediaType.APPLICATION_JSON));

        CategorySnapshot snapshot = client.reserveSeats(EVENT_ID, CATEGORY_ID, Quantity.of(3));

        assertThat(snapshot.availableSeats()).isEqualTo(97);
        server.verify();
    }

    @Test
    @DisplayName("409 devient un refus métier, pas une panne")
    void should_map_409_to_a_business_refusal() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/reservations")))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("""
                                {
                                  "type": "https://eventflow.dev/problems/insufficient-seats",
                                  "status": 409,
                                  "requested": 3,
                                  "available": 1
                                }
                                """));

        assertThatExceptionOfType(SeatsUnavailableException.class)
                .isThrownBy(() -> client.reserveSeats(EVENT_ID, CATEGORY_ID, Quantity.of(3)))
                .satisfies(exception -> {
                    // Les compteurs sont propagés : le client final saura
                    // combien de places restent réellement.
                    assertThat(exception.requested()).isEqualTo(3);
                    assertThat(exception.available()).isEqualTo(1);
                });
    }

    @Test
    void should_map_404_to_an_unknown_category() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/availability")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatExceptionOfType(CategoryUnavailableException.class)
                .isThrownBy(() -> client.findCategory(EVENT_ID, CATEGORY_ID));
    }

    @Test
    @DisplayName("500 devient une panne du catalogue")
    void should_map_5xx_to_a_catalog_failure() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/availability")))
                .andRespond(withServerError());

        assertThatExceptionOfType(EventCatalogUnavailableException.class)
                .isThrownBy(() -> client.findCategory(EVENT_ID, CATEGORY_ID));
    }
}
