package com.pijamaparty.store.accounts.domain;

import java.util.Arrays;
import java.util.Objects;

/** A {@link TotpSecret} as stored: encrypted, opaque to everything but the cipher that made it (T-E1-16). */
public final class EncryptedSecret {

    private final byte[] bytes;

    public EncryptedSecret(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("an encrypted secret is never empty");
        }
        this.bytes = bytes.clone();
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EncryptedSecret that && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "EncryptedSecret[***]";
    }
}
