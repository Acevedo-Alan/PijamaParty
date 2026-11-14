package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.notifications.adapter.out.mail.EmailWriter;
import com.pijamaparty.store.settings.api.StoreContact.Contact;
import java.net.URI;
import org.springframework.context.MessageSource;

/** An email about a customer account: only the parts of every email. */
final class AccountEmailWriter extends EmailWriter<AccountEmailWriter> {

    AccountEmailWriter(MessageSource messages, URI publicUrl, Contact contact) {
        super(messages, publicUrl, contact);
    }

    @Override
    protected AccountEmailWriter self() {
        return this;
    }
}
