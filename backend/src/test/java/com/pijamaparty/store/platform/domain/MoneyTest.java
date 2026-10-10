package com.pijamaparty.store.platform.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void givenMoneyOf1999_whenAddingMoneyOf1_thenTheResultIsMoneyOf2000() {
        assertThat(Money.of(1999).plus(Money.of(1))).isEqualTo(Money.of(2000));
    }

    @Test
    void givenANegativeAmount_whenCreatingMoney_thenItIsRejected() {
        assertThatThrownBy(() -> Money.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenASubtractionBelowZero_whenSubtracting_thenItIsRejected() {
        assertThatThrownBy(() -> Money.of(100).minus(Money.of(101))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenASubtractionThatStaysPositive_whenSubtracting_thenTheResultIsExact() {
        assertThat(Money.of(2000).minus(Money.of(1))).isEqualTo(Money.of(1999));
        assertThat(Money.of(500).minus(Money.of(500)).isZero()).isTrue();
    }

    @Test
    void givenAUnitPrice_whenMultiplyingByAQuantity_thenTheResultIsExact() {
        assertThat(Money.of(1999).times(3)).isEqualTo(Money.of(5997));
        assertThat(Money.of(1999).times(0)).isEqualTo(Money.zero());
    }

    @Test
    void givenANegativeQuantity_whenMultiplying_thenItIsRejected() {
        assertThatThrownBy(() -> Money.of(1999).times(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenAnAmountNearTheLimit_whenAddingBeyondIt_thenItFailsInsteadOfOverflowing() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE).plus(Money.of(1))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void givenTwoAmounts_whenComparing_thenTheyAreOrderedByCents() {
        assertThat(Money.of(2).isGreaterThan(Money.of(1))).isTrue();
        assertThat(Money.of(1).isLessThan(Money.of(2))).isTrue();
        assertThat(Money.of(1)).isLessThan(Money.of(2));
    }

    @Test
    void givenAnAmount_whenPrinting_thenItShowsPesosAndCentsInArs() {
        assertThat(Money.of(1999)).hasToString("ARS 19.99");
        assertThat(Money.of(5)).hasToString("ARS 0.05");
    }
}
