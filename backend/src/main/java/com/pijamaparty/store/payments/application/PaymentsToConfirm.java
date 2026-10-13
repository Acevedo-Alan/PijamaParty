package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import com.pijamaparty.store.platform.domain.Money;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** UC-15: the payment a provider's notification is about. Joins the caller's transaction, which must exist. */
public interface PaymentsToConfirm {

    /**
     * The payment of the order number at the provider, locked until the transaction ends, so two notifications of
     * the same payment are processed one after the other; empty when there is none.
     */
    Optional<PaymentToConfirm> lock(PaymentProvider provider, long orderNumber);

    /** @param amount what the order has to be paid with (R-02) */
    record PaymentToConfirm(UUID orderId, long orderNumber, PaymentStatus status, Money amount) {

        public PaymentToConfirm {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(amount, "amount");
        }
    }
}
