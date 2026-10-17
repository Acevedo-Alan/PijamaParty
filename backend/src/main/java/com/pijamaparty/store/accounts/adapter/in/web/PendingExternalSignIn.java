package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.domain.ExternalAccount;
import com.pijamaparty.store.accounts.domain.ExternalProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * UC-27 (T-E4-14): an external account the provider returned without an email, kept in the session, which is not
 * authenticated, while the visitor gives one: the provider, its account identifier and the name it gave (null for
 * none), until when it lasts. Never an email or a provider token.
 */
record PendingExternalSignIn(ExternalProvider provider, String subject, String name, Instant expiresAt)
    implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final Duration LIFETIME = Duration.ofMinutes(15);
    private static final String ATTRIBUTE = PendingExternalSignIn.class.getName();

    static PendingExternalSignIn of(ExternalAccount account, Optional<String> name, Instant now) {
        return new PendingExternalSignIn(account.provider(), account.subject(), name.orElse(null), now.plus(LIFETIME));
    }

    /** The external account waiting in this session, if any and not expired. */
    static Optional<PendingExternalSignIn> of(HttpServletRequest request, Instant now) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof PendingExternalSignIn pending)
            || !now.isBefore(pending.expiresAt())) {
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

    ExternalAccount account() {
        return new ExternalAccount(provider, subject);
    }

    Optional<String> providerName() {
        return Optional.ofNullable(name);
    }

    @Override
    public String toString() {
        return "PendingExternalSignIn[provider=" + provider + ", ***]";
    }
}
