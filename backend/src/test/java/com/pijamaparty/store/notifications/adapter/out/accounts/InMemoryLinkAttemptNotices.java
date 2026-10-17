package com.pijamaparty.store.notifications.adapter.out.accounts;

import com.pijamaparty.store.accounts.api.LinkAttemptNotices;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Fake of {@code accounts.api.LinkAttemptNotices}: the customers it knows; the administrator is never a recipient. */
class InMemoryLinkAttemptNotices implements LinkAttemptNotices {

    private final Map<UUID, Recipient> customers = new ConcurrentHashMap<>();

    UUID customer(String email, String name) {
        UUID id = UUID.randomUUID();
        customers.put(id, new Recipient(email, name));
        return id;
    }

    /** Known, so it is not just an unknown id, but never a recipient. */
    UUID administrator() {
        return UUID.randomUUID();
    }

    @Override
    public Optional<Recipient> recipientOf(UUID userId) {
        return Optional.ofNullable(customers.get(userId));
    }
}
