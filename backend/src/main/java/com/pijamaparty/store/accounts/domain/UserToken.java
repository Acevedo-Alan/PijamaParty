package com.pijamaparty.store.accounts.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The single-use token of an email link (UC-19): only its SHA-256 is kept (RNF-29.6). It lasts the lifetime of its
 * purpose from its creation, and it is used once, before it expires. Times are kept to the microsecond, as the
 * database stores them, so the expiry the database checks is exactly this one.
 */
public final class UserToken {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final UUID id;
    private final UserId userId;
    private final UserTokenPurpose purpose;
    private final String tokenHash;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant usedAt;

    private UserToken(UUID id, UserId userId, UserTokenPurpose purpose, String tokenHash, Instant createdAt,
                      Instant expiresAt, Instant usedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.usedAt = usedAt;
        if (!SHA_256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("a token is kept as its SHA-256 in lowercase hex");
        }
        if (!expiresAt.equals(createdAt.plus(purpose.lifetime()))) {
            throw new IllegalArgumentException("a token lasts exactly the lifetime of its purpose");
        }
        if (usedAt != null && (usedAt.isBefore(createdAt) || !usedAt.isBefore(expiresAt))) {
            throw new IllegalArgumentException("a token is used between its creation and its expiry");
        }
    }

    /** A new token of {@code purpose} for {@code user}, whose SHA-256 is {@code tokenHash}. */
    public static UserToken issue(UUID id, UserId user, UserTokenPurpose purpose, String tokenHash, Instant now) {
        Instant createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return new UserToken(id, user, purpose, tokenHash, createdAt, createdAt.plus(purpose.lifetime()), null);
    }

    /** Rebuilds a stored token; only persistence adapters call it. */
    public static UserToken restore(UUID id, UserId user, UserTokenPurpose purpose, String tokenHash,
                                    Instant createdAt, Instant expiresAt, Instant usedAt) {
        return new UserToken(id, user, purpose, tokenHash, createdAt, expiresAt, usedAt);
    }

    /**
     * Uses the token for {@code expected}: only a token of that purpose, never used and not expired at {@code now}.
     *
     * @return false, changing nothing, when it cannot be used
     */
    public boolean use(UserTokenPurpose expected, Instant now) {
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        if (purpose != expected || usedAt != null || at.isBefore(createdAt) || !at.isBefore(expiresAt)) {
            return false;
        }
        usedAt = at;
        return true;
    }

    public UUID id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public UserTokenPurpose purpose() {
        return purpose;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<Instant> usedAt() {
        return Optional.ofNullable(usedAt);
    }
}
