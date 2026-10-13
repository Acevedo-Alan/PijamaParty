package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.platform.domain.Money;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link PaymentGateway} (UC-14, Liskov Substitution): every provider, MercadoPago now and MODO in E3b,
 * charges exactly the server's amount, answers an https checkout, and turns its failures into {@code Unavailable}.
 */
public abstract class PaymentGatewayContractTest<T extends PaymentGateway> extends ContractTest<T> {

    protected static final CheckoutRequest REQUEST = new CheckoutRequest(UUID.randomUUID(), 1001,
        Money.of(2_100_050), PaymentProvider.MERCADOPAGO, new CheckoutRequest.Payer("Ana Pérez", "ana@example.com"),
        Instant.parse("2026-10-12T15:30:00Z"));

    protected abstract PaymentProvider expectedProvider();

    /** Makes the provider unreachable or failing for the next calls. */
    protected abstract void providerIsDown();

    /** What the provider would charge the buyer at the checkout it created. */
    protected abstract Money chargedAmount(Checkout checkout);

    @Test
    void givenTheGateway_whenAskedForItsProvider_thenItIsTheOneItTalksTo() {
        assertThat(subject.provider()).isEqualTo(expectedProvider());
    }

    @Test
    void givenAConfirmedOrder_whenTheCheckoutIsCreated_thenItChargesExactlyTheServersAmountOverHttps() {
        PaymentGateway.Outcome outcome = subject.createCheckout(REQUEST);

        assertThat(outcome).isInstanceOf(PaymentGateway.Outcome.Created.class);
        Checkout checkout = ((PaymentGateway.Outcome.Created) outcome).checkout();
        assertThat(checkout.url().getScheme()).isEqualTo("https");
        assertThat(chargedAmount(checkout)).isEqualTo(REQUEST.amount());
    }

    @Test
    void givenTheProviderDown_whenTheCheckoutIsCreated_thenItIsUnavailableWithoutThrowing() {
        providerIsDown();

        assertThat(subject.createCheckout(REQUEST)).isInstanceOf(PaymentGateway.Outcome.Unavailable.class);
    }
}
