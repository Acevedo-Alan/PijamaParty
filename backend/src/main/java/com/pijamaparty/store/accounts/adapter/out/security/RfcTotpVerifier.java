package com.pijamaparty.store.accounts.adapter.out.security;

import com.pijamaparty.store.accounts.application.TotpVerifier;
import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;

/** RFC 6238 with the totp library's generator: HMAC-SHA1, 6 digits, 30-second steps. */
@Component
class RfcTotpVerifier implements TotpVerifier {

    static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;

    private final DefaultCodeGenerator generator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, DIGITS);

    @Override
    public OptionalLong matchingStep(TotpSecret secret, SecondFactorCode.Totp code, Instant now) {
        long current = Math.floorDiv(now.getEpochSecond(), PERIOD_SECONDS);
        byte[] typed = code.digits().getBytes(StandardCharsets.US_ASCII);
        OptionalLong match = OptionalLong.empty();
        // Every step of the window is computed, so the time taken does not tell which one matched.
        for (long step = current - TOLERANCE_STEPS; step <= current + TOLERANCE_STEPS; step++) {
            byte[] expected = codeAt(secret, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, typed) && match.isEmpty()) {
                match = OptionalLong.of(step);
            }
        }
        return match;
    }

    /** The code of a time step; visible for the contract test, which plays the authenticator app. */
    String codeAt(TotpSecret secret, long step) {
        try {
            return generator.generate(secret.base32(), step);
        } catch (CodeGenerationException e) {
            throw new IllegalStateException("cannot compute a TOTP code", e);
        }
    }
}
