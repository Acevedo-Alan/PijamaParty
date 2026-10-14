package com.pijamaparty.store.platform.application;

import com.pijamaparty.store.platform.domain.IdempotencyScope;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@link IdempotencyKeys} and {@link ExpiredIdempotencyKeys} in memory. There are no transactions here, so a claim is
 * never held: {@link #holdElsewhere} stands for a request with the key still in flight.
 */
public final class InMemoryIdempotencyKeys implements IdempotencyKeys, ExpiredIdempotencyKeys {

    private record Ref(IdempotencyScope scope, UUID key) {}

    private record Stored(StoredAnswer answer, Instant expiresAt) {}

    private final Map<Ref, Stored> keys = new HashMap<>();
    private final Set<Ref> heldElsewhere = new HashSet<>();

    @Override
    public Claim claim(IdempotencyScope scope, UUID key) {
        Ref ref = new Ref(scope, key);
        if (heldElsewhere.contains(ref)) {
            return new Claim.Busy();
        }
        Stored stored = keys.get(ref);
        return stored == null ? new Claim.Fresh() : new Claim.Answered(stored.answer());
    }

    @Override
    public void record(IdempotencyScope scope, UUID key, StoredAnswer answer, Instant createdAt, Instant expiresAt) {
        if (keys.putIfAbsent(new Ref(scope, key), new Stored(answer, expiresAt)) != null) {
            throw new IllegalStateException("the key already has an answer");
        }
    }

    @Override
    public int deleteExpiredAt(Instant now) {
        int before = keys.size();
        keys.values().removeIf(stored -> !stored.expiresAt().isAfter(now));
        return before - keys.size();
    }

    /** Another request with this key is still being processed. */
    public void holdElsewhere(IdempotencyScope scope, UUID key) {
        heldElsewhere.add(new Ref(scope, key));
    }

    public Optional<Instant> expiryOf(IdempotencyScope scope, UUID key) {
        return Optional.ofNullable(keys.get(new Ref(scope, key))).map(Stored::expiresAt);
    }

    public int size() {
        return keys.size();
    }
}
