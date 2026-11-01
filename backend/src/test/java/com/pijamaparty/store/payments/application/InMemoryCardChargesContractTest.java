package com.pijamaparty.store.payments.application;

import com.pijamaparty.store.payments.api.PaymentProvider;

class InMemoryCardChargesContractTest extends CardChargesContractTest<InMemoryCardCharges> {

    @Override
    protected InMemoryCardCharges createSubject() {
        return new InMemoryCardCharges();
    }

    @Override
    protected PaymentProvider expectedProvider() {
        return PaymentProvider.MERCADOPAGO;
    }

    @Override
    protected void cardsAreRejectedForInsufficientFunds() {
        subject.rejectForFunds();
    }

    @Override
    protected void cardsAreInReview() {
        subject.review();
    }

    @Override
    protected void tokensAreRefused() {
        subject.refuse();
    }

    @Override
    protected void providerIsDown() {
        subject.down();
    }
}
