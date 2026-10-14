package com.pijamaparty.store.platform.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Whose {@code Idempotency-Key} it is (RNF-20.1): a customer's user, or else a guest's cart token, never in clear
 * (RNF-29.6). The same key in two scopes is two keys. {@code toString()} hides the value: a cart token's hash still
 * identifies a cart.
 */
public record IdempotencyScope(String value) {

    private static final Pattern FORMAT = Pattern.compile(
        "USER:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}|CART:[0-9a-f]{64}");

    public IdempotencyScope {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Not an idempotency scope");
        }
    }

    /** A customer's keys are its own wherever it buys from; a guest's belong to its cart. Staff have none. */
    public static Optional<IdempotencyScope> of(Actor actor) {
        return switch (actor.role()) {
            case CUSTOMER -> actor.userId().map(user -> new IdempotencyScope("USER:" + user.value()));
            case ANONYMOUS -> actor.cartToken().map(token -> new IdempotencyScope("CART:" + token.hash()));
            case ADMIN, SYSTEM -> Optional.empty();
        };
    }

    @Override
    public String toString() {
        return "IdempotencyScope[***]";
    }
}
