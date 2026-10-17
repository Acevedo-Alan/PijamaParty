package com.pijamaparty.store.accounts.api;

import com.pijamaparty.store.platform.domain.DomainEvent;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-19 (T-E4-03): a customer asked to recover its password. Published in the transaction that found the account, as
 * {@code SYSTEM}; listeners run in it, so the email is enqueued only if the account exists.
 */
public record PasswordResetRequested(UUID userId) implements DomainEvent {

    public PasswordResetRequested {
        Objects.requireNonNull(userId, "userId");
    }
}
