package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.accounts.domain.ExternalProvider;
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
 * UC-27 (T-E4-15): a signed-in customer asked, from "Mi cuenta", to link an account of a provider, kept in its session
 * until it comes back from the provider: the customer, the provider and until when it lasts. Only a {@code POST} with
 * the CSRF token stores it, so another site can never make a signed-in browser link an account.
 */
record PendingExternalLink(UUID customer, ExternalProvider provider, Instant expiresAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final Duration LIFETIME = Duration.ofMinutes(15);
    private static final String ATTRIBUTE = PendingExternalLink.class.getName();

    static PendingExternalLink of(UserId customer, ExternalProvider provider, Instant now) {
        return new PendingExternalLink(customer.value(), provider, now.plus(LIFETIME));
    }

    /** The link waiting in this session, if any and not expired; it is forgotten either way. */
    static Optional<PendingExternalLink> take(HttpServletRequest request, Instant now) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(ATTRIBUTE) instanceof PendingExternalLink pending)) {
            return Optional.empty();
        }
        session.removeAttribute(ATTRIBUTE);
        return now.isBefore(pending.expiresAt()) ? Optional.of(pending) : Optional.empty();
    }

    void store(HttpServletRequest request) {
        request.getSession(true).setAttribute(ATTRIBUTE, this);
    }

    UserId customerId() {
        return UserId.of(customer);
    }
}
