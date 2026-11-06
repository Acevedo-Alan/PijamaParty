package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.payments.domain.CheckoutAttempt;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake of {@link LoadPayments}, {@link SavePayments}, {@link PaymentsToConfirm}, {@link PaymentConfirmations},
 * {@link PaymentsByCheckout} and {@link CheckoutsToReconcile}: one payment per order and provider, and per order number
 * and provider, as the unique keys; the attempts of each payment numbered one after another, a checkout id once per
 * provider. Every call is synchronized, as the row lock would serialize it.
 */
public class InMemoryPaymentStore implements LoadPayments, SavePayments, PaymentsToConfirm, PaymentConfirmations,
    PaymentsByCheckout, CheckoutsToReconcile {

    /** What the table would hold of a payment; {@code checkout} is the current one. */
    public record StoredPayment(UUID id, long orderNumber, Money amount, Optional<Checkout> checkout,
                                PaymentStatus status, Optional<String> externalPaymentId,
                                Optional<CardCharge> cardCharge) {

        StoredPayment withCheckout(Checkout newCheckout) {
            return new StoredPayment(id, orderNumber, amount, Optional.of(newCheckout), status, externalPaymentId,
                cardCharge);
        }

        /** An approval or rejection verified: the card charge is over (T-E3-32). */
        StoredPayment verified(PaymentStatus newStatus, String newExternalPaymentId) {
            return new StoredPayment(id, orderNumber, amount, checkout, newStatus, Optional.of(newExternalPaymentId),
                Optional.empty());
        }

        StoredPayment withCardCharge(Optional<CardCharge> newCardCharge) {
            return new StoredPayment(id, orderNumber, amount, checkout, status, externalPaymentId, newCardCharge);
        }
    }

    /** What {@code card_charge_state} and {@code card_charge_started_at} would hold. */
    public record CardCharge(boolean inReview, Instant startedAt, UUID key) {}

    /** What {@code payment_checkout} would hold of an attempt. */
    public record StoredAttempt(CheckoutAttempt attempt, Optional<Checkout> checkout, Instant createdAt) {}

    private record Key(UUID orderId, PaymentProvider provider) {}

    private final Map<Key, StoredPayment> payments = new ConcurrentHashMap<>();
    private final Map<Key, List<StoredAttempt>> attempts = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<Checkout> checkoutOf(UUID orderId, PaymentProvider provider) {
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
            payment.orderNumber(), payment.amount(), Optional.empty(), PaymentStatus.PENDING, Optional.empty(),
            Optional.empty()));
    }

    @Override
    public synchronized CardChargeStart beginCardCharge(UUID orderId, PaymentProvider provider, UUID submitKey,
                                                        Instant at, Instant staleBefore) {
        var key = new Key(orderId, provider);
        StoredPayment payment = payments.get(key);
        if (payment != null && payment.status() == PaymentStatus.APPROVED) {
            return CardChargeStart.ALREADY_APPROVED;
        }
        if (payment == null) {
            return CardChargeStart.BUSY;
        }
        boolean free = payment.cardCharge().isEmpty()
            || payment.cardCharge().get().key().equals(submitKey)
            || (!payment.cardCharge().get().inReview() && payment.cardCharge().get().startedAt().isBefore(staleBefore));
        if (!free) {
            return CardChargeStart.BUSY;
        }
        payments.put(key, payment.withCardCharge(Optional.of(new CardCharge(false, at, submitKey))));
        return CardChargeStart.STARTED;
    }

    private static boolean ownedBy(StoredPayment payment, UUID submitKey) {
        return payment.cardCharge().filter(charge -> charge.key().equals(submitKey)).isPresent();
    }

    @Override
    public synchronized void releaseCardCharge(UUID orderId, PaymentProvider provider, UUID submitKey) {
        payments.computeIfPresent(new Key(orderId, provider),
            (key, payment) -> ownedBy(payment, submitKey) ? payment.withCardCharge(Optional.empty()) : payment);
    }

    @Override
    public synchronized void holdCardChargeInReview(UUID orderId, PaymentProvider provider, UUID submitKey) {
        payments.computeIfPresent(new Key(orderId, provider), (key, payment) ->
            payment.cardCharge().filter(charge -> !charge.inReview() && charge.key().equals(submitKey)).isPresent()
                ? payment.withCardCharge(Optional.of(new CardCharge(true, payment.cardCharge().get().startedAt(), payment.cardCharge().get().key())))
                : payment);
    }

    @Override
    public synchronized Optional<CheckoutAttempt> startAttempt(UUID orderId, PaymentProvider provider, Instant at) {
        var key = new Key(orderId, provider);
        StoredPayment payment = payments.get(key);
        if (payment == null) {
            return Optional.empty();
        }
        List<StoredAttempt> stored = attempts.computeIfAbsent(key, k -> new ArrayList<>());
        CheckoutAttempt attempt = CheckoutAttempt.of(payment.orderNumber(), stored.size() + 1);
        stored.add(new StoredAttempt(attempt, Optional.empty(), at));
        return Optional.of(attempt);
    }

    @Override
    public synchronized boolean recordCheckout(UUID orderId, PaymentProvider provider, int attempt, Checkout checkout,
                                               Instant at) {
        var key = new Key(orderId, provider);
        StoredPayment payment = payments.get(key);
        List<StoredAttempt> stored = attempts.getOrDefault(key, List.of());
        boolean alreadyRecorded = attempts.entrySet().stream()
            .filter(entry -> entry.getKey().provider() == provider)
            .flatMap(entry -> entry.getValue().stream())
            .anyMatch(other -> other.checkout().filter(c -> c.id().equals(checkout.id())).isPresent());
        if (payment == null || attempt < 1 || attempt > stored.size() || alreadyRecorded
            || stored.get(attempt - 1).checkout().isPresent()) {
            return false;
        }
        StoredAttempt before = stored.get(attempt - 1);
        stored.set(attempt - 1, new StoredAttempt(before.attempt(), Optional.of(checkout), before.createdAt()));
        boolean laterHasOne = stored.subList(attempt, stored.size()).stream()
            .anyMatch(later -> later.checkout().isPresent());
        if (laterHasOne) {
            return false;
        }
        payments.put(key, payment.withCheckout(checkout));
        return true;
    }

    @Override
    public synchronized Optional<PaymentToConfirm> lock(PaymentProvider provider, long orderNumber) {
        return payments.entrySet().stream()
            .filter(entry -> entry.getKey().provider() == provider && entry.getValue().orderNumber() == orderNumber)
            .findFirst()
            .map(entry -> new PaymentToConfirm(entry.getKey().orderId(), orderNumber, entry.getValue().status(),
                entry.getValue().amount(), entry.getValue().externalPaymentId()));
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

    @Override
    public synchronized OptionalLong orderNumberOf(PaymentProvider provider, String checkoutId) {
        return attempts.entrySet().stream()
            .filter(entry -> entry.getKey().provider() == provider && entry.getValue().stream()
                .anyMatch(attempt -> attempt.checkout().filter(c -> c.id().equals(checkoutId)).isPresent()))
            .mapToLong(entry -> payments.get(entry.getKey()).orderNumber())
            .findFirst();
    }

    @Override
    public synchronized List<CheckoutToReconcile> due(Set<PaymentProvider> providers, Set<PaymentStatus> statuses,
                                                      Instant createdBefore, Instant expiringAfter, int limit) {
        record Due(PaymentProvider provider, Checkout checkout, Instant createdAt) {}
        return attempts.entrySet().stream()
            .filter(entry -> providers.contains(entry.getKey().provider()))
            .filter(entry -> statuses.contains(payments.get(entry.getKey()).status()))
            .flatMap(entry -> entry.getValue().stream()
                .filter(attempt -> attempt.checkout().isPresent())
                .map(attempt -> new Due(entry.getKey().provider(), attempt.checkout().get(), attempt.createdAt())))
            .filter(due -> due.createdAt().isBefore(createdBefore))
            .filter(due -> due.checkout().expiresAt().filter(expiry -> expiry.isAfter(expiringAfter)).isPresent())
            .sorted(Comparator.comparing(Due::createdAt))
            .limit(limit)
            .map(due -> new CheckoutToReconcile(due.provider(), due.checkout().id()))
            .toList();
    }

    public synchronized Optional<StoredPayment> paymentOf(UUID orderId, PaymentProvider provider) {
        return Optional.ofNullable(payments.get(new Key(orderId, provider)));
    }

    public synchronized List<StoredAttempt> attemptsOf(UUID orderId, PaymentProvider provider) {
        return List.copyOf(attempts.getOrDefault(new Key(orderId, provider), List.of()));
    }

    public synchronized int size() {
        return payments.size();
    }
}
