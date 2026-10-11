package com.pijamaparty.store.accounts.domain;

import java.util.Arrays;
import java.util.Objects;

/** The SHA-256 of a recovery code, the only form in which it is stored (T-E1-16). */
public final class RecoveryCodeHash {

    private static final int LENGTH = 32;

    private final byte[] bytes;

    public RecoveryCodeHash(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("a SHA-256 has 32 bytes");
        }
        this.bytes = bytes.clone();
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RecoveryCodeHash that && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "RecoveryCodeHash[***]";
    }
}
