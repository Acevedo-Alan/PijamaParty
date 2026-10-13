package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import java.util.Optional;
import java.util.UUID;

/** UC-14: the payments already created. Joins the caller's transaction, or runs in its own. */
public interface LoadPayments {

    /** The checkout of the order's payment at the provider; empty without a payment or before its checkout. */
    Optional<Checkout> checkoutOf(UUID orderId, PaymentProvider provider);
}
