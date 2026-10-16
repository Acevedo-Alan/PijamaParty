package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-19 (T-E4-02): a customer needs a verification email, because it registered or because it signed in with its
 * right password before verifying. Published in that transaction, as {@code SYSTEM}; listeners run in it, so the
 * email is enqueued only if the account exists.
 */
public record EmailVerificationRequested(UUID userId) implements DomainEvent {

    public EmailVerificationRequested {
        Objects.requireNonNull(userId, "userId");
    }
}
