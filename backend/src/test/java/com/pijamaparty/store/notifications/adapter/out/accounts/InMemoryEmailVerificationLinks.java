package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.EmailVerificationLinks;
import com.pijamaparty.store.platform.application.InMemoryTokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Fake of {@code accounts.api.EmailVerificationLinks}: unverified customers, each issue a new token. */
class InMemoryEmailVerificationLinks implements EmailVerificationLinks {

    private final Map<UUID, String[]> unverified = new ConcurrentHashMap<>();
    private final AtomicLong issued = new AtomicLong();

    UUID customer(String email, String name) {
        UUID id = UUID.randomUUID();
        unverified.put(id, new String[] {email, name});
        return id;
    }

    @Override
    public Optional<EmailVerificationLink> issue(UUID userId) {
        return Optional.ofNullable(unverified.get(userId)).map(customer -> new EmailVerificationLink(customer[0],
            customer[1], new AccountToken(InMemoryTokenGenerator.nth(issued.incrementAndGet()))));
    }
}
