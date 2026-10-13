package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/** UC-15: records what the provider verified of a payment. Joins the caller's transaction, which must exist. */
public interface PaymentConfirmations {

    /**
     * Sets the status and the provider's payment id of the order's payment, unless it is already {@code APPROVED}
     * (an approval is final).
     *
     * @param status {@code APPROVED} or {@code REJECTED}
     * @return false when there is no such payment or it was already approved
     */
    boolean record(UUID orderId, PaymentProvider provider, PaymentStatus status, String externalPaymentId,
                   Instant at);
}
