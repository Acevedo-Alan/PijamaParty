package com.pijamaparty.store.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** The {@code Idempotency-Key} of a photo upload, unique per product (UC-06): a retry with it stores nothing new. */
public record UploadKey(UUID value) {

    public UploadKey {
        Objects.requireNonNull(value, "value");
    }

    public static UploadKey of(UUID value) {
        return new UploadKey(value);
    }
}
