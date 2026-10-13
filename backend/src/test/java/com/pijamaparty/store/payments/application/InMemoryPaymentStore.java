package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@link LoadPayments} and {@link SavePayments}: one payment per order and provider, as the unique key. */
public class InMemoryPaymentStore implements LoadPayments, SavePayments {

    /** What the table would hold of a payment. */
    public record StoredPayment(UUID id, Money amount, Optional<Checkout> checkout) {}

    private record Key(UUID orderId, PaymentProvider provider) {}

    private final Map<Key, StoredPayment> payments = new ConcurrentHashMap<>();

    @Override
    public Optional<Checkout> checkoutOf(UUID orderId, PaymentProvider provider) {
        return Optional.ofNullable(payments.get(new Key(orderId, provider))).flatMap(StoredPayment::checkout);
    }

    @Override
    public void insertPending(NewPayment payment) {
        payments.putIfAbsent(new Key(payment.orderId(), payment.provider()),
            new StoredPayment(payment.id(), payment.amount(), Optional.empty()));
    }

    @Override
    public boolean recordCheckout(UUID orderId, PaymentProvider provider, Checkout checkout, Instant at) {
        var key = new Key(orderId, provider);
        StoredPayment before = payments.get(key);
        if (before == null || before.checkout().isPresent()) {
            return false;
        }
        return payments.replace(key, before, new StoredPayment(before.id(), before.amount(), Optional.of(checkout)));
    }

    public Optional<StoredPayment> paymentOf(UUID orderId, PaymentProvider provider) {
        return Optional.ofNullable(payments.get(new Key(orderId, provider)));
    }

    public int size() {
        return payments.size();
    }
}
