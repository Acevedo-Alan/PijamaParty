package com.pijamaparty.store.ordering.application;

import com.pijamaparty.store.ordering.domain.PaymentProvider;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * UC-13 (T-E3b-04): step 2 of the checkout. The payment providers buyers may choose, MercadoPago first. Read only.
 */
public interface ListPaymentProviders {

    Result execute();

    /**
     * In the order of {@link PaymentProvider}; {@code cardFormPublicKey} (T-E3-29), the key of MercadoPago's card form
     * in the browser, empty when it is not configured.
     */
    record Result(List<PaymentProvider> providers, Optional<String> cardFormPublicKey) {

        public Result {
            providers = List.copyOf(providers);
            Objects.requireNonNull(cardFormPublicKey, "cardFormPublicKey");
        }

        public Result(List<PaymentProvider> providers) {
            this(providers, Optional.empty());
        }
    }
}
