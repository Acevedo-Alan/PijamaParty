package com.pijamaparty.store.platform.domain;

/**
 * An access token sent by the client without an account (RNF-29.6). It grants access, so it is a secret:
 * {@code toString()} never reveals it.
 */
public sealed interface AccessToken permits AccessToken.CartToken, AccessToken.TrackingToken {

    String value();

    /** Identifies an anonymous cart. */
    record CartToken(String value) implements AccessToken {
        public CartToken {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Cart token cannot be blank");
            }
        }

        @Override
        public String toString() {
            return "CartToken[***]";
        }
    }

    /** Opens an order through its tracking link. */
    record TrackingToken(String value) implements AccessToken {
        public TrackingToken {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Tracking token cannot be blank");
            }
        }

        @Override
        public String toString() {
            return "TrackingToken[***]";
        }
    }
}
