package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.PasswordResetLinks;
import com.pijamaparty.store.platform.application.InMemoryTokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Fake of {@code accounts.api.PasswordResetLinks}: customers, each issue a new token. */
class InMemoryPasswordResetLinks implements PasswordResetLinks {

    private final Map<UUID, String[]> customers = new ConcurrentHashMap<>();
    private final AtomicLong issued = new AtomicLong();

    UUID customer(String email, String name) {
        UUID id = UUID.randomUUID();
        customers.put(id, new String[] {email, name});
        return id;
    }

    @Override
    public Optional<PasswordResetLink> issue(UUID userId) {
        return Optional.ofNullable(customers.get(userId)).map(customer -> new PasswordResetLink(customer[0],
            customer[1], new AccountToken(InMemoryTokenGenerator.nth(issued.incrementAndGet()))));
    }
}
