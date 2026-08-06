package com.eventflow.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Quantity")
class QuantityTest {

    @Test
    void should_accept_a_positive_value() {
        assertThat(Quantity.of(1).value()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void should_reject_a_non_positive_value(int value) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Quantity.of(value))
                .withMessageContaining("strictement positive");
    }
}
