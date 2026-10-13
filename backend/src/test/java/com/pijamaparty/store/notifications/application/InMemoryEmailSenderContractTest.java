package com.pijamaparty.store.notifications.application;

import com.pijamaparty.store.notifications.domain.EmailMessage;
import java.util.List;

class InMemoryEmailSenderContractTest extends EmailSenderContractTest<InMemoryEmailSender> {

    @Override
    protected InMemoryEmailSender createSubject() {
        return new InMemoryEmailSender();
    }

    @Override
    protected List<String> deliveredTo() {
        return subject.delivered().stream().map(EmailMessage::to).toList();
    }

    @Override
    protected void serviceIsDown() {
        subject.mode(InMemoryEmailSender.Mode.DOWN);
    }

    @Override
    protected void serviceRejectsTheEmail() {
        subject.mode(InMemoryEmailSender.Mode.REJECT);
    }
}
