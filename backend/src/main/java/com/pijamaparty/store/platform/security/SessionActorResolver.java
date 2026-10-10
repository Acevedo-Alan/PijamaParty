package com.pijamaparty.store.platform.security;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.Role;
import com.pijamaparty.store.platform.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The administrator signed in through {@link SessionAuthentication} acts as {@code ADMIN} with its user id. Signed-in
 * customers are added in E4, with their cart and tracking tokens.
 */
final class SessionActorResolver implements ActorResolver {

    static final String AUTHORITY_PREFIX = "ROLE_";
    private static final String ADMIN = AUTHORITY_PREFIX + Role.ADMIN.name();

    @Override
    public Optional<Actor> resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).noneMatch(ADMIN::equals)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Actor.admin(UserId.of(UUID.fromString(authentication.getName()))));
        } catch (IllegalArgumentException notAUserId) {
            return Optional.empty();
        }
    }
}
