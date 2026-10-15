package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.util.Set;

class InMemoryEnabledPaymentProvidersContractTest
    extends EnabledPaymentProvidersContractTest<InMemoryEnabledPaymentProviders> {

    @Override
    protected InMemoryEnabledPaymentProviders createSubject() {
        return new InMemoryEnabledPaymentProviders(Set.of(PaymentProvider.MERCADOPAGO));
    }
}
