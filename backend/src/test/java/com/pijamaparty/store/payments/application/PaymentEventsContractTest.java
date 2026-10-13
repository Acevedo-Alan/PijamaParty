package com.pijamaparty.store.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.payments.api.PaymentApproved;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contract of {@link PaymentEvents}: each call publishes one event of {@code payments.api} with the ids. */
public abstract class PaymentEventsContractTest<T extends PaymentEvents> extends ContractTest<T> {

    /** What reached the listeners, in order. */
    protected abstract List<Object> published();

    @Test
    void givenAnApprovedPayment_whenPublished_thenListenersGetTheOrderAndProvider() {
        UUID order = UUID.randomUUID();

        subject.paymentApproved(order, PaymentProvider.MERCADOPAGO);

        assertThat(published()).containsExactly(new PaymentApproved(order, PaymentProvider.MERCADOPAGO));
    }
}
