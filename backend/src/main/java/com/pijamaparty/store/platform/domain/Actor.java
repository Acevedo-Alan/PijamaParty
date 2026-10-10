package com.pijamaparty.store.platform.domain;

import com.pijamaparty.store.platform.domain.AccessToken.CartToken;
import com.pijamaparty.store.platform.domain.AccessToken.TrackingToken;
import java.util.Objects;
import java.util.Optional;

/**
 * Who executes a use case. Commands carry it; the use case checks authorization and the same values feed the
 * Row Level Security context (RNF-27), so the database enforces it again.
 */
public record Actor(Role role, Optional<UserId> userId, Optional<CartToken> cartToken,
                    Optional<TrackingToken> trackingToken) {

    public Actor {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(cartToken, "cartToken");
        Objects.requireNonNull(trackingToken, "trackingToken");
        boolean needsUser = role == Role.CUSTOMER || role == Role.ADMIN;
        if (needsUser != userId.isPresent()) {
            throw new IllegalArgumentException(role + (needsUser ? " requires" : " cannot have") + " a user id");
        }
        boolean tokensAllowed = role == Role.ANONYMOUS || role == Role.CUSTOMER;
        if (!tokensAllowed && (cartToken.isPresent() || trackingToken.isPresent())) {
            throw new IllegalArgumentException(role + " cannot carry access tokens");
        }
    }

    public static Actor anonymous(Optional<CartToken> cartToken, Optional<TrackingToken> trackingToken) {
        return new Actor(Role.ANONYMOUS, Optional.empty(), cartToken, trackingToken);
    }

    public static Actor customer(UserId userId, Optional<CartToken> cartToken, Optional<TrackingToken> trackingToken) {
        return new Actor(Role.CUSTOMER, Optional.of(userId), cartToken, trackingToken);
    }

    public static Actor admin(UserId userId) {
        return new Actor(Role.ADMIN, Optional.of(userId), Optional.empty(), Optional.empty());
    }

    public static Actor system() {
        return new Actor(Role.SYSTEM, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean is(Role candidate) {
        return role == candidate;
    }
}
