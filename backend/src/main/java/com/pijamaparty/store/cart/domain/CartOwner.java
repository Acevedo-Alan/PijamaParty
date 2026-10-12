package com.pijamaparty.store.cart.domain;

import com.pijamaparty.store.platform.domain.Actor;
import com.pijamaparty.store.platform.domain.UserId;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Whose cart it is: a guest by the hash of its {@code cart_token}, or a customer by its user id. Each has at most
 * one cart, and Row Level Security lets each reach only its own (RNF-27).
 */
public sealed interface CartOwner permits CartOwner.Guest, CartOwner.Customer {

    /**
     * The owner of the actor's cart: a customer by its user id, a guest by its cart token. Empty for an actor that
     * cannot own a cart: the administrator, the system and a guest without a token.
     */
    static Optional<CartOwner> of(Actor actor) {
        return switch (actor.role()) {
            case CUSTOMER -> actor.userId().map(Customer::new);
            case ANONYMOUS -> actor.cartToken().map(token -> new Guest(token.hash()));
            case ADMIN, SYSTEM -> Optional.empty();
        };
    }

    /** {@code tokenHash} is {@code AccessToken.hash()} of the guest's cart token: SHA-256 in lowercase hex. */
    record Guest(String tokenHash) implements CartOwner {

        private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

        public Guest {
            if (tokenHash == null || !SHA_256_HEX.matcher(tokenHash).matches()) {
                throw new IllegalArgumentException("not a SHA-256 hash in lowercase hex");
            }
        }
    }

    record Customer(UserId userId) implements CartOwner {

        public Customer {
            Objects.requireNonNull(userId, "userId");
        }
    }
}
