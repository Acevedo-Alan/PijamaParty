package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.platform.testing.ContractTest;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link EnabledPaymentProviders} (T-E3b-02), as configured by default: MercadoPago always, MODO switched
 * off.
 */
public abstract class EnabledPaymentProvidersContractTest<T extends EnabledPaymentProviders> extends ContractTest<T> {

    @Test
    void givenTheDefaultConfiguration_whenAskedForMercadoPago_thenItIsEnabled() {
        assertThat(subject.isEnabled(PaymentProvider.MERCADOPAGO)).isTrue();
    }

    @Test
    void givenTheDefaultConfiguration_whenAskedForMODO_thenItIsSwitchedOff() {
        assertThat(subject.isEnabled(PaymentProvider.MODO)).isFalse();
    }

    @Test
    void givenTheDefaultConfiguration_whenListed_thenOnlyMercadoPagoIsEnabled() {
        assertThat(subject.enabled()).containsExactly(PaymentProvider.MERCADOPAGO);
    }
}
