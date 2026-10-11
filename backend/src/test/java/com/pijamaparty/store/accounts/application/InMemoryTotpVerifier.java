package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * Fake of {@link TotpVerifier}: the code of a step is derived from the secret and the step by a simple formula, so a
 * test plays the authenticator app with {@link #codeAt}. Steps are 30 seconds, with the same tolerance.
 */
public class InMemoryTotpVerifier implements TotpVerifier {

    public static final int PERIOD_SECONDS = 30;

    /** The code the app would show at {@code step}. */
    public static String codeAt(TotpSecret secret, long step) {
        long value = Math.floorMod(secret.base32().hashCode() * 31L + step * 7919L, 1_000_000L);
        return String.format("%06d", value);
    }

    public static long stepAt(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), PERIOD_SECONDS);
    }

    @Override
    public OptionalLong matchingStep(TotpSecret secret, SecondFactorCode.Totp code, Instant now) {
        long current = stepAt(now);
        for (long step = current - TOLERANCE_STEPS; step <= current + TOLERANCE_STEPS; step++) {
            if (codeAt(secret, step).equals(code.digits())) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }
}
