package com.pijamaparty.store.platform.security;

import com.pijamaparty.store.platform.web.ratelimit.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * The signed-in user of a request, for the back-office policy (T-E3-28, RNF-22 "Usuario"). The limiter runs before
 * Spring Security, so the user is read from the session as Spring Security will read it, without creating one. The
 * key is the principal name, the user id ({@link SessionAuthentication}), never the email. Without a signed-in
 * session there is no user and the policy does not apply.
 */
final class SessionUserKeyResolver implements RateLimitKeyResolver {

    private final SecurityContextRepository contexts;

    SessionUserKeyResolver(SecurityContextRepository contexts) {
        this.contexts = contexts;
    }

    @Override
    public String name() {
        return "user";
    }

    @Override
    public Optional<String> resolve(HttpServletRequest request) {
        Authentication authentication = contexts.loadDeferredContext(request).get().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(authentication.getName());
    }
}
