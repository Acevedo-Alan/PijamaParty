package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentApproved;
import com.pijamaparty.store.payments.api.PaymentProvider;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link PaymentEvents}: keeps what was published, in order. */
public class InMemoryPaymentEvents implements PaymentEvents {

    private final List<Object> published = new CopyOnWriteArrayList<>();

    @Override
    public void paymentApproved(UUID orderId, PaymentProvider provider) {
        published.add(new PaymentApproved(orderId, provider));
    }

    public List<Object> published() {
        return List.copyOf(published);
    }
}
