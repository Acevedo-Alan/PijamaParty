package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.Checkout;
import com.pijamaparty.store.platform.domain.Money;

class InMemoryPaymentGatewayContractTest extends PaymentGatewayContractTest<InMemoryPaymentGateway> {

    @Override
    protected InMemoryPaymentGateway createSubject() {
        return new InMemoryPaymentGateway(PaymentProvider.MERCADOPAGO);
    }

    @Override
    protected PaymentProvider expectedProvider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    protected void providerIsDown() {
        subject.down(true);
    }

    @Override
    protected Money chargedAmount(Checkout checkout) {
        return subject.chargedAt(checkout.id());
    }
}
