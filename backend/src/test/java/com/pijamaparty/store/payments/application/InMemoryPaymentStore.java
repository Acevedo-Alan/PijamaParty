package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of {@link LoadPayments}, {@link SavePayments}, {@link PaymentsToConfirm} and {@link PaymentConfirmations}: one
 * payment per order and provider, and per order number and provider, as the unique keys. Locks are not modeled.
 */
public class InMemoryPaymentStore implements LoadPayments, SavePayments, PaymentsToConfirm, PaymentConfirmations {

    /** What the table would hold of a payment. */
    public record StoredPayment(UUID id, long orderNumber, Money amount, Optional<Checkout> checkout,
                                PaymentStatus status, Optional<String> externalPaymentId) {

        StoredPayment withCheckout(Checkout newCheckout) {
            return new StoredPayment(id, orderNumber, amount, Optional.of(newCheckout), status, externalPaymentId);
        }

        StoredPayment verified(PaymentStatus newStatus, String newExternalPaymentId) {
            return new StoredPayment(id, orderNumber, amount, checkout, newStatus, Optional.of(newExternalPaymentId));
        }
    }

    private record Key(UUID orderId, PaymentProvider provider) {}

    private final Map<Key, StoredPayment> payments = new ConcurrentHashMap<>();

    @Override
    public Optional<Checkout> checkoutOf(UUID orderId, PaymentProvider provider) {
        return Optional.ofNullable(payments.get(new Key(orderId, provider))).flatMap(StoredPayment::checkout);
    }

    @Override
    public synchronized void insertPending(NewPayment payment) {
        boolean numberTaken = payments.entrySet().stream().anyMatch(entry ->
            entry.getKey().provider() == payment.provider()
                && entry.getValue().orderNumber() == payment.orderNumber()
                && !entry.getKey().orderId().equals(payment.orderId()));
        if (numberTaken) {
            throw new IllegalStateException("another payment of the provider has order number "
                + payment.orderNumber());
        }
        payments.putIfAbsent(new Key(payment.orderId(), payment.provider()), new StoredPayment(payment.id(),
            payment.orderNumber(), payment.amount(), Optional.empty(), PaymentStatus.PENDING, Optional.empty()));
    }

    @Override
    public synchronized boolean recordCheckout(UUID orderId, PaymentProvider provider, Checkout checkout,
                                               Instant at) {
        var key = new Key(orderId, provider);
        StoredPayment before = payments.get(key);
        if (before == null || before.checkout().isPresent()) {
            return false;
        }
        payments.put(key, before.withCheckout(checkout));
        return true;
    }

    @Override
    public synchronized Optional<PaymentToConfirm> lock(PaymentProvider provider, long orderNumber) {
        return payments.entrySet().stream()
            .filter(entry -> entry.getKey().provider() == provider && entry.getValue().orderNumber() == orderNumber)
            .findFirst()
            .map(entry -> new PaymentToConfirm(entry.getKey().orderId(), orderNumber, entry.getValue().status(),
                entry.getValue().amount()));
    }

    @Override
    public synchronized boolean record(UUID orderId, PaymentProvider provider, PaymentStatus status,
                                       String externalPaymentId, Instant at) {
        if (status == PaymentStatus.PENDING) {
            throw new IllegalArgumentException("only a verified approval or rejection is recorded");
        }
        var key = new Key(orderId, provider);
        StoredPayment before = payments.get(key);
        if (before == null || before.status() == PaymentStatus.APPROVED) {
            return false;
        }
        payments.put(key, before.verified(status, externalPaymentId));
        return true;
    }

    public Optional<StoredPayment> paymentOf(UUID orderId, PaymentProvider provider) {
        return Optional.ofNullable(payments.get(new Key(orderId, provider)));
    }

    public int size() {
        return payments.size();
    }
}
