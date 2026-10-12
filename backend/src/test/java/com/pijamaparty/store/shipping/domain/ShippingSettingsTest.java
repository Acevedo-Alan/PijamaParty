package com.pijamaparty.store.shipping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** UC-12: the free-shipping threshold is optional and goes from 1 cent to ARS 10,000,000. */
class ShippingSettingsTest {

    @Test
    void givenAThresholdWithinItsRange_whenSet_thenItIsKept() {
        assertThat(ShippingSettings.withThreshold(Optional.of(1L)))
            .contains(new ShippingSettings(Optional.of(Money.of(1))));
        assertThat(ShippingSettings.withThreshold(Optional.of(1_000_000_000L)))
            .contains(new ShippingSettings(Optional.of(Money.of(1_000_000_000L))));
    }

    @Test
    void givenNoThreshold_whenSet_thenThereIsNoFreeShipping() {
        assertThat(ShippingSettings.withThreshold(Optional.empty())).contains(ShippingSettings.withoutFreeShipping());
    }

    @Test
    void givenAThresholdOutOfRange_whenSet_thenItIsRefused() {
        assertThat(ShippingSettings.withThreshold(Optional.of(0L))).isEmpty();
        assertThat(ShippingSettings.withThreshold(Optional.of(-5L))).isEmpty();
        assertThat(ShippingSettings.withThreshold(Optional.of(1_000_000_001L))).isEmpty();
        assertThatThrownBy(() -> new ShippingSettings(Optional.of(Money.zero())))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
