package com.pijamaparty.store.payments.domain;

import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;

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

    /** What {@code verified} does to a payment in {@code current} status, created for {@code expected}. */
    static PaymentDecision decide(PaymentStatus current, Money expected, VerifiedPayment verified) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(verified, "verified");
        if (current == PaymentStatus.APPROVED) {
            return verified.status() == VerifiedStatus.APPROVED ? new AlreadyApproved() : new Unchanged();
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
