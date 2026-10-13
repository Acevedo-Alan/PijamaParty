package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailType;
import java.util.UUID;

class InMemoryEmailComposerContractTest extends EmailComposerContractTest<InMemoryEmailComposer> {

    @Override
    protected InMemoryEmailComposer createSubject() {
        return new InMemoryEmailComposer(EmailType.ORDER_PAID_CUSTOMER);
    }

    @Override
    protected EmailType expectedType() {
        return EmailType.ORDER_PAID_CUSTOMER;
    }

    @Override
    protected UUID existingReference() {
        UUID reference = UUID.randomUUID();
        subject.writes(reference);
        return reference;
    }
}
