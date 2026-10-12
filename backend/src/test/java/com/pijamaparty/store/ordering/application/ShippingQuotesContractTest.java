package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.shipping.api.Province;
import org.junit.jupiter.api.Test;

/** Contract of {@link ShippingQuotes} (UC-12). */
public abstract class ShippingQuotesContractTest<T extends ShippingQuotes> extends ContractTest<T> {

    protected abstract void givenRate(Province province, long costCents);

    protected abstract void givenFreeShippingFrom(long thresholdCents);

    @Test
    void givenAProvinceWithACost_whenQuoted_thenItCostsThat() {
        givenRate(Province.SANTA_FE, 450_000);
        givenRate(Province.CORDOBA, 0);

        assertThat(subject.costTo(Province.SANTA_FE, Money.of(2_000_000))).contains(Money.of(450_000));
        assertThat(subject.costTo(Province.CORDOBA, Money.of(2_000_000))).contains(Money.zero());
    }

    @Test
    void givenFreeShippingFrom50000_whenTheSubtotalAfterTheDiscountReachesIt_thenShippingCosts0() {
        givenRate(Province.SANTA_FE, 450_000);
        givenFreeShippingFrom(5_000_000);

        assertThat(subject.costTo(Province.SANTA_FE, Money.of(5_000_000))).contains(Money.zero());
        assertThat(subject.costTo(Province.SANTA_FE, Money.of(4_999_999))).contains(Money.of(450_000));
    }

    @Test
    void givenAProvinceWithoutACost_whenQuoted_thenThereIsNoShippingEvenAboveTheThreshold() {
        givenRate(Province.SANTA_FE, 450_000);
        givenFreeShippingFrom(5_000_000);

        assertThat(subject.costTo(Province.MENDOZA, Money.of(2_000_000))).isEmpty();
        assertThat(subject.costTo(Province.MENDOZA, Money.of(9_000_000))).isEmpty();
    }
}
