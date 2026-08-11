package com.eventflow.booking.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("BookingStatus")
class BookingStatusTest {

    @ParameterizedTest(name = "{0} -> {1} : {2}")
    @CsvSource({
            "PENDING,   CONFIRMED, true",
            "PENDING,   REJECTED,  true",
            "PENDING,   CANCELLED, true",
            "CONFIRMED, CANCELLED, true",
            "CONFIRMED, CONFIRMED, false",
            "CONFIRMED, REJECTED,  false",
            "REJECTED,  CONFIRMED, false",
            "REJECTED,  CANCELLED, false",
            "CANCELLED, CONFIRMED, false",
            "PENDING,   PENDING,   false"
    })
    void should_describe_the_transition_graph(
            BookingStatus from, BookingStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"REJECTED", "CANCELLED"})
    void should_mark_terminal_states_as_final(BookingStatus status) {
        assertThat(status.isFinal()).isTrue();
    }

    @Test
    void should_not_mark_pending_or_confirmed_as_final() {
        assertThat(BookingStatus.PENDING.isFinal()).isFalse();
        assertThat(BookingStatus.CONFIRMED.isFinal()).isFalse();
    }

    @Test
    @DisplayName("chaque état est couvert par le graphe de transitions")
    void should_declare_transitions_for_every_status() {
        // Garde-fou : ajouter un état sans compléter le graphe le rendrait
        // silencieusement terminal. Ce test force à y penser.
        assertThat(Arrays.stream(BookingStatus.values()))
                .allSatisfy(status -> assertThat(status.canTransitionTo(status)).isFalse());
    }
}
