package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.util.List;

/**
 * UC-13 (T-E3b-04): step 2 of the checkout. The payment providers buyers may choose, MercadoPago first. Read only.
 */
public interface ListPaymentProviders {

    Result execute();

    /** In the order of {@link PaymentProvider}. */
    record Result(List<PaymentProvider> providers) {

        public Result {
            providers = List.copyOf(providers);
        }
    }
}
