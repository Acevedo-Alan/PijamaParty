package com.pijamaparty.store.accounts.application;

import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import java.time.Instant;
import java.util.OptionalLong;

/** Outbound port: checks a code of the authenticator app (RFC 6238: HMAC-SHA1, 6 digits, 30-second steps). */
public interface TotpVerifier {

    /** One step of tolerance either side of {@code now}, for clocks that drift and slow typing. */
    int TOLERANCE_STEPS = 1;

    /** The time step the code belongs to, or empty when it matches none within the tolerance. */
    OptionalLong matchingStep(TotpSecret secret, SecondFactorCode.Totp code, Instant now);
}
