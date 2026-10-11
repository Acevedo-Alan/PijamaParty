package com.pijamaparty.store.accounts.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What the administrator types after the password (T-E1-16): the 6 digits of the authenticator app or one of the
 * recovery codes. Spaces, dashes and case do not matter. Never printed.
 */
public sealed interface SecondFactorCode {

    /** A recovery code is 10 characters from the alphabet without look-alikes of the SKUs (UC-06). */
    String RECOVERY_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    int RECOVERY_LENGTH = 10;

    Pattern DIGITS = Pattern.compile("\\d{6}");
    Pattern RECOVERY = Pattern.compile("[" + RECOVERY_ALPHABET + "]{" + RECOVERY_LENGTH + "}");

    /** Empty when the text is neither 6 digits nor a recovery code. */
    static Optional<SecondFactorCode> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (DIGITS.matcher(compact).matches()) {
            return Optional.of(new Totp(compact));
        }
        if (RECOVERY.matcher(compact).matches()) {
            return Optional.of(new Recovery(compact));
        }
        return Optional.empty();
    }

    record Totp(String digits) implements SecondFactorCode {

        public Totp {
            Objects.requireNonNull(digits, "digits");
            if (!DIGITS.matcher(digits).matches()) {
                throw new IllegalArgumentException("a TOTP code has 6 digits");
            }
        }

        @Override
        public String toString() {
            return "Totp[***]";
        }
    }

    /** A recovery code, compact (no dash) and in capitals. */
    record Recovery(String value) implements SecondFactorCode {

        public Recovery {
            Objects.requireNonNull(value, "value");
            if (!RECOVERY.matcher(value).matches()) {
                throw new IllegalArgumentException("not a recovery code");
            }
        }

        /** How it is shown once: {@code XXXXX-XXXXX}. */
        public String display() {
            return value.substring(0, RECOVERY_LENGTH / 2) + "-" + value.substring(RECOVERY_LENGTH / 2);
        }

        /** What is stored: its SHA-256. The codes are random, so a plain hash is enough. */
        public RecoveryCodeHash hash() {
            try {
                return new RecoveryCodeHash(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("every JVM has SHA-256", e);
            }
        }

        @Override
        public String toString() {
            return "Recovery[***]";
        }
    }
}
