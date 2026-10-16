package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.notifications.adapter.out.mail.EmailWriter;
import org.springframework.context.MessageSource;

/** An email about a customer account: only the parts of every email. */
final class AccountEmailWriter extends EmailWriter<AccountEmailWriter> {

    AccountEmailWriter(MessageSource messages) {
        super(messages);
    }

    @Override
    protected AccountEmailWriter self() {
        return this;
    }
}
