package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;
import com.pijamaparty.store.payments.domain.VerifiedPayment;
import com.pijamaparty.store.payments.domain.VerifiedStatus;
import com.pijamaparty.store.platform.domain.Money;

class InMemoryPaymentVerifierContractTest extends PaymentVerifierContractTest<InMemoryPaymentVerifier> {

    @Override
    protected InMemoryPaymentVerifier createSubject() {
        return new InMemoryPaymentVerifier(PaymentProvider.MERCADOPAGO);
    }

    @Override
    protected PaymentProvider expectedProvider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    protected void givenPayment(String id, long orderNumber, VerifiedStatus status, Money amount) {
        subject.holds(new VerifiedPayment(id, orderNumber, status, amount));
    }

    @Override
    protected void providerIsDown() {
        subject.down(true);
    }
}
