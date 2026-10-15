package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fake of {@link PaymentVerifier}: the provider's payments by id, or unavailable while down. Records the lookups. */
public class InMemoryPaymentVerifier implements PaymentVerifier {

    private final PaymentProvider provider;
    private final boolean byCheckout;
    private final Map<String, VerifiedPayment> payments = new ConcurrentHashMap<>();
    private final AtomicBoolean down = new AtomicBoolean();
    private final List<String> lookups = new CopyOnWriteArrayList<>();

    public InMemoryPaymentVerifier(PaymentProvider provider) {
        this(provider, false);
    }

    /** @param byCheckout whether its payments are asked for by checkout id, as MODO's (T-E3b-03) */
    public InMemoryPaymentVerifier(PaymentProvider provider, boolean byCheckout) {
        this.provider = provider;
        this.byCheckout = byCheckout;
    }

    /** Test arrangement: the provider holds this payment, replacing what it held under the same id. */
    public void holds(VerifiedPayment payment) {
        payments.put(payment.externalId(), payment);
    }

    public void down(boolean isDown) {
        down.set(isDown);
    }

    public List<String> lookups() {
        return List.copyOf(lookups);
    }

    @Override
    public PaymentProvider provider() {
        return provider;
    }

    @Override
    public boolean verifiesByCheckout() {
        return byCheckout;
    }

    @Override
    public Verification verify(String externalPaymentId) {
        lookups.add(externalPaymentId);
        if (down.get()) {
            return new Verification.Unavailable();
        }
        VerifiedPayment payment = payments.get(externalPaymentId);
        return payment == null ? new Verification.NotFound() : new Verification.Found(payment);
    }
}
