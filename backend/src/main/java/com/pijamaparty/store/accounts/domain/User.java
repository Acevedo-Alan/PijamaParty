package com.pijamaparty.store.accounts.domain;

import com.pijamaparty.store.platform.domain.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A customer or the administrator. Owns the sign-in lock rule of UC-04, which UC-19 applies to customers too: 5
 * consecutive failures lock the account for 15 minutes. Only consecutive failures count (a successful sign-in resets
 * them), and the failure that locks also resets the counter, so after the lock there are 5 attempts again. A wrong
 * second-factor code is a failure too. The administrator's second factor (T-E1-16) accepts each code once.
 */
public final class User {

    public static final int MAX_CONSECUTIVE_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    /** 00-dominio-y-reglas.md, maximum lengths. */
    public static final int MAX_NAME_LENGTH = 100;
    /** UC-21: digits only, as the order's phone (UC-13) and the check of {@code app_user.phone} (V29). */
    private static final Pattern PHONE = Pattern.compile("[0-9]{8,15}");

    private final UserId id;
    private final Email email;
    private boolean emailVerified;
    private PasswordHash passwordHash;
    private final String name;
    private String phone;
    private final UserRole role;
    private int failedSignInAttempts;
    private Instant lockedUntil;
    private SecondFactor secondFactor;
    private final Instant createdAt;
    private Instant updatedAt;

    private User(UserId id, Email email, boolean emailVerified, PasswordHash passwordHash, String name, String phone,
                 UserRole role, int failedSignInAttempts, Instant lockedUntil, SecondFactor secondFactor,
                 Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email");
        this.emailVerified = emailVerified;
        this.passwordHash = passwordHash;
        this.name = Objects.requireNonNull(name, "name");
        this.phone = phone;
        this.role = Objects.requireNonNull(role, "role");
        this.failedSignInAttempts = failedSignInAttempts;
        this.lockedUntil = lockedUntil;
        this.secondFactor = secondFactor;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!isValidName(name)) {
            throw new IllegalArgumentException("name must be trimmed and 1 to " + MAX_NAME_LENGTH + " characters");
        }
        if (phone != null && !isValidPhone(phone)) {
            throw new IllegalArgumentException("phone must be 8 to 15 digits");
        }
        if (failedSignInAttempts < 0 || failedSignInAttempts >= MAX_CONSECUTIVE_FAILURES) {
            throw new IllegalArgumentException("failed sign-in attempts out of range");
        }
        if (role == UserRole.ADMIN && passwordHash == null) {
            throw new IllegalArgumentException("the administrator signs in with a password (R-14)");
        }
        if (secondFactor != null && role != UserRole.ADMIN) {
            throw new IllegalArgumentException("only the administrator has a second factor (T-E1-16)");
        }
    }

    /** UC-04: the single administrator, created from the deployment settings. */
    public static User newAdministrator(UserId id, Email email, String name, PasswordHash passwordHash, Instant now) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        return new User(id, email, true, passwordHash, name, null, UserRole.ADMIN, 0, null, null, now, now);
    }

    /** UC-19: a customer who registers with a password; it signs in once its email is verified. */
    public static User newCustomer(UserId id, Email email, String name, PasswordHash passwordHash, Instant now) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        return new User(id, email, false, passwordHash, name, null, UserRole.CUSTOMER, 0, null, null, now, now);
    }

    /**
     * UC-27: a customer who signs in with a provider, without a password. Verified only when the provider vouched for
     * its email (Google); otherwise it waits for an email link, as a customer who registered.
     */
    public static User newExternalCustomer(UserId id, Email email, String name, boolean emailVerified, Instant now) {
        return new User(id, email, emailVerified, null, name, null, UserRole.CUSTOMER, 0, null, null, now, now);
    }

    /**
     * UC-27 (T-E4-14): the name of a customer created by a provider: the provider's, trimmed and cut to
     * {@value #MAX_NAME_LENGTH} characters, or the part of the email before {@code @} when the provider gave none.
     */
    public static String nameFromProvider(String providerName, Email email) {
        String name = providerName == null ? "" : providerName.strip();
        if (name.isEmpty()) {
            name = email.value().substring(0, email.value().indexOf('@'));
        }
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
            name = name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH)).strip();
        }
        return name;
    }

    /** Rebuilds a stored user without a phone or a second factor; only persistence adapters call it. */
    public static User restore(UserId id, Email email, boolean emailVerified, PasswordHash passwordHash, String name,
                               UserRole role, int failedSignInAttempts, Instant lockedUntil, Instant createdAt,
                               Instant updatedAt) {
        return new User(id, email, emailVerified, passwordHash, name, null, role, failedSignInAttempts, lockedUntil,
            null, createdAt, updatedAt);
    }

    /**
     * Rebuilds a stored user with its phone and its second factor, if any; only persistence adapters call it.
     *
     * @param phone null for none
     */
    public static User restore(UserId id, Email email, boolean emailVerified, PasswordHash passwordHash, String name,
                               String phone, UserRole role, int failedSignInAttempts, Instant lockedUntil,
                               SecondFactor secondFactor, Instant createdAt, Instant updatedAt) {
        return new User(id, email, emailVerified, passwordHash, name, phone, role, failedSignInAttempts, lockedUntil,
            secondFactor, createdAt, updatedAt);
    }

    public static boolean isValidName(String name) {
        return name != null && name.equals(name.strip()) && !name.isEmpty()
            && name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH;
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(phone).matches();
    }

    /**
     * UC-21 (T-E4-06): a purchase of the customer leaves its phone, already normalized, for the next checkout. The
     * same phone changes nothing.
     */
    public void rememberPhone(String newPhone, Instant now) {
        if (!isValidPhone(newPhone)) {
            throw new IllegalArgumentException("phone must be 8 to 15 digits");
        }
        if (role != UserRole.CUSTOMER) {
            throw new IllegalStateException("only a customer buys");
        }
        if (!newPhone.equals(phone)) {
            phone = newPhone;
            updatedAt = now;
        }
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** UC-19: the link of the verification email was opened. Verifying twice changes nothing. */
    public void verifyEmail(Instant now) {
        if (!emailVerified) {
            emailVerified = true;
            updatedAt = now;
        }
    }

    /**
     * UC-27 (T-E4-13): a linked Google account vouched for the email, or the link of a link confirmation email was
     * opened. Only a customer. An unverified account is verified and loses its password: whoever set it never proved
     * it owned the inbox, so an account pre-registered with someone else's email cannot be taken over. A verified
     * account changes nothing.
     */
    public void verifyEmailThroughExternalAccount(Instant now) {
        if (role != UserRole.CUSTOMER) {
            throw new IllegalStateException("only a customer links external accounts (R-14)");
        }
        if (!emailVerified) {
            emailVerified = true;
            passwordHash = null;
            updatedAt = now;
        }
    }

    /**
     * UC-19 (T-E4-03): the link of the reset email was opened with a new password. Only a customer: the old password,
     * if any, is replaced, the failed attempts and the lock are cleared, and the email is verified, because the link
     * reached its inbox.
     */
    public void resetPassword(PasswordHash newPasswordHash, Instant now) {
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        if (role != UserRole.CUSTOMER) {
            throw new IllegalStateException("only a customer resets its password by email");
        }
        passwordHash = newPasswordHash;
        failedSignInAttempts = 0;
        lockedUntil = null;
        emailVerified = true;
        updatedAt = now;
    }

    /**
     * UC-27 (T-E4-15): a signed-in customer without a password creates one from "Mi cuenta". Changing a password stays
     * with the recovery of UC-19. It does not verify the email.
     *
     * @return false, changing nothing, when the customer already has a password
     */
    public boolean createPassword(PasswordHash newPasswordHash, Instant now) {
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        if (role != UserRole.CUSTOMER) {
            throw new IllegalStateException("only a customer creates its password");
        }
        if (passwordHash != null) {
            return false;
        }
        passwordHash = newPasswordHash;
        updatedAt = now;
        return true;
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

    /** How a code of the authenticator app ended (T-E1-16). */
    public enum CodeOutcome {
        ACCEPTED,
        /** Its time step, or a later one, was already used: a code works once. */
        REPLAYED
    }

    /**
     * Records a right code of the authenticator app at time step {@code step}: the sign-in succeeds and that step is
     * spent. A step that is not after the last accepted one changes nothing and is {@link CodeOutcome#REPLAYED}.
     */
    public CodeOutcome acceptCodeAt(long step, Instant now) {
        if (secondFactor == null) {
            throw new IllegalStateException("no second factor to accept a code for");
        }
        if (step <= secondFactor.lastStep()) {
            return CodeOutcome.REPLAYED;
        }
        registerSuccessfulSignIn(now);
        secondFactor = secondFactor.afterStep(step);
        updatedAt = now;
        return CodeOutcome.ACCEPTED;
    }

    /**
     * Enrolls the authenticator app whose code of time step {@code step} was just confirmed; that step is already spent.
     * Only the administrator, and only without a second factor.
     */
    public void enrollSecondFactor(EncryptedSecret secret, long step, Instant now) {
        if (role != UserRole.ADMIN) {
            throw new IllegalStateException("only the administrator has a second factor");
        }
        if (secondFactor != null) {
            throw new IllegalStateException("a second factor is already enrolled");
        }
        registerSuccessfulSignIn(now);
        secondFactor = new SecondFactor(secret, now, step);
        updatedAt = now;
    }

    /** Forgets the second factor (lost phone and recovery codes), so the next sign-in enrolls again. */
    public void resetSecondFactor(Instant now) {
        if (secondFactor != null) {
            secondFactor = null;
            updatedAt = now;
        }
    }

    public Optional<SecondFactor> secondFactor() {
        return Optional.ofNullable(secondFactor);
    }

    public User copy() {
        return new User(id, email, emailVerified, passwordHash, name, phone, role, failedSignInAttempts, lockedUntil,
            secondFactor, createdAt, updatedAt);
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

    public Optional<String> phone() {
        return Optional.ofNullable(phone);
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
