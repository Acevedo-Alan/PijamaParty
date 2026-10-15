package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.CheckoutRequest;
import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.platform.domain.Money;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fake of {@link PaymentGateway}: a numbered checkout per call, or unavailable while down; its checkouts expire when
 * told to ({@link #expiringAt}). Records the requests and their attempts.
 */
public class InMemoryPaymentGateway implements PaymentGateway {

    private final PaymentProvider provider;
    private final boolean enabled;
    private final AtomicBoolean down = new AtomicBoolean();
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicReference<Optional<Instant>> expiresAt = new AtomicReference<>(Optional.empty());
    private final List<CheckoutRequest> requests = new CopyOnWriteArrayList<>();
    private final List<CheckoutAttempt> attempts = new CopyOnWriteArrayList<>();
    private final Map<String, Money> charged = new ConcurrentHashMap<>();

    public InMemoryPaymentGateway(PaymentProvider provider) {
        this(provider, true);
    }

    public InMemoryPaymentGateway(PaymentProvider provider, boolean enabled) {
        this.provider = provider;
        this.enabled = enabled;
    }

    @Override
    public PaymentProvider provider() {
        return provider;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public Outcome createCheckout(CheckoutRequest request, CheckoutAttempt attempt) {
        requests.add(request);
        attempts.add(attempt);
        if (down.get()) {
            return new Outcome.Unavailable();
        }
        String id = "checkout-" + sequence.incrementAndGet();
        charged.put(id, request.amount());
        return new Outcome.Created(new Checkout(id, URI.create("https://pay.example.com/" + id), expiresAt.get()));
    }

    public void down(boolean isDown) {
        down.set(isDown);
    }

    /** The next checkouts expire at this instant, as a MODO payment request. */
    public void expiringAt(Instant expiry) {
        expiresAt.set(Optional.of(expiry));
    }

    public List<CheckoutRequest> requests() {
        return List.copyOf(requests);
    }

    public List<CheckoutAttempt> attempts() {
        return List.copyOf(attempts);
    }

    public Money chargedAt(String checkoutId) {
        return charged.get(checkoutId);
    }
}
