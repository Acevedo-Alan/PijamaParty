package com.pijamaparty.store.accounts.adapter.out.event;

import com.pijamaparty.store.accounts.api.CustomerAccountDeleted;
import com.pijamaparty.store.accounts.api.EmailVerificationRequested;
import com.pijamaparty.store.accounts.api.LinkAttemptNoticeRequested;
import com.pijamaparty.store.accounts.api.LinkConfirmationRequested;
import com.pijamaparty.store.accounts.api.PasswordResetRequested;
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

    @Override
    public void passwordResetRequested(UserId customer) {
        publisher.publishEvent(new PasswordResetRequested(customer.value()));
    }

    @Override
    public void linkConfirmationRequested(UserId customer) {
        publisher.publishEvent(new LinkConfirmationRequested(customer.value()));
    }

    @Override
    public void linkAttemptNoticeRequested(UserId customer) {
        publisher.publishEvent(new LinkAttemptNoticeRequested(customer.value()));
    }

    @Override
    public void customerAccountDeleted(UserId customer) {
        publisher.publishEvent(new CustomerAccountDeleted(customer.value()));
    }
}
