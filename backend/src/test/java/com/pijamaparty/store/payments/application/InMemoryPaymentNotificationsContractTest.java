package com.pijamaparty.store.payments.application;

class InMemoryPaymentNotificationsContractTest extends PaymentNotificationsContractTest<InMemoryPaymentNotifications> {

    @Override
    protected InMemoryPaymentNotifications createSubject() {
        return new InMemoryPaymentNotifications();
    }
}
