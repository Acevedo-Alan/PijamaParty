package com.pijamaparty.store.platform.security;

import com.pijamaparty.store.platform.domain.Actor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Turns a request into the {@link Actor} that use cases and the Row Level Security context see. Each stage adds its
 * own resolver (cart token in E2, tracking token in E3, authenticated customer and administrator in E4); the first
 * one that recognizes the request wins, ordered with {@code @Order}. A request nobody recognizes is anonymous.
 *
 * <p>Controllers build the use-case command with {@code CurrentActor.get()}, so the authorization check in the use
 * case and the RLS context in the database always see the same actor (ARCHITECTURE.md section 8).
 */
@FunctionalInterface
public interface ActorResolver {

    Optional<Actor> resolve(HttpServletRequest request);
}
