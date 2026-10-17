package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-27 (T-E4-14): a customer needs the email that confirms the link of an external account whose email the provider
 * does not vouch for (Facebook). Published in the transaction that keeps the pending link, as {@code SYSTEM};
 * listeners run in it, so the email is enqueued only if the request commits.
 */
public record LinkConfirmationRequested(UUID userId) implements DomainEvent {

    public LinkConfirmationRequested {
        Objects.requireNonNull(userId, "userId");
    }
}
