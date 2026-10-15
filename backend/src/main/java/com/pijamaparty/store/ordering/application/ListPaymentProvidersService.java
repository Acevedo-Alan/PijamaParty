package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
class ListPaymentProvidersService implements ListPaymentProviders {

    private final EnabledPaymentProviders providers;

    ListPaymentProvidersService(EnabledPaymentProviders providers) {
        this.providers = providers;
    }

    @Override
    public Result execute() {
        var enabled = providers.enabled();
        return new Result(Arrays.stream(PaymentProvider.values()).filter(enabled::contains).toList());
    }
}
