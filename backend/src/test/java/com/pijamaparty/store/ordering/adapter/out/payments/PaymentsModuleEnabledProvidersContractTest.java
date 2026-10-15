package com.pijamaparty.store.ordering.adapter.out.payments;

import com.pijamaparty.store.ordering.application.EnabledPaymentProvidersContractTest;
import com.pijamaparty.store.platform.testing.ApiTest;

/** Through the public API of the payments module, with the application's default configuration (MODO off). */
@ApiTest
class PaymentsModuleEnabledProvidersContractTest
    extends EnabledPaymentProvidersContractTest<PaymentsModuleEnabledProviders> {

    private final PaymentsModuleEnabledProviders adapter;

    PaymentsModuleEnabledProvidersContractTest(PaymentsModuleEnabledProviders adapter) {
        this.adapter = adapter;
    }

    @Override
    protected PaymentsModuleEnabledProviders createSubject() {
        return adapter;
    }
}
