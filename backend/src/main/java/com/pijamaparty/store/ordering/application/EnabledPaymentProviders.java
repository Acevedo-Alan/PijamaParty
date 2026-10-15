package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.util.Set;

/**
 * Outbound port (T-E3b-02): the providers buyers may choose at the checkout; MODO is switched on by configuration.
 */
@FunctionalInterface
public interface EnabledPaymentProviders {

    Set<PaymentProvider> enabled();

    default boolean isEnabled(PaymentProvider provider) {
        return enabled().contains(provider);
    }
}
