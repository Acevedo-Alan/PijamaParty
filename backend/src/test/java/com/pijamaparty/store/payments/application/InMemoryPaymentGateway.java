package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.platform.domain.Money;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Fake of {@link PaymentGateway}: a numbered checkout per call, or unavailable while down. Records the requests. */
public class InMemoryPaymentGateway implements PaymentGateway {

    private final PaymentProvider provider;
    private final AtomicBoolean down = new AtomicBoolean();
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<CheckoutRequest> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Money> charged = new ConcurrentHashMap<>();

    public InMemoryPaymentGateway(PaymentProvider provider) {
        this.provider = provider;
    }

    @Override
    public PaymentProvider provider() {
        return provider;
    }

    @Override
    public Outcome createCheckout(CheckoutRequest request) {
        requests.add(request);
        if (down.get()) {
            return new Outcome.Unavailable();
        }
        String id = "checkout-" + sequence.incrementAndGet();
        charged.put(id, request.amount());
        return new Outcome.Created(new Checkout(id, URI.create("https://pay.example.com/" + id)));
    }

    public void down(boolean isDown) {
        down.set(isDown);
    }

    public List<CheckoutRequest> requests() {
        return List.copyOf(requests);
    }

    public Money chargedAt(String checkoutId) {
        return charged.get(checkoutId);
    }
}
