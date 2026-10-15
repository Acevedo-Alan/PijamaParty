package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.api.PaymentProviders;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** UC-14 (T-E3b-02): the providers whose gateway is switched on. */
@Service
class EnabledPaymentProvidersService implements PaymentProviders {

    private final Set<PaymentProvider> enabled;

    EnabledPaymentProvidersService(List<PaymentGateway> gateways) {
        Set<PaymentProvider> providers = EnumSet.noneOf(PaymentProvider.class);
        for (PaymentGateway gateway : gateways) {
            if (gateway.enabled()) {
                providers.add(gateway.provider());
            }
        }
        this.enabled = Collections.unmodifiableSet(providers);
    }

    @Override
    public Set<PaymentProvider> enabled() {
        return enabled;
    }
}
