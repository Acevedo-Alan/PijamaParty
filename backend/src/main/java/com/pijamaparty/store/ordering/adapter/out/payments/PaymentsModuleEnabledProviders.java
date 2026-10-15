package com.pijamaparty.store.ordering.adapter.out.payments;

import com.pijamaparty.store.ordering.application.EnabledPaymentProviders;
import com.pijamaparty.store.ordering.domain.PaymentProvider;
import com.pijamaparty.store.payments.api.PaymentProviders;
import org.springframework.stereotype.Component;

/** The providers the payments module has switched on (T-E3b-02), through its public API. */
@Component
class PaymentsModuleEnabledProviders implements EnabledPaymentProviders {

    private final PaymentProviders providers;

    PaymentsModuleEnabledProviders(PaymentProviders providers) {
        this.providers = providers;
    }

    @Override
    public boolean isEnabled(PaymentProvider provider) {
        return providers.enabled().contains(PaymentProviderMapping.toPayments(provider));
    }
}
