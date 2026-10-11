package com.pijamaparty.store.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.pijamaparty.store.accounts.domain.SecondFactorCode;
import com.pijamaparty.store.accounts.domain.TotpSecret;
import com.pijamaparty.store.platform.testing.ContractTest;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** Contract of {@link TotpVerifier}: 30-second steps, one step of tolerance either side. */
public abstract class TotpVerifierContractTest<T extends TotpVerifier> extends ContractTest<T> {

    protected static final TotpSecret SECRET = new TotpSecret("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP");
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:10Z");
    private static final long STEP = NOW.getEpochSecond() / 30;

    /** The code an authenticator app with this secret shows at {@code step}. */
    protected abstract String codeAt(TotpSecret secret, long step);

    @Test
    void givenTheCodeOfNow_whenChecked_thenItMatchesItsStep() {
        assertThat(subject.matchingStep(SECRET, code(STEP), NOW)).isEqualTo(OptionalLong.of(STEP));
    }

    @Test
    void givenTheCodeOfTheStepBeforeOrAfter_whenChecked_thenItStillMatchesThatStep() {
        assertThat(subject.matchingStep(SECRET, code(STEP - 1), NOW)).isEqualTo(OptionalLong.of(STEP - 1));
        assertThat(subject.matchingStep(SECRET, code(STEP + 1), NOW)).isEqualTo(OptionalLong.of(STEP + 1));
    }

    @Test
    void givenACodeTwoStepsAwayOrOfAnotherSecret_whenChecked_thenItMatchesNothing() {
        TotpSecret other = new TotpSecret("KRSXG5CTMVRXEZLUKRSXG5CTMVRXEZLU");

        assertThat(subject.matchingStep(SECRET, code(STEP - 2), NOW)).isEmpty();
        assertThat(subject.matchingStep(SECRET, code(STEP + 2), NOW)).isEmpty();
        assertThat(subject.matchingStep(other, code(STEP), NOW)).isEmpty();
    }

    private SecondFactorCode.Totp code(long step) {
        return new SecondFactorCode.Totp(codeAt(SECRET, step));
    }
}
