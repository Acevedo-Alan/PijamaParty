package com.pijamaparty.store.ordering.adapter.out.payments;

import com.pijamaparty.store.ordering.domain.PaymentProvider;

/** The order's provider as the payments module names it. */
final class PaymentProviderMapping {

    private PaymentProviderMapping() {
    }

    static com.pijamaparty.store.payments.api.PaymentProvider toPayments(PaymentProvider provider) {
        return switch (provider) {
            case MERCADOPAGO -> com.pijamaparty.store.payments.api.PaymentProvider.MERCADOPAGO;
            case MODO -> com.pijamaparty.store.payments.api.PaymentProvider.MODO;
        };
    }
}
