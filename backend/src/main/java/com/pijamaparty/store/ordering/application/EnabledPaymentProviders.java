package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port (T-E3b-02): the providers buyers may choose at the checkout; MODO is switched on by configuration.
 */
@FunctionalInterface
public interface EnabledPaymentProviders {

    Set<PaymentProvider> enabled();

    /** T-E3-29: the key of MercadoPago's card form in the browser; empty when it is not configured. */
    default Optional<String> cardFormPublicKey() {
        return Optional.empty();
    }

    default boolean isEnabled(PaymentProvider provider) {
        return enabled().contains(provider);
    }
}
