package com.pijamaparty.store.ordering.application;

class InMemoryChargeCardsContractTest extends ChargeCardsContractTest<InMemoryChargeCards> {

    @Override
    protected InMemoryChargeCards createSubject() {
        return new InMemoryChargeCards();
    }

    @Override
    protected void cardsAreRejectedForInsufficientFunds() {
        subject.answers(new PayOrderWithCard.Result.Rejected(PayOrderWithCard.Rejection.INSUFFICIENT_FUNDS));
    }

    @Override
    protected void cardsAreInReview() {
        subject.answers(new PayOrderWithCard.Result.InProcess());
    }

    @Override
    protected void providerIsDown() {
        subject.answers(new PayOrderWithCard.Result.ProviderUnavailable());
    }
}
