package com.eventflow.event.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Un montant et sa devise, indissociables.
 *
 * <p>Un {@code BigDecimal} nu ne dit pas dans quelle devise il est libellé :
 * rien n'empêche alors d'additionner des euros et des dollars. Ici l'addition
 * de deux devises différentes lève une exception au lieu de produire un
 * résultat silencieusement faux.
 *
 * <p>Le montant est normalisé à la construction sur le nombre de décimales de
 * la devise (2 pour l'euro), afin que {@code 10.00 €} et {@code 10.0 €} soient
 * égaux — {@link BigDecimal#equals} compare l'échelle, pas seulement la valeur.
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final Currency EUR = Currency.getInstance("EUR");

    public Money {
        Objects.requireNonNull(amount, "Le montant est obligatoire");
        Objects.requireNonNull(currency, "La devise est obligatoire");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Un montant ne peut pas être négatif : " + amount);
        }
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money euros(BigDecimal amount) {
        return new Money(amount, EUR);
    }

    public static Money euros(String amount) {
        return new Money(new BigDecimal(amount), EUR);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(Quantity quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity.value())), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "L'opérande est obligatoire");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Devises incompatibles : %s et %s".formatted(currency, other.currency));
        }
    }

    @Override
    public String toString() {
        return "%s %s".formatted(amount.toPlainString(), currency.getCurrencyCode());
    }
}
