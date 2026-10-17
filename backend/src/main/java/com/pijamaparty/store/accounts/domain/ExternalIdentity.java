package com.pijamaparty.store.accounts.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * An external account linked to a customer (UC-27), with the email it was linked with. A link is never updated: it is
 * unlinked and linked again (V34).
 */
public record ExternalIdentity(UUID id, UserId userId, ExternalAccount account, Email email, Instant createdAt) {

    public ExternalIdentity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Links {@code account} to {@code user} now, with {@code email}; times to the microsecond, as stored. */
    public static ExternalIdentity link(UUID id, UserId user, ExternalAccount account, Email email, Instant now) {
        return new ExternalIdentity(id, user, account, email, now.truncatedTo(ChronoUnit.MICROS));
    }

    @Override
    public String toString() {
        return "ExternalIdentity[id=" + id + ", userId=" + userId + ", " + account + ", ***]";
    }
}
