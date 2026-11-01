package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnabledPaymentProvidersServiceTest {

    @Test
    void givenMODOSwitchedOff_whenAskedForTheProviders_thenOnlyMercadoPagoIsEnabled() {
        var service = new EnabledPaymentProvidersService(List.of(
            new InMemoryPaymentGateway(PaymentProvider.MODO, false)), List.of(new InMemoryCardCharges()));

        assertThat(service.enabled()).containsExactly(PaymentProvider.MERCADOPAGO);
    }

    @Test
    void givenMODOSwitchedOn_whenAskedForTheProviders_thenBothAreEnabled() {
        var service = new EnabledPaymentProvidersService(List.of(
            new InMemoryPaymentGateway(PaymentProvider.MODO, true)), List.of(new InMemoryCardCharges()));

        assertThat(service.enabled()).containsExactlyInAnyOrder(PaymentProvider.MERCADOPAGO, PaymentProvider.MODO);
    }

    @Test
    void givenMercadoPagosCardForm_whenAskedForItsKey_thenItIsThePublicKey() {
        var service = new EnabledPaymentProvidersService(List.of(), List.of(new InMemoryCardCharges()));

        assertThat(service.cardFormPublicKey()).contains("TEST-public-key");
    }
}
