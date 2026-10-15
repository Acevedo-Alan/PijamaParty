package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import java.util.OptionalLong;

/**
 * UC-15 (T-E3b-03): the payment a provider's checkout belongs to, also for an older attempt, since MODO does not return
 * the {@code external_intention_id} when asked. Joins the caller's transaction, which must exist.
 */
public interface PaymentsByCheckout {

    /** The order number of the payment that recorded this checkout of the provider; empty when none did. */
    OptionalLong orderNumberOf(PaymentProvider provider, String checkoutId);
}
