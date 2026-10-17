package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.domain.ExternalProvider;
import com.pijamaparty.store.platform.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a signed-in customer asked, from "Mi cuenta", to do with a provider, kept in its session until it comes back
 * from the provider: link an account of it (UC-27, T-E4-15) or confirm the deletion of its account with the one
 * linked (UC-28, T-E4-16); the customer, the provider and until when it lasts. A session waits for one intent at a
 * time: a new one replaces the other. Only a {@code POST} with the CSRF token stores it, so another site can never
 * make a signed-in browser link an account.
 */
record PendingProviderIntent(UUID customer, ExternalProvider provider, Purpose purpose, Instant expiresAt)
    implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final Duration LIFETIME = Duration.ofMinutes(15);
    private static final String ATTRIBUTE = PendingProviderIntent.class.getName();

    PendingProviderIntent {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    static PendingProviderIntent of(UserId customer, ExternalProvider provider, Purpose purpose, Instant now) {
        return new PendingProviderIntent(customer.value(), provider, purpose, now.plus(LIFETIME));
    }

    /** The intent waiting in this session, if any and not expired; it is forgotten either way. */
    static Optional<PendingProviderIntent> take(HttpServletRequest request, Instant now) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof PendingProviderIntent pending)) {
            return Optional.empty();
        }
        session.removeAttribute(ATTRIBUTE);
        return now.isBefore(pending.expiresAt()) ? Optional.of(pending) : Optional.empty();
    }

    /** The intent waiting in this session, if any and not expired, left in place for the provider's return. */
    static Optional<PendingProviderIntent> peek(HttpServletRequest request, Instant now) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof PendingProviderIntent pending)) {
            return Optional.empty();
        }
        return now.isBefore(pending.expiresAt()) ? Optional.of(pending) : Optional.empty();
    }

    void store(HttpServletRequest request) {
        request.getSession(true).setAttribute(ATTRIBUTE, this);
    }

    UserId customerId() {
        return UserId.of(customer);
    }

    enum Purpose {

        /** UC-27 (T-E4-15): link an account of the provider. */
        LINK,

        /** UC-28 (T-E4-16): confirm the deletion of the account with the provider's account linked to it. */
        REAUTHENTICATE
    }
}
