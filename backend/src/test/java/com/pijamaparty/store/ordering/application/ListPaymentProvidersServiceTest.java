package com.pijamaparty.store.ordering.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** UC-13 (T-E3b-04): the payment providers of step 2 of the checkout, over an in-memory fake. */
class ListPaymentProvidersServiceTest {

    @Test
    void givenMODOSwitchedOff_whenListed_thenOnlyMercadoPagoIsOffered() {
        var providers = new InMemoryEnabledPaymentProviders(Set.of(PaymentProvider.MERCADOPAGO));

        assertThat(new ListPaymentProvidersService(providers).execute().providers())
            .containsExactly(PaymentProvider.MERCADOPAGO);
    }

    @Test
    void givenMODOSwitchedOn_whenListed_thenBothAreOfferedWithMercadoPagoFirst() {
        var providers = new InMemoryEnabledPaymentProviders(Set.of(PaymentProvider.MODO, PaymentProvider.MERCADOPAGO));

        assertThat(new ListPaymentProvidersService(providers).execute().providers())
            .isEqualTo(List.of(PaymentProvider.MERCADOPAGO, PaymentProvider.MODO));
    }
}
