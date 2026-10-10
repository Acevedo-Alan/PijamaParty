package com.pijamaparty.store.platform.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * An access token sent by the client without an account (RNF-29.6). It grants access, so it is a secret:
 * {@code toString()} never reveals it, and the database only ever sees its {@link #hash()}.
 */
public sealed interface AccessToken permits AccessToken.CartToken, AccessToken.TrackingToken {

    String value();

    /** SHA-256 in lowercase hex: what tables store and what the Row Level Security context compares with. */
    default String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

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
