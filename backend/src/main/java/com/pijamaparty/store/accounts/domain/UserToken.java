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
 * database stores them, so the expiry the database checks is exactly this one. A link confirmation (UC-27) always
 * names the external account it links, and no other purpose names one (V34).
 */
public final class UserToken {

    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

    private final UUID id;
    private final UserId userId;
    private final UserTokenPurpose purpose;
    private final String tokenHash;
    private final ExternalAccount externalAccount;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant usedAt;

    private UserToken(UUID id, UserId userId, UserTokenPurpose purpose, String tokenHash,
                      ExternalAccount externalAccount, Instant createdAt, Instant expiresAt, Instant usedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.externalAccount = externalAccount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.usedAt = usedAt;
        if (!SHA_256_HEX.matcher(tokenHash).matches()) {
            throw new IllegalArgumentException("a token is kept as its SHA-256 in lowercase hex");
        }
        if ((purpose == UserTokenPurpose.LINK_CONFIRMATION) != (externalAccount != null)) {
            throw new IllegalArgumentException("only a link confirmation names an external account, and it always does");
        }
        if (!expiresAt.equals(createdAt.plus(purpose.lifetime()))) {
            throw new IllegalArgumentException("a token lasts exactly the lifetime of its purpose");
        }
        if (usedAt != null && (usedAt.isBefore(createdAt) || !usedAt.isBefore(expiresAt))) {
            throw new IllegalArgumentException("a token is used between its creation and its expiry");
        }
    }

    /** A new token of {@code purpose} for {@code user}, whose SHA-256 is {@code tokenHash}; not a link confirmation. */
    public static UserToken issue(UUID id, UserId user, UserTokenPurpose purpose, String tokenHash, Instant now) {
        Instant createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return new UserToken(id, user, purpose, tokenHash, null, createdAt, createdAt.plus(purpose.lifetime()), null);
    }

    /** UC-27: a new link confirmation for {@code user}, which links {@code account} once used. */
    public static UserToken issueLinkConfirmation(UUID id, UserId user, ExternalAccount account, String tokenHash,
                                                  Instant now) {
        Objects.requireNonNull(account, "account");
        Instant createdAt = now.truncatedTo(ChronoUnit.MICROS);
        UserTokenPurpose purpose = UserTokenPurpose.LINK_CONFIRMATION;
        return new UserToken(id, user, purpose, tokenHash, account, createdAt, createdAt.plus(purpose.lifetime()),
            null);
    }

    /** Rebuilds a stored token that names no external account; only persistence adapters call it. */
    public static UserToken restore(UUID id, UserId user, UserTokenPurpose purpose, String tokenHash,
                                    Instant createdAt, Instant expiresAt, Instant usedAt) {
        return new UserToken(id, user, purpose, tokenHash, null, createdAt, expiresAt, usedAt);
    }

    /**
     * Rebuilds a stored token; only persistence adapters call it.
     *
     * @param externalAccount null unless it is a link confirmation
     */
    public static UserToken restore(UUID id, UserId user, UserTokenPurpose purpose, String tokenHash,
                                    ExternalAccount externalAccount, Instant createdAt, Instant expiresAt,
                                    Instant usedAt) {
        return new UserToken(id, user, purpose, tokenHash, externalAccount, createdAt, expiresAt, usedAt);
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

    /** The external account a link confirmation links; empty for every other purpose. */
    public Optional<ExternalAccount> externalAccount() {
        return Optional.ofNullable(externalAccount);
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
