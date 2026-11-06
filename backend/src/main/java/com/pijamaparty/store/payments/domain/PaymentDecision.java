package com.pijamaparty.store.payments.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-15: what a verified payment does to the store's payment of the order. An approved payment is final: nothing a
 * later notification says changes it. A rejection is recorded but leaves the order alone, so the buyer can try again
 * until the reservation expires (UC-16). Only the exact amount of the order approves it (R-02).
 */
public sealed interface PaymentDecision {

    /** The payment becomes {@code APPROVED}: the order is paid. */
    record Approve() implements PaymentDecision {}

    /** The payment was already approved: a repeated or later notification, with no effect. */
    record AlreadyApproved() implements PaymentDecision {}

    /**
     * T-E3-32: an approved payment of the provider other than the one recorded, for an order already paid: a second
     * charge. Nothing changes, and someone has to refund it.
     */
    record DuplicateApproval() implements PaymentDecision {}

    /** The payment becomes {@code REJECTED}, the last attempt the buyer made. */
    record Reject() implements PaymentDecision {}

    /** Pending, or something the store does not act on. */
    record Unchanged() implements PaymentDecision {}

    /** Approved for an amount other than the order's: nothing changes, and someone has to look at it. */
    record AmountMismatch(Money expected, Money paid) implements PaymentDecision {

        public AmountMismatch {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(paid, "paid");
        }
    }

    /**
     * What {@code verified} does to a payment in {@code current} status, created for {@code expected}, whose last
     * verified provider payment is {@code recordedId}.
     */
    static PaymentDecision decide(PaymentStatus current, Money expected, Optional<String> recordedId,
                                  VerifiedPayment verified) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(recordedId, "recordedId");
        Objects.requireNonNull(verified, "verified");
        if (current == PaymentStatus.APPROVED) {
            if (verified.status() != VerifiedStatus.APPROVED) {
                return new Unchanged();
            }
            return recordedId.filter(id -> !id.equals(verified.externalId())).isPresent()
                ? new DuplicateApproval()
                : new AlreadyApproved();
        }
        return switch (verified.status()) {
            case APPROVED -> verified.amount().equals(expected)
                ? new Approve()
                : new AmountMismatch(expected, verified.amount());
            case REJECTED -> new Reject();
            case PENDING, OTHER -> new Unchanged();
        };
    }
}
