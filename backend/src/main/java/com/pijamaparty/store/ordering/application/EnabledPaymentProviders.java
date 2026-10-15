package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.PaymentProvider;

/**
 * Outbound port (T-E3b-02): whether buyers may choose a provider at the checkout; MODO is switched on by
 * configuration.
 */
@FunctionalInterface
public interface EnabledPaymentProviders {

    boolean isEnabled(PaymentProvider provider);
}
