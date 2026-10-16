package com.pijamaparty.store.platform.security;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A user signed in through {@link SessionAuthentication} acts with its role and user id: the administrator as
 * {@code ADMIN}, a customer as {@code CUSTOMER} (UC-19). A customer acts on its own cart; the guest cart joins it in
 * T-E4-04, and a tracking link adds its token per request.
 */
final class SessionActorResolver implements ActorResolver {

    static final String AUTHORITY_PREFIX = "ROLE_";
    private static final String ADMIN = AUTHORITY_PREFIX + Role.ADMIN.name();
    private static final String CUSTOMER = AUTHORITY_PREFIX + Role.CUSTOMER.name();

    @Override
    public Optional<Actor> resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Set<String> authorities = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        try {
            UserId user = UserId.of(UUID.fromString(authentication.getName()));
            if (authorities.contains(ADMIN)) {
                return Optional.of(Actor.admin(user));
            }
            if (authorities.contains(CUSTOMER)) {
                return Optional.of(Actor.customer(user, Optional.empty(), Optional.empty()));
            }
            return Optional.empty();
        } catch (IllegalArgumentException notAUserId) {
            return Optional.empty();
        }
    }
}
