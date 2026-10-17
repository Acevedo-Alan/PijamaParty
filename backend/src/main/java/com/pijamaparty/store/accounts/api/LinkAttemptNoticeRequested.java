package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-27 (T-E4-14): someone tried to connect an external account without email (Facebook) to an existing customer by
 * typing its email. Nothing was linked; the customer needs the notice email, which carries no link token. Published
 * in the transaction of the request, as {@code SYSTEM}; listeners run in it, so the email is enqueued only if the
 * request commits.
 */
public record LinkAttemptNoticeRequested(UUID userId) implements DomainEvent {

    public LinkAttemptNoticeRequested {
        Objects.requireNonNull(userId, "userId");
    }
}
