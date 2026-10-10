package com.pijamaparty.store.platform.security;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.persistence.CurrentActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Binds the request's {@link Actor} to {@link CurrentActor} for the whole request, after Spring Security has
 * authenticated it, so every transaction of the request carries the right RLS context (RNF-27). The binding is
 * always removed when the request ends, so a pooled thread never keeps someone else's actor.
 */
final class ActorFilter extends OncePerRequestFilter {

    private static final Actor ANONYMOUS = Actor.anonymous(Optional.empty(), Optional.empty());

    private final List<ActorResolver> resolvers;

    ActorFilter(List<ActorResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        Actor actor = resolvers.stream()
            .map(resolver -> resolver.resolve(request))
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(ANONYMOUS);
        try (CurrentActor.Binding binding = CurrentActor.bind(actor)) {
            chain.doFilter(request, response);
        }
    }
}
