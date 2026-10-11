package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.application.SignInAdministrator;
import com.pijamaparty.store.accounts.domain.EncryptedSecret;
import com.pijamaparty.store.platform.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The sign-in between the right password and the second factor (T-E1-16), kept in the session, which is not
 * authenticated yet: the user id, the next step, until when it lasts, and the encrypted secret being enrolled. Never
 * the email, the password or a plain secret.
 */
record PendingSignIn(UUID userId, SignInAdministrator.NextStep nextStep, Instant expiresAt, byte[] enrollmentSecret)
    implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final Duration LIFETIME = Duration.ofMinutes(5);
    private static final String ATTRIBUTE = PendingSignIn.class.getName();

    static PendingSignIn after(SignInAdministrator.Result.PasswordAccepted accepted, Instant now) {
        return new PendingSignIn(accepted.administrator().value(), accepted.nextStep(), now.plus(LIFETIME), null);
    }

    /** The pending sign-in of this session for {@code step}, if any and not expired. */
    static Optional<PendingSignIn> of(HttpServletRequest request, SignInAdministrator.NextStep step, Instant now) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof PendingSignIn pending)) {
            return Optional.empty();
        }
        if (pending.nextStep() != step || !now.isBefore(pending.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    void store(HttpServletRequest request) {
        request.getSession(true).setAttribute(ATTRIBUTE, this);
    }

    static void clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(ATTRIBUTE);
        }
    }

    UserId user() {
        return UserId.of(userId);
    }

    PendingSignIn withEnrollmentSecret(EncryptedSecret secret) {
        return new PendingSignIn(userId, nextStep, expiresAt, secret.bytes());
    }

    Optional<EncryptedSecret> secret() {
        return enrollmentSecret == null ? Optional.empty() : Optional.of(new EncryptedSecret(enrollmentSecret));
    }

    @Override
    public String toString() {
        return "PendingSignIn[***]";
    }
}
