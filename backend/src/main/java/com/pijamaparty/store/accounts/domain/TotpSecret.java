package com.pijamaparty.store.accounts.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The shared secret of the administrator's authenticator app (RFC 6238), in base32 as the app types it. Only exists
 * in memory: it is stored encrypted ({@link EncryptedSecret}) and never printed.
 */
public record TotpSecret(String base32) {

    private static final Pattern BASE32 = Pattern.compile("[A-Z2-7]{16,64}");

    public TotpSecret {
        Objects.requireNonNull(base32, "base32");
        if (!BASE32.matcher(base32).matches()) {
            throw new IllegalArgumentException("not a base32 secret");
        }
    }

    @Override
    public String toString() {
        return "TotpSecret[***]";
    }
}
