package com.pijamaparty.store.accounts.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * An external account waiting for its link confirmation email (UC-27, T-E4-14): the token is minted when the email is
 * sent, from this request. One per customer (V35); the first request wins while it is fresh ({@link #yieldsTo}).
 */
public record PendingLink(UserId userId, ExternalAccount account, Instant requestedAt) {

    /** How long a request holds against another external account: the lifetime of the link it sends. */
    public static final Duration HOLD = UserTokenPurpose.LINK_CONFIRMATION.lifetime();

    public PendingLink {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(requestedAt, "requestedAt");
        requestedAt = requestedAt.truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Whether a request for {@code other} at {@code now} may take this one's place (decided, security review of
     * T-E4-14): the same external account refreshes it; another one only once this is {@link #HOLD} old. Whoever asks
     * for someone else's email can delay the owner's own request, never swap the account it links.
     */
    public boolean yieldsTo(ExternalAccount other, Instant now) {
        return account.equals(other) || !now.isBefore(requestedAt.plus(HOLD));
    }
}
