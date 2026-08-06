package com.eventflow.event.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Money")
class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void should_normalise_the_scale_to_the_currency() {
        // 10.0 et 10.00 doivent être le même montant : BigDecimal.equals
        // compare l'échelle, ce qui les rendrait différents sans normalisation.
        assertThat(Money.euros("10.0")).isEqualTo(Money.euros("10.00"));
    }

    @Test
    void should_reject_a_negative_amount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.euros("-1.00"))
                .withMessageContaining("négatif");
    }

    @Test
    void should_add_two_amounts_of_the_same_currency() {
        Money total = Money.euros("10.50").plus(Money.euros("4.50"));

        assertThat(total).isEqualTo(Money.euros("15.00"));
    }

    @Test
    void should_reject_money_addition_with_different_currencies() {
        Money euros = Money.euros("10.00");
        Money dollars = Money.of(new BigDecimal("10.00"), USD);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> euros.plus(dollars))
                .withMessageContaining("Devises incompatibles");
    }

    @Test
    void should_multiply_by_a_quantity() {
        Money total = Money.euros("49.90").times(Quantity.of(3));

        assertThat(total).isEqualTo(Money.euros("149.70"));
    }

    @Test
    void should_round_half_up_on_construction() {
        assertThat(Money.euros("10.005")).isEqualTo(Money.euros("10.01"));
    }
}
