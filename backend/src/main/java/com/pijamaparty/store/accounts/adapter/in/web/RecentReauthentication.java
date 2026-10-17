package com.pijamaparty.store.accounts.adapter.in.web;

import com.pijamaparty.store.platform.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * UC-28 (T-E4-16): the signed-in customer just came back from a provider with the external account linked to it, so
 * for 5 minutes this session may delete the account without the password. The deletion signs out, which uses it up.
 */
record RecentReauthentication(UUID customer, Instant expiresAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final Duration LIFETIME = Duration.ofMinutes(5);
    private static final String ATTRIBUTE = RecentReauthentication.class.getName();

    RecentReauthentication {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    static void store(HttpServletRequest request, UserId customer, Instant now) {
        request.getSession(true).setAttribute(ATTRIBUTE, new RecentReauthentication(customer.value(),
            now.plus(LIFETIME)));
    }

    /** Whether this session holds a reauthentication of {@code customer} that has not expired. */
    static boolean holds(HttpServletRequest request, UserId customer, Instant now) {
        HttpSession session = request.getSession(false);
        return session != null
            && session.getAttribute(ATTRIBUTE) instanceof RecentReauthentication recent
            && recent.customer().equals(customer.value())
            && now.isBefore(recent.expiresAt());
    }
}
