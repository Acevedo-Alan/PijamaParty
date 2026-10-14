package com.pijamaparty.store.platform.application;

import com.pijamaparty.store.platform.domain.IdempotencyScope;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Outbound port (RNF-20.1, RNF-20.2): the {@code Idempotency-Key}s of requests that had an effect, with the answer they
 * got. Joins the caller's transaction, which runs as the actor of the scope (RNF-27): the claim lasts until that
 * transaction ends, and the answer recorded commits or rolls back with the effect it describes.
 */
public interface IdempotencyKeys {

    /**
     * Claims the key for the caller's transaction without waiting: until it ends, no other transaction claims it.
     *
     * @return {@link Claim.Busy} when another transaction holds it; otherwise the answer recorded for the key, if any
     */
    Claim claim(IdempotencyScope scope, UUID key);

    /** Records the answer of a key this transaction claimed and found without one. */
    void record(IdempotencyScope scope, UUID key, StoredAnswer answer, Instant createdAt, Instant expiresAt);

    sealed interface Claim {

        /** A request with the same key is still being processed. */
        record Busy() implements Claim {}

        /** The key is this transaction's, and no request with it had an effect yet. */
        record Fresh() implements Claim {}

        /** The key is this transaction's, and the first request with it got this answer. */
        record Answered(StoredAnswer answer) implements Claim {}
    }

    /**
     * What the first request got: the hash of its content, what it created, and its answer without secrets.
     *
     * @param requestHash SHA-256 in lowercase hex of the request's content
     * @param body        the answer's fields; never an access token or personal data
     */
    record StoredAnswer(String requestHash, UUID resourceId, int status, Map<String, String> body) {

        private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");

        public StoredAnswer {
            Objects.requireNonNull(requestHash, "requestHash");
            Objects.requireNonNull(resourceId, "resourceId");
            if (!SHA_256_HEX.matcher(requestHash).matches()) {
                throw new IllegalArgumentException("The request hash is SHA-256 in lowercase hex");
            }
            if (status < 200 || status > 299) {
                throw new IllegalArgumentException("Only an answer with an effect is stored");
            }
            body = Map.copyOf(body);
        }
    }
}
