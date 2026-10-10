package com.pijamaparty.store.accounts.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A customer or the administrator. Owns the sign-in lock rule of UC-04: 5 consecutive failures lock the account for
 * 15 minutes. Only consecutive failures count (a successful sign-in resets them), and the failure that locks also
 * resets the counter, so after the lock there are 5 attempts again.
 */
public final class User {

    public static final int MAX_CONSECUTIVE_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    /** 00-dominio-y-reglas.md, maximum lengths. */
    public static final int MAX_NAME_LENGTH = 100;

    private final UserId id;
    private final Email email;
    private final boolean emailVerified;
    private final PasswordHash passwordHash;
    private final String name;
    private final UserRole role;
    private int failedSignInAttempts;
    private Instant lockedUntil;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Email email, boolean emailVerified, PasswordHash passwordHash, String name, UserRole role,
                 int failedSignInAttempts, Instant lockedUntil, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.emailVerified = emailVerified;
        this.passwordHash = passwordHash;
        this.name = Objects.requireNonNull(name, "name");
        this.role = Objects.requireNonNull(role, "role");
        this.failedSignInAttempts = failedSignInAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("name must be trimmed and 1 to " + MAX_NAME_LENGTH + " characters");
        }
        if (failedSignInAttempts < 0 || failedSignInAttempts >= MAX_CONSECUTIVE_FAILURES) {
            throw new IllegalArgumentException("failed sign-in attempts out of range");
        }
        if (role == UserRole.ADMIN && passwordHash == null) {
            throw new IllegalArgumentException("the administrator signs in with a password (R-14)");
        }
    }

    /** UC-04: the single administrator, created from the deployment settings. */
    public static User newAdministrator(UserId id, Email email, String name, PasswordHash passwordHash, Instant now) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        return new User(id, email, true, passwordHash, name, UserRole.ADMIN, 0, null, now, now);
    }

    /** Rebuilds a stored user; only persistence adapters call it. */
    public static User restore(UserId id, Email email, boolean emailVerified, PasswordHash passwordHash, String name,
                               UserRole role, int failedSignInAttempts, Instant lockedUntil, Instant createdAt,
                               Instant updatedAt) {
        return new User(id, email, emailVerified, passwordHash, name, role, failedSignInAttempts, lockedUntil,
            createdAt, updatedAt);
    }

    public static boolean isValidName(String name) {
        return name != null && name.equals(name.strip()) && !name.isEmpty()
            && name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Records a wrong password. Returns the end of the lock when this failure locks the account. */
    public Optional<Instant> registerFailedSignIn(Instant now) {
        if (isLockedAt(now)) {
            throw new IllegalStateException("a locked account does not check passwords");
        }
        updatedAt = now;
        failedSignInAttempts++;
        if (failedSignInAttempts < MAX_CONSECUTIVE_FAILURES) {
            return Optional.empty();
        }
        failedSignInAttempts = 0;
        lockedUntil = now.plus(LOCK_DURATION);
        return Optional.of(lockedUntil);
    }

    public void registerSuccessfulSignIn(Instant now) {
        if (isLockedAt(now)) {
            throw new IllegalStateException("a locked account cannot sign in");
        }
        if (failedSignInAttempts != 0 || lockedUntil != null) {
            failedSignInAttempts = 0;
            lockedUntil = null;
            updatedAt = now;
        }
    }

    public User copy() {
        return new User(id, email, emailVerified, passwordHash, name, role, failedSignInAttempts, lockedUntil,
            createdAt, updatedAt);
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public boolean emailVerified() {
        return emailVerified;
    }

    public Optional<PasswordHash> passwordHash() {
        return Optional.ofNullable(passwordHash);
    }

    public String name() {
        return name;
    }

    public UserRole role() {
        return role;
    }

    public int failedSignInAttempts() {
        return failedSignInAttempts;
    }

    public Optional<Instant> lockedUntil() {
        return Optional.ofNullable(lockedUntil);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
