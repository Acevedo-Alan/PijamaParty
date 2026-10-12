package com.pijamaparty.store.shipping.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import com.pijamaparty.store.shipping.domain.Province;
import org.junit.jupiter.api.Test;

/** Contract of the {@link ShippingCostCalculator} strategy (UC-12): the zone table now, a carrier API later. */
public abstract class ShippingCostCalculatorContractTest<T extends ShippingCostCalculator> extends ContractTest<T> {

    /** The shop ships to {@code province} for {@code costCents}. */
    protected abstract void givenShipping(Province province, long costCents);

    @Test
    void givenAProvinceWithShipping_whenAskedItsCost_thenItIsThatCost() {
        givenShipping(Province.SANTA_FE, 450_000);
        givenShipping(Province.CORDOBA, 0);

        assertThat(subject.costTo(Province.SANTA_FE)).contains(Money.of(450_000));
        assertThat(subject.costTo(Province.CORDOBA)).contains(Money.zero());
    }

    @Test
    void givenAProvinceWithoutShipping_whenAskedItsCost_thenThereIsNone() {
        givenShipping(Province.SANTA_FE, 450_000);

        assertThat(subject.costTo(Province.MENDOZA)).isEmpty();
    }
}
