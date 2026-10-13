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
    private final Map<String, VerifiedPayment> payments = new ConcurrentHashMap<>();
    private final AtomicBoolean down = new AtomicBoolean();
    private final List<String> lookups = new CopyOnWriteArrayList<>();

    public InMemoryPaymentVerifier(PaymentProvider provider) {
        this.provider = provider;
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
    public Verification verify(String externalPaymentId) {
        lookups.add(externalPaymentId);
        if (down.get()) {
            return new Verification.Unavailable();
        }
        VerifiedPayment payment = payments.get(externalPaymentId);
        return payment == null ? new Verification.NotFound() : new Verification.Found(payment);
    }
}
