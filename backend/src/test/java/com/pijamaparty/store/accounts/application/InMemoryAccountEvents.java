package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.api.EmailVerificationRequested;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of {@link AccountEvents}: records what it publishes. */
public class InMemoryAccountEvents implements AccountEvents {

    private final List<Object> published = new CopyOnWriteArrayList<>();

    @Override
    public void emailVerificationRequested(UserId customer) {
        published.add(new EmailVerificationRequested(customer.value()));
    }

    public List<Object> published() {
        return List.copyOf(published);
    }
}
