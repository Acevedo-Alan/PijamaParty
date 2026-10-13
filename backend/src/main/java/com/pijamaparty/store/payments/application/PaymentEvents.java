package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import java.util.UUID;

/** Outbound port: publishes the events of {@code payments.api} in the caller's transaction. */
public interface PaymentEvents {

    void paymentApproved(UUID orderId, PaymentProvider provider);
}
