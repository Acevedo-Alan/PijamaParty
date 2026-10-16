package com.pijamaparty.store.accounts.adapter.out.event;

import com.pijamaparty.store.accounts.api.EmailVerificationRequested;
import com.pijamaparty.store.accounts.application.AccountEvents;
import com.pijamaparty.store.platform.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes the events of {@code accounts.api} through Spring; plain listeners run at once, in the caller's thread. */
@Component
class SpringAccountEvents implements AccountEvents {

    private final ApplicationEventPublisher publisher;

    SpringAccountEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void emailVerificationRequested(UserId customer) {
        publisher.publishEvent(new EmailVerificationRequested(customer.value()));
    }
}
