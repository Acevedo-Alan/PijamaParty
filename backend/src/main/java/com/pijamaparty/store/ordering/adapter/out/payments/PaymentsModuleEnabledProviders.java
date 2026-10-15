package com.pijamaparty.store.ordering.adapter.out.payments;

import com.pijamaparty.store.ordering.application.EnabledPaymentProviders;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.payments.api.PaymentProviders;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** The providers the payments module has switched on (T-E3b-02), through its public API. */
@Component
class PaymentsModuleEnabledProviders implements EnabledPaymentProviders {

    private final PaymentProviders providers;

    PaymentsModuleEnabledProviders(PaymentProviders providers) {
        this.providers = providers;
    }

    @Override
    public Set<PaymentProvider> enabled() {
        Set<PaymentProvider> enabled = EnumSet.noneOf(PaymentProvider.class);
        for (var provider : providers.enabled()) {
            enabled.add(PaymentProviderMapping.fromPayments(provider));
        }
        return Collections.unmodifiableSet(enabled);
    }
}
