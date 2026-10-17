package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.LinkConfirmationLinks;
import com.pijamaparty.store.platform.application.InMemoryTokenGenerator;
import com.pijamaparty.store.platform.domain.AccessToken.AccountToken;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Fake of {@code accounts.api.LinkConfirmationLinks}: customers with a pending link, each issue a new token. */
class InMemoryLinkConfirmationLinks implements LinkConfirmationLinks {

    private record Pending(String email, String name, Provider provider) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong issued = new AtomicLong();

    UUID customer(String email, String name, Provider provider) {
        UUID id = UUID.randomUUID();
        pending.put(id, new Pending(email, name, provider));
        return id;
    }

    @Override
    public Optional<LinkConfirmationLink> issue(UUID userId) {
        return Optional.ofNullable(pending.get(userId)).map(customer -> new LinkConfirmationLink(customer.email(),
            customer.name(), customer.provider(),
            new AccountToken(InMemoryTokenGenerator.nth(issued.incrementAndGet()))));
    }
}
