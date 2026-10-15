package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.util.Set;

/** Fake of {@link EnabledPaymentProviders}: a fixed set. */
public record InMemoryEnabledPaymentProviders(Set<PaymentProvider> enabled) implements EnabledPaymentProviders {

    public InMemoryEnabledPaymentProviders {
        enabled = Set.copyOf(enabled);
    }
}
