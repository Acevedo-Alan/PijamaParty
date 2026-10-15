package com.pijamaparty.store.payments.api;

import java.util.Set;

/**
 * UC-13, UC-14 (T-E3b-02): the providers a buyer may choose at the checkout. MercadoPago always; MODO only while
 * {@code store.payments.modo.enabled} is on. An order placed with a provider that is later switched off still pays
 * through it.
 */
public interface PaymentProviders {

    Set<PaymentProvider> enabled();
}
